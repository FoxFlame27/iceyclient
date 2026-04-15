package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class HurtDirectionModule extends HudModule {
    public HurtDirectionModule() {
        super("hurtdir", "Hurt Direction", 5, 295);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        float t = client.player.getDamageTiltYaw();
        if (client.player.hurtTime <= 0) return "\u00A78\u2022 Safe";
        String arrow;
        float a = ((t % 360) + 360) % 360;
        if (a < 45 || a >= 315) arrow = "\u2191";
        else if (a < 135) arrow = "\u2192";
        else if (a < 225) arrow = "\u2193";
        else arrow = "\u2190";
        return "\u00A7c" + arrow + " Hit!";
    }
}
