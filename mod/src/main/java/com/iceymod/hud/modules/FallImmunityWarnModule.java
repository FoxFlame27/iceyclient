package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffects;

public class FallImmunityWarnModule extends HudModule {
    public FallImmunityWarnModule() {
        super("fallimmunity", "Fall Immunity", 0, 0);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        if (client.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) return "\u00A7c\u26A0 Slow Falling";
        if (client.player.hasStatusEffect(StatusEffects.LEVITATION)) return "\u00A7c\u26A0 Levitation";
        if (client.player.isTouchingWater()) return "\u00A7c\u26A0 In Water";
        if (client.player.isInLava()) return "\u00A7c\u26A0 In Lava";
        return "\u00A7a\u2714 Smash-ready";
    }
}
