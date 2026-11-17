package com.obddroid.ui.activities;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.SnackbarHelper;
import com.obddroid.utils.HelpDialogUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;

import com.obddroid.R;
import com.obddroid.scan.DiagnosticAnalyzer;
import com.obddroid.scan.ReportArtifacts;
import com.obddroid.scan.ReportBuilder;
import com.obddroid.scan.ScanConfiguration;
import com.obddroid.scan.ScanOrchestrator;
import com.obddroid.scan.ScanReport;
import com.obddroid.scan.ScanResultsManager;
import com.obddroid.scan.StageResult;
import com.obddroid.services.VehicleManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.obddroid.features.copilot.data.AgentCoPilotController;
import com.obddroid.features.copilot.ui.CoPilotActivity;

public class ScanActivity extends AppCompatActivity implements ScanOrchestrator.ScanProgressListener {

    private static final String TAG = "ScanActivity";

    private TextView vehicleName;
    private TextView vehicleDetails;
    private TextView progressText;
    private TextView progressPercentage;
    private TextView currentStageText;
    private ProgressBar progressBar;
    private LinearLayout stagesContainer;
    private CardView resultsCard;
    private TextView resultsSummary;
    private CardView aiAnalysisCard;
    private TextView aiAnalysisText;
    private ProgressBar aiAnalysisProgress;
    private VehicleInfoFooter vehicleFooter;
    private View footerOverlay;
    // Buttons removed - now using menu items

    private ScanOrchestrator scanService;
    private boolean serviceBound = false;
    private int totalStages = 0;
    private int completedStages = 0;
    private Map<Integer, View> stageViews = new HashMap<>();
    private ScanReport lastReport;
    private Menu optionsMenu;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ScanOrchestrator.LocalBinder binder = (ScanOrchestrator.LocalBinder) service;
            scanService = binder.getService();
            scanService.setProgressListener(ScanActivity.this);
            serviceBound = true;

            // If scan is already running, update UI
            if (scanService.isScanning()) {
                onScanStartInProgress();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            scanService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        // Set navigation bar color to match footer
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Full Vehicle Scan");
        }

        initializeViews();
        setupListeners();
        loadVehicleInfo();

        // Bind to scan service
        Intent intent = new Intent(this, ScanOrchestrator.class);
        startService(intent); // Ensure service is created
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void initializeViews() {
        vehicleName = findViewById(R.id.vehicle_name);
        vehicleDetails = findViewById(R.id.vehicle_details);
        progressText = findViewById(R.id.progress_text);
        progressPercentage = findViewById(R.id.progress_percentage);
        currentStageText = findViewById(R.id.current_stage_text);
        progressBar = findViewById(R.id.progress_bar);
        stagesContainer = findViewById(R.id.stages_container);
        resultsCard = findViewById(R.id.results_card);
        resultsSummary = findViewById(R.id.results_summary);
        aiAnalysisCard = findViewById(R.id.ai_analysis_card);
        aiAnalysisText = findViewById(R.id.ai_analysis_text);
        aiAnalysisProgress = findViewById(R.id.ai_analysis_progress);
        // Buttons removed - now using menu items

        vehicleFooter = findViewById(R.id.vehicle_footer);
        footerOverlay = findViewById(R.id.footer_overlay);
        if (vehicleFooter != null && footerOverlay != null) {
            vehicleFooter.setOverlayView(footerOverlay);
        }
    }

    private void setupListeners() {
        // Listeners moved to menu - onOptionsItemSelected handles all actions
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_scan, menu);
        this.optionsMenu = menu;
        updateMenuState();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_start_scan) {
            startScan();
            return true;
        } else if (id == R.id.action_analyze_ai) {
            runAIAnalysis();
            return true;
        } else if (id == R.id.action_share_report) {
            shareScanReport();
            return true;
        } else if (id == R.id.action_ask_copilot) {
            openCoPilot();
            return true;
        } else if (id == R.id.action_info) {
            showInfoDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateMenuState() {
        if (optionsMenu == null) return;

        // Default: enable Start Scan, disable others (all visible but greyed out)
        setMenuItemEnabled(R.id.action_start_scan, true);
        setMenuItemEnabled(R.id.action_analyze_ai, false);
        setMenuItemEnabled(R.id.action_share_report, false);
        setMenuItemEnabled(R.id.action_ask_copilot, false);
    }

    private void setMenuItemEnabled(int menuItemId, boolean enabled) {
        if (optionsMenu != null) {
            MenuItem item = optionsMenu.findItem(menuItemId);
            if (item != null) {
                item.setEnabled(enabled);
            }
        }
    }

    private void loadVehicleInfo() {
        VehicleManager vehicleManager = VehicleManager.getInstance(this);

        String displayName = vehicleManager.getVehicleDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            vehicleName.setText(displayName);
        }

        String vin = vehicleManager.getCurrentVIN();
        if (vin != null && !vin.isEmpty()) {
            vehicleDetails.setText("VIN: " + vin + "\nReady for comprehensive diagnostic scan");
        } else {
            vehicleDetails.setText("Ready for comprehensive diagnostic scan");
        }
    }

    private void startScan() {
        if (!serviceBound || scanService == null) {
            showSnackbar("Scan service not ready", SnackbarHelper.MessageType.ERROR);
            return;
        }

        if (scanService.isScanning()) {
            showSnackbar("Scan already in progress", SnackbarHelper.MessageType.INFO);
            return;
        }

        // Reset UI
        stagesContainer.removeAllViews();
        stageViews.clear();
        completedStages = 0;
        totalStages = 0;
        resultsCard.setVisibility(View.GONE);

        // Update menu items - hide all during scan
        setMenuItemEnabled(R.id.action_start_scan, false);
        setMenuItemEnabled(R.id.action_analyze_ai, false);
        setMenuItemEnabled(R.id.action_share_report, false);
        setMenuItemEnabled(R.id.action_ask_copilot, false);

        // Start scan with default configuration
        ScanConfiguration config = ScanConfiguration.getDefault();
        scanService.startScan(config);
    }

    private void cancelScan() {
        if (serviceBound && scanService != null) {
            scanService.cancelScan();
        }
    }

    private void openCoPilot() {
        Intent intent = new Intent(this, CoPilotActivity.class);
        startActivity(intent);
    }

    private void onScanStartInProgress() {
        progressText.setText("Scan in progress...");

        // Update menu items - hide all during scan
        setMenuItemEnabled(R.id.action_start_scan, false);
        setMenuItemEnabled(R.id.action_analyze_ai, false);
        setMenuItemEnabled(R.id.action_share_report, false);
        setMenuItemEnabled(R.id.action_ask_copilot, false);
    }

    @Override
    public void onScanStarted(int totalStages) {
        runOnUiThread(() -> {
            this.totalStages = totalStages;
            this.completedStages = 0;

            progressBar.setMax(totalStages);
            progressBar.setProgress(0);
            progressText.setText("Starting scan...");
            progressPercentage.setText("0%");
            currentStageText.setText(totalStages + " stages to complete");

            vehicleDetails.setText("Scanning in progress - please wait");
            setMenuItemEnabled(R.id.action_analyze_ai, false);
            setMenuItemEnabled(R.id.action_share_report, false);
            setMenuItemEnabled(R.id.action_ask_copilot, false);
        });
    }

    @Override
    public void onStageStarted(int stageIndex, String stageName) {
        runOnUiThread(() -> {
            // Add stage to UI if not already present
            if (!stageViews.containsKey(stageIndex)) {
                View stageView = createStageView(stageName, "in_progress");
                stagesContainer.addView(stageView);
                stageViews.put(stageIndex, stageView);
            }

            updateStageView(stageIndex, stageName, "in_progress", null);

            currentStageText.setText("Stage " + (stageIndex + 1) + "/" + totalStages + ": " + stageName);
            progressText.setText(stageName + "...");
        });
    }

    @Override
    public void onStageCompleted(int stageIndex, StageResult result) {
        runOnUiThread(() -> {
            completedStages++;

            int progress = (int) ((completedStages / (float) totalStages) * 100);
            progressBar.setProgress(completedStages);
            progressPercentage.setText(progress + "%");

            String status = result.isSuccess() ? "success" :
                           result.getStatus() == StageResult.Status.SKIPPED ? "skipped" : "failed";

            updateStageView(stageIndex, null, status, result.getMessage());

            if (completedStages == totalStages) {
                currentStageText.setText("All stages completed");
            }
        });
    }

    @Override
    public void onScanCompleted(ScanReport report) {
        runOnUiThread(() -> {
            this.lastReport = report;

            // Store scan results for CoPilot access
            ScanResultsManager.getInstance(this).storeScanReport(report);

            progressBar.setProgress(totalStages);
            progressPercentage.setText("100%");
            progressText.setText("Scan Complete!");
            currentStageText.setText("Duration: " + (report.getTotalDurationMs() / 1000.0) + " seconds");

            vehicleDetails.setText("Scan completed successfully");

            // Show results
            resultsCard.setVisibility(View.VISIBLE);
            resultsSummary.setText(report.getSummary());

            // Update buttons
            
            
            setMenuItemEnabled(R.id.action_start_scan, true);
            setMenuItemEnabled(R.id.action_analyze_ai, true);
            setMenuItemEnabled(R.id.action_ask_copilot, true);

            ReportArtifacts artifacts = ensureReportArtifacts(report);
            if (artifacts != null) {
                setMenuItemEnabled(R.id.action_share_report, true);
                // Upload scan JSON to CoPilot for retrieval (async)
                uploadScanToCoPilot(report, artifacts);
            } else {
                setMenuItemEnabled(R.id.action_share_report, false);
            }

            showSnackbar("Scan complete! " + report.getStageResults().size() + " stages finished",
                SnackbarHelper.MessageType.SUCCESS);
        });
    }

    private ReportArtifacts ensureReportArtifacts(ScanReport report) {
        if (report == null) {
            return null;
        }

        ReportArtifacts artifacts = report.getArtifacts();
        if (artifacts != null && artifactExists(artifacts)) {
            return artifacts;
        }

        try {
            ReportBuilder builder = new ReportBuilder(this);
            artifacts = builder.build(report);
            report.attachArtifacts(artifacts);
            return artifacts;
        } catch (IOException e) {
            showSnackbar("Unable to prepare report artifacts: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
            return null;
        }
    }

    private boolean artifactExists(ReportArtifacts artifacts) {
        if (artifacts == null) return false;
        return (artifacts.getArchiveFile() != null && artifacts.getArchiveFile().exists()) ||
               (artifacts.getMarkdownFile() != null && artifacts.getMarkdownFile().exists()) ||
               (artifacts.getJsonFile() != null && artifacts.getJsonFile().exists());
    }

    private void shareScanReport() {
        if (lastReport == null) {
            showSnackbar("Run a scan before sharing", SnackbarHelper.MessageType.INFO);
            return;
        }

        ReportArtifacts artifacts = ensureReportArtifacts(lastReport);
        if (artifacts == null) {
            return;
        }

        File shareFile = null;
        String mimeType = "application/zip";

        if (artifacts.getArchiveFile() != null && artifacts.getArchiveFile().exists()) {
            shareFile = artifacts.getArchiveFile();
            mimeType = "application/zip";
        } else if (artifacts.getMarkdownFile() != null && artifacts.getMarkdownFile().exists()) {
            shareFile = artifacts.getMarkdownFile();
            mimeType = "text/markdown";
        } else if (artifacts.getJsonFile() != null && artifacts.getJsonFile().exists()) {
            shareFile = artifacts.getJsonFile();
            mimeType = "application/json";
        } else {
            shareFile = writeSummaryToCache(lastReport);
            mimeType = "text/plain";
        }

        if (shareFile == null || !shareFile.exists()) {
            showSnackbar("No report file available to share", SnackbarHelper.MessageType.WARNING);
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", shareFile);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "OBD-Droid Scan Report " + lastReport.getScanId());
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(shareIntent, "Share Scan Report"));
        } catch (ActivityNotFoundException | SecurityException e) {
            showSnackbar("Unable to share report: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
        }
    }

    private File writeSummaryToCache(ScanReport report) {
        try {
            File cacheDir = new File(getCacheDir(), "scan_reports");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                return null;
            }

            File summaryFile = new File(cacheDir, report.getScanId() + "_summary.txt");
            try (FileWriter writer = new FileWriter(summaryFile, false)) {
                writer.write(report.getSummary());
            }
            return summaryFile;
        } catch (IOException e) {
            showSnackbar("Unable to create summary file: " + e.getMessage(), SnackbarHelper.MessageType.ERROR);
            return null;
        }
    }

    private void runAIAnalysis() {
        if (lastReport == null) {
            showSnackbar("No scan data available for analysis", SnackbarHelper.MessageType.INFO);
            return;
        }

        // AI diagnostic analysis is now integrated with CoPilot
        // Show message directing users to use CoPilot for analysis
        aiAnalysisCard.setVisibility(View.VISIBLE);
        aiAnalysisProgress.setVisibility(View.GONE);
        aiAnalysisText.setText("AI diagnostic analysis is now integrated with CoPilot!\n\n" +
            "Open CoPilot and ask:\n" +
            "• \"Analyze my scan results\"\n" +
            "• \"What do these fault codes mean?\"\n" +
            "• \"What should I fix first?\"\n\n" +
            "CoPilot can run scans, analyze results, and provide detailed repair recommendations - all in one conversation!");
        
        

        // Ensure future taps jump straight into CoPilot
        

        // Launch CoPilot immediately so the user flows straight into analysis
        launchCoPilotAnalysis();
    }

    @Override
    public void onScanCancelled() {
        runOnUiThread(() -> {
            progressText.setText("Scan Cancelled");
            currentStageText.setText("Scan was cancelled by user");
            vehicleDetails.setText("Scan cancelled - partial data may be available");

            
            
            setMenuItemEnabled(R.id.action_start_scan, true);
            setMenuItemEnabled(R.id.action_analyze_ai, false);
            setMenuItemEnabled(R.id.action_share_report, false);
            setMenuItemEnabled(R.id.action_ask_copilot, false);

            showSnackbar("Scan cancelled", SnackbarHelper.MessageType.WARNING);
        });
    }

    @Override
    public void onScanFailed(Exception error) {
        runOnUiThread(() -> {
            progressText.setText("Scan Failed");
            currentStageText.setText("Error: " + error.getMessage());
            vehicleDetails.setText("Scan failed - please try again");

            
            
            setMenuItemEnabled(R.id.action_start_scan, true);
            setMenuItemEnabled(R.id.action_analyze_ai, false);
            setMenuItemEnabled(R.id.action_share_report, false);
            setMenuItemEnabled(R.id.action_ask_copilot, false);

            showSnackbar("Scan failed: " + error.getMessage(), SnackbarHelper.MessageType.ERROR);
        });
    }

    private View createStageView(String stageName, String status) {
        LinearLayout stageLayout = new LinearLayout(this);
        stageLayout.setOrientation(LinearLayout.HORIZONTAL);
        stageLayout.setPadding(0, dpToPx(8), 0, dpToPx(8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        stageLayout.setLayoutParams(params);

        // Status icon
        TextView icon = new TextView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            dpToPx(24), dpToPx(24)
        );
        iconParams.gravity = Gravity.CENTER_VERTICAL;
        iconParams.setMarginEnd(dpToPx(12));
        icon.setLayoutParams(iconParams);
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(16);
        icon.setTag("icon");

        // Stage name
        TextView nameText = new TextView(this);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        nameText.setLayoutParams(nameParams);
        nameText.setText(stageName);
        nameText.setTextColor(getColor(R.color.text_primary_dark));
        nameText.setTextSize(14);
        nameText.setTag("name");

        // Message text (initially hidden)
        TextView messageText = new TextView(this);
        messageText.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        messageText.setTextColor(getColor(R.color.text_secondary_dark));
        messageText.setTextSize(12);
        messageText.setVisibility(View.GONE);
        messageText.setTag("message");

        stageLayout.addView(icon);
        stageLayout.addView(nameText);

        updateStageStatus(stageLayout, status, null);

        return stageLayout;
    }

    private void updateStageView(int stageIndex, String stageName, String status, String message) {
        View stageView = stageViews.get(stageIndex);
        if (stageView != null && stageView instanceof LinearLayout) {
            if (stageName != null) {
                TextView nameText = stageView.findViewWithTag("name");
                if (nameText != null) {
                    nameText.setText(stageName);
                }
            }
            updateStageStatus((LinearLayout) stageView, status, message);
        }
    }

    private void updateStageStatus(LinearLayout stageLayout, String status, String message) {
        TextView icon = stageLayout.findViewWithTag("icon");

        if (icon != null) {
            switch (status) {
                case "pending":
                    icon.setText("○");
                    icon.setTextColor(getColor(R.color.text_secondary_dark));
                    break;
                case "in_progress":
                    icon.setText("⟳");
                    icon.setTextColor(getColor(R.color.colorPrimary));
                    break;
                case "success":
                    icon.setText("✓");
                    icon.setTextColor(getColor(R.color.fault_success));
                    break;
                case "skipped":
                    icon.setText("⊘");
                    icon.setTextColor(getColor(R.color.text_secondary_dark));
                    break;
                case "failed":
                    icon.setText("✗");
                    icon.setTextColor(getColor(R.color.fault_error));
                    break;
            }
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void showSnackbar(String message, SnackbarHelper.MessageType type) {
        SnackbarHelper.showSnackbar(this, message, type);
    }

    /**
     * Upload scan JSON to CoPilot for retrieval in future conversations.
     * Enables queries like "Compare this scan to my last scan" or "When was P0420 first detected?"
     */
    private void uploadScanToCoPilot(ScanReport report, ReportArtifacts artifacts) {
        if (artifacts == null) {
            return;
        }

        new Thread(() -> {
            try {
                if (artifacts.getJsonFile() == null) {
                    Log.w(TAG, "No JSON file to upload for scan " + report.getScanId());
                    return;
                }

                java.io.File jsonFile = artifacts.getJsonFile();
                if (!jsonFile.exists()) {
                    Log.w(TAG, "JSON file doesn't exist: " + jsonFile.getPath());
                    return;
                }

                // Upload file to OpenAI
                com.obddroid.features.copilot.data.AgentApiClient apiClient =
                    new com.obddroid.features.copilot.data.AgentApiClient(getApplicationContext());

                String fileId = apiClient.uploadFile(jsonFile, "assistants");
                Log.i(TAG, "Uploaded scan to CoPilot: file_id=" + fileId + ", scan_id=" + report.getScanId());

                com.obddroid.scan.ScanResultsManager.getInstance(getApplicationContext())
                    .recordUploadedFile(report.getScanId(), fileId);

                AgentCoPilotController.getInstance().refreshFileSearchIndex();

            } catch (Exception e) {
                Log.e(TAG, "Failed to upload scan to CoPilot", e);
                // Don't show error to user - this is a background enhancement
            }
        }).start();
    }

    private void launchCoPilotAnalysis() {
        Intent intent = new Intent(this, CoPilotActivity.class);
        intent.putExtra(CoPilotActivity.EXTRA_INITIAL_MESSAGE,
            "Analyze my latest scan results and tell me what's wrong. What should I fix first?");
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private void showInfoDialog() {
        HelpDialogUtils.showHelpDialog(
            this,
            R.string.scan_info_title,
            R.string.scan_info_message,
            R.string.scan_info_ack,
            android.R.drawable.ic_menu_info_details
        );
    }
}
