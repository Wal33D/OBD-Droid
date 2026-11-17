package com.obddroid.custompid;

import android.content.Context;
import android.util.Log;

import com.obddroid.ecu.EcuDataItem;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.ecu.PidDefinition;
import com.obddroid.obd.ObdProt;
import com.obddroid.utils.DebugLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Integration layer between Custom PID system and existing OBD infrastructure
 * Loads custom PIDs from database and converts them to EcuDataItems
 * Integrates directly with ObdProt.PidPvs for live display
 *
 * @author Wal33D
 */
public class CustomPidIntegration {
    private static final String TAG = "CustomPidIntegration";
    public static final String ACTION_CUSTOM_PIDS_CHANGED = "com.obddroid.CUSTOM_PIDS_CHANGED";

    private final CustomPidManager pidManager;
    private final List<EcuDataItem> customDataItems;
    private final List<String> loadedPidKeys;  // Track which PIDs we've loaded

    /**
     * Create integration instance
     *
     * @param context Android context
     */
    public CustomPidIntegration(Context context) {
        this.pidManager = CustomPidManager.getInstance(context);
        this.customDataItems = new ArrayList<>();
        this.loadedPidKeys = new ArrayList<>();
    }

    /**
     * Load custom PIDs for a specific vehicle
     *
     * @param make Vehicle make (e.g., "Subaru", "BMW")
     * @param model Vehicle model (e.g., "WRX", "M3")
     * @param year Vehicle year
     * @return List of EcuDataItems created from custom PIDs
     */
    public List<EcuDataItem> loadCustomPidsForVehicle(String make, String model, int year) {
        customDataItems.clear();

        // Get matching custom PIDs
        List<CustomPid> customPids = pidManager.getPidsForVehicle(make, model, year);

        Log.i(TAG, String.format("Loading %d custom PIDs for %s %s %d",
                customPids.size(), make, model, year));

        // Convert each CustomPid to EcuDataItem
        for (CustomPid customPid : customPids) {
            try {
                if (!customPid.isEnabled()) {
                    continue;  // Skip disabled PIDs
                }

                EcuDataItem dataItem = createDataItem(customPid);
                if (dataItem != null) {
                    customDataItems.add(dataItem);
                    DebugLog.d(TAG, "Loaded custom PID: " + customPid.getName());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load custom PID: " + customPid.getName(), e);
            }
        }

        return new ArrayList<>(customDataItems);
    }

    /**
     * Load all enabled custom PIDs regardless of vehicle
     *
     * @return List of EcuDataItems from enabled custom PIDs
     */
    public List<EcuDataItem> loadAllEnabledPids() {
        customDataItems.clear();

        List<CustomPid> enabledPids = pidManager.getEnabledPids();

        Log.i(TAG, "Loading " + enabledPids.size() + " enabled custom PIDs");

        for (CustomPid customPid : enabledPids) {
            try {
                EcuDataItem dataItem = createDataItem(customPid);
                if (dataItem != null) {
                    customDataItems.add(dataItem);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load custom PID: " + customPid.getName(), e);
            }
        }

        return new ArrayList<>(customDataItems);
    }

    /**
     * Convert a CustomPid to an EcuDataItem
     *
     * @param customPid Custom PID to convert
     * @return EcuDataItem instance or null if conversion fails
     */
    private EcuDataItem createDataItem(CustomPid customPid) {
        // Validate first
        if (!CustomPidAdapter.isValid(customPid)) {
            Log.w(TAG, "Invalid custom PID: " + customPid.getName());
            return null;
        }

        try {
            // Convert to PidDefinition
            PidDefinition pidDef = CustomPidAdapter.toPidDefinition(customPid);

            // Create conversion array
            CustomPidConversion conversion = new CustomPidConversion(customPid);
            com.obddroid.ecu.Conversion[] conversions = new com.obddroid.ecu.Conversion[]{
                    conversion,
                    conversion  // Both metric and imperial use same formula
            };

            // Determine min/max values
            Number minValue = customPid.getMinValue() > 0 ? customPid.getMinValue() : null;
            Number maxValue = customPid.getMaxValue() > 0 ? customPid.getMaxValue() : null;

            // Create EcuDataItem
            EcuDataItem item = new EcuDataItem(
                    pidDef.getPid(),           // PID
                    pidDef.getOffset(),        // Offset
                    pidDef.getLengthBytes(),   // Number of bytes
                    pidDef.getBitOffset(),     // Bit offset
                    pidDef.getBitLength(),     // Number of bits
                    pidDef.getBitMask(),       // Bit mask
                    conversions,               // Conversions
                    pidDef.getFormat(),        // Format string
                    minValue,                  // Min value
                    maxValue,                  // Max value
                    pidDef.getMinUpdatePeriodMs(), // Update period
                    pidDef.getLabel(),         // Label
                    pidDef.getMnemonic()       // Mnemonic
            );

            return item;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create EcuDataItem for: " + customPid.getName(), e);
            return null;
        }
    }

    /**
     * Get currently loaded custom data items
     *
     * @return List of loaded custom EcuDataItems
     */
    public List<EcuDataItem> getLoadedCustomPids() {
        return new ArrayList<>(customDataItems);
    }

    /**
     * Reload custom PIDs (useful after database changes)
     *
     * @param make Vehicle make
     * @param model Vehicle model
     * @param year Vehicle year
     * @return Number of PIDs loaded
     */
    public int reloadForVehicle(String make, String model, int year) {
        List<EcuDataItem> items = loadCustomPidsForVehicle(make, model, year);
        Log.i(TAG, "Reloaded " + items.size() + " custom PIDs");
        return items.size();
    }

    /**
     * Get the custom PID manager
     *
     * @return CustomPidManager instance
     */
    public CustomPidManager getPidManager() {
        return pidManager;
    }

    /**
     * Test evaluation of a custom PID with sample data
     *
     * @param customPid PID to test
     * @param testBytes Test byte values
     * @return Evaluation result string
     */
    public String testCustomPid(CustomPid customPid, int[] testBytes) {
        return pidManager.testFormula(customPid.getFormula(), testBytes);
    }

    /**
     * Check how many custom PIDs are available for a vehicle
     *
     * @param make Vehicle make
     * @param model Vehicle model
     * @param year Vehicle year
     * @return Number of matching PIDs
     */
    public int getAvailableCount(String make, String model, int year) {
        return pidManager.getPidsForVehicle(make, model, year).size();
    }

    /**
     * Get statistics about loaded custom PIDs
     *
     * @return Statistics string
     */
    public String getStatistics() {
        int total = pidManager.getAllPids().size();
        int enabled = pidManager.getEnabledPids().size();
        int loaded = customDataItems.size();

        return String.format("Custom PIDs: %d total, %d enabled, %d loaded",
                total, enabled, loaded);
    }

    /**
     * Load all enabled custom PIDs directly into ObdProt.PidPvs for live display
     * This makes custom PIDs appear in Live Data screen without app restart
     */
    public void loadIntoLiveData() {
        Log.i(TAG, "Loading custom PIDs into ObdProt.PidPvs for live display");

        // Clear previously loaded custom PIDs
        clearCustomPidsFromLiveData();

        // Load all enabled custom PIDs
        List<CustomPid> enabledPids = pidManager.getEnabledPids();
        Log.i(TAG, "Found " + enabledPids.size() + " enabled custom PIDs");

        int loadedCount = 0;
        for (CustomPid customPid : enabledPids) {
            try {
                EcuDataItem dataItem = createDataItem(customPid);
                if (dataItem != null && dataItem.pv != null) {
                    // Generate unique key (use String key to avoid conflicts with standard PIDs)
                    String pidKey = "CUSTOM_" + customPid.getPidHex();

                    // Add to ObdProt.PidPvs so it shows up in Live Data
                    ObdProt.PidPvs.put(pidKey, dataItem.pv);

                    // Track this key so we can remove it later
                    loadedPidKeys.add(pidKey);
                    customDataItems.add(dataItem);

                    DebugLog.d(TAG, "Loaded custom PID into live data: " + customPid.getName() + " (key=" + pidKey + ")");
                    loadedCount++;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load custom PID into live data: " + customPid.getName(), e);
            }
        }

        Log.i(TAG, "Successfully loaded " + loadedCount + " custom PIDs into live data");
    }

    /**
     * Remove all custom PIDs from ObdProt.PidPvs
     * Useful before reloading or when custom PIDs are disabled
     */
    public void clearCustomPidsFromLiveData() {
        Log.i(TAG, "Clearing " + loadedPidKeys.size() + " custom PIDs from live data");

        for (String pidKey : loadedPidKeys) {
            try {
                ObdProt.PidPvs.remove(pidKey);
                DebugLog.d(TAG, "Removed custom PID: " + pidKey);
            } catch (Exception e) {
                Log.e(TAG, "Failed to remove custom PID: " + pidKey, e);
            }
        }

        loadedPidKeys.clear();
        customDataItems.clear();
    }

    /**
     * Refresh custom PIDs in live data (remove old ones, load new ones)
     * Call this after user adds/edits/deletes custom PIDs
     */
    public void refreshLiveData() {
        Log.i(TAG, "Refreshing custom PIDs in live data");
        loadIntoLiveData();
    }

    /**
     * Get the number of custom PIDs currently loaded in live data
     */
    public int getLoadedCount() {
        return loadedPidKeys.size();
    }
}
