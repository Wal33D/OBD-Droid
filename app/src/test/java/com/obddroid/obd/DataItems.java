package com.obddroid.obd;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.obddroid.ecu.EcuDataItem;
import com.obddroid.ecu.EcuDataPv;

import java.util.Vector;

/** Looks up decoded values in the protocol's shared data items (ObdProt.dataItems, from pids.csv). */
final class DataItems
{
	private DataItems() { }

	static EcuDataItem item(int service, int pid, String mnemonic)
	{
		Vector<EcuDataItem> items = ObdProt.dataItems.getPidDataItems(service, pid);
		assertNotNull(String.format("no data items for service %02X PID %02X", service, pid), items);
		for (EcuDataItem item : items)
		{
			if (mnemonic.equals(item.getMnemonic())) { return item; }
		}
		throw new AssertionError(String.format("no item %s for service %02X PID %02X", mnemonic, service, pid));
	}

	static Object value(int service, int pid, String mnemonic)
	{
		return item(service, pid, mnemonic).pv.get(EcuDataPv.FID_VALUE);
	}

	static float number(int service, int pid, String mnemonic)
	{
		Object value = value(service, pid, mnemonic);
		assertTrue(mnemonic + " is not numeric: " + value, value instanceof Number);
		return ((Number) value).floatValue();
	}
}
