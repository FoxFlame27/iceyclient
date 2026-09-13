package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

public class CrystalTrackerModule extends HudModule {
    public CrystalTrackerModule() {
        super("crystaltracker", "Crystals", 5, 100);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null || client.level == null) return null;
        int count = 0;
        double range = 8.0;
        for (Entity e : client.level.entitiesForRendering()) {
            if (e instanceof EndCrystal && e.distanceTo(client.player) <= range) count++;
        }
        String color = count >= 3 ? "\u00A7c" : count >= 1 ? "\u00A7e" : "\u00A78";
        return color + "\u2756 " + count + " Crystals";
    }
}
