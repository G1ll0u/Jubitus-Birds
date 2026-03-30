package com.jubitus.birds.render;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public final class LoftedBirdMesh {

    private LoftedBirdMesh() {
    }

    public static void drawSilhouetteTextured(
            double halfW,
            double halfL,
            double baseThick,
            SilhouetteProfile p,
            double capOffset
    ) {
        if (p == null || p.count() < 2) return;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        for (int i = 0; i < p.count() - 1; i++) {
            RingS a = ringFrom(p, i, halfW, halfL, baseThick);
            RingS b = ringFrom(p, i + 1, halfW, halfL, baseThick);

            // Left face
            quad(buf,
                    a.xL, 0, a.z, a.uL, a.v,
                    b.xL, 0, b.z, b.uL, b.v,
                    b.xC, b.yT, b.z, b.uC, b.v,
                    a.xC, a.yT, a.z, a.uC, a.v
            );

            // Top face
            quad(buf,
                    a.xC, a.yT, a.z, a.uC, a.v,
                    b.xC, b.yT, b.z, b.uC, b.v,
                    b.xR, 0, b.z, b.uR, b.v,
                    a.xR, 0, a.z, a.uR, a.v
            );

            // Right face
            quad(buf,
                    a.xR, 0, a.z, a.uR, a.v,
                    b.xR, 0, b.z, b.uR, b.v,
                    b.xC, b.yB, b.z, b.uC, b.v,
                    a.xC, a.yB, a.z, a.uC, a.v
            );

            // Bottom face
            quad(buf,
                    a.xC, a.yB, a.z, a.uC, a.v,
                    b.xC, b.yB, b.z, b.uC, b.v,
                    b.xL, 0, b.z, b.uL, b.v,
                    a.xL, 0, a.z, a.uL, a.v
            );
        }

        tess.draw();

        // Caps (close volume)
        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);

        RingS head = ringFrom(p, p.count() - 1, halfW, halfL, baseThick);
        double zh = head.z + capOffset;
        cap4Textured(buf, head, zh, true);

        RingS tail = ringFrom(p, 0, halfW, halfL, baseThick);
        double zt = tail.z - capOffset;
        cap4Textured(buf, tail, zt, false);

        tess.draw();
    }

    private static RingS ringFrom(SilhouetteProfile p, int i, double halfW, double halfL, double baseThick) {
        double t = p.t[i];
        double z = lerp(-halfL, +halfL, t);

        double v = p.v[i];

        double uL = p.uMin[i];
        double uR = p.uMax[i];
        double uC = p.uC[i];

        // Map UV U directly into world X, so geometry matches silhouette
        double xL = (uL - 0.5) * 2.0 * halfW;
        double xR = (uR - 0.5) * 2.0 * halfW;
        double xC = (uC - 0.5) * 2.0 * halfW;

        double th = baseThick * p.thickMul[i];
        double yT = +th;
        double yB = -th;

        return new RingS(z, xL, xR, xC, yT, yB, uL, uR, uC, v);
    }

    private static void quad(BufferBuilder buf,
                             double x1, double y1, double z1, double u1, double v1,
                             double x2, double y2, double z2, double u2, double v2,
                             double x3, double y3, double z3, double u3, double v3,
                             double x4, double y4, double z4, double u4, double v4) {
        buf.pos(x1, y1, z1).tex(u1, v1).endVertex();
        buf.pos(x2, y2, z2).tex(u2, v2).endVertex();
        buf.pos(x3, y3, z3).tex(u3, v3).endVertex();
        buf.pos(x4, y4, z4).tex(u4, v4).endVertex();
    }

    private static void cap4Textured(BufferBuilder buf, RingS r, double zTip, boolean head) {
        // Tip uses center UV (uC, same v)
        if (head) {
            tri(buf, r.xC, r.yT, r.z, r.uC, r.v, r.xR, 0, r.z, r.uR, r.v, r.xC, 0, zTip, r.uC, r.v);
            tri(buf, r.xR, 0, r.z, r.uR, r.v, r.xC, r.yB, r.z, r.uC, r.v, r.xC, 0, zTip, r.uC, r.v);
            tri(buf, r.xC, r.yB, r.z, r.uC, r.v, r.xL, 0, r.z, r.uL, r.v, r.xC, 0, zTip, r.uC, r.v);
            tri(buf, r.xL, 0, r.z, r.uL, r.v, r.xC, r.yT, r.z, r.uC, r.v, r.xC, 0, zTip, r.uC, r.v);
        } else {
            tri(buf, r.xR, 0, r.z, r.uR, r.v, r.xC, r.yT, r.z, r.uC, r.v, r.xC, 0, zTip, r.uC, r.v);
            tri(buf, r.xC, r.yT, r.z, r.uC, r.v, r.xL, 0, r.z, r.uL, r.v, r.xC, 0, zTip, r.uC, r.v);
            tri(buf, r.xL, 0, r.z, r.uL, r.v, r.xC, r.yB, r.z, r.uC, r.v, r.xC, 0, zTip, r.uC, r.v);
            tri(buf, r.xC, r.yB, r.z, r.uC, r.v, r.xR, 0, r.z, r.uR, r.v, r.xC, 0, zTip, r.uC, r.v);
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static void tri(BufferBuilder buf,
                            double x1, double y1, double z1, double u1, double v1,
                            double x2, double y2, double z2, double u2, double v2,
                            double x3, double y3, double z3, double u3, double v3) {
        buf.pos(x1, y1, z1).tex(u1, v1).endVertex();
        buf.pos(x2, y2, z2).tex(u2, v2).endVertex();
        buf.pos(x3, y3, z3).tex(u3, v3).endVertex();
    }

    public static void drawSilhouetteColor(
            double halfW,
            double halfL,
            double baseThick,
            SilhouetteProfile p,
            double capOffset,
            int rgb,
            float alpha
    ) {
        if (p == null || p.count() < 2) return;

        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = (rgb) & 255;
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i < p.count() - 1; i++) {
            RingS A = ringFrom(p, i, halfW, halfL, baseThick);
            RingS B = ringFrom(p, i + 1, halfW, halfL, baseThick);

            quadC(buf, A.xL, 0, A.z, r, g, b, a, B.xL, 0, B.z, r, g, b, a, B.xC, B.yT, B.z, r, g, b, a, A.xC, A.yT, A.z, r, g, b, a);
            quadC(buf, A.xC, A.yT, A.z, r, g, b, a, B.xC, B.yT, B.z, r, g, b, a, B.xR, 0, B.z, r, g, b, a, A.xR, 0, A.z, r, g, b, a);
            quadC(buf, A.xR, 0, A.z, r, g, b, a, B.xR, 0, B.z, r, g, b, a, B.xC, B.yB, B.z, r, g, b, a, A.xC, A.yB, A.z, r, g, b, a);
            quadC(buf, A.xC, A.yB, A.z, r, g, b, a, B.xC, B.yB, B.z, r, g, b, a, B.xL, 0, B.z, r, g, b, a, A.xL, 0, A.z, r, g, b, a);
        }

        tess.draw();

        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        RingS head = ringFrom(p, p.count() - 1, halfW, halfL, baseThick);
        double zh = head.z + capOffset;
        cap4Color(buf, head, zh, true, r, g, b, a);

        RingS tail = ringFrom(p, 0, halfW, halfL, baseThick);
        double zt = tail.z - capOffset;
        cap4Color(buf, tail, zt, false, r, g, b, a);

        tess.draw();
    }

    private static void quadC(BufferBuilder buf,
                              double x1, double y1, double z1, int r1, int g1, int b1, int a1,
                              double x2, double y2, double z2, int r2, int g2, int b2, int a2,
                              double x3, double y3, double z3, int r3, int g3, int b3, int a3,
                              double x4, double y4, double z4, int r4, int g4, int b4, int a4) {
        buf.pos(x1, y1, z1).color(r1, g1, b1, a1).endVertex();
        buf.pos(x2, y2, z2).color(r2, g2, b2, a2).endVertex();
        buf.pos(x3, y3, z3).color(r3, g3, b3, a3).endVertex();
        buf.pos(x4, y4, z4).color(r4, g4, b4, a4).endVertex();
    }

    private static void cap4Color(BufferBuilder buf, RingS r, double zTip, boolean head,
                                  int R, int G, int B, int A) {
        if (head) {
            triC(buf, r.xC, r.yT, r.z, R, G, B, A, r.xR, 0, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
            triC(buf, r.xR, 0, r.z, R, G, B, A, r.xC, r.yB, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
            triC(buf, r.xC, r.yB, r.z, R, G, B, A, r.xL, 0, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
            triC(buf, r.xL, 0, r.z, R, G, B, A, r.xC, r.yT, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
        } else {
            triC(buf, r.xR, 0, r.z, R, G, B, A, r.xC, r.yT, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
            triC(buf, r.xC, r.yT, r.z, R, G, B, A, r.xL, 0, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
            triC(buf, r.xL, 0, r.z, R, G, B, A, r.xC, r.yB, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
            triC(buf, r.xC, r.yB, r.z, R, G, B, A, r.xR, 0, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
        }
    }

    private static void triC(BufferBuilder buf,
                             double x1, double y1, double z1, int r1, int g1, int b1, int a1,
                             double x2, double y2, double z2, int r2, int g2, int b2, int a2,
                             double x3, double y3, double z3, int r3, int g3, int b3, int a3) {
        buf.pos(x1, y1, z1).color(r1, g1, b1, a1).endVertex();
        buf.pos(x2, y2, z2).color(r2, g2, b2, a2).endVertex();
        buf.pos(x3, y3, z3).color(r3, g3, b3, a3).endVertex();
    }

    public static final class SilhouetteProfile {
        // t: 0 tail .. 1 head
        // v: texture V coordinate per ring (0 top/head .. 1 bottom/tail)
        // uMin/uMax/uC: texture U coords for left/right/center per ring
        // thickMul: 0..1 thickness multiplier per ring
        public final float[] t, v, uMin, uMax, uC, thickMul;

        public SilhouetteProfile(float[] t, float[] v, float[] uMin, float[] uMax, float[] uC, float[] thickMul) {
            this.t = t;
            this.v = v;
            this.uMin = uMin;
            this.uMax = uMax;
            this.uC = uC;
            this.thickMul = thickMul;
        }

        public int count() {
            return t.length;
        }
    }

    private static final class RingS {
        final double z, xL, xR, xC, yT, yB;
        final double uL, uR, uC, v;

        RingS(double z, double xL, double xR, double xC, double yT, double yB,
              double uL, double uR, double uC, double v) {
            this.z = z;
            this.xL = xL;
            this.xR = xR;
            this.xC = xC;
            this.yT = yT;
            this.yB = yB;
            this.uL = uL;
            this.uR = uR;
            this.uC = uC;
            this.v = v;
        }
    }

    public static class Profile {
        public final float[] zT;   // 0 tail .. 1 head
        public final float[] wMul; // width multipliers per ring
        public final float[] tMul; // thickness multipliers per ring

        public Profile(float[] zT, float[] wMul, float[] tMul) {
            this.zT = zT;
            this.wMul = wMul;
            this.tMul = tMul;
        }
    }

}
