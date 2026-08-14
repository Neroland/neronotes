package za.co.neroland.neronotes.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.signal.ChannelNames;

/**
 * Server-side validation for player-chosen disk/composition names — locked
 * design decision 6, the same discipline as {@link ChannelNames} but with the
 * length cap taken from config ({@code disk.name_max_length}) and a
 * configurable case-insensitive word list ({@code moderation.blocked_words}).
 *
 * <p>Runs <strong>server-side at press time</strong> (and again at publish
 * time in a later stage). Control characters and legacy formatting codes are
 * stripped rather than refused; emptiness, length and the word list refuse.
 * A disk name is player-authored free text: it is never logged at info level
 * and never sent to telemetry.</p>
 */
public final class DiskNames {

    /**
     * The absolute wire/storage cap on a title, independent of the config cap
     * (which may be lower). Payload codecs bound their title strings by this.
     */
    public static final int HARD_MAX_LENGTH = 128;

    /** Validation outcome. */
    public enum Status {
        OK,
        /** Blank, or nothing left after stripping. */
        EMPTY,
        /** Longer than the configured cap after stripping. */
        TOO_LONG,
        /** Contains a word from the configured block list. */
        BLOCKED_WORD
    }

    /**
     * A validation result: the status plus the cleaned name (stripped and
     * trimmed; meaningful only when {@link #ok()}).
     */
    public record Result(Status status, String name) {

        public boolean ok() {
            return status == Status.OK;
        }
    }

    private DiskNames() {
    }

    /**
     * Clean and validate a raw player-chosen name: strip control characters
     * and legacy formatting codes ({@code §}), trim, then enforce the length
     * cap and the blocked-word list (case-insensitive substring match).
     */
    public static Result clean(String raw, int maxLength, List<String> blockedWords) {
        if (raw == null) {
            return new Result(Status.EMPTY, "");
        }
        StringBuilder kept = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '§') {
                continue; // strip, don't refuse — pasted text often carries these
            }
            kept.append(c);
        }
        String cleaned = kept.toString().trim();
        if (cleaned.isEmpty()) {
            return new Result(Status.EMPTY, "");
        }
        if (cleaned.length() > Math.min(maxLength, HARD_MAX_LENGTH)) {
            return new Result(Status.TOO_LONG, cleaned);
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        for (String word : blockedWords) {
            if (!word.isBlank() && lower.contains(word.toLowerCase(Locale.ROOT).trim())) {
                return new Result(Status.BLOCKED_WORD, cleaned);
            }
        }
        return new Result(Status.OK, cleaned);
    }

    /** Parse the comma-separated config word list; blank entries are dropped. */
    public static List<String> parseBlockedWords(String configValue) {
        List<String> words = new ArrayList<>();
        if (configValue == null || configValue.isBlank()) {
            return words;
        }
        for (String part : configValue.split(",")) {
            String word = part.trim();
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    /** {@link #clean} with the configured length cap and word list. */
    public static Result cleanConfigured(String raw) {
        return clean(raw,
                NeroNotesConfig.DISK_NAME_MAX_LENGTH.get(),
                parseBlockedWords(NeroNotesConfig.MODERATION_BLOCKED_WORDS.get()));
    }
}
