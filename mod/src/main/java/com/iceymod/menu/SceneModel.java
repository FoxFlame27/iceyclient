package com.iceymod.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iceymod.IceyMod;
import com.iceymod.compat.Gfx;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * A piece of a real Minecraft world as a 3D model the menu can draw: every
 * block face that can be seen from the side the viewer stands on, with its
 * place, its part of a block texture, its colour and the light on it.
 *
 * It is drawn without perspective, the way a diorama is looked at. Seen
 * that way every face is a parallelogram, which is what a 2D transform
 * makes of a rectangle, so each face is one ordinary GUI blit put in place
 * by a matrix, far ones first. That works the same on every game version
 * and needs no world to be loaded, and because it is real geometry the
 * whole place turns when the view does.
 *
 * The block textures are not shipped: they are read from the game (and so
 * from the player's resource pack) when the model is loaded, and packed
 * into one texture so the whole model goes to the graphics card in a few
 * calls.
 */
public final class SceneModel {

    private static final int TILE = 16;
    private static final float[] AO = { 1.00f, 0.80f, 0.63f, 0.48f };      // how much light a boxed-in corner keeps
    private static final float[][] NORMAL = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };

    public final Scenes.Scene scene;
    private int n;
    private float[] px, py, pz, ux, uy, uz, vx, vy, vz, cx, cy, cz;
    private int[] tu, tv, tw, th, face, flags;
    private int atlasW, atlasH;
    private int[] order;
    private float[] depth;
    private float cX, cY, cZ;
    private Identifier atlas;
    private boolean ready;
    public float yaw, pitch;

    private SceneModel(Scenes.Scene scene) { this.scene = scene; }

    private static final java.util.Map<String, SceneModel> LOADED = new java.util.HashMap<>();
    /** The model of a scene, read once per game session. Null when it cannot be read. */
    public static SceneModel of(Scenes.Scene scene) {
        if (scene == null) return null;
        if (LOADED.containsKey(scene.id)) return LOADED.get(scene.id);
        SceneModel m = load(scene);
        LOADED.put(scene.id, m);
        return m;
    }

    public boolean ready() { return ready; }
    public int faces() { return n; }

    /** Reads the model of a scene; null when it cannot be read. */
    public static SceneModel load(Scenes.Scene scene) {
        SceneModel m = new SceneModel(scene);
        try {
            Minecraft mc = Minecraft.getInstance();
            var res = mc.getResourceManager().getResource(Identifier.fromNamespaceAndPath(IceyMod.MOD_ID, "scenes/" + scene.id + ".json"));
            if (res.isEmpty()) return null;
            JsonObject o;
            try (InputStreamReader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) { o = JsonParser.parseReader(r).getAsJsonObject(); }
            m.yaw = o.get("yaw").getAsFloat();
            m.pitch = o.get("pitch").getAsFloat();
            JsonArray c = o.getAsJsonArray("centre");
            m.cX = c.get(0).getAsFloat(); m.cY = c.get(1).getAsFloat(); m.cZ = c.get(2).getAsFloat();
            JsonArray sunDir = o.has("sun") ? o.getAsJsonArray("sun") : null;
            float lx = sunDir != null ? sunDir.get(0).getAsFloat() : -0.4f, ly = sunDir != null ? sunDir.get(1).getAsFloat() : 0.67f, lz = sunDir != null ? sunDir.get(2).getAsFloat() : 0.6f;
            JsonArray tex = o.getAsJsonArray("textures");
            int[][] tiles = new int[tex.size()][];
            for (int i = 0; i < tiles.length; i++) tiles[i] = tile(tex.get(i).getAsString());
            JsonArray q = o.getAsJsonArray("quads");
            int n = q.size();
            m.n = n;
            m.px = new float[n]; m.py = new float[n]; m.pz = new float[n];
            m.ux = new float[n]; m.uy = new float[n]; m.uz = new float[n];
            m.vx = new float[n]; m.vy = new float[n]; m.vz = new float[n];
            m.cx = new float[n]; m.cy = new float[n]; m.cz = new float[n];
            m.tu = new int[n]; m.tv = new int[n]; m.tw = new int[n]; m.th = new int[n];
            m.face = new int[n]; m.flags = new int[n];
            m.order = new int[n]; m.depth = new float[n];
            // Every face gets its own square of the picture, with its light painted in.
            int cols = n <= 4096 ? 64 : 128;
            m.atlasW = cols * TILE;
            m.atlasH = Math.max(TILE, Integer.highestOneBit(Math.max(1, ((n + cols - 1) / cols) * TILE - 1)) << 1);
            NativeImage img = new NativeImage(m.atlasW, m.atlasH, true);
            // The direction of the view when the model is not turned (for the haze).
            double vy0 = Math.toRadians(m.yaw), vp0 = Math.toRadians(m.pitch);
            float f0x = (float) (-Math.sin(vy0) * Math.cos(vp0)), f0y = (float) -Math.sin(vp0), f0z = (float) (Math.cos(vy0) * Math.cos(vp0));
            float[][] light = new float[4][3];
            int i = 0;
            for (JsonElement e : q) {
                JsonArray a = e.getAsJsonArray();
                int t = Math.max(0, Math.min(tiles.length - 1, a.get(0).getAsInt()));
                m.px[i] = a.get(1).getAsFloat(); m.py[i] = a.get(2).getAsFloat(); m.pz[i] = a.get(3).getAsFloat();
                m.ux[i] = a.get(4).getAsFloat(); m.uy[i] = a.get(5).getAsFloat(); m.uz[i] = a.get(6).getAsFloat();
                m.vx[i] = a.get(7).getAsFloat(); m.vy[i] = a.get(8).getAsFloat(); m.vz[i] = a.get(9).getAsFloat();
                int u = Math.max(0, Math.min(15, a.get(10).getAsInt())), v = Math.max(0, Math.min(15, a.get(11).getAsInt()));
                int w = Math.max(1, Math.min(16 - u, a.get(12).getAsInt())), h = Math.max(1, Math.min(16 - v, a.get(13).getAsInt()));
                int tint = a.get(14).getAsInt();
                int sky = a.get(15).getAsInt(), blk = a.get(16).getAsInt(), emit = a.get(17).getAsInt();
                m.face[i] = Math.max(0, Math.min(5, a.get(18).getAsInt()));
                m.flags[i] = a.get(19).getAsInt();
                int aoBits = a.size() > 20 ? a.get(20).getAsInt() : 0, sunBits = a.size() > 21 ? a.get(21).getAsInt() : 15;
                boolean thin = (m.flags[i] & 1) != 0, water = (m.flags[i] & 2) != 0, flat = (m.flags[i] & 4) != 0;
                float[] nrm = NORMAL[m.face[i]];
                float ndl = thin || flat ? 0.55f : Math.max(0f, nrm[0] * lx + nrm[1] * ly + nrm[2] * lz);
                for (int k = 0; k < 4; k++)
                    corner(scene, light[k], sky, blk, emit, ndl, ((sunBits >> k) & 1) != 0, AO[(aoBits >> (k * 2)) & 3]);
                m.cx[i] = m.px[i] + (m.ux[i] + m.vx[i]) * 0.5f; m.cy[i] = m.py[i] + (m.uy[i] + m.vy[i]) * 0.5f; m.cz[i] = m.pz[i] + (m.uz[i] + m.vz[i]) * 0.5f;
                // Haze: further away, closer to the colour of the air.
                float d = (m.cx[i] - m.cX) * f0x + (m.cy[i] - m.cY) * f0y + (m.cz[i] - m.cZ) * f0z;
                float fogT = (d - scene.fogStart) / (scene.fogEnd - scene.fogStart);
                fogT = fogT < 0 ? 0 : fogT > 1 ? 1 : fogT;
                fogT = fogT * fogT * (3 - 2 * fogT) * scene.fogMax;
                int fr = (scene.fog >> 16) & 0xFF, fg = (scene.fog >> 8) & 0xFF, fb = scene.fog & 0xFF;
                int tr = (tint >> 16) & 0xFF, tg = (tint >> 8) & 0xFF, tb = tint & 0xFF;
                int ox = (i % cols) * TILE, oy = (i / cols) * TILE;
                int[] src = tiles[t];
                for (int y = 0; y < h; y++) {
                    float fy = (y + 0.5f) / h;
                    for (int x = 0; x < w; x++) {
                        float fx = (x + 0.5f) / w;
                        int p = src[(v + y) * TILE + u + x];
                        int al = p >>> 24;
                        if (al == 0) { img.setPixel(ox + x, oy + y, 0); continue; }
                        float w00 = (1 - fx) * (1 - fy), w10 = fx * (1 - fy), w01 = (1 - fx) * fy, w11 = fx * fy;
                        float lr = light[0][0] * w00 + light[1][0] * w10 + light[2][0] * w01 + light[3][0] * w11;
                        float lg = light[0][1] * w00 + light[1][1] * w10 + light[2][1] * w01 + light[3][1] * w11;
                        float lb = light[0][2] * w00 + light[1][2] * w10 + light[2][2] * w01 + light[3][2] * w11;
                        float r = ((p >> 16) & 0xFF) * tr / 255f * lr, g = ((p >> 8) & 0xFF) * tg / 255f * lg, b = (p & 0xFF) * tb / 255f * lb;
                        r += (fr - r) * fogT; g += (fg - g) * fogT; b += (fb - b) * fogT;
                        int R = r > 255 ? 255 : Math.round(r), G = g > 255 ? 255 : Math.round(g), B = b > 255 ? 255 : Math.round(b);
                        if (water) al = Math.round(al * 0.82f);
                        img.setPixel(ox + x, oy + y, (al << 24) | (R << 16) | (G << 8) | B);
                    }
                }
                m.tu[i] = ox; m.tv[i] = oy; m.tw[i] = w; m.th[i] = h;
                m.order[i] = i;
                i++;
            }
            m.atlas = Identifier.fromNamespaceAndPath(IceyMod.MOD_ID, "menu/atlas/" + scene.id);
            mc.getTextureManager().register(m.atlas, new DynamicTexture(() -> "icey scene " + scene.id, img));
            m.ready = n > 0;
            return m.ready ? m : null;
        } catch (Throwable t) {
            System.out.println("[IceyMod] scene model " + scene.id + " not read: " + t);
            return null;
        }
    }

    /**
     * The light at one corner of a face, the way a shader pack lights the
     * game: warm sun where it reaches and the face looks towards it, the
     * cool light of the sky in the shade, the glow of lamps, and less of
     * everything in a corner that is boxed in.
     */
    private static void corner(Scenes.Scene s, float[] out, int sky, int blk, int emit, float ndl, boolean sunlit, float ao) {
        float open = sky / 15f;
        float direct = sunlit ? s.day * ndl * (0.3f + 0.7f * open) : 0f;
        float amb = s.ambient * (0.5f + 0.5f * open);
        float lamp = (float) Math.pow(Math.max(blk, emit) / 15f, 1.7) * s.glow;
        float sr = ((s.sun >> 16) & 0xFF) / 255f, sg = ((s.sun >> 8) & 0xFF) / 255f, sb = (s.sun & 0xFF) / 255f;
        float hr = ((s.shade >> 16) & 0xFF) / 255f, hg = ((s.shade >> 8) & 0xFF) / 255f, hb = (s.shade & 0xFF) / 255f;
        float r = (hr * amb + sr * direct + 1.00f * lamp) * ao;
        float g = (hg * amb + sg * direct + 0.78f * lamp) * ao;
        float b = (hb * amb + sb * direct + 0.48f * lamp) * ao;
        if (emit >= 10) { r = Math.max(r, 1.05f); g = Math.max(g, 0.95f); b = Math.max(b, 0.78f); }
        out[0] = Math.min(1.3f, r); out[1] = Math.min(1.3f, g); out[2] = Math.min(1.3f, b);
    }

    /** A block texture from the game (so from the player's resource pack), as 16x16 ARGB. */
    private static int[] tile(String name) {
        int[] out = new int[TILE * TILE];
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(Identifier.withDefaultNamespace("textures/" + name + ".png"));
            if (res.isPresent()) {
                try (InputStream in = res.get().open(); NativeImage src = NativeImage.read(in)) {
                    // Animated textures are a column of frames (the first is used); packs may be finer than 16 pixels.
                    int size = Math.min(src.getWidth(), src.getHeight());
                    for (int y = 0; y < TILE; y++) for (int x = 0; x < TILE; x++) out[y * TILE + x] = src.getPixel(x * size / TILE, y * size / TILE);
                    return out;
                }
            }
        } catch (Throwable ignored) {}
        java.util.Arrays.fill(out, 0xFF8A8F98);
        return out;
    }

    // The view of this frame.
    private float rx, ry, rz, uxv, uyv, uzv, fx, fy, fz, scale, sx0, sy0;

    /**
     * Sets how the model is looked at: turned by {@code dYaw}/{@code dPitch} degrees from its
     * own angle, {@code blockPx} canvas pixels to a block, its centre at (centreX, centreY).
     */
    public void view(float dYaw, float dPitch, float blockPx, float centreX, float centreY) {
        double y = Math.toRadians(yaw + dYaw), p = Math.toRadians(pitch + dPitch);
        fx = (float) (-Math.sin(y) * Math.cos(p)); fy = (float) -Math.sin(p); fz = (float) (Math.cos(y) * Math.cos(p));
        rx = (float) -Math.cos(y); ry = 0; rz = (float) -Math.sin(y);
        // up = right x forward
        uxv = ry * fz - rz * fy; uyv = rz * fx - rx * fz; uzv = rx * fy - ry * fx;
        scale = blockPx; sx0 = centreX; sy0 = centreY;
    }

    public float screenX(float x, float y, float z) { return sx0 + ((x - cX) * rx + (y - cY) * ry + (z - cZ) * rz) * scale; }
    public float screenY(float x, float y, float z) { return sy0 - ((x - cX) * uxv + (y - cY) * uyv + (z - cZ) * uzv) * scale; }
    public float depthOf(float x, float y, float z) { return (x - cX) * fx + (y - cY) * fy + (z - cZ) * fz; }
    public float blockPx() { return scale; }
    /** How a figure facing the given way (0 = towards the viewer when the model is not turned) stands to the viewer now, in radians. */
    public float facing(float worldFacing, float dYaw) { return worldFacing + (float) Math.toRadians(dYaw); }

    /**
     * Draws the model: far faces first, each fading into the haze with distance.
     * {@code viewW}/{@code viewH} is the canvas (faces outside are skipped).
     */
    public void draw(Gfx g, int viewW, int viewH, float alpha) {
        if (!ready) return;
        // Far to near. The view changes little between frames, so last frame's order is nearly right.
        for (int i = 0; i < n; i++) depth[i] = (cx[i] - cX) * fx + (cy[i] - cY) * fy + (cz[i] - cZ) * fz;
        for (int i = 1; i < n; i++) {
            int o = order[i];
            float d = depth[o];
            int j = i - 1;
            while (j >= 0 && depth[order[j]] < d) { order[j + 1] = order[j]; j--; }
            order[j + 1] = o;
        }
        int tintAll = (Math.round(255 * Math.max(0f, Math.min(1f, alpha))) << 24) | 0xFFFFFF;
        int drawn = 0;
        Matrix3x2f mat = new Matrix3x2f();
        float grow = 0.012f;                                   // a hair of overlap hides the seams between faces
        for (int k = 0; k < n; k++) {
            int i = order[k];
            float[] nrm = NORMAL[face[i]];
            if ((flags[i] & 1) == 0 && nrm[0] * fx + nrm[1] * fy + nrm[2] * fz > -0.015f) continue;      // faces away
            float ex = ux[i] * rx + uy[i] * ry + uz[i] * rz, ey = -(ux[i] * uxv + uy[i] * uyv + uz[i] * uzv);
            float gx = vx[i] * rx + vy[i] * ry + vz[i] * rz, gy = -(vx[i] * uxv + vy[i] * uyv + vz[i] * uzv);
            float x0 = screenX(px[i], py[i], pz[i]), y0 = screenY(px[i], py[i], pz[i]);
            float ax = ex * scale, ay = ey * scale, bx = gx * scale, by = gy * scale;
            float minX = x0 + Math.min(0, ax) + Math.min(0, bx), maxX = x0 + Math.max(0, ax) + Math.max(0, bx);
            float minY = y0 + Math.min(0, ay) + Math.min(0, by), maxY = y0 + Math.max(0, ay) + Math.max(0, by);
            if (maxX < 0 || minX > viewW || maxY < 0 || minY > viewH) continue;
            int w = tw[i], h = th[i];
            float lenU = (float) Math.sqrt(ux[i] * ux[i] + uy[i] * uy[i] + uz[i] * uz[i]), lenV = (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
            float gu = lenU > 0.2f ? grow / lenU : 0f, gv = lenV > 0.2f ? grow / lenV : 0f;
            g.pose().pushMatrix();
            try {
                mat.set(ax * (1 + 2 * gu) / w, ay * (1 + 2 * gu) / w, bx * (1 + 2 * gv) / h, by * (1 + 2 * gv) / h, x0 - ax * gu - bx * gv, y0 - ay * gu - by * gv);
                g.pose().mul(mat);
                g.blit(atlas, 0, 0, (float) tu[i], (float) tv[i], w, h, atlasW, atlasH, tintAll);
            } finally {
                g.pose().popMatrix();
            }
            // A fresh layer every few dozen faces keeps the game's own overlap bookkeeping short.
            if (++drawn % 48 == 0) g.nextStratum();
        }
        g.nextStratum();
    }
}
