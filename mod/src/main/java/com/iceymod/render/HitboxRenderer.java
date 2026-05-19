package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.HitboxModule;
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
        // Routes through WorldRenderHook so it works regardless of which
        // package path Fabric API ships WorldRenderEvents at (the location
        // moved between 1.21.8 and 1.21.11).
        if (!WorldRenderHook.registerAfterEntities(HitboxRenderer::onRender)) {
            System.out.println("[IceyMod] WorldRenderEvents unavailable — hitbox renderer disabled");
        }
    }

    private static HitboxModule findModule() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof HitboxModule hm) return hm;
        }
        return null;
    }

    private static void onRender(WorldRenderHook.Ctx ctx) {
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
        Vec3d camPos = com.iceymod.Compat.cameraPos(cam);
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;

        // RenderLayer.getLines() and VertexRendering.drawBox both shifted
        // signature between 1.21.8 and 1.21.11. Use reflection so we can
        // gracefully no-op on the version where they don't match.
        VertexConsumer lines;
        Object linesLayer;
        try {
            linesLayer = RenderLayer.class.getMethod("getLines").invoke(null);
            if (linesLayer == null) return;
            // VertexConsumerProvider.getBuffer(RenderLayer) — the param
            // type may have changed too. Try with the layer we just got.
            lines = (VertexConsumer) consumers.getClass()
                    .getMethod("getBuffer", linesLayer.getClass().getSuperclass())
                    .invoke(consumers, linesLayer);
        } catch (Throwable t) {
            // Reflection failed — can't render hitboxes on this MC version.
            return;
        }
        if (lines == null) return;

        ms.push();
        ms.translate(-camPos.x, -camPos.y, -camPos.z);
        try {
            for (Entity e : client.world.getEntities()) {
                if (e == client.player && !includeSelf) continue;
                if (onlyLiving && !(e instanceof LivingEntity)) continue;
                if (e.squaredDistanceTo(client.player) > rangeSq) continue;
                Box box = e.getBoundingBox();
                // VertexRendering.drawBox signature changed in 1.21.11.
                // Reflective dispatch tries the known shapes.
                drawBoxReflective(ms, lines, box, r, g, b, a);
            }
        } catch (Throwable ignored) {
            // Iterator / drawBox renamed — fail silently rather than crash.
        }
        ms.pop();
    }

    private static void drawBoxReflective(MatrixStack ms, VertexConsumer lines, Box box,
                                          float r, float g, float b, float a) {
        // Try (MatrixStack, VertexConsumer, Box, float×4) — 1.21.8 shape
        try {
            VertexRendering.class.getMethod("drawBox",
                    MatrixStack.class, VertexConsumer.class, Box.class,
                    float.class, float.class, float.class, float.class)
                .invoke(null, ms, lines, box, r, g, b, a);
            return;
        } catch (Throwable ignored) {}
        // Try (MatrixStack, VertexConsumer, double×6, float×4) — older shape
        try {
            VertexRendering.class.getMethod("drawBox",
                    MatrixStack.class, VertexConsumer.class,
                    double.class, double.class, double.class,
                    double.class, double.class, double.class,
                    float.class, float.class, float.class, float.class)
                .invoke(null, ms, lines,
                        box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                        r, g, b, a);
            return;
        } catch (Throwable ignored) {}
        // Both shapes failed — silently skip this entity's box.
    }
}
