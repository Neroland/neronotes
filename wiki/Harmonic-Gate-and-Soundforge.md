# The Harmonic Gate and the Soundforge

The **Soundforge** is NeroNotes' composing dimension: a small, quiet, starlit platform floating in
the void, reached through the **Harmonic Gate** — a powered machine block. Composition happens
*there* by design; everything else in NeroNotes (Resonant Blocks, resonance channels, Resonators)
works everywhere, from your first day on Earth, with no gate in the way.

> **Placeholder art note:** like the Stage 3 blocks, the Harmonic Gate's textures are generated
> placeholders (matte black with a violet neon arch) until a real art pass replaces them.

## The Harmonic Gate

A blast-resistant, matte-black block with a violet neon arch. The arch stays dim while the gate
charges and lights fully once a crossing is affordable — the block answers "can I go?" at a glance.

### Crafting

Shaped recipe, 3×3:

| Slot | Slot | Slot |
| --- | --- | --- |
| Nero Alloy Plate | Plasma Glass | Nero Alloy Plate |
| Plasma Glass | Amethyst Block | Plasma Glass |
| Nero Alloy Plate | Plasma Glass | Nero Alloy Plate |

The plates and glass are matched by **tags** (`c:plates/nero_alloy`,
`nerolandcore:materials/plasma_glass`), so any mod's Nero Alloy plates or Plasma Glass variants
work. Both materials come from Neroland Core.

### Powering it

The gate accepts energy on every face through Neroland Core's shared energy capability. Any Nero
machine output works, and on NeoForge and Forge the capability falls back to standard **Forge
Energy**, so third-party FE sources (for example Energized Power) charge it too.

Two server config keys in `config/neronotes.properties` shape it:

- `gate.energy_capacity` — the gate's internal buffer (default 16,000 NE).
- `gate.teleport_energy_cost` — energy per crossing *into* the Soundforge (default 8,000 NE).
  **Returning is always free.** An operator can set the cost to `0` to make crossings free.

## No progression requirement

Nothing in NeroNotes is progression-gated. Entering the Soundforge needs exactly one thing: a
**charged** Harmonic Gate — craft it, power it, walk through, from your first day on Earth.
(Earlier development builds sealed entry behind a Neroland Core progression gate; that was
removed for standalone-first play.)

## Crossing over

Right-click a **charged** Harmonic Gate and you arrive on the
Soundforge platform: a polished blackstone deck under a fixed starfield, with four inset lights and
a Harmonic Gate at its centre. Around it stands the composing furniture: the **transport lectern**
(opens the [sequencer](Sequencer-and-Disk-Press.md)), the **Disk Press**, the
**[Publish Lectern](Publishing-and-the-Disk-Exchanger.md)**, four **pattern walls** along the north
rim and seven **voice pedestals** along the south rim. The platform is built by the mod on your
first entry — there is no worldgen in the void — and re-entering heals a vandalised platform.

Your exact position, facing and dimension are saved **before** you leave, server-side.

## Getting home

Three guarantees, so you are never stranded:

1. **The centre gate.** Using the Harmonic Gate on the platform returns you to exactly where you
   entered from — no energy, no checks. If someone broke it, re-entering through any gate rebuilds
   the platform.
2. **The command.** `/neronotes soundforge return` works for any player *inside* the Soundforge
   (it is an exit, not a teleport — it does nothing anywhere else, and needs no operator rights).
3. **Logging out inside is safe.** Your return anchor is stored with the world and survives
   restarts. If the anchor is ever missing or its dimension no longer exists, the gate sets you
   down at the world spawn instead of nowhere.

## Your data

The Soundforge keeps one small server-side record per player who has entered: the return position
and your in-progress composing session (the sequencer's working score). It stays inside your world
save, is never transmitted anywhere, and is erased by NeroNotes' per-player data purge (see
[Privacy and your data](Privacy-and-Your-Data.md)).
