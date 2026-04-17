package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Kills the damage-tilt camera shake so you can keep aiming through hits.
 */
public class NoHurtCamModule extends HudModule {
    public NoHurtCamModule() {
        super("nohurtcam", "No Hurt Cam", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        if (client.options.getDamageTiltStrength().getValue() != 0.0) {
            client.options.getDamageTiltStrength().setValue(0.0);
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
