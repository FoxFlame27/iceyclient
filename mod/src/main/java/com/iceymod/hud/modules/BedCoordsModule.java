package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public class BedCoordsModule extends HudModule {
    public BedCoordsModule() {
        super("bedcoords", "Bed Coords", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        BlockPos spawn = client.player.getLastDeathPos().isPresent() ? null : null;
        // getSpawnPointPosition available client-side? Use personal spawn.
        // In 1.21+ the spawn point is only server-known; show world spawn as fallback
        if (client.world == null) return null;
        // ClientWorld.getSpawnPos() was removed in 1.21.11. Use the
        // Compat helper which tries the method, then falls through to
        // (0, 64, 0) so the HUD still renders something.
        BlockPos ws = com.iceymod.Compat.worldSpawnPos(client.world);
        return "\u00A7a\u2302 Spawn: " + ws.getX() + ", " + ws.getY() + ", " + ws.getZ();
    }
}
