package com.obddroid.ui.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.obddroid.R;
import com.obddroid.services.FaultCodeService;
import com.obddroid.ui.adapters.FaultCodeListAdapter;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.features.faultcodes.data.FaultCodeReportExporter;
import com.obddroid.utils.SnackbarHelper;
import com.obddroid.utils.HelpDialogUtils;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Activity for displaying fault codes (DTCs) from the vehicle.
 * Shows current and pending fault codes with ability to scan and clear codes.
 */
public class FaultCodesActivity extends AppCompatActivity {

    private static final Logger log = Logger.getLogger(FaultCodesActivity.class.getSimpleName());

    // UI components
    private CardView milStatusCard;
    private ImageView milStatusIcon;
    private TextView milStatusText;
    private TextView milStatusSubtitle;
    private RecyclerView faultCodesList;
    private ProgressBar progressBar;
    private Button scanButton;
    private LinearLayout emptyView;
    private VehicleInfoFooter vehicleInfoFooter;

    // Service and adapter
    private FaultCodeService faultCodeService;
    private FaultCodeListAdapter adapter;
    private FaultCodeReportExporter reportExporter;

    // State
    private List<FaultCodeService.FaultCodeInfo> currentCodes = Collections.emptyList();
    private boolean isScanning;
    private boolean hasScanResult = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fault_codes);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.background_secondary));
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.fault_codes_title);
        }

        faultCodeService = new FaultCodeService();
        reportExporter = new FaultCodeReportExporter(this);

        initializeViews();
        setupClickListeners();

        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        if (vehicleInfoFooter != null) {
            vehicleInfoFooter.attachBackHandler(this);
            vehicleInfoFooter.setOverlayView(findViewById(R.id.footer_overlay));
        }

        showEmptyState();
    }

    private void initializeViews() {
        milStatusCard = findViewById(R.id.mil_status_card);
        milStatusIcon = findViewById(R.id.mil_status_icon);
        milStatusText = findViewById(R.id.mil_status_text);
        milStatusSubtitle = findViewById(R.id.mil_status_subtitle);
        faultCodesList = findViewById(R.id.fault_codes_list);
        progressBar = findViewById(R.id.progress_bar);
        scanButton = findViewById(R.id.scan_button);
        emptyView = findViewById(R.id.empty_view);

        faultCodesList.setLayoutManager(new LinearLayoutManager(this));
        faultCodesList.setNestedScrollingEnabled(false);

        adapter = new FaultCodeListAdapter();
        faultCodesList.setAdapter(adapter);
        adapter.setOnCodeClickListener(code -> openFaultCodeDetails(code));
    }

    private void setupClickListeners() {
        scanButton.setOnClickListener(v -> startScan());
    }

    private void openFaultCodeDetails(FaultCodeService.FaultCodeInfo code) {
        Intent intent = new Intent(this, FaultCodeDetailsActivity.class);
        intent.putExtra(FaultCodeDetailsActivity.EXTRA_FAULT_CODE, code.code);
        intent.putExtra(FaultCodeDetailsActivity.EXTRA_FAULT_DESCRIPTION, code.description);
        intent.putExtra(FaultCodeDetailsActivity.EXTRA_IS_PENDING, code.isPending);
        intent.putExtra(FaultCodeDetailsActivity.EXTRA_HAS_FREEZE, code.hasFreeze);
        intent.putExtra(FaultCodeDetailsActivity.EXTRA_DTC_VALUE, code.dtcNumber);
        startActivity(intent);
    }

    private void startScan() {
        startScan(false);
    }

    private void startScan(boolean isVerificationScan) {
        if (isScanning) {
            log.fine("Scan request ignored – already scanning");
            return;
        }

        isScanning = true;
        showProgress();

        // Show appropriate message during verification
        if (isVerificationScan) {
            scanButton.setText("Verifying...");
            SnackbarHelper.showInfo(this, "Checking if codes are cleared...",
                SnackbarHelper.Duration.SHORT);
        }

        faultCodeService.scanAllCodes()
            .thenAccept(codes -> runOnUiThread(() -> {
                if (isVerificationScan) {
                    handleVerificationComplete(codes);
                } else {
                    handleScanComplete(codes);
                }
            }))
            .exceptionally(error -> {
                runOnUiThread(() -> handleScanError(error));
                return null;
            });
    }

    private void handleScanComplete(List<FaultCodeService.FaultCodeInfo> codes) {
        isScanning = false;
        currentCodes = codes != null ? new ArrayList<>(codes) : Collections.emptyList();

        hideProgress();

        if (currentCodes.isEmpty()) {
            showNoCodesState();
        } else {
            showFaultCodes(currentCodes);
        }
    }

    private void handleVerificationComplete(List<FaultCodeService.FaultCodeInfo> codes) {
        isScanning = false;
        currentCodes = codes != null ? new ArrayList<>(codes) : Collections.emptyList();
        hideProgress();
        hasScanResult = true;

        // Reset scan button text
        scanButton.setText(R.string.fault_codes_button_scan);

        if (currentCodes.isEmpty()) {
            // All codes successfully cleared!
            SnackbarHelper.showSuccess(this,
                "✓ All fault codes cleared successfully!",
                SnackbarHelper.Duration.LONG);
            showNoCodesState();
        } else {
            // Some codes remain (likely permanent codes)
            int permanentCount = 0;
            for (FaultCodeService.FaultCodeInfo code : currentCodes) {
                if (code.type == FaultCodeService.CodeType.PERMANENT) {
                    permanentCount++;
                }
            }

            if (permanentCount > 0 && permanentCount == currentCodes.size()) {
                SnackbarHelper.showWarning(this,
                    permanentCount + " permanent code" + (permanentCount > 1 ? "s" : "") +
                    " remain (can't be cleared with scan tool)",
                    SnackbarHelper.Duration.LONG);
            } else {
                SnackbarHelper.showWarning(this,
                    currentCodes.size() + " code" + (currentCodes.size() > 1 ? "s" : "") +
                    " still present after clearing",
                    SnackbarHelper.Duration.LONG);
            }
            showFaultCodes(currentCodes);
        }

        log.info(() -> "Verification complete. " + currentCodes.size() + " codes remain");
    }

    private void handleScanError(Throwable error) {
        log.severe("Fault code scan failed: " + error);
        isScanning = false;
        currentCodes = Collections.emptyList();

        hideProgress();
        showEmptyState();

        String message = extractErrorMessage(error);
        SnackbarHelper.showError(this,
            getString(R.string.fault_codes_scan_failed, message),
            SnackbarHelper.Duration.LONG);
    }

    private void clearCodes() {
        if (isScanning) {
            log.fine("Clear request ignored – operation already in progress");
            SnackbarHelper.showInfo(this, "Scan operation in progress. Please wait...");
            return;
        }

        if (currentCodes.isEmpty()) {
            log.fine("Clear request ignored – no codes to clear");
            SnackbarHelper.showInfo(this, "No fault codes to clear. Scan for codes first.");
            return;
        }

        // Show confirmation dialog
        new AlertDialog.Builder(this)
            .setTitle("Clear Fault Codes")
            .setMessage("Are you sure you want to clear all fault codes? This action cannot be undone.")
            .setPositiveButton("Clear", (dialog, which) -> performClearCodes())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void performClearCodes() {
        isScanning = true;
        showProgress();

        faultCodeService.clearFaultCodes()
            .thenAccept(success -> runOnUiThread(() -> {
                if (success) {
                    log.info("Fault codes cleared successfully - rescanning in 3 seconds");

                    // Show success message
                    SnackbarHelper.showSuccess(this,
                        getString(R.string.fault_codes_clear_success),
                        SnackbarHelper.Duration.SHORT);

                    // Update progress text to show we're waiting
                    hideProgress();

                    // Clear the current display immediately
                    currentCodes.clear();
                    adapter.setFaultCodes(currentCodes);
                    showNoCodesState();  // Show cleared state

                    // Show "waiting to rescan" message
                    scanButton.postDelayed(() -> {
                        SnackbarHelper.showInfo(this,
                            "Verifying codes are cleared...",
                            SnackbarHelper.Duration.SHORT);
                    }, 1000);

                    // Wait 3 seconds before rescanning to give ECU time to reset
                    scanButton.postDelayed(() -> {
                        log.info("Starting automatic verification scan after clearing codes");
                        isScanning = false;  // Reset flag before rescan
                        startScan(true);  // Pass true for verification scan
                    }, 3000);
                } else {
                    hideProgress();
                    SnackbarHelper.showError(this,
                        getString(R.string.fault_codes_clear_failed, getString(R.string.fault_codes_generic_error)),
                        SnackbarHelper.Duration.LONG);
                }
            }))
            .exceptionally(error -> {
                runOnUiThread(() -> {
                    isScanning = false;
                    hideProgress();
                    String message = extractErrorMessage(error);
                    SnackbarHelper.showError(this,
                        getString(R.string.fault_codes_clear_failed, message),
                        SnackbarHelper.Duration.LONG);
                });
                return null;
            });
    }

    private void showFaultCodes(List<FaultCodeService.FaultCodeInfo> codes) {
        emptyView.setVisibility(View.GONE);
        faultCodesList.setVisibility(View.VISIBLE);
        milStatusCard.setVisibility(View.VISIBLE);
        hasScanResult = true;

        int cardColor = ContextCompat.getColor(this, R.color.fault_error);
        int iconColor = ContextCompat.getColor(this, R.color.text_primary_dark);
        milStatusCard.setCardBackgroundColor(cardColor);
        milStatusIcon.setColorFilter(iconColor);

        int count = codes.size();
        String header = getResources().getQuantityString(R.plurals.fault_codes_count, count, count);
        milStatusText.setText(header);

        int pending = 0;
        for (FaultCodeService.FaultCodeInfo code : codes) {
            if (code.isPending) {
                pending++;
            }
        }
        int confirmed = count - pending;
        milStatusSubtitle.setText(getString(R.string.fault_codes_status_summary, confirmed, pending));

        adapter.setFaultCodes(codes);
        scanButton.setText(R.string.fault_codes_button_rescan);
    }

    private void showNoCodesState() {
        emptyView.setVisibility(View.GONE);
        faultCodesList.setVisibility(View.GONE);
        milStatusCard.setVisibility(View.VISIBLE);
        hasScanResult = true;

        int cardColor = ContextCompat.getColor(this, R.color.fault_success);
        int iconColor = ContextCompat.getColor(this, R.color.text_primary_dark);
        milStatusCard.setCardBackgroundColor(cardColor);
        milStatusIcon.setColorFilter(iconColor);

        milStatusText.setText(R.string.fault_codes_no_codes);
        milStatusSubtitle.setText(R.string.fault_codes_engine_ok);

        adapter.setFaultCodes(Collections.emptyList());
        scanButton.setText(R.string.fault_codes_button_rescan);
    }

    private void showEmptyState() {
        emptyView.setVisibility(View.VISIBLE);
        faultCodesList.setVisibility(View.GONE);
        milStatusCard.setVisibility(View.GONE);
        hasScanResult = false;

        adapter.setFaultCodes(Collections.emptyList());
        scanButton.setText(R.string.fault_codes_button_scan);
    }

    private void showProgress() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        scanButton.setEnabled(false);
    }

    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
        scanButton.setEnabled(true);
    }

    private String extractErrorMessage(Throwable throwable) {
        if (throwable instanceof CompletionException || throwable instanceof ExecutionException) {
            Throwable cause = throwable.getCause();
            if (cause != null) {
                return extractErrorMessage(cause);
            }
        }
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return getString(R.string.fault_codes_generic_error);
        }
        return throwable.getMessage();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.fault_codes_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_rescan) {
            startScan();
            return true;
        } else if (id == R.id.action_clear_codes) {
            clearCodes();
            return true;
        } else if (id == R.id.action_save_report) {
            showSaveReportDialog();
            return true;
        } else if (id == R.id.action_info) {
            showInfoDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSaveReportDialog() {
        if (!hasScanResult) {
            SnackbarHelper.showInfo(this, "Scan fault codes before saving a report.");
            return;
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_export_ecu);
        dialog.setCancelable(true);

        TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
        if (dialogTitle != null) {
            dialogTitle.setText(getString(R.string.fault_codes_report_title));
        }

        View csvOption = dialog.findViewById(R.id.option_export_csv);
        if (csvOption != null) {
            csvOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportFaultCodesCsv();
            });
        }

        View jsonOption = dialog.findViewById(R.id.option_export_json);
        if (jsonOption != null) {
            jsonOption.setOnClickListener(v -> {
                dialog.dismiss();
                exportFaultCodesJson();
            });
        }

        View cancelButton = dialog.findViewById(R.id.btn_cancel);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void exportFaultCodesCsv() {
        try {
            String header = milStatusText != null ? milStatusText.getText().toString() : null;
            String subtitle = milStatusSubtitle != null ? milStatusSubtitle.getText().toString() : null;
            String location = reportExporter.exportToCsv(currentCodes, header, subtitle);
            SnackbarHelper.showSuccess(this, "Fault code report saved: " + location, SnackbarHelper.Duration.LONG);
        } catch (IOException e) {
            SnackbarHelper.showError(this, "Failed to export fault code report: " + e.getMessage(), SnackbarHelper.Duration.LONG);
            log.log(Level.SEVERE, "Fault code CSV export failed", e);
        }
    }

    private void exportFaultCodesJson() {
        try {
            String header = milStatusText != null ? milStatusText.getText().toString() : null;
            String subtitle = milStatusSubtitle != null ? milStatusSubtitle.getText().toString() : null;
            String location = reportExporter.exportToJson(currentCodes, header, subtitle);
            SnackbarHelper.showSuccess(this, "Fault code report saved: " + location, SnackbarHelper.Duration.LONG);
        } catch (IOException | JSONException e) {
            SnackbarHelper.showError(this, "Failed to export fault code report: " + e.getMessage(), SnackbarHelper.Duration.LONG);
            log.log(Level.SEVERE, "Fault code JSON export failed", e);
        }
    }

    private void showInfoDialog() {
        HelpDialogUtils.showHelpDialog(
            this,
            R.string.fault_codes_info_title,
            R.string.fault_codes_info_message,
            R.string.fault_codes_info_ack,
            android.R.drawable.ic_menu_info_details
        );
    }
}
