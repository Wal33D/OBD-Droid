package com.obddroid.obd;

import com.obddroid.can.CanProtFord;
import com.obddroid.ecu.ObdCodeItem;
import com.obddroid.interfaces.RawTelegramListener;
import com.obddroid.interfaces.TelegramListener;
import com.obddroid.interfaces.TelegramWriter;

import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;


/**
 * Communication protocol to talk to a ELM327 OBD interface
 *

 */
public class ElmProt
	extends ObdProt
	implements TelegramListener, TelegramWriter, Runnable
{
	/**
	 * virtual OBD service for CAN monitoring
	 */
	public static final int OBD_SVC_CAN_MONITOR = 256;
	
	/**
	 * property name for ECU addresses
	 */
	public static final String PROP_ECU_ADDRESS = "ecuaddr";
	/**
	 * property name for protocol status
	 */
	public static final String PROP_STATUS = "status";
	
	/**
	 * CAN protocol handler
	 */
	public static final CanProtFord canProt = new CanProtFord();
	/**
	 * Adaptive timing handler
	 */
	public final AdaptiveTiming mAdaptiveTiming = new AdaptiveTiming();
	
	/**
	 * number of bytes expected from opponent
	 */
	private int charsExpected = 0;
	/**
	 * remember last command which was sent
	 */
	private char[] lastCommand;
	
	/**
	 * preferred ELM protocol to be selected
	 */
	static private PROT preferredProtocol = PROT.ELM_PROT_AUTO;
	
	/**
	 * list of identified ECU addresses
	 */
	private final TreeSet<Integer> ecuAddresses = new TreeSet<Integer>();
	/**
	 * selected ECU address
	 */
	private int selectedEcuAddress = 0;
	/**
	 * list of raw telegram listeners (receive data BEFORE header stripping)
	 */
	@SuppressWarnings("rawtypes")
	private final Vector rawTelegramListeners = new Vector();
	/**
	 * custom ELM initialisation commands
	 */
	private final Vector<String> customInitCommands = new Vector<String>();
	/**
	 * last detected message counter ID
	 */
	private int lastMsgId = 0;

	/**
	 * ELM protocol ID's
	 */
	public enum PROT
	{
		ELM_PROT_AUTO("Automatic"),
		ELM_PROT_J1850PWM("SAE J1850 PWM (41.6 KBaud)"),
		ELM_PROT_J1850VPW("SAE J1850 VPW (10.4 KBaud)"),
		ELM_PROT_9141_2("ISO 9141-2 (5 Baud Init)"),
		ELM_PROT_14230_4("ISO 14230-4 KWP (5 Baud Init)"),
		ELM_PROT_14230_4F("ISO 14230-4 KWP (fast Init)"),
		ELM_PROT_15765_11_F("ISO 15765-4 CAN (11 Bit ID, 500 KBit)"),
		ELM_PROT_15765_29_F("ISO 15765-4 CAN (29 Bit ID, 500 KBit)"),
		ELM_PROT_15765_11_S("ISO 15765-4 CAN (11 Bit ID, 250 KBit)"),
		ELM_PROT_15765_29_S("ISO 15765-4 CAN (29 Bit ID, 250 KBit)"),
		ELM_PROT_J1939_29_S("SAE J1939 CAN (29 bit ID, 250* kbaud)"),
		ELM_PROT_USER1_CAN_11_S("User1 CAN (11* bit ID, 125* kbaud)"),
		ELM_PROT_USER2_CAN_11_S("User2 CAN (11* bit ID, 50* kbaud)");
		private final String description;
		
		PROT(String _description)
		{
			description = _description;
		}
		
		@Override
		public String toString()
		{
			return description;
		}
	}
	
	/**
	 * possible ELM responses and ID's
	 */
	enum RSP_ID
	{
		PROMPT(">"),
		OK("OK"),
		MODEL("ELM"),
		NODATA("NODATA"),
		SEARCH("SEARCHING"),
		ERROR("ERROR"),
		NOCONN("UNABLE"),
		NOCONN2("NABLETO"),
		CANERROR("CANERROR"),
		BUSBUSY("BUSBUSY"),
		BUSERROR("BUSERROR"),
		BUSINIERR("BUSINIT:ERR"),
		BUSINIERR2("BUSINIT:BUS"),
		BUSINIERR3("BUSINIT:...ERR"),
		FBERROR("FBERROR"),
		DATAERROR("DATAERROR"),
		BUFFERFULL("BUFFERFULL"),
		STOPPED("STOPPED"),
		RXERROR("<"),
		QMARK("?"),
		UNKNOWN("");
		private final String response;
		
		RSP_ID(String response)
		{
			this.response = response;
		}
		
		@Override
		public String toString()
		{
			return response;
		}
	}
	
	/**
	 * possible communication states
	 */
	public enum STAT
	{
		UNDEFINED("Undefined"),
		INITIALIZING("Initializing"),
		INITIALIZED("Initialized"),
		ECU_DETECT("ECU detect"),
		ECU_DETECTED("ECU detected"),
		ECU_SELECTED("Vehicle Connected"),
		CONNECTING("Connecting"),
		CONNECTED("Connected"),
		NODATA("No data"),
		STOPPED("Stopped"),
		DISCONNECTED("Disconnected"),
		BUSERROR("BUS error"),
		DATAERROR("DATA error"),
		RXERROR("RX error"),
		ERROR("Error");
		private final String elmState;
		
		STAT(String state)
		{
			elmState = state;
		}
		
		@Override
		public String toString()
		{
			return elmState;
		}
	}
	
	/**
	 * numeric IDs for commands
	 */
	public enum CMD
	{
		RESET("Z", 0, true), ///< reset adapter
		WARMSTART("WS", 0, true), ///< warm start
		PROTOCLOSE("PC", 0, true), ///< protocol close
		DEFAULTS("D", 0, true), ///< set all to defaults
		INFO("I", 0, true), ///< request adapter info
		LOWPOWER("LP", 0, true), ///< switch to low power mode
		ECHO("E", 1, true), ///< enable/disable echo
		SETLINEFEED("L", 1, true), ///< enable/disable line feeds
		SETSPACES("S", 1, true), ///< enable/disable spaces
		SETHEADER("H", 1, true), ///< enable/disable header response
		GETPROT("DP", 0, true), ///< get protocol
		SETPROT("SP", 1, true), ///< set protocol
		CANMONITOR("MA", 0, true), ///< monitor CAN messages
		SETPROTAUTO("SPA", 1, true), ///< set protocol auto
		ADAPTTIMING("AT", 1, true), ///< Set ELM internal adaptive timing (0-2)
		SETTIMEOUT("ST", 2, true), ///< set timeout (x*4ms)
		SETTXHDR("SH", 3, true), ///< set TX header
		SETCANRXFLT("CRA", 3, true), ///< set CAN RX filter
		CLRCANRXFLT("CRA", 0, true); ///< clear CAN RX filter
		
		static final String CMD_HEADER = "AT";
		private final String command;
		final int paramDigits;
		private final boolean disablingAllowed;
		private boolean enabled = true;
		
		CMD(String cmd, int numDigitsParameter, @SuppressWarnings("SameParameterValue") boolean allowAdaption)
		{
			command = cmd;
			paramDigits = numDigitsParameter;
			disablingAllowed = allowAdaption;
		}
		
		@Override
		public String toString()
		{
			return CMD_HEADER + command;
		}
		
		public boolean isEnabled()
		{
			return enabled;
		}
		
		void setEnabled(boolean enabled)
		{
			if (disablingAllowed)
			{
				this.enabled = enabled;
			}
			// log current state
			log.fine(String.format("ELM command '%s' -> %s",
				toString(),
				this.enabled ? "enabled" : "disabled"));
		}
		
		public boolean isDisablingAllowed()
		{
			return disablingAllowed;
		}
	}
	
	/**
	 * Adaptive timing mode
	 */
	public enum AdaptTimingMode
	{
		OFF,
		ELM_AT1,
		ELM_AT2,
		SOFTWARE
	}
	
	/**
	 * Adaptive ELM timing handler
	 * * optimizes ELM message timeout at runtime
	 */
	public class AdaptiveTiming
	{
		/**
		 * for ELM message timeout handling
		 */
		/**
		 * max. ELM Message Timeout [ms]
		 */
		private static final int ELM_TIMEOUT_MAX = 1000;
		/**
		 * default ELM message timeout
		 */
		private static final int ELM_TIMEOUT_DEFAULT = 200;
		/**
		 * Learning resolution of ELM Message Timeout [ms]
		 */
		private static final int ELM_TIMEOUT_RES = 4;
		/**
		 * minimum ELM timeout
		 */
		int ELM_TIMEOUT_MIN = 12;
		/**
		 * minimum ELM timeout (learned from vehicle)
		 */
		int ELM_TIMEOUT_LRN_LOW = 12;
		/**
		 * ELM message timeout: defaults to approx 200 [ms]
		 */
		int elmMsgTimeout = ELM_TIMEOUT_MAX;
		
		/**
		 * adaptive timing handling enabled?
		 */
		private AdaptTimingMode mode = AdaptTimingMode.OFF;
		
		public AdaptTimingMode getMode()
		{
			return mode;
		}
		
		public void setMode(AdaptTimingMode mode)
		{
			
			log.info(String.format("AdaptiveTiming mode: %s -> %s",
				this.mode.toString(),
				mode.toString()));
			this.mode = mode;
			// initialize with new mode
			initialize();
		}
		
		/**
		 * min. (configured) ELM Message Timeout
		 *
		 * @return minimum (configured) ELM timeout value [ms]
		 */
		public int getElmTimeoutMin()
		{
			return ELM_TIMEOUT_MIN;
		}
		
		/**
		 * Set min. (configured) ELM Message Timeout
		 *
		 * @param elmTimeoutMin minimum (configured) ELM timeout value [ms]
		 */
		public void setElmTimeoutMin(int elmTimeoutMin)
		{
			log.info(String.format("ELM min timeout: %d -> %d",
				ELM_TIMEOUT_MIN, elmTimeoutMin));
			ELM_TIMEOUT_MIN = elmTimeoutMin;
		}
		
		/**
		 * Initialize timing hadler
		 */
		void initialize()
		{
			if (mode == AdaptTimingMode.SOFTWARE)
			{
				// ... reset learned minimum timeout ...
				setElmTimeoutLrnLow(getElmTimeoutMin());
				// set default timeout
				setElmMsgTimeout(ELM_TIMEOUT_DEFAULT);
				// switch OFF ELM internal adaptive timing
				pushCommand(CMD.ADAPTTIMING, 0);
			}
			else
			{
				pushCommand(CMD.ADAPTTIMING, mode.ordinal());
			}
		}
		
		/**
		 * Adapt ELM message timeout
		 *
		 * @param increaseTimeout increase/decrease timeout
		 */
		void adapt(boolean increaseTimeout)
		{
			if (mode != AdaptTimingMode.SOFTWARE) { return; }
			if (increaseTimeout)
			{
				// increase OBD timeout since we may expect answers too fast
				if ((elmMsgTimeout + ELM_TIMEOUT_RES) < ELM_TIMEOUT_MAX)
				{
					// increase timeout, since we have just timed out
					setElmMsgTimeout(elmMsgTimeout + ELM_TIMEOUT_RES);
					// ... and limit MIN timeout for this session
					setElmTimeoutLrnLow(elmMsgTimeout);
				}
			}
			else
			{
				// reduce OBD timeout towards minimum limit
				if ((elmMsgTimeout - ELM_TIMEOUT_RES) >= getElmTimeoutLrnLow())
				{
					setElmMsgTimeout(elmMsgTimeout - ELM_TIMEOUT_RES);
				}
				
			}
		}
		
		/**
		 * LOW Learn value ELM Message Timeout
		 *
		 * @return currently learned timout value [ms]
		 */
		private int getElmTimeoutLrnLow()
		{
			return ELM_TIMEOUT_LRN_LOW;
		}
		
		/**
		 * set LOW Learn value ELM Message Timeout
		 *
		 * @param elmTimeoutLrnLow new learn value [ms]
		 */
		private void setElmTimeoutLrnLow(int elmTimeoutLrnLow)
		{
			log.info(String.format("ELM learn timeout: %d -> %d",
				ELM_TIMEOUT_LRN_LOW, elmTimeoutLrnLow));
			ELM_TIMEOUT_LRN_LOW = elmTimeoutLrnLow;
		}
		
		/**
		 * Set message timeout to ELM adapter to wait for valid response from vehicle
		 * If this timeout expires before a valid response is received from the
		 * vehicle, the ELM adapter will respond with "NO DATA"
		 *
		 * @param newTimeout desired timeout in milliseconds
		 */
		private void setElmMsgTimeout(int newTimeout)
		{
			if (newTimeout > 0 && newTimeout != elmMsgTimeout)
			{
				log.info("ELM Timeout: " + elmMsgTimeout + " -> " + newTimeout);
				// set the timeout variable
				elmMsgTimeout = newTimeout;
				// queue the new timeout message
				pushCommand(CMD.SETTIMEOUT, newTimeout / 4);
			}
		}
	}
	
	/**
	 * Creates a new instance of ElmProtocol
	 */
	public ElmProt()
	{
	}
	
	/**
	 * set preferred ELM protocol to be used
	 *
	 * @param protoIndex preferred ELM protocol index
	 */
	public static void setPreferredProtocol(int protoIndex)
	{
		preferredProtocol = PROT.values()[protoIndex];
		log.info("Preferred protocol: " + preferredProtocol);
	}
	
	/**
	 * set ECU address to be received
	 *
	 * @param ecuAddress ECU address to be filtered / 0 = clear address filter
	 */
	public void setEcuAddress(int ecuAddress)
	{
		log.info(String.format("Set ECU address: 0x%x", ecuAddress));
		selectedEcuAddress = ecuAddress;
		// ensure headers are off
		pushCommand(CMD.SETHEADER, 0);
		// set/clear RX filter
		pushCommand((selectedEcuAddress != 0) ? CMD.SETCANRXFLT : CMD.CLRCANRXFLT,
			selectedEcuAddress);
	}
	
	/**
	 * disable a set of ELM commands ELM commands from preference
	 *
	 * @param disabledCmds set of ELM commands (ATxx strings) to be disabled
	 */
	public static void disableCommands(Set<String> disabledCmds)
	{
		for (CMD cmd : CMD.values())
		{
			cmd.setEnabled(disabledCmds == null
			               || !disabledCmds.contains(cmd.toString()));
		}
	}
	
	/**
	 * create ELM command string from command id and paramter
	 *
	 * @param cmdID ID of ELM command
	 * @param param parameter for ELM command (0 if not required)
	 * @return command char sequence or NULL if command disabled/invalid
	 */
	private String createCommand(CMD cmdID, int param)
	{
		String cmd = null;
		if (cmdID.isEnabled())
		{
			cmd = cmdID.toString();
			// if parameter is required and provided, add parameter to command
			if (cmdID.paramDigits > 0)
			{
				String fmtString = "%0".concat(String.valueOf(cmdID.paramDigits)).concat("X");
				cmd += String.format(fmtString, param);
			}
		}
		// return command String
		return cmd;
	}
	
	/**
	 * send command to ELM adapter
	 *
	 * @param cmdID ID of ELM command
	 * @param param parameter for ELM command (0 if not required)
	 */
	public void sendCommand(CMD cmdID, int param)
	{
		// now send command
		String cmd = createCommand(cmdID, param);
		if (cmd != null) { sendTelegram(cmd.toCharArray()); }
	}
	
	/**
	 * queue command to ELM command queue
	 *
	 * @param cmdID ID of ELM command
	 * @param param parameter for ELM command (0 if not required)
	 */
	private void pushCommand(CMD cmdID, int param)
	{
		String cmd = createCommand(cmdID, param);
		if (cmd != null) { cmdQueue.add(cmd); }
	}
	
	@Override
	public void sendTelegram(char[] buffer)
	{
		log.fine(this.toString() + " TX:'" + String.valueOf(buffer) + "'");
		lastCommand = buffer;
		super.sendTelegram(buffer);
	}
	
	/**
	 * return numeric ID to given response
	 *
	 * @param response clear text response from ELM adapter
	 */
	private static RSP_ID getResponseId(String response)
	{
		RSP_ID result = RSP_ID.UNKNOWN;
		for (RSP_ID id : RSP_ID.values())
		{
			if (response.startsWith(id.toString()))
			{
				result = id;
				break;
			}
		}
		// return ID
		return (result);
	}
	
	/**
	 * send ELM adapter to sleep mode
	 */
	public void goToSleep()
	{
		sendCommand(CMD.LOWPOWER, 0);
	}
	
	/**
	 * reset ELM adapter
	 */
	public void reset()
	{
		// reset all learned protocol data
		super.reset();
		// either RESET or INFO command needs to be enabled
		if (CMD.RESET.isEnabled())
		{ sendCommand(CMD.RESET, 0); }
		else
		{ sendCommand(CMD.INFO, 0); }
	}
	
	/**
	 * request addresses of all connected ECUs
	 * (received IDs are evaluated in @ref:handleDataMessage)
	 */
	private void queryEcus()
	{
		// set status to ECU detection
		setStatus(STAT.ECU_DETECT);
		
		// clear all identified ECU addresses
		ecuAddresses.clear();
		// clear selected ECU
		selectedEcuAddress = 0;
		// remember to disable headers again
		pushCommand(CMD.SETHEADER, 0);
		// request PIDs (from all devices)
		cmdQueue.add("0100");
		// enable headers
		sendCommand(CMD.SETHEADER, 1);
	}
	
	private void initialize()
	{
		// set status to INITIALIZING
		setStatus(STAT.INITIALIZING);
		
		// push custom init commands
		cmdQueue.addAll(customInitCommands);
		
		// set to preferred protocol
		pushCommand(CMD.SETPROT, preferredProtocol.ordinal());
		
		// initialize adaptive timing handler
		mAdaptiveTiming.initialize();
		
		// speed up protocol by removing spaces and line feeds from output
		pushCommand(CMD.SETSPACES, 0);
		pushCommand(CMD.SETLINEFEED, 0);
		
		// immediate set echo off
		pushCommand(CMD.ECHO, 0);
	}
	
	/**
	 * Implementation of TelegramListener
	 */
	
	/**
	 * multiline response is pending, for responses w/o a length info
	 */
	private boolean responsePending = false;
	
	/**
	 * handle incoming protocol telegram
	 *
	 * @param buffer - telegram buffer
	 * @return number of listeners notified
	 */
	@Override
	public synchronized int handleTelegram(char[] buffer)
	{
		int result = 0;
		String bufferStr = new String(buffer);

		log.fine(this.toString() + " RX:'" + bufferStr + "'");

		// === RAW TELEGRAM LISTENERS ===
		// Notify raw listeners FIRST, before any processing
		// This gives them access to complete telegrams WITH headers (if enabled)
		notifyRawTelegramListeners(buffer);

		// empty result
		if (buffer.length == 0)
		{
			return result;
		}
		
		// if ths is echo of last command
		if (lastTxMsg.compareToIgnoreCase(bufferStr) == 0)
		{
			// ignore echoed command
			return result;
		}
		
		// log message reception as answer to last TX message
		log.fine("ELM rx:'" + bufferStr + "' (" + lastTxMsg + ")");
		
		// handle response
		switch (getResponseId(bufferStr))
		{
			case SEARCH:
				// Only set to CONNECTING if we're not already in a good connected state
				// This prevents unnecessary status changes during transient "SEARCHING" messages
				// that occur after NODATA responses
				if (status != STAT.ECU_DETECT &&
				    status != STAT.CONNECTED &&
				    status != STAT.ECU_DETECTED &&
				    status != STAT.ECU_SELECTED)
				{
					setStatus(STAT.CONNECTING);
				}
				// NO break here
			case QMARK:
			case NODATA:
			case OK:
			case ERROR:
			case NOCONN:
			case NOCONN2:
			case CANERROR:
			case BUSERROR:
			case BUSINIERR:
			case BUSINIERR2:
			case BUSINIERR3:
			case BUSBUSY:
			case FBERROR:
			case DATAERROR:
			case BUFFERFULL:
			case RXERROR:
				// remember this as last received message
				// do NOT respond immediately
				lastRxMsg = bufferStr;
				break;

			case STOPPED:
				// remember this as last received message
				lastRxMsg = bufferStr;
				// re-queue last command
				cmdQueue.add(String.valueOf(lastCommand));
				break;

			case MODEL:
				// the adapter (re)started (ATZ/ATWS banner): forget any error or
				// partial response from before, otherwise the next prompt still
				// sees that error and warm-starts the adapter again (obd-54d)
				lastRxMsg = bufferStr;
				charsExpected = 0;
				responsePending = false;
				initialize();
				break;

			// received a PROMPT, what was the last response?
			case PROMPT:
				// check for last received message
				switch (getResponseId(lastRxMsg))
				{
					case NOCONN:
					case NOCONN2:
					case CANERROR:
					case BUSERROR:
					case BUSINIERR:
					case BUSINIERR2:
					case BUSINIERR3:
					case BUSBUSY:
					case FBERROR:
						setStatus(STAT.DISCONNECTED);
						// re-queue last command
						cmdQueue.add(String.valueOf(lastCommand));
						// queue setting to preferred protocol
						pushCommand(CMD.SETPROT, preferredProtocol.ordinal());
						// Initialize adaptive timing
						mAdaptiveTiming.initialize();
						// immediately close current protocol
						sendCommand(CMD.PROTOCLOSE, 0);
						break;
					
					case DATAERROR:
						setStatus(STAT.DATAERROR);
						sendCommand(CMD.WARMSTART, 0);
						break;
					
					case BUFFERFULL:
					case RXERROR:
						setStatus(STAT.RXERROR);
						sendCommand(CMD.WARMSTART, 0);
						break;
					
					case ERROR:
						setStatus(STAT.ERROR);
						sendCommand(CMD.WARMSTART, 0);
						break;

					case NODATA:
						setStatus(STAT.NODATA);
						// re-queue next data item
						if (service != OBD_SVC_NONE)
						{
							cmdQueue.add(
								String.valueOf(
									createTelegram(emptyBuffer, service, getNextSupportedPid()))
							);
						}
						// increase OBD timeout since we may expect answers too fast
						mAdaptiveTiming.adapt(true);
						// set to preferred protocol
						pushCommand(CMD.SETPROT, preferredProtocol.ordinal());
						// NO break here since reaction is only quqeued
					
					case MODEL:
					case SEARCH:
					case STOPPED:
						// was already handled before prompt
					case QMARK:
						// last command stays ignored
					
					case OK:
					default:
						// if there is a pending data response, handle it now ...
						if (responsePending)
						{
							result = handleDataMessage(lastRxMsg);
						}
						
						// queued commands will be sent first
						if (cmdQueue.size() > 0)
						{
							// get last command
							String cmd = cmdQueue.lastElement();
							// and remove it from list
							cmdQueue.remove(cmd);
							// send the command
							sendTelegram(cmd.toCharArray());
						}
						else
						{
							// all queued commands are sent -> we are done initializing
							if (status == STAT.INITIALIZING)
							{
								// set status to initialized
								setStatus(STAT.INITIALIZED);
								// initiate query of connected ECUs
								queryEcus();
								break;
							}
							
							// all queued commands are sent -> we are done detecting ECUs
							setStatus(status == STAT.ECU_DETECT ? STAT.ECU_DETECTED : status);
							
							switch (service)
							{
								case OBD_SVC_VEH_INFO:
									// if all pid's have been read once ...
									if (pidsWrapped)
									{
										// ... terminate service loop
										break;
									}
									// no break here ...
								case OBD_SVC_DATA:
								case OBD_SVC_FREEZEFRAME:
								{
									// otherwise the next PID will be requested
									writeTelegram(emptyBuffer, service, getNextSupportedPid());
									// reduce OBD timeout towards minimum limit
									mAdaptiveTiming.adapt(false);
								}
								break;
								
								case OBD_SVC_NONE:
								default:
									// do nothing
							}
						}
				}
				break;
			
			// handle data response
			default:
				// if we are still initializing check for address entries
				switch (status)
				{
					case ECU_DETECT:
					{
						// start of 0100 response is end of address
						int adrEnd = bufferStr.indexOf("41");
						// if not a service response, check for possible NRC
						if(adrEnd < 0)
						{
							adrEnd = bufferStr.indexOf("7F01");
						}
						int adrStart = bufferStr.lastIndexOf(".") + 1;
						if (adrEnd > adrStart)
						{
							int adrLen = adrEnd - adrStart;
							if ((adrLen % 2) != 0)
							{
								/*
								 * odd address length
								 * -> CAN address length = 3 digits + 2 digits frame type
								 */
								adrLen = 3;
							}
							else
							{
								if (adrLen == 6)
								{
									/*
									 * 6 char (3 byte) prefix -> ISO9141 address <FF><RR><SS>
									 *     FF - Frame type
									 *     RR - receiver address
									 *     SS - sender address
									 */
									adrLen = 2;
									adrStart = adrEnd - adrLen;
								}
								else if (adrLen == 10)
								{
									/*
									 * 29 bit CAN address
									 * <CP><AABBCC><FT>
									 *     CP = CAN priority (5 relevant bits)
									 *     AABBCC = Address (24 Bit)
									 *     FT = Frame type
									 * -> CAN address length = 32 bits / 8 digits <CP><AABBCC>
									 */
									adrLen = 8;
									adrStart = 0;
								}
							}
							// extract address
							String address = bufferStr.substring(adrStart, adrStart + adrLen);
							
							log.fine(String.format("Found ECU address: 0x%s", address));
							// and add to list of addresses
							ecuAddresses.add(Integer.valueOf(address, 16));
						}
						return lastRxMsg.length();
					}
					default:
						break;
				}
				
				// we are connected ...
				setStatus(STAT.CONNECTED);
				
				// ELM clone verbose message (starting with '+')
				if(buffer[0] == '+')
				{
					// ignore message
					return (result);
				}
				
				// is this a length identifier?
				if (buffer[0] == '0' && buffer.length == 3)
				{
					// then remember the length to be expected
					charsExpected = Integer.valueOf(bufferStr, 16) * 2;
					lastRxMsg = "";
					return (result);
				}
				
				// is this a multi-line response
				int idx = bufferStr.indexOf(':');

				// .. or a ISO multi line response with format SVC PID MSGID DATA...
				if((idx < 0) && (buffer.length == 14))
				{
					final int[] dfcServices = {OBD_SVC_READ_CODES, OBD_SVC_PENDINGCODES, OBD_SVC_PERMACODES};
					int msgService = Integer.valueOf(bufferStr.substring(0, 2), 0x10) & ~0x40;
					// If response to current service and no DFC response ...
					if(msgService == getService()
					   && Arrays.binarySearch(dfcServices, msgService) < 0)
					{
						// Use header on 1st response, cut from continuation messages
						int msgId = Integer.valueOf(bufferStr.substring(4,6),0x10);
						if(msgId <= 1)
						{
							// 1st response line
							idx = 0;
							lastMsgId = msgId;
						}
						else if (msgId == (lastMsgId + 1))
						{
							// continuation line
							idx = 5;
							lastMsgId = msgId;
						}
						else
						{
							// NOT a ISO multi line message
							idx = -1;
							// cut additional (padding) byte
							bufferStr = bufferStr.substring(0,buffer.length-2);
						}
					}
				}

				if (idx >= 0)
				{
					if(idx == 0)
					{
						// initial ISO multiline message
						lastRxMsg = bufferStr;
						charsExpected = 0;
					}
					else if (buffer[0] == '0')
					{
						// first line of a multiline message
						lastRxMsg = bufferStr.substring(idx + 1);
					}
					else
					{
						// continuation lines
						// concat response without line counter
						lastRxMsg += bufferStr.substring(idx + 1);
					}

					/* no length known, set marker for pending response
					   response will be finished on reception of prompt */
					responsePending = (charsExpected == 0);
				}
				else
				{
					// otherwise use this as last received message
					lastRxMsg = bufferStr;
					charsExpected = 0;
					responsePending = false;
				}
				

				// if we haven't received complete result yet, then wait for the rest
				if (lastRxMsg.length() < charsExpected)
				{
					return (result);
				}
				
				// Trim a multiline response to expected length (cut off padding)
				if((charsExpected > 0) && (lastRxMsg.length() > charsExpected))
				{
					lastRxMsg = lastRxMsg.substring(0, charsExpected);
				}
				
				// if response is finished, handle it
				if (!responsePending)
				{
					result = handleDataMessage(lastRxMsg);
				}
		}
		return (result);
	}
	
	/**
	 * forward data message for further handling
	 *
	 * @param lastRxMsg received message to be forwarded
	 * @return number of bytes processed
	 */
	private int handleDataMessage(String lastRxMsg)
	{
		int result = 0;
		
		// otherwise process response
		switch (service)
		{
			case OBD_SVC_NONE:
				// ignore messages
				break;
			
			case OBD_SVC_CAN_MONITOR:
				result = canProt.handleTelegram(lastRxMsg.toCharArray());
				break;
			
			default:
				// Let the OBD protocol handle the telegram
				result = super.handleTelegram(lastRxMsg.toCharArray());
		}
		return result;
	}
	
	// switch to exit the demo thread
	public static boolean runDemo;
	// flag to track if demo codes have been cleared
	private static boolean demoCodesCleared = false;
	// flag to track if headers are enabled in demo mode
	private static boolean demoHeadersEnabled = false;
	// flag to track if freeze frame has been initialized in demo
	private boolean freezeFrameInitialized = false;

	/**
	 * run threaded loop to simulate incoming telegrams
	 */
	public void run()
	{
		int value = 0;
		Integer pid;
		runDemo = true;
		demoCodesCleared = false; // Reset cleared state when demo starts

		log.info("ELM DEMO thread started");
		while (runDemo)
		{
			try
			{
				handleTelegram(RSP_ID.MODEL.toString().toCharArray());
				// test case for issue AndrOBD/#61
				handleTelegram("+CONNECTING<<94:65:2D:9E:DF:B5".toCharArray());
				
				setStatus(STAT.ECU_DETECT);
				handleTelegram("SEARCHING...".toCharArray());
				handleTelegram("7EA074100000000".toCharArray());
				handleTelegram("486B104100BF9FA8919B".toCharArray());
				handleTelegram("...486B104100BF9FA8919B".toCharArray());
				handleTelegram("7E8064100000000".toCharArray());
				handleTelegram("7E9074100000000".toCharArray());
				handleTelegram("7EA074100000000".toCharArray());
				// test case for issue AndrOBD/#60
				handleTelegram("18DAF110064100BE3EB811".toCharArray());
				// test case for issue AndrOBD-Plugin/#10 (NRC22 on detect)
				handleTelegram("7E8037F0122".toCharArray());
				setStatus(STAT.ECU_DETECTED);
				
				while (runDemo)
				{
					// Check for ATH commands to enable/disable headers in demo mode
					if (lastCommand != null) {
						String cmd = new String(lastCommand).trim().toUpperCase();
						if (cmd.equals("ATH1")) {
							demoHeadersEnabled = true;
							handleTelegram("OK".toCharArray());
							Thread.sleep(100);
							continue;
						} else if (cmd.equals("ATH0")) {
							demoHeadersEnabled = false;
							handleTelegram("OK".toCharArray());
							Thread.sleep(100);
							continue;
						}
					}

					switch (service)
					{
						// read any kinds of trouble codes
						case OBD_SVC_READ_CODES:
							if (demoCodesCleared) {
								// If codes were cleared, report 0 codes + MIL OFF
								handleTelegram("4300".toCharArray());
							} else {
								// Remove P0000 when we have actual codes
								ObdProt.tCodes.remove(0);
								// Simulate 5 fault codes + MIL ON
								// Format: 43 05 (service + count) followed by codes
								// P0171, P0301, P0420, P0442, P0128
								handleTelegram("430501710301042004420128".toCharArray());
							}
							Thread.sleep(500);
							break;

						case OBD_SVC_PENDINGCODES:
							if (demoCodesCleared) {
								// When cleared, respond with 0 codes
								handleTelegram("4700".toCharArray());
								Thread.sleep(500);
							}
							// When codes exist, skip this service entirely
							break;

						case OBD_SVC_PERMACODES:
							if (demoCodesCleared) {
								// When cleared, respond with 0 codes
								handleTelegram("4A00".toCharArray());
								Thread.sleep(500);
							}
							// When codes exist, skip this service entirely
							break;

						// handle clear codes request
						case OBD_SVC_CLEAR_CODES:
							// Mark codes as cleared in demo mode
							demoCodesCleared = true;
							// Clear the codes list immediately
							ObdProt.tCodes.clear();
							// Add "no codes" message
							ObdProt.tCodes.put(0, new ObdCodeItem(0, "No trouble codes set"));
							// Send positive response for clear codes
							handleTelegram("44".toCharArray());
							Thread.sleep(500);
							break;
						
						// otherwise send data ...
						case OBD_SVC_DATA:
						case OBD_SVC_FREEZEFRAME:
							// Special handling for freeze frame in demo mode
							if (service == OBD_SVC_FREEZEFRAME && !freezeFrameInitialized) {
								log.info("DEMO: Initializing freeze frame with full data");

								// First send PID support for all PIDs
								int i;
								for (i = 0; i < 0xE0; i += 0x20) {
									handleTelegram(String.format("42%02X00FFFFFFFF", i).toCharArray());
								}
								handleTelegram(String.format("42%02X00FFFFFFFE", i).toCharArray());

								// Wait for messages to be processed
								try { Thread.sleep(100); } catch (Exception e) {}

								freezeFrameInitialized = true;
								log.info("DEMO: Freeze frame initialization complete");
								// Don't break - continue to send actual data
							}

							// For freeze frame, always send a cycle of common PIDs with frozen values
							if (service == OBD_SVC_FREEZEFRAME && freezeFrameInitialized) {
								// Common PIDs with realistic frozen values
								handleTelegram("42040064".toCharArray());       // Engine load 39%
								handleTelegram("420500B4".toCharArray());       // Coolant temp 60°C
								handleTelegram("420C001234".toCharArray());     // Engine RPM ~1165
								handleTelegram("420D0038".toCharArray());       // Vehicle speed 56 km/h
								handleTelegram("420E00B0".toCharArray());       // Timing advance 22°
								handleTelegram("420F0050".toCharArray());       // Intake temp 40°C
								handleTelegram("421000015E".toCharArray());     // MAF rate
								handleTelegram("42110080".toCharArray());       // Throttle position 50%
								handleTelegram("421F000078".toCharArray());     // Run time 120 seconds
								handleTelegram("42210001F4".toCharArray());     // Distance with MIL
								break; // Done for this cycle
							}

							// Normal live data processing
							pid = getNextSupportedPid();
							if (pid != 0)
							{
								// Special handling for PID 0x01 (MIL status + DTC count)
								if (service == OBD_SVC_DATA && pid == 0x01) {
									// PID 01: Monitor status since DTCs cleared
									// Byte A: Bit 7 = MIL status, Bits 0-6 = DTC count
									// Bytes B-D: Readiness status (from real Mercedes data)
									if (demoCodesCleared) {
										// MIL OFF + 0 DTCs
										handleTelegram("410100078500".toCharArray());
									} else {
										// MIL ON + 5 DTCs (matching the 5 codes in Mode 3)
										handleTelegram("410185078500".toCharArray());
									}
								} else {
									// Generic handling for other PIDs
									value++;
									value &= 0xFF;
									// format new data message and handle it as new reception
									handleTelegram(String.format(
										service == OBD_SVC_DATA ? "4%X%02X%02X%02X%02X%02X"
										                        : "4%X%02X00%02X%02X%02X%02X",
										service, pid, value, value, value, value).toCharArray());
								}
							}
							else
							{
								// simulate "ALL PIDs supported" for both services
								int i;
								for (i = 0; i < 0xE0; i += 0x20)
								{
									handleTelegram(String.format(
										service == OBD_SVC_DATA ? "4%X%02XFFFFFFFF"
										                        : "4%X%02X00FFFFFFFF",
										service, i).toCharArray());
								}
								handleTelegram(String.format(
									service == OBD_SVC_DATA ? "4%X%02XFFFFFFFE"
									                        : "4%X%02X00FFFFFFFE",
									service, i).toCharArray());
							}
							break;
						
						case OBD_SVC_VEH_INFO:
							pid = getNextSupportedPid();
							if (pid == 0)
							{
								// Real Mercedes supported PIDs: 0x02,0x04,0x06,0x08,0x0A,0x14
								if (demoHeadersEnabled) {
									// Respond from multiple ECUs with headers (NO SPACES)
									handleTelegram("7E806490055401000".toCharArray());   // Engine ECU
									handleTelegram("7E906490055401000".toCharArray());   // Transmission ECU
									handleTelegram("7EA06490055401000".toCharArray());   // FPCM ECU
								} else {
									handleTelegram("490055401000".toCharArray());
								}
							}
							// VIN from real Mercedes-Benz GLE-Class (PID 0x02)
							else if (pid == 0x02) {
								handleTelegram("014".toCharArray());
								handleTelegram("1:490201344A4744".toCharArray()); // "4JGD"
								handleTelegram("2:41354842374A42".toCharArray()); // "A5HB7JB"
								handleTelegram("3:31353831343434".toCharArray()); // "158144"
							}
							// Calibration ID (PID 0x04)
							else if (pid == 0x04) {
								if (demoHeadersEnabled) {
									// Multiple ECUs with different Cal IDs (matching Teensy simulator)
									// Engine: "2769011200190170" - multiframe response
									handleTelegram("7E81013490401323736".toCharArray());
									handleTelegram("7E82139303131323030".toCharArray());
									handleTelegram("7E82231393031373000".toCharArray());
									Thread.sleep(5);
									// Transmission: "00090237271900001"
									handleTelegram("7E91014490401303030".toCharArray());
									handleTelegram("7E92139303233373237".toCharArray());
									handleTelegram("7E92231393030303031".toCharArray());
									Thread.sleep(5);
									// FPCM: "00090121001900560"
									handleTelegram("7EA1014490401303030".toCharArray());
									handleTelegram("7EA2139303132313030".toCharArray());
									handleTelegram("7EA2231393030353630".toCharArray());
								} else {
									handleTelegram("49040132373639303131323030313930313730".toCharArray());
								}
							}
							// CVN (Calibration Verification Number) (PID 0x06)
							else if (pid == 0x06) {
								if (demoHeadersEnabled) {
									// Multiple ECUs with different CVNs (matching Teensy simulator)
									handleTelegram("7E806490601EB854939".toCharArray()); // Engine: EB854939
									Thread.sleep(5);
									handleTelegram("7E9064906015DEF71AD".toCharArray()); // Transmission: 5DEF71AD
									Thread.sleep(5);
									handleTelegram("7EA064906018CD7FF6C".toCharArray()); // FPCM: 8CD7FF6C
								} else {
									handleTelegram("490601EB854939".toCharArray());
								}
							}
							// Performance Tracking data (PID 0x08)
							else if (pid == 0x08) {
								handleTelegram("49081410622E4C176910621704106215D8106213CD106220AE10620000000001B303070DFC106209CD1062".toCharArray());
							}
							// ECU Name (PID 0x0A)
							else if (pid == 0x0A) {
								if (demoHeadersEnabled) {
									// Multiple ECUs with different names (matching Teensy simulator)
									// Engine: "ECM\0-EngineControl\0\0"
									handleTelegram("7E81017490A0145434D".toCharArray());
									handleTelegram("7E821002D456E67696E".toCharArray());
									handleTelegram("7E82265436F6E74726F".toCharArray());
									handleTelegram("7E8236C000000000000".toCharArray());
									Thread.sleep(5);
									// Transmission: "TCM\0-TransmisCtrl\0"
									handleTelegram("7E91016490A0154434D".toCharArray());
									handleTelegram("7E921002D5472616E73".toCharArray());
									handleTelegram("7E9226D69734374726C".toCharArray());
									handleTelegram("7E92300000000000000".toCharArray());
									Thread.sleep(5);
									// FPCM: "FPCM\0-FuelPumpCtrl\0\0\0"
									handleTelegram("7EA1018490A01465043".toCharArray());
									handleTelegram("7EA214D002D4675656C".toCharArray());
									handleTelegram("7EA2250756D70437472".toCharArray());
									handleTelegram("7EA236C000000000000".toCharArray());
								} else {
									handleTelegram("490A0145434D002D456E67696E65436F6E74726F6C0000".toCharArray());
								}
							}
							// Auxiliary I/O Status (PID 0x14)
							else if (pid == 0x14) {
								handleTelegram("4914010018".toCharArray());
							}
							break;
						
						case OBD_SVC_CTRL_MODE:
							handleTelegram("4800C0000000".toCharArray());
							break;

						case OBD_SVC_NONE:
							// just keep quiet until soneone requests something
							break;
						
						default:
							// respond "service not supported"
							handleTelegram(String.format("7F%02X11", service).toCharArray());
							Thread.sleep(500);
							break;
						
					}
					Thread.sleep(50);
				}
			}
			catch (Exception ex)
			{
				log.severe(ex.getLocalizedMessage());
			}
		}
		log.info("ELM DEMO thread finished");
	}
	
	/**
	 * set custom initialisation commands
	 *
	 * @param commands custom initialisation commands
	 */
	public void setCustomInitCommands(String[] commands)
	{
		List<String> cmds = Arrays.asList(commands);
		// reverse list, since all commands are pushed rather than queued
		Collections.reverse(cmds);
		// clear list
		customInitCommands.clear();
		// add all entries
		customInitCommands.addAll(cmds);
	}

	/**
	 * Raw Telegram Listener Management
	 * These methods allow listeners to receive RAW telegrams BEFORE header stripping.
	 */

	/**
	 * Add a raw telegram listener to receive data before protocol processing
	 *
	 * @param listener RawTelegramListener to be added
	 * @return true if adding was OK, otherwise false
	 */
	@SuppressWarnings("unchecked")
	public boolean addRawTelegramListener(RawTelegramListener listener)
	{
		log.fine("Adding raw telegram listener: " + listener.getClass().getSimpleName());
		return rawTelegramListeners.add(listener);
	}

	/**
	 * Remove a raw telegram listener
	 *
	 * @param listener RawTelegramListener to be removed
	 * @return true if removal was OK, otherwise false
	 */
	public boolean removeRawTelegramListener(RawTelegramListener listener)
	{
		log.fine("Removing raw telegram listener: " + listener.getClass().getSimpleName());
		return rawTelegramListeners.remove(listener);
	}

	/**
	 * Notify all raw telegram listeners about incoming telegram
	 * This is called BEFORE any protocol processing or header stripping.
	 *
	 * @param buffer Raw telegram buffer including headers (when enabled)
	 */
	@SuppressWarnings("rawtypes")
	private void notifyRawTelegramListeners(char[] buffer)
	{
		if (rawTelegramListeners.isEmpty())
		{
			return;
		}

		// Notify all raw listeners
		java.util.Iterator it = rawTelegramListeners.iterator();
		while (it.hasNext())
		{
			Object listener = it.next();
			if (listener instanceof RawTelegramListener)
			{
				try
				{
					((RawTelegramListener) listener).handleRawTelegram(buffer);
				}
				catch (Exception e)
				{
					log.warning("Raw telegram listener error: " + e.getMessage());
				}
			}
		}
	}

	/**
	 * Setter for property service.
	 *
	 * @param service    New value of property service.
	 * @param clearLists clear data list for this service
	 */
	@Override
	public void setService(int service, boolean clearLists)
	{
		// log the change in service
		if (service != this.service)
		{
			log.info("OBD Service: " + this.service + "->" + service);
			this.service = service;

			// Reset freeze frame flag when switching to freeze frame service
			if (service == OBD_SVC_FREEZEFRAME) {
				freezeFrameInitialized = false;
			}

			// send corresponding command(s)
			switch (service)
			{
				case OBD_SVC_CAN_MONITOR:
					sendCommand(CMD.CANMONITOR, 0);
					break;

				default:
					super.setService(service, clearLists);
			}
		}
	}
	
	/**
	 * set OBD service - compatibility function
	 *
	 * @param service New value of property service.
	 */
	public void setService(int service)
	{
		setService(service, true);
	}
	
	/**
	 * Holds value of property status.
	 */
	private STAT status = STAT.UNDEFINED;

	/**
	 * Timeout for CONNECTING state (5 seconds)
	 * If status stays CONNECTING longer than this, auto-reset to recover
	 */
	private static final long CONNECTING_TIMEOUT_MS = 5000;

	/**
	 * Timestamp when CONNECTING state started
	 */
	private long connectingStateStartTime = 0;

	/**
	 * Timeout for NODATA state recovery (3 seconds)
	 * If status stays NODATA longer than this, trigger recovery
	 */
	private static final long NODATA_TIMEOUT_MS = 3000;

	/**
	 * Maximum consecutive NODATA responses before triggering recovery
	 */
	private static final int NODATA_MAX_CONSECUTIVE = 5;

	/**
	 * Timestamp when NODATA state started
	 */
	private long nodataStateStartTime = 0;

	/**
	 * Counter for consecutive NODATA responses
	 */
	private int consecutiveNodataCount = 0;

	/**
	 * Last known good ECU state before NODATA started
	 */
	private STAT lastGoodState = STAT.UNDEFINED;

	/**
	 * Cycling detection: Track recent state transitions to detect stuck loops
	 */
	private int rapidCycleCount = 0;
	private long cycleDetectionWindowStart = 0;
	private static final int MAX_RAPID_CYCLES = 10; // Max cycles in detection window
	private static final long CYCLE_DETECTION_WINDOW_MS = 30000; // 30 seconds

	/**
	 * Getter for property status.
	 *
	 * @return Value of property status.
	 */
	public STAT getStatus()
	{
		return this.status;
	}
	
	/**
	 * Setter for property status.
	 *
	 * @param status New value of property status.
	 */
	private void setStatus(STAT status)
	{
		STAT oldStatus = this.status;

		// === NODATA Stuck Detection ===
		// Check if we're currently stuck in NODATA before processing new status
		// This runs on EVERY setStatus() call to catch stalled communication
		if (oldStatus == STAT.NODATA && nodataStateStartTime > 0)
		{
			long duration = System.currentTimeMillis() - nodataStateStartTime;

			// If stuck in NODATA beyond timeout, force recovery
			if (duration > NODATA_TIMEOUT_MS && status == STAT.NODATA)
			{
				log.warning(String.format(
					"NODATA stuck detected (%dms) - forcing recovery to %s",
					duration, lastGoodState));

				// Reset counters
				consecutiveNodataCount = 0;
				nodataStateStartTime = 0;

				// Force recovery if we have a good state to restore
				if (lastGoodState == STAT.ECU_DETECTED || lastGoodState == STAT.CONNECTED || lastGoodState == STAT.ECU_SELECTED)
				{
					log.info("Auto-recovering from stuck NODATA state");
					this.status = lastGoodState;
					firePropertyChange(new PropertyChangeEvent(this, PROP_STATUS, oldStatus, this.status));
					return; // Exit early - recovery complete
				}
			}
		}

		// Timeout guard: Check if leaving CONNECTING state after too long
		if (oldStatus == STAT.CONNECTING && status != STAT.CONNECTING)
		{
			long duration = System.currentTimeMillis() - connectingStateStartTime;
			if (duration > CONNECTING_TIMEOUT_MS)
			{
				log.warning(String.format("CONNECTING timeout detected (%dms) - recovered to %s",
					duration, status));
			}
		}

		// Track when entering CONNECTING state
		if (status == STAT.CONNECTING && oldStatus != STAT.CONNECTING)
		{
			connectingStateStartTime = System.currentTimeMillis();
		}
		// Auto-recovery: If stuck in CONNECTING for too long, log warning
		else if (status == STAT.CONNECTING && oldStatus == STAT.CONNECTING)
		{
			long duration = System.currentTimeMillis() - connectingStateStartTime;
			if (duration > CONNECTING_TIMEOUT_MS)
			{
				log.warning(String.format("Stuck in CONNECTING for %dms - may need manual recovery", duration));
			}
		}

		// === NODATA Recovery Logic ===
		// Track when entering NODATA state
		if (status == STAT.NODATA && oldStatus != STAT.NODATA)
		{
			// Save last good ECU state before NODATA
			if (oldStatus == STAT.CONNECTED || oldStatus == STAT.ECU_DETECTED || oldStatus == STAT.ECU_SELECTED)
			{
				lastGoodState = oldStatus;
				log.info("Saved last good state before NODATA: " + lastGoodState);
			}
			nodataStateStartTime = System.currentTimeMillis();
			consecutiveNodataCount++;
			log.info(String.format("Entered NODATA state (count: %d)", consecutiveNodataCount));
		}
		// Leaving NODATA - check if recovery is needed
		else if (oldStatus == STAT.NODATA && status != STAT.NODATA)
		{
			long duration = System.currentTimeMillis() - nodataStateStartTime;
			log.info(String.format("Recovered from NODATA after %dms (count: %d) -> %s",
				duration, consecutiveNodataCount, status));

			// If recovered to CONNECTED, reset counters
			if (status == STAT.CONNECTED)
			{
				consecutiveNodataCount = 0;
				lastGoodState = status;
			}
		}
		// Still in NODATA - check if recovery needed
		else if (status == STAT.NODATA && oldStatus == STAT.NODATA)
		{
			consecutiveNodataCount++;
			long duration = System.currentTimeMillis() - nodataStateStartTime;

			// Check if we need to trigger recovery
			if (consecutiveNodataCount >= NODATA_MAX_CONSECUTIVE || duration > NODATA_TIMEOUT_MS)
			{
				log.warning(String.format(
					"NODATA recovery triggered: count=%d, duration=%dms, lastGoodState=%s",
					consecutiveNodataCount, duration, lastGoodState));

				// Reset counters
				consecutiveNodataCount = 0;

				// Attempt recovery: restore to last good ECU state if known
				if (lastGoodState == STAT.ECU_DETECTED || lastGoodState == STAT.CONNECTED || lastGoodState == STAT.ECU_SELECTED)
				{
					log.info("Attempting to restore ECU connection state: " + lastGoodState);
					// Force status change to restored state
					this.status = lastGoodState;
					firePropertyChange(new PropertyChangeEvent(this, PROP_STATUS, oldStatus, this.status));
					return; // Skip normal status update below
				}
			}
		}
		// Leaving a good state - save it
		else if ((oldStatus == STAT.CONNECTED || oldStatus == STAT.ECU_DETECTED || oldStatus == STAT.ECU_SELECTED) &&
		         status != STAT.NODATA)
		{
			// Reset NODATA counter when successfully connecting
			if (consecutiveNodataCount > 0)
			{
				log.info("Reset NODATA counter after successful connection");
				consecutiveNodataCount = 0;
			}
			lastGoodState = oldStatus;
		}

		// === Rapid Cycling Detection ===
		// Detect if we're stuck in any cycling loop (CONNECTED<->NODATA or NODATA<->CONNECTING)
		if ((oldStatus == STAT.CONNECTED && status == STAT.NODATA) ||
		    (oldStatus == STAT.NODATA && status == STAT.CONNECTED) ||
		    (oldStatus == STAT.NODATA && status == STAT.CONNECTING) ||
		    (oldStatus == STAT.CONNECTING && status == STAT.NODATA))
		{
			long now = System.currentTimeMillis();

			// Start new detection window if needed
			if (cycleDetectionWindowStart == 0 || (now - cycleDetectionWindowStart) > CYCLE_DETECTION_WINDOW_MS)
			{
				cycleDetectionWindowStart = now;
				rapidCycleCount = 1;
			}
			else
			{
				rapidCycleCount++;

				// Check if we've exceeded the cycle threshold
				if (rapidCycleCount >= MAX_RAPID_CYCLES)
				{
					long windowDuration = now - cycleDetectionWindowStart;
					log.severe(String.format(
						"EXCESSIVE CYCLING DETECTED: %d transitions in %dms - adapter stuck in loop! Forcing disconnect...",
						rapidCycleCount, windowDuration));

					// Reset counters
					rapidCycleCount = 0;
					cycleDetectionWindowStart = 0;
					consecutiveNodataCount = 0;

					// Force disconnect by setting ERROR status
					// This will trigger the service layer to disconnect and allow manual reconnection
					this.status = STAT.ERROR;
					firePropertyChange(new PropertyChangeEvent(this, PROP_STATUS, oldStatus, this.status));
					return; // Exit early - don't process normal status update
				}
			}
		}
		// Reset cycle counter if we achieve stable connection for a while
		else if (status == STAT.CONNECTED && oldStatus == STAT.CONNECTED)
		{
			long now = System.currentTimeMillis();
			if (cycleDetectionWindowStart > 0 && (now - cycleDetectionWindowStart) > CYCLE_DETECTION_WINDOW_MS)
			{
				// Been stable for a while, reset cycle detection
				rapidCycleCount = 0;
				cycleDetectionWindowStart = 0;
			}
		}

		this.status = status;
		if (status != oldStatus)
		{
			log.info("Status change: " + oldStatus + "->" + status);
			// ECUs detected -> send identified ECU addresses
			if (status == STAT.ECU_DETECTED)
			{
				firePropertyChange(
					new PropertyChangeEvent(this, PROP_ECU_ADDRESS, null, ecuAddresses));
			}

			// now fire regular status change
			firePropertyChange(new PropertyChangeEvent(this, PROP_STATUS, oldStatus, status));
		}
	}
}
