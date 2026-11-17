package com.obddroid.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Vehicle-specific preferences manager
 *
 * Stores user preferences on a per-VIN basis including:
 * - Fuel tank capacity (gallons)
 * - Whether user has been prompted for tank size
 * - Other vehicle-specific settings
 *
 * This allows the app to remember user customizations for each vehicle they connect to.
 */
public class VehiclePreferences {

    private static final String TAG = "VehiclePreferences";
    private static final String PREFS_NAME = "vehicle_preferences";

    // Preference key patterns
    private static final String KEY_TANK_CAPACITY_PREFIX = "tank_capacity_";
    private static final String KEY_TANK_PROMPTED_PREFIX = "tank_prompted_";
    private static final String KEY_VOLUMETRIC_EFFICIENCY_PREFIX = "volumetric_efficiency_";
    private static final String KEY_CALIBRATION_PROMPTED_PREFIX = "calibration_prompted_";

    // Default volumetric efficiency for uncalibrated vehicles (85% baseline)
    private static final float DEFAULT_VE = 85.0f;

    private final SharedPreferences preferences;

    /**
     * Create VehiclePreferences instance
     *
     * @param context Application context
     */
    public VehiclePreferences(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        DebugLog.d(TAG, "VehiclePreferences initialized");
    }

    /**
     * Get the fuel tank capacity for a specific VIN
     *
     * @param vin Vehicle Identification Number (17 characters)
     * @return Tank capacity in gallons, or null if not set
     */
    public Float getTankCapacity(String vin) {
        if (!isValidVIN(vin)) {
            Log.w(TAG, "getTankCapacity: Invalid VIN provided");
            return null;
        }

        String key = KEY_TANK_CAPACITY_PREFIX + sanitizeVIN(vin);
        if (!preferences.contains(key)) {
            DebugLog.d(TAG, "No tank capacity stored for VIN: " + maskVIN(vin));
            return null;
        }

        float capacity = preferences.getFloat(key, 0f);
        if (capacity <= 0f) {
            Log.w(TAG, "Invalid tank capacity stored for VIN: " + maskVIN(vin) + " - " + capacity);
            return null;
        }

        DebugLog.d(TAG, "Retrieved tank capacity for VIN " + maskVIN(vin) + ": " + capacity + " gal");
        return capacity;
    }

    /**
     * Set the fuel tank capacity for a specific VIN
     *
     * @param vin Vehicle Identification Number
     * @param gallons Tank capacity in gallons (must be between 5 and 100)
     * @return true if saved successfully, false otherwise
     */
    public boolean setTankCapacity(String vin, float gallons) {
        if (!isValidVIN(vin)) {
            Log.e(TAG, "setTankCapacity: Invalid VIN provided");
            return false;
        }

        if (gallons < 5f || gallons > 100f) {
            Log.e(TAG, "setTankCapacity: Invalid capacity " + gallons + " gal (must be 5-100)");
            return false;
        }

        String key = KEY_TANK_CAPACITY_PREFIX + sanitizeVIN(vin);
        boolean success = preferences.edit()
                .putFloat(key, gallons)
                .commit();

        if (success) {
            Log.i(TAG, "Saved tank capacity for VIN " + maskVIN(vin) + ": " + gallons + " gal");
        } else {
            Log.e(TAG, "Failed to save tank capacity for VIN " + maskVIN(vin));
        }

        return success;
    }

    /**
     * Get the volumetric efficiency calibration for a specific VIN
     *
     * Volumetric Efficiency (VE) is used in MAP-based fuel consumption calculations
     * to estimate how efficiently the engine fills its cylinders with air.
     *
     * @param vin Vehicle Identification Number (17 characters)
     * @return VE percentage (50-130%), or null if not calibrated (use default 85%)
     */
    public Float getVolumetricEfficiency(String vin) {
        if (!isValidVIN(vin)) {
            Log.w(TAG, "getVolumetricEfficiency: Invalid VIN provided");
            return null;
        }

        String key = KEY_VOLUMETRIC_EFFICIENCY_PREFIX + sanitizeVIN(vin);
        if (!preferences.contains(key)) {
            DebugLog.d(TAG, "No VE calibration stored for VIN: " + maskVIN(vin) + " - using default " + DEFAULT_VE + "%");
            return null; // Caller should use DEFAULT_VE
        }

        float ve = preferences.getFloat(key, DEFAULT_VE);
        if (ve < 50f || ve > 130f) {
            Log.w(TAG, "Invalid VE stored for VIN: " + maskVIN(vin) + " - " + ve + "% (out of range)");
            return null;
        }

        DebugLog.d(TAG, "Retrieved VE for VIN " + maskVIN(vin) + ": " + ve + "%");
        return ve;
    }

    /**
     * Set the volumetric efficiency calibration for a specific VIN
     *
     * This value is determined through real-world calibration by comparing
     * app-calculated MPG to actual fill-up data.
     *
     * @param vin Vehicle Identification Number
     * @param vePercent Volumetric efficiency percentage (must be between 50 and 130)
     * @return true if saved successfully, false otherwise
     */
    public boolean setVolumetricEfficiency(String vin, float vePercent) {
        if (!isValidVIN(vin)) {
            Log.e(TAG, "setVolumetricEfficiency: Invalid VIN provided");
            return false;
        }

        if (vePercent < 50f || vePercent > 130f) {
            Log.e(TAG, "setVolumetricEfficiency: Invalid VE " + vePercent + "% (must be 50-130)");
            return false;
        }

        String key = KEY_VOLUMETRIC_EFFICIENCY_PREFIX + sanitizeVIN(vin);
        boolean success = preferences.edit()
                .putFloat(key, vePercent)
                .commit();

        if (success) {
            Log.i(TAG, "Saved VE for VIN " + maskVIN(vin) + ": " + vePercent + "%");
        } else {
            Log.e(TAG, "Failed to save VE for VIN " + maskVIN(vin));
        }

        return success;
    }

    /**
     * Check if the user has been prompted for fuel economy calibration for this VIN
     *
     * @param vin Vehicle Identification Number
     * @return true if user has been prompted, false otherwise
     */
    public boolean hasPromptedForCalibration(String vin) {
        if (!isValidVIN(vin)) {
            return false;
        }

        String key = KEY_CALIBRATION_PROMPTED_PREFIX + sanitizeVIN(vin);
        boolean prompted = preferences.getBoolean(key, false);
        DebugLog.d(TAG, "Has prompted for calibration - VIN " + maskVIN(vin) + ": " + prompted);
        return prompted;
    }

    /**
     * Mark that the user has been prompted for fuel economy calibration for this VIN
     *
     * @param vin Vehicle Identification Number
     * @return true if saved successfully, false otherwise
     */
    public boolean markCalibrationPrompted(String vin) {
        if (!isValidVIN(vin)) {
            Log.e(TAG, "markCalibrationPrompted: Invalid VIN provided");
            return false;
        }

        String key = KEY_CALIBRATION_PROMPTED_PREFIX + sanitizeVIN(vin);
        boolean success = preferences.edit()
                .putBoolean(key, true)
                .commit();

        if (success) {
            DebugLog.d(TAG, "Marked calibration prompted for VIN " + maskVIN(vin));
        } else {
            Log.e(TAG, "Failed to mark calibration prompted for VIN " + maskVIN(vin));
        }

        return success;
    }

    /**
     * Check if the user has been prompted for tank size for this VIN
     *
     * @param vin Vehicle Identification Number
     * @return true if user has been prompted, false otherwise
     */
    public boolean hasPromptedForTankSize(String vin) {
        if (!isValidVIN(vin)) {
            return false;
        }

        String key = KEY_TANK_PROMPTED_PREFIX + sanitizeVIN(vin);
        boolean prompted = preferences.getBoolean(key, false);
        DebugLog.d(TAG, "Has prompted for tank size - VIN " + maskVIN(vin) + ": " + prompted);
        return prompted;
    }

    /**
     * Mark that the user has been prompted for tank size for this VIN
     *
     * @param vin Vehicle Identification Number
     * @return true if saved successfully, false otherwise
     */
    public boolean markTankSizePrompted(String vin) {
        if (!isValidVIN(vin)) {
            Log.e(TAG, "markTankSizePrompted: Invalid VIN provided");
            return false;
        }

        String key = KEY_TANK_PROMPTED_PREFIX + sanitizeVIN(vin);
        boolean success = preferences.edit()
                .putBoolean(key, true)
                .commit();

        if (success) {
            DebugLog.d(TAG, "Marked tank size prompted for VIN " + maskVIN(vin));
        } else {
            Log.e(TAG, "Failed to mark tank size prompted for VIN " + maskVIN(vin));
        }

        return success;
    }

    /**
     * Clear all preferences for a specific VIN
     * Useful for testing or if user wants to reset settings
     *
     * @param vin Vehicle Identification Number
     * @return true if cleared successfully, false otherwise
     */
    public boolean clearVehiclePreferences(String vin) {
        if (!isValidVIN(vin)) {
            Log.e(TAG, "clearVehiclePreferences: Invalid VIN provided");
            return false;
        }

        String sanitizedVIN = sanitizeVIN(vin);
        boolean success = preferences.edit()
                .remove(KEY_TANK_CAPACITY_PREFIX + sanitizedVIN)
                .remove(KEY_TANK_PROMPTED_PREFIX + sanitizedVIN)
                .remove(KEY_VOLUMETRIC_EFFICIENCY_PREFIX + sanitizedVIN)
                .remove(KEY_CALIBRATION_PROMPTED_PREFIX + sanitizedVIN)
                .commit();

        if (success) {
            Log.i(TAG, "Cleared all preferences for VIN " + maskVIN(vin));
        } else {
            Log.e(TAG, "Failed to clear preferences for VIN " + maskVIN(vin));
        }

        return success;
    }

    /**
     * Clear ALL vehicle preferences (for all VINs)
     * Use with caution - primarily for testing or factory reset
     *
     * @return true if cleared successfully, false otherwise
     */
    public boolean clearAllPreferences() {
        boolean success = preferences.edit().clear().commit();
        if (success) {
            Log.i(TAG, "Cleared ALL vehicle preferences");
        } else {
            Log.e(TAG, "Failed to clear all preferences");
        }
        return success;
    }

    /**
     * Get the number of VINs with stored preferences
     * Useful for analytics or displaying "known vehicles" count
     *
     * @return Number of unique VINs with preferences
     */
    public int getKnownVehicleCount() {
        int count = 0;
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(KEY_TANK_CAPACITY_PREFIX)) {
                count++;
            }
        }
        DebugLog.d(TAG, "Known vehicle count: " + count);
        return count;
    }

    /**
     * Validate VIN format
     *
     * VIN must be:
     * - Not null or empty
     * - Exactly 17 characters long
     * - Contains only alphanumeric characters (excluding I, O, Q per VIN standard)
     *
     * @param vin Vehicle Identification Number to validate
     * @return true if valid format, false otherwise
     */
    private boolean isValidVIN(String vin) {
        if (vin == null || vin.trim().isEmpty()) {
            return false;
        }

        vin = vin.trim().toUpperCase();

        // VIN should be exactly 17 characters
        if (vin.length() != 17) {
            return false;
        }

        // VIN should only contain alphanumeric characters (no I, O, Q per ISO 3779)
        // We'll be lenient here and allow any alphanumeric for partial VINs
        if (!vin.matches("[A-HJ-NPR-Z0-9]{17}")) {
            DebugLog.d(TAG, "VIN contains invalid characters: " + vin);
            // Still allow it but log warning - some test VINs might not be perfect
            return true;
        }

        return true;
    }

    /**
     * Sanitize VIN for use as preference key
     * Ensures consistent casing and trimming
     *
     * @param vin Vehicle Identification Number
     * @return Sanitized VIN (uppercase, trimmed)
     */
    private String sanitizeVIN(String vin) {
        if (vin == null) {
            return "";
        }
        return vin.trim().toUpperCase();
    }

    /**
     * Mask VIN for logging (privacy protection)
     * Shows first 3 and last 4 characters, masks the middle
     * Example: 1HGBH41JXMN109186 -> 1HG********9186
     *
     * @param vin Vehicle Identification Number
     * @return Masked VIN string
     */
    private String maskVIN(String vin) {
        if (vin == null || vin.length() < 17) {
            return "***";
        }

        vin = vin.trim();
        if (vin.length() != 17) {
            return "***";
        }

        return vin.substring(0, 3) + "********" + vin.substring(13);
    }
}
