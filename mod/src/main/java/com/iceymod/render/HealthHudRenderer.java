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
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

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
 * (the 2D HUD pipeline) — same pipeline vanilla draws the hotbar /
 * chat / debug screen with, stable across every yarn version since
 * 1.20. The world→screen projection is done by <b>angles</b>, not
 * quaternion-rotated camera-local vectors:
 *
 * <ol>
 *   <li>Compute the head world position
 *       ({@code entity.pos + (0, height + offset, 0)}).
 *   <li>Compute the world-space yaw and pitch <i>from the camera to
 *       the head</i> using {@link Math#atan2}.
 *   <li>Subtract the camera's own yaw/pitch → delta angles.
 *   <li>Skip if the entity is more than ~89° off in either axis
 *       (behind the camera / far above or below).
 *   <li>Pinhole-project: {@code sx = sw/2 + tan(dYaw) / tan(fovH/2) *
 *       sw/2}, same for sy with vertical FOV.
 *   <li>Draw the bar + numeric label with {@link DrawContext}.
 * </ol>
 *
 * The 2D HUD pass runs <i>after</i> the world render finishes and the
 * framebuffer is composited, so our submissions land on the final
 * screen target — no flushing dance required.
 *
 * <p>Why angle-based instead of quaternion-conjugate: MC's
 * {@link Camera#getRotation()} uses the convention where camera-local
 * <b>+Z is forward</b> (not -Z like standard graphics — yaw=0 means
 * looking south at world +Z, and at yaw=0 the rotation is identity).
 * v1.86.9 and v1.86.13 both got this wrong: v1.86.13's HUD render
 * log showed entities landing at {@code (22, 711) on 427x240} —
 * the projection was actually rendering only the BEHIND-camera
 * entities (the ones my faulty cull check kept) to nonsense screen
 * positions. Angle-based projection sidesteps the whole convention
 * minefield: {@code cam.getYaw()/getPitch()} are MC's own canonical
 * orientation values, {@code atan2(-dx, dz)} matches MC yaw exactly.
 *
 * <h2>Module toggles</h2>
 * Reads {@link PlayerHealthModule} (players) and {@link MobHealthModule}
 * (mobs / animals) from the existing HudManager. Each can be turned on
 * or off independently via the Y-menu HUD config.
 */
public final class HealthHudRenderer {

    // ── Tunables ──────────────────────────────────────────────────────
    /** Max distance to render the bar. Kept short (~10 blocks) so far
     *  entities don't get a fixed-size 32-pixel bar above a 5-pixel
     *  player figure — matches vanilla nameplate fade-out behaviour
     *  ("always loaded, only visible up close"). */
    private static final double MAX_DIST = 10.0;
    private static final double MAX_DIST_SQ = MAX_DIST * MAX_DIST;
    /** Vertical offset above the entity's bbox top. Zero = projected
     *  anchor sits right at the head, below the vanilla username
     *  nameplate (which is at +0.5). Bar then draws just above the
     *  head with a tight 1-pixel gap. */
    private static final float Y_OFFSET = 0.0f;
    /** Fixed bar size — no distance scaling. Slim 32×3 strip. */
    private static final int BAR_WIDTH  = 32;
    private static final int BAR_HEIGHT = 3;
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
        double fovV = currentFovDeg(client);
        // MC's GameOptions.fov is the vertical FOV. Horizontal FOV
        // depends on the aspect ratio:
        //   tan(fovH/2) = tan(fovV/2) * (width / height)
        double aspect = (double) sw / Math.max(1, sh);
        double tanHalfV = Math.tan(Math.toRadians(fovV / 2.0));
        double tanHalfH = tanHalfV * aspect;
        double halfW = sw / 2.0;
        double halfH = sh / 2.0;

        float camYaw = cam.getYaw();
        float camPitch = cam.getPitch();

        int drewCount = 0;
        double firstDebugX = 0, firstDebugY = 0;

        try {
            for (var e : client.world.getEntities()) {
                if (!(e instanceof LivingEntity le)) continue;
                if (le == client.player) continue;
                if (le instanceof PlayerEntity pe && pe.isSpectator()) continue;
                if (le.isInvisibleTo(client.player)) continue;
                // PvP-server spawn areas (and many minigame hubs) use
                // ArmorStandEntity for kit-display NPCs, server signs,
                // welcome statues, etc. They're LivingEntities with
                // max-HP 20 and "always alive" so they'd each get a
                // health bar — drowning the actual players in clutter.
                // Skip them.
                if (le instanceof ArmorStandEntity) continue;

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

                // Project head to screen pixels via angle math.
                Vec3d entityPos = Compat.entityPos(le);
                double dx = entityPos.x - camPos.x;
                double dy = entityPos.y + le.getHeight() + Y_OFFSET - camPos.y;
                double dz = entityPos.z - camPos.z;
                double horizDist = Math.sqrt(dx * dx + dz * dz);
                if (horizDist < 1e-3 && Math.abs(dy) < 1e-3) continue;
                // MC yaw convention: yaw=0 looks south (+Z); yaw=90 looks
                // west (-X). atan2(-dx, dz) matches this exactly.
                double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
                // MC pitch convention: pitch=0 horizontal, +90 down, -90 up.
                double targetPitch = Math.toDegrees(-Math.atan2(dy, horizDist));
                double dYaw = MathHelper.wrapDegrees(targetYaw - camYaw);
                double dPitch = targetPitch - camPitch;
                // Behind / very off-axis — skip.
                if (Math.abs(dYaw) > 89.0) continue;
                if (Math.abs(dPitch) > 89.0) continue;
                // Pinhole-project the angular delta to screen pixels.
                double sx = halfW + Math.tan(Math.toRadians(dYaw)) / tanHalfH * halfW;
                double sy = halfH + Math.tan(Math.toRadians(dPitch)) / tanHalfV * halfH;

                // Fixed-size pixel-quad bar — same dimensions every
                // frame for every entity regardless of distance.
                float ratio = MathHelper.clamp(displayed / max, 0f, 1f);
                int barColor = healthColorArgb(ratio);
                int barW = BAR_WIDTH;
                int barH = BAR_HEIGHT;
                int x = (int) Math.round(sx);
                int y = (int) Math.round(sy);
                int bx = x - barW / 2;
                // 1-pixel gap above the head (projected anchor).
                int by = y - barH - 1;
                int fillW = Math.round(barW * ratio);

                // Black 1px border → dark bg → color fill.
                drawContext.fill(bx - 1, by - 1, bx + barW + 1, by + barH + 1, 0xFF000000);
                drawContext.fill(bx, by, bx + barW, by + barH, 0xFF2a2a35);
                if (fillW > 0) {
                    drawContext.fill(bx, by, bx + fillW, by + barH, barColor);
                }

                // Numeric "14/20" label above the bar — always shown.
                String hpText = String.format("%.0f/%.0f", displayed, max);
                int textW = tr.getWidth(hpText);
                drawContext.drawTextWithShadow(tr, hpText,
                        x - textW / 2, by - 10, 0xFFFFFFFF);

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
                    + " on " + sw + "x" + sh + " fov=" + fovV
                    + " camYaw=" + camYaw + " camPitch=" + camPitch + ")");
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

    /** Map an HP ratio to an ARGB color for the filled bar quad.
     *  Bright green high → yellow → gold → red low. Opaque alpha. */
    private static int healthColorArgb(float ratio) {
        if (ratio >= 0.80f) return 0xFF4ade80; // green
        if (ratio >= 0.50f) return 0xFFfacc15; // yellow
        if (ratio >= 0.25f) return 0xFFfb923c; // gold
        return 0xFFef4444;                      // red
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
