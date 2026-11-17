# OBD-Droid

**Full-stack Android diagnostics suite built for technicians, tuners, and enthusiasts.**

OBD-Droid turns any ELM327-compatible adapter into a professional-grade vehicle workstation. From live telemetry to AutoCheck intelligence and an AI copilot, every module is engineered to make complex diagnostics feel effortless.

> **TL;DR**
> - Plug in any ELM327 adapter, launch the Android app, and get live data in under a minute.
> - One tap runs a full ECU scan, logs CSV/GPS telemetry, and syncs findings with your AI copilot.
> - Safety recalls, AutoCheck history, VIN decode, and report exports are all presented in-app with the same cohesive UI.

---

## 🚀 Why It Stands Out

- **Mission Control for Your Vehicle** – Real-time dashboards, custom PIDs, HUD mode, and CSV/GPS logging.
- **Deep Diagnostics Pipeline** – ECU discovery, freeze frames, emissions readiness, and health scoring in one flow.
- **VIN-Aware Intelligence** – AutoCheck history with ownership, odometer, and open recalls side-by-side with NHTSA data.
- **AI Copilot** – OpenAI ChatGPT delivers contextual answers using live vehicle data and prior conversations.
- **Modular Architecture** – Service-driven Android app, standalone companion APIs, and reusable libraries.

> _“From handshake to fix recommendation, OBD-Droid shows how far Android can stretch inside the garage.”_

---

## 📸 Product Tour

<table>
  <tr>
    <td><img src="docs/screenshots/dashboard.png" width="260" alt="Dashboard" /></td>
    <td>Live vehicle overview with quick actions, health summaries, and shortcut metrics.</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/live-data.png" width="260" alt="Live Data" /></td>
    <td>Customizable PID lists, chart overlays, HUD mode, and streaming CSV capture.</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/fault-codes.png" width="260" alt="Fault Codes" /></td>
    <td>Rich DTC cards with freeze frames, remedy hints, and AI follow-ups.</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/vehicle-history.png" width="260" alt="Vehicle History" /></td>
    <td>AutoCheck report viewer with ownership timeline, odometer verification, and export actions.</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/recalls-all.png" width="260" alt="Safety Recalls" /></td>
    <td>Segmented toggle combining public NHTSA campaigns with VIN-specific AutoCheck open recalls.</td>
  </tr>
</table>

<details>
<summary>More UI moments</summary>

- <img src="docs/screenshots/dashboard-footer.png" width="220" alt="Vehicle Footer" /> – Persistent vehicle identity footer across screens.
- <img src="docs/screenshots/live-data-3.png" width="220" alt="Live Data (Scrolled)" /> – Extended PID telemetry with HUD-ready formatting.
- <img src="docs/screenshots/fault-code-detail.png" width="220" alt="Fault Code Detail" /> – Deep dive card with freeze frame, remedy, and AI assistance.
- <img src="docs/screenshots/full-vehicle-scan.png" width="220" alt="Full Vehicle Scan" /> – ECU discovery progress and baseline snapshot library.
- <img src="docs/screenshots/emissions.png" width="220" alt="Emissions Readiness" /> – I/M monitors with pass/fail flags for inspection prep.
- <img src="docs/screenshots/vin-decoder.png" width="220" alt="VIN Decoder" /> – VIN decode powering downstream features.
- <img src="docs/screenshots/vehicle-history-export.png" width="220" alt="Vehicle History Export" /> – Share-ready PDF/JSON export drawer.

</details>

---

## 🎛️ Feature Gallery

<table>
  <tr>
    <td><img src="docs/screenshots/emissions.png" width="220" alt="Emissions Readiness" /></td>
    <td><img src="docs/screenshots/full-vehicle-scan.png" width="220" alt="Full Vehicle Scan" /></td>
    <td><img src="docs/screenshots/ecu-list.png" width="220" alt="ECU Modules" /></td>
    <td><img src="docs/screenshots/live-data-6.png" width="220" alt="Live Data Telemetry" /></td>
  </tr>
  <tr>
    <td>Inspection-ready I/M monitor dashboard showing pass/fail status at a glance.</td>
    <td>Step-by-step ECU discovery with progress tracking and baseline snapshot creation.</td>
    <td>Comprehensive ECU list with addressing, protocol metadata, and change tracking.</td>
    <td>Live powertrain data fused with GPS & motion telemetry from the device sensors.</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/fault-codes-start-screen.png" width="220" alt="Fault Codes Start" /></td>
    <td><img src="docs/screenshots/fault-codes-detected.png" width="220" alt="Fault Codes Detected" /></td>
    <td><img src="docs/screenshots/fault-code-detail.png" width="220" alt="Fault Code Detail" /></td>
  </tr>
  <tr>
    <td>Fault code launcher prompting users to initiate a fresh scan.</td>
    <td>Detected DTC list with severity badges and quick actions.</td>
    <td>Drill-down view showing summaries, remedies, and export options.</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/vehicle-history.png" width="220" alt="Vehicle History" /></td>
    <td><img src="docs/screenshots/vehicle-history-2.png" width="220" alt="Vehicle History Timeline" /></td>
    <td><img src="docs/screenshots/vehicle-history-3.png" width="220" alt="Vehicle History Details" /></td>
    <td><img src="docs/screenshots/vehicle-history-export.png" width="220" alt="Vehicle History Export" /></td>
  </tr>
  <tr>
    <td>AutoCheck overview with vehicle score, alerts, and at-a-glance stats.</td>
    <td>Ownership timeline and odometer verification pulled from AutoCheck.</td>
    <td>Detailed campaign list, title history, and AutoCheck report drill-down.</td>
    <td>One-tap export drawer for PDF/JSON sharing of AutoCheck vehicle history.</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/vin-decoder.png" width="220" alt="VIN Decoder" /></td>
    <td><img src="docs/screenshots/dashboard-footer.png" width="220" alt="Vehicle Footer" /></td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td>VIN decode powering downstream features like recalls and AutoCheck.</td>
    <td>Signature vehicle footer with quick stats, connection state, and VIN context.</td>
    <td></td>
    <td></td>
  </tr>
</table>

---

## 🧩 Feature Deep Dive

### Real-time Telemetry
- Adaptive polling with automatic ISO/KWP/CAN protocol negotiation.
- Multiple view modes (list, chart, dashboard, HUD) backed by shared `ProcessVariables`.
- CSV logger service stitches OBD values with GPS, accelerometer, and metadata for post-drive analysis.
  <br/><img src="docs/screenshots/live-data-6.png" width="280" alt="Live Data with Telemetry" />
  <div><em>Live data + GPS/motion telemetry captured from device sensors alongside vehicle PIDs.</em></div>

### Diagnostics & Scanning
- Full-vehicle scan orchestrator maps ECUs, persistent DTCs, pending codes, and module metadata.
- Baseline scan library compares historic snapshots to spot new modules or faults.
- Emissions center mirrors inspection readiness (I/M monitors, catalyst status, O₂ sensors).

> <img src="docs/screenshots/full-vehicle-scan.png" width="280" alt="Full vehicle scan workflow" />

### Intelligent Assistance
- **CoPilot** (OpenAI ChatGPT) pulls context from active DTCs, vehicle metadata, and prior chats.
- Automatic suggested next steps and part lookup hints based on failure patterns.
- Conversation history cached per vehicle for continuity.

### Vehicle Intelligence
- VIN decode via `VINDecoder` (NHTSA) with manufacturer/trim heuristics.
- AutoCheck companion API delivers premium vehicle history, open recalls, ownership, and odometer insights.
- PDF export pipeline for shop handoffs, customer reports, and archive records.
- Hybrid recall center blending NHTSA campaigns with AutoCheck VIN-specific items.

### Fault Code Workflow
- Launch screen guides the user to initiate a fresh scan before clearing codes.<br/><img src="docs/screenshots/fault-codes-start-screen.png" width="220" alt="Fault Codes Start" />
- Detected DTC list surfaces severity badges, quick actions, and export shortcuts.<br/><img src="docs/screenshots/fault-codes-detected.png" width="220" alt="Fault Codes Detected" />
- Detail cards provide summaries, consequences, remedies, and OEM links in-app.<br/><img src="docs/screenshots/fault-code-detail.png" width="220" alt="Fault Code Detail" />

---

## 🧱 Architecture at a Glance

```
            ┌──────────┐
Adapter ⇨ CommService ⇨ ObdProt ⇨ ObdDataService ─┬─► Live Data / Gauges
            └──────────┘                           ├─► Fault Codes / Emissions
                                                  │   (freeze frames, readiness)
                                                  ├─► ScanOrchestrator → Baseline Library
                                                  ├─► CsvLoggingService (GPS + sensors)
                                                  └─► Feature Modules (CoPilot, Recalls, AutoCheck)

Vehicle Data Service ⇨ AutoCheckService ⇨ VehicleHistoryActivity / RecallActivity
NHTSA API            ⇨ RecallLookupAndroid ⇨ RecallActivity (All Recalls tab)
Claude API           ⇨ CoPilotService ⇨ CoPilotActivity (contextual AI answers)
```

- **Separation of Concerns** – OBD stack lives in its own package, while UI features orchestrate data via managers/services.
- **Request Queues & Caching** – Vehicle data service caches VIN lookups; in-app caching enables instant revisit offline.
- **Configurable Telemetry** – Logging and AI features respond to user settings stored in `SharedPreferences`.

---

## 🔧 Tech Stack

| Layer | Details |
| --- | --- |
| Language | Java 17 (Android), TypeScript/Node (AutoCheck companion) |
| UI | AppCompat + Material Components, RecyclerView, custom cards, HUD mode |
| Architecture | Service + manager pattern, feature-scoped packages, background workers |
| Data | SharedPreferences caching, on-disk CSV, JSON interop for AutoCheck reports |
| Integrations | NHTSA APIs, Experian AutoCheck (Playwright scrape), OpenAI ChatGPT |
| Tooling | Gradle, Android Studio Giraffe+, Lint, unit tests, GitHub Actions (companion API) |
| Submodules | [DTC Database](https://github.com/Wal33D/dtc-database) · [NHTSA Recall Lookup](https://github.com/Wal33D/nhtsa-recall-lookup) · [Automotive Logo Library](https://github.com/Wal33D/automotive-logo-library) |

---

## 📦 Project Map

```
OBD-Droid/
├── app/
│   ├── src/java/com/obddroid/
│   │   ├── features/          # Feature modules (copilot, emissions, recalls, etc.)
│   │   ├── obd/               # Core OBD protocol implementation
│   │   ├── scan/              # ECU discovery & orchestration
│   │   ├── telemetry/         # CSV logging, GPS stitching
│   │   ├── services/          # Foreground/background Android services
│   │   ├── ui/activities/     # Primary screens
│   │   └── utils/             # Helpers, VIN decoding, state managers
├── modules/                   # External libraries (submodules)
│   ├── dtc-database/          # Offline DTC catalog
│   ├── nhtsa-recall-lookup/   # NHTSA API bindings
│   └── automotive-logo-library/
├── docs/                      # Product notes, screenshots
└── README.md
```

---

## ⚙️ Getting Started

### Prerequisites
- Android Studio **Giraffe** (or newer) with **JDK 17**.
- Android SDK targets **API 21 – 34** (Android 5.0 – 14).
- Physical Android device (recommended) with Bluetooth or USB host support.
- ELM327-compatible OBD-II adapter (Bluetooth Classic, BLE serial, USB, or Wi-Fi).

### Clone & Bootstrap

```bash
git clone https://github.com/Wal33D/OBD-Droid.git
cd OBD-Droid

# Pull bundled libraries (DTC database, logos, NHTSA bindings)
git submodule update --init --recursive

# Build & install debug variant
./gradlew :app:assembleDebug
./gradlew :app:installDebug

# Launch on device
adb shell am start -n com.obddroid/.ui.activities.MainActivity
```

> **Note:** The Vehicle History feature requires a **private AutoCheck backend** that you host yourself. This is not included in the open-source release. If you have access to AutoCheck data through your own dealership or subscription, you can stand up your own backend and configure the endpoint in `local.properties`. All other features (live data, fault codes, emissions, recalls via NHTSA, AI copilot) work without it.

---

## 🧪 Developer Workflow

| Task | Command |
| --- | --- |
| Clean build | `./gradlew clean` |
| Compile sources | `./gradlew :app:compileDebugJavaWithJavac` |
| Unit tests | `./gradlew :app:testDebugUnitTest` |
| Lint & static analysis | `./gradlew :app:lintDebug` |
| Instrumentation tests | `./gradlew :app:connectedDebugAndroidTest` |
| Generate signed bundle | `./gradlew :app:bundleRelease` |

**Environment toggles**
- `gradle.properties` houses feature flags, logging verbosity, and Claude API settings.
- `app/build.gradle` encapsulates flavors, signing configs, and dependency graph.

---

## 🔌 Integrations

| Integration | Purpose | Notes |
| --- | --- | --- |
| **Vehicle Data Service** | Premium vehicle history & open recall data | **Private — not included.** Requires your own AutoCheck backend. Configure endpoint and API key in `local.properties`. |
| **NHTSA Recall API** | Campaign listings, remedy info, VIN decodes | Back bone for “All Recalls” tab and VIN decoder. |
| **OpenAI ChatGPT** | Conversational diagnostics | Context-aware responses with client-side redaction. |
| **VIN Decoder** | Make/model/trim heuristics | Normalizes manufacturer naming for recall lookups. |

---

## 🗺️ Roadmap

- [ ] Jetpack Compose migration for telemetry dashboards.
- [ ] BLE adapter improvements and auto-reconnect heuristics.
- [ ] Cloud sync for baseline scans and AutoCheck reports.
- [ ] In-app marketplace for pro data packs (TSBs, repair procedures).
- [x] Integrate AutoCheck open recalls alongside NHTSA campaigns.
- [x] Material-compliant segmented toggle for recalls UI.

---

## 🤝 Meet the Maker

Built and maintained by **Waleed Judah**
📫 aquataze@yahoo.com · 🔗 [LinkedIn](https://www.linkedin.com/in/waleed-judah-53406787/)

> Seriously though — if you're hiring, reach out. All I do is build.

> Found this useful? ⭐ the repo, share it with your community, and keep connected cars transparent.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE). See the `LICENSE` file for details.
