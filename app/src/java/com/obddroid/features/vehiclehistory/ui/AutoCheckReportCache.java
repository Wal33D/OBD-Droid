package com.obddroid.features.vehiclehistory.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.obddroid.features.vehiclehistory.model.AutoCheckReport;

import java.io.File;

/**
 * Remembers the last AutoCheck report per VIN (as JSON in SharedPreferences) and the PDF that came with
 * it, so the history screen opens on the saved report without a new lookup.
 */
final class AutoCheckReportCache {

    private static final String TAG = "AutoCheckActivity";
    private static final String PREFS_NAME = "AutoCheckCache";
    private static final String PREFS_KEY_PREFIX = "report_";

    private final Context context;
    private final SharedPreferences prefs;

    AutoCheckReportCache(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Stores the report under its VIN with the time, and the PDF path when there is one. Failures are
     * ignored: the cache is a convenience.
     */
    void save(AutoCheckReport report, String pdfFilePath) {
        if (report == null || report.getVin() == null) {
            return;
        }

        try {
            String cacheKey = PREFS_KEY_PREFIX + report.getVin();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(cacheKey, new Gson().toJson(report));
            editor.putLong(cacheKey + "_time", System.currentTimeMillis());
            if (pdfFilePath != null && !pdfFilePath.isEmpty()) {
                editor.putString(cacheKey + "_pdf", pdfFilePath);
            }
            editor.apply();
        } catch (Exception e) {
            // Caching is not critical
            Log.w(TAG, "Could not cache report", e);
        }
    }

    /** Whether a report is stored for this VIN (not yet parsed). */
    boolean has(String vin) {
        String json = prefs.getString(PREFS_KEY_PREFIX + vin, null);
        return json != null && !json.isEmpty();
    }

    /** The stored report for this VIN, or null when it does not parse. */
    AutoCheckReport load(String vin) {
        return new Gson().fromJson(prefs.getString(PREFS_KEY_PREFIX + vin, null), AutoCheckReport.class);
    }

    /**
     * The PDF for this VIN's report: the remembered path while the file exists (a deleted file is
     * forgotten), else the newest autocheck_&lt;vin&gt;*.pdf in the app's autocheck_reports folder, which is
     * then remembered. Null when there is none.
     */
    String findPdf(String vin) {
        String cacheKey = PREFS_KEY_PREFIX + vin;
        String cachedPdfPath = prefs.getString(cacheKey + "_pdf", null);
        if (cachedPdfPath != null && !cachedPdfPath.isEmpty()) {
            if (new File(cachedPdfPath).exists()) {
                return cachedPdfPath;
            }
            prefs.edit().remove(cacheKey + "_pdf").apply();
            return null;
        }

        File pdfDir = new File(context.getExternalFilesDir(null), "autocheck_reports");
        if (!pdfDir.exists() || !pdfDir.isDirectory()) {
            return null;
        }
        File[] pdfFiles = pdfDir.listFiles((dir, name) ->
            name.startsWith("autocheck_" + vin) && name.endsWith(".pdf"));
        if (pdfFiles == null || pdfFiles.length == 0) {
            return null;
        }

        File mostRecentPdf = pdfFiles[0];
        for (File pdf : pdfFiles) {
            if (pdf.lastModified() > mostRecentPdf.lastModified()) {
                mostRecentPdf = pdf;
            }
        }
        String path = mostRecentPdf.getAbsolutePath();
        prefs.edit().putString(cacheKey + "_pdf", path).apply();
        return path;
    }
}
