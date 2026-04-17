package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class HeavyCoreCountModule extends HudModule {
    public HeavyCoreCountModule() {
        super("heavycores", "Heavy Cores", 0, 0);
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
            if (s.isOf(Items.HEAVY_CORE)) count += s.getCount();
        }
        if (count == 0) return "\u00A78No cores";
        return "\u00A75\u2B21 " + count + " cores";
    }
}
