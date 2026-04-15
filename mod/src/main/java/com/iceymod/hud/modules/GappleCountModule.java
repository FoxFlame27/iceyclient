package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class GappleCountModule extends HudModule {
    public GappleCountModule() {
        super("gapples", "Gapples", 5, 160);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        int gold = 0, ench = 0;
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.GOLDEN_APPLE)) gold += s.getCount();
            else if (s.isOf(Items.ENCHANTED_GOLDEN_APPLE)) ench += s.getCount();
        }
        return "\u00A76\uD83C\uDF4E " + gold + " \u00A7d" + ench;
    }
}
