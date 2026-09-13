package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GappleCountModule extends HudModule {
    public GappleCountModule() {
        super("gapples", "Gapples", 5, 160);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null) return null;
        int gold = 0, ench = 0;
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.GOLDEN_APPLE)) gold += s.getCount();
            else if (s.is(Items.ENCHANTED_GOLDEN_APPLE)) ench += s.getCount();
        }
        return "\u00A76\uD83C\uDF4E " + gold + " \u00A7d" + ench;
    }
}
