package dev.overgrown.birdsong.speech;

import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class SpeechLanguage {
    private SpeechLanguage() {}

    public static String of(ServerPlayer player) {
        return "en-US";
    }

    public static String toBcp47(String minecraftLanguage) {
        if (minecraftLanguage == null || minecraftLanguage.isBlank()) return "en-US";
        int split = minecraftLanguage.indexOf('_');
        if (split < 0) return minecraftLanguage;
        return minecraftLanguage.substring(0, split) + "-" + minecraftLanguage.substring(split + 1).toUpperCase(Locale.ROOT);
    }
}
