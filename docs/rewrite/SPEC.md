# OBD-Droid protocol layer: behaviour specification

Bead: obd-4nc (rewrite step 1). Written 2026-11-16. No code changes go with this spec.
It describes **what** the new protocol layer must do. It is the input for rewrite
step 2: an implementation owned entirely by OBD-Droid.

## 0. How this spec was written (provenance)

- **Sources.** This spec was written from these sources only:
  - the public standards and datasheets listed in section 1;
  - the documented responses of the obdsim Teensy simulator (`obdsim` rig, `docs/MODE_0x.md`);
  - OBD-Droid's own call sites, i.e. code written for OBD-Droid that uses the protocol
    layer (`scan/`, `services/` except the four `*CommService` files, `features/`,
    `telemetry/`, `custompid/`, `ecu/EcuManager` and similar);
  - the app's settings screen.
- **What the author did not read.** The author did not open the bodies of the derived
  files listed in `PROVENANCE.md` section 3 (`ElmProt`, `ObdProt`, `ProtoHeader`,
  `ProcessVariables`, `EcuDataItem(s)`, `Conversions`, the `*CommService` files and the
  rest). Contact with them was limited to:
  - automated line-overlap counts;
  - the 22-line file header of `ElmProt`, read to establish licence and authorship;
  - the first two rows of `pids.csv`, read to confirm its column layout.
- **Structure.** The section structure, interface names and decoding model below are
  new. They follow the order of the standards, not the order of any existing class.
  Where this spec names current classes (section 13 only), it does so to say which call
  sites must move; it does not describe how those classes work.
- **Wording of formulas.** Formulas are the published ones. Each row cites the PID in
  SAE J1979-DA / ISO 15031-5 and is written in this document's own words and notation.
- **Not a strict clean room.** The same agent also produced `PROVENANCE.md` and so has
  seen file names and statistics of the derived code. See PROVENANCE.md question 4.

## 1. Public references

| Ref | Document | Used for |
|---|---|---|
| J1979 | SAE J1979, *E/E Diagnostic Test Modes* (and the equivalent ISO 15031-5) | services 01–0A, request/response formats, freeze frame, Service 09 data |
| J1979-DA | SAE J1979-DA, *Digital Annex of E/E Diagnostic Test Modes* | PID / TID / MID / INFOTYPE numbers, scaling and units |
| J2012 | SAE J2012 / ISO 15031-6, *Diagnostic Trouble Code definitions* | two-byte DTC encoding; generic code meanings |
| 15765-2 | ISO 15765-2, *Diagnostic communication over CAN – Transport protocol (ISO-TP)* | single/first/consecutive/flow-control frames |
| 15765-4 | ISO 15765-4, *Requirements for emissions-related systems* (OBD on CAN) | 11-bit 7DF/7E0–7E7/7E8–7EF and 29-bit 18DB33F1/18DA..F1 IDs, bit rates, P2 timing |
| 9141 | ISO 9141-2, *CARB requirements for interchange of digital information* | K-line 10.4 kbaud, 5-baud init, header format and checksum |
| 14230 | ISO 14230-4, *Keyword Protocol 2000 for emissions-related systems* | K-line fast init / 5-baud init, header format |
| J1850 | SAE J1850 (PWM 41.6 kbaud, VPW 10.4 kbaud) | older GM/Ford buses, reached only through the adapter |
| 14229 | ISO 14229-1, *Unified diagnostic services* | negative response `7F <sid> <nrc>` and NRC meanings (11, 12, 22, 31, 78) |
| ELM327 | Elm Electronics, *ELM327 OBD to RS232 Interpreter* datasheet (ELM327DSL) | AT command set, protocol numbers, response texts, prompt, timeouts |
| SPP | Bluetooth SIG, *Serial Port Profile*; assigned UUID `00001101-0000-1000-8000-00805F9B34FB` | Bluetooth Classic transport |
| BLE | Bluetooth Core Spec, GATT | Bluetooth Low Energy adapters (vendor services, see 3.2) |
| CFR565 | 49 CFR Part 565, *Vehicle Identification Number requirements* | VIN structure and check digit |
| SP811 | NIST Special Publication 811, *Guide for the Use of the SI* | metric → US unit conversion factors |
| SIM | obdsim rig, `docs/MODE_01.md`, `MODE_02.md`, `MODE_03.md`, `MODE_04.md`, `MODE_09.md` (MIT) | bench test vectors |

## 2. The interface the app needs

The app needs one long-lived **vehicle link** per connection, plus a value store that
UI and features observe. The names below are proposals for step 2; any naming is fine
if the capabilities are all present.

### 2.1 Capabilities (from the app's call sites)

| # | Capability | Why the app needs it (call sites) |
|---|---|---|
| C1 | Open / close a link over Bluetooth Classic, BLE, USB serial or TCP | adapter picker and auto-reconnect (`UnifiedAdapterSelectionActivity`, `StateManager`, `MainActivity`) |
| C2 | Observable link state (section 10) with change events | 20 files branch on the state: `ScanOrchestrator`, `FaultCodeService`, `EcuDiscoveryService`, `VehicleManager`, footers and activities |
| C3 | Adapter initialisation with progress callbacks | `obd/InitializationManager`, `InitProgressListener` show step-by-step progress |
| C4 | Protocol auto-detect, plus a user-preferred protocol | settings and the protocol picker |
| C5 | Supported-PID discovery per service and per ECU, cached for the session | live-data list, `UnsupportedModeHelper`, `EcuManager`, scan stages |
| C6 | Continuous polling of a chosen set of Service 01 PIDs, with per-PID update period, an optional single-PID focus mode, and change events per value | live data, dashboard, chart, CSV logging, MQTT telemetry, track mode, fuel economy |
| C7 | One-shot requests: service + optional PID (+ freeze-frame number), returning decoded values or a typed error | scan stages, CoPilot tools, emissions, VIN helper |
| C8 | Read stored (03), pending (07) and permanent (0A) DTCs, decoded, with which ECU reported each | `FaultCodeService`, `FaultCodeStage`, `PendingDtcStage`, `PermanentDtcStage` |
| C9 | Clear DTCs (04), only after explicit user confirmation, reporting acceptance or refusal | fault-code screen, `ClearFaultCodesTool` |
| C10 | Freeze frame: list frames, read a frame's PIDs, snapshot all supported PIDs of a frame, cache the last snapshot | `FreezeFrameManager`, `FreezeFrameStage` |
| C11 | Monitor / test results: Service 01 PID 01 and 41 readiness, Service 06 results | `MonitorTestStage`, emissions screen |
| C12 | Vehicle information (Service 09): VIN, calibration IDs, CVNs, ECU name, in-use performance counters, cached per session | `VehicleInfoStage`, `VinDataHelper`, `EcuDiscoveryService` |
| C13 | Multi-ECU: send with headers on, get each response tagged with its ECU address, then restore headers off; select one ECU for later requests | `EcuDiscoveryService` (sends ATH1, 0904, 0906, 090A, ATH0), `EcuManager` |
| C14 | Raw pass-through: send an arbitrary AT or OBD command string and get the raw text lines, including headers, before any decoding | discovery, `StateManager` reset (ATZ, ATD, ATH0) |
| C15 | Adapter control: reset (ATZ), defaults (ATD), low-power sleep, close protocol | disconnect and cleanup paths |
| C16 | Settings honoured live (section 11) | settings screen |
| C17 | A demo/simulated source with the same interface | demo mode menu |
| C18 | Value metadata: label, description, units (metric or imperial), display format, min/max, mnemonic, colour | adapters, charts, gauges, CSV/MQTT field names |
| C19 | Custom and phone-sensor values (GPS, accelerometer, user formulas) in the same value store | `custompid/*`, `telemetry/*` |
| C20 | Thread-safe access; no blocking of the UI thread; cancellable requests | scan runs on background threads; UI observes |

### 2.2 Proposed shape (illustrative, not binding)

```
VehicleLink
  connect(Transport, LinkOptions) / disconnect()
  state(): LinkState                         + listener
  adapterInfo(): {identity, version, voltage?, protocol}
  ecus(): list of EcuAddress                 + select(EcuAddress | ALL)
  request(ObdRequest): ObdResult             // C7, typed errors (section 9)
  poll(PollPlan) / stopPolling()             // C6
  readDtcs(kind = STORED | PENDING | PERMANENT): list of Dtc
  clearDtcs(confirmationToken): ClearResult
  freezeFrame(frame): map PID → DecodedValue
  vehicleInfo(): VehicleInfo                 // VIN, CALIDs, CVNs, ECU names
  raw(command, timeout): list of String      // C14
  addRawListener(cb)                         // lines before header stripping

ValueStore            // observed by UI; keyed by (service, pid, ecu)
  get / observe / clear(service)
  register(ValueDefinition)                  // C19: custom + sensor values

ValueDefinition        // data-driven, loaded from OBD-Droid's own table
  service, pid, byteOffset, byteLength, bitOffset?, bitLength?,
  formula (section 6), unitMetric, unitImperial?, format, min, max,
  mnemonic, label, description, pollPeriodMs
```

## 3. Transport

All transports present a byte stream to the adapter session. The session is the same
for every transport.

### 3.1 Bluetooth Classic (SPP)

- Connect an RFCOMM socket to the Serial Port service UUID
  `00001101-0000-1000-8000-00805F9B34FB` [SPP].
- If the secure socket fails, retry once with an insecure socket. Many clones have no
  MITM-capable pairing.
- Require the Android 12+ runtime permissions `BLUETOOTH_CONNECT` and
  `BLUETOOTH_SCAN`; on older releases, location.
- Treat a socket read returning -1, or an IOException, as **link lost** (section 10).

### 3.2 Bluetooth Low Energy

- There is no standard OBD GATT service. Adapters expose a vendor service with one
  characteristic that notifies (adapter → phone) and one that is written (phone →
  adapter). Commonly observed service UUIDs:
  - `FFE0` (characteristic `FFE1` both ways);
  - `FFF0` (`FFF1` notify, `FFF2` write);
  - `18F0` (`2AF0` notify, `2AF1` write).
- Discover by characteristic properties (one NOTIFY or INDICATE, one WRITE or
  WRITE_NO_RESPONSE), not by UUID alone.
- Write in chunks of at most (MTU − 3) bytes.
- Reassemble notifications into a byte stream before line parsing.

### 3.3 USB serial

- USB-serial bridges (FTDI, CP210x, CH34x, PL2303, CDC-ACM) are opened through
  usb-serial-for-android (MIT).
- Default line settings 38400 8-N-1 [ELM327 default]. If no prompt arrives, also try
  9600 and 115200.
- The USB device filter must be rebuilt from the chip vendors' published
  vendor/product IDs (see PROVENANCE section 5).

### 3.4 Wi-Fi (TCP)

- Plain TCP to a user-supplied host and port. Adapter manuals commonly default to
  `192.168.0.10:35000`.
- Connect timeout 5 s.
- A read of -1 means link lost.

### 3.5 Demo source

An in-process fake adapter that answers from a transcript table. The same table is used
by the unit tests (section 14).

## 4. Adapter session (ELM327 command set)

### 4.1 Framing rules [ELM327]

- **Commands.** Commands are ASCII, terminated by a carriage return (`\r`). The adapter
  ignores spaces and is case-insensitive.
- **End of reply.** A reply ends with the prompt character `>`. Everything before the
  prompt, split on `\r` (and `\n` if linefeeds are on), is the reply. Empty lines are
  discarded.
- **One request at a time.** Never send a new command before the previous prompt. A
  character sent while the adapter is still receiving aborts the request, and the
  adapter answers `STOPPED`.
- **Echo.** If echo is on, the first line repeats the command. Drop it.
- **Hex parsing.** Hex payload lines may have spaces (`41 0C 1A F8`) or not
  (`410C1AF8`, after `ATS0`). The parser must accept both.

### 4.2 Initialisation sequence

Each step reports progress (C3). The **reply timeout** applies to each step; if it
passes without a prompt, the step fails.

| Step | Command | Expect | Reply timeout | On failure |
|---|---|---|---|---|
| 1 | (none: drain input) | — | 200 ms of silence | — |
| 2 | `ATZ` (full reset) | banner line starting `ELM327` (version text after it), then `>` | 2 s | try `ATWS`; if still nothing, fail **adapter not responding** |
| 3 | `ATE0` | `OK` (the echo of `ATE0` itself may appear once) | 1 s | continue |
| 4 | `ATL0` | `OK` | 1 s | continue |
| 5 | `ATS1` or `ATS0` | `OK` or `?` | 1 s | record "spaces control unsupported" (v1.0–1.2) |
| 6 | `ATH0` | `OK` | 1 s | continue |
| 7 | `ATAT1` / `ATAT2` / `ATAT0` per setting | `OK` or `?` | 1 s | record unsupported |
| 8 | `ATSP0`, or `ATSPh` / `ATTPh` for a preferred protocol | `OK` | 1 s | fall back to `ATSP0` |
| 9 | user custom init commands, one per line (setting `elm_custom_init_cmds`) | any | 2 s each | log, continue |
| 10 | `ATI`, `AT@1` (optional), `ATRV` | identity, description, voltage | 1 s each | optional |
| 11 | `0100` | `41 00 …` (after optional `SEARCHING...`) | up to 15 s while `SEARCHING...` is shown | section 9 |
| 12 | `ATDPN` | protocol number, `A` prefix = auto-detected | 1 s | — |
| 13 | supported-PID discovery (5.1) and Service 09 PID 02 (VIN) | | | partial results allowed |

Rules:

- Commands that the user has listed in the "disable commands" setting
  (`elm_cmd_disable`) are skipped.
- A `?` reply marks a command as unsupported **for this adapter** for the rest of the
  session. It is never an init failure, except at step 2.
- Clone adapters often claim `v1.5` or `v2.1` without implementing that version's
  commands. Use the version text only as a hint; rely on actual `?` replies.

### 4.3 Protocol numbers [ELM327]

| No. | Protocol | Notes |
|---|---|---|
| 0 | automatic | adapter searches 1→9 |
| 1 | SAE J1850 PWM 41.6 kbaud | |
| 2 | SAE J1850 VPW 10.4 kbaud | |
| 3 | ISO 9141-2 (5-baud init, 10.4 kbaud) | header `48 6B xx`, checksum byte |
| 4 | ISO 14230-4 KWP (5-baud init) | |
| 5 | ISO 14230-4 KWP (fast init) | |
| 6 | ISO 15765-4 CAN 11-bit, 500 kbaud | most post-2008 vehicles |
| 7 | ISO 15765-4 CAN 29-bit, 500 kbaud | |
| 8 | ISO 15765-4 CAN 11-bit, 250 kbaud | |
| 9 | ISO 15765-4 CAN 29-bit, 250 kbaud | |
| A | SAE J1939 CAN 29-bit, 250 kbaud | heavy duty; not OBD-II |
| B, C | user CAN 1, 2 | |

`ATDPN` returns the number, prefixed `A` when auto-detected. `ATDP` returns a readable
name for display.

### 4.4 Timing

- **Base timeout.** `ATST hh` sets the adapter's reply wait to hh × 4 ms [ELM327]. The
  default is about 200 ms.
- **Adaptive timing (setting `adaptive_timing_mode`):**
  - **adapter**: `ATAT1` (or `ATAT2`, more aggressive), and let the adapter learn.
  - **app**: keep `ATAT0` and a fixed `ATST`. The app measures each ECU's response time
    and lowers `ATST` toward (worst observed + margin), never below 20 ms. It raises the
    timeout again after any `NO DATA` on a PID that answered before.
  - **off**: `ATAT0`, default `ATST`.
- **App-side timeouts.** The app-side wait for the `>` prompt is always longer than the
  adapter timeout: adapter timeout + 1 s for normal requests, 15 s during protocol search.
- **Response pending.** A `7F xx 78` (response pending [14229]) resets the app-side wait.
  After 5 consecutive `78` replies, give up with **timeout**.

### 4.5 Headers and multiple ECUs

- **Default.** With headers off (`ATH0`), replies are payload only. Multiple ECUs show
  as multiple lines, and the source of each is unknown.
- **Discovery (C13).**
  1. Turn headers on (`ATH1`) and send the request.
  2. Parse each line's header:

     | Bus | Header form | ECU address |
     |---|---|---|
     | CAN 11-bit [15765-4] | `7E8`–`7EF` | that ID |
     | CAN 29-bit [15765-4] | `18 DA F1 xx` | `xx` |
     | K-line / J1850 [9141, 14230] | 3 header bytes, ECU = 3rd; the last byte of the line is a checksum (sum mod 256) to verify and drop | 3rd header byte |

  3. With CAN headers on, the PCI byte (length or frame type) is shown and must be
     interpreted (4.6).
  4. **Always** restore `ATH0` afterwards, on success, failure or cancellation.
- **Selecting one ECU.** For CAN: `ATSH 7E0`–`7E7` to send physically, plus optionally
  `ATCRA 7E8` to filter replies. `ATSH 7DF` / `ATCRA` (no argument) restores
  functional addressing.

### 4.6 Multi-frame replies [15765-2, ELM327]

- **Headers off.** With headers off and CAN auto-formatting on (default `ATCAF1`), a
  multi-frame reply comes as:
  - a byte-count line (3 hex digits, e.g. `014`);
  - then lines prefixed `0:`, `1:`, … `F:`; the index wraps after `F`.
  - Concatenate in index order and cut to the byte count.
- **Headers on.** Frames carry their PCI:
  - `10 LL` (first frame, 12-bit length);
  - `2n` (consecutive frame, sequence n = 1…F then 0);
  - `0L` (single frame).
  - Reassemble per ECU address. Report a missing or duplicate sequence number as
    **malformed response**, for that ECU only.
- **Non-CAN Service 09.** Service 09 data arrives as numbered messages
  (`49 02 01 …` to `49 02 05 …`), each with 4 data bytes. Order by message number and
  strip leading `00` fill (vector M09-03).
- **Flow control.** The adapter sends flow-control frames itself. The app never sends
  `30 …` frames through the ELM interface.

## 5. OBD services

Request = service byte + parameters. Positive response = service + 0x40 [J1979].

| Service | Request | Response | Notes |
|---|---|---|---|
| 01 current data | `01 pid` (CAN: up to 6 PIDs in one request) | `41 pid data…` (repeated per PID) | decode with section 6 |
| 02 freeze frame | `02 pid frame` | `42 pid frame data…` | see 5.3 for the simulator variant without the frame byte |
| 03 stored DTCs | `03` | CAN: `43 count dtc…`; K-line/J1850: `43 dtc dtc dtc` per message, `00 00` = fill | 7.1 |
| 04 clear | `04` | `44`, or `7F 04 nrc` | 7.3 |
| 05 O2 monitor results | `05 tid sensor` | `45 …` | non-CAN only; low priority |
| 06 monitor results | `06 mid` (CAN), `06 tid` (non-CAN) | CAN: `46 mid tid uasid value min max` (repeated) | MID/TID and unit-and-scaling IDs from J1979-DA; supported-MID bitmaps at `00`, `20`, … |
| 07 pending DTCs | `07` | as 03, with `47` | |
| 08 control operation | `08 tid data` | `48 …` | not used by the app today; keep as raw only |
| 09 vehicle info | `09 infotype` | CAN: `49 infotype count data…` | section 8 |
| 0A permanent DTCs | `0A` | as 03, with `4A` | CAN only |

### 5.1 Supported-PID discovery

- **The request.** `01 00` returns a 4-byte bitmap [J1979-DA PID 00]: the
  most-significant bit of the first byte = PID 01, the least-significant bit of the
  fourth byte = PID 20.
- **Walking the ranges.** If PID 20 is set, request `01 20` (covering 21–40), then
  `01 40` and so on, up to `01 E0`.
- **Same scheme elsewhere.** Service 02 (frame 0), Service 06 (MID 00) and Service 09
  (INFOTYPE 00) use the same bitmap scheme.
- **Multiple ECUs.** Keep each ECU's bitmap separately; the union drives the PID list.
  Vectors M01-01, M01-02, SIM-01, SIM-02, SIM-07, SIM-12.
- **Caching.** Cache per vehicle (VIN, or adapter + protocol if no VIN) for 24 h, so
  reconnecting skips discovery. The cache is invalidated by a protocol change.

### 5.2 Polling (C6)

- **Order.** Poll PIDs round-robin, weighted by each PID's period. Period comes from
  the definition or the user; default 1 s, fast PIDs (0C, 0D, 11) 100–200 ms.
- **Batching.** On CAN, batch up to 6 Service 01 PIDs per request when the ECU
  accepted a multi-PID request once. Otherwise fall back to single PIDs.
- **Focus mode.** One PID is polled exclusively, e.g. while a chart is full screen.
- **Error tolerance (setting `data_disable_max`).** After N consecutive `NO DATA`
  replies or negative responses for one PID, stop polling that PID for the session and
  mark it **unavailable**. Default N = 3. A user action can re-enable it.
- **Priority.** One-shot requests (C7–C12) pre-empt polling between two requests.
  They never interrupt a request in flight.

### 5.3 Freeze frame (C10)

- **Which DTC stored the frame.** Read `02 02 frame` first. A non-zero DTC means the
  frame exists (vectors M02-01, M02-03).
- **Reading the frame.** Read the frame's supported PIDs (`02 00 frame`), then each
  supported PID. Decode exactly as Service 01 (same formulas).
- **Simulator variant.** The obdsim bench simulator replies without the frame byte
  (`42 pid data`, vectors SIM-07 to SIM-09).
  - Accept both: if payload length = 2 + the PID's data length, there is no frame byte.
  - If it is 3 + data length, byte 3 is the frame.
  - Otherwise the reply is **malformed**.
- **Caching.** Cache the last full snapshot with its timestamp.

## 6. Decoding values

### 6.1 Notation

- **Bytes.** A, B, C, D… are the data bytes after `41 pid` (or `42 pid frame`).
  `AB` = 256·A + B; `ABCD` = 2²⁴·A + 2¹⁶·B + 2⁸·C + D.
- **Signed values.** `s(AB)` is AB read as a two's-complement 16-bit value.
- **Bits.** `A7` is bit 7 (the most significant bit) of A.

### 6.2 Service 01 / 02 PID formulas

Each row is from J1979-DA, PID as listed (equivalent ISO 15031-5 definitions). Ranges
are those implied by the byte widths.

| PID | Name | Bytes | Formula | Unit |
|---|---|---|---|---|
| 00, 20, 40, 60, 80, A0, C0, E0 | PIDs supported (next 32) | 4 | bitmap, 5.1 | — |
| 01 | Monitor status since DTCs cleared | 4 | A7 = MIL; A6..A0 = DTC count; B3 = 1 compression / 0 spark ignition; B0–B2 supported & B4–B6 incomplete for misfire, fuel, components; C = supported, D = incomplete for the ignition-type-specific monitors (spark: catalyst, heated catalyst, EVAP, secondary air, —, O2 sensor, O2 heater, EGR/VVT) | — |
| 02 | DTC that stored freeze frame (Service 02 only) | 2 | DTC (7.1); 0000 = none | — |
| 03 | Fuel system status | 2 | A = system 1, B = system 2: 1 open loop (not ready), 2 closed loop, 4 open loop (load/decel), 8 open loop (fault), 16 closed loop (feedback fault) | enum |
| 04 | Calculated engine load | 1 | A·100/255 | % |
| 05 | Engine coolant temperature | 1 | A − 40 | °C |
| 06–09 | Short/long term fuel trim, bank 1/2 | 1 | A·100/128 − 100 | % |
| 0A | Fuel pressure (gauge) | 1 | 3·A | kPa |
| 0B | Intake manifold absolute pressure | 1 | A | kPa |
| 0C | Engine speed | 2 | AB / 4 | rpm |
| 0D | Vehicle speed | 1 | A | km/h |
| 0E | Timing advance (cyl. 1) | 1 | A/2 − 64 | ° before TDC |
| 0F | Intake air temperature | 1 | A − 40 | °C |
| 10 | MAF air flow rate | 2 | AB / 100 | g/s |
| 11 | Absolute throttle position | 1 | A·100/255 | % |
| 12 | Commanded secondary air status | 1 | enum (1 upstream, 2 downstream, 4 atmosphere/off, 8 diagnostics) | enum |
| 13 | O2 sensors present (2 banks) | 1 | bits 0–3 bank 1 S1–S4, bits 4–7 bank 2 S1–S4 | bitmap |
| 14–1B | O2 sensor 1–8 voltage + short-term trim | 2 | V = A / 200; trim = B·100/128 − 100; B = FF → sensor not used for trim | V, % |
| 1C | OBD standard this vehicle conforms to | 1 | enum (1 OBD-II CARB, 2 OBD EPA, 3 OBD + OBD-II, 6 EOBD, …) | enum |
| 1D | O2 sensors present (4 banks) | 1 | 2 bits per bank | bitmap |
| 1E | Auxiliary input status | 1 | A0 = PTO active | flag |
| 1F | Run time since engine start | 2 | AB | s |
| 21 | Distance travelled with MIL on | 2 | AB | km |
| 22 | Fuel rail pressure (relative to manifold vacuum) | 2 | AB · 0.079 | kPa |
| 23 | Fuel rail gauge pressure (diesel / direct injection) | 2 | AB · 10 | kPa |
| 24–2B | O2 sensor 1–8, equivalence ratio + voltage | 4 | ratio = AB · 2/65536; V = CD · 8/65536 | —, V |
| 2C | Commanded EGR | 1 | A·100/255 | % |
| 2D | EGR error | 1 | A·100/128 − 100 | % |
| 2E | Commanded evaporative purge | 1 | A·100/255 | % |
| 2F | Fuel tank level input | 1 | A·100/255 | % |
| 30 | Warm-ups since codes cleared | 1 | A | count |
| 31 | Distance since codes cleared | 2 | AB | km |
| 32 | EVAP system vapour pressure | 2 | s(AB) / 4 | Pa |
| 33 | Absolute barometric pressure | 1 | A | kPa |
| 34–3B | O2 sensor 1–8, equivalence ratio + current | 4 | ratio = AB · 2/65536; I = CD/256 − 128 | —, mA |
| 3C–3F | Catalyst temperature B1S1, B2S1, B1S2, B2S2 | 2 | AB/10 − 40 | °C |
| 41 | Monitor status this drive cycle | 4 | as PID 01 bytes B–D (A reserved) | — |
| 42 | Control module voltage | 2 | AB / 1000 | V |
| 43 | Absolute load value | 2 | AB·100/255 | % |
| 44 | Commanded equivalence ratio | 2 | AB · 2/65536 | — |
| 45 | Relative throttle position | 1 | A·100/255 | % |
| 46 | Ambient air temperature | 1 | A − 40 | °C |
| 47–4B | Absolute throttle B, C; accelerator pedal D, E, F | 1 | A·100/255 | % |
| 4C | Commanded throttle actuator | 1 | A·100/255 | % |
| 4D | Time run with MIL on | 2 | AB | min |
| 4E | Time since codes cleared | 2 | AB | min |
| 4F | Max equivalence ratio, O2 voltage, O2 current, MAP | 4 | A; B V; C mA; D·10 kPa | mixed |
| 50 | Max MAF | 4 | A·10 (B–D reserved) | g/s |
| 51 | Fuel type | 1 | enum (1 gasoline, 2 methanol, 3 ethanol, 4 diesel, 5 LPG, 6 CNG, 8 electric, …) | enum |
| 52 | Ethanol fuel % | 1 | A·100/255 | % |
| 53 | Absolute EVAP system vapour pressure | 2 | AB / 200 | kPa |
| 54 | EVAP system vapour pressure (wide range) | 2 | s(AB) | Pa |
| 55–58 | Short/long term secondary O2 trim, banks 1–4 | 1 or 2 | A·100/128 − 100 (and B for the paired bank) | % |
| 59 | Fuel rail absolute pressure | 2 | AB · 10 | kPa |
| 5A | Relative accelerator pedal position | 1 | A·100/255 | % |
| 5B | Hybrid battery pack remaining life | 1 | A·100/255 | % |
| 5C | Engine oil temperature | 1 | A − 40 | °C |
| 5D | Fuel injection timing | 2 | AB/128 − 210 | ° |
| 5E | Engine fuel rate | 2 | AB / 20 | L/h |
| 5F | Emission requirements | 1 | enum | enum |
| 61 | Driver's demand engine torque | 1 | A − 125 | % |
| 62 | Actual engine torque | 1 | A − 125 | % |
| 63 | Engine reference torque | 2 | AB | N·m |
| A6 | Odometer | 4 | ABCD / 10 | km |

PIDs not in this table are decoded as **raw hex** and listed by number. Adding a row is
a data change, not a code change. The table ships as OBD-Droid's own file (format to be
chosen in step 2), typed from J1979-DA. It is **not** derived from the current
`pids.csv`.

### 6.3 Units

- **Setting.** `measure_system` = metric (default) or imperial.
- **Conversions [SP811].**
  - km → mi ÷ 1.609344
  - km/h → mph ÷ 1.609344
  - °C → °F × 9/5 + 32
  - kPa → psi ÷ 6.894757
  - g/s → lb/min × 0.132277
  - L/h → US gal/h ÷ 3.785411784
  - Pa stays Pa (or inH₂O ÷ 249.089)
- **Where conversion happens.** Values are stored in metric and converted only for
  display, CSV and MQTT (the user's choice).

### 6.4 Custom and sensor values (C19)

- **Same store.** Custom PIDs (user formulas over A, B, C, D) and phone sensors (GPS,
  accelerometer) use the same value store and definitions as standard PIDs.
- **No wire requests.** They carry a reserved service/PID range (OBD-Droid's own
  `custom/` CSVs use `0x01 / 0xF1xx`), and the layer must never send these on the wire.
- **Formula evaluation.** The custom-PID formula evaluator belongs to OBD-Droid
  (`custompid/PidFormulaParser`, original code) and is reused as is.

## 7. Diagnostic trouble codes

### 7.1 Two-byte DTC decoding [J2012]

For bytes `H L`:

1. **Letter** = bits H7–H6: `00` P (powertrain), `01` C (chassis), `10` B (body),
   `11` U (network).
2. **First digit** = bits H5–H4 (0–3).
3. **Second character** = H3–H0 as one hex digit.
4. **Last two characters** = L as two hex digits.

Examples: `01 33` → P0133, `C1 00` → U0100 (vectors DTC-01 to DTC-06).

Rules:

- **Fill.** `00 00` is fill, not P0000. Skip it.
- **CAN count byte.** On CAN, the byte after `43`/`47`/`4A` is the number of DTCs.
  Trust the pairs actually received, and log a mismatch.
- **Multiple ECUs.** With several ECUs, keep the reporting ECU address on each DTC.
  The same code from two ECUs is two entries.

### 7.2 Descriptions

- **Source.** Descriptions come from the `dtc-database` submodule, keyed by code (and
  make, when known).
- **Rights.** PROVENANCE section 5 flags that its generic texts overlap AndrOBD's.
  Step 2 depends only on the lookup interface (code → description, severity), so the
  data can be replaced independently.
- **Unknown codes.** Unknown codes show as the code with "no description available".

### 7.3 Clearing (Service 04)

- **Confirmation.** Only after explicit user confirmation, naming the consequences:
  the MIL goes off, freeze frames are erased, readiness monitors reset, and an
  inspection may fail until drive cycles complete.
- **Engine running.** Many ECUs refuse while the engine runs: `7F 04 22` (vector
  M04-02). Report "turn engine off, ignition on" and do **not** retry automatically.
- **After `44`.** Re-read 03 / 07 / 0A and PID 01 to confirm, and clear cached freeze
  frames and DTCs.

### 7.4 Status

A DTC from 03 is **stored/confirmed**, from 07 **pending**, from 0A **permanent**. The MIL
state comes from PID 01 A7.

## 8. Vehicle information (Service 09) [J1979]

| INFOTYPE | Content | Decode |
|---|---|---|
| 00 | supported INFOTYPEs | bitmap (5.1) |
| 01 / 03 / 05 / 07 | message counts (non-CAN only) | count |
| 02 | VIN | 17 ASCII chars; CAN: `49 02 01` + 17 bytes; non-CAN: 5 messages, first has 3 bytes `00` fill |
| 04 | Calibration ID(s) | 16 bytes each, ASCII, NUL-padded; count byte says how many |
| 06 | CVN(s) | 4 bytes each, shown as 8 hex digits |
| 08 / 0B | In-use performance tracking (spark / compression) | 16-bit counters, pairs of numerator/denominator |
| 0A | ECU name | 20 bytes: 4-char acronym (NUL-padded) + `-` + 15-char name |

VIN validation [CFR565]:

- **Check digit.** Transliterate letters (A=1 … H=8, J=1 … N=5, P=7, R=9, S=2 … Z=9),
  weight positions 8,7,6,5,4,3,2,10,0,9,8,7,6,5,4,3,2, sum mod 11 (10 → `X`), and
  compare with position 9.
- **A failed check** is shown but flagged (some non-North-American VINs do not use the
  check digit).
- **Characters.** I, O and Q never appear.

## 9. Error cases

| Adapter or ECU reply | Meaning | Layer behaviour |
|---|---|---|
| no prompt within app timeout | adapter hung or link half-dead | one resend; then **timeout**. After 3 timeouts in a row, **link lost** |
| `?` | command not understood | mark command unsupported (4.2); return **unsupported command** |
| `NO DATA` | no ECU answered within the adapter timeout | **no data** for that request. Counts toward `data_disable_max` (5.2). Not a link error |
| `SEARCHING...` | protocol search in progress | informational; keep waiting (up to 15 s) |
| `UNABLE TO CONNECT` | no protocol found | **no vehicle bus**: ignition off or not OBD-II. Adapter remains usable |
| `BUS INIT: ...ERROR` | K-line init failed | **bus init failed**; offer retry and protocol choice |
| `CAN ERROR` | CAN bus error / wrong bit rate | **bus error**; re-run detection once |
| `BUS ERROR`, `FB ERROR`, `DATA ERROR`, `<DATA ERROR`, `ERR xx` | electrical or framing problem | **bus error**; drop the reply |
| `BUFFER FULL` | adapter overflow | slow polling (double periods); log |
| `STOPPED` | request interrupted | resend once |
| `LV RESET`, `ACT ALERT`, `LP ALERT` | low voltage / inactivity / low-power events | **adapter reset**: re-run init |
| `7F sid nrc` | ECU negative response [14229] | 11 service not supported; 12 sub-function not supported; 22 conditions not correct; 31 out of range (PID unsupported); 78 pending (4.4). Return **negative response(nrc)** |
| PCI / length inconsistent, missing ISO-TP frame, bad K-line checksum | malformed | **malformed response** for that ECU; never throws out of the layer |

"Reset adapter on errors" (`elm_reset_on_nrc`) is a count. After that many consecutive
**bus error**, **timeout** or **negative response** results, the layer re-runs the init
sequence (4.2). 0 = never.

## 10. Link states

The app needs these observable states. The names are this spec's own.

| State | Entered when | App behaviour today |
|---|---|---|
| DISCONNECTED | start; after `disconnect()`; after link lost | adapter picker / auto-reconnect |
| CONNECTING | transport opening | spinner |
| ADAPTER_INIT | transport open, init sequence running (progress events) | init progress UI |
| ADAPTER_READY | init done, no vehicle protocol yet | "connect ignition" hint |
| SEARCHING | protocol detection running | spinner |
| VEHICLE_CONNECTED | protocol detected, PID discovery done | live data and scans enabled |
| ECU_SELECTED | (optional) one ECU chosen for physical addressing | same as above, filtered |
| NO_VEHICLE_DATA | adapter OK but no ECU answers | retry banner |
| STOPPED | user stopped polling; link kept open | — |
| LINK_LOST | transport error | auto-reconnect (user setting) |

Transitions are delivered on a background thread. UI code must post to the main thread.

## 11. Settings the layer honours

| Key | Meaning |
|---|---|
| `adaptive_timing_mode` | off / adapter (ATAT1–2) / app-side (4.4) |
| `elm_custom_init_cmds` | extra commands sent at init step 9 |
| `elm_cmd_disable` | commands never to send |
| `elm_reset_on_nrc` | error count before automatic re-init (9) |
| `data_disable_max` | failures before a PID is suspended (5.2) |
| `data_items` | which values to show/poll |
| `measure_system` | metric / imperial (6.3) |
| preferred protocol (picker) | `ATSPh` instead of `ATSP0` |

## 12. Non-functional requirements

- **Threading.** One I/O thread per link and one request in flight; callers get futures
  or callbacks (C20).
- **Logging.** Log raw adapter traffic at debug level. Never log anything typed into
  custom-init fields that looks like a key. No VINs in release logs.
- **No network.** The layer never touches the network except a user-chosen Wi-Fi
  adapter.
- **Test isolation.** Tests run against transcripts; nothing in tests may open
  Bluetooth, USB or sockets.
- **Ownership.** The implementation must be new OBD-Droid code. No source from AndrOBD
  or any other GPL project. Libraries are allowed if their licence permits sale
  (MIT / Apache-2.0 / BSD; LGPL only as an unmodified dynamic dependency); list each one
  in `NOTICE`.

## 13. Migration map (current call sites → new capability)

This table names current derived classes only to show which call sites must move.

| Current use at call sites | New capability |
|---|---|
| `CommService.elm` singleton; `setService` / `getService` | `VehicleLink` + `poll()` / `request()` (C6, C7) |
| `ElmProt.STAT.*` checks in 20 files | `LinkState` (section 10) |
| `ObdProt.PidPvs` / `VidPvs` / `TidPvs` / `tCodes` listeners | `ValueStore.observe(service…)`, `readDtcs()` |
| `ObdProt.OBD_SVC_*` constants | service enum (section 5) |
| `EcuDataPv.FID_*` field reads (38 files) | `DecodedValue` + `ValueDefinition` fields (C18) |
| `EcuDataItems` / `EcuDataItem` / `Conversions` registration from `custompid/*`, `telemetry/*` | `ValueStore.register(ValueDefinition)` (C19) |
| `addRawTelegramListener`, `sendTelegram("ATH1")` | `addRawListener`, `raw()` (C13, C14) |
| `requestFreezeFrameSnapshot`, `getCachedFreezeFrame`, `setFreezeFrame_Id` | `freezeFrame(frame)` (C10) |
| `setFixedPid` / `resetFixedPid` | focus mode in `PollPlan` (5.2) |
| `setCustomInitCommands`, `disableCommands`, `setResetOnNrc`, `mAdaptiveTiming`, `setPreferredProtocol` | `LinkOptions` (section 11) |
| `runDemo` | demo transport (3.5) |
| reflection on `ElmProt` fields (3 sites) | a public accessor; reflection is not allowed |
| `ObdCodeList.setDatabaseInstance` | DTC description lookup interface (7.2) |

## 14. Test vectors

### 14.1 How the tests use these vectors

- **Unit tests.** Step 2 unit tests feed these through a transcript transport (no
  device). "Request" is what the layer sends. "Response bytes" is the decoded payload
  for decoder tests, or the literal adapter text (with `\r` and `>`) for parser tests
  (ELM-xx).
- **Rounding.** Expected values are computed from the formulas in section 6, not
  copied from any existing decoder. Values are rounded for display; tests should
  compare within ±0.01 of the last shown digit.

### 14.2 Simulator rows (SIM-xx)

- **Bench target.** The SIM-xx rows are the obdsim bench simulator's documented frames.
  Two of its documented decodes are wrong and are corrected here: the PID 00 bit listing
  (SIM-01) and the negative-response length (SIM-06).
- **Real-vehicle values not copied.** The simulator's VIN, calibration ID and CVN come
  from a real vehicle, so they are not reproduced. M09-01, M09-04 and M09-05 use the
  standard test VIN `1HGBH41JXMN109186` and invented values in the same frame layout.

### 14.3 Vector table

| # | Request | Response bytes | Expected decode | Source |
|---|---|---|---|---|
| M01-01 | 01 00 | 41 00 BE 3E B8 11 | Supported PIDs: 01, 03, 04, 05, 06, 07, 0B, 0C, 0D, 0E, 0F, 11, 13, 14, 15, 1C, 20 | J1979-DA PID 00 (bit-encoded support) |
| M01-02 | 01 20 | 41 20 80 01 A0 01 | Supported PIDs: 21, 30, 31, 33, 40 | J1979-DA PID 20 |
| M01-03 | 01 01 | 41 01 83 07 65 04 | MIL on; 3 emission DTCs; spark ignition; misfire, fuel system, components monitors supported and complete; catalyst, EVAP, O2 sensor, O2 heater supported; EVAP incomplete, others complete | J1979-DA PID 01 |
| M01-04 | 01 01 | 41 01 00 07 65 00 | MIL off; 0 DTCs; all supported monitors complete | J1979-DA PID 01 |
| M01-05 | 01 03 | 41 03 02 00 | Fuel system 1: closed loop (O2 feedback); fuel system 2: not present | J1979-DA PID 03 |
| M01-06 | 01 04 | 41 04 7F | 49.80 % | J1979-DA PID 04 load |
| M01-07 | 01 05 | 41 05 7B | 83 °C | J1979-DA PID 05 coolant |
| M01-08 | 01 05 | 41 05 00 | -40 °C | J1979-DA PID 05 coolant (minimum) |
| M01-09 | 01 05 | 41 05 FF | 215 °C | J1979-DA PID 05 coolant (maximum) |
| M01-10 | 01 06 | 41 06 80 | 0.00 % | J1979-DA PID 06 STFT bank 1 |
| M01-11 | 01 06 | 41 06 00 | -100.00 % | J1979-DA PID 06 STFT (minimum) |
| M01-12 | 01 06 | 41 06 FF | 99.22 % | J1979-DA PID 06 STFT (maximum) |
| M01-13 | 01 0A | 41 0A 64 | 300 kPa | J1979-DA PID 0A fuel pressure (gauge) |
| M01-14 | 01 0B | 41 0B 21 | 33 kPa | J1979-DA PID 0B intake manifold absolute pressure |
| M01-15 | 01 0D | 41 0D 3C | 60 km/h | J1979-DA PID 0D vehicle speed |
| M01-16 | 01 0E | 41 0E 90 | 8.0 ° before TDC | J1979-DA PID 0E timing advance |
| M01-17 | 01 0F | 41 0F 46 | 30 °C | J1979-DA PID 0F intake air temperature |
| M01-18 | 01 11 | 41 11 FF | 100.00 % | J1979-DA PID 11 throttle position |
| M01-19 | 01 2F | 41 2F 80 | 50.20 % | J1979-DA PID 2F fuel tank level |
| M01-20 | 01 33 | 41 33 65 | 101 kPa | J1979-DA PID 33 barometric pressure |
| M01-21 | 01 45 | 41 45 33 | 20.00 % | J1979-DA PID 45 relative throttle position |
| M01-22 | 01 46 | 41 46 3C | 20 °C | J1979-DA PID 46 ambient air temperature |
| M01-23 | 01 52 | 41 52 1A | 10.20 % | J1979-DA PID 52 ethanol fuel % |
| M01-24 | 01 5C | 41 5C 82 | 90 °C | J1979-DA PID 5C engine oil temperature |
| M01-25 | 01 0C | 41 0C 1A F8 | 1726.00 rpm | J1979-DA PID 0C engine speed |
| M01-26 | 01 0C | 41 0C 00 00 | 0.00 rpm | J1979-DA PID 0C engine speed (minimum) |
| M01-27 | 01 0C | 41 0C FF FF | 16383.75 rpm | J1979-DA PID 0C engine speed (maximum) |
| M01-28 | 01 10 | 41 10 01 F4 | 5.00 g/s | J1979-DA PID 10 MAF air flow |
| M01-29 | 01 1F | 41 1F 01 2C | 300 s | J1979-DA PID 1F run time since engine start |
| M01-30 | 01 21 | 41 21 00 64 | 100 km | J1979-DA PID 21 distance with MIL on |
| M01-31 | 01 22 | 41 22 01 00 | 20.224 kPa | J1979-DA PID 22 fuel rail pressure (relative to manifold) |
| M01-32 | 01 23 | 41 23 00 64 | 1000 kPa | J1979-DA PID 23 fuel rail gauge pressure |
| M01-33 | 01 31 | 41 31 03 E8 | 1000 km | J1979-DA PID 31 distance since codes cleared |
| M01-34 | 01 3C | 41 3C 13 88 | 460.0 °C | J1979-DA PID 3C catalyst temperature B1S1 |
| M01-35 | 01 42 | 41 42 37 DC | 14.300 V | J1979-DA PID 42 control module voltage |
| M01-36 | 01 43 | 41 43 00 80 | 50.20 % | J1979-DA PID 43 absolute load value |
| M01-37 | 01 44 | 41 44 80 00 | 1.000 (lambda) | J1979-DA PID 44 commanded equivalence ratio |
| M01-38 | 01 4D | 41 4D 00 0A | 10 min | J1979-DA PID 4D time run with MIL on |
| M01-39 | 01 5E | 41 5E 03 20 | 40.00 L/h | J1979-DA PID 5E engine fuel rate |
| M01-40 | 01 14 | 41 14 5A 80 | O2 B1S1: 0.450 V; short-term trim 0.00 % | J1979-DA PID 14 (A×0.005 V; B×100/128−100 %) |
| M01-41 | 01 24 | 41 24 80 00 66 66 | O2 S1 wide-range: equivalence ratio 1.000; voltage 3.200 V | J1979-DA PID 24 |
| M01-42 | 01 34 | 41 34 80 00 80 00 | O2 S1 wide-range: equivalence ratio 1.000; current 0.00 mA | J1979-DA PID 34 |
| M01-43 | 01 1C | 41 1C 01 | OBD standard: OBD-II as defined by CARB | J1979-DA PID 1C (enumerated) |
| M01-44 | 01 51 | 41 51 01 | Fuel type: gasoline | J1979-DA PID 51 (enumerated) |
| M01-45 | 01 A6 | 41 A6 00 01 E2 40 | Odometer: 12345.6 km | J1979-DA PID A6 (32-bit ÷10) |
| M01-46 | 01 0C 0D (multi-PID, CAN only) | 41 0C 1A F8 0D 3C | rpm 1726.00; speed 60 km/h (one response carries both PIDs) | J1979 (CAN allows up to 6 PIDs per Service 01 request) |
| SIM-01 | 01 00 (functional 7DF) | 7E8: 06 41 00 BF BE B8 93 00 | Engine ECU supports: 01, 03, 04, 05, 06, 07, 08, 09, 0B, 0C, 0D, 0E, 0F, 11, 13, 14, 15, 19, 1C, 1F, 20 (NOTE: obdsim MODE_01.md mis-states this decode) | obdsim docs/MODE_01.md Ex.3, decoded per J1979-DA PID 00 |
| SIM-02 | 01 00 (functional 7DF) | 7E9: 06 41 00 18 00 00 00 | Transmission ECU supports: 04, 05; two ECUs present | obdsim docs/MODE_01.md Ex.3 |
| SIM-03 | 01 0C | 7E8: 04 41 0C 09 90 | 612.00 rpm | obdsim MODE_01.md Ex.1 |
| SIM-04 | 01 14 | 7E8: 04 41 14 46 FF | O2 B1S1 0.350 V; B=FF means sensor not used for trim (show trim as n/a, not 99.22 %) | obdsim MODE_01.md Ex.2; J1979-DA PID 14 |
| SIM-05 | 01 06 | 7E8: 03 41 06 7F | STFT B1 -0.78 % | obdsim MODE_01.md Ex.4 |
| SIM-06 | 01 FF | 7E8: 03 7F 01 FF 12 | PCI says 3 bytes, so payload is 7F 01 FF: negative response to service 01; NRC byte as framed = FF (the doc intends 12). Decoder must report 'negative response, service 01' and must not crash or treat FF as a PID | obdsim MODE_01.md Ex.5; ISO 15765-2 PCI; ISO 14229-1 negative response format |
| SIM-07 | 02 00 00 | 7E8: 06 42 00 E8 19 30 12 | Freeze-frame supported PIDs: 01, 02, 03, 05, 0C, 0D, 10, 13, 14, 1C, 1F. obdsim omits the frame-number byte; standard reply is 42 00 <frame> <4 bytes> | obdsim docs/MODE_02.md Ex.1; J1979 Service 02 |
| SIM-08 | 02 0C 00 | 7E8: 04 42 0C 09 9A | Freeze frame 0 rpm 614.50 (frame byte omitted by simulator) | obdsim MODE_02.md Ex.2 |
| SIM-09 | 02 05 01 | 7E8: 03 42 05 87 | Freeze frame 1 coolant 95 °C (frame byte omitted by simulator) | obdsim MODE_02.md Ex.3 |
| M02-01 | 02 02 00 | 42 02 00 01 00 | Freeze frame 0 was stored by DTC P0100 | J1979 Service 02 PID 02; SAE J2012 DTC encoding |
| M02-02 | 02 0C 00 | 42 0C 00 1A F8 | Freeze frame 0 rpm 1726.00 | J1979 Service 02 (reply = 42 PID frame data) |
| M02-03 | 02 02 00 | 42 02 00 00 00 | No freeze frame stored (DTC 0000) | J1979 Service 02 PID 02 |
| SIM-10 | 03 | 7E8: 02 43 00 | No stored DTCs | obdsim docs/MODE_03.md; J1979 Service 03 on CAN (count byte) |
| SIM-11 | 03 | 7E8: 06 43 02 01 00 02 00 | 2 DTCs: P0100, P0200 | obdsim MODE_03.md Ex.2 |
| DTC-01 | (DTC bytes) 01 33 | - | P0133 | SAE J2012 / ISO 15031-6 two-byte DTC encoding |
| DTC-02 | (DTC bytes) 41 23 | - | C0123 | SAE J2012 / ISO 15031-6 two-byte DTC encoding |
| DTC-03 | (DTC bytes) 81 18 | - | B0118 | SAE J2012 / ISO 15031-6 two-byte DTC encoding |
| DTC-04 | (DTC bytes) C1 00 | - | U0100 | SAE J2012 / ISO 15031-6 two-byte DTC encoding |
| DTC-05 | (DTC bytes) 9A 3F | - | B1A3F | SAE J2012 / ISO 15031-6 two-byte DTC encoding |
| DTC-06 | (DTC bytes) 30 00 | - | P3000 | SAE J2012 / ISO 15031-6 two-byte DTC encoding |
| M03-01 | 03 | 43 04 01 33 03 00 04 20 C1 00 (CAN, multi-frame: 10 0A 43 04 01 33 03 00 / 21 04 20 C1 00) | 4 DTCs: P0133, P0300, P0420, U0100 | J1979 Service 03; ISO 15765-2 first/consecutive frames |
| M03-02 | 03 | 43 01 33 00 00 00 00 (ISO 9141-2 / 14230-4, no count byte) | 1 DTC: P0133 (0000 pairs are fill, not codes) | J1979 Service 03 non-CAN format |
| M07-01 | 07 | 47 01 01 71 | 1 pending DTC: P0171 | J1979 Service 07 |
| M0A-01 | 0A | 4A 01 04 20 | 1 permanent DTC: P0420 | J1979 Service 0A |
| M04-01 | 04 | 44 | Clear accepted: DTCs, freeze frames, monitor status reset | J1979 Service 04; obdsim docs/MODE_04.md |
| M04-02 | 04 | 7F 04 22 | Clear refused: conditions not correct (e.g. engine running). Report, do not retry automatically | J1979 Service 04; ISO 14229-1 NRC 22 |
| NRC-01 | 01 0C | 7F 01 78 then 41 0C 1A F8 | 78 = response pending: keep waiting (extend timeout), then decode 1726.00 rpm | ISO 14229-1 NRC 78 / ISO 15765-4 |
| NRC-02 | 09 02 | 7F 09 11 | Service not supported by this ECU | ISO 14229-1 NRC 11 |
| NRC-03 | 01 0C | 7F 01 31 | Request out of range (PID not supported) | ISO 14229-1 NRC 31 |
| SIM-12 | 09 00 | 7E8: 06 49 00 55 40 10 00 | Vehicle-info PIDs: 02, 04, 06, 08, 0A, 14 | obdsim docs/MODE_09.md |
| M09-01 | 09 02 (CAN) | 10 14 49 02 01 31 48 47 / flow control 30 00 00 / 21 42 48 34 31 4A 58 4D / 22 4E 31 30 39 31 38 36 | VIN 1HGBH41JXMN109186 | J1979 Service 09 PID 02; ISO 15765-2. Frame layout as obdsim MODE_09.md, with the standard test VIN in place of the simulator's VIN |
| M09-02 | 0902 (ELM327, ATH0, CAF1) | 014\r0: 49 02 01 31 48 47\r1: 42 48 34 31 4A 58 4D\r2: 4E 31 30 39 31 38 36 | VIN 1HGBH41JXMN109186 (ignore '014' byte-count line and the 'n:' line indexes) | ELM327 datasheet: multi-line CAN responses |
| M09-03 | 09 02 (ISO 9141-2 / J1850) | 49 02 01 00 00 00 31 / 49 02 02 48 47 42 48 / 49 02 03 34 31 4A 58 / 49 02 04 4D 4E 31 30 / 49 02 05 39 31 38 36 | VIN 1HGBH41JXMN109186 (5 messages of 4 bytes; leading 00 fill dropped) | J1979 Service 09 non-CAN message-count format |
| VIN-01 | (VIN) 1HGBH41JXMN109186 | - | Check digit computed X, position 9 is X: valid | 49 CFR 565 (VIN check digit) |
| VIN-02 | (VIN) 1HGBH41J0MN109186 | - | Check digit computed X, position 9 is 0: invalid; show VIN but flag it | 49 CFR 565 |
| M09-04 | 09 04 | 10 13 49 04 01 54 45 53 / 21 54 43 41 4C 30 30 30 / 22 30 30 30 30 30 31 00 | Calibration ID 'TESTCAL000000001' (invented value) | J1979 Service 09 PID 04 (16-byte ASCII, NUL padded) |
| M09-05 | 09 06 | 49 06 01 12 34 AB CD | CVN 1234ABCD (invented value) | J1979 Service 09 PID 06 (4-byte hex) |
| SIM-13 | 09 0A | 10 17 49 0A 01 45 43 4D / 21 00 2D 45 6E 67 69 6E / 22 65 43 6F 6E 74 72 6F / 23 6C 00 00 00 00 00 00 | ECU name 'ECM-EngineControl' (acronym ECM, NUL-padded to 4 chars, '-' separator) | obdsim docs/MODE_09.md PID 0A; J1979 Service 09 PID 0A |
| ELM-01 | ATZ | \r\rELM327 v1.5\r\r> | Adapter reset; identity 'ELM327', version 1.5 (clones commonly report 1.5 or 2.1 regardless of real feature set) | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-02 | ATE0 | ATE0\rOK\r\r> | Echo off acknowledged (echo of the command itself may precede OK this once) | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-03 | ATSP0 | OK\r\r> | Automatic protocol search selected | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-04 | ATDPN | A6\r\r> | Auto mode, protocol 6 = ISO 15765-4 CAN 11-bit 500 kbaud | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-05 | ATDPN | 3\r\r> | Fixed protocol 3 = ISO 9141-2 | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-06 | ATDP | AUTO, ISO 15765-4 (CAN 11/500)\r\r> | Human-readable protocol name | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-07 | ATRV | 12.6V\r\r> | Supply voltage 12.6 V | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-08 | 0100 | SEARCHING...\r41 00 BE 3E B8 11\r\r> | Drop the SEARCHING... line, decode as M01-01 | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-09 | 0100 | SEARCHING...\rUNABLE TO CONNECT\r\r> | No OBD bus found: ignition off or unsupported vehicle; state = no vehicle, adapter OK | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-10 | 010C | NO DATA\r\r> | No ECU answered this PID: mark PID unavailable, do not treat as link loss | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-11 | 010C | CAN ERROR\r\r> | CAN bus fault or wrong bit rate: retry protocol detection | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-12 | 0100 | BUS INIT: ...ERROR\r\r> | K-line (ISO 9141/14230) initialisation failed | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-13 | ATXYZ | ?\r\r> | Command not understood: record command as unsupported on this adapter, continue | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-14 | 0100 | STOPPED\r\r> | Request interrupted because a character arrived before the reply finished: resend once | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-15 | 0100 | BUFFER FULL\r\r> | Adapter overflow: slow the request rate / reduce headers | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-16 | 0100 (ATH1) | 7E8 06 41 00 BE 3E B8 11\r7E9 06 41 00 18 00 00 00\r\r> | Two ECUs: 7E8 supports as M01-01; 7E9 supports 04, 05 | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-17 | 0100 (ATH1, 29-bit) | 18 DA F1 10 06 41 00 BE 3E B8 11\r\r> | ECU address 0x10 (last header byte), payload as M01-01 | ELM327 datasheet (Elm Electronics, ELM327DSL); ISO 15765-4 29-bit addressing 18DA<target><source> |
| ELM-18 | 010C (ATS0) | 410C1AF8\r\r> | Spaces off: same decode as M01-25 (1726.00 rpm) | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-19 | ATST19 | OK\r\r> | Reply timeout set to 0x19 × 4 ms ≈ 100 ms (datasheet step is 4 ms) | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| ELM-20 | 010C (ATH1, ISO 9141-2) | 48 6B 10 41 0C 1A F8 22\r\r> | Header 48 6B 10 (ECU 0x10), data 41 0C 1A F8, last byte = checksum (sum mod 256); 1726.00 rpm | ELM327 datasheet (Elm Electronics, ELM327DSL) |
| UNIT-01 | (unit) 60 km/h | - | 37.28 mph | NIST SP 811 conversion factors |
| UNIT-02 | (unit) 83 °C | - | 181.4 °F | NIST SP 811 conversion factors |
| UNIT-03 | (unit) 101 kPa | - | 14.65 psi | NIST SP 811 conversion factors |
| UNIT-04 | (unit) 5.00 g/s MAF | - | 0.661 lb/min | NIST SP 811 conversion factors |
| UNIT-05 | (unit) 40.00 L/h | - | 10.57 US gal/h | NIST SP 811 conversion factors |

Total: 109 vectors.

## 15. Open questions for step 2

1. **BLE support.** Is BLE support in scope for the first rewrite? The current app has no
   GATT code (no `BluetoothGatt` anywhere in `app/src/java`), so today it is Classic SPP only.
2. **VAG / KW1281.** Should VAG/KW1281 support stay? PROVENANCE section 5: its data is
   third-party.
3. **J1939 and UDS.** Should SAE J1939 (heavy duty) or UDS (Service 22, manufacturer
   PIDs) come in a later step? This spec only covers OBD-II services 01–0A.
4. **Data table format.** What file format should the new PID definition table use?
   It must be data, carry a J1979-DA reference per row, and be loadable by
   `custompid/*`.
