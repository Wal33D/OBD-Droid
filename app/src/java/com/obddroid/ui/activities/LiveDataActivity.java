package com.obddroid.ui.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.obddroid.R;
import com.obddroid.obd.ObdProt;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.common.ProcessVariables.ProcessVar;
import com.obddroid.common.ProcessVariables.PvChangeEvent;
import com.obddroid.common.ProcessVariables.PvChangeListener;
import com.obddroid.common.ProcessVariables.TypedPvList;
import com.obddroid.telemetry.GpsTelemetryManager;
import com.obddroid.telemetry.SensorTelemetryManager;
import com.obddroid.services.CommService;
import com.obddroid.ui.adapters.ObdItemAdapter;
import com.obddroid.ui.adapters.ObdRecyclerAdapter;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.ui.activities.DashBoardActivity;
import com.obddroid.utils.ReportFileWriter;
import com.obddroid.utils.SnackbarHelper;
import com.obddroid.utils.PermissionManager;
import com.obddroid.utils.HelpDialogUtils;
import com.obddroid.custompid.CustomPidIntegration;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import java.util.List;
import java.util.logging.Logger;

/**
 * Live Data Activity
 *
 * Displays real-time OBD sensor data from the vehicle, including:
 * - Standard OBD PIDs (engine RPM, speed, temperature, etc.)
 * - GPS telemetry data (location, altitude, speed)
 * - Motion sensor data (acceleration, gyroscope)
 */
public class LiveDataActivity extends AppCompatActivity
        implements PvChangeListener {

    private static final Logger log = Logger.getLogger(LiveDataActivity.class.getName());

    // UI Components
    private RecyclerView recyclerView;
    private ObdRecyclerAdapter recyclerAdapter;
    private VehicleInfoFooter vehicleInfoFooter;
    private View snackbarAnchor;
    private ActionMode actionMode;

    // Telemetry managers
    private GpsTelemetryManager gpsTelemetryManager;
    private SensorTelemetryManager sensorTelemetryManager;

    // Custom PID integration
    private CustomPidIntegration customPidIntegration;

    // Broadcast receiver for custom PID changes
    private final BroadcastReceiver customPidChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log.info("Received custom PID change broadcast - refreshing custom PIDs");
            loadCustomPids();
            if (recyclerAdapter != null) {
                recyclerAdapter.updateData(ObdProt.PidPvs);
            }
        }
    };

    // Update handler
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private static final long UPDATE_INTERVAL = 500; // Update every 500ms

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (recyclerAdapter != null && !ObdProt.PidPvs.isEmpty()) {
                // Just refresh the displayed values, don't rebuild the entire list
                recyclerAdapter.notifyDataSetChanged();
            }
            updateHandler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_data);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.background_secondary));
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Live Data");
        }

        initializeViews();
        initializeTelemetryManagers();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recycler_view);
        snackbarAnchor = findViewById(R.id.snackbar_anchor);

        // Setup RecyclerView with StaggeredGridLayoutManager (2 columns, vertical)
        StaggeredGridLayoutManager layoutManager =
            new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);

        // Create adapter with grid tile layout
        recyclerAdapter = new ObdRecyclerAdapter(this, ObdProt.PidPvs, this::onSelectionChanged);
        recyclerView.setAdapter(recyclerAdapter);

        // Setup footer
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        if (vehicleInfoFooter != null) {
            vehicleInfoFooter.attachBackHandler(this);
            vehicleInfoFooter.setOverlayView(findViewById(R.id.footer_overlay));
        }
    }

    private void initializeTelemetryManagers() {
        // Telemetry managers are created on-demand
    }

    private void loadCustomPids() {
        try {
            if (customPidIntegration == null) {
                customPidIntegration = new CustomPidIntegration(this);
            }

            // Load all enabled custom PIDs into live data
            customPidIntegration.loadIntoLiveData();

            int loadedCount = customPidIntegration.getLoadedCount();
            if (loadedCount > 0) {
                log.info("Loaded " + loadedCount + " custom PIDs into live data");
            }
        } catch (Exception e) {
            log.warning("Failed to load custom PIDs: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register PV change listeners for OBD data
        ObdProt.PidPvs.addPvChangeListener(this, PvChangeEvent.PV_ADDED | PvChangeEvent.PV_MODIFIED);

        // Register broadcast receiver for custom PID changes
        LocalBroadcastManager.getInstance(this).registerReceiver(
            customPidChangeReceiver,
            new IntentFilter(CustomPidIntegration.ACTION_CUSTOM_PIDS_CHANGED)
        );

        // Load custom PIDs into live data
        loadCustomPids();

        // Set OBD service to live data mode
        CommService.elm.setService(ObdProt.OBD_SVC_DATA);
        log.info("Set OBD service to OBD_SVC_DATA (Live Data mode)");

        // Show streaming notification
        SnackbarHelper.showInfo(this, "Streaming live data, hang tight!");

        // Refresh adapter with current PIDs (in case new ones were added while paused)
        if (recyclerAdapter != null && !ObdProt.PidPvs.isEmpty()) {
            recyclerAdapter.updateData(ObdProt.PidPvs);
        }

        // Start updating the adapter
        updateHandler.post(updateRunnable);

        // Auto-start telemetry if enabled in preferences
        restoreTelemetryState();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister PV change listeners
        ObdProt.PidPvs.removePvChangeListener(this);

        // Unregister broadcast receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(customPidChangeReceiver);

        // Stop updates
        updateHandler.removeCallbacks(updateRunnable);

        // Only stop OBD polling if we're finishing (user leaving Live Data context)
        // Don't stop if just pausing for child activities (Charts, Dashboard, HUD)
        if (isFinishing()) {
            CommService.elm.setService(ObdProt.OBD_SVC_NONE);
            log.info("Set OBD service to NONE (stopped polling - activity finishing)");
        } else {
            log.info("Activity pausing but not finishing (child activity launched) - keeping OBD service active");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Backup cleanup of PV listener in case onPause() didn't run
        // Defense-in-depth pattern matching FuelEconomyActivity and EmissionsActivity
        if (ObdProt.PidPvs != null) {
            ObdProt.PidPvs.removePvChangeListener(this);
        }

        log.info("=== LiveDataActivity destroyed ===");
    }

    @Override
    public void pvChanged(PvChangeEvent event) {
        // When NEW PIDs are added (vehicle discovery), rebuild the adapter
        if ((event.getType() & PvChangeEvent.PV_ADDED) != 0) {
            runOnUiThread(() -> {
                if (recyclerAdapter != null) {
                    recyclerAdapter.updateData(ObdProt.PidPvs);
                }
            });
        }
        // For value modifications, the updateRunnable will handle the refresh
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_live_data, menu);

        // Update menu items based on current telemetry state
        updateMenuItems(menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenuItems(menu);
        return super.onPrepareOptionsMenu(menu);
    }

    private void updateMenuItems(Menu menu) {
        if (menu == null) return;

        // Update GPS telemetry menu item
        MenuItem gpsItem = menu.findItem(R.id.action_toggle_gps_telemetry);
        if (gpsItem != null && gpsTelemetryManager != null) {
            boolean isActive = gpsTelemetryManager.isActive();
            gpsItem.setTitle(isActive ? "Disable GPS Telemetry" : "Enable GPS Telemetry");
            gpsItem.setIcon(R.drawable.ic_gps_24);
        }

        // Update motion sensor telemetry menu item
        MenuItem sensorItem = menu.findItem(R.id.action_toggle_motion_telemetry);
        if (sensorItem != null && sensorTelemetryManager != null) {
            boolean isActive = sensorTelemetryManager.isActive();
            sensorItem.setTitle(isActive ? "Disable Motion Telemetry" : "Enable Motion Telemetry");
            sensorItem.setIcon(R.drawable.ic_sensors_24);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        } else if (id == R.id.action_toggle_gps_telemetry) {
            toggleGpsTelemetry();
            return true;
        } else if (id == R.id.action_toggle_motion_telemetry) {
            toggleMotionTelemetry();
            return true;
        } else if (id == R.id.action_save_report) {
            showSaveReportDialog();
            return true;
        } else if (id == R.id.action_manage_custom_pids) {
            Intent intent = new Intent(this, CustomPidManagerActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_info) {
            showInfoDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionManager.PERMISSION_REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start GPS telemetry
                log.info("Location permission granted, starting GPS telemetry");
                if (gpsTelemetryManager != null) {
                    gpsTelemetryManager.start();
                    SnackbarHelper.showSuccess(this, "GPS Telemetry enabled");
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    prefs.edit().putBoolean("gps_telemetry_enabled", true).apply();

                    // Refresh adapter and menu
                    if (recyclerAdapter != null && !ObdProt.PidPvs.isEmpty()) {
                        recyclerAdapter.updateData(ObdProt.PidPvs);
                    }
                    invalidateOptionsMenu();
                }
            } else {
                // Permission denied
                log.warning("Location permission denied by user");
                SnackbarHelper.showError(this, "GPS Telemetry requires location permission");
            }
        }
    }

    private void toggleGpsTelemetry() {
        if (gpsTelemetryManager == null) {
            gpsTelemetryManager = new GpsTelemetryManager(this);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (gpsTelemetryManager.isActive()) {
            gpsTelemetryManager.stop();
            SnackbarHelper.showInfo(this, "GPS Telemetry disabled");
            log.info("GPS Telemetry disabled by user");
            prefs.edit().putBoolean("gps_telemetry_enabled", false).apply();
        } else {
            // Check if we have location permissions before starting GPS
            if (!PermissionManager.hasLocationPermission(this)) {
                log.info("Location permission not granted, requesting...");
                PermissionManager.requestLocationPermission(this);
                SnackbarHelper.showInfo(this, "Location permission required for GPS Telemetry");
                return;
            }

            gpsTelemetryManager.start();
            SnackbarHelper.showSuccess(this, "GPS Telemetry enabled");
            log.info("GPS Telemetry enabled by user");
            prefs.edit().putBoolean("gps_telemetry_enabled", true).apply();
        }

        // Refresh adapter to show/hide fields (even if PidPvs becomes empty)
        if (recyclerAdapter != null) {
            recyclerAdapter.updateData(ObdProt.PidPvs);
        }

        // Update menu
        invalidateOptionsMenu();
    }

    private void toggleMotionTelemetry() {
        if (sensorTelemetryManager == null) {
            sensorTelemetryManager = new SensorTelemetryManager(this);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (sensorTelemetryManager.isActive()) {
            sensorTelemetryManager.stop();
            SnackbarHelper.showInfo(this, "Motion Telemetry disabled");
            log.info("Motion Telemetry disabled by user");
            prefs.edit().putBoolean("sensor_telemetry_enabled", false).apply();
        } else {
            sensorTelemetryManager.start();
            SnackbarHelper.showSuccess(this, "Motion Telemetry enabled");
            log.info("Motion Telemetry enabled by user");
            prefs.edit().putBoolean("sensor_telemetry_enabled", true).apply();
        }

        // Refresh adapter to show/hide fields (even if PidPvs becomes empty)
        if (recyclerAdapter != null) {
            recyclerAdapter.updateData(ObdProt.PidPvs);
        }

        // Update menu
        invalidateOptionsMenu();
    }

    private void restoreTelemetryState() {
        // Auto-start telemetry if it was enabled before
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean gpsEnabled = prefs.getBoolean("gps_telemetry_enabled", false);
        boolean sensorEnabled = prefs.getBoolean("sensor_telemetry_enabled", false);

        if (gpsEnabled) {
            if (gpsTelemetryManager == null) {
                gpsTelemetryManager = new GpsTelemetryManager(this);
            }
            gpsTelemetryManager.start();
        }

        if (sensorEnabled) {
            if (sensorTelemetryManager == null) {
                sensorTelemetryManager = new SensorTelemetryManager(this);
            }
            sensorTelemetryManager.start();
        }

        // Refresh adapter after auto-start
        if (recyclerAdapter != null) {
            recyclerAdapter.updateData(ObdProt.PidPvs);
        }
    }

    // ========== Selection Handler (for RecyclerView) ==========

    private void onSelectionChanged(int selectedCount) {
        if (selectedCount > 0) {
            if (actionMode == null) {
                actionMode = startActionMode(new ActionMode.Callback() {
                    @Override
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        MenuInflater inflater = mode.getMenuInflater();
                        inflater.inflate(R.menu.context_graph, menu);
                        return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        return false;
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        int id = item.getItemId();

                        if (id == R.id.chart_selected) {
                            launchChartView();
                            mode.finish();
                            return true;
                        } else if (id == R.id.dashboard_selected) {
                            launchDashboardView();
                            mode.finish();
                            return true;
                        } else if (id == R.id.hud_selected) {
                            launchHudView();
                            mode.finish();
                            return true;
                        } else if (id == R.id.filter_selected) {
                            applyFilter();
                            mode.finish();
                            return true;
                        }

                        return false;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode) {
                        actionMode = null;
                        recyclerAdapter.clearSelection();
                    }
                });
            }
            actionMode.setTitle(selectedCount + " selected");
        } else {
            if (actionMode != null) {
                actionMode.finish();
            }
        }
    }

    // ========== Visualization Launchers ==========

    private int[] getSelectedPositions() {
        return recyclerAdapter.getSelectedPositions();
    }

    private void launchChartView() {
        int[] selectedPositions = getSelectedPositions();
        if (selectedPositions.length == 0) {
            SnackbarHelper.showWarning(this, "Please select items to chart");
            return;
        }

        // Create a compatibility adapter for ChartActivity
        // ChartActivity expects an ObdItemAdapter, so we create one with selected items
        List<com.obddroid.ecu.EcuDataPv> selectedItems = recyclerAdapter.getSelectedItems();

        ObdItemAdapter chartAdapter = new ObdItemAdapter(this, R.layout.obd_item, ObdProt.PidPvs);
        chartAdapter.clear();
        chartAdapter.addAll(selectedItems);

        ChartActivity.setAdapter(chartAdapter);
        Intent intent = new Intent(this, ChartActivity.class);

        // Map to sequential positions since we're using a filtered adapter
        int[] chartPositions = new int[selectedItems.size()];
        for (int i = 0; i < selectedItems.size(); i++) {
            chartPositions[i] = i;
        }
        intent.putExtra(ChartActivity.POSITIONS, chartPositions);
        startActivity(intent);
    }

    private void launchDashboardView() {
        int[] selectedPositions = getSelectedPositions();
        if (selectedPositions.length == 0) {
            SnackbarHelper.showWarning(this, "Please select items for dashboard");
            return;
        }

        // Create a compatibility adapter for DashBoardActivity
        // DashBoardActivity expects an ObdItemAdapter, so we create one with selected items only
        List<com.obddroid.ecu.EcuDataPv> selectedItems = recyclerAdapter.getSelectedItems();

        ObdItemAdapter dashboardAdapter = new ObdItemAdapter(this, R.layout.obd_item, ObdProt.PidPvs);
        dashboardAdapter.clear();
        dashboardAdapter.addAll(selectedItems);

        DashBoardActivity.setAdapter(dashboardAdapter);
        Intent intent = new Intent(this, DashBoardActivity.class);

        // Map to sequential positions since we're using a filtered adapter
        int[] dashboardPositions = new int[selectedItems.size()];
        for (int i = 0; i < selectedItems.size(); i++) {
            dashboardPositions[i] = i;
        }
        intent.putExtra(DashBoardActivity.POSITIONS, dashboardPositions);
        // Note: RES_ID defaults to R.layout.dashboard, which is the correct container layout

        log.info("Launching dashboard with " + selectedItems.size() + " items");
        startActivity(intent);
    }

    private void launchHudView() {
        int[] selectedPositions = getSelectedPositions();
        if (selectedPositions.length == 0) {
            SnackbarHelper.showWarning(this, "Please select items for HUD");
            return;
        }

        SnackbarHelper.showInfo(this, "HUD view needs adapter compatibility update");
        log.info("HUD view requested with " + selectedPositions.length + " items");
    }

    private void applyFilter() {
        int[] selectedPositions = getSelectedPositions();
        if (selectedPositions.length == 0) {
            SnackbarHelper.showWarning(this, "Please select items to filter");
            return;
        }

        // Set list to filtered mode showing only selected items
        // TODO: Actually implement filtering by hiding non-selected items
        SnackbarHelper.showInfo(this, "Filter functionality coming soon");
        log.info("Filter requested for " + selectedPositions.length + " items");
    }

    // ========== Save Report ==========

    /**
     * Show dialog to choose export format (CSV or JSON)
     */
    private void showSaveReportDialog() {
        if (ObdProt.PidPvs.isEmpty()) {
            SnackbarHelper.showWarning(this, "No live data available to save");
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_export_ecu);
        dialog.setCancelable(true);

        TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
        if (dialogTitle != null) dialogTitle.setText("Save Live Data Report");

        View csvOption = dialog.findViewById(R.id.option_export_csv);
        if (csvOption != null) {
            csvOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportCsv();
            });
        }

        View jsonOption = dialog.findViewById(R.id.option_export_json);
        if (jsonOption != null) {
            jsonOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportJson();
            });
        }

        View cancelButton = dialog.findViewById(R.id.btn_cancel);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    /**
     * Export live data to CSV
     */
    private void exportCsv() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String filename = "live_data_" + timestamp + ".csv";
            // Built in memory, then saved through MediaStore (Documents/OBD-Droid) on Android 10+.
            StringWriter writer = new StringWriter();

            // CSV Header
            writer.append("PID,Description,Value,Units\n");

            // Write all PIDs
            for (java.util.Map.Entry<String, EcuDataPv> entry : ObdProt.PidPvs.entrySetTyped()) {
                String pid = String.valueOf(entry.getKey());
                EcuDataPv pv = entry.getValue();

                Object description = pv.get(EcuDataPv.FID_DESCRIPT);
                Object value = pv.get(EcuDataPv.FID_VALUE);
                String units = pv.getUnits();

                String desc = description != null ? String.valueOf(description) : "";
                String val = value != null ? String.valueOf(value) : "";
                String unit = units != null ? units : "";

                // Escape CSV special characters
                desc = escapeCsv(desc);
                val = escapeCsv(val);
                unit = escapeCsv(unit);

                writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\"\n", pid, desc, val, unit));
            }

            writer.flush();
            writer.close();
            ReportFileWriter.writeText(this, filename, ReportFileWriter.MIME_CSV, writer.toString());

            SnackbarHelper.showSuccess(this, "Saved to Documents/OBD-Droid/" + filename);
            log.info("Live data exported to CSV: " + filename);
        } catch (IOException e) {
            log.severe("Error exporting CSV: " + e.getMessage());
            SnackbarHelper.showError(this, "Failed to export CSV: " + e.getMessage());
        }
    }

    /**
     * Export live data to JSON
     */
    private void exportJson() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String filename = "live_data_" + timestamp + ".json";

            // Build JSON structure
            java.util.Map<String, java.util.Map<String, Object>> data = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, EcuDataPv> entry : ObdProt.PidPvs.entrySetTyped()) {
                String pid = String.valueOf(entry.getKey());
                EcuDataPv pv = entry.getValue();

                java.util.Map<String, Object> pidData = new java.util.HashMap<>();
                pidData.put("description", pv.get(EcuDataPv.FID_DESCRIPT));
                pidData.put("value", pv.get(EcuDataPv.FID_VALUE));
                pidData.put("units", pv.getUnits());

                data.put(pid, pidData);
            }

            // Create wrapper object with metadata
            java.util.Map<String, Object> report = new java.util.HashMap<>();
            report.put("timestamp", timestamp);
            report.put("exportDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            report.put("dataSource", "LiveData");
            report.put("pidCount", data.size());
            report.put("pids", data);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(report);

            ReportFileWriter.writeText(this, filename, ReportFileWriter.MIME_JSON, json);

            SnackbarHelper.showSuccess(this, "Saved to Documents/OBD-Droid/" + filename);
            log.info("Live data exported to JSON: " + filename);
        } catch (IOException e) {
            log.severe("Error exporting JSON: " + e.getMessage());
            SnackbarHelper.showError(this, "Failed to export JSON: " + e.getMessage());
        }
    }

    /**
     * Escape CSV special characters
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    private void showInfoDialog() {
        HelpDialogUtils.showHelpDialog(
            this,
            R.string.live_data_info_title,
            R.string.live_data_info_message,
            R.string.live_data_info_ack,
            android.R.drawable.ic_menu_info_details
        );
    }
}
