package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class ChunkModule extends HudModule {
    public ChunkModule() {
        super("chunk", "Chunk", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        int cx = (int) client.player.getX() >> 4;
        int cz = (int) client.player.getZ() >> 4;
        int localX = ((int) client.player.getX() % 16 + 16) % 16;
        int localZ = ((int) client.player.getZ() % 16 + 16) % 16;
        return "\u00A7e\u25A3 Chunk [" + cx + ", " + cz + "] (" + localX + ", " + localZ + ")";
    }
}
