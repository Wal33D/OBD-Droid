# OBD-Droid: provenance map of third-party-derived code

Bead: obd-4nc (rewrite step 1). Written 2026-11-16 against `main` at `05de190`.
This is an engineering record of where code came from. It is not legal advice; the
questions for the owner are at the end.

## 1. Summary

- **OBD-Droid started as a renamed copy of AndrOBD.** The first commit in
  `Wal33D/OBD-Droid` (`f104466`, 2025-09-15, "Initial commit - OBD-Droid Android
  application") is AndrOBD **V2.6.16**: upstream `fr3ts0n/AndrOBD` commit `02871bfe`
  (2025-07-25, "V2.6.16 - Merge DFC's from Mohammed Kamer"). Its
  `androbd/build.gradle` declares `versionCode 20616` / `versionName 'V2.6.16'`.
  The tree keeps AndrOBD's layout (`androbd/`, `library/`, `plugin/`,
  `awt_startup/`, `manual/`, 43 `values-xx` translation folders) with the Java package
  renamed from `com.fr3ts0n` to `com.obddroid`.
- **Upstream licence.** AndrOBD's repository licence is GPL-3.0 (`LICENSE`). Its
  per-file headers read "(C) Copyright 2015 by fr3ts0n … GNU General Public License …
  either version 2 of the License, or (at your option) any later version".
- **The headers were already gone in the first commit.** In `f104466` the derived
  files have no copyright or licence header; even the `@author` line is blank. The
  repository had no LICENSE file until `9a035af` (2026-02-25, "prep: open-source
  readiness") added an **MIT** `LICENSE` and a `NOTICE` that lists other libraries
  but not AndrOBD. No file in the current tree mentions AndrOBD, fr3ts0n or the GPL.
  (The polecat handbook says "do not strip licence headers"; there are none left to
  strip.)
- **Still in the tree today (`05de190`):**
  - 45 Java files derived from AndrOBD: 25 protocol and decoding, 4 transport, 16 UI and
    helpers. Section 3 lists them.
  - The two data tables the decoder runs on: `pids.csv` (byte-identical) and
    `conversions.csv`, plus the English message strings converted to `messages.csv`.
  - Six VAG/KW1281 resource files.
  - About 20 layout, menu, value and drawable resources.
- **Data from further back.** Some AndrOBD files carry data AndrOBD itself took from
  other sources. Section 5 lists them: VAG fault codes from a carscantool.de PDF, KW1281
  formulas from a mOByDiC 2700 manual, and the DTC description texts, which also overlap
  the `dtc-database` submodule.
- **Code written for OBD-Droid.** These were written after the fork and match no
  AndrOBD file beyond ordinary Android boilerplate: `scan/`, `features/`, `telemetry/`,
  `custompid/` and most of `services/` and `ecu/` (`EcuManager`, the `PidDefinition*`
  classes, the `Dtc*` classes, `EcuInfo`, `EcuScan*`, `EcuCacheManager`). However, they
  call into the derived classes heavily. Section 4 maps those dependencies.

## 2. Method (how each claim was checked)

1. **Git history.** A full clone of `https://github.com/Wal33D/OBD-Droid` (526 commits;
   the rig worktree is a shallow clone with 1 commit). For each current file,
   `git log --follow --name-status` traced renames back to its first path. For
   example, `app/src/java/com/obddroid/obd/ElmProt.java` traces back to
   `library/src/main/java/com/obddroid/ecu/prot/obd/ElmProt.java` in `f104466`.
2. **Upstream version.** A full clone of `https://github.com/fr3ts0n/AndrOBD`. The fork
   point was found with `git log -G'versionCode 2061[56]' -- androbd/build.gradle`,
   which gives `02871bfe` = V2.6.16, matching the first commit's version fields.
3. **Line overlap.** Each current file was normalised: `fr3ts0n` replaced with
   `obddroid`; whitespace collapsed; imports, `package` lines, comments, braces, bare
   `return`/`break` and lines under 14 characters dropped. The tables then show
   "lines found in upstream": the share of the remaining distinct lines that appear
   verbatim somewhere in AndrOBD V2.6.16's Java sources. This counts surviving text, not
   design. A low number does **not** mean a file is clean; structure, names and logic can
   survive rewording.
4. **Byte identity.** SHA-1 of every file in `app/src` and `modules/` was compared
   with every file in AndrOBD V2.6.16.
5. **Converted data.** For `.properties` files that OBD-Droid converted to CSV,
   key → value pairs were compared.
6. **Callers.** For each derived class, a word-boundary search of
   `app/src/java/com/obddroid` lists the files outside the derived set that name it.
   A few class names are also common words (`Messages`, `Conversion`), so their caller
   lists include some comment-only hits.

Confidence labels used below:

- **certain:** history reaches the AndrOBD path in `f104466` and the content matches.
- **probable:** the name and lineage match, but little upstream text remains.
- **uncertain:** the evidence is indirect.

## 3. Derived files still in the tree

Every row below is **certain** unless the overlap column says "lineage only", which
means **probable**. All 45 files trace by `git log --follow` (or, for `CanProtFord`, by
name and content) to the AndrOBD path in `f104466`. `…` abbreviates
`src/main/java/com/fr3ts0n/` plus the module's package path
(`library/…/ecu` = `library/src/main/java/com/fr3ts0n/ecu`;
`androbd/…` = `androbd/src/main/java/com/fr3ts0n/ecu/gui/androbd`).

### Protocol and decoding layer (the rewrite target)

| Current file (`app/src/java/com/obddroid/…`) | Lines | Upstream origin in AndrOBD V2.6.16 | Lines found in upstream | Called from (files outside this list) |
|---|---|---|---|---|
| `obd/ElmProt.java` | 1738 | `library/…/ecu/prot/obd/ElmProt.java` | 66% (362/548) | 16: ecu/EcuManager, interfaces/RawTelegramListener, scan/ScanContext, scan/ScanOrchestrator, services/DiscoveryManager, services/EcuDiscoveryService, services/FaultCodeService, services/StateManager, services/VehicleManager, ui/activities/DashboardUiHelper, ui/activities/EcuListActivity, ui/activities/FaultCodeDetailsActivity, ui/activities/FaultCodeUiHelper, ui/activities/UnifiedAdapterSelectionActivity, ui/activities/UnsupportedModeHelper, ui/components/VehicleInfoFooter |
| `obd/ObdProt.java` | 1865 | `library/…/ecu/prot/obd/ObdProt.java` | 41% (261/634) | 30: custompid/CustomPidIntegration, ecu/EcuManager, features/emissions/data/EmissionsDataManager, features/emissions/data/EmissionsDataParser, features/emissions/ui/EmissionsActivity, features/fueleconomy/ui/FuelEconomyActivity, features/trackmode/TrackModeActivity, features/trackmode/recording/TelemetryRecorder, obd/InitializationManager, scan/ScanOrchestrator, scan/stages/ComponentTestStage, scan/stages/LiveDataStage, scan/stages/MonitorTestStage, scan/stages/VehicleInfoStage, services/CsvLoggingService, services/FaultCodeService, services/ObdDataService, services/StateManager, telemetry/GpsTelemetryManager, telemetry/RemoteTelemetryManager, telemetry/SensorTelemetryManager, ui/activities/DashboardUiHelper, ui/activities/EcuListActivity, ui/activities/FaultCodeUiHelper, ui/activities/LiveDataActivity, ui/activities/UnsupportedModeHelper, ui/activities/VinDataHelper, ui/adapters/ObdRecyclerAdapter, ui/components/VehicleInfoFooter, ui/helpers/StateCleanupDialog |
| `obd/StreamHandler.java` | 261 | `library/…/prot/StreamHandler.java` | 100% (57/57) | none outside the derived set |
| `obd/ProtUtils.java` | 33 | `library/…/prot/ProtUtils.java` | 100% (8/8) | none outside the derived set |
| `obd/Messages.java` | 137 | `library/…/ecu/prot/obd/Messages.java` | 23% (8/35); rewritten to read messages.csv; same class name and role | 3: ecu/DTCDatabaseCodeList, features/copilot/data/AgentApiClient, ui/components/VehicleInfoFooter |
| `can/CanProt.java` | 217 | `library/…/ecu/prot/obd/CanProt.java` | 100% (55/55) | none outside the derived set |
| `can/CanProtFord.java` | 99 | `library/…/ecu/prot/obd/CanProtFord.java` | 46% (6/13); git --follow loses the trail at a 2025-10-25 move; name and content match | none outside the derived set |
| `common/ProtoHeader.java` | 1183 | `library/…/prot/ProtoHeader.java` | 99% (215/218) | none outside the derived set |
| `common/ProcessVariables.java` | 881 | `library/…/pvs/{ProcessVar,PvList,PvChangeEvent,PvLimits,IndexedProcessVar}.java` | 40% (126/316); several upstream files merged into one; history goes back to the initial commit | 17: ecu/EcuManager, features/emissions/ui/EmissionsActivity, features/fueleconomy/ui/FuelEconomyActivity, features/trackmode/TrackModeActivity, interfaces/IDataManager, obd/FreezeFrameManager, scan/stages/FreezeFrameStage, services/CsvLoggingService, services/ObdDataService, telemetry/GpsTelemetryManager, telemetry/RemoteTelemetryManager, telemetry/SensorTelemetryManager, ui/activities/FaultCodeUiHelper, ui/activities/LiveDataActivity, ui/activities/VinDataHelper, ui/adapters/ObdRecyclerAdapter, ui/components/VehicleInfoFooter |
| `common/UTF8Bundle.java` | 30 | `library/…/common/UTF8Bundle.java` | 100% (6/6) | none outside the derived set |
| `common/package-info.java` | 5 | `library/…/common/package-info.java` | n/a; package doc comment, same text | none outside the derived set |
| `interfaces/TelegramListener.java` | 18 | `library/…/prot/TelegramListener.java` | 100% (all) | 1: interfaces/RawTelegramListener |
| `interfaces/TelegramWriter.java` | 29 | `library/…/prot/TelegramWriter.java` | 100% (all) | none outside the derived set |
| `ecu/Conversion.java` | 52 | `library/…/ecu/Conversion.java` | 100% (6/6) | 9: custompid/CustomPidAdapter, custompid/CustomPidConversion, custompid/CustomPidIntegration, ecu/PidDefinition, ecu/PidDefinitionCsvParser, features/fueleconomy/ui/FuelEconomyActivity, telemetry/GpsTelemetryManager, telemetry/SensorTelemetryManager, ui/adapters/ObdRecyclerAdapter |
| `ecu/Conversions.java` | 701 | `library/…/ecu/{Conversions,VagConversion,BitmapConversion,LinearConversion,HashConversion,IntConversion}.java` | 77% (182/236); several upstream files merged into one | 3: custompid/CustomPidIntegration, telemetry/GpsTelemetryManager, telemetry/SensorTelemetryManager |
| `ecu/NumericConversion.java` | 67 | `library/…/ecu/NumericConversion.java` | 100% (11/11) | none outside the derived set |
| `ecu/EcuConversions.java` | 232 | `library/…/ecu/EcuConversions.java` | 83% (73/88) | 1: ecu/PidDefinitionCsvParser |
| `ecu/EcuDataItem.java` | 400 | `library/…/ecu/EcuDataItem.java` | 87% (106/122) | 6: custompid/CustomPidAdapter, custompid/CustomPidIntegration, ecu/PidDefinition, telemetry/GpsTelemetryManager, telemetry/SensorTelemetryManager, ui/adapters/ObdRecyclerAdapter |
| `ecu/EcuDataItems.java` | 306 | `library/…/ecu/EcuDataItems.java` | 94% (83/88) | 4: custompid/CustomPidIntegration, ecu/PidDefinitionCsvParser, telemetry/GpsTelemetryManager, telemetry/SensorTelemetryManager |
| `ecu/EcuDataPv.java` | 100 | `library/…/ecu/EcuDataPv.java` | 100% (32/32) | 25: custompid/CustomPidIntegration, ecu/EcuManager, ecu/PidRuntime, features/emissions/data/EmissionsDataManager, features/emissions/data/EmissionsDataParser, features/emissions/ui/EmissionsActivity, features/fueleconomy/ui/FuelEconomyActivity, features/trackmode/TrackModeActivity, features/trackmode/recording/TelemetryRecorder, obd/FreezeFrameManager, scan/stages/ComponentTestStage, scan/stages/FreezeFrameStage, scan/stages/LiveDataStage, scan/stages/MonitorTestStage, scan/stages/VehicleInfoStage, services/CsvLoggingService, services/ObdDataService, telemetry/GpsTelemetryManager, telemetry/RemoteTelemetryManager, telemetry/SensorTelemetryManager, ui/activities/FaultCodeUiHelper, ui/activities/LiveDataActivity, ui/activities/VinDataHelper, ui/adapters/ObdRecyclerAdapter, ui/components/VehicleInfoFooter |
| `ecu/EcuCodeItem.java` | 76 | `library/…/ecu/EcuCodeItem.java` | 100% (16/16) | 5: ecu/DTCDatabaseCodeList, ecu/DtcCatalog, obd/FreezeFrameManager, services/FaultCodeService, ui/activities/FaultCodeUiHelper |
| `ecu/EcuCodeList.java` | 150 | `library/…/ecu/EcuCodeList.java` | 72% (28/39) | none outside the derived set |
| `ecu/ObdCodeItem.java` | 96 | `library/…/ecu/ObdCodeItem.java` | 100% (23/23) | 1: ecu/DTCDatabaseCodeList |
| `ecu/ObdCodeList.java` | 63 | `library/…/ecu/ObdCodeList.java` | 58% (7/12) | 3: ecu/DTCDatabaseCodeList, ecu/DtcCatalogProvider, services/FaultCodeService |
| `ecu/ObdPid.java` | 78 | `library/…/ecu/ObdPid.java` | 100% (16/16) | none outside the derived set |

### Transport (Bluetooth, USB, Wi-Fi/TCP)

| Current file (`app/src/java/com/obddroid/…`) | Lines | Upstream origin in AndrOBD V2.6.16 | Lines found in upstream | Called from (files outside this list) |
|---|---|---|---|---|
| `services/CommService.java` | 189 | `androbd/…/CommService.java` | 93% (43/46) | 22: ecu/EcuManager, features/copilot/data/tools/ClearFaultCodesTool, features/emissions/ui/EmissionsActivity, features/fueleconomy/ui/FuelEconomyActivity, features/trackmode/TrackModeActivity, scan/ScanOrchestrator, scan/stages/ComponentTestStage, scan/stages/FreezeFrameStage, scan/stages/LiveDataStage, scan/stages/VehicleInfoStage, services/DiscoveryManager, services/EcuDiscoveryService, services/FaultCodeService, services/ObdServiceCoordinator, services/StateManager, ui/activities/EcuListActivity, ui/activities/FaultCodeDetailsActivity, ui/activities/FaultCodeUiHelper, ui/activities/LiveDataActivity, ui/activities/UnifiedAdapterSelectionActivity, ui/activities/UnsupportedModeHelper, ui/activities/VinDataHelper |
| `services/BluetoothCommService.java` | 580 | `androbd/…/BtCommService.java` | 59% (100/169); renamed | none outside the derived set |
| `services/UsbCommService.java` | 262 | `androbd/…/UsbCommService.java` | 100% (73/73) | none outside the derived set |
| `services/NetworkCommService.java` | 141 | `androbd/…/NetworkCommService.java` | 100% (44/44) | none outside the derived set |

### User interface and helpers

| Current file (`app/src/java/com/obddroid/…`) | Lines | Upstream origin in AndrOBD V2.6.16 | Lines found in upstream | Called from (files outside this list) |
|---|---|---|---|---|
| `ui/activities/MainActivity.java` | 4063 | `androbd/…/MainActivity.java` | 43% (551/1292); 4,063 lines now; about 550 distinctive upstream lines remain | 9: scan/ScanOrchestrator, services/CsvLoggingService, services/StateManager, ui/activities/DashboardUiHelper, ui/activities/FaultCodeUiHelper, ui/activities/UnifiedAdapterSelectionActivity, ui/activities/UnsupportedModeHelper, ui/components/VehicleInfoFooter, ui/coordinators/CsvLoggingUiCoordinator |
| `ui/activities/SettingsActivity.java` | 767 | `androbd/…/SettingsActivity.java` | 38% (102/267) | 4: features/copilot/data/tools/OpenScreenTool, telemetry/GpsTelemetryManager, telemetry/SensorTelemetryManager, ui/coordinators/RemoteTelemetryUiCoordinator |
| `ui/activities/DashBoardActivity.java` | 305 | `androbd/…/DashBoardActivity.java` | 80% (82/103) | 1: ui/activities/LiveDataActivity |
| `ui/activities/ChartActivity.java` | 396 | `androbd/…/ChartActivity.java` | 20% (36/177); mostly rewritten (MPAndroidChart); upstream structure remains | 1: ui/activities/LiveDataActivity |
| `ui/activities/CustomPidActivity.java` | 299 | `androbd/…/PidCustomization.java` | 93% (93/100); renamed | none outside the derived set |
| `ui/activities/UsbDeviceListActivity.java` | 215 | `androbd/…/UsbDeviceListActivity.java` | 78% (69/88) | none outside the derived set |
| `ui/activities/BtDeviceListActivity.java` | 504 | `androbd/…/BtDeviceListActivity.java` | 10% (19/190); mostly rewritten; lineage only | none outside the derived set |
| `ui/adapters/ObdItemAdapter.java` | 310 | `androbd/…/ObdItemAdapter.java` | 76% (77/101) | 2: ui/activities/FaultCodeUiHelper, ui/activities/LiveDataActivity |
| `ui/adapters/ObdGaugeAdapter.java` | 102 | `androbd/…/ObdGaugeAdapter.java` | 97% (37/38) | none outside the derived set |
| `ui/adapters/ColorAdapter.java` | 98 | `androbd/…/ColorAdapter.java` | 97% (35/36) | 1: ui/adapters/ObdRecyclerAdapter |
| `ui/adapters/TestResultAdapter.java` | 59 | `androbd/…/TidItemAdapter.java` | 86% (12/14); renamed | none outside the derived set |
| `ui/adapters/FaultCodeAdapter.java` | 234 | `androbd/…/DfcItemAdapter.java` | 10% (9/89); renamed, mostly rewritten; lineage only | none outside the derived set |
| `ui/components/AutoHider.java` | 113 | `androbd/…/AutoHider.java` | 94% (30/32) | none outside the derived set |
| `ui/components/MirrorRelativeLayout.java` | 38 | `androbd/…/MirrorRelativeLayout.java` | 100% (10/10); not referenced by any code (layouts may use it) | none outside the derived set |
| `utils/ExportTask.java` | 224 | `androbd/…/ExportTask.java` | 34% (31/92) | none outside the derived set |
| `utils/FileHelper.java` | 273 | `androbd/…/FileHelper.java` | 65% (58/89) | none outside the derived set |

### Resources and data files

| Current file | Upstream origin (V2.6.16) | Evidence | Confidence |
|---|---|---|---|
| `app/src/resources/standard/pids.csv` | `library/…/ecu/prot/obd/res/pids.csv` | byte-identical (431 rows; 379 Service 01/02 rows, 50 Service 09, 2 Service 08) | certain |
| `app/src/resources/standard/conversions.csv` | `library/…/ecu/prot/obd/res/conversions.csv` | 99% line match | certain |
| `app/src/resources/standard/messages.csv` | `library/…/ecu/prot/obd/res/messages.properties` | converted to TSV in `8fac158` (2025-10-15); 495 of 500 labels identical, with descriptions added by OBD-Droid | certain |
| `app/src/resources/volkswagen_auto_group/vag_pids.csv` | `library/…/ecu/prot/vag/res/vag_pids.csv` | byte-identical | certain |
| `app/src/resources/volkswagen_auto_group/vag_conversions.csv` | `library/…/ecu/prot/vag/res/vag_conversions.csv` | byte-identical | certain |
| `app/src/resources/volkswagen_auto_group/messages.csv` | `library/…/ecu/prot/vag/res/messages.properties` | 66 of 68 labels identical | certain |
| `app/src/resources/volkswagen_auto_group/codes.csv` | `library/…/ecu/prot/vag/res/codes.properties` | 717 of 717 identical; see section 5 | certain |
| `app/src/resources/volkswagen_auto_group/ecu_addresses.csv` | `library/…/ecu/prot/vag/res/ecuadr.properties` | 98 of 98 identical | certain |
| `app/src/resources/volkswagen_auto_group/kw1281_conversions.csv` | `library/…/ecu/prot/vag/res/kw1281-conversions.txt` | same formula table, reformatted; see section 5 | certain |
| `app/src/main/res/layout/obd_gauge.xml`, `pid_customization.xml`, `device_name.xml` | same names in `androbd/src/main/res/layout/` | byte-identical | certain |
| `app/src/main/res/layout/usb_list.xml` | same name | 100% line match | certain |
| `app/src/main/res/layout/head_up.xml`, `device_list.xml`, `dashboard.xml` | same names | 78%, 69%, 43% | certain |
| `app/src/main/res/layout/obd_item.xml`, `obd_list.xml`, `activity_fault_codes.xml` (was `obd_list.xml`), `startup_layout.xml` | same names | 25%, 19%, 9%, 8%; mostly rewritten | probable |
| `app/src/main/res/menu/context_graph.xml`, `obd_services.xml`, `main.xml`, `chart.xml` | same names | 81%, 50%, 42%, 31% | certain |
| `app/src/main/res/values/refs.xml` | same name | byte-identical | certain |
| `app/src/main/res/values/strings.xml` | same name | 32% of 462 lines | certain |
| `app/src/main/res/values/colors.xml`, `dimens.xml`, `styles.xml` | same names | 10%, 10%, 4%; mostly rewritten | probable |
| `app/src/main/res/xml/device_filter.xml` | same name | byte-identical (USB serial vendor/product IDs) | certain |
| `app/src/main/res/xml/settings.xml` | same name | 43% | certain |
| `app/src/main/res/xml/provider_paths.xml` | same name | 13% | probable |
| `app/src/main/res/drawable/activated_background.xml` | same name | byte-identical | certain |
| `app/src/main/res/drawable/ic_speedometer.xml`, `ic_headup.xml`, `ic_filter.xml`, `ic_chart.xml` | same names | identical vector path data; AndrOBD may itself have taken these from an icon set; not checked | certain (copy); upstream source **uncertain** |
| `app/src/AndroidManifest.xml` | `androbd/src/main/AndroidManifest.xml` | 28%; activity and service declarations for the derived classes | probable |

Not derived: the `app/src/resources/custom/*` and `app/src/main/assets/resources/custom/*`
GPS and motion CSVs (added `a2e8f18`, 2025-10-27, OBD-Droid's own format), the vPIC
database under `assets/databases` (NHTSA), the Lottie JSON in `res/raw`, and all other
layouts, menus and drawables. Their overlap is 0–7%: shared Android attribute lines only.

### Removed since the fork (for completeness)

The first commit also carried, and later commits removed:

- the AndrOBD plugin framework (`plugin/`, `PluginDataAdapter`, `PluginDataPv`);
- the desktop/AWT GUI (`awt_startup/`, `library/…/ecu/gui/application`,
  `library/…/prot/gui`, `library/…/pvs/gui`);
- the KW1281/VAG protocol class (`Kw1281Prot`);
- the AndrOBD `manual/`;
- 43 translation folders;
- the translated `codes_*.properties` / `messages_*.properties`;
- the English `codes.properties` DTC text (the app now uses the `dtc-database`
  submodule; see section 5).

They are no longer shipped. They remain in public git history.

### Low-overlap files examined and judged not derived

These share at least 5 normalised lines with AndrOBD, mostly Android boilerplate
(`setContentView`, intent extras, `Log` calls) or ELM state names:

| File | Lines shared | Judgement |
|---|---|---|
| `services/StateManager.java` | 13 of 126 (10%), 8 with upstream `MainActivity` | **uncertain**: may hold reset/cleanup code moved out of `MainActivity`; history starts after the fork |
| `ui/activities/UnifiedAdapterSelectionActivity.java` | 24 of 499 (5%) | not derived (device-picker boilerplate) |
| `ui/activities/LiveDataActivity.java` | 13 of 283 (5%) | not derived; may hold small fragments moved from `MainActivity` (**uncertain**) |
| `ui/adapters/DeviceAdapter.java` | 7 of 130 (5%) | not derived (was `ModernDeviceAdapter`, which AndrOBD does not have) |
| `custompid/CustomPid.java` | 5 of 91 | not derived |
| `EmissionsActivity`, `EcuListActivity`, `TrackModeActivity`, `FaultCodeUiHelper`, `FuelEconomyActivity`, `AutoCheckActivity` | 5–9 lines each (≤2%) | not derived (Android boilerplate) |

## 4. Which app code calls into the derived code

The tables in section 3 list callers per class. In summary, the app's own code reaches
the derived layer through a small number of entry points:

- **`CommService.elm`**: a static singleton of the derived ELM327 protocol class. It is
  used by 28 files: scan stages, discovery, fault-code, emissions, fuel-economy,
  track-mode, CoPilot tools and most activities. Members used:
  - service selection: `setService` / `getService`;
  - `getStatus`;
  - raw command sends: `sendTelegram`, `writeTelegram`;
  - freeze frames: `requestFreezeFrameSnapshot`, `getCachedFreezeFrame`,
    `getFreezeFrameManager`, `setFreezeFrame_Id`;
  - `getCachedVehicleInfo`;
  - property-change listeners;
  - raw-telegram listeners (pre-header-strip);
  - adapter control: `mAdaptiveTiming`, `reset`, `goToSleep`, `setResetOnNrc`,
    `setCustomInitCommands`.
- **`ElmProt` statics.** The connection status enum `STAT` (UNDEFINED, INITIALIZING,
  INITIALIZED, ECU_DETECTED, ECU_SELECTED, CONNECTING, CONNECTED, NODATA, DISCONNECTED,
  STOPPED) is used in 20 files. Also used: the protocol enum `PROT`, `AdaptTimingMode`,
  the `CMD` enum, `PROP_*` property names, `ecuAddresses`, `setPreferredProtocol`,
  `disableCommands`, `runDemo`. Three call sites read a private field by reflection
  (`ElmProt.class.getDeclaredField`).
- **`ObdProt` statics** (37 files):
  - the live value tables `PidPvs` (Service 01/02), `VidPvs` (Service 09), `TidPvs`
    (Service 06) and `tCodes` (DTCs);
  - the `OBD_SVC_*` service constants;
  - `getServiceName`, `getDataService`;
  - `setFixedPid` / `resetFixedPid`;
  - `getPermanentlySupportedPIDs`, `getCachedFaultCodes`;
  - the `dataItems` registry.
- **Value objects.** `EcuDataPv` field IDs (`FID_VALUE`, `FID_DESCRIPT`, `FID_UNITS`,
  `FID_FORMAT`, `FID_MIN`, `FID_MAX`, `FID_PID`, …) are used in 38 files.
  `ProcessVariables` change-event types are used in 28. `EcuCodeItem` fields are used
  in 10. `ObdCodeList` holds the hook that loads the DTC database.
- **Transport.** `MainActivity` alone instantiates `BluetoothCommService`,
  `UsbCommService` and `NetworkCommService`. Everything else goes through
  `CommService` and its `MEDIUM` enum (BLUETOOTH, USB, NETWORK).
- **Custom PIDs and telemetry** (`custompid/*`, `telemetry/*`) register their own items
  through `EcuDataItems`, `EcuDataItem`, `Conversions` and `EcuDataPv`. That puts
  OBD-Droid's own features on top of the derived data model.

Consequences for the rewrite:

- The derived data model (`EcuDataPv`, `ProcessVariables`, `EcuDataItem(s)`,
  `Conversion(s)`) reaches further into the app than the protocol classes do.
  Replacing only `ElmProt`/`ObdProt` would leave about 40 app files on derived types.
- The UI files that are mostly AndrOBD are `MainActivity`, `DashBoardActivity`,
  `CustomPidActivity`, `SettingsActivity`, the adapters, `AutoHider`, `ExportTask` and
  `FileHelper`. They are a separate rewrite item from the protocol layer.

## 5. Other third-party sources found

| Item | Source | Evidence | Status |
|---|---|---|---|
| VAG fault-code texts (`volkswagen_auto_group/codes.csv`) | AndrOBD, which credits "http://www.carscantool.de/PDF/FehlercodesVAGenglish.pdf … extracted painlessly using sed" | header comment in upstream `vag/res/codes.properties` | third-party list, copied twice; **rights uncertain** |
| KW1281 conversion formulas (`kw1281_conversions.csv`) | AndrOBD, which credits "PDF-Manual of mOByDiC 2700 AllPro version 1.01 released 21.01.2006" | header of upstream `kw1281-conversions.txt` | third-party manual; **rights uncertain** |
| Generic DTC descriptions in `modules/dtc-database` (MIT, Wal33D) | **uncertain** | 1,936 of AndrOBD's 3,584 `codes.properties` texts appear word-for-word in `dtc_codes.db`. AndrOBD's English `codes.properties` was in OBD-Droid's first commit. Both may instead trace to SAE J2012 wording, which is itself copyrighted SAE text | needs a separate provenance check of `modules/dtc-database/data/source-data/*` |
| OBD PID table (`pids.csv`) | AndrOBD; the PID numbers and formulas are SAE J1979 facts | the selection, mnemonics, labels and layout are AndrOBD's | rewrite should rebuild the table from J1979-DA (see SPEC.md) |
| `device_filter.xml` | AndrOBD; the vendor/product ID values are facts about USB serial chips | byte-identical | regenerate from chip vendors' published IDs |
| jSerialComm (LGPL-3.0), usb-serial-for-android (MIT), others | Gradle dependencies | `NOTICE` | outside this bead; already listed in `NOTICE` |
| Teensy ECU simulator (`obdsim` rig, MIT, © 2022 Sukkin Pang) | separate repository, used only as a test target | its `LICENSE` | not shipped in the app |

## 6. Who holds the upstream copyright

- **Java code.** `git log` over upstream `library/src/main/java` and
  `androbd/src/main/java` shows 4 author names: `fr3ts0n` (97 commits), Erwin
  Scheuch-Heilig (1, the same person by the copyright header's e-mail), Walter Huf (1),
  and one other contributor (1).
- **English data files.**
  - `pids.csv` and `conversions.csv`: authored only by `fr3ts0n`.
  - `messages.properties`: `fr3ts0n` plus 5 other names.
  - `codes.properties`: `fr3ts0n` plus 6 other names (mostly translation-platform
    edits).

## 7. Open legal questions for the owner

This document records facts; these questions need a lawyer, not an agent.

1. **Current distribution.** The public `Wal33D/OBD-Droid` repository (and any APK
   built from it) ships GPL-derived files under an MIT `LICENSE`, with the upstream
   copyright and licence notices removed and no attribution. Does this need correcting
   now, independent of the rewrite? Options include restoring headers and attribution
   and relicensing the repo GPL-3.0 until the rewrite lands.
2. **Upstream licence version.** The headers say "GPL version 2 or (at your option) any
   later version"; the upstream repository `LICENSE` is GPL-3.0. Which applies to the
   copy taken in September 2025?
3. **Published history.** Git history before the rewrite will still contain the
   derived code. Is a clean new repository needed for sale, or is removal from the
   shipped tree enough?
4. **Is a rewrite by the same people independent work?** The people (and agents) who
   will write the new protocol layer have seen the AndrOBD-derived code. A strict clean
   room separates the spec writer from the implementer. See SPEC.md section 0 for what
   this bead did. Decide whether the step 2 implementer must be an agent or person with
   no access to the derived files, and whether that needs to be recorded.
5. **Data tables.** PID formulas and DTC codes are facts from SAE J1979 / J2012. Can
   OBD-Droid ship its own re-typed PID table and its own DTC descriptions, given that
   SAE's text is copyrighted and AndrOBD's selection and wording are GPL? Does the
   `dtc-database` submodule (section 5) need its own provenance review before sale?
6. **Third-party data inside AndrOBD.** The VAG fault codes (carscantool.de PDF) and
   KW1281 formulas (mOByDiC manual) were never AndrOBD's to license. Should VAG/KW1281
   support be dropped or rebuilt from a licensed source?
7. **Earlier releases.** Were APKs built from the GPL-derived tree distributed (Play
   Store, sideload, testers)? If so, do source-offer obligations apply to those
   releases?
8. **Contacting upstream.** AndrOBD's code has essentially one author. Would asking
   them for a commercial licence or a relicence be cheaper than the rewrite, or a useful
   addition to it?
