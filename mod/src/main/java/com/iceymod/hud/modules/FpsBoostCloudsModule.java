package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;

/**
 * Invisible FPS booster: disables cloud rendering while enabled.
 */
public class FpsBoostCloudsModule extends HudModule {
    public FpsBoostCloudsModule() {
        super("fpsboost_clouds", "FPS: Disable Clouds", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.OPTIMIZATION; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) return;
        if (client.options.cloudStatus().get() != CloudStatus.OFF) {
            client.options.cloudStatus().set(CloudStatus.OFF);
        }
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
