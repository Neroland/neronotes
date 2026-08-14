package za.co.neroland.neronotes.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.score.ScoreFormatException;

/**
 * NeroNotes' own network channel: {@code neronotes:main}.
 *
 * <p>All payload types are registered on this channel by the per-loader
 * network implementations. Nothing is ever registered into Core's
 * {@code za.co.neroland.nerolandcore.network.CoreNetwork} — its payload
 * lists are drained during Core's own bootstrap, so downstream registrations
 * there are silently dropped.</p>
 *
 * <p>The payload set is declared once, here, at init step 8
 * ({@link #registerPayloads()}); each loader then wires the SAME declarations
 * into its own channel plumbing (Fabric {@code PayloadTypeRegistry} +
 * {@code ClientPlayNetworking}, NeoForge {@code PayloadRegistrar}, Forge
 * {@code ChannelBuilder}) so the wire format never drifts between loaders.
 * Stage 2 ships the first real payloads — the clientbound resonance note and
 * transport events, which are tiny by design. Any payload carrying a
 * <em>score</em> (later stages) must be bounded by the same size budget as
 * the disk and decode through {@link #decodeScoreFromWire(byte[])}.</p>
 */
public final class NotesNetwork {

    /** Channel namespace ({@code neronotes}). */
    public static final String CHANNEL_NAMESPACE = NeroNotesCommon.MOD_ID;
    /** Channel path ({@code main}). */
    public static final String CHANNEL_PATH = "main";
    /** The full channel id: {@code neronotes:main}. */
    public static final String CHANNEL_ID = CHANNEL_NAMESPACE + ":" + CHANNEL_PATH;

    /**
     * Hard bound on any score carried in a payload, in serialised bytes —
     * <strong>the same cap as the disk budget</strong>
     * ({@code disk.score_budget_bytes} config ceiling,
     * {@link ScoreCodec#HARD_BUDGET_CEILING_BYTES}). An unbounded score
     * payload is a server-crash vector: every score-carrying payload MUST
     * decode through {@link #decodeScoreFromWire(byte[])}, which enforces
     * this bound BEFORE parsing.
     */
    public static final int MAX_SCORE_PAYLOAD_BYTES = ScoreCodec.HARD_BUDGET_CEILING_BYTES;

    private NotesNetwork() {
    }

    /**
     * The single decode entry point for score bytes arriving off the wire.
     * Enforces {@link #MAX_SCORE_PAYLOAD_BYTES} before any NBT parsing and
     * never partially parses — see {@link ScoreCodec#fromBytes(byte[], int)}.
     * Stage 2+ payload codecs must route through here.
     *
     * @throws ScoreFormatException over-budget, newer-format or corrupt data
     */
    public static Score decodeScoreFromWire(byte[] data) throws ScoreFormatException {
        return ScoreCodec.fromBytes(data, MAX_SCORE_PAYLOAD_BYTES);
    }

    /**
     * A clientbound payload declaration: wire type + codec + the common
     * handler the loader receivers invoke on the client main thread. The
     * codec is {@link ByteBuf}-based so it is plain-JVM testable and adapts
     * to every loader's buffer generics.
     */
    public record ClientboundPayloadSpec<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<ByteBuf, T> codec,
            Consumer<T> handler) {
    }

    /**
     * A serverbound payload declaration: wire type + codec + the common
     * handler the loader receivers invoke <strong>on the server thread</strong>
     * with the sending player's server-side identity — the handler trusts the
     * {@link ServerPlayer}, never anything inside the payload.
     */
    public record ServerboundPayloadSpec<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<ByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
    }

    private static final List<ClientboundPayloadSpec<?>> CLIENTBOUND = new ArrayList<>();
    private static final List<ServerboundPayloadSpec<?>> SERVERBOUND = new ArrayList<>();
    private static boolean declared;

    /**
     * Payload-declaration seam — step 8 of {@code NeroNotesCommon.init()}.
     * Declares once; per-loader plumbing reads {@link #clientboundPayloads()}
     * and {@link #serverboundPayloads()} afterwards (all three loader entry
     * points run after {@code NeroNotesCommon.init()}), so ordering is
     * guaranteed. Stage 2: the two clientbound resonance payloads. Stage 5:
     * the sequencer session sync (clientbound, score-carrying and budget-
     * bounded) plus the first serverbound payloads — sequencer edits and the
     * Disk Press request.
     */
    public static synchronized void registerPayloads() {
        if (declared) {
            return;
        }
        declared = true;
        CLIENTBOUND.add(new ClientboundPayloadSpec<>(
                ResonanceNotePayload.TYPE, ResonanceNotePayload.STREAM_CODEC,
                ResonanceClientHandlers::handleNote));
        CLIENTBOUND.add(new ClientboundPayloadSpec<>(
                ResonanceTransportPayload.TYPE, ResonanceTransportPayload.STREAM_CODEC,
                ResonanceClientHandlers::handleTransport));
        CLIENTBOUND.add(new ClientboundPayloadSpec<>(
                SessionScorePayload.TYPE, SessionScorePayload.STREAM_CODEC,
                SequencerClientHandlers::handleSession));
        SERVERBOUND.add(new ServerboundPayloadSpec<>(
                SequencerEditPayload.TYPE, SequencerEditPayload.STREAM_CODEC,
                SequencerServerHandlers::handleEdit));
        SERVERBOUND.add(new ServerboundPayloadSpec<>(
                DiskPressPayload.TYPE, DiskPressPayload.STREAM_CODEC,
                SequencerServerHandlers::handlePress));
        NeroNotesCommon.LOGGER.debug(
                "[NeroNotes] network channel {} declared {} clientbound / {} serverbound payload(s)",
                CHANNEL_ID, CLIENTBOUND.size(), SERVERBOUND.size());
    }

    /** The declared clientbound payloads, for the per-loader channel wiring. */
    public static synchronized List<ClientboundPayloadSpec<?>> clientboundPayloads() {
        return List.copyOf(CLIENTBOUND);
    }

    /** The declared serverbound payloads, for the per-loader channel wiring. */
    public static synchronized List<ServerboundPayloadSpec<?>> serverboundPayloads() {
        return List.copyOf(SERVERBOUND);
    }
}
