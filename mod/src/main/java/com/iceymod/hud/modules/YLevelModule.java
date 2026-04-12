package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class YLevelModule extends HudModule {
    public YLevelModule() {
        super("ylevel", "Y Level", 5, 425);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        int y = (int) client.player.getY();
        String color;
        if (y < 0) color = "\u00A7c";      // red for deep
        else if (y < 40) color = "\u00A7e"; // yellow for mid
        else color = "\u00A7a";              // green for surface
        return color + "Y: " + y;
    }
}
