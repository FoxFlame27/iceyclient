package com.iceymod.hud.modules;

import com.iceymod.hud.HudModule;
import com.iceymod.hud.settings.BoolSetting;
import java.util.List;
import net.minecraft.client.Minecraft;
import com.iceymod.compat.Gfx;

/**
 * Renders saved waypoints as a HUD list with name, distance, and direction.
 * Press the waypoint keybind (B) to add your current position as a new waypoint.
 */
public class WaypointsModule extends HudModule {

    /**
     * When ON, every player death drops a "Last Death" waypoint at the
     * player's last known position. Replaces the old standalone
     * LastDeathModule HUD widget — the data is now an actual waypoint
     * you can fly back to.
     */
    public final BoolSetting deathWaypoint = addSetting(
            new BoolSetting("deathWaypoint", "Auto-Waypoint on Death", true));

    private static boolean wasDead = false;

    public WaypointsModule() {
        super("waypoints", "Waypoints", 0, 0);
        setEnabled(false);
    }

    @Override
    public void tick() {
        if (!deathWaypoint.get()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        boolean dead = client.player.isDeadOrDying();
        if (dead && !wasDead) {
            int x = (int) client.player.getX();
            int y = (int) client.player.getY();
            int z = (int) client.player.getZ();
            // Dedup so dying repeatedly in the same lava pit doesn't
            // create 20 "Last Death" waypoints. 32-block radius — close
            // deaths overwrite, distant deaths still register.
            boolean added = WaypointManager.addWaypointIfNew(
                    "Last Death", x, y, z, 0xFFFF3344, 32.0);
            if (added) {
                com.iceymod.compat.Chat.message(net.minecraft.network.chat.Component.literal(
                        "§b[IceyClient] §cLast Death waypointed §8(" + x + ", " + y + ", " + z + ")"), false);
            }
        }
        wasDead = dead;
    }

    public void addCurrentPosition() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        int x = (int) client.player.getX();
        int y = (int) client.player.getY();
        int z = (int) client.player.getZ();
        WaypointManager.addWaypoint("WP" + (WaypointManager.getWaypoints().size() + 1), x, y, z);
    }

    @Override
    public String getText(Minecraft client) { return null; }

    @Override
    public void render(Gfx context, Minecraft client) {
        if (!isEnabled() || client.player == null) return;
        List<WaypointManager.Waypoint> wps = WaypointManager.getWaypoints();
        if (wps.isEmpty()) {
            // Empty placeholder — still occupies real estate so the
            // module stays draggable in HudEditScreen and visible in
            // the HUD.
            String empty = "§7No waypoints";
            int tw = client.font.width(empty);
            this.width = tw + 10;
            this.height = 14;
            context.fill(getX(), getY(), getX() + this.width, getY() + this.height, 0x90000000);
            context.fill(getX(), getY(), getX() + 2, getY() + this.height, 0xFF5BC8F5);
            context.drawString(client.font, empty, getX() + 6, getY() + 3, 0xFFFFFFFF);
            return;
        }

        // Cap to 5 nearest waypoints — beyond that the list overflows
        // the screen and stops being draggable. Sort a copy by 3D
        // distance to player without mutating WaypointManager order.
        final int MAX_DISPLAY = 5;
        final double pX = client.player.getX();
        final double pY = client.player.getY();
        final double pZ = client.player.getZ();
        java.util.List<WaypointManager.Waypoint> sorted = new java.util.ArrayList<>(wps);
        sorted.sort((a, b) -> {
            double da = (a.x - pX) * (a.x - pX) + (a.y - pY) * (a.y - pY) + (a.z - pZ) * (a.z - pZ);
            double db = (b.x - pX) * (b.x - pX) + (b.y - pY) * (b.y - pY) + (b.z - pZ) * (b.z - pZ);
            return Double.compare(da, db);
        });
        int shown = Math.min(sorted.size(), MAX_DISPLAY);

        int x = getX();
        int y = getY();
        int lineH = 14;
        int gap = 2;
        int rowH = lineH + gap;

        int maxWidth = 0;
        String[] texts = new String[shown];
        WaypointManager.Waypoint[] visible = new WaypointManager.Waypoint[shown];
        float yaw = client.player.getYRot();
        for (int i = 0; i < shown; i++) {
            WaypointManager.Waypoint wp = sorted.get(i);
            visible[i] = wp;
            double dx = wp.x - pX;
            double dz = wp.z - pZ;
            double dy = wp.y - pY;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

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
            int tw = client.font.width(texts[i]);
            if (tw > maxWidth) maxWidth = tw;
        }
        // Trailing "+N more" line if there are extras beyond the cap.
        boolean hasOverflow = sorted.size() > shown;
        String overflowText = hasOverflow ? "§7+ " + (sorted.size() - shown) + " more" : null;
        if (hasOverflow) {
            int ow = client.font.width(overflowText);
            if (ow > maxWidth) maxWidth = ow;
        }
        this.width = maxWidth + 10;

        for (int i = 0; i < shown; i++) {
            int lineY = y + i * rowH;
            context.fill(x, lineY, x + this.width, lineY + lineH, 0x90000000);
            context.fill(x, lineY, x + 2, lineY + lineH, visible[i].color);
            context.drawString(client.font, texts[i], x + 6, lineY + 3, 0xFFFFFFFF);
        }
        if (hasOverflow) {
            int lineY = y + shown * rowH;
            context.fill(x, lineY, x + this.width, lineY + lineH, 0x90000000);
            context.fill(x, lineY, x + 2, lineY + lineH, 0xFF888888);
            context.drawString(client.font, overflowText, x + 6, lineY + 3, 0xFFAAAAAA);
        }
        this.height = (shown + (hasOverflow ? 1 : 0)) * rowH - gap;
    }
}
