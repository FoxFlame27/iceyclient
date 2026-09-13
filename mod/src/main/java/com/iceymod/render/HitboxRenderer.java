package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.HitboxModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Wireframe bounding boxes around nearby entities, colored by the
 * HitboxModule's color setting. The box edges are emitted by hand so no
 * vanilla shape-renderer helper (which keeps moving) is needed.
 */
public class HitboxRenderer {

    public static void register() {
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
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

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

        List<AABB> boxes = new ArrayList<>();
        for (Entity e : client.level.entitiesForRendering()) {
            if (e == client.player && !includeSelf) continue;
            if (onlyLiving && !(e instanceof LivingEntity)) continue;
            if (e.distanceToSqr(client.player) > rangeSq) continue;
            boxes.add(e.getBoundingBox());
        }
        if (boxes.isEmpty()) return;

        PoseStack ms = ctx.poseStack();
        RenderType type = ctx.lines();
        if (ms == null || type == null) return;
        Vec3 camPos = com.iceymod.Compat.cameraPos(ctx.camera());
        final float fr = r, fg = g, fb = b, fa = a;
        ms.pushPose();
        ms.translate(-camPos.x, -camPos.y, -camPos.z);
        ctx.geometry(type, (pose, vc) -> { for (AABB box : boxes) lineBox(pose, vc, box, fr, fg, fb, fa); });
        ms.popPose();
    }

    private static void lineBox(PoseStack.Pose pose, VertexConsumer vc, AABB bb, float r, float g, float b, float a) {
        float x1 = (float) bb.minX, y1 = (float) bb.minY, z1 = (float) bb.minZ;
        float x2 = (float) bb.maxX, y2 = (float) bb.maxY, z2 = (float) bb.maxZ;
        // 4 edges along X
        edge(pose, vc, x1, y1, z1, x2, y1, z1, r, g, b, a); edge(pose, vc, x1, y2, z1, x2, y2, z1, r, g, b, a);
        edge(pose, vc, x1, y1, z2, x2, y1, z2, r, g, b, a); edge(pose, vc, x1, y2, z2, x2, y2, z2, r, g, b, a);
        // 4 edges along Y
        edge(pose, vc, x1, y1, z1, x1, y2, z1, r, g, b, a); edge(pose, vc, x2, y1, z1, x2, y2, z1, r, g, b, a);
        edge(pose, vc, x1, y1, z2, x1, y2, z2, r, g, b, a); edge(pose, vc, x2, y1, z2, x2, y2, z2, r, g, b, a);
        // 4 edges along Z
        edge(pose, vc, x1, y1, z1, x1, y1, z2, r, g, b, a); edge(pose, vc, x2, y1, z1, x2, y1, z2, r, g, b, a);
        edge(pose, vc, x1, y2, z1, x1, y2, z2, r, g, b, a); edge(pose, vc, x2, y2, z1, x2, y2, z2, r, g, b, a);
    }

    private static void edge(PoseStack.Pose pose, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float r, float g, float b, float a) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0f) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;
        vc.addVertex(pose, ax, ay, az).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, bx, by, bz).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }
}
