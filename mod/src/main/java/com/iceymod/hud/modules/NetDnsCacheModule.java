package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import java.net.InetAddress;
import java.security.Security;
import net.minecraft.client.Minecraft;

/**
 * Invisible ping/network optimization: shortens DNS cache TTL so stale
 * routes get re-resolved, and pre-warms the current server's hostname.
 */
public class NetDnsCacheModule extends HudModule {
    private boolean applied = false;
    private long lastResolveAt = 0;

    public NetDnsCacheModule() {
        super("net_dnscache", "Net: DNS Cache Tuning", 0, 0);
    }

    @Override
    public Category getCategory() { return Category.OPTIMIZATION; }

    @Override
    protected boolean shouldShowStyleSettings() { return false; }

    @Override
    public void tick() {
        if (!applied) {
            try {
                Security.setProperty("networkaddress.cache.ttl", "60");
                Security.setProperty("networkaddress.cache.negative.ttl", "5");
                System.setProperty("sun.net.inetaddr.ttl", "60");
                System.setProperty("sun.net.inetaddr.negative.ttl", "5");
            } catch (Throwable ignored) {}
            applied = true;
        }

        long now = System.currentTimeMillis();
        if (now - lastResolveAt < 30_000) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getCurrentServer() == null) return;
        String addr = client.getCurrentServer().ip;
        if (addr == null) return;
        String host = addr.contains(":") ? addr.substring(0, addr.indexOf(':')) : addr;
        try {
            InetAddress.getAllByName(host);
        } catch (Throwable ignored) {}
        lastResolveAt = now;
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(com.iceymod.compat.Gfx context, Minecraft client) {}
}
