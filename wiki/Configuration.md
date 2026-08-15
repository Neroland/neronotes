# Configuration

All NeroNotes settings live in one file, `config/neronotes.properties`, managed through Neroland
Core's config framework (validated, hot-reloadable, synced).

**Server-authoritative vs. client-local.** Every gameplay key is **server-authoritative**: in
multiplayer the server's values win, and clients never decide what they may play, publish or own.
The only **client-local** keys are the four audio-comfort/privacy ones at the bottom — those are
yours on your own machine and the server never overrides them.

## Server-authoritative keys

### The resonance signal

| Key | Default (range) | Meaning |
| --- | --- | --- |
| `signal.emit_range_blocks` | `64` (16–128) | Range in blocks within which emitters broadcast resonance (note/transport) events. |
| `signal.max_playing_channels_per_chunk_radius` | `3` (1–16) | Audio-spam cap: concurrently playing channels per chunk radius; further play requests are quietly refused. |

### Synchronised playback

| Key | Default (range) | Meaning |
| --- | --- | --- |
| `sync.drift_threshold_ms` | `100` (20–1000) | Measured playback drift beyond which a client hard-seeks to the server anchor. Playback rate is never adjusted. |
| `sync.max_latency_compensation_ms` | `500` (0–2000) | Clamp on the latency compensation applied when scheduling against the server timeline anchor. |

### The Harmonic Gate

| Key | Default (range) | Meaning |
| --- | --- | --- |
| `gate.energy_capacity` | `16000` (1000–1000000) | Energy buffer capacity (NE) of the Harmonic Gate. Applies to gates placed after a change. |
| `gate.teleport_energy_cost` | `8000` (0–1000000) | Energy one crossing **into** the Soundforge consumes. Returning is always free. Set it to `0` to make entry free too — the energy charge is the Soundforge's only requirement (NeroNotes has no progression gates). |

### Disks and moderation

| Key | Default (range) | Meaning |
| --- | --- | --- |
| `disk.score_budget_bytes` | `16384` (1024–65536) | Hard cap in bytes on a serialised score, enforced at press time and on the wire. Over-budget scores are refused with both byte counts named — never truncated. |
| `disk.name_max_length` | `48` (8–128) | Maximum length of a player-chosen disk/composition title, validated server-side at press and publish time. |
| `moderation.blocked_words` | *(empty)* | Comma-separated, case-insensitive words refused in player-chosen titles. Empty disables the list. |

### The shared library and the Disk Exchanger

| Key | Default (range) | Meaning |
| --- | --- | --- |
| `library.publishing_enabled` | `true` | Whether players may publish compositions to the shared library at all. |
| `library.size_cap` | `1000` (10–100000) | Maximum number of published entries the library holds server-wide. |
| `library.per_player_quota` | `25` (1–1000) | Maximum published entries a single player may hold. |
| `library.op_approval_required` | `false` | If true, a published disk only becomes visible after an operator runs `/neronotes library approve <id>`. |
| `library.page_size` | `50` (10–100) | Page size for library listings (paginated from the first release). |
| `exchanger.enabled` | `true` | Whether the Disk Exchanger machine is enabled. |

### Data protection

| Key | Default (range) | Meaning |
| --- | --- | --- |
| `data.retention_days` | `365` (0–3650) | Days of player inactivity after which NeroNotes purges that player's stored data (authorship severed, sessions, channels). `0` disables the automatic sweep. |
| `data.action_logging_enabled` | `false` | Whether non-essential action logging (press/publish/download events) is enabled. Player-authored strings are never logged at info level regardless. |

### Companion module

| Key | Default | Meaning |
| --- | --- | --- |
| `link.module_enabled` | `true` | Whether NeroNotes registers its [companion (NeroLink) module](Companion-Link-Module.md). When false, companion clients see no NeroNotes data and can perform no NeroNotes actions. |

## Client-local keys

Exactly four kinds, and no more — audio comfort and the telemetry opt-out:

| Key | Default (range) | Meaning |
| --- | --- | --- |
| `client.telemetry_opt_out` | `false` | Set `true` to opt out of anonymous error reporting entirely. See [Privacy and your data](Privacy-and-Your-Data.md). |
| `client.volume.deep_bass` | `1.0` (0–1) | Volume multiplier for the Deep Bass voice family. |
| `client.volume.sub_pad` | `1.0` (0–1) | Volume multiplier for the Sub Pad voice family. |
| `client.volume.low_drone` | `1.0` (0–1) | Volume multiplier for the Low Drone voice family. |
| `client.volume.high_lead` | `1.0` (0–1) | Volume multiplier for the High Lead voice family. |
| `client.volume.glassy_pluck` | `1.0` (0–1) | Volume multiplier for the Glassy Pluck voice family. |
| `client.volume.percussion` | `1.0` (0–1) | Volume multiplier for the Percussion voice family. |
| `client.volume.synth_texture` | `1.0` (0–1) | Volume multiplier for the Synth Texture voice family. |
| `client.glow_intensity` | `1.0` (0–1) | Intensity of the neon edge-light flare on Resonant Blocks and Resonators. |
| `client.mute_other_bases` | `false` | Mute playback from channels owned by other players — the shared-server comfort switch. A local audio preference only; it grants and removes no permissions. |
