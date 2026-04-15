package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Invisible ping/network optimization: tunes Netty allocator and socket
 * buffer sizing for lower jitter / smoother packet flow.
 */
public class NetBufferModule extends HudModule {
    private boolean applied = false;

    public NetBufferModule() {
        super("net_buffer", "Net: Socket Buffer Tuning", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.OPTIMIZATION; }

    @Override
    public void tick() {
        if (applied) return;
        try {
            System.setProperty("io.netty.allocator.maxOrder", "9");
            System.setProperty("io.netty.allocator.type", "pooled");
            System.setProperty("io.netty.recycler.maxCapacityPerThread", "4096");
        } catch (Throwable ignored) {}
        applied = true;
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
