package com.iceymod.screen;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.modules.WaypointsModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Waypoint keybind menu. States:
 *   - MAIN: Set here / Rename / Delete / Delete all
 *   - DELETE_LIST: pick a waypoint to delete
 *   - RENAME_LIST: pick a waypoint to rename
 *   - RENAME_INPUT: text field to enter the new name
 */
public class WaypointMenuScreen extends Screen {

    private enum State { MAIN, DELETE_LIST, RENAME_LIST, RENAME_INPUT }
    private State state = State.MAIN;
    private int renamingIndex = -1;
    private TextFieldWidget nameInput;

    public WaypointMenuScreen() {
        super(Text.literal("Waypoints"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int btnW = 220;
        int btnH = 22;
        int gap = 6;

        switch (state) {
            case MAIN -> buildMainMenu(cx, btnW, btnH, gap);
            case DELETE_LIST -> buildList(cx, btnW, btnH, gap, "\u00A7c\u2716 ", idx -> {
                WaypointManager.removeWaypoint(idx);
                if (WaypointManager.getWaypoints().isEmpty()) state = State.MAIN;
                rebuild();
            });
            case RENAME_LIST -> buildList(cx, btnW, btnH, gap, "\u00A7e\u270E ", idx -> {
                renamingIndex = idx;
                state = State.RENAME_INPUT;
                rebuild();
            });
            case RENAME_INPUT -> buildRenameInput(cx, btnW, btnH, gap);
        }
    }

    private void buildMainMenu(int cx, int btnW, int btnH, int gap) {
        int y = this.height / 2 - 60;

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

        ButtonWidget renBtn = ButtonWidget.builder(
                Text.literal("\u00A7e\u270E Rename Waypoint"),
                b -> { state = State.RENAME_LIST; rebuild(); }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build();
        renBtn.active = !WaypointManager.getWaypoints().isEmpty();
        addDrawableChild(renBtn);
        y += btnH + gap;

        ButtonWidget delBtn = ButtonWidget.builder(
                Text.literal("\u00A7c\u2716 Delete Waypoint"),
                b -> { state = State.DELETE_LIST; rebuild(); }
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

    private void buildList(int cx, int btnW, int btnH, int gap, String prefix, java.util.function.IntConsumer onPick) {
        List<WaypointManager.Waypoint> wps = WaypointManager.getWaypoints();
        int y = this.height / 2 - (wps.size() * (btnH + gap)) / 2 - 20;

        for (int i = 0; i < wps.size(); i++) {
            final int idx = i;
            WaypointManager.Waypoint wp = wps.get(i);
            String label = prefix + "\u00A7r" + wp.name + " \u00A77(" + wp.x + ", " + wp.y + ", " + wp.z + ")";
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(label),
                    b -> onPick.accept(idx)
            ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
            y += btnH + gap;
        }

        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2190 Back"),
                b -> { state = State.MAIN; rebuild(); }
        ).dimensions(cx - btnW / 2, y + gap, btnW, btnH).build());
    }

    private void buildRenameInput(int cx, int btnW, int btnH, int gap) {
        if (renamingIndex < 0 || renamingIndex >= WaypointManager.getWaypoints().size()) {
            state = State.MAIN;
            rebuild();
            return;
        }
        WaypointManager.Waypoint wp = WaypointManager.getWaypoints().get(renamingIndex);
        int y = this.height / 2 - 20;

        nameInput = new TextFieldWidget(this.textRenderer, cx - btnW / 2, y, btnW, btnH, Text.literal(""));
        nameInput.setMaxLength(32);
        nameInput.setText(wp.name);
        nameInput.setFocused(true);
        addDrawableChild(nameInput);
        setInitialFocus(nameInput);

        y += btnH + gap;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00A7aSave"),
                b -> {
                    WaypointManager.renameWaypoint(renamingIndex, nameInput.getText());
                    renamingIndex = -1;
                    state = State.MAIN;
                    rebuild();
                }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("\u2190 Cancel"),
                b -> {
                    renamingIndex = -1;
                    state = State.RENAME_LIST;
                    rebuild();
                }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (state == State.RENAME_INPUT && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && nameInput != null) {
            WaypointManager.renameWaypoint(renamingIndex, nameInput.getText());
            renamingIndex = -1;
            state = State.MAIN;
            rebuild();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        String title = switch (state) {
            case MAIN -> "\u00A7b\u00A7lWaypoints";
            case DELETE_LIST -> "\u00A7b\u00A7lDelete Waypoint";
            case RENAME_LIST -> "\u00A7b\u00A7lRename Waypoint";
            case RENAME_INPUT -> "\u00A7b\u00A7lNew Name";
        };
        context.drawCenteredTextWithShadow(this.textRenderer, title, this.width / 2, this.height / 2 - 100, 0xFFFFFFFF);
        if (state != State.RENAME_INPUT) {
            String subtitle = "\u00A77" + WaypointManager.getWaypoints().size() + " saved";
            context.drawCenteredTextWithShadow(this.textRenderer, subtitle, this.width / 2, this.height / 2 - 86, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
