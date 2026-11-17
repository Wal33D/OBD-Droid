package com.obddroid.ecu;

import java.util.Locale;

/**
 * Definition of a single OBD failure code
 *

 */
public class ObdCodeItem extends EcuCodeItem
{
	/**
	 *
	 */
	private static final long serialVersionUID = -3976920283943009811L;

	/** code types */
	private static final String codeTypes = "PCBU";

	/** Creates a new instance of ObdCodeItem */
	public ObdCodeItem()
	{
		setKeyAttribute(FIELDS[0]);
	}

	/**
	 * Creates a new instance of ObdCodeItem
	 *
	 * @param code        String representation of DFC
	 * @param description descriptive text of DFC
	 */
	public ObdCodeItem(String code, String description)
	{
		setKeyAttribute(FIELDS[0]);
		put(FID_CODE, code);
		put(FID_DESCRIPT, description);
	}

	/**
	 * Creates a new instance of ObdCodeItem
	 *
	 * @param numericCode numeric code ID
	 * @param description descriptive text of DFC
	 */
	public ObdCodeItem(int numericCode, String description)
	{
		setKeyAttribute(FIELDS[0]);
		put(FID_CODE, getPCode(numericCode));
		put(FID_DESCRIPT, description);
	}

	/**
	 * Return numeric code representation from P/C/B/U-Code String
	 *
	 * @param pCode P/C/B/U-Code String
	 * @return numeric code value for corresponding code
	 */
	protected static int getNumericCode(String pCode)
	{
		// get code number
		int numCode = Integer.valueOf(pCode.substring(1), 16).intValue();
		int typIdx = codeTypes.indexOf(pCode.charAt(0));
		numCode |= (typIdx << 14);

		return (numCode);
	}

	/**
	 * Return P/C/B/U-Code String representation from numeric code
	 *
	 * @param numericCode numeric code value for corresponding code
	 * @return P/C/B/U-Code String
	 */
	static String getPCode(int numericCode)
	{
		int codeType = numericCode >> 14;
		int codeVal = numericCode & 0x3FFF;
		// formatted locally: ProtoHeader's padding char is a global that is ' ' until an ObdProt exists (obd-i4t)
		return String.format(Locale.ROOT, "%c%04X", codeTypes.charAt(codeType), codeVal);
	}
}
