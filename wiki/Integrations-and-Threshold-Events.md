# Integrations and threshold events

NeroNotes requires only **Neroland Core**. Every other pairing is optional and feature-detected at
startup: when a companion mod is present the pairing lights up, and when it is absent NeroNotes
simply degrades silently — no errors, no missing-mod warnings, no broken features.

## Threshold events (for pack makers)

NeroNotes announces server-wide musical milestones on Neroland Core's shared **threshold event
bus**. Any mod built on Core can listen without depending on NeroNotes — this is how quest packs
turn milestones into content.

| Channel | Fires when | Thresholds | Scope |
| --- | --- | --- | --- |
| `neronotes:compositions_published` | the server's shared library grows past a milestone | 1, 10, 50, 100, 500, 1000 | `library` |
| `neronotes:channel_listeners` | one resonance channel's listener count grows past a milestone | 2, 5, 10, 25 | the dimension id (e.g. `minecraft:overworld`) |

Semantics, precisely:

- **Rising edges only.** A crossing fires the moment the count passes a threshold upward, once.
  A count sitting above a threshold never re-fires it; an unchanged count fires nothing.
- **Re-crossings are real.** If the library shrinks below a milestone (unpublish, takedown) and
  later grows past it again, that is a genuine new crossing and it fires again.
- The published count includes entries awaiting operator approval — publishing is the composer's
  act; approval only controls visibility.
- **Privacy by design:** the scope of a crossing is always a *place or system* key — `library`, or
  a dimension id. No player name, no UUID, no channel name, no coordinates. A crossing tells you
  *that the server's music scene hit a milestone*, never *who* or *where exactly*.

## NeroQuests

With NeroQuests installed, its `custom_event` quest objective consumes exactly the channels above
— "the server published its first composition" becomes quest content with no configuration on the
NeroNotes side. Useful ids for quest packs:

- Items: `neronotes:blank_disk` (the natural reward — press- and Exchanger-ready),
  `neronotes:custom_disk` (only meaningful with disk contents; prefer granting blank disks).
- Threshold channels: the two in the table above.

NeroNotes declares no progression gates — the Soundforge needs only a charged Harmonic Gate, so
there is no gate id for a quest pack to reference.

Because threshold events are server-wide by design, *per-player* objectives ("press your own first
disk") belong to quest-pack mechanics such as inventory or advancement objectives, not to the
threshold bus. Voice unlocks do not exist in 0.1.0 — every voice ships unlocked.

## NeroEconomy

The Disk Exchanger consults a **pricing seam** before every library copy. In 0.1.0 the built-in
answer is *free* — always allowed, nothing charged — even with NeroEconomy installed; the seam
exists so a later release can price Exchanger copies and route composer royalties (anonymous and
erased-author compositions have no royalty target by design). Duplicating a disk you already own
never involves the library and is outside the seam.

## NeroEvents

Reserved for later: a documented seam exists for server events temporarily taking over consenting
channels (concerts, station-wide themes), but nothing is built in 0.1.0 and no behaviour changes
with NeroEvents installed.

## Other worlds

Nothing to configure: resonance channels, Resonators and synchronised playback are per-dimension
and work anywhere — the overworld, the Soundforge, or any dimension another mod adds. NeroNotes
never assumes the overworld for playback. The flip side is a deliberate scope cut: **there are no
cross-dimension relays** in 0.1.0 — a channel lives, plays and is heard within one dimension.
