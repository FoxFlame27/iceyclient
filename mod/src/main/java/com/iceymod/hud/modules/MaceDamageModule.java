package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class MaceDamageModule extends HudModule {
    public MaceDamageModule() {
        super("macedamage", "Mace Damage", 5, 145);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        float fall = (float) client.player.fallDistance;
        double bonus;
        if (fall <= 0) bonus = 0;
        else if (fall <= 3) bonus = fall * 4.0;
        else if (fall <= 8) bonus = 12 + (fall - 3) * 2.0;
        else bonus = 22 + (fall - 8) * 1.0;
        return "\u00A7c\u2694 +" + String.format("%.1f", bonus) + " smash";
    }
}
