package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

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
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) return;
        if (client.options.damageTiltStrength().get() != 0.0) {
            client.options.damageTiltStrength().set(0.0);
        }
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
