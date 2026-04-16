package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class LastDeathModule extends HudModule {
    private static int deathX, deathY, deathZ;
    private static boolean hasDeath = false;
    private static boolean wasDead = false;
    private static boolean registered = false;

    public LastDeathModule() {
        super("lastdeath", "Last Death", 0, 0);
        setEnabled(false);
        registerTracker();
    }

    private static void registerTracker() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            boolean dead = client.player.isDead();
            if (dead && !wasDead) {
                deathX = (int) client.player.getX();
                deathY = (int) client.player.getY();
                deathZ = (int) client.player.getZ();
                hasDeath = true;
            }
            wasDead = dead;
        });
    }

    @Override
    public String getText(MinecraftClient client) {
        if (!hasDeath) return "\u00A78No deaths yet";
        return "\u00A7c\u2620 Died: " + deathX + ", " + deathY + ", " + deathZ;
    }
}
