package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Invisible FPS booster: disables entity shadows while enabled.
 */
public class FpsBoostShadowsModule extends HudModule {
    public FpsBoostShadowsModule() {
        super("fpsboost_shadows", "FPS: Disable Shadows", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.OPTIMIZATION; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        if (client.options.getEntityShadows().getValue()) {
            client.options.getEntityShadows().setValue(false);
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
