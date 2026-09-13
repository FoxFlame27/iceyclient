package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingModule extends HudModule {
    public PingModule() {
        super("ping", "Ping", 5, 22);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.getConnection() == null || client.player == null) return null;
        PlayerInfo entry = client.getConnection().getPlayerInfo(client.player.getUUID());
        if (entry == null) return null;
        int latency = entry.getLatency();
        // Color code based on ping
        String color;
        if (latency < 50) color = "\u00A7a";       // green
        else if (latency < 100) color = "\u00A7e";  // yellow
        else if (latency < 200) color = "\u00A76";  // gold
        else color = "\u00A7c";                      // red
        return color + latency + " ms";
    }
}
