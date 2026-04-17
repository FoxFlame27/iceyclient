package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * OptiFine-style zoom. Hold the zoom keybind (C) to zoom in.
 * Smoothly interpolates FOV and restores on release.
 */
public class ZoomModule extends HudModule {
    private static final int ZOOM_FOV = 20;
    private boolean zooming = false;
    private int savedFov = 70;
    private boolean fovSaved = false;
    private float currentZoomFov = 70f;

    public ZoomModule() {
        super("zoom", "Zoom", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    public void setZooming(boolean z) { this.zooming = z; }
    public boolean isZooming() { return zooming; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        SimpleOption<Integer> fovOpt = client.options.getFov();

        if (zooming) {
            if (!fovSaved) {
                savedFov = fovOpt.getValue();
                fovSaved = true;
                currentZoomFov = savedFov;
            }
            // Smooth zoom in
            currentZoomFov += (ZOOM_FOV - currentZoomFov) * 0.3f;
            fovOpt.setValue(Math.round(currentZoomFov));
        } else if (fovSaved) {
            // Smooth zoom out
            currentZoomFov += (savedFov - currentZoomFov) * 0.4f;
            int target = Math.round(currentZoomFov);
            fovOpt.setValue(target);
            if (Math.abs(target - savedFov) <= 1) {
                fovOpt.setValue(savedFov);
                fovSaved = false;
            }
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
