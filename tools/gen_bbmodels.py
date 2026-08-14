#!/usr/bin/env python3
"""Blockbench block/item model generation for NeroNotes — currently a no-op.

The Stage 3 blocks (Resonant Blocks, Resonator) are plain full cubes whose
model JSON is hand-authored under common/src/main/resources (the multiloader
runs no datagen), so there is nothing to generate yet. The script exists so
the `genAssets` Gradle task (which runs gen_textures.py + gen_bbmodels.py)
succeeds; later stages with shaped models can fill it in. ADDITIVE contract
as with gen_textures.py: never overwrite existing assets.

Usage: python tools/gen_bbmodels.py [--multiloader]
"""

import sys


def main():
    print("gen_bbmodels: no generated Blockbench models for neronotes (block models are hand-authored); nothing to do.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
