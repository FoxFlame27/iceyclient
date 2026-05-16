package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.TargetHealthModule;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Renders a target's health as a "nameplate-style" text above their head
 * in world space. Replaces the old fixed-HUD-position TargetHealthModule
 * text — per user: "change the target health hud to be above the other
 * players head … if you come close to a player it shows but try and
 * maximize the distance."
 *
 * <p>The module {@link TargetHealthModule} now only controls the on/off
 * toggle (its {@code getText} returns null so no HUD text shows). This
 * renderer iterates all loaded players in the client world, filters by
 * distance, and draws their hearts + health/max as text billboarded to
 * the camera using the same {@code WorldRenderEvents.AFTER_ENTITIES}
 * hook pattern as {@link HitboxRenderer}.
 *
 * <p>Max practical distance is bounded by the server's
 * entity-tracking range — typically 64 blocks for players. We render up
 * to {@link #MAX_DIST}; anything further isn't in the client world's
 * player list anyway.
 */
public class TargetHealthRenderer {

    /** Max distance to show the nameplate. 64 matches the vanilla
     *  player-entity tracking range — beyond this the player isn't on
     *  the client side. */
    private static final double MAX_DIST = 64.0;
    private static final double MAX_DIST_SQ = MAX_DIST * MAX_DIST;

    public static void register() {
        try {
            WorldRenderEvents.AFTER_ENTITIES.register(TargetHealthRenderer::onRender);
        } catch (Throwable t) {
            System.out.println("[IceyMod] WorldRenderEvents unavailable — target-health renderer disabled: " + t.getMessage());
        }
    }

    private static TargetHealthModule findModule() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof TargetHealthModule thm) return thm;
        }
        return null;
    }

    private static void onRender(WorldRenderContext ctx) {
        TargetHealthModule mod = findModule();
        if (mod == null || !mod.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        Camera cam = ctx.camera();
        Vec3d camPos = cam.getPos();
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (vcp == null) return;
        TextRenderer textRenderer = client.textRenderer;
        if (textRenderer == null) return;

        try {
            for (PlayerEntity p : client.world.getPlayers()) {
                if (p == client.player) continue;
                if (p.squaredDistanceTo(client.player) > MAX_DIST_SQ) continue;

                float hp = p.getHealth();
                float max = p.getMaxHealth();
                if (max <= 0f) continue;
                String color = hp > max * 0.66f ? "§a"
                             : hp > max * 0.33f ? "§e" : "§c";
                Text text = Text.literal(color + "❤ "
                        + String.format("%.1f", hp) + "/" + String.format("%.0f", max));

                Vec3d pos = p.getPos();
                // Position the text just above the nameplate.
                double yOffset = p.getHeight() + 0.6;
                ms.push();
                ms.translate(pos.x - camPos.x, pos.y - camPos.y + yOffset, pos.z - camPos.z);
                ms.multiply(cam.getRotation());
                // -0.025 mirrors the vanilla nameplate scale (the negative
                // X / Y are because the text quad faces the camera).
                ms.scale(-0.025f, -0.025f, 0.025f);
                Matrix4f matrix = ms.peek().getPositionMatrix();

                int halfWidth = textRenderer.getWidth(text) / 2;
                // SEE_THROUGH so the health stays visible even when the
                // player is behind another block — easier to spot from
                // a distance through cover. Background tint 0x40000000
                // (semi-transparent black) for legibility against bright
                // skies / snow.
                textRenderer.draw(text, (float) (-halfWidth), 0f,
                        0xFFFFFFFF, false, matrix, vcp,
                        TextRenderer.TextLayerType.SEE_THROUGH,
                        0x40000000, 0xF000F0);

                ms.pop();
            }
        } catch (Throwable ignored) {
            // Yarn signature drift fallback — fail silently rather than
            // crash the render frame. Some yarn variants may rename
            // TextRenderer.draw overloads or TextLayerType constants.
        }
    }
}
