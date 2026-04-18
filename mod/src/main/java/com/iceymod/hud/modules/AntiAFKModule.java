package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Rotates the player slightly every 2 minutes so the server doesn't flag
 * you as AFK and kick you.
 */
public class AntiAFKModule extends HudModule {
    public final com.iceymod.hud.settings.IntSetting intervalMinutes = addSetting(
            new com.iceymod.hud.settings.IntSetting("intervalMin", "Interval (min)", 2, 1, 10));
    public final com.iceymod.hud.settings.BoolSetting swingHand = addSetting(
            new com.iceymod.hud.settings.BoolSetting("swing", "Swing Hand", true));
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
        long intervalMs = intervalMinutes.get() * 60_000L;
        if (now - lastActionAt < intervalMs) return;

        client.player.setYaw(client.player.getYaw() + 1.0f);
        if (swingHand.get()) client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        lastActionAt = now;
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
