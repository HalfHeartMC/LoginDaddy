package org.halfheart.logindaddy.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import org.halfheart.logindaddy.LoginDaddy;

public record LoginDaddyPayload() implements CustomPayload {
    public static final Id<LoginDaddyPayload> ID = new Id<>(LoginDaddy.HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, LoginDaddyPayload> CODEC = PacketCodec.unit(new LoginDaddyPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}