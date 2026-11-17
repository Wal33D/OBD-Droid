package com.obddroid.features.vehiclehistory.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.Window;

import androidx.core.content.FileProvider;

import com.obddroid.R;
import com.obddroid.features.vehiclehistory.model.AutoCheckReport;
import com.obddroid.utils.ReportFileWriter;
import com.obddroid.utils.SnackbarHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Saves the vehicle history report to Documents/OBD-Droid as a PDF copy, CSV or JSON (the save dialog),
 * then opens the saved file. The CSV text comes from {@link AutoCheckReportFormat#csv}.
 */
final class AutoCheckExporter {

    private final Activity activity;

    AutoCheckExporter(Activity activity) {
        this.activity = activity;
    }

    /** The report on screen and its PDF, read when the user picks a format. */
    interface Source {
        AutoCheckReport report();

        /** The PDF fetched in the background, or null while it is still being made. */
        String pdfFilePath();
    }

    /** The format chooser for the report on screen. */
    void showSaveDialog(Source source) {
        if (source.report() == null) {
            SnackbarHelper.showSnackbar(activity, "No report available to export", SnackbarHelper.MessageType.WARNING);
            return;
        }

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_save_report);
        dialog.setCancelable(true);

        View pdfOption = dialog.findViewById(R.id.option_export_pdf);
        pdfOption.setOnClickListener(v -> {
            dialog.dismiss();
            savePdfCopy(source.report(), source.pdfFilePath());
        });

        View csvOption = dialog.findViewById(R.id.option_export_csv);
        csvOption.setOnClickListener(v -> {
            dialog.dismiss();
            exportCsv(source.report());
        });

        View jsonOption = dialog.findViewById(R.id.option_export_json);
        jsonOption.setOnClickListener(v -> {
            dialog.dismiss();
            exportJson(source.report());
        });

        View cancelBtn = dialog.findViewById(R.id.btn_cancel);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void savePdfCopy(AutoCheckReport report, String pdfFilePath) {
        if (report == null) {
            SnackbarHelper.showSnackbar(activity, "No report available", SnackbarHelper.MessageType.WARNING);
            return;
        }
        if (pdfFilePath == null || pdfFilePath.isEmpty()) {
            SnackbarHelper.showSnackbar(activity, "PDF is still being generated, please wait...", SnackbarHelper.MessageType.INFO);
            return;
        }

        try {
            File sourcePdf = new File(pdfFilePath);
            if (!sourcePdf.exists()) {
                SnackbarHelper.showSnackbar(activity, "PDF file not found", SnackbarHelper.MessageType.ERROR);
                return;
            }

            String fileName = AutoCheckReportFormat.exportFileName(report.getVin(), fileTimestamp(), "pdf");
            ReportFileWriter.Result saved = ReportFileWriter.exportStream(activity, fileName,
                ReportFileWriter.MIME_PDF, out -> {
                    try (FileInputStream in = new FileInputStream(sourcePdf)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                });

            SnackbarHelper.showSnackbar(activity, "PDF saved: " + fileName, SnackbarHelper.MessageType.SUCCESS);
            openSaved(saved);
        } catch (Exception e) {
            SnackbarHelper.showSnackbar(activity, "Failed to save PDF: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
        }
    }

    private void exportCsv(AutoCheckReport report) {
        if (report == null) return;
        String fileName = AutoCheckReportFormat.exportFileName(report.getVin(), fileTimestamp(), "csv");
        String displayTimestamp = displayTimestamp();

        try {
            ReportFileWriter.Result saved = ReportFileWriter.export(activity, fileName,
                ReportFileWriter.MIME_CSV, writer -> writer.write(AutoCheckReportFormat.csv(report, displayTimestamp)));

            SnackbarHelper.showSnackbar(activity, "CSV saved: " + fileName, SnackbarHelper.MessageType.SUCCESS);
            openSaved(saved);
        } catch (Exception e) {
            SnackbarHelper.showSnackbar(activity, "Failed to export CSV: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
        }
    }

    private void exportJson(AutoCheckReport report) {
        if (report == null) return;
        String fileName = AutoCheckReportFormat.exportFileName(report.getVin(), fileTimestamp(), "json");

        try {
            String json = toJson(report, displayTimestamp()).toString(2);
            ReportFileWriter.Result saved = ReportFileWriter.export(activity, fileName,
                ReportFileWriter.MIME_JSON, writer -> writer.write(json));

            SnackbarHelper.showSnackbar(activity, "JSON saved: " + fileName, SnackbarHelper.MessageType.SUCCESS);
            openSaved(saved);
        } catch (Exception e) {
            SnackbarHelper.showSnackbar(activity, "Failed to export JSON: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
        }
    }

    /** The JSON export: vehicle, score, stats, safety and the history events. */
    private static JSONObject toJson(AutoCheckReport report, String displayTimestamp) throws Exception {
        JSONObject root = new JSONObject();
        root.put("report_type", "OBD-Droid Vehicle History Report");
        root.put("generated_at", displayTimestamp);
        root.put("timestamp", new Date().getTime());

        JSONObject vehicle = new JSONObject();
        vehicle.put("vin", report.getVin());
        if (report.getYear() != null) vehicle.put("year", report.getYear());
        if (report.getMake() != null) vehicle.put("make", report.getMake());
        if (report.getModel() != null) vehicle.put("model", report.getModel());
        if (report.getStyle() != null) vehicle.put("style", report.getStyle());
        if (report.getEngine() != null) vehicle.put("engine", report.getEngine());
        root.put("vehicle", vehicle);

        if (report.getScore() != null) {
            JSONObject score = new JSONObject();
            score.put("value", report.getScore());
            if (report.getScoreRange() != null) {
                score.put("range_low", report.getScoreRange().low);
                score.put("range_high", report.getScoreRange().high);
            }
            root.put("autocheck_score", score);
        }

        JSONObject stats = new JSONObject();
        if (report.getOwners() != null) stats.put("owners", report.getOwners());
        if (report.getLastOdometer() != null) stats.put("last_odometer", report.getLastOdometer());
        if (report.getServiceRecords() != null) stats.put("service_records", report.getServiceRecords());
        if (report.getVehicleAge() != null) stats.put("vehicle_age", report.getVehicleAge());
        root.put("stats", stats);

        JSONObject safety = new JSONObject();
        if (report.getTitleBrand() != null) safety.put("title_brand", report.getTitleBrand());
        if (report.getAccidentDamage() != null) safety.put("accident_damage", report.getAccidentDamage());
        safety.put("total_loss", report.getTotalLoss() != null && report.getTotalLoss());
        safety.put("structural_damage", report.getStructuralDamage() != null && report.getStructuralDamage());
        safety.put("airbag_deployed", report.getAirbagDeployed() != null && report.getAirbagDeployed());
        safety.put("odometer_rollback", report.getOdometerRollback() != null && report.getOdometerRollback());
        root.put("safety", safety);

        if (report.getHistoryEvents() != null && !report.getHistoryEvents().isEmpty()) {
            JSONArray events = new JSONArray();
            for (AutoCheckReport.HistoryEvent event : report.getHistoryEvents()) {
                JSONObject eventObj = new JSONObject();
                if (event.date != null) eventObj.put("date", event.date);
                if (event.location != null) eventObj.put("location", event.location);
                if (event.odometer != null) eventObj.put("odometer", event.odometer);
                if (event.source != null) eventObj.put("source", event.source);
                if (event.details != null) eventObj.put("details", event.details);
                events.put(eventObj);
            }
            root.put("history_events", events);
        }
        return root;
    }

    private static String fileTimestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private static String displayTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    /** Opens an export just written by {@link ReportFileWriter}. */
    private void openSaved(ReportFileWriter.Result saved) {
        Uri uri = saved.uri != null
            ? saved.uri
            : FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", saved.file);
        openFile(uri, saved.mimeType, saved.filename);
    }

    /** Opens the file in a viewer, or the share sheet when no app handles the type. */
    private void openFile(Uri fileUri, String mimeType, String fileName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);

            try {
                activity.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType(mimeType);
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(shareIntent, "Open " + fileName + " with..."));
            }
        } catch (Exception e) {
            SnackbarHelper.showSnackbar(activity, "Saved to Documents/OBD-Droid", SnackbarHelper.MessageType.SUCCESS);
        }
    }
}
