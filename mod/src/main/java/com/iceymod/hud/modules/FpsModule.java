package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class FpsModule extends HudModule {
    public FpsModule() {
        super("fps", "FPS", 5, 5);
    }

    @Override
    public String getText(MinecraftClient client) {
        return client.getCurrentFps() + " FPS";
    }
}
