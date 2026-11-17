package com.obddroid.custompid;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import com.obddroid.utils.DebugLog;

/**
 * Manager class for custom PID operations
 * Handles CRUD, validation, and pre-loaded manufacturer PIDs
 *
 * @author Wal33D
 */
public class CustomPidManager {
    private static final String TAG = "CustomPidManager";

    private final CustomPidDatabase database;
    private static CustomPidManager instance;

    public static synchronized CustomPidManager getInstance(Context context) {
        if (instance == null) {
            instance = new CustomPidManager(context.getApplicationContext());
        }
        return instance;
    }

    private CustomPidManager(Context context) {
        this.database = CustomPidDatabase.getInstance(context);

        // Load sample PIDs if database is empty
        if (database.getPidCount() == 0) {
            Log.i(TAG, "Loading sample manufacturer-specific PIDs");
            loadSamplePids();
        }
    }

    /**
     * Add a new custom PID
     */
    public long addPid(CustomPid pid) throws PidFormulaParser.FormulaException {
        // Validate formula
        if (!PidFormulaParser.isValidFormula(pid.getFormula())) {
            throw new PidFormulaParser.FormulaException("Invalid formula: " + pid.getFormula());
        }

        // Validate PID hex
        if (!isValidHex(pid.getPidHex())) {
            throw new PidFormulaParser.FormulaException("Invalid PID hex: " + pid.getPidHex());
        }

        return database.insertPid(pid);
    }

    /**
     * Update an existing PID
     */
    public boolean updatePid(CustomPid pid) throws PidFormulaParser.FormulaException {
        // Validate formula
        if (!PidFormulaParser.isValidFormula(pid.getFormula())) {
            throw new PidFormulaParser.FormulaException("Invalid formula: " + pid.getFormula());
        }

        return database.updatePid(pid) > 0;
    }

    /**
     * Delete a PID
     */
    public boolean deletePid(long id) {
        return database.deletePid(id) > 0;
    }

    /**
     * Get a PID by ID
     */
    public CustomPid getPid(long id) {
        return database.getPid(id);
    }

    /**
     * Get all PIDs
     */
    public List<CustomPid> getAllPids() {
        return database.getAllPids();
    }

    /**
     * Get enabled PIDs only
     */
    public List<CustomPid> getEnabledPids() {
        return database.getEnabledPids();
    }

    /**
     * Get PIDs for specific vehicle
     */
    public List<CustomPid> getPidsForVehicle(String make, String model, int year) {
        return database.getPidsForVehicle(make, model, year);
    }

    /**
     * Search PIDs
     */
    public List<CustomPid> searchPids(String query) {
        return database.searchPids(query);
    }

    /**
     * Evaluate a PID with given response bytes
     */
    public double evaluatePid(CustomPid pid, int[] bytes) throws PidFormulaParser.FormulaException {
        return PidFormulaParser.evaluate(pid.getFormula(), bytes);
    }

    /**
     * Test a PID formula with sample data
     */
    public String testFormula(String formula, int[] testBytes) {
        try {
            double result = PidFormulaParser.evaluate(formula, testBytes);
            return String.format("Result: %.2f", result);
        } catch (PidFormulaParser.FormulaException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Validate PID hex string
     */
    private boolean isValidHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(hex, 16);
            return hex.length() >= 2 && hex.length() <= 8;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Load sample manufacturer-specific PIDs
     * These are common custom PIDs for popular vehicles
     */
    private void loadSamplePids() {
        List<CustomPid> samplePids = new ArrayList<>();

        // Subaru PIDs
        samplePids.add(createPid("Boost Pressure", "221634", "(A*256+B)/10", "kPa",
                "Subaru", "WRX, STI", "2008-2021",
                "Turbo boost pressure for Subaru WRX/STI"));

        samplePids.add(createPid("Feedback Knock", "221418", "A-128", "degrees",
                "Subaru", null, "2008-2021",
                "Feedback knock correction"));

        samplePids.add(createPid("Fine Learning Knock", "221419", "A-128", "degrees",
                "Subaru", null, "2008-2021",
                "Fine learning knock correction"));

        // BMW PIDs
        samplePids.add(createPid("Oil Temperature", "2101", "A-48", "°C",
                "BMW", null, "2000-2020",
                "Engine oil temperature"));

        samplePids.add(createPid("Boost Pressure", "2105", "A*0.04", "PSI",
                "BMW", null, "2000-2020",
                "Turbo boost pressure"));

        // Ford PIDs
        samplePids.add(createPid("Transmission Temp", "221210", "A*0.555-40", "°C",
                "Ford", "F-150, Mustang", "2015-2023",
                "Automatic transmission fluid temperature"));

        samplePids.add(createPid("Boost Desired", "2212", "A", "kPa",
                "Ford", "EcoBoost", "2015-2023",
                "Desired boost pressure"));

        // GM/Chevrolet PIDs
        samplePids.add(createPid("Transmission Temp", "221142", "(A*256+B)*0.1-40", "°C",
                "Chevrolet", "Corvette, Camaro", "2010-2023",
                "Transmission fluid temperature"));

        samplePids.add(createPid("Oil Life", "221E1F", "A", "%",
                "Chevrolet", null, "2010-2023",
                "Remaining oil life percentage"));

        // Honda/Acura PIDs
        samplePids.add(createPid("Battery Current", "221630", "((A*256+B)-32768)/10", "A",
                "Honda", "Accord, Civic", "2016-2023",
                "12V battery current"));

        samplePids.add(createPid("CVT Temp", "221901", "A-40", "°C",
                "Honda", "Accord, CR-V", "2014-2023",
                "CVT transmission temperature"));

        // Toyota/Lexus PIDs
        samplePids.add(createPid("Hybrid Battery SOC", "223C", "(A*100)/255", "%",
                "Toyota", "Prius, RAV4 Hybrid", "2010-2023",
                "Hybrid battery state of charge"));

        samplePids.add(createPid("Hybrid Battery Temp", "2205", "A-40", "°C",
                "Toyota", "Prius, Camry Hybrid", "2010-2023",
                "Hybrid battery temperature"));

        // Volkswagen/Audi PIDs
        samplePids.add(createPid("Boost Actual", "221104", "((A*256+B)/100)-100", "kPa",
                "Volkswagen", "GTI, Golf R", "2015-2023",
                "Actual turbo boost pressure"));

        samplePids.add(createPid("Oil Pressure", "221105", "A*10", "kPa",
                "Audi", null, "2010-2023",
                "Engine oil pressure"));

        // Mazda PIDs
        samplePids.add(createPid("Boost Pressure", "221628", "(A*256+B-101.3)*0.145", "PSI",
                "Mazda", "CX-5, CX-9", "2016-2023",
                "Turbo boost pressure for SkyActiv-G turbo"));

        // Nissan/Infiniti PIDs
        samplePids.add(createPid("Boost Pressure", "221630", "A*0.01", "bar",
                "Nissan", "370Z, GTR", "2009-2023",
                "Turbo boost pressure"));

        // Insert all sample PIDs
        for (CustomPid pid : samplePids) {
            try {
                database.insertPid(pid);
                DebugLog.d(TAG, "Loaded sample PID: " + pid.getName());
            } catch (Exception e) {
                Log.e(TAG, "Failed to load sample PID: " + pid.getName(), e);
            }
        }

        Log.i(TAG, "Loaded " + samplePids.size() + " sample manufacturer PIDs");
    }

    /**
     * Helper to create a PID
     */
    private CustomPid createPid(String name, String pidHex, String formula, String units,
                                String make, String model, String years, String description) {
        CustomPid pid = new CustomPid(name, pidHex, formula, units);
        pid.setVehicleMake(make);
        pid.setVehicleModel(model);
        pid.setVehicleYears(years);
        pid.setDescription(description);
        return pid;
    }

    /**
     * Export all PIDs to CSV format
     */
    public String exportToCSV() {
        StringBuilder csv = new StringBuilder();
        csv.append("Name,PID Hex,Formula,Units,Make,Model,Years,Description\n");

        for (CustomPid pid : getAllPids()) {
            csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    escapeCSV(pid.getName()),
                    pid.getPidHex(),
                    pid.getFormula(),
                    pid.getUnits(),
                    escapeCSV(pid.getVehicleMake()),
                    escapeCSV(pid.getVehicleModel()),
                    escapeCSV(pid.getVehicleYears()),
                    escapeCSV(pid.getDescription())
            ));
        }

        return csv.toString();
    }

    /**
     * Import PIDs from CSV format
     */
    public int importFromCSV(String csv) {
        int imported = 0;
        String[] lines = csv.split("\n");

        for (int i = 1; i < lines.length; i++) { // Skip header
            try {
                String[] parts = parseCSVLine(lines[i]);
                if (parts.length >= 8) {
                    CustomPid pid = new CustomPid(parts[0], parts[1], parts[2], parts[3]);
                    pid.setVehicleMake(parts[4]);
                    pid.setVehicleModel(parts[5]);
                    pid.setVehicleYears(parts[6]);
                    pid.setDescription(parts[7]);

                    addPid(pid);
                    imported++;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to import line: " + lines[i], e);
            }
        }

        Log.i(TAG, "Imported " + imported + " PIDs from CSV");
        return imported;
    }

    /**
     * Simple CSV parser
     */
    private String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(field.toString());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        result.add(field.toString());

        return result.toArray(new String[0]);
    }

    /**
     * Escape CSV values
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\"\"");
    }
}
