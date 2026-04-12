package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameMode;

public class GameModeModule extends HudModule {
    public GameModeModule() {
        super("gamemode", "Game Mode", 5, 440);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.interactionManager == null) return null;
        GameMode mode = client.interactionManager.getCurrentGameMode();
        if (mode == null) return null;
        String color;
        switch (mode) {
            case CREATIVE: color = "\u00A7b"; break;
            case SPECTATOR: color = "\u00A77"; break;
            case ADVENTURE: color = "\u00A7e"; break;
            default: color = "\u00A7a"; break; // survival
        }
        return color + mode.getName().toUpperCase();
    }
}
