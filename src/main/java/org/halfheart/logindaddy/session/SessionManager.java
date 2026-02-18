package org.halfheart.logindaddy.session;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final Set<UUID> loggedInPlayers = new HashSet<>();
    private static final Map<UUID, GameMode> originalGameModes = new HashMap<>();
    private static final Map<String, CachedSession> ipSessionCache = new ConcurrentHashMap<>();
    private static final long SESSION_CACHE_DURATION = 24 * 60 * 60 * 1000;

    public static class CachedSession {
        public final String username;
        public final String ip;
        public final long timestamp;

        public CachedSession(String username, String ip) {
            this.username = username;
            this.ip = ip;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isValid() {
            return System.currentTimeMillis() - timestamp < SESSION_CACHE_DURATION;
        }
    }

    public static void login(ServerPlayerEntity player) {
        loggedInPlayers.add(player.getUuid());
        GameMode originalMode = originalGameModes.remove(player.getUuid());
        if (originalMode != null) {
            player.changeGameMode(originalMode);
        }
        String ip = getPlayerIP(player);
        if (ip != null) {
            ipSessionCache.put(ip, new CachedSession(player.getName().getString(), ip));
        }
    }

    public static void loginPlayer(ServerPlayerEntity player) { login(player); }

    public static boolean isLogged(ServerPlayerEntity player) {
        return loggedInPlayers.contains(player.getUuid());
    }

    public static boolean isLoggedIn(ServerPlayerEntity player) { return isLogged(player); }

    public static void logout(UUID uuid) {
        loggedInPlayers.remove(uuid);
        originalGameModes.remove(uuid);
    }

    public static void logoutPlayer(ServerPlayerEntity player) { logout(player.getUuid()); }

    public static void clearAll() {
        loggedInPlayers.clear();
        originalGameModes.clear();
        ipSessionCache.clear();
    }

    public static boolean hasCachedSession(ServerPlayerEntity player) {
        String ip = getPlayerIP(player);
        if (ip == null) return false;
        CachedSession session = ipSessionCache.get(ip);
        if (session == null) return false;
        if (!session.isValid()) {
            ipSessionCache.remove(ip);
            return false;
        }
        return session.username.equalsIgnoreCase(player.getName().getString());
    }

    public static boolean tryAutoLogin(ServerPlayerEntity player) {
        if (hasCachedSession(player)) {
            login(player);
            return true;
        }
        return false;
    }

    public static void freezePlayer(ServerPlayerEntity player) {
        originalGameModes.putIfAbsent(player.getUuid(), player.interactionManager.getGameMode());
        player.changeGameMode(GameMode.SPECTATOR);
    }

    public static String getPlayerIP(ServerPlayerEntity player) {
        if (player.networkHandler == null || player.networkHandler.getPlayer().getIp() == null) {
            return null;
        }
        String ip = player.networkHandler.player.getIp();
        if (ip.contains("/")) ip = ip.substring(ip.indexOf("/") + 1);
        if (ip.contains(":")) ip = ip.substring(0, ip.indexOf(":"));
        return ip;
    }

    public static void cleanupExpiredSessions() {
        ipSessionCache.entrySet().removeIf(entry -> !entry.getValue().isValid());
    }
}