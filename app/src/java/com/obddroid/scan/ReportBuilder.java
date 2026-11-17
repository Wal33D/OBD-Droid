package com.obddroid.scan;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import android.content.pm.PackageManager;

/**
 * Builds comprehensive scan report artifacts (Markdown, JSON, zip bundle).
 * Ensures scan data is human-readable, machine-readable, and easy to share.
 */
public final class ReportBuilder {

    private static final String TAG = "ReportBuilder";

    private static final String JSON_FILENAME = "scan_report.json";
    private static final String MARKDOWN_FILENAME = "scan_report.md";
    private static final String SUMMARY_FILENAME = "scan_summary.txt";
    private static final String ARCHIVE_FILENAME = "scan_bundle.zip";
    private static final String STAGE_DIR_NAME = "stage_data";

    private final Context appContext;

    public ReportBuilder(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Generate all report artifacts for the supplied scan report.
     *
     * @param report Completed scan report
     * @return Files generated for the report
     * @throws IOException if any artifact cannot be written
     */
    public ReportArtifacts build(ScanReport report) throws IOException {
        if (report == null) {
            throw new IllegalArgumentException("ScanReport must not be null");
        }

        File outputDir = report.getOutputDirectory();
        if (outputDir == null) {
            throw new IOException("No output directory available for report " + report.getScanId());
        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outputDir.getAbsolutePath());
        }

        JSONObject reportJson = report.toJson();

        File jsonFile = writeJsonFile(outputDir, reportJson);
        File summaryFile = writeSummaryFile(outputDir, report.getSummary());
        File markdownFile = writeMarkdownFile(outputDir, report);
        List<File> stageFiles = writeStageFiles(outputDir, report.getStageResults());
        File archiveFile = buildArchive(outputDir, jsonFile, markdownFile, summaryFile, stageFiles);

        Log.i(TAG, "Report artifacts generated for scan " + report.getScanId());

        return new ReportArtifacts(jsonFile, markdownFile, summaryFile, archiveFile, stageFiles);
    }

    private File writeJsonFile(File outputDir, JSONObject reportJson) throws IOException {
        File jsonFile = new File(outputDir, JSON_FILENAME);
        try (FileWriter writer = new FileWriter(jsonFile)) {
            writer.write(reportJson.toString(2));
        } catch (JSONException e) {
            throw new IOException("Failed to serialize report JSON", e);
        }
        return jsonFile;
    }

    private File writeSummaryFile(File outputDir, String summary) throws IOException {
        File summaryFile = new File(outputDir, SUMMARY_FILENAME);
        try (FileWriter writer = new FileWriter(summaryFile)) {
            writer.write(summary != null ? summary : "No summary available");
        }
        return summaryFile;
    }

    private File writeMarkdownFile(File outputDir, ScanReport report) throws IOException {
        File markdownFile = new File(outputDir, MARKDOWN_FILENAME);
        String markdown = buildMarkdown(report);
        try (FileWriter writer = new FileWriter(markdownFile)) {
            writer.write(markdown);
        }
        return markdownFile;
    }

    private List<File> writeStageFiles(File outputDir,
                                       List<ScanOrchestrator.StageExecutionRecord> stageResults) throws IOException {
        List<File> files = new ArrayList<>();
        if (stageResults == null || stageResults.isEmpty()) {
            return files;
        }

        File stagesDir = new File(outputDir, STAGE_DIR_NAME);
        if (!stagesDir.exists() && !stagesDir.mkdirs()) {
            throw new IOException("Failed to create stage data directory: " + stagesDir.getAbsolutePath());
        }

        for (int i = 0; i < stageResults.size(); i++) {
            ScanOrchestrator.StageExecutionRecord record = stageResults.get(i);
            String safeId = record.stage.getId()
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_\\-]", "_");
            String filename = String.format(Locale.US, "%02d_%s.json", i + 1, safeId);
            File stageFile = new File(stagesDir, filename);

            try (FileWriter writer = new FileWriter(stageFile)) {
                writer.write(buildStageJson(record).toString(2));
            } catch (JSONException e) {
                throw new IOException("Failed to write stage file for " + record.stage.getId(), e);
            }

            files.add(stageFile);
        }

        return files;
    }

    private JSONObject buildStageJson(ScanOrchestrator.StageExecutionRecord record) {
        try {
            JSONObject base = record.toJson();

            if (record.result.getError() != null) {
                base.put("error", record.result.getError().getMessage());
            }

            return base;
        } catch (JSONException e) {
            JSONObject fallback = new JSONObject();
            try {
                fallback.put("stageId", record.stage.getId());
                fallback.put("stageName", record.stage.getDisplayName());
                fallback.put("status", record.result.getStatus().name());
                fallback.put("message", record.result.getMessage());
            } catch (JSONException jsonException) {
                // Ignore
            }
            return fallback;
        }
    }

    private File buildArchive(File outputDir,
                              File jsonFile,
                              File markdownFile,
                              File summaryFile,
                              List<File> stageFiles) throws IOException {
        File archiveFile = new File(outputDir, ARCHIVE_FILENAME);

        try (FileOutputStream fos = new FileOutputStream(archiveFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            addFileToZip(zos, jsonFile, jsonFile.getName());
            addFileToZip(zos, markdownFile, markdownFile.getName());
            addFileToZip(zos, summaryFile, summaryFile.getName());

            if (stageFiles != null) {
                for (File stageFile : stageFiles) {
                    String entryName = STAGE_DIR_NAME + "/" + stageFile.getName();
                    addFileToZip(zos, stageFile, entryName);
                }
            }

        }

        return archiveFile;
    }

    private void addFileToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, read);
            }
        }

        zos.closeEntry();
    }

    private String buildMarkdown(ScanReport report) {
        StringBuilder md = new StringBuilder();

        md.append("# OBD-Droid Full Vehicle Scan Report\n\n");
        md.append("_Generated by ReportBuilder v1_\n\n");

        JSONObject metadata = report.getMetadata();
        long timestamp = metadata.optLong("scanTimestamp", System.currentTimeMillis());
        md.append("**Scan ID:** ").append(report.getScanId()).append("  \n");
        md.append("**Generated:** ").append(formatDate(timestamp)).append("  \n");
        md.append(String.format(Locale.US, "**Duration:** %.1f seconds  \n", report.getTotalDurationMs() / 1000.0));
        md.append("**Overall Status:** ").append(report.isSuccess() ? "SUCCESS" : "COMPLETED WITH ERRORS").append("  \n");

        if (metadata.has("sessionId")) {
            md.append("**Session ID:** ").append(metadata.optString("sessionId")).append("  \n");
        }

        md.append("\n");

        md.append("## Vehicle Information\n\n");
        JSONArray infoLines = new JSONArray();
        if (metadata.has("vin")) {
            infoLines.put("VIN: " + metadata.optString("vin"));
        }
        if (metadata.has("year") || metadata.has("make") || metadata.has("model")) {
            String vehicle = String.format(Locale.US, "%s %s %s",
                metadata.optString("year", ""),
                metadata.optString("make", ""),
                metadata.optString("model", "")).trim();
            infoLines.put("Vehicle: " + vehicle.replaceAll(" +", " ").trim());
        }
        if (metadata.has("ecuCount")) {
            infoLines.put("ECUs Discovered: " + metadata.optInt("ecuCount"));
        }

        if (infoLines.length() == 0) {
            md.append("_No vehicle metadata captured._\n\n");
        } else {
            for (int i = 0; i < infoLines.length(); i++) {
                md.append("- ").append(infoLines.optString(i)).append("\n");
            }
            md.append("\n");
        }

        md.append("## Stage Overview\n\n");
        md.append("| # | Stage | Status | Duration | Message |\n");
        md.append("|---|-------|--------|----------|---------|\n");

        List<ScanOrchestrator.StageExecutionRecord> stages = report.getStageResults();
        for (int i = 0; i < stages.size(); i++) {
            ScanOrchestrator.StageExecutionRecord record = stages.get(i);
            String status = formatStatus(record.result.getStatus());
            String duration = formatDuration(record.durationMs);
            String message = sanitizeForTable(record.result.getMessage());

            md.append("| ")
                .append(i + 1)
                .append(" | ")
                .append(escapePipes(record.stage.getDisplayName()))
                .append(" | ")
                .append(status)
                .append(" | ")
                .append(duration)
                .append(" | ")
                .append(message)
                .append(" |\n");
        }

        md.append("\n");

        md.append("## Stage Details\n\n");
        for (int i = 0; i < stages.size(); i++) {
            ScanOrchestrator.StageExecutionRecord record = stages.get(i);
            md.append("### ").append(record.stage.getDisplayName())
                .append(" (`").append(record.stage.getId()).append("`)\n\n");

            md.append("- Status: ").append(record.result.getStatus().name()).append("\n");
            md.append("- Duration: ").append(formatDuration(record.durationMs)).append("\n");
            if (record.result.getMessage() != null && !record.result.getMessage().isEmpty()) {
                md.append("- Message: ").append(record.result.getMessage()).append("\n");
            }
            if (record.result.getError() != null) {
                md.append("- Error: ").append(record.result.getError().getMessage()).append("\n");
            }

            if (record.result.getData() != null) {
                md.append("\n```json\n");
                try {
                    md.append(record.result.getData().toString(2));
                } catch (JSONException e) {
                    md.append(record.result.getData().toString());
                }
                md.append("\n```\n\n");
            } else {
                md.append("\n_No structured data captured for this stage._\n\n");
            }
        }

        md.append("## Files & Sharing\n\n");
        md.append("- `").append(JSON_FILENAME).append("` – Full machine-readable scan output (JSON)\n");
        md.append("- `").append(MARKDOWN_FILENAME).append("` – Human-readable scan summary (Markdown)\n");
        md.append("- `").append(SUMMARY_FILENAME).append("` – Plain-text overview\n");
        md.append("- `").append(ARCHIVE_FILENAME).append("` – Zip bundle containing all artifacts\n");
        if (!report.getStageResults().isEmpty()) {
            md.append("- `").append(STAGE_DIR_NAME).append("/` – Individual stage payloads (JSON)\n");
        }

        md.append("\nGenerated by OBD-Droid ").append(getAppVersion()).append("\n");

        return md.toString();
    }

    private String formatStatus(StageResult.Status status) {
        switch (status) {
            case SUCCESS:
                return "SUCCESS";
            case SKIPPED:
                return "SKIPPED";
            case FAILED:
                return "FAILED";
            case FATAL_ERROR:
                return "FATAL";
            default:
                return status.name();
        }
    }

    private String formatDuration(long durationMs) {
        return String.format(Locale.US, "%.1f s", durationMs / 1000.0);
    }

    private String sanitizeForTable(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String sanitized = value.replace("\n", " ").replace("\r", " ");
        if (sanitized.length() > 120) {
            sanitized = sanitized.substring(0, 117) + "...";
        }
        return escapePipes(sanitized.trim());
    }

    private String escapePipes(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }

    private String formatDate(long timestampMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date(timestampMs));
    }

    private String getAppVersion() {
        try {
            return appContext.getPackageManager()
                .getPackageInfo(appContext.getPackageName(), 0)
                .versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "vUnknown";
        }
    }
}
