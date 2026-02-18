package org.halfheart.logindaddy.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.halfheart.logindaddy.network.LoginDaddyPayload;

public class LoginDaddyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        try {
            PayloadTypeRegistry.playS2C().register(LoginDaddyPayload.ID, LoginDaddyPayload.CODEC);
            PayloadTypeRegistry.playC2S().register(LoginDaddyPayload.ID, LoginDaddyPayload.CODEC);
        } catch (IllegalArgumentException ignored) {}

        ClientPlayNetworking.registerGlobalReceiver(LoginDaddyPayload.ID, (payload, context) -> {
            if (!context.client().isInSingleplayer()) {
                ClientPlayNetworking.send(new LoginDaddyPayload());
            }
        });
    }
}