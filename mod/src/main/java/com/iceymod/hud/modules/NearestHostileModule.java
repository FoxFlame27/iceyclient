package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;

public class NearestHostileModule extends HudModule {
    public NearestHostileModule() {
        super("nearesthostile", "Nearest Hostile", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null || client.level == null) return null;
        double min = Double.MAX_VALUE;
        Monster nearest = null;
        for (Entity e : client.level.entitiesForRendering()) {
            if (!(e instanceof Monster)) continue;
            double d = e.distanceTo(client.player);
            if (d < min) { min = d; nearest = (Monster) e; }
        }
        if (nearest == null) return "\u00A7a\u2714 No hostiles";
        String color = min < 6 ? "\u00A7c" : min < 16 ? "\u00A7e" : "\u00A7a";
        return color + "\u2620 " + nearest.getName().getString() + " " + String.format("%.1fm", min);
    }
}
