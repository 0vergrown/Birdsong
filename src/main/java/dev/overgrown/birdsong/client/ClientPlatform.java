package dev.overgrown.birdsong.client;

import dev.overgrown.birdsong.network.SpeechTriggerC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class ClientPlatform {
    private ClientPlatform() {}

    public static boolean canSendTrigger() {
        return ClientPlayNetworking.canSend(SpeechTriggerC2S.CHANNEL);
    }

    public static void sendTrigger(ResourceLocation power) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new SpeechTriggerC2S(power).write(buf);
        ClientPlayNetworking.send(SpeechTriggerC2S.CHANNEL, buf);
    }
}
