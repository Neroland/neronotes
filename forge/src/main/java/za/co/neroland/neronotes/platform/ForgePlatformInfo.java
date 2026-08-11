package za.co.neroland.neronotes.platform;

import java.nio.file.Path;
import java.util.List;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import za.co.neroland.neronotes.NeroNotesCommon;

/**
 * Forge {@link PlatformInfo} (ServiceLoader-provided; see Services). Forge 26's
 * {@code ModList} is all static methods — there is no {@code ModList.get()}.
 */
public final class ForgePlatformInfo implements PlatformInfo {

    @Override
    public String getModVersion() {
        return ModList.getMods().stream()
                .filter(mod -> NeroNotesCommon.MOD_ID.equals(mod.getModId()))
                .findFirst()
                .map(mod -> mod.getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.isLoaded(modId);
    }

    @Override
    public List<String> getLoadedModIds() {
        return ModList.getMods().stream()
                .map(mod -> mod.getModId())
                .sorted()
                .toList();
    }

    @Override
    public boolean isDevelopment() {
        return !FMLEnvironment.production;
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
