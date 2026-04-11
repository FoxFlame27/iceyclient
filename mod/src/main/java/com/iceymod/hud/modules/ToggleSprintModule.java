package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Shows sprint state indicator.
 */
public class ToggleSprintModule extends HudModule {
    public ToggleSprintModule() {
        super("togglesprint", "Sprint", 5, 200);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.MOVEMENT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        boolean sprinting = client.player.isSprinting();
        return sprinting ? "\u00A76SPRINT \u00A7aON" : "\u00A78SPRINT OFF";
    }
}
