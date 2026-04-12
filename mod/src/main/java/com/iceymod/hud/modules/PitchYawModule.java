package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

public class PitchYawModule extends HudModule {
    public PitchYawModule() {
        super("pitchyaw", "Pitch/Yaw", 5, 470);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        float yaw = client.player.getYaw();
        float pitch = client.player.getPitch();
        return String.format("\u00A7bY:%.0f\u00B0 \u00A7aP:%.0f\u00B0", yaw, pitch);
    }
}
