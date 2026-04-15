package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;

public class CrystalTrackerModule extends HudModule {
    public CrystalTrackerModule() {
        super("crystaltracker", "Crystals", 5, 100);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        int count = 0;
        double range = 8.0;
        for (Entity e : client.world.getEntities()) {
            if (e instanceof EndCrystalEntity && e.distanceTo(client.player) <= range) count++;
        }
        String color = count >= 3 ? "\u00A7c" : count >= 1 ? "\u00A7e" : "\u00A78";
        return color + "\u2756 " + count + " Crystals";
    }
}
