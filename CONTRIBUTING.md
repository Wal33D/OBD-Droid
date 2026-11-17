# Contributing to OBD-Droid

OBD-Droid is published by **Somnus Labs Inc.** under the [MIT License](LICENSE). Thanks for helping.

You don't need to write code to contribute. The most useful things right now are:

- **Adapter and vehicle reports.** Tell us which adapter and car you used and what worked, in
  [Discussions → Adapter compatibility](https://github.com/Wal33D/OBD-Droid/discussions/categories/adapter-compatibility)
  or with the *Adapter / vehicle report* issue form. They go into [docs/compatibility.md](docs/compatibility.md).
- **Bug reports** with the *Bug report* issue form (adapter, connection, vehicle and protocol fields included).
- **Questions and ideas** in [Discussions](https://github.com/Wal33D/OBD-Droid/discussions): Q&A, Feature ideas,
  Vehicle reports, Show and tell. Issues are for bugs and agreed work.

## Privacy first: no VINs

Never post a VIN, licence plate, location, owner name or an unedited log in an issue, discussion, pull request,
test fixture or commit. Crop the vehicle footer out of screenshots. In code and tests use an invented VIN that
fails the check digit, or the standard test VIN `1HGBH41JXMN109186`.

## Getting started

1. Fork the repository and clone your fork.
2. Initialise submodules: `git submodule update --init --recursive`
3. Open the project in a current Android Studio (its bundled JDK works; the build targets Java 17).
4. Build: `./gradlew :app:assembleDebug`

The build needs no keys. The AI copilot uses an OpenAI key you enter in the app's Settings, and Vehicle
History needs the private backend below; without them those two features are unavailable and everything else
works.

### Optional: vehicle-history backend

The Vehicle History feature needs a private backend that you host yourself. If you have one, put your endpoint
and key in `local.properties` (never commit that file):

```properties
AUTOCHECK_API_BASE_URL=https://your-private-endpoint
AUTOCHECK_API_KEY=your-api-key
```

## Making changes

1. Create a feature branch from `main`.
2. Keep commits focused, with a message that says what changed and why.
3. Before you open a pull request:
   - `./gradlew :app:testDebugUnitTest` (unit tests)
   - `./gradlew :app:lintDebug` (new lint errors fail the build; existing ones are in the baseline)
   - `./gradlew :app:assembleDebug`
4. Open a pull request against `main`.

### Tests

Unit tests never talk to a real adapter, car, phone or online service. Protocol tests replay recorded ELM327
transcripts (see `app/src/test/java/com/obddroid/obd/`); network features are stubbed. If you add a transcript
recorded from a real car, remove the VIN and any other identifying responses first.

Bench testing with real hardware is welcome as a report (what adapter, what car, what happened), not as an
automated test. For repeatable bench work, the
[Teensy 4.0 OBD-II simulator](https://github.com/Wal33D/Teensy40_OBDII_simulator) answers like an ECU.

## Pull request guidelines

- Keep PRs small and about one change.
- Say what changed and why, and how you checked it.
- If the change affects the UI, include screenshots (from an emulator or your phone, VIN cropped out).
- Don't add a heavy dependency without saying why in the PR.

## Code style

- Java 17 source compatibility; Kotlin is fine for new code.
- Follow the conventions in the surrounding code.
- No wildcard imports.

## Where code may come from

OBD-Droid is being made fully MIT, so everything in it has to be code that can be published under MIT.

- Write your own code, from the public standards (SAE J1979, ISO 15765-4, the ELM327 data sheet) and this
  repository's own spec ([docs/protocol-spec.md](docs/protocol-spec.md)).
- **Do not copy code from GPL-licensed projects** (including other OBD apps such as AndrOBD), from
  decompiled apps, or from anywhere whose licence you don't know. Reading a GPL project's code and then
  writing the same thing from memory counts as copying.
- Code under a permissive licence (MIT, BSD, Apache-2.0) is fine with its copyright notice kept and a line in
  [NOTICE](NOTICE). Say where it came from in the PR.
- Some files carry older licence history and are being replaced; they are listed in
  [docs/rewrite/derived-paths.txt](docs/rewrite/derived-paths.txt) and the plan is in
  [docs/rewrite/PLAN.md](docs/rewrite/PLAN.md). Please ask in an issue before building on them, and don't
  remove or edit their licence headers.

## Licence

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE), the same
licence as the project, and that you have the right to submit them under it.

## Conduct

Everyone taking part follows the [Code of Conduct](CODE_OF_CONDUCT.md).
