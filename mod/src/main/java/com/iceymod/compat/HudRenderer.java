package com.iceymod.compat;

/** A HUD overlay drawn every frame via {@link HudHook}. */
@FunctionalInterface
public interface HudRenderer {
    void render(Gfx g, float tickDelta);
}
