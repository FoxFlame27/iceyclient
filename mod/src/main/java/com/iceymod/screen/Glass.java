package com.iceymod.screen;

import com.iceymod.compat.Gfx;

/**
 * Drawing helpers for the glass-style menu: rounded panels, hairline
 * outlines and colour maths. Everything is built from plain fills, so it
 * works on every Minecraft version the compat layer covers.
 */
final class Glass {
    private Glass() {}

    /** How far each of the first {@code r} rows is pulled in to round a corner. */
    private static int inset(int r, int row) {
        double dy = r - row - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    static void fill(Gfx g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            g.fill(x + in, y + i, x + w - in, y + i + 1, color);
            g.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    /** Rounded fill with square bottom corners, for a header sitting on a body. */
    static void fillTop(Gfx g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w / 2, h)));
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            g.fill(x + in, y + i, x + w - in, y + i + 1, color);
        }
        g.fill(x, y + r, x + w, y + h, color);
    }

    static void outline(Gfx g, int x, int y, int w, int h, int r, int color) {
        if (w <= 1 || h <= 1) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y + 1, x + 1, y + h - 1, color);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
            return;
        }
        int top = inset(r, 0);
        g.fill(x + top, y, x + w - top, y + 1, color);
        g.fill(x + top, y + h - 1, x + w - top, y + h, color);
        for (int i = 1; i < r; i++) {
            int in = inset(r, i);
            // Each row covers from its own inset up to where the row above started.
            int span = Math.max(1, inset(r, i - 1) - in);
            g.fill(x + in, y + i, x + in + span, y + i + 1, color);
            g.fill(x + w - in - span, y + i, x + w - in, y + i + 1, color);
            g.fill(x + in, y + h - 1 - i, x + in + span, y + h - i, color);
            g.fill(x + w - in - span, y + h - 1 - i, x + w - in, y + h - i, color);
        }
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }

    static int alpha(int color, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
    }

    /** Blend {@code color} towards white by {@code t} (0..1), keeping it opaque. */
    static int lighten(int color, float t) {
        int r = (color >> 16) & 0xFF, gr = (color >> 8) & 0xFF, b = color & 0xFF;
        r += Math.round((255 - r) * t);
        gr += Math.round((255 - gr) * t);
        b += Math.round((255 - b) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }
}
