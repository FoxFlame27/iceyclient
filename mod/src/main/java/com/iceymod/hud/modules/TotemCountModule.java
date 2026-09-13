package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TotemCountModule extends HudModule {
    public TotemCountModule() {
        super("totems", "Totems", 5, 235);
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
            if (s.is(Items.TOTEM_OF_UNDYING)) count += s.getCount();
        }
        String color = count >= 3 ? "\u00A7a" : count >= 1 ? "\u00A7e" : "\u00A7c";
        return color + "\u2620 " + count + " totems";
    }
}
