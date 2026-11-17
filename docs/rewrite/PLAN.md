# OBD-Droid: plan for the clean rewrite of the AndrOBD-derived code

Bead: obd-43g. Written 2026-11-16 against `main` at `54ed8e5`. This is the plan and the
specification only. It writes no replacement code. It is an engineering plan, not legal
advice. What a lawyer should still check is in section 7.

Companion documents:

- [`PROVENANCE.md`](PROVENANCE.md): where each derived file came from (obd-4nc).
- [`../protocol-spec.md`](../protocol-spec.md): what the new protocol layer must do,
  written from public standards (obd-4nc; moved and extended here).
- [`derived-paths.txt`](derived-paths.txt): the machine-readable list of derived paths
  that implementers must not open.

## 0. Summary

- **Owner decision (obd-86c, 2026-11-16).** OBD-Droid stays MIT. Every part derived
  from AndrOBD (GPL-3.0) is replaced by code written fresh, not edited or paraphrased
  from the GPL files.
- **Size.** 45 Java files are derived, 17,990 of the app's 66,642 Java lines (27%):
  - 25 protocol and decoding files (8,817 lines);
  - 4 transport files (1,172 lines);
  - 16 user-interface and helper files (8,001 lines).

  On top of these come the PID tables the decoder runs on (`pids.csv`, `conversions.csv`,
  `messages.csv`), six unused VAG/KW1281 tables, and 29 resource files: 24 layouts,
  menus, values and drawables, plus the shared strings, colours, dimensions, styles and
  manifest. Section 1 re-checks the obd-4nc inventory over the whole tree and history.
  It finds no further derived files.
- **The shape of the code decides the order.** The core is one inheritance chain:
  `ElmProt` extends `ObdProt`, which extends `ProtoHeader`, and `ObdProt` pulls in the
  whole derived data model. So the 24 core files cannot be deleted one at a time; they go
  together at one cutover (section 1.7). The phases before the cutover build the new
  engine beside the old one. They also move OBD-Droid's own code onto OBD-Droid's own
  interfaces, so the cutover becomes a switch plus deletions.
- **Phases** (section 4). Each phase leaves a working app.

  | Phase | Builds | Derived files deleted |
  |---|---|---|
  | P0 | test harness, transcripts, provenance gates, legacy baseline; removes unused derived data | the VAG folder (6 data files) |
  | P1 | transports (Bluetooth, USB, Wi-Fi, demo) | 5 files, 1,433 lines |
  | P2 | value model and OBD-Droid's own PID table; ~40 app files moved onto it | none (the core still needs the data model) |
  | P3 | the new engine: ELM327 session, framing, init, detection, polling, freeze frame | none (new engine is off by default) |
  | P4 | DTCs, vehicle info, monitors, multi-ECU, clear; app moved onto `VehicleLink` | none |
  | P5 | cutover to the new engine | 24 files, 8,556 lines, plus the three PID tables |
  | P6 | user-interface track (UI spec first) | 16 files, 8,001 lines, plus derived resources |
  | P7 | close-out: whole-tree gates, NOTICE, data provenance, legal review | anything left |

- **Estimate.** About 35–45 worker sessions, plus one or two bench sessions by the owner,
  plus the lawyer's time.
- **The clean-room rule** (section 5). Implementers work only from
  `docs/protocol-spec.md`, the public standards and the tests. They never open the derived
  files or AndrOBD. A separate integrator role does the wiring and the deletions and
  writes no protocol logic. A different worker than the spec writers implements each
  phase. Two automated gates check every rewrite merge request for text taken from
  AndrOBD.

## 1. Inventory

### 1.1 Method

Every check was automated or read-only. AndrOBD was cloned read-only into a scratch
folder outside the repository; nothing from it entered the repository.

1. **Licence headers.** A search of `app/src` for `GNU General`, `GPL`, `Copyright`,
   `fr3ts0n` and `AndrOBD`.
2. **First commit of each file.** `git log --follow --diff-filter=A` on a full clone of
   `Wal33D/OBD-Droid` (527 commits; the rig worktree is shallow).
3. **Line overlap with AndrOBD V2.6.16** (upstream `02871bfe`, the fork point), with the
   normalisation PROVENANCE.md section 2 describes. A low number does not prove a file
   is clean: structure and logic can survive rewording.
4. **Moved code.** `git blame -w -C -C -M` on every source file at `HEAD`. This finds
   lines moved or copied out of a derived file into a file created later, which the
   first-commit check misses. One caveat: commit `e094e2a` (2025-09-17, "Restore
   accidentally deleted library files") re-imported 12 AndrOBD library files (about
   1,800 lines) two days after the fork. Blame credits those lines to that commit, not
   to the fork, so blame counts were used only to find moved code, not to measure files.
5. **Upstream text added after the fork.** Every line added by every commit after the
   fork was compared with AndrOBD V2.6.16 and with current AndrOBD `master` (`7ad354b0`,
   2026-11-07). This would show code copied from a newer AndrOBD release, or copied in
   later.
6. **Structure.** The top-level class declaration lines of the derived files (name,
   `extends`, `implements`) were compared with upstream, and a graph built of which
   derived class names which other derived class. These were declaration lines only,
   not bodies.

### 1.2 Results in brief

- **Licence headers:** none in any file. The first commit (`f104466`, 2025-09-15)
  already had none (PROVENANCE.md section 1).
- **The derived list in PROVENANCE.md section 3 is complete.** No other file has a
  cluster of upstream lines.
- **Moved code:** the files OBD-Droid created three days after the fork
  (`obd/ErrorHandler`, `obd/InitializationManager`, `obd/InitProgressListener`,
  `obd/FreezeFrameManager`) carry no lines moved from derived files. They are ours.
- **`ui/adapters/DeviceAdapter`:** 37 of its lines trace back to the first commit, but
  to `ModernDeviceAdapter.java`. That file was OBD-Droid's own addition in that commit;
  it never existed in AndrOBD. Ours. The same holds for the 3 such lines in
  `CustomPidManagerActivity`.
- **`ui/activities/UnifiedAdapterSelectionActivity`:** 15 lines came from the derived
  device-list activities. They are boilerplate and constants (a logger line, a refresh
  message number, `onPause`). Marked **partly derived (trivial)**. P6 rewrites them; the
  file may be read.
- **Other files:** `services/StateManager` (two `case` labels naming derived constants)
  and five files with one such line each (a logger line in two files, keep-screen-on, a
  clipboard call, a `case` label) are ours.
- **Upstream text added after the fork:** no file outside the derived list has more
  than 11 distinctive lines (at least 40 characters and occurring once upstream) that
  match AndrOBD at either version. Every one checked is Android boilerplate or a call
  to the derived API, for example `ObdProt.PidPvs.addPvChangeListener(this,`.
- **Attribution text still in the tree:** see 1.5.

### 1.3 The protocol packages, every file

Every Java file under `app/src/java/com/obddroid/{obd,can,ecu,common,interfaces}`.
"Overlap" is the share of a file's distinct normalised lines found verbatim in AndrOBD
V2.6.16 (method 3). "Derived" files trace by history to an AndrOBD file
(PROVENANCE.md section 3). Notes on two first commits:

- `ProcessVariables` was created in `a0d68c1` by merging five upstream files that came
  in with the fork.
- `CanProtFord` was moved in `9ef8f0e`, where `git log --follow` loses its trail.

| File | Lines | Overlap | First commit | Verdict | Fate |
|---|---|---|---|---|---|
| `can/CanProt.java` | 217 | 100% (62/62) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `can/CanProtFord.java` | 99 | 71% (17/24) | `9ef8f0e` 2025-10-25 | **derived** | deleted in P5 |
| `common/ProcessVariables.java` | 881 | 41% (145/351) | `a0d68c1` 2025-10-19 | **derived** | deleted in P5 |
| `common/ProtoHeader.java` | 1183 | 99% (254/257) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `common/UTF8Bundle.java` | 30 | 100% (8/8) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `common/package-info.java` | 5 | 0% (0/0) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/Conversion.java` | 52 | 100% (6/6) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/Conversions.java` | 701 | 73% (207/285) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/DTCDatabaseCodeList.java` | 141 | 0% (0/36) | `7a8c99d` 2025-10-14 | ours | extends the derived `ObdCodeList`, so P4 moves it onto the new DTC description interface (spec 7.2) |
| `ecu/DtcCatalog.java` | 47 | 0% (0/8) | `4c6147b` 2025-10-20 | ours | keep |
| `ecu/DtcCatalogProvider.java` | 56 | 0% (0/27) | `4c6147b` 2025-10-20 | ours | keep |
| `ecu/EcuCacheManager.java` | 167 | 1% (1/75) | `32bd861` 2025-10-18 | ours | keep |
| `ecu/EcuCodeItem.java` | 76 | 100% (19/19) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/EcuCodeList.java` | 150 | 71% (34/48) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/EcuConversions.java` | 232 | 82% (74/90) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/EcuDataItem.java` | 400 | 84% (113/134) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/EcuDataItems.java` | 306 | 95% (87/92) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/EcuDataPv.java` | 100 | 100% (36/36) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/EcuInfo.java` | 145 | 1% (1/69) | `774721f` 2025-10-18 | ours | keep |
| `ecu/EcuManager.java` | 525 | 1% (1/193) | `774721f` 2025-10-18 | ours | P4 moves it onto `VehicleLink` |
| `ecu/EcuScan.java` | 101 | 3% (1/38) | `32bd861` 2025-10-18 | ours | keep |
| `ecu/EcuScanHistoryManager.java` | 247 | 2% (2/94) | `32bd861` 2025-10-18 | ours | keep |
| `ecu/NumericConversion.java` | 67 | 100% (13/13) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/ObdCodeItem.java` | 96 | 100% (27/27) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/ObdCodeList.java` | 63 | 57% (8/14) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/ObdPid.java` | 78 | 100% (22/22) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `ecu/PidDefinition.java` | 176 | 1% (1/97) | `4c6147b` 2025-10-20 | ours | P2 replaces or adapts it with the new value definitions |
| `ecu/PidDefinitionCsvParser.java` | 426 | 2% (4/184) | `3c7dbba` 2025-10-21 | ours | P2 replaces or adapts it with the new value definitions |
| `ecu/PidRuntime.java` | 40 | 0% (0/18) | `4c6147b` 2025-10-20 | ours | P2 moves it off `EcuDataPv` |
| `interfaces/IDataManager.java` | 73 | 0% (0/10) | `cf0110c` 2025-09-18 | ours | P2 moves it off `ProcessVariables` |
| `interfaces/RawTelegramListener.java` | 40 | 0% (0/2) | `cf46e88` 2025-10-18 | ours | P3 replaces it with the raw-line listener (spec C14) |
| `interfaces/TelegramListener.java` | 18 | 100% (2/2) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `interfaces/TelegramWriter.java` | 29 | 100% (3/3) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `obd/ElmProt.java` | 1738 | 67% (375/561) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `obd/ErrorHandler.java` | 315 | 2% (3/130) | `43a6deb` 2025-09-18 | ours | P3 decides keep or fold into the engine |
| `obd/FreezeFrameManager.java` | 193 | 0% (0/74) | `43a6deb` 2025-09-18 | ours | P3 moves it onto the new freeze-frame call (spec C10) |
| `obd/InitProgressListener.java` | 75 | 9% (2/23) | `43a6deb` 2025-09-18 | ours | P3 keeps or adapts it (spec C3) |
| `obd/InitializationManager.java` | 332 | 1% (1/124) | `43a6deb` 2025-09-18 | ours | P3 moves it onto the new init sequence (spec 4.2) |
| `obd/Messages.java` | 137 | 24% (9/38) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `obd/ObdProt.java` | 1865 | 39% (269/684) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `obd/ProtUtils.java` | 33 | 100% (9/9) | `f104466` 2025-09-15 | **derived** | deleted in P5 |
| `obd/StreamHandler.java` | 261 | 100% (60/60) | `f104466` 2025-09-15 | **derived** | deleted in P1 |

Totals for these packages: 42 files, 11,916 lines; 25 derived (8,817 lines), 17 ours.

### 1.4 Derived files elsewhere

PROVENANCE.md section 3 lists them with their upstream origins and callers. The re-check
agrees. They are:

- **Transport, deleted in P1:**
  - `services/CommService` (189 lines);
  - `services/BluetoothCommService` (580);
  - `services/UsbCommService` (262);
  - `services/NetworkCommService` (141).

  `obd/StreamHandler` (261) goes with them.
- **User interface and helpers, deleted in P6:**

  | File | Lines | Overlap |
  |---|---|---|
  | `MainActivity` | 4,063 | 42% |
  | `SettingsActivity` | 767 | 38% |
  | `DashBoardActivity` | 305 | 80% |
  | `ChartActivity` | 396 | 21% |
  | `CustomPidActivity` | 300 | 93% |
  | `UsbDeviceListActivity` | 215 | 78% |
  | `BtDeviceListActivity` | 504 | 10% |
  | `ObdItemAdapter` | 310 | 77% |
  | `ObdGaugeAdapter` | 102 | 97% |
  | `ColorAdapter` | 98 | 95% |
  | `TestResultAdapter` | 59 | 88% |
  | `FaultCodeAdapter` | 234 | 11% |
  | `AutoHider` | 113 | 94% |
  | `MirrorRelativeLayout` | 38 | 100% |
  | `ExportTask` | 224 | 34% |
  | `FileHelper` | 273 | 64% |

- **Partly derived (trivial):** `UnifiedAdapterSelectionActivity` (1.2).

### 1.5 Attribution text still in the tree

The derived files no longer say where they came from, but a few comments and strings
still name AndrOBD:

- **`ui/activities/MainActivity`:** a class comment "Main Activity for AndrOBD app", the
  log tag `"AndrOBD"`, and the log file name `AndrOBD.log.%g.txt`.
- **`obd/ElmProt`:** comments that cite AndrOBD issue numbers (#60, #61, and Plugin
  #10).
- **`obd/StreamHandler`:** a fix comment citing AndrOBD issue 27.
- **`services/UsbCommService`:** a workaround comment citing AndrOBD issue 285 and a
  usb-serial-for-android issue.

`features/fueleconomy/ui/FuelEconomyActivity` (ours) names AndrOBD only as a product, in
a list of other apps. Leave all of these alone: they are evidence of origin, and they
disappear when their files are deleted. Whether attribution should be restored in the
meantime is a legal question (section 7).

### 1.6 Data tables and resources

PROVENANCE.md section 3 lists them. This plan adds whether each is used:

| Item | Used by | Plan |
|---|---|---|
| `resources/standard/pids.csv`, `conversions.csv`, `messages.csv` | the derived loaders (`EcuDataItems`, `EcuConversions`, `Messages`), which hard-code `/standard/` | P2 builds OBD-Droid's own table from SAE J1979-DA; P5 deletes these |
| `resources/volkswagen_auto_group/` (6 files) | **nothing**: no loader opens this folder | delete in P0 (bead R0d). It also holds third-party data AndrOBD took from other sources (PROVENANCE.md section 5) |
| `res/xml/device_filter.xml` (USB vendor and product IDs) | the USB transport | P1 regenerates it from the chip vendors' published IDs |
| derived layouts, menus, `refs.xml`, drawables | UI; `device_list.xml`, `obd_services.xml` and `refs.xml` are named nowhere else in `app/src` | P0 (R0d) deletes the unreferenced ones after `lint` confirms them unused (`UnusedResources`); P6 replaces the rest |
| `ic_speedometer`, `ic_headup`, `ic_filter`, `ic_chart` | UI | P6 replaces them with Material Symbols (Apache-2.0) or OBD-Droid's own |
| shared files (`strings.xml` 32% derived, `colors.xml`, `dimens.xml`, `styles.xml`, `AndroidManifest.xml`) | everything | P6 replaces the derived entries; everyone may add their own entries meanwhile |

### 1.7 Structure: why the core goes at one cutover

From the declaration lines and the name references among derived files:

- `ElmProt` extends `ObdProt`, which extends `ProtoHeader`. `ElmProt` implements
  `TelegramListener` and `TelegramWriter`.
- `ObdProt` names almost the whole derived data model: `ProcessVariables`, `EcuDataPv`,
  `EcuDataItem(s)`, `EcuConversions`, `Conversion`, `EcuCodeItem`, `EcuCodeList`,
  `ObdCodeItem`, `ObdPid`, `Messages`, `ProtUtils`. It also calls OBD-Droid's own
  `services/ObdDataService` and `interfaces/IDataManager`.
- `CanProt` extends `ProtoHeader`; `CanProtFord` extends `CanProt` and is created inside
  `ElmProt`. OBD-Droid's own code never uses the CAN-monitor mode they serve (spec
  question 5).
- **Transport.** `CommService` holds the `ElmProt` singleton. The Bluetooth and network
  services use `StreamHandler`, and the USB service uses `TelegramWriter`. No core class
  names a transport class.
- **Android.** `ObdProt` and `Conversions` import one Android class each. The rest of the
  core is plain Java.

Consequences:

1. **The transport goes first.** It touches the core only through the `CommService.elm`
   field and two small interfaces (`TelegramListener`, 18 lines; `TelegramWriter`,
   29 lines). A thin bridge lets the old engine run on new transports.
2. **The core goes together.** `ElmProt`, `ObdProt`, `ProtoHeader` and the data model
   each need the others, so none of them can be deleted before the old engine is
   switched off (P5).
3. **Move our code first.** OBD-Droid's own code reaches the data model directly in
   about 40 files and the protocol classes in about 30 (PROVENANCE.md section 4). Moving
   those call sites onto OBD-Droid's own value interfaces (P2) and link interface (P4)
   before the cutover makes P5 small and reversible.

## 2. Behaviour specification

The specification is [`docs/protocol-spec.md`](../protocol-spec.md). It contains:

- the public references;
- the capabilities the app needs (C1–C20), each traced to its call sites;
- transports;
- the ELM327 session: framing, initialisation, protocol numbers, timing, headers and
  multiple ECUs, multi-frame replies;
- adapter quirks (4.7);
- OBD services 01–0A;
- PID formulas, each with its SAE J1979-DA citation, and units;
- DTC decoding, descriptions and clearing;
- Service 09 and VIN validation;
- error cases, link states and settings;
- non-functional requirements;
- the migration map from today's call sites;
- 126 black-box test vectors (109 in section 14, plus Q-01 to Q-17 in 4.7);
- open questions.

This bead changed four things:

1. moved the file from `docs/rewrite/SPEC.md`;
2. added the adapter quirks section (4.7) with its own vectors;
3. corrected the Service 08 row: the scan does use it, to list supported TIDs;
4. added open questions 5–7.

Rules for the spec:

- **The mayor owns it.** Changes go through a bead and a commit, never silently inside
  an implementation.
- **Gaps.** An implementer who finds a gap follows the cited standard. If the standard
  does not settle it, they ask on their bead and keep to the spec as written in the
  meantime.
- **Open questions 1–7 of the spec** need the owner's answers before the phase that
  depends on them:
  - BLE support (question 1) before P1;
  - the PID table format (question 4) before P2;
  - CAN monitor and Service 05/08 (questions 5 and 6) before P3;
  - VAG/KW1281 (question 2) before P0's cleanup bead;
  - J1939 and UDS (question 3) are out of scope for this rewrite.

## 3. Test harness plan

### 3.1 Goal

The harness pins the behaviour **before** the change, by running against the old engine,
and proves the behaviour **after**, by running the same tests against the new engine. It
needs no adapter, car, phone or emulator: everything runs as JVM unit tests on recorded
or synthetic transcripts (rig handbook rules).

### 3.2 Test layers

| Layer | Checks | Input | Runs against |
|---|---|---|---|
| L1 decoder | payload bytes → values, DTCs, VIN, units (spec 6–8) | spec vectors M01-*, M02-*, DTC-*, M09-*, VIN-*, UNIT-* | new code only |
| L2 session | adapter text in and out: prompt, echo, status words, multi-line replies, quirks (spec 4) | ELM-*, NRC-*, Q-* | new code only |
| L3 link | state machine, init sequence, protocol detection, polling and batching, multi-ECU, error handling, timeouts (spec 4.2–4.6, 5, 9, 10) | multi-step transcripts on a virtual clock | new code only |
| L4 contract | what the app sees: values, DTCs, VIN, freeze frame, link states, for a given transcript | the same transcripts | **both** engines, through one test seam (3.6) |

L4 is the equivalence proof. L1–L3 pin the details of the new code.

### 3.3 Transcript format

The format below is a proposal; P0 may refine it. It must stay plain text, one event per
line, and readable in a diff. Files end in `.elmt` and live under
`app/src/test/resources/transcripts/{spec,quirks,sim,bench}/`.

```
# Title and source: "spec ELM-08", "obdsim MODE_01.md example 1", or "bench 2026-12-01 (scrubbed)"
@adapter ELM327 v1.5           optional metadata
> 0100                         what the app sends (CR implied; case and spaces normalised)
< SEARCHING...                 one adapter line (CR appended)
< 41 00 BE 3E B8 11
<>                             the prompt
<x 00 00 3E                    raw bytes, for NUL and garbage cases
@delay 150ms                   virtual time passes before the next adapter byte
@chunk 3                       deliver adapter bytes in chunks of N (split reads, BLE)
= value 01 0C rpm 1726.00      expectations, used by L3 and L4
= state VEHICLE_CONNECTED
= dtc 03 7E8 P0133
= error 010C NO_DATA
```

There are two matching modes:

- **Strict:** each `>` must match the next command the engine sends. A mismatch fails
  the test and names the expected and actual command.
- **Lenient:** a `@map` block maps a command to its reply, and repeats it. Polling tests
  use this, where the order of requests is the engine's choice.

### 3.4 Fakes and test rules

- **`ScriptedAdapter`** plays a transcript behind the new byte-stream `Transport`
  interface. It answers byte for byte as an adapter would, including chunking and delays.
- **`VirtualClock`.** Every timeout in the new engine reads time from an injected clock.
  Tests never sleep. The whole transcript suite should run in under a minute.
- **No Android in the new core.** The new packages (`com.obddroid.link.*`, section 4)
  import no `android.*`. A unit test fails the build if they do. That keeps L1–L4 as
  plain JUnit 4 tests (already in `app/build.gradle`), with no Robolectric.
- **Android transports stay thin.** The Android transport classes (Bluetooth, USB,
  Wi-Fi) are kept small and get no JVM tests beyond construction. The owner's bench run
  covers them.
- **Fixture guard.** A unit test scans every transcript and fails on any 17-character
  VIN-shaped string except `1HGBH41JXMN109186`, and on any value that is not on an
  allowlist of invented calibration IDs and CVNs.

### 3.5 Where transcripts come from

1. **The spec's vectors** (section 14 and 4.7): 126 today, turned into `.elmt`
   files in P0.
2. **The obdsim simulator's documentation** (`docs/MODE_01.md` to `MODE_09.md` in the
   obdsim rig). The simulator speaks raw CAN, and the app speaks to an ELM327. A small
   generator in the test sources turns the documented CAN frames into adapter text using
   the spec's rules: payload lines with headers off, ID plus PCI plus data with headers
   on, multi-frame reassembly as in 4.6. The simulator's VIN, calibration ID and CVN come
   from a real vehicle, so they are replaced (spec 14.2).
3. **Examples from the standards:** J1979 and ISO 15765-2 frame layouts, written fresh.
4. **Bench captures, made only by the owner and by hand.** A debug-only "record adapter
   traffic" setting writes `.elmt` files while the app runs against the obdsim simulator
   on the bench. Each capture is scrubbed (VIN, calibration IDs, CVNs and any location
   removed) before it is committed. The fixture guard checks every file. Bead R0a builds
   the recorder; the owner decides when to use it.
5. **Never:** captures from a real car, a customer or a dealership.

### 3.6 Pinning today's behaviour (before)

- **The test seam.** P0 defines `EngineUnderTest`, a small test-only interface. Given a
  `ScriptedAdapter`, it connects, polls, reads DTCs, reads Service 09 and reports what the
  app would see.
- **The legacy runner.** The integrator writes `LegacyEngineUnderTest` in
  `app/src/test/java/com/obddroid/legacybridge/`. It drives the old engine through its
  public API, the way OBD-Droid's call sites do, behind a bridge over
  `TelegramListener`/`TelegramWriter`. Two things need care:
  - The old core imports two Android classes. Try
    `testOptions.unitTests.returnDefaultValues = true` first. If the old engine needs real
    Android behaviour, add Robolectric (MIT) as a `testImplementation` dependency only.
  - The runner calls OBD-Droid's own `ObdDataService` and `IDataManager`, so it may need
    fakes of those.
- **The baseline.** Every L4 transcript runs on the old engine. The result goes into
  `docs/rewrite/legacy-baseline.md`: pass, or fail with the observed output. That file
  is test output and holds no code.
- **Failures on the old engine** do not become expectations. The mayor marks each one:
  - **old-engine bug:** the new engine follows the spec; the case is listed as an
    intended change;
  - **spec error:** the spec and its vector are corrected through a bead.

### 3.7 Equivalence rule (after)

- **P3–P5 exit gate.** Every L4 transcript that passes on the old engine passes on the
  new one, except those listed as intended changes. All L1–L3 tests pass.
- **After P5.** The contract tests stay. Only the legacy runner goes, with the old
  engine.

### 3.8 Provenance gates

Bead R0b writes both gates. The land-bot runs them on every merge request that touches
the rewrite.

- **Overlap gate** (`tools/provenance/overlap_check.py`):
  - **Input.** It fetches AndrOBD at `02871bfe` (and `master`) into a temporary or cached
    folder outside the worktree at run time. No upstream text is ever stored in the
    repository.
  - **Comparison.** It normalises as in PROVENANCE.md section 2 and compares every file
    in the new packages with AndrOBD and with the files in `derived-paths.txt` at the
    merge base.
  - **Failure.** Any new file with 3 or more matching lines of at least 14 characters
    fails, unless the line is on a short allowlist of Java and Android boilerplate kept
    beside the script.
  - **Output.** File names and counts. `--show` prints the matching lines, for reviewers
    only.
- **Blame gate:** `git blame -w -C -C -M` over the new packages. No line may come from a
  path in `derived-paths.txt`, from the fork commit `f104466`, or from the re-import
  commit `e094e2a`.
- **Path gate:**
  - **Clean beads.** The diff may touch only the new packages, their tests and
    transcripts, `docs/`, and build-file lines that add the new packages.
  - **Integrator beads.** New logic files may appear only under `legacybridge`. Call-site
    edits, deletions and build files are also allowed.

### 3.9 Running the tests

Everything runs through `gt-test`:

- the `changed` suite while working;
- `gt-test unit` (assembleDebug, unit tests, lint) once before `gt done`.

The Gradle build never runs on the Mac by hand (rig handbook rule 1). The gate scripts
are Python with no network access other than the AndrOBD fetch. On the test host they use
a cached clone path from an environment variable.

## 4. Phases

### 4.1 Overview

New code goes into new packages:

- `com.obddroid.link`: pure Java, no Android, holding the transport interface, ELM
  session, framing, ISO-TP, services, decoding, DTCs, VIN, value store and the
  `VehicleLink` interface;
- `com.obddroid.link.android`: the Android transports;
- `com.obddroid.legacybridge`: temporary integrator glue, deleted in P5.

The names are proposals; the phase beads may adjust them.

| Phase | Clean work | Integrator work | Deleted at end | Why the app still works | Estimate (sessions) |
|---|---|---|---|---|---|
| P0 | harness, transcripts, gates (R0a, R0b) | legacy runner and baseline (R0c); unused-data cleanup (R0d) | VAG folder; unreferenced derived resources; `jSerialComm` dependency | no production behaviour changes | 2 + 1 + 1 + 1 |
| P1 | transports (R1a) | wire them in, bridge the old engine (R1b) | `CommService`, `BluetoothCommService`, `UsbCommService`, `NetworkCommService`, `StreamHandler` (1,433 lines) | the old engine runs unchanged on the new transports; contract tests plus the owner's bench check of each transport | 2–3 + 2 |
| P2 | value model, PID table, decoders (R2a) | bridge old values into the new store; move ~40 files (R2b) | none (the core uses the data model) | the old engine still produces every value; the app reads them through the new store | 3 + 3 |
| P3 | the engine core (R3a) | engine switch, debug builds only (R3b) | none | the new engine is off by default | 4–5 + 1 |
| P4 | DTCs, Service 09, 06, 08 listing, multi-ECU, clear, `VehicleLink` (R4a) | `VehicleLink` over the old engine; move ~30 call sites (R4b) | none | callers use `VehicleLink`; the old engine stays the default | 2–3 + 3 |
| P5 | none | cutover, owner bench run, delete the old core (R5) | 24 core files (8,556 lines), the three PID tables, `legacybridge` | equivalence gate (3.7) and bench run before deletion | 2 + owner |
| P6 | UI spec (R6s), then screens (R6a–R6f) | wire screens, delete old ones (R6z) | 16 UI files (8,001 lines), derived resources | each screen is switched only when its replacement passes its tests | 2 + 10–13 + 2 |
| P7 | none | close-out (R7) | anything left | whole-tree gates show zero | 1–2 + lawyer |

Dependencies, in "X needs Y" terms:

- **The protocol chain.** R0a needs nothing. R0b needs nothing. R0c needs R0a. R1a needs
  R0a. R1b needs R1a and R0c. R2a needs R0a. R2b needs R2a and R1b. R3a needs R2a. R3b
  needs R3a and R2b. R4a needs R3a. R4b needs R4a and R3b. R5 needs R4b.
- **Cleanup.** R0d needs an owner answer to spec question 2.
- **The UI track.**
  - R6s needs nothing, and can run at any time.
  - R6a (helpers: `ExportTask`, `FileHelper`, `AutoHider`, `MirrorRelativeLayout`,
    `ColorAdapter`) needs R6s only.
  - The other screens need R6s and R4b, because they should be written against
    `VehicleLink` and the value store, not the old engine.
  - R6z needs the screens and R5.
- **Close-out.** R7 needs R5 and R6z.

The phase rule from the bead: **each phase deletes, in its integrator bead, every
derived file it has left with no callers, and updates `derived-paths.txt` in the same
commit.** Because of 1.7, the protocol core is left with no callers only at P5.

### 4.2 Releases during the rewrite

Between P1 and P5, builds still contain the derived core. Whether such builds may be
released to the public (Play Store or GitHub releases) is the owner's call, after legal
advice (section 7, question 1). The plan does not depend on releasing them; internal and
bench builds are enough.

### 4.3 Follow-up bead texts

Each block below is ready to file as a bead, with its dependencies wired as in 4.1.
Every bead also carries the rig handbook rules: no adapter, car, phone or emulator in
tests; no real VINs; commit as Wal33D; `gt-test unit` before `gt done`. "Clean" beads
follow section 5.2, and "integrator" beads section 5.3.

Recommended runtime:

- **Integrator beads: CLAUDE-ONLY.** They open the derived code and must judge what
  counts as porting logic.
- **Clean beads: either runtime.** The mayor decides. Using a different model family from
  the spec writers also strengthens the separation.
- **R6s (UI spec): CLAUDE-ONLY.** Like the protocol spec.

```
R0a (clean) Rewrite P0: transcript test harness
Build the test harness in docs/rewrite/PLAN.md section 3: the .elmt transcript format and
parser, ScriptedAdapter behind a new byte-stream Transport interface
(com.obddroid.link.transport, pure Java), VirtualClock, the EngineUnderTest seam (test
sources), the fixture guard, an import guard (no android.* under com.obddroid.link), and
transcripts for every vector in docs/protocol-spec.md section 14 and 4.7. Add the
CAN-frame-to-ELM-text generator and transcripts for the obdsim docs examples (replace the
simulator's VIN/CALID/CVN as the spec 14.2 says). Add a debug-only "record adapter
traffic" switch that writes .elmt files (off by default, never in release builds).
Clean-room: section 5.2; do not open any path in docs/rewrite/derived-paths.txt or
AndrOBD. Acceptance: the transcript suite runs under gt-test in under a minute (L1-L3
tests may be @Ignore'd placeholders naming the vector until the code exists); the guard
tests pass; attestation comment on the bead.
```

```
R0b (clean) Rewrite P0: provenance gates
Write tools/provenance/overlap_check.py and the blame and path gates in PLAN.md section
3.8. The overlap gate fetches AndrOBD (https://github.com/fr3ts0n/AndrOBD) at 02871bfe and
master into a cache outside the worktree (path from an environment variable) and never
stores upstream text in the repository; it prints file names and counts, with --show for
reviewers. The blame gate fails on any line in the new packages whose origin is a path in
derived-paths.txt, commit f104466 or commit e094e2a. Document how the land-bot runs them
on rewrite merge requests. Clean-room: you write comparison code only; do not read
AndrOBD or derived files beyond running the tool on a synthetic test fixture. Acceptance:
tests of the gates on synthetic files (a planted match fails, clean code passes).
```

```
R0c (integrator) Rewrite P0: legacy runner and baseline
Write LegacyEngineUnderTest in app/src/test/java/com/obddroid/legacybridge/: it drives the
current engine (CommService.elm / ElmProt) through its public API, as OBD-Droid's call
sites do, behind a bridge over TelegramListener/TelegramWriter fed by R0a's
ScriptedAdapter. Run every L4 transcript and write docs/rewrite/legacy-baseline.md (pass,
or fail plus the observed output; no code). Use testOptions.unitTests.returnDefaultValues
first; add Robolectric (MIT, testImplementation only) only if needed, and say why. You may
open derived files; write no protocol logic (PLAN.md 5.3). Do not edit the new packages.
Acceptance: baseline file committed; mayor reviews each failure (old-engine bug or spec
error) before P3 starts.
```

```
R0d (integrator) Rewrite P0: remove unused derived data and dependencies
Needs the owner's answer to protocol-spec.md question 2 (VAG/KW1281). Delete
app/src/resources/volkswagen_auto_group/ (loaded by nothing). Remove the unused
com.fazecast:jSerialComm dependency (no references in app/src) and its NOTICE entry. Run
lint UnusedResources and delete the derived layouts, menus and values it reports unused
(candidates: layout/device_list.xml, menu/obd_services.xml, values/refs.xml). Update
derived-paths.txt in the same commit. Acceptance: gt-test unit passes; the app builds and
behaves the same.
```

```
R1a (clean) Rewrite P1: transports
Implement the transports in protocol-spec.md section 3 under com.obddroid.link.transport
(interface, pure Java) and com.obddroid.link.android (Android implementations):
Bluetooth Classic SPP with the insecure-socket retry, USB serial through
usb-serial-for-android (38400, then 9600 and 115200), TCP (5 s connect), and a demo
transport over ScriptedAdapter. BLE only if the owner answered spec question 1 with yes.
Include line splitting (CR, LF, NUL and garbage handling, prompt detection) per spec 4.1
and quirks Q-03, Q-04, Q-05, Q-08, Q-15, Q-16. Regenerate res/xml/device_filter.xml from
the chip vendors' published USB vendor/product IDs (FTDI, Silicon Labs CP210x, WCH CH34x,
Prolific PL2303, CDC-ACM) without opening the current file. Clean-room: section 5.2.
Acceptance: L2 tests for the quirks above pass; attestation comment.
```

```
R1b (integrator) Rewrite P1: wire the new transports, delete the old ones
Add OBD-Droid's own LinkService (holding the active transport) and a bridge in
com.obddroid.legacybridge that feeds the existing engine through
TelegramListener/TelegramWriter. Move every use of CommService.elm, CommService.MEDIUM and
CommService.STATE (about 22 files; PROVENANCE.md section 4) onto LinkService. Delete
CommService, BluetoothCommService, UsbCommService, NetworkCommService and StreamHandler;
update the manifest and derived-paths.txt. Write no protocol logic. Acceptance: the L4
contract tests give the same results as docs/rewrite/legacy-baseline.md; mayor files the
owner's bench check (connect over Bluetooth, USB and Wi-Fi against obdsim) before the
change ships.
```

```
R2a (clean) Rewrite P2: value model, PID table and decoders
Implement protocol-spec.md section 6 (formulas, units), 2.1 C18 and C19 (value metadata,
custom and sensor values) and 2.2 (ValueDefinition, DecodedValue, ValueStore) under
com.obddroid.link. Build OBD-Droid's own PID definition table from SAE J1979-DA in the
format the owner chose (spec question 4), one row per PID with its J1979-DA reference;
write labels and descriptions in our own words. Do not open pids.csv, conversions.csv or
messages.csv. Decoders are pure functions: payload bytes in, values out. Clean-room:
section 5.2. Acceptance: all L1 vectors for Service 01/02 and units pass; table rows each
cite J1979-DA; attestation comment.
```

```
R2b (integrator) Rewrite P2: move the app onto the value store
In com.obddroid.legacybridge, forward the old engine's value changes (PidPvs, VidPvs,
TidPvs, tCodes) into R2a's ValueStore, and forward custom-PID registrations that need
polling to the old engine. Move OBD-Droid's own code off EcuDataPv, ProcessVariables,
EcuDataItem(s) and Conversion(s): about 40 files, including custompid/*, telemetry/*,
scan/stages/*, the live-data, dashboard and chart screens' adapters, PidRuntime,
PidDefinition* and IDataManager (PROVENANCE.md section 4 lists them). Do not move the
derived UI files (P6 replaces them). Write no protocol logic. Acceptance: contract tests
match the baseline; no file outside the derived list and legacybridge imports the derived
data model.
```

```
R3a (clean) Rewrite P3: engine core
Implement protocol-spec.md section 4 (framing, init sequence with progress, protocol
numbers and detection, timing, headers and multi-ECU, multi-frame and K-line checksum),
4.7 (all quirks), 5.1-5.3 (supported-PID discovery, polling with batching and focus mode,
freeze frame), 9 (errors), 10 (link states), 11 (settings), C14 (raw pass-through and raw
listener) and C17 (demo) under com.obddroid.link, feeding R2a's ValueStore. All timing
uses the injected clock. Clean-room: section 5.2. Acceptance: all L2 and L3 tests and the
L4 contract tests for live data and freeze frame pass on the new engine; equivalence
with legacy-baseline.md except listed intended changes; gates pass; attestation.
```

```
R3b (integrator) Rewrite P3: engine switch
Add a debug-only setting protocol_engine (legacy | new; default legacy; hidden in
release builds). With "new", LinkService runs R3a's engine on the transport instead of
the bridge; DTC, Service 09 and 06 calls report "not available on the new engine yet".
Move InitializationManager, InitProgressListener and FreezeFrameManager onto the
engine-neutral calls where needed. Write no protocol logic. Acceptance: both settings
build and pass the contract tests that apply.
```

```
R4a (clean) Rewrite P4: DTCs, vehicle information, monitors, multi-ECU
Implement protocol-spec.md section 7 (DTC decode, the description lookup interface,
clear with a confirmation token, status), 8 (Service 09, VIN check digit), Service 06
results and Service 08 supported-TID listing (C11), multi-ECU discovery (C13), and the
full VehicleLink interface (2.2) on the new engine. Clean-room: section 5.2. Acceptance:
DTC-*, M03-*, M04-*, M07-*, M0A-*, M09-*, VIN-*, NRC-* and the multi-ECU quirks pass;
contract tests pass; attestation.
```

```
R4b (integrator) Rewrite P4: move the app onto VehicleLink
Implement VehicleLink over the old engine in com.obddroid.legacybridge, so callers no
longer care which engine runs. Move OBD-Droid's own callers off ElmProt/ObdProt statics
and CommService: FaultCodeService, scan/ScanOrchestrator and stages, EcuDiscoveryService,
EcuManager, VehicleManager, StateManager, VinDataHelper, UnsupportedModeHelper,
VehicleInfoFooter, the CoPilot tools, emissions, fuel economy and track mode (PROVENANCE.md
section 4). Replace the three reflection reads of ElmProt fields. Move
DTCDatabaseCodeList off ObdCodeList onto the DTC description interface. Write no protocol
logic. Acceptance: no file outside the derived list and legacybridge names a derived
protocol class; contract tests match the baseline on both engines.
```

```
R5 (integrator) Rewrite P5: cutover and removal of the old core
Make the new engine the default in all builds; keep the legacy switch for one internal
build. File the owner's bench-run task (manual, from the bench runbook bead): each
adapter type against obdsim, checking the 4.7 [field] quirks and the main screens. After
the owner reports a pass: delete ElmProt, ObdProt, ProtoHeader, CanProt, CanProtFord,
Messages, ProcessVariables, UTF8Bundle, common/package-info, TelegramListener,
TelegramWriter, ProtUtils, Conversion, Conversions, NumericConversion, EcuConversions,
EcuDataItem, EcuDataItems, EcuDataPv, EcuCodeItem, EcuCodeList, ObdCodeItem, ObdCodeList,
ObdPid, the three standard/*.csv tables, the legacybridge packages (main and test) and
the engine switch; update derived-paths.txt. Acceptance: whole-tree overlap and blame
gates show zero outside the P6 UI files; gt-test unit passes.
```

```
R6s (spec) Rewrite P6: UI behaviour specification
Write docs/ui-spec.md: what each derived screen and helper must do, from the app's
behaviour (Robolectric or preview screenshots, the settings list, OBD-Droid's own docs
and call sites), not from the derived source. Screens: main/home and navigation, settings,
dashboard and head-up display, chart, custom PID editor, device lists, the live-data list
item and gauge adapters, colour picker, test-result and fault-code list items; helpers:
export, file helper, auto-hide, mirrored layout. Include a test list per screen. The
writer of this spec implements none of P6. CLAUDE-ONLY.
```

```
R6a-R6f (clean) Rewrite P6: screens
One bead per group, each written from docs/ui-spec.md, the value store and VehicleLink
only:
  R6a helpers (export, file helper, auto-hide, mirrored layout, colour picker);
  R6b main screen and navigation (today's MainActivity, 4,063 lines: split into a small
      activity plus feature-owned controllers);
  R6c settings;
  R6d dashboard, head-up display, chart and gauges;
  R6e custom PID editor;
  R6f list items (live data, test results, fault codes) and the device pickers (fold
      into UnifiedAdapterSelectionActivity, and rewrite its 15 boilerplate lines from the
      old device lists).
New layouts, menus, strings and icons (Material Symbols, Apache-2.0, or our own); do
not copy from the resources in derived-paths.txt. Clean-room: section 5.2. Acceptance:
the spec's tests per screen; gates pass; attestation.
```

```
R6z (integrator) Rewrite P6: switch screens and delete the old UI
Point the manifest, menus and intents at the new screens one at a time; delete each
derived UI file and resource when nothing names it; remove the derived entries from
strings.xml, colors.xml, dimens.xml, styles.xml and the manifest; update
derived-paths.txt. Acceptance: derived-paths.txt lists no code paths; gt-test unit
passes.
```

```
R7 (integrator) Rewrite P7: close-out
Run the overlap and blame gates over the whole tree (app/src and modules/) and record
zero. Update NOTICE and README (PLAN.md section 6). File the dtc-database provenance
review and its LICENSE file (section 6.3). Produce a dependency licence report from the
gt-test host (all transitive runtime dependencies with their licences). Prepare the
package for the lawyer: PROVENANCE.md, PLAN.md, the bead attestations and gate results.
CLAUDE-ONLY.
```

## 5. Rules for the workers

### 5.1 Roles

| Role | Who | May read | May write |
|---|---|---|---|
| Spec owner | the mayor | everything | `docs/protocol-spec.md`, `docs/ui-spec.md` (through beads) |
| Spec writers | obd-4nc (polecat furiosa), obd-43g (polecat nux), R6s | declaration lines and statistics of derived files, never their bodies (their section 0 records what each saw) | specs and plans only. **They implement nothing.** |
| Clean implementers | one bead each in R0a, R0b, R1a, R2a, R3a, R4a, R6a–f | 5.2 | the new packages, their tests and transcripts, docs |
| Integrators | one bead each in R0c, R0d, R1b, R2b, R3b, R4b, R5, R6z, R7 | anything | 5.3 |
| Reviewers | the land-bot and the mayor | anything | verdicts |

Polecat sessions start fresh: a session remembers nothing from earlier sessions, even
under the same polecat name. In this plan "a different worker" therefore means a
different session on a different bead. Every clean bead still records its attestation
(5.2, rule 7), because this point needs the lawyer's view (section 7, question 2).

### 5.2 Clean implementers

1. **What you work from:**
   - `docs/protocol-spec.md` (or `docs/ui-spec.md`) and this plan;
   - the public standards they cite;
   - the tests and transcripts;
   - OBD-Droid's own code outside `derived-paths.txt`;
   - the new packages.
2. **Never open:**
   - any path in `docs/rewrite/derived-paths.txt`;
   - AndrOBD or any fork of it, on GitHub or anywhere else;
   - the `legacybridge` packages.

   Running the legacy contract tests and reading their pass/fail output is allowed; it is
   black-box behaviour.
3. **Search without the derived paths.** Never grep the whole of `app/src`. Use
   `git grep` with the exclusions shown at the top of `derived-paths.txt`. If a tool shows
   you derived code by accident, stop, say so on the bead, and let the mayor re-sling the
   bead to a fresh session.
4. **Do not ask about the derived code.** Never ask another agent how the old code works,
   and never paste derived code into any prompt.
5. **Where the spec is unclear,** follow the cited standard. If that does not settle it,
   ask on your bead. Never settle it by reading old code.
6. **Where new code goes.** Write only in the new packages, their tests and transcripts,
   and docs. Never edit a derived file.
7. **Attest before `gt done`** with a bead comment:
   `Clean-room: I did not open any path in docs/rewrite/derived-paths.txt, the legacybridge
   packages, or AndrOBD. Sources: <spec sections, standards, tests>.`

### 5.3 Integrators

1. You may open derived files. Open them only to wire and to delete.
2. Write no protocol, decoding or UI logic. Allowed changes:
   - edits at OBD-Droid's own call sites;
   - the `legacybridge` packages, holding only type conversion and event forwarding;
   - build and manifest files;
   - deletions;
   - `derived-paths.txt`.
3. Never copy derived code into the new packages or into OBD-Droid's own files. Never
   edit the new packages; if they need a change, file a bead for a clean implementer.
4. Every bead ends with the derived files it left unused deleted, in the same merge
   request.

### 5.4 Reviewers

The land-bot runs the gates in 3.8 and checks the attestation comment on clean beads. Any
gate failure or missing attestation is FIX-FIRST.

## 6. Leftovers

### 6.1 Licence headers

- **Derived files.** None have a header today (1.2). Do not add GPL headers to the
  derived files, and do not strip anything. Whether attribution should be restored until
  the files are deleted is legal question 1.
- **New files.** The repository's own files carry no per-file header. An
  `SPDX-License-Identifier: MIT` line in new files is optional; the owner decides, and
  whatever is chosen should apply to all new files.

### 6.2 NOTICE and README

Today `NOTICE` lists the libraries and submodules but not AndrOBD, and the README says
MIT with no credit to AndrOBD. To fix in R7, or earlier where noted:

- **jSerialComm:** remove the entry and the dependency (R0d); nothing uses it.
- **FlexboxLayout:** the URL points to an unrelated iOS project
  (`nicklockwood/FlexboxLayout`). Google's library is
  `https://github.com/google/flexbox-layout` (Apache-2.0).
- **Missing entries:** add the libraries that ship but are not listed: the Kotlin
  standard library (Apache-2.0) and Prism4j, which Markwon's syntax-highlight module
  pulls in (Apache-2.0).
- **AndrOBD credit:** whether `NOTICE` or the README should name AndrOBD as the project's
  origin, before or after the rewrite, is legal question 1.

### 6.3 Submodules

| Submodule | Licence | Open item |
|---|---|---|
| `modules/automotive-logo-library` | MIT (`LICENSE` present) | Car-maker logos are trademarks; their use is a separate question (7, question 9) |
| `modules/nhtsa-recall-lookup` | MIT (`LICENSE` present) | NHTSA data is US federal government data |
| `modules/dtc-database` | README says MIT, but **no `LICENSE` file at the pinned commit `161a876`** | Add a LICENSE. Review where `data/source-data/*.txt` (38 files of generic and manufacturer codes) came from. 1,936 of AndrOBD's 3,584 English DTC texts appear word for word in it (PROVENANCE.md section 5), and generic texts may follow SAE J2012 wording |

Other bundled data:

- `assets/databases` (NHTSA vPIC): US government data.
- The Lottie animations in `res/raw`: their source should be recorded in R7.

### 6.4 Dependencies and their licences

These are the direct dependencies in `app/build.gradle`. The licences come from `NOTICE`
and the projects' published licences; R7 produces a full transitive report on the
`gt-test` host.

| Dependency | Licence | Note |
|---|---|---|
| `com.fazecast:jSerialComm:2.10.4` | LGPL-3.0 (as listed in NOTICE) | unused; remove (R0d) |
| `com.github.mik3y:usb-serial-for-android:3.9.0` | MIT | used by the USB transport; confirm the licence of the exact release in R7 (older releases were LGPL) |
| `com.github.anastr:speedviewlib:1.6.1` | Apache-2.0 | |
| `com.github.PhilJay:MPAndroidChart:v3.1.0` | Apache-2.0 | |
| `androidx.*`, `com.google.android.material` | Apache-2.0 | |
| `com.hivemq:hivemq-mqtt-client:1.3.0` | Apache-2.0 | |
| `com.google.android.flexbox:flexbox:3.0.0` | Apache-2.0 | fix the NOTICE URL |
| `com.google.code.gson:gson:2.10.1` | Apache-2.0 | |
| `com.airbnb.android:lottie:6.2.0` | Apache-2.0 | |
| `io.noties.markwon:core`, `syntax-highlight` 4.6.2 | Apache-2.0 | pulls in Prism4j (Apache-2.0) |
| Kotlin plugin and standard library 1.8.0 | Apache-2.0 | |
| Android Gradle plugin 8.5.2, Gradle wrapper | Apache-2.0 | build only |
| `junit:junit:4.13.2` | EPL-1.0 | test only, not shipped |

No GPL dependency was found among the direct dependencies. The transitive report in R7
must confirm this.

### 6.5 Other places derived material lives

- **Git history.** The first commit and the history up to P6 contain the derived code,
  in the public `Wal33D/OBD-Droid` repository and its clones. Removing it from `HEAD`
  does not remove it from history (legal question 6).
- **Earlier builds.** Any APKs built before P6 contain the derived code (legal question 6).
- **The obdsim rig.** Not derived from AndrOBD, but its README says it carries real
  Mercedes-Benz data. Test fixtures must not copy it (3.5); it is not shipped.
- **Translations.** The 43 translation folders were removed after the fork. If
  translations come back, they must be translated fresh from the new `strings.xml`.

## 7. What a lawyer should still check

This plan lowers the risk; it does not remove it, and it is not legal advice. These are
the questions a lawyer should answer. Most extend the open questions in PROVENANCE.md
section 7.

1. **Today's state.** The public repository, and any APK built from it, ships
   GPL-derived code under an MIT licence, without the upstream notices and without
   credit. The rewrite takes weeks. Should something be done now? Options include
   restoring the notices and credit, marking the repository GPL-3.0 until P6 is done, or
   pausing public releases. Are the builds between P1 and P5 safe to release (4.2)?
2. **Independence of the rewrite.** Is this process independent enough? The spec writers
   are AI agent sessions that saw file names, class declarations and statistics of the
   derived code, never its bodies. The implementers are separate sessions of the same or
   another AI model that never open the derived code, with written attestations and
   automated text-overlap gates. Three points need an answer:
   - Do fresh sessions of the same model count as different people?
   - These models may have seen AndrOBD's public source in their training data. Does that
     matter, given that the gates check the output?
   - Does the owner's own review of merge requests, having seen the old code, matter?
3. **Is the spec itself derivative?** `docs/protocol-spec.md` was written from public
   standards and OBD-Droid's own call sites. Its migration map (section 13) names derived
   classes and members to say which call sites move. Is that acceptable?
4. **Data and facts.**
   - PID formulas and DTC codes are facts from SAE J1979 and J2012. Can OBD-Droid ship its
     own re-typed PID table and its own descriptions?
   - SAE's documents are copyrighted. Does writing labels from them need care or a licence?
   - Does `modules/dtc-database` (6.3), with its overlap with AndrOBD's DTC texts, need to
     be rebuilt?
5. **Upstream licence version.** The upstream file headers said "GPL version 2 or any
   later version"; the upstream repository's licence file is GPL-3.0. Which applies to
   the September 2025 copy?
6. **History and past releases.**
   - Is removing the code from the shipped tree enough, or does a sale need a fresh
     repository without the history?
   - Were APKs from the derived tree distributed? If so, are there source-offer
     obligations for those releases?
7. **Third-party data inside AndrOBD.** The VAG fault codes (from a carscantool.de PDF)
   and the KW1281 formulas (from a mOByDiC manual) are deleted in P0. Does anything else
   remain?
8. **Asking upstream.** AndrOBD's code has essentially one author (PROVENANCE.md
   section 6). Would a commercial licence or a relicence from them be cheaper than the
   rewrite, or useful alongside it, for example to cover history and past releases?
9. **Trademarks.** The car-maker logos in `modules/automotive-logo-library`, and any
   naming question between "OBD-Droid" and "AndrOBD", are trademark questions, not
   copyright ones.
10. **Dependencies.** Confirm the transitive licence report (6.4), and that no LGPL or
    GPL library remains once jSerialComm is dropped.
