package com.obddroid.features.recalls.data;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.obddroid.features.recalls.model.RecallSearchResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.recalllookup.core.RecallRecord;

/**
 * Handles export of recall search results to CSV and JSON formats.
 *
 * Supports:
 * - CSV export with proper escaping
 * - JSON export with structured data
 * - Android Q+ MediaStore API
 * - Legacy file system for older Android versions
 *
 */
public class RecallExporter {

    private static final String EXPORT_DIRECTORY = Environment.DIRECTORY_DOCUMENTS + "/OBD-Droid";
    private final Context context;

    public RecallExporter(Context context) {
        this.context = context;
    }

    /**
     * Export recall search result to CSV format.
     *
     * @param result The recall search result to export
     * @return Path/URI of exported file
     * @throws IOException if export fails
     */
    public String exportToCsv(RecallSearchResult result) throws IOException {
        if (result == null || result.getRecallCount() == 0) {
            throw new IllegalArgumentException("No recall data available to export");
        }

        String filename = buildExportFilename(result.getVin(), "csv");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, EXPORT_DIRECTORY);

            Uri uri = context.getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            if (uri == null) {
                throw new IOException("Unable to create export file");
            }

            try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
                 OutputStreamWriter osWriter = new OutputStreamWriter(outputStream);
                 BufferedWriter writer = new BufferedWriter(osWriter)) {
                writeCsvContent(writer, result);
            }

            return filename;
        } else {
            // Legacy file system for Android 9 and below
            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File obdroidDir = new File(documentsDir, "OBD-Droid");
            if (!obdroidDir.exists() && !obdroidDir.mkdirs()) {
                throw new IOException("Unable to create export directory");
            }

            File csvFile = new File(obdroidDir, filename);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
                writeCsvContent(writer, result);
            }

            return csvFile.getAbsolutePath();
        }
    }

    /**
     * Export recall search result to JSON format.
     *
     * @param result The recall search result to export
     * @return Path/URI of exported file
     * @throws IOException if export fails
     * @throws JSONException if JSON creation fails
     */
    public String exportToJson(RecallSearchResult result) throws IOException, JSONException {
        if (result == null || result.getRecallCount() == 0) {
            throw new IllegalArgumentException("No recall data available to export");
        }

        String filename = buildExportFilename(result.getVin(), "json");

        // Build JSON structure
        JSONObject root = new JSONObject();
        root.put("generatedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
        root.put("vin", result.getVin());
        root.put("vehicle", result.getVehicleDisplayName());
        root.put("recallCount", result.getRecallCount());

        JSONArray recallsArray = new JSONArray();
        for (RecallRecord recall : result.getRecalls()) {
            JSONObject item = new JSONObject();
            putIfNotEmpty(item, "campaignNumber", recall.getNhtsaCampaignNumber());
            putIfNotEmpty(item, "actionNumber", recall.getNhtsaActionNumber());
            putIfNotEmpty(item, "manufacturer", recall.getManufacturer());
            putIfNotEmpty(item, "component", recall.getComponent());
            putIfNotEmpty(item, "modelYear", recall.getModelYear());
            putIfNotEmpty(item, "make", recall.getMake());
            putIfNotEmpty(item, "model", recall.getModel());
            putIfNotEmpty(item, "reportReceivedDate", recall.getReportReceivedDate());
            putIfNotEmpty(item, "summary", recall.getSummary());
            putIfNotEmpty(item, "remedy", recall.getRemedy());
            putIfNotEmpty(item, "consequence", recall.getConsequence());
            putIfNotEmpty(item, "notes", recall.getNotes());
            putIfNotEmpty(item, "mfrRecallNumber", recall.getMfrRecallNumber());
            putIfNotNull(item, "overTheAirUpdate", recall.getOverTheAirUpdate());
            putIfNotNull(item, "parkIt", recall.getParkIt());
            putIfNotNull(item, "parkOutside", recall.getParkOutside());
            recallsArray.put(item);
        }
        root.put("recalls", recallsArray);

        String jsonString = root.toString(2); // Pretty print with 2-space indent

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, EXPORT_DIRECTORY);

            Uri uri = context.getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            if (uri == null) {
                throw new IOException("Unable to create export file");
            }

            try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
                 OutputStreamWriter osWriter = new OutputStreamWriter(outputStream);
                 BufferedWriter writer = new BufferedWriter(osWriter)) {
                writer.write(jsonString);
            }

            return filename;
        } else {
            // Legacy file system for Android 9 and below
            File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File obdroidDir = new File(documentsDir, "OBD-Droid");
            if (!obdroidDir.exists() && !obdroidDir.mkdirs()) {
                throw new IOException("Unable to create export directory");
            }

            File jsonFile = new File(obdroidDir, filename);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile))) {
                writer.write(jsonString);
            }

            return jsonFile.getAbsolutePath();
        }
    }

    /**
     * Write CSV content for recall search result.
     */
    private void writeCsvContent(BufferedWriter writer, RecallSearchResult result) throws IOException {
        // Header row
        writer.write("Campaign #,Component,Model Year,Make,Model,Report Date,Summary,Remedy,Consequence,Notes\n");

        // Data rows
        for (RecallRecord recall : result.getRecalls()) {
            writer.write(csvEscape(recall.getNhtsaCampaignNumber()));
            writer.write(",");
            writer.write(csvEscape(recall.getComponent()));
            writer.write(",");
            writer.write(csvEscape(recall.getModelYear()));
            writer.write(",");
            writer.write(csvEscape(recall.getMake()));
            writer.write(",");
            writer.write(csvEscape(recall.getModel()));
            writer.write(",");
            writer.write(csvEscape(recall.getReportReceivedDate()));
            writer.write(",");
            writer.write(csvEscape(recall.getSummary()));
            writer.write(",");
            writer.write(csvEscape(recall.getRemedy()));
            writer.write(",");
            writer.write(csvEscape(recall.getConsequence()));
            writer.write(",");
            writer.write(csvEscape(recall.getNotes()));
            writer.write("\n");
        }
    }

    /**
     * Build export filename with VIN suffix and timestamp.
     */
    private String buildExportFilename(String vin, String extension) {
        String vinSuffix = "vehicle";
        if (!TextUtils.isEmpty(vin)) {
            if (vin.length() >= 6) {
                vinSuffix = vin.substring(vin.length() - 6).toUpperCase(Locale.US);
            } else {
                vinSuffix = vin.toUpperCase(Locale.US);
            }
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return vinSuffix + "-safety-recalls-" + timestamp + "." + extension;
    }

    /**
     * Escape CSV value (handle commas, quotes, newlines).
     */
    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace("\r", " ").replace("\n", " ").trim();
        if (sanitized.contains(",") || sanitized.contains("\"")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    /**
     * Put value in JSON object only if not empty.
     */
    private void putIfNotEmpty(JSONObject target, String key, String value) throws JSONException {
        if (!TextUtils.isEmpty(value)) {
            target.put(key, value);
        }
    }

    /**
     * Put boolean value in JSON object only if not null.
     */
    private void putIfNotNull(JSONObject target, String key, Boolean value) throws JSONException {
        if (value != null) {
            target.put(key, value);
        }
    }
}
