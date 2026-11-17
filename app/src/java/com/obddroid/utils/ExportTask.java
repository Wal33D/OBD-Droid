package com.obddroid.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;

import com.github.mikephil.charting.data.Entry;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.obddroid.R;

/**
 * Builds the CSV dump for sharing chart data.
 * Updated for MPAndroidChart compatibility.
 */
public class ExportTask {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Activity activity;

    @SuppressLint("SimpleDateFormat")
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private static final String OPT_FIELD_DELIM = "csv_field_delimiter";
    private static final String OPT_RECORD_DELIM = "csv_record_delimiter";
    private static final String OPT_TEXT_QUOTED = "csv_text_quoted";
    private static final String OPT_SEND_EXPORT = "send_after_export";

    private static String CSV_FIELD_DELIMITER = ",";
    private static String CSV_LINE_DELIMITER = "\n";
    private static boolean CSV_TEXT_QUOTED = false;

    private static final String TAG = ExportTask.class.getSimpleName();
    private static final Logger log = Logger.getLogger(TAG);

    private final SharedPreferences prefs;
    private final String path;
    private final String fileName;

    /**
     * Series data holder for export
     */
    public static class SeriesData {
        public String label;
        public List<Entry> entries;

        public SeriesData(String label, List<Entry> entries) {
            this.label = label;
            this.entries = entries;
        }
    }

    public ExportTask(Activity activity) {
        this.activity = activity;
        path = FileHelper.getPath(activity).concat(File.separator + "csv");
        fileName = path.concat(File.separator + FileHelper.getFileName().concat(".csv"));

        prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        CSV_FIELD_DELIMITER = prefs.getString(OPT_FIELD_DELIM, ",");
        CSV_LINE_DELIMITER = prefs.getString(OPT_RECORD_DELIM, "\n");
        CSV_TEXT_QUOTED = prefs.getBoolean(OPT_TEXT_QUOTED, false);
    }

    private static String quoteStringIfNeeded(String string) {
        return String.format(CSV_TEXT_QUOTED ? "\"%s\"" : "%s", string);
    }

    @SafeVarargs
    public final <T> void execute(T... params) {
        executorService.execute(() -> {
            String result = performExport(params);
            mainHandler.post(() -> onPostExecute(result));
        });
    }

    @SuppressWarnings("unchecked")
    private <T> String performExport(T... params) {
        if (params == null || params.length == 0) {
            return null;
        }

        // Handle both List<SeriesData> and List<ChartSeriesData>
        List<?> seriesList = (List<?>) params[0];
        if (seriesList.isEmpty()) {
            return null;
        }

        // Extract series data
        List<SeriesExportData> exportDataList = new java.util.ArrayList<>();
        int maxDataPoints = 0;

        for (Object obj : seriesList) {
            SeriesExportData exportData = extractSeriesData(obj);
            if (exportData != null) {
                exportDataList.add(exportData);
                maxDataPoints = Math.max(maxDataPoints, exportData.entries.size());
            }
        }

        if (exportDataList.isEmpty()) {
            return null;
        }

        // Create CSV directory
        new File(path).mkdirs();

        try (FileWriter writer = new FileWriter(new File(fileName))) {
            // Write header line
            writer.append(quoteStringIfNeeded(activity.getString(R.string.time)));
            writer.append(CSV_FIELD_DELIMITER);
            for (SeriesExportData data : exportDataList) {
                writer.append(quoteStringIfNeeded(data.label));
                writer.append(CSV_FIELD_DELIMITER);
            }
            writer.append(CSV_LINE_DELIMITER);

            // Write data rows
            for (int i = 0; i < maxDataPoints; i++) {
                // Use timestamp from first series entry
                long timestamp = 0;
                if (!exportDataList.isEmpty() && i < exportDataList.get(0).entries.size()) {
                    timestamp = (long) exportDataList.get(0).entries.get(i).getX();
                }

                writer.append(dateFormat.format(new Date(timestamp)));
                writer.append(CSV_FIELD_DELIMITER);

                for (SeriesExportData data : exportDataList) {
                    if (i < data.entries.size()) {
                        Entry entry = data.entries.get(i);
                        writer.append(String.valueOf(entry.getY()));
                    }
                    writer.append(CSV_FIELD_DELIMITER);
                }
                writer.append(CSV_LINE_DELIMITER);
            }

        } catch (IOException e) {
            log.log(Level.SEVERE, "Error exporting CSV", e);
            return null;
        }

        return fileName;
    }

    /**
     * Extract series data using reflection to handle both SeriesData and ChartSeriesData types
     */
    private SeriesExportData extractSeriesData(Object obj) {
        try {
            // Try to access fields via reflection
            java.lang.reflect.Field labelField = obj.getClass().getDeclaredField("label");
            java.lang.reflect.Field entriesField = obj.getClass().getDeclaredField("entries");

            labelField.setAccessible(true);
            entriesField.setAccessible(true);

            String label = (String) labelField.get(obj);
            @SuppressWarnings("unchecked")
            List<Entry> entries = (List<Entry>) entriesField.get(obj);

            return new SeriesExportData(label, entries);
        } catch (ReflectiveOperationException | ClassCastException e) {
            log.log(Level.WARNING, "Could not extract series data", e);
            return null;
        }
    }

    /**
     * Internal data structure for export
     */
    private static class SeriesExportData {
        String label;
        List<Entry> entries;

        SeriesExportData(String label, List<Entry> entries) {
            this.label = label;
            this.entries = entries;
        }
    }

    private void onPostExecute(String result) {
        if (result == null) {
            SnackbarHelper.showError(activity, "Export failed", SnackbarHelper.Duration.SHORT);
            executorService.shutdown();
            return;
        }

        // Show saved message
        String msg = String.format("CSV %s to %s",
                activity.getString(R.string.saved),
                fileName);
        log.log(Level.INFO, msg);
        SnackbarHelper.showSuccess(activity, msg, SnackbarHelper.Duration.SHORT);

        // If export file should be sent immediately
        if (prefs.getBoolean(OPT_SEND_EXPORT, false)) {
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("*/*");
            sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(fileName)));
            activity.startActivity(
                    Intent.createChooser(sendIntent,
                            activity.getResources().getText(R.string.send_to)));
        }

        executorService.shutdown();
    }
}
