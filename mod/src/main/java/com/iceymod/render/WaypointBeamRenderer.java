package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.WaypointManager;
import com.iceymod.hud.modules.WaypointsModule;
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
        if (!WorldRenderHook.registerAfterTranslucent(WaypointBeamRenderer::onRender)) {
            System.out.println("[IceyMod] WorldRenderEvents unavailable — waypoint beams disabled");
        }
    }

    private static boolean beamsEnabled() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof WaypointsModule) return m.isEnabled();
        }
        return false;
    }

    private static void onRender(WorldRenderHook.Ctx ctx) {
        if (!beamsEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        List<WaypointManager.Waypoint> wps = WaypointManager.getWaypoints();
        if (wps.isEmpty()) return;

        Camera cam = ctx.camera();
        Vec3d camPos = com.iceymod.Compat.cameraPos(cam);
        MatrixStack ms = ctx.matrixStack();
        long worldTime = client.world.getTime();
        float tickDelta = ctx.tickDelta();

        Vec3d playerPos = com.iceymod.Compat.entityPos(client.player);
        VertexConsumerProvider vcp = ctx.consumers();
        TextRenderer textRenderer = client.textRenderer;

        for (WaypointManager.Waypoint wp : wps) {
            // --- Beacon beam ---
            ms.push();
            ms.translate(wp.x + 0.5 - camPos.x, -64 - camPos.y, wp.z + 0.5 - camPos.z);
            try {
                // Draw the beam ourselves. BeaconBlockEntityRenderer.renderBeam
                // takes a VertexConsumerProvider on 1.21.8 but an
                // OrderedRenderCommandQueue on 1.21.9+, so calling it can't
                // work from a world-render hook on both — the vertex API is
                // identical across versions, only the beam RenderLayer
                // factory moved (see beaconLayer()).
                drawBeam(ms, vcp, BeaconBlockEntityRenderer.BEAM_TEXTURE,
                        tickDelta, 1.0f, worldTime, 0,
                        BeaconBlockEntityRenderer.MAX_BEAM_HEIGHT, wp.color, 0.2f, 0.25f);
            } catch (Throwable t) {
                if (beamErrorLogged.compareAndSet(false, true)) System.out.println("[IceyMod] waypoint beam draw failed: " + t);
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

    private static final java.util.concurrent.atomic.AtomicBoolean beamErrorLogged = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static java.lang.reflect.Method beamLayerMethod;
    private static boolean beamLayerProbed;

    /**
     * The beacon-beam RenderLayer factory: {@code RenderLayer.getBeaconBeam}
     * on 1.21.8 (intermediary method_23592), {@code RenderLayers.beaconBeam}
     * on 1.21.9+ (class_12249#method_75988). RenderLayers doesn't exist on
     * 1.21.8, so it's loaded by name — both the yarn name (dev) and the
     * intermediary name (production jar) are tried. Matched by shape AND
     * name so the other (Identifier, boolean) factories can't be picked.
     */
    private static net.minecraft.client.render.RenderLayer beaconLayer(net.minecraft.util.Identifier tex, boolean translucent) throws Exception {
        if (!beamLayerProbed) {
            beamLayerProbed = true;
            java.util.List<Class<?>> holders = new java.util.ArrayList<>();
            holders.add(net.minecraft.client.render.RenderLayer.class);
            for (String n : new String[] { "net.minecraft.client.render.RenderLayers", "net.minecraft.class_12249" }) {
                try { holders.add(Class.forName(n)); } catch (Throwable ignored) {}
            }
            java.util.Set<String> names = java.util.Set.of("getBeaconBeam", "beaconBeam", "method_23592", "method_75988");
            outer:
            for (Class<?> h : holders) {
                for (java.lang.reflect.Method m : h.getMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    if (!names.contains(m.getName())) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length != 2 || p[0] != net.minecraft.util.Identifier.class || p[1] != boolean.class) continue;
                    if (m.getReturnType() != net.minecraft.client.render.RenderLayer.class) continue;
                    beamLayerMethod = m;
                    break outer;
                }
            }
            if (beamLayerMethod == null) System.out.println("[IceyMod] beacon beam RenderLayer factory not found — waypoint beams disabled");
        }
        if (beamLayerMethod == null) return null;
        return (net.minecraft.client.render.RenderLayer) beamLayerMethod.invoke(null, tex, translucent);
    }

    /** Port of vanilla BeaconBlockEntityRenderer.renderBeam (minus its own 0.5 translate — the caller centres on the block). */
    private static void drawBeam(MatrixStack ms, VertexConsumerProvider vcp, net.minecraft.util.Identifier tex,
                                 float tickDelta, float heightScale, long worldTime,
                                 int yOffset, int maxY, int color, float innerRadius, float outerRadius) throws Exception {
        net.minecraft.client.render.RenderLayer inner = beaconLayer(tex, false);
        net.minecraft.client.render.RenderLayer outerLayer = beaconLayer(tex, true);
        if (inner == null || outerLayer == null) return;
        int endY = yOffset + maxY;
        float time = (float) Math.floorMod(worldTime, 40L) + tickDelta;
        float rot = maxY < 0 ? time : -time;
        float frac = net.minecraft.util.math.MathHelper.fractionalPart(rot * 0.2f - (float) net.minecraft.util.math.MathHelper.floor(rot * 0.1f));

        ms.push();
        ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(time * 2.25f - 45.0f));
        float v1 = -1.0f + frac;
        float v2 = (float) maxY * heightScale * (0.5f / innerRadius) + v1;
        beamLayer(ms, vcp.getBuffer(inner), color | 0xFF000000, yOffset, endY,
                0.0f, innerRadius, innerRadius, 0.0f, -innerRadius, 0.0f, 0.0f, -innerRadius, 0.0f, 1.0f, v2, v1);
        ms.pop();

        float v3 = -1.0f + frac;
        float v4 = (float) maxY * heightScale + v3;
        int translucent = (color & 0x00FFFFFF) | (32 << 24);
        beamLayer(ms, vcp.getBuffer(outerLayer), translucent, yOffset, endY,
                -outerRadius, -outerRadius, outerRadius, -outerRadius, -outerRadius, outerRadius, outerRadius, outerRadius, 0.0f, 1.0f, v4, v3);
    }

    private static void beamLayer(MatrixStack ms, net.minecraft.client.render.VertexConsumer vc, int color, int yOffset, int height,
                                  float x1, float z1, float x2, float z2, float x3, float z3, float x4, float z4,
                                  float u1, float u2, float v1, float v2) {
        MatrixStack.Entry entry = ms.peek();
        beamFace(entry, vc, color, yOffset, height, x1, z1, x2, z2, u1, u2, v1, v2);
        beamFace(entry, vc, color, yOffset, height, x4, z4, x3, z3, u1, u2, v1, v2);
        beamFace(entry, vc, color, yOffset, height, x2, z2, x4, z4, u1, u2, v1, v2);
        beamFace(entry, vc, color, yOffset, height, x3, z3, x1, z1, u1, u2, v1, v2);
    }

    private static void beamFace(MatrixStack.Entry entry, net.minecraft.client.render.VertexConsumer vc, int color, int yOffset, int height,
                                 float x1, float z1, float x2, float z2, float u1, float u2, float v1, float v2) {
        beamVertex(entry, vc, color, height, x1, z1, u2, v1);
        beamVertex(entry, vc, color, yOffset, x1, z1, u2, v2);
        beamVertex(entry, vc, color, yOffset, x2, z2, u1, v2);
        beamVertex(entry, vc, color, height, x2, z2, u1, v1);
    }

    private static void beamVertex(MatrixStack.Entry entry, net.minecraft.client.render.VertexConsumer vc, int color, int y, float x, float z, float u, float v) {
        vc.vertex(entry.getPositionMatrix(), x, (float) y, z)
          .color(color)
          .texture(u, v)
          .overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV)
          .light(15728880)
          .normal(0.0f, 1.0f, 0.0f);
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
