package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

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
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) return;
        if (client.options.entityShadows().get()) {
            client.options.entityShadows().set(false);
        }
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
