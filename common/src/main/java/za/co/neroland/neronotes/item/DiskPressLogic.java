package za.co.neroland.neronotes.item;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import za.co.neroland.neronotes.score.Score;
import za.co.neroland.neronotes.score.ScoreCodec;
import za.co.neroland.neronotes.score.ScoreSizeException;
import za.co.neroland.neronotes.voice.VoiceFamily;

/**
 * The pure Disk Press decision: given a session score and the player's press
 * choices, either produce the {@link DiskContents} for the new disk or refuse
 * with a named reason. Plain-JVM testable — the menu/network layer around it
 * only moves items and translates messages.
 *
 * <p>Two policies live here (locked decisions 5 and 6):</p>
 * <ul>
 *   <li><strong>The size budget is enforced via
 *       {@link ScoreCodec#toBytes(Score, int)}</strong> — an over-budget
 *       score is refused with both the actual and the budget byte counts for
 *       the translated message. Nothing is ever truncated.</li>
 *   <li><strong>Name validation</strong> via {@link DiskNames} (length cap,
 *       control/formatting strip, blocked-word list).</li>
 *   <li><strong>Anonymous is a first-class choice</strong>: when chosen, no
 *       display name enters the contents at all; the author UUID stays (for
 *       erasure), never for display.</li>
 * </ul>
 */
public final class DiskPressLogic {

    /** Why a press was refused. */
    public enum ErrorKind {
        NONE,
        /** The session score has no notes — nothing to press. */
        EMPTY_SCORE,
        /** Name refused (see the carried {@link DiskNames.Status}). */
        BAD_NAME,
        /** The serialised score exceeds the configured budget. */
        OVER_BUDGET
    }

    /**
     * The press outcome. On success {@link #contents} is set and
     * {@link #sizeBytes} is the disk's serialised score size; on
     * {@link ErrorKind#OVER_BUDGET} both byte counts are set for the message;
     * on {@link ErrorKind#BAD_NAME} {@link #nameStatus} says why.
     */
    public record Result(ErrorKind error, DiskContents contents, long sizeBytes, int budgetBytes,
                         DiskNames.Status nameStatus) {

        public boolean ok() {
            return error == ErrorKind.NONE;
        }

        static Result success(DiskContents contents, long sizeBytes, int budgetBytes) {
            return new Result(ErrorKind.NONE, contents, sizeBytes, budgetBytes, DiskNames.Status.OK);
        }

        static Result overBudget(long actualBytes, int budgetBytes) {
            return new Result(ErrorKind.OVER_BUDGET, null, actualBytes, budgetBytes, DiskNames.Status.OK);
        }

        static Result badName(DiskNames.Status status) {
            return new Result(ErrorKind.BAD_NAME, null, 0, 0, status);
        }

        static Result emptyScore() {
            return new Result(ErrorKind.EMPTY_SCORE, null, 0, 0, DiskNames.Status.OK);
        }
    }

    private DiskPressLogic() {
    }

    /**
     * Decide a press.
     *
     * @param score          the session score to press
     * @param rawTitle       the player-typed title (validated here, server-side)
     * @param anonymous      the player's attribution choice (opt-out of credit)
     * @param author         the composer's UUID (server identity, never client-asserted)
     * @param authorName     the composer's display name (ignored when anonymous)
     * @param budgetBytes    the configured score budget ({@code disk.score_budget_bytes})
     * @param nameMaxLength  the configured name cap ({@code disk.name_max_length})
     * @param blockedWords   the configured word list
     * @param familyResolver voice id → family (the voice registry in production;
     *                       a stub in tests) — empty for unknown voices
     */
    public static Result press(Score score, String rawTitle, boolean anonymous,
                               UUID author, String authorName,
                               int budgetBytes, int nameMaxLength, List<String> blockedWords,
                               Function<String, Optional<VoiceFamily>> familyResolver) {
        if (score == null || score.noteCount() == 0) {
            return Result.emptyScore();
        }
        DiskNames.Result name = DiskNames.clean(rawTitle, nameMaxLength, blockedWords);
        if (!name.ok()) {
            return Result.badName(name.status());
        }
        long sizeBytes;
        try {
            sizeBytes = ScoreCodec.toBytes(score, budgetBytes).length;
        } catch (ScoreSizeException overBudget) {
            // Refuse, never truncate — carry both numbers for the translated message.
            return Result.overBudget(overBudget.actualBytes(), overBudget.budgetBytes());
        }
        DiskContents contents = new DiskContents(score, name.name(), author,
                anonymous ? "" : authorName, anonymous, dominantFamily(score, familyResolver).id());
        return Result.success(contents, sizeBytes, budgetBytes);
    }

    /** The family with the most notes across the score's layers (ties: first seen; no notes: fallback). */
    public static VoiceFamily dominantFamily(Score score,
                                             Function<String, Optional<VoiceFamily>> familyResolver) {
        Map<VoiceFamily, Integer> counts = new EnumMap<>(VoiceFamily.class);
        for (Score.Layer layer : score.layers()) {
            Optional<VoiceFamily> family = familyResolver.apply(layer.voiceId());
            if (family.isPresent() && !layer.notes().isEmpty()) {
                counts.merge(family.get(), layer.notes().size(), Integer::sum);
            }
        }
        VoiceFamily best = VoiceFamily.HIGH_LEAD;
        int bestCount = 0;
        for (VoiceFamily family : VoiceFamily.values()) {
            int count = counts.getOrDefault(family, 0);
            if (count > bestCount) {
                best = family;
                bestCount = count;
            }
        }
        return best;
    }
}
