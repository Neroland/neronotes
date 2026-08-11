# NeroNotes

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**.

**Status:** in development — version `0.0.1-alpha.1`. The foundation is in place (Neroland Core
wiring, platform seams, config, opt-out error reporting — see [`PRIVACY.md`](PRIVACY.md)); no
gameplay content yet.

## Build targets

- **Minecraft:** 26.1.2 and 26.2
- **Loaders:** NeoForge, MinecraftForge/Forge, Fabric (the "6 cells")
- **Java:** 25
- Mod id: `neronotes` · package `za.co.neroland.neronotes`

## Layout

The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:

- `common/` — shared, loader-agnostic source spliced into every loader node
- `fabric/` — Fabric Loom
- `forge/` — ForgeGradle
- `neoforge/` — ModDevGradle
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert

## Building

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build \
          :forge:26.1.2:build :forge:26.2:build \
          :fabric:26.1.2:build :fabric:26.2:build   # all six
```

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context.

## Documentation

- [`wiki/`](wiki/) — player- and contributor-facing docs for NeroNotes (published to the GitHub
  wiki); `wiki/Home.md` is the index.
- `USING-CORE.md` — the Neroland Core APIs this mod consumes (written as features land).
- [`PRIVACY.md`](PRIVACY.md) — what the optional error reporting does and does not collect, and how
  gameplay data in your world save is handled.
