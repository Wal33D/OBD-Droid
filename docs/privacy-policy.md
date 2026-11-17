# OBD-Droid Privacy Policy

<!--
Draft for the Play listing and the product website (obd-0n9). Written from what the app does in
app/src/java as of version 0.1.0. Before publishing, the owner fills the two bracketed fields
(contact email, effective date) and confirms the AutoCheck line. When a feature that sends data
off the phone is added or changed, update the matching section here in the same change.
-->

**Effective date:** [date of first publication]
**App:** OBD-Droid (Android, package `com.obddroid`)
**Publisher:** Somnus Labs Inc. ("we", "us")
**Contact:** [privacy contact email]

OBD-Droid reads diagnostic data from your vehicle through an ELM327-compatible OBD-II adapter and
shows it on your phone. This policy explains what the app collects, where that data goes, and the
choices you have.

## The short version

- We do not run accounts, we do not run analytics or advertising, and the app contains no
  tracking or crash-reporting SDKs.
- Vehicle data, scan results, trip logs and settings are stored **on your phone**. We do not
  receive them.
- Some features you choose to use send specific data to a third-party service (listed below).
  Nothing is sent to these services unless you use the feature.
- We do not sell or share personal information for advertising.

## What the app stores on your phone

| Data | Why |
|---|---|
| Live sensor readings (PIDs), fault codes (DTCs), freeze-frame data, readiness monitors | To display diagnostics and keep a scan history |
| Vehicle identification number (VIN), make, model, year read from the vehicle or decoded | To identify the vehicle, decode it and look up recalls |
| Vehicle history and saved scan reports | So you can review past scans |
| Track Mode sessions: sensor data with GPS position, speed and time | To show laps and routes when you use Track Mode |
| Custom PID definitions | Your own sensor formulas |
| CoPilot conversation index (one thread per vehicle VIN) | To continue a CoPilot conversation for that vehicle |
| Settings, including an OpenAI API key and Remote Telemetry server login if you enter them | To run those features; kept in the app's private storage, which other apps cannot read |
| Paired adapter name and address | To reconnect to your adapter |

This data stays on the device until you delete it, clear the app's storage, or uninstall the app.
Android's own backup service may include the app's data in your device backup to your Google
account if backup is turned on in your phone's settings; that backup is governed by Google's
terms, and you control it in Android settings.

## When data leaves your phone

Each item below happens only when you use the feature described.

| Feature | Data sent | Sent to |
|---|---|---|
| VIN decoding | The VIN | U.S. NHTSA vPIC service (vpic.nhtsa.dot.gov) |
| Recall lookup | The VIN or the make, model and year | U.S. NHTSA recalls service (api.nhtsa.gov) |
| Vehicle history report | The VIN | AutoCheck vehicle history service [owner: confirm provider and endpoint operator] |
| CoPilot assistant (off by default) | Your chat messages and the vehicle data CoPilot reads to answer (for example fault codes, scan results, VIN) | OpenAI, using the API key **you** enter. Conversations are kept as threads in your OpenAI account until you delete them (Settings > CoPilot > Delete all conversations removes them from OpenAI and from the phone). OpenAI's terms and privacy policy apply to your key |
| CoPilot spoken replies | The text of CoPilot's replies | OpenAI text-to-speech, using your API key |
| CoPilot voice input | Your spoken request | Your phone's speech recognition service (for example Google), through Android's standard speech API; the app receives only the recognized text |
| Remote Telemetry (off by default) | The sensor values you select | The MQTT server **you** configure, with the login you provide |
| Track Mode map | Map tile requests for the area shown | OpenStreetMap tile servers (tile.openstreetmap.org) |
| Vehicle database update | No personal data; the app downloads a VIN-decoding database file | jsDelivr CDN |
| Help videos | Opens a link | YouTube, in your browser or the YouTube app |
| Share / export a report | The report you chose to share | The app or person you pick in Android's share sheet |

Like any internet request, these services can see your device's IP address and the time of the
request. Their handling of data is covered by their own privacy policies.

The app does not send anything to Somnus Labs servers.

## Permissions

| Permission | Used for |
|---|---|
| Bluetooth / nearby devices | Finding and connecting to your OBD adapter |
| Location | Track Mode and GPS telemetry (position and speed alongside sensor data); on older Android versions, Bluetooth scanning also requires it |
| Microphone | CoPilot voice input, only while you are speaking to it |
| Notifications | Showing connection status while the app keeps your adapter connected |
| Foreground service | Keeping the adapter connection alive while you drive |
| Internet / network state | The online features listed above; Wi-Fi adapters |

USB adapters use Android's USB permission prompt, which appears when you plug the adapter in.

You can deny or revoke any permission in Android settings; the related feature stops working and
the rest of the app keeps working.

## Children

OBD-Droid is a vehicle diagnostics tool and is not directed at children under 13. We do not
knowingly collect personal information from children.

## Your choices

- Leave CoPilot and Remote Telemetry off, or turn them off at any time in Settings.
- Delete all CoPilot conversations (on the phone and in your OpenAI account) in Settings.
- Remove your OpenAI API key or telemetry password in Settings.
- Clear the app's storage in Android settings to remove scan history, Track Mode sessions,
  saved reports and settings. Uninstalling removes all app data from the phone.
- Because we do not hold your data on our servers, there is nothing for us to delete or export on
  our side. For data held by a third-party service above, contact that service.

## Security

Data the app stores lives in its private storage on your phone, which Android does not let other
apps read. Requests to the services above use HTTPS. A Remote Telemetry server you configure uses
TLS only if you choose the SSL option for it. Do not reuse a password for your telemetry server
that you use elsewhere.

## Changes

If this policy changes, we will update the effective date above and post the new version at the
same address. Material changes will also be noted in the app's release notes.

## Contact

Questions about this policy: [privacy contact email], Somnus Labs Inc.
