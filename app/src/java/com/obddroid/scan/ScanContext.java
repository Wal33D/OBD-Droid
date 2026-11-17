package com.obddroid.scan;

import android.content.Context;

import com.obddroid.obd.ElmProt;
import com.obddroid.services.VehicleManager;
import com.obddroid.services.discovery.DiscoveryManager;

import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONException;

/**
 * Shared execution context for all scan stages.
 * Provides access to services, configuration, and cross-stage state.
 */
public final class ScanContext {

    private final Context appContext;
    private final String scanId;
    private final File outputDirectory;
    private final ElmProt elmProt;
    private final VehicleManager vehicleManager;
    private final DiscoveryManager discoveryManager;
    private final AtomicBoolean cancelled;
    private final Map<String, Object> sharedData;
    private final ScanConfiguration configuration;

    public ScanContext(Context context,
                      String scanId,
                      ElmProt elmProt,
                      ScanConfiguration configuration) {
        this.appContext = context.getApplicationContext();
        this.scanId = scanId;
        this.elmProt = elmProt;
        this.configuration = configuration != null ? configuration : ScanConfiguration.getDefault();
        this.vehicleManager = VehicleManager.getInstance(appContext);
        this.discoveryManager = DiscoveryManager.getInstance();
        this.cancelled = new AtomicBoolean(false);
        this.sharedData = new HashMap<>();

        // Create output directory for this scan
        File baseDir = new File(context.getExternalFilesDir(null), "scans");
        this.outputDirectory = new File(baseDir, scanId);
        if (!outputDirectory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outputDirectory.mkdirs();
        }
    }

    public Context getAppContext() {
        return appContext;
    }

    public String getScanId() {
        return scanId;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public ElmProt getElmProt() {
        return elmProt;
    }

    public VehicleManager getVehicleManager() {
        return vehicleManager;
    }

    public DiscoveryManager getDiscoveryManager() {
        return discoveryManager;
    }

    public ScanConfiguration getConfiguration() {
        return configuration;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void checkCancelled() throws InterruptedException {
        if (cancelled.get()) {
            throw new InterruptedException("Scan cancelled by user");
        }
    }

    /**
     * Store data to be shared between stages (e.g., DTC count for freeze frame skip logic)
     */
    public void putSharedData(String key, Object value) {
        sharedData.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key, Class<T> type) {
        Object value = sharedData.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public JSONObject getVehicleMetadata() {
        JSONObject metadata = new JSONObject();
        try {
            metadata.put("scanId", scanId);
            metadata.put("timestamp", System.currentTimeMillis());
            if (vehicleManager.getCurrentVIN() != null) {
                metadata.put("vin", vehicleManager.getCurrentVIN());
            }
            if (vehicleManager.getCurrentVehicleData() != null) {
                metadata.put("make", vehicleManager.getCurrentVehicleData().getMake());
                metadata.put("model", vehicleManager.getCurrentVehicleData().getModel());
                metadata.put("year", vehicleManager.getCurrentVehicleData().getModelYear());
            }
            metadata.put("sessionId", discoveryManager.getActiveSessionId());
        } catch (JSONException e) {
            // Ignore
        }
        return metadata;
    }
}
