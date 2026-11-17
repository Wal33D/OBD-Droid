package com.obddroid.features.vehiclehistory.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.obddroid.features.vehiclehistory.model.AutoCheckReport;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.obddroid.BuildConfig;
import com.obddroid.utils.DebugLog;

/**
 * Service to fetch AutoCheck vehicle history reports from the API.
 * Runs API calls on background thread and returns results on main thread.
 *
 * Requires a private AutoCheck backend (not included in this project).
 * Configure via gradle properties (set in local.properties):
 *   AUTOCHECK_API_BASE_URL=your-private-endpoint
 *   AUTOCHECK_API_KEY=your-api-key
 */
public class AutoCheckService {
    private static final String TAG = "AutoCheckService";

    // API Configuration – set via BuildConfig (see gradle.properties / local.properties)
    private static final String API_BASE_URL = BuildConfig.AUTOCHECK_API_BASE_URL;
    private static final String API_KEY = BuildConfig.AUTOCHECK_API_KEY;
    private static final int TIMEOUT_MS = 60000; // 60 seconds for browser automation

    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface AutoCheckCallback {
        void onSuccess(AutoCheckReport report);
        void onError(String errorMessage);
    }

    public interface AutoCheckPdfCallback {
        void onSuccess(AutoCheckReport report, String pdfFilePath);
        void onError(String errorMessage);
    }

    public AutoCheckService(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Fetch AutoCheck report for a VIN
     * @param vin The 17-character VIN
     * @param callback Callback to receive results on main thread
     */
    public void fetchReport(String vin, AutoCheckCallback callback) {
        if (vin == null || vin.length() != 17) {
            mainHandler.post(() -> callback.onError("Invalid VIN. Must be 17 characters."));
            return;
        }

        executor.execute(() -> {
            try {
                DebugLog.d(TAG, "Fetching AutoCheck report for VIN: " + vin);

                // Build API URL
                URL url = new URL(API_BASE_URL + "/api/autocheck/lookup");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                // Configure request
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-API-Key", API_KEY);
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setDoOutput(true);

                // Build request body
                JSONObject requestBody = new JSONObject();
                requestBody.put("vin", vin);

                // Send request
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Check response code
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    String error = readErrorStream(conn);
                    Log.e(TAG, "API error " + responseCode + ": " + error);
                    mainHandler.post(() -> callback.onError("API Error: " + error));
                    return;
                }

                // Read response
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                }

                // Parse response
                JSONObject json = new JSONObject(response.toString());

                // Extract report data from "data" field
                JSONObject reportData = json.getJSONObject("data");
                AutoCheckReport report = AutoCheckReport.fromJSON(reportData);

                DebugLog.d(TAG, "Successfully fetched AutoCheck report: " + report.getVehicleName());

                // Return on main thread
                mainHandler.post(() -> callback.onSuccess(report));

            } catch (Exception e) {
                Log.e(TAG, "Error fetching AutoCheck report", e);
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                mainHandler.post(() -> callback.onError("Failed to fetch report: " + errorMsg));
            }
        });
    }

    /**
     * Fetch AutoCheck report with PDF generation
     * @param vin The 17-character VIN
     * @param callback Callback to receive results with PDF file path on main thread
     */
    public void fetchReportWithPdf(String vin, AutoCheckPdfCallback callback) {
        if (vin == null || vin.length() != 17) {
            mainHandler.post(() -> callback.onError("Invalid VIN. Must be 17 characters."));
            return;
        }

        executor.execute(() -> {
            try {
                DebugLog.d(TAG, "Fetching AutoCheck report with PDF for VIN: " + vin);

                // Build API URL
                URL url = new URL(API_BASE_URL + "/api/autocheck/lookup");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                // Configure request
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-API-Key", API_KEY);
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setDoOutput(true);

                // Build request body with PDF generation flag
                JSONObject requestBody = new JSONObject();
                requestBody.put("vin", vin);
                requestBody.put("generatePdf", true);

                // Send request
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Check response code
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    String error = readErrorStream(conn);
                    Log.e(TAG, "API error " + responseCode + ": " + error);
                    mainHandler.post(() -> callback.onError("API Error: " + error));
                    return;
                }

                // Read response
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                }

                // Parse response
                JSONObject json = new JSONObject(response.toString());

                // Extract report data from "data" field
                JSONObject reportData = json.getJSONObject("data");
                AutoCheckReport report = AutoCheckReport.fromJSON(reportData);

                // Extract and save PDF if available (at top level of response)
                String pdfFilePath = null;
                if (json.has("pdfBase64")) {
                    String pdfBase64 = json.getString("pdfBase64");
                    pdfFilePath = savePdfToFile(vin, pdfBase64);
                    DebugLog.d(TAG, "PDF saved to: " + pdfFilePath);
                }

                DebugLog.d(TAG, "Successfully fetched AutoCheck report with PDF: " + report.getVehicleName());

                // Return on main thread
                final String finalPdfPath = pdfFilePath;
                mainHandler.post(() -> callback.onSuccess(report, finalPdfPath));

            } catch (Exception e) {
                Log.e(TAG, "Error fetching AutoCheck report with PDF", e);
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                mainHandler.post(() -> callback.onError("Failed to fetch report: " + errorMsg));
            }
        });
    }

    /**
     * Save PDF base64 data to a file
     * @param vin Vehicle VIN for filename
     * @param pdfBase64 Base64 encoded PDF data
     * @return File path to the saved PDF
     */
    private String savePdfToFile(String vin, String pdfBase64) throws Exception {
        // Decode base64
        byte[] pdfBytes = android.util.Base64.decode(pdfBase64, android.util.Base64.DEFAULT);

        // Create directory if it doesn't exist
        java.io.File pdfDir = new java.io.File(context.getExternalFilesDir(null), "autocheck_reports");
        if (!pdfDir.exists()) {
            pdfDir.mkdirs();
        }

        // Create PDF file
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date());
        java.io.File pdfFile = new java.io.File(pdfDir, "autocheck_" + vin + "_" + timestamp + ".pdf");

        // Write PDF bytes to file
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(pdfFile)) {
            fos.write(pdfBytes);
        }

        return pdfFile.getAbsolutePath();
    }

    /**
     * Decode VIN to get basic vehicle info (faster, doesn't count against rate limit)
     */
    public void decodeVIN(String vin, AutoCheckCallback callback) {
        if (vin == null || vin.length() != 17) {
            mainHandler.post(() -> callback.onError("Invalid VIN. Must be 17 characters."));
            return;
        }

        executor.execute(() -> {
            try {
                DebugLog.d(TAG, "Decoding VIN: " + vin);

                URL url = new URL(API_BASE_URL + "/api/autocheck/decode");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-API-Key", API_KEY);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                JSONObject requestBody = new JSONObject();
                requestBody.put("vin", vin);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    String error = readErrorStream(conn);
                    mainHandler.post(() -> callback.onError("Decode failed: " + error));
                    return;
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                }

                JSONObject json = new JSONObject(response.toString());

                // The decode endpoint returns basic vehicle info at top level
                String decodedVin = json.getString("vin");

                AutoCheckReport report = new AutoCheckReport(decodedVin);
                if (json.has("year")) report.setYear(json.getString("year"));
                if (json.has("make")) report.setMake(json.getString("make"));
                if (json.has("model")) report.setModel(json.getString("model"));

                mainHandler.post(() -> callback.onSuccess(report));

            } catch (Exception e) {
                Log.e(TAG, "Error decoding VIN", e);
                mainHandler.post(() -> callback.onError("Failed to decode: " + e.getMessage()));
            }
        });
    }

    /**
     * Check API health status
     */
    public void checkHealth(HealthCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(API_BASE_URL + "/health");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", API_KEY);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                boolean healthy = (responseCode == HttpURLConnection.HTTP_OK);

                mainHandler.post(() -> callback.onHealthCheck(healthy));

            } catch (Exception e) {
                Log.e(TAG, "Health check failed", e);
                mainHandler.post(() -> callback.onHealthCheck(false));
            }
        });
    }

    public interface HealthCallback {
        void onHealthCheck(boolean healthy);
    }

    private String readErrorStream(HttpURLConnection conn) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "Unknown error";
        }
    }

    /**
     * Shutdown executor when service is no longer needed
     */
    public void shutdown() {
        executor.shutdown();
    }
}
