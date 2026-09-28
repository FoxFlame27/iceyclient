package com.iceymod.screen;

import com.iceymod.Branding;
import com.iceymod.MenuPrefs;
import com.iceymod.compat.Gfx;
import com.iceymod.compat.IceyScreen;
import com.iceymod.compat.MC;
import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import com.iceymod.hud.settings.ColorSetting;
import com.iceymod.hud.settings.DoubleSetting;
import com.iceymod.hud.settings.EnumSetting;
import com.iceymod.hud.settings.IntSetting;
import com.iceymod.hud.settings.Setting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Y menu in its "Panels" style: one glass column per group, modules
 * as rows (click to toggle), settings unfolding in place on right-click.
 *
 * The whole menu is drawn on its own canvas, at least 640 wide, whatever
 * the GUI scale is set to, so five columns fit on every window size. The
 * canvas scale is always a whole number of screen pixels per font pixel,
 * which keeps the text sharp.
 *
 * Mouse buttons are polled (see HudEditScreen for why): the click
 * callbacks changed signature between Minecraft versions.
 */
public class PanelMenuScreen extends IceyScreen {

    private enum Group {
        HUD("HUD"), COMBAT("Combat"), WORLD("World"), PLAYER("Player"), PERFORMANCE("Performance");

        final String title;
        Group(String title) { this.title = title; }
    }

    /** Modules shown under World / Player; everything else follows its category. */
    private static final Set<String> WORLD_IDS = Set.of(
            "bedcoords", "biomelocator", "structurelocator", "waypoints", "chunk", "nethercoords",
            "lookat", "hostilecount", "nearesthostile", "mobkills", "blocksmined", "distwalked",
            "hitboxes", "itemglow", "biome", "weather", "worldtime", "daycounter", "entitycount",
            "blockunder");
    private static final Set<String> PLAYER_IDS = Set.of(
            "zoom", "perspective", "freelook", "freecam", "fullbright", "autosprint", "autorespawn",
            "nohurtcam", "nobob", "nofovchange", "antiafk", "safewalk", "automaceswap", "autototem",
            "scrollbinds");

    private static final int GROUPS = Group.values().length;
    private static final int MIN_CANVAS_W = 640;
    private static final int PANEL_W = 116;
    private static final int GAP = 8;
    private static final int HEADER_H = 24;
    private static final int PAD = 5;
    private static final int ROW_H = 16;
    private static final int ROW_GAP = 2;
    private static final int RADIUS = 5;
    private static final int TOP_BAR_MID = 20;
    private static final int SEARCH_W = 200;
    private static final int SEARCH_Y = 10;
    private static final int SEARCH_H = 20;

    private static final int PANEL_BG = 0xD60A0E1A;
    private static final int HAIRLINE = 0x2EFFFFFF;
    private static final int TEXT = 0xFFC9D1DE;
    private static final int TEXT_BRIGHT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF7F8AA0;

    // Kept between openings so the menu comes back the way it was left.
    private static final int[] scroll = new int[GROUPS];
    private static final Set<String> expanded = new HashSet<>();
    private static String searchQuery = "";
    private static final ItemStack[] icons = new ItemStack[GROUPS];

    private EditBox searchField;

    // Canvas
    private float scale = 1f;
    private int vw, vh;
    // Mouse, in canvas coordinates
    private int mx, my;
    private boolean leftDown, leftPressed, rightPressed;
    private boolean prevLeft = true, prevRight = true;
    private Setting<?> dragging;
    private int dragX, dragW;
    private String tooltip;

    // Panel rectangles from the last frame, for the scroll wheel.
    private final int[] panelX = new int[GROUPS];
    private final int[] panelY = new int[GROUPS];
    private final int[] panelH = new int[GROUPS];
    private final int[] maxScroll = new int[GROUPS];
    private int panelW = PANEL_W;

    public PanelMenuScreen() {
        super(Component.literal(Branding.name()));
    }

    @Override
    protected void init() {
        // A click that opened or returned to this screen must not count as a click in it.
        prevLeft = true;
        prevRight = true;
        dragging = null;

        // The field lives on the canvas like everything else, so it is laid
        // out in canvas coordinates and drawn inside the canvas transform.
        computeCanvas();
        searchField = new EditBox(this.font, vw / 2 - SEARCH_W / 2 + 10, SEARCH_Y + 6,
                SEARCH_W - 20, 12, Component.literal(""));
        searchField.setBordered(false);
        searchField.setMaxLength(32);
        searchField.setHint(Component.literal("§7Search modules…"));
        searchField.setValue(searchQuery);
        searchField.setResponder(s -> {
            if (!s.equals(searchQuery)) {
                searchQuery = s;
                java.util.Arrays.fill(scroll, 0);
            }
        });
        addRenderableWidget(searchField);
    }

    // ── Frame ────────────────────────────────────────────────────────────

    @Override
    protected void renderScreenBackground(Gfx g, int mouseX, int mouseY, float delta) {
        // Own veil instead of the vanilla blur (double-blur issues on recent versions).
        g.fill(0, 0, this.width, this.height, 0xB0070A14);
    }

    @Override
    protected void renderScreen(Gfx g, int mouseX, int mouseY, float delta) {
        computeCanvas();
        try { pollMouse(mouseX, mouseY); } catch (Throwable ignored) {}
        tooltip = null;
        int accent = MenuPrefs.accent();

        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        try {
            drawSearch(g, accent, delta);
            drawTopBar(g, accent);
            drawPanels(g, accent);
            drawFooter(g, accent);
            drawTooltip(g);
        } finally {
            g.pose().popMatrix();
        }

        if (!leftDown && dragging != null) {
            dragging = null;
            HudManager.save();
        }
    }

    private void computeCanvas() {
        int fbW = Math.max(1, minecraft.getWindow().getWidth());
        double guiScale = fbW / (double) Math.max(1, this.width);
        int pixelsPerPoint = Math.max(1, Math.min((int) Math.round(guiScale), fbW / MIN_CANVAS_W));
        scale = (float) Math.min(1.0, pixelsPerPoint / guiScale);
        vw = (int) Math.ceil(this.width / scale);
        vh = (int) Math.ceil(this.height / scale);
    }

    private void pollMouse(int mouseX, int mouseY) {
        long handle = minecraft.getWindow().handle();
        leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rightDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        leftPressed = leftDown && !prevLeft;
        rightPressed = rightDown && !prevRight;
        prevLeft = leftDown;
        prevRight = rightDown;
        mx = (int) (mouseX / scale);
        my = (int) (mouseY / scale);
    }

    private boolean over(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ── Top bar: name on the left, search, colour on the right ───────────

    private void drawSearch(Gfx g, int accent, float delta) {
        int x = vw / 2 - SEARCH_W / 2;
        boolean hover = over(x, SEARCH_Y, SEARCH_W, SEARCH_H);
        // The field's own click area is in GUI coordinates, which the canvas
        // transform doesn't move; focus follows where it is drawn instead.
        if (leftPressed && searchField != null) {
            searchField.setFocused(hover);
            setFocused(hover ? searchField : null);
            if (hover) leftPressed = false;
        }
        boolean focused = searchField != null && searchField.isFocused();
        Glass.fill(g, x, SEARCH_Y, SEARCH_W, SEARCH_H, RADIUS, 0xC80A0E1A);
        Glass.outline(g, x, SEARCH_Y, SEARCH_W, SEARCH_H, RADIUS,
                focused ? Glass.alpha(accent, 0xC0) : hover ? 0x50FFFFFF : HAIRLINE);
        superRender(g, mx, my, delta);
    }

    private void drawTopBar(Gfx g, int accent) {
        int midY = TOP_BAR_MID;

        List<HudModule> all = HudManager.getModules();
        int on = 0;
        for (HudModule m : all) if (m.isEnabled()) on++;
        String name = Branding.name().toUpperCase();
        g.drawString(font, name, 14, midY - 9, Glass.lighten(accent, 0.2f), false);
        g.drawString(font, all.size() + " modules · " + on + " on", 14, midY + 2, MUTED, false);

        int size = 10, gap = 5;
        int rowW = MenuPrefs.ACCENTS.length * size + (MenuPrefs.ACCENTS.length - 1) * gap;
        int x = vw - 14 - rowW;
        int y = midY - size / 2;
        for (int i = 0; i < MenuPrefs.ACCENTS.length; i++) {
            int c = MenuPrefs.ACCENTS[i];
            int cx = x + i * (size + gap);
            boolean selected = (c & 0xFFFFFF) == (accent & 0xFFFFFF);
            boolean hover = over(cx - 2, y - 2, size + 4, size + 4);
            if (selected || hover) {
                Glass.outline(g, cx - 2, y - 2, size + 4, size + 4, 5, selected ? 0xFFFFFFFF : 0x80FFFFFF);
            }
            Glass.fill(g, cx, y, size, size, 4, c);
            if (hover) {
                tooltip = MenuPrefs.ACCENT_NAMES[i];
                if (leftPressed) {
                    leftPressed = false;
                    MenuPrefs.setAccent(c);
                }
            }
        }
        String label = "Colour";
        g.drawString(font, label, x - font.width(label) - 8, midY - 4, MUTED, false);
    }

    // ── Panels ───────────────────────────────────────────────────────────

    private static Group groupOf(HudModule m) {
        if (m.getCategory() == HudModule.Category.OPTIMIZATION) return Group.PERFORMANCE;
        if (WORLD_IDS.contains(m.getId())) return Group.WORLD;
        if (PLAYER_IDS.contains(m.getId())) return Group.PLAYER;
        return m.getCategory() == HudModule.Category.COMBAT ? Group.COMBAT : Group.HUD;
    }

    /** Row label. The Performance column drops the "FPS:" / "Net:" prefixes its header makes redundant. */
    private static String labelOf(HudModule m, Group group) {
        String n = m.getName();
        if (group == Group.PERFORMANCE && (n.startsWith("FPS: ") || n.startsWith("Net: "))) return n.substring(5);
        return n;
    }

    private static ItemStack iconOf(Group group) {
        int i = group.ordinal();
        if (icons[i] == null) {
            try {
                icons[i] = new ItemStack(switch (group) {
                    case HUD -> Items.CLOCK;
                    case COMBAT -> Items.DIAMOND_SWORD;
                    case WORLD -> Items.COMPASS;
                    case PLAYER -> Items.FEATHER;
                    case PERFORMANCE -> Items.REDSTONE;
                });
            } catch (Throwable t) {
                icons[i] = ItemStack.EMPTY;
            }
        }
        return icons[i];
    }

    private void drawPanels(Gfx g, int accent) {
        panelW = Math.max(60, Math.min(PANEL_W, (vw - 20 - (GROUPS - 1) * GAP) / GROUPS));
        int totalW = GROUPS * panelW + (GROUPS - 1) * GAP;
        int startX = (vw - totalW) / 2;
        int top = SEARCH_Y + SEARCH_H + 14;
        int maxH = Math.max(HEADER_H + 2 * PAD + ROW_H, vh - top - 44);

        String q = searchQuery == null ? "" : searchQuery.toLowerCase().trim();
        List<List<HudModule>> lists = new ArrayList<>();
        int[] total = new int[GROUPS];
        int[] enabled = new int[GROUPS];
        for (int i = 0; i < GROUPS; i++) lists.add(new ArrayList<>());
        for (HudModule m : HudManager.getModules()) {
            int i = groupOf(m).ordinal();
            total[i]++;
            if (m.isEnabled()) enabled[i]++;
            if (q.isEmpty() || m.getName().toLowerCase().contains(q)) lists.get(i).add(m);
        }

        for (Group group : Group.values()) {
            int i = group.ordinal();
            drawPanel(g, group, lists.get(i), enabled[i], total[i],
                    startX + i * (panelW + GAP), top, maxH, accent);
        }
    }

    private void drawPanel(Gfx g, Group group, List<HudModule> modules, int enabled, int total,
                           int x, int top, int maxH, int accent) {
        int i = group.ordinal();
        int contentH = 0;
        for (HudModule m : modules) {
            contentH += ROW_H + ROW_GAP;
            if (isExpanded(m)) contentH += settingsHeight(m) + ROW_GAP;
        }
        contentH = modules.isEmpty() ? ROW_H : contentH - ROW_GAP;
        int viewH = Math.min(contentH, maxH - HEADER_H - 2 * PAD);
        int h = HEADER_H + PAD + viewH + PAD;

        panelX[i] = x;
        panelY[i] = top;
        panelH[i] = h;
        maxScroll[i] = Math.max(0, contentH - viewH);
        scroll[i] = Math.max(0, Math.min(scroll[i], maxScroll[i]));

        Glass.fill(g, x, top, panelW, h, RADIUS, PANEL_BG);
        Glass.fillTop(g, x, top, panelW, HEADER_H, RADIUS, 0x12FFFFFF);
        g.fill(x + 1, top + HEADER_H - 1, x + panelW - 1, top + HEADER_H, Glass.alpha(accent, 0x66));
        Glass.outline(g, x, top, panelW, h, RADIUS, HAIRLINE);

        try { g.renderItem(iconOf(group), x + 5, top + 4); } catch (Throwable ignored) {}
        g.drawString(font, group.title, x + 25, top + 8, TEXT_BRIGHT, false);
        String count = enabled + "/" + total;
        int countX = x + panelW - 7 - font.width(count);
        if (countX > x + 27 + font.width(group.title)) {
            g.drawString(font, count, countX, top + 8, MUTED, false);
        }

        int cx = x + PAD;
        int cw = panelW - 2 * PAD;
        int cy = top + HEADER_H + PAD;
        if (modules.isEmpty()) {
            g.drawCenteredString(font, "§8No matches", x + panelW / 2, cy + 4, 0xFFFFFFFF);
            return;
        }

        boolean inView = over(cx, cy, cw, viewH);
        g.enableScissor(cx, cy, cx + cw, cy + viewH);
        try {
            int y = cy - scroll[i];
            for (HudModule m : modules) {
                boolean visible = y + ROW_H > cy && y < cy + viewH;
                if (visible) drawRow(g, m, group, cx, y, cw, inView, accent);
                y += ROW_H + ROW_GAP;
                if (isExpanded(m)) {
                    int sh = settingsHeight(m);
                    if (y + sh > cy && y < cy + viewH) drawSettings(g, m, cx, y, cw, inView, accent);
                    y += sh + ROW_GAP;
                }
            }
        } finally {
            g.disableScissor();
        }

        if (maxScroll[i] > 0) {
            int trackX = x + panelW - 3;
            int thumbH = Math.max(12, viewH * viewH / contentH);
            int thumbY = cy + (viewH - thumbH) * scroll[i] / maxScroll[i];
            g.fill(trackX, cy, trackX + 1, cy + viewH, 0x18FFFFFF);
            g.fill(trackX, thumbY, trackX + 1, thumbY + thumbH, Glass.alpha(accent, 0xC0));
        }
    }

    private static boolean isExpanded(HudModule m) {
        return expanded.contains(m.getId());
    }

    private void drawRow(Gfx g, HudModule m, Group group, int x, int y, int w, boolean inView, int accent) {
        boolean on = m.isEnabled();
        boolean hasSettings = !m.getSettings().isEmpty();
        boolean hover = inView && over(x, y, w, ROW_H);

        int bg = on ? Glass.alpha(accent, hover ? 0x40 : 0x26) : (hover ? 0x24FFFFFF : 0x0EFFFFFF);
        Glass.fill(g, x, y, w, ROW_H, 3, bg);
        if (on) g.fill(x, y + 4, x + 2, y + ROW_H - 4, accent);

        int right = x + w - 6;
        Glass.fill(g, right - 4, y + 6, 4, 4, 2, on ? accent : 0x50FFFFFF);
        right -= 9;
        if (hasSettings) {
            String mark = isExpanded(m) ? "-" : "+";
            g.drawString(font, mark, right - font.width(mark), y + 4, hover ? TEXT : MUTED, false);
            right -= 9;
        }

        String label = labelOf(m, group);
        int room = right - (x + 7);
        String shown = fit(label, room);
        int color = on ? Glass.lighten(accent, 0.3f) : (hover ? TEXT_BRIGHT : TEXT);
        g.drawString(font, shown, x + 7, y + 4, color, false);

        if (!hover) return;
        if (!shown.equals(m.getName())) tooltip = m.getName();
        if (leftPressed) {
            leftPressed = false;
            m.toggle();
            HudManager.save();
        } else if (rightPressed) {
            // Modules without options unfold too, to say so.
            rightPressed = false;
            if (!expanded.remove(m.getId())) expanded.add(m.getId());
        }
    }

    // ── Settings, unfolded under a row ───────────────────────────────────

    private static boolean isSlider(Setting<?> s) {
        return s instanceof IntSetting || s instanceof DoubleSetting;
    }

    private static int settingsHeight(HudModule m) {
        if (m.getSettings().isEmpty()) return 15;
        int h = 6;
        for (Setting<?> s : m.getSettings()) h += isSlider(s) ? 21 : 13;
        return h;
    }

    private void drawSettings(Gfx g, HudModule m, int x, int y, int w, boolean inView, int accent) {
        int sh = settingsHeight(m);
        Glass.fill(g, x + 3, y, w - 3, sh, 3, 0x55000000);
        g.fill(x + 3, y + 3, x + 4, y + sh - 3, Glass.alpha(accent, 0x90));

        int left = x + 9;
        int right = x + w - 6;
        int sy = y + 3;
        if (m.getSettings().isEmpty()) {
            g.drawString(font, fit("Nothing to set up", right - left), left, sy + 1, MUTED, false);
            return;
        }
        for (Setting<?> s : m.getSettings()) {
            int rowH = isSlider(s) ? 21 : 13;
            boolean hover = inView && over(x + 3, sy, w - 3, rowH);
            int labelColor = hover ? TEXT_BRIGHT : TEXT;

            if (s instanceof BoolSetting bs) {
                int tw = 14, th = 8;
                int tx = right - tw, ty = sy + 2;
                Glass.fill(g, tx, ty, tw, th, 4, bs.get() ? accent : 0x40FFFFFF);
                Glass.fill(g, bs.get() ? tx + tw - 7 : tx + 1, ty + 1, 6, 6, 3, 0xFFFFFFFF);
                g.drawString(font, fit(s.label, tx - 4 - left), left, sy + 2, labelColor, false);
                if (hover && leftPressed) {
                    leftPressed = false;
                    bs.set(!bs.get());
                    HudManager.save();
                }
            } else if (s instanceof EnumSetting es) {
                String value = fit(es.getCurrentOption(), (right - left) / 2);
                int vx = right - font.width(value);
                g.drawString(font, value, vx, sy + 2, Glass.lighten(accent, 0.3f), false);
                g.drawString(font, fit(s.label, vx - 4 - left), left, sy + 2, labelColor, false);
                if (hover && leftPressed) {
                    leftPressed = false;
                    es.cycle();
                    HudManager.save();
                }
            } else if (s instanceof ColorSetting cs) {
                int sw = 14, swh = 8;
                int swx = right - sw;
                Glass.fill(g, swx, sy + 2, sw, swh, 2, 0xFF000000 | cs.get());
                Glass.outline(g, swx, sy + 2, sw, swh, 2, hover ? 0xFFFFFFFF : 0x80FFFFFF);
                g.drawString(font, fit(s.label, swx - 4 - left), left, sy + 2, labelColor, false);
                if (hover && leftPressed) {
                    leftPressed = false;
                    MC.setScreen(minecraft, new ColorPickerScreen(cs, this));
                }
            } else if (isSlider(s)) {
                drawSlider(g, s, left, right, sy, hover, labelColor, accent);
            }
            sy += rowH;
        }
    }

    private void drawSlider(Gfx g, Setting<?> s, int left, int right, int sy, boolean hover, int labelColor, int accent) {
        double min, max, step, value;
        String text;
        if (s instanceof IntSetting is) {
            min = is.min; max = is.max; step = is.step; value = is.get();
            text = String.valueOf(is.get());
        } else {
            DoubleSetting ds = (DoubleSetting) s;
            min = ds.min; max = ds.max; step = ds.step; value = ds.get();
            text = String.format("%.2f", ds.get());
        }

        int trackW = right - left;
        if (hover && leftPressed && my >= sy + 10) {
            leftPressed = false;
            dragging = s;
            dragX = left;
            dragW = trackW;
        }
        if (dragging == s && leftDown && dragW > 0 && max > min) {
            double t = Math.max(0, Math.min(1, (mx - dragX) / (double) dragW));
            double v = min + t * (max - min);
            if (step > 0) v = min + Math.round((v - min) / step) * step;
            if (s instanceof IntSetting is) {
                is.set((int) Math.round(v));
                value = is.get();
                text = String.valueOf(is.get());
            } else {
                DoubleSetting ds = (DoubleSetting) s;
                ds.set(v);
                value = ds.get();
                text = String.format("%.2f", ds.get());
            }
        }

        int vx = right - font.width(text);
        g.drawString(font, text, vx, sy + 2, Glass.lighten(accent, 0.3f), false);
        g.drawString(font, fit(s.label, vx - 4 - left), left, sy + 2, labelColor, false);

        double t = max > min ? Math.max(0, Math.min(1, (value - min) / (max - min))) : 0;
        int ty = sy + 14;
        int knob = left + (int) Math.round(t * (trackW - 4));
        g.fill(left, ty + 1, right, ty + 3, 0x38FFFFFF);
        g.fill(left, ty + 1, knob + 2, ty + 3, accent);
        Glass.fill(g, knob, ty - 1, 4, 6, 2, dragging == s || hover ? 0xFFFFFFFF : Glass.lighten(accent, 0.5f));
    }

    // ── Footer ───────────────────────────────────────────────────────────

    private void drawFooter(Gfx g, int accent) {
        String[] labels = { "Edit HUD Layout", "Classic Menu", "Done" };
        int[] widths = new int[labels.length];
        int gap = 8, totalW = 0;
        for (int i = 0; i < labels.length; i++) {
            widths[i] = font.width(labels[i]) + 26;
            totalW += widths[i];
        }
        totalW += gap * (labels.length - 1);

        int h = 20;
        int y = vh - 32;
        int x = (vw - totalW) / 2;

        if (x > 130) {
            g.drawString(font, "Left click", 14, y + 1, TEXT, false);
            g.drawString(font, "toggle", 14 + font.width("Left click "), y + 1, MUTED, false);
            g.drawString(font, "Right click", 14, y + 11, TEXT, false);
            g.drawString(font, "settings", 14 + font.width("Right click "), y + 11, MUTED, false);
        }

        for (int i = 0; i < labels.length; i++) {
            boolean primary = i == labels.length - 1;
            boolean hover = over(x, y, widths[i], h);
            if (primary) {
                Glass.fill(g, x, y, widths[i], h, RADIUS, hover ? Glass.lighten(accent, 0.2f) : accent);
                g.drawString(font, labels[i], x + 13, y + 6, 0xFF06121D, false);
            } else {
                Glass.fill(g, x, y, widths[i], h, RADIUS, hover ? 0xE6141A2C : PANEL_BG);
                Glass.outline(g, x, y, widths[i], h, RADIUS, hover ? Glass.alpha(accent, 0xC0) : HAIRLINE);
                g.drawString(font, labels[i], x + 13, y + 6, hover ? TEXT_BRIGHT : TEXT, false);
            }
            if (hover && leftPressed) {
                leftPressed = false;
                if (i == 0) {
                    MC.setScreen(minecraft, new HudEditScreen(this));
                } else if (i == 1) {
                    HudManager.save();
                    MenuPrefs.setStyle(MenuPrefs.STYLE_GRID);
                    MC.setScreen(minecraft, new IceyModScreen());
                } else {
                    onClose();
                }
            }
            x += widths[i] + gap;
        }
    }

    // ── Bits ─────────────────────────────────────────────────────────────

    private void drawTooltip(Gfx g) {
        if (tooltip == null || dragging != null) return;
        int w = font.width(tooltip) + 12;
        int h = 16;
        int x = Math.max(4, Math.min(mx + 10, vw - w - 4));
        int y = my - h - 4;
        if (y < 4) y = my + 14;
        Glass.fill(g, x, y, w, h, 4, 0xF20A0E1A);
        Glass.outline(g, x, y, w, h, 4, 0x40FFFFFF);
        g.drawString(font, tooltip, x + 6, y + 4, TEXT_BRIGHT, false);
    }

    /** Cut {@code s} to {@code room} pixels, ending in an ellipsis when it doesn't fit. */
    private String fit(String s, int room) {
        if (room <= 0) return "";
        if (font.width(s) <= room) return s;
        String dots = "…";
        return font.plainSubstrByWidth(s, Math.max(0, room - font.width(dots))).trim() + dots;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int cx = (int) (mouseX / scale);
        int cy = (int) (mouseY / scale);
        for (int i = 0; i < GROUPS; i++) {
            if (cx >= panelX[i] && cx < panelX[i] + panelW && cy >= panelY[i] && cy < panelY[i] + panelH[i]) {
                scroll[i] = Math.max(0, Math.min(maxScroll[i], scroll[i] - (int) Math.round(verticalAmount * 18)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        HudManager.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
