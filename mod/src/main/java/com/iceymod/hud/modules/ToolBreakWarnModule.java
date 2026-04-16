package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public class ToolBreakWarnModule extends HudModule {
    public ToolBreakWarnModule() {
        super("toolwarn", "Tool Break Warn", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        ItemStack s = client.player.getMainHandStack();
        if (s.isEmpty() || !s.isDamageable()) return null;
        int max = s.getMaxDamage();
        int remain = max - s.getDamage();
        float pct = remain / (float) max;
        if (pct > 0.15f) return null;
        boolean flash = (System.currentTimeMillis() / 400) % 2 == 0;
        String color = flash ? "\u00A7c" : "\u00A76";
        return color + "\u26A0 TOOL BREAKING! " + remain + " left";
    }
}
