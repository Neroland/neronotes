# Changelog

All notable changes to **NeroNotes** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0-beta.1] - 2026-10-11

Optional NeroEconomy pricing for Disk Exchanger library copies. NeroEconomy stays optional:
without it nothing changes and copies stay free.

### Added

- **Paid library copies with NeroEconomy.** With NeroEconomy 0.3.0 or later installed, copying a
  library entry at the Disk Exchanger costs `exchanger.library_copy_price` credits (new key,
  default 25, 0 = free). For an entry whose composer is someone else, the price goes to the
  composer less NeroEconomy's transfer fee, which goes to its Reserve; for anonymous,
  erased-author or your own entries the whole price goes to the Reserve. A refused payment writes
  nothing and the Exchanger says why. Duplicating a disk you hold stays free.
- **The split on the Exchanger screen.** The title bar shows the copy price, and the Copy button's
  tooltip states what you pay and what the composer receives before you confirm. The composer is
  never named or identified.
- **Pressed disks are untradeable in NeroEconomy.** `neronotes:custom_disk` joins the
  `neroeconomy:untradeable` item tag (data only, harmless without NeroEconomy), so disks are never
  listed, vaulted or sent to the companion app with their composer and title data.

### Changed

- The Disk Exchanger pricing seam gains add-only defaults: a server-aware `chargeCopy` overload
  and price/fee quotes for the screen. Existing implementations keep working unchanged.
- Mod metadata lists NeroEconomy as an optional dependency (`[0.3.0-beta.1,)`); the build
  compiles against its API only and never bundles it.

## [0.3.0-beta.1] - 2026-09-24

EMI compatibility. No gameplay, id, tag or config change.

### Added

- **EMI support.** Every NeroNotes recipe uses a vanilla recipe type, which EMI shows on its own, so
  nothing needed a plugin. The build now compiles against the community EMI Unofficial Port (Unstable),
  the only EMI build for Minecraft 26.x, and dev clients load it with `-PwithEmi` (default runs stay
  JEI-only). EMI stays optional.

## [0.2.0-beta.1] - 2026-09-20

Minecraft **26.3** support. No gameplay, id, tag or config change.

### Added

- **Minecraft 26.3** as a new Stonecutter node on every loader — NeoForge `26.3.0.7-beta`,
  Forge `26.3-66.0.2` and Fabric (fabric-api `0.161.0+26.3`, NeoForm `26.3-1`) — built alongside
  26.1.2 and 26.2, so every release now ships **nine** loader × version jars.

### Changed

- VS Code run/debug configurations (`.vscode/launch.json`, `.vscode/tasks.json`) gain the three
  26.3 cells; the "Build all" task now builds all nine.
- CI (`multiloader.yml`, `publish.yml`) builds, attaches and publishes the 26.3 jars.
- Requires **Neroland Core 1.13.0** (was `1.11.0`) — the first Core release with a 26.3
  build. The loader range still derives from the pin (`[${nerolandcore_version},2.0)`).
- JEI pins moved to the newest published builds on each Minecraft version: `29.40.0.101` (26.1.2), `30.35.0.223` (26.2) and `31.3.0.18` (26.3). Compile-time API only — JEI remains a soft dependency and the shipped jar gains no hard requirement.

### 26.3 port notes

- 26.3 API differences are handled with Stonecutter blocks: `PoseStack#rotate` (was `mulPose`), the new `Prediction` argument on `drop` / `placeItemBackInInventory`, `setPermanentlyInvulnerable`, and similar renames.
- Build: the shared `common/` Java source is now preprocessed by Stonecutter for every non-active node (`stonecutterProcessCommon`), so common code can carry `//? if >=26.3 {` blocks, and `common/src/main/resources-<mc>` overlay folders are merged over the shared resources for matching nodes (`mergeCommonResources`). The active node still compiles the raw `common/` folder.
- Build plugins aligned with Neroland Core: ModDevGradle `2.0.147` (the older 2.0.141 cannot set up NeoForge 26.3), ForgeGradle `7.0.40`, Stonecutter `0.9.8`.

