package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Invisible ping/network optimization: prefers IPv4 stack to avoid
 * IPv6 fallback timeouts on connection start.
 */
public class NetIpv4Module extends HudModule {
    private boolean applied = false;

    public NetIpv4Module() {
        super("net_ipv4", "Net: Prefer IPv4", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.OPTIMIZATION; }

    @Override
    public void tick() {
        if (applied) return;
        try {
            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("java.net.preferIPv6Addresses", "false");
        } catch (Throwable ignored) {}
        applied = true;
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
