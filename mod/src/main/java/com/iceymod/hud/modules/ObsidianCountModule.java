package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ObsidianCountModule extends HudModule {
    public ObsidianCountModule() {
        super("obsidian", "Obsidian", 5, 250);
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
            if (s.is(Items.OBSIDIAN)) count += s.getCount();
        }
        return "\u00A75\u25A3 " + count + " obby";
    }
}
