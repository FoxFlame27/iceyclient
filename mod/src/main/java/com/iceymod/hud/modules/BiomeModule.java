package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class BiomeModule extends HudModule {
    public BiomeModule() {
        super("biome", "Biome", 5, 275);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        try {
            var biome = client.world.getBiome(client.player.getBlockPos());
            var key = biome.getKey().orElse(null);
            if (key == null) return "Unknown";
            String path = key.getValue().getPath();
            // Capitalize and replace underscores
            String[] words = path.split("_");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
