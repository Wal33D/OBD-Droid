package com.obddroid.scan.stages;

import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;
import com.obddroid.scan.ScanContext;
import com.obddroid.scan.ScanStage;
import com.obddroid.scan.StageResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stage: Analyze emissions monitor readiness (Mode 01) and IUMPR metrics (Mode 09).
 * Builds a structured snapshot compatible with AI analyzer and human-readable reports.
 */
public final class MonitorTestStage implements ScanStage {

    @Override
    public String getId() {
        return "MONITOR_TESTS";
    }

    @Override
    public String getDisplayName() {
        return "Analyzing Emissions Monitors";
    }

    @Override
    public int getEstimatedDurationSeconds() {
        return 6;
    }

    @Override
    public StageResult execute(ScanContext context) throws InterruptedException {
        if (!context.getConfiguration().isIncludeMonitorTests()) {
            return StageResult.skipped("Monitor analysis disabled in configuration");
        }

        context.checkCancelled();

        MonitorSnapshot snapshot = buildSnapshot();
        try {
            JSONObject data = snapshot.toJson();

            String message;
            if (snapshot.availableCount == 0) {
                message = "No emissions monitor data available yet";
            } else {
                message = String.format(Locale.US,
                    "%d of %d monitor(s) ready • %d not ready",
                    snapshot.readyCount,
                    snapshot.availableCount,
                    snapshot.availableCount - snapshot.readyCount
                );
            }

            // Share summary for downstream stages if needed
            context.putSharedData("monitorSummary", data);
            context.putSharedData("monitorReadyCount", snapshot.readyCount);
            context.putSharedData("monitorAvailableCount", snapshot.availableCount);

            return StageResult.success(message, data);
        } catch (JSONException e) {
            return StageResult.failed("Failed to serialize monitor snapshot: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean shouldSkip(ScanContext context) {
        return false;
    }

    private MonitorSnapshot buildSnapshot() {
        MonitorSnapshot snapshot = new MonitorSnapshot();
        // IUMPR first, then PID 01 readiness, so readiness (authoritative) wins over any
        // availability the IUMPR pass inferred.
        snapshot.populateIumprFromVid();
        snapshot.populateReadinessFromPid();
        snapshot.computeOverall();
        return snapshot;
    }

    /**
     * Lightweight data model capturing both readiness and IUMPR metrics.
     */
    private static final class MonitorSnapshot {
        private final Map<String, MonitorStatus> monitors = new LinkedHashMap<>();
        private int availableCount;
        private int readyCount;

        private MonitorSnapshot() {
            initialize();
        }

        private void initialize() {
            monitors.put("MISFIRE", MonitorStatus.continuous("Misfire Detection"));
            monitors.put("FUEL", MonitorStatus.continuous("Fuel System Monitor"));
            monitors.put("CCM", MonitorStatus.continuous("Comprehensive Component (CCM)"));

            monitors.put("CATALYST", MonitorStatus.nonContinuous("Catalyst Efficiency"));
            monitors.put("EVAP", MonitorStatus.nonContinuous("EVAP System"));
            monitors.put("O2SENSOR", MonitorStatus.nonContinuous("O2 Sensor Monitor"));
            monitors.put("O2HEATER", MonitorStatus.nonContinuous("O2 Sensor Heater"));
            monitors.put("EGR", MonitorStatus.nonContinuous("EGR System"));
            monitors.put("AIR", MonitorStatus.nonContinuous("Secondary Air System"));
        }

        private void populateReadinessFromPid() {
            if (ObdProt.PidPvs == null || ObdProt.PidPvs.isEmpty()) {
                return;
            }

            List<Map.Entry<String, EcuDataPv>> entries =
                new ArrayList<>(ObdProt.PidPvs.entrySetTyped());

            for (Map.Entry<String, EcuDataPv> entry : entries) {
                EcuDataPv pv = entry.getValue();
                if (pv == null) continue;

                String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                Object valueObj = pv.get(EcuDataPv.FID_VALUE);
                if (description == null || valueObj == null) continue;

                MonitorStatus monitor = findMonitorByDescription(description.toLowerCase(Locale.US));
                if (monitor != null) {
                    monitor.applyReadiness(valueObj);
                }
            }
        }

        private MonitorStatus findMonitorByDescription(String lowerDescription) {
            if (lowerDescription.contains("misfire")) {
                return monitors.get("MISFIRE");
            } else if (lowerDescription.contains("fuel system")) {
                return monitors.get("FUEL");
            } else if (lowerDescription.contains("component")) {
                return monitors.get("CCM");
            } else if (lowerDescription.contains("catalyst")) {
                return monitors.get("CATALYST");
            } else if (lowerDescription.contains("evap")) {
                return monitors.get("EVAP");
            } else if (lowerDescription.contains("oxygen sensor heater")) {
                return monitors.get("O2HEATER");
            } else if (lowerDescription.contains("oxygen sensor")) {
                return monitors.get("O2SENSOR");
            } else if (lowerDescription.contains("egr")) {
                return monitors.get("EGR");
            } else if (lowerDescription.contains("secondary air") || lowerDescription.contains("air system")) {
                return monitors.get("AIR");
            }
            return null;
        }

        private void populateIumprFromVid() {
            if (ObdProt.VidPvs == null || ObdProt.VidPvs.isEmpty()) {
                return;
            }

            List<Map.Entry<Integer, EcuDataPv>> entries =
                new ArrayList<>(ObdProt.VidPvs.entrySetTyped());

            int obdConditions = 0;
            int ignitionCounter = 0;

            int catComp1 = 0, catCond1 = 0, catComp2 = 0, catCond2 = 0;
            int o2Comp1 = 0, o2Cond1 = 0, o2Comp2 = 0, o2Cond2 = 0;
            int evapComp = 0, evapCond = 0;
            int egrComp = 0, egrCond = 0;
            int airComp = 0, airCond = 0;
            int heaterComp = 0, heaterCond = 0;

            for (Map.Entry<Integer, EcuDataPv> entry : entries) {
                EcuDataPv pv = entry.getValue();
                if (pv == null) continue;

                String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                if (description == null) continue;
                description = description.trim();

                int value = getIntValue(pv);
                if (description.equals("OBD Conditions Count") || description.equals("OBD Monitoring Conditions")) {
                    if (value > 0 || obdConditions == 0) {
                        obdConditions = Math.max(obdConditions, value);
                    }
                } else if (description.equals("Ignition Counter") || description.equals("Ignition Cycles")) {
                    if (value > 0 || ignitionCounter == 0) {
                        ignitionCounter = Math.max(ignitionCounter, value);
                    }
                } else if (containsAll(description, "Catalyst", "Completion", "Bank 1")) {
                    catComp1 = value;
                } else if (containsAll(description, "Catalyst", "Conditions", "Bank 1")) {
                    catCond1 = value;
                } else if (containsAll(description, "Catalyst", "Completion", "Bank 2")) {
                    catComp2 = value;
                } else if (containsAll(description, "Catalyst", "Conditions", "Bank 2")) {
                    catCond2 = value;
                } else if (containsAll(description, "O2 Sensor", "Completion", "Bank 1")) {
                    o2Comp1 = value;
                } else if (containsAll(description, "O2 Sensor", "Conditions", "Bank 1")) {
                    o2Cond1 = value;
                } else if (containsAll(description, "O2 Sensor", "Completion", "Bank 2")) {
                    o2Comp2 = value;
                } else if (containsAll(description, "O2 Sensor", "Conditions", "Bank 2")) {
                    o2Cond2 = value;
                } else if (containsAll(description, "EVAP", "Completion")) {
                    evapComp = value;
                } else if (containsAll(description, "EVAP", "Conditions")) {
                    evapCond = value;
                } else if (containsAll(description, "EGR", "Completion")) {
                    egrComp = value;
                } else if (containsAll(description, "EGR", "Conditions")) {
                    egrCond = value;
                } else if (containsAll(description, "Secondary Air", "Completion") ||
                           containsAll(description, "AIR", "Completion")) {
                    airComp = value;
                } else if (containsAll(description, "Secondary Air", "Conditions") ||
                           containsAll(description, "AIR", "Conditions")) {
                    airCond = value;
                } else if (containsAll(description, "Heated Catalyst", "Completion")) {
                    heaterComp = value;
                } else if (containsAll(description, "Heated Catalyst", "Conditions")) {
                    heaterCond = value;
                }
            }

            int conditionsBaseline = obdConditions > 0 ? obdConditions : ignitionCounter;
            if (conditionsBaseline > 0) {
                updateContinuousMonitor("MISFIRE", conditionsBaseline);
                updateContinuousMonitor("FUEL", conditionsBaseline);
                updateContinuousMonitor("CCM", conditionsBaseline);
            }

            updateMonitorPerformance("CATALYST", catComp1 + catComp2, catCond1 + catCond2);
            updateMonitorPerformance("O2SENSOR", o2Comp1 + o2Comp2, o2Cond1 + o2Cond2);
            updateMonitorPerformance("EVAP", evapComp, evapCond);
            updateMonitorPerformance("EGR", egrComp, egrCond);
            updateMonitorPerformance("AIR", airComp, airCond);
            updateMonitorPerformance("O2HEATER", heaterComp, heaterCond);
        }

        private void updateContinuousMonitor(String key, int conditions) {
            MonitorStatus monitor = monitors.get(key);
            if (monitor == null) return;

            monitor.conditions = conditions;
            monitor.completions = conditions;
            if (conditions > 0) {
                monitor.available = true;
                monitor.ready = true;
            }
        }

        private void updateMonitorPerformance(String key, int completions, int conditions) {
            MonitorStatus monitor = monitors.get(key);
            if (monitor == null) return;

            monitor.completions = completions;
            monitor.conditions = conditions;

            if (conditions > 0) {
                monitor.available = true;
                if (!monitor.hasReadinessSample()) {
                    monitor.ready = completions > 0;
                }
            }
        }

        private void computeOverall() {
            availableCount = 0;
            readyCount = 0;
            for (MonitorStatus monitor : monitors.values()) {
                if (monitor.available) {
                    availableCount++;
                    if (monitor.ready) {
                        readyCount++;
                    }
                }
            }
        }

        private JSONObject toJson() throws JSONException {
            JSONObject root = new JSONObject();
            root.put("availableCount", availableCount);
            root.put("readyCount", readyCount);
            root.put("notReadyCount", Math.max(0, availableCount - readyCount));

            JSONArray monitorsArray = new JSONArray();
            for (MonitorStatus monitor : monitors.values()) {
                monitorsArray.put(monitor.toJson());
            }
            root.put("monitors", monitorsArray);

            if (availableCount > 0) {
                boolean passesEpa = (availableCount - readyCount) <= 1;
                root.put("passesEpaStandard", passesEpa);
            } else {
                root.put("passesEpaStandard", JSONObject.NULL);
            }

            return root;
        }

        private boolean containsAll(String haystack, String... needles) {
            String lower = haystack.toLowerCase(Locale.US);
            for (String needle : needles) {
                if (!lower.contains(needle.toLowerCase(Locale.US))) {
                    return false;
                }
            }
            return true;
        }

        private int getIntValue(EcuDataPv pv) {
            try {
                Object value = pv.get(EcuDataPv.FID_VALUE);
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                if (value != null) {
                    String str = value.toString().trim();
                    if (!str.isEmpty()) {
                        return (int) Float.parseFloat(str);
                    }
                }
            } catch (Exception ignored) {
            }
            return 0;
        }
    }

    /**
     * Per-monitor tracking container.
     */
    private static final class MonitorStatus {
        final String name;
        final boolean continuous;
        boolean available;
        boolean ready;
        int completions;
        int conditions;
        private boolean readinessSampled;

        private MonitorStatus(String name, boolean continuous) {
            this.name = name;
            this.continuous = continuous;
        }

        static MonitorStatus continuous(String name) {
            return new MonitorStatus(name, true);
        }

        static MonitorStatus nonContinuous(String name) {
            return new MonitorStatus(name, false);
        }

        void applyReadiness(Object valueObj) {
            readinessSampled = true;
            if (valueObj instanceof Number) {
                int raw = ((Number) valueObj).intValue();
                if (continuous) {
                    boolean supported = (raw & 0x01) != 0;
                    boolean incomplete = (raw & 0x10) != 0;
                    available = supported;
                    ready = supported && !incomplete;
                } else {
                    // J1979 PID 01 bytes C/D, decoded as ((C << 8 | D) >> n) & 0x101:
                    // 0x100 = supported (byte C), 0x001 = not complete (byte D)
                    boolean supported = (raw & 0x100) != 0;
                    boolean incomplete = (raw & 0x001) != 0;
                    available = supported;
                    ready = supported && !incomplete;
                }
            } else {
                String status = String.valueOf(valueObj);
                available = status.contains("Available") || status.contains("Complete");
                ready = status.contains("Complete");
            }
        }

        boolean hasReadinessSample() {
            return readinessSampled;
        }

        JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("continuous", continuous);
            obj.put("available", available);
            obj.put("ready", ready);
            obj.put("completions", completions);
            obj.put("conditions", conditions);
            obj.put("completionPercentage", completionPercentage());

            String quality = iumprQuality();
            if (quality != null) {
                obj.put("iumprQuality", quality);
            } else {
                obj.put("iumprQuality", JSONObject.NULL);
            }
            return obj;
        }

        private int completionPercentage() {
            if (conditions <= 0) return 0;
            return Math.min(100, Math.round((completions / (float) conditions) * 100f));
        }

        private String iumprQuality() {
            if (completions <= 0 || conditions <= 0) return null;
            float ratio = completions / (float) conditions;

            if (name.contains("EVAP")) {
                if (ratio >= 0.52f) return "🟢 Excellent";
                if (ratio >= 0.10f) return "🟡 Good";
                return "🟠 Low Frequency";
            }

            if (ratio >= 0.10f) return "🟢 Excellent";
            return "🟠 Low Frequency";
        }
    }
}
