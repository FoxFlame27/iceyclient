package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;

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
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        if (client.options.getCloudRenderMode().getValue() != CloudRenderMode.OFF) {
            client.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
