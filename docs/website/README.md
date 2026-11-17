# OBD-Droid product website: domain shortlist and site plan (obd-0n9)

Status: **waiting on the owner's choice of domain.** Nothing has been bought, no DNS has changed, and
no site repo exists yet. Under the bead, the new repo and rig are built only after the owner
approves a domain. The privacy policy the Play listing needs is already written:
[`docs/privacy-policy.md`](../privacy-policy.md).

## Domain shortlist

Checked 2026-11-17 against the registries' public RDAP services (Verisign for .com, Google
Registry for .app/.dev, Identity Digital for .io). A 404 means the registry has no record, so the
name is unregistered and can be bought at a normal registrar price. Re-check on the day of
purchase.

| Domain | Status on 2026-11-17 | Notes |
|---|---|---|
| **obddroid.app** | Available | **Recommended.** Exact name. `.app` is HTTPS-only (HSTS preload), which suits a static site. Usually about $15–20/yr |
| obddroid.dev | Available | Also HTTPS-only. Reads as a developer tool rather than a consumer app |
| getobddroid.com | Available | Good `.com` fallback; "get" prefix is common for app landing pages |
| obd-droid.app | Available | Hyphen matches the GitHub repo name (OBD-Droid), but people rarely type hyphens. Buy only as a redirect to obddroid.app if wanted |
| obd-droid.com | Available | Same: a redirect at most |
| obddroidapp.com | Available | Weaker fallback |
| obddroid.io | Available | Costs more (about $35–60/yr) and adds nothing over .app |
| obddroid.com | **Taken** | Registered 2017-12-14 through DomainPeople, nameservers at Web Hosting Canada, currently expires **2026-12-14**. If it's not ours, it's someone else's, and it can only be had if they let it lapse or sell it. Worth a look after 2026-12-14 plus the registry grace periods (roughly 75 days), but don't plan on it |

Existing Somnus Labs site: `www.somnuslabs.com` (hosted on Vercel) has no OBD-Droid page and no
`/privacy` page today. A subpath (`somnuslabs.com/obd-droid`) works as a stopgap or as the
company's product page linking out. It's a weaker home for the SEO DTC directory, which works
best on its own domain.

**Recommendation:** buy `obddroid.app` (one domain, one Confirm). Optional later: `getobddroid.com`
as a redirect if a `.com` is wanted for print or word of mouth.

Owner Confirm text (for the mayor to put in the inbox):

> Buy obddroid.app for the OBD-Droid website (about $15–20/yr, 1 year, auto-renew on). Nothing
> else is bought. Confirm / Deny.

## Interim privacy-policy URL

Play Console needs a public privacy-policy URL before the site exists. Once this branch lands,
`https://github.com/Wal33D/OBD-Droid/blob/main/docs/privacy-policy.md` is public (the repo is public)
and works for the listing. After the site is live, point the listing at
`https://obddroid.app/privacy` and keep the GitHub copy as the source.

Before either URL is used, the owner fills the contact email and the effective date in the
policy, and confirms the AutoCheck line.

## Site plan (built after the domain is approved)

Stack: Astro, static output. Astro fits a content site well: Markdown pages, near-zero JavaScript,
and fast builds for a few thousand DTC pages. Hosted on Vercel like somnuslabs.com, as a new
repo (for example `Wal33D/obddroid-site`) and a new rig. The mayor deploys and makes the DNS change.

| Page | Content | Source |
|---|---|---|
| `/` | What it does; supported ELM327 adapters (Bluetooth Classic, USB, Wi-Fi; BLE is not supported yet); screenshots; GitHub link; Play badge once released | `README.md`, `docs/screenshots/` |
| `/privacy` | Privacy policy | `docs/privacy-policy.md` (copied in at build time so there is one source) |
| `/docs/adapter-setup` | Pairing and connecting each adapter type, common clone-adapter quirks | New; from `docs/protocol-spec.md` and the ELM fixes in git history |
| `/docs/protocols` | Supported OBD-II protocols (ISO 15765-4 CAN 11/29-bit, ISO 9141-2, ISO 14230-4 KWP2000, SAE J1850 PWM/VPW) | `docs/protocol-spec.md` |
| `/docs/pids` | Supported Mode 01 PIDs and units; custom PIDs | App PID tables |
| `/docs/dtc-lookup` | How fault-code lookup works offline | `modules/dtc-database` |
| `/changelog` | Release notes | Git tags / release notes |
| `/hardware-lab` | The Teensy 4.0 ECU simulator the app is tested against | obdsim rig README |
| `/codes/` and `/codes/<code>` | SEO directory: one page per DTC (meaning, common causes, related codes) and an index by system (P/B/C/U) | `modules/dtc-database` data; page templates reused from the private `Wal33D/nolemons` OBD code directory |

Notes for the build bead:

- The DTC directory needs a licence check on the code descriptions before publishing thousands of
  pages. Generic SAE J2012 code titles are widely published, but manufacturer-specific text may
  not be ours to republish. Reusing nolemons templates is fine, since it's our own private repo.
- No analytics or tracking scripts on the site unless the privacy policy is updated to match.
- Screenshots must come from `docs/screenshots/` or a Robolectric/preview test, never from a
  device an agent drives. Check them for real VINs or plates before use.
- `obddroid.app` sits on the HSTS preload list, so the site must serve HTTPS from day one (Vercel
  does this).

## Next steps

1. Owner: Confirm the domain (text above), and fill in the policy's contact email and effective date.
2. Mayor: buy the domain and point DNS at Vercel after the Confirm.
3. New bead: create the `obddroid-site` repo and rig and build the pages above (Grok-suitable,
   except deploy and DNS).
