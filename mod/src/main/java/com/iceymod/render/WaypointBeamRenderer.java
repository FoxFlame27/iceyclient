package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.modules.WaypointsModule;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Renders each waypoint as a beacon-style vertical beam so it's visible
 * from far away. Hooks into AFTER_TRANSLUCENT so depth-testing is correct.
 */
public class WaypointBeamRenderer {

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WaypointBeamRenderer::onRender);
    }

    private static boolean beamsEnabled() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof WaypointsModule) return m.isEnabled();
        }
        return false;
    }

    private static void onRender(WorldRenderContext ctx) {
        if (!beamsEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        List<WaypointManager.Waypoint> wps = WaypointManager.getWaypoints();
        if (wps.isEmpty()) return;

        Camera cam = ctx.camera();
        Vec3d camPos = cam.getPos();
        MatrixStack ms = ctx.matrixStack();
        long worldTime = client.world.getTime();
        float tickDelta = ctx.tickCounter().getTickProgress(false);

        for (WaypointManager.Waypoint wp : wps) {
            ms.push();
            // Translate so the waypoint is at the correct world position relative to the camera
            ms.translate(wp.x + 0.5 - camPos.x, -64 - camPos.y, wp.z + 0.5 - camPos.z);
            try {
                BeaconBlockEntityRenderer.renderBeam(
                        ms,
                        ctx.consumers(),
                        BeaconBlockEntityRenderer.BEAM_TEXTURE,
                        tickDelta,
                        1.0f,
                        worldTime,
                        0,
                        BeaconBlockEntityRenderer.MAX_BEAM_HEIGHT,
                        wp.color,
                        0.2f,
                        0.25f
                );
            } catch (Throwable ignored) {
                // If the renderer API ever changes, don't crash the game
            }
            ms.pop();
        }
    }
}
