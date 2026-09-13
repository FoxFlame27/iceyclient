package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

/**
 * Kills the FOV punch when sprinting or using speed effects.
 */
public class NoFovChangeModule extends HudModule {
    public NoFovChangeModule() {
        super("nofovchange", "No FOV Change", 0, 0);
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
        if (client.options.fovEffectScale().get() != 0.0) {
            client.options.fovEffectScale().set(0.0);
        }
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
