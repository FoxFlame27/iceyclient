package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;

/**
 * Shows the cardinal direction the player is facing (N/S/E/W).
 */
public class DirectionModule extends HudModule {
    public DirectionModule() {
        super("direction", "Direction", 5, 56);
        setEnabled(false);
    }

    @Override
    public String getText(MinecraftClient client) {
        if (client.player == null) return null;
        float yaw = client.player.getYaw();
        // Normalize yaw to 0-360
        yaw = ((yaw % 360) + 360) % 360;
        String dir;
        if (yaw >= 315 || yaw < 45) dir = "S";
        else if (yaw >= 45 && yaw < 135) dir = "W";
        else if (yaw >= 135 && yaw < 225) dir = "N";
        else dir = "E";

        String full;
        if (yaw >= 337.5 || yaw < 22.5) full = "South";
        else if (yaw >= 22.5 && yaw < 67.5) full = "South-West";
        else if (yaw >= 67.5 && yaw < 112.5) full = "West";
        else if (yaw >= 112.5 && yaw < 157.5) full = "North-West";
        else if (yaw >= 157.5 && yaw < 202.5) full = "North";
        else if (yaw >= 202.5 && yaw < 247.5) full = "North-East";
        else if (yaw >= 247.5 && yaw < 292.5) full = "East";
        else full = "South-East";

        return "\u00A7b" + dir + " \u00A77" + full + " \u00A78(" + String.format("%.1f", yaw) + "\u00B0)";
    }
}
