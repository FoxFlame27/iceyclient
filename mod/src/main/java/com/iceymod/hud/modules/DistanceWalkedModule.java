package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class DistanceWalkedModule extends HudModule {
    private static double total = 0;
    private static double lastX = Double.NaN, lastY = Double.NaN, lastZ = Double.NaN;
    private static boolean registered = false;

    public DistanceWalkedModule() {
        super("distwalked", "Distance", 5, 505);
        setEnabled(false);
        registerTracker();
    }

    private static void registerTracker() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            double x = client.player.getX(), y = client.player.getY(), z = client.player.getZ();
            if (!Double.isNaN(lastX)) {
                double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 20) total += d;
            }
            lastX = x; lastY = y; lastZ = z;
        });
    }

    @Override
    public String getText(MinecraftClient client) {
        if (total >= 1000) return "\u00A7b\u2192 " + String.format("%.2f km", total / 1000);
        return "\u00A7b\u2192 " + String.format("%.0f m", total);
    }
}
