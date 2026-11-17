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
import java.util.logging.Logger;
import org.json.JSONException;

/**
 * Stage 2: Capture live data snapshot from Mode 01 PIDs
 */
public final class LiveDataStage implements ScanStage {

    private static final Logger log = Logger.getLogger(LiveDataStage.class.getName());

    @Override
    public String getId() {
        return "LIVE_DATA";
    }

    @Override
    public String getDisplayName() {
        return "Capturing Live Data Snapshot";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 5;
    }

    @Override
    public StageResult execute(ScanContext context) throws InterruptedException {
        context.checkCancelled();

        try {
            // ACTIVELY REQUEST live data from vehicle (Service 01)
            if (CommService.elm != null) {
                log.info("SCAN: Activating live data service (Mode 01)");

                // Remember current service so we can reliably restore it afterwards
                int previousService = CommService.elm.getService();
                boolean serviceChanged = previousService != ObdProt.OBD_SVC_DATA;

                try {
                    // Activate Service 01 (live data) - this will trigger OBD to start querying PIDs
                    if (serviceChanged) {
                        CommService.elm.setService(ObdProt.OBD_SVC_DATA, false);
                    }

                    // Wait for data to populate (give OBD protocol time to query supported PIDs)
                    log.info("SCAN: Waiting for live data to populate...");
                    int maxWaitSeconds = 8;
                    int initialSize = ObdProt.PidPvs.size();

                    for (int i = 0; i < maxWaitSeconds * 2; i++) {
                        Thread.sleep(500);
                        context.checkCancelled();

                        int currentSize = ObdProt.PidPvs.size();
                        log.fine("SCAN: PidPvs size: " + currentSize);

                        // If we've received data and it's been stable for 1 second, we're done
                        if (currentSize > 1 && i > 2) {
                            break;
                        }
                    }

                    log.info("SCAN: Live data populated, PidPvs size: " + ObdProt.PidPvs.size());
                } finally {
                    if (serviceChanged) {
                        log.info("SCAN: Restoring previous service " + ObdProt.getServiceName(previousService));
                        CommService.elm.setService(previousService, false);
                    }
                }
            } else {
                log.warning("SCAN: CommService.elm is null, cannot actively request live data");
            }

            JSONObject data = new JSONObject();
            JSONArray pidsArray = new JSONArray();

            int capturedCount = 0;

            // Snapshot current PidPvs (Mode 01 data)
            if (ObdProt.PidPvs != null && !ObdProt.PidPvs.isEmpty()) {
                // Get a copy of the entries to avoid concurrent modification
                @SuppressWarnings("unchecked")
                java.util.Set<Map.Entry<String, EcuDataPv>> entries =
                    new java.util.HashSet<>(ObdProt.PidPvs.entrySet());

                for (Map.Entry<String, EcuDataPv> entry : entries) {
                    context.checkCancelled();

                    try {
                        String key = entry.getKey();
                        EcuDataPv pv = entry.getValue();

                        if (pv == null) {
                            log.warning("SCAN: Skipping null PID entry for key: " + key);
                            continue;
                        }

                        JSONObject pidObj = new JSONObject();
                        pidObj.put("key", key);

                        // Safely get values with null checks
                        Object description = pv.get(EcuDataPv.FID_DESCRIPT);
                        Object value = pv.get(EcuDataPv.FID_VALUE);
                        Object units = pv.get(EcuDataPv.FID_UNITS);
                        Object pid = pv.get(EcuDataPv.FID_PID);

                        pidObj.put("description", description != null ? description.toString() : "");
                        pidObj.put("value", value != null ? value.toString() : "");
                        pidObj.put("units", units != null ? units.toString() : "");
                        pidObj.put("pid", pid != null ? pid.toString() : "");

                        pidsArray.put(pidObj);
                        capturedCount++;
                    } catch (JSONException e) {
                        // Log the actual error so we can debug
                        log.warning("SCAN: Error serializing PID " + entry.getKey() + ": " + e.getMessage());
                        continue;
                    }
                }
            }

            data.put("pids", pidsArray);
            data.put("count", capturedCount);
            data.put("timestamp", System.currentTimeMillis());

            String message = capturedCount > 0 ?
                String.format("Captured %d live data PID(s)", capturedCount) :
                "No live data available";

            log.info("SCAN: Live data stage complete - " + message);

            return StageResult.success(message, data);

        } catch (Exception e) {
            log.severe("SCAN: Live data stage failed: " + e.getMessage());
            return StageResult.failed("Failed to capture live data: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        return !context.getConfiguration().isIncludeLiveData();
    }
}
