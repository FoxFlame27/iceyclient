package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.Minecraft;

public class PitchYawModule extends HudModule {
    public PitchYawModule() {
        super("pitchyaw", "Pitch/Yaw", 5, 470);
        setEnabled(false);
    }

    @Override
    public String getText(Minecraft client) {
        if (client.player == null) return null;
        float yaw = client.player.getYRot();
        float pitch = client.player.getXRot();
        return String.format("\u00A7bY:%.0f\u00B0 \u00A7aP:%.0f\u00B0", yaw, pitch);
    }
}
