package com.iceymod.screen;

import com.iceymod.compat.Gfx;
import com.iceymod.hud.Paint;

/** Drawing helpers for the glass-style menu; the HUD's {@link Paint} does the work. */
final class Glass {
    private Glass() {}
    static void fill(Gfx g, int x, int y, int w, int h, int r, int color) { Paint.fill(g, x, y, w, h, r, color); }
    static void fillTop(Gfx g, int x, int y, int w, int h, int r, int color) { Paint.fillTop(g, x, y, w, h, r, color); }
    static void outline(Gfx g, int x, int y, int w, int h, int r, int color) { Paint.outline(g, x, y, w, h, r, color); }
    static int alpha(int color, int a) { return Paint.alpha(color, a); }
    static int lighten(int color, float t) { return Paint.lighten(color, t); }
}
