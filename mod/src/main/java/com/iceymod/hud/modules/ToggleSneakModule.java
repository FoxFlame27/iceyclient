package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Shows sneak state indicator.
 */
public class ToggleSneakModule extends HudModule {
    public ToggleSneakModule() {
        super("togglesneak", "Sneak", 5, 185);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.MOVEMENT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        boolean sneaking = client.player.isSneaking();
        return sneaking ? "\u00A7bSNEAK \u00A7aON" : "\u00A78SNEAK OFF";
    }
}
