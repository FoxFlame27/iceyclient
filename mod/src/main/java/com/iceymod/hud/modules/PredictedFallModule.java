package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public class PredictedFallModule extends HudModule {
    public PredictedFallModule() {
        super("predictedfall", "Predicted Fall", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        int px = (int) Math.floor(client.player.getX());
        int py = (int) Math.floor(client.player.getY());
        int pz = (int) Math.floor(client.player.getZ());
        int minY = client.world.getBottomY();
        int groundY = minY;
        for (int y = py - 1; y >= minY; y--) {
            BlockPos p = new BlockPos(px, y, pz);
            if (!client.world.getBlockState(p).isAir()) { groundY = y; break; }
        }
        int fall = py - groundY - 1;
        if (fall < 1) return "\u00A78On ground";
        String color = fall >= 10 ? "\u00A7c" : fall >= 4 ? "\u00A7e" : "\u00A7a";
        return color + "\u2193 " + fall + " blocks down";
    }
}
