# Resonance channels

The **resonance signal** is how NeroNotes carries music around a base: emitters broadcast note and
transport events (`note_on`, and `play` / `stop` / `seek`) on a **channel**, and nearby listeners
hear them rendered client-side from the mod's registered sounds (all of which alias vanilla sound
events in this release — no custom audio ships yet). Music is data, not audio — the server never
streams sound. Note lengths are carried in every score, but this release voices notes as
`note_on` one-shots; sustained `note_off` pairs are a later release.

> The channel system (this page) is the server-side foundation. The in-world blocks that use it —
> [Resonant Blocks and Resonators](Resonant-Blocks-and-Resonators.md) — play through it with
> synchronised playback.

## Channel identity — owner-scoped, never global

A channel is identified by **dimension + owner + name** and displayed by its name. Names are only
unique per owner: your `workshop` channel and a neighbour's `workshop` channel are different
channels. There is no global channel namespace, so nobody can squat or hijack a name you use.

Channel names are validated server-side: at most 32 characters, no control characters, no `§`
formatting codes.

## Listening vs. controlling

- **Anyone in range may listen.** Resonance events reach subscribed players within the configured
  emit range (`signal.emit_range_blocks`, default 64 blocks, capped at 128) in the channel's
  dimension.
- **Only the owner, trusted players, or an operator may control** a channel: emitting notes,
  transport (`play` / `stop` / `seek`), and renaming all require it. Every check runs on the
  server — the client never decides what it may control, and proximity never grants permission.

## The trust list

Each channel carries a **trust list** — players its owner has granted control. Trusted players can
emit and drive the transport; only the **owner or an operator** can edit the trust list itself,
rename in their stead, or delete the channel. The owner is implicitly trusted and never appears on
the list.

**An honest limitation of this release:** the trust list, renaming and deletion are enforced
server-side and cleared correctly by data erasure, but **0.1.0 ships no command or GUI to edit
them** — so in practice a channel answers to its owner and operators, and the "trusted" role is
not yet reachable in normal play. A channel-management surface is a later release.

## The audio-spam cap

To keep shared areas pleasant, the server caps how many channels can be **playing at once within a
chunk radius** (`signal.max_playing_channels_per_chunk_radius`, default 3). A `play` request over
the cap is quietly refused — no chat spam, no log spam. Stopping a channel frees its slot.

## Persistence and your data

Channel definitions (dimension, owner, name, trust list) are stored in the world save, guarded by
Neroland Core's saved-data recovery so a corrupted file cannot crash-loop the server. Channel
ownership and trust entries are part of a player's personal data: when a player's data is erased
through Core's erasure tooling, their channels and every trust-list entry naming them are removed,
while other players' channels are untouched. See [`PRIVACY.md`](../PRIVACY.md).

## Configuration

| Key                                            | Default     | Meaning                                                 |
|------------------------------------------------|-------------|---------------------------------------------------------|
| `signal.emit_range_blocks`                     | 64 (16–128) | Broadcast range for resonance events                    |
| `signal.max_playing_channels_per_chunk_radius` | 3 (1–16)    | Concurrently playing channels allowed per chunk radius  |

Both keys are server-authoritative: in multiplayer the server's values win.
