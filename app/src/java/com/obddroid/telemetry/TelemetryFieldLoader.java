package com.obddroid.telemetry;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import com.obddroid.utils.DebugLog;

/**
 * Loads custom GPS and Motion telemetry field definitions from CSV files.
 * Allows field labels and descriptions to be modified without recompiling.
 */
public class TelemetryFieldLoader {
    private static final String TAG = "TelemetryFieldLoader";

    private static final String GPS_MESSAGES_PATH = "resources/custom/gps_messages.csv";
    private static final String MOTION_MESSAGES_PATH = "resources/custom/motion_messages.csv";

    /**
     * Loads GPS field definitions from CSV.
     * Returns map of mnemonic -> FieldDefinition
     */
    public static Map<String, FieldDefinition> loadGpsFields(Context context) {
        return loadFieldsFromCsv(context, GPS_MESSAGES_PATH);
    }

    /**
     * Loads Motion sensor field definitions from CSV.
     * Returns map of mnemonic -> FieldDefinition
     */
    public static Map<String, FieldDefinition> loadMotionFields(Context context) {
        return loadFieldsFromCsv(context, MOTION_MESSAGES_PATH);
    }

    private static Map<String, FieldDefinition> loadFieldsFromCsv(Context context, String path) {
        Map<String, FieldDefinition> fields = new HashMap<>();

        try {
            InputStream is = context.getAssets().open(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            // Skip header line
            String line = reader.readLine();

            // Read field definitions
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split(",", 3);
                if (parts.length >= 2) {
                    String mnemonic = parts[0].trim();
                    String label = parts[1].trim();
                    String description = parts.length > 2 ? parts[2].trim() : "";

                    fields.put(mnemonic, new FieldDefinition(label, description));
                }
            }

            reader.close();
            DebugLog.d(TAG, "Loaded " + fields.size() + " fields from " + path);

        } catch (IOException e) {
            Log.w(TAG, "Could not load custom fields from " + path + ", using defaults: " + e.getMessage());
        }

        return fields;
    }

    /**
     * Represents a custom field definition from CSV
     */
    public static class FieldDefinition {
        public final String label;
        public final String description;

        public FieldDefinition(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }
}
