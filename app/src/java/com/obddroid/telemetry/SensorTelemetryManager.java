package com.obddroid.telemetry;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.obddroid.R;
import com.obddroid.ecu.Conversion;
import com.obddroid.ecu.EcuDataItem;
import com.obddroid.ecu.EcuDataItems;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.ecu.Conversions;
import com.obddroid.obd.ObdProt;
import com.obddroid.ui.activities.SettingsActivity;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Streams device motion sensor data (accelerometer) into the PID list so it can be graphed,
 * logged, and exported alongside standard OBD telemetry.
 */
public class SensorTelemetryManager implements SensorEventListener {

    private static final String TAG = "SensorTelemetry";
    private static final int SERVICE = ObdProt.OBD_SVC_DATA;
    private static final int CUSTOM_PID_BASE = 0xF120;

    private final Context appContext;
    private final SensorManager sensorManager;
    private final Map<DataField, EcuDataItem> items = new EnumMap<>(DataField.class);
    private final Map<DataField, EcuDataPv> dataPvs = new EnumMap<>(DataField.class);
    private final Set<String> registeredKeys = new HashSet<>();
    private final Map<String, TelemetryFieldLoader.FieldDefinition> customFields;

    private Sensor accelerometer;
    private boolean active = false;

    public SensorTelemetryManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);

        // Load custom field definitions from CSV if available
        this.customFields = TelemetryFieldLoader.loadMotionFields(context);
    }

    public boolean isActive() {
        return active;
    }

    @MainThread
    public void start() {
        if (active) {
            return;
        }
        if (sensorManager == null) {
            Log.w(TAG, "SensorManager unavailable");
            return;
        }
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) {
            Log.w(TAG, "Accelerometer sensor not available on this device");
            return;
        }

        registerDataItems();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        active = true;
    }

    @MainThread
    public void stop() {
        if (!active || sensorManager == null) {
            return;
        }
        sensorManager.unregisterListener(this);
        active = false;

        // Remove sensor fields from PidPvs so they disappear from Live Data
        unregisterDataItems();
    }

    private void unregisterDataItems() {
        Log.d(TAG, "Unregistering " + registeredKeys.size() + " sensor fields from PidPvs");
        for (String key : registeredKeys) {
            ObdProt.PidPvs.remove(key);
        }

        // Clear local tracking
        items.clear();
        dataPvs.clear();
        registeredKeys.clear();

        Log.d(TAG, "Sensor fields removed from PidPvs");
    }

    private void registerDataItems() {
        for (DataField field : DataField.values()) {
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
                Log.d(TAG, "Using custom label for " + field.mnemonic + ": " + description);
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
            item.pv.put(EcuDataPv.FID_VALUE, Double.valueOf(0d));
            item.pv.put(EcuDataPv.FID_UNITS, field.units);
            item.pv.put(EcuDataPv.FID_FORMAT, field.formatPattern);

            EcuDataItems.byMnemonic.put(field.mnemonic, item);
            ObdProt.dataItems.appendItemToService(SERVICE, item);
            String key = item.toString();
            registeredKeys.add(key);
            ObdProt.PidPvs.putTyped(key, item.pv);

            items.put(field, item);
            dataPvs.put(field, item.pv);
        }
        ensureLiveDataPreferences();
    }

    private static Conversion[] createIdentityConversions(String units) {
        Conversion metric = new Conversions.Linear(1, 1, 0, 0, units);
        Conversion imperial = new Conversions.Linear(1, 1, 0, 0, units);
        return new Conversion[]{metric, imperial};
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!active || event.sensor == null || event.values == null || event.values.length < 3) {
            return;
        }
        updateField(DataField.ACC_X, event.values[0]);
        updateField(DataField.ACC_Y, event.values[1]);
        updateField(DataField.ACC_Z, event.values[2]);

        // Trigger change notification so LiveDataActivity picks up the updates
        notifyDataChanged();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }

    private void updateField(DataField field, float value) {
        EcuDataPv pv = dataPvs.get(field);
        if (pv == null) {
            return;
        }
        double clipped = clamp(value, field.min, field.max);
        pv.put(EcuDataPv.FID_VALUE, Double.valueOf(clipped));
        pv.put(EcuDataPv.FID_UNITS, field.units);
        pv.put(EcuDataPv.FID_DESCRIPT, appContext.getString(field.labelResId));
    }

    /**
     * Notify observers that sensor data has changed
     * This triggers PvChangeEvent.PV_MODIFIED for all sensor fields
     */
    private void notifyDataChanged() {
        // Trigger change events for all registered sensor fields
        for (Map.Entry<DataField, EcuDataItem> entry : items.entrySet()) {
            String key = entry.getValue().toString();
            EcuDataPv pv = entry.getValue().pv;
            // Use put with PV_MODIFIED action to trigger change listeners
            ObdProt.PidPvs.put(key, pv, com.obddroid.common.ProcessVariables.PvChangeEvent.PV_MODIFIED);
        }
    }

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private void ensureLiveDataPreferences() {
        // Add sensor fields to selected PIDs preference so they appear in Live Data
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext);
        Set<String> selectedPids = prefs.getStringSet(com.obddroid.ui.activities.SettingsActivity.KEY_DATA_ITEMS, null);

        // Only update if user has a saved selection (if null/empty, adapter shows all by default)
        if (selectedPids != null && !selectedPids.isEmpty()) {
            // Make a mutable copy
            Set<String> updatedPids = new HashSet<>(selectedPids);

            // Add all sensor field keys
            for (String key : registeredKeys) {
                if (!updatedPids.contains(key)) {
                    updatedPids.add(key);
                    Log.d(TAG, "Added sensor field to preferences: " + key);
                }
            }

            // Save updated selection
            prefs.edit().putStringSet(com.obddroid.ui.activities.SettingsActivity.KEY_DATA_ITEMS, updatedPids).apply();
            Log.d(TAG, "Sensor fields added to selected PIDs preference");
        }

        Log.d(TAG, "Sensor fields registered in PidPvs: " + registeredKeys.size());
    }

    private enum DataField {
        ACC_X("ACC_X", R.string.sensor_field_acc_x, "m/s²", -9.81d, 9.81d, "%.2f"),
        ACC_Y("ACC_Y", R.string.sensor_field_acc_y, "m/s²", -9.81d, 9.81d, "%.2f"),
        ACC_Z("ACC_Z", R.string.sensor_field_acc_z, "m/s²", -9.81d, 9.81d, "%.2f");

        final String mnemonic;
        final int labelResId;
        final String units;
        final double min;
        final double max;
        final String formatPattern;

        DataField(String mnemonic, int labelResId, String units, double min, double max, String formatPattern) {
            this.mnemonic = mnemonic;
            this.labelResId = labelResId;
            this.units = units;
            this.min = min;
            this.max = max;
            this.formatPattern = formatPattern;
        }
    }
}
