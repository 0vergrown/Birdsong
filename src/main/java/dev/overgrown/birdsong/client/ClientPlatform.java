package dev.overgrown.birdsong.client;

import dev.overgrown.birdsong.network.SpeechTriggerC2S;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientPlatform {
    private ClientPlatform() {}

    public static boolean canSendTrigger() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(SpeechTriggerC2S.TYPE);
    }

    public static void sendTrigger(ResourceLocation power) {
        PacketDistributor.sendToServer(new SpeechTriggerC2S(power));
    }
}
