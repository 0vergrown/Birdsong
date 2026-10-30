package dev.overgrown.birdsong.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.overgrown.birdsong.client.speech.SpeechClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;

@Environment(EnvType.CLIENT)
public final class BirdsongClient implements ClientModInitializer {
    public static final KeyMapping PUSH_TO_TALK = new KeyMapping("key.birdsong.push_to_talk",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.birdsong");

    @Override
    public void onInitializeClient() {
        BirdsongClientConfig.get();
        KeyBindingHelper.registerKeyBinding(PUSH_TO_TALK);
        SpeechClient.setPushToTalkKey(PUSH_TO_TALK);
        ClientTickEvents.END_CLIENT_TICK.register(SpeechClient::clientTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SpeechClient.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SpeechClient.onLeave());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> SpeechCommands.register(dispatcher));
    }
}
