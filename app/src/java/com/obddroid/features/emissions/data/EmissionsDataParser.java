package com.obddroid.features.emissions.data;

import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Parser for OBD-II emissions monitor data
 *
 * Handles parsing of:
 * - Mode 1 PID 0x01: Monitor readiness status (bit flags)
 * - Mode 9 PID 0x08: IUMPR (In-Use Monitor Performance Ratio) data
 *
 * This class has no Android dependencies and can be unit tested independently.
 */
public class EmissionsDataParser {

    private static final Logger log = Logger.getLogger(EmissionsDataParser.class.getName());

    /**
     * Parse monitor key from OBD description string
     *
     * Maps various description formats to standardized monitor keys:
     * MISFIRE, FUEL, CCM, CATALYST, EVAP, O2SENSOR, O2HEATER, EGR, AIR
     *
     * @param description OBD description string
     * @return Monitor key or null if not recognized
     */
    public static String parseMonitorKeyFromDescription(String description) {
        if (description == null) return null;

        String lowerDescription = description.toLowerCase(Locale.US);

        // CRITICAL: Only parse READINESS STATUS PIDs from Mode 1 PID 0x01!
        // Reject regular sensor PIDs like "Oxygen sensors present", "EVAP purge", etc.
        // Readiness PIDs have "test" in description, except "Misfire status"
        boolean isReadinessPid = lowerDescription.contains("test")
                              || lowerDescription.equals("misfire status");

        if (!isReadinessPid) {
            return null;  // Skip non-readiness PIDs
        }

        if (lowerDescription.contains("misfire")) {
            return "MISFIRE";
        } else if (lowerDescription.contains("fuel system")) {
            return "FUEL";
        } else if (lowerDescription.contains("component")) {
            return "CCM";
        } else if (lowerDescription.contains("catalyst")) {
            return "CATALYST";
        } else if (lowerDescription.contains("evap")) {
            return "EVAP";
        } else if (lowerDescription.contains("oxygen sensor heater")) {
            return "O2HEATER";
        } else if (lowerDescription.contains("oxygen sensor")) {
            return "O2SENSOR";
        } else if (lowerDescription.contains("egr")) {
            return "EGR";
        } else if (lowerDescription.contains("secondary air") || lowerDescription.contains("air system")) {
            return "AIR";
        }

        return null;
    }

    /**
     * Check if a monitor is continuous (always running when engine is on)
     *
     * Continuous monitors: MISFIRE, FUEL, CCM
     * Non-continuous monitors: All others
     *
     * @param monitorKey Standardized monitor key
     * @return true if continuous
     */
    public static boolean isContinuousMonitor(String monitorKey) {
        return "MISFIRE".equals(monitorKey)
            || "FUEL".equals(monitorKey)
            || "CCM".equals(monitorKey);
    }

    /**
     * Parse monitor status from Mode 1 PID 0x01 value
     *
     * Handles both numeric bit flags and text status strings. Supported/complete come from
     * PID 01 alone; IUMPR counts are not used here.
     *
     * @param monitorKey Monitor key
     * @param monitor MonitorData object to update
     * @param valueObj Raw value from OBD (Number or String)
     * @param description OBD description for logging
     */
    public static void parseMonitorStatus(String monitorKey, MonitorData monitor,
                                          Object valueObj, String description) {
        boolean isAvailable = false;
        boolean isComplete = false;

        if (valueObj instanceof Number) {
            int raw = ((Number) valueObj).intValue();
            boolean isContinuous = isContinuousMonitor(monitorKey);

            if (isContinuous) {
                // Continuous monitors use different bit positions
                isAvailable = (raw & 0x01) != 0;
                boolean incompleteBitSet = (raw & 0x10) != 0;
                isComplete = isAvailable && !incompleteBitSet;
            } else {
                // Non-continuous monitors (SAE J1979 Mode 01 PID 01, pids.csv TEST_STATUS_8):
                // the decoded value is ((C << 8 | D) >> n) & 0x101, so
                //   0x100 = byte C bit n = monitor supported
                //   0x001 = byte D bit n = monitor not complete
                // PID 01 is authoritative; IUMPR (Mode 09) counts are informational only.
                isAvailable = (raw & 0x100) != 0;
                boolean incompleteBitSet = (raw & 0x001) != 0;
                isComplete = isAvailable && !incompleteBitSet;
            }

            log.info(String.format(Locale.US,
                "  Monitor bits [%s] raw=0x%03X -> available=%s, complete=%s (desc=%s)",
                monitorKey,
                raw & 0x1FF,
                isAvailable,
                isComplete,
                description));
        } else {
            // Text-based status parsing
            String statusStr = String.valueOf(valueObj);
            isAvailable = statusStr.contains("(*) Available") || statusStr.contains("(*) Complete");
            isComplete = statusStr.contains("(*) Complete");
            log.info(String.format(Locale.US,
                "  Monitor text [%s] \"%s\" -> available=%s, complete=%s",
                monitorKey,
                statusStr.replace("\n", " | "),
                isAvailable,
                isComplete));
        }

        monitor.isAvailable = isAvailable;
        monitor.isReady = isComplete && isAvailable;
    }

    /**
     * Extract integer value from OBD PV data
     *
     * Handles both Number objects and String values.
     * Correctly parses floating point strings (e.g., "5136.0" → 5136, not 51360)
     *
     * @param pv EcuDataPv object
     * @return Integer value or 0 if parsing fails
     */
    public static int getIntValue(EcuDataPv pv) {
        try {
            Object dataValue = pv.get(EcuDataPv.FID_VALUE);
            if (dataValue != null) {
                // Handle numeric types directly to avoid decimal point removal bug
                if (dataValue instanceof Number) {
                    return ((Number) dataValue).intValue();
                }
                // Handle string values (parse as float first to handle decimals correctly)
                String valStr = dataValue.toString().trim();
                if (!valStr.isEmpty()) {
                    // Parse as float first, then convert to int (avoids "5136.0" → "51360" bug)
                    return (int) Float.parseFloat(valStr);
                }
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
        return 0;
    }

    /**
     * Parse IUMPR data from Mode 9 PID 0x08
     *
     * Updates monitor performance data (completions/conditions) for all monitors.
     * Handles dual-bank engines by aggregating Bank 1 + Bank 2 data.
     *
     * @param monitorDataMap Map of monitor keys to MonitorData objects
     */
    public static void parseIUMPRData(Map<String, MonitorData> monitorDataMap) {
        if (ObdProt.VidPvs == null || ObdProt.VidPvs.isEmpty()) {
            log.info("parseIUMPRData: VidPvs is " + (ObdProt.VidPvs == null ? "null" : "empty"));
            return;
        }

        log.info("parseIUMPRData: Parsing " + ObdProt.VidPvs.size() + " VID entries...");

        // IUMPR global counters
        int obdConditions = 0;
        int ignitionCounter = 0;
        int foundIUMPRCount = 0;

        // Per-monitor data
        int catComp1 = 0, catCond1 = 0, catComp2 = 0, catCond2 = 0;
        int o2sComp1 = 0, o2sCond1 = 0, o2sComp2 = 0, o2sCond2 = 0;
        int evapComp = 0, evapCond = 0;
        int egrComp = 0, egrCond = 0;
        int airComp = 0, airCond = 0;
        int hccatComp = 0, hccatCond = 0;

        // Iterate through all VidPvs entries using a snapshot to avoid concurrent modification
        List<Map.Entry<Integer, EcuDataPv>> vidEntries =
            new ArrayList<>(ObdProt.VidPvs.entrySetTyped());
        for (Map.Entry<Integer, EcuDataPv> entry : vidEntries) {
            EcuDataPv pv = entry.getValue();
            if (pv != null) {
                String description = String.valueOf(pv.get(EcuDataPv.FID_DESCRIPT));
                int intValue = getIntValue(pv);

                if (description == null) continue;

                // Match descriptions to data fields
                // IMPORTANT: Match OBD Conditions and Ignition Counter EXACTLY to avoid false matches
                // IMPORTANT: Vehicle may return duplicate entries with 0 values - only use non-zero values

                if (description.equals("OBD Monitoring Conditions Encountered Counts") ||
                    description.equals("OBD Monitoring Conditions")) {
                    // Only update if new value is non-zero, or if we don't have a value yet
                    if (intValue > 0 || obdConditions == 0) {
                        if (intValue > obdConditions) {
                            obdConditions = intValue;
                            foundIUMPRCount++;
                            log.info("  Found: OBD Conditions = " + intValue);
                        } else if (intValue == 0 && obdConditions > 0) {
                            log.info("  Ignoring duplicate OBD Conditions with value 0 (keeping " + obdConditions + ")");
                        }
                    }
                } else if (description.equals("Ignition Counter") ||
                          description.equals("Ignition Cycles")) {
                    // Only update if new value is non-zero, or if we don't have a value yet
                    if (intValue > 0 || ignitionCounter == 0) {
                        if (intValue > ignitionCounter) {
                            ignitionCounter = intValue;
                            foundIUMPRCount++;
                            log.info("  Found: Ignition Counter = " + intValue);
                        } else if (intValue == 0 && ignitionCounter > 0) {
                            log.info("  Ignoring duplicate Ignition Counter with value 0 (keeping " + ignitionCounter + ")");
                        }
                    }
                }
                // Catalyst Monitor
                else if (description.contains("Catalyst Monitor Completion") && description.contains("Bank 1")) {
                    catComp1 = intValue;
                } else if (description.contains("Catalyst Monitor Conditions") && description.contains("Bank 1")) {
                    catCond1 = intValue;
                } else if (description.contains("Catalyst Monitor Completion") && description.contains("Bank 2")) {
                    catComp2 = intValue;
                } else if (description.contains("Catalyst Monitor Conditions") && description.contains("Bank 2")) {
                    catCond2 = intValue;
                }
                // O2 Sensor Monitor
                else if (description.contains("O2 Sensor Monitor Completion") && description.contains("Bank 1")) {
                    o2sComp1 = intValue;
                } else if (description.contains("O2 Sensor Monitor Conditions") && description.contains("Bank 1")) {
                    o2sCond1 = intValue;
                } else if (description.contains("O2 Sensor Monitor Completion") && description.contains("Bank 2")) {
                    o2sComp2 = intValue;
                } else if (description.contains("O2 Sensor Monitor Conditions") && description.contains("Bank 2")) {
                    o2sCond2 = intValue;
                }
                // EVAP
                else if (description.contains("EVAP") && description.contains("Completion")) {
                    evapComp = intValue;
                } else if (description.contains("EVAP") && description.contains("Conditions")) {
                    evapCond = intValue;
                }
                // EGR
                else if (description.contains("EGR") && description.contains("Completion")) {
                    egrComp = intValue;
                } else if (description.contains("EGR") && description.contains("Conditions")) {
                    egrCond = intValue;
                }
                // Secondary Air
                else if (description.contains("Secondary Air") && description.contains("Completion")) {
                    airComp = intValue;
                } else if (description.contains("Secondary Air") && description.contains("Conditions")) {
                    airCond = intValue;
                }
                // O2 Heater / Heated Catalyst
                else if ((description.contains("Heated Catalyst") || description.contains("O2 Sensor Heater"))
                         && description.contains("Completion")) {
                    hccatComp = intValue;
                } else if ((description.contains("Heated Catalyst") || description.contains("O2 Sensor Heater"))
                           && description.contains("Conditions")) {
                    hccatCond = intValue;
                }
            }
        }

        log.info("parseIUMPRData: Found " + foundIUMPRCount + " IUMPR entries");

        // Update continuous monitors with global OBD conditions
        // Continuous monitors are always running, so completions = conditions
        if (obdConditions > 0) {
            updateContinuousMonitor(monitorDataMap, "MISFIRE", obdConditions);
            updateContinuousMonitor(monitorDataMap, "FUEL", obdConditions);
            updateContinuousMonitor(monitorDataMap, "CCM", obdConditions);
        }

        // Update non-continuous monitors
        // Aggregate Bank 1 + Bank 2 data for dual-bank engines
        if (catCond1 > 0 || catCond2 > 0) {
            updateMonitorPerformance(monitorDataMap, "CATALYST",
                catComp1 + catComp2, catCond1 + catCond2);
        }
        if (o2sCond1 > 0 || o2sCond2 > 0) {
            updateMonitorPerformance(monitorDataMap, "O2SENSOR",
                o2sComp1 + o2sComp2, o2sCond1 + o2sCond2);
        }
        if (evapCond > 0) {
            updateMonitorPerformance(monitorDataMap, "EVAP", evapComp, evapCond);
        }
        if (egrCond > 0) {
            updateMonitorPerformance(monitorDataMap, "EGR", egrComp, egrCond);
        }
        if (airCond > 0) {
            updateMonitorPerformance(monitorDataMap, "AIR", airComp, airCond);
        }
        if (hccatCond > 0) {
            updateMonitorPerformance(monitorDataMap, "O2HEATER", hccatComp, hccatCond);
        }
    }

    /**
     * Update continuous monitor with conditions count
     *
     * Continuous monitors are always running, so completions = conditions (100%)
     *
     * @param monitorDataMap Map of monitors
     * @param monitorKey Monitor key
     * @param conditions Number of conditions encountered
     */
    private static void updateContinuousMonitor(Map<String, MonitorData> monitorDataMap,
                                                 String monitorKey, int conditions) {
        MonitorData monitor = monitorDataMap.get(monitorKey);
        if (monitor == null) return;

        // Continuous monitors are always running when engine is on
        // So we set completions = conditions (100% completion rate)
        monitor.conditions = conditions;
        monitor.completions = conditions;

        // Mark as available if we have data
        if (conditions > 0) {
            monitor.isAvailable = true;
        }

        log.info(String.format("  Updated continuous monitor [%s]: conditions=%d, completions=%d",
            monitorKey, conditions, conditions));
    }

    /**
     * Update monitor performance data from IUMPR
     *
     * @param monitorDataMap Map of monitors
     * @param monitorKey Monitor key
     * @param completions Number of times monitor completed
     * @param conditions Number of times conditions were met
     */
    private static void updateMonitorPerformance(Map<String, MonitorData> monitorDataMap,
                                                  String monitorKey, int completions, int conditions) {
        MonitorData monitor = monitorDataMap.get(monitorKey);
        if (monitor == null) return;

        // Update IUMPR data (Mode 09) - for display/informational purposes
        monitor.completions = completions;
        monitor.conditions = conditions;

        // Monitor is available if it has IUMPR condition data
        // This indicates the monitor is equipped and operating
        if (conditions > 0) {
            monitor.isAvailable = true;

            // FALLBACK: If Mode 01 readiness data is not available (empty PidPvs),
            // use IUMPR completion count. A monitor is "Ready" if it has completed
            // at least once (completions > 0), regardless of the IUMPR ratio.
            // The ratio (e.g., 0.002%) is for regulatory compliance tracking,
            // NOT for emissions test readiness. Mode 01 PID 01 is AUTHORITATIVE
            // when available, but this fallback prevents all monitors showing
            // "Not Ready" when Mode 01 is unavailable.
            if (ObdProt.PidPvs == null || ObdProt.PidPvs.isEmpty()) {
                monitor.isReady = (completions > 0);
            }
            // else: Mode 01 status (set in parseMonitorStatus) takes precedence
        }

        log.info(String.format("  Updated monitor [%s]: completions=%d, conditions=%d, ratio=%.1f%%",
            monitorKey, completions, conditions, monitor.getRatio() * 100));
    }
}
