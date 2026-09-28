package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

/**
 * Invisible network option: when a server has both kinds of address, use
 * the IPv4 one, so a half-working IPv6 route can't slow down connecting.
 *
 * It must not switch IPv6 off (java.net.preferIPv4Stack): on a network
 * that only has IPv6 that leaves the game unable to reach any server.
 */
public class NetIpv4Module extends HudModule {
    private boolean applied = false;

    public NetIpv4Module() {
        super("net_ipv4", "Net: Prefer IPv4", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.OPTIMIZATION; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        if (applied) return;
        try {
            System.setProperty("java.net.preferIPv6Addresses", "false");
        } catch (Throwable ignored) {}
        applied = true;
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
