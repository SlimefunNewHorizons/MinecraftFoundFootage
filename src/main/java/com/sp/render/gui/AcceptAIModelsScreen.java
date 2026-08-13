package com.sp.render.gui;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.networking.InitializePackets;
import com.sp.util.AIConsentStore;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

/**
 * Shown before the main menu (and before the player enters the backrooms for the first time).
 * The player must accept the download of the mod's external voice and AI models to continue;
 * declining closes the game.
 */
public class AcceptAIModelsScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 200;

    public AcceptAIModelsScreen() {
        super(Text.translatable("ai.consent.title"));
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("ai.consent.accept"), button -> accept())
                .dimensions(centerX - 145, centerY + PANEL_HEIGHT / 2 - 24, 135, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("ai.consent.decline"), button -> decline())
                .dimensions(centerX + 10, centerY + PANEL_HEIGHT / 2 - 24, 135, 20)
                .build());
    }

    private void accept() {
        AIConsentStore.setAccepted(true);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            PlayerComponent playerComponent = InitializeComponents.PLAYER.get(client.player);
            playerComponent.setAcceptedAIModels(true);

            PacketByteBuf buffer = PacketByteBufs.create();
            ClientPlayNetworking.send(InitializePackets.ACCEPT_AI_MODELS, buffer);
        }
        this.close();
    }

    private void decline() {
        MinecraftClient.getInstance().scheduleStop();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;

        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xC0000000);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 2, 0xFF7A0000);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, panelY + 16, 0xFFFF5555);
        context.drawTextWrapped(this.textRenderer, Text.translatable("ai.consent.body"), panelX + 20, panelY + 40, PANEL_WIDTH - 40, 0xFFCCCCCC);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("ai.consent.note"), centerX, panelY + PANEL_HEIGHT - 50, 0xFF888888);

        super.render(context, mouseX, mouseY, delta);
    }
}