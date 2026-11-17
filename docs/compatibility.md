# Adapter and vehicle compatibility

Which OBD-II adapters and vehicles OBD-Droid has been used with, and what worked. Users report it, and a
maintainer copies each report into the tables below.

**Add yours:** post in [Discussions → Adapter compatibility](https://github.com/Wal33D/OBD-Droid/discussions/categories/adapter-compatibility)
or open an [Adapter / vehicle report](https://github.com/Wal33D/OBD-Droid/issues/new?template=adapter_report.yml).
Reports that say "everything works" are as useful as failures.

No VINs, licence plates or locations go in this file or in the reports it links to.

## Legend

| Mark | Meaning |
|------|---------|
| ✅ | Reported working |
| ⚠️ | Works with caveats (see Notes) |
| ❌ | Reported not working |
| (blank) | Not tried / not reported |

Columns: **Conn** = connects and stays connected, **Live** = live data, **Read** = read fault codes,
**Clear** = clear fault codes, **FF** = freeze frame, **I/M** = emissions readiness, **VIN** = reads the VIN,
**Multi** = full vehicle scan finds more than one ECU.

## Adapters

One row per adapter model and connection type, summarising every vehicle it was reported on.

| Adapter | Chip / firmware | Connection | Conn | Live | Read | Clear | FF | I/M | VIN | Multi | Vehicles | Reports |
|---------|-----------------|------------|------|------|------|-------|----|-----|-----|-------|----------|---------|

_No community reports yet._

## Vehicles

One row per adapter + vehicle report.

| Vehicle (year make model engine) | Protocol | Adapter | Connection | Overall | Notes | App / Android | Report |
|----------------------------------|----------|---------|------------|---------|-------|---------------|--------|

_No community reports yet._

## Known adapter issues

General notes that apply across many reports go here (for example clone ELM327 v2.1 chips that answer
`ATI` but ignore some commands, or BLE adapters that need the phone's location permission to be found).
Each note links the reports that back it.

_None recorded yet._

## For maintainers: adding a report

1. Check the report has no VIN, plate, location or owner name in its text or screenshots. If it does, edit
   the post to remove them (or ask the poster to) before linking it.
2. Add a row to **Vehicles**, linking the Discussion or issue in the Report column (`#123`).
3. Update the adapter's row in **Adapters**: add the vehicle, and lower a mark if the new report failed
   (one ❌ beside several ✅ becomes ⚠️ with a note).
4. If the report found a bug, open an issue from it with the Bug report template and link both ways.
5. Label the Discussion `added-to-compat` and mark the answer, so the queue shows what's left.
