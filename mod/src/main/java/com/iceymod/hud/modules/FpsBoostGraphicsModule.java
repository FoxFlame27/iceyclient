package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

/**
 * Invisible FPS booster: forces Fast graphics mode while enabled.
 */
public class FpsBoostGraphicsModule extends HudModule {
    public FpsBoostGraphicsModule() {
        super("fpsboost_graphics", "FPS: Fast Graphics", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.OPTIMIZATION; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) return;
        // Options.graphicsMode() (1.21.8) became graphicsPreset() (1.21.11+);
        // the compat layer picks the right one per build. A jar running on a
        // point release it wasn't built for just no-ops here.
        try { com.iceymod.compat.MC.forceFastGraphics(client); }
        catch (Throwable ignored) {}
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
