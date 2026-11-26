package com.iceymod.screen;

import com.iceymod.Branding;
import com.iceymod.MenuPrefs;
import com.iceymod.compat.Gfx;
import com.iceymod.compat.IceyScreen;
import com.iceymod.compat.Input;
import com.iceymod.compat.MC;
import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Y menu in its "Cards" style: a sidebar on the left,
 * filter chips on top and one glass tile per module, filled in the accent
 * colour when the module is on.
 *
 * Left click toggles a module, right click opens its settings.
 *
 * Drawn on its own canvas like {@link PanelMenuScreen}, with the mouse
 * polled for the same reason (click callbacks differ between versions).
 */
public class CardMenuScreen extends IceyScreen {

    private static final int MIN_CANVAS_W = 640;
    private static final int SIDE_W = 118;
    private static final int PAD = 12;
    private static final int CARD_W = 112;
    private static final int CARD_H = 24;
    private static final int BAR_H = 17;
    private static final int GAP = 6;
    private static final int RADIUS = 5;
    private static final int SEARCH_W = 150;
    private static final int SEARCH_H = 18;
    private static final int TOP_Y = 12;
    private static final int CHIPS_Y = 40;
    private static final int GRID_Y = 66;

    private static final int SIDE_BG = 0x99090C16;
    private static final int CARD_BG = 0xA00E1424;
    private static final int BAR_OFF = 0xB0222A3C;
    private static final int HAIRLINE = 0x3CFFFFFF;
    private static final int TEXT = 0xFFC9D1DE;
    private static final int TEXT_BRIGHT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF7F8AA0;

    // Kept between openings so the menu comes back the way it was left.
    private static int scroll;
    private static int filter = -1;                 // -1 = all, else a Group ordinal
    private static String searchQuery = "";

    private EditBox searchField;
    private float scale = 1f;
    private int vw, vh;
    private int mx, my;
    private boolean leftPressed, rightPressed;
    private boolean prevLeft = true, prevRight = true;
    private int maxScroll;
    private String tooltip;

    public CardMenuScreen() {
        super(Component.literal(Branding.name()));
    }

    /** The Y menu in whichever style is chosen. */
    public static IceyScreen current() {
        if (MenuPrefs.isCards()) return new CardMenuScreen();
        return MenuPrefs.isPanels() ? new PanelMenuScreen() : new IceyModScreen();
    }

    @Override
    protected void init() {
        prevLeft = true;
        prevRight = true;
        computeCanvas();
        searchField = new EditBox(this.font, vw - PAD - SEARCH_W + 8, TOP_Y + 5, SEARCH_W - 16, 12, Component.literal(""));
        searchField.setBordered(false);
        searchField.setMaxLength(32);
        searchField.setHint(Component.literal("§7Search…"));
        searchField.setValue(searchQuery);
        searchField.setResponder(s -> {
            if (!s.equals(searchQuery)) {
                searchQuery = s;
                scroll = 0;
            }
        });
        addRenderableWidget(searchField);
    }

    @Override
    protected void renderScreenBackground(Gfx g, int mouseX, int mouseY, float delta) {
        Glass.veil(g, this.width, this.height);
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
            drawSidebar(g, accent);
            drawHeader(g, accent);
            drawGrid(g, accent);
            drawTooltip(g);
        } finally {
            g.pose().popMatrix();
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
        boolean leftDown = Input.mouseDown(InputConstants.MOUSE_BUTTON_LEFT);
        boolean rightDown = Input.mouseDown(InputConstants.MOUSE_BUTTON_RIGHT);
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

    // ── Sidebar ──────────────────────────────────────────────────────────

    private void drawSidebar(Gfx g, int accent) {
        g.fill(0, 0, SIDE_W, vh, SIDE_BG);
        g.fill(SIDE_W - 1, 0, SIDE_W, vh, 0x3CFFFFFF);
        g.fill(SIDE_W - 2, 0, SIDE_W - 1, vh, 0x0CFFFFFF);

        List<HudModule> all = HudManager.getModules();
        int on = 0;
        for (HudModule m : all) if (m.isEnabled()) on++;
        g.drawString(font, Branding.name().toUpperCase(), 12, 14, Glass.lighten(accent, 0.2f), false);
        g.drawString(font, on + " of " + all.size() + " on", 12, 25, MUTED, false);

        int y = 46;
        y = navRow(g, "Mods", y, true, accent) ? y : y;
        y += 20;
        if (navRow(g, "Edit HUD Layout", y, false, accent)) {
            MC.setScreen(minecraft, new HudEditScreen(this));
            return;
        }
        y += 30;

        g.drawString(font, "MENU LOOK", 12, y, MUTED, false);
        y += 12;
        navRow(g, "Cards", y, true, accent);
        y += 20;
        if (navRow(g, "Panels", y, false, accent)) {
            HudManager.save();
            MenuPrefs.setStyle(MenuPrefs.STYLE_PANELS);
            MC.setScreen(minecraft, new PanelMenuScreen());
            return;
        }
        y += 20;
        if (navRow(g, "Classic", y, false, accent)) {
            HudManager.save();
            MenuPrefs.setStyle(MenuPrefs.STYLE_GRID);
            MC.setScreen(minecraft, new IceyModScreen());
            return;
        }
        y += 30;

        g.drawString(font, "COLOUR", 12, y, MUTED, false);
        y += 13;
        int size = 10, gap = 6;
        for (int i = 0; i < MenuPrefs.ACCENTS.length; i++) {
            int c = MenuPrefs.ACCENTS[i];
            int cx = 12 + i * (size + gap);
            boolean selected = (c & 0xFFFFFF) == (accent & 0xFFFFFF);
            boolean hover = over(cx - 2, y - 2, size + 4, size + 4);
            if (selected || hover) Glass.outline(g, cx - 2, y - 2, size + 4, size + 4, 5, selected ? 0xFFFFFFFF : 0x80FFFFFF);
            Glass.fill(g, cx, y, size, size, 4, c);
            if (hover) {
                tooltip = MenuPrefs.ACCENT_NAMES[i];
                if (leftPressed) { leftPressed = false; MenuPrefs.setAccent(c); }
            }
        }

        // Done, pinned to the bottom
        int bw = SIDE_W - 24, bh = 20, bx = 12, by = vh - 34;
        boolean hover = over(bx, by, bw, bh);
        Glass.fill(g, bx, by, bw, bh, RADIUS, hover ? Glass.lighten(accent, 0.2f) : accent);
        g.drawString(font, "Done", bx + (bw - font.width("Done")) / 2, by + 6, 0xFF06121D, false);
        if (hover && leftPressed) { leftPressed = false; onClose(); }

        if (by - 30 > y + 20) {
            g.drawString(font, "Left click: on / off", 12, by - 26, MUTED, false);
            g.drawString(font, "Right click: settings", 12, by - 15, MUTED, false);
        }
    }

    /** One row of the sidebar; true when it was clicked (never for the active row). */
    private boolean navRow(Gfx g, String label, int y, boolean active, int accent) {
        int x = 6, w = SIDE_W - 12, h = 17;
        boolean hover = over(x, y, w, h);
        if (active) {
            Glass.fill(g, x, y, w, h, 4, Glass.alpha(accent, 0x30));
            g.fill(x, y + 4, x + 2, y + h - 4, accent);
        } else if (hover) {
            Glass.fill(g, x, y, w, h, 4, 0x1EFFFFFF);
        }
        g.drawString(font, label, x + 8, y + 5, active ? Glass.lighten(accent, 0.35f) : (hover ? TEXT_BRIGHT : TEXT), false);
        if (!active && hover && leftPressed) { leftPressed = false; return true; }
        return false;
    }

    // ── Title, search, chips ─────────────────────────────────────────────

    private void drawSearch(Gfx g, int accent, float delta) {
        int x = vw - PAD - SEARCH_W;
        boolean hover = over(x, TOP_Y, SEARCH_W, SEARCH_H);
        if (leftPressed && searchField != null) {
            searchField.setFocused(hover);
            setFocused(hover ? searchField : null);
            if (hover) leftPressed = false;
        }
        boolean focused = searchField != null && searchField.isFocused();
        Glass.pane(g, x, TOP_Y, SEARCH_W, SEARCH_H, RADIUS, 0x8C0A0E1A,
                focused ? Glass.alpha(accent, 0xD0) : hover ? 0x60FFFFFF : HAIRLINE);
        superRender(g, mx, my, delta);
    }

    private void drawHeader(Gfx g, int accent) {
        int x0 = SIDE_W + PAD;
        g.pose().pushMatrix();
        g.pose().translate(x0, TOP_Y + 1);
        g.pose().scale(1.5f, 1.5f);
        g.drawString(font, "Mods", 0, 0, TEXT_BRIGHT, false);
        g.pose().popMatrix();

        PanelMenuScreen.Group[] groups = PanelMenuScreen.Group.values();
        int x = x0;
        for (int i = -1; i < groups.length; i++) {
            String label = i < 0 ? "All" : groups[i].title;
            int w = font.width(label) + 16, h = 16;
            if (x + w > vw - PAD) break;
            boolean selected = filter == i;
            boolean hover = over(x, CHIPS_Y, w, h);
            if (selected) {
                Glass.fill(g, x, CHIPS_Y, w, h, RADIUS, accent);
                g.drawString(font, label, x + 8, CHIPS_Y + 4, 0xFF06121D, false);
            } else {
                Glass.pane(g, x, CHIPS_Y, w, h, RADIUS, hover ? 0xB01A2134 : CARD_BG, hover ? 0x70FFFFFF : HAIRLINE);
                g.drawString(font, label, x + 8, CHIPS_Y + 4, hover ? TEXT_BRIGHT : TEXT, false);
            }
            if (hover && leftPressed && !selected) {
                leftPressed = false;
                filter = i;
                scroll = 0;
            }
            x += w + 6;
        }
    }

    // ── Cards ────────────────────────────────────────────────────────────

    private void drawGrid(Gfx g, int accent) {
        int x0 = SIDE_W + PAD;
        int areaW = vw - x0 - PAD;
        int viewH = vh - GRID_Y - 10;
        if (areaW < 60 || viewH < 20) return;
        int cols = Math.max(1, (areaW + GAP) / (CARD_W + GAP));
        int cardW = (areaW - (cols - 1) * GAP) / cols;

        String q = searchQuery == null ? "" : searchQuery.toLowerCase().trim();
        List<HudModule> shown = new ArrayList<>();
        for (HudModule m : HudManager.getModules()) {
            if (filter >= 0 && PanelMenuScreen.groupOf(m).ordinal() != filter) continue;
            if (!q.isEmpty() && !m.getName().toLowerCase().contains(q)) continue;
            shown.add(m);
        }
        if (shown.isEmpty()) {
            g.drawCenteredString(font, "§8No matches", x0 + areaW / 2, GRID_Y + 20, 0xFFFFFFFF);
            maxScroll = 0;
            return;
        }

        int rows = (shown.size() + cols - 1) / cols;
        int contentH = rows * (CARD_H + GAP) - GAP;
        maxScroll = Math.max(0, contentH - viewH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        boolean inView = over(x0, GRID_Y, areaW, viewH);

        g.enableScissor(x0, GRID_Y, x0 + areaW, GRID_Y + viewH);
        try {
            for (int i = 0; i < shown.size(); i++) {
                int cx = x0 + (i % cols) * (cardW + GAP);
                int cy = GRID_Y + (i / cols) * (CARD_H + GAP) - scroll;
                if (cy + CARD_H <= GRID_Y || cy >= GRID_Y + viewH) continue;
                drawCard(g, shown.get(i), cx, cy, cardW, inView, accent);
            }
        } finally {
            g.disableScissor();
        }

        if (maxScroll > 0) {
            int trackX = vw - 5;
            int thumbH = Math.max(14, viewH * viewH / contentH);
            int thumbY = GRID_Y + (viewH - thumbH) * scroll / maxScroll;
            g.fill(trackX, GRID_Y, trackX + 2, GRID_Y + viewH, 0x18FFFFFF);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, Glass.alpha(accent, 0xC0));
        }
    }

    private void drawCard(Gfx g, HudModule m, int x, int y, int w, boolean inView, int accent) {
        boolean on = m.isEnabled();
        boolean hover = inView && over(x, y, w, CARD_H);
        PanelMenuScreen.Group group = PanelMenuScreen.groupOf(m);

        // One glass tile per module: filled in the accent colour when it is on.
        // (The pixel icons that stood here were dropped: they read as blocky
        // next to the smooth glass.)
        if (on) {
            Glass.pane(g, x, y, w, CARD_H, RADIUS, Glass.alpha(hover ? Glass.lighten(accent, 0.15f) : accent, 0xE6),
                    Glass.alpha(Glass.lighten(accent, 0.45f), 0xC0));
        } else {
            Glass.pane(g, x, y, w, CARD_H, RADIUS, hover ? 0xB01A2134 : CARD_BG, hover ? 0x90FFFFFF : HAIRLINE);
        }

        boolean hasSettings = !m.getSettings().isEmpty();
        String label = fit(PanelMenuScreen.labelOf(m, group), w - (hasSettings ? 26 : 12));
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (CARD_H - 8) / 2,
                on ? 0xFF06121D : (hover ? TEXT_BRIGHT : TEXT), false);
        if (hasSettings) g.drawString(font, "+", x + w - 11, y + (CARD_H - 8) / 2, on ? 0xB006121D : (hover ? TEXT : MUTED), false);

        if (!hover) return;
        if (!label.equals(m.getName())) tooltip = m.getName();
        if (leftPressed) {
            leftPressed = false;
            m.toggle();
            HudManager.save();
        } else if (rightPressed) {
            rightPressed = false;
            if (hasSettings) MC.setScreen(minecraft, new ModuleSettingsScreen(m, this));
        }
    }

    // ── Bits ─────────────────────────────────────────────────────────────

    private void drawTooltip(Gfx g) {
        if (tooltip == null) return;
        int w = font.width(tooltip) + 12, h = 16;
        int x = Math.max(4, Math.min(mx + 10, vw - w - 4));
        int y = my - h - 4;
        if (y < 4) y = my + 14;
        Glass.fill(g, x, y, w, h, 4, 0xF20A0E1A);
        Glass.outline(g, x, y, w, h, 4, 0x40FFFFFF);
        g.drawString(font, tooltip, x + 6, y + 4, TEXT_BRIGHT, false);
    }

    private String fit(String s, int room) {
        if (room <= 0) return "";
        if (font.width(s) <= room) return s;
        String dots = "…";
        return font.plainSubstrByWidth(s, Math.max(0, room - font.width(dots))).trim() + dots;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX / scale >= SIDE_W) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(verticalAmount * 22)));
            return true;
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
