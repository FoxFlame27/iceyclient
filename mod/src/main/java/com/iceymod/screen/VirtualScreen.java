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

    // The glass panes of the last frame: behind them the world is blurred, everywhere else it is sharp.
    private java.util.List<float[]> panes = new java.util.ArrayList<>(), lastPanes = new java.util.ArrayList<>();

    protected void beginFrame(int mouseX, int mouseY) {
        java.util.List<float[]> swap = lastPanes; lastPanes = panes; panes = swap; panes.clear();
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
    static int a(int color, float alpha) {
        int al = Math.round(((color >>> 24) & 0xFF) * clamp01(alpha));
        return (al << 24) | (color & 0xFFFFFF);
    }
    static int mix(int c1, int c2, float t) {
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

    /** An icon of the icon font, {@code size} canvas pixels wide and high, its middle at (cx, cy). */
    protected void icon(Gfx g, char c, float cx, float cy, float size, int color) {
        if ((color >>> 24) < 8 || !iconsUsable()) return;
        float k = size / 16f;
        g.pose().pushMatrix();
        try {
            g.pose().translate(cx - size / 2f, cy - size / 2f);
            g.pose().scale(k, k);
            g.drawString(this.font, UiFont.icon(c), 0, 0, color, false);
        } finally { g.pose().popMatrix(); }
    }
    protected boolean iconsUsable() { return UiFont.iconsUsable(this.font); }

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
        model = SceneModel.of(sc);
        scene = model != null ? model.scene : sc;          // the place under today's weather
    }

    /** Falling snow or rain in front of the place (drawn sharp, after the blur). */
    protected void weather(Gfx g, float alpha) {
        if (scene == null || alpha <= 0.02f) return;
        float tt = (System.currentTimeMillis() % 1000000L) / 1000f;
        if (scene.snowing) {
            for (int i = 0; i < 90; i++) {
                float seed = i * 12.9898f;
                float fx = (float) ((Math.sin(seed) * 43758.5453) % 1.0); if (fx < 0) fx += 1;
                float fy = (float) ((Math.sin(seed * 1.7f) * 24634.6345) % 1.0); if (fy < 0) fy += 1;
                float speed = 12f + (i % 7) * 4.5f;
                float x = (fx * VW + (float) Math.sin(tt * 0.6f + i) * 10f + tt * (5f + i % 5)) % VW;
                float y = (fy * VH + tt * speed) % VH;
                int size = i % 5 == 0 ? 2 : 1;
                g.fill(Math.round(x), Math.round(y), Math.round(x) + size, Math.round(y) + size, a(0xFFFFFFFF, alpha * (0.35f + (i % 4) * 0.14f)));
            }
        } else if (scene.raining) {
            g.fill(0, 0, VW, VH, a(0x1A1C2A40, alpha));
            for (int i = 0; i < 110; i++) {
                float seed = i * 7.233f;
                float fx = (float) ((Math.sin(seed) * 43758.5453) % 1.0); if (fx < 0) fx += 1;
                float fy = (float) ((Math.sin(seed * 2.1f) * 13634.6345) % 1.0); if (fy < 0) fy += 1;
                float speed = 330f + (i % 6) * 38f;
                float y = (fy * (VH + 40) + tt * speed) % (VH + 40) - 20;
                float x = (fx * (VW + 60) - y * 0.16f + 30) % (VW + 60) - 30;
                int col = a(0xFFC4D6F2, alpha * (0.20f + (i % 3) * 0.09f));
                // a short slanted streak
                for (int k = 0; k < 3; k++) g.fill(Math.round(x - k), Math.round(y + k * 3), Math.round(x - k) + 1, Math.round(y + k * 3) + 3, col);
            }
        }
    }

    /**
     * Draws the sky and the place as the camera behind the player sees it,
     * moved a little by the mouse. The world is sharp; only what lies behind
     * a glass pane is blurred, the way the game blurs behind its own menus
     * (the pane is then frosted glass over a crisp world).
     *
     * @param zoom  1 = the camera's own opening angle; more = closer
     * @param extraYaw  degrees added to the turn (the sweep at the first start)
     * @param blurAll  the whole place soft, as a backdrop
     */
    protected void drawPlace(Gfx g, float zoom, float extraYaw, float alpha, boolean blurAll) {
        float k = 1f - (float) Math.exp(-dt * 3.5f);
        float sway = (float) Math.sin(System.nanoTime() / 1e9 * 0.22) * 0.9f;
        orbitYaw += (((mx / VW) - 0.5f) * -11f + sway - orbitYaw) * k;
        orbitPitch += (((my / VH) - 0.5f) * 3.6f - orbitPitch) * k;
        if (scene == null || model == null) { g.fill(0, 0, VW, VH, 0xFF0A0F1C); return; }
        // A very wide window sees no more to the sides than the model has: it gets a closer picture instead.
        model.bounds(0, 0, VW, VH);
        model.view(orbitYaw + extraYaw, orbitPitch, model.focalFor(Math.max(VH, VW / 1.95f)) * zoom, VW / 2f, VH / 2f);
        // The sky: deep at the top, the colour of the haze at the horizon and under it.
        int horizon = Math.max(8, Math.min(VH, Math.round(model.horizonY())));
        int bands = 28;
        for (int i = 0; i < bands; i++) {
            float t = i / (float) (bands - 1);
            g.fill(0, horizon * i / bands, VW, horizon * (i + 1) / bands, mix(0xFF000000 | scene.skyTop, 0xFF000000 | scene.skyBottom, t * (2f - t)));
        }
        if (horizon < VH) g.fill(0, horizon, VW, VH, 0xFF000000 | scene.skyBottom);
        g.nextStratum();
        if (blurAll) {
            model.draw(g, 0, 0, VW, VH, alpha);
            g.blurBehind();
            return;
        }
        if (lastPanes.isEmpty()) { model.draw(g, 0, 0, VW, VH, alpha); return; }
        // Clipping works in whole GUI pixels, which are not the canvas's: the boxes are worked out in GUI pixels so they meet exactly.
        int GW = this.width, GH = this.height;
        // Behind each pane (and a little around it, so the blur has something to take from at the rim).
        for (float[] p : lastPanes) {
            int x0 = Math.max(0, (int) Math.floor((p[0] - 12) * s)), y0 = Math.max(0, (int) Math.floor((p[1] - 12) * s));
            int x1 = Math.min(GW, (int) Math.ceil((p[2] + 12) * s)), y1 = Math.min(GH, (int) Math.ceil((p[3] + 12) * s));
            if (x1 > x0 && y1 > y0) clipped(g, x0, y0, x1, y1, alpha);
        }
        g.blurBehind();
        // Everywhere else, sharp: the window without the panes, as rows of boxes.
        java.util.TreeSet<Integer> ys = new java.util.TreeSet<>();
        ys.add(0); ys.add(GH);
        int[][] hole = new int[lastPanes.size()][];
        for (int i = 0; i < hole.length; i++) {
            float[] p = lastPanes.get(i);
            // A few pixels in from the pane's edge, so its round corners have the sharp world behind them.
            hole[i] = new int[] { (int) Math.ceil((p[0] + 3) * s), (int) Math.ceil((p[1] + 3) * s), (int) Math.floor((p[2] - 3) * s), (int) Math.floor((p[3] - 3) * s) };
            if (hole[i][2] <= hole[i][0] || hole[i][3] <= hole[i][1]) { hole[i] = null; continue; }
            ys.add(Math.max(0, Math.min(GH, hole[i][1]))); ys.add(Math.max(0, Math.min(GH, hole[i][3])));
        }
        Integer prev = null;
        java.util.List<int[]> row = new java.util.ArrayList<>();
        for (Integer y : ys) {
            if (prev != null && y > prev) {
                row.clear();
                for (int[] h : hole) if (h != null && h[1] <= prev && h[3] >= y) row.add(h);
                row.sort((a2, b2) -> Integer.compare(a2[0], b2[0]));
                int x = 0;
                for (int[] h : row) {
                    if (h[0] > x) clipped(g, x, prev, Math.min(GW, h[0]), y, alpha);
                    x = Math.max(x, h[2]);
                }
                if (x < GW) clipped(g, x, prev, GW, y, alpha);
            }
            prev = y;
        }
    }

    /** The place, drawn only inside a box given in GUI pixels. */
    private void clipped(Gfx g, int x0, int y0, int x1, int y1, float alpha) {
        g.pose().pushMatrix();
        try { g.pose().identity(); g.enableScissor(x0, y0, x1, y1); } finally { g.pose().popMatrix(); }
        try { model.draw(g, x0 / s - 1, y0 / s - 1, x1 / s + 1, y1 / s + 1, alpha); } finally { g.disableScissor(); }
    }

    /** Where a spot of the place is on the canvas right now: x, y, canvas pixels to a block there, distance from the camera. */
    protected float[] place(float x, float y, float z) { return model.project(x, y, z); }

    /**
     * A pane of frosted glass. The world behind is already blurred, so the
     * pane itself is a soft shadow, a thin smoky tint, light falling in
     * from the top and a bright rim.
     */
    protected void glass(Gfx g, float fx, float fy, float fw, float fh, int r, float alpha) {
        int x = Math.round(fx), y = Math.round(fy), w = Math.round(fw), h = Math.round(fh);
        if (w <= 2 || h <= 2 || alpha <= 0.01f) return;
        if (alpha > 0.12f) panes.add(new float[] { x, y, x + w, y + h });
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
