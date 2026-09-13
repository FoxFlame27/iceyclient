package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

public class NetherCoordsModule extends HudModule {
    public NetherCoordsModule() {
        super("nethercoords", "Nether Coords", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null || client.level == null) return null;
        double x = client.player.getX();
        double z = client.player.getZ();
        boolean inNether = client.level.dimension() == Level.NETHER;
        if (inNether) {
            return "\u00A7a\u2302 OW: " + (int)(x * 8) + ", " + (int)(z * 8);
        }
        return "\u00A7c\u2302 Nether: " + (int)(x / 8) + ", " + (int)(z / 8);
    }
}
