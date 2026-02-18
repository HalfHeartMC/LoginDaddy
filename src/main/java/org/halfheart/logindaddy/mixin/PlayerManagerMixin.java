package org.halfheart.logindaddy.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.halfheart.logindaddy.limbo.LimboManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Inject(method = "onPlayerConnect", at = @At("HEAD"))
    private void markPreLimbo(ClientConnection connection, ServerPlayerEntity player,
                              ConnectedClientData clientData, CallbackInfo ci) {
        LimboManager.markPreLimbo(player.getUuid());
    }

    @Inject(method = "broadcast(Lnet/minecraft/text/Text;Z)V", at = @At("HEAD"), cancellable = true)
    private void suppressLimboMessages(Text message, boolean overlay, CallbackInfo ci) {
        String raw = message.getString();
        for (ServerPlayerEntity player : ((PlayerManager) (Object) this).getPlayerList()) {
            if (LimboManager.isPreLimbo(player.getUuid()) || LimboManager.isInLimbo(player.getUuid())) {
                if (raw.contains(player.getName().getString())) {
                    ci.cancel();
                    return;
                }
            }
        }
    }

    @Inject(method = "respawnPlayer", at = @At("RETURN"))
    private void onRespawnPlayer(ServerPlayerEntity player, boolean alive, Entity.RemovalReason removalReason, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        ServerPlayerEntity newPlayer = cir.getReturnValue();
        if (newPlayer == null) return;
        if (!LimboManager.isInLimbo(newPlayer.getUuid())) return;

        MinecraftServer server = ((PlayerManager) (Object) this).getServer();
        ServerWorld limboWorld = server.getWorld(LimboManager.LIMBO_WORLD_KEY);
        if (limboWorld == null) return;

        newPlayer.teleport(limboWorld, 0.5, 200.0, 0.5, Set.of(), 0f, 0f, false);
        newPlayer.changeGameMode(GameMode.SPECTATOR);
        newPlayer.sendAbilitiesUpdate();

        newPlayer.removeStatusEffect(StatusEffects.BLINDNESS);
        newPlayer.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS, Integer.MAX_VALUE, 255, false, false, false));
    }
}