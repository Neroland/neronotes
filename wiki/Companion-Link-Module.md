# Companion link module (NeroLink)

NeroNotes registers a module with Neroland Core's **link API**, the shared surface the **NeroLink**
bridge mod serves to companion clients (for example a phone app). With NeroLink installed on the
server, a paired companion client can browse the shared composition library, see its own disks and
resonance channels, and start or stop its own Resonators remotely.

NeroNotes itself ships **no HTTP server and no networking** for this — it only registers data
sections and actions with Core. Without NeroLink installed nothing is exposed anywhere, and the
module costs nothing.

## What your companion client can see

Everything is scoped to **the account that paired the client**. There is no way to browse other
players' channels, disks, or bases through this module.

| Section | Contents |
|---|---|
| `library` | The shared library's **visible** entries — the same public listing every player sees at the Disk Exchanger: title, composer credit (see below), voice palette and the aggregate download count. Paginated (`page` parameter, zero-based), never the whole library at once. Scores never cross this surface. |
| `disks` | The pressed custom disks **you** are carrying: title, credit, voice palette and score stats. Inventories only exist while you are online, so this list is empty while you are offline (the snapshot says `player_online: false` so the app can explain why). |
| `channels` | Only the resonance channels **you own or are trusted on** — name, dimension, your role, live playing state, a listener count, and (for your own channels) the size of the trust list. Never a server-wide roster. |
| `now_playing` | The currently playing subset of exactly those same channels. |

### Composer credit and anonymity

Anonymous compositions carry **no author field at all** in any link response — not an empty one.
A composition published anonymously and a composition whose author asked for data erasure look
identical: `"anonymous": true` and nothing else. Player UUIDs never appear in any section: your
own channels are simply "yours", trusted channels are referenced by an opaque id that does not
encode their owner, and trust lists are reported as a count, not a list of people.

## What your companion client can do

| Action | Effect |
|---|---|
| `play` | Start the loaded Resonator(s) bound to one of your channels (owned or trusted), from their current position. |
| `stop` | Stop them. |

Both actions:

- work **only on channels you own or are trusted on** — anything else answers `NOT_OWNER`,
  identically whether the channel belongs to someone else or does not exist;
- require you to be **online in the game** — remotely playing audio at an offline player's base
  is refused by design;
- act only on Resonators whose chunk is currently loaded, and pass the same server-side
  authorisation and audio-spam cap as pressing the button in person. Operator status is never
  honoured over the link — an admin's phone has an admin's account, not an admin's powers.

There is deliberately **no `skip` action**: 0.1.0 has no playlists or queues — a Resonator plays
one disk — so there is nothing honest to skip to. It can arrive together with playlists in a later
release. Likewise nothing on the link can create or rename a channel, edit a trust list, press a
disk, or publish, unpublish, approve or remove a library entry: publishing is a committal decision
you make in the world, not an API call.

## Live events

- `now_playing` — sent **to a channel's owner only** when one of their channels genuinely starts
  or stops playing (a Resonator re-arming after a restart counts; periodic re-anchors do not).
- `library` — a broadcast when the shared library changes (publish, unpublish, takedown,
  approval). It carries **counts only** — no titles, no authors, no entry ids.

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `link.module_enabled` | `true` | Server-side master switch. When `false`, NeroNotes registers nothing with the link API: companion clients see no NeroNotes data and can perform no NeroNotes actions. |

## Privacy

The module follows the same rules as the rest of NeroNotes (see
[Privacy and your data](Privacy-and-Your-Data.md)): every response is scoped to the requesting
account, anonymous authorship is preserved everywhere, no player UUID or third-party identity is
ever emitted, and a failure in the link module can never affect gameplay — it is registered last
and isolated, and the worst outcome is a companion app that reports NeroNotes as absent.
