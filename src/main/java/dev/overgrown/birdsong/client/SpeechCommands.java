package dev.overgrown.birdsong.client;

import com.mojang.brigadier.CommandDispatcher;
import dev.overgrown.birdsong.client.speech.SpeechClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@Environment(EnvType.CLIENT)
public final class SpeechCommands {
    private SpeechCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("birdsong").then(literal("speech")
            .then(literal("on").executes(ctx -> reply(ctx.getSource(), SpeechClient::enable)))
            .then(literal("off").executes(ctx -> reply(ctx.getSource(), SpeechClient::disable)))
            .then(literal("later").executes(ctx -> reply(ctx.getSource(), SpeechClient::later)))
            .then(literal("status").executes(ctx -> reply(ctx.getSource(), SpeechClient::status)))));
    }

    private static int reply(FabricClientCommandSource source, Supplier<Component> action) {
        source.sendFeedback(Component.literal("[Birdsong] ").append(action.get()));
        return 1;
    }
}
