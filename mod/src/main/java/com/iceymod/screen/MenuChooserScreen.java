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
        drawPlace(g, 1f, 0f, 1f, true);
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

    private static final net.minecraft.resources.Identifier PANORAMA = net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/title/background/panorama_0.png");
    private static final net.minecraft.resources.Identifier MC_LOGO = net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/title/minecraft.png");
    private static final net.minecraft.resources.Identifier GLASS_BUTTON = net.minecraft.resources.Identifier.withDefaultNamespace("widget/button");
    private static final String[] BUTTONS = { "Singleplayer", "Multiplayer", "Options..." };

    /** A whole texture drawn into a box (whatever its real size is). */
    private void picture(Gfx g, net.minecraft.resources.Identifier tex, float x, float y, float w, float h, int tw, int th, int tint) {
        g.pose().pushMatrix();
        try {
            g.pose().translate(x, y);
            g.pose().scale(w / tw, h / th);
            g.blit(tex, 0, 0, 0f, 0f, tw, th, tw, th, tint);
        } finally { g.pose().popMatrix(); }
    }

    /** Text in the game's own pixel font (the vanilla and Icey menus use it). */
    private void pixelText(Gfx g, String str, float cx, float y, float size, int color) {
        g.pose().pushMatrix();
        try {
            g.pose().translate(cx - this.font.width(str) * size / 2f, y);
            g.pose().scale(size, size);
            g.drawString(this.font, str, 0, 0, color, true);
        } finally { g.pose().popMatrix(); }
    }

    /** A small picture of each menu, put together from the real pieces. */
    private void preview(Gfx g, int which, float x, float y, float w, float h, float al) {
        int ix = Math.round(x), iy = Math.round(y), iw = Math.round(w), ih = Math.round(h);
        int white = a(0xFFFFFFFF, al);
        g.enableScissor(ix, iy, ix + iw, iy + ih);
        try {
            if (which == 2) {
                // The scene itself, small: the real place with you in it.
                if (scene != null && model != null) {
                    for (int i = 0; i < 10; i++) g.fill(ix, iy + ih * i / 10, ix + iw, iy + ih * (i + 1) / 10, a(mix(0xFF000000 | scene.skyTop, 0xFF000000 | scene.skyBottom, i / 9f), al));
                    model.bounds(ix, iy, ix + iw, iy + ih);
                    model.view(orbitYaw, orbitPitch, model.focalFor(h) * 1.08f, x + w / 2f, y + h * 0.5f);
                    model.draw(g, ix, iy, ix + iw, iy + ih, al);
                    String uuid = minecraft.getUser().getProfileId().toString();
                    MenuTextures.Tex skin = MenuTextures.skin(uuid);
                    Doll.Pose p = new Doll.Pose();
                    float[] at = model.project(0, 0, 0);
                    p.pitch = model.pitchSeen(0, 0, 0);
                    p.yaw = model.yawSeen(0, 0) + 0.2f;
                    Doll.draw(g, skin.id, skin.height, false, at[0], at[1], 1.875f * at[2], p, a(0xFF000000 | scene.light, al));
                } else g.fill(ix, iy, ix + iw, iy + ih, a(0xFF16233A, al));
                // its bar and its panel, hinted
                g.fill(ix, iy, ix + iw, iy + 11, a(0x99060912, al));
                rect(g, x + w / 2f - 34, y + 3, 68, 6, 3, a(0x40FFFFFF, al));
                rect(g, x + w / 2f - 33, y + 4, 14, 4, 2, a(MenuPrefs.accent(), al));
                rect(g, x + w - 50, y + h - 44, 44, 38, 4, a(0xB30C1322, al));
                ring(g, x + w - 50, y + h - 44, 44, 38, 4, a(0x40FFFFFF, al));
                rect(g, x + w - 46, y + h - 18, 36, 8, 3, a(MenuPrefs.accent(), al));
            } else {
                // The title picture behind, as both of these menus have it.
                float side = Math.max(w, h * 16f / 9f);
                picture(g, PANORAMA, x + (w - side) / 2f, y + (h - side) / 2f, side, side, 256, 256, a(which == 0 ? 0xFFFFFFFF : 0xFFB8C0CC, al));
                g.fill(ix, iy, ix + iw, iy + ih, a(which == 0 ? 0x1A000000 : 0x40060912, al));
                float bw = w * 0.56f, bx = x + (w - bw) / 2f, bh = Math.max(9f, h * 0.105f), by = y + h * 0.5f, step = bh + 3f;
                if (which == 0) {
                    float lw = w * 0.62f;
                    picture(g, MC_LOGO, x + (w - lw) / 2f, y + h * 0.12f, lw, lw * 44f / 256f * (256f / 256f), 256, 64, white);
                    for (int i = 0; i < 3; i++) {
                        float yy = by + i * step;
                        g.fill(Math.round(bx), Math.round(yy), Math.round(bx + bw), Math.round(yy + bh), a(0xFF000000, al));
                        g.fill(Math.round(bx) + 1, Math.round(yy) + 1, Math.round(bx + bw) - 1, Math.round(yy + bh) - 1, a(0xFF6C6C6C, al));
                        g.fill(Math.round(bx) + 1, Math.round(yy) + 1, Math.round(bx + bw) - 1, Math.round(yy) + 2, a(0xFFAAAAAA, al));
                        g.fill(Math.round(bx) + 1, Math.round(yy + bh) - 2, Math.round(bx + bw) - 1, Math.round(yy + bh) - 1, a(0xFF545454, al));
                        pixelText(g, BUTTONS[i], x + w / 2f, yy + (bh - 4f) / 2f, 0.5f, white);
                    }
                } else {
                    try {
                        float hs = h * 0.2f;
                        picture(g, Branding.head(), x + (w - hs) / 2f, y + h * 0.07f, hs, hs, 64, 64, white);
                        float lw = w * 0.5f, lh = lw * Branding.logoHeight() / Branding.logoWidth();
                        picture(g, Branding.logo(), x + (w - lw) / 2f, y + h * 0.07f + hs + 3, lw, lh, Branding.logoWidth(), Branding.logoHeight(), white);
                    } catch (Throwable ignored) {}
                    for (int i = 0; i < 3; i++) {
                        float yy = by + i * step;
                        try { g.sprite(GLASS_BUTTON, Math.round(bx), Math.round(yy), Math.round(bw), Math.round(bh), white); }
                        catch (Throwable t) { rect(g, bx, yy, bw, bh, 3, a(0x660E1320, al)); ring(g, bx, yy, bw, bh, 3, a(0x70FFFFFF, al)); }
                        pixelText(g, BUTTONS[i], x + w / 2f, yy + (bh - 4f) / 2f, 0.5f, white);
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
