package com.iceymod.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Iceymod+ (server) leaderboard picker. Three categories, one click each —
 * dispatches the matching {@code /icey top <category>} command server-side
 * and closes; the response renders in chat like any other command.
 *
 * <p>We don't parse the server response or render a custom in-screen list
 * because that would require a client/server packet protocol (and the
 * iceymod client mod isn't required for an iceysmp server). Keeping the
 * client-side here purely a shortcut launcher keeps iceymod usable on
 * iceysmp servers AND on vanilla SMPs that don't have the plugin.
 */
public final class LeaderboardScreen extends Screen {

    public LeaderboardScreen() {
        super(Text.literal("Icey SMP Leaderboard"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int btnW = 240;
        int btnH = 22;
        int gap = 6;
        int y = this.height / 2 - 60;

        addBtn(cx, btnW, btnH, y, "§b⛏ Mining (Haste)",     "mining");   y += btnH + gap;
        addBtn(cx, btnW, btnH, y, "§c⚔ PvP (Strength)",       "pvp");      y += btnH + gap;
        addBtn(cx, btnW, btnH, y, "§e⏱ Playtime (Saturation)","playtime"); y += btnH + gap * 2;

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.cancel"),
                b -> this.close()
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
    }

    private void addBtn(int cx, int btnW, int btnH, int y, String label, String category) {
        addDrawableChild(ButtonWidget.builder(
                Text.literal(label),
                b -> {
                    try {
                        if (client != null && client.player != null) {
                            // sendChatCommand takes the command without the leading '/'.
                            client.player.networkHandler.sendChatCommand("icey top " + category);
                        }
                    } catch (Throwable ignored) {}
                    this.close();
                }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer,
                "§b§lLeaderboard",
                this.width / 2, this.height / 2 - 88, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                "§7Requires §biceymod+§7 (Icey SMP) on the server",
                this.width / 2, this.height / 2 - 74, 0xFFAAAAAA);
    }
}
