package com.obddroid.features.fueleconomy.ui;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.obddroid.R;
import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;
import com.obddroid.common.ProcessVariables.PvChangeEvent;
import com.obddroid.common.ProcessVariables.PvChangeListener;
import com.obddroid.features.fueleconomy.data.FuelEconomyDataManager;
import com.obddroid.features.fueleconomy.data.FuelEconomyDataManager.HistoricalData;
import com.obddroid.features.fueleconomy.data.FuelEconomyPreferences;
import com.obddroid.features.fueleconomy.data.FuelEconomyReportExporter;
import com.obddroid.services.CommService;
import com.obddroid.services.VehicleManager;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.VehicleData;
import com.obddroid.utils.VehiclePreferences;

import java.util.logging.Logger;

/**
 * Fuel Economy Activity
 * Displays detailed fuel economy metrics including:
 * - Instantaneous and average MPG with historical bar chart
 * - Fuel level percentage
 * - Estimated range
 * - Current fuel flow rate
 * <p>
 * Live values come from {@link FuelObdReader}, the fuel rate from {@link FuelRateModel}, the tank size from
 * {@link TankCapacity}, and the views are in {@link FuelEconomyViews}. Calibration and the saved report
 * are {@link FuelCalibrationDialog} and {@link FuelEconomyReportDialog}. This class runs the once-a-second
 * update and the OBD listener.
 */
public class FuelEconomyActivity extends AppCompatActivity implements PvChangeListener {

    private static final Logger log = Logger.getLogger(FuelEconomyActivity.class.getName());

    private static final long UPDATE_INTERVAL = 1000; // Update every second

    private FuelEconomyViews views;
    private VehicleInfoFooter vehicleInfoFooter;
    private View snackbarAnchor;

    private FuelEconomyDataManager dataManager;
    private FuelEconomyPreferences fuelEconomyPreferences;
    private VehicleManager vehicleManager;
    private final FuelObdReader obd = new FuelObdReader();
    private FuelRateModel fuelRate;
    private TankCapacity tankCapacity;
    private FuelCalibrationDialog calibrationDialog;
    private FuelEconomyReportDialog reportDialog;

    private Handler updateHandler;

    // Periodic update runnable (field-based for precise cleanup)
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateDisplayedValues();
            updateHandler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

    // A new or lost vehicle invalidates the tank size; a new one may need the tank prompt
    private final VehicleManager.VehicleChangeListener vehicleChangeListener =
            new VehicleManager.SimpleVehicleChangeListener() {
                @Override
                public void onVINChanged(String vin) {
                    runOnUiThread(() -> {
                        tankCapacity.clearCache();
                        tankCapacity.promptIfNeeded();
                    });
                }

                @Override
                public void onVehicleDecoded(VehicleData vehicleData) {
                    runOnUiThread(() -> {
                        tankCapacity.clearCache();
                        tankCapacity.promptIfNeeded();
                    });
                }

                @Override
                public void onVehicleDisconnected() {
                    runOnUiThread(() -> tankCapacity.clearCache());
                }

                @Override
                public void onDecodingError(String error) {
                    runOnUiThread(() -> tankCapacity.clearCache());
                }

                @Override
                public void onVINRetrievalFailed() {
                    runOnUiThread(() -> tankCapacity.clearCache());
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fuel_economy);

        log.info("=== FuelEconomyActivity onCreate() ===");

        // Set status bar and navigation bar colors to match footer (dark grey #212121)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#212121"));
            getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Fuel Economy");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        views = new FuelEconomyViews(this);
        log.info("Views initialized");

        dataManager = new FuelEconomyDataManager();
        fuelEconomyPreferences = new FuelEconomyPreferences(this);
        vehicleManager = VehicleManager.getInstance(this);
        fuelRate = new FuelRateModel(obd, fuelEconomyPreferences, vehicleManager);
        tankCapacity = new TankCapacity(this, vehicleManager, new VehiclePreferences(this), new TankCapacity.Host() {
            @Override
            public void showSnackbar(String message, int duration) {
                FuelEconomyActivity.this.showSnackbar(message, duration);
            }

            @Override
            public void refresh() {
                updateDisplayedValues();
            }
        });
        calibrationDialog = new FuelCalibrationDialog(this, fuelEconomyPreferences, vehicleManager,
            new FuelCalibrationDialog.Host() {
                @Override
                public float appMpg() {
                    return appMpgForCalibration();
                }

                @Override
                public void showSnackbar(String message, int duration) {
                    FuelEconomyActivity.this.showSnackbar(message, duration);
                }
            });
        reportDialog = new FuelEconomyReportDialog(this, new FuelEconomyReportExporter(this),
            new FuelEconomyReportDialog.Source() {
                @Override
                public boolean hasData() {
                    return dataManager.hasData() || views.showsMpg();
                }

                @Override
                public FuelEconomyReportExporter.Snapshot snapshot() {
                    return buildReportSnapshot();
                }
            });

        initializeDemoData();

        updateHandler = new Handler(Looper.getMainLooper());

        log.info("Registering PV change listener...");
        ObdProt.PidPvs.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED | PvChangeEvent.PV_MODIFIED);
        log.info("PV change listener registered. PidPvs size: " + ObdProt.PidPvs.size());

        setupFooterOverlay();

        vehicleManager.addListener(vehicleChangeListener);

        // Ask for the tank size once per vehicle
        tankCapacity.promptIfNeeded();
    }

    /**
     * Wire up footer overlay to close footer when clicking outside
     */
    private void setupFooterOverlay() {
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        snackbarAnchor = findViewById(R.id.content_frame);  // Use content frame for snackbar creation
        View overlay = findViewById(R.id.footer_overlay);

        if (vehicleInfoFooter != null && overlay != null) {
            vehicleInfoFooter.setOverlayView(overlay);
            log.info("Footer overlay wired up successfully");
        }
    }

    /**
     * Show a snackbar anchored above the vehicle footer
     */
    private void showSnackbar(String message, int duration) {
        Snackbar snackbar = Snackbar.make(snackbarAnchor, message, duration);
        snackbar.setAnchorView(vehicleInfoFooter);  // Position above the footer
        snackbar.getView().setElevation(6f);  // Lower than footer's 8f so it slides from behind
        snackbar.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        log.info("=== FuelEconomyActivity onResume() ===");

        // Always request Live Data service to ensure OBD data is being collected
        // This is needed even if PidPvs is not empty (e.g. when navigating from LiveDataActivity)
        log.info("Requesting Live Data service (PidPvs size: " + ObdProt.PidPvs.size() + ")");
        try {
            CommService.elm.setService(ObdProt.OBD_SVC_DATA);
        } catch (Exception e) {
            log.warning("Failed to request Live Data service: " + e.getMessage());
        }

        startPeriodicUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPeriodicUpdates();

        // Only stop OBD polling if we're finishing (user leaving Fuel Economy)
        // Keep polling active if just pausing temporarily
        if (isFinishing()) {
            CommService.elm.setService(ObdProt.OBD_SVC_NONE);
            log.info("Set OBD service to NONE (stopped polling - activity finishing)");
        } else {
            log.info("Activity pausing but not finishing - keeping OBD service active");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Backup cleanup of PV listener in case onPause() didn't run
        // Defense-in-depth pattern matching LiveDataActivity and EmissionsActivity
        if (ObdProt.PidPvs != null) {
            ObdProt.PidPvs.removePvChangeListener(this);
        }

        if (vehicleManager != null) {
            vehicleManager.removeListener(vehicleChangeListener);
        }

        log.info("=== FuelEconomyActivity destroyed ===");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.fuel_economy_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_info) {
            showInfoDialog();
            return true;
        } else if (itemId == R.id.action_calibrate) {
            calibrationDialog.show(fuelRate.calibratedVE());
            return true;
        } else if (itemId == R.id.action_save_report) {
            reportDialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showInfoDialog() {
        boolean mapAvailable = fuelRate.usesMap();
        boolean calibrated = mapAvailable && fuelRate.calibratedVE() != null;

        new android.app.AlertDialog.Builder(this)
            .setTitle("Fuel Economy")
            .setMessage(FuelEconomyFormat.infoMessage(mapAvailable, calibrated))
            .setPositiveButton("Got it", null)
            .show();
    }

    /** The app's MPG for calibration: long-term average, else the average on screen, else 25. */
    private float appMpgForCalibration() {
        float appMPG = dataManager.getLongTermAverage();
        if (appMPG <= 0) {
            try {
                appMPG = views.averageMpg();
            } catch (NumberFormatException e) {
                appMPG = 25.0f;
            }
        }
        return appMPG;
    }

    private FuelEconomyReportExporter.Snapshot buildReportSnapshot() {
        FuelEconomyReportExporter.Snapshot snapshot = new FuelEconomyReportExporter.Snapshot();

        String vin = vehicleManager.getCurrentVIN();
        snapshot.vin = vin;

        VehicleData vehicleData = vehicleManager.getCurrentVehicleData();
        if (vehicleData != null) {
            snapshot.vehicleName = vehicleData.getDisplayName();
        }

        views.fill(snapshot);

        HistoricalData history = dataManager.getHistoricalData();
        if (history != null) {
            snapshot.recentAvgMpg = history.recentAverage;
            snapshot.mediumAvgMpg = history.mediumAverage;
            snapshot.longTermAvgMpg = history.longTermAverage;
        } else {
            snapshot.recentAvgMpg = Float.NaN;
            snapshot.mediumAvgMpg = Float.NaN;
            snapshot.longTermAvgMpg = Float.NaN;
        }

        if (!TextUtils.isEmpty(vin)) {
            snapshot.veCalibrated = fuelEconomyPreferences.isCalibrated(vin);
            snapshot.tankCapacityGallons = fuelEconomyPreferences.getTankCapacity(vin);
        }

        snapshot.statusDetail = null;
        return snapshot;
    }

    /** Sample readings so the chart is not empty before the first drive. */
    private void initializeDemoData() {
        float[] sampleData = {20f, 23f, 12f, 18f, 45f, 15f, 35f, 30f, 25f, 20f, 25f, 22f};
        for (float value : sampleData) {
            dataManager.addDataPoint(value);
        }
        views.showHistory(dataManager.getHistoricalData());
    }

    private void startPeriodicUpdates() {
        updateHandler.postDelayed(updateRunnable, UPDATE_INTERVAL);
    }

    private void stopPeriodicUpdates() {
        // Precise callback removal - matches LiveDataActivity pattern
        updateHandler.removeCallbacks(updateRunnable);
    }

    @Override
    public void pvChanged(PvChangeEvent event) {
        runOnUiThread(() -> {
            try {
                log.info("pvChanged() received! Event type: " + event.getType());
                Object value = event.getValue();
                if (value instanceof EcuDataPv) {
                    EcuDataPv pv = (EcuDataPv) value;
                    log.info("Processing PV with PID: " + pv.get(EcuDataPv.FID_PID));
                    updateFromOBDData(pv);
                }
            } catch (Exception e) {
                log.warning("Error processing PV change: " + e.getMessage());
            }
        });
    }

    private void updateFromOBDData(EcuDataPv pv) {
        Object pidObj = pv.get(EcuDataPv.FID_PID);
        if (pidObj == null) return;

        int pid = (Integer) pidObj;
        Object valueObj = pv.get(EcuDataPv.FID_VALUE);
        if (valueObj == null) return;

        String valueStr = valueObj.toString();

        try {
            switch (pid) {
                case 0x2F: // Fuel tank level (%)
                    float fuelLevel = Float.parseFloat(valueStr);
                    views.showFuelLevel(fuelLevel);
                    log.info("Fuel level updated: " + fuelLevel + "%");
                    break;

                case 0x0D: // Vehicle speed (km/h)
                case 0x0C: // Engine RPM
                case 0x04: // Engine load
                    // Trigger MPG calculation when any of these update
                    updateMPGCalculation();
                    break;
            }
        } catch (NumberFormatException e) {
            log.warning("Invalid number format for PID " + Integer.toHexString(pid) + ": " + valueStr);
        }
    }

    /**
     * Fuel flow, instant and average MPG, chart and range from the current readings. Paused below 1 mph.
     */
    private void updateMPGCalculation() {
        float speed = obd.speedMph();
        float rpm = obd.rpm();
        float engineLoad = obd.engineLoad();

        if (speed < 1.0f) {
            return;
        }

        float estimatedFuelRateGalH = fuelRate.fuelRateGalH(rpm, engineLoad, speed);
        views.showFuelFlow(estimatedFuelRateGalH);

        if (estimatedFuelRateGalH > 0.01f) {
            // Cap unrealistic values (sometimes calculations go wild)
            float instantMpg = Math.min(Math.max(speed / estimatedFuelRateGalH, 5f), 99f);
            views.showInstantMpg(instantMpg);

            dataManager.addDataPoint(instantMpg);

            HistoricalData historical = dataManager.getHistoricalData();
            float avgMpg = historical.mediumAverage;
            if (avgMpg > 0) {
                views.showAverageMpg(avgMpg);
            }

            views.showHistory(historical);
            updateRange(avgMpg > 0 ? avgMpg : instantMpg);
        }
    }

    private void updateRange(float avgMpg) {
        try {
            float remainingFuel = (views.fuelLevel() / 100f) * tankCapacity.gallons();
            views.showRange(remainingFuel * avgMpg);
        } catch (Exception e) {
            log.warning("Error calculating range: " + e.getMessage());
        }
    }

    /** Once a second: status, MPG, throttle, time to empty and fuel level, pulled as a backup to the listener. */
    private void updateDisplayedValues() {
        try {
            views.showVehicleStatus(obd.speedMph());
            updateMPGCalculation();
            views.showThrottle(obd.throttle());
            updateTimeToEmpty();

            Float fuelLevel = obd.fuelLevelPercent();
            if (fuelLevel != null) {
                views.showFuelLevel(fuelLevel);
                log.info("Fuel level updated: " + fuelLevel + "%");
            }
        } catch (Exception e) {
            log.warning("updateDisplayedValues error: " + e.getMessage());
        }
    }

    private void updateTimeToEmpty() {
        try {
            float fuelLevelPct = views.fuelLevel();
            float fuelFlowRate = views.fuelFlow();
            float remainingFuel = (fuelLevelPct / 100f) * tankCapacity.gallons();
            views.showTimeToEmpty(FuelEconomyFormat.timeToEmpty(remainingFuel, fuelFlowRate));
        } catch (Exception e) {
            views.showTimeToEmpty("--");
        }
    }
}
