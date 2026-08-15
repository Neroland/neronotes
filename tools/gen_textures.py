#!/usr/bin/env python3
"""Procedural PLACEHOLDER block textures for NeroNotes (Stages 3-4).

Every PNG this script writes is a generated placeholder, not final art: a
matte near-black base with a neon accent per voice family. Say so wherever
the art is described (changelog, wiki) until real textures replace them.

ADDITIVE by design: any texture that already exists on disk is skipped, so
reruns only fill gaps and never overwrite committed or hand-edited art.
Needs Pillow; without it the script prints a notice and exits 0 (the
`genAssets` Gradle task stays green on machines without Pillow).

Usage: python tools/gen_textures.py [--multiloader]
(The --multiloader flag is accepted for genAssets compatibility; this repo
is always multiloader, so it changes nothing.)
"""

import random
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
TEXTURES = REPO / "common" / "src" / "main" / "resources" / "assets" / "neronotes" / "textures" / "block"
ITEM_TEXTURES = REPO / "common" / "src" / "main" / "resources" / "assets" / "neronotes" / "textures" / "item"

# Neon accent per voice family (matches voice/VoiceFamily).
FAMILY_ACCENTS = {
    "deep_bass": (139, 0, 255),
    "sub_pad": (46, 107, 255),
    "low_drone": (0, 179, 164),
    "high_lead": (57, 255, 20),
    "glassy_pluck": (0, 229, 255),
    "percussion": (255, 41, 117),
    "synth_texture": (255, 140, 0),
}

RESONATOR_ACCENT = (0, 229, 255)
HARMONIC_ACCENT = (191, 64, 255)  # violet neon (Stage 4: the Harmonic Gate)
BASE = (17, 17, 22)
SIZE = 16


def scale(color, factor):
    return tuple(min(255, int(round(c * factor))) for c in color)


def matte_base(img, rng):
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            noise = rng.randint(-4, 4)
            px[x, y] = tuple(max(0, min(255, c + noise)) for c in BASE)


def edge_glow(img, accent, edge_factor, corner_factor):
    px = img.load()
    edge = scale(accent, edge_factor)
    corner = scale(accent, corner_factor)
    for i in range(SIZE):
        for x, y in ((i, 0), (i, SIZE - 1), (0, i), (SIZE - 1, i)):
            px[x, y] = edge
    for x, y in ((0, 0), (0, SIZE - 1), (SIZE - 1, 0), (SIZE - 1, SIZE - 1)):
        px[x, y] = corner


def center_dot(img, accent, factor):
    px = img.load()
    dot = scale(accent, factor)
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = dot


def resonant_block(image_module, family, accent, lit):
    rng = random.Random(hash(family) & 0xFFFF)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    if lit:
        edge_glow(img, accent, 1.0, 0.85)
        center_dot(img, accent, 0.9)
    else:
        edge_glow(img, accent, 0.28, 0.2)
        center_dot(img, accent, 0.35)
    return img


def resonator_side(image_module, ring_factor):
    rng = random.Random(0x5E50)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    ring = scale(RESONATOR_ACCENT, ring_factor)
    for x in range(SIZE):
        px[x, 6] = ring
        px[x, 9] = ring
    dim = scale(RESONATOR_ACCENT, min(1.0, ring_factor) * 0.5)
    for x in range(SIZE):
        px[x, 7] = dim
        px[x, 8] = dim
    return img


def resonator_top(image_module):
    rng = random.Random(0x70B0)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    slot = scale(RESONATOR_ACCENT, 0.55)
    for x in range(4, 12):
        px[x, 7] = slot
        px[x, 8] = slot
    return img


def resonator_bottom(image_module):
    rng = random.Random(0xB070)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    return img


def harmonic_gate_side(image_module, charged):
    rng = random.Random(0x4A7E)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    factor = 1.0 if charged else 0.35
    frame = scale(HARMONIC_ACCENT, factor)
    # The arch: two columns and a lintel.
    for y in range(3, SIZE):
        px[3, y] = frame
        px[12, y] = frame
    for x in range(3, 13):
        px[x, 3] = frame
    # A faint inner shimmer down the middle of the opening.
    inner = scale(HARMONIC_ACCENT, factor * 0.45)
    for y in range(6, 14):
        for x in (7, 8):
            px[x, y] = inner
    return img


def harmonic_gate_top(image_module):
    rng = random.Random(0x6A7E)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    edge_glow(img, HARMONIC_ACCENT, 0.5, 0.35)
    center_dot(img, HARMONIC_ACCENT, 0.8)
    return img


def harmonic_gate_bottom(image_module):
    rng = random.Random(0xB47E)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    return img


def transport_lectern_side(image_module):
    rng = random.Random(0x1EC7)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    accent = scale(HARMONIC_ACCENT, 0.85)
    dim = scale(HARMONIC_ACCENT, 0.35)
    # A console face: a bright readout strip and faint fader columns.
    for x in range(2, 14):
        px[x, 4] = accent
        px[x, 5] = dim
    for x in (3, 6, 9, 12):
        for y in range(8, 14):
            px[x, y] = dim
    return img


def transport_lectern_top(image_module):
    rng = random.Random(0x1EC8)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    edge_glow(img, HARMONIC_ACCENT, 0.4, 0.3)
    # A tick-grid: the sequencer surface in miniature.
    dim = scale(HARMONIC_ACCENT, 0.5)
    for x in range(3, 13, 3):
        for y in range(3, 13, 3):
            px[x, y] = dim
    return img


def disk_press_side(image_module):
    rng = random.Random(0xD15C)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    accent = scale(RESONATOR_ACCENT, 0.8)
    dim = scale(RESONATOR_ACCENT, 0.35)
    # A press mouth: a horizontal slot with jaws above and below.
    for x in range(2, 14):
        px[x, 7] = accent
        px[x, 8] = accent
        px[x, 5] = dim
        px[x, 10] = dim
    return img


def disk_press_top(image_module):
    rng = random.Random(0xD15D)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    edge_glow(img, RESONATOR_ACCENT, 0.4, 0.3)
    # A disk outline seen from above.
    dim = scale(RESONATOR_ACCENT, 0.55)
    for x, y in ((7, 4), (8, 4), (5, 5), (10, 5), (4, 7), (4, 8), (11, 7), (11, 8),
                 (5, 10), (10, 10), (7, 11), (8, 11)):
        px[x, y] = dim
    center_dot(img, RESONATOR_ACCENT, 0.8)
    return img


def publish_lectern_side(image_module):
    rng = random.Random(0x9B15)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    accent = scale(HARMONIC_ACCENT, 0.85)
    dim = scale(HARMONIC_ACCENT, 0.35)
    # A release desk: a bright beacon column rising from a readout strip.
    for x in range(2, 14):
        px[x, 10] = accent
        px[x, 11] = dim
    for y in range(3, 10):
        px[7, y] = dim
        px[8, y] = dim
    px[7, 3] = accent
    px[8, 3] = accent
    return img


def publish_lectern_top(image_module):
    rng = random.Random(0x9B16)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    edge_glow(img, HARMONIC_ACCENT, 0.4, 0.3)
    # An upward arrow: publishing sends the work out into the library.
    dim = scale(HARMONIC_ACCENT, 0.6)
    for x, y in ((7, 3), (8, 3), (6, 4), (9, 4), (5, 5), (10, 5)):
        px[x, y] = dim
    for y in range(4, 13):
        px[7, y] = dim
        px[8, y] = dim
    return img


def disk_exchanger_side(image_module):
    rng = random.Random(0xE8C4)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    accent = scale(RESONATOR_ACCENT, 0.8)
    dim = scale(RESONATOR_ACCENT, 0.35)
    # Two exchange slots with opposing arrows: give a blank, take a copy.
    for x in range(2, 14):
        px[x, 4] = accent
        px[x, 11] = accent
    for x, y in ((4, 6), (5, 6), (6, 6), (6, 5), (6, 7),
                 (9, 9), (10, 9), (11, 9), (9, 8), (9, 10)):
        px[x, y] = dim
    return img


def disk_exchanger_top(image_module):
    rng = random.Random(0xE8C5)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    edge_glow(img, RESONATOR_ACCENT, 0.4, 0.3)
    # Two half-discs trading places.
    dim = scale(RESONATOR_ACCENT, 0.55)
    for x, y in ((4, 4), (5, 3), (6, 3), (7, 4), (3, 5), (3, 6), (4, 7),
                 (11, 8), (12, 9), (12, 10), (11, 11), (10, 12), (9, 12), (8, 11)):
        px[x, y] = dim
    center_dot(img, RESONATOR_ACCENT, 0.8)
    return img


def pattern_wall(image_module, layer, lit):
    rng = random.Random(0x9A77 + layer)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    factor = 1.0 if lit else 0.4
    accent = scale(HARMONIC_ACCENT, factor)
    edge_glow(img, HARMONIC_ACCENT, factor * 0.5, factor * 0.35)
    # Glyph pips: layer index + 1 dots across the middle.
    pips = layer + 1
    start = 8 - pips * 2 + 1
    for p in range(pips):
        x = start + p * 4
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            px[x + dx, 7 + dy] = accent
    return img


def voice_pedestal_side(image_module):
    rng = random.Random(0x9ED5)
    img = image_module.new("RGB", (SIZE, SIZE))
    matte_base(img, rng)
    px = img.load()
    dim = scale(RESONATOR_ACCENT, 0.4)
    # A pedestal column narrowing toward the base.
    for y in range(2, 14):
        px[4, y] = dim
        px[11, y] = dim
    for x in range(4, 12):
        px[x, 2] = scale(RESONATOR_ACCENT, 0.6)
    return img


def disk_item(image_module, ring_color, ring_factor, center_factor):
    img = image_module.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    base = (24, 24, 30, 255)
    ring = tuple(list(scale(ring_color, ring_factor)) + [255])
    hub = tuple(list(scale(ring_color, center_factor)) + [255])
    # A filled disc with a coloured data ring and a hub.
    for y in range(SIZE):
        for x in range(SIZE):
            dx = x - 7.5
            dy = y - 7.5
            d2 = dx * dx + dy * dy
            if d2 <= 42:
                px[x, y] = base
            if 20 <= d2 <= 30:
                px[x, y] = ring
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = hub
    return img


def main():
    try:
        from PIL import Image
    except ImportError:
        print("gen_textures: Pillow not installed; skipping texture generation (nothing failed).")
        return 0

    TEXTURES.mkdir(parents=True, exist_ok=True)
    jobs = {}
    for family, accent in FAMILY_ACCENTS.items():
        jobs[f"resonant_block_{family}.png"] = lambda f=family, a=accent: resonant_block(Image, f, a, False)
        jobs[f"resonant_block_{family}_lit.png"] = lambda f=family, a=accent: resonant_block(Image, f, a, True)
    jobs["resonator_side.png"] = lambda: resonator_side(Image, 0.3)
    jobs["resonator_side_playing.png"] = lambda: resonator_side(Image, 0.8)
    jobs["resonator_side_pulse.png"] = lambda: resonator_side(Image, 1.0)
    jobs["resonator_top.png"] = lambda: resonator_top(Image)
    jobs["resonator_bottom.png"] = lambda: resonator_bottom(Image)
    jobs["harmonic_gate_side.png"] = lambda: harmonic_gate_side(Image, False)
    jobs["harmonic_gate_side_charged.png"] = lambda: harmonic_gate_side(Image, True)
    jobs["harmonic_gate_top.png"] = lambda: harmonic_gate_top(Image)
    jobs["harmonic_gate_bottom.png"] = lambda: harmonic_gate_bottom(Image)
    # Stage 5 — Soundforge composing furniture.
    jobs["transport_lectern_side.png"] = lambda: transport_lectern_side(Image)
    jobs["transport_lectern_top.png"] = lambda: transport_lectern_top(Image)
    jobs["disk_press_side.png"] = lambda: disk_press_side(Image)
    jobs["disk_press_top.png"] = lambda: disk_press_top(Image)
    jobs["voice_pedestal_side.png"] = lambda: voice_pedestal_side(Image)
    for layer in range(4):
        jobs[f"pattern_wall_{layer}.png"] = lambda l=layer: pattern_wall(Image, l, False)
        jobs[f"pattern_wall_{layer}_lit.png"] = lambda l=layer: pattern_wall(Image, l, True)
    # Stage 6 — publishing + the shared library.
    jobs["publish_lectern_side.png"] = lambda: publish_lectern_side(Image)
    jobs["publish_lectern_top.png"] = lambda: publish_lectern_top(Image)
    jobs["disk_exchanger_side.png"] = lambda: disk_exchanger_side(Image)
    jobs["disk_exchanger_top.png"] = lambda: disk_exchanger_top(Image)

    # Stage 5 — disk item textures (RGBA, transparent background).
    ITEM_TEXTURES.mkdir(parents=True, exist_ok=True)
    item_jobs = {
        "blank_disk.png": lambda: disk_item(Image, (140, 150, 165), 0.6, 0.4),
        "custom_disk.png": lambda: disk_item(Image, RESONATOR_ACCENT, 1.0, 0.85),
    }

    created = skipped = 0
    for name, build in sorted(jobs.items()):
        target = TEXTURES / name
        if target.exists():
            skipped += 1
            continue  # additive: never overwrite existing/committed art
        build().save(target)
        created += 1
        print(f"gen_textures: wrote {target.relative_to(REPO)}")
    for name, build in sorted(item_jobs.items()):
        target = ITEM_TEXTURES / name
        if target.exists():
            skipped += 1
            continue  # additive: never overwrite existing/committed art
        build().save(target)
        created += 1
        print(f"gen_textures: wrote {target.relative_to(REPO)}")
    print(f"gen_textures: {created} placeholder texture(s) created, {skipped} existing skipped.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
