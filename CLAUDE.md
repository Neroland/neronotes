# Project context for AI coding agents — neronotes

> `AGENTS.md` and `CLAUDE.md` are kept **byte-identical**; update both together.

## The mod

- **NeroNotes** — the music layer of the Neroland sci-fi Minecraft mod ecosystem, built on
  **Neroland Core** (required dependency, `nerolandcore_version` in `gradle.properties`; the
  manifest version ranges are DERIVED from it — never hardcode `[1.0,2.0)`).
- **Status:** Stage 5 complete (on Stages 0–2: Core wiring, `platform/` seams,
  `config/NeroNotesConfig`, `telemetry/NeroNotesTelemetry` + `PRIVACY.md`, `menu/MenuOpener`,
  own channel `neronotes:main`, numbered `init()`; versioned `score/Score` + `ScoreCodec`;
  data-driven `voice/VoiceRegistry` + `sound/NeroNotesSounds` — **no `.ogg` ships**, vanilla sound
  aliases only; the server-side resonance signal in `signal/` — owner-scoped channels persisted in
  `ChannelStore` behind Core's `SavedDataRecovery`, `ChannelAccess` authorisation (**proximity is
  never permission**), the `ChannelConcurrencyGuard` audio-spam cap, `ResonanceService`, and the
  tiny `ResonanceNotePayload`/`ResonanceTransportPayload` wired per loader). Stage 3 adds
  **Resonant Blocks, Resonators and synchronised playback**: `block/` + `item/` registrations via
  Core's `RegistrationProvider` (init steps 3/4, creative tab at 6; tooltips on `NotesBlockItem` —
  `Block` has no hover text) — seven family-tuned matte-black Resonant Blocks (`lit` blockstate
  flare, tap / sneak-tap interaction, pitch adoption via the bounded `ResonantBlockIndex`) and the
  Resonator disk player (`playing`/`pulse` neon-ring blockstates, owner recorded server-side at
  placement, server tick schedule emitting through `ResonanceService` as its owner — machines get
  no operator bypass). Sync per locked decision 4 lives in `sync/PlaybackClock` +
  `sync/ChannelPlayhead` (server anchor, clamped RTT/2 compensation, hard seek on drift — never a
  rate change) with `client/ClientPlaybackEngine` installed from each loader's client entry point;
  `client.volume.*`, `client.glow_intensity` and `client.mute_other_bases` are honoured now.
  Block textures are **generated placeholders** (`tools/gen_textures.py`, `./gradlew genAssets`,
  additive). Resonators play a `Score` from NBT; the disk items that write one arrive in Stage 5.
  Stage 4 adds **the Harmonic Gate and the Soundforge**: `block/HarmonicGateBlock` +
  `entity/HarmonicGateBlockEntity` (extends Core's `machine.AbstractMachineBlockEntity`; `charged`
  blockstate; energy exposed on Core's shared `nerolandcore:energy` capability per loader —
  NeoForge `NeoForgeCapabilities`, Forge `ForgeCapabilities`, Fabric `FabricEnergyLookup`
  registration in the entry point — so FE sources like Energized Power charge it); the
  `neronotes:soundforge` progression gate (datapack `neroland_gates/soundforge.json`, requires
  `nerolandcore:industrial_power`, checked server-side via Core `ProgressionGates` — everything
  from Stages 1–3 stays ungated); the Soundforge void dimension by datapack
  (`dimension/soundforge.json` + `dimension_type/soundforge.json`, tagged optionally into
  `neroland:space/dimensions` — never `orElseThrow` on SpaceTags, empty is normal) with a
  code-built platform on first entry (`soundforge/SoundforgeDimension`); teleport/session/return
  logic in `soundforge/SoundforgeTravel` with per-player return anchors persisted in
  `soundforge/SoundforgeSessionStore` behind Core's `SavedDataRecovery`
  (`neronotes:soundforge_sessions`, purge-ready: `purgePlayer`/`purgePlayerAndBackup`/`hasRow`);
  returning is always free (centre gate, or the `/neronotes soundforge return` safety hatch in
  `command/NeroNotesCommands` — works only inside, no op needed); gate recipe uses Core material
  tags (`c:plates/nero_alloy`, `nerolandcore:materials/plasma_glass`) + a vanilla amethyst block,
  config keys `gate.energy_capacity` / `gate.teleport_energy_cost`.
  26.x API notes learned here: `BlockEntityType`'s `(BlockEntitySupplier, Set)` constructor is
  public on 26.2 but on 26.1.2 vanilla keeps it private with NO builder/factory path — NeoForge
  and Forge patch it public, Fabric needs the two `neronotes.accesswidener` entries (harmless
  no-ops on 26.2); `BlockPos.getCenter()` was removed in 26.2 (use `Vec3.atCenterOf`);
  `Level.random` is protected (use `getRandom()`); `Item.appendHoverText` is the 5-arg
  `(ItemStack, TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)` form on both.
  Stage 5 adds **sequencer sessions, the Disk Press and custom disks**: the transport lectern
  (`block/TransportLecternBlock` + preview-playing BE) opens `menu/SequencerMenu` via `MenuOpener`
  → `client/SequencerScreen`, a pragmatic paged grid editor (NOT a DAW) editing the per-player
  session score persisted in `SoundforgeSessionStore` session data (`soundforge/SequencerSessions`,
  pure bounds in `soundforge/SessionEditor`: 4 layers, 1000 notes/layer, tick < 16384 — provably
  under the 64 KiB wire ceiling). Every edit is a serverbound `SequencerEditPayload` (the FIRST
  serverbound payloads — all three loaders now wire `NotesNetwork.serverboundPayloads()`; Forge
  channel went `.bidirectional()`, Fabric `sendToServer` via the lazily-resolved
  `FabricClientNetworkSender`, NeoForge via `ClientPacketDistributor`); the server echoes
  authoritative state as the budget-bounded `SessionScorePayload` (decoded ONLY through
  `NotesNetwork.decodeScoreFromWire`) and all gauges are data-slot backed (`addDataSlots`).
  Pattern walls (`layer` 0–3 + `lit` flash) and voice pedestals (`family` 0–6) are in-world
  layer/voice selection; `SoundforgeDimension.ensurePlatform` now places lectern, press, 4 walls,
  7 pedestals (guard checks gate AND lectern, so old platforms heal). The Disk Press
  (`block/DiskPressBlock`, no BE; `menu/DiskPressMenu` + `client/DiskPressScreen` with EditBox
  title + the FIRST-CLASS anonymity toggle) presses the session onto a `blank_disk` →
  `custom_disk` carrying the `neronotes:disk_contents` component (`item/DiskContents`, registered
  in `item/NeroNotesDataComponents`): budget enforced via `ScoreCodec.toBytes(score, budget)` with
  a translated refusal naming BOTH byte counts (never truncates), name validation in
  `item/DiskNames` (config `disk.name_max_length` + new `moderation.blocked_words`), pure press
  decision in `item/DiskPressLogic`. Anonymous disks store the author UUID (erasure needs it) but
  NO display name — `DiskContents` scrubs it in the compact ctor and `authorDisplay()` is empty;
  tooltips show "By an anonymous composer". Core highlight tags shipped under
  `data/neroland/tags/item/highlight/{machines,tools,materials}.json`. Blank disks have a survival
  recipe (copper + plasma-glass tag + amethyst). Preview plays the session once on the player's
  own `preview` channel through the normal resonance auth path (no operator bypass). Lectern,
  walls, pedestals and press all refuse politely outside the Soundforge (locked decision 2).
- Mod id: **`neronotes`** (matches the registry namespace + every loader manifest). Package root:
  `za.co.neroland.neronotes`. Author: **Neroland**.
- Version: **0.0.1-alpha.1**.
- Targets **MC 26.1.2 AND 26.2** on **NeoForge, MinecraftForge/Forge, and Fabric** → the **"6 cells"**.
  **Java 25.** Mappings = official Mojang names (26.x ships de-obfuscated; no Parchment).

## Working rules

- **Keep responses concise and direct** — minimal verbosity, minimal formatting.
- **POPIA & GDPR**: keep all logging/telemetry/scripts compliant — only public version strings, never
  personal data; minimise data, set retention limits, support export/erasure and opt-out. No
  player-authored strings (disk names, titles, author display names) in telemetry or info-level logs.
- **NEVER commit or push automatically.** Leave changes **staged**; the developer reviews and commits
  with native git (the source of truth).
- **Use relative paths only** — never hard-code machine-specific absolute paths in committed files.
- **Never run commands against production databases.** Treat any DB command as illustrative.

## Repo layout — flattened cross-loader build

- **The build IS the repo root.** `common/` (shared source spliced into every node), `neoforge/`
  (ModDevGradle), `forge/` (ForgeGradle), `fabric/` (Fabric Loom). Root build files: `settings.gradle`,
  `stonecutter.gradle` (the REAL root build script — Stonecutter repoints `buildFileName` here; the root
  `build.gradle` is inert), `gradle.properties`, `gradlew`, `gradle/`.
- **Version/loader axis = Stonecutter.** Each loader×MC is a real node `:<loader>:<mc>`
  (`:fabric:26.1.2 :fabric:26.2 :neoforge:26.1.2 :neoforge:26.2 :forge:26.1.2 :forge:26.2`). `common` is
  NOT a node — its source is spliced via `rootProject.ext.commonJava` / `commonResources`. Dependency pins
  live in `gradle.properties` as `*_version_<mc>` keys; `mc_versions=26.1.2,26.2`.

## Build & verify

- Build with **per-node tasks, never plain `build`**. All six cells:
  `./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :forge:26.1.2:build :forge:26.2:build :fabric:26.1.2:build :fabric:26.2:build`
- Static analysis: `./gradlew :fabric:26.2:ecjCheck` (the VS Code Problems panel, via `tools/ecj.prefs`).
  The task only FAILS on errors.
- Tests: JUnit 5 sources live in `common/src/test/java` but `common` is not a Gradle project — they are
  wired into the **NeoForge nodes only** (see `neoforge/build.gradle`): `./gradlew :neoforge:26.2:test`.
  Loom and ForgeGradle have no equivalent wiring.
- Neroland Core resolves from **mavenLocal or GitHub Packages** (repositories in `stonecutter.gradle`;
  lazy `gpr.user`/`gpr.key` → `GITHUB_ACTOR`/`GITHUB_TOKEN` credentials).
- A Cowork agent sandbox cannot decompile Minecraft — run builds natively (or via the local gradle MCP)
  on the developer's machine.
- **Verify the cells build before marking a task done.** Never sign off on an uncompiled change.

## Conventions (cross-loader)

- **Resources are HAND-AUTHORED in `common/src/main/resources`** — the multiloader does not run datagen.
  Validate JSON after edits.
- **Platform seams via ServiceLoader (no Architectury).** `platform/Services.init()` resolves EVERY
  service eagerly at init step 0 — never lazily mid-tick. One impl per loader (same package) plus a
  `META-INF/services` entry. Keep `common/` free of `net.neoforged.*` / `net.fabricmc.*` /
  `net.minecraftforge.*` imports.
- **Numbered `init()` ordering** in `NeroNotesCommon.init()` (0 platform → 1 config → 2 telemetry →
  3 blocks+BEs → 4 items+menus → 5 sounds+voices → 6 creative tab → 7 data/erasure → 8 network →
  9 channel/playback → 10 integrations → 11 link module in try/catch). Later stages fill the numbered
  placeholder slots; do not reorder.
- **All menus open through `menu/MenuOpener`** — never raw `player.openMenu(...)`.
- **Network payloads go on NeroNotes' own `neronotes:main` channel** (`network/NotesNetwork`), never
  into Core's `CoreNetwork`.
- **Every `SavedData` accessor routes through Core's `SavedDataRecovery`** — a direct
  `computeIfAbsent` is a review failure.
- Config via Core's `ConfigSchema`/`ConfigManager` (`config/NeroNotesConfig`,
  `config/neronotes.properties`). Gameplay keys are server-authoritative; client-local keys are exactly
  telemetry opt-out, per-voice-family volumes, glow intensity, mute-other-bases.
- Loader entry points: `NeroNotesFabric` (+ `NeroNotesFabricClient`), `NeroNotesForge`,
  `NeroNotesNeoForge` — each calls `NeroNotesCommon.init()` during construction.
- NeoForge/Forge debug tasks use `-PneronotesDebug`; Fabric Loom honours Gradle `--debug-jvm`.

## IDE (VS Code) run & debug

- Workspace: **`neronotes.code-workspace`** (single-root `"."`). Import the Stonecutter nodes as **static
  Eclipse projects**: `./gradlew eclipse` (live Buildship/Loom import is disabled —
  `java.import.gradle.enabled=false`). Re-run `./gradlew eclipse` after dependency changes, then reload
  VS Code. Per-node Eclipse project names are `neronotes-<loader>-<mc>`.
- **Run/Debug** a cell from `tasks.json` / `launch.json`.

## Wiki — keep `wiki/` updated

- This mod has its own **dedicated wiki** in `wiki/` at the repo root: the player- and
  contributor-facing docs for NeroNotes (features, blocks/items, machines, progression, recipes, FAQ).
- **Everything under `wiki/` publishes to a public GitHub wiki via `wiki.yml` and there is no CI guard**
  — treat every word there as public. No private references, no player data, ever.
- **Whenever you add, change, or remove a feature, update `wiki/` in the same change** — treat the
  wiki as part of "done"; code without a matching wiki update is incomplete.
- One page per topic; keep `wiki/Home.md` as the index that links every page, with relative links
  between pages. Validate Markdown via the gradle MCP `markdown_check` (honours `.markdownlint.json`).
- The wiki is **per-mod** — document only NeroNotes here.

## DO NOT

- Commit or push automatically — leave changes staged for the developer.
- Hard-code absolute machine paths in committed files.
- Add loader-specific code to `common/` — use the platform seams.
- Register payloads into Core's `CoreNetwork`, resolve services lazily, or call `openMenu` outside
  `MenuOpener`.
