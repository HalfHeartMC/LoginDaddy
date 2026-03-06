package org.halfheart.logindaddy.client.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ServerKeyManager {

    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("LoginDaddy");
    private static final Path SERVER_LIST_FILE = CONFIG_DIR.resolve("serverlist.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static String getSavedKey(String serverAddress) {
        JsonObject json = loadJson();
        if (!json.has(serverAddress)) return null;
        return json.get(serverAddress).getAsString();
    }

    public static void saveKey(String serverAddress, String key) {
        JsonObject json = loadJson();
        json.addProperty(serverAddress, key);
        saveJson(json);
    }

    public static String getCurrentServerAddress(MinecraftClient client) {
        ServerInfo entry = client.getCurrentServerEntry();
        if (entry == null) return "unknown";
        return entry.address;
    }

    private static JsonObject loadJson() {
        File file = SERVER_LIST_FILE.toFile();
        if (!file.exists()) return new JsonObject();
        try (Reader reader = new FileReader(file)) {
            JsonObject result = GSON.fromJson(reader, JsonObject.class);
            return result != null ? result : new JsonObject();
        } catch (IOException e) {
            return new JsonObject();
        }
    }

    private static void saveJson(JsonObject json) {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = new FileWriter(SERVER_LIST_FILE.toFile())) {
                GSON.toJson(json, writer);
            }
        } catch (IOException ignored) {
        }
    }
}