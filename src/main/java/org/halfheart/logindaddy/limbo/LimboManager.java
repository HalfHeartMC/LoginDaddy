package org.halfheart.logindaddy.limbo;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.halfheart.logindaddy.LoginDaddy;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LimboManager {

    public static final RegistryKey<World> LIMBO_WORLD_KEY = RegistryKey.of(
            RegistryKeys.WORLD,
            Identifier.of(LoginDaddy.MOD_ID, "limbo")
    );

    private static final double LIMBO_X = 0.5;
    private static final double LIMBO_Y = 200.0;
    private static final double LIMBO_Z = 0.5;

    private static final Set<UUID> preLimbo = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, double[]> returnPoints = new ConcurrentHashMap<>();
    private static final Set<UUID> inLimbo = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> pendingAuth = ConcurrentHashMap.newKeySet();

    private static int tickCounter = 0;

    public static void registerTickHandler() {
        ServerTickEvents.END_SERVER_TICK.register(LimboManager::onServerTick);
    }

    public static void markPreLimbo(UUID uuid) {
        preLimbo.add(uuid);
    }

    public static boolean isPreLimbo(UUID uuid) {
        return preLimbo.contains(uuid);
    }

    public static void enterLimbo(ServerPlayerEntity player, MinecraftServer server,
                                  double returnX, double returnY, double returnZ,
                                  float returnYaw, float returnPitch) {
        UUID uuid = player.getUuid();
        preLimbo.remove(uuid);
        returnPoints.put(uuid, new double[]{returnX, returnY, returnZ, returnYaw, returnPitch});
        inLimbo.add(uuid);

        ServerWorld limboWorld = server.getWorld(LIMBO_WORLD_KEY);
        if (limboWorld != null) {
            player.teleport(limboWorld, LIMBO_X, LIMBO_Y, LIMBO_Z, Set.of(), 0f, 0f, false);
        } else {
            LoginDaddy.LOGGER.warn("[Limbo] Limbo world not loaded!");
        }

        player.changeGameMode(GameMode.SPECTATOR);
        player.sendAbilitiesUpdate();

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS, Integer.MAX_VALUE, 255, false, false, false));

        hideFromTabList(player, server);
        sendLimboScreen(player);
        LoginDaddy.LOGGER.info("[Limbo] {} entered limbo", player.getName().getString());
    }

    public static void tryRelease(ServerPlayerEntity player, MinecraftServer server) {
        if (!inLimbo.contains(player.getUuid())) return;

        String username = player.getName().getString();

        if (!LoginDaddy.getDatabaseManager().isWhitelisted(username)) {
            teleportToReturnPoint(player, server);
            cleanup(player.getUuid());
            player.networkHandler.disconnect(Text.literal(
                    "\u00a7c\u00a7lNot Whitelisted\n\n" +
                            "\u00a77You are not on the whitelist for this server.\n" +
                            "\u00a77Contact an admin in the Discord Server to be added."
            ));
            LoginDaddy.LOGGER.info("[Limbo] {} kicked - not whitelisted", username);
            return;
        }

        pendingAuth.add(player.getUuid());

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 999999, 5));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("\u00a76\u00a7lLoginDaddy")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("\u00a7eAuthenticate to play")));

        if (!LoginDaddy.getDatabaseManager().isRegistered(username)) {
            player.sendMessage(Text.literal("\u00a76\u250c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2510"), false);
            player.sendMessage(Text.literal("\u00a7e\u00a7l  Welcome! Please register:"), false);
            player.sendMessage(Text.literal("\u00a7f  /register <password>"), false);
            player.sendMessage(Text.literal("\u00a76\u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2518"), false);
        } else {
            player.sendMessage(Text.literal("\u00a7ePlease login: \u00a7f/login <password>"), false);
        }

        LoginDaddy.LOGGER.info("[Limbo] {} passed whitelist - awaiting login", username);
    }

    public static void releaseLimbo(ServerPlayerEntity player, MinecraftServer server) {
        UUID uuid = player.getUuid();
        cleanup(uuid);

        player.removeStatusEffect(StatusEffects.BLINDNESS);

        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(5.0f);
        player.setHealth(player.getMaxHealth());

        net.minecraft.server.network.ServerPlayerEntity.Respawn respawn = player.getRespawn();
        ServerWorld respawnWorld;
        double tx;
        double ty;
        double tz;
        float  yaw;

        if (respawn != null) {
            respawnWorld = server.getWorld(respawn.respawnData().getDimension());
            if (respawnWorld == null) respawnWorld = server.getOverworld();
            net.minecraft.util.math.BlockPos rp = respawn.respawnData().getPos();
            tx  = rp.getX() + 0.5;
            ty  = rp.getY();
            tz  = rp.getZ() + 0.5;
            yaw = respawn.respawnData().yaw();
        } else {
            respawnWorld = server.getOverworld();
            net.minecraft.world.WorldProperties.SpawnPoint sp = respawnWorld.getSpawnPoint();
            net.minecraft.util.math.BlockPos sp2 = sp.getPos();
            tx  = sp2.getX() + 0.5;
            ty  = sp2.getY();
            tz  = sp2.getZ() + 0.5;
            yaw = sp.yaw();
        }

        player.teleport(respawnWorld, tx, ty, tz, Set.of(), yaw, 0f, false);
        player.changeGameMode(GameMode.SURVIVAL);
        player.sendAbilitiesUpdate();

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 0, 0));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("")));

        restoreToTabList(player, server);

        server.getPlayerManager().broadcast(
                Text.literal("\u00a7e" + player.getName().getString() + " joined the game"), false);

        player.sendMessage(Text.literal("\u00a7a\u00a7l\u2714 Welcome to the server!"), false);
        LoginDaddy.LOGGER.info("[Limbo] {} released to overworld", player.getName().getString());
    }

    private static void hideFromTabList(ServerPlayerEntity player, MinecraftServer server) {
        PlayerRemoveS2CPacket removePacket = new PlayerRemoveS2CPacket(List.of(player.getUuid()));
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            if (!other.getUuid().equals(player.getUuid())) {
                other.networkHandler.sendPacket(removePacket);
            }
        }
    }

    private static void restoreToTabList(ServerPlayerEntity player, MinecraftServer server) {
        PlayerListS2CPacket addPacket = new PlayerListS2CPacket(
                EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER, PlayerListS2CPacket.Action.UPDATE_LISTED),
                List.of(player)
        );
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            other.networkHandler.sendPacket(addPacket);
        }
    }

    private static void teleportToReturnPoint(ServerPlayerEntity player, MinecraftServer server) {
        double[] rp = returnPoints.get(player.getUuid());
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return;
        if (rp != null) {
            player.teleport(overworld, rp[0], rp[1], rp[2], Set.of(), (float) rp[3], (float) rp[4], false);
        } else {
            player.teleport(overworld, 0.5, 64, 0.5, Set.of(), 0f, 0f, false);
        }
    }

    public static boolean isInLimbo(UUID uuid) {
        return inLimbo.contains(uuid);
    }

    public static boolean isPendingAuth(UUID uuid) {
        return pendingAuth.contains(uuid);
    }

    public static double[] getReturnPoint(UUID uuid) {
        return returnPoints.get(uuid);
    }

    public static void removeFromLimbo(UUID uuid) {
        cleanup(uuid);
    }

    public static void clearAll() {
        preLimbo.clear();
        returnPoints.clear();
        inLimbo.clear();
        pendingAuth.clear();
    }

    private static void cleanup(UUID uuid) {
        preLimbo.remove(uuid);
        returnPoints.remove(uuid);
        inLimbo.remove(uuid);
        pendingAuth.remove(uuid);
    }

    private static void sendLimboScreen(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 999999, 5));
        player.networkHandler.sendPacket(new TitleS2CPacket(
                Text.literal("\u00a70\u00a7k|||  \u00a7f\u00a7lLoginDaddy\u00a70\u00a7k  |||")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(
                Text.literal("\u00a78Initializing...")));
    }

    private static void onServerTick(MinecraftServer server) {
        if (inLimbo.isEmpty()) return;
        tickCounter++;
        if (tickCounter % 60 != 0) return;
        for (UUID uuid : inLimbo) {
            if (pendingAuth.contains(uuid)) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null || player.isDisconnected()) continue;
            sendLimboScreen(player);
        }
    }
}