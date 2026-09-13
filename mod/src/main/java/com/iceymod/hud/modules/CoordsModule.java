package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

public class CoordsModule extends HudModule {
    public CoordsModule() {
        super("coords", "Coords", 5, 39);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null) return null;
        return String.format("%.1f / %.1f / %.1f",
                client.player.getX(), client.player.getY(), client.player.getZ());
    }
}
