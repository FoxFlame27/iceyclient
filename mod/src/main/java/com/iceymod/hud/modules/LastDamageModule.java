package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class LastDamageModule extends HudModule {
    private static float lastHp = -1f;
    private static float lastDmg = 0f;
    private static long lastTime = 0;
    private static boolean registered = false;

    public LastDamageModule() {
        super("lastdamage", "Last Damage", 5, 310);
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
            float hp = client.player.getHealth();
            if (lastHp >= 0 && hp < lastHp) {
                lastDmg = lastHp - hp;
                lastTime = System.currentTimeMillis();
            }
            lastHp = hp;
        });
    }

    @Override
    public String getText(MinecraftClient client) {
        if (lastTime == 0 || System.currentTimeMillis() - lastTime > 10000) return "\u00A78No recent hits";
        return "\u00A7c-" + String.format("%.1f", lastDmg) + " HP";
    }
}
