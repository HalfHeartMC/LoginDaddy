package org.halfheart.logindaddy.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.halfheart.logindaddy.client.managers.ServerKeyManager;
import org.halfheart.logindaddy.network.LoginDaddyPayload;

public class ServerKeyScreen extends Screen {

    private final String serverAddress;
    private final String serverKey;
    private final boolean keyChanged;

    private TextFieldWidget keyField;
    private boolean wrongKey = false;

    public ServerKeyScreen(String serverAddress, String serverKey, boolean keyChanged) {
        super(Text.literal("LoginDaddy - Server Key"));
        this.serverAddress = serverAddress;
        this.serverKey = serverKey;
        this.keyChanged = keyChanged;
    }

    @Override
    protected void init() {
        keyField = new TextFieldWidget(textRenderer, width / 2 - 100, height / 2 - 10, 200, 20,
                Text.literal("Enter server key..."));
        keyField.setMaxLength(128);
        keyField.setPlaceholder(Text.literal("\u00a78Enter server key..."));
        addDrawableChild(keyField);
        setInitialFocus(keyField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Connect"), btn -> submit())
                .dimensions(width / 2 - 102, height / 2 + 18, 100, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Disconnect"), btn -> {
            if (client != null) client.disconnect(Text.literal("Disconnected"));
        }).dimensions(width / 2 + 2, height / 2 + 18, 100, 20).build());
    }

    private void submit() {
        String entered = keyField.getText().trim();
        if (entered.equals(serverKey)) {
            ServerKeyManager.saveKey(serverAddress, entered);
            wrongKey = false;
            if (client != null) client.setScreen(null);
            ClientPlayNetworking.send(new LoginDaddyPayload(false, entered));
        } else {
            wrongKey = true;
            keyField.setText("");
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == 257 || input.key() == 335) {
            submit();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(textRenderer,
                "\u00a76\u00a7lLoginDaddy \u00a77- Server Key Required",
                width / 2, height / 2 - 50, 0xFFFFFF);

        if (keyChanged) {
            context.drawCenteredTextWithShadow(textRenderer,
                    "\u00a7eThe server key has changed. Enter the new key:",
                    width / 2, height / 2 - 30, 0xFFFFFF);
        } else {
            context.drawCenteredTextWithShadow(textRenderer,
                    "\u00a77This server requires a key to join:",
                    width / 2, height / 2 - 30, 0xFFFFFF);
        }

        if (wrongKey) {
            context.drawCenteredTextWithShadow(textRenderer,
                    "\u00a7cIncorrect key. Please try again.",
                    width / 2, height / 2 + 46, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}