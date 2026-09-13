package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class VelocityModule extends HudModule {
    public VelocityModule() {
        super("velocity", "Velocity", 5, 485);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null) return null;
        Vec3 v = client.player.getDeltaMovement();
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        return String.format("\u00A7bH:%.2f \u00A7aV:%.2f", horizontal, v.y);
    }
}
