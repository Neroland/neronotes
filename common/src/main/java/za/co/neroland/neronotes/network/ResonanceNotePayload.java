package za.co.neroland.neronotes.network;

import java.util.UUID;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.signal.ChannelNames;

/**
 * Server → client: a single {@code note_on} / {@code note_off} resonance
 * event on a channel. <strong>Deliberately tiny</strong> — a few dozen bytes.
 * Note events NEVER carry a score; whole scores travel only through the
 * budget-guarded score payloads (see {@link NotesNetwork#decodeScoreFromWire})
 * in later stages.
 *
 * <p>The channel is identified by {@code (owner, channelName)}; the dimension
 * is implicit — the server only ever sends this to subscribed players in the
 * emitting dimension within {@code signal.emit_range_blocks}. All string
 * fields are length-capped in the codec AND validated in the constructor, so
 * a malicious peer cannot inflate the payload.</p>
 *
 * <p>Stage 3 additions: {@code origin} (the emitting block, for positional
 * audio and the neon flare) and {@code scoreTick} — the note's timeline
 * position in score ticks, or {@code -1} for a live note with no timeline
 * (a Resonant Block tap). Timeline notes are what the client playhead
 * schedules against the server anchor; live notes always play immediately.</p>
 *
 * @param owner       channel owner (part of the channel identity)
 * @param channelName channel display name (≤ {@link ChannelNames#MAX_LENGTH})
 * @param noteOn      true = {@code note_on}, false = {@code note_off}
 * @param voiceId     voice to render with (resolved client-side through the
 *                    voice registry, which falls back on unknown ids)
 * @param pitch       MIDI-style note number 0–127
 * @param velocity    0–127
 * @param origin      position of the emitting block
 * @param scoreTick   timeline position in score ticks, or {@code -1} (live)
 */
public record ResonanceNotePayload(UUID owner, String channelName, boolean noteOn,
                                   String voiceId, int pitch, int velocity,
                                   BlockPos origin, long scoreTick)
        implements CustomPacketPayload {

    /** Wire cap on a voice id (registry ids like {@code neronotes:void_bass}). */
    public static final int MAX_VOICE_ID_LENGTH = 64;

    /** The {@code scoreTick} value meaning "live note, no timeline position". */
    public static final long LIVE_NOTE = -1L;

    public static final CustomPacketPayload.Type<ResonanceNotePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(
                    NotesNetwork.CHANNEL_NAMESPACE, "resonance_note"));

    private static final StreamCodec<ByteBuf, String> CHANNEL_NAME_CODEC =
            ByteBufCodecs.stringUtf8(ChannelNames.MAX_LENGTH);
    private static final StreamCodec<ByteBuf, String> VOICE_ID_CODEC =
            ByteBufCodecs.stringUtf8(MAX_VOICE_ID_LENGTH);

    /**
     * ByteBuf-based so plain-JVM tests can round-trip it; every element codec
     * is bounded (capped strings, var-ints), keeping the whole payload tiny.
     * Hand-rolled rather than {@code composite} — eight fields, one obvious
     * ordering, no arity games.
     */
    public static final StreamCodec<ByteBuf, ResonanceNotePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, payload.owner());
                CHANNEL_NAME_CODEC.encode(buf, payload.channelName());
                ByteBufCodecs.BOOL.encode(buf, payload.noteOn());
                VOICE_ID_CODEC.encode(buf, payload.voiceId());
                ByteBufCodecs.VAR_INT.encode(buf, payload.pitch());
                ByteBufCodecs.VAR_INT.encode(buf, payload.velocity());
                buf.writeLong(payload.origin().asLong());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.scoreTick() + 1); // -1 (live) encodes as 0
            },
            buf -> {
                UUID owner = UUIDUtil.STREAM_CODEC.decode(buf);
                String channelName = CHANNEL_NAME_CODEC.decode(buf);
                boolean noteOn = ByteBufCodecs.BOOL.decode(buf);
                String voiceId = VOICE_ID_CODEC.decode(buf);
                int pitch = ByteBufCodecs.VAR_INT.decode(buf);
                int velocity = ByteBufCodecs.VAR_INT.decode(buf);
                BlockPos origin = BlockPos.of(buf.readLong());
                long scoreTick = ByteBufCodecs.VAR_LONG.decode(buf) - 1;
                return new ResonanceNotePayload(owner, channelName, noteOn, voiceId,
                        pitch, velocity, origin, scoreTick);
            });

    public ResonanceNotePayload {
        if (owner == null) {
            throw new IllegalArgumentException("owner must not be null");
        }
        if (channelName == null || channelName.isBlank() || channelName.length() > ChannelNames.MAX_LENGTH) {
            throw new IllegalArgumentException("channelName must be 1.." + ChannelNames.MAX_LENGTH + " characters");
        }
        if (voiceId == null || voiceId.isBlank() || voiceId.length() > MAX_VOICE_ID_LENGTH) {
            throw new IllegalArgumentException("voiceId must be 1.." + MAX_VOICE_ID_LENGTH + " characters");
        }
        if (pitch < Score.MIN_PITCH || pitch > Score.MAX_PITCH) {
            throw new IllegalArgumentException("pitch (" + pitch + ") out of range 0..127");
        }
        if (velocity < Score.MIN_VELOCITY || velocity > Score.MAX_VELOCITY) {
            throw new IllegalArgumentException("velocity (" + velocity + ") out of range 0..127");
        }
        if (origin == null) {
            throw new IllegalArgumentException("origin must not be null");
        }
        if (scoreTick < LIVE_NOTE) {
            throw new IllegalArgumentException("scoreTick (" + scoreTick + ") must be >= -1 (live)");
        }
    }

    /** Whether this is a live note with no timeline position (always plays immediately). */
    public boolean isLive() {
        return scoreTick == LIVE_NOTE;
    }

    @Override
    public CustomPacketPayload.Type<ResonanceNotePayload> type() {
        return TYPE;
    }
}
