package com.obddroid.scan.stages;

import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;
import com.obddroid.scan.ScanContext;
import com.obddroid.scan.ScanStage;
import com.obddroid.scan.StageResult;
import com.obddroid.services.CommService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stage: Capture vehicle information from Mode 09
 */
public final class VehicleInfoStage implements ScanStage {

    private static final Logger log = Logger.getLogger(VehicleInfoStage.class.getName());

    @Override
    public String getId() {
        return "VEHICLE_INFO";
    }

    @Override
    public String getDisplayName() {
        return "Reading Vehicle Information";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 5;
    }

    @Override
    public StageResult execute(ScanContext context) throws InterruptedException {
        context.checkCancelled();

        try {
            // ACTIVELY REQUEST vehicle info from vehicle (Service 09)
            if (CommService.elm != null) {
                log.info("SCAN: Activating vehicle info service (Mode 09)");

                int previousService = CommService.elm.getService();
                boolean serviceChanged = previousService != ObdProt.OBD_SVC_VEH_INFO;

                try {
                    // Activate Service 09 (vehicle info) - this will trigger OBD to start querying VIDs
                    if (serviceChanged) {
                        CommService.elm.setService(ObdProt.OBD_SVC_VEH_INFO, false);
                    }

                    // Wait for data to populate (give OBD protocol time to query supported VIDs)
                    log.info("SCAN: Waiting for vehicle info to populate...");
                    int maxWaitSeconds = 6;

                    for (int i = 0; i < maxWaitSeconds * 2; i++) {
                        Thread.sleep(500);
                        context.checkCancelled();

                        int currentSize = ObdProt.VidPvs.size();
                        log.fine("SCAN: VidPvs size: " + currentSize);

                        // If we've received data and it's been stable for 1 second, we're done
                        if (currentSize > 0 && i > 2) {
                            break;
                        }
                    }

                    log.info("SCAN: Vehicle info populated, VidPvs size: " + ObdProt.VidPvs.size());
                } finally {
                    if (serviceChanged) {
                        log.info("SCAN: Restoring previous service " + ObdProt.getServiceName(previousService));
                        CommService.elm.setService(previousService, false);
                    }
                }
            } else {
                log.warning("SCAN: CommService.elm is null, cannot actively request vehicle info");
            }

            JSONObject data = new JSONObject();
            JSONArray vidsArray = new JSONArray();

            int capturedCount = 0;

            // Snapshot current VidPvs (Mode 09 data)
            if (ObdProt.VidPvs != null && !ObdProt.VidPvs.isEmpty()) {
                // Get a copy of the entries to avoid concurrent modification
                // NOTE: VidPvs keys are actually Strings at runtime (e.g., "04.1.0"), not Integers
                @SuppressWarnings("unchecked")
                java.util.Set<Map.Entry<Object, EcuDataPv>> entries =
                    new java.util.HashSet<>(ObdProt.VidPvs.entrySet());

                for (Map.Entry<Object, EcuDataPv> entry : entries) {
                    context.checkCancelled();

                    try {
                        Object key = entry.getKey();
                        EcuDataPv pv = entry.getValue();

                        if (pv == null) {
                            log.warning("SCAN: Skipping null VID entry for key: " + key);
                            continue;
                        }

                        JSONObject vidObj = new JSONObject();
                        vidObj.put("vid", key != null ? key.toString() : "unknown");

                        // Safely get values with null checks
                        Object description = pv.get(EcuDataPv.FID_DESCRIPT);
                        Object value = pv.get(EcuDataPv.FID_VALUE);
                        Object units = pv.get(EcuDataPv.FID_UNITS);

                        vidObj.put("description", description != null ? description.toString() : "");
                        vidObj.put("value", value != null ? value.toString() : "");
                        vidObj.put("units", units != null ? units.toString() : "");

                        vidsArray.put(vidObj);
                        capturedCount++;

                        log.info("SCAN: Captured VID " + key + ": " + description + " = " + value);
                    } catch (Exception e) {
                        log.log(Level.WARNING, "SCAN: Error serializing VID " + entry.getKey(), e);
                        continue;
                    }
                }
            }

            // Also capture VIN from VehicleManager if available
            String vin = context.getVehicleManager().getCurrentVIN();
            if (vin != null && !vin.isEmpty()) {
                data.put("vin", vin);
            }

            data.put("vids", vidsArray);
            data.put("count", capturedCount);
            data.put("timestamp", System.currentTimeMillis());

            String message = capturedCount > 0 ?
                String.format("Captured %d vehicle info item(s)", capturedCount) :
                "No vehicle info available";

            if (vin != null && !vin.isEmpty()) {
                message += " (VIN: " + vin + ")";
            }

            log.info("SCAN: Vehicle info stage complete - " + message);

            return StageResult.success(message, data);

        } catch (Exception e) {
            log.severe("SCAN: Vehicle info stage failed: " + e.getMessage());
            return StageResult.failed("Failed to capture vehicle info: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        return false; // Always try to capture vehicle info
    }
}
