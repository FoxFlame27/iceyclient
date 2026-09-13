package com.iceymod.screen;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.StructureLocatorModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.structure.StructureTracker;
import java.util.List;
import net.minecraft.client.Minecraft;
import com.iceymod.compat.Gfx;
import com.iceymod.compat.IceyScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Structure Locator menu — same state-machine shape as WaypointMenuScreen
 * so the UX matches.
 *
 *   MAIN  ▸ Start/Pause · Waypoint it · Delete · Clear all · Close
 *   WAYPOINT_LIST ▸ pick → sends to WaypointManager
 *   DELETE_LIST   ▸ pick → remove from tracker
 */
public class StructureMenuScreen extends IceyScreen {

    private enum State { MAIN, WAYPOINT_LIST, DELETE_LIST, TYPES }
    private State state = State.MAIN;

    public StructureMenuScreen() {
        super(Component.literal("Structure Locator"));
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
        addRenderableWidget(Button.builder(
                Component.literal(scanLabel),
                b -> {
                    if (mod != null) {
                        boolean turningOn = !mod.isEnabled();
                        mod.setEnabled(turningOn);
                        if (turningOn) {
                            // Ensure widget is in a visible spot when first
                            // enabled — applyCenterDefaults only fires on
                            // first launch, so existing configs may have
                            // this buried in the mid-screen info grid.
                            int mx = mod.getX(), my = mod.getY();
                            Minecraft mc = Minecraft.getInstance();
                            if (mc != null && mc.getWindow() != null) {
                                int sw = mc.getWindow().getGuiScaledWidth();
                                int sh = mc.getWindow().getGuiScaledHeight();
                                if (sw > 0 && sh > 0) {
                                    boolean buriedX = mx > sw / 4 && mx < sw * 3 / 4;
                                    boolean buriedY = my > sh / 4 && my < sh * 3 / 4;
                                    if (buriedX || buriedY) {
                                        mod.setX(8);
                                        mod.setY(40);
                                    }
                                }
                            }
                            StructureTracker.rescanNearby();
                        }
                    }
                    rebuild();
                }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addRenderableWidget(Button.builder(
                Component.literal("§d☑ Select Structures"),
                b -> { state = State.TYPES; rebuild(); }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;


        Button wpBtn = Button.builder(
                Component.literal("§b✎ Waypoint a Structure"),
                b -> { state = State.WAYPOINT_LIST; rebuild(); }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build();
        wpBtn.active = count > 0;
        addRenderableWidget(wpBtn);
        y += btnH + gap;

        Button delBtn = Button.builder(
                Component.literal("§c✖ Delete a Structure"),
                b -> { state = State.DELETE_LIST; rebuild(); }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build();
        delBtn.active = count > 0;
        addRenderableWidget(delBtn);
        y += btnH + gap;

        Button clearBtn = Button.builder(
                Component.literal("§c✖ Clear All"),
                b -> {
                    StructureTracker.clear();
                    // Re-sweep currently-loaded chunks so anything still in
                    // range shows back up — otherwise the HUD gets stuck on
                    // "Scanning chunks…" until the player walks to new chunks.
                    StructureTracker.rescanNearby();
                    this.onClose();
                }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build();
        clearBtn.active = count > 0;
        addRenderableWidget(clearBtn);
        y += btnH + gap * 2;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                b -> this.onClose()
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
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
                new Row("End Gateways",     mod.endGateways),
                new Row("Ocean Monuments",  mod.oceanMonuments),
                new Row("Ancient Cities",   mod.ancientCities),
                new Row("Ruined Portals",   mod.ruinedPortals),
                new Row("Desert Pyramids",  mod.desertPyramids),
                new Row("Villages",         mod.villages),
                new Row("Spawners",         mod.spawners)
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
            addRenderableWidget(Button.builder(
                    Component.literal(label),
                    b -> {
                        r.setting().set(!r.setting().get());
                        StructureTracker.rescanNearby();
                        rebuild();
                    }
            ).bounds(bx, by, colW, btnH).build());
        }

        addRenderableWidget(Button.builder(
                Component.literal("← Back"),
                b -> { state = State.MAIN; rebuild(); }
        ).bounds(cx - btnW / 2, y0 + gridH + gap * 2, btnW, btnH).build());
    }

    private void buildList(int cx, int btnW, int btnH, int gap, String prefix, java.util.function.IntConsumer onPick) {
        List<StructureTracker.Found> all = StructureTracker.getSortedByDistance();
        Minecraft c = Minecraft.getInstance();
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
        StructureLocatorModule mod = findModule();
        boolean scanning = mod != null && mod.isEnabled();
        int count = StructureTracker.getFound().size();

        String title = switch (state) {
            case MAIN -> "§b§lStructure Locator";
            case TYPES -> "§b§lSelect Structures";
            case WAYPOINT_LIST -> "§b§lWaypoint a Structure";
            case DELETE_LIST -> "§b§lDelete a Structure";
        };
        context.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 110, 0xFFFFFFFF);
        String subtitle = "§7" + count + " found §8• §7scan: " + (scanning ? "§aon" : "§coff");
        context.drawCenteredString(this.font, subtitle, this.width / 2, this.height / 2 - 96, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
