package com.obddroid.features.emissions.ui;

import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;

import org.json.JSONArray;
import org.json.JSONObject;

import com.obddroid.R;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;
import com.obddroid.common.ProcessVariables.PvChangeEvent;
import com.obddroid.common.ProcessVariables.PvChangeListener;
import com.obddroid.features.emissions.data.EmissionsCalculator;
import com.obddroid.features.emissions.data.EmissionsDataManager;
import com.obddroid.features.emissions.data.EmissionsReportGenerator;
import com.obddroid.features.emissions.data.MonitorData;
import com.obddroid.services.CommService;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.ReportFileWriter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.io.IOException;

/**
 * Emissions Diagnostics Activity
 *
 * Displays comprehensive emissions monitoring information:
 * - Monitor readiness status (Mode 1 PID 0x01)
 * - IUMPR performance data (Mode 9 PID 0x08)
 * - Combined visual dashboard
 * - Pre-emissions test readiness check
 *
 * This combines data from multiple OBD services to provide mechanics
 * and vehicle owners with complete emissions system health information.
 */
public class EmissionsActivity extends AppCompatActivity implements PvChangeListener {

    private static final Logger log = Logger.getLogger(EmissionsActivity.class.getName());

    // UI Components
    private TextView overallStatusText;
    private TextView overallStatusSubtext;
    private TextView lastUpdatedText;
    private TextView statusIcon;
    private LinearLayout emissionsStatusBanner;
    private LinearLayout monitorsContainer;
    private VehicleInfoFooter vehicleInfoFooter;
    private View snackbarAnchor;  // Anchor view for snackbars

    // Scan screen components
    private View emptyView;
    private Button scanButton;
    private NestedScrollView contentScrollView;
    private boolean hasScanned = false;

    // Data layer components
    private EmissionsDataManager dataManager;

    // Update handler
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private static final long UPDATE_INTERVAL = 2000; // Update every 2 seconds

    // Periodic update runnable (field-based for precise cleanup)
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateDisplay();
            updateHandler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

    // Service management
    private boolean dataQueryInProgress = false;

    // Colors
    private static final int COLOR_READY = Color.parseColor("#4CAF50");      // Green
    private static final int COLOR_NOT_READY = Color.parseColor("#FFC107");  // Amber
    private static final int COLOR_ERROR = Color.parseColor("#F44336");       // Red
    private static final int COLOR_UNKNOWN = Color.parseColor("#9E9E9E");    // Gray


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.info("=== EmissionsActivity onCreate() ===");

        setContentView(R.layout.activity_emissions);

        // Set navigation bar color to match footer
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));  // #212121
        }

        // Setup action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Emissions Diagnostics");
        }

        // Initialize views
        initializeViews();

        // Initialize data layer
        dataManager = new EmissionsDataManager();

        // Setup scan button click handler
        setupScanButton();

        log.info("EmissionsActivity initialized - showing empty state");
    }

    private void initializeViews() {
        // Scan screen components
        emptyView = findViewById(R.id.empty_view);
        scanButton = findViewById(R.id.scan_button);
        contentScrollView = findViewById(R.id.content_scroll_view);

        // Content views
        emissionsStatusBanner = findViewById(R.id.emissions_status_banner);
        statusIcon = findViewById(R.id.status_icon);
        overallStatusText = findViewById(R.id.overall_status_text);
        overallStatusSubtext = findViewById(R.id.overall_status_subtext);
        lastUpdatedText = findViewById(R.id.last_updated_text);
        monitorsContainer = findViewById(R.id.monitors_container);
        vehicleInfoFooter = findViewById(R.id.vehicle_info_footer);
        snackbarAnchor = findViewById(R.id.content_frame);  // Use CoordinatorLayout for snackbar creation

        log.info("Views initialized");
    }

    private void setupScanButton() {
        if (scanButton != null) {
            scanButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startEmissionsScan();
                }
            });
        }
    }

    /**
     * ULTRA-SIMPLE scan transition - no delays, no handlers, just instant state switching!
     */
    private void startEmissionsScan() {
        log.info("=== Starting emissions scan (instant transition) ===");

        // 1. Hide empty state, show content - SYNCHRONOUSLY
        emptyView.setVisibility(View.GONE);
        contentScrollView.setVisibility(View.VISIBLE);

        // 2. Mark as scanned
        hasScanned = true;

        // 3. Start periodic updates
        startPeriodicUpdates();

        // 4. Trigger immediate display update
        updateDisplay();

        log.info("Emissions scan complete - content visible!");
    }

    /**
     * Show a snackbar anchored above the vehicle footer
     */
    private void showSnackbar(String message) {
        com.google.android.material.snackbar.Snackbar snackbar =
            com.google.android.material.snackbar.Snackbar.make(snackbarAnchor, message,
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT);
        snackbar.setAnchorView(vehicleInfoFooter);  // Position above the footer
        snackbar.getView().setElevation(6f);  // Lower than footer's 8f so it slides from behind
        snackbar.show();
    }


    @Override
    protected void onResume() {
        super.onResume();
        log.info("=== EmissionsActivity onResume() ===");

        // Register PV change listeners (only for ADD and MODIFY events to reduce overhead)
        if (ObdProt.PidPvs != null) {
            ObdProt.PidPvs.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED | PvChangeEvent.PV_MODIFIED);
        }
        if (ObdProt.VidPvs != null) {
            ObdProt.VidPvs.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED | PvChangeEvent.PV_MODIFIED);
        }

        // Request live sensor data from vehicle (Mode 1)
        // This is critical for emissions diagnostics - we need real-time O2 sensors, fuel trim, etc.
        if (CommService.elm != null) {
            log.info("Setting OBD service to OBD_SVC_DATA (Mode 1) for live sensor data");
            CommService.elm.setService(ObdProt.OBD_SVC_DATA);
        }

        // Only request data and update display if we've already scanned
        // Otherwise, wait for scan button click
        if (hasScanned) {
            log.info("Already scanned - requesting emissions data and updating display");
            requestEmissionsData();
            updateDisplay();
        } else {
            log.info("Not scanned yet - showing empty state, waiting for scan button");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister listeners
        if (ObdProt.PidPvs != null) {
            ObdProt.PidPvs.removePvChangeListener(this);
        }
        if (ObdProt.VidPvs != null) {
            ObdProt.VidPvs.removePvChangeListener(this);
        }

        // Stop updates - precise callback removal matches LiveDataActivity pattern
        updateHandler.removeCallbacks(updateRunnable);

        // Only stop OBD polling if activity is finishing, otherwise keep it active
        // This matches LiveDataActivity and FuelEconomyActivity pattern
        if (isFinishing()) {
            if (CommService.elm != null) {
                log.info("Activity finishing - stopping OBD service");
                CommService.elm.setService(ObdProt.OBD_SVC_NONE);
            }
        } else {
            log.info("Activity pausing but not finishing - keeping OBD service active");
        }

        dataQueryInProgress = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Backup cleanup of listeners in case onPause() didn't run
        // This matches FuelEconomyActivity pattern for safety
        if (ObdProt.PidPvs != null) {
            ObdProt.PidPvs.removePvChangeListener(this);
        }
        if (ObdProt.VidPvs != null) {
            ObdProt.VidPvs.removePvChangeListener(this);
        }

        log.info("=== EmissionsActivity destroyed ===");
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        log.info("onCreateOptionsMenu called - inflating menu");
        getMenuInflater().inflate(R.menu.emissions_menu, menu);
        log.info("Menu inflated, items: " + menu.size());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        log.info("onOptionsItemSelected: ID=" + item.getItemId());

        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_rescan) {
            log.info("Rescan requested from overflow menu");
            showSnackbar("Rescanning emissions data...");
            // Clear existing data and request fresh data from vehicle
            if (CommService.elm != null) {
                log.info("Clearing cached emissions data for fresh scan");
                // Request fresh data from vehicle
                startEmissionsDataRequest();
            } else {
                showSnackbar("Not connected to vehicle");
            }
            return true;
        } else if (item.getItemId() == R.id.action_export) {
            log.info("Save Report requested from overflow menu");
            showSaveReportDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void pvChanged(PvChangeEvent event) {
        // Debounce updates to prevent memory exhaustion from rapid OBD data changes
        // With 80+ PIDs updating, this was being called 100+ times/second causing OOM
        updateHandler.removeCallbacksAndMessages(null);
        updateHandler.postDelayed(this::updateDisplay, UPDATE_INTERVAL);
    }

    /**
     * Request emissions data from the vehicle
     * This is called when the activity loads to display existing cached data
     * For fresh data requests, use startEmissionsDataRequest()
     */
    private void requestEmissionsData() {
        log.info("=== Checking for emissions data ===");

        // Check if data already exists
        boolean hasPidData = ObdProt.PidPvs != null && !ObdProt.PidPvs.isEmpty();
        boolean hasVidData = ObdProt.VidPvs != null && !ObdProt.VidPvs.isEmpty();
        log.info("PidPvs: " + (hasPidData ? ObdProt.PidPvs.size() + " items" : "empty/null"));
        log.info("VidPvs: " + (hasVidData ? ObdProt.VidPvs.size() + " items" : "empty/null"));

        if (CommService.elm == null) {
            log.warning("Not connected to vehicle - will display cached data if available");
            updateDisplay();
            return;
        }

        log.info("Connected - current OBD service: " + CommService.elm.getService());

        // If we don't have data and we're connected, start requesting it
        if (!hasPidData || !hasVidData) {
            log.info("Missing emissions data - starting data request from vehicle");
            startEmissionsDataRequest();
        } else {
            log.info("Cached emissions data available - displaying");
            updateDisplay();
        }
    }

    /**
     * Start a fresh emissions data request from the vehicle
     * This requests Mode 1 live data (already set in onResume)
     */
    private void startEmissionsDataRequest() {
        if (CommService.elm == null) {
            log.warning("Cannot request emissions data - not connected to vehicle");
            showSnackbar("Not connected to vehicle");
            return;
        }

        if (dataQueryInProgress) {
            log.info("Data query already in progress, skipping duplicate request");
            return;
        }

        log.info("Requesting fresh emissions data from vehicle");
        dataQueryInProgress = true;

        // Service is already set to OBD_SVC_DATA in onResume()
        // Data will flow automatically through PvChangeListener callbacks

        // Update display to show current data
        updateDisplay();
        dataQueryInProgress = false;
    }

    private void startPeriodicUpdates() {
        updateHandler.postDelayed(updateRunnable, UPDATE_INTERVAL);
    }

    private boolean hasExportableData() {
        for (MonitorData monitor : dataManager.getMonitorDataMap().values()) {
            if (monitor.isAvailable || monitor.conditions > 0 || monitor.completions > 0) {
                return true;
            }
        }
        return false;
    }

    private java.util.List<MonitorData> getOrderedMonitorList() {
        java.util.List<MonitorData> ordered = new java.util.ArrayList<>();
        ordered.add(dataManager.getMonitorDataMap().get("MISFIRE"));
        ordered.add(dataManager.getMonitorDataMap().get("FUEL"));
        ordered.add(dataManager.getMonitorDataMap().get("CCM"));
        ordered.add(dataManager.getMonitorDataMap().get("CATALYST"));
        ordered.add(dataManager.getMonitorDataMap().get("EVAP"));
        ordered.add(dataManager.getMonitorDataMap().get("O2SENSOR"));
        ordered.add(dataManager.getMonitorDataMap().get("O2HEATER"));
        ordered.add(dataManager.getMonitorDataMap().get("EGR"));
        ordered.add(dataManager.getMonitorDataMap().get("AIR"));
        return ordered;
    }

    private void exportEmissionsReport() {
        if (!hasExportableData()) {
            showSnackbar("No emissions data available to export yet");
            return;
        }

        // Ensure latest values are displayed/exported
        updateDisplay();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String displayTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String fileName = "emissions_report_" + timestamp + ".csv";

        try {
            ReportFileWriter.export(this, fileName, ReportFileWriter.MIME_CSV, writer -> {
                // Compute summary stats
                int availableCount = 0;
                int readyCount = 0;
                for (MonitorData monitor : dataManager.getMonitorDataMap().values()) {
                    if (monitor.isAvailable) {
                        availableCount++;
                        if (monitor.isReady) {
                            readyCount++;
                        }
                    }
                }
                int notReadyCount = availableCount - readyCount;

                writer.write(String.format(Locale.US,
                        "OBD-Droid Emissions Report,%s\n", displayTimestamp));
                writer.write(String.format(Locale.US,
                        "Available Monitors,%d\nReady Monitors,%d\nMonitors Needing Drive Cycle,%d\n\n",
                        availableCount, readyCount, notReadyCount));
                writer.write("Monitor,Available,Ready,Completions,Conditions,Completion %,IUMPR Quality\n");

                for (MonitorData monitor : getOrderedMonitorList()) {
                    if (monitor == null) {
                        continue;
                    }
                    String completionPercent = monitor.getPercentageDisplay();
                    String quality = monitor.getIUMPRQuality();
                    // Avoid commas disrupting CSV by replacing with semicolons
                    if (quality != null) {
                        quality = quality.replace(",", ";");
                    } else {
                        quality = "";
                    }

                    writer.write(String.format(Locale.US,
                            "\"%s\",%s,%s,%d,%d,%s,%s\n",
                            monitor.name,
                            monitor.isAvailable ? "Yes" : "No",
                            monitor.isReady ? "Yes" : "No",
                            monitor.completions,
                            monitor.conditions,
                            completionPercent,
                            quality));
                }
            });
            showSnackbar("Emissions report saved: " + fileName);
            log.info("Emissions report exported successfully: " + fileName);
        } catch (IOException e) {
            log.warning("Failed to export emissions report: " + e.getMessage());
            showSnackbar("Failed to export emissions report");
        }
    }

    /**
     * Show dialog to choose export format (PDF, CSV, or JSON)
     */
    private void showSaveReportDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_save_report);
        dialog.setCancelable(true);

        // Hide PDF option (only used by Vehicle History)
        View pdfOption = dialog.findViewById(R.id.option_export_pdf);
        if (pdfOption != null) {
            pdfOption.setVisibility(View.GONE);
        }

        // CSV option
        View csvOption = dialog.findViewById(R.id.option_export_csv);
        csvOption.setOnClickListener(v -> {
            dialog.dismiss();
            exportEmissionsReport();
        });

        // JSON option
        View jsonOption = dialog.findViewById(R.id.option_export_json);
        jsonOption.setOnClickListener(v -> {
            dialog.dismiss();
            exportAsJSON();
        });

        // Cancel button
        View cancelBtn = dialog.findViewById(R.id.btn_cancel);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Export emissions report as PDF with formatted layout and charts
     */
    private void exportAsPDF() {
        // Ensure latest values are displayed/exported
        updateDisplay();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String displayTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String fileName = "emissions_report_" + timestamp + ".pdf";

        PdfDocument document = new PdfDocument();

        try {
            // Create PDF page (8.5" x 11" at 72 DPI)
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(612, 792, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Configure paint for text
            Paint titlePaint = new Paint();
            titlePaint.setTextSize(24);
            titlePaint.setColor(Color.BLACK);
            titlePaint.setAntiAlias(true);

            Paint headingPaint = new Paint();
            headingPaint.setTextSize(18);
            headingPaint.setColor(Color.BLACK);
            headingPaint.setAntiAlias(true);

            Paint bodyPaint = new Paint();
            bodyPaint.setTextSize(12);
            bodyPaint.setColor(Color.BLACK);
            bodyPaint.setAntiAlias(true);

            Paint labelPaint = new Paint();
            labelPaint.setTextSize(10);
            labelPaint.setColor(Color.GRAY);
            labelPaint.setAntiAlias(true);

            // Compute summary stats
            int availableCount = 0;
            int readyCount = 0;
            for (MonitorData monitor : dataManager.getMonitorDataMap().values()) {
                if (monitor.isAvailable) {
                    availableCount++;
                    if (monitor.isReady) {
                        readyCount++;
                    }
                }
            }
            int notReadyCount = availableCount - readyCount;

            // Draw header
            int y = 60;
            canvas.drawText("OBD-Droid Emissions Report", 50, y, titlePaint);
            y += 30;
            canvas.drawText(displayTimestamp, 50, y, labelPaint);
            y += 40;

            // Draw summary
            canvas.drawText("Emissions Test Readiness Summary", 50, y, headingPaint);
            y += 30;
            canvas.drawText("Available Monitors: " + availableCount, 70, y, bodyPaint);
            y += 20;
            canvas.drawText("Ready Monitors: " + readyCount, 70, y, bodyPaint);
            y += 20;
            canvas.drawText("Monitors Needing Drive Cycle: " + notReadyCount, 70, y, bodyPaint);
            y += 40;

            // Draw readiness indicator
            Paint statusPaint = new Paint();
            statusPaint.setTextSize(16);
            statusPaint.setAntiAlias(true);
            if (readyCount == availableCount && availableCount > 0) {
                statusPaint.setColor(Color.rgb(76, 175, 80)); // Green
                canvas.drawText("✓ READY FOR EMISSIONS TEST", 70, y, statusPaint);
            } else {
                statusPaint.setColor(Color.rgb(255, 152, 0)); // Orange
                canvas.drawText("⚠ NOT READY - Additional Drive Cycle Required", 70, y, statusPaint);
            }
            y += 50;

            // Draw monitor details
            canvas.drawText("Monitor Details", 50, y, headingPaint);
            y += 30;

            for (MonitorData monitor : getOrderedMonitorList()) {
                if (monitor == null) continue;

                // Check if we need a new page
                if (y > 720) {
                    document.finishPage(page);
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = 60;
                }

                // Monitor name
                canvas.drawText(monitor.name, 70, y, bodyPaint);
                y += 15;

                // Status
                String status = monitor.isAvailable ? (monitor.isReady ? "Ready ✓" : "Not Ready") : "Not Equipped";
                Paint statusTextPaint = new Paint(labelPaint);
                if (monitor.isReady) {
                    statusTextPaint.setColor(Color.rgb(76, 175, 80));
                } else if (monitor.isAvailable) {
                    statusTextPaint.setColor(Color.rgb(255, 152, 0));
                }
                canvas.drawText("Status: " + status, 90, y, statusTextPaint);
                y += 15;

                // IUMPR data
                if (monitor.isAvailable) {
                    String iumprText = String.format(Locale.US, "IUMPR: %d / %d (%s)",
                            monitor.completions, monitor.conditions, monitor.getPercentageDisplay());
                    canvas.drawText(iumprText, 90, y, labelPaint);
                    y += 15;

                    String quality = monitor.getIUMPRQuality();
                    if (quality != null && !quality.isEmpty()) {
                        canvas.drawText("Quality: " + quality, 90, y, labelPaint);
                        y += 15;
                    }
                }

                y += 10; // Spacing between monitors
            }

            document.finishPage(page);

            ReportFileWriter.exportStream(this, fileName, ReportFileWriter.MIME_PDF, document::writeTo);
            showSnackbar("PDF report saved: " + fileName);
            log.info("PDF report exported successfully: " + fileName);

        } catch (Exception e) {
            log.warning("Failed to export PDF: " + e.getMessage());
            showSnackbar("Failed to export PDF report");
        } finally {
            document.close();
        }
    }

    /**
     * Export emissions report as JSON for APIs and developers
     */
    private void exportAsJSON() {
        // Ensure latest values are displayed/exported
        updateDisplay();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String displayTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String fileName = "emissions_report_" + timestamp + ".json";

        try {
            // Compute summary stats
            int availableCount = 0;
            int readyCount = 0;
            for (MonitorData monitor : dataManager.getMonitorDataMap().values()) {
                if (monitor.isAvailable) {
                    availableCount++;
                    if (monitor.isReady) {
                        readyCount++;
                    }
                }
            }
            int notReadyCount = availableCount - readyCount;

            // Build JSON structure
            JSONObject root = new JSONObject();
            root.put("report_type", "OBD-Droid Emissions Report");
            root.put("generated_at", displayTimestamp);
            root.put("timestamp", new Date().getTime());

            JSONObject summary = new JSONObject();
            summary.put("available_monitors", availableCount);
            summary.put("ready_monitors", readyCount);
            summary.put("not_ready_monitors", notReadyCount);
            summary.put("is_ready_for_test", (readyCount == availableCount && availableCount > 0));
            summary.put("passes_epa_standards", (readyCount == availableCount && availableCount > 0));
            root.put("summary", summary);

            JSONArray monitorsArray = new JSONArray();
            for (MonitorData monitor : getOrderedMonitorList()) {
                if (monitor == null) continue;

                JSONObject monitorObj = new JSONObject();
                monitorObj.put("name", monitor.name);
                monitorObj.put("available", monitor.isAvailable);
                monitorObj.put("ready", monitor.isReady);
                monitorObj.put("completions", monitor.completions);
                monitorObj.put("conditions", monitor.conditions);
                monitorObj.put("completion_percentage", monitor.getPercentageDisplay());

                String quality = monitor.getIUMPRQuality();
                monitorObj.put("iumpr_quality", quality != null ? quality : "");

                monitorsArray.put(monitorObj);
            }
            root.put("monitors", monitorsArray);

            // Vehicle info (if available)
            VehicleInfoFooter vehicleFooter = findViewById(R.id.vehicle_info_footer);
            if (vehicleFooter != null) {
                JSONObject vehicleInfo = new JSONObject();
                // Add vehicle info if available from VID data
                root.put("vehicle", vehicleInfo);
            }

            String json = root.toString(2); // Pretty print with 2-space indent
            ReportFileWriter.export(this, fileName, ReportFileWriter.MIME_JSON, writer -> writer.write(json));

            showSnackbar("JSON report saved: " + fileName);
            log.info("JSON report exported successfully: " + fileName);

        } catch (Exception e) {
            log.warning("Failed to export JSON: " + e.getMessage());
            showSnackbar("Failed to export JSON report");
        }
    }

    private void updateDisplay() {
        log.info(">>> updateDisplay() called");
        log.info("  PidPvs: " + (ObdProt.PidPvs != null ? ObdProt.PidPvs.size() + " items" : "null"));
        log.info("  VidPvs: " + (ObdProt.VidPvs != null ? ObdProt.VidPvs.size() + " items" : "null"));

        // Log current sensor values for debugging
        if (ObdProt.PidPvs != null && !ObdProt.PidPvs.isEmpty()) {
            logSensorValues();
        }

        // Update all monitor data using data manager
        dataManager.updateAllData();

        // Count how many monitors have data
        int monitorsWithData = 0;
        for (MonitorData monitor : dataManager.getMonitorDataMap().values()) {
            if (monitor.conditions > 0 || monitor.isAvailable) {
                monitorsWithData++;
            }
        }
        log.info("  Monitors with data: " + monitorsWithData + "/9");

        // Update overall status
        updateOverallStatus();

        // Rebuild monitor cards
        rebuildMonitorCards();

        log.info("<<< updateDisplay() complete");
    }


    private void updateOverallStatus() {
        // Calculate readiness using calculator
        EmissionsCalculator.ReadinessStatus status =
            EmissionsCalculator.calculateReadiness(dataManager.getMonitorDataMap());

        log.info("=== Overall Status Calculation ===");
        log.info("  Available monitors: " + status.availableCount);
        log.info("  Ready monitors: " + status.readyCount);
        log.info("  Not ready monitors: " + status.notReadyCount);
        log.info("  hasData: " + status.hasData + ", isReady: " + status.isReady);

        if (status.isReady) {
            log.info("  Setting status: READY (green)");
            emissionsStatusBanner.setBackgroundColor(COLOR_READY);
            statusIcon.setText("✓");
        } else if (!status.hasData) {
            log.info("  Setting status: WAITING FOR DATA (gray)");
            emissionsStatusBanner.setBackgroundColor(COLOR_UNKNOWN);
            statusIcon.setText("?");
        } else {
            log.info("  Setting status: NOT READY (yellow)");
            emissionsStatusBanner.setBackgroundColor(COLOR_NOT_READY);
            statusIcon.setText("⚠");
        }

        // Update status text using calculator-provided strings
        overallStatusText.setText(status.getStatusText());
        overallStatusSubtext.setText(status.getSubtext());

        // Update timestamp
        updateTimestamp();

        log.info("=== Overall Status Update Complete ===");
    }

    private void updateTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
        String currentTime = sdf.format(new Date());
        lastUpdatedText.setText("Last updated: " + currentTime);
    }

    private void rebuildMonitorCards() {
        monitorsContainer.removeAllViews();

        // Add continuous monitors section
        addSectionHeader("CONTINUOUS MONITORS", "(Always Running)");
        addMonitorCard(dataManager.getMonitorDataMap().get("MISFIRE"), true);
        addMonitorCard(dataManager.getMonitorDataMap().get("FUEL"), true);
        addMonitorCard(dataManager.getMonitorDataMap().get("CCM"), true);

        // Add non-continuous monitors section (sorted: Ready → Not Ready → Not Equipped)
        addSectionHeader("NON-CONTINUOUS MONITORS", "(Requires Specific Conditions)");

        // Collect and sort non-continuous monitors
        java.util.List<MonitorData> nonContinuousMonitors = new java.util.ArrayList<>();
        nonContinuousMonitors.add(dataManager.getMonitorDataMap().get("CATALYST"));
        nonContinuousMonitors.add(dataManager.getMonitorDataMap().get("EVAP"));
        nonContinuousMonitors.add(dataManager.getMonitorDataMap().get("O2SENSOR"));
        nonContinuousMonitors.add(dataManager.getMonitorDataMap().get("O2HEATER"));
        nonContinuousMonitors.add(dataManager.getMonitorDataMap().get("EGR"));
        nonContinuousMonitors.add(dataManager.getMonitorDataMap().get("AIR"));

        // Sort: Ready monitors first, then Not Ready, then Not Equipped at bottom
        java.util.Collections.sort(nonContinuousMonitors, new java.util.Comparator<MonitorData>() {
            @Override
            public int compare(MonitorData m1, MonitorData m2) {
                // Prioritize available monitors over non-available
                if (m1.isAvailable != m2.isAvailable) {
                    return m1.isAvailable ? -1 : 1;  // Available first
                }
                // Among available monitors, prioritize ready over not ready
                if (m1.isAvailable && m2.isAvailable) {
                    if (m1.isReady != m2.isReady) {
                        return m1.isReady ? -1 : 1;  // Ready first
                    }
                }
                return 0;  // Keep original order for same status
            }
        });

        // Add sorted monitors
        for (MonitorData monitor : nonContinuousMonitors) {
            addMonitorCard(monitor, false);
        }
    }

    private void addSectionHeader(String title, String subtitle) {
        View headerView = getLayoutInflater().inflate(R.layout.emissions_section_header, monitorsContainer, false);
        TextView titleText = headerView.findViewById(R.id.section_title);
        TextView subtitleText = headerView.findViewById(R.id.section_subtitle);

        titleText.setText(title);
        subtitleText.setText(subtitle);

        monitorsContainer.addView(headerView);
    }

    private void addMonitorCard(MonitorData monitor, boolean isContinuous) {
        if (monitor == null) return;

        View cardView = getLayoutInflater().inflate(R.layout.emissions_monitor_card, monitorsContainer, false);

        // Get view components
        View statusIndicator = cardView.findViewById(R.id.monitor_status_indicator);
        TextView nameText = cardView.findViewById(R.id.monitor_name);
        TextView statusText = cardView.findViewById(R.id.monitor_status_text);
        TextView performanceText = cardView.findViewById(R.id.monitor_performance_text);
        View performanceBar = cardView.findViewById(R.id.monitor_performance_bar);

        // Set monitor name
        nameText.setText(monitor.name);

        // Set status
        if (!monitor.isAvailable) {
            statusIndicator.setBackgroundColor(COLOR_UNKNOWN);
            statusText.setText("Not Equipped");
            performanceText.setVisibility(View.GONE);
            performanceBar.setVisibility(View.GONE);
        } else if (monitor.conditions == 0) {
            // Monitor marked as "available" but has no condition data = not really available
            // Force it to not available so it doesn't count against EPA readiness
            monitor.isAvailable = false;
            statusIndicator.setBackgroundColor(COLOR_UNKNOWN);
            statusText.setText("No Data");
            performanceText.setText("Drive cycle needed");
            performanceBar.setVisibility(View.GONE);
        } else if (monitor.isReady) {
            statusIndicator.setBackgroundColor(COLOR_READY);

            // Clean, simple status text
            statusText.setText("✓ Ready");

            // Build performance text with quality indicator on separate lines
            String iumprQuality = monitor.getIUMPRQuality();
            StringBuilder perfText = new StringBuilder();

            // Line 1: Percentage and ratio
            perfText.append(String.format(
                "%s (%,d / %,d)",
                monitor.getPercentageDisplay(),
                monitor.completions,
                monitor.conditions
            ));

            // Line 2: Quality indicator (if available)
            if (iumprQuality != null) {
                perfText.append("\n").append(iumprQuality);

                // Line 3: Explanation for low frequency monitors
                if (iumprQuality.contains("Low Frequency")) {
                    perfText.append("\n⚠️ ").append(monitor.getIUMPRQualityExplanation());
                }
            }

            performanceText.setText(perfText.toString());

            // Set performance bar width
            performanceBar.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) performanceBar.getLayoutParams();
            params.weight = monitor.getRatio();
            performanceBar.setLayoutParams(params);
            performanceBar.setBackgroundColor(COLOR_READY);
        } else {
            statusIndicator.setBackgroundColor(COLOR_NOT_READY);
            statusText.setText("⚠ Not Ready");

            if (monitor.completions == 0 && monitor.conditions > 0) {
                performanceText.setText(String.format(
                    "0%% • %,d conditions encountered, 0 completions",
                    monitor.conditions
                ));
            } else {
                performanceText.setText(String.format(
                    "%s • %,d completions / %,d conditions (needs more driving)",
                    monitor.getPercentageDisplay(),
                    monitor.completions,
                    monitor.conditions
                ));
            }

            // Set performance bar width
            performanceBar.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) performanceBar.getLayoutParams();

            // Progress bar should show readiness progress, not IUMPR ratio
            // If monitor is Ready, show 100%. If Not Ready, cap progress at 80% max
            // to avoid confusion (IUMPR ratio can be >100% while monitor is Not Ready)
            float progressWeight;
            if (monitor.isReady) {
                progressWeight = 1.0f;  // 100% - monitor is ready
            } else {
                // Cap at 80% for not-ready monitors, even if IUMPR is high
                // This shows data is accumulating but monitor isn't complete yet
                progressWeight = Math.min(monitor.getRatio(), 0.8f);
            }

            params.weight = progressWeight;
            performanceBar.setLayoutParams(params);
            performanceBar.setBackgroundColor(COLOR_NOT_READY);
        }

        monitorsContainer.addView(cardView);
    }

    //===================================================================================
    // SENSOR DATA RETRIEVAL - Critical emissions-related sensors from Mode 1
    //===================================================================================

    /**
     * Get oxygen sensor 1 bank 1 voltage (PID 0x14)
     * @return Voltage in volts, or null if not available
     */
    private Float getO2Sensor1Bank1() {
        EcuDataPv pv = ObdProt.PidPvs.getTyped("14.0.0");  // PID 0x14, sensor 0, bank 0
        if (pv != null) {
            Object value = pv.get(EcuDataPv.FID_VALUE);
            if (value != null) {
                try {
                    return Float.parseFloat(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Get oxygen sensor 2 bank 1 voltage (PID 0x15)
     * @return Voltage in volts, or null if not available
     */
    private Float getO2Sensor2Bank1() {
        EcuDataPv pv = ObdProt.PidPvs.getTyped("15.0.0");  // PID 0x15
        if (pv != null) {
            Object value = pv.get(EcuDataPv.FID_VALUE);
            if (value != null) {
                try {
                    return Float.parseFloat(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Get short term fuel trim bank 1 (PID 0x06)
     * @return Percentage (-100 to +100), or null if not available
     */
    private Float getShortTermFuelTrim() {
        EcuDataPv pv = ObdProt.PidPvs.getTyped("06.0.0");  // PID 0x06
        if (pv != null) {
            Object value = pv.get(EcuDataPv.FID_VALUE);
            if (value != null) {
                try {
                    return Float.parseFloat(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Get long term fuel trim bank 1 (PID 0x07)
     * @return Percentage (-100 to +100), or null if not available
     */
    private Float getLongTermFuelTrim() {
        EcuDataPv pv = ObdProt.PidPvs.getTyped("07.0.0");  // PID 0x07
        if (pv != null) {
            Object value = pv.get(EcuDataPv.FID_VALUE);
            if (value != null) {
                try {
                    return Float.parseFloat(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Get engine coolant temperature (PID 0x05)
     * @return Temperature in Celsius, or null if not available
     */
    private Float getCoolantTemp() {
        EcuDataPv pv = ObdProt.PidPvs.getTyped("05.0.0");  // PID 0x05
        if (pv != null) {
            Object value = pv.get(EcuDataPv.FID_VALUE);
            if (value != null) {
                try {
                    return Float.parseFloat(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Get intake air temperature (PID 0x0F)
     * @return Temperature in Celsius, or null if not available
     */
    private Float getIntakeAirTemp() {
        EcuDataPv pv = ObdProt.PidPvs.getTyped("0F.0.0");  // PID 0x0F
        if (pv != null) {
            Object value = pv.get(EcuDataPv.FID_VALUE);
            if (value != null) {
                try {
                    return Float.parseFloat(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Get engine load (PID 0x04)
     * @return Load percentage (0-100), or null if not available
     */
    private Float getEngineLoad() {
        EcuDataPv pv = ObdProt.PidPvs.getTyped("04.0.0");  // PID 0x04
        if (pv != null) {
            Object value = pv.get(EcuDataPv.FID_VALUE);
            if (value != null) {
                try {
                    return Float.parseFloat(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Get engine RPM (PID 0x0C)
     * @return RPM, or null if not available
     */
    private Float getEngineRPM() {
        EcuDataPv pv = ObdProt.PidPvs.getTyped("0C.0.0");  // PID 0x0C
        if (pv != null) {
            Object value = pv.get(EcuDataPv.FID_VALUE);
            if (value != null) {
                try {
                    return Float.parseFloat(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Get mass air flow (PID 0x10)
     * @return MAF in grams/second, or null if not available
     */
    private Float getMassAirFlow() {
        EcuDataPv pv = ObdProt.PidPvs.getTyped("10.0.0");  // PID 0x10
        if (pv != null) {
            Object value = pv.get(EcuDataPv.FID_VALUE);
            if (value != null) {
                try {
                    return Float.parseFloat(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Get throttle position (PID 0x11)
     * @return Throttle percentage (0-100), or null if not available
     */
    private Float getThrottlePosition() {
        EcuDataPv pv = ObdProt.PidPvs.getTyped("11.0.0");  // PID 0x11
        if (pv != null) {
            Object value = pv.get(EcuDataPv.FID_VALUE);
            if (value != null) {
                try {
                    return Float.parseFloat(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Log current emissions sensor values for debugging
     */
    private void logSensorValues() {
        log.info("=== Current Emissions Sensor Values ===");
        log.info("  O2 Sensor 1 Bank 1: " + formatSensorValue(getO2Sensor1Bank1(), "V"));
        log.info("  O2 Sensor 2 Bank 1: " + formatSensorValue(getO2Sensor2Bank1(), "V"));
        log.info("  Short Term Fuel Trim: " + formatSensorValue(getShortTermFuelTrim(), "%"));
        log.info("  Long Term Fuel Trim: " + formatSensorValue(getLongTermFuelTrim(), "%"));
        log.info("  Coolant Temperature: " + formatSensorValue(getCoolantTemp(), "°C"));
        log.info("  Intake Air Temperature: " + formatSensorValue(getIntakeAirTemp(), "°C"));
        log.info("  Engine Load: " + formatSensorValue(getEngineLoad(), "%"));
        log.info("  Engine RPM: " + formatSensorValue(getEngineRPM(), ""));
        log.info("  Mass Air Flow: " + formatSensorValue(getMassAirFlow(), "g/s"));
        log.info("  Throttle Position: " + formatSensorValue(getThrottlePosition(), "%"));
        log.info("======================================");
    }

    /**
     * Format sensor value for display
     */
    private String formatSensorValue(Float value, String unit) {
        if (value == null) {
            return "N/A";
        }
        return String.format(Locale.US, "%.2f %s", value, unit);
    }
}
