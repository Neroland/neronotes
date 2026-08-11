package za.co.neroland.neronotes.score;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import za.co.neroland.neronotes.score.Score.Layer;
import za.co.neroland.neronotes.score.Score.Note;

/**
 * The one codec for {@link Score}: NBT for disks and SavedData, uncompressed
 * NBT bytes for size accounting and the wire. Round-trips are exact — every
 * field is an int or a string, nothing lossy.
 *
 * <p>Two policies are enforced here and nowhere else:</p>
 * <ul>
 *   <li><strong>Version:</strong> a score declaring a {@code formatVersion}
 *       newer than {@link Score#CURRENT_FORMAT_VERSION} is rejected with
 *       {@link ScoreVersionException} — never a silent partial parse.</li>
 *   <li><strong>Size:</strong> the serialised byte count is checked against a
 *       caller-supplied budget (config {@code disk.score_budget_bytes},
 *       hard ceiling {@link #HARD_BUDGET_CEILING_BYTES}). Encode refuses,
 *       decode refuses <em>before parsing</em>; nothing is truncated.</li>
 * </ul>
 *
 * <p>Plain-JVM safe: only NBT classes, no registries — usable in unit tests
 * and on both logical sides.</p>
 */
public final class ScoreCodec {

    /**
     * The absolute ceiling on a serialised score, in bytes. Mirrors the
     * configured maximum of {@code disk.score_budget_bytes} (the config can
     * lower the budget, never raise it past this). Wire payloads are bounded
     * by the same number — see {@code network/NotesNetwork}.
     */
    public static final int HARD_BUDGET_CEILING_BYTES = 65536;

    // NBT field names — short on purpose; every byte counts against the budget.
    private static final String KEY_VERSION = "v";
    private static final String KEY_TEMPO = "tempo";
    private static final String KEY_TICKS_PER_BEAT = "tpb";
    private static final String KEY_LOOP_START = "loop_start";
    private static final String KEY_LOOP_END = "loop_end";
    private static final String KEY_LAYERS = "layers";
    private static final String KEY_VOICE = "voice";
    private static final String KEY_NOTES = "notes";

    /** Ints per note in the packed {@code notes} int array. */
    private static final int INTS_PER_NOTE = 4;

    private ScoreCodec() {
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    /** Serialise a score to NBT. Notes are packed as one int array per layer. */
    public static CompoundTag toNbt(Score score) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_VERSION, score.formatVersion());
        tag.putInt(KEY_TEMPO, score.tempoBpm());
        tag.putInt(KEY_TICKS_PER_BEAT, score.ticksPerBeat());
        tag.putInt(KEY_LOOP_START, score.loopStartTick());
        tag.putInt(KEY_LOOP_END, score.loopEndTick());
        ListTag layers = new ListTag();
        for (Layer layer : score.layers()) {
            CompoundTag layerTag = new CompoundTag();
            layerTag.putString(KEY_VOICE, layer.voiceId());
            int[] packed = new int[layer.notes().size() * INTS_PER_NOTE];
            int i = 0;
            for (Note note : layer.notes()) {
                packed[i++] = note.tick();
                packed[i++] = note.pitch();
                packed[i++] = note.velocity();
                packed[i++] = note.lengthTicks();
            }
            layerTag.putIntArray(KEY_NOTES, packed);
            layers.add(layerTag);
        }
        tag.put(KEY_LAYERS, layers);
        return tag;
    }

    /**
     * Decode a score from NBT.
     *
     * @throws ScoreVersionException if the score declares a format version
     *                               newer than {@link Score#CURRENT_FORMAT_VERSION}
     * @throws ScoreFormatException  if any field is missing or out of range —
     *                               never a silent partial parse
     */
    public static Score fromNbt(CompoundTag tag) throws ScoreFormatException {
        int version = requireInt(tag, KEY_VERSION);
        if (version > Score.CURRENT_FORMAT_VERSION) {
            throw new ScoreVersionException(version, Score.CURRENT_FORMAT_VERSION);
        }
        int tempo = requireInt(tag, KEY_TEMPO);
        int ticksPerBeat = requireInt(tag, KEY_TICKS_PER_BEAT);
        int loopStart = requireInt(tag, KEY_LOOP_START);
        int loopEnd = requireInt(tag, KEY_LOOP_END);
        Optional<ListTag> layersTag = tag.getList(KEY_LAYERS);
        if (layersTag.isEmpty()) {
            throw new ScoreFormatException("score is missing the '" + KEY_LAYERS + "' list");
        }
        List<Layer> layers = new ArrayList<>(layersTag.get().size());
        for (int i = 0; i < layersTag.get().size(); i++) {
            Optional<CompoundTag> layerTag = layersTag.get().getCompound(i);
            if (layerTag.isEmpty()) {
                throw new ScoreFormatException("score layer " + i + " is not a compound tag");
            }
            layers.add(readLayer(layerTag.get(), i));
        }
        try {
            return new Score(version, tempo, ticksPerBeat, loopStart, loopEnd, layers);
        } catch (IllegalArgumentException invalid) {
            throw new ScoreFormatException("score fields out of range: " + invalid.getMessage(), invalid);
        }
    }

    private static int requireInt(CompoundTag tag, String key) throws ScoreFormatException {
        Optional<Integer> value = tag.getInt(key);
        if (value.isEmpty()) {
            throw new ScoreFormatException("score is missing the int field '" + key + "'");
        }
        return value.get();
    }

    private static Layer readLayer(CompoundTag layerTag, int index) throws ScoreFormatException {
        Optional<String> voiceId = layerTag.getString(KEY_VOICE);
        if (voiceId.isEmpty() || voiceId.get().isBlank()) {
            throw new ScoreFormatException("score layer " + index + " is missing its '" + KEY_VOICE + "' id");
        }
        Optional<int[]> packed = layerTag.getIntArray(KEY_NOTES);
        if (packed.isEmpty()) {
            throw new ScoreFormatException("score layer " + index + " is missing its '" + KEY_NOTES + "' array");
        }
        int[] ints = packed.get();
        if (ints.length % INTS_PER_NOTE != 0) {
            throw new ScoreFormatException("score layer " + index + " has a malformed note array (length "
                    + ints.length + " is not a multiple of " + INTS_PER_NOTE + ")");
        }
        List<Note> notes = new ArrayList<>(ints.length / INTS_PER_NOTE);
        try {
            for (int i = 0; i < ints.length; i += INTS_PER_NOTE) {
                notes.add(new Note(ints[i], ints[i + 1], ints[i + 2], ints[i + 3]));
            }
            return new Layer(voiceId.get(), notes);
        } catch (IllegalArgumentException invalid) {
            throw new ScoreFormatException(
                    "score layer " + index + " has an invalid note: " + invalid.getMessage(), invalid);
        }
    }

    // ------------------------------------------------------------------
    // Bytes + size budget
    // ------------------------------------------------------------------

    /** Serialise to uncompressed NBT bytes — the unit the size budget counts. */
    public static byte[] toBytes(Score score) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.write(toNbt(score), out);
        } catch (IOException impossible) {
            throw new UncheckedIOException("in-memory NBT write failed", impossible);
        }
        return bytes.toByteArray();
    }

    /**
     * Serialise with the budget enforced — the Disk Press entry point.
     *
     * @throws ScoreSizeException if the serialised score exceeds {@code budgetBytes}
     */
    public static byte[] toBytes(Score score, int budgetBytes) throws ScoreSizeException {
        byte[] data = toBytes(score);
        if (data.length > budgetBytes) {
            throw new ScoreSizeException(data.length, budgetBytes);
        }
        return data;
    }

    /** The exact serialised byte count of a score (uncompressed NBT). */
    public static int serialisedSize(Score score) {
        return toBytes(score).length;
    }

    /**
     * Assert a score fits the budget without keeping the bytes.
     *
     * @throws ScoreSizeException naming both the actual size and the limit
     */
    public static void checkBudget(Score score, int budgetBytes) throws ScoreSizeException {
        int size = serialisedSize(score);
        if (size > budgetBytes) {
            throw new ScoreSizeException(size, budgetBytes);
        }
    }

    /**
     * Decode from bytes with the budget enforced BEFORE parsing. The NBT
     * reader additionally runs under an {@link NbtAccounter} heap quota as
     * defence in depth against pathological nesting.
     *
     * @throws ScoreSizeException    if {@code data} is over budget (checked first)
     * @throws ScoreVersionException if the score declares a newer format version
     * @throws ScoreFormatException  if the bytes are not a well-formed score
     */
    public static Score fromBytes(byte[] data, int budgetBytes) throws ScoreFormatException {
        if (data.length > budgetBytes) {
            throw new ScoreSizeException(data.length, budgetBytes);
        }
        CompoundTag tag;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            tag = NbtIo.read(in, NbtAccounter.create(16L * HARD_BUDGET_CEILING_BYTES));
        } catch (IOException | RuntimeException corrupt) {
            throw new ScoreFormatException("score bytes are not well-formed NBT", corrupt);
        }
        return fromNbt(tag);
    }
}
