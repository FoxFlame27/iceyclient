package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class KillStreakModule extends HudModule {
    private static int streak = 0;
    private static final Map<Integer, Float> lastHit = new HashMap<>();
    private static final Set<Integer> deadTracked = new HashSet<>();
    private static boolean registered = false;

    public KillStreakModule() {
        super("killstreak", "Kill Streak", 5, 265);
        setEnabled(false);
        registerTracker();
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    private static void registerTracker() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            if (client.player.isDead()) { streak = 0; deadTracked.clear(); lastHit.clear(); return; }
            Entity target = client.targetedEntity;
            if (target instanceof LivingEntity le) {
                lastHit.put(le.getId(), le.getHealth());
            }
            for (Map.Entry<Integer, Float> e : lastHit.entrySet()) {
                Entity ent = client.world.getEntityById(e.getKey());
                if ((ent == null || !ent.isAlive()) && !deadTracked.contains(e.getKey())) {
                    deadTracked.add(e.getKey());
                    streak++;
                }
            }
        });
    }

    @Override
    public String getText(MinecraftClient client) {
        String color = streak >= 10 ? "\u00A7c" : streak >= 5 ? "\u00A76" : streak >= 1 ? "\u00A7a" : "\u00A78";
        return color + "\u2694 " + streak + " streak";
    }
}
