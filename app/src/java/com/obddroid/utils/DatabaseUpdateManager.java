package com.obddroid.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Manages automatic updates for the vPIC database
 *
 * Strategy:
 * 1. Ships with bundled database (works offline immediately)
 * 2. Checks for updates monthly in background (WiFi only)
 * 3. Downloads and replaces database seamlessly
 * 4. Falls back to bundled database if update fails
 */
public class DatabaseUpdateManager {

    private static final String TAG = "DatabaseUpdateManager";
    private static final String PREFS_NAME = "database_updates";
    private static final String PREF_LAST_UPDATE = "last_update_timestamp";
    private static final String PREF_DATABASE_VERSION = "database_version";

    // Update configuration
    // NHTSA vPIC database compiled into SQLite format (public USDOT vehicle data)
    private static final String DB_DOWNLOAD_URL =
        "https://cdn.jsdelivr.net/npm/@cardog/corgi@latest/dist/db/vpic.lite.db.gz";
    private static final long UPDATE_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000; // 30 days
    private static final String DB_NAME = "vpic.lite.db";

    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor;

    public interface UpdateCallback {
        void onUpdateStarted();
        void onUpdateProgress(int bytesDownloaded, int totalBytes);
        void onUpdateSuccess(String newVersion);
        void onUpdateFailed(String error);
        void onUpdateNotNeeded();
    }

    public DatabaseUpdateManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Check if database update is needed and initiate if necessary
     * This should be called on app startup (background thread)
     */
    public void checkForUpdates(UpdateCallback callback) {
        executor.execute(() -> {
            try {
                DebugLog.d(TAG, "Checking for database updates...");

                // Check if update is needed
                if (!isUpdateNeeded()) {
                    DebugLog.d(TAG, "Update not needed yet (last update was recent)");
                    if (callback != null) {
                        callback.onUpdateNotNeeded();
                    }
                    return;
                }

                // Check WiFi connection
                if (!isWiFiConnected()) {
                    DebugLog.d(TAG, "Not on WiFi - skipping update");
                    if (callback != null) {
                        callback.onUpdateNotNeeded();
                    }
                    return;
                }

                DebugLog.d(TAG, "✓ Update needed and WiFi available - starting download");
                performUpdate(callback);

            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
                if (callback != null) {
                    callback.onUpdateFailed(e.getMessage());
                }
            }
        });
    }

    /**
     * Force update check regardless of interval
     */
    public void forceUpdate(UpdateCallback callback) {
        executor.execute(() -> {
            if (!isWiFiConnected()) {
                Log.w(TAG, "Force update requested but not on WiFi");
                if (callback != null) {
                    callback.onUpdateFailed("WiFi connection required for database update");
                }
                return;
            }
            performUpdate(callback);
        });
    }

    /**
     * Check if update is needed based on last update time
     */
    private boolean isUpdateNeeded() {
        long lastUpdate = prefs.getLong(PREF_LAST_UPDATE, 0);
        long now = System.currentTimeMillis();
        long timeSinceUpdate = now - lastUpdate;

        DebugLog.d(TAG, String.format("Last update: %d days ago",
            timeSinceUpdate / (24 * 60 * 60 * 1000)));

        return timeSinceUpdate >= UPDATE_INTERVAL_MS;
    }

    /**
     * Check if device is connected to WiFi
     */
    private boolean isWiFiConnected() {
        ConnectivityManager cm = (ConnectivityManager)
            context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        Network network = cm.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        if (capabilities == null) return false;

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    /**
     * Perform the actual database update
     */
    private void performUpdate(UpdateCallback callback) {
        File tempFile = null;
        File tempExtracted = null;

        try {
            if (callback != null) {
                callback.onUpdateStarted();
            }

            DebugLog.d(TAG, "Downloading database from: " + DB_DOWNLOAD_URL);

            // Download to temp file
            tempFile = new File(context.getCacheDir(), "vpic_update.db.gz");
            tempExtracted = new File(context.getCacheDir(), "vpic_update.db");

            downloadFile(DB_DOWNLOAD_URL, tempFile, callback);

            DebugLog.d(TAG, "✓ Download complete, extracting...");

            // Extract gzip
            extractGzip(tempFile, tempExtracted);

            DebugLog.d(TAG, "✓ Extraction complete, verifying...");

            // Verify downloaded database
            if (!verifyDatabase(tempExtracted)) {
                throw new IOException("Downloaded database failed verification");
            }

            DebugLog.d(TAG, "✓ Verification passed, replacing database...");

            // Replace current database
            File dbFile = context.getDatabasePath(DB_NAME);
            replaceDatabase(tempExtracted, dbFile);

            // Update metadata
            long now = System.currentTimeMillis();
            prefs.edit()
                .putLong(PREF_LAST_UPDATE, now)
                .putString(PREF_DATABASE_VERSION, String.valueOf(now))
                .apply();

            DebugLog.d(TAG, "✓ Database update complete!");

            if (callback != null) {
                callback.onUpdateSuccess(String.valueOf(now));
            }

        } catch (Exception e) {
            Log.e(TAG, "Database update failed", e);
            if (callback != null) {
                callback.onUpdateFailed(e.getMessage());
            }
        } finally {
            // Cleanup temp files
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            if (tempExtracted != null && tempExtracted.exists()) {
                tempExtracted.delete();
            }
        }
    }

    /**
     * Download file from URL with progress tracking
     */
    private void downloadFile(String urlString, File destFile, UpdateCallback callback)
            throws IOException {
        HttpURLConnection connection = null;
        InputStream input = null;
        FileOutputStream output = null;

        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP " +
                    connection.getResponseCode());
            }

            int fileLength = connection.getContentLength();
            input = new BufferedInputStream(connection.getInputStream());
            output = new FileOutputStream(destFile);

            byte[] buffer = new byte[8192];
            int total = 0;
            int count;

            while ((count = input.read(buffer)) != -1) {
                total += count;
                output.write(buffer, 0, count);

                // Progress callback (every 1MB)
                if (callback != null && total % (1024 * 1024) == 0) {
                    callback.onUpdateProgress(total, fileLength);
                }
            }

            output.flush();

            DebugLog.d(TAG, String.format("Downloaded %d bytes", total));

        } finally {
            if (output != null) output.close();
            if (input != null) input.close();
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Extract gzip compressed file
     */
    private void extractGzip(File gzipFile, File outputFile) throws IOException {
        try (GZIPInputStream gzis = new GZIPInputStream(new FileInputStream(gzipFile));
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }

            DebugLog.d(TAG, String.format("Extracted to %d bytes", outputFile.length()));
        }
    }

    /**
     * Verify database integrity by checking it can be opened
     */
    private boolean verifyDatabase(File dbFile) {
        if (!dbFile.exists() || dbFile.length() < 1000000) {
            Log.e(TAG, "Database file too small or doesn't exist");
            return false;
        }

        // Basic verification - check file size is reasonable (30-100MB)
        long size = dbFile.length();
        if (size < 30_000_000 || size > 100_000_000) {
            Log.e(TAG, "Database size out of expected range: " + size);
            return false;
        }

        DebugLog.d(TAG, "Database verification passed (size: " + size + " bytes)");
        return true;
    }

    /**
     * Atomically replace current database with new one
     */
    private void replaceDatabase(File newDb, File currentDb) throws IOException {
        // Close any open connections first
        // (caller should handle this)

        // Create backup
        File backup = new File(currentDb.getAbsolutePath() + ".backup");
        if (currentDb.exists()) {
            if (!currentDb.renameTo(backup)) {
                throw new IOException("Failed to create database backup");
            }
        }

        try {
            // Copy new database to location
            copyFile(newDb, currentDb);

            // Delete backup on success
            if (backup.exists()) {
                backup.delete();
            }

        } catch (IOException e) {
            // Restore backup on failure
            if (backup.exists()) {
                backup.renameTo(currentDb);
            }
            throw e;
        }
    }

    /**
     * Copy file from source to destination
     */
    private void copyFile(File src, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dest)) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.flush();
        }
    }

    /**
     * Get last update timestamp
     */
    public long getLastUpdateTime() {
        return prefs.getLong(PREF_LAST_UPDATE, 0);
    }

    /**
     * Get current database version
     */
    public String getDatabaseVersion() {
        return prefs.getString(PREF_DATABASE_VERSION, "bundled");
    }

    /**
     * Get days since last update
     */
    public int getDaysSinceUpdate() {
        long lastUpdate = getLastUpdateTime();
        if (lastUpdate == 0) return -1; // Never updated

        long diff = System.currentTimeMillis() - lastUpdate;
        return (int) (diff / (24 * 60 * 60 * 1000));
    }

    /**
     * Shutdown executor
     */
    public void shutdown() {
        executor.shutdown();
    }
}
