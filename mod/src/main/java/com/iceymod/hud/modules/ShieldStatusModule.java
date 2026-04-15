package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ShieldStatusModule extends HudModule {
    public ShieldStatusModule() {
        super("shield", "Shield", 5, 385);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        ItemStack main = client.player.getMainHandStack();
        ItemStack off = client.player.getOffHandStack();
        boolean hasShield = main.isOf(Items.SHIELD) || off.isOf(Items.SHIELD);
        if (!hasShield) return "\u00A78No shield";
        if (client.player.isBlocking()) return "\u00A7a\u25AE Blocking";
        return "\u00A7e\u25AE Ready";
    }
}
