# Project context for AI coding agents — neronotes

> `AGENTS.md` and `CLAUDE.md` are kept **byte-identical**; update both together.

## The mod

- **NeroNotes** — the music layer of the Neroland sci-fi Minecraft mod ecosystem, built on
  **Neroland Core** (required dependency, `nerolandcore_version` in `gradle.properties`; the
  manifest version ranges are DERIVED from it — never hardcode `[1.0,2.0)`).
- **Status:** Stage 2 complete (on Stages 0–1: Core wiring, `platform/` seams,
  `config/NeroNotesConfig`, `telemetry/NeroNotesTelemetry` + `PRIVACY.md`, `menu/MenuOpener`,
  own channel `neronotes:main`, numbered `init()`; versioned `score/Score` + `ScoreCodec`,
  data-driven `voice/VoiceRegistry`, `sound/NeroNotesSounds` — **no `.ogg` ships**, vanilla sound
  aliases only). Stage 2 adds the **resonance signal**, server-side: the `signal/` package —
  owner-scoped channels `(dimension, ownerUUID, name)` with per-channel trust lists
  (`ChannelKey`/`ResonanceChannel`/`ChannelTable`), persisted in `ChannelStore`
  (`SavedData` behind Core's `SavedDataRecovery`, name `neronotes:channels`, `purgePlayer` +
  `purgePlayerAndBackup` ready for the Stage 7 erasure hook), owner-based authorisation through
  `ChannelAccess` (owner / trust list / operator — **proximity is never permission**), the
  audio-spam cap `ChannelConcurrencyGuard`, and `ResonanceService` (subscriptions, ranged
  broadcast of note/transport events per `signal.emit_range_blocks`). First real network payloads
  (`ResonanceNotePayload`, `ResonanceTransportPayload` — tiny, never score-carrying) declared once
  in `NotesNetwork` and wired per loader: Fabric `PayloadTypeRegistry`+`ClientPlayNetworking`,
  NeoForge `PayloadRegistrar`, Forge `ChannelBuilder` payload channel. No blocks/items yet;
  playback/sync is Stage 3.
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
