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
        int lineH = 14;
        int gap = 2;
        int rowH = lineH + gap;

        // Compute widest row first so all rows share the same width — matches the style of single-line modules
        int maxWidth = 0;
        String[] texts = new String[wps.size()];
        for (int i = 0; i < wps.size(); i++) {
            WaypointManager.Waypoint wp = wps.get(i);
            double dx = wp.x - client.player.getX();
            double dz = wp.z - client.player.getZ();
            double dy = wp.y - client.player.getY();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

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

            texts[i] = arrow + " " + wp.name + " " + (int) dist + "m";
            int tw = client.textRenderer.getWidth(texts[i]);
            if (tw > maxWidth) maxWidth = tw;
        }
        this.width = maxWidth + 10;

        // Render each row matching the default module style: 0x90 black bg, 2px colored side bar, white text
        for (int i = 0; i < wps.size(); i++) {
            WaypointManager.Waypoint wp = wps.get(i);
            int lineY = y + i * rowH;
            context.fill(x, lineY, x + this.width, lineY + lineH, 0x90000000);
            context.fill(x, lineY, x + 2, lineY + lineH, wp.color);
            context.drawTextWithShadow(client.textRenderer, texts[i], x + 6, lineY + 3, 0xFFFFFFFF);
        }
        this.height = wps.size() * rowH - gap;
    }
}
