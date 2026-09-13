package com.iceymod.screen;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.BiomeLocatorModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.structure.BiomeTracker;
import java.util.List;
import net.minecraft.client.Minecraft;
import com.iceymod.compat.Gfx;
import com.iceymod.compat.IceyScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Same UX as StructureMenuScreen, but for biomes. MAIN ▸ Find/Pause ·
 * Select Biomes · Waypoint · Delete · Clear All.
 */
public class BiomeMenuScreen extends IceyScreen {

    private enum State { MAIN, TYPES, WAYPOINT_LIST, DELETE_LIST }
    private State state = State.MAIN;

    public BiomeMenuScreen() {
        super(Component.literal("Biome Locator"));
    }

    private BiomeLocatorModule findModule() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof BiomeLocatorModule bm) return bm;
        }
        return null;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int btnW = 240;
        int btnH = 22;
        int gap = 6;

        switch (state) {
            case MAIN -> buildMain(cx, btnW, btnH, gap);
            case TYPES -> buildTypes(cx, btnW, btnH, gap);
            case WAYPOINT_LIST -> buildList(cx, btnW, btnH, gap, "§b✎ ", idx -> {
                List<BiomeTracker.Found> all = BiomeTracker.getSortedByDistance();
                if (idx >= 0 && idx < all.size()) {
                    BiomeTracker.Found f = all.get(idx);
                    WaypointManager.addWaypoint(f.type.label, f.pos.getX(), 64, f.pos.getZ());
                }
                state = State.MAIN;
                rebuild();
            });
            case DELETE_LIST -> buildList(cx, btnW, btnH, gap, "§c✖ ", idx -> {
                List<BiomeTracker.Found> all = BiomeTracker.getSortedByDistance();
                if (idx >= 0 && idx < all.size()) BiomeTracker.remove(all.get(idx));
                if (BiomeTracker.getFound().isEmpty()) state = State.MAIN;
                rebuild();
            });
        }
    }

    private void buildMain(int cx, int btnW, int btnH, int gap) {
        BiomeLocatorModule mod = findModule();
        boolean scanning = mod != null && mod.isEnabled();
        int count = BiomeTracker.getFound().size();
        int y = this.height / 2 - 80;

        addRenderableWidget(Button.builder(
                Component.literal(scanning ? "§e⏸ Pause Finding" : "§a+ Find Biomes"),
                b -> {
                    if (mod != null) {
                        mod.setEnabled(!mod.isEnabled());
                        if (mod.isEnabled()) BiomeTracker.rescanNearby();
                    }
                    rebuild();
                }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addRenderableWidget(Button.builder(
                Component.literal("§d☑ Select Biomes"),
                b -> { state = State.TYPES; rebuild(); }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        Button wpBtn = Button.builder(
                Component.literal("§b✎ Waypoint a Biome"),
                b -> { state = State.WAYPOINT_LIST; rebuild(); }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build();
        wpBtn.active = count > 0;
        addRenderableWidget(wpBtn);
        y += btnH + gap;

        Button delBtn = Button.builder(
                Component.literal("§c✖ Delete a Biome"),
                b -> { state = State.DELETE_LIST; rebuild(); }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build();
        delBtn.active = count > 0;
        addRenderableWidget(delBtn);
        y += btnH + gap;

        Button clearBtn = Button.builder(
                Component.literal("§c✖ Clear All"),
                b -> { BiomeTracker.clear(); BiomeTracker.rescanNearby(); this.onClose(); }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build();
        clearBtn.active = count > 0;
        addRenderableWidget(clearBtn);
        y += btnH + gap * 2;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                b -> this.onClose()
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
    }

    private void buildTypes(int cx, int btnW, int btnH, int gap) {
        BiomeLocatorModule mod = findModule();
        if (mod == null) {
            state = State.MAIN; rebuild(); return;
        }
        BiomeTracker.BiomeType[] types = BiomeTracker.BiomeType.values();
        int colW = (btnW - gap) / 2;
        int rowsPerCol = (types.length + 1) / 2;
        int gridH = rowsPerCol * (btnH + gap);
        int y0 = this.height / 2 - gridH / 2 - 10;

        for (int i = 0; i < types.length; i++) {
            final BiomeTracker.BiomeType t = types[i];
            BoolSetting s = mod.getTypeSetting(t);
            if (s == null) continue;
            int col = i % 2;
            int row = i / 2;
            int bx = cx - btnW / 2 + col * (colW + gap);
            int by = y0 + row * (btnH + gap);
            String label = (s.get() ? "§a☑ " : "§7☐ ") + t.label;
            addRenderableWidget(Button.builder(
                    Component.literal(label),
                    b -> { s.set(!s.get()); BiomeTracker.rescanNearby(); rebuild(); }
            ).bounds(bx, by, colW, btnH).build());
        }

        addRenderableWidget(Button.builder(
                Component.literal("← Back"),
                b -> { state = State.MAIN; rebuild(); }
        ).bounds(cx - btnW / 2, y0 + gridH + gap * 2, btnW, btnH).build());
    }

    private void buildList(int cx, int btnW, int btnH, int gap, String prefix, java.util.function.IntConsumer onPick) {
        List<BiomeTracker.Found> all = BiomeTracker.getSortedByDistance();
        Minecraft c = Minecraft.getInstance();
        int y = this.height / 2 - (all.size() * (btnH + gap)) / 2 - 20;
        for (int i = 0; i < all.size(); i++) {
            final int idx = i;
            BiomeTracker.Found f = all.get(i);
            int dist = 0;
            if (c != null && c.player != null) {
                double dx = f.pos.getX() - c.player.getX();
                double dz = f.pos.getZ() - c.player.getZ();
                dist = (int) Math.sqrt(dx * dx + dz * dz);
            }
            String label = prefix + "§r" + f.type.label + " §7(" + f.pos.getX() + ", " + f.pos.getZ() + ") §8• §f" + dist + "m";
            addRenderableWidget(Button.builder(
                    Component.literal(label),
                    b -> onPick.accept(idx)
            ).bounds(cx - btnW / 2, y, btnW, btnH).build());
            y += btnH + gap;
        }
        addRenderableWidget(Button.builder(
                Component.literal("← Back"),
                b -> { state = State.MAIN; rebuild(); }
        ).bounds(cx - btnW / 2, y + gap, btnW, btnH).build());
    }

    private void rebuild() { this.clearWidgets(); this.init(); }

    @Override
    protected void renderScreenBackground(Gfx context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    protected void renderScreen(Gfx context, int mouseX, int mouseY, float delta) {
        superRender(context, mouseX, mouseY, delta);
        BiomeLocatorModule mod = findModule();
        boolean scanning = mod != null && mod.isEnabled();
        int count = BiomeTracker.getFound().size();
        String title = switch (state) {
            case MAIN -> "§a§lBiome Locator";
            case TYPES -> "§a§lSelect Biomes";
            case WAYPOINT_LIST -> "§a§lWaypoint a Biome";
            case DELETE_LIST -> "§a§lDelete a Biome";
        };
        context.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 110, 0xFFFFFFFF);
        String subtitle = "§7" + count + " found §8• §7scan: " + (scanning ? "§aon" : "§coff");
        context.drawCenteredString(this.font, subtitle, this.width / 2, this.height / 2 - 96, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
