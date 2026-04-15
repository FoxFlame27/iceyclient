package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public class InventorySlotsModule extends HudModule {
    public InventorySlotsModule() {
        super("invslots", "Inventory Slots", 5, 520);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        var inv = client.player.getInventory();
        int free = 0;
        int main = 36;
        for (int i = 0; i < main; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) free++;
        }
        String color = free > 20 ? "\u00A7a" : free > 5 ? "\u00A7e" : "\u00A7c";
        return color + "\uD83D\uDCE6 " + free + "/" + main + " free";
    }
}
