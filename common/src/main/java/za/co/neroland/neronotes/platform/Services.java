package za.co.neroland.neronotes.platform;

import java.util.ServiceLoader;

import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * Eager ServiceLoader resolution for the platform seams. {@link #init()} is
 * step 0 of {@code NeroNotesCommon.init()} and resolves <em>every</em>
 * service up front — a lazy {@code ServiceLoader} read mid-tick caused a
 * production crash in a sibling mod, so the getters only hand out instances
 * that were cached during init.
 */
public final class Services {

    private static PlatformInfo platformInfo;
    private static NetworkPlatform network;

    private Services() {
    }

    /**
     * Resolve and cache all platform services. Called exactly once, from
     * {@code NeroNotesCommon.init()} step 0, before anything else runs.
     */
    public static void init() {
        if (platformInfo != null) {
            return; // already initialised
        }
        platformInfo = load(PlatformInfo.class);
        network = load(NetworkPlatform.class);
    }

    /** The cached {@link PlatformInfo}; throws if {@link #init()} has not run. */
    public static PlatformInfo platform() {
        return require(platformInfo, PlatformInfo.class);
    }

    /** The cached {@link NetworkPlatform}; throws if {@link #init()} has not run. */
    public static NetworkPlatform network() {
        return require(network, NetworkPlatform.class);
    }

    private static <T> T load(Class<T> service) {
        T impl = ServiceLoader.load(service, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No " + service.getName() + " implementation found on the classpath"));
        NeroNotesCommon.LOGGER.debug("[NeroNotes] resolved {} -> {}",
                service.getSimpleName(), impl.getClass().getName());
        return impl;
    }

    private static <T> T require(T instance, Class<T> service) {
        if (instance == null) {
            throw new IllegalStateException(
                    service.getSimpleName() + " requested before Services.init() — services must be resolved eagerly during init, never lazily mid-tick");
        }
        return instance;
    }
}
