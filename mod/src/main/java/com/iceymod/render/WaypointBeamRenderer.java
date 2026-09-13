package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.modules.WaypointsModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Renders each waypoint as a beacon-style vertical beam plus a billboarded
 * "name • 42m" tag floating above it. The beam geometry is a port of the
 * vanilla beacon renderer (the vertex layout is identical on every version;
 * only how vertices reach the GPU differs, which WorldRenderHook hides).
 */
public class WaypointBeamRenderer {

    public static void register() {
        if (!WorldRenderHook.registerAfterTranslucent(WaypointBeamRenderer::onRender)) {
            System.out.println("[IceyMod] WorldRenderEvents unavailable — waypoint beams disabled");
        }
    }

    private static boolean beamsEnabled() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof WaypointsModule) return m.isEnabled();
        }
        return false;
    }

    private static boolean layerWarned;

    private static void onRender(WorldRenderHook.Ctx ctx) {
        if (!beamsEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        List<WaypointManager.Waypoint> wps = WaypointManager.getWaypoints();
        if (wps.isEmpty()) return;

        Camera cam = ctx.camera();
        Vec3 camPos = com.iceymod.Compat.cameraPos(cam);
        PoseStack ms = ctx.poseStack();
        if (ms == null) return;
        long worldTime = client.level.getGameTime();
        float tickDelta = ctx.tickDelta();
        Vec3 playerPos = com.iceymod.Compat.entityPos(client.player);

        RenderType inner = ctx.beaconBeam(BeaconRenderer.BEAM_LOCATION, false);
        RenderType outer = ctx.beaconBeam(BeaconRenderer.BEAM_LOCATION, true);
        if (inner == null || outer == null) {
            if (!layerWarned) { layerWarned = true; System.out.println("[IceyMod] beacon beam RenderType unavailable — waypoint beams disabled"); }
            return;
        }

        for (WaypointManager.Waypoint wp : wps) {
            // --- Beam ---
            ms.pushPose();
            ms.translate(wp.x + 0.5 - camPos.x, -64 - camPos.y, wp.z + 0.5 - camPos.z);
            drawBeam(ctx, ms, inner, outer, tickDelta, worldTime, BeaconRenderer.MAX_RENDER_Y, wp.color, 0.2f, 0.25f);
            ms.popPose();

            // --- Floating name + distance tag ---
            try {
                double dx = (wp.x + 0.5) - playerPos.x;
                double dy = wp.y - playerPos.y;
                double dz = (wp.z + 0.5) - playerPos.z;
                int distance = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
                double tagX = wp.x + 0.5;
                double tagY = Math.max(playerPos.y + 20.0, wp.y + 3.0);
                double tagZ = wp.z + 0.5;
                String colored = toChatColor(wp.color) + wp.name + " §7• §f" + distance + "m";
                int cw = client.font.width(colored);

                ms.pushPose();
                ms.translate(tagX - camPos.x, tagY - camPos.y, tagZ - camPos.z);
                ms.mulPose(cam.rotation());              // billboard
                float scale = 0.03f;
                ms.scale(-scale, -scale, scale);         // text is huge by default; flip so it reads upright
                ms.translate(-cw / 2f, 0f, 0f);
                ctx.worldText(Component.literal(colored), true, 0xFFFFFFFF, 0x66000000);
                ms.popPose();
            } catch (Throwable ignored) {}
        }
    }

    /** Port of vanilla BeaconRenderer.renderBeaconBeam minus its own 0.5 translate (caller centres on the block). */
    private static void drawBeam(WorldRenderHook.Ctx ctx, PoseStack ms, RenderType inner, RenderType outer,
                                 float tickDelta, long worldTime, int maxY, int color, float innerRadius, float outerRadius) {
        int yOffset = 0;
        int endY = yOffset + maxY;
        float time = (float) Math.floorMod(worldTime, 40L) + tickDelta;
        float rot = maxY < 0 ? time : -time;
        float frac = Mth.frac(rot * 0.2f - (float) Mth.floor(rot * 0.1f));
        final int innerColor = color | 0xFF000000;
        final int outerColor = (color & 0x00FFFFFF) | (32 << 24);
        final float v1 = -1.0f + frac;
        final float v2 = (float) maxY * (0.5f / innerRadius) + v1;
        final float v4 = (float) maxY + v1;

        ms.pushPose();
        ms.mulPose(Axis.YP.rotationDegrees(time * 2.25f - 45.0f));
        ctx.geometry(inner, (pose, vc) -> beamLayer(pose, vc, innerColor, yOffset, endY,
                0.0f, innerRadius, innerRadius, 0.0f, -innerRadius, 0.0f, 0.0f, -innerRadius, 0.0f, 1.0f, v2, v1));
        ms.popPose();

        ctx.geometry(outer, (pose, vc) -> beamLayer(pose, vc, outerColor, yOffset, endY,
                -outerRadius, -outerRadius, outerRadius, -outerRadius, -outerRadius, outerRadius, outerRadius, outerRadius, 0.0f, 1.0f, v4, v1));
    }

    private static void beamLayer(PoseStack.Pose pose, VertexConsumer vc, int color, int yOffset, int height,
                                  float x1, float z1, float x2, float z2, float x3, float z3, float x4, float z4,
                                  float u1, float u2, float v1, float v2) {
        beamFace(pose, vc, color, yOffset, height, x1, z1, x2, z2, u1, u2, v1, v2);
        beamFace(pose, vc, color, yOffset, height, x4, z4, x3, z3, u1, u2, v1, v2);
        beamFace(pose, vc, color, yOffset, height, x2, z2, x4, z4, u1, u2, v1, v2);
        beamFace(pose, vc, color, yOffset, height, x3, z3, x1, z1, u1, u2, v1, v2);
    }

    private static void beamFace(PoseStack.Pose pose, VertexConsumer vc, int color, int yOffset, int height,
                                 float x1, float z1, float x2, float z2, float u1, float u2, float v1, float v2) {
        beamVertex(pose, vc, color, height, x1, z1, u2, v1);
        beamVertex(pose, vc, color, yOffset, x1, z1, u2, v2);
        beamVertex(pose, vc, color, yOffset, x2, z2, u1, v2);
        beamVertex(pose, vc, color, height, x2, z2, u1, v1);
    }

    private static void beamVertex(PoseStack.Pose pose, VertexConsumer vc, int color, int y, float x, float z, float u, float v) {
        vc.addVertex(pose, x, (float) y, z)
          .setColor(color)
          .setUv(u, v)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(15728880)
          .setNormal(pose, 0.0f, 1.0f, 0.0f);
    }

    /** Map an ARGB color roughly to the nearest MC chat-color code so the tag matches the beam. */
    private static String toChatColor(int argb) {
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        if (r < 40 && g < 40 && b < 40) return "§0";
        if (r > 220 && g > 220 && b > 220) return "§f";
        if (Math.abs(r - g) < 30 && Math.abs(g - b) < 30) return "§7";
        if (r > 200 && g > 160 && b < 120) return "§6";
        if (r > 200 && g > 200 && b < 140) return "§e";
        if (r > 200 && g < 150 && b < 150) return "§c";
        if (r < 150 && g > 180 && b < 150) return "§a";
        if (r < 150 && g > 150 && b > 200) return "§b";
        if (r > 150 && g < 180 && b > 180) return "§d";
        return "§f";
    }
}
