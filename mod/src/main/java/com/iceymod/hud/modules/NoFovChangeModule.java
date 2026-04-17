package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

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
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        if (client.options.getFovEffectScale().getValue() != 0.0) {
            client.options.getFovEffectScale().setValue(0.0);
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
