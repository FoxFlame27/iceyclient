package com.iceymod.compat;

/** Drawn on top of a vanilla screen after it rendered (see ScreenHook). */
@FunctionalInterface
public interface ScreenOverlay {
    void render(Gfx g, int mouseX, int mouseY, float delta);
}
