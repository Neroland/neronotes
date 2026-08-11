package za.co.neroland.neronotes.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * NeroNotes' registered {@link SoundEvent}s — one per built-in voice, via
 * Core's {@link RegistrationProvider} (the loader entry points call
 * {@code RegistrationProvider.attach(bus)} on NeoForge/Forge; Fabric
 * registers eagerly when {@link #init()} class-loads this).
 *
 * <p><strong>0.1.0 ships no {@code .ogg} files.</strong> Every event is
 * aliased to a <em>vanilla</em> sound in
 * {@code assets/neronotes/sounds.json} ({@code "type": "event"}), so real
 * audio later is a pure resource change and the jar stays small. The mapping
 * from voice ids to these events is data-driven in
 * {@code assets/neronotes/voices/default.json} — see
 * {@code voice/VoiceRegistry}.</p>
 */
public final class NeroNotesSounds {

    private static final RegistrationProvider<SoundEvent> SOUNDS =
            RegistrationProvider.get(Registries.SOUND_EVENT, NeroNotesCommon.MOD_ID);

    // One event per built-in voice; family in the trailing comment.
    public static final RegistrationProvider.RegistryEntry<SoundEvent> VOICE_VOID_BASS = voice("voice.void_bass");           // deep bass
    public static final RegistrationProvider.RegistryEntry<SoundEvent> VOICE_GRAV_PAD = voice("voice.grav_pad");             // sub pad
    public static final RegistrationProvider.RegistryEntry<SoundEvent> VOICE_REACTOR_DRONE = voice("voice.reactor_drone");   // low drone
    public static final RegistrationProvider.RegistryEntry<SoundEvent> VOICE_PULSE_LEAD = voice("voice.pulse_lead");         // high lead (also the fallback voice's event)
    public static final RegistrationProvider.RegistryEntry<SoundEvent> VOICE_CRYSTAL_PLUCK = voice("voice.crystal_pluck");   // glassy pluck
    public static final RegistrationProvider.RegistryEntry<SoundEvent> VOICE_PLASMA_KICK = voice("voice.plasma_kick");       // percussion
    public static final RegistrationProvider.RegistryEntry<SoundEvent> VOICE_NEBULA_TEXTURE = voice("voice.nebula_texture"); // synth texture

    private NeroNotesSounds() {
    }

    private static RegistrationProvider.RegistryEntry<SoundEvent> voice(String path) {
        return SOUNDS.register(path, key -> SoundEvent.createVariableRangeEvent(key.identifier()));
    }

    /**
     * Class-load hook — step 5 of {@code NeroNotesCommon.init()}. The static
     * initialisers above queue every event with the provider; Fabric
     * registers immediately, NeoForge/Forge drain on their registry events.
     */
    public static void init() {
        NeroNotesCommon.LOGGER.debug("[NeroNotes] sound events queued for registration");
    }
}
