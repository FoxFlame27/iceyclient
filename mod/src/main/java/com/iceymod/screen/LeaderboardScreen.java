package com.iceymod.screen;

import com.iceymod.compat.Gfx;
import com.iceymod.compat.IceyScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * iceymod+ leaderboard picker — one button per server-side category,
 * paginated (8 per page) so 16 entries don't crowd a single screen. Each
 * button dispatches {@code /leaderboard <id>} via the chat-command sender
 * and closes.
 *
 * <p>The category list is hardcoded against the server's known IDs so we
 * don't need a client/server packet protocol — iceymod stays useful on
 * vanilla servers (the command simply fails with "unknown command" if
 * iceymod+ isn't installed).
 */
public final class LeaderboardScreen extends IceyScreen {

    private static final Entry[] ENTRIES = new Entry[] {
            new Entry("mining",      "§b⛏ Mining",        "Haste"),
            new Entry("pvp",         "§c⚔ PvP",            "Strength"),
            new Entry("playtime",    "§e⏱ Playtime",       "Saturation"),
            new Entry("fishing",     "§b🎣 Fishing",       "Luck"),
            new Entry("walking",     "§a👟 Distance",      "Speed"),
            new Entry("jumps",       "§e⤴ Jumps",          "Jump Boost"),
            new Entry("water",       "§9~ Water Movement", "Dolphin's Grace"),
            new Entry("dmgtaken",    "§4❤ Damage Taken",   "Resistance"),
    };

    private static final int PER_PAGE = 8;
    private int page = 0;

    public LeaderboardScreen() {
        super(Component.literal("Icey SMP Leaderboard"));
    }

    private int pageCount() {
        return (ENTRIES.length + PER_PAGE - 1) / PER_PAGE;
    }

    @Override
    protected void init() {
        int cols = 2;
        int btnW = 220;
        int btnH = 24;
        int gap = 6;
        int rowsPerPage = PER_PAGE / cols;
        int gridW = cols * btnW + (cols - 1) * gap;
        int gridH = rowsPerPage * btnH + (rowsPerPage - 1) * gap;
        int startX = this.width / 2 - gridW / 2;
        int startY = this.height / 2 - gridH / 2 - 8;

        int from = page * PER_PAGE;
        int to = Math.min(ENTRIES.length, from + PER_PAGE);
        for (int i = from; i < to; i++) {
            final Entry e = ENTRIES[i];
            int local = i - from;
            int col = local % cols;
            int row = local / cols;
            int x = startX + col * (btnW + gap);
            int y = startY + row * (btnH + gap);
            addRenderableWidget(Button.builder(
                    Component.literal(e.label + " §7→ " + e.effect),
                    b -> {
                        try {
                            if (minecraft != null && minecraft.player != null) {
                                minecraft.player.connection.sendCommand("leaderboard " + e.id);
                            }
                        } catch (Throwable ignored) {}
                        this.onClose();
                    }
            ).bounds(x, y, btnW, btnH).build());
        }

        int navY = startY + gridH + 12;
        int navBtnW = 80;
        int navGap = 8;

        Button prev = Button.builder(
                Component.literal("§7◀ Prev"),
                b -> { if (page > 0) { page--; rebuild(); } }
        ).bounds(this.width / 2 - navBtnW - navGap - 50, navY, navBtnW, 20).build();
        prev.active = page > 0;
        addRenderableWidget(prev);

        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                b -> this.onClose()
        ).bounds(this.width / 2 - 50, navY, 100, 20).build());

        Button next = Button.builder(
                Component.literal("Next §7▶"),
                b -> { if (page < pageCount() - 1) { page++; rebuild(); } }
        ).bounds(this.width / 2 + 50 + navGap, navY, navBtnW, 20).build();
        next.active = page < pageCount() - 1;
        addRenderableWidget(next);
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    @Override
    protected void renderScreenBackground(Gfx context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    protected void renderScreen(Gfx context, int mouseX, int mouseY, float delta) {
        superRender(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font,
                "§b§lLeaderboard",
                this.width / 2, 20, 0xFFFFFFFF);
        context.drawCenteredString(this.font,
                "§7Page §f" + (page + 1) + "§7 of §f" + pageCount()
                + " §8· §7requires §biceymod+§7 on the server",
                this.width / 2, 34, 0xFFAAAAAA);
    }

    private record Entry(String id, String label, String effect) {}
}
