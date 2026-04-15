package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class HealthPercentModule extends HudModule {
    public HealthPercentModule() {
        super("healthpct", "Health %", 5, 445);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        float hp = client.player.getHealth();
        float max = client.player.getMaxHealth();
        int pct = Math.round((hp / max) * 100f);
        String color = pct >= 66 ? "\u00A7a" : pct >= 33 ? "\u00A7e" : "\u00A7c";
        return color + "\u2764 " + pct + "%";
    }
}
