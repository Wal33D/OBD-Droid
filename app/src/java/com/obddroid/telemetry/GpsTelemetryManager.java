package com.obddroid.telemetry;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.obddroid.R;
import com.obddroid.ecu.Conversion;
import com.obddroid.ecu.EcuDataItem;
import com.obddroid.ecu.EcuDataItems;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.ecu.Conversions;
import com.obddroid.obd.ObdProt;
import com.obddroid.ui.activities.SettingsActivity;
import com.obddroid.utils.DebugLog;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Captures device GPS telemetry and exposes it as synthetic PIDs so the rest of the app (dashboards,
 * CSV logging, etc.) can consume it just like regular vehicle data.
 */
public class GpsTelemetryManager implements LocationListener {

    private static final String TAG = "GpsTelemetry";

    private static final int SERVICE = ObdProt.OBD_SVC_DATA;
    private static final int CUSTOM_PID_BASE = 0xF100;

    private final Context appContext;
    private final LocationManager locationManager;
    private final Map<GpsField, EcuDataItem> items = new EnumMap<>(GpsField.class);
    private final Map<GpsField, EcuDataPv> dataPvs = new EnumMap<>(GpsField.class);
    private final Set<String> registeredKeys = new HashSet<>();
    private final Map<String, TelemetryFieldLoader.FieldDefinition> customFields;

    private boolean active = false;

    public GpsTelemetryManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.locationManager = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);

        // Load custom field definitions from CSV if available
        this.customFields = TelemetryFieldLoader.loadGpsFields(context);
    }

    public boolean isActive() {
        return active;
    }

    @MainThread
    public void start() {
        if (active) {
            return;
        }
        if (locationManager == null) {
            Log.w(TAG, "LocationManager unavailable");
            return;
        }

        registerDataItems();
        requestLocationUpdates();
    }

    @MainThread
    public void stop() {
        if (!active || locationManager == null) {
            return;
        }
        locationManager.removeUpdates(this);
        active = false;

        // Remove GPS fields from PidPvs so they disappear from Live Data
        unregisterDataItems();
    }

    private void unregisterDataItems() {
        DebugLog.d(TAG, "Unregistering " + registeredKeys.size() + " GPS fields from PidPvs");
        for (String key : registeredKeys) {
            ObdProt.PidPvs.remove(key);
        }

        // Clear local tracking
        items.clear();
        dataPvs.clear();
        registeredKeys.clear();

        DebugLog.d(TAG, "GPS fields removed from PidPvs");
    }

    private void registerDataItems() {
        for (GpsField field : GpsField.values()) {
            if (items.containsKey(field)) {
                continue;
            }

            int pid = CUSTOM_PID_BASE + field.ordinal();
            Conversion[] conversions = createIdentityConversions(field.units);

            // Use custom CSV label if available, otherwise fall back to string resource
            String description;
            TelemetryFieldLoader.FieldDefinition customField = customFields.get(field.mnemonic.toLowerCase());
            if (customField != null) {
                description = customField.label;
                DebugLog.d(TAG, "Using custom label for " + field.mnemonic + ": " + description);
            } else {
                description = appContext.getString(field.labelResId);
            }

            EcuDataItem item = new EcuDataItem(
                pid,
                0,
                0,
                0,
                32,
                0xFFFFFFFFL,
                conversions,
                field.formatPattern,
                field.min,
                field.max,
                0,
                description,
                field.mnemonic
            );

            // Seed PV defaults
            item.pv.put(EcuDataPv.FID_VALUE, Double.valueOf(0d));
            item.pv.put(EcuDataPv.FID_UNITS, field.units);
            item.pv.put(EcuDataPv.FID_FORMAT, field.formatPattern);
            item.pv.put(EcuDataPv.FID_DESCRIPT, description);

            EcuDataItems.byMnemonic.put(field.mnemonic, item);
            ObdProt.dataItems.appendItemToService(SERVICE, item);
            String key = item.toString();
            registeredKeys.add(key);
            ObdProt.PidPvs.putTyped(key, item.pv);

            items.put(field, item);
            dataPvs.put(field, item.pv);

            DebugLog.d(TAG, "Registered GPS field: " + field.mnemonic + " with key: " + key);
        }
        DebugLog.d(TAG, "Total GPS fields registered: " + registeredKeys.size());
        ensureLiveDataPreferences();
    }

    private static Conversion[] createIdentityConversions(String units) {
        Conversion metric = new Conversions.Linear(1, 1, 0, 0, units);
        Conversion imperial = new Conversions.Linear(1, 1, 0, 0, units);
        return new Conversion[]{metric, imperial};
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdates() {
        boolean fineGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
            == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (!fineGranted && !coarseGranted) {
            Log.w(TAG, "Location permission not granted");
            return;
        }

        active = true;

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                this,
                Looper.getMainLooper()
            );
        } catch (SecurityException ex) {
            Log.e(TAG, "Unable to request GPS provider updates", ex);
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                2000L,
                0f,
                this,
                Looper.getMainLooper()
            );
        } catch (IllegalArgumentException ignored) {
            // Network provider not available, ignore.
        } catch (SecurityException ex) {
            Log.e(TAG, "Unable to request network provider updates", ex);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (!active) {
            return;
        }
        DebugLog.d(TAG, "GPS location update: lat=" + location.getLatitude() + ", lon=" + location.getLongitude());
        updateField(GpsField.LATITUDE, location.getLatitude());
        updateField(GpsField.LONGITUDE, location.getLongitude());
        updateField(GpsField.ALTITUDE, location.hasAltitude() ? location.getAltitude() : null);
        updateField(GpsField.BEARING, location.hasBearing() ? (double) normaliseBearing(location.getBearing()) : null);
        double speedKmh = location.hasSpeed() ? location.getSpeed() * 3.6 : Double.NaN;
        updateField(GpsField.SPEED, Double.isNaN(speedKmh) ? null : speedKmh);

        // Trigger change notification so LiveDataActivity picks up the updates
        notifyDataChanged();
    }

    @Override public void onProviderEnabled(@NonNull String provider) { }
    @Override public void onProviderDisabled(@NonNull String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    private double normaliseBearing(float bearing) {
        if (bearing < 0f) {
            return (bearing % 360f) + 360f;
        }
        return bearing % 360f;
    }

    private void updateField(GpsField field, @Nullable Double value) {
        EcuDataPv pv = dataPvs.get(field);
        if (pv == null) {
            Log.w(TAG, "updateField: PV is null for field " + field.mnemonic);
            return;
        }
        if (value == null) {
            return;
        }
        double clipped = clamp(value, field.min, field.max);

        // Get old value to check if it changed
        Object oldValue = pv.get(EcuDataPv.FID_VALUE);

        pv.put(EcuDataPv.FID_VALUE, Double.valueOf(clipped));
        pv.put(EcuDataPv.FID_FORMAT, field.formatPattern);
        pv.put(EcuDataPv.FID_DESCRIPT, appContext.getString(field.labelResId));
        pv.put(EcuDataPv.FID_UNITS, field.units);

        DebugLog.d(TAG, "Updated " + field.mnemonic + ": " + oldValue + " -> " + clipped);
    }

    /**
     * Notify observers that GPS data has changed
     * This triggers PvChangeEvent.PV_MODIFIED for all GPS fields
     */
    private void notifyDataChanged() {
        // Trigger change events for all registered GPS fields
        for (Map.Entry<GpsField, EcuDataItem> entry : items.entrySet()) {
            String key = entry.getValue().toString();
            EcuDataPv pv = entry.getValue().pv;
            // Use put with PV_MODIFIED action to trigger change listeners
            ObdProt.PidPvs.put(key, pv, com.obddroid.common.ProcessVariables.PvChangeEvent.PV_MODIFIED);
        }
    }

    private double clamp(double value, double min, double max) {
        if (min != Double.NEGATIVE_INFINITY && value < min) {
            return min;
        }
        if (max != Double.POSITIVE_INFINITY && value > max) {
            return max;
        }
        return value;
    }

    private void ensureLiveDataPreferences() {
        // Add GPS fields to selected PIDs preference so they appear in Live Data
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        Set<String> selectedPids = prefs.getStringSet(SettingsActivity.KEY_DATA_ITEMS, null);

        // Only update if user has a saved selection (if null/empty, adapter shows all by default)
        if (selectedPids != null && !selectedPids.isEmpty()) {
            // Make a mutable copy
            Set<String> updatedPids = new HashSet<>(selectedPids);

            // Add all GPS field keys
            for (String key : registeredKeys) {
                if (!updatedPids.contains(key)) {
                    updatedPids.add(key);
                    DebugLog.d(TAG, "Added GPS field to preferences: " + key);
                }
            }

            // Save updated selection
            prefs.edit().putStringSet(SettingsActivity.KEY_DATA_ITEMS, updatedPids).apply();
            DebugLog.d(TAG, "GPS fields added to selected PIDs preference");
        }

        DebugLog.d(TAG, "GPS fields registered in PidPvs: " + registeredKeys.size());
    }

    private enum GpsField {
        LATITUDE("GPS_LATITUDE", R.string.gps_field_latitude, "°", -90d, 90d, "%.6f"),
        LONGITUDE("GPS_LONGITUDE", R.string.gps_field_longitude, "°", -180d, 180d, "%.6f"),
        ALTITUDE("GPS_ALTITUDE", R.string.gps_field_altitude, "m", -500d, 10000d, "%.1f"),
        BEARING("GPS_BEARING", R.string.gps_field_bearing, "°", 0d, 360d, "%.1f"),
        SPEED("GPS_SPEED", R.string.gps_field_speed, "km/h", 0d, 300d, "%.1f");

        final String mnemonic;
        final int labelResId;
        final String units;
        final Double min;
        final Double max;
        final String formatPattern;

        GpsField(String mnemonic, int labelResId, String units, Double min, Double max, String formatPattern) {
            this.mnemonic = mnemonic;
            this.labelResId = labelResId;
            this.units = units;
            this.min = min;
            this.max = max;
            this.formatPattern = formatPattern;
        }
    }
}
