package com.iceymod.screen;

import com.iceymod.Branding;
import com.iceymod.MenuPrefs;
import com.iceymod.compat.Gfx;
import com.iceymod.compat.MC;
import com.iceymod.menu.MenuTextures;
import com.iceymod.menu.Scenes;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Asked once, the first time the game starts with the mod: which main
 * menu? Minecraft's own with nothing from Icey, the Icey one, or the
 * scene. The choice goes to the launcher's settings, where it can be
 * changed later, and "Menu style" in the scene menu opens this again.
 */
public class MenuChooserScreen extends VirtualScreen {

    private static final String[] MODES = { MenuPrefs.TITLE_VANILLA, MenuPrefs.TITLE_ICEY, MenuPrefs.TITLE_SCENE };
    private static final String[] NAMES = { "Vanilla", "Icey", "Icey Scene" };
    private static final String[][] LINES = {
        { "Minecraft's own menu.", "Nothing from Icey on it." },
        { "The Icey logo and", "glass buttons." },
        { "You and your friends in a", "place that changes every start." },
    };

    private long openedAt;
    private String picked;
    private long pickedAt;

    public MenuChooserScreen() { super(Component.literal("Main menu")); }

    @Override
    protected void init() {
        swallowClick();
        setScene(Scenes.current());
        openedAt = System.currentTimeMillis();
    }

    @Override
    protected void renderScreenBackground(Gfx g, int mouseX, int mouseY, float delta) {}

    @Override
    protected void renderScreen(Gfx g, int mouseX, int mouseY, float delta) {
        try {
            beginFrame(mouseX, mouseY);
            g.pose().pushMatrix();
            try {
                g.pose().scale(s, s);
                draw(g);
            } finally { g.pose().popMatrix(); }
        } catch (Throwable t) {
            System.out.println("[IceyMod] menu chooser failed: " + t);
            SceneMenu.asked = true;
            MC.setScreen(minecraft, new TitleScreen());
        }
    }

    private void draw(Gfx g) {
        long now = System.currentTimeMillis();
        float t = (now - openedAt) / 1000f;
        int accent = MenuPrefs.accent();
        drawPlace(g, 0.94f, 0f, 1f);
        g.fill(0, 0, VW, VH, 0x73060912);

        float in = outCubic(span(t, 0.05f, 0.5f));
        textC(g, "§lChoose your main menu", VW / 2f, VH * 0.5f - 152 - 8 * (1 - in), 2f, a(0xFFFFFFFF, in));
        textC(g, "You can change it later in Icey Client's settings.", VW / 2f, VH * 0.5f - 128, 1f, a(0xFFB8C4D6, in));

        float gap = 14;
        float cw = Math.min(214, (VW - 40 - gap * 2) / 3f), ph = Math.round(cw * 9 / 16f), ch = ph + 62;
        float x0 = (VW - (cw * 3 + gap * 2)) / 2f, y0 = VH * 0.5f - 104;
        String current = MenuPrefs.titleMenu();
        for (int i = 0; i < 3; i++) {
            float ci = outCubic(span(t, 0.15f + i * 0.08f, 0.6f + i * 0.08f));
            float x = Math.round(x0 + i * (cw + gap)), y = Math.round(y0 + 16 * (1 - ci));
            boolean hov = picked == null && over(x, y, cw, ch);
            float hv = ease("card" + i, hov, 14f);
            y -= Math.round(hv * 3);
            glass(g, x, y, cw, ch, 6, ci);
            preview(g, i, x + 6, y + 6, cw - 12, ph - 6, ci);
            boolean is = MODES[i].equals(current);
            ring(g, x, y, cw, ch, 6, a(accent, Math.max(hv, is ? 0.8f : 0f) * ci));
            text(g, "§l" + NAMES[i], x + 12, y + ph + 8, 1.25f, a(0xFFFFFFFF, ci));
            if (is) textR(g, "Current", x + cw - 12, y + ph + 10, 1f, a(accent, ci));
            text(g, LINES[i][0], x + 12, y + ph + 26, 1f, a(0xFFB8C4D6, ci));
            text(g, LINES[i][1], x + 12, y + ph + 37, 1f, a(0xFFB8C4D6, ci));
            if (hov && clicked) { click(); picked = MODES[i]; pickedAt = now; swallowClick(); }
        }
        textC(g, "Vanilla buttons come back fully at the next start of the game.", VW / 2f, y0 + ch + 16, 1f, a(0xFF8E9AB0, in));

        if (picked != null) {
            float out = clamp01((now - pickedAt) / 200f);
            g.fill(0, 0, VW, VH, a(0xFF05070D, out));
            if (out >= 1f) {
                String mode = picked; picked = null;
                MenuPrefs.setTitleMenu(mode);
                SceneMenu.asked = true;
                SceneMenu.failed = false;
                MC.setScreen(minecraft, MenuPrefs.TITLE_SCENE.equals(mode) && model != null ? new SceneMenuScreen() : new TitleScreen());
            }
        }
        float black = 1f - span(t, 0f, 0.3f);
        if (black > 0.003f) g.fill(0, 0, VW, VH, a(0xFF05070D, black));
    }

    /** A small drawing of each menu, made from the real pieces. */
    private void preview(Gfx g, int which, float x, float y, float w, float h, float al) {
        int ix = Math.round(x), iy = Math.round(y), iw = Math.round(w), ih = Math.round(h);
        g.enableScissor(ix, iy, ix + iw, iy + ih);
        try {
            if (which == 2) {
                // The place in small: sky, ground, two players, the bar and the panel.
                int top = scene != null ? scene.skyTop : 0x5B8DD6, bottom = scene != null ? scene.skyBottom : 0xBFD8F2;
                for (int i = 0; i < 12; i++) g.fill(ix, iy + ih * i / 12, ix + iw, iy + ih * (i + 1) / 12, a(mix(0xFF000000 | top, 0xFF000000 | bottom, i / 11f), al));
                g.fill(ix, iy + Math.round(ih * 0.62f), ix + iw, iy + ih, a(mix(0xFF000000 | bottom, 0xFF1B2A1E, 0.55f), al));
                g.fill(ix, iy + Math.round(ih * 0.62f), ix + iw, iy + Math.round(ih * 0.62f) + 1, a(0x33FFFFFF, al));
                String uuid = minecraft.getUser().getProfileId().toString();
                MenuTextures.Tex skin = MenuTextures.skin(uuid);
                Doll.Pose p = new Doll.Pose();
                p.yaw = 0.35f; p.headYaw = -0.2f;
                Doll.draw(g, skin.id, skin.height, false, x + w * 0.5f, y + h * 0.9f, h * 0.5f, p, a(0xFFFFFFFF, al));
                Doll.Pose q = new Doll.Pose();
                q.yaw = -0.4f;
                Doll.draw(g, MenuTextures.STEVE.id, 64, false, x + w * 0.24f, y + h * 0.74f, h * 0.3f, q, a(0xFFB9C0CC, al));
                g.fill(ix, iy, ix + iw, iy + 9, a(0x99060912, al));
                for (int i = 0; i < 4; i++) g.fill(ix + iw / 2 - 30 + i * 16, iy + 4, ix + iw / 2 - 30 + i * 16 + 10, iy + 5, a(0xFFFFFFFF, al * 0.8f));
                rect(g, x + w - 44, y + h - 40, 38, 34, 3, a(0xB30A1020, al));
                rect(g, x + w - 41, y + h - 16, 32, 7, 2, a(MenuPrefs.accent(), al));
            } else {
                g.fill(ix, iy, ix + iw, iy + ih, a(which == 0 ? 0xFF2A3038 : 0xFF0C1424, al));
                float bw = w * 0.5f, bx = x + (w - bw) / 2f, by = y + h * 0.46f;
                if (which == 0) {
                    textC(g, "§lMINECRAFT", x + w / 2f, y + h * 0.16f, 1.5f, a(0xFFD8D8D8, al));
                    for (int i = 0; i < 3; i++) {
                        float yy = by + i * 13;
                        g.fill(Math.round(bx), Math.round(yy), Math.round(bx + bw), Math.round(yy + 10), a(0xFF101010, al));
                        g.fill(Math.round(bx + 1), Math.round(yy + 1), Math.round(bx + bw - 1), Math.round(yy + 9), a(0xFF6F6F6F, al));
                        g.fill(Math.round(bx + 1), Math.round(yy + 1), Math.round(bx + bw - 1), Math.round(yy + 2), a(0xFFA8A8A8, al));
                    }
                } else {
                    try {
                        float hs = h * 0.24f;
                        g.pose().pushMatrix();
                        g.pose().translate(x + w / 2f - hs / 2f, y + h * 0.07f);
                        g.pose().scale(hs / 64f, hs / 64f);
                        g.blit(Branding.head(), 0, 0, 0f, 0f, 64, 64, 64, 64, a(0xFFFFFFFF, al));
                        g.pose().popMatrix();
                    } catch (Throwable ignored) {}
                    textC(g, "§l" + Branding.name().toUpperCase(), x + w / 2f, y + h * 0.34f, 1f, a(0xFFFFFFFF, al));
                    for (int i = 0; i < 3; i++) {
                        float yy = by + 3 + i * 13;
                        rect(g, bx, yy, bw, 10, 3, a(0x40FFFFFF, al));
                        ring(g, bx, yy, bw, 10, 3, a(0x50FFFFFF, al));
                    }
                }
            }
        } finally { g.disableScissor(); }
    }

    /** Esc: decide later (the normal menu for now, asked again at the next start). */
    @Override
    public void onClose() {
        SceneMenu.asked = true;
        MC.setScreen(minecraft, MenuPrefs.isSceneTitle() && !SceneMenu.failed && model != null ? new SceneMenuScreen() : new TitleScreen());
    }
}
