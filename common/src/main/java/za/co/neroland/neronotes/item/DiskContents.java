package za.co.neroland.neronotes.item;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.score.ScoreFormatException;
import za.co.neroland.neronotes.voice.VoiceFamily;

/**
 * The data component a pressed custom disk carries: the score, the validated
 * title, the authorship record, and the voice-palette family that styles the
 * label. Registered as {@code neronotes:disk_contents} in
 * {@link NeroNotesDataComponents}.
 *
 * <p><strong>Anonymous authorship is a first-class state</strong> (Stage 5,
 * locked decision 6 + POPIA/GDPR): the author UUID is <em>always</em> stored
 * — it is what Core's data-erasure hook keys on — but when {@code anonymous}
 * is set, no display name is stored at all and {@link #authorDisplay()} is
 * empty, so no client-visible surface (tooltip, label, later the library) can
 * name the author. The compact constructor enforces that invariant.</p>
 *
 * <p>Plain-JVM safe: codecs and NBT only, no registries — directly
 * unit-testable.</p>
 *
 * @param score      the pressed score (already within the disk budget)
 * @param title      the validated, player-chosen title
 * @param author     the composer's UUID (stored even when anonymous — erasure needs it)
 * @param authorName the composer's display name; {@code ""} when anonymous
 * @param anonymous  whether the composer chose to publish anonymously
 * @param familyId   the dominant {@link VoiceFamily} id styling the label
 */
public record DiskContents(Score score, String title, UUID author, String authorName,
                           boolean anonymous, String familyId) {

    /** Score ⇄ NBT, refusing unreadable/newer scores with a codec error (never a throw). */
    public static final Codec<Score> SCORE_CODEC = CompoundTag.CODEC.comapFlatMap(
            tag -> {
                try {
                    return DataResult.success(ScoreCodec.fromNbt(tag));
                } catch (ScoreFormatException unreadable) {
                    return DataResult.error(() -> "unreadable disk score: " + unreadable.getMessage());
                }
            },
            ScoreCodec::toNbt);

    public static final Codec<DiskContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SCORE_CODEC.fieldOf("score").forGetter(DiskContents::score),
            Codec.STRING.fieldOf("title").forGetter(DiskContents::title),
            UUIDUtil.CODEC.fieldOf("author").forGetter(DiskContents::author),
            Codec.STRING.optionalFieldOf("author_name", "").forGetter(DiskContents::authorName),
            Codec.BOOL.optionalFieldOf("anonymous", false).forGetter(DiskContents::anonymous),
            Codec.STRING.optionalFieldOf("family", VoiceFamily.HIGH_LEAD.id()).forGetter(DiskContents::familyId)
    ).apply(instance, DiskContents::new));

    public DiskContents {
        if (score == null || author == null) {
            throw new IllegalArgumentException("score and author must not be null");
        }
        if (title == null) {
            title = "";
        }
        if (title.length() > DiskNames.HARD_MAX_LENGTH) {
            title = title.substring(0, DiskNames.HARD_MAX_LENGTH); // defensive vs hostile stack NBT
        }
        // The anonymity invariant: an anonymous disk stores NO display name.
        authorName = anonymous || authorName == null ? "" : authorName;
        familyId = familyId == null || familyId.isBlank() ? VoiceFamily.HIGH_LEAD.id() : familyId;
    }

    /**
     * The client-visible author, if any. Empty for an anonymous disk — the
     * caller shows the translated "Anonymous" line instead. The UUID is never
     * part of any display surface.
     */
    public Optional<String> authorDisplay() {
        return anonymous || authorName.isBlank() ? Optional.empty() : Optional.of(authorName);
    }

    /** The label's voice family (palette), with a defined fallback. */
    public VoiceFamily family() {
        return VoiceFamily.byId(familyId).orElse(VoiceFamily.HIGH_LEAD);
    }
}
