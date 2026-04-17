package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SmashKillsModule extends HudModule {
    private static int smashKills = 0;
    private static final Map<Integer, Float> tracked = new HashMap<>();
    private static final Set<Integer> counted = new HashSet<>();
    private static boolean registered = false;

    public SmashKillsModule() {
        super("smashkills", "Smash Kills", 0, 0);
        setEnabled(false);
        registerTracker();
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    private static void registerTracker() {
        if (registered) return;
        registered = true;
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player.getMainHandStack().isOf(Items.MACE) && player.fallDistance > 1.5f && entity instanceof LivingEntity le) {
                tracked.put(entity.getId(), le.getHealth());
            }
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;
            for (Map.Entry<Integer, Float> e : tracked.entrySet()) {
                Entity ent = client.world.getEntityById(e.getKey());
                if ((ent == null || !ent.isAlive()) && !counted.contains(e.getKey())) {
                    counted.add(e.getKey());
                    smashKills++;
                }
            }
        });
    }

    @Override
    public String getText(MinecraftClient client) {
        return "\u00A7c\u2694 " + smashKills + " smash kills";
    }
}
