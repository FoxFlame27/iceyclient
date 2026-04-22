package com.iceymod.render;

import com.iceymod.hud.HudManager;
import com.iceymod.hud.HudModule;
import com.iceymod.hud.modules.MinimapModule;
import com.iceymod.hud.modules.WaypointManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.joml.Matrix3x2fStack;

/**
 * Xaero-style minimap renderer. Keeps a NativeImage backing texture,
 * rescans a radius of blocks around the player on a throttle, and draws
 * it as a HUD widget with biome-tinted colors, height shading, player
 * arrow, and waypoint dots.
 *
 * Sampling strategy: for each pixel in the texture, reverse-project to a
 * world (x,z) column, look up the WORLD_SURFACE top block, then pick a
 * color using biome-aware tinting for grass/leaves/water (so forests
 * look forest-green, swamps look muddy, oceans match the biome shade).
 */
public class MinimapRenderer {
    private static final int TEX_SIZE = 128;
    private static final Identifier TEXTURE_ID = Identifier.of("iceymod", "minimap");

    private static NativeImage image;
    private static NativeImageBackedTexture texture;
    private static boolean initAttempted = false;

    private static long lastUpdateTick = Long.MIN_VALUE;
    private static int lastCenterX = Integer.MIN_VALUE;
    private static int lastCenterZ = Integer.MIN_VALUE;
    private static int lastRadius = -1;
    private static String lastWorldKey = "";

    public static void register() {
        try {
            HudRenderCallback.EVENT.register(MinimapRenderer::onHudRender);
        } catch (Throwable t) {
            System.out.println("[IceyMod] HudRenderCallback unavailable — minimap disabled: " + t.getMessage());
        }
    }

    private static MinimapModule getModule() {
        for (HudModule m : HudManager.getModules()) {
            if (m instanceof MinimapModule mm) return mm;
        }
        return null;
    }

    private static void ensureTexture() {
        if (initAttempted) return;
        initAttempted = true;
        try {
            image = new NativeImage(TEX_SIZE, TEX_SIZE, false);
            for (int i = 0; i < TEX_SIZE; i++) {
                for (int j = 0; j < TEX_SIZE; j++) {
                    image.setColorArgb(i, j, 0xFF101018);
                }
            }
            texture = new NativeImageBackedTexture(() -> "iceymod-minimap", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE_ID, texture);
        } catch (Throwable t) {
            System.out.println("[IceyMod] Minimap texture init failed: " + t.getMessage());
            image = null;
            texture = null;
        }
    }

    private static void onHudRender(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter) {
        MinimapModule mod = getModule();
        if (mod == null || !mod.isEnabled()) return;
        if (!HudManager.isHudVisible()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        if (client.currentScreen != null && !(client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;

        try {
            ensureTexture();
            if (image == null || texture == null) return;
            updatePixels(client, mod);
            drawWidget(ctx, client, mod);
        } catch (Throwable ignored) {
            // Never crash the HUD
        }
    }

    private static void updatePixels(MinecraftClient client, MinimapModule mod) {
        ClientWorld world = client.world;
        int px = (int) Math.floor(client.player.getX());
        int pz = (int) Math.floor(client.player.getZ());
        int radius = mod.radius.get();
        long now = world.getTime();
        String worldKey = world.getRegistryKey().getValue().toString();

        boolean worldChanged = !worldKey.equals(lastWorldKey);
        boolean radiusChanged = radius != lastRadius;
        boolean moved = Math.abs(px - lastCenterX) >= 1 || Math.abs(pz - lastCenterZ) >= 1;
        boolean timedOut = now - lastUpdateTick >= 20;

        if (!worldChanged && !radiusChanged && !moved && !timedOut) return;

        lastCenterX = px;
        lastCenterZ = pz;
        lastRadius = radius;
        lastWorldKey = worldKey;
        lastUpdateTick = now;

        boolean biomeTint = mod.biomeTint.get();
        boolean heightShade = mod.heightShade.get();

        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable neighbor = new BlockPos.Mutable();

        // blocksPerPixel < 1 means each pixel covers less than a block (zoom in)
        float blocksPerPixel = (radius * 2f) / TEX_SIZE;

        for (int pyPix = 0; pyPix < TEX_SIZE; pyPix++) {
            for (int pxPix = 0; pxPix < TEX_SIZE; pxPix++) {
                int bx = px + (int) Math.floor((pxPix - TEX_SIZE / 2f) * blocksPerPixel);
                int bz = pz + (int) Math.floor((pyPix - TEX_SIZE / 2f) * blocksPerPixel);
                int color = sampleColumn(world, bx, bz, pos, neighbor, biomeTint, heightShade);
                image.setColorArgb(pxPix, pyPix, color);
            }
        }
        try {
            texture.upload();
        } catch (Throwable ignored) {}
    }

    private static int sampleColumn(ClientWorld world, int x, int z,
                                    BlockPos.Mutable pos, BlockPos.Mutable neighbor,
                                    boolean biomeTint, boolean heightShade) {
        try {
            int topY;
            try {
                topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            } catch (Throwable t) {
                return 0xFF101018;
            }
            if (topY <= world.getBottomY() + 1) return 0xFF101018;

            pos.set(x, topY - 1, z);
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                pos.set(x, topY - 2, z);
                state = world.getBlockState(pos);
            }

            int rgb;
            if (biomeTint && (state.isOf(Blocks.GRASS_BLOCK)
                    || state.isOf(Blocks.SHORT_GRASS)
                    || state.isOf(Blocks.TALL_GRASS)
                    || state.isOf(Blocks.FERN)
                    || state.isOf(Blocks.LARGE_FERN)
                    || state.isOf(Blocks.SUGAR_CANE))) {
                rgb = BiomeColors.getGrassColor(world, pos) | 0xFF000000;
            } else if (biomeTint && state.isIn(BlockTags.LEAVES)) {
                rgb = BiomeColors.getFoliageColor(world, pos) | 0xFF000000;
            } else if (biomeTint && state.isOf(Blocks.WATER)) {
                rgb = BiomeColors.getWaterColor(world, pos) | 0xFF000000;
            } else {
                FluidState fluid = state.getFluidState();
                if (biomeTint && !fluid.isEmpty() && fluid.isIn(FluidTags.WATER)) {
                    rgb = BiomeColors.getWaterColor(world, pos) | 0xFF000000;
                } else {
                    MapColor mc = state.getMapColor(world, pos);
                    int mcRgb = mc.color;
                    if (mcRgb == 0) mcRgb = 0x3F76E4; // sane default for air-ish columns
                    rgb = mcRgb | 0xFF000000;
                }
            }

            if (heightShade) {
                int westY;
                int northY;
                try {
                    westY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x - 1, z);
                    northY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z - 1);
                } catch (Throwable t) {
                    return rgb;
                }
                int shade = 0;
                if (topY > westY) shade += 14;
                else if (topY < westY) shade -= 14;
                if (topY > northY) shade += 8;
                else if (topY < northY) shade -= 8;
                rgb = applyShade(rgb, shade);
            }

            return rgb;
        } catch (Throwable t) {
            return 0xFF101018;
        }
    }

    private static int applyShade(int argb, int shade) {
        int a = (argb >>> 24) & 0xFF;
        int r = clamp(((argb >>> 16) & 0xFF) + shade);
        int g = clamp(((argb >>> 8) & 0xFF) + shade);
        int b = clamp((argb & 0xFF) + shade);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static void drawWidget(DrawContext ctx, MinecraftClient client, MinimapModule mod) {
        int size = mod.size.get();
        int x = mod.getX();
        int y = mod.getY();

        // Clamp into screen so freshly-added modules aren't off-screen
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        if (x + size > sw) x = sw - size - 4;
        if (y + size > sh) y = sh - size - 4;
        if (x < 0) x = 4;
        if (y < 0) y = 4;

        // Outer frame
        ctx.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xFF000000);
        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF5BC8F5);
        ctx.fill(x, y, x + size, y + size, 0xFF0A0A12);

        // The texture
        try {
            ctx.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    TEXTURE_ID,
                    x, y,
                    0f, 0f,
                    size, size,
                    TEX_SIZE, TEX_SIZE
            );
        } catch (Throwable ignored) {}

        int cx = x + size / 2;
        int cy = y + size / 2;
        float pixelsPerBlock = (float) size / (mod.radius.get() * 2f);

        // Waypoint dots
        if (mod.waypoints.get()) {
            for (WaypointManager.Waypoint wp : WaypointManager.getWaypoints()) {
                float dx = ((float) wp.x - (float) client.player.getX()) * pixelsPerBlock;
                float dz = ((float) wp.z - (float) client.player.getZ()) * pixelsPerBlock;
                int wpx = Math.round(cx + dx);
                int wpz = Math.round(cy + dz);

                if (wpx >= x + 1 && wpx <= x + size - 2 && wpz >= y + 1 && wpz <= y + size - 2) {
                    ctx.fill(wpx - 2, wpz - 2, wpx + 3, wpz + 3, 0xFF000000);
                    ctx.fill(wpx - 1, wpz - 1, wpx + 2, wpz + 2, wp.color);
                } else {
                    // Edge-clamp indicator: project to nearest border
                    float clampedX = Math.max(x + 3, Math.min(x + size - 4, wpx));
                    float clampedY = Math.max(y + 3, Math.min(y + size - 4, wpz));
                    int bx = Math.round(clampedX);
                    int bz = Math.round(clampedY);
                    ctx.fill(bx - 1, bz - 1, bx + 2, bz + 2, wp.color);
                }
            }
        }

        // Player arrow (center, rotated with yaw)
        drawPlayerArrow(ctx, cx, cy, client.player.getYaw());

        // North "N" tag
        if (mod.northTag.get()) {
            ctx.fill(cx - 4, y + 1, cx + 5, y + 10, 0xB0000000);
            ctx.drawCenteredTextWithShadow(client.textRenderer, "§fN", cx + 1, y + 2, 0xFFFFFFFF);
        }

        // Coords underneath
        if (mod.coords.get()) {
            int ix = (int) Math.floor(client.player.getX());
            int iy = (int) Math.floor(client.player.getY());
            int iz = (int) Math.floor(client.player.getZ());
            String txt = "§f" + ix + " §7/ §f" + iy + " §7/ §f" + iz;
            int tw = client.textRenderer.getWidth(txt);
            int textY = y + size + 2;
            ctx.fill(cx - tw / 2 - 2, textY - 1, cx + tw / 2 + 2, textY + 9, 0xB0000000);
            ctx.drawCenteredTextWithShadow(client.textRenderer, txt, cx, textY, 0xFFFFFFFF);
        }
    }

    private static void drawPlayerArrow(DrawContext ctx, int cx, int cy, float yaw) {
        // Map yaw onto the 2D minimap: +X east, +Z south (=> +Y screen).
        // Player facing vector: fx = -sin(yaw), fz = cos(yaw).
        Matrix3x2fStack ms = ctx.getMatrices();
        ms.pushMatrix();
        ms.translate((float) cx, (float) cy);
        ms.rotate((float) Math.toRadians(yaw + 180.0));
        // A triangle pointing "up" in local space. After the rotation
        // above, local -Y is forward in player space.
        ctx.fill(-1, -4, 1, 1, 0xFFFFFFFF);           // shaft
        ctx.fill(-2, -3, 2, -2, 0xFFFFFFFF);          // tip row
        ctx.fill(-3, -2, 3, -1, 0xFFFFFFFF);          // wing row
        ctx.fill(-1, 1, 1, 3, 0xFFFF3A3A);            // red tail so you can tell front from back
        // Outline
        ctx.fill(-1, -5, 1, -4, 0xFF000000);
        ctx.fill(-4, -2, -3, -1, 0xFF000000);
        ctx.fill(3, -2, 4, -1, 0xFF000000);
        ms.popMatrix();
    }
}
