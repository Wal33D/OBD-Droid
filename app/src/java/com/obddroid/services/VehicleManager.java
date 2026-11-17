package com.obddroid.services;

import android.content.Context;
import android.util.Log;
import com.obddroid.obd.ElmProt;
import com.obddroid.utils.VINDecoder;
import com.obddroid.utils.VehicleData;
import com.obddroid.utils.DebugLog;
import java.util.ArrayList;
import java.util.List;

/**
 * Global Vehicle Manager - Single source of truth for connected vehicle information
 *
 * This singleton manages:
 * - Current VIN and decoded vehicle data
 * - Notifying all listeners when vehicle info changes
 * - Caching decoded VINs
 * - Providing vehicle info to any component in the app
 *
 * Architecture: This decouples VIN decoding from UI components
 */
public class VehicleManager {

    private static final String TAG = "VehicleManager";
    private static VehicleManager instance;

    // Current vehicle state
    private String currentVIN = null;
    private VehicleData currentVehicleData = null;
    private boolean isDecoding = false;
    private ElmProt.STAT ecuConnectionState = ElmProt.STAT.UNDEFINED;
    private boolean ecuSelected = false;
    private boolean vinRetrievalAttempted = false;
    private boolean vinRetrievalFailed = false;

    // Listeners for vehicle changes
    private final List<VehicleChangeListener> listeners = new ArrayList<>();

    // VIN decoder (tries online NHTSA, falls back to offline)
    private VINDecoder vinDecoder;

    /**
     * Listener interface for vehicle changes
     */
    public interface VehicleChangeListener {
        void onVINChanged(String vin);
        void onVehicleDecoded(VehicleData vehicleData);
        void onVehicleDisconnected();
        void onECUConnectionChanged(ElmProt.STAT state);
        void onVINRetrievalFailed();
        void onDecodingStarted();
        void onDecodingError(String error);
    }

    /**
     * Simple listener adapter - implement only what you need
     */
    public static class SimpleVehicleChangeListener implements VehicleChangeListener {
        @Override public void onVINChanged(String vin) {}
        @Override public void onVehicleDecoded(VehicleData vehicleData) {}
        @Override public void onVehicleDisconnected() {}
        @Override public void onECUConnectionChanged(ElmProt.STAT state) {}
        @Override public void onVINRetrievalFailed() {}
        @Override public void onDecodingStarted() {}
        @Override public void onDecodingError(String error) {}
    }

    private VehicleManager() {
        // Decoder will be initialized when context is provided
    }

    /**
     * Initialize with context (call this first from Application or main Activity)
     */
    public static synchronized VehicleManager getInstance(Context context) {
        if (instance == null) {
            instance = new VehicleManager();
        }
        // Initialize enhanced decoder if not already done
        if (instance.vinDecoder == null && context != null) {
            instance.vinDecoder = new VINDecoder(context.getApplicationContext());
            DebugLog.d(TAG, "VIN Decoder initialized (parallel race strategy)");
        }
        return instance;
    }

    /**
     * Get singleton instance (must call getInstance(context) first)
     */
    public static synchronized VehicleManager getInstance() {
        if (instance == null) {
            instance = new VehicleManager();
        }
        return instance;
    }

    /**
     * Pre-warm the VIN database on startup for instant decoding
     * This runs in background and ensures the 66MB database is ready
     */
    public void prewarmDatabase() {
        if (vinDecoder != null) {
            DebugLog.d(TAG, "Pre-warming VIN database...");
            vinDecoder.prewarmDatabase();
        }
    }

    /**
     * Register a listener for vehicle changes
     */
    public void addListener(VehicleChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);

            // Immediately notify of current state
            if (currentVehicleData != null) {
                listener.onVehicleDecoded(currentVehicleData);
            } else if (currentVIN != null) {
                listener.onVINChanged(currentVIN);
                if (isDecoding) {
                    listener.onDecodingStarted();
                }
            }
        }
    }

    /**
     * Unregister a listener
     */
    public void removeListener(VehicleChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Set the current VIN (called when VIN is read from OBD)
     * This will automatically trigger decoding
     */
    public void setVIN(String vin) {
        if (vin == null || vin.trim().isEmpty()) {
            // VIN retrieval failed (Mode 9 not supported)
            vinRetrievalAttempted = true;
            vinRetrievalFailed = true;

            // Notify listeners of failure
            for (VehicleChangeListener listener : listeners) {
                listener.onVINRetrievalFailed();
            }
            return;
        }

        vin = vin.trim().toUpperCase();
        vinRetrievalAttempted = true;
        vinRetrievalFailed = false;

        // Check if VIN actually changed
        if (vin.equals(currentVIN)) {
            DebugLog.d(TAG, "VIN unchanged: " + vin);
            return;
        }

        DebugLog.d(TAG, "New VIN detected: " + vin);
        currentVIN = vin;
        isDecoding = true;

        // Notify listeners of VIN change
        for (VehicleChangeListener listener : listeners) {
            listener.onVINChanged(vin);
            listener.onDecodingStarted();
        }

        // Decode using enhanced decoder (tries online, falls back to offline)
        if (vinDecoder != null) {
            final String vinToDecode = vin;
            vinDecoder.decodeAsync(vinToDecode, new VINDecoder.DecodeCallback() {
                @Override
                public void onSuccess(VehicleData vehicleData) {
                    DebugLog.d(TAG, "VIN decode successful: " + vehicleData.getDisplayName());
                    handleDecodedVehicle(vehicleData);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "VIN decode failed: " + error);
                    handleDecodingError(error);
                }
            });
        } else {
            // Fallback if decoder not initialized
            Log.e(TAG, "VIN Decoder not initialized - call getInstance(context) first");
            handleDecodingError("VIN Decoder not initialized");
        }
    }

    /**
     * Handle successfully decoded vehicle
     */
    private void handleDecodedVehicle(VehicleData vehicleData) {
        DebugLog.d(TAG, "Vehicle decoded: " + vehicleData.getDisplayName());
        currentVehicleData = vehicleData;
        isDecoding = false;

        // Notify all listeners
        for (VehicleChangeListener listener : listeners) {
            listener.onVehicleDecoded(vehicleData);
        }
    }

    /**
     * Handle decoding error
     */
    private void handleDecodingError(String error) {
        Log.e(TAG, "Failed to decode VIN: " + error);
        isDecoding = false;
        currentVehicleData = null;

        // Notify listeners of error
        for (VehicleChangeListener listener : listeners) {
            listener.onDecodingError("Unable to decode VIN: " + error);
        }
    }

    /**
     * Clear current vehicle (called on disconnect)
     */
    public void clearVehicle() {
        DebugLog.d(TAG, "Clearing vehicle data");
        currentVIN = null;
        currentVehicleData = null;
        isDecoding = false;
        ecuSelected = false;
        vinRetrievalAttempted = false;
        vinRetrievalFailed = false;
        // Don't reset ECU state here - it's managed separately

        // Notify all listeners
        for (VehicleChangeListener listener : listeners) {
            listener.onVehicleDisconnected();
        }
    }

    /**
     * Clear VIN cache to force re-read from vehicle
     * Used by refresh functionality
     */
    public void clearVINCache() {
        DebugLog.d(TAG, "Clearing VIN cache for refresh");
        currentVIN = null;
        currentVehicleData = null;
        isDecoding = false;
        vinRetrievalAttempted = false;
        vinRetrievalFailed = false;
        // Don't notify disconnected - we're just refreshing
        // ECU state remains connected
    }

    /**
     * Handle VIN retrieval timeout
     */
    public void handleVINTimeout() {
        DebugLog.d(TAG, "VIN retrieval timed out");
        vinRetrievalAttempted = true;
        // DON'T mark as permanently failed - allow manual retry via vehicle info page
        // vinRetrievalFailed = true;
        isDecoding = false;

        // Notify listeners that decoding failed
        for (VehicleChangeListener listener : listeners) {
            listener.onDecodingError("VIN retrieval timed out - you can manually view vehicle info");
        }
    }

    /**
     * Update ECU connection state
     */
    public void setECUConnectionState(ElmProt.STAT state) {
        DebugLog.d(TAG, "ECU connection state changed: " + state);
        ElmProt.STAT oldState = ecuConnectionState;
        ecuConnectionState = state;

        // Don't notify about ECU connection until actually selected
        // ECU_DETECTED means ECUs are found but not selected yet
        if (state != ElmProt.STAT.ECU_DETECTED) {
            // Notify all listeners
            for (VehicleChangeListener listener : listeners) {
                listener.onECUConnectionChanged(state);
            }
        }

        // If ECU disconnected or no longer detected, clear selection state
        if (state == ElmProt.STAT.UNDEFINED ||
            state == ElmProt.STAT.DISCONNECTED ||
            state == ElmProt.STAT.STOPPED ||
            state == ElmProt.STAT.NODATA) {
            ecuSelected = false;
            vinRetrievalAttempted = false;
            vinRetrievalFailed = false;
        }

        // If going from connected/initialized back to initializing, we're reconnecting
        if ((oldState == ElmProt.STAT.CONNECTED || oldState == ElmProt.STAT.INITIALIZED) &&
            state == ElmProt.STAT.INITIALIZING) {
            // Clear vehicle data for new connection
            clearVehicle();
        }
    }

    /**
     * Get current ECU connection state
     */
    public ElmProt.STAT getECUConnectionState() {
        return ecuConnectionState;
    }

    /**
     * Mark that ECU has been selected by user
     */
    public void setECUSelected(boolean selected) {
        DebugLog.d(TAG, "ECU selected: " + selected);
        ecuSelected = selected;
        vinRetrievalAttempted = false;
        vinRetrievalFailed = false;

        if (selected) {
            // Now notify listeners that ECU is truly connected
            // But don't assume Mode 9 failure yet
            for (VehicleChangeListener listener : listeners) {
                listener.onECUConnectionChanged(ElmProt.STAT.CONNECTED);
            }
        }
    }

    /**
     * Check if VIN retrieval has been attempted and failed
     */
    public boolean hasVINRetrievalFailed() {
        return vinRetrievalAttempted && vinRetrievalFailed;
    }

    /**
     * Check if ECU is connected (even without VIN)
     */
    public boolean isECUConnected() {
        // Only consider connected if ECU is actually selected
        return ecuSelected && (ecuConnectionState == ElmProt.STAT.ECU_DETECTED ||
                               ecuConnectionState == ElmProt.STAT.CONNECTED);
    }

    /**
     * Get current VIN
     */
    public String getCurrentVIN() {
        return currentVIN;
    }

    /**
     * Get current decoded vehicle data
     */
    public VehicleData getCurrentVehicleData() {
        return currentVehicleData;
    }

    /**
     * Check if currently decoding
     */
    public boolean isDecoding() {
        return isDecoding;
    }

    /**
     * Check if vehicle is connected (has VIN)
     */
    public boolean isVehicleConnected() {
        return currentVIN != null;
    }

    /**
     * Get display name for current vehicle
     */
    public String getVehicleDisplayName() {
        if (currentVehicleData != null) {
            return currentVehicleData.getDisplayName();
        } else if (currentVIN != null) {
            return currentVIN;
        }
        return "No Vehicle";
    }

    /**
     * Get manufacturer name (for icon/avatar)
     */
    public String getManufacturer() {
        if (currentVehicleData != null) {
            if (currentVehicleData.make != null && !currentVehicleData.make.isEmpty()) {
                return currentVehicleData.make;
            }
            if (currentVehicleData.manufacturer != null) {
                return currentVehicleData.manufacturer;
            }
        }
        return null;
    }

    /**
     * Get manufacturer initial for avatar
     */
    public String getManufacturerInitial() {
        String manufacturer = getManufacturer();
        if (manufacturer != null && !manufacturer.isEmpty()) {
            return manufacturer.substring(0, 1).toUpperCase();
        }
        return "?";
    }

    /**
     * Validate a VIN format (without decoding)
     */
    public boolean validateVIN(String vin) {
        if (vinDecoder != null) {
            return vinDecoder.validate(vin);
        }
        return false;
    }

    /**
     * Force offline decoding (useful when no internet)
     */
    public void decodeVINOffline(String vin) {
        if (vin == null || vin.trim().isEmpty()) {
            return;
        }

        vin = vin.trim().toUpperCase();

        // Check if VIN actually changed
        if (vin.equals(currentVIN) && currentVehicleData != null) {
            DebugLog.d(TAG, "VIN unchanged and already decoded: " + vin);
            return;
        }

        DebugLog.d(TAG, "Forcing offline VIN decode: " + vin);
        currentVIN = vin;
        isDecoding = true;

        // Notify listeners of VIN change
        for (VehicleChangeListener listener : listeners) {
            listener.onVINChanged(vin);
            listener.onDecodingStarted();
        }

        if (vinDecoder != null) {
            VehicleData offlineData = vinDecoder.decode(vin);
            if (offlineData != null && offlineData.getMake() != null) {
                offlineData.setMessage("Decoded offline");
                handleDecodedVehicle(offlineData);
            } else {
                handleDecodingError("Offline decode failed");
            }
        } else {
            handleDecodingError("VIN Decoder not initialized");
        }
    }

    /**
     * Get quick manufacturer info without full decode
     */
    public String getQuickManufacturer(String vin) {
        if (vinDecoder != null) {
            return vinDecoder.getManufacturer(vin);
        }
        return null;
    }

    /**
     * Estimate fuel tank capacity based on vehicle characteristics
     *
     * Uses vehicle class, body style, and other attributes to provide
     * an intelligent estimate of tank capacity in gallons.
     *
     * @param vehicleData Decoded vehicle information
     * @return Estimated tank capacity in gallons
     */
    public float estimateTankCapacity(VehicleData vehicleData) {
        if (vehicleData == null) {
            DebugLog.d(TAG, "No vehicle data available for tank estimation, using default");
            return 16.0f; // Generic default
        }

        float baseCapacity = estimateBaseCapacityFromClass(vehicleData);

        // Apply adjustments based on vehicle characteristics
        float adjustedCapacity = applyCapacityAdjustments(baseCapacity, vehicleData);

        // Ensure capacity is within reasonable bounds
        adjustedCapacity = Math.max(10.0f, Math.min(adjustedCapacity, 40.0f));

        Log.i(TAG, String.format("Estimated tank capacity for %s: %.1f gal",
                vehicleData.getDisplayName(), adjustedCapacity));

        return adjustedCapacity;
    }

    /**
     * Estimate base tank capacity from vehicle class/body type
     */
    private float estimateBaseCapacityFromClass(VehicleData vehicleData) {
        String bodyClass = vehicleData.bodyClass;
        String vehicleType = vehicleData.vehicleType;

        if (bodyClass == null || bodyClass.isEmpty()) {
            bodyClass = "";
        }
        if (vehicleType == null || vehicleType.isEmpty()) {
            vehicleType = "";
        }

        bodyClass = bodyClass.toLowerCase();
        vehicleType = vehicleType.toLowerCase();

        // Pickup Trucks - Largest tanks
        if (bodyClass.contains("pickup") || vehicleType.contains("truck")) {
            DebugLog.d(TAG, "Detected pickup truck - base: 26 gal");
            return 26.0f; // Pickup trucks: 26-36 gallons
        }

        // Large SUVs
        if (bodyClass.contains("sport utility") || bodyClass.contains("suv")) {
            // Check if it's a large/luxury SUV
            if (isLargeSUV(vehicleData)) {
                DebugLog.d(TAG, "Detected large SUV - base: 23 gal");
                return 23.0f; // Large SUVs: 22-26 gallons
            } else {
                DebugLog.d(TAG, "Detected midsize SUV - base: 19 gal");
                return 19.0f; // Midsize SUVs/Crossovers: 18-20 gallons
            }
        }

        // Crossovers (often classified as wagon or hatchback)
        if (bodyClass.contains("crossover") || bodyClass.contains("wagon/suv")) {
            DebugLog.d(TAG, "Detected crossover - base: 17 gal");
            return 17.0f; // Crossovers: 15-18 gallons
        }

        // Vans and Minivans
        if (bodyClass.contains("van") || bodyClass.contains("minivan") ||
            bodyClass.contains("passenger van") || bodyClass.contains("cargo van")) {
            DebugLog.d(TAG, "Detected van - base: 20 gal");
            return 20.0f; // Vans: 18-25 gallons
        }

        // Sedans
        if (bodyClass.contains("sedan")) {
            // Check size classification
            if (bodyClass.contains("large") || bodyClass.contains("full-size")) {
                DebugLog.d(TAG, "Detected large sedan - base: 18 gal");
                return 18.0f; // Full-size sedans: 17-20 gallons
            } else if (bodyClass.contains("mid") || bodyClass.contains("medium")) {
                DebugLog.d(TAG, "Detected midsize sedan - base: 16 gal");
                return 16.0f; // Midsize sedans: 15-18 gallons
            } else if (bodyClass.contains("compact") || bodyClass.contains("small")) {
                DebugLog.d(TAG, "Detected compact sedan - base: 14 gal");
                return 14.0f; // Compact sedans: 13-15 gallons
            } else {
                DebugLog.d(TAG, "Detected sedan (unspecified size) - base: 16 gal");
                return 16.0f; // Generic sedan
            }
        }

        // Coupes
        if (bodyClass.contains("coupe") || bodyClass.contains("2-door")) {
            DebugLog.d(TAG, "Detected coupe - base: 15 gal");
            return 15.0f; // Coupes: 14-17 gallons
        }

        // Hatchbacks
        if (bodyClass.contains("hatchback")) {
            DebugLog.d(TAG, "Detected hatchback - base: 13 gal");
            return 13.0f; // Hatchbacks: 12-15 gallons
        }

        // Convertibles
        if (bodyClass.contains("convertible")) {
            DebugLog.d(TAG, "Detected convertible - base: 15 gal");
            return 15.0f; // Convertibles: 14-17 gallons
        }

        // Wagons
        if (bodyClass.contains("wagon") || bodyClass.contains("estate")) {
            DebugLog.d(TAG, "Detected wagon - base: 17 gal");
            return 17.0f; // Wagons: 16-19 gallons
        }

        // Sports cars and performance vehicles
        if (bodyClass.contains("sport") || vehicleType.contains("sports")) {
            DebugLog.d(TAG, "Detected sports car - base: 16 gal");
            return 16.0f; // Sports cars: 14-19 gallons
        }

        // Default fallback
        DebugLog.d(TAG, "Unknown body class '" + bodyClass + "' - using default: 16 gal");
        return 16.0f; // Generic default for unknown types
    }

    /**
     * Apply adjustments to base capacity based on vehicle characteristics
     */
    private float applyCapacityAdjustments(float baseCapacity, VehicleData vehicleData) {
        float adjusted = baseCapacity;

        // Luxury brand adjustment (typically larger tanks)
        if (isLuxuryBrand(vehicleData.make)) {
            adjusted += 2.0f;
            DebugLog.d(TAG, "Applied luxury brand adjustment: +2.0 gal");
        }

        // Engine displacement adjustment
        if (vehicleData.displacementL != null && !vehicleData.displacementL.isEmpty()) {
            try {
                float displacement = Float.parseFloat(vehicleData.displacementL);
                if (displacement >= 5.0f) {
                    // Large engines (5.0L+) often paired with larger tanks
                    adjusted += 3.0f;
                    DebugLog.d(TAG, "Applied large engine adjustment: +3.0 gal");
                } else if (displacement >= 3.5f) {
                    // Mid-large engines (3.5L-5.0L)
                    adjusted += 1.5f;
                    DebugLog.d(TAG, "Applied mid-large engine adjustment: +1.5 gal");
                } else if (displacement <= 1.5f) {
                    // Small engines (< 1.5L) often in economy cars
                    adjusted -= 2.0f;
                    DebugLog.d(TAG, "Applied small engine adjustment: -2.0 gal");
                }
            } catch (NumberFormatException e) {
                DebugLog.d(TAG, "Could not parse displacement: " + vehicleData.displacementL);
            }
        }

        // Hybrid/Electric adjustment (smaller fuel tanks)
        if (vehicleData.electrificationLevel != null &&
            !vehicleData.electrificationLevel.isEmpty() &&
            !vehicleData.electrificationLevel.equalsIgnoreCase("Not Applicable")) {

            String level = vehicleData.electrificationLevel.toLowerCase();
            if (level.contains("plug-in hybrid") || level.contains("phev")) {
                adjusted -= 4.0f;
                DebugLog.d(TAG, "Applied PHEV adjustment: -4.0 gal");
            } else if (level.contains("hybrid")) {
                adjusted -= 2.0f;
                DebugLog.d(TAG, "Applied hybrid adjustment: -2.0 gal");
            }
        }

        // AWD/4WD adjustment (typically larger vehicles with bigger tanks)
        if (vehicleData.driveType != null && !vehicleData.driveType.isEmpty()) {
            String driveType = vehicleData.driveType.toLowerCase();
            if (driveType.contains("4wd") || driveType.contains("awd") ||
                driveType.contains("4-wheel") || driveType.contains("all-wheel")) {
                adjusted += 1.0f;
                DebugLog.d(TAG, "Applied AWD/4WD adjustment: +1.0 gal");
            }
        }

        return adjusted;
    }

    /**
     * Determine if this is a large/luxury SUV
     */
    private boolean isLargeSUV(VehicleData vehicleData) {
        // Check model name for large SUV indicators
        String model = vehicleData.model;
        if (model != null) {
            model = model.toLowerCase();
            // Common large SUV names
            if (model.contains("expedition") || model.contains("suburban") ||
                model.contains("tahoe") || model.contains("yukon") ||
                model.contains("navigator") || model.contains("escalade") ||
                model.contains("gls") || model.contains("gle") || // Mercedes large SUVs
                model.contains("gx") || model.contains("lx") || // Lexus large SUVs
                model.contains("x7") || model.contains("x5") || // BMW large SUVs
                model.contains("q7") || model.contains("q8") || // Audi large SUVs
                model.contains("cayenne") || model.contains("range rover") ||
                model.contains("sequoia") || model.contains("land cruiser") ||
                model.contains("armada") || model.contains("qx80") ||
                model.contains("durango")) {
                return true;
            }
        }

        // Check if luxury brand (luxury SUVs tend to be larger)
        if (isLuxuryBrand(vehicleData.make)) {
            return true;
        }

        // Check displacement (large SUVs typically have bigger engines)
        if (vehicleData.displacementL != null && !vehicleData.displacementL.isEmpty()) {
            try {
                float displacement = Float.parseFloat(vehicleData.displacementL);
                if (displacement >= 3.5f) {
                    return true; // Large engine suggests large SUV
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return false;
    }

    /**
     * Check if manufacturer is a luxury brand
     */
    private boolean isLuxuryBrand(String make) {
        if (make == null || make.isEmpty()) {
            return false;
        }

        make = make.toLowerCase();

        return make.contains("mercedes") || make.contains("bmw") ||
               make.contains("audi") || make.contains("lexus") ||
               make.contains("porsche") || make.contains("tesla") ||
               make.contains("cadillac") || make.contains("lincoln") ||
               make.contains("infiniti") || make.contains("acura") ||
               make.contains("jaguar") || make.contains("land rover") ||
               make.contains("maserati") || make.contains("bentley") ||
               make.contains("rolls-royce") || make.contains("aston martin") ||
               make.contains("genesis") || make.contains("alfa romeo") ||
               make.contains("volvo");
    }

    /**
     * Reload VIN database after automatic update
     * Called by DatabaseUpdateManager after successful update
     */
    public void reloadVinDatabase() {
        if (vinDecoder != null) {
            DebugLog.d(TAG, "Reloading VIN database in VehicleManager");
            vinDecoder.reloadDatabase();
        }
    }
}
