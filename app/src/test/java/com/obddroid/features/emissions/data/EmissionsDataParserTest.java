package com.obddroid.features.emissions.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Readiness interpretation of the decoded Mode 01 PID 01 values. The raw values are what pids.csv
 * produces (see ObdProtDecodeTest): continuous monitors 0x01 = supported, 0x10 = incomplete;
 * non-continuous monitors 0x100 = supported (byte C), 0x001 = incomplete (byte D).
 */
public class EmissionsDataParserTest
{
	@Test
	public void readinessLabelsMapToMonitorKeys()
	{
		assertEquals("MISFIRE", EmissionsDataParser.parseMonitorKeyFromDescription("Misfire status"));
		assertEquals("FUEL", EmissionsDataParser.parseMonitorKeyFromDescription("Fuel system test status"));
		assertEquals("CCM", EmissionsDataParser.parseMonitorKeyFromDescription("Component test status"));
		assertEquals("CATALYST", EmissionsDataParser.parseMonitorKeyFromDescription("Catalyst test status"));
		assertEquals("EVAP", EmissionsDataParser.parseMonitorKeyFromDescription("Evaporative system test status"));
		assertEquals("O2HEATER", EmissionsDataParser.parseMonitorKeyFromDescription("Oxygen sensor heater test status"));
		assertEquals("O2SENSOR", EmissionsDataParser.parseMonitorKeyFromDescription("Oxygen sensor test status"));
		assertEquals("EGR", EmissionsDataParser.parseMonitorKeyFromDescription("EGR system test status"));
		assertEquals("AIR", EmissionsDataParser.parseMonitorKeyFromDescription("Secondary air system test status"));
	}

	@Test
	public void sensorPidsAreNotReadinessMonitors()
	{
		assertNull(EmissionsDataParser.parseMonitorKeyFromDescription("Oxygen sensors present"));
		assertNull(EmissionsDataParser.parseMonitorKeyFromDescription("Commanded evaporative purge"));
		assertNull(EmissionsDataParser.parseMonitorKeyFromDescription(null));
	}

	@Test
	public void continuousMonitors()
	{
		MonitorData m = parse("MISFIRE", 0x01, 0);
		assertTrue(m.isAvailable);
		assertTrue(m.isReady);

		m = parse("FUEL", 0x11, 0);
		assertTrue(m.isAvailable);
		assertFalse(m.isReady);

		m = parse("CCM", 0x00, 0);
		assertFalse(m.isAvailable);
		assertFalse(m.isReady);
	}

	@Test
	public void supportedButIncompleteNonContinuousMonitorIsNotReady()
	{
		MonitorData m = parse("EVAP", 0x101, 0);
		assertTrue(m.isAvailable);
		assertFalse(m.isReady);
	}

	@Ignore("obd-akd: non-continuous branch treats 0x100 (supported) as 'incomplete' and ignores 0x001")
	@Test
	public void nonContinuousMonitorsFollowJ1979()
	{
		// supported and complete, no IUMPR data needed
		MonitorData m = parse("CATALYST", 0x100, 0);
		assertTrue(m.isAvailable);
		assertTrue(m.isReady);

		// not supported, whatever IUMPR says
		m = parse("EGR", 0x000, 5);
		assertFalse(m.isAvailable);
		assertFalse(m.isReady);
	}

	@Test
	public void textStatusFromTheLiveDataList()
	{
		MonitorData m = new MonitorData("CATALYST");
		EmissionsDataParser.parseMonitorStatus("CATALYST", m,
			"(*) Available" + System.lineSeparator() + "(*) Complete", "Catalyst test status");
		assertTrue(m.isAvailable);
		assertTrue(m.isReady);

		m = new MonitorData("EVAP");
		EmissionsDataParser.parseMonitorStatus("EVAP", m,
			"(*) Available" + System.lineSeparator() + "(  ) Incomplete", "Evaporative system test status");
		assertTrue(m.isAvailable);
		assertFalse(m.isReady);
	}

	private static MonitorData parse(String key, int raw, int iumprConditions)
	{
		MonitorData m = new MonitorData(key);
		m.conditions = iumprConditions;
		EmissionsDataParser.parseMonitorStatus(key, m, raw, key);
		return m;
	}
}
