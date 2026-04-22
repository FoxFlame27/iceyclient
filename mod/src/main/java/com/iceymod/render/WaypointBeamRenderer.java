package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.modules.WaypointsModule;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Renders each waypoint as a beacon-style vertical beam and a billboarded
 * "name • 42m" tag that floats above it so you can spot both the location
 * and the distance from anywhere.
 */
public class WaypointBeamRenderer {

    public static void register() {
        try {
            WorldRenderEvents.AFTER_TRANSLUCENT.register(WaypointBeamRenderer::onRender);
        } catch (Throwable t) {
            System.out.println("[IceyMod] WorldRenderEvents unavailable (VulkanMod / stub?) — waypoint beams disabled: " + t.getMessage());
        }
    }

    private static boolean beamsEnabled() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof WaypointsModule) return m.isEnabled();
        }
        return false;
    }

    private static void onRender(WorldRenderContext ctx) {
        if (!beamsEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        List<WaypointManager.Waypoint> wps = WaypointManager.getWaypoints();
        if (wps.isEmpty()) return;

        Camera cam = ctx.camera();
        Vec3d camPos = cam.getPos();
        MatrixStack ms = ctx.matrixStack();
        long worldTime = client.world.getTime();
        float tickDelta = ctx.tickCounter().getTickProgress(false);

        Vec3d playerPos = client.player.getPos();
        VertexConsumerProvider vcp = ctx.consumers();
        TextRenderer textRenderer = client.textRenderer;

        for (WaypointManager.Waypoint wp : wps) {
            // --- Beacon beam ---
            ms.push();
            ms.translate(wp.x + 0.5 - camPos.x, -64 - camPos.y, wp.z + 0.5 - camPos.z);
            try {
                BeaconBlockEntityRenderer.renderBeam(
                        ms,
                        vcp,
                        BeaconBlockEntityRenderer.BEAM_TEXTURE,
                        tickDelta,
                        1.0f,
                        worldTime,
                        0,
                        BeaconBlockEntityRenderer.MAX_BEAM_HEIGHT,
                        wp.color,
                        0.2f,
                        0.25f
                );
            } catch (Throwable ignored) {
                // Renderer API changed? Don't crash the frame.
            }
            ms.pop();

            // --- Floating name + distance tag ---
            try {
                double dx = (wp.x + 0.5) - playerPos.x;
                double dy = wp.y - playerPos.y;
                double dz = (wp.z + 0.5) - playerPos.z;
                int distance = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
                String label = wp.name + " §7• §f" + distance + "m";

                // Anchor the tag high in the air above the beam so terrain / blocks
                // can't eat it. Matches the beam's x/z and a Y well above most builds.
                double tagX = wp.x + 0.5;
                double tagY = Math.max(playerPos.y + 20.0, wp.y + 3.0);
                double tagZ = wp.z + 0.5;

                ms.push();
                ms.translate(tagX - camPos.x, tagY - camPos.y, tagZ - camPos.z);
                // Billboard: rotate to face the camera
                ms.multiply(cam.getRotation());
                // Text is huge by default; shrink and flip Y so it reads right-side up
                float scale = 0.03f;
                ms.scale(-scale, -scale, scale);

                org.joml.Matrix4f mtx = ms.peek().getPositionMatrix();
                int w = textRenderer.getWidth(label);
                int bgColor = 0x66000000;     // translucent black
                int textColor = 0xFFFFFFFF;   // white (the name keeps its own color via §)

                // Prefix the waypoint's own color onto the name so it matches the beam
                String colored = toChatColor(wp.color) + wp.name + " §7• §f" + distance + "m";
                int cw = textRenderer.getWidth(colored);

                textRenderer.draw(
                        Text.literal(colored),
                        -cw / 2f,
                        0f,
                        textColor,
                        false,
                        mtx,
                        vcp,
                        TextRenderer.TextLayerType.SEE_THROUGH,
                        bgColor,
                        0x00F000F0 // full-bright light value
                );
                ms.pop();
            } catch (Throwable ignored) {
                // Any text-render API wobble shouldn't crash the frame.
            }
        }
    }

    /**
     * Map an ARGB color roughly to the nearest MC chat-color code so the
     * waypoint name in the floating tag matches the beam color visually.
     */
    private static String toChatColor(int argb) {
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        if (r < 40 && g < 40 && b < 40) return "§0";            // black
        if (r > 220 && g > 220 && b > 220) return "§f";          // white
        if (Math.abs(r - g) < 30 && Math.abs(g - b) < 30) return "§7"; // gray
        if (r > 200 && g > 160 && b < 120) return "§6";          // orange
        if (r > 200 && g > 200 && b < 140) return "§e";          // yellow
        if (r > 200 && g < 150 && b < 150) return "§c";          // red
        if (r < 150 && g > 180 && b < 150) return "§a";          // green
        if (r < 150 && g > 150 && b > 200) return "§b";          // aqua
        if (r > 150 && g < 180 && b > 180) return "§d";          // pink
        return "§f";
    }
}
