package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ElytraDurabilityModule extends HudModule {
    public ElytraDurabilityModule() {
        super("elytra", "Elytra", 5, 220);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null) return null;
        ItemStack chest = client.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.is(Items.ELYTRA)) return "\u00A78No elytra";
        int max = chest.getMaxDamage();
        int dmg = chest.getDamageValue();
        int remain = max - dmg;
        int pct = Math.round((remain / (float) max) * 100f);
        String color = pct >= 50 ? "\u00A7a" : pct >= 20 ? "\u00A7e" : "\u00A7c";
        return color + "\u25B3 " + remain + "/" + max + " (" + pct + "%)";
    }
}
