package com.obddroid.features.fueleconomy.ui;

import com.obddroid.features.fueleconomy.data.FuelEconomyCalculator;
import com.obddroid.features.fueleconomy.data.FuelEconomyPreferences;
import com.obddroid.services.VehicleManager;
import com.obddroid.utils.VehicleData;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Picks the fuel-rate method for the connected vehicle and feeds {@link FuelEconomyCalculator}: MAP-based
 * speed-density when MAP and IAT are read (VE calibrated per VIN, else estimated), otherwise the RPM/load
 * estimate. Engine displacement comes from the decoded vehicle, 2.5 L when unknown.
 */
final class FuelRateModel {

    private static final Logger log = Logger.getLogger(FuelRateModel.class.getName());

    /** VE assumed by the calibration dialog when this vehicle has none saved. */
    static final float DEFAULT_VE = 85.0f;

    private final FuelObdReader obd;
    private final FuelEconomyPreferences preferences;
    private final VehicleManager vehicleManager;

    FuelRateModel(FuelObdReader obd, FuelEconomyPreferences preferences, VehicleManager vehicleManager) {
        this.obd = obd;
        this.preferences = preferences;
        this.vehicleManager = vehicleManager;
    }

    /** Speed-density can be used (MAP and IAT present). */
    boolean usesMap() {
        return obd.hasMapAndIat();
    }

    /**
     * Fuel consumption in gal/h by the best method available: speed-density when MAP/IAT are present
     * and it gives more than 0.05 gal/h, else the RPM/load estimate.
     */
    float fuelRateGalH(float rpm, float loadPercent, float speedMph) {
        if (usesMap()) {
            log.info("Using MAP-based Speed-Density fuel calculation");
            float mapFuelRate = mapFuelRate();
            if (mapFuelRate > 0.05f) {
                return mapFuelRate;
            }
            log.warning("MAP calculation returned invalid result, falling back to estimation");
        } else {
            log.info("MAP sensors not available, using RPM/Load estimation");
        }

        return FuelEconomyCalculator.calculateEstimatedFuelRate(rpm, loadPercent, speedMph, displacementFactor());
    }

    private float mapFuelRate() {
        try {
            float rpm = obd.rpm();
            float mapKpa = obd.manifoldPressureKpa();
            float iatCelsius = obd.intakeAirTempC();
            float loadPercent = obd.engineLoad();
            float displacement = engineDisplacement();
            return FuelEconomyCalculator.calculateFuelRateMAP(rpm, mapKpa, iatCelsius, loadPercent,
                displacement, volumetricEfficiency(loadPercent, rpm));
        } catch (Exception e) {
            log.log(Level.WARNING, "Error in MAP-based fuel calculation", e);
            return 0f;
        }
    }

    /** The calibrated VE for this vehicle, else an estimate from load and RPM. */
    private float volumetricEfficiency(float loadPercent, float rpm) {
        Float calibratedVE = calibratedVE();
        if (calibratedVE != null) {
            return calibratedVE;
        }
        return FuelEconomyCalculator.estimateVolumetricEfficiency(loadPercent, rpm);
    }

    /** The VE saved by calibration for the current VIN, or null. */
    Float calibratedVE() {
        try {
            String vin = vehicleManager.getCurrentVIN();
            if (vin == null || vin.isEmpty()) {
                log.fine("No VIN available - cannot get calibrated VE");
                return null;
            }
            Float calibratedVE = preferences.getCalibratedVE(vin);
            if (calibratedVE != null) {
                log.info("Using calibrated VE for VIN: " + calibratedVE + "%");
            }
            return calibratedVE;
        } catch (Exception e) {
            log.warning("Error getting calibrated VE: " + e.getMessage());
            return null;
        }
    }

    /** Displacement in litres for speed-density; 2.5 when unknown or unparseable. */
    private float engineDisplacement() {
        try {
            Float displacement = decodedDisplacement();
            if (displacement != null) {
                return displacement;
            }
        } catch (NumberFormatException e) {
            log.fine("Could not get vehicle displacement: " + e.getMessage());
        }
        return 2.5f;
    }

    /** Displacement as the RPM/load scale factor (litres); 2.5 when unknown or unparseable. */
    private float displacementFactor() {
        try {
            Float displacement = decodedDisplacement();
            if (displacement != null) {
                log.info("Using actual displacement for fuel rate: " + displacement + "L");
                return FuelEconomyCalculator.getDisplacementFactor(displacement);
            }
        } catch (NumberFormatException e) {
            log.warning("Could not parse displacement: " + e.getMessage());
        }
        log.info("Using default 2.5L displacement factor (no vehicle data available)");
        return 2.5f;
    }

    private Float decodedDisplacement() {
        VehicleData vehicleData = vehicleManager.getCurrentVehicleData();
        if (vehicleData != null && vehicleData.displacementL != null && !vehicleData.displacementL.isEmpty()) {
            return Float.parseFloat(vehicleData.displacementL);
        }
        return null;
    }
}
