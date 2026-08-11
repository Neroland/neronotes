package za.co.neroland.neronotes.platform;

import java.nio.file.Path;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;

import za.co.neroland.neronotes.NeroNotesCommon;

/** Fabric {@link PlatformInfo} (ServiceLoader-provided; see Services). */
public final class FabricPlatformInfo implements PlatformInfo {

    @Override
    public String getModVersion() {
        return FabricLoader.getInstance().getModContainer(NeroNotesCommon.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public List<String> getLoadedModIds() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(mod -> mod.getMetadata().getId())
                .sorted()
                .toList();
    }

    @Override
    public boolean isDevelopment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
