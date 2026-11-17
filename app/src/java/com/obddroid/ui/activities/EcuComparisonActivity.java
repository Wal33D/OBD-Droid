package com.obddroid.ui.activities;

import android.app.Dialog;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.obddroid.R;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.ecu.EcuInfo;
import com.obddroid.ecu.EcuScan;
import com.obddroid.services.VehicleManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Activity for displaying ECU scan comparison results
 */
public class EcuComparisonActivity extends AppCompatActivity {

    private static final Logger log = Logger.getLogger(EcuComparisonActivity.class.getName());

    public static final String EXTRA_BASELINE_SCAN = "baseline_scan";
    public static final String EXTRA_CURRENT_ECUS = "current_ecus";
    public static final String EXTRA_IS_FROM_IMPORT = "is_from_import";
    public static final String EXTRA_IMPORT_FILENAME = "import_filename";

    private TextView baselineDate;
    private TextView currentDate;
    private LinearLayout comparisonList;
    private TextView changedCountView;
    private TextView unchangedCountView;
    private VehicleInfoFooter vehicleInfoFooter;

    // Store comparison data for export
    private EcuScan baselineScan;
    private List<EcuInfo> currentEcus;
    private boolean isCurrentFromImport;
    private String currentImportFilename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ecu_comparison);

        // Set navigation bar color to match footer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));  // #212121
        }

        // Set up action bar with back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("ECU Comparison");
        }

        // Initialize views
        baselineDate = findViewById(R.id.baseline_date);
        currentDate = findViewById(R.id.current_date);
        comparisonList = findViewById(R.id.comparison_list);
        changedCountView = findViewById(R.id.changed_count);
        unchangedCountView = findViewById(R.id.unchanged_count);
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        if (vehicleInfoFooter != null) {
            vehicleInfoFooter.attachBackHandler(this);
        }

        // Set up vehicle info footer with overlay
        View overlay = findViewById(R.id.footer_overlay);
        if (vehicleInfoFooter != null && overlay != null) {
            vehicleInfoFooter.setOverlayView(overlay);
            log.info("Footer overlay wired up successfully");
        }

        // Get data from intent
        this.baselineScan = getSerializableExtraCompat(EXTRA_BASELINE_SCAN, EcuScan.class);
        Serializable currentEcusSerializable = getSerializableExtraCompat(EXTRA_CURRENT_ECUS);
        if (currentEcusSerializable instanceof List) {
            //noinspection unchecked
            this.currentEcus = (List<EcuInfo>) currentEcusSerializable;
        }
        this.isCurrentFromImport = getIntent().getBooleanExtra(EXTRA_IS_FROM_IMPORT, false);
        this.currentImportFilename = getIntent().getStringExtra(EXTRA_IMPORT_FILENAME);

        if (baselineScan == null || currentEcus == null) {
            log.severe("Missing comparison data");
            finish();
            return;
        }

        // Set current date label
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
        baselineDate.setText(sdf.format(baselineScan.getDate()));

        if (isCurrentFromImport && currentImportFilename != null) {
            currentDate.setText(currentImportFilename); // Already formatted timestamp from filename
        } else {
            currentDate.setText("Live Data");
        }

        // Populate comparison
        populateComparison(baselineScan, currentEcus);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_ecu_comparison, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_export_csv) {
            showExportDialog();
            return true;
        } else if (item.getItemId() == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Show About ECU Comparison dialog
     */
    private void showAboutDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_about_ecu_comparison);
        dialog.setCancelable(true);

        // Close button
        View closeBtn = dialog.findViewById(R.id.btn_close);
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Show export dialog to choose format
     */
    private void showExportDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_export_ecu);
        dialog.setCancelable(true);

        // Set dialog title
        TextView dialogTitle = dialog.findViewById(R.id.dialog_title);
        dialogTitle.setText("Save Report");

        // CSV option
        View csvOption = dialog.findViewById(R.id.option_export_csv);
        csvOption.setOnClickListener(v -> {
            dialog.dismiss();
            exportComparisonToCSV();
        });

        // JSON option
        View jsonOption = dialog.findViewById(R.id.option_export_json);
        jsonOption.setOnClickListener(v -> {
            dialog.dismiss();
            exportComparisonToJSON();
        });

        // Cancel button
        View cancelBtn = dialog.findViewById(R.id.btn_cancel);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Export comparison data to JSON format
     */
    private void exportComparisonToJSON() {
        showSnackbar("JSON export coming soon");
        // TODO: Implement JSON export
    }

    private <T extends Serializable> T getSerializableExtraCompat(String key, Class<T> clazz) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getIntent().getSerializableExtra(key, clazz);
        }
        @SuppressWarnings("deprecation")
        Serializable value = getIntent().getSerializableExtra(key);
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        return null;
    }

    private Serializable getSerializableExtraCompat(String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getIntent().getSerializableExtra(key, Serializable.class);
        }
        @SuppressWarnings("deprecation")
        Serializable value = getIntent().getSerializableExtra(key);
        return value;
    }

    private void exportComparisonToCSV() {
        if (baselineScan == null || currentEcus == null) {
            showSnackbar("No comparison data to export");
            return;
        }

        try {
            // Get VIN and extract last 6 digits
            String vin = VehicleManager.getInstance(this).getCurrentVIN();
            String vinSuffix = "UNKNOWN";
            if (vin != null && vin.length() >= 6) {
                vinSuffix = vin.substring(vin.length() - 6);
            }

            // Create filename with VIN suffix and timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = sdf.format(new Date());
            String filename = vinSuffix + "-ecu_comparison-" + timestamp + ".csv";

            // Use MediaStore for Android 10+ to save to Documents/OBDroid
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use MediaStore API
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/OBDroid");

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                if (uri != null) {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    OutputStreamWriter writer = new OutputStreamWriter(outputStream);

                    writeComparisonCSV(writer);

                    writer.flush();
                    writer.close();
                    outputStream.close();

                    log.info("Exported ECU comparison via MediaStore to: Documents/OBDroid/" + filename);
                    showSnackbar("Exported comparison to:\nDocuments/OBDroid/" + filename);
                } else {
                    throw new IOException("Failed to create file in Documents");
                }
            } else {
                // Android 9 and below - Use legacy file system
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File obdroidDir = new File(documentsDir, "OBDroid");
                if (!obdroidDir.exists()) {
                    obdroidDir.mkdirs();
                }

                File csvFile = new File(obdroidDir, filename);
                FileWriter writer = new FileWriter(csvFile);

                writeComparisonCSV(writer);

                writer.flush();
                writer.close();

                log.info("Exported ECU comparison to: " + csvFile.getAbsolutePath());
                showSnackbar("Exported comparison to:\nDocuments/OBDroid/" + csvFile.getName());
            }

        } catch (IOException e) {
            log.severe("Error exporting comparison CSV: " + e.getMessage());
            showSnackbar("Export failed: " + e.getMessage());
        }
    }

    private void writeComparisonCSV(Appendable writer) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);

        // Write metadata
        writer.append("# ECU Comparison Report\n");
        writer.append("# Baseline Scan: ").append(sdf.format(baselineScan.getDate())).append("\n");
        writer.append("# Current Scan: Live Data\n");
        writer.append("# VIN: ").append(csvEscape(baselineScan.getVin())).append("\n");
        writer.append("\n");

        // Write header
        writer.append("ECU Address,Name,Status,Baseline Cal ID,Current Cal ID,Baseline CVN,Current CVN\n");

        // Compare ECUs
        List<EcuInfo> baselineEcus = baselineScan.getEcus();

        // Process current ECUs
        for (EcuInfo currentEcu : currentEcus) {
            EcuInfo baselineEcu = findEcuByAddress(baselineEcus, currentEcu.getAddress());

            writer.append(String.format("0x%X", currentEcu.getAddress())).append(",");
            writer.append(csvEscape(currentEcu.getName())).append(",");

            if (baselineEcu == null) {
                // NEW ECU
                writer.append("NEW").append(",");
                writer.append("").append(",");
                writer.append(csvEscape(currentEcu.getCalibrationId())).append(",");
                writer.append("").append(",");
                writer.append(csvEscape(currentEcu.getCalibrationVerification())).append("\n");
            } else {
                // Compare Cal ID and CVN
                String baseCalId = baselineEcu.getCalibrationId();
                String currCalId = currentEcu.getCalibrationId();
                boolean calIdChanged = !equals(baseCalId, currCalId);

                String baseCvn = baselineEcu.getCalibrationVerification();
                String currCvn = currentEcu.getCalibrationVerification();
                boolean cvnChanged = !equals(baseCvn, currCvn);

                if (calIdChanged || cvnChanged) {
                    writer.append("CHANGED").append(",");
                } else {
                    writer.append("NO CHANGES").append(",");
                }

                writer.append(csvEscape(baseCalId)).append(",");
                writer.append(csvEscape(currCalId)).append(",");
                writer.append(csvEscape(baseCvn)).append(",");
                writer.append(csvEscape(currCvn)).append("\n");
            }
        }

        // Check for removed ECUs
        for (EcuInfo baselineEcu : baselineEcus) {
            if (findEcuByAddress(currentEcus, baselineEcu.getAddress()) == null) {
                writer.append(String.format("0x%X", baselineEcu.getAddress())).append(",");
                writer.append(csvEscape(baselineEcu.getName())).append(",");
                writer.append("REMOVED").append(",");
                writer.append(csvEscape(baselineEcu.getCalibrationId())).append(",");
                writer.append("").append(",");
                writer.append(csvEscape(baselineEcu.getCalibrationVerification())).append(",");
                writer.append("").append("\n");
            }
        }
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    private void populateComparison(EcuScan baseline, List<EcuInfo> currentEcus) {
        // Compare ECUs
        int changedCount = 0;
        int unchangedCount = 0;
        List<EcuInfo> baselineEcus = baseline.getEcus();

        // Add current ECUs to comparison
        for (EcuInfo currentEcu : currentEcus) {
            EcuInfo baselineEcu = findEcuByAddress(baselineEcus, currentEcu.getAddress());

            if (baselineEcu == null) {
                // NEW ECU
                addComparisonItem(null, currentEcu, null, null,
                    "NEW ECU", "#F57C00", true);
                changedCount++;
            } else {
                // Compare Cal ID and CVN
                String baseCalId = baselineEcu.getCalibrationId();
                String currCalId = currentEcu.getCalibrationId();
                boolean calIdChanged = !equals(baseCalId, currCalId);

                String baseCvn = baselineEcu.getCalibrationVerification();
                String currCvn = currentEcu.getCalibrationVerification();
                boolean cvnChanged = !equals(baseCvn, currCvn);

                if (calIdChanged || cvnChanged) {
                    changedCount++;
                    addComparisonItem(baselineEcu, currentEcu,
                        calIdChanged ? new String[]{baseCalId, currCalId} : null,
                        cvnChanged ? new String[]{baseCvn, currCvn} : null,
                        "CHANGED", "#F57C00", true);
                } else {
                    unchangedCount++;
                    addComparisonItem(baselineEcu, currentEcu, null, null,
                        "No changes", "#4CAF50", false);
                }
            }
        }

        // Check for removed ECUs
        for (EcuInfo baselineEcu : baselineEcus) {
            if (findEcuByAddress(currentEcus, baselineEcu.getAddress()) == null) {
                addComparisonItem(baselineEcu, null, null, null,
                    "REMOVED", "#D32F2F", false);
                changedCount++;
            }
        }

        // Update summary counts
        changedCountView.setText(String.valueOf(changedCount));
        unchangedCountView.setText(String.valueOf(unchangedCount));
    }

    private void addComparisonItem(EcuInfo baselineEcu, EcuInfo currentEcu,
                                   String[] calIdChange, String[] cvnChange,
                                   String statusText, String statusColor, boolean showChanges) {
        View itemView = getLayoutInflater().inflate(R.layout.item_ecu_comparison, comparisonList, false);

        // Use currentEcu if available, otherwise baselineEcu (for removed ECUs)
        EcuInfo displayEcu = currentEcu != null ? currentEcu : baselineEcu;

        // Set ECU info
        TextView ecuName = itemView.findViewById(R.id.ecu_name);
        TextView ecuAddress = itemView.findViewById(R.id.ecu_address);
        TextView status = itemView.findViewById(R.id.status_text);
        ImageView statusIcon = itemView.findViewById(R.id.status_icon);

        ecuName.setText(displayEcu.getName());
        ecuAddress.setText(String.format("0x%X", displayEcu.getAddress()));
        status.setText(statusText);

        // Set status color
        int color = android.graphics.Color.parseColor(statusColor);
        status.setTextColor(color);

        // Set status icon based on type (icons have their own colors)
        if (statusText.contains("CHANGED")) {
            statusIcon.setImageResource(R.drawable.ic_ecu_status_changed);
        } else if (statusText.contains("NEW")) {
            statusIcon.setImageResource(R.drawable.ic_ecu_status_new);
        } else if (statusText.contains("REMOVED")) {
            statusIcon.setImageResource(R.drawable.ic_ecu_status_removed);
        } else {
            // No changes - use checkmark
            statusIcon.setImageResource(R.drawable.ic_ecu_status_ok);
        }

        // Show Cal ID and CVN (always visible)
        LinearLayout calIdDisplayContainer = itemView.findViewById(R.id.calibration_id_container);
        LinearLayout cvnDisplayContainer = itemView.findViewById(R.id.calibration_verification_container);
        TextView calIdText = itemView.findViewById(R.id.ecu_calibration_id);
        TextView cvnText = itemView.findViewById(R.id.ecu_calibration_verification);

        String calId = displayEcu.getCalibrationId();
        String cvn = displayEcu.getCalibrationVerification();

        if (calId != null && !calId.isEmpty()) {
            calIdDisplayContainer.setVisibility(View.VISIBLE);
            calIdText.setText(calId);
        }

        if (cvn != null && !cvn.isEmpty()) {
            cvnDisplayContainer.setVisibility(View.VISIBLE);
            cvnText.setText(cvn);
        }

        // Show changes if applicable
        if (showChanges && (calIdChange != null || cvnChange != null)) {
            LinearLayout changesContainer = itemView.findViewById(R.id.changes_container);
            changesContainer.setVisibility(View.VISIBLE);

            if (calIdChange != null) {
                LinearLayout calIdContainer = itemView.findViewById(R.id.cal_id_change_container);
                calIdContainer.setVisibility(View.VISIBLE);
                TextView calIdOld = itemView.findViewById(R.id.cal_id_old);
                TextView calIdNew = itemView.findViewById(R.id.cal_id_new);
                calIdOld.setText(calIdChange[0] != null ? calIdChange[0] : "N/A");
                calIdNew.setText(calIdChange[1] != null ? calIdChange[1] : "N/A");
            }

            if (cvnChange != null) {
                LinearLayout cvnContainer = itemView.findViewById(R.id.cvn_change_container);
                cvnContainer.setVisibility(View.VISIBLE);
                TextView cvnOld = itemView.findViewById(R.id.cvn_old);
                TextView cvnNew = itemView.findViewById(R.id.cvn_new);
                cvnOld.setText(cvnChange[0] != null ? cvnChange[0] : "N/A");
                cvnNew.setText(cvnChange[1] != null ? cvnChange[1] : "N/A");
            }
        }

        // Add tap listener to show detailed comparison modal with animation
        itemView.setOnClickListener(v -> {
            // Animate the tap
            v.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start();
                    showEcuDetailsModal(baselineEcu, currentEcu, statusText);
                })
                .start();
        });

        comparisonList.addView(itemView);
    }

    /**
     * Show detailed comparison modal for an individual ECU
     */
    private void showEcuDetailsModal(EcuInfo baselineEcu, EcuInfo currentEcu, String statusText) {
        // Use currentEcu if available, otherwise baselineEcu
        EcuInfo displayEcu = currentEcu != null ? currentEcu : baselineEcu;

        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_ecu_details, null);

        // Set header info
        TextView ecuNameTitle = dialogView.findViewById(R.id.ecu_name_title);
        TextView ecuAddressSubtitle = dialogView.findViewById(R.id.ecu_address_subtitle);
        ecuNameTitle.setText(displayEcu.getName());
        ecuAddressSubtitle.setText(String.format("0x%X", displayEcu.getAddress()));

        // Set timestamps
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
        TextView baselineTimestamp = dialogView.findViewById(R.id.baseline_timestamp);
        TextView currentTimestamp = dialogView.findViewById(R.id.current_timestamp);
        baselineTimestamp.setText(sdf.format(baselineScan.getDate()));

        // Set current scan timestamp - either parsed timestamp or "Live Data"
        if (isCurrentFromImport && currentImportFilename != null) {
            currentTimestamp.setText(currentImportFilename); // Already formatted timestamp
        } else {
            currentTimestamp.setText("Live Data");
        }

        // Set status
        TextView ecuStatus = dialogView.findViewById(R.id.ecu_status);
        ecuStatus.setText(statusText);

        // Set status color
        if (statusText.contains("CHANGED") || statusText.contains("NEW")) {
            ecuStatus.setTextColor(android.graphics.Color.parseColor("#F57C00"));
        } else if (statusText.contains("REMOVED")) {
            ecuStatus.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
        } else {
            ecuStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
        }

        // Set baseline data
        TextView baselineCalId = dialogView.findViewById(R.id.baseline_cal_id);
        TextView baselineCvn = dialogView.findViewById(R.id.baseline_cvn);

        if (baselineEcu != null) {
            String baseCalId = baselineEcu.getCalibrationId();
            String baseCvn = baselineEcu.getCalibrationVerification();
            baselineCalId.setText(baseCalId != null ? baseCalId : "N/A");
            baselineCvn.setText(baseCvn != null ? baseCvn : "N/A");
        } else {
            baselineCalId.setText("(Not in baseline scan)");
            baselineCvn.setText("N/A");
        }

        // Set current data
        TextView currentCalId = dialogView.findViewById(R.id.current_cal_id);
        TextView currentCvn = dialogView.findViewById(R.id.current_cvn);

        if (currentEcu != null) {
            String currCalId = currentEcu.getCalibrationId();
            String currCvn = currentEcu.getCalibrationVerification();
            currentCalId.setText(currCalId != null ? currCalId : "N/A");
            currentCvn.setText(currCvn != null ? currCvn : "N/A");
        } else {
            currentCalId.setText("(Removed from vehicle)");
            currentCvn.setText("N/A");
        }

        // Create dialog
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        // Set up close button
        Button closeButton = dialogView.findViewById(R.id.btn_close);
        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private EcuInfo findEcuByAddress(List<EcuInfo> ecus, int address) {
        for (EcuInfo ecu : ecus) {
            if (ecu.getAddress() == address) {
                return ecu;
            }
        }
        return null;
    }

    private boolean equals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
