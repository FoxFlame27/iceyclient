package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

/**
 * Shows the current in-game time (day/night cycle).
 */
public class WorldTimeModule extends HudModule {
    public WorldTimeModule() {
        super("worldtime", "World Time", 5, 545);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.level == null) return null;
        long time = com.iceymod.compat.MC.dayTime(client.level) % 24000;
        String phase;
        String color;
        if (time < 6000) { phase = "Morning"; color = "\u00A7e"; }
        else if (time < 12000) { phase = "Day"; color = "\u00A7a"; }
        else if (time < 13000) { phase = "Dusk"; color = "\u00A76"; }
        else if (time < 23000) { phase = "Night"; color = "\u00A71"; }
        else { phase = "Dawn"; color = "\u00A76"; }
        return color + phase + " \u00A78(" + time + ")";
    }
}
