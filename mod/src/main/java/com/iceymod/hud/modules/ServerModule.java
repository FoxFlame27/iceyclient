package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

/**
 * Shows the current server IP / singleplayer status.
 */
public class ServerModule extends HudModule {
    public ServerModule() {
        super("server", "Server", 5, 90);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null) {
            ServerInfo server = client.getCurrentServerEntry();
            return "\u00A7b" + server.address;
        }
        if (client.isInSingleplayer()) {
            return "\u00A7aSingleplayer";
        }
        return null;
    }
}
