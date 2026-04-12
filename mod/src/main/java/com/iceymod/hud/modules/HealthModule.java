package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class HealthModule extends HudModule {
    public HealthModule() {
        super("health", "Health", 5, 320);
        setEnabled(false);
    }


    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        float hp = client.player.getHealth();
        float max = client.player.getMaxHealth();
        float absorb = client.player.getAbsorptionAmount();
        String color;
        float pct = hp / max;
        if (pct > 0.6f) color = "\u00A7a";
        else if (pct > 0.3f) color = "\u00A7e";
        else color = "\u00A7c";
        String absorbText = absorb > 0 ? " \u00A76+" + (int) absorb : "";
        return color + "\u2764 " + (int) hp + "/" + (int) max + absorbText;
    }
}
