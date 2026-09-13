package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shows total arrow count in inventory (like Lunar's arrow counter).
 */
public class ArrowCountModule extends HudModule {
    public ArrowCountModule() {
        super("arrows", "Arrows", 5, 215);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null) return null;
        int count = 0;
        var inventory = client.player.getInventory();
        int size = inventory.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW)) {
                count += stack.getCount();
            }
        }
        if (count == 0) return "\u00A78No arrows";
        return "\u00A7e\u27B3 " + count;
    }
}
