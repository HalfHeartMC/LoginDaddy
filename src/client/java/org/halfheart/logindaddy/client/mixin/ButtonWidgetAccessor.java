package org.halfheart.logindaddy.client.mixin;

import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin for {@link ButtonWidget} so we can replace the private
 * {@code onPress} handler at runtime without having to recreate the whole widget.
 */
@Mixin(ButtonWidget.class)
public interface ButtonWidgetAccessor {

    @Mutable
    @Accessor("onPress")
    void setOnPress(ButtonWidget.PressAction onPress);
}