package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class ItemCooldownModule extends HudModule {
    public ItemCooldownModule() {
        super("itemcd", "Item Cooldown", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null) return null;
        ItemStack s = client.player.getMainHandItem();
        if (s.isEmpty()) return null;
        float cd = client.player.getCooldowns().getCooldownPercent(s, 0f);
        if (cd <= 0) return null;
        int pct = Math.round(cd * 100f);
        return "\u00A7e\u231B " + pct + "% CD";
    }
}
