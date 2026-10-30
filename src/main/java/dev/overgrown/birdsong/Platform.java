package dev.overgrown.birdsong;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class Platform {
    private Platform() {}

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public static boolean isModLoaded(String id) {
        return ModList.get().isLoaded(id);
    }

    public static String version() {
        return ModList.get().getModContainerById(Birdsong.MOD_ID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("unknown");
    }
}
