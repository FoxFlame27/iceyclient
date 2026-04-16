package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class FallDistanceModule extends HudModule {
    public FallDistanceModule() {
        super("falldist", "Fall Distance", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        float fall = (float) client.player.fallDistance;
        if (fall < 0.5f) return "\u00A78Grounded";
        String color = fall >= 10 ? "\u00A7c" : fall >= 4 ? "\u00A7e" : "\u00A7a";
        return color + "\u2193 " + String.format("%.1f", fall) + " blocks";
    }
}
