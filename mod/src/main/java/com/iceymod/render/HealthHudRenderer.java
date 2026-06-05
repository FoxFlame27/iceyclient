package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.MobHealthModule;
import com.iceymod.hud.modules.PlayerHealthModule;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-space health HUD rendered above each LivingEntity's head as a
 * billboarded bar + numeric label. Mirrors the vanilla nameplate render
 * approach but with a custom bar geometry instead of text.
 *
 * <h2>Why this rewrite (v1.86.11)</h2>
 * v1.86.10 added an explicit {@code imm.draw()} flush thinking the
 * VertexConsumerProvider just wasn't being drained at AFTER_ENTITIES on
 * 1.21.11. It still didn't show. Two real problems:
 *
 * <ol>
 *   <li><b>Wrong event phase.</b> On fabric-rendering-v1 16.x (1.21.11)
 *       {@code AFTER_TRANSLUCENT} was <i>removed entirely</i> from the
 *       {@code WorldRenderEvents} class — confirmed at runtime via
 *       {@code NoSuchFieldException: AFTER_TRANSLUCENT}. AFTER_ENTITIES
 *       still exists but fires <i>before</i> the world-render pipeline
 *       finalises depth + framebuffer state for overlay text, so
 *       anything we submit there gets eaten by translucency or
 *       overwritten by the framebuffer composite. The remaining
 *       post-pass injection point is {@code LAST} — fires once after
 *       all world geometry is drawn, before the HUD pass — and it
 *       still exists on 1.21.8 too, so it's the right primary target.
 *   <li><b>Wrong text layer.</b> {@code TextLayerType.NORMAL} is
 *       depth-tested. At LAST the depth buffer holds every opaque +
 *       translucent fragment in front of the entity head, so NORMAL
 *       text gets z-rejected. Vanilla nameplates use {@code SEE_THROUGH}
 *       (uses {@code RenderLayer.getTextSeeThrough(font)} — no depth
 *       test) so the nameplate is always visible.
 * </ol>
 *
 * Switching to LAST + SEE_THROUGH gets pixels on screen. We keep the
 * explicit {@code imm.draw()} flush at the end as a defensive measure
 * — harmless on 1.21.8 where the pipeline drains automatically.
 *
 * <h2>Registration</h2>
 * Called from {@link com.iceymod.IceyMod#onInitializeClient()}:
 * <pre>
 *   HealthHudRenderer.register();
 * </pre>
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
    private static final float Y_OFFSET = 0.3f;
    /** Per-tick lerp factor for the animated fill — 5% means the bar
     *  catches up to a sudden HP change over about 20 ticks (1 second). */
    private static final float LERP_FACTOR = 0.05f;
    /** Vanilla nameplate text scale used for the numeric label below. */
    private static final float TEXT_SCALE = 0.025f;
    /** Vanilla nameplate background alpha (~25% black). */
    private static final int BG_COLOR = 0x40000000;
    /** Full-bright packed light (sky+block both max). */
    private static final int FULL_LIGHT = 0xF000F0;

    /** Per-player lerped health, keyed by entity UUID. */
    private static final Map<UUID, Float> lerpedHealth = new HashMap<>();
    /** First-frame log gate so we know in production logs which event
     *  phase actually fired (helps diagnose future yarn drift without
     *  spamming the console every frame). */
    private static boolean loggedFirstFrame = false;

    private HealthHudRenderer() {}

    /** Wire the renderer + disconnect cleanup. */
    public static void register() {
        // Try injection points in order of "latest in the world pass
        // that still exists on this fabric-rendering-v1 version":
        //   LAST              — exists on both 1.21.8 and 1.21.11
        //   AFTER_TRANSLUCENT — exists on 1.21.8, REMOVED on 1.21.11
        //   AFTER_ENTITIES    — exists on both, but text submissions
        //                       here get eaten on 1.21.11 (the bug)
        // The fallback chain keeps the mod working if a future yarn
        // drift removes LAST too.
        boolean registered = WorldRenderHook.registerLast(HealthHudRenderer::onRender);
        if (!registered) {
            System.out.println("[IceyMod] HealthHudRenderer: LAST unavailable, falling back to AFTER_TRANSLUCENT");
            registered = WorldRenderHook.registerAfterTranslucent(HealthHudRenderer::onRender);
        }
        if (!registered) {
            System.out.println("[IceyMod] HealthHudRenderer: AFTER_TRANSLUCENT unavailable, falling back to AFTER_ENTITIES");
            registered = WorldRenderHook.registerAfterEntities(HealthHudRenderer::onRender);
        }
        if (!registered) {
            System.out.println("[IceyMod] HealthHudRenderer: WorldRenderEvents unavailable — bar disabled");
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

    /** Per-frame entrypoint. Iterates loaded LivingEntities and renders
     *  a bar above each within range. */
    private static void onRender(WorldRenderHook.Ctx ctx) {
        PlayerHealthModule playerMod = find(PlayerHealthModule.class);
        MobHealthModule mobMod = find(MobHealthModule.class);
        boolean showPlayers = playerMod != null && playerMod.isEnabled();
        boolean showMobs = mobMod != null && mobMod.isEnabled();
        if (!showPlayers && !showMobs) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        Camera cam = ctx.camera();
        if (cam == null) cam = client.gameRenderer != null ? client.gameRenderer.getCamera() : null;
        if (cam == null) return;
        Vec3d camPos = com.iceymod.Compat.cameraPos(cam);
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        TextRenderer tr = client.textRenderer;
        if (tr == null) return;

        int drewCount = 0;

        try {
            for (var e : client.world.getEntities()) {
                if (!(e instanceof LivingEntity le)) continue;
                // Skip self — local player doesn't get a bar over their own head.
                if (le == client.player) continue;
                // Skip spectators + invisible-to-viewer entities.
                if (le instanceof PlayerEntity pe && pe.isSpectator()) continue;
                if (le.isInvisibleTo(client.player)) continue;

                boolean isPlayer = le instanceof PlayerEntity;
                if (isPlayer && !showPlayers) continue;
                if (!isPlayer && !showMobs) continue;
                if (le.squaredDistanceTo(client.player) > MAX_DIST_SQ) continue;

                float current = le.getHealth();
                float max = le.getMaxHealth();
                if (max <= 0f) continue;
                // Lerp the displayed health toward the real value so HP
                // bumps feel smooth instead of snapping.
                Float prev = lerpedHealth.get(le.getUuid());
                float displayed = (prev == null) ? current
                        : MathHelper.lerp(LERP_FACTOR, prev, current);
                lerpedHealth.put(le.getUuid(), displayed);

                renderBarForEntity(ms, vcp, cam, camPos, tr, le, displayed, max);
                drewCount++;
            }
        } catch (Throwable t) {
            System.out.println("[IceyMod] HealthHudRenderer render error: " + t);
            return;
        }

        if (!loggedFirstFrame && drewCount > 0) {
            loggedFirstFrame = true;
            System.out.println("[IceyMod] HealthHudRenderer: first-frame render OK (" + drewCount + " entities)");
        }

        // Force-flush the consumer buffer. Vanilla drains the entity VCP
        // once at the end of the entity pass; anything submitted later
        // at AFTER_TRANSLUCENT sits in the buffer unless we draw() it.
        if (drewCount > 0 && vcp instanceof VertexConsumerProvider.Immediate imm) {
            try { imm.draw(); } catch (Throwable ignored) {}
        }
    }

    /** Per-entity render. Translates to the entity's head, billboards
     *  to the camera, draws the bar + numeric label as text. The bar is
     *  Unicode block characters (█/░) with §-color so we go through the
     *  same {@code TextRenderer.draw} path as the numeric label — one
     *  buffered batch, one explicit flush at the end. Avoids the
     *  yarn-drifty RenderLayer / VertexConsumer quad API entirely.
     *
     *  Uses {@code SEE_THROUGH} layer (no depth test) so the nameplate
     *  is visible even when foliage / translucent blocks are between the
     *  camera and the entity head — matches vanilla nameplate behaviour. */
    private static void renderBarForEntity(MatrixStack ms, VertexConsumerProvider vcp, Camera cam,
                                           Vec3d camPos, TextRenderer tr,
                                           LivingEntity le, float displayedHp, float maxHp) {
        Vec3d entityPos = com.iceymod.Compat.entityPos(le);
        double headY = entityPos.y + le.getHeight() + Y_OFFSET;

        ms.push();
        try {
            // Translate to entity head in camera-relative space.
            ms.translate(entityPos.x - camPos.x, headY - camPos.y, entityPos.z - camPos.z);
            // Billboard toward the camera.
            ms.multiply(cam.getRotation());
            // Vanilla nameplate scale (negative X/Y so text reads correctly
            // facing the player).
            ms.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

            float ratio = MathHelper.clamp(displayedHp / maxHp, 0f, 1f);

            // Build the bar as Unicode block characters. 20 cells wide.
            // Filled cells use the color matching the HP ratio, empty
            // cells use dark gray.
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

            // Numeric label below the bar.
            Text label = Text.literal(
                    String.format("§f%.1f §7/ §f%.0f", displayedHp, maxHp));

            Matrix4f matrix = ms.peek().getPositionMatrix();
            int barWidth = tr.getWidth(barText);
            int labelWidth = tr.getWidth(label);

            // SEE_THROUGH: no depth test → bar always visible above the
            // head regardless of foliage / smoke / clouds in between.
            // backgroundColor non-zero → TextRenderer emits the dark
            // backdrop quad through the same VCP in one batch.
            tr.draw(barText, -barWidth / 2f, 0f,
                    0xFFFFFFFF, false, matrix, vcp,
                    TextRenderer.TextLayerType.SEE_THROUGH, BG_COLOR, FULL_LIGHT);
            tr.draw(label, -labelWidth / 2f, 10f,
                    0xFFFFFFFF, false, matrix, vcp,
                    TextRenderer.TextLayerType.SEE_THROUGH, BG_COLOR, FULL_LIGHT);
        } finally {
            ms.pop();
        }
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
