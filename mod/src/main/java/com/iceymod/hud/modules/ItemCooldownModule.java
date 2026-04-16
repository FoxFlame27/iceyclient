package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public class ItemCooldownModule extends HudModule {
    public ItemCooldownModule() {
        super("itemcd", "Item Cooldown", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        ItemStack s = client.player.getMainHandStack();
        if (s.isEmpty()) return null;
        float cd = client.player.getItemCooldownManager().getCooldownProgress(s, 0f);
        if (cd <= 0) return null;
        int pct = Math.round(cd * 100f);
        return "\u00A7e\u231B " + pct + "% CD";
    }
}
