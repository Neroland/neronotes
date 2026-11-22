package za.co.neroland.neronotes.telemetry;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.sentry.IScopes;
import io.sentry.Scope;
import io.sentry.Scopes;
import io.sentry.SentryClient;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.UncaughtExceptionHandlerIntegration;
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
 * NeroNotes stack frame are dropped in {@code beforeSend}. No personal data
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
     * Sentry DSN for the NeroNotes project (EU ingest). A DSN is a public,
     * send-only client key, so shipping it in the jar is expected. Reporting
     * still only starts when the player has not opted out.
     */
    private static final String DSN = "https://caad87f68d8255f7f2a65cbb8d29f782@o4511183823241216.ingest.de.sentry.io/4512346896728144";

    /** Hard cap on events sent in one game session. */
    static final int MAX_EVENTS_PER_SESSION = 10;

    /** Only frames from NeroNotes' own package qualify an event (never Core's or another mod's). */
    private static final String NEROLAND_PACKAGE_PREFIX = "za.co.neroland.neronotes";

    private static final AtomicInteger EVENTS_THIS_SESSION = new AtomicInteger();
    private static final Set<String> SESSION_FINGERPRINTS = Collections.synchronizedSet(new HashSet<>());
    private static volatile boolean active;
    /** NeroNotes' own scopes; {@code null} until {@link #init()} succeeds. Never the global hub. */
    private static volatile IScopes scopes;
    /** Process-lifetime uncaught-exception hook bound to {@link #scopes}; kept so it is never collected. */
    private static UncaughtExceptionHandlerIntegration uncaughtHandler;

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
        if (!Boolean.TRUE.equals(NeroNotesConfig.TELEMETRY_ENABLED.get())) {
            NeroNotesCommon.LOGGER.info("[NeroNotes] telemetry: opted out via config; error reporting disabled");
            return;
        }
        try {
            // Own client and scopes, never the global hub: every Nero mod bundles the same Sentry jar, so
            // a global Sentry.init would replace (and silence) every other mod's client.
            SentryOptions options = baseOptions(Services.platform().getModVersion(),
                    Services.platform().isDevelopment() ? "development" : "production");
            options.setTracesSampleRate(0.05);
            options.setBeforeSend((event, hint) -> ForeignMixinFailure.matches(event) ? null : filterAndScrub(event));
            IScopes own = isolatedScopes(options, NeroNotesCommon.MOD_ID);
            scopes = own;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    own.flush(2000L);
                } catch (RuntimeException ignored) {
                    // best-effort flush on shutdown
                }
            }, "neronotes-sentry-shutdown"));
            active = true;
            NeroNotesCommon.LOGGER.info("[NeroNotes] telemetry: error reporting enabled (opt out via telemetryEnabled=false in config/neronotes.properties; see PRIVACY.md)");
        } catch (RuntimeException | LinkageError e) {
            NeroNotesCommon.LOGGER.warn("[NeroNotes] telemetry: initialisation failed; continuing without error reporting", e);
        }
    }

    /** Options for NeroNotes' own client: same privacy settings as before, never the global hub's. */
    static SentryOptions baseOptions(String modVersion, String environment) {
        SentryOptions options = new SentryOptions();
        options.setDsn(DSN);
        options.setRelease(NeroNotesCommon.MOD_ID + "@" + modVersion);
        options.setEnvironment(environment);
        options.setSendDefaultPii(false);          // POPIA/GDPR: never the sender's IP/identity
        options.setAttachServerName(false);        // hostname is identifying
        options.setEnableUncaughtExceptionHandler(true);
        options.setEnableShutdownHook(false);      // flushed by our own hook; never close the global hub
        options.setEnableAutoSessionTracking(false);
        return options;
    }

    /**
     * Builds isolated scopes bound to a client of our own: what {@code Sentry.init} does, minus the global
     * hub. The uncaught-exception handler chains to the previous one, so every mod still sees crashes and
     * keeps only its own through {@code beforeSend}.
     */
    static IScopes isolatedScopes(SentryOptions options, String creator) {
        options.activate();                        // start Sentry's executors, as the global init would
        Scopes own = new Scopes(new Scope(options), new Scope(options), new Scope(options), creator);
        own.bindClient(new SentryClient(options));
        if (options.isEnableUncaughtExceptionHandler()) {
            uncaughtHandler = new UncaughtExceptionHandlerIntegration();
            uncaughtHandler.register(own, options);
        }
        return own;
    }

    /**
     * Report a handled failure. {@code source} and {@code operation} must be
     * stable, non-identifying labels (a subsystem name plus a translation key
     * or class name) — never player-authored text or rendered titles.
     */
    public static void captureHandled(String source, String operation, Throwable failure) {
        NeroNotesCommon.LOGGER.warn("[NeroNotes] handled failure in {} ({}): {}",
                source, operation, scrub(String.valueOf(failure)));
        IScopes s = scopes;
        if (!active || s == null) {
            return;
        }
        try {
            s.captureException(failure, scope -> {
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
