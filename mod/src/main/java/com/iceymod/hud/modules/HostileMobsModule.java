package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;

public class HostileMobsModule extends HudModule {
    public HostileMobsModule() {
        super("hostilecount", "Hostile Mobs", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        int count = 0;
        double range = 16.0;
        for (Entity e : client.world.getEntities()) {
            if (e instanceof HostileEntity && e.distanceTo(client.player) <= range) count++;
        }
        String color = count >= 5 ? "\u00A7c" : count >= 1 ? "\u00A7e" : "\u00A7a";
        return color + "\u2620 " + count + " hostile";
    }
}
