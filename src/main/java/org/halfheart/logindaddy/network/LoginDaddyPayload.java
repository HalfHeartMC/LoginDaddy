package org.halfheart.logindaddy.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import org.halfheart.logindaddy.LoginDaddy;

public record LoginDaddyPayload(boolean release) implements CustomPayload {

    public static final Id<LoginDaddyPayload> ID = new Id<>(LoginDaddy.HANDSHAKE_ID);

    public static final PacketCodec<PacketByteBuf, LoginDaddyPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.release()),
            buf -> new LoginDaddyPayload(buf.readBoolean())
    );

    public LoginDaddyPayload() {
        this(false);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}