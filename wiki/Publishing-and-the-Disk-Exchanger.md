# Publishing and the Disk Exchanger

Once a composition is pressed onto a [Resonant Disk](Sequencer-and-Disk-Press.md), you can share it
with the whole server: **publish** it to the shared library at the Publish Lectern in the Soundforge,
and anyone can **download** a copy at a Disk Exchanger back home.

> Block textures are generated placeholders for now, like the rest of the mod's art.

## The shared library

The library is one server-wide catalogue of published compositions. Each entry holds the
composition's title, its credited composer (or nothing, for anonymous publishes), its dominant voice
family, the score itself, and an **aggregate download count**.

That count is deliberately the *only* download data the library keeps: nobody's listening history,
no record of who downloaded what or when, no play logs — one number per composition, full stop.

## Publishing at the Publish Lectern

The **Publish Lectern** stands on the Soundforge platform, beside the arrival spot. Tap it while
holding a pressed Resonant Disk and the composition is published. The disk stays yours — the
library stores a copy of the score, not the item.

The server checks, in order:

- **Publishing is enabled** (`library.publishing_enabled`).
- **You composed the disk** — only a disk's composer may publish it.
- **The title still passes the server's naming rules** (length cap and word list — the same
  validation as the Disk Press, run again at publish time).
- **The score still fits the size budget** (`disk.score_budget_bytes`) — over-budget is refused,
  never trimmed.
- **The library has room** (`library.size_cap`, server-wide) and **you have quota left**
  (`library.per_player_quota`).

If the server runs **approval mode** (`library.op_approval_required`, off by default), your entry is
created hidden and appears in the library only after an operator approves it.

### Publishing anonymously

Anonymity is decided when you press the disk, and it carries through: a disk pressed anonymously
publishes anonymously. Anonymous entries show "an anonymous composer" everywhere — the listing, the
copied disks, everywhere — and store no display name at all.

## The Disk Exchanger

The **Disk Exchanger** is an overworld machine — craftable in survival (copper blocks, plasma glass,
amethyst and a blank disk), placeable anywhere, no Soundforge required. Making music is a journey;
downloading it is not.

Open it to browse the library:

- **The listing is paginated** (`library.page_size` entries per page, 50 by default) with page
  controls in the screen — it stays quick however large the library grows.
- Each row shows the title, the composer (or the anonymous line), and the download count.
- **Copy**: select a row, put a **blank disk** in the blank slot, and press Copy. The server writes
  the composition onto your disk and adds one to the entry's download count. The score never
  travels to your client during browsing — copying happens entirely on the server.
- **Duplicate**: put a pressed disk in the source slot and a blank disk in the blank slot, and
  press Dupe for an exact copy (title, palette and the credit choice included). The library and its
  counts are not involved.

Operators can switch the whole machine off with `exchanger.enabled`.

## Commands

Everything the lectern and the Exchanger do is also reachable under `/neronotes library`:

| Command | Who | What |
| ------- | --- | ---- |
| `/neronotes library browse [page]` | anyone | List one page of the library in chat (pages start at 1). |
| `/neronotes library publish` | anyone | Publish the pressed disk in your main hand. |
| `/neronotes library unpublish <id>` | the composer | Remove **your own** entry from the library. |
| `/neronotes library remove <id>` | operators | Take any entry down (moderation). |
| `/neronotes library approve <id>` | operators | Make a pending entry visible (approval mode only). |

## Configuration

| Key | Default | Meaning |
| --- | ------- | ------- |
| `library.publishing_enabled` | `true` | Master toggle for publishing. |
| `library.size_cap` | `1000` | Server-wide maximum published entries. |
| `library.per_player_quota` | `25` | Maximum entries one player may hold. |
| `library.op_approval_required` | `false` | Entries stay hidden until an operator approves them. |
| `library.page_size` | `50` | Entries per library page. |
| `exchanger.enabled` | `true` | Enables the Disk Exchanger machine. |

All of these are server-authoritative.

## Privacy notes

- Download counts are aggregate only — the library never records who downloaded a composition.
- Anonymous entries expose no composer anywhere, and the choice is first-class, made at press time.
- When a composer's stored data is erased (see the data-erasure support built through the whole
  mod), their library entries are **anonymised, not deleted**: the link to the person is severed,
  the music keeps playing, and disks other players already copied keep working.
