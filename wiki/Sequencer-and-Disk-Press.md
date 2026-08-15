# The Sequencer, Custom Disks and the Disk Press

Composition in NeroNotes happens **inside the Soundforge** — walk through a charged
[Harmonic Gate](Harmonic-Gate-and-Soundforge.md) and the arrival platform now carries a full
composing studio. Everything below only works inside the Soundforge; outside it, these blocks are
decorative.

> All block textures are generated placeholders and every instrument voice aliases a vanilla sound
> event in this release.

## The Transport Lectern

The matte-black console beside the return gate. Using it opens the **sequencer**: a paged note grid
that edits your personal *session score* — a composition that belongs to you and survives trips home,
logouts and server restarts.

- **Note grid** — click a cell to place or remove a note on the active layer. Notes from your other
  layers show dimmed in their voice colours. `◀`/`▶` page through time, `▲`/`▼` shift the visible
  octave.
- **Layers** — up to **4 layers**, each with its own voice. `L1`–`L4` select the active layer,
  `+L`/`-L` add and remove layers, and the voice button cycles the active layer's voice.
- **Tempo** — `-`/`+` adjust the BPM. Any tempo plays, but Minecraft schedules audio on its 50 ms
  game-tick grid: a score tick lasts `60000 / (BPM × 4)` ms (the sequencer uses 4 score ticks per
  beat), and only tempos where that duration is a whole multiple of 50 ms land every note *exactly*
  on the grid — **75, 100, 150 and 300 BPM** are the practical tick-perfect choices (200, 150, 100
  and 50 ms per tick respectively). New sessions default to 150 BPM. Other tempos (like
  120 BPM, 125 ms per tick) still play, but each note rounds to the nearest game tick — up to
  ±50 ms, which can be audible as a subtle limp on fast material.
- **Loop** — `[` sets the loop start at the current page, `]` sets the loop end after it, `×` clears
  the loop. A looping score repeats on a Resonator; a preview always plays once through.
- **Preview** — plays your session once, in place, through your own dedicated `preview` channel —
  synchronised like any other playback, and subject to the same nearby-channel cap.
- **Size gauge** — shows the serialised size of your session against the server's disk budget, so an
  over-budget press is never a surprise.

Every edit is validated by the server. The client only proposes; the server's copy of your session
is always the real one.

## Pattern walls and voice pedestals

The platform's in-world selection surfaces, for composing without leaving the world:

- **Pattern walls** (north rim, one per layer slot) — tap one to make its layer your active layer;
  it flashes to confirm. Sneak-tap retunes the wall itself to another layer slot.
- **Voice pedestals** (south rim, one per voice family) — tap one to cycle your active layer through
  that family's voices. Sneak-tap retunes the pedestal to the next family.

## Blank disks

A **Blank Resonant Disk** is craftable in survival: copper ingots around Plasma Glass, with an
amethyst shard below (yields 2). It carries no data — it is the Disk Press's raw material.

## The Disk Press

The block on the other side of the gate. Using it opens the press screen:

1. Put a **blank disk** in the input slot.
2. Type a **title**. Titles are validated by the server: a configurable length limit
   (48 characters by default), control characters and formatting codes stripped, and an optional
   server word list.
3. Choose your **credit**: *Credited to you* (default) or *Anonymous*. Publishing anonymously is a
   first-class choice — an anonymous disk stores no display name at all, and no tooltip, label or
   listing will ever name you.
4. Press.

The pressed **Resonant Disk** takes your title as its name, coloured by the composition's dominant
voice family, with a glowing label. Its tooltip shows the composer ("By …" or "By an anonymous
composer"), the layer/note counts and the tempo.

### The size budget

A disk holds at most `disk.score_budget_bytes` of serialised score (16 KiB by default; the server
can raise it to a hard ceiling of 64 KiB). An over-budget score is **refused with a message naming
both the actual size and the limit** — the press never trims or truncates a composition.

## What a pressed disk can do

- **Play it at home** — right-click a [Resonator](Resonant-Blocks-and-Resonators.md) with the disk
  in hand to load its composition (the disk is read, not consumed), then right-click to play.
- **Publish it** — take it to the [Publish Lectern](Publishing-and-the-Disk-Exchanger.md) to share
  it in the server-wide library, where other players can copy it through the Disk Exchanger.

Still deliberately out of this release: real-audio import, `.ogg` files (all voices alias vanilla
sounds), MIDI import, and in-world note placement — the sequencer GUI at the lectern is the
composing surface, with the walls and pedestals as in-world selection aids.

## Configuration

| Key | Default | Meaning |
| --- | --- | --- |
| `disk.score_budget_bytes` | `16384` | Serialised score cap enforced by the Disk Press (ceiling 65536). |
| `disk.name_max_length` | `48` | Maximum disk title length. |
| `moderation.blocked_words` | *(empty)* | Comma-separated, case-insensitive words refused in titles. |
