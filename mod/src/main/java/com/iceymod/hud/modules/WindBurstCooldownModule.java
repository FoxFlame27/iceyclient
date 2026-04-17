package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class WindBurstCooldownModule extends HudModule {
    public WindBurstCooldownModule() {
        super("windburstcd", "Wind Burst CD", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        ItemStack mace = client.player.getMainHandStack();
        if (!mace.isOf(Items.MACE)) return "\u00A78No mace";
        float cd = client.player.getItemCooldownManager().getCooldownProgress(mace, 0f);
        if (cd <= 0) return "\u00A7a\u2728 Ready";
        int pct = Math.round(cd * 100f);
        return "\u00A7c\u231B " + pct + "%";
    }
}
