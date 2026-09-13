package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.Map;

public class TotemPopsModule extends HudModule {
    private static int pops = 0;
    private static final Map<Integer, Float> lastHealth = new HashMap<>();
    private static boolean registered = false;

    public TotemPopsModule() {
        super("totempops", "Totem Pops", 5, 115);
        setEnabled(false);
        registerTracker();
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    private static void registerTracker() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) return;
            for (Entity e : client.level.entitiesForRendering()) {
                if (!(e instanceof Player) || e == client.player) continue;
                LivingEntity le = (LivingEntity) e;
                Float prev = lastHealth.get(e.getId());
                if (prev != null && prev <= 0.5f && le.getHealth() > 5f) pops++;
                lastHealth.put(e.getId(), le.getHealth());
            }
        });
    }

    public static void reset() { pops = 0; lastHealth.clear(); }

    @Override
    public String getText(Minecraft client) {
        return "\u00A7d\u2726 " + pops + " Pops";
    }
}
