package com.iceymod.screen;

import com.iceymod.Branding;
import com.iceymod.IceyMod;
import com.iceymod.MenuPrefs;
import com.iceymod.compat.Gfx;
import com.iceymod.compat.IceyScreen;
import com.iceymod.compat.Input;
import com.iceymod.compat.MC;
import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.menu.MenuTextures;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Y menu in its "Cards" style: a sidebar on the left, filter chips on
 * top and one large tile per module, with the module's icon in the middle
 * and its name on a bar along the bottom that fills with the accent colour
 * when the module is on.
 *
 * Left click switches a module on or off; right click, or the gear that
 * shows on a tile under the mouse, opens its settings.
 *
 * Drawn on the fine canvas of {@link VirtualScreen} in the menu typeface,
 * with the mouse polled (click callbacks differ between game versions).
 */
public class CardMenuScreen extends VirtualScreen {

    private static final int SIDE_W = 164;
    private static final int PAD = 18;
    private static final int GAP = 10;
    private static final int BAR_H = 24;
    private static final int GRID_Y = 86;

    private static final int TEXT = 0xFFD3DAE6;
    private static final int MUTED = 0xFF8792A8;

    // Kept between openings so the menu comes back the way it was left.
    private static float scrollTo;
    private static int filter = -1;                 // -1 = all, else a Group ordinal
    private static String searchQuery = "";

    private EditBox search;
    private boolean searchOn;
    private float scroll = scrollTo;
    private float maxScroll;
    private boolean rightClicked, prevRight = true;
    private String tooltip;
    private final long openedAt = System.currentTimeMillis();

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
        String keep = search != null ? search.getValue() : searchQuery;
        search = new EditBox(this.font, 0, 0, 100, 10, Component.literal(""));
        search.setBordered(false);
        search.setMaxLength(32);
        search.setTextColor(0xFFFFFFFF);
        search.setValue(keep);
        try {
            if (UiFont.usable(this.font)) search.addFormatter((text, start) -> net.minecraft.util.FormattedCharSequence.forward(text, UiFont.style(false)));
        } catch (Throwable ignored) {}
        search.setResponder(v -> {
            if (!v.equals(searchQuery)) { searchQuery = v; scrollTo = 0; }
        });
        addWidget(search);
    }

    @Override
    protected void renderScreenBackground(Gfx g, int mouseX, int mouseY, float delta) {
        Glass.veil(g, this.width, this.height);
    }

    @Override
    protected void renderScreen(Gfx g, int mouseX, int mouseY, float delta) {
        beginFrame(mouseX, mouseY);
        boolean right = false;
        try { right = Input.mouseDown(InputConstants.MOUSE_BUTTON_RIGHT); } catch (Throwable ignored) {}
        rightClicked = right && !prevRight;
        prevRight = right;
        tooltip = null;
        g.pose().pushMatrix();
        try {
            g.pose().scale(s, s);
            draw(g, delta);
        } finally {
            g.pose().popMatrix();
        }
    }

    private void draw(Gfx g, float delta) {
        int accent = MenuPrefs.accent();
        float t = (System.currentTimeMillis() - openedAt) / 1000f;
        float in = outCubic(span(t, 0f, 0.22f));
        g.fill(0, 0, VW, VH, a(0x40060912, in));

        sidebar(g, accent, in);
        header(g, accent, in, delta);
        chips(g, accent, in);
        grid(g, accent, t);

        if (tooltip != null) {
            float w = textW(tooltip, 1f) + 14;
            float x = Math.max(4, Math.min(mx + 12, VW - w - 4)), y = my - 22 < 4 ? my + 16 : my - 22;
            rect(g, x, y, w, 17, 5, 0xF20A0E1A);
            ring(g, x, y, w, 17, 5, 0x40FFFFFF);
            text(g, tooltip, x + 7, y + 5, 1f, 0xFFFFFFFF);
        }
        // A click that landed on nothing takes the keyboard away from the search field.
        if (clicked) { searchOn = false; }
        search.setFocused(searchOn);
        if (searchOn) { if (getFocused() != search) setFocused(search); }
        else if (getFocused() == search) setFocused(null);
    }

    // ── Sidebar ──────────────────────────────────────────────────────────

    private void sidebar(Gfx g, int accent, float in) {
        float off = (1f - in) * -14f;
        g.fill(0, 0, SIDE_W, VH, a(0xB3080B15, in));
        g.fill(SIDE_W - 1, 0, SIDE_W, VH, a(0x30FFFFFF, in));

        List<HudModule> all = HudManager.getModules();
        int on = 0;
        for (HudModule m : all) if (m.isEnabled()) on++;
        text(g, "§l" + Branding.name().toUpperCase(), 16 + off, 17, 1.25f, a(Glass.lighten(accent, 0.25f), in));
        text(g, on + " of " + all.size() + " on", 16 + off, 34, 1f, a(MUTED, in));

        float y = 60;
        text(g, "MODS & OPTIONS", 16 + off, y, 1f, a(MUTED, in * 0.8f));
        y += 15;
        row(g, Icons.MODS, "Mods", y, 2, accent, in, off);
        y += 27;
        if (row(g, Icons.HUD, "Edit HUD", y, 0, accent, in, off)) {
            HudManager.save();
            MC.setScreen(minecraft, new HudEditScreen(this));
            return;
        }
        y += 38;

        text(g, "MENU LOOK", 16 + off, y, 1f, a(MUTED, in * 0.8f));
        y += 15;
        row(g, Icons.CARDS, "Cards", y, 1, accent, in, off);
        y += 27;
        if (row(g, Icons.PANELS, "Panels", y, 0, accent, in, off)) {
            HudManager.save();
            MenuPrefs.setStyle(MenuPrefs.STYLE_PANELS);
            MC.setScreen(minecraft, new PanelMenuScreen());
            return;
        }
        y += 27;
        if (row(g, Icons.CLASSIC, "Classic", y, 0, accent, in, off)) {
            HudManager.save();
            MenuPrefs.setStyle(MenuPrefs.STYLE_GRID);
            MC.setScreen(minecraft, new IceyModScreen());
            return;
        }
        y += 38;

        text(g, "COLOUR", 16 + off, y, 1f, a(MUTED, in * 0.8f));
        y += 16;
        for (int i = 0; i < MenuPrefs.ACCENTS.length; i++) {
            int c = MenuPrefs.ACCENTS[i];
            float cx = 16 + i * 19 + off;
            if (cx + 13 > SIDE_W - 8) break;
            boolean selected = (c & 0xFFFFFF) == (accent & 0xFFFFFF);
            boolean hover = over(cx - 3, y - 3, 19, 19);
            float hv = ease("sw" + i, hover || selected, 16f);
            if (hv > 0.02f) ring(g, cx - 3, y - 3, 19, 19, 8, a(selected ? 0xFFFFFFFF : 0x99FFFFFF, hv * in));
            rect(g, cx, y, 13, 13, 6, a(0xFF000000 | c, in));
            if (hover) {
                tooltip = MenuPrefs.ACCENT_NAMES[i];
                if (clicked) { click(); swallowClick(); MenuPrefs.setAccent(c); }
            }
        }

        // The player, pinned to the bottom, and how the tiles are used.
        float by = VH - 44;
        g.fill(10, Math.round(by) - 9, SIDE_W - 10, Math.round(by) - 8, a(0x1EFFFFFF, in));
        try {
            MenuTextures.Tex skin = MenuTextures.skin(minecraft.getUser().getProfileId().toString());
            Doll.face(g, skin.id, skin.height, 16 + off, by, 24, a(0xFFFFFFFF, in));
        } catch (Throwable ignored) {}
        text(g, "§l" + fit(minecraft.getUser().getName(), SIDE_W - 62, 1f), 48 + off, by + 3, 1f, a(0xFFFFFFFF, in));
        text(g, Branding.name() + " " + IceyMod.BUILD, 48 + off, by + 14, 1f, a(MUTED, in));
        if (by - 44 > y + 26) {
            text(g, "Left click: on / off", 16 + off, by - 38, 1f, a(MUTED, in));
            text(g, "Right click: settings", 16 + off, by - 26, 1f, a(MUTED, in));
        }
    }

    /**
     * One row of the sidebar. {@code state}: 2 = this page (filled), 1 = the chosen one of a group, 0 = a plain row.
     * True when a plain row was clicked.
     */
    private boolean row(Gfx g, char icon, String label, float y, int state, int accent, float in, float off) {
        float x = 8 + off, w = SIDE_W - 16, h = 23;
        boolean hover = state != 2 && over(x, y, w, h);
        float hv = ease("row" + label, hover, 16f);
        int ink = state == 2 ? onInk(accent) : state == 1 ? Glass.lighten(accent, 0.35f) : mix(TEXT, 0xFFFFFFFF, hv);
        if (state == 2) rect(g, x, y, w, h, 6, a(0xFF000000 | accent, in));
        else if (state == 1) rect(g, x, y, w, h, 6, a(Glass.alpha(accent, 0x2E), in));
        else if (hv > 0.02f) rect(g, x, y, w, h, 6, a(0x1AFFFFFF, hv * in));
        icon(g, icon, x + 17, y + h / 2f, 13, a(ink, in));
        text(g, (state == 2 ? "§l" : "") + label, x + 32, y + 8, 1f, a(ink, in));
        if (state == 1) icon(g, Icons.CHECK, x + w - 14, y + h / 2f, 11, a(ink, in));
        if (state == 0 && hover && clicked) { click(); swallowClick(); return true; }
        return false;
    }

    /** Ink that reads on the accent colour: dark on a light accent, white on a deep one. */
    private static int onInk(int accent) {
        float lum = (0.299f * ((accent >> 16) & 0xFF) + 0.587f * ((accent >> 8) & 0xFF) + 0.114f * (accent & 0xFF)) / 255f;
        return lum > 0.56f ? 0xFF06121D : 0xFFFFFFFF;
    }

    // ── Title, search, chips ─────────────────────────────────────────────

    private void header(Gfx g, int accent, float in, float delta) {
        float x0 = SIDE_W + PAD;
        text(g, "§lMods", x0, 17 - (1f - in) * 6f, 2f, a(0xFFFFFFFF, in));

        // Close, top right.
        float cx = VW - PAD - 24;
        boolean chov = over(cx, 14, 24, 24);
        float chv = ease("close", chov, 16f);
        rect(g, cx, 14, 24, 24, 6, a(0xFFFFFFFF, (0.07f + 0.10f * chv) * in));
        icon(g, Icons.CLOSE, cx + 12, 26, 12, a(mix(TEXT, 0xFFFFFFFF, chv), in));
        if (chov && clicked) { click(); swallowClick(); onClose(); return; }

        // Search.
        float sw = Math.min(210, VW - x0 - 150), sx = cx - 10 - sw;
        if (sw < 90) return;
        boolean hov = over(sx, 14, sw, 24);
        if (clicked && hov) { searchOn = true; swallowClick(); }
        float fv = ease("search", searchOn, 16f), hv = ease("searchh", hov, 16f);
        rect(g, sx, 14, sw, 24, 6, a(0xFF0A0E1A, 0.55f * in));
        ring(g, sx, 14, sw, 24, 6, a(mix(mix(0x33FFFFFF, 0x66FFFFFF, hv), 0xFF000000 | accent, fv), in));
        icon(g, Icons.SEARCH, sx + 13, 26, 12, a(mix(MUTED, 0xFFFFFFFF, fv), in));
        search.setX(Math.round(sx + 26)); search.setY(Math.round(22)); search.setWidth(Math.round(sw - 34));
        search.setHint(UiFont.usable(this.font) ? Component.literal("Search").withStyle(UiFont.style(false).withColor(0x8792A8)) : Component.literal("§7Search"));
        g.enableScissor(Math.round(sx + 24), 16, Math.round(sx + sw - 4), 36);
        try { g.drawWidget(search, Math.round(mx), Math.round(my), delta); } finally { g.disableScissor(); }
    }

    private void chips(Gfx g, int accent, float in) {
        PanelMenuScreen.Group[] groups = PanelMenuScreen.Group.values();
        char[] icons = { Icons.G_HUD, Icons.G_COMBAT, Icons.G_WORLD, Icons.G_PLAYER, Icons.G_PERFORMANCE };
        float x = SIDE_W + PAD, y = 52;
        for (int i = -1; i < groups.length; i++) {
            String label = i < 0 ? "All" : groups[i].title;
            boolean hasIcon = i >= 0 && iconsUsable();
            float w = textW(label, 1f) + (hasIcon ? 38 : 22), h = 23;
            if (x + w > VW - PAD) break;
            boolean selected = filter == i;
            boolean hover = over(x, y, w, h);
            float hv = ease("chip" + i, hover, 16f), sv = ease("chipon" + i, selected, 18f);
            rect(g, x, y, w, h, 6, a(mix(mix(0x1CFFFFFF, 0x30FFFFFF, hv), 0xFF000000 | accent, sv), in));
            if (sv < 0.98f) ring(g, x, y, w, h, 6, a(mix(0x38FFFFFF, 0x70FFFFFF, hv), in * (1f - sv)));
            int ink = mix(mix(TEXT, 0xFFFFFFFF, hv), onInk(accent), sv);
            float tx = x + 11;
            if (hasIcon) { icon(g, icons[Math.min(i, icons.length - 1)], x + 16, y + h / 2f, 12, a(ink, in)); tx = x + 27; }
            text(g, (selected ? "§l" : "") + label, tx, y + 8, 1f, a(ink, in));
            if (hover && clicked && !selected) { click(); swallowClick(); filter = i; scrollTo = 0; }
            x += w + 7;
        }
    }

    // ── Cards ────────────────────────────────────────────────────────────

    private void grid(Gfx g, int accent, float t) {
        float x0 = SIDE_W + PAD;
        float areaW = VW - x0 - PAD - 4;
        float viewH = VH - GRID_Y - 8;
        if (areaW < 120 || viewH < 40) return;
        int cols = Math.max(2, (int) ((areaW + GAP) / (148 + GAP)));
        float cardW = (areaW - (cols - 1) * GAP) / cols;
        float cardH = Math.max(76, Math.min(98, Math.round(cardW * 0.56f)));

        String q = searchQuery == null ? "" : searchQuery.toLowerCase().trim();
        List<HudModule> shown = new ArrayList<>();
        for (HudModule m : HudManager.getModules()) {
            if (filter >= 0 && PanelMenuScreen.groupOf(m).ordinal() != filter) continue;
            if (!q.isEmpty() && !m.getName().toLowerCase().contains(q)) continue;
            shown.add(m);
        }
        if (shown.isEmpty()) {
            textC(g, "No module matches", x0 + areaW / 2f, GRID_Y + 40, 1.25f, a(MUTED, outCubic(span(t, 0f, 0.3f))));
            maxScroll = 0;
            return;
        }

        int rows = (shown.size() + cols - 1) / cols;
        float contentH = rows * (cardH + GAP) - GAP + 6;
        maxScroll = Math.max(0, contentH - viewH);
        scrollTo = Math.max(0, Math.min(scrollTo, maxScroll));
        scroll += (scrollTo - scroll) * (1f - (float) Math.exp(-dt * 18f));
        if (Math.abs(scrollTo - scroll) < 0.3f) scroll = scrollTo;
        boolean inView = over(x0, GRID_Y, areaW, viewH);

        g.enableScissor(Math.round(x0) - 2, GRID_Y - 2, Math.round(x0 + areaW) + 2, Math.round(GRID_Y + viewH));
        try {
            for (int i = 0; i < shown.size(); i++) {
                float cx = x0 + (i % cols) * (cardW + GAP);
                float cy = GRID_Y + (i / cols) * (cardH + GAP) - scroll;
                if (cy + cardH <= GRID_Y - 4 || cy >= GRID_Y + viewH) continue;
                // The tiles come up row by row when the menu opens.
                float in = outCubic(span(t, 0.03f + (i / cols) * 0.035f, 0.26f + (i / cols) * 0.035f));
                if (in <= 0.01f) continue;
                card(g, shown.get(i), cx, cy + (1f - in) * 10f, cardW, cardH, inView, accent, in);
            }
        } finally {
            g.disableScissor();
        }

        if (maxScroll > 0) {
            float trackX = VW - 9;
            float thumbH = Math.max(18, viewH * viewH / contentH);
            float thumbY = GRID_Y + (viewH - thumbH) * scroll / maxScroll;
            rect(g, trackX, GRID_Y, 3, viewH, 2, 0x18FFFFFF);
            rect(g, trackX, thumbY, 3, thumbH, 2, Glass.alpha(accent, 0xD0));
        }
    }

    private void card(Gfx g, HudModule m, float fx, float fy, float fw, float fh, boolean inView, int accent, float in) {
        int x = Math.round(fx), y = Math.round(fy), w = Math.round(fw), h = Math.round(fh);
        boolean hover = inView && over(x, y, w, h);
        float hv = ease("card" + m.getId(), hover, 16f), k = ease("on" + m.getId(), m.isEnabled(), 14f);
        PanelMenuScreen.Group group = PanelMenuScreen.groupOf(m);
        boolean hasSettings = !m.getSettings().isEmpty();

        // The tile: glass, a little lighter under the mouse, its rim in the accent colour when on.
        Glass.fill(g, x, y, w, h, 8, a(mix(0x1EFFFFFF, 0x30FFFFFF, hv), in));
        Glass.fill(g, x, y, w, h, 8, a(0x2A0A1020, in));
        // The bar with the name along the bottom.
        int bar = mix(mix(0x26FFFFFF, 0x3CFFFFFF, hv), mix(0xFF000000 | accent, 0xFFFFFFFF, 0.10f * hv), k);
        Glass.fillBottom(g, x, y + h - BAR_H, w, BAR_H, 8, a(bar, in));
        Glass.outline(g, x, y, w, h, 8, a(mix(mix(0x2EFFFFFF, 0x66FFFFFF, hv), Glass.alpha(Glass.lighten(accent, 0.3f), 0xD0), k * 0.8f), in));

        String label = fit(PanelMenuScreen.labelOf(m, group), w - 16, 1f);
        int ink = mix(mix(MUTED, 0xFFFFFFFF, hv), onInk(accent), k);
        textC(g, (k > 0.5f ? "§l" : "") + label, x + w / 2f, y + h - BAR_H + 8, 1f, a(ink, in));

        // The icon, in the middle of what is above the bar.
        float iy = y + (h - BAR_H) / 2f + 1;
        int iconInk = mix(mix(0xFF8E9AB2, 0xFFD6DEEC, hv), 0xFFFFFFFF, k);
        if (iconsUsable()) icon(g, Icons.of(m.getId()), x + w / 2f, iy - hv * 1.5f, 26 + hv * 2f, a(iconInk, in));
        else textC(g, "§l" + m.getName().substring(0, 1).toUpperCase(), x + w / 2f, iy - 8, 2f, a(iconInk, in));

        // On or off, top right.
        rect(g, x + w - 13, y + 8, 5, 5, 2, a(mix(0x55FFFFFF, 0xFF000000 | Glass.lighten(accent, 0.2f), k), in));

        // Settings, top left, when the mouse is on the tile.
        boolean gear = false;
        if (hasSettings && hv > 0.02f) {
            gear = hover && over(x + 4, y + 4, 20, 20);
            float gv = ease("gear" + m.getId(), gear, 18f);
            if (gv > 0.02f) rect(g, x + 4, y + 4, 20, 20, 5, a(0x30FFFFFF, gv * in));
            icon(g, Icons.SETTINGS, x + 14, y + 14, 12, a(mix(0xFFAAB4C6, 0xFFFFFFFF, gv), hv * in));
        }

        if (!hover) return;
        if (gear) tooltip = "Settings";
        else if (!label.equals(m.getName())) tooltip = m.getName();
        if (clicked) {
            click();
            swallowClick();
            if (gear) { HudManager.save(); MC.setScreen(minecraft, new ModuleSettingsScreen(m, this)); }
            else { m.toggle(); HudManager.save(); }
        } else if (rightClicked) {
            rightClicked = false;
            if (hasSettings) { click(); HudManager.save(); MC.setScreen(minecraft, new ModuleSettingsScreen(m, this)); }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX / s >= SIDE_W) {
            scrollTo = Math.max(0, Math.min(maxScroll, scrollTo - (float) verticalAmount * 46f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        HudManager.save();
        super.onClose();
    }
}
