package com.obddroid.features.recalls.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.obddroid.features.recalls.model.RecallSearchResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.github.recalllookup.core.RecallRecord;
import com.obddroid.utils.VehicleData;
import com.obddroid.utils.DebugLog;

/**
 * Manages recall search data persistence and caching.
 *
 * Responsibilities:
 * - Cache recall search results to SharedPreferences
 * - Retrieve cached results for a VIN
 * - Manage in-memory state
 *
 * Cache structure:
 * - VIN → RecallSearchResult (persisted as JSON)
 * - Results are cached per-VIN with timestamp
 *
 */
public class RecallDataManager {

    private static final String TAG = "RecallDataManager";
    private static final String PREFS_NAME = "RecallDataCache";
    private static final String PREF_LAST_VIN = "last_vin";
    private static final String PREF_CACHED_RECALLS = "cached_recalls";
    private static final String PREF_CACHED_VEHICLE_DATA = "cached_vehicle_data";
    private static final String PREF_CACHE_TIMESTAMP = "cache_timestamp";

    private final SharedPreferences prefs;
    private RecallSearchResult currentResult;

    public RecallDataManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Save a recall search result to cache.
     *
     * @param result The search result to cache
     */
    public void cacheResult(RecallSearchResult result) {
        if (result == null || TextUtils.isEmpty(result.getVin())) {
            Log.w(TAG, "Cannot cache null or empty result");
            return;
        }

        this.currentResult = result;

        SharedPreferences.Editor editor = prefs.edit();

        try {
            // Save VIN
            editor.putString(PREF_LAST_VIN, result.getVin());

            // Save recall data as JSON
            JSONArray recallsArray = new JSONArray();
            for (RecallRecord recall : result.getRecalls()) {
                JSONObject recallObj = new JSONObject();
                recallObj.put("campaignNumber", recall.getNhtsaCampaignNumber());
                recallObj.put("component", recall.getComponent());
                recallObj.put("summary", recall.getSummary());
                recallObj.put("remedy", recall.getRemedy());
                recallObj.put("reportReceivedDate", recall.getReportReceivedDate());
                recallObj.put("modelYear", recall.getModelYear());
                recallObj.put("make", recall.getMake());
                recallObj.put("model", recall.getModel());
                recallObj.put("manufacturer", recall.getManufacturer());
                recallObj.put("consequence", recall.getConsequence());
                recallObj.put("notes", recall.getNotes());
                recallsArray.put(recallObj);
            }
            editor.putString(PREF_CACHED_RECALLS, recallsArray.toString());

            // Save vehicle data as JSON
            VehicleData vehicleData = result.getVehicleData();
            if (vehicleData != null) {
                JSONObject vehicleObj = new JSONObject();
                if (vehicleData.getMake() != null) vehicleObj.put("make", vehicleData.getMake());
                if (vehicleData.getModel() != null) vehicleObj.put("model", vehicleData.getModel());
                if (vehicleData.getModelYear() != null) vehicleObj.put("modelYear", vehicleData.getModelYear());
                if (vehicleData.getVin() != null) vehicleObj.put("vin", vehicleData.getVin());
                editor.putString(PREF_CACHED_VEHICLE_DATA, vehicleObj.toString());
            }

            // Save timestamp
            editor.putLong(PREF_CACHE_TIMESTAMP, result.getTimestamp());
            editor.apply();

            DebugLog.d(TAG, "Cached " + result.getRecallCount() + " recalls for VIN: " + result.getVin());
        } catch (JSONException e) {
            Log.e(TAG, "Error caching result", e);
        }
    }

    /**
     * Load cached result for a specific VIN.
     *
     * @param vin The VIN to load cached data for
     * @return Cached result if available and matches VIN, null otherwise
     */
    public RecallSearchResult loadCachedResult(String vin) {
        if (TextUtils.isEmpty(vin)) {
            return null;
        }

        String cachedVin = prefs.getString(PREF_LAST_VIN, "");

        // Check if VIN matches
        if (!vin.equalsIgnoreCase(cachedVin)) {
            DebugLog.d(TAG, "VIN mismatch - no cached data for: " + vin);
            return null;
        }

        // If we have current result in memory for this VIN, return it
        if (currentResult != null && vin.equalsIgnoreCase(currentResult.getVin())) {
            DebugLog.d(TAG, "Returning in-memory cached result for VIN: " + vin);
            return currentResult;
        }

        // Try to load from SharedPreferences
        try {
            String recallsJson = prefs.getString(PREF_CACHED_RECALLS, null);
            String vehicleDataJson = prefs.getString(PREF_CACHED_VEHICLE_DATA, null);
            long timestamp = prefs.getLong(PREF_CACHE_TIMESTAMP, 0);

            if (recallsJson == null || vehicleDataJson == null) {
                DebugLog.d(TAG, "No cached data found for VIN: " + vin);
                return null;
            }

            // Parse vehicle data
            JSONObject vehicleObj = new JSONObject(vehicleDataJson);
            // Note: VehicleData doesn't have a public constructor, so we can't reconstruct it
            // We'll return null and rely on in-memory caching
            DebugLog.d(TAG, "Cannot reconstruct VehicleData from cache - use in-memory only");
            return null;

        } catch (JSONException e) {
            Log.e(TAG, "Error loading cached result", e);
            return null;
        }
    }

    /**
     * Get the current in-memory result.
     *
     * @return Current result or null
     */
    public RecallSearchResult getCurrentResult() {
        return currentResult;
    }

    /**
     * Set the current in-memory result.
     *
     * @param result The result to store in memory
     */
    public void setCurrentResult(RecallSearchResult result) {
        this.currentResult = result;
    }

    /**
     * Clear the current result from memory.
     */
    public void clearCurrentResult() {
        this.currentResult = null;
    }

    /**
     * Check if we have cached data for a specific VIN.
     *
     * @param vin The VIN to check
     * @return true if cached data exists
     */
    public boolean hasCachedData(String vin) {
        if (TextUtils.isEmpty(vin)) {
            return false;
        }

        String cachedVin = prefs.getString(PREF_LAST_VIN, "");
        return vin.equalsIgnoreCase(cachedVin) && prefs.contains(PREF_CACHED_RECALLS);
    }

    /**
     * Get the age of cached data in minutes.
     *
     * @return Age in minutes, or -1 if no cache
     */
    public long getCacheAgeMinutes() {
        long timestamp = prefs.getLong(PREF_CACHE_TIMESTAMP, 0);
        if (timestamp == 0) {
            return -1;
        }
        return (System.currentTimeMillis() - timestamp) / (60 * 1000);
    }

    /**
     * Check if cached data is fresh (less than 24 hours old).
     *
     * @return true if cache is fresh
     */
    public boolean isCacheFresh() {
        long ageMinutes = getCacheAgeMinutes();
        return ageMinutes >= 0 && ageMinutes < 1440; // 24 hours = 1440 minutes
    }

    /**
     * Clear all cached data.
     */
    public void clearCache() {
        prefs.edit().clear().apply();
        currentResult = null;
        DebugLog.d(TAG, "Cache cleared");
    }
}
