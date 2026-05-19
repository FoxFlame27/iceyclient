package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;

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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        // GameOptions.getGraphicsMode() was removed in 1.21.11. Use
        // reflection so the module silently no-ops on versions where
        // the accessor is gone instead of crashing the tick.
        try {
            Object opt = client.options.getClass().getMethod("getGraphicsMode").invoke(client.options);
            if (opt == null) return;
            Object current = opt.getClass().getMethod("getValue").invoke(opt);
            if (current != GraphicsMode.FAST) {
                opt.getClass().getMethod("setValue", Object.class).invoke(opt, GraphicsMode.FAST);
            }
        } catch (Throwable ignored) {
            // Method gone on this MC version — module silently no-ops.
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
