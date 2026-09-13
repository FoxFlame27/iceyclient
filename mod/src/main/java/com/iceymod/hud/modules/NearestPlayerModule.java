package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class NearestPlayerModule extends HudModule {
    public NearestPlayerModule() {
        super("nearestplayer", "Nearest Player", 5, 415);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null || client.level == null) return null;
        double min = Double.MAX_VALUE;
        Player nearest = null;
        for (Entity e : client.level.entitiesForRendering()) {
            if (!(e instanceof Player) || e == client.player) continue;
            double d = e.distanceTo(client.player);
            if (d < min) { min = d; nearest = (Player) e; }
        }
        if (nearest == null) return "\u00A78No players";
        String color = min < 8 ? "\u00A7c" : min < 20 ? "\u00A7e" : "\u00A7a";
        return color + nearest.getName().getString() + " " + String.format("%.1f", min) + "m";
    }
}
