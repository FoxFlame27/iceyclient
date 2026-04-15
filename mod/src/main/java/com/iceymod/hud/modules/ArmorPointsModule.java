package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class ArmorPointsModule extends HudModule {
    public ArmorPointsModule() {
        super("armorpoints", "Armor Points", 5, 430);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        int ap = client.player.getArmor();
        String color = ap >= 15 ? "\u00A7a" : ap >= 8 ? "\u00A7e" : ap > 0 ? "\u00A76" : "\u00A7c";
        return color + "\u2692 " + ap + " armor";
    }
}
