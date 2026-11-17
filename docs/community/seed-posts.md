# Seed post drafts (obd-l3v)

**DRAFTS. Nothing here has been posted.** Each one goes out only after the gates in [README.md](README.md#4-seed-posts-drafts-only)
are met and the owner confirms that post. They're written in the owner's voice, as the developer, because every
community below expects people to say when they're promoting their own work.

Fill the placeholders before posting: `[Play link]`, `[price]`, `[Discord invite]` (drop the line if there is
no server yet). Re-read each community's rules on the day: they change, and several limit self-promotion
(weekly threads, flair, account age or karma).

What the drafts claim, all checked against the app on 2026-11-17: ELM327-compatible adapters over Bluetooth
Classic, BLE, Wi-Fi and USB; live data as lists, charts and a HUD; read and clear fault codes with freeze frame;
emissions readiness (I/M monitors); full vehicle scan that finds the ECUs; VIN decode with NHTSA recalls; CSV
logging with GPS. Don't add features that aren't in the released build. The drafts leave out vehicle history
and the AI copilot on purpose: one needs a private backend, the other the user's own OpenAI key.

---

## 1. r/OBD2

**Rules to check:** self-promotion allowed if disclosed? App posts may need a flair.

**Title:** I built an open-source OBD-II app for Android and I'd like adapter reports (good or bad)

**Body:**

> I'm the developer of OBD-Droid, an Android app for ELM327-style adapters. The source is MIT on GitHub and the
> app is on Play: [Play link] ([price]).
>
> What it does today:
> - Live data as lists, charts or a HUD, with CSV logging (GPS optional)
> - Read and clear codes, freeze frame, emissions readiness monitors
> - A full scan that finds the ECUs on the bus
> - VIN decode and NHTSA recalls
> - Bluetooth Classic, BLE, Wi-Fi and USB adapters
>
> What I need: I've tested it on a bench simulator and [owner: the cars and adapters you've used], and that's
> nowhere near the range of cheap clones out there. If you try it, I'd love to hear which adapter and car you used and what did or didn't work.
> There's a form for it in GitHub Discussions, and the results go into a public compatibility list:
> https://github.com/Wal33D/OBD-Droid/discussions/categories/adapter-compatibility
>
> Bug reports go to GitHub Issues. Please leave your VIN out of anything you post.

---

## 2. r/MechanicAdvice

**Don't post an announcement here.** The sub is for people asking for help, and an app launch post is likely
to be removed as advertising. Instead the owner (or nobody) answers questions where a scan tool reading would
actually help, and mentions the app only when it fits, with the disclosure. Reusable reply:

> If you have an ELM327 Bluetooth adapter (the $15 kind), a free scan app can show you the stored and pending
> codes plus the freeze frame, which tells you the speed, temperature and load when the code set. Post those
> here and people can say a lot more. (Disclosure: I make one of those apps, OBD-Droid, [Play link], but any
> app that shows freeze frame data will do.)

Never diagnose from the app's output alone in a reply, and never suggest clearing a code to pass an inspection.

---

## 3. r/AndroidApps

**Rules to check:** developer posts usually need the "Dev" flair, and some weeks restrict self-promotion to a
thread. Account age/karma minimums may apply.

**Title:** [DEV] OBD-Droid: open-source car diagnostics with live data, codes, freeze frame and recalls

**Body:**

> Hi, I'm the developer. OBD-Droid turns a cheap ELM327 OBD-II adapter (Bluetooth, BLE, Wi-Fi or USB) into a
> diagnostics tool:
>
> - Live engine data as lists, charts or a HUD, with CSV logging
> - Read and clear fault codes, with freeze frame
> - Emissions readiness, so you know before an inspection whether the monitors are ready
> - A full scan of the modules on the car
> - VIN decode and open NHTSA safety recalls
>
> Price: [price]. Source (MIT): https://github.com/Wal33D/OBD-Droid
> Play: [Play link]
>
> Feedback is very welcome, especially which adapter and car you used.

---

## 4. r/embedded (the simulator)

**Gate:** the simulator's README publishes a full VIN and calls its data "real Mercedes-Benz data". Replace it
with the standard test VIN (and check the firmware and history for the same VIN) before this post or the
Hackaday tip. Beads: obd-20h (simulator), obd-wqj (the matching problem in this repo).

**Rules to check:** project posts are fine if they're technical; lead with the hardware, not the app.

**Title:** A Teensy 4.0 OBD-II ECU simulator (CAN, SAE J1979) for testing scan tools without a car

**Body:**

> I needed a repeatable way to test an OBD-II app without sitting in a driveway, so I built an ECU simulator on
> a Teensy 4.0 (FlexCAN_T4 plus an MCP2562 CAN transceiver). It answers SAE J1979 requests over ISO 15765-4
> (CAN 11-bit, 500 kbit/s):
>
> - Mode 01: 44 live PIDs, which move as the simulator cycles through idle, city, accelerating and highway
> - Mode 02: freeze frames; Mode 03: stored codes; Mode 04: clear codes, which also resets the monitors
> - Mode 09: VIN and other vehicle info, with ISO-TP multi-frame responses
>
> Each mode lives in its own file and registers itself at compile time, so adding Mode 06 or 07 is one new file.
>
> Repo (MIT): https://github.com/Wal33D/Teensy40_OBDII_simulator
>
> Things that were more interesting than I expected: [owner fills in one or two from experience].
>
> It's what I test my Android app against (OBD-Droid, also open source), but it works with any scan tool that
> plugs into an OBD-II port. Happy to answer questions about the CAN side.

The mode and PID list above was checked against the simulator's README on 2026-11-17; check it again on the
day in case it changed.

---

## 5. XDA Forums

**Where:** Android Apps & Games forum, as a new app thread. XDA expects the developer to keep the first post
up to date (changelog, download link), so only post when someone will maintain the thread.

**Title:** [APP][5.0+] OBD-Droid: OBD-II diagnostics for ELM327 adapters (open source)

**First post:**

> **OBD-Droid** reads your car's diagnostics through an ELM327-compatible OBD-II adapter.
>
> **Features**
> - Bluetooth Classic, BLE, Wi-Fi and USB adapters
> - Live data: list, chart and HUD views; CSV logging with optional GPS
> - Fault codes: read, clear, freeze frame
> - Emissions readiness (I/M monitors)
> - Full vehicle scan / ECU list
> - VIN decode and NHTSA recalls
>
> **Download:** [Play link] ([price])
> **Source:** https://github.com/Wal33D/OBD-Droid (MIT)
> **Bugs:** GitHub Issues. **Adapter reports:** GitHub Discussions.
> **Requires:** Android 5.0+ and an ELM327-compatible adapter.
>
> **Changelog**
> - [version]: first public release.
>
> Please don't post your VIN or logs that contain it.

(minSdk is 21, which is Android 5.0. Update the title if that changes before release.)

---

## 6. Hackaday tip line

**Gate:** same simulator check as r/embedded. Hackaday covers hardware hacks, so the tip is about the simulator;
the app is context. Send through their tip form or tip email (check the address on hackaday.com on the day).

**Subject:** Teensy 4.0 OBD-II ECU simulator for testing scan tools

**Body:**

> Hi Hackaday,
>
> I built an open-source OBD-II ECU simulator on a Teensy 4.0 so I could test a car diagnostics app without a
> car. It speaks SAE J1979 over CAN (ISO 15765-4, 11-bit, 500 kbit/s): 44 live PIDs that move through a
> simulated drive, freeze frames, stored and cleared trouble codes, and the VIN over multi-frame ISO-TP. Any
> scan tool or ELM327 adapter can talk to it.
>
> https://github.com/Wal33D/Teensy40_OBDII_simulator (MIT)
>
> The Android app I test with it is also open source: https://github.com/Wal33D/OBD-Droid
>
> Thanks,
> [owner name], Somnus Labs

---

## 7. F-Droid

Skipped: the bead asked for this only if the licence became GPL. It stays MIT and ships on Play. See
[README.md](README.md#5-f-droid-not-now).
