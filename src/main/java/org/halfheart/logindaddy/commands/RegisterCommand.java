package org.halfheart.logindaddy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.halfheart.logindaddy.LoginDaddy;
import org.halfheart.logindaddy.limbo.LimboManager;

public class RegisterCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        dispatcher.register(
                CommandManager.literal("register")
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

                                    if (password.length() < 4) {
                                        player.sendMessage(Text.literal("\u00a7cPassword must be at least 4 characters!"), false);
                                        return 0;
                                    }
                                    if (password.length() > 50) {
                                        player.sendMessage(Text.literal("\u00a7cPassword too long! Max 50 characters."), false);
                                        return 0;
                                    }
                                    if (LoginDaddy.getDatabaseManager().isRegistered(username)) {
                                        player.sendMessage(Text.literal("\u00a7cAlready registered! Use /login <password>"), false);
                                        return 0;
                                    }

                                    if (LoginDaddy.getDatabaseManager().registerUser(username, password)) {
                                        player.sendMessage(Text.literal("\u00a7a\u2714 Registered! Remember your password."), false);
                                        LimboManager.releaseLimbo(player, context.getSource().getServer());
                                        return 1;
                                    }

                                    player.sendMessage(Text.literal("\u00a7cRegistration failed. Try again."), false);
                                    return 0;
                                })
                        )
        );

        dispatcher.register(
                CommandManager.literal("changepassword")
                        .then(CommandManager.argument("oldPassword", StringArgumentType.word())
                                .then(CommandManager.argument("newPassword", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayer();
                                            if (player == null) return 0;

                                            if (LimboManager.isInLimbo(player.getUuid())) {
                                                player.sendMessage(Text.literal("\u00a7cYou must be logged in to change your password!"), false);
                                                return 0;
                                            }

                                            String username = player.getName().getString();
                                            String oldPass = StringArgumentType.getString(context, "oldPassword");
                                            String newPass = StringArgumentType.getString(context, "newPassword");

                                            if (!LoginDaddy.getDatabaseManager().verifyPassword(username, oldPass)) {
                                                player.sendMessage(Text.literal("\u00a7c\u2717 Incorrect current password!"), false);
                                                return 0;
                                            }
                                            if (LoginDaddy.getDatabaseManager().resetPassword(username, newPass)) {
                                                player.sendMessage(Text.literal("\u00a7a\u2714 Password changed successfully!"), false);
                                                return 1;
                                            }
                                            return 0;
                                        })
                                )
                        )
        );

        dispatcher.register(
                CommandManager.literal("resetpassword")
                        .requires(src -> src.getEntity() == null)
                        .then(CommandManager.argument("username", StringArgumentType.word())
                                .then(CommandManager.argument("newPassword", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String username = StringArgumentType.getString(context, "username");
                                            String newPass  = StringArgumentType.getString(context, "newPassword");

                                            if (!LoginDaddy.getDatabaseManager().isRegistered(username)) {
                                                context.getSource().sendError(Text.literal("\u00a7c" + username + " is not registered!"));
                                                return 0;
                                            }
                                            if (LoginDaddy.getDatabaseManager().resetPassword(username, newPass)) {
                                                context.getSource().sendFeedback(
                                                        () -> Text.literal("\u00a7a\u2714 Reset password for " + username), true);
                                                return 1;
                                            }
                                            return 0;
                                        })
                                )
                        )
        );
    }
}