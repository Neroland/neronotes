# Privacy and your data

NeroNotes stores player-authored content — compositions, disk and library attribution, channel
ownership — so it takes data protection seriously. Everything described here lives **inside the
world save, on the server you play on**; nothing gameplay-related is ever sent to the mod authors.
The full policy ships with the mod as `PRIVACY.md`.

## What the mod stores about you

- Composition scores you create in the Soundforge and press onto disks.
- Library and disk attribution: your UUID plus your chosen display name — or an anonymous marker.
  Publishing anonymously is a first-class choice, and anonymous entries store no display name.
- Published library entries with an **aggregate download count only** — no listening history, no
  record of who downloaded what or when, no play logs.
- Resonance channel ownership and trust lists.
- Your Soundforge return anchor and session score.
- A last-seen timestamp (UUID + login time, nothing else) that only exists to drive the retention
  sweep below.

## Exporting your data

Run **`/neronotes data export`** — self-service, no operator needed. You get a chat summary, and
the full record is written to `neronotes/exports/<your-uuid>.json` inside the world folder
(operator-controlled, like the rest of the world save). It includes your channels, trust
memberships, session summary and every library entry you authored — including anonymous ones —
with the full scores base64-encoded for portability. Only your own data is included; other players
appear at most as counts.

Operators can serve an access request on a player's behalf with
**`/neronotes data export <uuid>`**.

## Erasing your data

Run **`/neronotes data erase-me`**, read the warning, then **`/neronotes data erase-me confirm`**.
This is **irreversible** and goes through Neroland Core's shared erasure hook, so one request
purges your data across all installed Neroland mods. Operators can equally use Core's
`/neroland data eraseme` or `/neroland data erase <uuid>`.

**Published compositions: the link is severed, the work is kept.** Your library entries become
anonymous — no UUID, no name — but the compositions stay published and every disk other players
downloaded keeps playing. Erasure removes the person, not other players' music.

**Pressed disks already in circulation** keep whatever attribution was chosen at press time — they
are item data in other players' inventories, which the server cannot enumerate or rewrite (the
same way a signed book keeps its signature). The shared library is the authoritative attribution
record, and that is where erasure severs the link. A disk pressed anonymously never carried a name
in the first place.

## Automatic retention sweep

Servers keep NeroNotes data only while a player stays active: shortly after each server start, and
daily thereafter, the mod purges the stored data of any player who has not logged in for
`data.retention_days` days (default 365; set 0 to disable). The sweep uses the same purge path as
an erasure request — including the sever-the-link library treatment — and only ever touches
NeroNotes' own data. Players currently online are never purged.

## Logging and error reporting

- Non-essential action logging is **off by default** (`data.action_logging_enabled = false`), and
  player-authored text (titles, disk names, channel names) and player identities are never written
  to info-level logs regardless.
- Optional crash reporting is **opt-out** (`client.telemetry_opt_out = true` disables it
  completely) and never contains player names, UUIDs, coordinates, chat, player-authored text, or
  a list of installed mods. See `PRIVACY.md` for the full description.

## Command reference

| Command | Who | What |
| ------- | --- | ---- |
| `/neronotes data export` | any player | Export your own stored data (chat summary + JSON file in the world folder) |
| `/neronotes data export <uuid>` | operators | Export a named player's data to serve an access request |
| `/neronotes data erase-me` | any player | Show the irreversible-erasure warning |
| `/neronotes data erase-me confirm` | any player | Erase your stored data across all Neroland mods |
