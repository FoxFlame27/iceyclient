package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class DeathCounterModule extends HudModule {
    private static int deaths = 0;
    private static boolean wasDead = false;
    private static boolean registered = false;

    public DeathCounterModule() {
        super("deaths", "Deaths", 5, 280);
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
            boolean dead = client.player.isDead() || client.player.getHealth() <= 0;
            if (dead && !wasDead) deaths++;
            wasDead = dead;
        });
    }

    @Override
    public String getText(MinecraftClient client) {
        return "\u00A7c\u2620 " + deaths + " deaths";
    }
}
