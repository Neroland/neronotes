# NeroNotes privacy notice

NeroNotes is a Minecraft mod. This notice explains what data the mod handles, what it sends off
your machine, and your choices. It applies to the mod only — not to any Minecraft server you play
on, whose operator is responsible for their own server data.

> NeroNotes is in development. This notice covers the current build (optional error reporting and
> the planned gameplay data model) and will be finalised alongside the 0.1.0 release as the
> composition, publishing and library features land.

## What is collected (error reporting)

NeroNotes includes **optional, opt-out error reporting** via Sentry so crashes involving NeroNotes
code can be found and fixed. When an error is reported, the report contains:

- the exception type, message and stack trace (only errors that touch NeroNotes/Neroland code are
  sent; everything else is dropped before sending);
- the NeroNotes version and whether the game runs in a development environment.

Reports are scrubbed before sending: your home directory path and operating-system username are
removed. Reporting is hard-capped at 10 events per game session and duplicate errors are sent only
once per session. No reporting occurs at all until a real reporting endpoint (DSN) is configured in
the build.

## What is not collected

- No player names, UUIDs, IP addresses, chat, or coordinates.
- No composition titles, disk names, or author display names — player-authored text never leaves
  your server in telemetry.
- No list of other installed mods is transmitted.
- No analytics, no session tracking, no advertising identifiers.

## How to opt out

Set `client.telemetry_opt_out = true` in `config/neronotes.properties`. The mod then performs no
error reporting whatsoever. Opting out has no gameplay effect.

## Compositions, authorship and the shared library (gameplay data stored in your world)

NeroNotes' gameplay features (arriving through the 0.1.0 stages) store data **inside your world
save, on the server you play on** — it is never sent to the mod authors:

- composition scores you create and press onto disks;
- disk authorship (your player UUID plus your chosen display name, or an anonymous marker —
  publishing anonymously is a first-class choice);
- published library entries and aggregate download counts (no listening history, no per-download
  identity records, no play logs);
- resonance channel ownership and trust lists.

This data supports export and erasure through Neroland Core's shared player-data erasure hook: one
request purges a player across all Neroland mods. When an author is erased, the link is severed
but the work is kept — the entry becomes anonymous and other players' downloaded disks keep
working. The exact commands and the retention sweep for inactive players are documented in the
wiki as those features ship.

## Legal basis and your rights

Error reporting relies on **legitimate interest** (GDPR Art. 6(1)(f) / POPIA s11(1)(f)): keeping
the mod stable, using the minimum data needed, with a full opt-out. You have the right to access,
correct or erase personal data, to object to processing, and to complain to your supervisory
authority (in South Africa, the Information Regulator).

Contact: **[info@neroland.co.za](mailto:info@neroland.co.za)**

## Retention

- Error reports are retained by the error-reporting service only as long as needed to diagnose and
  fix the issue.
- Gameplay data lives in your world save under the server operator's control. NeroNotes ships a
  configurable inactivity window (`data.retention_days`, default 365; 0 disables it) after which an
  inactive player's stored NeroNotes data is purged automatically.

## Changes to this policy

This file is versioned with the mod. Material changes are called out in `CHANGELOG.md` and take
effect with the release that carries them.
