package dev.overgrown.birdsong.speech;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SpeechCommand {
    private SpeechCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(Commands.literal("birdsong:speech")
            .requires(source -> source.hasPermission(0))
            .then(Commands.argument("text", StringArgumentType.greedyString())
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String text = StringArgumentType.getString(ctx, "text");
                    int fired = ActionOnSpeechPower.fire(player, text, SpeechLanguage.of(player));
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                        fired == 0 ? "birdsong.command.speech.none" : "birdsong.command.speech.fired", text, fired), false);
                    return fired;
                })));
        dispatcher.register(Commands.literal("apoli:speech").requires(source -> source.hasPermission(0)).redirect(node));
    }
}
