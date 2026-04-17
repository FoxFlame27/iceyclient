package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * OptiFine-style zoom. Hold the zoom keybind (default M) to zoom in.
 * FOV is modified in GameRendererMixin via getZoomFactor() — we DON'T
 * touch the game's actual FOV option so there's no lag and no option
 * persistence side effects.
 */
public class ZoomModule extends HudModule {
    private static final float ZOOM_TARGET = 0.28f;   // 1.0 = normal, lower = more zoom
    private static final float SMOOTH = 0.25f;
    private boolean zooming = false;
    private float currentFactor = 1.0f;

    public ZoomModule() {
        super("zoom", "Zoom", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    public void setZooming(boolean z) { this.zooming = z; }
    public boolean isZooming() { return zooming; }

    /** 1.0 = no zoom, <1.0 = zoomed in. Multiply FOV by this value. */
    public float getZoomFactor() { return currentFactor; }

    @Override
    public void tick() {
        float target = zooming ? ZOOM_TARGET : 1.0f;
        currentFactor += (target - currentFactor) * SMOOTH;
        if (Math.abs(currentFactor - target) < 0.001f) currentFactor = target;
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
