package dev.overgrown.birdsong.client;

import dev.overgrown.birdsong.network.SpeechTriggerC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.ResourceLocation;

public final class ClientPlatform {
    private ClientPlatform() {}

    public static boolean canSendTrigger() {
        return ClientPlayNetworking.canSend(SpeechTriggerC2S.TYPE);
    }

    public static void sendTrigger(ResourceLocation power) {
        ClientPlayNetworking.send(new SpeechTriggerC2S(power));
    }
}
