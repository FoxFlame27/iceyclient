package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.MobHealthModule;
import com.iceymod.hud.modules.PlayerHealthModule;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
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
 * <h2>Why this rewrite</h2>
 * v1.86.9 switched to {@code HudRenderCallback} + manual projection
 * because the world-space approach was failing on 1.21.11. Real cause:
 * the {@code VertexConsumerProvider} returned by
 * {@code WorldRenderContext.consumers()} is a buffered {@code Immediate}
 * that needs an explicit {@code .draw()} to flush — without that, the
 * text + bar geometry sit in the buffer forever and never reach the
 * screen. This version goes back to the world-space approach but calls
 * {@code immediate.draw()} explicitly at the end of every render.
 *
 * <h2>Registration</h2>
 * Called from {@link com.iceymod.IceyMod#onInitializeClient()}:
 * <pre>
 *   HealthHudRenderer.register();
 * </pre>
 * Inside, {@code register} wires:
 * <ul>
 *   <li>{@code WorldRenderHook.registerAfterEntities(...)} — fires every
 *       frame post-entity rendering with the matrix stack + consumers.
 *   <li>{@code ClientPlayConnectionEvents.DISCONNECT} — clears the
 *       per-player lerp cache when leaving a world.
 * </ul>
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
    /** Bar dimensions in world units (1 unit ≈ 1 block). */
    private static final float BAR_WIDTH = 1.5f;
    private static final float BAR_HEIGHT = 0.12f;
    /** Border thickness inside/outside the bar. */
    private static final float BORDER = 0.012f;
    /** Vertical offset above the entity's bbox top (sits clear of the
     *  vanilla username nameplate which is at +0.5). */
    private static final float Y_OFFSET = 0.3f;
    /** Per-tick lerp factor for the animated fill — 5% means the bar
     *  catches up to a sudden HP change over about 20 ticks (1 second). */
    private static final float LERP_FACTOR = 0.05f;
    /** Vanilla nameplate text scale used for the numeric label below. */
    private static final float TEXT_SCALE = 0.025f;

    /** Per-player lerped health, keyed by entity UUID. */
    private static final Map<UUID, Float> lerpedHealth = new HashMap<>();

    private HealthHudRenderer() {}

    /** Wire the renderer + disconnect cleanup. */
    public static void register() {
        // Use WorldRenderHook so the AFTER_ENTITIES registration works
        // across the 1.21.8 / 1.21.11 fabric-rendering-v1 package shift.
        if (!WorldRenderHook.registerAfterEntities(HealthHudRenderer::onRender)) {
            System.out.println("[IceyMod] HealthHudRenderer: WorldRenderEvents unavailable — bar disabled");
        }
        try {
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> lerpedHealth.clear());
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

        boolean drewAny = false;

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
                drewAny = true;
            }
        } catch (Throwable t) {
            System.out.println("[IceyMod] HealthHudRenderer render error: " + t);
            return;
        }

        // ── Force-flush the consumer buffer ───────────────────────────
        // On 1.21.11+ the world-render pipeline no longer auto-drains the
        // VertexConsumerProvider at AFTER_ENTITIES — any quads/text we
        // submitted above will sit in the buffer forever unless we call
        // draw() ourselves. Cast safely and call draw() if it's an
        // Immediate; if it isn't, the existing pipeline will flush.
        if (drewAny) {
            try {
                if (vcp instanceof VertexConsumerProvider.Immediate imm) {
                    imm.draw();
                }
            } catch (Throwable ignored) {}
        }
    }

    /** Per-entity render. Translates to the entity's head, billboards
     *  to the camera, draws the bar + numeric label as text. The bar is
     *  Unicode block characters (█/░) with §-color so we go through the
     *  same {@code TextRenderer.draw} path as the numeric label — one
     *  buffered batch, one explicit flush at the end. Avoids the
     *  yarn-drifty RenderLayer / VertexConsumer quad API entirely. */
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

            // Bar line — full alpha white text, the §-colors inside
            // override per character. Drop shadow on for legibility.
            tr.draw(barText, -barWidth / 2f, 0f,
                    0xFFFFFFFF, true, matrix, vcp,
                    TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
            // Numeric label one line below the bar.
            tr.draw(label, -labelWidth / 2f, 10f,
                    0xFFFFFFFF, true, matrix, vcp,
                    TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
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
