# NeroNotes

> The music layer of the Neroland sci-fi Minecraft mod family, built on **Neroland Core**.

**Version `0.1.0-beta.1`** — feature-complete for the first release; runtime verification in
progress. See [`CHANGELOG.md`](CHANGELOG.md) for exactly what shipped and what was deliberately
cut.

## What it does

**Music is data, not audio.** A composition is a compact, versioned score — notes, voices,
timing, loop points — stored and synced by the server and rendered client-side from registered
sound events. Nothing is streamed, nothing is uploaded, and vanilla note blocks and jukeboxes are
untouched.

The loop: **compose → press → sync → play → publish → download.**

- **Resonant Blocks** — seven matte-black, family-tuned blocks that sound and flare to notes on
  nearby resonance channels. Available from the start, no progression required.
- **Resonance channels** — owner-scoped, server-authoritative signal channels. Anyone in range
  listens; only the owner, trusted players or an operator can emit or control transport. A
  per-chunk-radius cap keeps overlapping bases civil, and a client-side "mute other players'
  bases" toggle exists from day one.
- **Resonators** — disk players with a neon ring. Load a pressed disk (the disk is read, not
  consumed), toggle play/stop, and playback stays **audibly in step for every listener**: the
  server owns the timeline anchor, clients compensate for latency (clamped) and hard-seek on
  drift — playback rate is never bent.
- **The Harmonic Gate and the Soundforge** — a Core-powered machine that carries you to a small
  starlit composing dimension. Its energy charge is the only requirement — no progression gate.
  Returning is always free and you can never be stranded.
- **The sequencer and the Disk Press** — a paged grid editor at the transport lectern (4 layers,
  1000 notes per layer, server-validated), pressed onto disks with a hard size budget (16 KiB
  default, 64 KiB ceiling — refused with both byte counts, never truncated) and a **first-class
  anonymous-publishing choice**.
- **The shared library and the Disk Exchanger** — publish compositions server-wide, browse
  paginated listings, download onto blank disks. The library stores an aggregate download count
  and nothing else about who listened.
- **Data protection built in** — self-service `/neronotes data export` and
  `/neronotes data erase-me`, a retention sweep for inactive players, and a documented
  sever-the-link-keep-the-work answer for erased authors. See [`PRIVACY.md`](PRIVACY.md).
- **Ecosystem-aware, never dependent** — threshold-event milestones for NeroQuests, a pricing
  seam for NeroEconomy, and a companion (NeroLink) module with strictly requester-scoped data
  and `play`/`stop` actions. All optional, all feature-detected at runtime.

**Honesty about placeholders:** this release ships **no `.ogg` audio** — every voice aliases a
vanilla sound event — and block/item textures are generated placeholders. No real-audio import,
no MIDI, no playlists, no light shows, no cross-dimension relays. The full list is in
[`CHANGELOG.md`](CHANGELOG.md).

## Requirements

- **Neroland Core `1.11.0` or newer** (required; loads before NeroNotes).
- **Minecraft 26.1.2 or 26.2** on **NeoForge, MinecraftForge/Forge, or Fabric** — the "6 cells".
- **Java 25.**

## Building

The build is the repo root, a flattened cross-loader structure driven by Stonecutter:

- `common/` — shared, loader-agnostic source spliced into every loader node
- `neoforge/` (ModDevGradle) · `forge/` (ForgeGradle) · `fabric/` (Fabric Loom)
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build \
          :forge:26.1.2:build :forge:26.2:build \
          :fabric:26.1.2:build :fabric:26.2:build   # all six
./gradlew :fabric:26.2:ecjCheck       # static analysis
./gradlew :neoforge:26.2:test         # unit tests (wired into the NeoForge nodes)
```

Neroland Core resolves from Maven Local or GitHub Packages — see
[`USING-CORE.md`](USING-CORE.md).

## Documentation

- [`wiki/`](wiki/Home.md) — player-facing docs: blocks, channels, the Soundforge, the sequencer,
  disks, publishing, commands, configuration, privacy, the companion module.
- [`USING-CORE.md`](USING-CORE.md) — every Neroland Core API this mod consumes, and how the Core
  dependency is declared.
- [`PRIVACY.md`](PRIVACY.md) — what the optional error reporting does and does not collect, and
  how gameplay data in your world save is handled.
- [`CHANGELOG.md`](CHANGELOG.md) — release history, including the explicit not-in-this-release
  list.
- [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) — contributor and AI-agent context.
