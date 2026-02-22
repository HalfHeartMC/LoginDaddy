package org.halfheart.logindaddy.client.mixin;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.TranslatableTextContent;
import org.halfheart.logindaddy.client.limbo.ClientLimboState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin {

    @Inject(method = "initWidgets", at = @At("TAIL"))
    private void logindaddy$hideDisconnect(CallbackInfo ci) {
        if (!ClientLimboState.inLimbo) return;

        GameMenuScreen self = (GameMenuScreen) (Object) this;

        for (var element : self.children()) {
            if (!(element instanceof ButtonWidget btn)) continue;

            if (btn.getMessage().getContent() instanceof TranslatableTextContent ttc) {
                String key = ttc.getKey();

                if (key.equals("menu.disconnect")) {
                    btn.visible = false;
                    btn.active  = false;
                }
            }
        }
    }
}