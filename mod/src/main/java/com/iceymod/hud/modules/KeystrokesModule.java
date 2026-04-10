package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class KeystrokesModule extends HudModule {
    private static final int KEY_SIZE = 22;
    private static final int GAP = 2;

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", 5, 78);
    }

    @Override
    public String getText(MinecraftClient client) {
        return null; // custom rendering
    }

    @Override
    public void render(DrawContext context, MinecraftClient client) {
        if (!isEnabled()) return;
        int bx = getX();
        int by = getY();
        this.width = KEY_SIZE * 3 + GAP * 2;
        this.height = KEY_SIZE * 2 + GAP + KEY_SIZE + GAP; // 3 rows: W, ASD, Space

        var opts = client.options;

        // Row 1: W centered
        drawKey(context, client, "W", bx + KEY_SIZE + GAP, by, KEY_SIZE, KEY_SIZE, opts.forwardKey.isPressed());

        // Row 2: A S D
        int row2Y = by + KEY_SIZE + GAP;
        drawKey(context, client, "A", bx, row2Y, KEY_SIZE, KEY_SIZE, opts.leftKey.isPressed());
        drawKey(context, client, "S", bx + KEY_SIZE + GAP, row2Y, KEY_SIZE, KEY_SIZE, opts.backKey.isPressed());
        drawKey(context, client, "D", bx + (KEY_SIZE + GAP) * 2, row2Y, KEY_SIZE, KEY_SIZE, opts.rightKey.isPressed());

        // Row 3: Space bar (full width)
        int row3Y = row2Y + KEY_SIZE + GAP;
        int spaceW = KEY_SIZE * 3 + GAP * 2;
        boolean spacePressed = opts.jumpKey.isPressed();
        int spaceBg = spacePressed ? 0xCC5BC8F5 : 0x90000000;
        int spaceText = spacePressed ? 0xFF000000 : 0xFFFFFFFF;
        context.fill(bx, row3Y, bx + spaceW, row3Y + 12, spaceBg);
        String spaceLabel = "\u2014\u2014\u2014"; // em dashes as spacebar visual
        int textX = bx + (spaceW - client.textRenderer.getWidth(spaceLabel)) / 2;
        context.drawText(client.textRenderer, spaceLabel, textX, row3Y + 2, spaceText, false);

        this.height = row3Y + 12 - by;
    }

    private void drawKey(DrawContext context, MinecraftClient client, String key, int x, int y, int w, int h, boolean pressed) {
        int bg = pressed ? 0xCC5BC8F5 : 0x90000000;
        int textColor = pressed ? 0xFF000000 : 0xFFFFFFFF;
        context.fill(x, y, x + w, y + h, bg);
        int textX = x + (w - client.textRenderer.getWidth(key)) / 2;
        int textY = y + (h - 8) / 2;
        context.drawText(client.textRenderer, key, textX, textY, textColor, false);
    }
}
