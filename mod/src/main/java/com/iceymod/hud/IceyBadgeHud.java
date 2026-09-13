package com.iceymod.hud;

import com.iceymod.compat.Gfx;
import com.iceymod.compat.HudHook;
import com.iceymod.network.IceyNetwork;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a small Icey badge above each remote player's nametag if they're
 * a confirmed Icey Client user. Hand-rolled world→screen projection from
 * the camera's position/yaw/pitch/FOV, drawn as a HUD overlay.
 */
public final class IceyBadgeHud {

    private static final Identifier BADGE = Identifier.fromNamespaceAndPath("iceymod", "icon.png");
    private static final int BADGE_SIZE = 12;
    private static final int BADGE_Y_OFFSET = 28; // px above the nametag

    private IceyBadgeHud() {}

    public static void register() {
        HudHook.register("badges", (g, tickDelta) -> {
            try { renderBadges(g); }
            catch (Throwable t) { /* never crash the HUD */ }
        });
    }

    private static void renderBadges(Gfx g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) return;
        Camera cam = com.iceymod.compat.MC.camera(mc);
        if (cam == null) return;

        Vec3 camPos = com.iceymod.Compat.cameraPos(cam);
        double yawRad = Math.toRadians(com.iceymod.Compat.cameraYaw(cam));
        double pitchRad = Math.toRadians(com.iceymod.Compat.cameraPitch(cam));
        double cy = Math.cos(yawRad), sy = Math.sin(yawRad);
        double cp = Math.cos(pitchRad), sp = Math.sin(pitchRad);

        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        double fov = mc.options.fov().get();
        double tanHalf = Math.tan(Math.toRadians(fov / 2.0));
        double aspect = (double) screenW / screenH;

        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            UUID uuid = p.getUUID();
            if (uuid == null || !IceyNetwork.isOnline(uuid)) continue;

            Vec3 head = new Vec3(p.getX(), p.getY() + p.getEyeHeight() + 0.6, p.getZ());
            Vec3 rel = head.subtract(camPos);

            // Inverse camera rotation: yaw (around Y) then pitch (around X);
            // afterwards the camera looks down +Z and visible points have z > 0.
            double rx = rel.x * cy - rel.z * sy;
            double rz = rel.x * sy + rel.z * cy;
            double ry = rel.y * cp - rz * sp;
            double rz2 = rel.y * sp + rz * cp;
            if (rz2 < 0.5) continue;

            double sx = (rx / (rz2 * tanHalf * aspect)) * (screenW / 2.0) + screenW / 2.0;
            double syScreen = -(ry / (rz2 * tanHalf)) * (screenH / 2.0) + screenH / 2.0;
            if (sx < -BADGE_SIZE || sx > screenW + BADGE_SIZE) continue;
            if (syScreen < -BADGE_SIZE || syScreen > screenH + BADGE_SIZE) continue;

            int drawX = (int) (sx - BADGE_SIZE / 2.0);
            int drawY = (int) (syScreen - BADGE_Y_OFFSET);
            g.blit(BADGE, drawX, drawY, 0f, 0f, BADGE_SIZE, BADGE_SIZE, BADGE_SIZE, BADGE_SIZE);
        }
    }
}
