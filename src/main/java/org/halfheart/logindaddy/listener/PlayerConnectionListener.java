package org.halfheart.logindaddy.listener;

import org.halfheart.logindaddy.LoginDaddy;
import org.halfheart.logindaddy.limbo.LimboManager;
import org.halfheart.logindaddy.network.LoginDaddyPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerConnectionListener {

    private static final Set<UUID> pendingHandshake = ConcurrentHashMap.newKeySet();
    private static final long HANDSHAKE_TIMEOUT_MS = 10_000;

    public static void register() {
        LimboManager.registerTickHandler();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!server.isDedicated()) return;

            ServerPlayerEntity player = handler.getPlayer();
            UUID uuid = player.getUuid();
            String username = player.getName().getString();

            LoginDaddy.LOGGER.info("[LoginDaddy] {} joined, starting handshake...", username);

            LoginDaddy.validatedPlayers.remove(uuid);
            pendingHandshake.add(uuid);

            double joinX = player.getX();
            double joinY = player.getY();
            double joinZ = player.getZ();
            float joinYaw = player.getYaw();
            float joinPitch = player.getPitch();

            if (player.getHealth() <= 0f) {
                player.setHealth(player.getMaxHealth());
            }

            LimboManager.enterLimbo(player, server, joinX, joinY, joinZ, joinYaw, joinPitch);
            sender.sendPacket(new LoginDaddyPayload());

            Thread handshakeThread = new Thread(() -> {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < HANDSHAKE_TIMEOUT_MS) {
                    try { Thread.sleep(200); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (LoginDaddy.validatedPlayers.contains(uuid)) {
                        pendingHandshake.remove(uuid);
                        return;
                    }
                    if (server.getPlayerManager().getPlayer(uuid) == null) {
                        pendingHandshake.remove(uuid);
                        return;
                    }
                }
                server.execute(() -> {
                    pendingHandshake.remove(uuid);
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                    if (p != null && !p.isDisconnected()) {
                        LoginDaddy.LOGGER.info("[LoginDaddy] {} timed out - no mod, kicking", username);
                        p.networkHandler.disconnect(Text.literal(
                                "\u00a7c\u00a7lLoginDaddy Mod Required\n\n" +
                                        "\u00a77This server requires the LoginDaddy client mod.\n" +
                                        "\u00a77Ask an admin for the download link."
                        ));
                        LimboManager.removeFromLimbo(uuid);
                    }
                });
            }, "logindaddy-handshake-" + username);

            handshakeThread.setDaemon(true);
            handshakeThread.start();
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (!server.isDedicated()) return;

            ServerPlayerEntity player = handler.getPlayer();
            UUID uuid = player.getUuid();

            if (LimboManager.isInLimbo(uuid)) {
                double[] rp = LimboManager.getReturnPoint(uuid);
                ServerWorld overworld = server.getWorld(World.OVERWORLD);
                if (overworld != null) {
                    double x   = rp != null ? rp[0] : 0.5;
                    double y   = rp != null ? rp[1] : 64.0;
                    double z   = rp != null ? rp[2] : 0.5;
                    float  yaw = rp != null ? (float) rp[3] : 0f;
                    float  pit = rp != null ? (float) rp[4] : 0f;
                    player.setServerWorld(overworld);
                    player.refreshPositionAndAngles(x, y, z, yaw, pit);
                }
            }

            LoginDaddy.validatedPlayers.remove(uuid);
            LimboManager.removeFromLimbo(uuid);
            pendingHandshake.remove(uuid);
        });
    }
}