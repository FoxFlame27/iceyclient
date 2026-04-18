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
        if (client.options.getGraphicsMode().getValue() != GraphicsMode.FAST) {
            client.options.getGraphicsMode().setValue(GraphicsMode.FAST);
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
