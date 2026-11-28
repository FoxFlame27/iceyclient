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
 * block face a camera behind the player can see, with its place, its part
 * of a block texture, its colour and the light on it.
 *
 * It is drawn with nothing but ordinary GUI blits, far faces first. A blit
 * put in place by a 2D matrix is a parallelogram; a face seen in
 * perspective is almost one when it is small or far away, and a face that
 * is near and large is cut into pieces until each piece is. That works the
 * same on every game version and needs no world to be loaded, and because
 * it is real geometry the whole place moves when the camera does.
 *
 * The block textures are not shipped: they are read from the game (and so
 * from the player's resource pack) when the model is loaded, and packed
 * into one texture with the light painted in: sun where it reaches, in
 * its real shape (sixteen samples a face), the sky's light in the shade,
 * the glow of lamps, dark corners, haze with distance.
 */
public final class SceneModel {

    private static final int TILE = 16;
    private static final float[] AO = { 1.00f, 0.80f, 0.63f, 0.48f };      // how much light a boxed-in corner keeps
    private static final float[][] NORMAL = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };

    public final Scenes.Scene scene;
    private int n;
    private float[] px, py, pz, ux, uy, uz, vx, vy, vz, cx, cy, cz;
    private int[] tu, tv, tw, th, flags;
    private float[] nx, ny, nz;
    private int atlasW, atlasH;
    private Identifier atlas;
    private boolean ready;
    /** The camera at rest: which way it looks, how far it is from the player's middle, its opening angle, how far it sees. */
    public float yaw, pitch, dist = 8f, fov = 50f, far = 46f;

    private SceneModel(Scenes.Scene scene) { this.scene = scene; }

    private static final java.util.Map<String, SceneModel> LOADED = new java.util.HashMap<>();
    /** The model of a scene, read once per game session. Null when it cannot be read. */
    public static SceneModel of(Scenes.Scene scene) {
        if (scene == null) return null;
        if (LOADED.containsKey(scene.id)) return LOADED.get(scene.id);
        SceneModel m = load(Scenes.today(scene));
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
            if (o.has("dist")) m.dist = o.get("dist").getAsFloat();
            if (o.has("fov")) m.fov = o.get("fov").getAsFloat();
            if (o.has("far")) m.far = o.get("far").getAsFloat();
            JsonArray sunDir = o.has("sun") ? o.getAsJsonArray("sun") : null;
            float lx = sunDir != null ? sunDir.get(0).getAsFloat() : -0.4f, ly = sunDir != null ? sunDir.get(1).getAsFloat() : 0.67f, lz = sunDir != null ? sunDir.get(2).getAsFloat() : 0.6f;
            JsonArray tex = o.getAsJsonArray("textures");
            Tex[] tiles = new Tex[tex.size()];
            for (int i = 0; i < tiles.length; i++) tiles[i] = tile(tex.get(i).getAsString());
            JsonArray q = o.getAsJsonArray("quads");
            int n = q.size();
            m.n = n;
            m.px = new float[n]; m.py = new float[n]; m.pz = new float[n];
            m.ux = new float[n]; m.uy = new float[n]; m.uz = new float[n];
            m.vx = new float[n]; m.vy = new float[n]; m.vz = new float[n];
            m.cx = new float[n]; m.cy = new float[n]; m.cz = new float[n];
            m.tu = new int[n]; m.tv = new int[n]; m.tw = new int[n]; m.th = new int[n];
            m.flags = new int[n];
            m.nx = new float[n]; m.ny = new float[n]; m.nz = new float[n];
            m.cam = new float[n * 13]; m.sub = new byte[n]; m.keys = new long[n];
            // Every face gets its own square of the picture, with its light painted in.
            int cols = n <= 4096 ? 64 : 128;
            m.atlasW = cols * TILE;
            m.atlasH = Math.max(TILE, Integer.highestOneBit(Math.max(1, ((n + cols - 1) / cols) * TILE - 1)) << 1);
            NativeImage img = new NativeImage(m.atlasW, m.atlasH, true);
            // Where the camera is at rest (for the haze).
            m.view(0, 0, 100, 0, 0);
            float c0x = m.camX, c0y = m.camY, c0z = m.camZ;
            float[][] base = new float[4][3], sunL = new float[4][3];
            float[] grid = new float[16];
            int i = 0;
            for (JsonElement e : q) {
                JsonArray a = e.getAsJsonArray();
                Tex src = tiles[Math.max(0, Math.min(tiles.length - 1, a.get(0).getAsInt()))];
                if (src == null) continue;                    // a texture this game version does not have: leave the face out
                m.px[i] = a.get(1).getAsFloat(); m.py[i] = a.get(2).getAsFloat(); m.pz[i] = a.get(3).getAsFloat();
                m.ux[i] = a.get(4).getAsFloat(); m.uy[i] = a.get(5).getAsFloat(); m.uz[i] = a.get(6).getAsFloat();
                m.vx[i] = a.get(7).getAsFloat(); m.vy[i] = a.get(8).getAsFloat(); m.vz[i] = a.get(9).getAsFloat();
                m.flags[i] = a.get(19).getAsInt();
                // Where the face's picture is in its texture: block textures count in sixteenths, mob textures in their own pixels.
                boolean whole = (m.flags[i] & 8) != 0;
                float unit = whole ? 1f : src.w / 16f;
                int u = Math.max(0, Math.min(whole ? src.w - 1 : 15, a.get(10).getAsInt())), v = Math.max(0, Math.min(whole ? src.h - 1 : 15, a.get(11).getAsInt()));
                int sw = Math.max(1, Math.min((whole ? src.w : 16) - u, a.get(12).getAsInt())), sh = Math.max(1, Math.min((whole ? src.h : 16) - v, a.get(13).getAsInt()));
                int w = Math.min(TILE, sw), h = Math.min(TILE, sh);
                int tint = a.get(14).getAsInt();
                int sky = a.get(15).getAsInt(), blk = a.get(16).getAsInt(), emit = a.get(17).getAsInt();
                int face = Math.max(0, Math.min(5, a.get(18).getAsInt()));
                int aoBits = a.size() > 20 ? a.get(20).getAsInt() : 0, sunBits = a.size() > 21 ? a.get(21).getAsInt() : 15;
                boolean thin = (m.flags[i] & 1) != 0, water = (m.flags[i] & 2) != 0, flat = (m.flags[i] & 4) != 0, fill = (m.flags[i] & 64) != 0;
                if ((m.flags[i] & 16) != 0 && a.size() > 24) { m.nx[i] = a.get(22).getAsFloat(); m.ny[i] = a.get(23).getAsFloat(); m.nz[i] = a.get(24).getAsFloat(); }
                else { m.nx[i] = NORMAL[face][0]; m.ny[i] = NORMAL[face][1]; m.nz[i] = NORMAL[face][2]; }
                float ndl = thin || flat ? 0.62f : Math.max(0f, m.nx[i] * lx + m.ny[i] * ly + m.nz[i] * lz);
                for (int k = 0; k < 4; k++) corner(scene, base[k], sunL[k], sky, blk, emit, ndl, AO[(aoBits >> (k * 2)) & 3]);
                // Where the sun reaches: sixteen spots across the face (older files: its four corners).
                if ((m.flags[i] & 32) != 0) for (int k = 0; k < 16; k++) grid[k] = ((sunBits >> k) & 1) != 0 ? 1f : 0f;
                else for (int k = 0; k < 16; k++) grid[k] = ((sunBits >> (((k & 3) >= 2 ? 1 : 0) | ((k >> 2) >= 2 ? 2 : 0))) & 1) != 0 ? 1f : 0f;
                m.cx[i] = m.px[i] + (m.ux[i] + m.vx[i]) * 0.5f; m.cy[i] = m.py[i] + (m.uy[i] + m.vy[i]) * 0.5f; m.cz[i] = m.pz[i] + (m.uz[i] + m.vz[i]) * 0.5f;
                // Haze: further from the camera, closer to the colour of the air.
                float ddx = m.cx[i] - c0x, ddy = m.cy[i] - c0y, ddz = m.cz[i] - c0z;
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                float fogT = (d - scene.fogStart) / (scene.fogEnd - scene.fogStart);
                fogT = fogT < 0 ? 0 : fogT > 1 ? 1 : fogT;
                fogT = fogT * fogT * (3 - 2 * fogT) * scene.fogMax;
                int fr = (scene.fog >> 16) & 0xFF, fg = (scene.fog >> 8) & 0xFF, fb = scene.fog & 0xFF;
                int tr = (tint >> 16) & 0xFF, tg = (tint >> 8) & 0xFF, tb = tint & 0xFF;
                int ox = (i % cols) * TILE, oy = (i / cols) * TILE;
                // Leaves are drawn solid: what shows through their holes is the dark inside of the tree.
                int holeR = 0, holeG = 0, holeB = 0;
                if (fill) {
                    long sr = 0, sg = 0, sb = 0; int cnt = 0;
                    for (int t2 = 0; t2 < src.px.length; t2++) { int p = src.px[t2]; if ((p >>> 24) > 40) { sr += (p >> 16) & 0xFF; sg += (p >> 8) & 0xFF; sb += p & 0xFF; cnt++; } }
                    if (cnt > 0) { holeR = (int) (sr / cnt * 0.52f); holeG = (int) (sg / cnt * 0.52f); holeB = (int) (sb / cnt * 0.52f); }
                }
                for (int y = 0; y < h; y++) {
                    float fy = (y + 0.5f) / h;
                    for (int x = 0; x < w; x++) {
                        float fx = (x + 0.5f) / w;
                        int sxp = Math.min(src.w - 1, (int) ((u + (x + 0.5f) * sw / w) * unit)), syp = Math.min(src.h - 1, (int) ((v + (y + 0.5f) * sh / h) * unit));
                        int p = src.px[syp * src.w + sxp];
                        int al = p >>> 24;
                        if (al < 26) {
                            if (!fill) { img.setPixel(ox + x, oy + y, 0); continue; }
                            p = 0xFF000000 | (holeR << 16) | (holeG << 8) | holeB; al = 255;
                        } else if (fill) al = 255;
                        // Water, ice and tinted glass are drawn solid, a little darker the more see-through they are (the depth
                        // behind them). Drawn see-through, the places where two pieces of a face overlap would add up to stripes.
                        float depth = 1f;
                        if (al < 255 || water) { depth = 0.45f + 0.55f * (water ? Math.min(al, 200) : al) / 255f; al = 255; }
                        float w00 = (1 - fx) * (1 - fy), w10 = fx * (1 - fy), w01 = (1 - fx) * fy, w11 = fx * fy;
                        // How much sun this spot gets: between the four nearest of the sixteen samples.
                        float gx = Math.max(0f, Math.min(3f, fx * 4f - 0.5f)), gy = Math.max(0f, Math.min(3f, fy * 4f - 0.5f));
                        int gx0 = Math.min(2, (int) gx), gy0 = Math.min(2, (int) gy);
                        float tx = gx - gx0, ty = gy - gy0;
                        float sun = (grid[gy0 * 4 + gx0] * (1 - tx) + grid[gy0 * 4 + gx0 + 1] * tx) * (1 - ty) + (grid[gy0 * 4 + 4 + gx0] * (1 - tx) + grid[gy0 * 4 + 5 + gx0] * tx) * ty;
                        sun = sun * sun * (3 - 2 * sun);
                        float lr = base[0][0] * w00 + base[1][0] * w10 + base[2][0] * w01 + base[3][0] * w11 + sun * (sunL[0][0] * w00 + sunL[1][0] * w10 + sunL[2][0] * w01 + sunL[3][0] * w11);
                        float lg = base[0][1] * w00 + base[1][1] * w10 + base[2][1] * w01 + base[3][1] * w11 + sun * (sunL[0][1] * w00 + sunL[1][1] * w10 + sunL[2][1] * w01 + sunL[3][1] * w11);
                        float lb = base[0][2] * w00 + base[1][2] * w10 + base[2][2] * w01 + base[3][2] * w11 + sun * (sunL[0][2] * w00 + sunL[1][2] * w10 + sunL[2][2] * w01 + sunL[3][2] * w11);
                        if (emit >= 10) { lr = Math.max(lr, 1.05f); lg = Math.max(lg, 0.95f); lb = Math.max(lb, 0.78f); }
                        float r = grade(((p >> 16) & 0xFF) * tr / 65025f * lr * depth), g = grade(((p >> 8) & 0xFF) * tg / 65025f * lg * depth), b = grade((p & 0xFF) * tb / 65025f * lb * depth);
                        // A little more colour, the way a shader pack's picture has it.
                        float lum = 0.299f * r + 0.587f * g + 0.114f * b;
                        r = lum + (r - lum) * SATURATION; g = lum + (g - lum) * SATURATION; b = lum + (b - lum) * SATURATION;
                        r = r * 255f; g = g * 255f; b = b * 255f;
                        r += (fr - r) * fogT; g += (fg - g) * fogT; b += (fb - b) * fogT;
                        int R = r > 255 ? 255 : r < 0 ? 0 : Math.round(r), G = g > 255 ? 255 : g < 0 ? 0 : Math.round(g), B = b > 255 ? 255 : b < 0 ? 0 : Math.round(b);
                        img.setPixel(ox + x, oy + y, (al << 24) | (R << 16) | (G << 8) | B);
                    }
                }
                m.tu[i] = ox; m.tv[i] = oy; m.tw[i] = w; m.th[i] = h;
                i++;
            }
            m.n = i;
            m.atlas = Identifier.fromNamespaceAndPath(IceyMod.MOD_ID, "menu/atlas/" + scene.id);
            mc.getTextureManager().register(m.atlas, new DynamicTexture(() -> "icey scene " + scene.id, img));
            m.ready = m.n > 0;
            return m.ready ? m : null;
        } catch (Throwable t) {
            System.out.println("[IceyMod] scene model " + scene.id + " not read: " + t);
            return null;
        }
    }

    private static final float SATURATION = 1.2f;
    /** How far (canvas pixels) a corner of a drawn piece may be from where perspective puts it; pieces overlap by that much, so no gap shows. */
    private static final float SEAM = 0.55f;

    /** Bright light rolls off softly instead of going flat white (0..about 1.6 in, 0..1 out). */
    private static float grade(float c) {
        if (c <= 0.6f) return c;
        return 0.6f + 0.4f * (1f - (float) Math.exp(-(c - 0.6f) / 0.4f));
    }

    /**
     * The light at one corner of a face, the way a shader pack lights the
     * game. {@code base}: what is there without the sun (the cool light of
     * the sky, the glow of lamps); {@code sun}: what the sun adds where it
     * reaches, for a face that looks towards it. Less of both in a corner
     * that is boxed in.
     */
    private static void corner(Scenes.Scene s, float[] base, float[] sun, int sky, int blk, int emit, float ndl, float ao) {
        float open = sky / 15f;
        float direct = s.day * ndl * (0.3f + 0.7f * open);
        float amb = s.ambient * (0.5f + 0.5f * open);
        float lamp = (float) Math.pow(Math.max(blk, emit) / 15f, 1.7) * s.glow;
        float sr = ((s.sun >> 16) & 0xFF) / 255f, sg = ((s.sun >> 8) & 0xFF) / 255f, sb = (s.sun & 0xFF) / 255f;
        float hr = ((s.shade >> 16) & 0xFF) / 255f, hg = ((s.shade >> 8) & 0xFF) / 255f, hb = (s.shade & 0xFF) / 255f;
        base[0] = (hr * amb + 1.00f * lamp) * ao; base[1] = (hg * amb + 0.78f * lamp) * ao; base[2] = (hb * amb + 0.48f * lamp) * ao;
        // The sun is dimmed less by a boxed-in corner than the sky's light is.
        float aoSun = 0.45f + 0.55f * ao;
        sun[0] = sr * direct * aoSun; sun[1] = sg * direct * aoSun; sun[2] = sb * direct * aoSun;
    }

    private static final class Tex {
        int[] px; int w, h;
    }

    /**
     * A texture from the game (so from the player's resource pack). Block
     * textures come as 16x16; mob textures keep their own size. Null when
     * this game version has no such texture (mob textures were renamed
     * between versions: the known other names are tried).
     */
    private static Tex tile(String name) {
        boolean mob = name.startsWith("entity/");
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add(name);
        if (mob) {
            int slash = name.lastIndexOf('/');
            String dir = name.substring(0, slash), file = name.substring(slash + 1), kind = dir.substring(dir.lastIndexOf('/') + 1);
            for (String climate : new String[] { "temperate", "cold", "warm" })
                if (file.startsWith(climate + "_")) names.add(dir + "/" + file.substring(climate.length() + 1) + "_" + climate);
            names.add(dir + "/" + kind + "_" + file);
            if (!name.contains("/" + kind + "/" + kind)) names.add("entity/" + file + "/" + file);
        }
        for (String candidate : names) {
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(Identifier.withDefaultNamespace("textures/" + candidate + ".png"));
                if (res.isEmpty()) continue;
                try (InputStream in = res.get().open(); NativeImage src = NativeImage.read(in)) {
                    Tex t = new Tex();
                    if (mob) {
                        t.w = Math.min(256, src.getWidth()); t.h = Math.min(256, src.getHeight());
                        t.px = new int[t.w * t.h];
                        for (int y = 0; y < t.h; y++) for (int x = 0; x < t.w; x++) t.px[y * t.w + x] = src.getPixel(x * src.getWidth() / t.w, y * src.getHeight() / t.h);
                    } else {
                        // Animated textures are a column of frames (the first is used); packs may be finer than 16 pixels.
                        int size = Math.min(src.getWidth(), src.getHeight());
                        t.w = t.h = TILE;
                        t.px = new int[TILE * TILE];
                        for (int y = 0; y < TILE; y++) for (int x = 0; x < TILE; x++) t.px[y * TILE + x] = src.getPixel(x * size / TILE, y * size / TILE);
                    }
                    return t;
                }
            } catch (Throwable ignored) {}
        }
        if (mob) return null;
        Tex t = new Tex();
        t.w = t.h = TILE; t.px = new int[TILE * TILE];
        java.util.Arrays.fill(t.px, 0xFF8A8F98);
        return t;
    }

    // ── The camera of this frame ──
    private float camX, camY, camZ, rx, ry, rz, uxv, uyv, uzv, fx, fy, fz, focal, sx0, sy0;
    private float baseYaw;
    // Per face, for this frame: its corner and edges as the camera sees them (9), its box on the canvas (4); into how many pieces it is cut.
    private float[] cam;
    private byte[] sub;
    private long[] keys;
    private int seen;
    private boolean prepared;

    /**
     * Puts the camera: turned around the player by {@code dYaw}/{@code dPitch} degrees from where it rests,
     * {@code focalPx} canvas pixels of focal length (half the picture height over tan(fov/2), times any zoom),
     * the middle of the picture at (centreX, centreY).
     */
    public void view(float dYaw, float dPitch, float focalPx, float centreX, float centreY) {
        double y = Math.toRadians(yaw + dYaw), p = Math.toRadians(pitch + dPitch);
        // The player's middle sits a little under the middle of the picture.
        double ang = p + Math.atan(0.2 * Math.tan(Math.toRadians(fov / 2)));
        camX = (float) (Math.sin(y) * dist * Math.cos(ang)); camY = 1f + (float) (dist * Math.sin(ang)); camZ = (float) (-Math.cos(y) * dist * Math.cos(ang));
        fx = (float) (-Math.sin(y) * Math.cos(p)); fy = (float) -Math.sin(p); fz = (float) (Math.cos(y) * Math.cos(p));
        rx = (float) -Math.cos(y); ry = 0; rz = (float) -Math.sin(y);
        // up = right x forward
        uxv = ry * fz - rz * fy; uyv = rz * fx - rx * fz; uzv = rx * fy - ry * fx;
        focal = focalPx; sx0 = centreX; sy0 = centreY;
        baseYaw = (float) Math.toRadians(yaw);
        prepared = false;
    }

    /** The focal length for a picture this high (canvas pixels), so the camera's opening angle fills it. */
    public float focalFor(float pictureHeight) { return pictureHeight / 2f / (float) Math.tan(Math.toRadians(fov / 2)); }

    /**
     * Where a point of the place is on the canvas: x, y, how many canvas pixels a block is there, and how far
     * from the camera it is (blocks; under 0.3 means behind the camera, the other values are then not to be used).
     */
    public float[] project(float x, float y, float z) {
        float dx = x - camX, dy = y - camY, dz = z - camZ;
        float zf = dx * fx + dy * fy + dz * fz;
        if (zf < 0.3f) return new float[] { -99999f, -99999f, 0f, zf };
        float k = focal / zf;
        return new float[] { sx0 + (dx * rx + dy * ry + dz * rz) * k, sy0 - (dx * uxv + dy * uyv + dz * uzv) * k, k, zf };
    }
    /** How far a figure standing there is seen from the side (0 = straight from the front when the camera rests behind the player), in radians. */
    public float yawSeen(float x, float z) {
        float dx = x - camX, dz = z - camZ;
        // Between -180 and 180 degrees: callers halve it (how far the head turns), and a full turn too many would point the head backwards.
        double a = Math.atan2(-dx, dz) - baseYaw;
        a = a - Math.floor((a + Math.PI) / (2 * Math.PI)) * (2 * Math.PI);
        return (float) a;
    }
    /** How steeply the camera looks down on a figure standing there, in radians. */
    public float pitchSeen(float x, float y, float z) {
        float dx = x - camX, dz = z - camZ;
        return (float) Math.atan2(camY - (y + 1f), Math.sqrt(dx * dx + dz * dz));
    }
    /** The canvas row of the horizon. */
    public float horizonY() { return sy0 + focal * fy / (float) Math.sqrt(fx * fx + fz * fz); }

    /** Works out, once per frame, which faces the camera sees, where, and in which order they go down (far first). */
    private void prepare() {
        prepared = true;
        int m = 0;
        for (int i = 0; i < n; i++) {
            float dx = px[i] - camX, dy = py[i] - camY, dz = pz[i] - camZ;
            // Faces away from the camera?
            if ((flags[i] & 1) == 0 && nx[i] * (cx[i] - camX) + ny[i] * (cy[i] - camY) + nz[i] * (cz[i] - camZ) > -0.004f) continue;
            float p0x = dx * rx + dy * ry + dz * rz, p0y = dx * uxv + dy * uyv + dz * uzv, p0z = dx * fx + dy * fy + dz * fz;
            float ax = ux[i] * rx + uy[i] * ry + uz[i] * rz, ay = ux[i] * uxv + uy[i] * uyv + uz[i] * uzv, az = ux[i] * fx + uy[i] * fy + uz[i] * fz;
            float bx = vx[i] * rx + vy[i] * ry + vz[i] * rz, by = vx[i] * uxv + vy[i] * uyv + vz[i] * uzv, bz = vx[i] * fx + vy[i] * fy + vz[i] * fz;
            float z0 = p0z, z1 = p0z + az, z2 = p0z + bz, z3 = p0z + az + bz;
            if (z0 < 0.35f || z1 < 0.35f || z2 < 0.35f || z3 < 0.35f) continue;         // at or behind the lens
            float k0 = focal / z0, k1 = focal / z1, k2 = focal / z2, k3 = focal / z3;
            float s0x = sx0 + p0x * k0, s0y = sy0 - p0y * k0;
            float s1x = sx0 + (p0x + ax) * k1, s1y = sy0 - (p0y + ay) * k1;
            float s2x = sx0 + (p0x + bx) * k2, s2y = sy0 - (p0y + by) * k2;
            float s3x = sx0 + (p0x + ax + bx) * k3, s3y = sy0 - (p0y + ay + by) * k3;
            float minX = Math.min(Math.min(s0x, s1x), Math.min(s2x, s3x)), maxX = Math.max(Math.max(s0x, s1x), Math.max(s2x, s3x));
            float minY = Math.min(Math.min(s0y, s1y), Math.min(s2y, s3y)), maxY = Math.max(Math.max(s0y, s1y), Math.max(s2y, s3y));
            if (maxX < clipX0 || minX > clipX1 || maxY < clipY0 || minY > clipY1) continue;
            if (maxX - minX < 0.45f && maxY - minY < 0.45f) continue;                 // smaller than a pixel: not worth a draw
            int o = i * 13;
            cam[o] = p0x; cam[o + 1] = p0y; cam[o + 2] = p0z; cam[o + 3] = ax; cam[o + 4] = ay; cam[o + 5] = az; cam[o + 6] = bx; cam[o + 7] = by; cam[o + 8] = bz;
            cam[o + 9] = minX - 1; cam[o + 10] = minY - 1; cam[o + 11] = maxX + 1; cam[o + 12] = maxY + 1;
            // How far the face is from being a parallelogram (canvas pixels at a corner) decides into how many pieces it is cut.
            float e = Math.max(Math.abs(s0x - s1x - s2x + s3x), Math.abs(s0y - s1y - s2y + s3y)) * 0.25f;
            sub[i] = (byte) (e <= SEAM ? 1 : Math.min(6, (int) Math.ceil(Math.sqrt(e / SEAM))));
            float ex = cx[i] - camX, ey = cy[i] - camY, ez = cz[i] - camZ;
            keys[m++] = ((long) Float.floatToIntBits(ex * ex + ey * ey + ez * ez) << 32) | i;
        }
        java.util.Arrays.sort(keys, 0, m);
        seen = m;
    }

    private float clipX0 = -1e9f, clipY0 = -1e9f, clipX1 = 1e9f, clipY1 = 1e9f;
    /** The part of the canvas anything will be drawn in this frame (faces outside are not even looked at). Call before the first draw. */
    public void bounds(float x0, float y0, float x1, float y1) { clipX0 = x0; clipY0 = y0; clipX1 = x1; clipY1 = y1; prepared = false; }

    public int facesSeen() { if (!prepared && ready) prepare(); return seen; }

    private final Matrix3x2f mat = new Matrix3x2f();
    private final float[] gx = new float[49], gy = new float[49];
    private int drawn;

    /** One piece of a face: the parallelogram that fits its four corners best, grown a little so no seam shows. */
    private void piece(Gfx g, float s0x, float s0y, float s1x, float s1y, float s2x, float s2y, float s3x, float s3y, float u, float v, int w, int h, int tint) {
        float ax = ((s1x - s0x) + (s3x - s2x)) * 0.5f, ay = ((s1y - s0y) + (s3y - s2y)) * 0.5f;
        float bx = ((s2x - s0x) + (s3x - s1x)) * 0.5f, by = ((s2y - s0y) + (s3y - s1y)) * 0.5f;
        float mx = (s0x + s1x + s2x + s3x) * 0.25f, my = (s0y + s1y + s2y + s3y) * 0.25f;
        float e = Math.max(Math.abs(s0x - s1x - s2x + s3x), Math.abs(s0y - s1y - s2y + s3y)) * 0.25f;
        float la = (float) Math.sqrt(ax * ax + ay * ay), lb = (float) Math.sqrt(bx * bx + by * by);
        if (la < 0.02f || lb < 0.02f) return;
        float grow = 0.3f + e;
        float ka = (la + 2 * grow) / la, kb = (lb + 2 * grow) / lb;
        ax *= ka; ay *= ka; bx *= kb; by *= kb;
        g.pose().pushMatrix();
        try {
            mat.set(ax / w, ay / w, bx / h, by / h, mx - (ax + bx) * 0.5f, my - (ay + by) * 0.5f);
            g.pose().mul(mat);
            g.blit(atlas, 0, 0, u, v, w, h, atlasW, atlasH, tint);
        } finally {
            g.pose().popMatrix();
        }
        // A fresh layer every few dozen pieces keeps the game's own overlap bookkeeping short.
        if (++drawn % 48 == 0) g.nextStratum();
    }

    /**
     * Draws what of the model lies in a part of the canvas, far faces first. (The caller clips to that part.)
     */
    public void draw(Gfx g, float x0, float y0, float x1, float y1, float alpha) {
        if (!ready) return;
        if (!prepared) prepare();
        int tintAll = (Math.round(255 * Math.max(0f, Math.min(1f, alpha))) << 24) | 0xFFFFFF;
        for (int k = seen - 1; k >= 0; k--) {
            int i = (int) keys[k];
            int o = i * 13;
            if (cam[o + 11] < x0 || cam[o + 9] > x1 || cam[o + 12] < y0 || cam[o + 10] > y1) continue;
            float p0x = cam[o], p0y = cam[o + 1], p0z = cam[o + 2], ax = cam[o + 3], ay = cam[o + 4], az = cam[o + 5], bx = cam[o + 6], by = cam[o + 7], bz = cam[o + 8];
            int w = tw[i], h = th[i];
            int nu = Math.min(sub[i], w), nv = Math.min(sub[i], h);
            // The corners of the pieces, on whole pixels of the face's picture.
            for (int b = 0; b <= nv; b++) {
                float fv = Math.round(b * h / (float) nv) / (float) h;
                for (int a = 0; a <= nu; a++) {
                    float fu = Math.round(a * w / (float) nu) / (float) w;
                    float z = p0z + az * fu + bz * fv, kk = focal / z;
                    gx[b * 7 + a] = sx0 + (p0x + ax * fu + bx * fv) * kk;
                    gy[b * 7 + a] = sy0 - (p0y + ay * fu + by * fv) * kk;
                }
            }
            for (int b = 0; b < nv; b++) {
                int v0 = Math.round(b * h / (float) nv), v1 = Math.round((b + 1) * h / (float) nv);
                if (v1 <= v0) continue;
                for (int a = 0; a < nu; a++) {
                    int u0 = Math.round(a * w / (float) nu), u1 = Math.round((a + 1) * w / (float) nu);
                    if (u1 <= u0) continue;
                    int q = b * 7 + a;
                    piece(g, gx[q], gy[q], gx[q + 1], gy[q + 1], gx[q + 7], gy[q + 7], gx[q + 8], gy[q + 8], tu[i] + u0, tv[i] + v0, u1 - u0, v1 - v0, tintAll);
                }
            }
        }
        g.nextStratum();
    }
}
