package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class WindChargeCountModule extends HudModule {
    public WindChargeCountModule() {
        super("windcharges", "Wind Charges", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        int count = 0;
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.WIND_CHARGE)) count += s.getCount();
        }
        return "\u00A7b\u27A4 " + count + " wind";
    }
}
