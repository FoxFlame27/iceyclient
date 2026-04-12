package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Custom icy-blue crosshair overlay (drawn over vanilla one).
 */
public class CrosshairModule extends HudModule {
    public CrosshairModule() {
        super("crosshair", "Crosshair", 5, 395);
        setEnabled(false);
    }


    @Override
    public String getText(MinecraftClient client) {
        return null;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client) {
        if (!isEnabled()) return;
        // Draw a small icy blue crosshair at center of screen
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        int cx = sw / 2;
        int cy = sh / 2;
        int color = 0xFF5BC8F5;
        // Horizontal line
        context.fill(cx - 5, cy, cx + 5, cy + 1, color);
        // Vertical line
        context.fill(cx, cy - 5, cx + 1, cy + 5, color);
        // Center dot
        context.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);

        // Hide our module from being a "click target" by zeroing dimensions
        this.width = 0;
        this.height = 0;
    }
}
