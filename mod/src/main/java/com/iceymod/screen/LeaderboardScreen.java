package com.iceymod.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * iceymod+ leaderboard picker — one button per server-side category. Click
 * → client dispatches {@code /icey top <id>} via
 * {@link net.minecraft.client.network.ClientPlayNetworkHandler#sendChatCommand}
 * and closes the screen. The response renders in chat.
 *
 * <p>The category list is hardcoded against the server's known IDs so the
 * client mod doesn't need a client/server packet protocol — iceymod stays
 * useful even on servers that don't have iceymod+ installed (the command
 * just fails with "unknown command" in that case).
 */
public final class LeaderboardScreen extends Screen {

    /** Mirror of iceymod+ Category enum. Keep IDs in sync with
     *  {@code com.iceysmp.LeaderboardManager.Category}. */
    private static final Entry[] ENTRIES = new Entry[] {
            new Entry("mining",      "§b⛏ Mining",        "Haste"),
            new Entry("pvp",         "§c⚔ PvP",            "Strength"),
            new Entry("playtime",    "§e⏱ Playtime",       "Saturation"),
            new Entry("mobkills",    "§4☠ Mob Kills",      "Resistance"),
            new Entry("animalkills", "§a🐄 Animal Kills",  "Night Vision"),
            new Entry("crops",       "§a🌾 Farming",       "Haste"),
            new Entry("diamonds",    "§b💎 Diamonds",      "Speed"),
            new Entry("wood",        "§6🪵 Wood",          "Haste"),
            new Entry("dmgdealt",    "§c⚔ Damage Dealt",   "Strength"),
            new Entry("dmgtaken",    "§4❤ Damage Taken",   "Resistance"),
            new Entry("deaths",      "§7☠ Deaths",         "Regeneration"),
            new Entry("fishing",     "§b🎣 Fishing",       "Luck"),
            new Entry("walking",     "§a👟 Distance",      "Speed"),
            new Entry("jumps",       "§e⤴ Jumps",          "Jump Boost"),
            new Entry("xplevels",    "§a⚡ XP Levels",      "Hero of Village"),
            new Entry("sneak",       "§8👤 Sneak Time",    "Slow Falling"),
    };

    public LeaderboardScreen() {
        super(Text.literal("Icey SMP Leaderboard"));
    }

    @Override
    protected void init() {
        int cols = 2;
        int btnW = 200;
        int btnH = 20;
        int gap = 4;
        int gridW = cols * btnW + (cols - 1) * gap;
        int rows = (ENTRIES.length + cols - 1) / cols;
        int gridH = rows * btnH + (rows - 1) * gap;
        int startX = this.width / 2 - gridW / 2;
        int startY = this.height / 2 - gridH / 2 - 10;

        for (int i = 0; i < ENTRIES.length; i++) {
            final Entry e = ENTRIES[i];
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * (btnW + gap);
            int y = startY + row * (btnH + gap);
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(e.label + " §7→ " + e.effect),
                    b -> {
                        try {
                            if (client != null && client.player != null) {
                                client.player.networkHandler.sendChatCommand("icey top " + e.id);
                            }
                        } catch (Throwable ignored) {}
                        this.close();
                    }
            ).dimensions(x, y, btnW, btnH).build());
        }

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.cancel"),
                b -> this.close()
        ).dimensions(this.width / 2 - 100, startY + gridH + 12, 200, 20).build());
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
                this.width / 2, 20, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                "§7Requires §biceymod+§7 on the server — response shows in chat",
                this.width / 2, 34, 0xFFAAAAAA);
    }

    private record Entry(String id, String label, String effect) {}
}
