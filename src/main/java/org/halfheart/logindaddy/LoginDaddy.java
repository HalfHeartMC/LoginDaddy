package org.halfheart.logindaddy;

import org.halfheart.logindaddy.commands.LoginCommand;
import org.halfheart.logindaddy.commands.RegisterCommand;
import org.halfheart.logindaddy.commands.WhitelistCommand;
import org.halfheart.logindaddy.config.ConfigManager;
import org.halfheart.logindaddy.database.DatabaseManager;
import org.halfheart.logindaddy.limbo.LimboManager;
import org.halfheart.logindaddy.listener.PlayerConnectionListener;
import org.halfheart.logindaddy.network.LoginDaddyPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LoginDaddy implements ModInitializer {
    public static final String MOD_ID = "logindaddy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier HANDSHAKE_ID = Identifier.of("logindaddy", "handshake");

    public static final Set<UUID> validatedPlayers = ConcurrentHashMap.newKeySet();

    private static DatabaseManager databaseManager;
    private static ConfigManager configManager;

    @Override
    public void onInitialize() {
        LOGGER.info("LoginDaddy initializing...");

        PayloadTypeRegistry.playC2S().register(LoginDaddyPayload.ID, LoginDaddyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LoginDaddyPayload.ID, LoginDaddyPayload.CODEC);

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            LOGGER.info("LoginDaddy initialized (client-only mode)!");
            return;
        }

        configManager = new ConfigManager();
        configManager.loadConfig();
        databaseManager = new DatabaseManager(configManager);

        ServerPlayNetworking.registerGlobalReceiver(LoginDaddyPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (player == null) return;
                validatedPlayers.add(player.getUuid());
                LOGGER.info("Handshake confirmed for {}", player.getName().getString());
                LimboManager.tryRelease(player, context.server());
            });
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LoginCommand.register(dispatcher);
            RegisterCommand.register(dispatcher);
            WhitelistCommand.register(dispatcher);
        });

        PlayerConnectionListener.register();

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (databaseManager != null) databaseManager.close();
            validatedPlayers.clear();
            LimboManager.clearAll();
        });

        LOGGER.info("LoginDaddy initialized!");
    }

    public static DatabaseManager getDatabaseManager() { return databaseManager; }
    public static ConfigManager getConfigManager() { return configManager; }
}