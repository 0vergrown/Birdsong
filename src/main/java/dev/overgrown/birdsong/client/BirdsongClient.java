package dev.overgrown.birdsong.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.overgrown.birdsong.client.config.BirdsongConfigScreen;
import dev.overgrown.birdsong.client.speech.SpeechClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

public final class BirdsongClient {
    public static final KeyMapping PUSH_TO_TALK = new KeyMapping("key.birdsong.push_to_talk",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.birdsong");

    private BirdsongClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        BirdsongClientConfig.get();
        SpeechClient.setPushToTalkKey(PUSH_TO_TALK);
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(PUSH_TO_TALK));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> SpeechClient.clientTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> SpeechClient.onJoin());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> SpeechClient.onLeave());
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> SpeechCommands.register(event.getDispatcher()));
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new BirdsongConfigScreen(parent));
    }
}
