package dev.overgrown.birdsong.compat.catalogue;

import dev.overgrown.birdsong.client.config.BirdsongConfigScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.screens.Screen;

@Environment(EnvType.CLIENT)
public final class CatalogueConfigFactory {
    private CatalogueConfigFactory() {}

    public static Screen createConfigScreen(Screen currentScreen, ModContainer container) {
        return new BirdsongConfigScreen(currentScreen);
    }
}
