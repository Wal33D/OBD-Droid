package com.obddroid.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Hybrid VIN Decoder - Parallel Race Strategy with Online/Offline
 *
 * Strategy:
 * 1. Start BOTH NHTSA API and offline database queries in PARALLEL
 * 2. Use whichever completes first for fastest response time
 * 3. Prefer online data if both complete (more comprehensive)
 * 4. Pre-initialize offline database on startup for instant access
 *
 * Performance: ~1-2 seconds typical, 3 seconds worst-case
 *
 * Powered by:
 * - Primary: NHTSA vPIC API (online)
 * - Fallback: NHTSA Offline Decoder - 66MB vPIC database (offline)
 *
 * @author Wal33D <aquataze@yahoo.com>
 */
public class VINDecoder {

    private static final String TAG = "VINDecoder";
    private static final long RACE_TIMEOUT_MS = 5000; // Maximum total wait time

    private final NhtsaVINDecoder onlineDecoder;
    private final NhtsaOfflineVINDecoder offlineDecoder;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean databasePrewarmed = false;

    public interface DecodeCallback {
        void onSuccess(VehicleData vehicleData);
        void onError(String error);
    }

    public VINDecoder(Context context) {
        this.onlineDecoder = new NhtsaVINDecoder(context);
        this.offlineDecoder = new NhtsaOfflineVINDecoder(context);
        DebugLog.d(TAG, "✓ Hybrid VIN Decoder initialized (Parallel Race Strategy)");

        // Pre-warm database in background
        prewarmDatabase();
    }

    /**
     * Pre-initialize the offline database in background
     * This ensures the 66MB database is ready when needed
     */
    public void prewarmDatabase() {
        if (!databasePrewarmed) {
            executor.execute(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    // Do a dummy decode to force database initialization
                    offlineDecoder.validate("00000000000000000");
                    long elapsed = System.currentTimeMillis() - startTime;
                    databasePrewarmed = true;
                    DebugLog.d(TAG, "✓ Database pre-warmed in " + elapsed + "ms");
                } catch (Exception e) {
                    Log.w(TAG, "Database pre-warm failed: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Decode VIN asynchronously with parallel race strategy
     * Both decoders run simultaneously, fastest wins
     */
    public void decodeAsync(String vin, DecodeCallback callback) {
        executor.execute(() -> {
            try {
                DebugLog.d(TAG, "Starting parallel VIN decode for: " + vin);
                long startTime = System.currentTimeMillis();

                VehicleData vehicleData = decodeParallel(vin);

                long elapsed = System.currentTimeMillis() - startTime;
                DebugLog.d(TAG, String.format("✓ Decode completed in %dms", elapsed));

                if (vehicleData.isValid()) {
                    DebugLog.d(TAG, String.format("✓ Decoded (%s): %s %s %s",
                            vehicleData.dataSource,
                            vehicleData.getModelYear(),
                            vehicleData.getMake(),
                            vehicleData.getModel()));
                } else {
                    Log.w(TAG, "✗ Decode failed: " + vehicleData.getErrorMessage());
                }

                // Callback on main thread
                mainHandler.post(() -> {
                    if (vehicleData.isValid()) {
                        callback.onSuccess(vehicleData);
                    } else {
                        callback.onError(vehicleData.getErrorMessage());
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Decode error", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Decode VIN synchronously with parallel race strategy
     */
    public VehicleData decode(String vin) {
        return decodeParallel(vin);
    }

    /**
     * Parallel decode strategy - race both decoders
     */
    private VehicleData decodeParallel(String vin) {
        // Track which decoder finishes first
        AtomicBoolean raceCompleted = new AtomicBoolean(false);
        CompletableFuture<VehicleData> result = new CompletableFuture<>();

        // Start online decoder in parallel
        CompletableFuture<VehicleData> onlineFuture = CompletableFuture.supplyAsync(() -> {
            try {
                DebugLog.d(TAG, "  → Starting online decode...");
                long start = System.currentTimeMillis();
                VehicleData data = onlineDecoder.decode(vin);
                long elapsed = System.currentTimeMillis() - start;

                if (data != null && data.isValid()) {
                    DebugLog.d(TAG, "  ✓ Online decode succeeded in " + elapsed + "ms");
                    data.dataSource = "NHTSA API (Online)";

                    // If we're first, use our result
                    if (raceCompleted.compareAndSet(false, true)) {
                        DebugLog.d(TAG, "  🏆 Online decoder won the race!");
                        result.complete(data);
                    }
                    return data;
                } else {
                    DebugLog.d(TAG, "  ✗ Online decode failed after " + elapsed + "ms");
                    return null;
                }
            } catch (Exception e) {
                DebugLog.d(TAG, "  ✗ Online decode error: " + e.getMessage());
                return null;
            }
        }, executor);

        // Start offline decoder in parallel
        CompletableFuture<VehicleData> offlineFuture = CompletableFuture.supplyAsync(() -> {
            try {
                DebugLog.d(TAG, "  → Starting offline decode...");
                long start = System.currentTimeMillis();
                NhtsaOfflineVINDecoder.VehicleInfo info = offlineDecoder.decode(vin);
                long elapsed = System.currentTimeMillis() - start;

                if (info != null && info.valid) {
                    DebugLog.d(TAG, "  ✓ Offline decode succeeded in " + elapsed + "ms");
                    VehicleData data = VehicleData.fromOfflineDecoder(info);
                    data.dataSource = "Offline Database";
                    addOfflineIndicators(data);

                    // If we're first, use our result
                    if (raceCompleted.compareAndSet(false, true)) {
                        DebugLog.d(TAG, "  🏆 Offline decoder won the race!");
                        result.complete(data);
                    }
                    return data;
                } else {
                    DebugLog.d(TAG, "  ✗ Offline decode failed after " + elapsed + "ms");
                    return null;
                }
            } catch (Exception e) {
                DebugLog.d(TAG, "  ✗ Offline decode error: " + e.getMessage());
                return null;
            }
        }, executor);

        // Wait for the first successful result (race condition)
        try {
            // Wait up to RACE_TIMEOUT_MS for a result
            VehicleData winnerData = result.get(RACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Optional: Wait a bit longer to see if online data comes in
            // (prefer online data if both succeed close together)
            if (winnerData.dataSource.equals("Offline Database")) {
                try {
                    // Give online decoder 500ms more to complete
                    VehicleData onlineData = onlineFuture.get(500, TimeUnit.MILLISECONDS);
                    if (onlineData != null && onlineData.isValid()) {
                        DebugLog.d(TAG, "  ⚡ Switching to online data (better quality)");
                        return onlineData;
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException | TimeoutException ignored) {
                    // Online didn't finish in time, stick with offline
                }
            }

            return winnerData;

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.e(TAG, "Race timeout or error: " + e.getMessage());

            // Both failed, try to get any result
            try {
                // Check if offline at least completed
                VehicleData offlineData = offlineFuture.getNow(null);
                if (offlineData != null && offlineData.isValid()) {
                    return offlineData;
                }

                // Check if online completed
                VehicleData onlineData = onlineFuture.getNow(null);
                if (onlineData != null && onlineData.isValid()) {
                    return onlineData;
                }
            } catch (CancellationException ignored) {
                // Both suppliers catch their own errors and return null, so getNow only throws if cancelled
            }

            // Total failure
            VehicleData errorData = new VehicleData();
            errorData.setValid(false);
            errorData.setErrorMessage("VIN decode failed: " + e.getMessage());
            return errorData;
        } finally {
            // Cancel any still-running tasks
            onlineFuture.cancel(true);
            offlineFuture.cancel(true);
        }
    }

    /**
     * Add "Requires internet" indicators for fields not available offline
     */
    private void addOfflineIndicators(VehicleData data) {
        // Fields that are typically missing in offline mode
        if (data.engineCylinders == null || data.engineCylinders.isEmpty()) {
            data.engineCylinders = "Requires internet";
        }
        if (data.displacementCC == null || data.displacementCC.isEmpty()) {
            data.displacementCC = "Requires internet";
        }
        if (data.driveType == null || data.driveType.isEmpty()) {
            data.driveType = "Requires internet";
        }
        if (data.transmissionStyle == null || data.transmissionStyle.isEmpty()) {
            data.transmissionStyle = "Requires internet";
        }
        if (data.transmissionSpeeds == null || data.transmissionSpeeds.isEmpty()) {
            data.transmissionSpeeds = "Requires internet";
        }
        if (data.wheelBase == null || data.wheelBase.isEmpty()) {
            data.wheelBase = "Requires internet";
        }
        if (data.plantCity == null || data.plantCity.isEmpty()) {
            data.plantCity = "Requires internet";
        }
    }

    /**
     * Get manufacturer name from VIN (fast, offline only)
     */
    public String getManufacturer(String vin) {
        return offlineDecoder.getManufacturer(vin);
    }

    /**
     * Get make name from VIN (fast, offline only)
     */
    public String getMake(String vin) {
        return offlineDecoder.getMake(vin);
    }

    /**
     * Validate VIN format
     */
    public boolean validate(String vin) {
        return offlineDecoder.validate(vin);
    }

    /**
     * Reload database (called after automatic update)
     */
    public void reloadDatabase() {
        DebugLog.d(TAG, "Reloading VIN database after update");
        offlineDecoder.reloadDatabase();
        databasePrewarmed = false;
        prewarmDatabase();
    }

    /**
     * Shutdown decoder and cleanup resources
     */
    public void shutdown() {
        executor.shutdown();
        offlineDecoder.close();
        DebugLog.d(TAG, "VIN Decoder shutdown");
    }
}
