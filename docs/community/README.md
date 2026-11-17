# OBD-Droid community plan (obd-l3v)

Status 2026-11-17: **prepared, nothing public yet.** This branch adds the repo files (issue forms, Discussions
forms, the compatibility list, updated CONTRIBUTING and Code of Conduct) and drafts. Anything that posts in
public, changes the GitHub repo's settings, or creates an account under the owner's name waits for the owner's
Confirm (OWNER-POLICY.md). Agents post nothing.

## What is in this branch

| File | What it is |
|------|------------|
| `.github/ISSUE_TEMPLATE/bug_report.yml` | Bug form: app version, phone/Android, adapter, connection, vehicle, protocol, app area, how often. Warns against VINs. |
| `.github/ISSUE_TEMPLATE/adapter_report.yml` | Adapter / vehicle report form with a "what worked" checklist. Feeds the compatibility list. |
| `.github/ISSUE_TEMPLATE/feature_request.yml` | Feature request: problem first, then proposal. |
| `.github/ISSUE_TEMPLATE/config.yml` | Turns off blank issues; links questions and ideas to Discussions. |
| `.github/DISCUSSION_TEMPLATE/*.yml` | Forms for the Adapter compatibility, Vehicle reports and Feature ideas categories. Q&A and Show and tell stay free text. |
| `docs/compatibility.md` | Community-maintained adapter and vehicle matrix, empty, with the maintainer steps for adding a report. |
| `CONTRIBUTING.md` | Somnus Labs Inc., MIT, no-VIN rule, how to report adapters, tests use transcripts, and "where code may come from" (no GPL code, clean-room rule for the rewrite). |
| `CODE_OF_CONDUCT.md` | Somnus Labs, scope covers Discussions and any Discord, private reports to a contact address (placeholder), no posting others' VINs, no defeat-device advice. |
| `docs/community/seed-posts.md` | Drafts of the launch posts, one per place, with what to check before each. |

The `config.yml` links point at Discussions categories, so they 404 until step 1 below is done. Do step 1 on
the day this branch lands, or hold the merge until then.

## Order of work, and what gates each step

### 1. GitHub Discussions and labels (low risk, do first)

One Confirm for the owner. GitHub has no API for creating Discussions categories, so this is clicks in the repo
settings, done by the mayor in the browser after the Confirm:

1. Settings → General → Features → tick **Discussions**.
2. Discussions → categories (pencil icon). GitHub creates Announcements, General, Ideas, Polls, Q&A and Show and
   tell. Make the list:
   - **Announcements** (keep; only maintainers post)
   - **Q&A** (keep; question/answer format, slug `q-a`)
   - **Adapter compatibility**: new, question/answer format, so the slug is `adapter-compatibility`
   - **Vehicle reports**: new, open-ended discussion format, slug `vehicle-reports`
   - **Feature ideas**: new, open-ended discussion format, slug `feature-ideas`. Delete the default **Ideas**
     category (or rename it only if the slug comes out as `feature-ideas`; the form file name must match the slug).
   - **Show and tell** (keep, slug `show-and-tell`)
   - Delete **General** and **Polls** so people pick a real category.
3. Labels (Issues → Labels): add `needs-triage`, `compatibility` and `added-to-compat`. `bug` and `enhancement`
   exist by default.
4. Check: New issue shows three forms and no blank option; New discussion in Adapter compatibility shows the form.

### 2. Compatibility list

`docs/compatibility.md` starts empty and is filled from reports, never from our own customers' or dealership
vehicles. A maintainer (mayor, or a polecat bead when there's a batch) copies reports in, checking each for a VIN
or plate first. The owner's own bench runs can go in as rows once the bench runbook has an adapter and car to name.

### 3. One chat channel: Discord, later

The bead asks to pick one. **Recommendation: a Somnus Labs Discord server, opened when the Play listing is live
and Discussions has its first few outside posts; not a subreddit.**

- Adapter trouble is easier to sort out live, with screenshots, than in a forum thread.
- One server can host OBD-Droid, the Teensy simulator and later Somnus Labs products, with channels
  `#obd-droid`, `#adapters`, `#teensy-simulator`, `#dev`, plus `#announcements` and `#rules` (the Code of Conduct).
- A subreddit with no users looks abandoned and needs the same moderation. Reddit is better used by posting in the
  existing subs (step 4) than by owning one. Registering `r/obddroid` early just to hold the name is a separate
  small Confirm if the owner wants it.
- Discord is not searchable from the web, so answers that matter (adapter quirks, fixes) get copied to
  Discussions or `docs/compatibility.md`. Discussions stays the record.

Opening the server needs the owner's Discord account (it is created under the owner's name): one Confirm with the
channel list above, server name "Somnus Labs", and a link back to the Code of Conduct. The invite link then goes
in README and CONTRIBUTING.

### 4. Seed posts (drafts only)

Drafts are in [seed-posts.md](seed-posts.md): r/OBD2, r/MechanicAdvice, r/AndroidApps, r/embedded (simulator),
XDA, and the Hackaday tip line. Each is posted by the owner from the owner's own accounts, or by the mayor after a
Confirm that names the post. **None go out until every gate below is met:**

| Gate | Why | Bead |
|------|-----|------|
| The AndrOBD-derived code is replaced and the licence text fixed | The repo says MIT. Pointing a crowd at it while GPL-derived files are still there invites a public licence complaint. | obd-52k (rewrite step 3), obd-86c |
| Real VIN and stock-unit data are out of the public repo | `docs/vehicle-profiles` has a full real VIN. Launch posts send people to the repo. | obd-wqj |
| People can install the app | Posts need a link: Play listing, or at least a signed GitHub Release. | obd-jln (Play release epic) |
| Privacy policy URL is live, contact email filled in | The Play listing and every post that links to the app need it. | obd-0n9 |
| Discussions on (step 1), conduct contact address filled in | Posts point people to Discussions for reports and questions. | this bead |
| The simulator repo's real VIN is replaced before the r/embedded and Hackaday posts | Its README publishes a full VIN and calls its data "real Mercedes-Benz data". | obd-20h |

Space the posts out (about one a week) so the replies can be answered. Answering replies in the first day
matters more than the post.

### 5. F-Droid: not now

The bead lists F-Droid "if the licence ends up GPL". The owner chose to stay MIT and sell the app on Play, so
the F-Droid listing is skipped. F-Droid does take MIT apps; if the owner later wants a free FOSS build there,
note that the AI copilot (OpenAI) and vehicle-history features would be flagged as non-free network services,
and F-Droid builds and signs the app itself.

## For the owner (one inbox item per line when the gates are met)

1. **Confirm:** turn on GitHub Discussions on Wal33D/OBD-Droid with the categories and labels in step 1.
2. **Fill in:** the conduct contact address in `CODE_OF_CONDUCT.md` (can be the same address as the privacy policy).
3. **Confirm (later):** open a "Somnus Labs" Discord server with the channels in step 3.
4. **Confirm (later, one per post):** each seed post in `seed-posts.md`, once the gates are met.

## Noticed while doing this

- README's tech table says vehicle history is an "Experian AutoCheck (Playwright scrape)". Launch posts send
  people to the README, so the owner may want that line reworded or removed before step 4. The drafts do not
  mention AutoCheck.
