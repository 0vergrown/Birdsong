package dev.overgrown.birdsong;

import dev.overgrown.birdsong.network.SpeechTriggerC2S;
import dev.overgrown.birdsong.speech.ActionOnSpeechPower;
import dev.overgrown.birdsong.speech.SpeechCommand;
import dev.overgrown.birdsong.voice.VoiceHearing;
import dev.overgrown.birdsong.voice.VoiceState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class Birdsong implements ModInitializer {
    public static final String MOD_ID = "birdsong";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        BirdsongTypes.register();

        PayloadTypeRegistry.playC2S().register(SpeechTriggerC2S.TYPE, SpeechTriggerC2S.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SpeechTriggerC2S.TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            context.server().execute(() -> ActionOnSpeechPower.fireTrigger(sender, payload.power()));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            VoiceState.tick(server);
            VoiceHearing.tick(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            VoiceState.clear();
            VoiceHearing.reset();
            ActionOnSpeechPower.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> SpeechCommand.register(dispatcher));
    }

    private static void forget(UUID player) {
        VoiceState.forget(player);
        VoiceHearing.forget(player);
        ActionOnSpeechPower.forget(player);
    }
}
