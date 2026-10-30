package dev.overgrown.birdsong;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class Platform {
    private Platform() {}

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static boolean isModLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(Birdsong.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }
}
