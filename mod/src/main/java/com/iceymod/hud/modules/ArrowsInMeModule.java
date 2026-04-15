package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class ArrowsInMeModule extends HudModule {
    public ArrowsInMeModule() {
        super("arrowsinme", "Arrows Stuck", 5, 370);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        int n = client.player.getStuckArrowCount();
        if (n == 0) return "\u00A78No arrows";
        return "\u00A7c\u27B3 " + n + " stuck";
    }
}
