package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

public class FpsModule extends HudModule {
    public FpsModule() {
        super("fps", "FPS", 5, 5);
    }

    @Override
    public String getText(Minecraft client) {
        return client.getFps() + " FPS";
    }
}
