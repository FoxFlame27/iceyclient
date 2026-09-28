package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

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
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        if (applied) return;
        try {
            // Only the Netty hint. sun.net.useExclusiveBind used to be set
            // here too; it changes how Windows hands out sockets and has
            // nothing to do with latency.
            System.setProperty("io.netty.tcp.nodelay", "true");
        } catch (Throwable ignored) {}
        applied = true;
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
