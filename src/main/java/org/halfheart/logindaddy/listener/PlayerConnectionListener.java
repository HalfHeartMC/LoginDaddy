package org.halfheart.logindaddy.listener;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.halfheart.logindaddy.LoginDaddy;
import org.halfheart.logindaddy.database.DatabaseManager;
import org.halfheart.logindaddy.limbo.LimboManager;
import org.halfheart.logindaddy.network.LoginDaddyPayload;
import org.halfheart.logindaddy.session.SessionManager;

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
            UUID   uuid     = player.getUuid();
            String username = player.getName().getString();

            LoginDaddy.LOGGER.info("[LoginDaddy] {} joined, starting handshake...", username);
            LoginDaddy.validatedPlayers.remove(uuid);
            pendingHandshake.add(uuid);

            DatabaseManager.PlayerData data =
                    LoginDaddy.getDatabaseManager().loadAndDeletePlayerData(uuid.toString());

            double joinX, joinY, joinZ;
            float  joinYaw, joinPitch;
            boolean wasDead = false;

            double[] preResolved = LimboManager.consumePreResolvedReturnPoint(uuid);

            if (preResolved != null) {
                joinX = preResolved[0]; joinY = preResolved[1]; joinZ = preResolved[2];
                joinYaw  = (float) preResolved[3]; joinPitch = (float) preResolved[4];
                wasDead = true;
                LoginDaddy.LOGGER.info("[LoginDaddy] {} was dead, using resolved respawn point", username);
            } else if (data != null && data.died) {
                joinX = player.getX(); joinY = player.getY(); joinZ = player.getZ();
                joinYaw = player.getYaw(); joinPitch = player.getPitch();
                wasDead = true;
                LoginDaddy.LOGGER.info("[LoginDaddy] {} had died last session, using vanilla coords", username);
            } else if (data != null) {
                joinX = data.x; joinY = data.y; joinZ = data.z;
                joinYaw = data.yaw; joinPitch = data.pitch;
                LoginDaddy.LOGGER.info("[LoginDaddy] {} using saved position from DB", username);
            } else {
                joinX = player.getX(); joinY = player.getY(); joinZ = player.getZ();
                joinYaw = player.getYaw(); joinPitch = player.getPitch();
            }

            String returnDimension;
            if (wasDead) {
                returnDimension = "minecraft:overworld";
            } else if (data != null) {
                returnDimension = sanitizeDimension(data.dimension);
            } else {
                returnDimension = player.getEntityWorld().getRegistryKey().getValue().toString();
            }

            float restoreHealth;
            int   restoreFood;
            float restoreSaturation;

            if (data != null) {
                restoreHealth     = data.health;
                restoreFood       = data.food;
                restoreSaturation = data.saturation;
            } else {
                restoreHealth     = player.getHealth();
                restoreFood       = player.getHungerManager().getFoodLevel();
                restoreSaturation = player.getHungerManager().getSaturationLevel();
            }

            LimboManager.setPendingStats(uuid, restoreHealth, restoreFood, restoreSaturation);
            LimboManager.enterLimbo(player, server,
                    joinX, joinY, joinZ, joinYaw, joinPitch,
                    returnDimension);

            sender.sendPacket(new LoginDaddyPayload(false, LoginDaddy.getConfigManager().getServerKey()));

            Thread handshakeThread = new Thread(() -> {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < HANDSHAKE_TIMEOUT_MS) {
                    try { Thread.sleep(200); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }

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
            UUID   uuid     = player.getUuid();
            String username = player.getName().getString();

            if (player.hasVehicle()) player.stopRiding();
            for (net.minecraft.entity.Entity passenger : player.getPassengerList()) passenger.stopRiding();

            boolean isInLimbo = LimboManager.isInLimbo(uuid);
            double[] rp       = LimboManager.getReturnPoint(uuid);
            boolean died      = player.getHealth() <= 0f;

            double saveX, saveY, saveZ;
            float  saveYaw, savePitch;

            if (isInLimbo && rp != null) {
                saveX = rp[0]; saveY = rp[1]; saveZ = rp[2];
                saveYaw = (float) rp[3]; savePitch = (float) rp[4];
            } else {
                saveX = player.getX(); saveY = player.getY(); saveZ = player.getZ();
                saveYaw = player.getYaw(); savePitch = player.getPitch();
            }

            float saveHealth;
            int   saveFood;
            float saveSaturation;

            if (died) {
                saveHealth     = player.getMaxHealth();
                saveFood       = 20;
                saveSaturation = 5.0f;
            } else {
                saveHealth     = player.getHealth();
                saveFood       = player.getHungerManager().getFoodLevel();
                saveSaturation = player.getHungerManager().getSaturationLevel();
            }

            String saveDimension;
            if (isInLimbo && rp != null) {
                saveDimension = sanitizeDimension(LimboManager.getReturnDimensionForSave(uuid));
            } else {
                saveDimension = sanitizeDimension(player.getEntityWorld().getRegistryKey().getValue().toString());
            }

            LoginDaddy.getDatabaseManager().savePlayerData(
                    uuid.toString(),
                    saveX, saveY, saveZ, saveYaw, savePitch,
                    saveHealth, saveFood, saveSaturation,
                    died, saveDimension);

            LoginDaddy.LOGGER.info("[LoginDaddy] Saved data for {} (died={}, dim={})",
                    username, died, saveDimension);

            LoginDaddy.validatedPlayers.remove(uuid);
            SessionManager.logout(uuid);
            LimboManager.removeFromLimbo(uuid);
            pendingHandshake.remove(uuid);
        });
    }

    private static String sanitizeDimension(String dimension) {
        if (dimension == null || dimension.startsWith("logindaddy:")) return "minecraft:overworld";
        return dimension;
    }
}