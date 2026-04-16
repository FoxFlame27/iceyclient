package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

public class NetherCoordsModule extends HudModule {
    public NetherCoordsModule() {
        super("nethercoords", "Nether Coords", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        double x = client.player.getX();
        double z = client.player.getZ();
        boolean inNether = client.world.getRegistryKey() == World.NETHER;
        if (inNether) {
            return "\u00A7a\u2302 OW: " + (int)(x * 8) + ", " + (int)(z * 8);
        }
        return "\u00A7c\u2302 Nether: " + (int)(x / 8) + ", " + (int)(z / 8);
    }
}
