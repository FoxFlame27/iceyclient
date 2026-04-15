package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

import java.net.InetAddress;
import java.security.Security;

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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getCurrentServerEntry() == null) return;
        String addr = client.getCurrentServerEntry().address;
        if (addr == null) return;
        String host = addr.contains(":") ? addr.substring(0, addr.indexOf(':')) : addr;
        try {
            InetAddress.getAllByName(host);
        } catch (Throwable ignored) {}
        lastResolveAt = now;
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client) {}
}
