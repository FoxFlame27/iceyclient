package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class EnderPearlCountModule extends HudModule {
    public EnderPearlCountModule() {
        super("pearls", "Pearls", 5, 175);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null) return null;
        int count = 0;
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.ENDER_PEARL)) count += s.getCount();
        }
        return "\u00A7b\u25C9 " + count + " pearls";
    }
}
