package com.obddroid.ui.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.SQLException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.obddroid.R;
import com.obddroid.custompid.CustomPid;
import com.obddroid.custompid.CustomPidManager;
import com.obddroid.custompid.CustomPidIntegration;
import com.obddroid.custompid.PidFormulaParser;
import com.obddroid.utils.HelpDialogUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for managing custom PIDs
 * Users can view, add, edit, and delete custom PIDs
 *
 * @author Wal33D
 */
public class CustomPidManagerActivity extends AppCompatActivity {
    private static final String TAG = "CustomPidManagerActivity";

    private CustomPidManager pidManager;
    private ListView listView;
    private TextView emptyView;
    private Button btnAddPid;
    private CustomPidAdapter adapter;
    private List<CustomPid> pidList;

    // File picker launchers
    private ActivityResultLauncher<String> importCSVLauncher;
    private ActivityResultLauncher<String> exportCSVLauncher;
    private ActivityResultLauncher<String> importJSONLauncher;
    private ActivityResultLauncher<String> exportJSONLauncher;

    /**
     * Notify other components that custom PIDs have changed
     * This allows LiveDataActivity to refresh without app restart
     */
    private void notifyCustomPidsChanged() {
        Intent intent = new Intent(CustomPidIntegration.ACTION_CUSTOM_PIDS_CHANGED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_pid_manager);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Custom PIDs");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize manager
        pidManager = CustomPidManager.getInstance(this);

        // Initialize file picker launchers
        initializeFilePickers();

        // Find views
        listView = findViewById(R.id.pid_list);
        emptyView = findViewById(R.id.empty_view);
        btnAddPid = findViewById(R.id.btn_add_pid);

        // Setup adapter
        pidList = new ArrayList<>();
        adapter = new CustomPidAdapter(this, pidList);
        listView.setAdapter(adapter);

        // Setup click listeners
        btnAddPid.setOnClickListener(v -> showAddEditDialog(null));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            CustomPid pid = pidList.get(position);
            showPidOptionsDialog(pid);
        });

        // Load PIDs
        refreshPidList();
    }

    private void refreshPidList() {
        pidList.clear();
        pidList.addAll(pidManager.getAllPids());
        adapter.notifyDataSetChanged();

        // Show/hide empty view
        if (pidList.isEmpty()) {
            listView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private void showAddEditDialog(final CustomPid existingPid) {
        boolean isEdit = existingPid != null;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isEdit ? "Edit Custom PID" : "Add Custom PID");

        // Create dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_custom_pid, null);

        EditText etName = dialogView.findViewById(R.id.et_pid_name);
        EditText etPidHex = dialogView.findViewById(R.id.et_pid_hex);
        EditText etFormula = dialogView.findViewById(R.id.et_formula);
        EditText etUnits = dialogView.findViewById(R.id.et_units);
        EditText etMake = dialogView.findViewById(R.id.et_make);
        EditText etModel = dialogView.findViewById(R.id.et_model);
        EditText etYears = dialogView.findViewById(R.id.et_years);
        EditText etDescription = dialogView.findViewById(R.id.et_description);

        // Pre-fill if editing
        if (isEdit) {
            etName.setText(existingPid.getName());
            etPidHex.setText(existingPid.getPidHex());
            etFormula.setText(existingPid.getFormula());
            etUnits.setText(existingPid.getUnits());
            etMake.setText(existingPid.getVehicleMake());
            etModel.setText(existingPid.getVehicleModel());
            etYears.setText(existingPid.getVehicleYears());
            etDescription.setText(existingPid.getDescription());
        }

        builder.setView(dialogView);

        builder.setPositiveButton(isEdit ? "Update" : "Add", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String pidHex = etPidHex.getText().toString().trim();
            String formula = etFormula.getText().toString().trim();
            String units = etUnits.getText().toString().trim();
            String make = etMake.getText().toString().trim();
            String model = etModel.getText().toString().trim();
            String years = etYears.getText().toString().trim();
            String description = etDescription.getText().toString().trim();

            if (name.isEmpty() || pidHex.isEmpty() || formula.isEmpty()) {
                Toast.makeText(this, "Name, PID, and Formula are required", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                CustomPid pid = isEdit ? existingPid : new CustomPid();
                pid.setName(name);
                pid.setPidHex(pidHex);
                pid.setFormula(formula);
                pid.setUnits(units);
                pid.setVehicleMake(make.isEmpty() ? null : make);
                pid.setVehicleModel(model.isEmpty() ? null : model);
                pid.setVehicleYears(years.isEmpty() ? null : years);
                pid.setDescription(description.isEmpty() ? null : description);

                if (isEdit) {
                    pidManager.updatePid(pid);
                    Toast.makeText(this, "PID updated", Toast.LENGTH_SHORT).show();
                } else {
                    pidManager.addPid(pid);
                    Toast.makeText(this, "PID added", Toast.LENGTH_SHORT).show();
                }

                refreshPidList();
                notifyCustomPidsChanged();  // Notify live data to refresh
            } catch (PidFormulaParser.FormulaException | SQLException e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showPidOptionsDialog(final CustomPid pid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(pid.getName());

        String status = pid.isEnabled() ? "✓ ENABLED" : "✗ Disabled";
        String message = String.format(
            "Status: %s\n\nPID: %s\nFormula: %s\nUnits: %s\n\n%s",
            status,
            pid.getPidHex(),
            pid.getFormula(),
            pid.getUnits(),
            pid.getDescription() != null ? pid.getDescription() : ""
        );

        builder.setMessage(message);

        // Toggle enable/disable
        String toggleText = pid.isEnabled() ? "Disable" : "Enable";
        builder.setPositiveButton(toggleText, (dialog, which) -> {
            try {
                pid.setEnabled(!pid.isEnabled());
                pidManager.updatePid(pid);
                Toast.makeText(this, pid.isEnabled() ? "PID enabled" : "PID disabled", Toast.LENGTH_SHORT).show();
                refreshPidList();
                notifyCustomPidsChanged();  // Notify live data to refresh
            } catch (PidFormulaParser.FormulaException | SQLException e) {
                Toast.makeText(this, "Error updating PID: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        builder.setNeutralButton("Edit", (dialog, which) -> showAddEditDialog(pid));

        builder.setNegativeButton("Delete", (dialog, which) -> {
            new AlertDialog.Builder(this)
                .setTitle("Delete PID?")
                .setMessage("Are you sure you want to delete \"" + pid.getName() + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    pidManager.deletePid(pid.getId());
                    Toast.makeText(this, "PID deleted", Toast.LENGTH_SHORT).show();
                    refreshPidList();
                    notifyCustomPidsChanged();  // Notify live data to refresh
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        builder.setNeutralButton("Close", null);
        builder.show();
    }


    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_custom_pid_manager, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_export) {
            showExportDialog();
            return true;
        } else if (id == R.id.action_import) {
            showImportDialog();
            return true;
        } else if (id == R.id.action_info) {
            showInfoDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initializeFilePickers() {
        // CSV Import
        importCSVLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    importFromCSVFile(uri);
                }
            }
        );

        // CSV Export
        exportCSVLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            uri -> {
                if (uri != null) {
                    saveCSVToFile(uri);
                }
            }
        );

        // JSON Import
        importJSONLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    importFromJSONFile(uri);
                }
            }
        );

        // JSON Export
        exportJSONLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    saveJSONToFile(uri);
                }
            }
        );
    }

    private void showExportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Export Custom PIDs");
        builder.setMessage("Choose export format:");

        builder.setPositiveButton("CSV Format", (dialog, which) -> {
            String filename = "custom_pids_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv";
            exportCSVLauncher.launch(filename);
        });

        builder.setNeutralButton("JSON Format", (dialog, which) -> {
            String filename = "custom_pids_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".json";
            exportJSONLauncher.launch(filename);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showImportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Import Custom PIDs");
        builder.setMessage("Choose file format to import:");

        builder.setPositiveButton("CSV Format", (dialog, which) -> {
            importCSVLauncher.launch("text/*");
        });

        builder.setNeutralButton("JSON Format", (dialog, which) -> {
            importJSONLauncher.launch("application/json");
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveCSVToFile(Uri uri) {
        try {
            String csv = pidManager.exportToCSV();

            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            if (outputStream != null) {
                outputStream.write(csv.getBytes());
                outputStream.close();
                Toast.makeText(this, "Exported " + pidList.size() + " PIDs to CSV", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException | SecurityException e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveJSONToFile(Uri uri) {
        try {
            // For now, use CSV format with .json extension
            // TODO: Implement proper JSON export in CustomPidManager
            String csv = pidManager.exportToCSV();

            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            if (outputStream != null) {
                outputStream.write(csv.getBytes());
                outputStream.close();
                Toast.makeText(this, "Exported " + pidList.size() + " PIDs", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException | SecurityException e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importFromCSVFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder csv = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    csv.append(line).append("\n");
                }
                reader.close();
                inputStream.close();

                int imported = pidManager.importFromCSV(csv.toString());
                Toast.makeText(this, "Imported " + imported + " PIDs from CSV", Toast.LENGTH_SHORT).show();
                refreshPidList();
                notifyCustomPidsChanged();
            }
        } catch (IOException | SecurityException e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importFromJSONFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                inputStream.close();

                // For now, treat as CSV
                // TODO: Implement proper JSON import in CustomPidManager
                int imported = pidManager.importFromCSV(content.toString());
                Toast.makeText(this, "Imported " + imported + " PIDs", Toast.LENGTH_SHORT).show();
                refreshPidList();
                notifyCustomPidsChanged();
            }
        } catch (IOException | SecurityException e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Adapter for custom PID list
     */
    private static class CustomPidAdapter extends BaseAdapter {
        private final Context context;
        private final List<CustomPid> pids;

        CustomPidAdapter(Context context, List<CustomPid> pids) {
            this.context = context;
            this.pids = pids;
        }

        @Override
        public int getCount() {
            return pids.size();
        }

        @Override
        public CustomPid getItem(int position) {
            return pids.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(
                    R.layout.item_custom_pid, parent, false);
            }

            CustomPid pid = pids.get(position);

            TextView pidName = convertView.findViewById(R.id.pid_name);
            TextView pidHex = convertView.findViewById(R.id.pid_hex);
            TextView pidFormula = convertView.findViewById(R.id.pid_formula);
            TextView pidVehicle = convertView.findViewById(R.id.pid_vehicle);
            TextView pidUnits = convertView.findViewById(R.id.pid_units);

            pidName.setText(pid.getName());
            pidHex.setText(pid.getPidHex());
            pidFormula.setText(pid.getFormula());
            pidUnits.setText(pid.getUnits() != null ? pid.getUnits() : "");

            // Vehicle info
            String vehicleInfo = "";
            if (pid.getVehicleMake() != null && !pid.getVehicleMake().isEmpty()) {
                vehicleInfo = pid.getVehicleMake();
                if (pid.getVehicleModel() != null && !pid.getVehicleModel().isEmpty()) {
                    vehicleInfo += " " + pid.getVehicleModel();
                }
                if (pid.getVehicleYears() != null && !pid.getVehicleYears().isEmpty()) {
                    vehicleInfo += " (" + pid.getVehicleYears() + ")";
                }
            } else {
                vehicleInfo = "Universal";
            }
            pidVehicle.setText(vehicleInfo);

            return convertView;
        }
    }

    private void showInfoDialog() {
        HelpDialogUtils.showHelpDialog(
            this,
            R.string.custom_pid_info_title,
            R.string.custom_pid_info_message,
            R.string.custom_pid_info_ack,
            android.R.drawable.ic_menu_info_details
        );
    }
}
