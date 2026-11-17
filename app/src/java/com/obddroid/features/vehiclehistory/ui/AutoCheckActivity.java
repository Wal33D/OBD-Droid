package com.obddroid.features.vehiclehistory.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.obddroid.R;
import com.obddroid.features.vehiclehistory.data.AutoCheckService;
import com.obddroid.features.vehiclehistory.model.AutoCheckReport;
import com.obddroid.services.VehicleManager;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.utils.SnackbarHelper;

/**
 * Vehicle history (AutoCheck) for the connected vehicle's VIN.
 * <p>
 * Opens on the cached report when there is one ({@link AutoCheckReportCache}); "Check history" and
 * Refresh fetch a new report, then its PDF in the background. {@link AutoCheckReportView} draws the
 * report and {@link AutoCheckExporter} saves it as PDF, CSV or JSON. This class keeps the screen state
 * (empty, loading, error, report) and the fetches.
 */
public class AutoCheckActivity extends AppCompatActivity {

    private static final String TAG = "AutoCheckActivity";

    // Screen state views
    private CardView emptyStateCard;
    private Button checkHistoryButton;
    private CardView loadingContainer;
    private TextView loadingText;
    private CardView errorCard;
    private TextView errorMessage;
    private LinearLayout reportContainer;

    private String currentVin;
    private AutoCheckReport currentReport;
    private String currentPdfFilePath;

    private AutoCheckReportView reportView;
    private AutoCheckReportCache cache;
    private AutoCheckExporter exporter;
    private AutoCheckService autoCheckService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_autocheck);

        // Set navigation bar color to match footer
        getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Vehicle History");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        autoCheckService = new AutoCheckService(this);
        cache = new AutoCheckReportCache(this);
        exporter = new AutoCheckExporter(this);

        emptyStateCard = findViewById(R.id.empty_state_card);
        checkHistoryButton = findViewById(R.id.check_history_button);
        loadingContainer = findViewById(R.id.loading_container);
        loadingText = findViewById(R.id.loading_text);
        errorCard = findViewById(R.id.error_card);
        errorMessage = findViewById(R.id.error_message);
        reportContainer = findViewById(R.id.report_container);
        reportView = new AutoCheckReportView(this);

        setupFooterOverlay();
        loadCurrentVin();

        checkHistoryButton.setOnClickListener(v -> fetchVehicleHistory());

        loadCachedReport();
        checkApiHealth();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.vehicle_history_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem saveItem = menu.findItem(R.id.action_save_report);
        if (saveItem != null) {
            saveItem.setVisible(currentReport != null);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            // Fetch a new report: hide the current one and show loading
            reportContainer.setVisibility(View.GONE);
            errorCard.setVisibility(View.GONE);
            loadingContainer.setVisibility(View.VISIBLE);
            loadingText.setText("Refreshing vehicle history...");

            fetchVehicleHistory();
            return true;
        } else if (item.getItemId() == R.id.action_save_report) {
            exporter.showSaveDialog(new AutoCheckExporter.Source() {
                @Override
                public AutoCheckReport report() {
                    return currentReport;
                }

                @Override
                public String pdfFilePath() {
                    return currentPdfFilePath;
                }
            });
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupFooterOverlay() {
        VehicleInfoFooter vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        View overlay = findViewById(R.id.footer_overlay);

        if (vehicleInfoFooter != null && overlay != null) {
            vehicleInfoFooter.setOverlayView(overlay);
        }
    }

    /** The VIN from VehicleManager; without one the button is disabled. */
    private void loadCurrentVin() {
        try {
            currentVin = VehicleManager.getInstance().getCurrentVIN();

            if (currentVin == null || currentVin.isEmpty()) {
                checkHistoryButton.setEnabled(false);
                checkHistoryButton.setText("No VIN Available");
            }
        } catch (Exception e) {
            checkHistoryButton.setEnabled(false);
            checkHistoryButton.setText("VIN Not Available");
        }
    }

    private void checkApiHealth() {
        autoCheckService.checkHealth(healthy -> {
            if (!healthy) {
                showError("Warning: AutoCheck API server is not reachable.\n\n" +
                        "Please ensure:\n" +
                        "1. Your laptop is running the API server\n" +
                        "2. Both devices are on the same WiFi\n" +
                        "3. Update the IP address if needed");
            }
        });
    }

    private void fetchVehicleHistory() {
        if (currentVin == null || currentVin.isEmpty()) {
            SnackbarHelper.showSnackbar(this, "No VIN available", SnackbarHelper.MessageType.ERROR);
            return;
        }

        if (currentVin.length() != 17) {
            SnackbarHelper.showSnackbar(this, "Invalid VIN length", SnackbarHelper.MessageType.ERROR);
            return;
        }

        String vin = currentVin.toUpperCase();

        errorCard.setVisibility(View.GONE);
        reportContainer.setVisibility(View.GONE);
        emptyStateCard.setVisibility(View.GONE);
        invalidateOptionsMenu();

        checkHistoryButton.setEnabled(false);
        loadingContainer.setVisibility(View.VISIBLE);

        // The report without its PDF first, for a faster first view
        autoCheckService.fetchReport(vin, new AutoCheckService.AutoCheckCallback() {
            @Override
            public void onSuccess(AutoCheckReport report) {
                loadingContainer.setVisibility(View.GONE);
                checkHistoryButton.setEnabled(true);

                cache.save(report, currentPdfFilePath);
                displayReport(report);

                SnackbarHelper.showSnackbar(AutoCheckActivity.this,
                    "Report loaded successfully!",
                    SnackbarHelper.MessageType.SUCCESS);

                generatePdfInBackground(vin);
            }

            @Override
            public void onError(String error) {
                loadingContainer.setVisibility(View.GONE);
                checkHistoryButton.setEnabled(true);
                showError(error);
            }
        });
    }

    /** Shows the report and keeps it for the save menu. */
    private void displayReport(AutoCheckReport report) {
        this.currentReport = report;
        invalidateOptionsMenu();

        errorCard.setVisibility(View.GONE);
        emptyStateCard.setVisibility(View.GONE);
        reportContainer.setVisibility(View.VISIBLE);

        reportView.show(report);
    }

    /**
     * Fetches the report again with its PDF, silently, so the PDF is ready when the user saves. A failure
     * is not shown: the report is already on screen.
     */
    private void generatePdfInBackground(String vin) {
        autoCheckService.fetchReportWithPdf(vin, new AutoCheckService.AutoCheckPdfCallback() {
            @Override
            public void onSuccess(AutoCheckReport report, String pdfFilePath) {
                currentPdfFilePath = pdfFilePath;

                if (pdfFilePath != null && !pdfFilePath.isEmpty()) {
                    invalidateOptionsMenu();
                    cache.save(report, currentPdfFilePath);
                }
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    private void showError(String error) {
        errorCard.setVisibility(View.VISIBLE);
        errorMessage.setText(error);
        reportContainer.setVisibility(View.GONE);
        emptyStateCard.setVisibility(View.VISIBLE);  // Show empty state so user can retry
        invalidateOptionsMenu();
    }

    /** Opens on the cached report for the current VIN, and its PDF, when there is one. */
    private void loadCachedReport() {
        if (currentVin == null || currentVin.isEmpty()) {
            return;
        }

        try {
            if (cache.has(currentVin)) {
                loadingContainer.setVisibility(View.VISIBLE);
                loadingText.setText("Loading cached report...");

                AutoCheckReport report = cache.load(currentVin);
                if (report != null) {
                    displayReport(report);

                    loadingContainer.setVisibility(View.GONE);
                    loadingText.setText("Loading vehicle history...");

                    String pdfPath = cache.findPdf(currentVin);
                    if (pdfPath != null) {
                        currentPdfFilePath = pdfPath;
                        invalidateOptionsMenu();
                    }
                } else {
                    loadingContainer.setVisibility(View.GONE);
                    loadingText.setText("Loading vehicle history...");
                }
            }
        } catch (Exception e) {
            // If the cache cannot be read, the user can fetch a new report
            loadingContainer.setVisibility(View.GONE);
            loadingText.setText("Loading vehicle history...");
            Log.w(TAG, "Could not load cached report", e);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoCheckService != null) {
            autoCheckService.shutdown();
        }
    }
}
