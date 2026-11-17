package com.obddroid.features.fueleconomy.ui;

import com.obddroid.ecu.EcuDataPv;
import com.obddroid.obd.ObdProt;

import java.util.logging.Logger;

/**
 * The live OBD values the fuel economy screen reads from {@link ObdProt#PidPvs} (keys "PID.SENSOR.BANK",
 * e.g. "0D.0.0"), each with a fallback when the PID has no value yet.
 */
final class FuelObdReader {

    private static final Logger log = Logger.getLogger(FuelObdReader.class.getName());

    /** Vehicle speed (PID 0x0D) in mph; 0 without data. */
    float speedMph() {
        try {
            EcuDataPv speedPv = ObdProt.PidPvs.getTyped("0D.0.0");
            log.info("Speed PV lookup: " + (speedPv != null ? "FOUND" : "NULL"));
            if (speedPv != null) {
                Object value = speedPv.get(EcuDataPv.FID_VALUE);
                log.info("Speed value: " + value);
                if (value != null) {
                    float kmh = Float.parseFloat(value.toString());
                    float mph = kmh * 0.621371f;
                    log.info("Speed: " + kmh + " km/h = " + mph + " mph");
                    return mph;
                }
            }
        } catch (Exception e) {
            log.warning("Error getting speed: " + e.getMessage());
        }
        log.info("Speed: returning 0 (no data)");
        return 0f;
    }

    /** Engine speed (PID 0x0C); 800 (idle) without data. */
    float rpm() {
        try {
            EcuDataPv rpmPv = ObdProt.PidPvs.getTyped("0C.0.0");
            log.info("RPM PV lookup: " + (rpmPv != null ? "FOUND" : "NULL"));
            if (rpmPv != null) {
                Object value = rpmPv.get(EcuDataPv.FID_VALUE);
                log.info("RPM value: " + value);
                if (value != null) {
                    float rpm = Float.parseFloat(value.toString());
                    log.info("RPM: " + rpm);
                    return rpm;
                }
            }
        } catch (Exception e) {
            log.warning("Error getting RPM: " + e.getMessage());
        }
        log.info("RPM: returning 800 (default idle)");
        return 800f;
    }

    /** Engine load (PID 0x04) in percent; 20 (light load) without data. */
    float engineLoad() {
        try {
            EcuDataPv loadPv = ObdProt.PidPvs.getTyped("04.0.0");
            log.info("Load PV lookup: " + (loadPv != null ? "FOUND" : "NULL"));
            if (loadPv != null) {
                Object value = loadPv.get(EcuDataPv.FID_VALUE);
                log.info("Load value: " + value);
                if (value != null) {
                    float load = Float.parseFloat(value.toString());
                    log.info("Engine Load: " + load + "%");
                    return load;
                }
            }
        } catch (Exception e) {
            log.warning("Error getting engine load: " + e.getMessage());
        }
        log.info("Engine Load: returning 20 (default)");
        return 20f;
    }

    /** Throttle position (PID 0x11) in percent; 0 without data. */
    float throttle() {
        try {
            EcuDataPv throttlePv = ObdProt.PidPvs.getTyped("11.0.0");
            if (throttlePv != null) {
                Object value = throttlePv.get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    return Float.parseFloat(value.toString());
                }
            }
        } catch (Exception e) {
            // No throttle reading: show closed
        }
        return 0f;
    }

    /**
     * Manifold absolute pressure (PID 0x0B) in kPa when within 10-250; else 101.3 (sea-level
     * atmosphere).
     */
    float manifoldPressureKpa() {
        try {
            EcuDataPv mapPv = ObdProt.PidPvs.getTyped("0B.0.0");
            log.fine("MAP PV lookup: " + (mapPv != null ? "FOUND" : "NULL"));
            if (mapPv != null) {
                Object value = mapPv.get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    float mapKpa = Float.parseFloat(value.toString());
                    log.fine("MAP: " + mapKpa + " kPa");
                    if (mapKpa >= 10f && mapKpa <= 250f) {
                        return mapKpa;
                    }
                    log.warning("MAP value out of range: " + mapKpa + " kPa");
                }
            }
        } catch (Exception e) {
            log.fine("Error getting MAP: " + e.getMessage());
        }
        log.fine("MAP: returning 101.3 kPa (atmospheric pressure fallback)");
        return 101.3f;
    }

    /** Intake air temperature (PID 0x0F) in °C when within -40..120; else 25. */
    float intakeAirTempC() {
        try {
            EcuDataPv iatPv = ObdProt.PidPvs.getTyped("0F.0.0");
            log.fine("IAT PV lookup: " + (iatPv != null ? "FOUND" : "NULL"));
            if (iatPv != null) {
                Object value = iatPv.get(EcuDataPv.FID_VALUE);
                if (value != null) {
                    float iatCelsius = Float.parseFloat(value.toString());
                    log.fine("IAT: " + iatCelsius + "°C");
                    if (iatCelsius >= -40f && iatCelsius <= 120f) {
                        return iatCelsius;
                    }
                    log.warning("IAT value out of range: " + iatCelsius + "°C");
                }
            }
        } catch (Exception e) {
            log.fine("Error getting IAT: " + e.getMessage());
        }
        log.fine("IAT: returning 25°C (room temperature fallback)");
        return 25.0f;
    }

    /** Both MAP (0x0B) and IAT (0x0F) are being read, so speed-density can be used. */
    boolean hasMapAndIat() {
        try {
            return ObdProt.PidPvs.get("0B.0.0") != null && ObdProt.PidPvs.get("0F.0.0") != null;
        } catch (Exception e) {
            log.fine("Error checking MAP availability: " + e.getMessage());
            return false;
        }
    }

    /**
     * The fuel tank level (PID 0x2F) in percent, or null when the PID is not present or has no value.
     * A value that does not parse throws NumberFormatException.
     */
    Float fuelLevelPercent() {
        EcuDataPv fuelLevelPv = ObdProt.PidPvs.getTyped("2F.0.0");
        if (fuelLevelPv == null) {
            log.warning("Fuel level PV (2F.0.0) not found or wrong type");
            return null;
        }
        Object value = fuelLevelPv.get(EcuDataPv.FID_VALUE);
        return value != null ? Float.parseFloat(value.toString()) : null;
    }
}
