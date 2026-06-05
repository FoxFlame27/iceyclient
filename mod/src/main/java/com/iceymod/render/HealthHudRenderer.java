package com.iceymod.render;

import com.iceymod.Compat;
import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.MobHealthModule;
import com.iceymod.hud.modules.PlayerHealthModule;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Health HUD as a 2D HUD overlay with manual world→screen projection.
 *
 * <h2>Why this rewrite (v1.86.13)</h2>
 * Three previous attempts at world-space rendering all failed silently
 * on 1.21.11 — the runtime log told the full story:
 *
 * <pre>
 *   WorldRenderEvents.register('LAST') failed: NoSuchFieldException: LAST
 *   WorldRenderEvents.register('AFTER_TRANSLUCENT') failed: NoSuchFieldException
 *   HealthHudRenderer: AFTER_ENTITIES fallback registered
 *   HealthHudRenderer: first-frame render OK (17 entities)
 *   ...but nothing visible.
 * </pre>
 *
 * On fabric-rendering-v1 16.x (1.21.11) <b>LAST and AFTER_TRANSLUCENT
 * were both removed</b>, and AFTER_ENTITIES — the only post-pass phase
 * left — sits inside a render-target context that drops late text
 * submissions even when {@code imm.draw()} is called explicitly. No
 * world-render injection point works for nameplate overlays anymore.
 *
 * <h2>The fix</h2>
 * Drop world-render entirely. Register on {@link HudRenderCallback}
 * (the 2D HUD pipeline) — that's the same pipeline vanilla draws the
 * hotbar / chat / debug screen with, and it's been stable across every
 * yarn version since 1.20. For each {@link LivingEntity} in range:
 *
 * <ol>
 *   <li>Compute the head world position
 *       ({@code entity.pos + (0, height + offset, 0)}).
 *   <li>Subtract the camera world position → camera-relative offset.
 *   <li>Apply the inverse of the camera rotation → camera-local
 *       coordinates (x = right, y = up, -z = forward).
 *   <li>If z &gt;= 0 the entity is behind the camera — skip.
 *   <li>Pinhole-project to screen-space pixels using the game's FOV.
 *   <li>Draw the bar + numeric label with {@link DrawContext}.
 * </ol>
 *
 * The 2D HUD pass runs <i>after</i> the world render finishes and the
 * framebuffer is composited, so our submissions land on the final
 * screen target — no flushing dance required.
 *
 * <p>v1.86.9 tried this approach and abandoned it because "coordinates
 * landed at (68, 492) — basically off-screen". Wrong math: that build
 * either applied the camera rotation forwards instead of inverse, or
 * used FOV in degrees inside Math.tan(). This implementation does
 * neither — inverse-rotates via {@link Quaternionf#conjugate()} and
 * uses Math.toRadians on the FOV.
 *
 * <h2>Module toggles</h2>
 * Reads {@link PlayerHealthModule} (players) and {@link MobHealthModule}
 * (mobs / animals) from the existing HudManager. Each can be turned on
 * or off independently via the Y-menu HUD config.
 */
public final class HealthHudRenderer {

    // ── Tunables ──────────────────────────────────────────────────────
    /** Max distance to render the bar (config-able later). */
    private static final double MAX_DIST = 30.0;
    private static final double MAX_DIST_SQ = MAX_DIST * MAX_DIST;
    /** Vertical offset above the entity's bbox top (sits clear of the
     *  vanilla username nameplate which is at +0.5). */
    private static final float Y_OFFSET = 0.5f;
    /** Per-tick lerp factor for the animated fill — 5% means the bar
     *  catches up to a sudden HP change over about 20 ticks (1 second). */
    private static final float LERP_FACTOR = 0.05f;
    /** Default MC field of view in degrees, used when the options
     *  accessor isn't available on this yarn version. */
    private static final double DEFAULT_FOV_DEG = 70.0;

    /** Per-player lerped health, keyed by entity UUID. */
    private static final Map<UUID, Float> lerpedHealth = new HashMap<>();
    /** First-frame log gate. Logs the projection result on the first
     *  successful draw so future yarn-drift diagnoses don't require
     *  code-spelunking. */
    private static boolean loggedFirstFrame = false;

    private HealthHudRenderer() {}

    /** Wire the renderer + disconnect cleanup. */
    public static void register() {
        try {
            HudRenderCallback.EVENT.register(HealthHudRenderer::onRender);
            System.out.println("[IceyMod] HealthHudRenderer: HUD callback registered");
        } catch (Throwable t) {
            System.out.println("[IceyMod] HealthHudRenderer: HudRenderCallback registration failed: " + t);
            return;
        }
        try {
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                lerpedHealth.clear();
                loggedFirstFrame = false;
            });
        } catch (Throwable t) {
            System.out.println("[IceyMod] HealthHudRenderer: DISCONNECT hook failed (cache will not auto-clear): " + t);
        }
    }

    private static <T extends HudModule> T find(Class<T> cls) {
        for (HudModule m : HudManager.getModules()) {
            if (cls.isInstance(m)) return cls.cast(m);
        }
        return null;
    }

    /** Per-frame entrypoint. Walks loaded LivingEntities, projects each
     *  head position to screen pixels, and draws the bar + label there. */
    private static void onRender(DrawContext drawContext, Object tickCounter) {
        PlayerHealthModule playerMod = find(PlayerHealthModule.class);
        MobHealthModule mobMod = find(MobHealthModule.class);
        boolean showPlayers = playerMod != null && playerMod.isEnabled();
        boolean showMobs = mobMod != null && mobMod.isEnabled();
        if (!showPlayers && !showMobs) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        // Stay out of pause / inventory / config screens, but allow chat
        // (vanilla nameplates are visible while typing in chat too).
        if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) return;

        Camera cam = client.gameRenderer != null ? client.gameRenderer.getCamera() : null;
        if (cam == null) return;
        TextRenderer tr = client.textRenderer;
        if (tr == null) return;

        Vec3d camPos = Compat.cameraPos(cam);
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        double fovDeg = currentFovDeg(client);
        double tanHalf = Math.tan(Math.toRadians(fovDeg / 2.0));
        // Focal length in pixels for vertical FOV. (sh / 2) / tan(fov/2)
        // — same derivation as a pinhole camera. Horizontal projection
        // uses the same focal length because we have a square pixel
        // viewport in scaled GUI space.
        double focal = (sh / 2.0) / tanHalf;
        // Inverse camera rotation: getRotation() rotates camera-local
        // axes into world axes. We want the opposite (world → camera).
        // For a unit quaternion, conjugate == inverse.
        Quaternionf invRot = new Quaternionf(cam.getRotation()).conjugate();

        int drewCount = 0;
        double firstDebugX = 0, firstDebugY = 0;

        try {
            for (var e : client.world.getEntities()) {
                if (!(e instanceof LivingEntity le)) continue;
                if (le == client.player) continue;
                if (le instanceof PlayerEntity pe && pe.isSpectator()) continue;
                if (le.isInvisibleTo(client.player)) continue;

                boolean isPlayer = le instanceof PlayerEntity;
                if (isPlayer && !showPlayers) continue;
                if (!isPlayer && !showMobs) continue;
                if (le.squaredDistanceTo(client.player) > MAX_DIST_SQ) continue;

                float current = le.getHealth();
                float max = le.getMaxHealth();
                if (max <= 0f) continue;

                // Lerp displayed HP toward true HP for a smooth bar.
                Float prev = lerpedHealth.get(le.getUuid());
                float displayed = (prev == null) ? current
                        : MathHelper.lerp(LERP_FACTOR, prev, current);
                lerpedHealth.put(le.getUuid(), displayed);

                // Project head to screen pixels.
                Vec3d entityPos = Compat.entityPos(le);
                double headWorldY = entityPos.y + le.getHeight() + Y_OFFSET;
                Vector3f v = new Vector3f(
                        (float) (entityPos.x - camPos.x),
                        (float) (headWorldY  - camPos.y),
                        (float) (entityPos.z - camPos.z));
                invRot.transform(v);
                // After inverse-rotation, -z is forward. Skip behind-camera.
                if (v.z >= -0.05f) continue;
                double sx = sw / 2.0 + (v.x / -v.z) * focal;
                double sy = sh / 2.0 - (v.y / -v.z) * focal;

                // Build bar text (20 cells of █/░ with §-colors).
                float ratio = MathHelper.clamp(displayed / max, 0f, 1f);
                String barColor = healthColorCode(ratio);
                int barCells = 20;
                int filled = Math.round(ratio * barCells);
                StringBuilder bar = new StringBuilder();
                bar.append("§7[").append(barColor);
                for (int i = 0; i < filled; i++) bar.append('█');
                bar.append("§8");
                for (int i = filled; i < barCells; i++) bar.append('░');
                bar.append("§7]");
                Text barText = Text.literal(bar.toString());
                Text label = Text.literal(
                        String.format("§f%.1f §7/ §f%.0f", displayed, max));

                int barW = tr.getWidth(barText);
                int labelW = tr.getWidth(label);
                int x = (int) Math.round(sx);
                int y = (int) Math.round(sy);

                // Bar above, label one line below. Y offset chosen so
                // the bar sits visually above the player's name tag.
                drawContext.drawTextWithShadow(tr, barText,
                        x - barW / 2, y - 12, 0xFFFFFFFF);
                drawContext.drawTextWithShadow(tr, label,
                        x - labelW / 2, y, 0xFFFFFFFF);

                if (drewCount == 0) { firstDebugX = sx; firstDebugY = sy; }
                drewCount++;
            }
        } catch (Throwable t) {
            System.out.println("[IceyMod] HealthHudRenderer render error: " + t);
            return;
        }

        if (!loggedFirstFrame && drewCount > 0) {
            loggedFirstFrame = true;
            System.out.println("[IceyMod] HealthHudRenderer: first-frame HUD render OK ("
                    + drewCount + " entities, first at " + (int) firstDebugX + "," + (int) firstDebugY
                    + " on " + sw + "x" + sh + " fov=" + fovDeg + ")");
        }
    }

    /** Read the current FOV from GameOptions. Two yarn shapes:
     *  1.21.8: {@code options.getFov()} returns SimpleOption&lt;Integer&gt;.
     *  Newer:  same shape, value is Integer or Double.
     *  Falls back to 70° if anything blows up. */
    private static double currentFovDeg(MinecraftClient client) {
        try {
            Object opts = client.options;
            if (opts == null) return DEFAULT_FOV_DEG;
            Object simpleOpt = opts.getClass().getMethod("getFov").invoke(opts);
            if (simpleOpt == null) return DEFAULT_FOV_DEG;
            Object val = simpleOpt.getClass().getMethod("getValue").invoke(simpleOpt);
            if (val instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {}
        return DEFAULT_FOV_DEG;
    }

    /** Map an HP ratio to the closest section-code color for the filled
     *  cells. Coarser than RGB lerp but produces clean, readable colors
     *  that match the rest of the mod's UI palette. */
    private static String healthColorCode(float ratio) {
        if (ratio >= 0.80f) return "§a"; // green
        if (ratio >= 0.50f) return "§e"; // yellow
        if (ratio >= 0.25f) return "§6"; // gold
        return "§c";                      // red
    }
}
