package za.co.neroland.neronotes.platform;

import java.nio.file.Path;
import java.util.List;

/**
 * Loader-neutral queries about the running platform. One implementation per
 * loader ({@code FabricPlatformInfo}, {@code NeoForgePlatformInfo},
 * {@code ForgePlatformInfo}), resolved via {@link java.util.ServiceLoader}
 * eagerly in {@link Services#init()} — never lazily mid-tick.
 */
public interface PlatformInfo {

    /** This mod's own version string (e.g. {@code 0.0.1-alpha.1}), or {@code "unknown"}. */
    String getModVersion();

    /** Whether a mod with the given id is loaded. Used for soft-integration feature detection. */
    boolean isModLoaded(String modId);

    /**
     * Sorted ids of every loaded mod. Mod ids only — never versions of other
     * mods, and never anything player-identifying.
     */
    List<String> getLoadedModIds();

    /** Whether this is a development environment (dev runs, tests). */
    boolean isDevelopment();

    /** The loader's config directory (where {@code neronotes.properties} lives). */
    Path getConfigDir();
}
