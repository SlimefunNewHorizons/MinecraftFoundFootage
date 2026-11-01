package com.sp.render.gui;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.networking.InitializePackets;
import com.sp.util.AIConsentStore;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Shown before the main menu (and before the player enters the backrooms for the first time).
 * It explains everything the mod downloads and how voices are used, links the source code, and the player must
 * accept to continue; declining closes the game.
 */
public class AcceptAIModelsScreen extends Screen {
    private static final String ORIGINAL_REPOSITORY = "https://github.com/SpacePotatoee/MinecraftFoundFootage";
    private static final String FORK_REPOSITORY = "https://github.com/Chagui68/MinecraftFoundFootage";

    private static final int MAX_PANEL_WIDTH = 440;
    private static final int MARGIN = 20;
    private static final int BUTTONS_HEIGHT = 56;

    private List<OrderedText> lines = List.of();
    private double scroll;

    public AcceptAIModelsScreen() {
        super(Text.translatable("ai.consent.title"));
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    private int panelWidth() {
        return Math.min(MAX_PANEL_WIDTH, this.width - 2 * MARGIN);
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int bodyTop() {
        return MARGIN + 34;
    }

    private int bodyBottom() {
        return this.height - MARGIN - BUTTONS_HEIGHT - 14;
    }

    @Override
    protected void init() {
        int panelX = panelX();
        int half = (panelWidth() - 30) / 2;
        int row2 = this.height - MARGIN - 26;
        int row1 = row2 - 24;

        this.lines = this.textRenderer.wrapLines(Text.translatable("ai.consent.body"), panelWidth() - 40);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("ai.consent.github_original"),
                        ConfirmLinkScreen.opening(ORIGINAL_REPOSITORY, this, true))
                .dimensions(panelX + 10, row1, half, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("ai.consent.github_fork"),
                        ConfirmLinkScreen.opening(FORK_REPOSITORY, this, true))
                .dimensions(panelX + 20 + half, row1, half, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("ai.consent.accept"), button -> accept())
                .dimensions(panelX + 10, row2, half, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("ai.consent.decline"), button -> decline())
                .dimensions(panelX + 20 + half, row2, half, 20).build());
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

    private int maxScroll() {
        int content = this.lines.size() * (this.textRenderer.fontHeight + 1);
        return Math.max(0, content - (bodyBottom() - bodyTop()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        this.scroll = MathHelper.clamp(this.scroll - amount * 12, 0, maxScroll());
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int panelX = panelX();
        int panelWidth = panelWidth();
        int centerX = this.width / 2;

        context.fill(panelX, MARGIN, panelX + panelWidth, this.height - MARGIN, 0xC0000000);
        context.fill(panelX, MARGIN, panelX + panelWidth, MARGIN + 2, 0xFF7A0000);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, MARGIN + 14, 0xFFFF5555);

        // The text scrolls inside its own box, above the buttons
        int top = bodyTop();
        int bottom = bodyBottom();
        context.enableScissor(panelX, top, panelX + panelWidth, bottom);
        int y = top - (int) this.scroll;
        for (OrderedText line : this.lines) {
            context.drawText(this.textRenderer, line, panelX + 20, y, 0xFFCCCCCC, false);
            y += this.textRenderer.fontHeight + 1;
        }
        context.disableScissor();

        if (maxScroll() > 0) {
            int barHeight = Math.max(12, (bottom - top) * (bottom - top) / (bottom - top + maxScroll()));
            int barY = top + (int) ((bottom - top - barHeight) * (this.scroll / maxScroll()));
            context.fill(panelX + panelWidth - 8, barY, panelX + panelWidth - 5, barY + barHeight, 0xFF888888);
        }
        // Until the end is reached, ask to keep reading; at the end, remind what declining does
        Text hint = this.scroll < maxScroll() - 1 ? Text.translatable("ai.consent.scroll") : Text.translatable("ai.consent.note");
        context.drawCenteredTextWithShadow(this.textRenderer, hint, centerX, bottom + 3, 0xFF888888);

        super.render(context, mouseX, mouseY, delta);
    }
}
