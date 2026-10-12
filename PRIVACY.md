# NeroNotes privacy notice

NeroNotes is a Minecraft mod. This notice explains what data the mod handles, what it sends off
your machine, and your choices. It applies to the mod only — not to any Minecraft server you play
on, whose operator is responsible for their own server data.

## What is collected (error reporting)

NeroNotes includes anonymous error reporting via Sentry (EU servers) so crashes involving NeroNotes
code can be found and fixed. It is **on by default** and **opt-out** (see
[How to opt out](#how-to-opt-out)). When an error is reported, the report contains:

- the exception type, message and stack trace (only errors that touch NeroNotes' own code are
  sent; errors from Neroland Core, other mods or Minecraft are dropped before sending);
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

Set `telemetryEnabled = false` in `config/neronotes.properties` (created on first launch). The mod
then performs no error reporting whatsoever. This is a client-local switch (never synced from a
server), it takes effect on restart, and opting out has no gameplay effect.

## Compositions, authorship and the shared library (gameplay data stored in your world)

NeroNotes' gameplay features store data **inside your world save, on the server you play on** — it
is never sent to the mod authors:

- composition scores you create in the Soundforge and press onto disks;
- disk and library authorship: your player UUID plus your chosen display name, or an anonymous
  marker — publishing anonymously is a first-class choice, and anonymous entries store **no**
  display name (the UUID is kept solely so quota, unpublish and erasure still work);
- published library entries and aggregate download counts — there is **no listening history, no
  per-download identity or timestamp, and no play log**; a download increments one integer;
- resonance channel ownership and trust lists;
- your Soundforge return anchor and session score;
- a last-seen timestamp (UUID + login time only) that exists solely to drive the retention sweep
  below;
- two per-player milestone flags ("has published a composition", "has been listened to") kept in
  Neroland Core's progression-gate store, which Core's own privacy notice and erasure cover; they
  record no composition, channel or listener.

**Access and portability.** Run `/neronotes data export` to export everything above that concerns
you — chat shows a summary, and the full record (including your published scores, base64-encoded)
is written to `neronotes/exports/<your-uuid>.json` inside the world folder, under the server
operator's control. Only your own data is ever included: other players appear at most as counts,
never by name or UUID. Operators can run `/neronotes data export <uuid>` to serve an access
request on a player's behalf.

**Erasure.** Run `/neronotes data erase-me` (then `... confirm`) to irreversibly erase your stored
data. This goes through Neroland Core's shared player-data erasure hook, so one request purges you
across all Neroland mods; operators can equally use Core's `/neroland data eraseme` /
`/neroland data erase <uuid>`. NeroNotes then deletes your Soundforge session, your channels, your
entries on other channels' trust lists and your last-seen record, and refreshes its recovery
backups so no erased row survives in a backup file.

**What happens to a published composition when its author is erased: the link is severed, the work
is kept.** Your library entries lose their author UUID and display name and become anonymous, but
the compositions stay published and every disk other players downloaded keeps playing. Reasoning:
erasure removes the *person* — their identity and everything keyed to it — not other players'
inventories and bases; deleting the works would destroy other people's legitimately obtained
copies without erasing any additional personal data, since the severed entries no longer reference
you at all.

**Pressed disks already in circulation** (in players' inventories, chests and Resonators) carry
the attribution chosen at press time as item data inside the world save. The server cannot
enumerate or rewrite items in offline players' inventories, so these copies are **world data
outside the scope of erasure** — the same way a signed book keeps its signature. The shared
library is the authoritative attribution record and is where erasure severs the link; a disk
pressed anonymously never contained a display name to begin with. We chose to document this
honestly rather than promise an "erase on next load" mechanism, which would itself require keeping
a list of erased UUIDs — retaining exactly the identifier an erasure is meant to remove.

## Paid library copies (only with NeroEconomy installed)

When NeroEconomy is installed, copying a library entry at the Disk Exchanger can cost credits
(`exchanger.library_copy_price`). NeroNotes **stores nothing new** for this: it hands NeroEconomy
the copier's UUID and, only to route the composer's royalty, the entry's author UUID. Anonymous and
erased-author entries have no author UUID, so their price goes to NeroEconomy's Reserve and no
author is ever looked up. Neither side is shown the other: the copier sees only the price and the
split, never the composer's name or UUID, and NeroNotes never logs a UUID or name with a payment.
NeroEconomy records the payment in its own ledger (origin `neronotes/library_copy`), which is
covered by NeroEconomy's privacy notice and is exported and erased with that player's economy data.
Pressed disks are tagged `neroeconomy:untradeable`, so NeroEconomy never lists, vaults or sends
them — the composer UUID and title inside a disk are never stored by the economy.

## Legal basis and your rights

Error reporting relies on **legitimate interest** (GDPR Art. 6(1)(f) / POPIA s11(1)(f)): keeping
the mod stable, using the minimum data needed, with a full opt-out. You have the right to access,
correct or erase personal data, to object to processing, and to complain to your supervisory
authority (in South Africa, the Information Regulator).

Contact: **[info@neroland.co.za](mailto:info@neroland.co.za)**

## Retention

- Error reports are retained by the error-reporting service only as long as needed to diagnose and
  fix the issue.
- Gameplay data lives in your world save under the server operator's control. NeroNotes runs an
  automatic **retention sweep** — shortly after each server start and daily thereafter — that
  purges the stored NeroNotes data of any player inactive longer than `data.retention_days`
  (default 365; 0 disables the sweep). "Inactive" is measured from the last-seen timestamp
  recorded at login; players currently online are never purged. The sweep uses the same purge path
  as an erasure request, including the sever-the-link library treatment, and governs NeroNotes'
  own data only.
- Non-essential action logging is off by default (`data.action_logging_enabled = false`), and no
  player-authored text or player identity is ever written to info-level logs regardless.

## Changes to this policy

This file is versioned with the mod. Material changes are called out in `CHANGELOG.md` and take
effect with the release that carries them.
