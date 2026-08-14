# Resonant Blocks and Resonators

NeroNotes' first in-world instruments: seven **Resonant Blocks** — matte-black blocks each tuned
to one voice family — and the **Resonator**, a disk player that performs a whole composition on a
[resonance channel](Resonance-Channels.md), in sync for every listener.

> **Placeholder art:** the block textures in this release are generated placeholders (a matte
> near-black base with a neon accent per voice family), not final art. All sounds are aliases of
> vanilla sound events — no custom audio ships yet.

## Resonant Blocks

One block per voice family, each with that family's neon accent:

| Block                       | Voice family  |
|-----------------------------|---------------|
| Deep Bass Resonant Block    | Deep Bass     |
| Sub Pad Resonant Block      | Sub Pad       |
| Low Drone Resonant Block    | Low Drone     |
| High Lead Resonant Block    | High Lead     |
| Glassy Pluck Resonant Block | Glassy Pluck  |
| Percussion Resonant Block   | Percussion    |
| Synth Texture Resonant Block| Synth Texture |

### Using them

- **Tap** (right-click) — the block plays its current note as a live, local sound heard by anyone
  in range, and its neon edge-light flares for a moment. No channel and no permission involved —
  it is exactly as public as tapping a vanilla note block.
- **Sneak-tap** — tune the block one step higher within its family's pitch band (wrapping back to
  the bottom), previewing the new note as you go.
- **Incoming resonance** — when a channel note of the block's own family plays nearby, the block
  adopts that note's pitch and flares along with the music. Your walls light up with the track.

The flare is a blockstate (`lit`), so it also emits a little real light while it burns.

## The Resonator

The Resonator is a disk player: a matte-black unit with a neon ring that stays lit while it is
playing and pulses on each burst of notes.

- **Placement** — whoever places a Resonator becomes its owner, recorded server-side. It binds to
  the owner's `base` resonance channel (created automatically if it does not exist yet).
- **Use** (right-click) — toggles play/stop of the composition it holds. Control follows the
  channel's rules: the owner, players on the channel's trust list, or an operator. Anyone else
  gets a quiet refusal message — and machines get no shortcuts: a Resonator emits with exactly
  its owner's authority.
- The [audio-spam cap](Resonance-Channels.md#the-audio-spam-cap) applies: if too many channels
  are already playing nearby, pressing play answers with a polite "too busy" message.

Resonators currently play a composition stored on the block itself; the craftable disks that
carry compositions between machines arrive in a later stage of 0.1.0.

## Synchronised playback

Two Resonators on the same channel stay audibly in step, and so do two listeners with different
connection speeds. How:

- The **server owns the timeline** — a single anchor (position + game tick) per channel.
- Each client schedules its audio against that anchor, compensating by half its measured
  round-trip time, clamped to `sync.max_latency_compensation_ms` (default 500 ms).
- If a client's playback drifts beyond `sync.drift_threshold_ms` (default 100 ms), it performs a
  **hard seek** to the correct position. Playback speed is never adjusted to catch up — a brief
  seek is far less audible than warped music.
- Late joiners and players whose chunks reload simply seek to the current position and fall in
  step.

## Client configuration

These keys in `config/neronotes.properties` are yours locally — the server never overrides them:

| Key                           | Default   | Meaning                                            |
|-------------------------------|-----------|----------------------------------------------------|
| `client.volume.deep_bass`     | 1.0 (0–1) | Volume multiplier for the Deep Bass family         |
| `client.volume.sub_pad`       | 1.0 (0–1) | Volume multiplier for the Sub Pad family           |
| `client.volume.low_drone`     | 1.0 (0–1) | Volume multiplier for the Low Drone family         |
| `client.volume.high_lead`     | 1.0 (0–1) | Volume multiplier for the High Lead family         |
| `client.volume.glassy_pluck`  | 1.0 (0–1) | Volume multiplier for the Glassy Pluck family      |
| `client.volume.percussion`    | 1.0 (0–1) | Volume multiplier for the Percussion family        |
| `client.volume.synth_texture` | 1.0 (0–1) | Volume multiplier for the Synth Texture family     |
| `client.glow_intensity`       | 1.0 (0–1) | Intensity of the neon flare particles              |
| `client.mute_other_bases`     | `false`   | Mute playback from channels owned by other players |

`client.mute_other_bases` is the comfort switch for shared servers: enable it and other players'
synced base audio goes silent for you, while your own channels keep playing. It is a local audio
preference only — it grants and removes no permissions.

Two related **server-authoritative** keys tune the sync engine for everyone:

| Key                                | Default          | Meaning                                     |
|------------------------------------|------------------|---------------------------------------------|
| `sync.drift_threshold_ms`          | 100 (20–1000)    | Drift beyond which a client hard-seeks      |
| `sync.max_latency_compensation_ms` | 500 (0–2000)     | Clamp on round-trip latency compensation    |
