# Changelog

All notable changes to **NeroNotes** are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Each release covers all six build
cells: Minecraft 26.1.2 and 26.2 on NeoForge, MinecraftForge/Forge and Fabric.

## [Unreleased]

### Added

- **`/neronotes gallery` (operator)** — a labelled in-world showcase following the ecosystem
  gallery convention: every NeroNotes block on one plaza (the seven Resonant Blocks, a Resonator,
  the Harmonic Gate, transport lectern, Disk Press, Publish Lectern, Disk Exchanger, the four
  pattern walls and the seven voice pedestals, each under a floating label describing its real
  interaction), with the Resonator playing a built-in four-layer looping demo composition on the
  command sender's own `gallery` channel through the normal server-side authorisation path — the
  in-range Resonant Blocks of the demo's families flare in time. `/neronotes gallery clear`
  removes the footprint; re-running rebuilds in place without duplicating labels.
- **A dedicated NeroNotes creative tab** (`itemGroup.neronotes`, Resonator icon) holding every
  NeroNotes block and item in progression order. Items **moved** here from Neroland Core's shared
  Neroland tab.
- **Asset-completeness test** — a plain-JVM test that walks every blockstate → model → texture
  reference (and every item definition and `sounds.json` entry), asserting each referenced file
  ships **and** that every shipped PNG is structurally valid (signature, chunk CRCs, inflatable
  pixel data), so a corrupt or missing texture can no longer reach a release unnoticed.

### Changed

- **The gallery demo composition is now tick-perfect and grooves harder** — rewritten at
  150 BPM × 4 ticks per beat (one score tick = 100 ms = exactly 2 game ticks): four-on-the-floor
  kick with off-beat accents, bass locked to the kick with chromatic pickups, an on-grid
  syncopated lead and tight off-beat plucks. The first cut ran 120 BPM × 4 (125 ms = 2.5 game
  ticks), which quantised alternate notes 100/150 ms apart — an audible limp.
- **New sequencer sessions default to 150 BPM** (previously 120) so a fresh composition is
  tick-perfect out of the box; any tempo in range remains selectable. The tick-grid rule is
  documented on the wiki Sequencer page and in the `Score` javadoc.
- Playback timing hardening on both sides: the server Resonator now treats the loop end as
  strictly exclusive when emitting (a note on the boundary can no longer double-fire at the
  wrap), and the client's delayed-note scheduling guards its game-tick rounding against
  floating-point noise (no more spurious extra-tick delays at tick-perfect tempos).

### Fixed

- **"Save and quit" froze the game forever at "Saving worlds" while a Resonator was playing**
  (reported from the first-run gallery pass; the log ends abruptly after
  `MinecraftServer: Saving worlds`, no crash report). Root cause: `ResonatorBlockEntity.setRemoved`
  ran its stop-playback side effects, and on 26.x `setRemoved` fires on **chunk unload** too
  (`LevelChunk.clearAllBlockEntities`) — including every unload inside the server's shutdown
  "has work" drain loop. The stop path's `getBlockState`/`setBlock` ring update synchronously
  re-loaded the very chunk being unloaded, and because the freshly loaded copy carries the
  persisted `playing` flag, every unload re-loaded the chunk again — the drain loop never
  emptied and shutdown hung silently. Destruction-only side effects (the STOP transport that
  frees the channel's play slot) now live in `preRemoveSideEffects`, which vanilla calls on
  real removal only; `setRemoved` is world-inert (index bookkeeping) on both the Resonator and
  the transport lectern. A chunk that merely unloads keeps its persisted playing state and
  resumes on reload, exactly as designed.
- **Server and client runtime state now clears on the right lifecycle edges** (all three
  loaders). Server-stopped (NeoForge/Forge `ServerStoppedEvent`, Fabric
  `ServerLifecycleEvents.SERVER_STOPPED`) clears the resonance subscriber map and audio-spam
  guard (`ResonanceService.clearRuntime` — previously written but never wired), both
  block-entity indexes (`ResonantBlockIndex`, `ResonatorIndex`), the link module's captured
  server handle and the retention-sweep tick counter — so no stale `ServerPlayer`/`BlockEntity`
  reference survives into the next singleplayer world. Player logout now drops that player's
  channel subscriptions. Client disconnect (NeoForge/Forge
  `ClientPlayerNetworkEvent.LoggingOut`, Fabric `ClientPlayConnectionEvents.DISCONNECT`) clears
  the playback engine's tracked playheads and the sequencer/exchanger screen caches.
- **Three corrupt block texture PNGs** — `transport_lectern_side`, `transport_lectern_top`
  (the Transport Lectern rendered as the pink/black missing texture, as seen in the gallery
  plaza) and `pattern_wall_0_lit` (flashed pink while a layer-0 pattern wall was lit). The files
  shipped byte-corrupt (bad chunk CRCs / truncation); regenerated from the texture generator.
  `.gitattributes` now marks `*.png` (and other binary assets) `binary` so normalisation can
  never corrupt them.

### Removed

- **The `neronotes:soundforge` progression gate** — all hard progression gates are gone
  (standalone-first, following Nerotech). Entering the Soundforge now requires only a charged
  Harmonic Gate: the datapack gate file (`data/neronotes/neroland_gates/soundforge.json`, which
  required Core's Industrial Power), the server-side `ProgressionGates` check, the
  "gate is sealed" refusal message and the quest-pack gate id are all removed. The energy
  mechanic is unchanged (`gate.energy_capacity` / `gate.teleport_energy_cost`; an operator can
  set the cost to `0` to make entry free).

## [0.1.0-beta.1] - 2026-08-12

The first feature release. NeroNotes ships the complete minimal music loop —
**compose → press → sync → play → publish → download** — as data-driven, server-authoritative
gameplay. Music is data, not audio: the server stores and syncs compact versioned scores and the
client renders them from registered sound events; no audio is ever streamed or uploaded.

### Added

- **Score format** — a compact, versioned score (`formatVersion`, tempo, ticks-per-beat, loop
  points, up to 4 layers of `(tick, pitch, velocity, length)` notes on named voices) with an NBT
  codec, a hard serialised-size budget (default 16 KiB, config-raisable to a 64 KiB ceiling)
  enforced **both at press time and on the wire**, and a reader that refuses newer format versions
  with a clear message instead of guessing.
- **Voices and sounds** — a data-driven voice registry (`assets/neronotes/voices/default.json`):
  8 voices across 7 families (Deep Bass, Sub Pad, Low Drone, High Lead, Glassy Pluck, Percussion,
  Synth Texture). Unknown voice ids play through a fallback voice instead of erroring.
- **The resonance signal** — owner-scoped channels (`dimension + owner + name`) carrying note and
  transport events to subscribed clients within a configurable range (default 64 blocks). Anyone
  in range may listen; emitting and transport control require the channel owner, its trust list,
  or an operator — **checked server-side, never by proximity, never by the client**. A
  configurable audio-spam cap quietly limits concurrently playing channels per chunk radius.
  Channel state persists in the world save behind Neroland Core's saved-data recovery guard.
- **Resonant Blocks** — seven matte-black blocks, one per voice family, with a neon edge-light
  flare on each note. Tap to sound the current pitch, sneak-tap to tune; they also re-tune and
  flare to matching notes on nearby channels.
- **Resonators** — the disk player. The placer becomes the owner; it binds to the owner's `base`
  channel. Right-click with a pressed disk loads its composition (the disk is read, not
  consumed); right-click toggles play/stop; sneak-click with an empty hand clears it. It keeps
  playing while its owner is offline and resumes correctly across restarts and chunk reloads.
- **Synchronised playback** — the server owns the timeline anchor
  `(channel, position, game tick)`; clients schedule against it with half-round-trip latency
  compensation clamped to a configurable maximum (default 500 ms) and perform a **hard seek**
  when drift exceeds a configurable threshold (default 100 ms). Playback rate is never adjusted.
  Late joiners and chunk reloads seek to the current position.
- **The Harmonic Gate** — a Neroland Core machine (energy on Core's shared `nerolandcore:energy`
  capability, with standard Forge Energy fallback on NeoForge/Forge, so FE mods such as Energized
  Power can charge it). A charged crossing costs energy; **returning is always free**. Entry
  requires Core's Industrial Power progression gate; everything else in the mod stays ungated.
- **The Soundforge** — a small starlit void dimension (datapack-defined) with a built arrival
  platform: the Harmonic Gate, transport lectern, Disk Press, Publish Lectern, four pattern walls
  and seven voice pedestals. Return anchors persist per player; a player who logs out inside is
  never stranded (`/neronotes soundforge return` works without op).
- **The sequencer** — a paged grid editor opened at the transport lectern (a pragmatic editor,
  not a DAW): 4 layers, up to 1000 notes per layer, per-layer voices, tempo and loop points, and
  a one-shot preview on the player's own `preview` channel. Every edit is validated
  server-side; the session score persists per player.
- **The Disk Press and custom disks** — presses the session onto a blank disk with a
  server-validated title (length cap, control/formatting-code strip, configurable word list) and
  a **first-class anonymity choice**: anonymous disks store no display name anywhere (the author
  UUID is kept solely so quota, unpublish and erasure still work). Over-budget scores are refused
  with a message naming both byte counts — never truncated. Blank disks have a survival recipe.
- **Publishing and the shared library** — the Publish Lectern (or `/neronotes library publish`)
  publishes a pressed disk to one server-wide library storing title, attribution choice, score
  and an **aggregate download count only** — no listening history, no per-download identity or
  timestamps, no play logs. Server-wide size cap, per-player quota, optional operator-approval
  mode (default off), operator takedown.
- **The Disk Exchanger** — an overworld machine for browsing the library (paginated, 50 per
  page) and copying an entry onto a blank disk, or duplicating a disk you already have. Scores
  never cross the wire to the Exchanger client; the server writes the disk.
- **Commands** — `/neronotes soundforge return`; `/neronotes library browse [page] | publish |
  unpublish <id>` and operator `remove <id>` / `approve <id>`; `/neronotes data export` (self
  or operator `<uuid>` variant) and `/neronotes data erase-me [confirm]`.
- **Data protection (POPIA / GDPR)** — one eraser registered with Neroland Core's shared
  `PlayerDataErasure` hook; self-service export (JSON under the world folder) and erasure
  commands; an automatic retention sweep purging inactive players' stored data (default 365
  days, configurable, 0 disables); recovery backups refreshed on purge so erased rows do not
  survive in backups. **Erasing an author severs the link and keeps the work**: library entries
  become anonymous, downloaded disks keep playing — the full policy is in `PRIVACY.md`.
- **Ecosystem integrations, all optional** — Core `ThresholdEvents` crossings
  (`neronotes:compositions_published`, `neronotes:channel_listeners`; scopes are places/systems,
  never players) that NeroQuests' `custom_event` objective can consume; a NeroEconomy pricing
  seam on Exchanger copies (copies stay free in 0.1.0); a documented NeroEvents seam. Everything
  is runtime feature-detected — the only required dependency is Neroland Core 1.11.0+.
- **Companion (NeroLink) link module** — read sections `library`, `disks`, `channels`,
  `now_playing` and actions `play` / `stop`, all scoped strictly to the requesting player's own
  or trusted channels (`NOT_OWNER` otherwise, no operator bypass, no server-wide roster).
  Anonymous library entries carry no author field at all.
- **Config** — the full schema in `config/neronotes.properties` via Core's config framework.
  Every gameplay key is server-authoritative; the only client-local keys are the telemetry
  opt-out, per-voice-family volumes, glow intensity and "mute other players' bases".
- **Telemetry** — optional, opt-out Sentry error reporting, scrubbed and hard-capped; entirely
  inert until a real DSN is configured at build time. See `PRIVACY.md`.

### Not in this release

Deliberate scope cuts, written down so nobody mistakes a placeholder for a feature:

- **No real-audio import.** There is no transcode path, no resource-pack delivery, no upload of
  any kind — and no disabled stub pretending otherwise.
- **No `.ogg` files ship.** All seven registered sound events **alias vanilla sounds**
  (note block, beacon, conduit). Swapping in real instrument audio later is a pure resource
  change; the current palette is a placeholder and sounds like one.
- **Block and item textures are generated placeholders** (`tools/gen_textures.py`), disclosed
  here on purpose.
- **No in-world note placement.** Composing is a GUI at the transport lectern inside the
  Soundforge; the pattern walls and voice pedestals are display and selection surfaces.
- **No MIDI import.**
- **No playlists, queues or radio channels** — a Resonator plays one composition. This is also
  why the link module has no `skip` action: with nothing to skip to, it would be a dishonest
  alias for `stop`.
- **No light-show blocks.**
- **No cross-dimension relays** — channels live and play within one dimension.
- **No in-game surface yet for channel management** — trust lists, renaming and deletion exist
  and are enforced server-side (and erasure clears them), but 0.1.0 ships no command or GUI to
  edit them, so in practice a channel answers to its owner and operators.
- **Sustained notes are not voiced** — note lengths are stored in the score, but 0.1.0 renders
  `note_on` one-shots only.

## [0.0.1-alpha.1]

Internal multiloader skeleton (never released): loader entry points, Stonecutter six-cell build,
CI workflows. No gameplay.
