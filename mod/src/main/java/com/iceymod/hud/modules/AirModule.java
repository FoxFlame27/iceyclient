package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class AirModule extends HudModule {
    public AirModule() {
        super("air", "Air", 5, 530);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        int air = client.player.getAir();
        int max = client.player.getMaxAir();
        if (air >= max) return null; // hide when full air (on land)
        int pct = (int) (((float) air / max) * 100);
        String color = pct < 25 ? "\u00A7c" : pct < 50 ? "\u00A7e" : "\u00A7b";
        return color + "Air: " + pct + "%";
    }
}
