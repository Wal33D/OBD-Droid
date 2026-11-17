package com.obddroid.features.recalls.data;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.obddroid.features.recalls.model.RecallSearchResult;
import com.obddroid.features.recalls.model.AutoCheckRecallResult;
import com.obddroid.features.vehiclehistory.data.AutoCheckService;
import com.obddroid.features.vehiclehistory.model.AutoCheckReport;

import java.util.List;

import io.github.recalllookup.android.RecallLookupAndroid;
import io.github.recalllookup.core.RecallRecord;
import com.obddroid.utils.VINDecoder;
import com.obddroid.utils.VehicleData;
import com.obddroid.utils.DebugLog;

/**
 * Service for performing recall searches using NHTSA APIs.
 *
 * Two-step process:
 * 1. Decode VIN to get vehicle make/model/year (nhtsa-vin-decoder)
 * 2. Lookup recalls using vehicle information (nhtsa-recall-lookup)
 *
 */
public class RecallService {

    private static final String TAG = "RecallService";

    private final VINDecoder vinDecoder;
    private final RecallLookupAndroid recallLookup;
    private final AutoCheckService autoCheckService;

    /**
     * Callback interface for recall search results
     */
    public interface RecallSearchCallback {
        void onSearchStarted();
        void onVinDecoded(VehicleData vehicleData);
        void onSearchCompleted(RecallSearchResult result);
        void onSearchFailed(String error);
    }

    /**
     * Callback interface for AutoCheck open recall lookups.
     */
    public interface OpenRecallCallback {
        void onLoading();
        void onSuccess(AutoCheckRecallResult result);
        void onError(String error);
    }

    public RecallService(Context context) {
        this.vinDecoder = new VINDecoder(context);
        this.recallLookup = new RecallLookupAndroid(context);
        this.autoCheckService = new AutoCheckService(context);
        DebugLog.d(TAG, "RecallService initialized");
    }

    /**
     * Perform a recall search for the given VIN.
     *
     * This is an asynchronous operation that:
     * 1. Validates the VIN
     * 2. Decodes the VIN to get vehicle data
     * 3. Looks up recalls for that vehicle
     * 4. Returns results via callback
     *
     * @param vin      The 17-character VIN to search
     * @param callback Callback for search results
     */
    public void searchRecallsByVin(String vin, RecallSearchCallback callback) {
        DebugLog.d(TAG, "searchRecallsByVin called with VIN: " + vin);

        // Validate VIN
        if (TextUtils.isEmpty(vin)) {
            Log.e(TAG, "VIN is empty");
            callback.onSearchFailed("No VIN provided");
            return;
        }

        if (vin.length() != 17) {
            Log.e(TAG, "Invalid VIN length: " + vin.length());
            callback.onSearchFailed("Invalid VIN (must be 17 characters)");
            return;
        }

        callback.onSearchStarted();

        // Step 1: Decode VIN to get vehicle data
        DebugLog.d(TAG, "Step 1: Decoding VIN to get vehicle data");
        vinDecoder.decodeAsync(vin, new VINDecoder.DecodeCallback() {
            @Override
            public void onSuccess(VehicleData vehicleData) {
                DebugLog.d(TAG, "VIN decode success");
                DebugLog.d(TAG, "VehicleData: " + (vehicleData != null ?
                    "Make=" + vehicleData.getMake() + ", Model=" + vehicleData.getModel() +
                    ", Year=" + vehicleData.getModelYear() : "null"));

                if (vehicleData == null || vehicleData.getMake() == null || vehicleData.getModel() == null) {
                    callback.onSearchFailed("Unable to decode vehicle information from VIN");
                    return;
                }

                callback.onVinDecoded(vehicleData);

                // Step 2: Lookup recalls with smart fallback strategy
                DebugLog.d(TAG, "Step 2: Starting smart recall lookup with fallback");
                tryRecallLookupWithFallback(vin, vehicleData, 0, callback);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "VIN decode error: " + error);
                callback.onSearchFailed(error != null ? error :
                    "Failed to decode VIN. Please check vehicle connection.");
            }
        });
    }

    /**
     * Fetch open recalls from the AutoCheck API for a VIN.
     * This supplements the NHTSA recall data with vehicle-specific campaigns.
     */
    public void fetchOpenRecalls(String vin, OpenRecallCallback callback) {
        if (callback == null) {
            return;
        }

        if (TextUtils.isEmpty(vin)) {
            callback.onError("No VIN provided");
            return;
        }

        if (vin.length() != 17) {
            callback.onError("Invalid VIN (must be 17 characters)");
            return;
        }

        callback.onLoading();

        autoCheckService.fetchReport(vin, new AutoCheckService.AutoCheckCallback() {
            @Override
            public void onSuccess(AutoCheckReport report) {
                AutoCheckRecallResult result = AutoCheckRecallResult.fromReport(report);
                if (result == null) {
                    callback.onError("AutoCheck did not return recall data");
                } else {
                    callback.onSuccess(result);
                }
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage != null ? errorMessage : "Failed to fetch open recalls");
            }
        });
    }

    /**
     * Try recall lookup with multiple model name variations (fallback strategy).
     * This ensures compatibility across all vehicle manufacturers.
     */
    private void tryRecallLookupWithFallback(final String vin, final VehicleData vehicleData,
                                             final int attemptIndex, final RecallSearchCallback callback) {
        // Generate list of model name variations to try
        String[] modelVariations = generateModelVariations(vehicleData);

        if (attemptIndex >= modelVariations.length) {
            // All attempts failed
            Log.e(TAG, "All recall lookup attempts failed for " + vehicleData.getMake() + " " + vehicleData.model);
            callback.onSearchFailed("Unable to find recalls. The vehicle model format may not be recognized.");
            return;
        }

        String currentModel = modelVariations[attemptIndex];
        DebugLog.d(TAG, "Trying recall lookup (attempt " + (attemptIndex + 1) + "/" + modelVariations.length +
            "): " + vehicleData.getMake() + " " + currentModel + " " + vehicleData.getModelYear());

        recallLookup.getRecalls(
            vehicleData.getMake(),
            currentModel,
            vehicleData.getModelYear(),
            new RecallLookupAndroid.RecallCallback() {
                @Override
                public void onSuccess(List<RecallRecord> recalls) {
                    DebugLog.d(TAG, "Recall lookup SUCCESS with model: " + currentModel +
                        " - found " + (recalls != null ? recalls.size() : 0) + " recalls");
                    RecallSearchResult result = new RecallSearchResult(vin, vehicleData, recalls);
                    callback.onSearchCompleted(result);
                }

                @Override
                public void onError(String error) {
                    if (error != null && error.contains("HTTP 400")) {
                        // 400 error means bad format - try next variation
                        Log.w(TAG, "Model format rejected (HTTP 400), trying next variation...");
                        tryRecallLookupWithFallback(vin, vehicleData, attemptIndex + 1, callback);
                    } else {
                        // Other error - fail immediately
                        Log.e(TAG, "Recall lookup error: " + error);
                        callback.onSearchFailed(error != null ? error :
                            "Failed to search recalls. Please try again.");
                    }
                }
            });
    }

    /**
     * Generate multiple model name variations to try.
     * Returns array of model strings from most specific to least specific.
     */
    private String[] generateModelVariations(VehicleData vehicleData) {
        java.util.List<String> variations = new java.util.ArrayList<>();
        String model = vehicleData.model;
        String series = vehicleData.series;
        String trim = vehicleData.trim;

        if (model == null || model.isEmpty()) {
            return new String[]{""};
        }

        // Remove common suffixes
        String baseModel = model
            .replace("-Class", "")
            .replace(" Class", "")
            .trim();

        // Use series if available, otherwise fallback to trim (NHTSA often puts data in Trim field)
        String trimOrSeries = (series != null && !series.isEmpty() && !series.equals("Not Applicable"))
            ? series
            : trim;

        // Variation 1: Clean series model (e.g., "GLE350" from "GLE350-4M") - Mercedes/luxury format
        if (trimOrSeries != null && !trimOrSeries.isEmpty() && !trimOrSeries.equals("Not Applicable")) {
            String cleanSeries = extractCleanSeriesModel(trimOrSeries);
            if (cleanSeries != null && !cleanSeries.isEmpty() && !cleanSeries.equals(baseModel)) {
                variations.add(cleanSeries);
            }
        }

        // Variation 2: Base model + full series (e.g., "GLE GLE350-4M")
        if (trimOrSeries != null && !trimOrSeries.isEmpty() && !trimOrSeries.equals("Not Applicable")) {
            variations.add(baseModel + " " + trimOrSeries);
        }

        // Variation 3: Base model + extracted trim number (e.g., "GLE 350")
        if (trimOrSeries != null && !trimOrSeries.isEmpty() && !trimOrSeries.equals("Not Applicable")) {
            String trimNumber = extractTrimNumber(trimOrSeries);
            if (trimNumber != null && !trimNumber.isEmpty() && !trimNumber.equals(trimOrSeries)) {
                variations.add(baseModel + " " + trimNumber);
            }
        }

        // Variation 4: Just base model (e.g., "GLE")
        variations.add(baseModel);

        // Variation 5: Original model as-is (e.g., "GLE-Class")
        if (!model.equals(baseModel)) {
            variations.add(model);
        }

        DebugLog.d(TAG, "Generated " + variations.size() + " model variations: " + variations);
        return variations.toArray(new String[0]);
    }

    /**
     * Extract clean series model from series string by removing suffixes after dash/space.
     * Examples: "GLE350-4M" -> "GLE350", "X5 xDrive40i" -> "X5", "Accord EX-L" -> "Accord EX"
     */
    private String extractCleanSeriesModel(String series) {
        if (series == null || series.isEmpty()) {
            return null;
        }

        // Extract alphanumeric portion before any dash (for Mercedes: "GLE350-4M" -> "GLE350")
        String cleaned = series.split("-")[0].trim();

        // Also handle space-separated suffixes (for BMW: "X5 xDrive40i" -> "X5")
        // But only if the second part looks like a suffix (starts with lowercase or has specific patterns)
        String[] parts = cleaned.split("\\s+");
        if (parts.length > 1) {
            // Keep first part if second part looks like a suffix (lowercase start, or contains "Drive", etc.)
            String secondPart = parts[1];
            if (secondPart.matches("^[a-z].*") || secondPart.contains("Drive") || secondPart.contains("Matic")) {
                cleaned = parts[0];
            }
        }

        return cleaned;
    }

    /**
     * Extract trim number from series string.
     * Examples: "GLE350-4M" -> "350", "Accord EX" -> "EX", "335i" -> "335"
     */
    private String extractTrimNumber(String series) {
        if (series == null || series.isEmpty()) {
            return null;
        }

        // Try to extract just the numeric part (e.g., "GLE350-4M" -> "350")
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(series);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // If no numbers, return the series as-is (handles "EX", "LX", etc.)
        return series;
    }

    /**
     * Cancel any ongoing search operations (if supported by underlying libraries)
     */
    public void cancelSearch() {
        // Note: Current libraries don't support cancellation
        DebugLog.d(TAG, "cancelSearch called (not implemented by underlying libraries)");
    }
}
