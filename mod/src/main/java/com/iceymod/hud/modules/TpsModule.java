package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Deque;

public class TpsModule extends HudModule {
    private static final Deque<Long> ticks = new ArrayDeque<>();
    private static boolean registered = false;

    public TpsModule() {
        super("tps", "TPS", 5, 475);
        setEnabled(false);
        registerTracker();
    }

    private static void registerTracker() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_WORLD_TICK.register(world -> {
            long now = System.currentTimeMillis();
            ticks.addLast(now);
            while (!ticks.isEmpty() && now - ticks.peekFirst() > 2000) ticks.pollFirst();
        });
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.world == null) return null;
        float tps = ticks.size() / 2f;
        tps = Math.min(tps, 20f);
        String color = tps >= 18 ? "\u00A7a" : tps >= 14 ? "\u00A7e" : "\u00A7c";
        return color + String.format("%.1f TPS", tps);
    }
}
