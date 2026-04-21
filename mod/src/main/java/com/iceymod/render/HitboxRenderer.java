package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.HitboxModule;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Wireframe bounding boxes around every loaded entity, colored by the
 * HitboxModule's color setting. Registered at AFTER_ENTITIES so entities
 * are already in the frame and the lines sit cleanly on top.
 */
public class HitboxRenderer {

    public static void register() {
        // Same VulkanMod-stub guard as WaypointBeamRenderer.
        try {
            WorldRenderEvents.AFTER_ENTITIES.register(HitboxRenderer::onRender);
        } catch (Throwable t) {
            System.out.println("[IceyMod] WorldRenderEvents unavailable — hitbox renderer disabled: " + t.getMessage());
        }
    }

    private static HitboxModule findModule() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof HitboxModule hm) return hm;
        }
        return null;
    }

    private static void onRender(WorldRenderContext ctx) {
        HitboxModule mod = findModule();
        if (mod == null || !mod.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        int argb = mod.color.get();
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >>> 16) & 0xFF) / 255f;
        float g = ((argb >>> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        if (a <= 0f) a = 1f;

        int rangeBlocks = mod.range.get();
        int rangeSq = rangeBlocks * rangeBlocks;
        boolean includeSelf = mod.showSelf.get();
        boolean onlyLiving = mod.onlyLiving.get();

        Camera cam = ctx.camera();
        Vec3d camPos = cam.getPos();
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;

        VertexConsumer lines;
        try {
            lines = consumers.getBuffer(RenderLayer.getLines());
        } catch (Throwable t) {
            return;
        }

        ms.push();
        ms.translate(-camPos.x, -camPos.y, -camPos.z);
        try {
            for (Entity e : client.world.getEntities()) {
                if (e == client.player && !includeSelf) continue;
                if (onlyLiving && !(e instanceof LivingEntity)) continue;
                if (e.squaredDistanceTo(client.player) > rangeSq) continue;
                Box box = e.getBoundingBox();
                VertexRendering.drawBox(ms, lines, box, r, g, b, a);
            }
        } catch (Throwable ignored) {
            // If the entity iterator or drawBox signature changes in a future
            // version, fail silently rather than crash the frame.
        }
        ms.pop();
    }
}
