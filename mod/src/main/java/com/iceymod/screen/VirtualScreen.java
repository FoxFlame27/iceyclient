package com.iceymod.screen;

import com.iceymod.compat.Gfx;
import com.iceymod.compat.IceyScreen;
import com.iceymod.compat.Input;
import com.iceymod.menu.SceneModel;
import com.iceymod.menu.Scenes;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * A screen drawn on its own canvas instead of the game's GUI scale.
 *
 * Minecraft's automatic GUI scale gives a 1440p window a canvas of only
 * 427x240, which is far too coarse for a menu with small labels. Here one
 * canvas pixel is a whole number of real pixels chosen so the canvas is
 * about 480 high whatever the window, and text sizes are snapped so every
 * letter pixel is a whole number of real pixels too (sharp, never blurry).
 * Also: the mouse in canvas units, a click that fires once, eased values
 * for hover and slide animations, and the scene picture behind it all.
 */
abstract class VirtualScreen extends IceyScreen {
    protected int px = 1;                 // real pixels per canvas pixel
    protected float s = 1f;               // canvas pixel in GUI units
    protected int VW = 640, VH = 360;
    protected float mx, my;
    protected boolean down, clicked;
    private boolean prevDown = true;      // a button held while the screen opens is not a click
    protected float dt = 0.016f;
    private long lastFrame;
    private final Map<String, Float> eased = new HashMap<>();

    // The place behind the menu: a 3D model, looked at from an angle that follows the mouse.
    protected Scenes.Scene scene;
    protected SceneModel model;
    protected float orbitYaw, orbitPitch;

    protected VirtualScreen(Component title) { super(title); }

    protected void beginFrame(int mouseX, int mouseY) {
        int fbW = Math.max(1, minecraft.getWindow().getWidth()), fbH = Math.max(1, minecraft.getWindow().getHeight());
        int gui = Math.max(1, Math.round(fbW / (float) Math.max(1, this.width)));
        px = Math.max(1, Math.min(gui, Math.round(fbH / 480f)));
        s = px / (float) gui;
        VW = (int) Math.ceil(fbW / (float) px);
        VH = (int) Math.ceil(fbH / (float) px);
        mx = mouseX / s; my = mouseY / s;
        boolean now = Input.mouseDown(InputConstants.MOUSE_BUTTON_LEFT);
        clicked = now && !prevDown;
        down = now;
        prevDown = now;
        long t = System.nanoTime();
        dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (t - lastFrame) / 1e9f);
        lastFrame = t;
    }

    /** After this, a button that is still held does not count as a new click. */
    protected void swallowClick() { clicked = false; prevDown = true; }

    protected boolean over(float x, float y, float w, float h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    /** A value that glides towards 1 while {@code on} and back to 0 otherwise. */
    protected float ease(String key, boolean on, float perSecond) {
        float v = eased.getOrDefault(key, 0f);
        float target = on ? 1f : 0f;
        v += (target - v) * (1f - (float) Math.exp(-dt * perSecond));
        if (Math.abs(target - v) < 0.002f) v = target;
        eased.put(key, v);
        return v;
    }
    protected void setEase(String key, float v) { eased.put(key, v); }

    protected static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    /** Fast start, soft landing. */
    protected static float outCubic(float t) { t = clamp01(t); float u = 1 - t; return 1 - u * u * u; }
    protected static float span(float t, float from, float to) { return clamp01((t - from) / (to - from)); }
    protected static int a(int color, float alpha) {
        int al = Math.round(((color >>> 24) & 0xFF) * clamp01(alpha));
        return (al << 24) | (color & 0xFFFFFF);
    }
    protected static int mix(int c1, int c2, float t) {
        t = clamp01(t);
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return (Math.round(a1 + (a2 - a1) * t) << 24) | (Math.round(r1 + (r2 - r1) * t) << 16) | (Math.round(g1 + (g2 - g1) * t) << 8) | Math.round(b1 + (b2 - b1) * t);
    }

    // ── Text ──
    /** A text size that puts every letter pixel on whole real pixels. */
    protected float size(float want) { return Math.max(1, Math.round(want * px)) / (float) px; }
    protected float textW(String str, float size) { return this.font.width(UiFont.of(this.font, str)) * size(size); }

    protected void text(Gfx g, String str, float x, float y, float size, int color) {
        if ((color >>> 24) < 8 || str.isEmpty()) return;
        float k = size(size);
        g.pose().pushMatrix();
        try {
            g.pose().translate(Math.round(x * px) / (float) px, Math.round(y * px) / (float) px);
            g.pose().scale(k, k);
            g.drawString(this.font, UiFont.of(this.font, str), 0, 0, color, false);
        } finally { g.pose().popMatrix(); }
    }
    protected void textC(Gfx g, String str, float cx, float y, float size, int color) { text(g, str, cx - textW(str, size) / 2f, y, size, color); }
    protected void textR(Gfx g, String str, float right, float y, float size, int color) { text(g, str, right - textW(str, size), y, size, color); }
    /** The text, cut with "…" when it is wider than {@code maxW}. */
    protected String fit(String str, float maxW, float size) {
        if (textW(str, size) <= maxW) return str;
        String pre = str.startsWith("§l") ? "§l" : "";
        String body = str.substring(pre.length());
        int lo = 0, hi = body.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (textW(pre + body.substring(0, mid) + "…", size) <= maxW) lo = mid; else hi = mid - 1;
        }
        return pre + body.substring(0, lo).stripTrailing() + "…";
    }

    // ── Shapes (rounded ones come from the glass sprites) ──
    protected void rect(Gfx g, float x, float y, float w, float h, int r, int color) {
        if ((color >>> 24) == 0) return;
        Glass.fill(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), r, color);
    }
    protected void ring(Gfx g, float x, float y, float w, float h, int r, int color) {
        if ((color >>> 24) == 0) return;
        Glass.outline(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), r, color);
    }

    // ── The place behind ──
    protected void setScene(Scenes.Scene sc) {
        scene = sc;
        model = SceneModel.of(sc);
    }

    /**
     * Draws the sky and the place, turned a little by the mouse, then blurs
     * it all the way the game blurs behind its own menus: what comes after
     * (the players, the panels) is sharp in front of a soft world.
     *
     * @param zoom  1 = eight blocks from top to bottom of the window
     * @param extraYaw  degrees added to the turn (the sweep at the first start)
     */
    protected void drawPlace(Gfx g, float zoom, float extraYaw, float alpha) {
        float k = 1f - (float) Math.exp(-dt * 3.5f);
        float sway = (float) Math.sin(System.nanoTime() / 1e9 * 0.22) * 1.2f;
        orbitYaw += (((mx / VW) - 0.5f) * -15f + sway - orbitYaw) * k;
        orbitPitch += (((my / VH) - 0.5f) * 5f - orbitPitch) * k;
        if (scene == null || model == null) { g.fill(0, 0, VW, VH, 0xFF0A0F1C); return; }
        int bands = 32;
        for (int i = 0; i < bands; i++) {
            int c = mix(0xFF000000 | scene.skyTop, 0xFF000000 | scene.skyBottom, i / (float) (bands - 1));
            g.fill(0, VH * i / bands, VW, VH * (i + 1) / bands, c);
        }
        g.nextStratum();
        model.view(orbitYaw + extraYaw, orbitPitch, VH / 8.0f * zoom, VW / 2f, VH / 2f);
        model.draw(g, VW, VH, alpha);
        g.blurBehind();
    }

    /** Where a spot of the place is on the canvas right now, and how tall a player is there. */
    protected float placeX(float x, float y, float z) { return model.screenX(x, y, z); }
    protected float placeY(float x, float y, float z) { return model.screenY(x, y, z); }
    protected float playerHeight() { return 1.875f * model.blockPx(); }

    /**
     * A pane of frosted glass. The world behind is already blurred, so the
     * pane itself is a soft shadow, a thin smoky tint, light falling in
     * from the top and a bright rim.
     */
    protected void glass(Gfx g, float fx, float fy, float fw, float fh, int r, float alpha) {
        int x = Math.round(fx), y = Math.round(fy), w = Math.round(fw), h = Math.round(fh);
        if (w <= 2 || h <= 2 || alpha <= 0.01f) return;
        Glass.shadow(g, x, y + 3, w, h, a(0x70000000, alpha));
        Glass.fill(g, x, y, w, h, r, a(0x6E0C1322, alpha));
        Glass.fill(g, x, y, w, h, r, a(0x14FFFFFF, alpha));
        // Light from above: brightest at the rim, gone a third of the way down.
        int fade = Math.min(h / 2, 46);
        Glass.fillTop(g, x, y, w, Math.min(h, r + 1), r, a(0x1AFFFFFF, alpha));
        for (int i = 0; i < fade; i += 2) {
            float t = 1f - i / (float) fade;
            g.fill(x + 1, y + r + 1 + i, x + w - 1, y + r + 3 + i, a(0x1AFFFFFF, alpha * t * t));
        }
        Glass.outline(g, x, y, w, h, r, a(0x47FFFFFF, alpha));
        g.fill(x + r, y, x + w - r, y + 1, a(0x66FFFFFF, alpha));
    }

    protected void click() {
        try {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
