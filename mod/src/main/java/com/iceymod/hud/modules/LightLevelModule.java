package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.LightType;

public class LightLevelModule extends HudModule {
    public LightLevelModule() {
        super("light", "Light Level", 5, 290);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null || client.world == null) return null;
        var pos = client.player.getBlockPos();
        int sky = client.world.getLightLevel(LightType.SKY, pos);
        int block = client.world.getLightLevel(LightType.BLOCK, pos);
        int total = Math.max(sky, block);
        String color;
        if (total >= 12) color = "\u00A7a";
        else if (total >= 8) color = "\u00A7e";
        else color = "\u00A7c";
        return color + total + " \u00A78(B:" + block + " S:" + sky + ")";
    }
}
