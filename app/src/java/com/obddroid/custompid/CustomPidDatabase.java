package com.obddroid.custompid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import com.obddroid.utils.DebugLog;

/**
 * SQLite database for storing custom PID definitions
 *
 * @author Wal33D
 */
public class CustomPidDatabase extends SQLiteOpenHelper {
    private static final String TAG = "CustomPidDatabase";

    private static final String DATABASE_NAME = "custom_pids.db";
    private static final int DATABASE_VERSION = 2;  // Bump to disable all pre-loaded PIDs by default

    // Table name
    private static final String TABLE_PIDS = "custom_pids";

    // Column names
    private static final String COL_ID = "id";
    private static final String COL_NAME = "name";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_PID_HEX = "pid_hex";
    private static final String COL_FORMULA = "formula";
    private static final String COL_UNITS = "units";
    private static final String COL_VEHICLE_MAKE = "vehicle_make";
    private static final String COL_VEHICLE_MODEL = "vehicle_model";
    private static final String COL_VEHICLE_YEARS = "vehicle_years";
    private static final String COL_MIN_VALUE = "min_value";
    private static final String COL_MAX_VALUE = "max_value";
    private static final String COL_UPDATE_PERIOD = "update_period";
    private static final String COL_ENABLED = "enabled";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_UPDATED_AT = "updated_at";

    // Singleton instance
    private static CustomPidDatabase instance;

    public static synchronized CustomPidDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new CustomPidDatabase(context.getApplicationContext());
        }
        return instance;
    }

    private CustomPidDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE " + TABLE_PIDS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NAME + " TEXT NOT NULL, " +
                COL_DESCRIPTION + " TEXT, " +
                COL_PID_HEX + " TEXT NOT NULL, " +
                COL_FORMULA + " TEXT NOT NULL, " +
                COL_UNITS + " TEXT, " +
                COL_VEHICLE_MAKE + " TEXT, " +
                COL_VEHICLE_MODEL + " TEXT, " +
                COL_VEHICLE_YEARS + " TEXT, " +
                COL_MIN_VALUE + " INTEGER DEFAULT 0, " +
                COL_MAX_VALUE + " INTEGER DEFAULT 65535, " +
                COL_UPDATE_PERIOD + " INTEGER DEFAULT 1000, " +
                COL_ENABLED + " INTEGER DEFAULT 1, " +
                COL_CREATED_AT + " INTEGER, " +
                COL_UPDATED_AT + " INTEGER" +
                ")";

        db.execSQL(CREATE_TABLE);

        // Create indices for common queries
        db.execSQL("CREATE INDEX idx_pid_hex ON " + TABLE_PIDS + "(" + COL_PID_HEX + ")");
        db.execSQL("CREATE INDEX idx_vehicle_make ON " + TABLE_PIDS + "(" + COL_VEHICLE_MAKE + ")");
        db.execSQL("CREATE INDEX idx_enabled ON " + TABLE_PIDS + "(" + COL_ENABLED + ")");

        Log.i(TAG, "Database created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        if (oldVersion < 2) {
            // Version 2: Disable all PIDs by default (they were enabled in v1)
            Log.i(TAG, "Migrating to v2: Disabling all PIDs by default");
            db.execSQL("UPDATE " + TABLE_PIDS + " SET " + COL_ENABLED + " = 0");
        }
    }

    /**
     * Insert a new custom PID
     */
    public long insertPid(CustomPid pid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = pidToContentValues(pid);

        long id = db.insert(TABLE_PIDS, null, values);
        DebugLog.d(TAG, "Inserted PID: " + pid.getName() + " with ID: " + id);

        return id;
    }

    /**
     * Update an existing custom PID
     */
    public int updatePid(CustomPid pid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = pidToContentValues(pid);

        int rows = db.update(TABLE_PIDS, values, COL_ID + " = ?",
                new String[]{String.valueOf(pid.getId())});

        DebugLog.d(TAG, "Updated PID: " + pid.getName() + ", rows affected: " + rows);
        return rows;
    }

    /**
     * Delete a custom PID
     */
    public int deletePid(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_PIDS, COL_ID + " = ?", new String[]{String.valueOf(id)});

        DebugLog.d(TAG, "Deleted PID ID: " + id + ", rows affected: " + rows);
        return rows;
    }

    /**
     * Get a PID by ID
     */
    public CustomPid getPid(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PIDS, null, COL_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null);

        CustomPid pid = null;
        if (cursor != null && cursor.moveToFirst()) {
            pid = cursorToPid(cursor);
            cursor.close();
        }

        return pid;
    }

    /**
     * Get all custom PIDs
     */
    public List<CustomPid> getAllPids() {
        return getAllPids(null);
    }

    /**
     * Get all enabled custom PIDs
     */
    public List<CustomPid> getEnabledPids() {
        return getAllPids(COL_ENABLED + " = 1");
    }

    /**
     * Get PIDs for a specific vehicle
     */
    public List<CustomPid> getPidsForVehicle(String make, String model, int year) {
        List<CustomPid> allPids = getEnabledPids();
        List<CustomPid> matchingPids = new ArrayList<>();

        for (CustomPid pid : allPids) {
            if (pid.matchesVehicle(make, model, year)) {
                matchingPids.add(pid);
            }
        }

        return matchingPids;
    }

    /**
     * Get PIDs by vehicle make
     */
    public List<CustomPid> getPidsByMake(String make) {
        String where = COL_VEHICLE_MAKE + " = ? OR " + COL_VEHICLE_MAKE + " IS NULL";
        return getAllPids(where, new String[]{make});
    }

    /**
     * Search PIDs by name or description
     */
    public List<CustomPid> searchPids(String query) {
        String where = COL_NAME + " LIKE ? OR " + COL_DESCRIPTION + " LIKE ?";
        String searchPattern = "%" + query + "%";
        return getAllPids(where, new String[]{searchPattern, searchPattern});
    }

    /**
     * Get all PIDs with optional where clause
     */
    private List<CustomPid> getAllPids(String whereClause, String... whereArgs) {
        List<CustomPid> pids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_PIDS, null, whereClause, whereArgs,
                null, null, COL_NAME + " ASC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                pids.add(cursorToPid(cursor));
            } while (cursor.moveToNext());
            cursor.close();
        }

        DebugLog.d(TAG, "Retrieved " + pids.size() + " PIDs");
        return pids;
    }

    /**
     * Get count of all PIDs
     */
    public int getPidCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PIDS, null);
        int count = 0;
        if (cursor != null && cursor.moveToFirst()) {
            count = cursor.getInt(0);
            cursor.close();
        }
        return count;
    }

    /**
     * Delete all PIDs (use with caution!)
     */
    public void deleteAllPids() {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_PIDS, null, null);
        Log.w(TAG, "Deleted all PIDs, rows affected: " + rows);
    }

    /**
     * Convert CustomPid to ContentValues
     */
    private ContentValues pidToContentValues(CustomPid pid) {
        ContentValues values = new ContentValues();
        values.put(COL_NAME, pid.getName());
        values.put(COL_DESCRIPTION, pid.getDescription());
        values.put(COL_PID_HEX, pid.getPidHex());
        values.put(COL_FORMULA, pid.getFormula());
        values.put(COL_UNITS, pid.getUnits());
        values.put(COL_VEHICLE_MAKE, pid.getVehicleMake());
        values.put(COL_VEHICLE_MODEL, pid.getVehicleModel());
        values.put(COL_VEHICLE_YEARS, pid.getVehicleYears());
        values.put(COL_MIN_VALUE, pid.getMinValue());
        values.put(COL_MAX_VALUE, pid.getMaxValue());
        values.put(COL_UPDATE_PERIOD, pid.getUpdatePeriod());
        values.put(COL_ENABLED, pid.isEnabled() ? 1 : 0);
        values.put(COL_CREATED_AT, pid.getCreatedAt());
        values.put(COL_UPDATED_AT, pid.getUpdatedAt());
        return values;
    }

    /**
     * Convert Cursor to CustomPid
     */
    private CustomPid cursorToPid(Cursor cursor) {
        CustomPid pid = new CustomPid();
        pid.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)));
        pid.setName(cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)));
        pid.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION)));
        pid.setPidHex(cursor.getString(cursor.getColumnIndexOrThrow(COL_PID_HEX)));
        pid.setFormula(cursor.getString(cursor.getColumnIndexOrThrow(COL_FORMULA)));
        pid.setUnits(cursor.getString(cursor.getColumnIndexOrThrow(COL_UNITS)));
        pid.setVehicleMake(cursor.getString(cursor.getColumnIndexOrThrow(COL_VEHICLE_MAKE)));
        pid.setVehicleModel(cursor.getString(cursor.getColumnIndexOrThrow(COL_VEHICLE_MODEL)));
        pid.setVehicleYears(cursor.getString(cursor.getColumnIndexOrThrow(COL_VEHICLE_YEARS)));
        pid.setMinValue(cursor.getInt(cursor.getColumnIndexOrThrow(COL_MIN_VALUE)));
        pid.setMaxValue(cursor.getInt(cursor.getColumnIndexOrThrow(COL_MAX_VALUE)));
        pid.setUpdatePeriod(cursor.getInt(cursor.getColumnIndexOrThrow(COL_UPDATE_PERIOD)));
        pid.setEnabled(cursor.getInt(cursor.getColumnIndexOrThrow(COL_ENABLED)) == 1);
        pid.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)));
        pid.setUpdatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED_AT)));
        return pid;
    }
}
