package za.co.neroland.neronotes.voice;

import java.util.Locale;
import java.util.Optional;

/**
 * The seven NeroNotes voice families. A family is a timbre role, not a single
 * sound: every {@link VoiceDefinition} belongs to exactly one family, the
 * client volume keys ({@code client.volume.<family>}) are per-family, and
 * Resonant Blocks are tuned per-family in Stage 3.
 *
 * <p>The set is fixed for 0.1.0 — voice <em>packs</em> add voices within
 * these families (data-driven, no code), they do not add families.</p>
 */
public enum VoiceFamily {

    DEEP_BASS(0xFF8B00FF),
    SUB_PAD(0xFF2E6BFF),
    LOW_DRONE(0xFF00B3A4),
    HIGH_LEAD(0xFF39FF14),
    GLASSY_PLUCK(0xFF00E5FF),
    PERCUSSION(0xFFFF2975),
    SYNTH_TEXTURE(0xFFFF8C00);

    private final String id = name().toLowerCase(Locale.ROOT);
    private final int accentColour;

    VoiceFamily(int accentColour) {
        this.accentColour = accentColour;
    }

    /**
     * The family's neon accent colour (ARGB) — the same palette the generated
     * placeholder textures use ({@code tools/gen_textures.py}). Stage 5 styles
     * disk labels and the sequencer grid with it.
     */
    public int accentColour() {
        return accentColour;
    }

    /** Stable snake_case id, as used in voice JSON and config keys ({@code deep_bass}, ...). */
    public String id() {
        return id;
    }

    /** Translation key for the family display name ({@code neronotes.voice_family.<id>}). */
    public String translationKey() {
        return "neronotes.voice_family." + id;
    }

    /** Resolve a family from its snake_case id; empty for unknown ids. */
    public static Optional<VoiceFamily> byId(String id) {
        for (VoiceFamily family : values()) {
            if (family.id.equals(id)) {
                return Optional.of(family);
            }
        }
        return Optional.empty();
    }
}
