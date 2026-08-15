# NeroNotes Wiki

Player- and contributor-facing documentation for **NeroNotes**, part of the
Neroland ecosystem. Built on **Neroland Core**.

> **Status:** version `0.1.0-beta.1` — the full loop **compose → press → sync → play → publish →
> download** ships: Resonant Blocks and Resonators with synchronised channel playback (load a
> pressed disk onto a Resonator by right-clicking with it — the disk is read, not consumed), the
> Harmonic Gate and Soundforge composing dimension, the sequencer at the transport lectern with
> pattern walls and voice pedestals, blank disks and the Disk Press (anonymous publishing is a
> first-class choice), sharing (the Publish Lectern, the server-wide shared library, and the Disk
> Exchanger for downloading and duplicating disks), full data-protection support (self-service
> export and erasure commands plus an automatic retention sweep), feature-detected ecosystem
> integrations (threshold-event milestones, quest-pack ids, a pricing seam — copies are free in
> 0.1.0), and a companion (NeroLink) link module: library browsing, your own disks and channels,
> and remote play/stop on your own Resonators.
>
> **What this release honestly is not:** block and item textures are generated placeholders, and
> **no `.ogg` audio ships** — every voice aliases a vanilla sound event. There is no real-audio
> import, no MIDI import, no in-world note placement (the sequencer is a GUI), no playlists or
> queues (hence no link `skip` action), no light-show blocks, no cross-dimension relays, and no
> in-game channel trust/rename surface yet. The same list lives in the
> [changelog](https://github.com/Neroland/neronotes/blob/main/CHANGELOG.md).

## Contents

- [Resonance channels](Resonance-Channels.md) — channel ownership, trust lists, the emit range and
  the audio-spam cap (the server-side foundation everything plays through).
- [Resonant Blocks and Resonators](Resonant-Blocks-and-Resonators.md) — the seven family-tuned
  Resonant Blocks, the Resonator disk player, synchronised playback and the client audio/glow
  settings.
- [The Harmonic Gate and the Soundforge](Harmonic-Gate-and-Soundforge.md) — the powered gate
  machine (its energy charge is the only requirement — no progression gate), the starlit
  composing dimension, and the never-stranded return guarantees.
- [The Sequencer, Custom Disks and the Disk Press](Sequencer-and-Disk-Press.md) — the transport
  lectern's grid sequencer, pattern walls and voice pedestals, blank disks, the size budget, title
  rules, and pressing with or without credit.
- [Publishing and the Disk Exchanger](Publishing-and-the-Disk-Exchanger.md) — the Publish Lectern,
  the server-wide shared library (size cap, quotas, optional operator approval), the Disk Exchanger
  for downloading and duplicating disks, the `/neronotes library` commands, and the aggregate-only
  download counting.
- [Integrations and threshold events](Integrations-and-Threshold-Events.md) — the server-wide
  milestone events pack makers can build quests on, the ids a quest pack can reference, the
  Exchanger pricing seam (free in 0.1.0), and how everything degrades silently when a companion
  mod is absent.
- [Companion link module (NeroLink)](Companion-Link-Module.md) — what a paired companion app can
  see (the library, your own disks and channels, what is playing) and do (play/stop on your own
  channels only), the anonymity guarantees, and the `link.module_enabled` switch.
- [Commands](Commands.md) — every `/neronotes` command: the Soundforge exit hatch, the library
  commands, and the data access/erasure commands, with who may run each.
- [Configuration](Configuration.md) — every key in `config/neronotes.properties` with its
  default and range, and which keys are server-authoritative vs. client-local.
- [Privacy and your data](Privacy-and-Your-Data.md) — what the mod stores, the
  `/neronotes data export` and `/neronotes data erase-me` commands, what erasure does to published
  compositions and disks in circulation, and the automatic retention sweep.

Add one page per block, item, machine, or system as it is built, and link it here. Keep this page
as the index.

## See also

- [Build & contributor context](../AGENTS.md)
