package org.halfheart.logindaddy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.halfheart.logindaddy.LoginDaddy;
import org.halfheart.logindaddy.limbo.LimboManager;

public class LoginCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("login")
                        .then(CommandManager.argument("password", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player == null) return 0;

                                    if (!LimboManager.isPendingAuth(player.getUuid())) {
                                        player.sendMessage(Text.literal("\u00a7cYou are already logged in!"), false);
                                        return 0;
                                    }

                                    String username = player.getName().getString();
                                    String password = StringArgumentType.getString(context, "password");

                                    if (!LoginDaddy.getDatabaseManager().isRegistered(username)) {
                                        player.sendMessage(Text.literal("\u00a7cNot registered! Use /register <password>"), false);
                                        return 0;
                                    }

                                    if (LoginDaddy.getDatabaseManager().verifyPassword(username, password)) {
                                        LimboManager.releaseLimbo(player, context.getSource().getServer());
                                        return 1;
                                    } else {
                                        player.sendMessage(Text.literal("\u00a7c\u2717 Wrong password!"), false);
                                        return 0;
                                    }
                                })
                        )
        );
    }
}