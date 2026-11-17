package com.obddroid.features.fueleconomy.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FuelEconomyFormatTest {

    @Test
    public void parseNumberReadsDisplayedValues() {
        assertEquals(23.4f, FuelEconomyFormat.parseNumber("23.4"), 0.0001f);
        assertEquals(12f, FuelEconomyFormat.parseNumber(" 12 mph "), 0.0001f);
        assertEquals(-3.5f, FuelEconomyFormat.parseNumber("-3.5"), 0.0001f);
        assertTrue(Float.isNaN(FuelEconomyFormat.parseNumber("--")));
        assertTrue(Float.isNaN(FuelEconomyFormat.parseNumber("")));
        assertTrue(Float.isNaN(FuelEconomyFormat.parseNumber(null)));
        assertTrue(Float.isNaN(FuelEconomyFormat.parseNumber("mph")));
        assertTrue("a time is not one number", Float.isNaN(FuelEconomyFormat.parseNumber("1.2.3")));
    }

    @Test
    public void cleanTextDropsPlaceholders() {
        assertEquals("2:30", FuelEconomyFormat.cleanText(" 2:30 "));
        assertNull(FuelEconomyFormat.cleanText("--"));
        assertNull(FuelEconomyFormat.cleanText("   "));
        assertNull(FuelEconomyFormat.cleanText(null));
    }

    @Test
    public void timeToEmptyIsHoursAndMinutesUnderTenHours() {
        assertEquals("2:30", FuelEconomyFormat.timeToEmpty(5f, 2f));
        assertEquals("0:15", FuelEconomyFormat.timeToEmpty(0.5f, 2f));
        assertEquals("12.5", FuelEconomyFormat.timeToEmpty(12.5f, 1f));
        assertEquals("no meaningful flow", "--", FuelEconomyFormat.timeToEmpty(10f, 0.05f));
        assertEquals("--", FuelEconomyFormat.timeToEmpty(10f, Float.NaN));
    }

    @Test
    public void calibratedVEScalesByMpgRatioWithinBounds() {
        assertEquals(93.5f, FuelEconomyFormat.calibratedVE(85f, 27.5f, 25f), 0.001f);
        assertEquals("capped at 130", 130f, FuelEconomyFormat.calibratedVE(85f, 60f, 25f), 0.001f);
        assertEquals("floored at 50", 50f, FuelEconomyFormat.calibratedVE(85f, 5f, 25f), 0.001f);
    }

    @Test
    public void infoMessageNamesTheMethodInUse() {
        String map = FuelEconomyFormat.infoMessage(true, false);
        assertTrue(map.contains("Method: MAP-based Speed-Density\nAccuracy: ~88% (uncalibrated)\nCalibrated: No"));
        assertTrue(map.contains("TIP: Use the calibration tool"));

        String calibrated = FuelEconomyFormat.infoMessage(true, true);
        assertTrue(calibrated.contains("Accuracy: ~95% (calibrated)\nCalibrated: Yes ✓"));
        assertFalse(calibrated.contains("TIP:"));

        String estimate = FuelEconomyFormat.infoMessage(false, false);
        assertTrue(estimate.contains("Method: RPM/Load Estimation\nAccuracy: ~65%"));
        assertTrue(estimate.contains("doesn't have MAP/IAT sensors"));
        assertTrue(estimate.endsWith("Live Data is active for accurate readings."));
    }
}
