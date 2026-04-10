package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class CoordsModule extends HudModule {
    public CoordsModule() {
        super("coords", "Coords", 5, 39);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        return String.format("%.1f / %.1f / %.1f",
                client.player.getX(), client.player.getY(), client.player.getZ());
    }
}
