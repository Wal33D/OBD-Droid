package com.obddroid.obd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.obddroid.ecu.EcuCodeItem;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * ElmProt driven by recorded ELM327 transcripts (test resources /elm/*.txt): AT initialisation,
 * protocol search and ECU detection, NO DATA / SEARCHING / bus and adapter errors, and ISO-TP and
 * ISO 9141 multi-line reassembly. No adapter, phone, emulator or car is involved.
 */
public class ElmProtTranscriptTest
{
	private static final String TEST_VIN = "1HGBH41JXMN109186";

	private ElmTranscript t;

	@Before
	public void setUp()
	{
		ElmTranscript.resetSharedState();
		t = new ElmTranscript();
	}

	@Test
	public void atInitialisationAndCanEcuDetection() throws Exception
	{
		t.play("can11_detect.txt").assertNoUnexpectedTx();

		assertEquals(ElmProt.STAT.ECU_DETECTED, t.elm.getStatus());
		assertEquals(new HashSet<>(Arrays.asList(0x7E8, 0x7E9)), ecuAddressesReported());
	}

	@Test
	public void iso9141EcuAddressIsTheSenderByte() throws Exception
	{
		t.play("iso9141_vin.txt").assertNoUnexpectedTx();

		assertEquals(new HashSet<>(Arrays.asList(0x10)), ecuAddressesReported());
	}

	@Test
	public void atzDisabledFallsBackToAti() throws Exception
	{
		ElmProt.disableCommands(new HashSet<>(Arrays.asList("ATZ")));
		t.playText("ati", "@reset\nTX ATI\n").assertNoUnexpectedTx();
	}

	@Test
	public void pidDiscoveryNoDataAndFirstLiveValues() throws Exception
	{
		t.play("can11_pid_discovery.txt").assertNoUnexpectedTx();

		// supported PIDs from the three bitmask blocks (both ECUs merged) get process variables
		for (String key : new String[]{"01.0.0", "05.0.0", "0C.0.0", "0D.0.0", "1F.0.0", "2F.0.0", "4C.0.0", "58.0.0"})
		{
			assertTrue("PID item " + key + " missing", ObdProt.PidPvs.containsKey(key));
		}
		// ... unsupported ones do not
		for (String key : new String[]{"02.0.0", "0A.0.0", "10.0.0", "22.0.0", "48.0.0"})
		{
			assertFalse("PID item " + key + " present", ObdProt.PidPvs.containsKey(key));
		}

		assertEquals(2f, DataItems.number(1, 0x01, "number_fault_codes"), 0f);
		assertEquals(1f, DataItems.number(1, 0x01, "status_mil"), 0f);
		// PID 01 byte A goes out raw (MIL bit included); the UI masks it
		assertEquals(0x82, t.elm.getNumCodes());
		assertEquals(95f, DataItems.number(1, 0x05, "engine_coolant_temperature"), 0.001f);
	}

	@Test
	public void canErrorBusInitErrorAndUnableToConnectReopenTheProtocol() throws Exception
	{
		t.play("can11_bus_errors.txt").assertNoUnexpectedTx();
		assertEquals(ElmProt.STAT.CONNECTED, t.elm.getStatus());
	}

	@Test
	public void dataErrorWarmStartsTheAdapter() throws Exception
	{
		t.play("can11_adapter_errors.txt").assertNoUnexpectedTx();
	}

	@Test
	public void bufferFullRxErrorAndErrorWarmStartTheAdapter() throws Exception
	{
		String[][] cases = {
			{"BUFFER FULL", "RXERROR"},
			{"<RX ERROR", "RXERROR"},
			{"ERROR", "ERROR"},
		};
		for (String[] c : cases)
		{
			setUp();
			t.play("can11_detect.txt");
			t.playText(c[0], "@service 01\nTX 0100\nRX " + c[0] + "\n>\n@status " + c[1] + "\nTX ATWS\n")
			 .assertNoUnexpectedTx();
		}
	}

	@Ignore("obd-54d: after ATWS the stale error in lastRxMsg sends ATWS again instead of re-initialising")
	@Test
	public void warmStartBannerRunsTheAtInitialisationAgain() throws Exception
	{
		t.play("can11_adapter_errors.txt");
		t.playText("after ATWS", "RX\nRX ELM327 v1.5\n>\n@status INITIALIZING\nTX ATE0\n")
		 .assertNoUnexpectedTx();
	}

	@Test
	public void stoppedRequeuesTheLastCommand() throws Exception
	{
		t.play("can11_detect.txt");
		t.playText("stopped", "@service 01\nTX 0100\nRX STOPPED\n>\nTX 0100\n").assertNoUnexpectedTx();
	}

	@Test
	public void unknownCommandIsSkipped() throws Exception
	{
		t.play("can11_detect.txt");
		// '?' is ignored; with nothing queued and no service active nothing more is sent
		t.playText("qmark", "RX ?\n>\n@status ECU_DETECTED\n").assertNoUnexpectedTx();
	}

	@Test
	public void canIsoTpVinIsReassembled() throws Exception
	{
		t.play("can11_vin.txt").assertNoUnexpectedTx();
		assertEquals(TEST_VIN, DataItems.value(9, 0x02, "vehicle_identification_number"));
		assertEquals(1f, DataItems.number(9, 0x02, "vin_numitems"), 0f);
	}

	@Test
	public void iso9141MultiLineVinIsReassembled() throws Exception
	{
		t.play("iso9141_vin.txt").assertNoUnexpectedTx();
		assertEquals(TEST_VIN, DataItems.value(9, 0x02, "vehicle_identification_number"));
	}

	@Test
	public void storedPendingAndPermanentCodesAreRead() throws Exception
	{
		t.play("can11_dtcs.txt").assertNoUnexpectedTx();

		assertCode(0x0301, "P0301", ObdProt.OBD_SVC_READ_CODES);
		assertCode(0xC123, "U0123", ObdProt.OBD_SVC_READ_CODES);
		assertCode(0x0420, "P0420", ObdProt.OBD_SVC_PENDINGCODES);
	}

	private static void assertCode(int key, String code, int service)
	{
		EcuCodeItem pv = (EcuCodeItem) ObdProt.tCodes.get(key);
		assertNotNull("code " + code + " not read", pv);
		assertEquals(code, pv.get(EcuCodeItem.FID_CODE));
		assertEquals(service, pv.get(EcuCodeItem.FID_STATUS));
	}

	@SuppressWarnings("unchecked")
	private Set<Integer> ecuAddressesReported()
	{
		Set<Integer> result = null;
		for (PropertyChangeEvent e : t.events)
		{
			if (ElmProt.PROP_ECU_ADDRESS.equals(e.getPropertyName()))
			{
				result = new HashSet<>((Collection<Integer>) e.getNewValue());
			}
		}
		assertNotNull("no ECU address event", result);
		return result;
	}
}
