package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;

public class NearestHostileModule extends HudModule {
    public NearestHostileModule() {
        super("nearesthostile", "Nearest Hostile", 0, 0);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        double min = Double.MAX_VALUE;
        HostileEntity nearest = null;
        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof HostileEntity)) continue;
            double d = e.distanceTo(client.player);
            if (d < min) { min = d; nearest = (HostileEntity) e; }
        }
        if (nearest == null) return "\u00A7a\u2714 No hostiles";
        String color = min < 6 ? "\u00A7c" : min < 16 ? "\u00A7e" : "\u00A7a";
        return color + "\u2620 " + nearest.getName().getString() + " " + String.format("%.1fm", min);
    }
}
