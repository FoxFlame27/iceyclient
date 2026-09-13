package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

/**
 * Shows food saturation level (hidden stat).
 */
public class SaturationModule extends HudModule {
    public SaturationModule() {
        super("saturation", "Saturation", 5, 230);
        setEnabled(false);
    }


    @Override
    public String getText(Minecraft client) {
        if (client.player == null) return null;
        float sat = client.player.getFoodData().getSaturationLevel();
        int food = client.player.getFoodData().getFoodLevel();
        String color;
        if (sat > 10) color = "\u00A7a";
        else if (sat > 5) color = "\u00A7e";
        else color = "\u00A7c";
        return color + String.format("%.1f", sat) + " \u00A77sat \u00A78| \u00A76" + food + " \u00A77food";
    }
}
