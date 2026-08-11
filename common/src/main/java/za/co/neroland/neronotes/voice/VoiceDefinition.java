package za.co.neroland.neronotes.voice;

import net.minecraft.resources.Identifier;

import za.co.neroland.neronotes.score.Score;

/**
 * One playable voice: id, the {@link net.minecraft.sounds.SoundEvent}
 * <em>identifier</em> it renders through, its pitch band and its
 * {@link VoiceFamily}.
 *
 * <p>Deliberately holds the sound event's {@link Identifier}, not the
 * {@code SoundEvent} itself: definitions stay plain data, usable in plain-JVM
 * tests and on the server without a live registry. Resolution to an actual
 * {@code SoundEvent} happens client-side at play time (Stage 3), which is
 * also what lets a voice pack reference any sound event — ours, vanilla's or
 * another mod's — without code.</p>
 *
 * @param voiceId      stable voice id, e.g. {@code neronotes:void_bass}
 * @param soundEventId the sound event this voice renders through
 * @param family       the voice family
 * @param minPitch     lowest playable pitch (MIDI-style, inclusive)
 * @param maxPitch     highest playable pitch (MIDI-style, inclusive)
 */
public record VoiceDefinition(String voiceId, Identifier soundEventId, VoiceFamily family,
                              int minPitch, int maxPitch) {

    public VoiceDefinition {
        if (voiceId == null || voiceId.isBlank()) {
            throw new IllegalArgumentException("voiceId must not be blank");
        }
        if (soundEventId == null) {
            throw new IllegalArgumentException("soundEventId must not be null");
        }
        if (family == null) {
            throw new IllegalArgumentException("family must not be null");
        }
        if (minPitch < Score.MIN_PITCH || maxPitch > Score.MAX_PITCH || minPitch > maxPitch) {
            throw new IllegalArgumentException("pitch band [" + minPitch + ", " + maxPitch
                    + "] must lie within [" + Score.MIN_PITCH + ", " + Score.MAX_PITCH + "] with min <= max");
        }
    }

    /** Whether a score pitch lies inside this voice's band. */
    public boolean inBand(int pitch) {
        return pitch >= minPitch && pitch <= maxPitch;
    }

    /** The nearest playable pitch: {@code pitch} clamped into the band. */
    public int clampToBand(int pitch) {
        return Math.max(minPitch, Math.min(maxPitch, pitch));
    }
}
