package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class FoodModule extends HudModule {
    public FoodModule() {
        super("food", "Food", 5, 455);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        int food = client.player.getHungerManager().getFoodLevel();
        String color;
        if (food > 14) color = "\u00A7a";
        else if (food > 6) color = "\u00A7e";
        else color = "\u00A7c";
        return color + "\ud83c\udf56 " + food + "/20";
    }
}
