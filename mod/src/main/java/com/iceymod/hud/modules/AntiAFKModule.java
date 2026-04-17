package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Rotates the player slightly every 2 minutes so the server doesn't flag
 * you as AFK and kick you.
 */
public class AntiAFKModule extends HudModule {
    private long lastActionAt = 0;

    public AntiAFKModule() {
        super("antiafk", "Anti-AFK", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        if (lastActionAt == 0) { lastActionAt = now; return; }
        if (now - lastActionAt < 120_000) return;

        // Tiny nudge — rotate yaw by 1 degree and send a swing
        client.player.setYaw(client.player.getYaw() + 1.0f);
        client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        lastActionAt = now;
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
