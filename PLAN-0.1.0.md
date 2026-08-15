# PLAN-0.1.0 — runtime verification for NeroNotes `0.1.0-beta.1`

Everything below must be run **by the owner on real clients and servers** — the build agent
cannot launch Minecraft and has not claimed any of these checks. All static gates already pass:
six cells build green, `ecjCheck` clean, the full JUnit suite (including `ErasureConformance`)
passes on `:neoforge:26.2:test`.

Conventions used below:

- **Primary cell:** `:neoforge:26.2` — run every check there.
- **Spot-check cells:** named per check; at minimum each of the six cells must pass check 1.
- Log lines are quoted exactly. NeroNotes logs under the logger name `NeroNotes`; every message
  starts with the literal prefix `[NeroNotes]` and a space. Lines marked *(debug)* only appear with debug
  logging enabled for that logger (e.g. Fabric `log4j` config or `-Dforge.logging.console.level=debug`).
- On a stock build the Sentry DSN is the placeholder, so telemetry is entirely inert. The only
  telemetry line is *(debug)* `[NeroNotes] telemetry: no DSN configured; error reporting disabled`
  — you will **not** see an "error reporting enabled" line, and that is correct.

## Prerequisites

- [ ] Neroland Core `1.11.0` artifacts resolvable (Maven Local via `./gradlew publishToMavenLocal`
      in the Core repo, or GitHub Packages credentials).
- [ ] Fresh build of all six cells from the current tree:
      `./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :forge:26.1.2:build :forge:26.2:build :fabric:26.1.2:build :fabric:26.2:build :fabric:26.2:ecjCheck :neoforge:26.2:test`
- [ ] Two Minecraft accounts (checks 3, 4, 7, 9 need a second player).
- [ ] A dedicated-server setup for the primary cell (the sync checks are meaningless in
      single-player).
- [ ] A network-latency tool for check 3 (e.g. clumsy on Windows) able to add ~150 ms to one
      client.
- [ ] Optional but recommended jars: **NeroQuests** (check 1b), an **Energized Power** build for
      MC 26.x (check 5), **NeroLink + the companion app** (check 11), and a Forge-lineage
      **Paper-hybrid** server (check 12).
- [ ] Sentry: either leave the placeholder DSN (telemetry inert) or inject the real DSN — decide
      before tagging.

## Runtime checks

### 1. Clean load on all six cells; config written

Cells: **all six** — `:neoforge:26.1.2`, `:neoforge:26.2`, `:forge:26.1.2`, `:forge:26.2`,
`:fabric:26.1.2`, `:fabric:26.2` (this is the one check run everywhere).

Do this: launch a client per cell (Core + NeroNotes only), create/enter a world, then quit.

**Expect:** no errors or warnings from NeroNotes in the log; `config/neronotes.properties`
exists afterwards and contains the full key schema (from `signal.emit_range_blocks` to
`client.mute_other_bases`). Startup lines, in order (bootstrap line varies per loader):

```text
[NeroNotes] NeoForge bootstrap
[NeroNotes] common init
[NeroNotes] voice registry ready: 8 voices across 7 families
[NeroNotes] NeroLink module registered (schema v1).
```

(`Forge bootstrap` / `Fabric bootstrap` on the other loaders; Fabric clients additionally log
`[NeroNotes] Fabric client bootstrap`.) On joining a world the client logs:

```text
[NeroNotes] client playback engine installed
```

With debug logging you should also see the integration detections *(debug)*, all three on a
Core-only install:

```text
[NeroNotes] neroquests absent — degrading silently
[NeroNotes] neroeconomy absent — degrading silently
[NeroNotes] neroevents absent — degrading silently
[NeroNotes] integrations ready — ThresholdEvents channels: neronotes:compositions_published, neronotes:channel_listeners
```

**1b. Core+NeroQuests configuration** (primary cell only): add the NeroQuests jar and reload.
**Expect:** the same clean load, with the first detection line replaced by *(debug)*:

```text
[NeroNotes] detected neroquests — ThresholdEvents crossings become quest triggers
```

There must be no other behavioural difference at load. This is the Stage 8 two-configuration
gate: **Core-only and Core+Quests must both load green.**

### 2. Resonant Blocks: voice, tuning and the neon flare

Primary `:neoforge:26.2`; spot-check `:fabric:26.1.2`.

Do this: place one Resonant Block of each of the seven families (creative tab → the dedicated
**NeroNotes** tab; NeroNotes items no longer sit in Core's shared Neroland tab).
Tap each (right-click, empty hand); sneak-tap a few times; watch the block while a nearby
Resonator plays (after check 3).

**Expect:**

- Tapping sounds the block's current note **immediately** (it is a local one-shot — as public
  and as instant as a vanilla note block) and the neon edge flares **on the note, not a tick
  late** (the `lit` blockstate, which also emits light).
- Sneak-tap steps the pitch up within the family band, wrapping, previewing each step.
- During channel playback, blocks of the matching family adopt the incoming pitch and flare in
  time with the music.
- Each family sounds different (all are vanilla aliases: note-block bass/harp/chime/basedrum/bit,
  beacon, conduit — placeholder palette, disclosed in the changelog).

### 3. THE sync check — two Resonators, one channel, two clients

Primary `:neoforge:26.2` (dedicated server); spot-check `:forge:26.2` and `:fabric:26.2`
clients against the same server if you want cross-loader clients.

**This is the mod's pass/fail.** Do this:

1. As player A: enter the Soundforge, compose a distinct, rhythmic score (use percussion), press
   a disk (checks 6 and 8 describe the route).
2. Place **two Resonators** ~20 blocks apart (both bind to A's `base` channel), right-click each
   with the pressed disk to load it — **Expect** chat: `The Resonator adopts "<title>". The disk
   stays with you.` — then right-click one to play.
3. Stand between them with both clients (A and B). **Expect:** the two Resonators and both
   clients are **audibly in step** — one music, not an echo.
4. Add ~150 ms latency to client B's connection. **Expect:** B stays in step (compensation is
   clamped by `sync.max_latency_compensation_ms`); at worst a brief **hard seek** — never
   sped-up/slowed "warped" playback.
5. Chunk reload: as B, F3+A (or travel away and back). **Expect:** playback resumes at the
   **current** position and falls back in step (a reload is a seek, not a restart).
6. Late join: have B log out and back in mid-track. **Expect:** B hears the track from its
   current position within ~1 second (the immediate late-joiner anchor), in step.
7. Restart the server mid-play. **Expect:** the Resonator resumes near its stopped position and
   listeners re-sync.

Also verify against the loaded-disk lifecycle: sneak-click a Resonator with an empty hand —
**Expect** chat `The Resonator falls silent — its composition is cleared.` As player B (not
owner, not op) try to play/stop/load A's Resonator — **Expect** the quiet refusal:

```text
Only the channel owner, trusted players or an operator may control this Resonator.
```

### 4. The concurrency cap and "mute other players' bases"

Primary `:neoforge:26.2` (same server as check 3).

Do this (cap): set `signal.max_playing_channels_per_chunk_radius = 1` on the server. Player A
plays their Resonator. Player B (with their own Resonator + disk nearby, on B's own `base`
channel) presses play.

**Expect:** B's chat answers quietly — no log spam, nothing in the server log:

```text
Too many channels are already playing nearby.
```

Stop A's Resonator, retry as B — now it plays (the slot freed). Restore the config afterwards.

Do this (mute): on client B set `client.mute_other_bases = true` (client's own
`config/neronotes.properties`), rejoin, stand next to A's playing Resonator.

**Expect:** B hears **nothing** from A's channel while B's own channels still play; A is
unaffected. The toggle changes audio only — B still cannot control A's Resonator, and setting it
back restores the audio.

### 5. Harmonic Gate energy — Core machine and Energized Power

Primary `:neoforge:26.2`; spot-check `:forge:26.1.2` (Forge Energy fallback matters most there).

Do this: craft the Harmonic Gate (Nero Alloy plates + Plasma Glass ring + amethyst block — tag
recipe, Core's materials). Place it and feed it from a Neroland Core-compatible generator (e.g. a
Nero machine outputting on Core's `nerolandcore:energy` capability). Then swap the source for an
**Energized Power** generator/cable.

**Expect:** the gate charges from **both** sources (the capability falls back to standard Forge
Energy on NeoForge/Forge); its arch lights when a crossing is affordable
(`gate.teleport_energy_cost`, default 8,000 NE of a 16,000 NE buffer). Using an uncharged gate
answers:

```text
The Harmonic Gate hums faintly; it needs more energy for a crossing.
```

There is no progression requirement: a charged gate carries any player through, with no Core
gate to open first (hard gates were removed for standalone-first play).

### 6. The Soundforge: compose, press, log out inside, return

Primary `:neoforge:26.2`; spot-check `:fabric:26.2`.

Do this: cross a charged gate.

**Expect** chat `The Harmonic Gate carries you into the Soundforge.`; on first entry, server log:

```text
[NeroNotes] building the Soundforge arrival platform
```

You arrive on the platform: centre gate, transport lectern, Disk Press, Publish Lectern, four
pattern walls (north), seven voice pedestals (south). Then:

1. Open the sequencer at the transport lectern; place notes on 2+ layers, set tempo and a loop,
   preview once (it plays once through, on your own `preview` channel, and counts against the
   nearby-channel cap). The size gauge (`<n> / <budget> bytes`) moves as you edit.
2. Tap a pattern wall (layer select — it flashes) and a voice pedestal (voice cycle) and see the
   sequencer reflect them.
3. Press a disk at the Disk Press (blank disk in, title, **Credited to you**, Press) — chat:
   `The press hums — your composition is written.`
4. **Log out while standing in the Soundforge.** Log back in. **Expect:** you are still in the
   Soundforge, your session score is intact, and both exits work: the centre gate, and
   `/neronotes soundforge return` → chat `The Soundforge releases you — you are back where you
   started.` at your exact entry position. (Running the command anywhere else answers
   `You are not in the Soundforge.`; returning consumed no energy.)

### 7. Publish, then download on a second account — including anonymously

Primary `:neoforge:26.2` (server from check 3).

Do this: as player A, publish the pressed disk (tap the Publish Lectern holding it, or
`/neronotes library publish`) — **Expect** chat: `Published to the shared library as entry #1.
The disk stays yours.` Then press a **second** disk with the **Anonymous** choice and publish it
too. As player B in the overworld: craft/place the Disk Exchanger, open it with a blank disk,
browse, select A's entries, **Copy**.

**Expect:**

- B receives playable copies onto blank disks; the entries' download counts increment by one
  per copy (aggregate only — verify with `/neronotes library browse`:
  `#1 <title> — by <name> — 1 download(s)`).
- The **anonymous** entry shows `an anonymous composer` in the Exchanger row and the browse
  listing, and the copied disk's tooltip reads `By an anonymous composer` — **no surface
  anywhere names A**: not the listing, not the tooltip, not the link module (check 11).
- `Dupe` duplicates a disk B already holds without touching any count.
- With `library.op_approval_required = true`, a fresh publish answers `Submitted as entry #N —
  it appears in the library once an operator approves it.` and stays invisible to B until
  `/neronotes library approve <N>`.

### 8. The over-budget press refusal

Primary `:neoforge:26.2`.

Do this: set `disk.score_budget_bytes = 1024` (the minimum) on the server, then try to press a
reasonably full session.

**Expect:** the press refuses — nothing is written, nothing truncated — with the translated
message naming **both** byte counts:

```text
This composition is <actual> bytes; the Disk Press limit is 1024 bytes. Trim the score — the press never truncates.
```

Restore the default (16384) and the same press succeeds.

### 9. Data export and erasure — the sever-the-link check

Primary `:neoforge:26.2` (after check 7, so A has published entries and B holds copies).

Do this: as player A run `/neronotes data export`.

**Expect** chat naming the file and a summary:

```text
Your NeroNotes data was exported to neronotes/exports/<uuid>.json inside the world folder.
Owned channels: 2 · trusted on: 0 · published entries: 2 · Soundforge session: yes
```

Open the JSON: it contains only A's data (other players appear at most as counts), with A's
scores base64-encoded. Then run `/neronotes data erase-me` — **Expect** the warning + the hint
`Run '/neronotes data erase-me confirm' to proceed.` — then `/neronotes data erase-me confirm`
— **Expect** `Your stored Neroland data has been erased. Published compositions remain,
anonymously.`

**Verify the outcome** (there is deliberately **no log line** for an erasure — a UUID is
personal data):

- `/neronotes library browse` now shows A's entries as `an anonymous composer`; they remain
  downloadable and their scores intact.
- **B's downloaded disks still play** in B's Resonator.
- A's channels are gone (A's Resonator quietly stops and won't restart until placed anew),
  A's Soundforge session is empty, a fresh `/neronotes data export` shows zeros.
- B's channels, session and entries are untouched (bystander survival).

### 10. The retention sweep

Primary `:neoforge:26.2` (dedicated server).

Do this: have account B join once, then stop the server. Set `data.retention_days = 1` and age
B's record — either advance the machine clock two days, or use a world where B genuinely last
joined over a day ago. Start the server **without** B and wait one minute (the sweep runs one
minute after start, then daily).

**Expect** exactly one log line (count only, no identity):

```text
[NeroNotes] retention sweep purged stored data for 1 inactive player record(s)
```

B's stored data is now treated exactly like an erasure (library entries severed to anonymous,
sessions/channels/activity gone). A player who is **online** is never purged — repeat with B
connected and confirm silence (a zero-purge sweep logs nothing). Restore `data.retention_days`.

### 11. The link module through the companion app

Primary `:neoforge:26.2` with **NeroLink** installed and a paired companion client.

Do this: pair the app as player A (who owns channels and disks from earlier checks); browse
every section; trigger `play` and `stop`; then pair a second app session as player B and request
A's channel.

**Expect:**

- Sections `library`, `disks`, `channels`, `now_playing` return: the public library listing
  (anonymous entries carry **no author field at all**), only A's own carried disks (empty with
  `player_online: false` while A is offline), and **only** channels A owns — never a server-wide
  roster, never any player UUID in a payload.
- `play` starts A's loaded Resonator (loaded chunks only); `stop` stops it; `now_playing`
  events arrive on genuine transitions.
- B requesting A's channel gets `NOT_OWNER` — and "does not exist" answers identically, so the
  API confirms nothing about other people's channels. An operator's pairing gets **no** bypass.
- With `link.module_enabled = false` the server logs at startup:

```text
[NeroNotes] The NeroLink module is disabled by config; companion clients will not see NeroNotes data.
```

### 12. Paper-hybrid note — unsupported, but graceful

Any Forge-lineage hybrid (e.g. Arclight-style) close to `:forge:26.2`.

Hybrids are **unsupported**; this check only proves failure is graceful. Do this: install Core +
NeroNotes on the hybrid, join, and open each menu (transport lectern, Disk Press, Disk
Exchanger).

**Expect:** either the menus simply work, or — if the hybrid breaks `openMenu` — **no crash**:
the interaction consumes quietly and chat shows:

```text
This screen could not be opened. Please try again.
```

with a `[NeroNotes] handled failure in menu (open:<class>): ...` warning in the log (class name
only — never a menu title). A server crash on menu open is a FAIL of this check.

### 13. Shutdown while music plays — save & quit must not hang

Primary `:neoforge:26.2` (the cell where the 2026-08-15 freeze was caught).

This is the regression check for the shutdown freeze: `setRemoved` used to run world-mutating
stop side effects on chunk unload, which re-loaded the unloading chunk forever inside
`MinecraftServer.stopServer`'s drain loop — the game froze at "Saving worlds" with no error and
no crash report.

Do this: in a world, run `/neronotes gallery` and confirm the Resonator is playing and looping.
**While the beat is still playing**, hit Esc → **Save and Quit to Title**. The menu must appear
within a few seconds. Relaunch the same world.

**Expect:**

- Quit completes promptly — the log runs `Saving worlds` → `Saving chunks for level ...` (all
  dimensions) → `ThreadedAnvilChunkStorage: All dimensions are saved` → `Stopping!` with **no
  hang, no ERROR and no stack trace** between them.
- On rejoin, the gallery Resonator **resumes playing on its own** from its persisted position
  (a reload is a seek, not a restart) and the Resonant Blocks flare again.
- Break the playing Resonator with an axe: the music stops for everyone and the block drops —
  then place it back, load nothing, and confirm the world still saves and quits cleanly.
- Repeat the save-and-quit once more from inside the **Soundforge** while the gallery plays in
  the overworld: same clean shutdown lines, no hang.

A shutdown that sits on the "Saving world" screen for more than ~15 seconds is THE bug and a
FAIL.

### Optional — the showcase gallery (`/neronotes gallery`)

Primary `:neoforge:26.2`. Optional: not counted in release readiness (the gallery is an
operator demo aid, not player-facing gameplay), but it is a fast one-command smoke of blocks,
channels, playback and the flare index in one place.

Do this: as an operator on open flat ground, run `/neronotes gallery`.

**Expect:** a labelled plaza appears a few blocks east — 7 Resonant Blocks, a Resonator, the
Harmonic Gate, transport lectern, Disk Press, Publish Lectern, Disk Exchanger, 4 pattern walls
and 7 voice pedestals, each with a floating label — **no pink/black missing-texture faces
anywhere on the plaza** (the transport lectern shipped corrupt textures once). The Resonator
starts a repeating four-layer 150 BPM beat immediately (it plays on **your** `gallery` channel)
and keeps looping **with a steady, even pulse — no alternating long/short "limp" between
off-beats, and no stutter at the four-bar loop wrap**; the Percussion,
Deep Bass, High Lead and Glassy Pluck Resonant Blocks flare in time with it. Re-run the command
standing in the same spot — the plaza rebuilds in place with **no duplicated labels**. Then
`/neronotes gallery clear` from the same spot — the plaza and labels vanish and the music stops.
A non-op running `/neronotes gallery` must be refused by the permission gate.

## Release readiness

- [ ] All thirteen checks above pass on the primary cell; check 1 passes on **all six** cells.
- [ ] Check 3 (sync) passed **including** the 150 ms, chunk-reload and late-join variants — this
      is the release's pass/fail.
- [ ] Both load configurations green: Core-only and Core+NeroQuests (check 1b).
- [ ] Sentry DSN decision made (placeholder kept = telemetry inert, or real DSN injected and a
      test event verified).
- [ ] `gradle.properties` reads `mod_version=0.1.0-beta.1` (already set; the agent bumped it and
      rebuilt green).
- [ ] Wiki re-read once against the release build (it publishes publicly via `wiki.yml` on push —
      no private references, verified by grep at build time).
- [ ] Owner commits the working tree (nothing was committed by the agent), tags `v0.1.0-beta.1`,
      and pushes — the publish workflow (CurseForge direct-curl + Modrinth v3 environment PATCH)
      takes it from the tag.
