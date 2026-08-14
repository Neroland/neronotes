package za.co.neroland.neronotes.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.voice.VoiceFamily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The disk component round-trips exactly through its codec, keeps the author
 * UUID even when anonymous (erasure needs it), and rejects unreadable scores
 * rather than guessing.
 */
class DiskContentsCodecTest {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static Score score() {
        return new Score(Score.CURRENT_FORMAT_VERSION, 96, 4, 0, 8, List.of(
                new Score.Layer("neronotes:crystal_pluck", List.of(
                        new Score.Note(0, 70, 100, 2), new Score.Note(4, 74, 90, 2)))));
    }

    @Test
    void creditedDiskRoundTripsExactly() {
        DiskContents original = new DiskContents(score(), "Aurora", AUTHOR, "Dario",
                false, VoiceFamily.GLASSY_PLUCK.id());
        Tag encoded = DiskContents.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        DiskContents decoded = DiskContents.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(original, decoded);
        assertEquals(Optional.of("Dario"), decoded.authorDisplay());
        assertEquals(VoiceFamily.GLASSY_PLUCK, decoded.family());
    }

    @Test
    void anonymousDiskRoundTripsWithUuidButWithoutAName() {
        DiskContents original = new DiskContents(score(), "Nameless", AUTHOR, "",
                true, VoiceFamily.GLASSY_PLUCK.id());
        Tag encoded = DiskContents.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        DiskContents decoded = DiskContents.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        // The UUID survives (erasure keys on it) ...
        assertEquals(AUTHOR, decoded.author());
        assertTrue(decoded.anonymous());
        // ... but no display name exists anywhere in the component ...
        assertEquals("", decoded.authorName());
        assertEquals(Optional.empty(), decoded.authorDisplay());
        // ... and the stored form carries no author_name value either.
        String serialised = encoded.toString();
        assertFalse(serialised.contains("Dario"), "no display name in the stored component");
    }

    @Test
    void unreadableScoreIsRejectedNotGuessed() {
        CompoundTag bogus = new CompoundTag();
        bogus.putString("not", "a score");
        DataResult<Score> parsed = DiskContents.SCORE_CODEC.parse(NbtOps.INSTANCE, bogus);
        assertTrue(parsed.isError(), "a malformed score must be a codec error, never a partial parse");
    }

    @Test
    void hostileTitleIsClampedDefensively() {
        String enormous = "x".repeat(DiskNames.HARD_MAX_LENGTH * 2);
        DiskContents contents = new DiskContents(score(), enormous, AUTHOR, "", false,
                VoiceFamily.GLASSY_PLUCK.id());
        assertEquals(DiskNames.HARD_MAX_LENGTH, contents.title().length());
    }
}
