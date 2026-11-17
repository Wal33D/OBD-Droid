package com.obddroid.ecu;

import static org.junit.Assert.assertEquals;

import com.obddroid.common.ProtoHeader;

import org.junit.Test;

import java.lang.reflect.Field;

/** Two-byte DTC to text: the top two bits pick P/C/B/U, the remaining 14 bits are the hex digits (SAE J2012). */
public class ObdCodeItemTest
{
	private static String code(int numeric)
	{
		return (String) new ObdCodeItem(numeric, "").get(EcuCodeItem.FID_CODE);
	}

	@Test
	public void systemLetterComesFromTheTopTwoBits()
	{
		assertEquals("P0301", code(0x0301));
		assertEquals("C0300", code(0x4300));
		assertEquals("B0123", code(0x8123));
		assertEquals("U0123", code(0xC123));
	}

	@Test
	public void secondDigitCoversManufacturerAndReservedRanges()
	{
		assertEquals("P1234", code(0x1234));
		assertEquals("P2000", code(0x2000));
		assertEquals("P3FFF", code(0x3FFF));
		assertEquals("U3FFF", code(0xFFFF));
	}

	/** ProtoHeader.paddingChr is ' ' until an ObdProt is built; codes must not depend on it (obd-i4t). */
	@Test
	public void zeroPaddedBeforeAnyProtocolExists() throws Exception
	{
		Field padding = ProtoHeader.class.getDeclaredField("paddingChr");
		padding.setAccessible(true);
		Object saved = padding.get(null);
		padding.set(null, ' ');
		try
		{
			assertEquals("P0301", code(0x0301));
			assertEquals("P0000", code(0x0000));
			assertEquals(0x0301, ObdCodeItem.getNumericCode(code(0x0301)));
		} finally
		{
			padding.set(null, saved);
		}
	}

	@Test
	public void numericCodeRoundTrips()
	{
		for (int numeric : new int[]{0x0100, 0x0420, 0x4300, 0x8123, 0xC123, 0x3FFF})
		{
			assertEquals(numeric, ObdCodeItem.getNumericCode(code(numeric)));
		}
	}
}
