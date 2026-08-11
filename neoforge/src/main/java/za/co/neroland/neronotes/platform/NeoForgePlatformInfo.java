package za.co.neroland.neronotes.platform;

import java.nio.file.Path;
import java.util.List;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import za.co.neroland.neronotes.NeroNotesCommon;

/** NeoForge {@link PlatformInfo} (ServiceLoader-provided; see Services). */
public final class NeoForgePlatformInfo implements PlatformInfo {

    @Override
    public String getModVersion() {
        return ModList.get().getModContainerById(NeroNotesCommon.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public List<String> getLoadedModIds() {
        return ModList.get().getMods().stream()
                .map(mod -> mod.getModId())
                .sorted()
                .toList();
    }

    @Override
    public boolean isDevelopment() {
        return !FMLEnvironment.isProduction();
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
