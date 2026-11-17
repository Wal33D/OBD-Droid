package com.obddroid.ecu;

import static org.junit.Assert.assertEquals;

import com.obddroid.obd.ElmProt;

import org.junit.BeforeClass;
import org.junit.Test;

/** Two-byte DTC to text: the top two bits pick P/C/B/U, the remaining 14 bits are the hex digits (SAE J2012). */
public class ObdCodeItemTest
{
	/**
	 * getPCode zero-pads through ProtoHeader.paddingChr, which only ObdProt's constructor sets to '0'
	 * (obd-i4t); the app always has a protocol instance by the time codes are decoded.
	 */
	@BeforeClass
	public static void protocolExists()
	{
		new ElmProt();
	}

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

	@Test
	public void numericCodeRoundTrips()
	{
		for (int numeric : new int[]{0x0100, 0x0420, 0x4300, 0x8123, 0xC123, 0x3FFF})
		{
			assertEquals(numeric, ObdCodeItem.getNumericCode(code(numeric)));
		}
	}
}
