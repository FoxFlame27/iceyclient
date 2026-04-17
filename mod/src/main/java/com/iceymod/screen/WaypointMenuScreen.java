package com.iceymod.screen;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.modules.WaypointsModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Menu shown when the waypoint keybind is pressed.
 * Options: Set Waypoint Here, Delete (sub-list), Delete All.
 */
public class WaypointMenuScreen extends Screen {

    private boolean showDeleteList = false;

    public WaypointMenuScreen() {
        super(Text.literal("Waypoints"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int btnW = 220;
        int btnH = 22;
        int gap = 6;

        if (showDeleteList) {
            buildDeleteList(cx, btnW, btnH, gap);
        } else {
            buildMainMenu(cx, btnW, btnH, gap);
        }
    }

    private void buildMainMenu(int cx, int btnW, int btnH, int gap) {
        int startY = this.height / 2 - 50;
        int y = startY;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00A7a+ Set Waypoint Here"),
                b -> {
                    HudModule wp = findWaypointsModule();
                    if (wp instanceof WaypointsModule) {
                        wp.setEnabled(true);
                        ((WaypointsModule) wp).addCurrentPosition();
                    }
                    this.close();
                }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        ButtonWidget delBtn = ButtonWidget.builder(
                Text.literal("\u00A7e\u2716 Delete Waypoint"),
                b -> { showDeleteList = true; rebuild(); }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build();
        delBtn.active = !WaypointManager.getWaypoints().isEmpty();
        addDrawableChild(delBtn);
        y += btnH + gap;

        ButtonWidget clearBtn = ButtonWidget.builder(
                Text.literal("\u00A7c\u2716 Delete All"),
                b -> {
                    int n = WaypointManager.getWaypoints().size();
                    for (int i = n - 1; i >= 0; i--) WaypointManager.removeWaypoint(i);
                    this.close();
                }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build();
        clearBtn.active = !WaypointManager.getWaypoints().isEmpty();
        addDrawableChild(clearBtn);
        y += btnH + gap * 2;

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.cancel"),
                b -> this.close()
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
    }

    private void buildDeleteList(int cx, int btnW, int btnH, int gap) {
        List<WaypointManager.Waypoint> wps = WaypointManager.getWaypoints();
        int listStartY = this.height / 2 - (wps.size() * (btnH + gap)) / 2 - 20;
        int y = listStartY;

        for (int i = 0; i < wps.size(); i++) {
            final int idx = i;
            WaypointManager.Waypoint wp = wps.get(i);
            String label = "\u00A7c\u2716 \u00A7r" + wp.name + " \u00A77(" + wp.x + ", " + wp.y + ", " + wp.z + ")";
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(label),
                    b -> {
                        WaypointManager.removeWaypoint(idx);
                        if (WaypointManager.getWaypoints().isEmpty()) showDeleteList = false;
                        rebuild();
                    }
            ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
            y += btnH + gap;
        }

        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2190 Back"),
                b -> { showDeleteList = false; rebuild(); }
        ).dimensions(cx - btnW / 2, y + gap, btnW, btnH).build());
    }

    private void rebuild() {
        this.clearChildren();
        this.init();
    }

    private HudModule findWaypointsModule() {
        for (HudModule m : HudManager.getModules()) {
            if (m.getId().equals("waypoints")) return m;
        }
        return null;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        String title = showDeleteList ? "\u00A7b\u00A7lDelete Waypoint" : "\u00A7b\u00A7lWaypoints";
        context.drawCenteredTextWithShadow(this.textRenderer, title, this.width / 2, this.height / 2 - 90, 0xFFFFFFFF);
        String subtitle = "\u00A77" + WaypointManager.getWaypoints().size() + " saved";
        context.drawCenteredTextWithShadow(this.textRenderer, subtitle, this.width / 2, this.height / 2 - 76, 0xFFAAAAAA);
    }

    @Override
    public boolean shouldPause() { return false; }
}
