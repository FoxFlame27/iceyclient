package com.iceymod.screen;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.modules.WaypointsModule;
import java.util.List;
import net.minecraft.client.Minecraft;
import com.iceymod.compat.Gfx;
import com.iceymod.compat.IceyScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Waypoint keybind menu. States:
 *   - MAIN: Set here / Rename / Edit coords / Delete / Delete all
 *   - *_LIST: pick a waypoint for the given action
 *   - RENAME_INPUT / EDIT_INPUT: input field(s) for the chosen waypoint
 */
public class WaypointMenuScreen extends IceyScreen {

    private enum State { MAIN, DELETE_LIST, RENAME_LIST, RENAME_INPUT, EDIT_LIST, EDIT_INPUT, COLOR_LIST }
    private State state = State.MAIN;
    private int actionIndex = -1;
    private EditBox nameInput;
    private EditBox xInput, yInput, zInput;

    public WaypointMenuScreen() {
        super(Component.literal("Waypoints"));
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
            case COLOR_LIST -> buildList(cx, btnW, btnH, gap, "§d🎨 ", idx -> {
                var list = WaypointManager.getWaypoints();
                if (idx < 0 || idx >= list.size()) return;
                WaypointManager.Waypoint wp = list.get(idx);
                final int targetIdx = idx;
                if (this.minecraft != null) {
                    com.iceymod.compat.MC.setScreen(this.minecraft, new ColorPickerScreen(
                            wp.color, "Waypoint Color: " + wp.name,
                            color -> WaypointManager.updateWaypointColor(targetIdx, color),
                            this));
                }
            });
            case RENAME_INPUT -> buildRenameInput(cx, btnW, btnH, gap);
            case EDIT_INPUT -> buildEditInput(cx, btnW, btnH, gap);
        }
    }

    private void buildMainMenu(int cx, int btnW, int btnH, int gap) {
        int y = this.height / 2 - 76;

        addRenderableWidget(Button.builder(
                Component.literal("§a+ Set Waypoint Here"),
                b -> {
                    HudModule wp = findWaypointsModule();
                    if (wp instanceof WaypointsModule) {
                        wp.setEnabled(true);
                        ((WaypointsModule) wp).addCurrentPosition();
                        // Snap widget into a visible spot if it's currently
                        // buried mid-screen (applyCenterDefaults only fires
                        // on first launch, so existing configs may have
                        // this hidden behind other modules).
                        int wx = wp.getX(), wy = wp.getY();
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null && mc.getWindow() != null) {
                            int sw = mc.getWindow().getGuiScaledWidth();
                            int sh = mc.getWindow().getGuiScaledHeight();
                            if (sw > 0 && sh > 0) {
                                boolean buriedX = wx > sw / 4 && wx < sw * 3 / 4;
                                boolean buriedY = wy > sh / 4 && wy < sh * 3 / 4;
                                if (buriedX || buriedY) {
                                    wp.setX(8);
                                    wp.setY(200);
                                }
                            }
                        }
                    }
                    this.onClose();
                }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        Button renBtn = Button.builder(
                Component.literal("§e✎ Rename Waypoint"),
                b -> { state = State.RENAME_LIST; rebuild(); }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build();
        renBtn.active = !WaypointManager.getWaypoints().isEmpty();
        addRenderableWidget(renBtn);
        y += btnH + gap;

        Button editBtn = Button.builder(
                Component.literal("§b✎ Edit Coordinates"),
                b -> { state = State.EDIT_LIST; rebuild(); }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build();
        editBtn.active = !WaypointManager.getWaypoints().isEmpty();
        addRenderableWidget(editBtn);
        y += btnH + gap;

        Button colorBtn = Button.builder(
                Component.literal("§d🎨 Recolor Waypoint"),
                b -> { state = State.COLOR_LIST; rebuild(); }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build();
        colorBtn.active = !WaypointManager.getWaypoints().isEmpty();
        addRenderableWidget(colorBtn);
        y += btnH + gap;

        Button delBtn = Button.builder(
                Component.literal("§c✖ Delete Waypoint"),
                b -> { state = State.DELETE_LIST; rebuild(); }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build();
        delBtn.active = !WaypointManager.getWaypoints().isEmpty();
        addRenderableWidget(delBtn);
        y += btnH + gap;

        Button clearBtn = Button.builder(
                Component.literal("§c✖ Delete All"),
                b -> {
                    int n = WaypointManager.getWaypoints().size();
                    for (int i = n - 1; i >= 0; i--) WaypointManager.removeWaypoint(i);
                    this.onClose();
                }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build();
        clearBtn.active = !WaypointManager.getWaypoints().isEmpty();
        addRenderableWidget(clearBtn);
        y += btnH + gap * 2;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                b -> this.onClose()
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
    }

    private void buildList(int cx, int btnW, int btnH, int gap, String prefix, java.util.function.IntConsumer onPick) {
        List<WaypointManager.Waypoint> wps = WaypointManager.getWaypoints();
        int y = this.height / 2 - (wps.size() * (btnH + gap)) / 2 - 20;

        for (int i = 0; i < wps.size(); i++) {
            final int idx = i;
            WaypointManager.Waypoint wp = wps.get(i);
            String label = prefix + "§r" + wp.name + " §7(" + wp.x + ", " + wp.y + ", " + wp.z + ")";
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

    private void buildRenameInput(int cx, int btnW, int btnH, int gap) {
        if (actionIndex < 0 || actionIndex >= WaypointManager.getWaypoints().size()) {
            state = State.MAIN;
            rebuild();
            return;
        }
        WaypointManager.Waypoint wp = WaypointManager.getWaypoints().get(actionIndex);
        int y = this.height / 2 - 20;

        nameInput = new EditBox(this.font, cx - btnW / 2, y, btnW, btnH, Component.literal(""));
        nameInput.setMaxLength(32);
        nameInput.setValue(wp.name);
        nameInput.setFocused(true);
        addRenderableWidget(nameInput);
        setInitialFocus(nameInput);

        y += btnH + gap;
        addRenderableWidget(Button.builder(
                Component.literal("§aSave"),
                b -> commitRename()
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addRenderableWidget(Button.builder(
                Component.literal("← Cancel"),
                b -> {
                    actionIndex = -1;
                    state = State.RENAME_LIST;
                    rebuild();
                }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
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
        xInput = new EditBox(this.font, fx, y, fieldW, btnH, Component.literal(""));
        xInput.setValue(String.valueOf(wp.x));
        xInput.setMaxLength(8);
        addRenderableWidget(xInput);

        yInput = new EditBox(this.font, fx + fieldW + 6, y, fieldW, btnH, Component.literal(""));
        yInput.setValue(String.valueOf(wp.y));
        yInput.setMaxLength(8);
        addRenderableWidget(yInput);

        zInput = new EditBox(this.font, fx + (fieldW + 6) * 2, y, fieldW, btnH, Component.literal(""));
        zInput.setValue(String.valueOf(wp.z));
        zInput.setMaxLength(8);
        addRenderableWidget(zInput);

        setInitialFocus(xInput);

        y += btnH + gap;
        addRenderableWidget(Button.builder(
                Component.literal("§eUse My Current Position"),
                b -> {
                    Minecraft c = Minecraft.getInstance();
                    if (c != null && c.player != null) {
                        xInput.setValue(String.valueOf((int) c.player.getX()));
                        yInput.setValue(String.valueOf((int) c.player.getY()));
                        zInput.setValue(String.valueOf((int) c.player.getZ()));
                    }
                }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addRenderableWidget(Button.builder(
                Component.literal("§aSave"),
                b -> commitEdit()
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
        y += btnH + gap;

        addRenderableWidget(Button.builder(
                Component.literal("← Cancel"),
                b -> {
                    actionIndex = -1;
                    state = State.EDIT_LIST;
                    rebuild();
                }
        ).bounds(cx - btnW / 2, y, btnW, btnH).build());
    }

    private void commitRename() {
        WaypointManager.renameWaypoint(actionIndex, nameInput.getValue());
        actionIndex = -1;
        state = State.MAIN;
        rebuild();
    }

    private void commitEdit() {
        try {
            int x = Integer.parseInt(xInput.getValue().trim());
            int y = Integer.parseInt(yInput.getValue().trim());
            int z = Integer.parseInt(zInput.getValue().trim());
            WaypointManager.updateWaypointCoords(actionIndex, x, y, z);
            actionIndex = -1;
            state = State.MAIN;
            rebuild();
        } catch (NumberFormatException e) {
            // Leave the input visible so the user can fix it.
        }
    }

    private void rebuild() {
        this.clearWidgets();
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
            long handle = minecraft.getWindow().handle();
            boolean down = org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (down && !prevEnterDown) {
                if (state == State.RENAME_INPUT && nameInput != null) { commitRename(); }
                else if (state == State.EDIT_INPUT && xInput != null) { commitEdit(); }
            }
            prevEnterDown = down;
        } catch (Throwable ignored) {}
    }

    @Override
    protected void renderScreenBackground(Gfx context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    protected void renderScreen(Gfx context, int mouseX, int mouseY, float delta) {
        pollEnter();
        superRender(context, mouseX, mouseY, delta);
        String title = switch (state) {
            case MAIN -> "§b§lWaypoints";
            case DELETE_LIST -> "§b§lDelete Waypoint";
            case RENAME_LIST -> "§b§lRename Waypoint";
            case EDIT_LIST -> "§b§lEdit Coordinates";
            case RENAME_INPUT -> "§b§lNew Name";
            case EDIT_INPUT -> "§b§lEdit Coordinates";
            case COLOR_LIST -> "§d§lRecolor Waypoint";
        };
        context.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 110, 0xFFFFFFFF);
        if (state != State.RENAME_INPUT && state != State.EDIT_INPUT) {
            String subtitle = "§7" + WaypointManager.getWaypoints().size() + " saved";
            context.drawCenteredString(this.font, subtitle, this.width / 2, this.height / 2 - 96, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
