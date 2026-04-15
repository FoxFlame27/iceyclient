package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class BowChargeModule extends HudModule {
    public BowChargeModule() {
        super("bowcharge", "Bow Charge", 5, 355);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        ItemStack active = client.player.getActiveItem();
        if (!active.isOf(Items.BOW) && !active.isOf(Items.CROSSBOW)) return "\u00A78No bow";
        int used = active.getMaxUseTime(client.player) - client.player.getItemUseTimeLeft();
        float charge = Math.min(used / 20f, 1.0f);
        int pct = Math.round(charge * 100f);
        String color = pct >= 100 ? "\u00A7a" : pct >= 50 ? "\u00A7e" : "\u00A7c";
        return color + "\u27B3 " + pct + "%";
    }
}
