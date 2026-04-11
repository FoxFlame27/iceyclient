package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

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
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        int count = 0;
        var inventory = client.player.getInventory();
        int size = inventory.size();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(Items.ARROW) || stack.isOf(Items.SPECTRAL_ARROW) || stack.isOf(Items.TIPPED_ARROW)) {
                count += stack.getCount();
            }
        }
        if (count == 0) return "\u00A78No arrows";
        return "\u00A7e\u27B3 " + count;
    }
}
