package dev.overgrown.birdsong.network;

import dev.overgrown.birdsong.Birdsong;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record SpeechTriggerC2S(ResourceLocation power) {
    public static final ResourceLocation CHANNEL = Birdsong.id("speech_trigger");

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(power);
    }

    public static SpeechTriggerC2S read(FriendlyByteBuf buf) {
        return new SpeechTriggerC2S(buf.readResourceLocation());
    }
}
