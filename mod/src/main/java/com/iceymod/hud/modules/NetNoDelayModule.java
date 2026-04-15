package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Invisible ping/network optimization: hints Netty/Java to send packets
 * without Nagle batching (TCP_NODELAY) for lower per-packet latency.
 */
public class NetNoDelayModule extends HudModule {
    private boolean applied = false;

    public NetNoDelayModule() {
        super("net_nodelay", "Net: TCP No-Delay", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.OPTIMIZATION; }

    @Override
    public void tick() {
        if (applied) return;
        try {
            System.setProperty("io.netty.tcp.nodelay", "true");
            System.setProperty("sun.net.useExclusiveBind", "false");
        } catch (Throwable ignored) {}
        applied = true;
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
