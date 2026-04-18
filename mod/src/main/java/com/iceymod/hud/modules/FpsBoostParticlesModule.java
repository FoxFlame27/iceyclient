package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticlesMode;

/**
 * Invisible FPS booster: forces particles to MINIMAL while enabled.
 */
public class FpsBoostParticlesModule extends HudModule {
    private ParticlesMode previous = null;

    public FpsBoostParticlesModule() {
        super("fpsboost_particles", "FPS: Minimal Particles", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.OPTIMIZATION; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        ParticlesMode cur = client.options.getParticles().getValue();
        if (cur != ParticlesMode.MINIMAL) {
            if (previous == null) previous = cur;
            client.options.getParticles().setValue(ParticlesMode.MINIMAL);
        }
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
