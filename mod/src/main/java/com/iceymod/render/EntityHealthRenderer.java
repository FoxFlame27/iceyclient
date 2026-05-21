package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.MobHealthModule;
import com.iceymod.hud.modules.PlayerHealthModule;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders a target's health as a 2D "nameplate" above each player / mob,
 * implemented via {@code HudRenderCallback} + manual world-to-screen
 * projection instead of {@code WorldRenderEvents.AFTER_ENTITIES}.
 *
 * <h2>Why this approach</h2>
 * fabric-rendering-v1 16.x (1.21.11+) restructured the world-render
 * pipeline. {@code AFTER_ENTITIES} fires with a {@code matrices()}
 * MatrixStack and a {@code consumers()} VertexConsumerProvider, but the
 * consumer buffer is no longer auto-flushed at that point in the new
 * pipeline — text submitted via {@code TextRenderer.draw} just sits in
 * the buffer and never reaches the screen. Logs from the user's 1.21.11
 * install confirmed: {@code drew health above player <name>} fired
 * every frame, but no text visible in-game.
 *
 * <p>This rewrite drops the world-space approach. Now per frame we:
 *
 * <ol>
 *   <li>Get the camera position + rotation.
 *   <li>For each {@link LivingEntity} within 64 blocks, compute its
 *       head position in world space.
 *   <li>Transform to camera-relative space via the camera's inverse rotation.
 *   <li>Skip if behind the camera ({@code z >= 0}).
 *   <li>Project to screen via the FOV-based pinhole formula
 *       {@code x' = x * focal / -z}, {@code y' = y * focal / -z}.
 *   <li>Draw the heart-and-HP text via the {@link DrawContext} 2D HUD
 *       pipeline, which has been stable across every yarn version we
 *       care about.
 * </ol>
 *
 * <p>Trade-off: the text doesn't scale with distance — it's always
 * one font size, which actually reads better at distance than a tiny
 * 3D-billboarded nameplate. Visible 64 blocks out.
 */
public final class EntityHealthRenderer {

    private static final double MAX_DIST = 64.0;
    private static final double MAX_DIST_SQ = MAX_DIST * MAX_DIST;

    public static void register() {
        try {
            HudRenderCallback.EVENT.register(EntityHealthRenderer::onHudRender);
        } catch (Throwable t) {
            System.out.println("[IceyMod] HudRenderCallback unavailable — entity-health renderer disabled: " + t.getMessage());
        }
    }

    private static <T extends HudModule> T find(Class<T> cls) {
        for (HudModule m : HudManager.getModules()) {
            if (cls.isInstance(m)) return cls.cast(m);
        }
        return null;
    }

    private static void onHudRender(DrawContext ctx, Object tickCounter) {
        PlayerHealthModule pm = find(PlayerHealthModule.class);
        MobHealthModule mm = find(MobHealthModule.class);
        boolean showPlayers = pm != null && pm.isEnabled();
        boolean showMobs = mm != null && mm.isEnabled();
        if (!showPlayers && !showMobs) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        if (client.gameRenderer == null) return;

        Camera cam = client.gameRenderer.getCamera();
        if (cam == null) return;
        Vec3d camPos = com.iceymod.Compat.cameraPos(cam);
        Quaternionf camRot = cam.getRotation();
        if (camRot == null) return;
        // Invert camera rotation so we can transform world positions
        // into camera-relative space.
        Quaternionf camRotInv = new Quaternionf(camRot).invert();

        TextRenderer tr = client.textRenderer;
        if (tr == null) return;

        // FOV + window for the projection formula.
        double fovDeg;
        try { fovDeg = (double)(int) client.options.getFov().getValue(); }
        catch (Throwable t) { fovDeg = 70.0; }
        double fovRad = Math.toRadians(fovDeg);
        int scaledW = client.getWindow().getScaledWidth();
        int scaledH = client.getWindow().getScaledHeight();
        int pixelH = client.getWindow().getHeight();
        // Focal length in SCALED-screen units. MC's HUD coords are in
        // scaled pixels, not physical pixels, so the projection has to
        // match.
        float focal = (float)((pixelH / 2.0) / Math.tan(fovRad / 2.0));
        float guiScale = (float)(scaledH > 0 ? (double) pixelH / scaledH : 1.0);
        float focalGui = focal / guiScale;

        try {
            for (var e : client.world.getEntities()) {
                if (!(e instanceof LivingEntity le)) continue;
                if (le == client.player) continue;
                boolean isPlayer = le instanceof PlayerEntity;
                if (isPlayer && !showPlayers) continue;
                if (!isPlayer && !showMobs) continue;
                if (le.squaredDistanceTo(client.player) > MAX_DIST_SQ) continue;
                if (le.isInvisibleTo(client.player)) continue;

                float hp = le.getHealth();
                float max = le.getMaxHealth();
                if (max <= 0f) continue;
                String color = hp > max * 0.66f ? "§a"
                             : hp > max * 0.33f ? "§e" : "§c";
                Text text = Text.literal(color + "❤ "
                        + String.format("%.1f", hp) + "/" + String.format("%.0f", max));

                Vec3d entityPos = com.iceymod.Compat.entityPos(le);
                // Head position slightly above the bbox top so the
                // text sits ABOVE the vanilla nameplate.
                double yOffset = le.getHeight() + 0.6;
                Vector3f rel = new Vector3f(
                        (float)(entityPos.x - camPos.x),
                        (float)(entityPos.y - camPos.y + yOffset),
                        (float)(entityPos.z - camPos.z)
                );
                // Transform into camera space.
                rel.rotate(camRotInv);

                // MC's camera looks down -Z. After rotation, z < 0 = in
                // front, z > 0 = behind.
                if (rel.z >= -0.05f) continue;

                // Pinhole projection: screen_x_offset = x * focal / -z
                float sx = scaledW * 0.5f + (rel.x / -rel.z) * focalGui;
                float sy = scaledH * 0.5f + (-rel.y / -rel.z) * focalGui;
                // The (-rel.y) because screen Y grows downward but world Y
                // grows upward; the camera-relative coords are world-axis.

                int width = tr.getWidth(text);
                int x = (int)(sx - width / 2f);
                int y = (int)sy;
                // Draw with shadow so the text is readable on any backdrop.
                ctx.drawText(tr, text, x, y, 0xFFFFFFFF, true);

                if (!loggedFirstRender) {
                    System.out.println("[IceyMod] EntityHealthRenderer: HUD nameplate drawn at ("
                            + x + "," + y + ") for " + (isPlayer ? "player" : "mob"));
                    loggedFirstRender = true;
                }
            }
        } catch (Throwable t) {
            if (!loggedFirstError) {
                System.out.println("[IceyMod] EntityHealthRenderer error (suppressing further): " + t);
                loggedFirstError = true;
            }
        }
    }

    private static boolean loggedFirstRender = false;
    private static boolean loggedFirstError = false;
}
