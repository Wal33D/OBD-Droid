package com.obddroid.ui.activities;

import android.content.Intent;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.obddroid.R;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.ecu.EcuScan;
import com.obddroid.ecu.EcuScanHistoryManager;
import com.obddroid.services.VehicleManager;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Activity for selecting a baseline scan to compare against current ECU data.
 * Shows a list of historical scans with option to import from CSV.
 */
public class BaselineScanSelectionActivity extends AppCompatActivity {

    private static final Logger log = Logger.getLogger(BaselineScanSelectionActivity.class.getName());
    public static final String EXTRA_SELECTED_SCAN = "selected_scan";
    public static final String EXTRA_IMPORT_CSV_REQUESTED = "import_csv_requested";

    private LinearLayout scanListContainer;
    private VehicleInfoFooter vehicleInfoFooter;
    private EcuScanHistoryManager scanHistoryManager;
    private List<EcuScan> scans;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_baseline_scan_selection);

        // Set navigation bar color to match footer
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));  // #212121
        }

        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Select Baseline Scan");
        }

        // Initialize views
        scanListContainer = findViewById(R.id.scan_list_container);
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        if (vehicleInfoFooter != null) {
            vehicleInfoFooter.attachBackHandler(this);
        }

        // Set up vehicle info footer
        View overlay = findViewById(R.id.footer_overlay);
        if (vehicleInfoFooter != null && overlay != null) {
            vehicleInfoFooter.setOverlayView(overlay);
            log.info("Footer overlay wired up successfully");
        }

        // Initialize scan history manager
        scanHistoryManager = new EcuScanHistoryManager(this);

        // Load scans for current VIN
        String vin = VehicleManager.getInstance(this).getCurrentVIN();
        if (vin != null && !vin.isEmpty()) {
            scans = scanHistoryManager.getScansForVin(vin);
            populateScans();
        } else {
            log.warning("No VIN available");
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.baseline_scan_selection, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_import_csv) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_IMPORT_CSV_REQUESTED, true);
            setResult(RESULT_OK, resultIntent);
            finish();
            return true;
        } else if (id == R.id.action_clear_all) {
            clearAllScans();
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Show About Baseline Scans dialog
     */
    private void showAboutDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_about_baseline_scan);
        dialog.setCancelable(true);

        // Close button
        View closeBtn = dialog.findViewById(R.id.btn_close);
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Clear all saved scans for the current VIN
     */
    private void clearAllScans() {
        if (scans == null || scans.isEmpty()) {
            return;
        }

        // Show confirmation dialog
        new android.app.AlertDialog.Builder(this)
            .setTitle("Clear All Scans?")
            .setMessage("This will permanently delete all " + scans.size() + " saved scans for this vehicle. This action cannot be undone.")
            .setPositiveButton("Clear All", (dialog, which) -> {
                // Get current VIN
                String vin = VehicleManager.getInstance(this).getCurrentVIN();
                if (vin != null && !vin.isEmpty()) {
                    // Clear all scans for this VIN
                    scanHistoryManager.clearScansForVin(vin);

                    // Reload the scan list
                    scans = scanHistoryManager.getScansForVin(vin);
                    populateScans();

                    log.info("Cleared all scans for VIN: " + vin);
                }
            })
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    /**
     * Populate the scan list with all available scans
     */
    private void populateScans() {
        scanListContainer.removeAllViews();

        if (scans == null || scans.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No saved scans available");
            emptyText.setTextSize(16);
            emptyText.setPadding(32, 32, 32, 32);
            emptyText.setGravity(android.view.Gravity.CENTER);
            emptyText.setTextColor(Color.parseColor("#CCCCCC"));
            scanListContainer.addView(emptyText);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);

        for (int i = 0; i < scans.size(); i++) {
            EcuScan scan = scans.get(i);

            // Create card for scan item
            CardView card = new CardView(this);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardParams.topMargin = dpToPx(6);
            cardParams.bottomMargin = dpToPx(6);
            card.setLayoutParams(cardParams);
            card.setCardBackgroundColor(Color.parseColor("#2C2C2C"));
            card.setRadius(dpToPx(12));
            card.setCardElevation(dpToPx(4));
            card.setClickable(true);
            card.setFocusable(true);

            // Get the ripple effect from theme
            android.util.TypedValue outValue = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            card.setForeground(getDrawable(outValue.resourceId));

            // Create text view inside card
            TextView scanItem = new TextView(this);
            scanItem.setText(sdf.format(scan.getDate()) + " (" + scan.getEcus().size() + " ECUs)");
            scanItem.setTextSize(15);
            scanItem.setTextColor(Color.parseColor("#FFFFFF"));
            scanItem.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

            card.addView(scanItem);

            final int index = i;
            card.setOnClickListener(v -> {
                // Return selected scan
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_SELECTED_SCAN, scans.get(index));
                setResult(RESULT_OK, resultIntent);
                finish();
            });

            scanListContainer.addView(card);
        }
    }

    /**
     * Convert dp to pixels
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
