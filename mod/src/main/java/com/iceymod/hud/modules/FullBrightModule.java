package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Forces max gamma while enabled — see in caves without torches.
 */
public class FullBrightModule extends HudModule {
    public FullBrightModule() {
        super("fullbright", "Full Bright", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        if (client.options.getGamma().getValue() < 1.0) {
            client.options.getGamma().setValue(1.0);
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
