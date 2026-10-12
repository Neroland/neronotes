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

NeroQuests is optional. NeroNotes ships a quest chapter as plain data inside its own jar — it
appears in the quest book when NeroQuests is installed and does nothing otherwise; NeroNotes has
no dependency on NeroQuests in any form.

### The "Resonance" chapter

Chapter `neronotes:resonance`, 11 quests:

| Quest | Scope | Done when |
| --- | --- | --- |
| Blank Slate | you | craft a Resonant Disk (blank) |
| Tuning Fork | you | craft a Harmonic Gate (rewards four blank disks) |
| The Soundforge | you | enter the Soundforge |
| Pressed | you | hold a pressed disk of your own |
| Published | you | publish one of your compositions to the shared library (anonymously counts) |
| Swap Meet | you | craft a Disk Exchanger |
| On the Air | you | someone other than you tunes in to one of your channels |
| First Pressing | server | the shared library holds its first composition |
| A Library Grows | server | ten compositions |
| A Hundred Songs | server | one hundred compositions |
| Drew a Crowd | server | one resonance channel has five listeners at once |

The four *server* quests are a shared "world log": they complete once for the whole server and
credit nobody in particular, because they read the threshold channels above.

### Personal milestone gates

Two personal milestones are recorded as player-scope **record gates** in Neroland Core's gate
store, which the quest pack reads with a `gate_open` objective:

| Gate | Opens when |
| --- | --- |
| `neronotes:first_publish` ("Published Composer") | one of your disks is published — on operator approval instead when `library.op_approval_required` is on |
| `neronotes:first_broadcast` ("On the Air") | a listener other than you joins one of your channels, whether or not you are online |

They have no requirements and gate nothing: NeroNotes declares no *progression* gates — the
Soundforge needs only a charged Harmonic Gate. A gate records only *that* you published or were
heard, never which composition or who listened, so an anonymous publication stays anonymous.

### Ids for your own packs

- Items: `neronotes:blank_disk` (the natural reward — press- and Exchanger-ready),
  `neronotes:custom_disk` (only meaningful with disk contents; prefer granting blank disks).
- Threshold channels: the two in the table above (server-wide world-log quests).
- Gates: the two above (per-player quests).

Voice unlocks do not exist — every voice ships unlocked.

## NeroEconomy

NeroEconomy is optional. Without it, library copies at the Disk Exchanger are free. With it
installed (0.3.0 or later), each library copy costs `exchanger.library_copy_price` credits (25 by
default, 0 for free):

- **An entry with a composer who is not you**: the price moves from you to the composer in one
  step, less NeroEconomy's transfer fee (5 % by default), which goes to the Reserve. Copying never
  creates credits.
- **An anonymous or erased-author entry, or your own**: the whole price goes to the Reserve. An
  author is never looked up.

The price shows in the Exchanger's title bar, and the Copy button's tooltip states the split before
you confirm. The composer is never named or identified to you. If the payment is refused (not
enough credits, a budget limit), the Exchanger writes nothing and tells you why. Duplicating a disk
you already own never involves the library and is always free.

Pressed disks are tagged `neroeconomy:untradeable`, so they cannot be listed, vaulted or sent to
the companion app through NeroEconomy.

## NeroEvents

Reserved for later: a documented seam exists for server events temporarily taking over consenting
channels (concerts, station-wide themes), but nothing is built in 0.1.0 and no behaviour changes
with NeroEvents installed.

## Other worlds

Nothing to configure: resonance channels, Resonators and synchronised playback are per-dimension
and work anywhere — the overworld, the Soundforge, or any dimension another mod adds. NeroNotes
never assumes the overworld for playback. The flip side is a deliberate scope cut: **there are no
cross-dimension relays** in 0.1.0 — a channel lives, plays and is heard within one dimension.
