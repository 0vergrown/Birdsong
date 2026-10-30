package dev.overgrown.birdsong.client;

import com.mojang.brigadier.CommandDispatcher;
import dev.overgrown.birdsong.client.speech.SpeechClient;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public final class SpeechCommands {
    private SpeechCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("birdsong").then(Commands.literal("speech")
            .then(Commands.literal("on").executes(ctx -> reply(ctx.getSource(), SpeechClient::enable)))
            .then(Commands.literal("off").executes(ctx -> reply(ctx.getSource(), SpeechClient::disable)))
            .then(Commands.literal("later").executes(ctx -> reply(ctx.getSource(), SpeechClient::later)))
            .then(Commands.literal("status").executes(ctx -> reply(ctx.getSource(), SpeechClient::status)))));
    }

    private static int reply(CommandSourceStack source, Supplier<Component> action) {
        Component message = Component.literal("[Birdsong] ").append(action.get());
        source.sendSuccess(() -> message, false);
        return 1;
    }
}
