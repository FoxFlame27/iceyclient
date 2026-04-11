package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class XpModule extends HudModule {
    public XpModule() {
        super("xp", "XP", 5, 335);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.PLAYER; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        int level = client.player.experienceLevel;
        int progress = (int) (client.player.experienceProgress * 100);
        return "\u00A7aLvl " + level + " \u00A77(" + progress + "%)";
    }
}
