package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Renders saved waypoints as a HUD list with name, distance, and direction.
 * Press the waypoint keybind (B) to add your current position as a new waypoint.
 */
public class WaypointsModule extends HudModule {

    public WaypointsModule() {
        super("waypoints", "Waypoints", 0, 0);
        setEnabled(false);
    }

    public void addCurrentPosition() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        int x = (int) client.player.getX();
        int y = (int) client.player.getY();
        int z = (int) client.player.getZ();
        WaypointManager.addWaypoint("WP" + (WaypointManager.getWaypoints().size() + 1), x, y, z);
    }

    @Override
    public String getText(MinecraftClient client) { return null; }

    @Override
    public void render(DrawContext context, MinecraftClient client) {
        if (!isEnabled() || client.player == null) return;
        List<WaypointManager.Waypoint> wps = WaypointManager.getWaypoints();
        if (wps.isEmpty()) return;

        int x = getX();
        int y = getY();
        this.width = 140;

        int drawn = 0;
        for (WaypointManager.Waypoint wp : wps) {
            double dx = wp.x - client.player.getX();
            double dz = wp.z - client.player.getZ();
            double dy = wp.y - client.player.getY();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // Direction arrow based on player yaw
            float yaw = client.player.getYaw();
            double angle = Math.toDegrees(Math.atan2(-dx, dz));
            double rel = ((angle - yaw) % 360 + 540) % 360 - 180;
            String arrow;
            if (rel > -22.5 && rel <= 22.5) arrow = "\u2191";
            else if (rel > 22.5 && rel <= 67.5) arrow = "\u2197";
            else if (rel > 67.5 && rel <= 112.5) arrow = "\u2192";
            else if (rel > 112.5 && rel <= 157.5) arrow = "\u2198";
            else if (rel > 157.5 || rel <= -157.5) arrow = "\u2193";
            else if (rel > -157.5 && rel <= -112.5) arrow = "\u2199";
            else if (rel > -112.5 && rel <= -67.5) arrow = "\u2190";
            else arrow = "\u2196";

            String text = arrow + " " + wp.name + " " + (int) dist + "m";
            int textW = client.textRenderer.getWidth(text);
            if (textW + 10 > this.width) this.width = textW + 10;

            int lineY = y + drawn * 14;
            context.fill(x, lineY, x + this.width, lineY + 13, 0x80000000);
            context.fill(x, lineY, x + 2, lineY + 13, wp.color);
            context.drawTextWithShadow(client.textRenderer, text, x + 5, lineY + 3, wp.color);
            drawn++;
        }
        this.height = Math.max(drawn * 14, 14);
    }
}
