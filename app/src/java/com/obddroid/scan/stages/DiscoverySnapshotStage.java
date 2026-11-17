package com.obddroid.scan.stages;

import com.obddroid.scan.ScanContext;
import com.obddroid.scan.ScanStage;
import com.obddroid.scan.StageResult;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * Stage 1: Capture current discovery session state (adapter, ECUs, addresses)
 */
public final class DiscoverySnapshotStage implements ScanStage {

    @Override
    public String getId() {
        return "DISCOVERY_SNAPSHOT";
    }

    @Override
    public String getDisplayName() {
        return "Capturing ECU Discovery Data";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 2;
    }

    @Override
    public StageResult execute(ScanContext context) throws InterruptedException {
        context.checkCancelled();

        try {
            JSONObject data = new JSONObject();

            // Get discovery summary
            JSONObject discoverySummary = context.getDiscoveryManager().getDiscoverySummary();
            data.put("discovery", discoverySummary);

            // Get all ECU snapshots
            JSONObject ecuSnapshots = context.getDiscoveryManager().getAllEcuSnapshots();
            data.put("ecuSnapshots", ecuSnapshots);

            // Add vehicle metadata
            data.put("vehicle", context.getVehicleMetadata());

            int ecuCount = context.getDiscoveryManager().getDiscoveredAddressesSnapshot().size();
            String message = String.format("Captured %d ECU(s)", ecuCount);

            // Store ECU count for other stages
            context.putSharedData("ecuCount", ecuCount);

            return StageResult.success(message, data);

        } catch (JSONException e) {
            return StageResult.failed("Failed to capture discovery data: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        return false; // Always run
    }
}
