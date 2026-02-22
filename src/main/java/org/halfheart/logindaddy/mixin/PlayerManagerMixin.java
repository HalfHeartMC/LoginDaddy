package org.halfheart.logindaddy.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldProperties;
import org.halfheart.logindaddy.limbo.LimboManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Inject(method = "onPlayerConnect", at = @At("HEAD"))
    private void onPlayerConnectHead(ClientConnection connection, ServerPlayerEntity player,
                                     ConnectedClientData clientData, CallbackInfo ci) {
        UUID uuid = player.getUuid();
        MinecraftServer server = ((PlayerManager) (Object) this).getServer();

        if (player.hasVehicle()) {
            player.stopRiding();
        }
        for (Entity passenger : player.getPassengerList()) {
            passenger.stopRiding();
        }

        for (ServerWorld world : server.getWorlds()) {
            Entity existing = world.getEntity(uuid);
            if (existing != null && existing != player) {
                existing.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        boolean wasDead = player.getHealth() <= 0f;

        if (wasDead) {
            player.setHealth(player.getMaxHealth());

            ServerPlayerEntity.Respawn respawn = player.getRespawn();
            if (respawn != null) {
                WorldProperties.SpawnPoint sp = respawn.respawnData();
                ServerWorld spawnWorld = server.getWorld(sp.getDimension());
                if (spawnWorld != null) {
                    BlockPos pos = sp.getPos();
                    LimboManager.setPreResolvedReturnPoint(uuid,
                            pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5,
                            sp.yaw(), sp.pitch());
                }
            }

            if (!LimboManager.isPreResolvedSet(uuid)) {
                WorldProperties.SpawnPoint sp = server.getOverworld().getSpawnPoint();
                BlockPos pos = sp.getPos();
                LimboManager.setPreResolvedReturnPoint(uuid,
                        pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        sp.yaw(), 0f);
            }
        }

        LimboManager.markPreLimbo(uuid);
    }

    @Redirect(method = "onPlayerConnect",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V"))
    private void suppressVanillaJoinMessage(PlayerManager manager, Text message, boolean overlay,
                                            ClientConnection connection, ServerPlayerEntity player,
                                            ConnectedClientData clientData) {
    }
}