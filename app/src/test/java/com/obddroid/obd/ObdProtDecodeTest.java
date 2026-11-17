package com.obddroid.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.obddroid.ecu.Conversion;
import com.obddroid.ecu.EcuCodeItem;
import com.obddroid.ecu.EcuDataItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * OBD response decoding in ObdProt, fed the hex strings ElmProt hands it (spaces stripped, headers
 * off): Mode 01 scaling for the common PIDs, PID 01 readiness bits, Mode 03/07/0A trouble codes
 * with and without a count byte, Mode 09 VIN, and negative responses. Values follow SAE J1979;
 * raw bytes for 05/0C/0D and the supported-PID masks come from the obdsim firmware mode handlers.
 */
public class ObdProtDecodeTest
{
	private ObdProt obd;
	private final List<PropertyChangeEvent> events = new ArrayList<>();

	@Before
	public void setUp()
	{
		ElmTranscript.resetSharedState();
		obd = new ObdProt();
		obd.reset();
		obd.addPropertyChangeListener(events::add);
		EcuDataItem.cnvSystem = EcuDataItem.SYSTEM_METRIC;
	}

	@After
	public void restoreUnits()
	{
		EcuDataItem.cnvSystem = EcuDataItem.SYSTEM_METRIC;
	}

	private void rx(String hex)
	{
		obd.handleTelegram(hex.replace(" ", "").toCharArray());
	}

	// ---- Mode 01 scaling ------------------------------------------------------------------

	@Test
	public void engineSpeedIsQuarterRpm()
	{
		rx("41 0C 1A F8");          // 0x1AF8 / 4
		assertEquals(1726f, DataItems.number(1, 0x0C, "engine_speed"), 0.001f);
		rx("41 0C 00 00");
		assertEquals(0f, DataItems.number(1, 0x0C, "engine_speed"), 0.001f);
		rx("41 0C FF FF");
		assertEquals(16383.75f, DataItems.number(1, 0x0C, "engine_speed"), 0.001f);
	}

	@Test
	public void vehicleSpeedIsKmhAndConvertsToMph()
	{
		rx("41 0D 50");
		assertEquals(80f, DataItems.number(1, 0x0D, "vehicle_speed"), 0.001f);

		EcuDataItem.cnvSystem = EcuDataItem.SYSTEM_IMPERIAL;
		rx("41 0D 50");
		assertEquals(80f * 1000 / 1609, DataItems.number(1, 0x0D, "vehicle_speed"), 0.001f);
	}

	@Test
	public void coolantTemperatureIsOffsetBy40()
	{
		rx("41 05 87");              // obdsim: 95 degC
		assertEquals(95f, DataItems.number(1, 0x05, "engine_coolant_temperature"), 0.001f);
		rx("41 05 00");
		assertEquals(-40f, DataItems.number(1, 0x05, "engine_coolant_temperature"), 0.001f);
		rx("41 05 FF");
		assertEquals(215f, DataItems.number(1, 0x05, "engine_coolant_temperature"), 0.001f);

		EcuDataItem.cnvSystem = EcuDataItem.SYSTEM_IMPERIAL;
		rx("41 05 87");
		assertEquals(203f, DataItems.number(1, 0x05, "engine_coolant_temperature"), 0.001f);
	}

	@Test
	public void fuelLevelIsPercentOf255()
	{
		rx("41 2F 80");
		assertEquals(128f * 100 / 255, DataItems.number(1, 0x2F, "fuel_level"), 0.001f);
		rx("41 2F FF");
		assertEquals(100f, DataItems.number(1, 0x2F, "fuel_level"), 0.001f);
		rx("41 2F 00");
		assertEquals(0f, DataItems.number(1, 0x2F, "fuel_level"), 0.001f);
	}

	@Test
	public void runTimeSinceStartIsSecondsShownAsHours()
	{
		rx("41 1F 0E 10");           // 3600 s
		assertEquals(1f, DataItems.number(1, 0x1F, "running_time"), 0.0001f);
		rx("41 1F FF FF");           // 65535 s
		assertEquals(65535f / 3600, DataItems.number(1, 0x1F, "running_time"), 0.001f);
		assertEquals("h", DataItems.item(1, 0x1F, "running_time").pv.getUnits());
	}

	// ---- PID 01 readiness -----------------------------------------------------------------

	/**
	 * A=82: MIL on, 2 codes. B=07: misfire, fuel and component monitors supported, none incomplete.
	 * C=65: catalyst, EVAP, O2 sensor, O2 heater supported. D=04: EVAP incomplete.
	 */
	private static final String PID01 = "41 01 82 07 65 04";

	@Test
	public void milAndCodeCount()
	{
		rx(PID01);
		assertEquals(2f, DataItems.number(1, 0x01, "number_fault_codes"), 0f);
		assertEquals(1f, DataItems.number(1, 0x01, "status_mil"), 0f);
		assertEquals(0x82, obd.getNumCodes());

		rx("41 01 00 07 65 00");
		assertEquals(0f, DataItems.number(1, 0x01, "number_fault_codes"), 0f);
		assertEquals(0f, DataItems.number(1, 0x01, "status_mil"), 0f);
	}

	@Test
	public void continuousMonitorBitsAreAvailableAndIncompleteNibbles()
	{
		rx(PID01);
		// value = (B >> n) & 0x11: bit 0 = supported (B bit n), bit 4 = incomplete (B bit n+4)
		assertEquals(0x01, raw("status_misfires"));
		assertEquals(0x01, raw("status_fuel_system"));
		assertEquals(0x01, raw("status_component_test"));
		assertReadiness("status_misfires", true, true);

		rx("41 01 00 17 00 00");     // misfire supported but incomplete
		assertEquals(0x11, raw("status_misfires"));
		assertReadiness("status_misfires", true, false);

		rx("41 01 00 06 00 00");     // misfire not supported
		assertReadiness("status_misfires", false, false);
	}

	@Test
	public void nonContinuousMonitorBitsAreAvailableAndIncompleteBytes()
	{
		rx(PID01);
		// value = ((C << 8 | D) >> n) & 0x101: 0x100 = supported (C bit n), 0x001 = incomplete (D bit n)
		assertEquals(0x100, raw("status_catalyst_test"));
		assertEquals(0x101, raw("status_evaporative_system_test"));
		assertEquals(0x000, raw("status_secondary_air_system_test"));
		assertEquals(0x100, raw("status_oxygen_sensor_test"));
		assertEquals(0x100, raw("status_oxygen_sensor_heater_test"));
		assertEquals(0x000, raw("status_egr_system_test"));

		assertReadiness("status_catalyst_test", true, true);
		assertReadiness("status_evaporative_system_test", true, false);
		assertReadiness("status_secondary_air_system_test", false, false);
		assertReadiness("status_egr_system_test", false, false);
	}

	private static int raw(String mnemonic)
	{
		return (int) DataItems.number(1, 0x01, mnemonic);
	}

	/** What the live-data list shows for a monitor (TestStatus conversion text). */
	private static void assertReadiness(String mnemonic, boolean available, boolean complete)
	{
		EcuDataItem item = DataItems.item(1, 0x01, mnemonic);
		Conversion cnv = item.getConversions()[EcuDataItem.cnvSystem];
		String text = cnv.physToPhysFmtString((Number) item.pv.get(com.obddroid.ecu.EcuDataPv.FID_VALUE),
		                                      item.getFormat());
		String expected = (available ? "(*) Available" : "(  ) Not available")
		                  + System.lineSeparator()
		                  + (!available ? "(  ) N/A" : complete ? "(*) Complete" : "(  ) Incomplete");
		assertEquals(mnemonic, expected, text);
	}

	// ---- Mode 03 / 07 / 0A trouble codes --------------------------------------------------

	@Test
	public void canStoredCodesWithCountByte()
	{
		rx("43 03 01 00 43 00 81 23");
		assertEquals(3, obd.getNumCodes());
		assertCode(0x0100, "P0100", 0x03);
		assertCode(0x4300, "C0300", 0x03);
		assertCode(0x8123, "B0123", 0x03);
	}

	@Test
	public void nonCanStoredCodesWithoutCountBytePaddedWithZeros()
	{
		// J1850 / ISO 9141 / KWP: three code slots per line, unused slots are 0000
		rx("43 01 00 03 01 00 00");
		assertCode(0x0100, "P0100", 0x03);
		assertCode(0x0301, "P0301", 0x03);
		assertNull(ObdProt.tCodes.get(0x0000));
	}

	@Test
	public void pendingAndPermanentCodesKeepTheirService()
	{
		rx("47 01 04 20");
		rx("4A 01 C1 23");
		assertCode(0x0420, "P0420", 0x07);
		assertCode(0xC123, "U0123", 0x0A);
	}

	@Test
	public void noCodesGivesThePlaceholderEntry()
	{
		rx("43 00");
		assertEquals(0, obd.getNumCodes());
		EcuCodeItem pv = (EcuCodeItem) ObdProt.tCodes.get(0);
		assertNotNull(pv);
		assertEquals(Messages.getString("no.trouble.codes.set"), pv.get(EcuCodeItem.FID_DESCRIPT));
	}

	private static void assertCode(int key, String code, int service)
	{
		EcuCodeItem pv = (EcuCodeItem) ObdProt.tCodes.get(key);
		assertNotNull("code " + code + " not decoded", pv);
		assertEquals(code, pv.get(EcuCodeItem.FID_CODE));
		assertEquals(service, pv.get(EcuCodeItem.FID_STATUS));
	}

	// ---- Mode 09 --------------------------------------------------------------------------

	@Test
	public void vinFromCanPayload()
	{
		rx("49 02 01 31 48 47 42 48 34 31 4A 58 4D 4E 31 30 39 31 38 36");
		assertEquals("1HGBH41JXMN109186", DataItems.value(9, 0x02, "vehicle_identification_number"));
	}

	@Test
	public void vinWithLeadingZeroPaddingFromIsoPayload()
	{
		// ISO 9141 frames carry 4 bytes each, so the 17 VIN bytes are front-padded with 00 00 00
		rx("49 02 01 00 00 00 31 48 47 42 48 34 31 4A 58 4D 4E 31 30 39 31 38 36");
		assertEquals("1HGBH41JXMN109186", DataItems.value(9, 0x02, "vehicle_identification_number"));
	}

	// ---- Negative responses ---------------------------------------------------------------

	@Test
	public void negativeResponseIsReportedAndCancelsTheService()
	{
		obd.setService(ObdProt.OBD_SVC_DATA, true);
		rx("7F 01 22");                // conditions not correct -> CANCEL
		assertEquals(ObdProt.OBD_SVC_NONE, obd.getService());
		PropertyChangeEvent nrc = lastEvent(ObdProt.PROP_NRC);
		assertEquals(ObdProt.NRC.CNC, nrc.getOldValue());
		assertTrue(String.valueOf(nrc.getNewValue()), String.valueOf(nrc.getNewValue()).contains("Conditions not correct"));
	}

	@Test
	public void requestOutOfRangeIsSkippedNotCancelled()
	{
		obd.setService(ObdProt.OBD_SVC_DATA, true);
		rx("7F 01 31");
		assertEquals(ObdProt.OBD_SVC_DATA, obd.getService());
		assertEquals(ObdProt.NRC.ROOR, lastEvent(ObdProt.PROP_NRC).getOldValue());
	}

	private PropertyChangeEvent lastEvent(String name)
	{
		PropertyChangeEvent result = null;
		for (PropertyChangeEvent e : events)
		{
			if (name.equals(e.getPropertyName())) { result = e; }
		}
		assertNotNull("no " + name + " event", result);
		return result;
	}
}
