package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Shows the current server IP / singleplayer status.
 */
public class ServerModule extends HudModule {
    public ServerModule() {
        super("server", "Server", 5, 90);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.getCurrentServer() != null) {
            ServerData server = client.getCurrentServer();
            return "\u00A7b" + server.ip;
        }
        if (client.isLocalServer()) {
            return "\u00A7aSingleplayer";
        }
        return null;
    }
}
