package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public class NearestPlayerModule extends HudModule {
    public NearestPlayerModule() {
        super("nearestplayer", "Nearest Player", 5, 415);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        double min = Double.MAX_VALUE;
        PlayerEntity nearest = null;
        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof PlayerEntity) || e == client.player) continue;
            double d = e.distanceTo(client.player);
            if (d < min) { min = d; nearest = (PlayerEntity) e; }
        }
        if (nearest == null) return "\u00A78No players";
        String color = min < 8 ? "\u00A7c" : min < 20 ? "\u00A7e" : "\u00A7a";
        return color + nearest.getName().getString() + " " + String.format("%.1f", min) + "m";
    }
}
