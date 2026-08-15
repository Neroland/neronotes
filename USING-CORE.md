# Using Neroland Core

NeroNotes is built on **Neroland Core** (`nerolandcore`), the shared foundation library of the
Neroland mod family. Core is the **only required dependency**. This document lists every Core API
NeroNotes actually consumes — verified against the source — and how the dependency is declared.

## Declaring the dependency

The compiled-against Core version lives in `gradle.properties`:

```properties
nerolandcore_version=1.11.0
```

**The manifest version ranges are DERIVED from that property — never hardcoded.** A hardcoded
`[1.0,2.0)` lets an old Core load against a newer mod, which has caused real crashes in this
family. As wired in this repo:

- `neoforge/build.gradle` and `forge/build.gradle` (dependency + manifest token):

  ```groovy
  implementation "za.co.neroland.nerolandcore:nerolandcore-<loader>-${mc}:${rootProject.nerolandcore_version}"
  // in generateModMetadata / manifest replacement:
  nerolandcore_version_range: "[${rootProject.nerolandcore_version},2.0)",
  ```

  Both `mods.toml` templates declare Core `required` with
  `versionRange = "${nerolandcore_version_range}"` and `ordering = "AFTER"` (Core loads first).

- `fabric/build.gradle` + `fabric/src/main/templates/fabric.mod.json`:

  ```groovy
  implementation "za.co.neroland.nerolandcore:nerolandcore-fabric-${mc}:${rootProject.nerolandcore_version}"
  ```

  ```json
  "depends": { "nerolandcore": ">=${nerolandcore_version} <2.0.0" }
  ```

Core artifacts resolve from **Maven Local** (run `./gradlew publishToMavenLocal` in the Core
repo) or from **GitHub Packages** (`https://maven.pkg.github.com/Neroland/neroland-core`, lazy
`gpr.user`/`gpr.key` → `GITHUB_ACTOR`/`GITHUB_TOKEN` credentials) — both configured in the root
`stonecutter.gradle`.

## Core APIs consumed

All packages below are `za.co.neroland.nerolandcore.*`.

### `data` — saved-data recovery and player-data erasure

- **`SavedDataRecovery`** — every one of NeroNotes' four `SavedData` stores loads through
  `SavedDataRecovery.get(...)` and refreshes backups with `backupNow(...)` after purges. A direct
  `getDataStorage().computeIfAbsent` is a review failure here. The stores:
  `signal/ChannelStore` (`neronotes:channels`), `library/LibraryStore` (`neronotes:library`),
  `soundforge/SoundforgeSessionStore` (`neronotes:soundforge_sessions`) and
  `data/ActivityStore` (`neronotes:activity`).
- **`PlayerDataErasure`** — `data/NeroNotesData` registers one eraser at init step 7 (early on
  purpose), purging library authorship (sever the link, keep the work), Soundforge sessions,
  channels + trust entries, the activity record and live subscriptions.
  `/neronotes data erase-me confirm` calls `PlayerDataErasure.erase(...)` — the full Neroland
  fan-out, same as Core's `/neroland data eraseme`.
- **`ErasureConformance`** — runs in the test suite
  (`common/src/test/.../NeroNotesErasureConformanceTest`) with a probe per store, seeded data, a
  bystander-survival check and a no-UUID-in-report assertion.

### `config` — typed configuration

- **`ConfigSchema` / `ConfigManager` / `ConfigValue`** — `config/NeroNotesConfig` registers the
  full schema (written to `config/neronotes.properties`). Every gameplay key is
  server-authoritative; the only client-local keys are the telemetry opt-out, per-voice-family
  volumes, glow intensity and mute-other-bases.

### `event` — threshold events

- **`ThresholdEvents`** — `integration/NotesThresholds` fires `ThresholdCrossing`s on the
  channels `neronotes:compositions_published` (scope `library`) and
  `neronotes:channel_listeners` (scope = dimension id). Scopes are places/systems, never player
  identities — Core's contract. NeroQuests' `custom_event` objective consumes these.

### `machine` / `energy` / `platform` — the powered machine seam

- **`machine.AbstractMachineBlockEntity`** — `entity/HarmonicGateBlockEntity` extends it (energy
  buffer, ticking, side config for free).
- **`energy.NeroEnergyStorage`** — the gate's buffer type, exposed per loader on Core's shared
  `nerolandcore:energy` capability so any Core-aware (or, via Core's fallback, standard
  Forge-Energy) source can charge it:
  - NeoForge: `platform.NeoForgeEnergyLookup.ENERGY` (`neoforge/.../NeoForgeCapabilities`)
  - Forge: `platform.ForgeEnergyLookup.ENERGY` (`forge/.../ForgeCapabilities`)
  - Fabric: `platform.FabricEnergyLookup.ENERGY` (registered in `NeroNotesFabric`)

### `registry` — registration and the creative tab

- **`RegistrationProvider`** — all NeroNotes registrations (blocks, block entities, items, data
  components, menus, sound events) go through Core's provider;
  `RegistrationProvider.attach(bus)` is called from the NeoForge and Forge entry points (Fabric
  registers eagerly).
- **Creative tab** — NeroNotes registers its own dedicated **NeroNotes** tab
  (`item/NeroNotesCreativeTab`, `itemGroup.neronotes`, Resonator icon) at init step 6, via
  Core's `RegistrationProvider` over the vanilla `CREATIVE_MODE_TAB` registry — the same
  cross-loader pattern as Core's `CoreCreativeTab`, which NeroNotes no longer contributes to
  (items moved to the dedicated tab).

### `link` — the companion (NeroLink) SPI

- **`NeroLinkRegistry`, `LinkSnapshotProvider`, `LinkActionHandler`, `LinkActionResult`,
  `LinkModuleInfo`, `LinkEvent`** — the five-class module in `link/` (init step 11, last, in
  try/catch): sections `library` / `disks` / `channels` / `now_playing`, actions `play` /
  `stop`, events `now_playing` (owner-scoped) and `library` (broadcast, counts only). Both
  `register*` calls take two arguments (provider + `LinkModuleInfo`).

### `worldgen` — space tags

- **`SpaceTags`** — the Soundforge dimension type is tagged into `neroland:space/dimensions`
  with `"required": false` (`data/neroland/tags/dimension_type/space/dimensions.json`), so
  space-aware mods can recognise it while a Core-only server sees a legitimately empty tag.
  Consumed in tests only (`SoundforgeResourcesTest` proves the empty-tag path never throws);
  no runtime code calls it.

### Tags and datapack conventions

- **Recipes** use Core material tags, never another mod's item ids:
  `#nerolandcore:materials/plasma_glass` (blank disk, Disk Exchanger, Harmonic Gate) and
  `#c:plates/nero_alloy` (Harmonic Gate).
- **Item highlights** — Core's coloured slot borders are data-only: NeroNotes ships
  `data/neroland/tags/item/highlight/{machines,tools,materials}.json` (namespace `neroland`,
  `"replace": false`, `"required": false` entries).

### What NeroNotes deliberately does **not** use

- **`network.CoreNetwork`** — never. All payloads live on NeroNotes' own `neronotes:main`
  channel (`network/NotesNetwork`); Core's payload lists are drained during Core's own bootstrap
  and downstream registrations would be silently dropped.
- **Core's menu helpers** — `menu/MenuOpener` is NeroNotes' own seam (Paper-hybrid-safe
  `openMenu` wrapper); every menu opens through it.
- **`progression.ProgressionGates` / `CoreGates`** — NeroNotes declares and checks no
  progression gates. The `neronotes:soundforge` gate (requiring Core's Industrial Power) shipped
  through Stage 10 and was removed on 2026-08-15 for standalone-first play, following Nerotech:
  entering the Soundforge needs only a charged Harmonic Gate.
- **Core sound or dimension helpers** — Core ships none; NeroNotes registers its own
  `SoundEvent`s and defines the Soundforge as a datapack dimension.
