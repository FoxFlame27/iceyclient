package com.iceymod.screen;

import com.iceymod.compat.Gfx;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * A player figure drawn straight from a skin picture, in 3D, with nothing
 * but flat textured quads.
 *
 * Seen without perspective, every face of a box is a parallelogram, and a
 * parallelogram is exactly what a 2D transform makes of a rectangle. So
 * each visible face is one ordinary GUI blit of its part of the skin, put
 * in place by a matrix. That needs no entity, no world and no model class,
 * so it is the same code on every game version, and any number of figures
 * can be on screen at once.
 */
final class Doll {
    private Doll() {}

    /** How the figure stands. Angles in radians. */
    static final class Pose {
        float yaw;                  // body turned to the viewer's right when positive
        float pitch = 0.16f;        // seen a little from above
        float headYaw, headPitch;   // relative to the body; pitch down when positive
        float rArmX, lArmX;         // arm swung back when positive
        float rArmZ, lArmZ;         // arm lifted sideways (right arm: negative lifts it)
        float rLegX, lLegX;
    }

    private static float[] rx(float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); return new float[] { 1, 0, 0, 0, c, -s, 0, s, c }; }
    private static float[] ry(float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); return new float[] { c, 0, s, 0, 1, 0, -s, 0, c }; }
    private static float[] rz(float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); return new float[] { c, -s, 0, s, c, 0, 0, 0, 1 }; }
    private static final float[] ID = { 1, 0, 0, 0, 1, 0, 0, 0, 1 };

    private static float[] mul(float[] a, float[] b) {
        float[] m = new float[9];
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++)
            m[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
        return m;
    }
    private static float[] ap(float[] m, float x, float y, float z) {
        return new float[] { m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z };
    }

    /** One box of the body: where it hangs, how big it is, where its picture is in the skin. */
    private static final class Part {
        float px, py, pz;           // pivot
        float ox, oy, oz;           // middle of the box, from the pivot
        float hx, hy, hz;           // half sizes
        int w, h, d;                // size in skin pixels
        int u, v;                   // base layer
        int ou, ov;                 // outer layer, -1 for none
        float inflate;              // how far the outer layer stands off
        float[] rot = ID;
        boolean mirror;             // old 64x32 skins: the left limbs are the right ones mirrored
        float depth;
        float[] m, o;
    }

    private static Part part(float px, float py, float pz, float ox, float oy, float oz, int w, int h, int d, int u, int v, int ou, int ov, float inflate) {
        Part p = new Part();
        p.px = px; p.py = py; p.pz = pz; p.ox = ox; p.oy = oy; p.oz = oz;
        p.w = w; p.h = h; p.d = d; p.hx = w / 2f; p.hy = h / 2f; p.hz = d / 2f;
        p.u = u; p.v = v; p.ou = ou; p.ov = ov; p.inflate = inflate;
        return p;
    }

    /**
     * @param fx,fy  where the feet are (the middle between them)
     * @param height how tall the figure is on screen, feet to top of the head
     * @param tint   ARGB the whole figure is multiplied by (alpha fades it)
     */
    static void draw(Gfx g, Identifier skin, int texH, boolean slim, float fx, float fy, float height, Pose p, int tint) {
        if (skin == null || height < 4) return;
        boolean old = texH < 64;
        int tH = old ? 32 : 64;
        float unit = height / 32f;
        int aw = slim && !old ? 3 : 4;
        float ax = slim && !old ? 5.5f : 6f;

        Part head = part(0, 24, 0, 0, 4, 0, 8, 8, 8, 0, 0, 32, 0, 0.5f);
        head.rot = mul(ry(p.headYaw), rx(p.headPitch));
        Part body = part(0, 24, 0, 0, -6, 0, 8, 12, 4, 16, 16, old ? -1 : 16, 32, 0.25f);
        Part rArm = part(-5, 22, 0, -(ax - 5), -4, 0, aw, 12, 4, 40, 16, old ? -1 : 40, 32, 0.25f);
        rArm.rot = mul(rx(p.rArmX), rz(p.rArmZ));
        Part lArm = old ? part(5, 22, 0, ax - 5, -4, 0, aw, 12, 4, 40, 16, -1, 0, 0.25f)
                        : part(5, 22, 0, ax - 5, -4, 0, aw, 12, 4, 32, 48, 48, 48, 0.25f);
        lArm.mirror = old;
        lArm.rot = mul(rx(p.lArmX), rz(p.lArmZ));
        Part rLeg = part(-2, 12, 0, 0, -6, 0, 4, 12, 4, 0, 16, old ? -1 : 0, 32, 0.25f);
        rLeg.rot = rx(p.rLegX);
        Part lLeg = old ? part(2, 12, 0, 0, -6, 0, 4, 12, 4, 0, 16, -1, 0, 0.25f)
                        : part(2, 12, 0, 0, -6, 0, 4, 12, 4, 16, 48, 0, 48, 0.25f);
        lLeg.mirror = old;
        lLeg.rot = rx(p.lLegX);

        float[] view = mul(rx(p.pitch), ry(p.yaw));
        Part[] parts = { head, body, rArm, lArm, rLeg, lLeg };
        for (Part q : parts) {
            q.m = mul(view, q.rot);
            float[] c = ap(q.rot, q.ox, q.oy, q.oz);
            q.o = ap(view, q.px + c[0], q.py + c[1], q.pz + c[2]);
            q.depth = q.o[2];
        }
        java.util.Arrays.sort(parts, (a, b) -> Float.compare(a.depth, b.depth));
        for (Part q : parts) {
            box(g, skin, tH, q, 0f, q.u, q.v, fx, fy, unit, tint);
            if (q.ou >= 0) box(g, skin, tH, q, q.inflate, q.ou, q.ov, fx, fy, unit, tint);
        }
    }

    // Faces: corner the picture starts at, the edge it runs along, the edge it runs down, and the outward direction.
    private static final float[][] FACE = {
        // front
        { -1, 1, 1,   2, 0, 0,   0, -2, 0,   0, 0, 1 },
        // back
        { 1, 1, -1,  -2, 0, 0,   0, -2, 0,   0, 0, -1 },
        // the player's right side
        { -1, 1, -1,  0, 0, 2,   0, -2, 0,  -1, 0, 0 },
        // the player's left side
        { 1, 1, 1,    0, 0, -2,  0, -2, 0,   1, 0, 0 },
        // top
        { -1, 1, -1,  2, 0, 0,   0, 0, 2,    0, 1, 0 },
        // bottom
        { -1, -1, 1,  2, 0, 0,   0, 0, -2,   0, -1, 0 },
    };

    private static void box(Gfx g, Identifier skin, int texH, Part q, float inflate, int u0, int v0, float fx, float fy, float unit, int tint) {
        float hx = q.hx + inflate, hy = q.hy + inflate, hz = q.hz + inflate;
        for (int f = 0; f < 6; f++) {
            float[] F = FACE[f];
            float[] n = ap(q.m, F[9], F[10], F[11]);
            if (n[2] <= 0.02f) continue;                       // faces away
            int tw, th, tu, tv;
            int side = f;
            if (q.mirror && (f == 2 || f == 3)) side = f == 2 ? 3 : 2;
            switch (side) {
                case 0 -> { tu = u0 + q.d; tv = v0 + q.d; tw = q.w; th = q.h; }
                case 1 -> { tu = u0 + q.d + q.w + q.d; tv = v0 + q.d; tw = q.w; th = q.h; }
                case 2 -> { tu = u0; tv = v0 + q.d; tw = q.d; th = q.h; }
                case 3 -> { tu = u0 + q.d + q.w; tv = v0 + q.d; tw = q.d; th = q.h; }
                case 4 -> { tu = u0 + q.d; tv = v0; tw = q.w; th = q.d; }
                default -> { tu = u0 + q.d + q.w; tv = v0; tw = q.w; th = q.d; }
            }
            float sx = F[0] * hx, sy = F[1] * hy, sz = F[2] * hz;
            float ux = F[3] * hx, uy = F[4] * hy, uz = F[5] * hz;
            float vx = F[6] * hx, vy = F[7] * hy, vz = F[8] * hz;
            if (q.mirror) { sx += ux; sy += uy; sz += uz; ux = -ux; uy = -uy; uz = -uz; }
            float[] p0 = ap(q.m, sx, sy, sz), du = ap(q.m, ux, uy, uz), dv = ap(q.m, vx, vy, vz);
            float x0 = fx + (q.o[0] + p0[0]) * unit, y0 = fy - (q.o[1] + p0[1]) * unit;
            // Light from the front left, a little from above; never darker than 62%.
            float lit = 0.62f + 0.30f * Math.max(0f, n[0] * -0.35f + n[1] * 0.35f + n[2] * 0.87f) + 0.10f * Math.max(0f, n[1]);
            int color = shade(tint, Math.min(1f, lit));
            g.pose().pushMatrix();
            try {
                g.pose().mul(new Matrix3x2f(du[0] * unit / tw, -du[1] * unit / tw, dv[0] * unit / th, -dv[1] * unit / th, x0, y0));
                g.blit(skin, 0, 0, (float) tu, (float) tv, tw, th, 64, texH, color);
            } finally {
                g.pose().popMatrix();
            }
        }
    }

    private static int shade(int argb, float k) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, gr = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (a << 24) | (Math.round(r * k) << 16) | (Math.round(gr * k) << 8) | Math.round(b * k);
    }

    /** Just the face (with the hat layer), as a flat square. */
    static void face(Gfx g, Identifier skin, int texH, float x, float y, float size, int tint) {
        if (skin == null) return;
        int tH = texH < 64 ? 32 : 64;
        g.pose().pushMatrix();
        try {
            g.pose().translate(x, y);
            g.pose().scale(size / 8f, size / 8f);
            g.blit(skin, 0, 0, 8f, 8f, 8, 8, 64, tH, tint);
            g.blit(skin, 0, 0, 40f, 8f, 8, 8, 64, tH, tint);
        } finally {
            g.pose().popMatrix();
        }
    }
}
