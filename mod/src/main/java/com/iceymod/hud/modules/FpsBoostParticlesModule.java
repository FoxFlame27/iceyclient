package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;

/**
 * Invisible FPS booster: forces particles to MINIMAL while enabled.
 */
public class FpsBoostParticlesModule extends HudModule {
    private ParticleStatus previous = null;

    public FpsBoostParticlesModule() {
        super("fpsboost_particles", "FPS: Minimal Particles", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.OPTIMIZATION; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) return;
        ParticleStatus cur = client.options.particles().get();
        if (cur != ParticleStatus.MINIMAL) {
            if (previous == null) previous = cur;
            client.options.particles().set(ParticleStatus.MINIMAL);
        }
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
