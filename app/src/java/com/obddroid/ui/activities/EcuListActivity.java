package com.obddroid.ui.activities;

import android.app.Dialog;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.obddroid.R;
import com.obddroid.obd.ElmProt;
import com.obddroid.obd.ObdProt;
import com.obddroid.services.CommService;
import com.obddroid.services.EcuDiscoveryService;
import com.obddroid.ui.adapters.EcuAdapter;
import com.obddroid.ui.components.VehicleInfoFooter;
import com.obddroid.ecu.EcuCacheManager;
import com.obddroid.ecu.EcuInfo;
import com.obddroid.ecu.EcuManager;
import com.obddroid.ecu.EcuScan;
import com.obddroid.ecu.EcuScanHistoryManager;
import com.obddroid.services.VehicleManager;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Activity for displaying the list of discovered ECUs in the vehicle.
 * Shows ECU addresses, names, calibration IDs, and other diagnostic information.
 */
public class EcuListActivity extends AppCompatActivity implements EcuManager.EcuManagerListener {

    private static final Logger log = Logger.getLogger(EcuListActivity.class.getName());
    private static final int REQUEST_CODE_IMPORT_CSV = 1001;
    private static final int REQUEST_CODE_IMPORT_CSV_FOR_COMPARISON = 1002;
    private static final int REQUEST_CODE_SELECT_BASELINE_SCAN = 1003;
    private static final int REQUEST_CODE_IMPORT_JSON = 1004;
    private static final int REQUEST_CODE_IMPORT_JSON_FOR_COMPARISON = 1005;

    private RecyclerView recyclerView;
    private EcuAdapter adapter;
    private View emptyView;
    private ProgressBar progressBar;
    private Button scanButton;
    private View snackbarAnchor;  // Anchor view for snackbars
    private EcuManager ecuManager;
    private EcuCacheManager cacheManager;
    private EcuScanHistoryManager scanHistoryManager;
    private VehicleInfoFooter vehicleInfoFooter;
    private boolean allowAutoUpdates = false;  // Don't show auto-discovered ECUs until scan is done
    private boolean isDataFromImport = false;  // Track if current data is from CSV import
    private String importedFileName = null;  // Track imported file name

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ecu_list);

        // Set navigation bar color to match footer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.background_secondary));  // #212121
        }

        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.ecu_list_title);
        }

        // Initialize views
        recyclerView = findViewById(R.id.ecu_recycler_view);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progress_bar);
        scanButton = findViewById(R.id.scan_button);
        vehicleInfoFooter = findViewById(R.id.vehicle_footer);
        if (vehicleInfoFooter != null) {
            vehicleInfoFooter.attachBackHandler(this);
        }
        snackbarAnchor = findViewById(R.id.content_frame);  // Use content frame for snackbar creation

        // Set up RecyclerView
        adapter = new EcuAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Set up ECU Manager
        ecuManager = EcuManager.getInstance();

        // Clear any old ECU data to start fresh
        // This prevents showing stale Mode 9 data from the app's automatic discovery
        ecuManager.clear();

        ecuManager.addListener(this);

        // Set up scan button
        scanButton.setOnClickListener(v -> scanForEcus());

        // Set up vehicle info footer (already initialized above)
        View overlay = findViewById(R.id.footer_overlay);

        if (vehicleInfoFooter != null && overlay != null) {
            vehicleInfoFooter.setOverlayView(overlay);
            log.info("Footer overlay wired up successfully");
        }

        // Initialize cache manager
        cacheManager = new EcuCacheManager(this);

        // Initialize scan history manager
        scanHistoryManager = new EcuScanHistoryManager(this);

        // Start with empty view - wait for user to tap scan or import
        updateEmptyView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // DON'T call startListening() - we want FRESH scans only!
        // EcuListActivity uses isolated EcuDiscoveryService for on-demand scanning.
        // Loading cached addresses from the connection stage would pollute fresh scan results.
        updateEmptyView();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Nothing to stop - we never started listening
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ecuManager.removeListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.ecu_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_rescan_ecus) {
            scanForEcus();
            return true;
        } else if (id == R.id.action_compare_scans) {
            openCompareScans();
            return true;
        } else if (id == R.id.action_export_csv) {
            showExportDialog();
            return true;
        } else if (id == R.id.action_import_csv) {
            showImportDialog();
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Show About ECU Modules dialog
     */
    private void showAboutDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_about_ecu);
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
            exportToCSV();
        });

        // JSON option
        View jsonOption = dialog.findViewById(R.id.option_export_json);
        jsonOption.setOnClickListener(v -> {
            dialog.dismiss();
            exportToJSON();
        });

        // Cancel button
        View cancelBtn = dialog.findViewById(R.id.btn_cancel);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Show import dialog to choose format
     */
    private void showImportDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_import_ecu);
        dialog.setCancelable(true);

        // CSV option
        View csvOption = dialog.findViewById(R.id.option_import_csv);
        csvOption.setOnClickListener(v -> {
            dialog.dismiss();
            importFromCSV();
        });

        // JSON option
        View jsonOption = dialog.findViewById(R.id.option_import_json);
        jsonOption.setOnClickListener(v -> {
            dialog.dismiss();
            importFromJSON();
        });

        // Cancel button
        View cancelBtn = dialog.findViewById(R.id.btn_cancel);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Export ECU data to JSON format
     */
    private void exportToJSON() {
        // Get ECU data from adapter
        List<EcuInfo> ecuList = adapter.getEcuList();
        if (ecuList.isEmpty()) {
            showSnackbar("No ECU data to export", Snackbar.LENGTH_SHORT);
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
            String filename = vinSuffix + "-ecu_modules-" + timestamp + ".json";

            // Build JSON structure
            JSONObject root = new JSONObject();
            root.put("export_date", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));
            root.put("vin", vin != null ? vin : "");
            root.put("ecu_count", ecuList.size());

            JSONArray ecusArray = new JSONArray();
            for (EcuInfo ecu : ecuList) {
                JSONObject ecuJson = new JSONObject();
                ecuJson.put("address", String.format("0x%X", ecu.getAddress()));
                ecuJson.put("address_decimal", ecu.getAddress());
                ecuJson.put("name", ecu.getName() != null ? ecu.getName() : "");
                ecuJson.put("type", ecu.getEcuType() != null ? ecu.getEcuType() : "");
                ecuJson.put("calibration_id", ecu.getCalibrationId() != null ? ecu.getCalibrationId() : "");
                ecuJson.put("cvn", ecu.getCalibrationVerification() != null ? ecu.getCalibrationVerification() : "");
                ecusArray.put(ecuJson);
            }
            root.put("ecus", ecusArray);

            String jsonString = root.toString(2);  // Pretty print with 2-space indent

            // Use MediaStore for Android 10+ to save to Documents/OBD-Droid
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use MediaStore API
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/OBD-Droid");

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                if (uri != null) {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    OutputStreamWriter writer = new OutputStreamWriter(outputStream);
                    writer.write(jsonString);
                    writer.flush();
                    writer.close();
                    outputStream.close();

                    log.info("Exported " + ecuList.size() + " ECUs via MediaStore to: Documents/OBD-Droid/" + filename);
                    showSnackbar("Exported " + ecuList.size() + " ECU(s) to:\nDocuments/OBD-Droid/" + filename,
                        Snackbar.LENGTH_LONG);
                } else {
                    throw new IOException("Failed to create file in Documents");
                }
            } else {
                // Android 9 and below - Use legacy file system
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File obdroidDir = new File(documentsDir, "OBD-Droid");
                if (!obdroidDir.exists()) {
                    obdroidDir.mkdirs();
                }

                File jsonFile = new File(obdroidDir, filename);
                FileWriter writer = new FileWriter(jsonFile);
                writer.write(jsonString);
                writer.flush();
                writer.close();

                log.info("Exported " + ecuList.size() + " ECUs to: " + jsonFile.getAbsolutePath());
                showSnackbar("Exported " + ecuList.size() + " ECU(s) to:\nDocuments/OBD-Droid/" + jsonFile.getName(),
                    Snackbar.LENGTH_LONG);
            }

        } catch (IOException | JSONException e) {
            log.severe("Error exporting JSON: " + e.getMessage());
            showSnackbar("Export failed: " + e.getMessage(),
                Snackbar.LENGTH_LONG);
        }
    }

    /**
     * Load existing ECU data from manager
     */
    private void loadEcuData() {
        List<EcuInfo> ecuList = ecuManager.getEcuList();
        log.info("Loading ECU data: " + ecuList.size() + " ECUs found");

        adapter.setEcuList(ecuList);
        updateEmptyView();
    }

    /**
     * Scan for ECUs using the isolated discovery service.
     * This uses Mode 9 commands with headers enabled to properly identify ECU addresses.
     * NOTE: This is SEPARATE from the standard Mode 9 flow used elsewhere in the app.
     */
    private void scanForEcus() {
        log.info("Scan for ECUs requested (using isolated discovery service)");

        // Check if connected to vehicle
        if (CommService.elm == null) {
            showSnackbar("Not connected to vehicle", Snackbar.LENGTH_SHORT);
            return;
        }

        // Check if we're actually connected (not just initialized)
        ElmProt.STAT status = CommService.elm.getStatus();
        if (status != ElmProt.STAT.CONNECTED && status != ElmProt.STAT.ECU_DETECTED) {
            showSnackbar("Please connect to vehicle first", Snackbar.LENGTH_SHORT);
            return;
        }

        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);

        // Clear existing data
        ecuManager.clear();
        adapter.setEcuList(null);

        // Reset import flags since we're scanning live
        isDataFromImport = false;
        importedFileName = null;

        showSnackbar("Scanning for ECUs with detailed discovery...", Snackbar.LENGTH_SHORT);

        // Use the NEW isolated discovery service
        log.info("Starting isolated ECU discovery (with headers enabled)");

        // Run discovery on background thread
        new Thread(() -> {
            try {
                // Create discovery service (no params needed - uses CommService.elm)
                EcuDiscoveryService discoveryService = new EcuDiscoveryService();

                // Start discovery (returns CompletableFuture)
                Map<Integer, EcuDiscoveryService.EcuDiscoveryInfo> discoveryData =
                    discoveryService.discoverEcus().get(15, java.util.concurrent.TimeUnit.SECONDS);

                log.info("Discovery complete! Found " + discoveryData.size() + " ECUs");

                // Update ECU manager with discovery data
                ecuManager.updateFromDiscoveryData(discoveryData);

                // Update UI on main thread
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    scanButton.setEnabled(true);

                    // Enable auto-updates now that we've done our first scan
                    allowAutoUpdates = true;

                    List<EcuInfo> ecuList = ecuManager.getEcuList();
                    loadEcuData();  // Refresh display

                    // Save scan results to cache
                    String vin = VehicleManager.getInstance().getCurrentVIN();
                    if (vin != null && !vin.isEmpty() && !ecuList.isEmpty()) {
                        cacheManager.saveScanResults(vin, ecuList);
                        log.info("Saved " + ecuList.size() + " ECUs to cache for VIN");
                    }

                    if (ecuList.isEmpty()) {
                        showSnackbar("No ECUs found. Vehicle may not support Mode 9.",
                            Snackbar.LENGTH_LONG);
                    } else {
                        showSnackbar("Found " + ecuList.size() + " ECU(s) with detailed info",
                            Snackbar.LENGTH_SHORT);

                        // Auto-save scan to history
                        saveScanToHistory(ecuList);
                    }
                });

            } catch (java.util.concurrent.TimeoutException e) {
                log.warning("ECU discovery timed out after 15 seconds");
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    scanButton.setEnabled(true);
                    showSnackbar("Discovery timed out - try again or check connection",
                        Snackbar.LENGTH_LONG);
                });
            } catch (Exception e) {
                log.warning("Error during ECU discovery: " + e.getMessage());
                e.printStackTrace();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    scanButton.setEnabled(true);
                    showSnackbar("Error scanning for ECUs: " + e.getMessage(),
                        Snackbar.LENGTH_LONG);
                });
            }
        }).start();
    }

    /**
     * Load cached ECU scan results for the current VIN
     */
    private void loadCachedData() {
        String vin = VehicleManager.getInstance().getCurrentVIN();
        if (vin == null || vin.isEmpty()) {
            log.info("No VIN available - skipping cache load");
            return;
        }

        List<EcuInfo> cachedEcus = cacheManager.loadScanResults(vin);
        if (cachedEcus != null && !cachedEcus.isEmpty()) {
            log.info("Loading " + cachedEcus.size() + " cached ECUs for VIN");

            // Populate adapter with cached data directly
            adapter.setEcuList(cachedEcus);
            updateEmptyView();

            // Enable auto-updates since we have valid data
            allowAutoUpdates = true;

            showSnackbar("Loaded " + cachedEcus.size() + " ECU(s) from cache",
                Snackbar.LENGTH_SHORT);
        }
    }

    /**
     * Export ECU data to CSV file
     */
    private void exportToCSV() {
        // Get ECU data from adapter (which shows cached or scanned data)
        List<EcuInfo> ecuList = adapter.getEcuList();
        if (ecuList.isEmpty()) {
            showSnackbar("No ECU data to export", Snackbar.LENGTH_SHORT);
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
            String filename = vinSuffix + "-ecu_modules-" + timestamp + ".csv";

            // Use MediaStore for Android 10+ to save to Documents/OBD-Droid
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use MediaStore API
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/OBD-Droid");

                Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                if (uri != null) {
                    OutputStream outputStream = getContentResolver().openOutputStream(uri);
                    OutputStreamWriter writer = new OutputStreamWriter(outputStream);

                    // Write header
                    writer.append("ECU Address,Name,Type,Calibration ID,CVN\n");

                    // Write data
                    for (EcuInfo ecu : ecuList) {
                        writer.append(String.format("0x%X", ecu.getAddress())).append(",");
                        writer.append(csvEscape(ecu.getName())).append(",");
                        writer.append(csvEscape(ecu.getEcuType())).append(",");
                        writer.append(csvEscape(ecu.getCalibrationId())).append(",");
                        writer.append(csvEscape(ecu.getCalibrationVerification())).append("\n");
                    }

                    writer.flush();
                    writer.close();
                    outputStream.close();

                    log.info("Exported " + ecuList.size() + " ECUs via MediaStore to: Documents/OBD-Droid/" + filename);
                    showSnackbar("Exported " + ecuList.size() + " ECU(s) to:\nDocuments/OBD-Droid/" + filename,
                        Snackbar.LENGTH_LONG);
                } else {
                    throw new IOException("Failed to create file in Documents");
                }
            } else {
                // Android 9 and below - Use legacy file system
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File obdroidDir = new File(documentsDir, "OBD-Droid");
                if (!obdroidDir.exists()) {
                    obdroidDir.mkdirs();
                }

                File csvFile = new File(obdroidDir, filename);
                FileWriter writer = new FileWriter(csvFile);

                // Write header
                writer.append("ECU Address,Name,Type,Calibration ID,CVN\n");

                // Write data
                for (EcuInfo ecu : ecuList) {
                    writer.append(String.format("0x%X", ecu.getAddress())).append(",");
                    writer.append(csvEscape(ecu.getName())).append(",");
                    writer.append(csvEscape(ecu.getEcuType())).append(",");
                    writer.append(csvEscape(ecu.getCalibrationId())).append(",");
                    writer.append(csvEscape(ecu.getCalibrationVerification())).append("\n");
                }

                writer.flush();
                writer.close();

                log.info("Exported " + ecuList.size() + " ECUs to: " + csvFile.getAbsolutePath());
                showSnackbar("Exported " + ecuList.size() + " ECU(s) to:\nDocuments/OBD-Droid/" + csvFile.getName(),
                    Snackbar.LENGTH_LONG);
            }

        } catch (IOException e) {
            log.severe("Error exporting CSV: " + e.getMessage());
            showSnackbar("Export failed: " + e.getMessage(),
                Snackbar.LENGTH_LONG);
        }
    }

    /**
     * Import ECU data from CSV file
     */
    private void importFromCSV() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_CODE_IMPORT_CSV);
    }

    /**
     * Import ECU data from JSON file
     */
    private void importFromJSON() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        String[] mimeTypes = {"application/json", "text/plain"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, REQUEST_CODE_IMPORT_JSON);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_IMPORT_CSV && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                importCSVFile(data.getData());
            }
        } else if (requestCode == REQUEST_CODE_IMPORT_JSON && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                importJSONFile(data.getData());
            }
        } else if (requestCode == REQUEST_CODE_IMPORT_CSV_FOR_COMPARISON && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                importCSVFileForComparison(data.getData());
            }
        } else if (requestCode == REQUEST_CODE_IMPORT_JSON_FOR_COMPARISON && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                importJSONFileForComparison(data.getData());
            }
        } else if (requestCode == REQUEST_CODE_SELECT_BASELINE_SCAN && resultCode == RESULT_OK) {
            if (data != null) {
                // Check if user requested CSV import
                boolean importCsvRequested = data.getBooleanExtra(
                    BaselineScanSelectionActivity.EXTRA_IMPORT_CSV_REQUESTED, false);

                if (importCsvRequested) {
                    // User tapped Import CSV button
                    importCsvForComparison();
                } else {
                    // User selected a baseline scan
                    EcuScan selectedScan = (EcuScan) data.getSerializableExtra(
                        BaselineScanSelectionActivity.EXTRA_SELECTED_SCAN);

                    if (selectedScan != null) {
                        compareAgainstCurrentData(selectedScan);
                    }
                }
            }
        }
    }

    /**
     * Import CSV file and populate ECU list
     */
    private void importCSVFile(Uri uri) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(getContentResolver().openInputStream(uri)));

            List<EcuInfo> importedEcus = new java.util.ArrayList<>();
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;  // Skip header
                    continue;
                }

                String[] parts = csvParse(line);
                if (parts.length >= 5) {
                    try {
                        // Parse: ECU Address, Name, Type (ignored - auto-derived), Calibration ID, CVN
                        String addressStr = parts[0].trim();
                        int address = Integer.decode(addressStr);  // Handles 0x prefix
                        String name = parts[1].trim();
                        // Skip parts[2] - type is auto-derived from name/address
                        String calId = parts[3].trim();
                        String cvn = parts[4].trim();

                        EcuInfo ecu = new EcuInfo(address);
                        if (!name.isEmpty()) ecu.setName(name);
                        if (!calId.isEmpty()) ecu.setCalibrationId(calId);
                        if (!cvn.isEmpty()) ecu.setCalibrationVerification(cvn);

                        importedEcus.add(ecu);
                    } catch (NumberFormatException e) {
                        log.warning("Skipping invalid line: " + line);
                    }
                }
            }

            reader.close();

            // Update adapter with imported data
            adapter.setEcuList(importedEcus);
            updateEmptyView();

            // Mark data as imported
            isDataFromImport = true;
            importedFileName = extractTimestampFromFilename(getFileNameFromUri(uri)); // Extract timestamp from filename

            showSnackbar("Imported " + importedEcus.size() + " ECU(s)",
                Snackbar.LENGTH_LONG);

            log.info("Successfully imported " + importedEcus.size() + " ECUs from: " + importedFileName);

        } catch (IOException e) {
            log.severe("Error importing CSV: " + e.getMessage());
            showSnackbar("Import failed: " + e.getMessage(),
                Snackbar.LENGTH_LONG);
        }
    }

    /**
     * Import CSV file for comparison (instead of displaying)
     */
    private void importCsvForComparison() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_CODE_IMPORT_CSV_FOR_COMPARISON);
    }

    /**
     * Import CSV file and use it as baseline for comparison
     */
    private void importCSVFileForComparison(Uri uri) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(getContentResolver().openInputStream(uri)));

            List<EcuInfo> importedEcus = new java.util.ArrayList<>();
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;  // Skip header
                    continue;
                }

                String[] parts = csvParse(line);
                if (parts.length >= 5) {
                    try {
                        // Parse: ECU Address, Name, Type (ignored - auto-derived), Calibration ID, CVN
                        String addressStr = parts[0].trim();
                        int address = Integer.decode(addressStr);  // Handles 0x prefix
                        String name = parts[1].trim();
                        // Skip parts[2] - type is auto-derived from name/address
                        String calId = parts[3].trim();
                        String cvn = parts[4].trim();

                        EcuInfo ecu = new EcuInfo(address);
                        if (!name.isEmpty()) ecu.setName(name);
                        if (!calId.isEmpty()) ecu.setCalibrationId(calId);
                        if (!cvn.isEmpty()) ecu.setCalibrationVerification(cvn);

                        importedEcus.add(ecu);
                    } catch (NumberFormatException e) {
                        log.warning("Skipping invalid line: " + line);
                    }
                }
            }

            reader.close();

            if (importedEcus.isEmpty()) {
                showSnackbar("No ECU data found in CSV", Snackbar.LENGTH_SHORT);
                return;
            }

            // Create an EcuScan from imported data (use current time as timestamp)
            String vin = VehicleManager.getInstance(this).getCurrentVIN();
            EcuScan importedScan = new EcuScan(vin, importedEcus);

            // Compare against current data
            compareAgainstCurrentData(importedScan);

            log.info("Imported " + importedEcus.size() + " ECUs from CSV for comparison");

        } catch (IOException e) {
            log.severe("Error importing CSV: " + e.getMessage());
            showSnackbar("Import failed: " + e.getMessage(),
                Snackbar.LENGTH_LONG);
        }
    }

    /**
     * Import JSON file and populate ECU list
     */
    private void importJSONFile(Uri uri) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(getContentResolver().openInputStream(uri)));

            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            reader.close();

            JSONObject root = new JSONObject(jsonBuilder.toString());
            JSONArray ecusArray = root.getJSONArray("ecus");

            List<EcuInfo> importedEcus = new java.util.ArrayList<>();
            for (int i = 0; i < ecusArray.length(); i++) {
                JSONObject ecuJson = ecusArray.getJSONObject(i);

                // Parse address - try hex first, then decimal
                int address;
                if (ecuJson.has("address")) {
                    String addressStr = ecuJson.getString("address");
                    address = Integer.decode(addressStr);  // Handles 0x prefix
                } else {
                    address = ecuJson.getInt("address_decimal");
                }

                String name = ecuJson.optString("name", "");
                String calId = ecuJson.optString("calibration_id", "");
                String cvn = ecuJson.optString("cvn", "");

                EcuInfo ecu = new EcuInfo(address);
                if (!name.isEmpty()) ecu.setName(name);
                if (!calId.isEmpty()) ecu.setCalibrationId(calId);
                if (!cvn.isEmpty()) ecu.setCalibrationVerification(cvn);

                importedEcus.add(ecu);
            }

            // Update adapter with imported data
            adapter.setEcuList(importedEcus);
            updateEmptyView();

            // Mark data as imported
            isDataFromImport = true;
            importedFileName = extractTimestampFromFilename(getFileNameFromUri(uri));

            showSnackbar("Imported " + importedEcus.size() + " ECU(s) from JSON",
                Snackbar.LENGTH_LONG);

            log.info("Successfully imported " + importedEcus.size() + " ECUs from JSON: " + importedFileName);

        } catch (IOException | JSONException e) {
            log.severe("Error importing JSON: " + e.getMessage());
            showSnackbar("Import failed: " + e.getMessage(),
                Snackbar.LENGTH_LONG);
        }
    }

    /**
     * Import JSON file and use it as baseline for comparison
     */
    private void importJSONFileForComparison(Uri uri) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(getContentResolver().openInputStream(uri)));

            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            reader.close();

            JSONObject root = new JSONObject(jsonBuilder.toString());
            JSONArray ecusArray = root.getJSONArray("ecus");

            List<EcuInfo> importedEcus = new java.util.ArrayList<>();
            for (int i = 0; i < ecusArray.length(); i++) {
                JSONObject ecuJson = ecusArray.getJSONObject(i);

                // Parse address - try hex first, then decimal
                int address;
                if (ecuJson.has("address")) {
                    String addressStr = ecuJson.getString("address");
                    address = Integer.decode(addressStr);  // Handles 0x prefix
                } else {
                    address = ecuJson.getInt("address_decimal");
                }

                String name = ecuJson.optString("name", "");
                String calId = ecuJson.optString("calibration_id", "");
                String cvn = ecuJson.optString("cvn", "");

                EcuInfo ecu = new EcuInfo(address);
                if (!name.isEmpty()) ecu.setName(name);
                if (!calId.isEmpty()) ecu.setCalibrationId(calId);
                if (!cvn.isEmpty()) ecu.setCalibrationVerification(cvn);

                importedEcus.add(ecu);
            }

            if (importedEcus.isEmpty()) {
                showSnackbar("No ECU data found in JSON", Snackbar.LENGTH_SHORT);
                return;
            }

            // Create an EcuScan from imported data (use current time as timestamp)
            String vin = VehicleManager.getInstance(this).getCurrentVIN();
            EcuScan importedScan = new EcuScan(vin, importedEcus);

            // Compare against current data
            compareAgainstCurrentData(importedScan);

            log.info("Imported " + importedEcus.size() + " ECUs from JSON for comparison");

        } catch (IOException | JSONException e) {
            log.severe("Error importing JSON: " + e.getMessage());
            showSnackbar("Import failed: " + e.getMessage(),
                Snackbar.LENGTH_LONG);
        }
    }

    /**
     * Compare a baseline scan against current live ECU data
     */
    private void compareAgainstCurrentData(EcuScan baseline) {
        List<EcuInfo> currentEcus = adapter.getEcuList();
        if (currentEcus.isEmpty()) {
            showSnackbar("No current ECU data to compare against", Snackbar.LENGTH_SHORT);
            return;
        }

        // Launch comparison activity
        Intent intent = new Intent(this, EcuComparisonActivity.class);
        intent.putExtra(EcuComparisonActivity.EXTRA_BASELINE_SCAN, baseline);
        intent.putExtra(EcuComparisonActivity.EXTRA_CURRENT_ECUS, new java.util.ArrayList<>(currentEcus));
        intent.putExtra(EcuComparisonActivity.EXTRA_IS_FROM_IMPORT, isDataFromImport);
        intent.putExtra(EcuComparisonActivity.EXTRA_IMPORT_FILENAME, importedFileName);
        startActivity(intent);
    }

    /**
     * Parse CSV line handling quoted values
     */
    private String[] csvParse(String line) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());

        return result.toArray(new String[0]);
    }

    /**
     * Escape CSV values (handle commas and quotes)
     */
    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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

    /**
     * Update empty view visibility based on ECU count
     */
    private void updateEmptyView() {
        if (adapter.getItemCount() == 0) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onEcuDiscovered(EcuInfo ecu) {
        // Ignore automatic ECU discoveries until user explicitly scans
        if (!allowAutoUpdates) {
            log.fine("Ignoring auto-discovered ECU (waiting for manual scan): " + ecu);
            return;
        }

        runOnUiThread(() -> {
            log.info("ECU discovered: " + ecu);
            adapter.addEcu(ecu);
            updateEmptyView();
        });
    }

    @Override
    public void onEcuUpdated(EcuInfo ecu) {
        // Ignore automatic ECU updates until user explicitly scans
        if (!allowAutoUpdates) {
            log.fine("Ignoring auto-updated ECU (waiting for manual scan): " + ecu);
            return;
        }

        runOnUiThread(() -> {
            log.info("ECU updated: " + ecu);
            adapter.updateEcu(ecu);
        });
    }

    /**
     * Save scan to history for later comparison
     */
    private void saveScanToHistory(List<EcuInfo> ecuList) {
        try {
            String vin = VehicleManager.getInstance(this).getCurrentVIN();
            EcuScan scan = new EcuScan(vin, ecuList);
            boolean saved = scanHistoryManager.saveScan(scan);
            if (saved) {
                log.info("Saved ECU scan to history: " + ecuList.size() + " ECUs");
            } else {
                log.warning("Failed to save ECU scan to history");
            }
        } catch (Exception e) {
            log.severe("Error saving scan to history: " + e.getMessage());
        }
    }

    /**
     * Open scan comparison activity
     */
    private void openCompareScans() {
        // Check if we have current ECU data
        List<EcuInfo> currentEcus = adapter.getEcuList();
        if (currentEcus.isEmpty()) {
            showSnackbar("No current ECU data. Perform a scan first.", Snackbar.LENGTH_LONG);
            return;
        }

        // Get scan history for current VIN
        String vin = VehicleManager.getInstance(this).getCurrentVIN();
        if (vin == null || vin.isEmpty()) {
            showSnackbar("No VIN available", Snackbar.LENGTH_SHORT);
            return;
        }

        // Launch baseline scan selection activity
        Intent intent = new Intent(this, BaselineScanSelectionActivity.class);
        startActivityForResult(intent, REQUEST_CODE_SELECT_BASELINE_SCAN);
    }


    /**
     * Show comparison results in a dialog
     */
    private void showComparisonResult(EcuScan baseline, List<EcuInfo> currentEcus) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);

        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_ecu_comparison, null);

        // Set baseline and current dates
        TextView baselineDate = dialogView.findViewById(R.id.baseline_date);
        TextView currentDate = dialogView.findViewById(R.id.current_date);
        baselineDate.setText(sdf.format(baseline.getDate()));
        currentDate.setText("Live Data");

        // Get comparison list container
        LinearLayout comparisonList = dialogView.findViewById(R.id.comparison_list);

        // Compare ECUs
        int changedCount = 0;
        int unchangedCount = 0;
        List<EcuInfo> baselineEcus = baseline.getEcus();

        // Add current ECUs to comparison
        for (EcuInfo currentEcu : currentEcus) {
            EcuInfo baselineEcu = findEcuByAddress(baselineEcus, currentEcu.getAddress());

            if (baselineEcu == null) {
                // NEW ECU
                addComparisonItem(comparisonList, currentEcu, null, null,
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
                    addComparisonItem(comparisonList, currentEcu,
                        calIdChanged ? new String[]{baseCalId, currCalId} : null,
                        cvnChanged ? new String[]{baseCvn, currCvn} : null,
                        "CHANGED", "#F57C00", true);
                } else {
                    unchangedCount++;
                    addComparisonItem(comparisonList, currentEcu, null, null,
                        "No changes", "#4CAF50", false);
                }
            }
        }

        // Check for removed ECUs
        for (EcuInfo baselineEcu : baselineEcus) {
            if (findEcuByAddress(currentEcus, baselineEcu.getAddress()) == null) {
                addComparisonItem(comparisonList, baselineEcu, null, null,
                    "REMOVED", "#D32F2F", false);
                changedCount++;
            }
        }

        // Update summary counts
        TextView changedCountView = dialogView.findViewById(R.id.changed_count);
        TextView unchangedCountView = dialogView.findViewById(R.id.unchanged_count);
        changedCountView.setText(String.valueOf(changedCount));
        unchangedCountView.setText(String.valueOf(unchangedCount));

        // Create dialog
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create();

        // Set up buttons
        Button exportButton = dialogView.findViewById(R.id.btn_export);
        Button closeButton = dialogView.findViewById(R.id.btn_close);

        exportButton.setOnClickListener(v -> {
            // TODO: Export comparison report
            showSnackbar("Export comparison - Coming soon", Snackbar.LENGTH_SHORT);
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Add a comparison item to the list
     */
    private void addComparisonItem(LinearLayout container, EcuInfo ecu,
                                   String[] calIdChange, String[] cvnChange,
                                   String statusText, String statusColor, boolean showChanges) {
        View itemView = getLayoutInflater().inflate(R.layout.item_ecu_comparison, container, false);

        // Set ECU info
        TextView ecuName = itemView.findViewById(R.id.ecu_name);
        TextView ecuAddress = itemView.findViewById(R.id.ecu_address);
        TextView status = itemView.findViewById(R.id.status_text);
        ImageView statusIcon = itemView.findViewById(R.id.status_icon);

        ecuName.setText(ecu.getName());
        ecuAddress.setText(String.format("0x%X", ecu.getAddress()));
        status.setText(statusText);

        // Set status color
        int color = android.graphics.Color.parseColor(statusColor);
        status.setTextColor(color);
        statusIcon.setColorFilter(color);

        // Set status icon based on type
        if (statusText.contains("CHANGED")) {
            statusIcon.setImageResource(android.R.drawable.ic_menu_edit);
        } else if (statusText.contains("NEW")) {
            statusIcon.setImageResource(android.R.drawable.ic_input_add);
        } else if (statusText.contains("REMOVED")) {
            statusIcon.setImageResource(android.R.drawable.ic_menu_delete);
        } else {
            // No changes - use checkmark
            statusIcon.setImageResource(android.R.drawable.checkbox_on_background);
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

        container.addView(itemView);
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

    /**
     * Get human-readable filename from URI
     */
    private String getFileNameFromUri(android.net.Uri uri) {
        String fileName = null;

        // Try to get display name from ContentResolver
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            log.warning("Could not get filename from URI: " + e.getMessage());
        }

        // Fallback to last path segment if ContentResolver fails
        if (fileName == null || fileName.isEmpty()) {
            fileName = uri.getLastPathSegment();
        }

        return fileName;
    }

    /**
     * Extract and format timestamp from imported CSV filename
     * Expects format: {VIN}-ecu_modules-{yyyyMMdd_HHmmss}.csv
     * Returns formatted date string like "Oct 18, 2025 21:34"
     */
    private String extractTimestampFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "Unknown";
        }

        try {
            // Extract timestamp portion from filename (e.g., "20251018_213443")
            // Pattern: {something}-{yyyyMMdd_HHmmss}.csv
            String[] parts = filename.split("-");
            if (parts.length >= 3) {
                String lastPart = parts[parts.length - 1]; // "20251018_213443.csv"
                String timestampStr = lastPart.replace(".csv", ""); // "20251018_213443"

                // Parse the timestamp
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);

                java.util.Date date = inputFormat.parse(timestampStr);
                if (date != null) {
                    return outputFormat.format(date);
                }
            }
        } catch (Exception e) {
            log.warning("Could not extract timestamp from filename: " + filename + " - " + e.getMessage());
        }

        // Fallback to filename if parsing fails
        return filename;
    }
}
