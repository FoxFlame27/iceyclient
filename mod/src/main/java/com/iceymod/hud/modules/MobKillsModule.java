package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MobKillsModule extends HudModule {
    private static int kills = 0;
    private static final Map<Integer, Float> trackedHealth = new HashMap<>();
    private static final Set<Integer> counted = new HashSet<>();
    private static boolean registered = false;

    public MobKillsModule() {
        super("mobkills", "Mob Kills", 0, 0);
        setEnabled(false);
        registerTracker();
    }

    private static void registerTracker() {
        if (registered) return;
        registered = true;
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity instanceof LivingEntity) {
                trackedHealth.put(entity.getId(), ((LivingEntity) entity).getHealth());
            }
            return InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) return;
            for (Map.Entry<Integer, Float> e : trackedHealth.entrySet()) {
                Entity ent = client.level.getEntity(e.getKey());
                if ((ent == null || !ent.isAlive()) && !counted.contains(e.getKey())) {
                    counted.add(e.getKey());
                    kills++;
                }
            }
        });
    }

    @Override
    public String getText(Minecraft client) {
        return "\u00A7a\u2694 " + kills + " kills";
    }
}
