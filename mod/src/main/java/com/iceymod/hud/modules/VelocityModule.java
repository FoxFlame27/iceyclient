package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public class VelocityModule extends HudModule {
    public VelocityModule() {
        super("velocity", "Velocity", 5, 485);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        Vec3d v = client.player.getVelocity();
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        return String.format("\u00A7bH:%.2f \u00A7aV:%.2f", horizontal, v.y);
    }
}
