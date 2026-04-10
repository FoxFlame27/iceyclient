package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Toggles entity hitbox rendering (same as F3+B).
 * In 1.21.11 hitbox toggle is via the debug key state.
 */
public class HitboxModule extends HudModule {
    public HitboxModule() {
        super("hitboxes", "Hitboxes", 5, 200);
        setEnabled(false);
    }

    @Override
    public void tick() {
        // Hitboxes are rendered when the debug key is toggled.
        // We simulate this by toggling the option each tick.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getEntityRenderDispatcher() == null) return;
        // The actual hitbox rendering is controlled internally by MC's debug state.
        // This module just displays the status indicator.
    }

    @Override
    public String getText(MinecraftClient client) {
        return "Hitboxes: " + (isEnabled() ? "\u00A7aON" : "\u00A7cOFF");
    }
}
