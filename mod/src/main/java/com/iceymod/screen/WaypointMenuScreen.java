package com.iceymod.screen;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.modules.WaypointsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Waypoint keybind menu. States:
 *   - MAIN: Set here / Rename / Edit coords / Delete / Delete all
 *   - *_LIST: pick a waypoint for the given action
 *   - RENAME_INPUT / EDIT_INPUT: input field(s) for the chosen waypoint
 */
public class WaypointMenuScreen extends Screen {

    private enum State { MAIN, DELETE_LIST, RENAME_LIST, RENAME_INPUT, EDIT_LIST, EDIT_INPUT }
    private State state = State.MAIN;
    private int actionIndex = -1;
    private TextFieldWidget nameInput;
    private TextFieldWidget xInput, yInput, zInput;

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
            case DELETE_LIST -> buildList(cx, btnW, btnH, gap, "§c✖ ", idx -> {
                WaypointManager.removeWaypoint(idx);
                if (WaypointManager.getWaypoints().isEmpty()) state = State.MAIN;
                rebuild();
            });
            case RENAME_LIST -> buildList(cx, btnW, btnH, gap, "§e✎ ", idx -> {
                actionIndex = idx;
                state = State.RENAME_INPUT;
                rebuild();
            });
            case EDIT_LIST -> buildList(cx, btnW, btnH, gap, "§b✎ ", idx -> {
                actionIndex = idx;
                state = State.EDIT_INPUT;
                rebuild();
            });
            case RENAME_INPUT -> buildRenameInput(cx, btnW, btnH, gap);
            case EDIT_INPUT -> buildEditInput(cx, btnW, btnH, gap);
        }
    }

    private void buildMainMenu(int cx, int btnW, int btnH, int gap) {
        int y = this.height / 2 - 76;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("§a+ Set Waypoint Here"),
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
                Text.literal("§e✎ Rename Waypoint"),
                b -> { state = State.RENAME_LIST; rebuild(); }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build();
        renBtn.active = !WaypointManager.getWaypoints().isEmpty();
        addDrawableChild(renBtn);
        y += btnH + gap;

        ButtonWidget editBtn = ButtonWidget.builder(
                Text.literal("§b✎ Edit Coordinates"),
                b -> { state = State.EDIT_LIST; rebuild(); }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build();
        editBtn.active = !WaypointManager.getWaypoints().isEmpty();
        addDrawableChild(editBtn);
        y += btnH + gap;

        ButtonWidget delBtn = ButtonWidget.builder(
                Text.literal("§c✖ Delete Waypoint"),
                b -> { state = State.DELETE_LIST; rebuild(); }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build();
        delBtn.active = !WaypointManager.getWaypoints().isEmpty();
        addDrawableChild(delBtn);
        y += btnH + gap;

        ButtonWidget clearBtn = ButtonWidget.builder(
                Text.literal("§c✖ Delete All"),
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
            String label = prefix + "§r" + wp.name + " §7(" + wp.x + ", " + wp.y + ", " + wp.z + ")";
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

    private void buildRenameInput(int cx, int btnW, int btnH, int gap) {
        if (actionIndex < 0 || actionIndex >= WaypointManager.getWaypoints().size()) {
            state = State.MAIN;
            rebuild();
            return;
        }
        WaypointManager.Waypoint wp = WaypointManager.getWaypoints().get(actionIndex);
        int y = this.height / 2 - 20;

        nameInput = new TextFieldWidget(this.textRenderer, cx - btnW / 2, y, btnW, btnH, Text.literal(""));
        nameInput.setMaxLength(32);
        nameInput.setText(wp.name);
        nameInput.setFocused(true);
        addDrawableChild(nameInput);
        setInitialFocus(nameInput);

        y += btnH + gap;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("§aSave"),
                b -> commitRename()
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("← Cancel"),
                b -> {
                    actionIndex = -1;
                    state = State.RENAME_LIST;
                    rebuild();
                }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
    }

    private void buildEditInput(int cx, int btnW, int btnH, int gap) {
        if (actionIndex < 0 || actionIndex >= WaypointManager.getWaypoints().size()) {
            state = State.MAIN;
            rebuild();
            return;
        }
        WaypointManager.Waypoint wp = WaypointManager.getWaypoints().get(actionIndex);
        int y = this.height / 2 - 40;
        int fieldW = btnW / 3 - 4;

        // Three side-by-side X Y Z fields
        int fx = cx - btnW / 2;
        xInput = new TextFieldWidget(this.textRenderer, fx, y, fieldW, btnH, Text.literal(""));
        xInput.setText(String.valueOf(wp.x));
        xInput.setMaxLength(8);
        addDrawableChild(xInput);

        yInput = new TextFieldWidget(this.textRenderer, fx + fieldW + 6, y, fieldW, btnH, Text.literal(""));
        yInput.setText(String.valueOf(wp.y));
        yInput.setMaxLength(8);
        addDrawableChild(yInput);

        zInput = new TextFieldWidget(this.textRenderer, fx + (fieldW + 6) * 2, y, fieldW, btnH, Text.literal(""));
        zInput.setText(String.valueOf(wp.z));
        zInput.setMaxLength(8);
        addDrawableChild(zInput);

        setInitialFocus(xInput);

        y += btnH + gap;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("§eUse My Current Position"),
                b -> {
                    MinecraftClient c = MinecraftClient.getInstance();
                    if (c != null && c.player != null) {
                        xInput.setText(String.valueOf((int) c.player.getX()));
                        yInput.setText(String.valueOf((int) c.player.getY()));
                        zInput.setText(String.valueOf((int) c.player.getZ()));
                    }
                }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("§aSave"),
                b -> commitEdit()
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("← Cancel"),
                b -> {
                    actionIndex = -1;
                    state = State.EDIT_LIST;
                    rebuild();
                }
        ).dimensions(cx - btnW / 2, y, btnW, btnH).build());
    }

    private void commitRename() {
        WaypointManager.renameWaypoint(actionIndex, nameInput.getText());
        actionIndex = -1;
        state = State.MAIN;
        rebuild();
    }

    private void commitEdit() {
        try {
            int x = Integer.parseInt(xInput.getText().trim());
            int y = Integer.parseInt(yInput.getText().trim());
            int z = Integer.parseInt(zInput.getText().trim());
            WaypointManager.updateWaypointCoords(actionIndex, x, y, z);
            actionIndex = -1;
            state = State.MAIN;
            rebuild();
        } catch (NumberFormatException e) {
            // Leave the input visible so the user can fix it.
        }
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

    // 1.21.11 changed Screen.keyPressed's signature so our (int,int,int)
    // override stops firing. Poll Enter via raw GLFW in render() with
    // edge detection — works on both 1.21.8 and 1.21.11.
    private boolean prevEnterDown = false;

    private void pollEnter() {
        try {
            long handle = client.getWindow().getHandle();
            boolean down = org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (down && !prevEnterDown) {
                if (state == State.RENAME_INPUT && nameInput != null) { commitRename(); }
                else if (state == State.EDIT_INPUT && xInput != null) { commitEdit(); }
            }
            prevEnterDown = down;
        } catch (Throwable ignored) {}
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        pollEnter();
        super.render(context, mouseX, mouseY, delta);
        String title = switch (state) {
            case MAIN -> "§b§lWaypoints";
            case DELETE_LIST -> "§b§lDelete Waypoint";
            case RENAME_LIST -> "§b§lRename Waypoint";
            case EDIT_LIST -> "§b§lEdit Coordinates";
            case RENAME_INPUT -> "§b§lNew Name";
            case EDIT_INPUT -> "§b§lEdit Coordinates";
        };
        context.drawCenteredTextWithShadow(this.textRenderer, title, this.width / 2, this.height / 2 - 110, 0xFFFFFFFF);
        if (state != State.RENAME_INPUT && state != State.EDIT_INPUT) {
            String subtitle = "§7" + WaypointManager.getWaypoints().size() + " saved";
            context.drawCenteredTextWithShadow(this.textRenderer, subtitle, this.width / 2, this.height / 2 - 96, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
