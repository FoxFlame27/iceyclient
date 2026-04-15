package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class CritIndicatorModule extends HudModule {
    public CritIndicatorModule() {
        super("critready", "Crit Ready", 5, 340);
        setEnabled(false);
    }

    @Override
    public Category getCategory() { return Category.COMBAT; }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        boolean canCrit = client.player.fallDistance > 0
                && !client.player.isOnGround()
                && !client.player.isTouchingWater()
                && !client.player.hasVehicle()
                && !client.player.isClimbing();
        boolean cooldownReady = client.player.getAttackCooldownProgress(0f) >= 0.95f;
        if (canCrit && cooldownReady) return "\u00A7a\u2728 CRIT!";
        if (canCrit) return "\u00A7e\u2728 Wait CD";
        return "\u00A78\u2022 No crit";
    }
}
