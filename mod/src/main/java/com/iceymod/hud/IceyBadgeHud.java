package com.iceymod.hud;

import com.iceymod.network.IceyNetwork;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Draws a small Icey badge above each remote player's nametag if
 * they're a confirmed Icey Client user.
 *
 * <p>Implementation: HudRenderCallback (always-stable fabric API) +
 * a hand-rolled world→screen projection so we don't have to mixin
 * into MC's entity renderer pipeline (which has the most yarn
 * churn). The projection math is the same yaw/pitch/FOV approach
 * the old health HUD used, with positions taken directly from the
 * entity getters (no reflection — these are intermediary-stable).
 *
 * <p>The actual draw call uses the same reflective trick as
 * {@code PlayerListHudMixin} since {@code DrawContext.drawTexture}
 * keeps changing signature on 1.21.x.
 */
public final class IceyBadgeHud {

    private static final Identifier BADGE = Identifier.of("iceymod", "icon.png");
    private static final int BADGE_SIZE = 12;
    private static final int BADGE_Y_OFFSET = 28; // px above the nametag

    private static Method cachedDrawTexture;
    private static Object cachedPipeline;
    private static boolean drawLookupTried;
    private static boolean firstRenderLogged;

    private IceyBadgeHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            try { renderBadges(ctx); }
            catch (Throwable t) { /* never crash the HUD */ }
        });
        System.out.println("[IceyMod] IceyBadgeHud: HudRenderCallback registered");
    }

    private static void renderBadges(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;
        if (mc.gameRenderer == null) return;
        Camera cam = mc.gameRenderer.getCamera();
        if (cam == null || !cam.isReady()) return;

        Vec3d camPos = cam.getPos();
        double yawRad = Math.toRadians(cam.getYaw());
        double pitchRad = Math.toRadians(cam.getPitch());
        double cy = Math.cos(yawRad), sy = Math.sin(yawRad);
        double cp = Math.cos(pitchRad), sp = Math.sin(pitchRad);

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        double fov = mc.options.getFov().getValue();
        double tanHalf = Math.tan(Math.toRadians(fov / 2.0));
        double aspect = (double) screenW / screenH;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            UUID uuid = p.getUuid();
            if (uuid == null || !IceyNetwork.isOnline(uuid)) continue;

            // World-space head position, slightly above the name tag.
            Vec3d head = new Vec3d(p.getX(),
                                   p.getY() + p.getStandingEyeHeight() + 0.6,
                                   p.getZ());
            Vec3d rel = head.subtract(camPos);

            // Inverse camera rotation: yaw first (around Y), then pitch (around X).
            // After this, the camera is looking down +Z; objects in front have z > 0.
            double rx = rel.x * cy - rel.z * sy;
            double rz = rel.x * sy + rel.z * cy;
            double ry = rel.y * cp - rz * sp;
            double rz2 = rel.y * sp + rz * cp;

            if (rz2 < 0.5) continue; // behind camera or right on it

            double sx = (rx / (rz2 * tanHalf * aspect)) * (screenW / 2.0) + screenW / 2.0;
            double syScreen = -(ry / (rz2 * tanHalf)) * (screenH / 2.0) + screenH / 2.0;

            if (sx < -BADGE_SIZE || sx > screenW + BADGE_SIZE) continue;
            if (syScreen < -BADGE_SIZE || syScreen > screenH + BADGE_SIZE) continue;

            int drawX = (int) (sx - BADGE_SIZE / 2.0);
            int drawY = (int) (syScreen - BADGE_Y_OFFSET);

            if (!firstRenderLogged) {
                System.out.println("[IceyMod] IceyBadgeHud: drawing badge for " + uuid
                    + " at (" + drawX + "," + drawY + ")");
                firstRenderLogged = true;
            }
            drawTextureReflective(ctx, BADGE, drawX, drawY, BADGE_SIZE, BADGE_SIZE);
        }
    }

    private static void drawTextureReflective(DrawContext ctx, Identifier tex,
                                              int x, int y, int w, int h) {
        if (!drawLookupTried) {
            drawLookupTried = true;
            try {
                for (Method m : DrawContext.class.getMethods()) {
                    if (!m.getName().equals("drawTexture")) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length != 10) continue;
                    if (p[1] != Identifier.class) continue;
                    if (p[2] != int.class || p[3] != int.class) continue;
                    if (p[4] != float.class || p[5] != float.class) continue;
                    cachedDrawTexture = m;
                    cachedPipeline = resolvePipeline(p[0]);
                    break;
                }
            } catch (Throwable ignored) {}
        }
        if (cachedDrawTexture == null || cachedPipeline == null) return;
        try {
            cachedDrawTexture.invoke(ctx, cachedPipeline, tex, x, y, 0f, 0f, w, h, w, h);
        } catch (Throwable ignored) {}
    }

    private static Object resolvePipeline(Class<?> pipelineType) {
        String pkg = pipelineType.getPackage() != null
            ? pipelineType.getPackage().getName() : "";
        String[] holders = {
            pkg + ".RenderPipelines",
            "net.minecraft.client.gl.RenderPipelines",
            "net.minecraft.client.render.RenderPipelines",
            "com.mojang.blaze3d.pipeline.RenderPipelines"
        };
        String[] fields = { "GUI_TEXTURED", "GUI", "MAIN_TARGET", "POSITION_TEX" };
        for (String h : holders) {
            try {
                Class<?> c = Class.forName(h);
                for (String f : fields) {
                    try {
                        Field fld = c.getField(f);
                        Object v = fld.get(null);
                        if (v != null) return v;
                    } catch (ReflectiveOperationException ignored) {}
                }
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }
}
