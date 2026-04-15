package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class AttackCooldownModule extends HudModule {
    public AttackCooldownModule() {
        super("cooldown", "Attack Cooldown", 5, 205);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        float p = client.player.getAttackCooldownProgress(0f);
        int pct = Math.round(p * 100f);
        String color = pct >= 100 ? "\u00A7a" : pct >= 50 ? "\u00A7e" : "\u00A7c";
        return color + "\u2694 " + pct + "%";
    }
}
