package za.co.neroland.neronotes.telemetry;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;

import za.co.neroland.neronotes.NeroNotesCommon;
import za.co.neroland.neronotes.config.NeroNotesConfig;
import za.co.neroland.neronotes.platform.Services;

/**
 * Opt-out Sentry error reporting for NeroNotes. See PRIVACY.md.
 *
 * <p>Privacy posture: {@code sendDefaultPii=false}, no server name, no session
 * tracking, home-path and OS-username scrubbing, a hard per-session event cap
 * ({@value #MAX_EVENTS_PER_SESSION}) and per-session dedup. Events without a
 * Neroland stack frame are dropped in {@code beforeSend}. No personal data
 * ever — and specifically no composition titles, disk names or author display
 * names, which are player-authored free text.</p>
 *
 * <p>Handled exceptions are reported through
 * {@link #captureHandled(String, String, Throwable)}, which uses the Sentry
 * 8.x {@code ScopeCallback} overload of {@code captureException} (a plain
 * {@code withScope} wrapper does NOT attach the tags to the event), sets
 * {@link SentryLevel#WARNING}, and fingerprints by source AND operation so
 * unrelated handled failures do not collapse into one issue.</p>
 */
public final class NeroNotesTelemetry {

    /**
     * Sentry DSN. While this placeholder is in place {@link #init()} performs
     * no Sentry initialisation at all and no telemetry can be sent; replace it
     * with the real project DSN to enable reporting.
     */
    private static final String DSN = "__NERONOTES_SENTRY_DSN_PLACEHOLDER__";

    /** Hard cap on events sent in one game session. */
    static final int MAX_EVENTS_PER_SESSION = 10;

    private static final String NEROLAND_PACKAGE_PREFIX = "za.co.neroland";

    private static final AtomicInteger EVENTS_THIS_SESSION = new AtomicInteger();
    private static final Set<String> SESSION_FINGERPRINTS = Collections.synchronizedSet(new HashSet<>());
    private static volatile boolean active;

    private NeroNotesTelemetry() {
    }

    /**
     * Initialise Sentry — step 2 of {@code NeroNotesCommon.init()} (after
     * config, so the opt-out is readable). Does nothing when the DSN is the
     * placeholder or the player has opted out.
     */
    public static void init() {
        if (DSN.contains("PLACEHOLDER")) {
            NeroNotesCommon.LOGGER.debug("[NeroNotes] telemetry: no DSN configured; error reporting disabled");
            return;
        }
        if (Boolean.TRUE.equals(NeroNotesConfig.TELEMETRY_OPT_OUT.get())) {
            NeroNotesCommon.LOGGER.info("[NeroNotes] telemetry: opted out via config; error reporting disabled");
            return;
        }
        try {
            Sentry.init(NeroNotesTelemetry::configure);
            active = true;
            NeroNotesCommon.LOGGER.info("[NeroNotes] telemetry: error reporting enabled (opt out via client.telemetry_opt_out; see PRIVACY.md)");
        } catch (RuntimeException | LinkageError e) {
            NeroNotesCommon.LOGGER.warn("[NeroNotes] telemetry: initialisation failed; continuing without error reporting", e);
        }
    }

    private static void configure(SentryOptions options) {
        options.setDsn(DSN);
        options.setRelease(NeroNotesCommon.MOD_ID + "@" + Services.platform().getModVersion());
        options.setEnvironment(Services.platform().isDevelopment() ? "development" : "production");
        options.setSendDefaultPii(false);
        options.setAttachServerName(false);
        options.setEnableAutoSessionTracking(false);
        options.setTracesSampleRate(0.05);
        options.setBeforeSend((event, hint) -> filterAndScrub(event));
    }

    /**
     * Report a handled failure. {@code source} and {@code operation} must be
     * stable, non-identifying labels (a subsystem name plus a translation key
     * or class name) — never player-authored text or rendered titles.
     */
    public static void captureHandled(String source, String operation, Throwable failure) {
        NeroNotesCommon.LOGGER.warn("[NeroNotes] handled failure in {} ({}): {}",
                source, operation, scrub(String.valueOf(failure)));
        if (!active) {
            return;
        }
        try {
            Sentry.captureException(failure, scope -> {
                // ScopeCallback overload — Sentry 8.x withScope(...) tags never reach the event.
                scope.setLevel(SentryLevel.WARNING);
                scope.setTag("neronotes.source", source);
                scope.setTag("neronotes.operation", operation);
                scope.setTag("neronotes.handled", "true");
                // Fingerprint by source AND operation so unrelated handled failures stay separate issues.
                scope.setFingerprint(List.of(NeroNotesCommon.MOD_ID, source, operation,
                        failure.getClass().getName()));
            });
        } catch (RuntimeException | LinkageError e) {
            NeroNotesCommon.LOGGER.debug("[NeroNotes] telemetry: failed to report handled exception", e);
        }
    }

    // ------------------------------------------------------------------
    // beforeSend pipeline: Neroland-only filter -> session dedup -> session
    // cap -> PII scrub. Returning null drops the event.
    // ------------------------------------------------------------------

    private static SentryEvent filterAndScrub(SentryEvent event) {
        if (!touchesNeroland(event)) {
            return null; // not our crash — never report other code's failures
        }
        String fingerprint = sessionFingerprint(event);
        if (!SESSION_FINGERPRINTS.add(fingerprint)) {
            return null; // already reported this failure shape this session
        }
        if (EVENTS_THIS_SESSION.incrementAndGet() > MAX_EVENTS_PER_SESSION) {
            return null; // per-session cap
        }
        scrubEvent(event);
        return event;
    }

    /** True when the event has no exception chain, or any frame is a Neroland frame. */
    private static boolean touchesNeroland(SentryEvent event) {
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions == null || exceptions.isEmpty()) {
            return true; // message-only events are always our own captures
        }
        for (SentryException exception : exceptions) {
            SentryStackTrace stackTrace = exception.getStacktrace();
            if (stackTrace == null || stackTrace.getFrames() == null) {
                continue;
            }
            for (SentryStackFrame frame : stackTrace.getFrames()) {
                String module = frame.getModule();
                if (module != null && module.startsWith(NEROLAND_PACKAGE_PREFIX)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Dedup fingerprint: exception types plus only the Neroland frames. */
    private static String sessionFingerprint(SentryEvent event) {
        StringBuilder fingerprint = new StringBuilder();
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            for (SentryException exception : exceptions) {
                fingerprint.append(exception.getType()).append('|');
                SentryStackTrace stackTrace = exception.getStacktrace();
                if (stackTrace == null || stackTrace.getFrames() == null) {
                    continue;
                }
                for (SentryStackFrame frame : stackTrace.getFrames()) {
                    String module = frame.getModule();
                    if (module != null && module.startsWith(NEROLAND_PACKAGE_PREFIX)) {
                        fingerprint.append(module).append('.').append(frame.getFunction())
                                .append(':').append(frame.getLineno()).append(';');
                    }
                }
            }
        }
        if (fingerprint.length() == 0 && event.getMessage() != null) {
            fingerprint.append(event.getMessage().getFormatted());
        }
        return fingerprint.toString();
    }

    private static void scrubEvent(SentryEvent event) {
        event.setServerName(null);
        if (event.getMessage() != null) {
            event.getMessage().setFormatted(scrub(event.getMessage().getFormatted()));
            event.getMessage().setMessage(scrub(event.getMessage().getMessage()));
        }
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions == null) {
            return;
        }
        for (SentryException exception : exceptions) {
            exception.setValue(scrub(exception.getValue()));
            SentryStackTrace stackTrace = exception.getStacktrace();
            if (stackTrace == null || stackTrace.getFrames() == null) {
                continue;
            }
            for (SentryStackFrame frame : stackTrace.getFrames()) {
                frame.setFilename(scrub(frame.getFilename()));
                frame.setAbsPath(scrub(frame.getAbsPath()));
            }
        }
    }

    /** Remove the user's home path and OS username from a string. */
    static String scrub(String raw) {
        if (raw == null) {
            return null;
        }
        String scrubbed = raw;
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            scrubbed = scrubbed.replace(home, "~");
            scrubbed = scrubbed.replace(home.replace('\\', '/'), "~");
        }
        String user = System.getProperty("user.name");
        if (user != null && !user.isBlank()) {
            scrubbed = scrubbed.replace(user, "[user]");
        }
        return scrubbed;
    }
}
