package com.obddroid.ui.components;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.obddroid.utils.VehicleData;

import org.junit.Test;

public class VehicleInfoFormatTest {

    private static final String TEST_VIN = "1HGBH41JXMN109186";

    @Test
    public void connectionLookForEachLinkState() {
        VehicleInfoFormat.ConnectionLook none = VehicleInfoFormat.connectionLook(false, true);
        assertEquals("?", none.iconText);
        assertEquals("No Vehicle Connected", none.title);
        assertEquals("Waiting for OBD connection...", none.status);
        assertEquals(VehicleInfoFormat.GREY, none.statusColor);

        VehicleInfoFormat.ConnectionLook waiting = VehicleInfoFormat.connectionLook(true, false);
        assertEquals("OBD", waiting.iconText);
        assertEquals("ECU Connected", waiting.title);
        assertEquals("Retrieving vehicle information...", waiting.status);
        assertEquals(VehicleInfoFormat.CYAN, waiting.statusColor);

        VehicleInfoFormat.ConnectionLook noMode9 = VehicleInfoFormat.connectionLook(true, true);
        assertEquals("OBD", noMode9.iconText);
        assertEquals("Vehicle Info not available (Mode 9 not supported)", noMode9.status);
        assertEquals(VehicleInfoFormat.ORANGE, noMode9.statusColor);
    }

    @Test
    public void headlinePutsTheYearFirstAndSkipsMissingParts() {
        VehicleData data = new VehicleData();
        data.make = "HONDA";
        data.model = "Civic";
        data.modelYear = "2015";
        assertEquals("2015 HONDA Civic", VehicleInfoFormat.headline(data));

        data.model = "";
        assertEquals("2015 HONDA", VehicleInfoFormat.headline(data));

        VehicleData yearOnly = new VehicleData();
        yearOnly.modelYear = "2015";
        assertEquals("2015 ", VehicleInfoFormat.headline(yearOnly));
    }

    @Test
    public void statusLineAddsTheVinTail() {
        assertEquals("2.0L 4-cyl • VIN: 109186", VehicleInfoFormat.statusLine("2.0L 4-cyl", TEST_VIN));
        assertEquals("Connected • VIN: 109186", VehicleInfoFormat.statusLine(null, TEST_VIN));
        assertEquals("Connected", VehicleInfoFormat.statusLine("", "12345"));
    }

    @Test
    public void vinFromMode9NeedsTheDescriptionAndSeventeenCharacters() {
        assertEquals(TEST_VIN, VehicleInfoFormat.vinFromMode9("Vehicle Identification Number", " " + TEST_VIN + " "));
        assertNull(VehicleInfoFormat.vinFromMode9("Vehicle Identification Number", "1HGBH41J"));
        assertNull(VehicleInfoFormat.vinFromMode9("ECU name", TEST_VIN));
        assertNull(VehicleInfoFormat.vinFromMode9(null, TEST_VIN));
        assertNull(VehicleInfoFormat.vinFromMode9("Vehicle Identification Number", null));
    }

    @Test
    public void vinFieldsSplitIntoTheVinAndItsExtras() {
        assertTrue(VehicleInfoFormat.isMode9Vin("Vehicle Identification Number"));
        assertFalse(VehicleInfoFormat.isExtraVinField("Vehicle Identification Number"));
        assertFalse(VehicleInfoFormat.isExtraVinField("Number of VIN items"));
        assertFalse(VehicleInfoFormat.isExtraVinField("Message count VIN"));
        assertFalse(VehicleInfoFormat.isExtraVinField("Calibration identifier"));
        assertTrue(VehicleInfoFormat.isExtraVinField("VIN check"));
    }

    @Test
    public void onlyIgnitionAndMonitoringCountersAreSystemCounters() {
        assertTrue(VehicleInfoFormat.isSystemCounter("Ignition Counter"));
        assertTrue(VehicleInfoFormat.isSystemCounter("IGNCNTR"));
        assertFalse("\"Monitoring\" matches the emission-monitor rule first",
                VehicleInfoFormat.isSystemCounter("OBD Monitoring Conditions Encountered Counts"));

        assertFalse("VIN data is shown under Vehicle Identification",
                VehicleInfoFormat.isSystemCounter("Vehicle Identification Number"));
        assertFalse("protocol counts are hidden", VehicleInfoFormat.isSystemCounter("Message count VIN"));
        assertFalse("emission monitors are hidden",
                VehicleInfoFormat.isSystemCounter("Catalyst Monitor Completion Counts Bank 1"));
        assertFalse("calibration data is on the ECU page", VehicleInfoFormat.isSystemCounter("Calibration identifier"));
        assertFalse(VehicleInfoFormat.isSystemCounter("ECU name"));
    }

    @Test
    public void manufacturerLosesCorporateSuffixes() {
        assertEquals("AMERICAN HONDA MOTOR CO.",
                VehicleInfoFormat.manufacturerName("AMERICAN HONDA MOTOR CO., INC."));
        assertEquals("TOYOTA MOTOR MANUFACTURING",
                VehicleInfoFormat.manufacturerName("TOYOTA MOTOR MANUFACTURING OF NORTH AMERICA"));
        assertEquals("FORD MOTOR COMPANY", VehicleInfoFormat.manufacturerName("FORD MOTOR COMPANY"));
        assertNull(VehicleInfoFormat.manufacturerName(null));
    }

    @Test
    public void bodyClassAndVehicleTypeAreShortened() {
        assertEquals("Sport Utility Vehicle (SUV)",
                VehicleInfoFormat.bodyClass("Sport Utility Vehicle (SUV)/Multi-Purpose Vehicle (MPV)"));
        assertEquals("Sedan", VehicleInfoFormat.bodyClass("Sedan"));
        assertNull(VehicleInfoFormat.bodyClass(null));

        assertEquals("MULTIPURPOSE PASSENGER VEHICLE \n(MPV)",
                VehicleInfoFormat.vehicleType("MULTIPURPOSE PASSENGER VEHICLE (MPV)"));
        assertEquals("TRUCK", VehicleInfoFormat.vehicleType("TRUCK"));
    }

    @Test
    public void vehicleEmojiFollowsTheBodyClass() {
        assertEquals("🚙", VehicleInfoFormat.vehicleEmoji("Sport Utility Vehicle (SUV)"));
        assertEquals("🛻", VehicleInfoFormat.vehicleEmoji("Pickup"));
        assertEquals("🚐", VehicleInfoFormat.vehicleEmoji("Minivan"));
        assertEquals("🚗", VehicleInfoFormat.vehicleEmoji("Sedan/Saloon"));
        assertEquals("🏎️", VehicleInfoFormat.vehicleEmoji("Coupe"));
        assertEquals("🏍️", VehicleInfoFormat.vehicleEmoji("Motorcycle - Street"));
        assertEquals("unknown classes get a car", "🚗", VehicleInfoFormat.vehicleEmoji("Limousine"));
        assertNull(VehicleInfoFormat.vehicleEmoji(null));
    }
}
