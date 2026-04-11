package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Prevents fall damage by resetting fallDistance every tick.
 */
public class NoFallModule extends HudModule {
    public NoFallModule() {
        super("nofall", "No Fall", 5, 245);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.UTILITY; }

    @Override
    public void tick() {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.fallDistance = 0;
        }
    }

    @Override
    public String getText(MinecraftClient client) {
        return "No Fall: \u00A7aON";
    }
}
