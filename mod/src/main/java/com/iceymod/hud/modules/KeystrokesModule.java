package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.Paint;
import net.minecraft.client.Minecraft;
import com.iceymod.compat.Gfx;

public class KeystrokesModule extends HudModule {
    private static final int KEY_SIZE = 22;
    private static final int GAP = 2;
    private static final int SPACE_H = 12;
    private static final int RADIUS = 4;

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", 5, 78);
    }

    @Override
    public String getText(Minecraft client) {
        return null; // custom rendering
    }

    @Override
    public void render(Gfx context, Minecraft client) {
        if (!isEnabled()) return;
        int bx = getX();
        int by = getY();
        this.width = KEY_SIZE * 3 + GAP * 2;

        var opts = client.options;

        // Row 1: W centered
        drawKey(context, client, "W", bx + KEY_SIZE + GAP, by, KEY_SIZE, KEY_SIZE, opts.keyUp.isDown());

        // Row 2: A S D
        int row2Y = by + KEY_SIZE + GAP;
        drawKey(context, client, "A", bx, row2Y, KEY_SIZE, KEY_SIZE, opts.keyLeft.isDown());
        drawKey(context, client, "S", bx + KEY_SIZE + GAP, row2Y, KEY_SIZE, KEY_SIZE, opts.keyDown.isDown());
        drawKey(context, client, "D", bx + (KEY_SIZE + GAP) * 2, row2Y, KEY_SIZE, KEY_SIZE, opts.keyRight.isDown());

        // Row 3: Space bar (full width), drawn as a low key with a bar glyph
        int row3Y = row2Y + KEY_SIZE + GAP;
        drawKey(context, client, null, bx, row3Y, this.width, SPACE_H, opts.keyJump.isDown());

        this.height = row3Y + SPACE_H - by;
    }

    private void drawKey(Gfx context, Minecraft client, String key, int x, int y, int w, int h, boolean pressed) {
        int accent = barColor.get();
        int bg = pressed ? accent : Paint.alpha(Paint.PANEL, Paint.PANEL_ALPHA);
        Paint.fill(context, x, y, w, h, RADIUS, bg);
        Paint.outline(context, x, y, w, h, RADIUS, pressed ? Paint.alpha(Paint.lighten(accent, 0.45f), 0xA0) : Paint.HAIRLINE);
        int fg = pressed ? 0xFF06121D : textColor.get();
        if (key == null) {
            // Space: a short rounded bar in the middle of the key.
            int bw = Math.max(10, w / 3);
            Paint.fill(context, x + (w - bw) / 2, y + h / 2 - 1, bw, 2, 1, pressed ? 0xFF06121D : Paint.alpha(fg, 0xC0));
            return;
        }
        int textX = x + (w - client.font.width(key)) / 2;
        int textY = y + (h - 8) / 2;
        context.drawString(client.font, key, textX, textY, fg, false);
    }
}
