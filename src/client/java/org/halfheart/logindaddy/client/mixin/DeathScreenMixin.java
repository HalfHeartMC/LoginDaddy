package org.halfheart.logindaddy.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
public class DeathScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void logindaddy$interceptTitleButton(CallbackInfo ci) {
        DeathScreen self = (DeathScreen) (Object) this;

        for (var element : self.children()) {
            if (!(element instanceof ButtonWidget btn)) continue;
            if (!(btn.getMessage().getContent() instanceof TranslatableTextContent ttc)) continue;

            String key = ttc.getKey();
            if (!key.equals("menu.returnToMenu") && !key.equals("deathScreen.titleScreen")) continue;

            ((ButtonWidgetAccessor) btn).setOnPress(button -> {
                MinecraftClient client = MinecraftClient.getInstance();
                ClientPlayerEntity player = client.player;
                if (player != null) {
                    player.requestRespawn();
                }
                client.execute(() -> client.disconnect(Text.literal("")));
            });

            break;
        }
    }
}