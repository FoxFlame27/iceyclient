package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.MobHealthModule;
import com.iceymod.hud.modules.PlayerHealthModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Renders an entity's health as a "nameplate-style" text above its head
 * in world space. Replaces the old fixed-HUD-position TargetHealthModule.
 * Two-module toggle:
 *
 * <ul>
 *   <li>{@link PlayerHealthModule} — controls the nameplate for OTHER
 *       players (excluding the local player).
 *   <li>{@link MobHealthModule} — controls the nameplate for non-player
 *       LivingEntities (zombies, villagers, animals, etc.).
 * </ul>
 *
 * <h2>Render approach (vanilla nameplate algorithm)</h2>
 * Mirrors {@code EntityRenderer.renderLabelIfPresent} — the canonical
 * way Minecraft draws floating text above entities:
 * <ol>
 *   <li>Translate world-relative to camera (subtract {@code camera.getPos}).
 *   <li>Multiply by {@code camera.getRotation()} so the text quad faces the camera.
 *   <li>Scale by {@code -0.025f} on X/Y (the {@code -} flips the text
 *       so it reads correctly facing the player) and {@code 0.025f} on Z.
 *   <li>Draw text TWICE:
 *       <ol type="a">
 *         <li>{@code TextLayerType.SEE_THROUGH} with a faint alpha
 *             ({@code 0x21FFFFFF}) — visible through walls, faded.
 *         <li>{@code TextLayerType.NORMAL} with full-opaque white
 *             ({@code -1}) — drawn over the SEE_THROUGH layer in
 *             world view, so unoccluded text reads at full strength
 *             while occluded text shows faintly through cover.
 *       </ol>
 * </ol>
 *
 * <p>Sight cap is the vanilla player entity-tracking distance (~64
 * blocks for players, ~80 for mobs depending on type). Beyond that the
 * entity isn't on the client and we couldn't draw it anyway.
 */
public final class EntityHealthRenderer {

    private static final double MAX_DIST = 64.0;
    private static final double MAX_DIST_SQ = MAX_DIST * MAX_DIST;
    /** Vanilla nameplate text scale. */
    private static final float SCALE = 0.025f;

    public static void register() {
        if (!WorldRenderHook.registerAfterEntities(EntityHealthRenderer::onRender)) {
            System.out.println("[IceyMod] WorldRenderEvents unavailable — entity-health renderer disabled");
        }
    }

    private static <T extends HudModule> T find(Class<T> cls) {
        for (HudModule m : HudManager.getModules()) {
            if (cls.isInstance(m)) return cls.cast(m);
        }
        return null;
    }

    private static void onRender(WorldRenderHook.Ctx ctx) {
        PlayerHealthModule playerMod = find(PlayerHealthModule.class);
        MobHealthModule mobMod = find(MobHealthModule.class);
        boolean showPlayers = playerMod != null && playerMod.isEnabled();
        boolean showMobs = mobMod != null && mobMod.isEnabled();
        if (!showPlayers && !showMobs) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        Camera cam = ctx.camera();
        Vec3d camPos = cam.getPos();
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (vcp == null) return;
        TextRenderer tr = client.textRenderer;
        if (tr == null) return;

        try {
            // Iterate all loaded entities. Filter by LivingEntity first
            // (skips items, arrows, paintings) then split into player vs
            // mob, then check the corresponding module's enabled flag.
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

                drawNameplate(ms, vcp, cam, tr, le, text);

                if (!loggedFirstRender) {
                    System.out.println("[IceyMod] EntityHealthRenderer: drew health above "
                            + (isPlayer ? "player " : "mob ") + le.getType().getUntranslatedName());
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

    /** Vanilla two-pass nameplate draw — see class doc. */
    private static void drawNameplate(MatrixStack ms, VertexConsumerProvider vcp,
                                      Camera cam, TextRenderer tr,
                                      LivingEntity le, Text text) {
        Vec3d pos = le.getPos();
        Vec3d camPos = cam.getPos();
        // Sit above the vanilla username nameplate. Vanilla nameplate
        // renders at getHeight() + 0.5; +1.0 gives clear separation.
        double yOffset = le.getHeight() + 1.0;
        ms.push();
        ms.translate(pos.x - camPos.x, pos.y - camPos.y + yOffset, pos.z - camPos.z);
        ms.multiply(cam.getRotation());
        ms.scale(-SCALE, -SCALE, SCALE);
        Matrix4f matrix = ms.peek().getPositionMatrix();

        int halfWidth = tr.getWidth(text) / 2;
        // Background tint — alpha is from client text-background opacity
        // setting (default 25%). 0x40 = 64/255 ≈ 25%.
        int bgColor = 0x40000000;

        // Pass 1: SEE_THROUGH (visible through walls, faint).
        // 0x21FFFFFF = ~13% alpha white — matches vanilla nameplate.
        tr.draw(text, (float) (-halfWidth), 0f, 0x21FFFFFF, false, matrix, vcp,
                TextRenderer.TextLayerType.SEE_THROUGH, bgColor, 0xF000F0);
        // Pass 2: NORMAL (drawn over the SEE_THROUGH layer in world view
        // — unoccluded text reads at full white).
        tr.draw(text, (float) (-halfWidth), 0f, 0xFFFFFFFF, false, matrix, vcp,
                TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);

        ms.pop();
    }

    private static boolean loggedFirstRender = false;
    private static boolean loggedFirstError = false;
}
