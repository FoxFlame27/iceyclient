package com.iceymod.screen;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.StructureLocatorModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.structure.StructureTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Structure Locator menu — same state-machine shape as WaypointMenuScreen
 * so the UX matches.
 *
 *   MAIN  ▸ Start/Pause · Waypoint it · Delete · Clear all · Close
 *   WAYPOINT_LIST ▸ pick → sends to WaypointManager
 *   DELETE_LIST   ▸ pick → remove from tracker
 */
public class StructureMenuScreen extends Screen {

    private enum State { MAIN, WAYPOINT_LIST, DELETE_LIST, TYPES }
    private State state = State.MAIN;

    public StructureMenuScreen() {
        super(Text.literal("Structure Locator"));
    }

    private StructureLocatorModule findModule() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof StructureLocatorModule sm) return sm;
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
                List<StructureTracker.Found> all = StructureTracker.getSortedByDistance();
                if (idx >= 0 && idx < all.size()) {
                    StructureTracker.Found f = all.get(idx);
                    WaypointManager.addWaypoint(f.type.label, f.pos.getX(), f.pos.getY(), f.pos.getZ());
                }
                state = State.MAIN;
                rebuild();
            });
            case DELETE_LIST -> buildList(cx, btnW, btnH, gap, "§c✖ ", idx -> {
                List<StructureTracker.Found> all = StructureTracker.getSortedByDistance();
                if (idx >= 0 && idx < all.size()) {
                    StructureTracker.Found f = all.get(idx);
                    StructureTracker.remove(f);
                }
                if (StructureTracker.getFound().isEmpty()) state = State.MAIN;
                rebuild();
            });
        }
    }

    private void buildMain(int cx, int btnW, int btnH, int gap) {
        StructureLocatorModule mod = findModule();
        boolean scanning = mod != null && mod.isEnabled();
        int count = StructureTracker.getFound().size();
        int y = this.height / 2 - 80;

        String scanLabel = scanning ? "§e⏸ Pause Finding" : "§a+ Find New Structures";
        addDrawableChild(ButtonWidget.builder(
                Text.literal(scanLabel),
                b -> {
                    if (mod != null) {
                        boolean turningOn = !mod.isEnabled();
                        mod.setEnabled(turningOn);
                        if (turningOn) StructureTracker.rescanNearby();
                    }
                    rebuild();
                }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("§d☑ Select Structures"),
                b -> { state = State.TYPES; rebuild(); }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        ButtonWidget wpBtn = ButtonWidget.builder(
                Text.literal("§b✎ Waypoint a Structure"),
                b -> { state = State.WAYPOINT_LIST; rebuild(); }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build();
        wpBtn.active = count > 0;
        addDrawableChild(wpBtn);
        y += btnH + gap;

        ButtonWidget delBtn = ButtonWidget.builder(
                Text.literal("§c✖ Delete a Structure"),
                b -> { state = State.DELETE_LIST; rebuild(); }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build();
        delBtn.active = count > 0;
        addDrawableChild(delBtn);
        y += btnH + gap;

        ButtonWidget clearBtn = ButtonWidget.builder(
                Text.literal("§c✖ Clear All"),
                b -> {
                    StructureTracker.clear();
                    // Re-sweep currently-loaded chunks so anything still in
                    // range shows back up — otherwise the HUD gets stuck on
                    // "Scanning chunks…" until the player walks to new chunks.
                    StructureTracker.rescanNearby();
                    this.close();
                }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build();
        clearBtn.active = count > 0;
        addDrawableChild(clearBtn);
        y += btnH + gap * 2;

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.cancel"),
                b -> this.close()
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
    }

    /**
     * Two-column grid of toggle buttons, one per structure type. Pressing
     * a button flips the corresponding BoolSetting and re-renders. New
     * findings start respecting the toggle on the next chunk scan.
     */
    private void buildTypes(int cx, int btnW, int btnH, int gap) {
        StructureLocatorModule mod = findModule();
        if (mod == null) {
            state = State.MAIN;
            rebuild();
            return;
        }

        record Row(String label, BoolSetting setting) {}
        Row[] rows = new Row[] {
                new Row("Trial Chambers",   mod.trialChambers),
                new Row("Strongholds",      mod.strongholds),
                new Row("Player Bases",     mod.playerBases),
                new Row("Nether Fortresses",mod.netherFortresses),
                new Row("Bastion Remnants", mod.bastions),
                new Row("End Cities",       mod.endCities),
                new Row("Ocean Monuments",  mod.oceanMonuments),
                new Row("Ancient Cities",   mod.ancientCities),
                new Row("Ruined Portals",   mod.ruinedPortals),
                new Row("Desert Pyramids",  mod.desertPyramids),
                new Row("Villages",         mod.villages)
        };

        int colW = (btnW - gap) / 2;
        int rowsPerCol = (rows.length + 1) / 2;
        int gridH = rowsPerCol * (btnH + gap);
        int y0 = this.height / 2 - gridH / 2 - 10;

        for (int i = 0; i < rows.length; i++) {
            final Row r = rows[i];
            int col = i % 2;
            int row = i / 2;
            int bx = cx - btnW / 2 + col * (colW + gap);
            int by = y0 + row * (btnH + gap);
            String label = (r.setting().get() ? "§a☑ " : "§7☐ ") + r.label();
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(label),
                    b -> {
                        r.setting().set(!r.setting().get());
                        StructureTracker.rescanNearby();
                        rebuild();
                    }
            ).dimensions(bx, by, colW, btnH).build());
        }

        addDrawableChild(ButtonWidget.builder(
                Text.literal("← Back"),
                b -> { state = State.MAIN; rebuild(); }
        ).dimensions(cx - btnW / 2, y0 + gridH + gap * 2, btnW, btnH).build());
    }

    private void buildList(int cx, int btnW, int btnH, int gap, String prefix, java.util.function.IntConsumer onPick) {
        List<StructureTracker.Found> all = StructureTracker.getSortedByDistance();
        MinecraftClient c = MinecraftClient.getInstance();
        int y = this.height / 2 - (all.size() * (btnH + gap)) / 2 - 20;

        for (int i = 0; i < all.size(); i++) {
            final int idx = i;
            StructureTracker.Found f = all.get(i);
            int dist = 0;
            if (c != null && c.player != null) {
                double dx = f.pos.getX() - c.player.getX();
                double dy = f.pos.getY() - c.player.getY();
                double dz = f.pos.getZ() - c.player.getZ();
                dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            String label = prefix + "§r" + f.type.label + " §7(" + f.pos.getX() + ", " + f.pos.getY() + ", " + f.pos.getZ() + ") §8• §f" + dist + "m";
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(label),
                    b -> onPick.accept(idx)
            ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
            y += btnH + gap;
        }

        addDrawableChild(ButtonWidget.builder(
                Text.literal("← Back"),
                b -> { state = State.MAIN; rebuild(); }
        ).dimensions(cx - btnW / 2, y + gap, btnW, btnH).build());
    }

    private void rebuild() {
        this.clearChildren();
        this.init();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        StructureLocatorModule mod = findModule();
        boolean scanning = mod != null && mod.isEnabled();
        int count = StructureTracker.getFound().size();

        String title = switch (state) {
            case MAIN -> "§b§lStructure Locator";
            case TYPES -> "§b§lSelect Structures";
            case WAYPOINT_LIST -> "§b§lWaypoint a Structure";
            case DELETE_LIST -> "§b§lDelete a Structure";
        };
        context.drawCenteredTextWithShadow(this.textRenderer, title, this.width / 2, this.height / 2 - 110, 0xFFFFFFFF);
        String subtitle = "§7" + count + " found §8• §7scan: " + (scanning ? "§aon" : "§coff");
        context.drawCenteredTextWithShadow(this.textRenderer, subtitle, this.width / 2, this.height / 2 - 96, 0xFFAAAAAA);
    }

    @Override
    public boolean shouldPause() { return false; }
}
