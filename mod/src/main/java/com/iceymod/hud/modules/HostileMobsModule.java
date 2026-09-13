package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;

public class HostileMobsModule extends HudModule {
    public HostileMobsModule() {
        super("hostilecount", "Hostile Mobs", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null || client.level == null) return null;
        int count = 0;
        double range = 16.0;
        for (Entity e : client.level.entitiesForRendering()) {
            if (e instanceof Monster && e.distanceTo(client.player) <= range) count++;
        }
        String color = count >= 5 ? "\u00A7c" : count >= 1 ? "\u00A7e" : "\u00A7a";
        return color + "\u2620 " + count + " hostile";
    }
}
