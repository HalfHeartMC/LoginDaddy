package org.halfheart.logindaddy.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final Path CONFIG_DIR   = FabricLoader.getInstance().getConfigDir().resolve("LoginDaddy");
    private static final Path SESSION_FILE = CONFIG_DIR.resolve("sessions.json");
    private static final Gson GSON         = new GsonBuilder().setPrettyPrinting().create();

    private static final long SESSION_DURATION_MS = 48L * 60 * 60 * 1000;

    private static final Set<UUID>                  loggedInPlayers = ConcurrentHashMap.newKeySet();
    private static final Map<String, CachedSession> ipSessionCache  = new ConcurrentHashMap<>();

    public static class CachedSession {
        public final String username;
        public final String ip;
        public final long   timestamp;

        public CachedSession(String username, String ip, long timestamp) {
            this.username  = username;
            this.ip        = ip;
            this.timestamp = timestamp;
        }

        public CachedSession(String username, String ip) {
            this(username, ip, System.currentTimeMillis());
        }

        public boolean isValid() {
            return System.currentTimeMillis() - timestamp < SESSION_DURATION_MS;
        }
    }

    public static void loadSessions() {
        File file = SESSION_FILE.toFile();
        if (!file.exists()) return;
        try (Reader reader = new FileReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;
            for (String ip : json.keySet()) {
                JsonObject entry = json.getAsJsonObject(ip);
                String username  = entry.get("username").getAsString();
                long   timestamp = entry.get("timestamp").getAsLong();
                CachedSession session = new CachedSession(username, ip, timestamp);
                if (session.isValid()) ipSessionCache.put(ip, session);
            }
        } catch (IOException ignored) {}
    }

    public static void saveSessions() {
        try {
            Files.createDirectories(CONFIG_DIR);
            cleanupExpiredSessions();
            JsonObject json = new JsonObject();
            for (Map.Entry<String, CachedSession> entry : ipSessionCache.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("username",  entry.getValue().username);
                obj.addProperty("ip",        entry.getValue().ip);
                obj.addProperty("timestamp", entry.getValue().timestamp);
                json.add(entry.getKey(), obj);
            }
            try (Writer writer = new FileWriter(SESSION_FILE.toFile())) {
                GSON.toJson(json, writer);
            }
        } catch (IOException ignored) {}
    }

    public static void login(ServerPlayerEntity player) {
        loggedInPlayers.add(player.getUuid());
        String ip = getPlayerIP(player);
        if (ip != null) {
            ipSessionCache.put(ip, new CachedSession(player.getName().getString(), ip));
            saveSessions();
        }
    }

    public static boolean isLoggedIn(ServerPlayerEntity player) {
        return loggedInPlayers.contains(player.getUuid());
    }

    public static void logout(UUID uuid) {
        loggedInPlayers.remove(uuid);
    }

    public static boolean hasCachedSession(ServerPlayerEntity player) {
        String ip = getPlayerIP(player);
        if (ip == null) return false;
        CachedSession session = ipSessionCache.get(ip);
        if (session == null) return false;
        if (!session.isValid()) {
            ipSessionCache.remove(ip);
            saveSessions();
            return false;
        }
        return session.username.equalsIgnoreCase(player.getName().getString());
    }

    public static boolean tryAutoLogin(ServerPlayerEntity player) {
        if (hasCachedSession(player)) {
            loggedInPlayers.add(player.getUuid());
            return true;
        }
        return false;
    }

    public static String getPlayerIP(ServerPlayerEntity player) {
        if (player.networkHandler == null) return null;
        String ip = player.networkHandler.player.getIp();
        if (ip == null) return null;
        if (ip.contains("/")) ip = ip.substring(ip.indexOf("/") + 1);
        if (ip.contains(":")) ip = ip.substring(0, ip.indexOf(":"));
        return ip;
    }

    public static void cleanupExpiredSessions() {
        ipSessionCache.entrySet().removeIf(e -> !e.getValue().isValid());
    }

    public static void clearLoggedIn() {
        loggedInPlayers.clear();
    }
}