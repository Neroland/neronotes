# Commands

Every NeroNotes command lives under the `/neronotes` root. None of them are needed for normal
play — the blocks and screens do everything — but they cover the safety hatch, library
management from chat, and your data rights.

"Operator" below means Minecraft's gamemaster permission (classic op level 2).

## Soundforge

| Command | Who | Effect |
| --- | --- | --- |
| `/neronotes soundforge return` | Any player **inside** the Soundforge | The exit hatch: returns you to your saved entry point (or the world spawn if the anchor is gone). Does nothing outside the Soundforge; needs no operator rights — being able to leave never depends on op status. |

## Shared library

The same server-side flows as the [Publish Lectern and Disk Exchanger](Publishing-and-the-Disk-Exchanger.md).

| Command | Who | Effect |
| --- | --- | --- |
| `/neronotes library browse [page]` | Anyone | One page of the shared library (1-based; page size follows `library.page_size`). Anonymous entries name nobody. |
| `/neronotes library publish` | Anyone | Publish the pressed disk in your **main hand** — same checks as the Publish Lectern (publishing enabled, you composed it, title revalidated, budget revalidated, size cap and quota). |
| `/neronotes library unpublish <id>` | The entry's composer | Remove **your own** entry. The server checks authorship — nobody else's entries can be touched. |
| `/neronotes library remove <id>` | Operator | Takedown: remove any entry. |
| `/neronotes library approve <id>` | Operator | Make a pending entry visible — only meaningful when `library.op_approval_required` is on. |

## Your data (access and erasure)

Details and reasoning in [Privacy and your data](Privacy-and-Your-Data.md).

| Command | Who | Effect |
| --- | --- | --- |
| `/neronotes data export` | Any player, self-service | Export **your own** stored NeroNotes data: a chat summary plus a full JSON file at `neronotes/exports/<your-uuid>.json` inside the world folder. |
| `/neronotes data export <uuid>` | Operator | Export a named player's data, for serving an access request on their behalf. |
| `/neronotes data erase-me` | Any player, self-service | Warns what erasure means (published entries become anonymous, everything else is deleted) and how to confirm. Changes nothing by itself. |
| `/neronotes data erase-me confirm` | Any player, self-service | Irreversibly erases your stored data through Neroland Core's shared erasure hook — one request purges you across **all** Neroland mods. Operators can equally use Core's `/neroland data` commands. |

## Showcase gallery (operators)

A demo/debug aid, following the wider Neroland gallery convention: one command that stages every
block so a server owner can see the mod working without building anything.

| Command | Who | Effect |
| --- | --- | --- |
| `/neronotes gallery` | Operator | Builds a labelled showcase plaza a few blocks east of you: all seven Resonant Blocks (one per voice family), a Resonator, the Harmonic Gate, the transport lectern, the Disk Press, the Publish Lectern, the Disk Exchanger, the four pattern walls (layers 1–4) and the seven voice pedestals, each with a floating label saying what it does. The Resonator is loaded with a built-in four-layer demo composition whose loop points make it repeat until stopped; it plays on **your own** `gallery` channel (created if absent, reused if you already have one — an ordinary owner channel) through the normal server-side authorisation path, and the Resonant Blocks of the families it plays flare in time. Re-running the command in the same spot rebuilds in place without stacking labels. |
| `/neronotes gallery clear` | Operator | Removes the gallery footprint (blocks and labels). Run it standing where you ran `gallery`. Breaking or clearing the Resonator stops the demo and frees its channel play slot; the `gallery` channel itself remains yours, like any channel you own. |

## What has no command (on purpose)

- **Playing and composing** — Resonators, the sequencer, the Disk Press and the Exchanger are
  in-world interactions, not chat commands.
- **Channel management** — 0.1.0 ships no `/neronotes channel …` commands; trust-list editing,
  renaming and deletion have no player-facing surface yet (see
  [Resonance channels](Resonance-Channels.md#the-trust-list)).
