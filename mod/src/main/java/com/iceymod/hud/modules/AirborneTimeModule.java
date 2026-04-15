package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class AirborneTimeModule extends HudModule {
    private static long leftGroundAt = 0;
    private static boolean wasGrounded = true;
    private static boolean registered = false;

    public AirborneTimeModule() {
        super("airborne", "Airborne Time", 5, 325);
        setEnabled(false);
        registerTracker();
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    private static void registerTracker() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            boolean g = client.player.isOnGround();
            if (wasGrounded && !g) leftGroundAt = System.currentTimeMillis();
            wasGrounded = g;
        });
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        if (client.player.isOnGround()) return "\u00A78Grounded";
        long ms = System.currentTimeMillis() - leftGroundAt;
        return "\u00A7b\u2191 " + String.format("%.2fs", ms / 1000f);
    }
}
