package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

public class BiomeModule extends HudModule {
    public BiomeModule() {
        super("biome", "Biome", 5, 275);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null || client.level == null) return null;
        try {
            var biome = client.level.getBiome(client.player.blockPosition());
            var key = biome.unwrapKey().orElse(null);
            if (key == null) return "Unknown";
            String path = key.identifier().getPath();
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
