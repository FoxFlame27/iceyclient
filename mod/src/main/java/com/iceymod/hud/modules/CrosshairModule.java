package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.ColorSetting;
import com.iceymod.hud.settings.EnumSetting;
import com.iceymod.hud.settings.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Custom crosshair overlay drawn on top of the vanilla one.
 */
public class CrosshairModule extends HudModule {
    public final ColorSetting color = addSetting(new ColorSetting("color", "Color", 0xFF5BC8F5));
    public final IntSetting size = addSetting(new IntSetting("size", "Size", 5, 2, 15));
    public final IntSetting thickness = addSetting(new IntSetting("thickness", "Thickness", 1, 1, 4));
    public final EnumSetting style = addSetting(new EnumSetting("style", "Style",
            new String[]{"Cross", "Dot", "Cross+Dot", "Square"}, 2));

    public CrosshairModule() {
        super("crosshair", "Crosshair", 5, 395);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(DrawContext context, MinecraftClient client) {
        if (!isEnabled()) return;
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        int cx = sw / 2;
        int cy = sh / 2;
        int c = color.get();
        int s = size.get();
        int t = thickness.get();
        int styleIdx = style.get();

        boolean drawCross = styleIdx == 0 || styleIdx == 2;
        boolean drawDot = styleIdx == 1 || styleIdx == 2;
        boolean drawSquare = styleIdx == 3;

        if (drawCross) {
            context.fill(cx - s, cy - t / 2, cx + s, cy + t / 2 + (t % 2), c);
            context.fill(cx - t / 2, cy - s, cx + t / 2 + (t % 2), cy + s, c);
        }
        if (drawDot) {
            context.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        }
        if (drawSquare) {
            context.fill(cx - s, cy - s, cx + s, cy - s + t, c);
            context.fill(cx - s, cy + s - t + 1, cx + s, cy + s + 1, c);
            context.fill(cx - s, cy - s, cx - s + t, cy + s, c);
            context.fill(cx + s - t + 1, cy - s, cx + s + 1, cy + s, c);
        }

        this.width = 0;
        this.height = 0;
    }
}
