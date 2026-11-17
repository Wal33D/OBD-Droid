package com.obddroid.features.emissions.data;

import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manager for emissions monitor data
 *
 * Manages the collection of OBD-II emissions monitors and provides
 * high-level operations for updating and querying monitor status.
 *
 * Maintains a LinkedHashMap to preserve monitor display order:
 * - Continuous monitors (MISFIRE, FUEL, CCM)
 * - Non-continuous monitors (CATALYST, EVAP, O2SENSOR, etc.)
 */
public class EmissionsDataManager {

    private static final Logger log = Logger.getLogger(EmissionsDataManager.class.getName());

    private final Map<String, MonitorData> monitorDataMap;

    public EmissionsDataManager() {
        this.monitorDataMap = new LinkedHashMap<>();
        initializeMonitors();
    }

    /**
     * Initialize all standard OBD-II emissions monitors
     *
     * Creates monitor data objects in proper display order:
     * 1. Continuous monitors (always running)
     * 2. Non-continuous monitors (require specific conditions)
     */
    private void initializeMonitors() {
        // Continuous monitors (always running when engine is on)
        monitorDataMap.put("MISFIRE", new MonitorData("Misfire Detection"));
        monitorDataMap.put("FUEL", new MonitorData("Fuel System Monitor"));
        monitorDataMap.put("CCM", new MonitorData("Comprehensive Component (CCM)"));

        // Non-continuous monitors (require specific drive cycle conditions)
        monitorDataMap.put("CATALYST", new MonitorData("Catalyst Efficiency"));
        monitorDataMap.put("EVAP", new MonitorData("EVAP System"));
        monitorDataMap.put("O2SENSOR", new MonitorData("O2 Sensor Monitor"));
        monitorDataMap.put("O2HEATER", new MonitorData("O2 Sensor Heater"));
        monitorDataMap.put("EGR", new MonitorData("EGR System"));
        monitorDataMap.put("AIR", new MonitorData("Secondary Air System"));

        log.info("Initialized " + monitorDataMap.size() + " emissions monitors");
    }

    /**
     * Get the monitor data map
     *
     * @return Map of monitor keys to MonitorData objects
     */
    public Map<String, MonitorData> getMonitorDataMap() {
        return monitorDataMap;
    }

    /**
     * Get a specific monitor by key
     *
     * @param monitorKey Monitor key (e.g., "MISFIRE", "CATALYST")
     * @return MonitorData or null if not found
     */
    public MonitorData getMonitor(String monitorKey) {
        return monitorDataMap.get(monitorKey);
    }

    /**
     * Update monitor readiness data from Mode 1 PID 0x01
     *
     * Parses all PID entries and updates monitor status flags.
     */
    public void updateMonitorReadiness() {
        if (ObdProt.PidPvs == null || ObdProt.PidPvs.isEmpty()) {
            log.info("updateMonitorReadiness: PidPvs is " + (ObdProt.PidPvs == null ? "null" : "empty"));
            return;
        }

        log.info("updateMonitorReadiness: Parsing " + ObdProt.PidPvs.size() + " PID entries...");

        // Iterate through PidPvs to find monitor status entries
        int foundCount = 0;
        List<Map.Entry<String, EcuDataPv>> entries =
            new ArrayList<>(ObdProt.PidPvs.entrySetTyped());
        for (Map.Entry<String, EcuDataPv> entry : entries) {
            EcuDataPv pv = entry.getValue();
            if (pv != null) {
                String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                Object formattedVal = pv.get(EcuDataPv.FID_VALUE);

                if (description == null || formattedVal == null) continue;

                String monitorKey = EmissionsDataParser.parseMonitorKeyFromDescription(description);

                if (monitorKey != null) {
                    MonitorData monitor = monitorDataMap.get(monitorKey);
                    if (monitor != null) {
                        EmissionsDataParser.parseMonitorStatus(monitorKey, monitor, formattedVal, description);
                        foundCount++;
                    }
                }
            }
        }
        log.info("updateMonitorReadiness: Found " + foundCount + " monitor status entries");
    }

    /**
     * Update IUMPR performance data from Mode 9 PID 0x08
     *
     * Parses all VID entries and updates monitor performance ratios.
     */
    public void updateIUMPRData() {
        EmissionsDataParser.parseIUMPRData(monitorDataMap);
    }

    /**
     * Update all monitor data (readiness + IUMPR)
     *
     * IUMPR is parsed before readiness so that Mode 01 PID 01 (authoritative)
     * overrides any availability the IUMPR pass inferred from condition counts.
     */
    public void updateAllData() {
        updateIUMPRData();
        updateMonitorReadiness();
    }

    /**
     * Reset all monitor data to default state
     */
    public void reset() {
        monitorDataMap.clear();
        initializeMonitors();
        log.info("Reset all monitor data");
    }

    /**
     * Check if any monitor data is available
     *
     * @return true if at least one monitor has data
     */
    public boolean hasData() {
        for (MonitorData monitor : monitorDataMap.values()) {
            if (monitor.isAvailable) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get list of continuous monitors
     *
     * @return List of MonitorData for continuous monitors
     */
    public List<MonitorData> getContinuousMonitors() {
        List<MonitorData> monitors = new ArrayList<>();
        monitors.add(monitorDataMap.get("MISFIRE"));
        monitors.add(monitorDataMap.get("FUEL"));
        monitors.add(monitorDataMap.get("CCM"));
        return monitors;
    }

    /**
     * Get list of non-continuous monitors
     *
     * @return List of MonitorData for non-continuous monitors
     */
    public List<MonitorData> getNonContinuousMonitors() {
        List<MonitorData> monitors = new ArrayList<>();
        for (Map.Entry<String, MonitorData> entry : monitorDataMap.entrySet()) {
            String key = entry.getKey();
            if (!EmissionsDataParser.isContinuousMonitor(key)) {
                monitors.add(entry.getValue());
            }
        }
        return monitors;
    }

    /**
     * Get summary statistics
     *
     * @return Summary object with counts and status
     */
    public Summary getSummary() {
        int available = 0;
        int ready = 0;
        int notReady = 0;

        for (MonitorData monitor : monitorDataMap.values()) {
            if (monitor.isAvailable) {
                available++;
                if (monitor.isReady) {
                    ready++;
                } else {
                    notReady++;
                }
            }
        }

        return new Summary(available, ready, notReady);
    }

    /**
     * Summary statistics for emissions monitors
     */
    public static class Summary {
        public final int availableCount;
        public final int readyCount;
        public final int notReadyCount;

        public Summary(int availableCount, int readyCount, int notReadyCount) {
            this.availableCount = availableCount;
            this.readyCount = readyCount;
            this.notReadyCount = notReadyCount;
        }
    }
}
