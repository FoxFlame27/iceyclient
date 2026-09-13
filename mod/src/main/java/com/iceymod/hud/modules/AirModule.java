package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

public class AirModule extends HudModule {
    public AirModule() {
        super("air", "Air", 5, 530);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null) return null;
        int air = client.player.getAirSupply();
        int max = client.player.getMaxAirSupply();
        if (air >= max) return null; // hide when full air (on land)
        int pct = (int) (((float) air / max) * 100);
        String color = pct < 25 ? "\u00A7c" : pct < 50 ? "\u00A7e" : "\u00A7b";
        return color + "Air: " + pct + "%";
    }
}
