package dev.overgrown.birdsong;

import dev.overgrown.birdsong.client.BirdsongClient;
import dev.overgrown.birdsong.network.SpeechTriggerC2S;
import dev.overgrown.birdsong.speech.ActionOnSpeechPower;
import dev.overgrown.birdsong.speech.SpeechCommand;
import dev.overgrown.birdsong.voice.VoiceHearing;
import dev.overgrown.birdsong.voice.VoiceState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Mod(Birdsong.MOD_ID)
public final class Birdsong {
    public static final String MOD_ID = "birdsong";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public Birdsong(IEventBus modBus, ModContainer container) {
        BirdsongTypes.register();
        modBus.addListener(RegisterPayloadHandlersEvent.class, Birdsong::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            VoiceState.tick(event.getServer());
            VoiceHearing.tick(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> {
            VoiceState.clear();
            VoiceHearing.reset();
            ActionOnSpeechPower.clear();
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> forget(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> SpeechCommand.register(event.getDispatcher()));
        if (FMLEnvironment.dist == Dist.CLIENT) BirdsongClient.init(modBus, container);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(SpeechTriggerC2S.TYPE, SpeechTriggerC2S.STREAM_CODEC, (payload, context) ->
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) ActionOnSpeechPower.fireTrigger(player, payload.power());
            }));
    }

    private static void forget(UUID player) {
        VoiceState.forget(player);
        VoiceHearing.forget(player);
        ActionOnSpeechPower.forget(player);
    }
}
