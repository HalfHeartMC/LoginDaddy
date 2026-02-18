package org.halfheart.logindaddy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.halfheart.logindaddy.LoginDaddy;

public class WhitelistCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("logindaddy")
                        .requires(source -> source.getEntity() == null)
                        .then(CommandManager.literal("whitelist")
                                .then(CommandManager.literal("add")
                                        .then(CommandManager.argument("username", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String username = StringArgumentType.getString(ctx, "username");
                                                    if (LoginDaddy.getDatabaseManager().addToWhitelist(username)) {
                                                        ctx.getSource().sendFeedback(() ->
                                                                Text.literal("\u00a7a\u2714 Added \u00a7f" + username + "\u00a7a to the whitelist."), true);
                                                        return 1;
                                                    }
                                                    ctx.getSource().sendError(
                                                            Text.literal("\u00a7c" + username + " is already whitelisted."));
                                                    return 0;
                                                })
                                        )
                                )
                                .then(CommandManager.literal("remove")
                                        .then(CommandManager.argument("username", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String username = StringArgumentType.getString(ctx, "username");
                                                    if (LoginDaddy.getDatabaseManager().removeFromWhitelist(username)) {
                                                        ctx.getSource().sendFeedback(() ->
                                                                Text.literal("\u00a7c\u2717 Removed \u00a7f" + username + "\u00a7c from the whitelist."), true);
                                                        return 1;
                                                    }
                                                    ctx.getSource().sendError(
                                                            Text.literal("\u00a7c" + username + " was not on the whitelist."));
                                                    return 0;
                                                })
                                        )
                                )
                        )
        );
    }
}