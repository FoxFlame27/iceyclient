package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Automatically sets sprint key to pressed when player moves forward.
 */
public class AutoSprintModule extends HudModule {
    public AutoSprintModule() {
        super("autosprint", "Auto Sprint", 5, 380);
        setEnabled(false);
    }


    @Override
    public void tick() {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options == null) return;
        if (client.options.forwardKey.isPressed() && !client.player.isSneaking()) {
            client.player.setSprinting(true);
        }
    }

    @Override
    public String getText(MinecraftClient client) {
        return "Auto Sprint: \u00A7aON";
    }
}
