package com.iceymod.compat;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenHook {
    private ScreenHook() {}
    public static void afterRender(Screen screen, ScreenOverlay overlay) {
        ScreenEvents.afterForeground(screen).register((scr, g, mouseX, mouseY, delta) -> overlay.render(new Gfx(g), mouseX, mouseY, delta));
    }
}
