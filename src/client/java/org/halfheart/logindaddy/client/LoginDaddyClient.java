package org.halfheart.logindaddy.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.gui.screen.DeathScreen;
import org.halfheart.logindaddy.client.limbo.ClientLimboState;
import org.halfheart.logindaddy.client.managers.ServerKeyManager;
import org.halfheart.logindaddy.client.screen.ServerKeyScreen;
import org.halfheart.logindaddy.network.LoginDaddyPayload;

public class LoginDaddyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        try {
            PayloadTypeRegistry.playS2C().register(LoginDaddyPayload.ID, LoginDaddyPayload.CODEC);
        } catch (IllegalArgumentException ignored) {}
        try {
            PayloadTypeRegistry.playC2S().register(LoginDaddyPayload.ID, LoginDaddyPayload.CODEC);
        } catch (IllegalArgumentException ignored) {}

        ClientPlayNetworking.registerGlobalReceiver(LoginDaddyPayload.ID, (payload, context) -> {
            if (context.client().isInSingleplayer()) return;

            if (payload.release()) {
                context.client().execute(() -> ClientLimboState.inLimbo = false);
            } else {
                context.client().execute(() -> {
                    ClientLimboState.inLimbo = true;

                    if (context.client().currentScreen instanceof DeathScreen) {
                        context.client().setScreen(null);
                    }

                    String serverKey = payload.serverKey();
                    String serverAddress = ServerKeyManager.getCurrentServerAddress(context.client());

                    if (!serverKey.isEmpty()) {
                        String savedKey = ServerKeyManager.getSavedKey(serverAddress);
                        if (savedKey != null && savedKey.equals(serverKey)) {
                            ClientPlayNetworking.send(new LoginDaddyPayload(false, serverKey));
                        } else {
                            boolean keyChanged = savedKey != null && !savedKey.equals(serverKey);
                            ClientPlayNetworking.send(new LoginDaddyPayload(false, ""));
                            context.client().setScreen(new ServerKeyScreen(serverAddress, serverKey, keyChanged));
                        }
                    } else {
                        ClientPlayNetworking.send(new LoginDaddyPayload(false, ""));
                    }
                });
            }
        });
    }
}