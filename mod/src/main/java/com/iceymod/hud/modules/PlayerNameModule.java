package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class PlayerNameModule extends HudModule {
    public PlayerNameModule() {
        super("playername", "Player Name", 5, 500);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        return "\u00A7b" + client.player.getName().getString();
    }
}
