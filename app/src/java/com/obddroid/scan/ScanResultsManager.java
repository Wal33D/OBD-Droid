package com.obddroid.scan;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manager for accessing and storing scan results.
 * Persists lightweight metadata so completed scans can be reloaded by CoPilot tools.
 */
public class ScanResultsManager {

    private static final String TAG = "ScanResultsManager";
    private static final int MAX_REMEMBERED_SCANS = 25;
    private static final int CACHE_SIZE = 6;
    private static final String INDEX_DIR = "scan_results";
    private static final String INDEX_FILE = "index.json";
    private static final String REPORT_JSON = "scan_report.json";
    private static final String REPORT_MARKDOWN = "scan_report.md";
    private static final String REPORT_SUMMARY = "scan_summary.txt";
    private static final String REPORT_ARCHIVE = "scan_bundle.zip";
    private static final String STAGE_DIR = "stage_data";

    private static ScanResultsManager instance;

    private final Context context;
    private final File indexFile;
    private final Deque<ScanEntry> entries = new ArrayDeque<>();
    private final Map<String, ScanReport> reportCache = new LinkedHashMap<String, ScanReport>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ScanReport> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private boolean indexLoaded = false;

    private ScanResultsManager(Context context) {
        this.context = context.getApplicationContext();
        File dir = new File(this.context.getFilesDir(), INDEX_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Unable to create scan index directory: " + dir.getAbsolutePath());
        }
        this.indexFile = new File(dir, INDEX_FILE);
    }

    public static synchronized ScanResultsManager getInstance(Context context) {
        if (instance == null) {
            instance = new ScanResultsManager(context);
        }
        return instance;
    }

    /**
     * Persist the supplied scan report metadata and keep a live reference in cache.
     */
    public synchronized void storeScanReport(ScanReport report) {
        if (report == null) {
            return;
        }
        loadIndexIfNeeded();

        ScanEntry entry = new ScanEntry();
        entry.scanId = report.getScanId();
        entry.outputDir = report.getOutputDirectory() != null
            ? report.getOutputDirectory().getAbsolutePath()
            : null;
        entry.timestamp = report.getMetadata().optLong("scanTimestamp", System.currentTimeMillis());
        entry.totalDurationMs = report.getTotalDurationMs();
        entry.success = report.isSuccess();
        entry.vin = report.getMetadata().optString("vin", null);
        entry.vehicleLabel = buildVehicleLabel(report.getMetadata());
        entry.stageCount = report.getStageResults() != null ? report.getStageResults().size() : 0;
        entry.fileId = null; // filled once upload completes
        entry.fileAttached = false;

        // Remove any previous record for this scan
        removeEntryIfExists(entry.scanId);
        entries.addFirst(entry);
        trimEntriesIfNeeded();

        reportCache.put(entry.scanId, report);
        persistIndexSafe();
    }

    /**
     * Associate an uploaded OpenAI file ID with the stored scan so CoPilot can retrieve it later.
     */
    public synchronized void recordUploadedFile(String scanId, String fileId) {
        if (TextUtils.isEmpty(scanId) || TextUtils.isEmpty(fileId)) {
            return;
        }
        loadIndexIfNeeded();
        ScanEntry entry = findEntry(scanId);
        if (entry != null) {
            entry.fileId = fileId;
            entry.fileAttached = false;
            persistIndexSafe();
        }
    }

    /**
     * Return the uploaded file id for a scan, or null if none recorded yet.
     */
    public synchronized String getUploadedFileId(String scanId) {
        if (TextUtils.isEmpty(scanId)) {
            return null;
        }
        loadIndexIfNeeded();
        ScanEntry entry = findEntry(scanId);
        return entry != null ? entry.fileId : null;
    }

    public synchronized ScanReport getLatestScan() {
        loadIndexIfNeeded();
        ScanEntry entry = entries.peekFirst();
        return entry != null ? loadReport(entry) : null;
    }

    public synchronized ScanReport getScanById(String scanId) {
        if (TextUtils.isEmpty(scanId)) {
            return null;
        }
        loadIndexIfNeeded();
        ScanEntry entry = findEntry(scanId);
        return entry != null ? loadReport(entry) : null;
    }

    public synchronized String getRecentScans(int limit) {
        loadIndexIfNeeded();
        if (entries.isEmpty()) {
            return "No scans recorded yet.";
        }

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        int count = 0;
        for (ScanEntry entry : entries) {
            if (count >= limit) {
                break;
            }
            if (count > 0) {
                sb.append("\n");
            }
            sb.append("#").append(count + 1).append(" ");
            sb.append(entry.scanId);
            if (!TextUtils.isEmpty(entry.vehicleLabel)) {
                sb.append(" – ").append(entry.vehicleLabel);
            }
            if (!TextUtils.isEmpty(entry.vin)) {
                sb.append(" (VIN ").append(entry.vin).append(")");
            }
            sb.append(" @ ").append(sdf.format(new Date(entry.timestamp)));
            sb.append(" [").append(entry.success ? "OK" : "Issues").append("]");
            count++;
        }
        return sb.toString();
    }

    public synchronized boolean hasScans() {
        loadIndexIfNeeded();
        return !entries.isEmpty();
    }

    public synchronized JSONObject getLatestScanSummary() {
        ScanReport report = getLatestScan();
        if (report == null) {
            return null;
        }
        JSONObject summary = new JSONObject();
        try {
            JSONObject metadata = report.getMetadata();
            summary.put("scanId", report.getScanId());
            summary.put("totalDurationMs", report.getTotalDurationMs());
            summary.put("success", report.isSuccess());
            summary.put("stageCount", report.getStageResults() != null ? report.getStageResults().size() : 0);
            if (metadata != null) {
                summary.put("metadata", new JSONObject(metadata.toString()));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to build summary JSON", e);
        }
        return summary;
    }

    public synchronized ScanSummary getLatestScanSummaryModel() {
        loadIndexIfNeeded();
        ScanEntry entry = entries.peekFirst();
        return entry != null ? toSummary(entry) : null;
    }

    public synchronized ScanSummary getLatestUploadedScanPendingAttachment() {
        loadIndexIfNeeded();
        for (ScanEntry entry : entries) {
            if (!TextUtils.isEmpty(entry.fileId) && !entry.fileAttached) {
                return toSummary(entry);
            }
        }
        return null;
    }

    public synchronized void markFileAttached(String scanId) {
        if (TextUtils.isEmpty(scanId)) {
            return;
        }
        loadIndexIfNeeded();
        ScanEntry entry = findEntry(scanId);
        if (entry != null) {
            entry.fileAttached = true;
            persistIndexSafe();
        }
    }

    public synchronized List<ScanSummary> getRecentScanSummaries(int limit) {
        loadIndexIfNeeded();
        if (entries.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        List<ScanSummary> summaries = new ArrayList<>();
        int count = 0;
        for (ScanEntry entry : entries) {
            summaries.add(toSummary(entry));
            count++;
            if (count >= limit) {
                break;
            }
        }
        return Collections.unmodifiableList(summaries);
    }

    public synchronized List<ScanSummary> getRecentScanSummariesForVin(String vin, int limit) {
        loadIndexIfNeeded();
        if (entries.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        List<ScanSummary> summaries = new ArrayList<>();
        int count = 0;
        for (ScanEntry entry : entries) {
            if (vin == null || vin.equals(entry.vin)) {
                summaries.add(toSummary(entry));
                count++;
                if (count >= limit) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(summaries);
    }

    // --- Internal helpers -------------------------------------------------

    private void loadIndexIfNeeded() {
        if (indexLoaded) {
            return;
        }
        entries.clear();
        if (!indexFile.exists()) {
            indexLoaded = true;
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(indexFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            if (sb.length() == 0) {
                indexLoaded = true;
                return;
            }

            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("entries");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) {
                        continue;
                    }
                    ScanEntry entry = ScanEntry.fromJson(obj);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to read scan index", e);
        } finally {
            indexLoaded = true;
        }
    }

    private void persistIndexSafe() {
        try {
            writeIndex();
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to persist scan index", e);
        }
    }

    private void writeIndex() throws IOException, JSONException {
        JSONArray arr = new JSONArray();
        for (ScanEntry entry : entries) {
            arr.put(entry.toJson());
        }
        JSONObject root = new JSONObject();
        root.put("entries", arr);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(indexFile, false))) {
            writer.write(root.toString(2));
        }
    }

    private ScanEntry findEntry(String scanId) {
        for (ScanEntry entry : entries) {
            if (scanId.equals(entry.scanId)) {
                return entry;
            }
        }
        return null;
    }

    private void removeEntryIfExists(String scanId) {
        ScanEntry existing = findEntry(scanId);
        if (existing != null) {
            entries.remove(existing);
            reportCache.remove(scanId);
        }
    }

    private void trimEntriesIfNeeded() {
        while (entries.size() > MAX_REMEMBERED_SCANS) {
            ScanEntry removed = entries.removeLast();
            if (removed != null) {
                reportCache.remove(removed.scanId);
            }
        }
    }

    private ScanReport loadReport(ScanEntry entry) {
        if (entry == null) {
            return null;
        }
        ScanReport cached = reportCache.get(entry.scanId);
        if (cached != null) {
            return cached;
        }

        try {
            ScanReport report = loadReportFromDisk(entry);
            if (report != null) {
                reportCache.put(entry.scanId, report);
            }
            return report;
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to load scan " + entry.scanId, e);
            return null;
        }
    }

    private ScanReport loadReportFromDisk(ScanEntry entry) throws IOException, JSONException {
        if (TextUtils.isEmpty(entry.outputDir)) {
            return null;
        }

        File outputDir = new File(entry.outputDir);
        if (!outputDir.exists()) {
            Log.w(TAG, "Scan output directory missing, dropping entry: " + entry.outputDir);
            entries.remove(entry);
            return null;
        }

        File jsonFile = new File(outputDir, REPORT_JSON);
        if (!jsonFile.exists()) {
            Log.w(TAG, "scan_report.json missing for " + entry.scanId);
            return null;
        }

        String jsonString = readAll(jsonFile);
        JSONObject root = new JSONObject(jsonString);
        JSONObject metadata = root.optJSONObject("metadata");
        JSONArray stages = root.optJSONArray("stages");

        List<ScanOrchestrator.StageExecutionRecord> stageRecords = parseStageRecords(stages);
        ScanReport report = ScanReport.fromStoredData(entry.scanId, metadata, outputDir, stageRecords);

        // Attach existing artifacts so sharing/export reuses on-disk files.
        report.attachArtifacts(buildArtifacts(outputDir));

        // If metadata lacked timestamp, supplement it with entry timestamp
        if (metadata != null && !metadata.has("scanTimestamp")) {
            try {
                metadata.put("scanTimestamp", entry.timestamp);
            } catch (JSONException e) {
                Log.w(TAG, "Unable to backfill scan timestamp", e);
            }
        }

        return report;
    }

    private List<ScanOrchestrator.StageExecutionRecord> parseStageRecords(JSONArray stages) throws JSONException {
        if (stages == null || stages.length() == 0) {
            return Collections.emptyList();
        }

        List<ScanOrchestrator.StageExecutionRecord> records = new ArrayList<>();
        for (int i = 0; i < stages.length(); i++) {
            JSONObject obj = stages.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            String stageId = obj.optString("stageId", "UNKNOWN_STAGE");
            String stageName = obj.optString("stageName", stageId);
            long startTime = obj.optLong("startTime", 0L);
            long durationMs = obj.optLong("durationMs", 0L);
            int estimateSeconds = obj.optInt("estimatedDurationSeconds", durationMs > 0 ? (int) Math.max(1, durationMs / 1000L) : 5);

            String statusValue = obj.optString("status", StageResult.Status.FAILED.name());
            StageResult.Status status;
            try {
                status = StageResult.Status.valueOf(statusValue);
            } catch (IllegalArgumentException ex) {
                status = StageResult.Status.FAILED;
            }
            String message = obj.optString("message", "");
            JSONObject data = obj.optJSONObject("data");

            StageResult result;
            switch (status) {
                case SUCCESS:
                    result = StageResult.success(message, data);
                    break;
                case SKIPPED:
                    result = StageResult.skipped(message != null ? message : "Stage skipped");
                    break;
                case FATAL_ERROR:
                    result = StageResult.fatalError(message != null ? message : "Stage fatal error", null);
                    break;
                case FAILED:
                default:
                    result = StageResult.failed(message != null ? message : "Stage failed", null);
                    break;
            }

            ScanStage stage = new StoredStage(stageId, stageName, estimateSeconds);
            records.add(new ScanOrchestrator.StageExecutionRecord(stage, result, startTime, durationMs));
        }
        return records;
    }

    private ReportArtifacts buildArtifacts(File outputDir) {
        File jsonFile = fileIfExists(new File(outputDir, REPORT_JSON));
        File markdownFile = fileIfExists(new File(outputDir, REPORT_MARKDOWN));
        File summaryFile = fileIfExists(new File(outputDir, REPORT_SUMMARY));
        File archiveFile = fileIfExists(new File(outputDir, REPORT_ARCHIVE));

        List<File> stageFiles = new ArrayList<>();
        File stageDir = new File(outputDir, STAGE_DIR);
        if (stageDir.exists()) {
            File[] files = stageDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname != null && pathname.isFile() && pathname.getName().endsWith(".json");
                }
            });
            if (files != null) {
                ArrayList<File> list = new ArrayList<>();
                Collections.addAll(list, files);
                Collections.sort(list);
                stageFiles.addAll(list);
            }
        }

        return new ReportArtifacts(jsonFile, markdownFile, summaryFile, archiveFile, stageFiles);
    }

    private File fileIfExists(File file) {
        return file != null && file.exists() ? file : null;
    }

    private String readAll(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String buildVehicleLabel(JSONObject metadata) {
        if (metadata == null) {
            return null;
        }
        String make = metadata.optString("make", "");
        String model = metadata.optString("model", "");
        int year = metadata.optInt("year", 0);
        if (TextUtils.isEmpty(make) && TextUtils.isEmpty(model) && year == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (year > 0) {
            sb.append(year).append(" ");
        }
        if (!TextUtils.isEmpty(make)) {
            sb.append(make);
        }
        if (!TextUtils.isEmpty(model)) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(model);
        }
        return sb.toString().trim();
    }

    // --- Helper data structures -------------------------------------------

    public static final class ScanSummary {
        public final String scanId;
        public final String vin;
        public final String vehicleLabel;
        public final long timestamp;
        public final long totalDurationMs;
        public final boolean success;
        public final int stageCount;
        public final String fileId;
        public final boolean fileAttached;

        private ScanSummary(String scanId,
                            String vin,
                            String vehicleLabel,
                            long timestamp,
                            long totalDurationMs,
                            boolean success,
                            int stageCount,
                            String fileId,
                            boolean fileAttached) {
            this.scanId = scanId;
            this.vin = vin;
            this.vehicleLabel = vehicleLabel;
            this.timestamp = timestamp;
            this.totalDurationMs = totalDurationMs;
            this.success = success;
            this.stageCount = stageCount;
            this.fileId = fileId;
            this.fileAttached = fileAttached;
        }
    }

    private ScanSummary toSummary(ScanEntry entry) {
        if (entry == null) {
            return null;
        }
        return new ScanSummary(
            entry.scanId,
            entry.vin,
            entry.vehicleLabel,
            entry.timestamp,
            entry.totalDurationMs,
            entry.success,
            entry.stageCount,
            entry.fileId,
            entry.fileAttached
        );
    }

    private static final class ScanEntry {
        String scanId;
        String outputDir;
        long timestamp;
        long totalDurationMs;
        boolean success;
        String vin;
        String vehicleLabel;
        int stageCount;
        String fileId;
        boolean fileAttached;

        JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("scanId", scanId);
            obj.put("outputDir", outputDir);
            obj.put("timestamp", timestamp);
            obj.put("totalDurationMs", totalDurationMs);
            obj.put("success", success);
            obj.put("stageCount", stageCount);
            if (!TextUtils.isEmpty(vin)) {
                obj.put("vin", vin);
            }
            if (!TextUtils.isEmpty(vehicleLabel)) {
                obj.put("vehicleLabel", vehicleLabel);
            }
            if (!TextUtils.isEmpty(fileId)) {
                obj.put("fileId", fileId);
            }
            obj.put("fileAttached", fileAttached);
            return obj;
        }

        static ScanEntry fromJson(JSONObject obj) {
            if (obj == null) {
                return null;
            }
            ScanEntry entry = new ScanEntry();
            entry.scanId = obj.optString("scanId", null);
            entry.outputDir = obj.optString("outputDir", null);
            entry.timestamp = obj.optLong("timestamp", System.currentTimeMillis());
            entry.totalDurationMs = obj.optLong("totalDurationMs", 0L);
            entry.success = obj.optBoolean("success", true);
            entry.stageCount = obj.optInt("stageCount", 0);
            entry.vin = obj.optString("vin", null);
            entry.vehicleLabel = obj.optString("vehicleLabel", null);
            entry.fileId = obj.optString("fileId", null);
            entry.fileAttached = obj.optBoolean("fileAttached", false);

            if (TextUtils.isEmpty(entry.scanId) || TextUtils.isEmpty(entry.outputDir)) {
                return null;
            }
            return entry;
        }
    }

    private static final class StoredStage implements ScanStage {
        private final String id;
        private final String displayName;
        private final int estimatedDuration;

        StoredStage(String id, String displayName, int estimatedDuration) {
            this.id = id;
            this.displayName = displayName;
            this.estimatedDuration = estimatedDuration;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public int getEstimatedDurationSeconds() {
            return estimatedDuration;
        }

        @Override
        public StageResult execute(ScanContext context) {
            throw new UnsupportedOperationException("Stored stage cannot be executed.");
        }

        @Override
        public boolean shouldSkip(ScanContext context) {
            return false;
        }
    }
}
