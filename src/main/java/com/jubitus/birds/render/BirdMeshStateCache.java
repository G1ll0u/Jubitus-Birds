package com.jubitus.birds.render;

import com.jubitus.birds.species.BirdSpecies;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BirdMeshStateCache {

    private static final double HALF_L = 0.7;
    private static final double BASE_THICKNESS = 0.22;
    private static final double CAP_OFFSET = 0.06;

    private static final Map<Key, CompiledMesh> CACHE = new ConcurrentHashMap<>();

    private BirdMeshStateCache() {
    }

    public static void clear() {
        CACHE.clear();
    }

    public static CompiledMesh getOrBuild(ResourceLocation tex,
                                          BirdSpecies.SilhouetteSettings sil,
                                          int lod,
                                          int widthBin,
                                          LoftedBirdMesh.SilhouetteProfile outer,
                                          LoftedBirdMesh.SilhouetteProfile core,
                                          int avgRgb,
                                          boolean includeCore) {
        Key key = new Key(tex, sil.stableHash(), lod, widthBin, includeCore);
        CompiledMesh cached = CACHE.get(key);
        if (cached != null) return cached;

        double halfW = widthBin / 20.0D;
        CompiledMesh built = compile(halfW, outer, includeCore ? core : null, avgRgb);
        CACHE.put(key, built);
        return built;
    }

    private static CompiledMesh compile(double halfW,
                                        LoftedBirdMesh.SilhouetteProfile outer,
                                        LoftedBirdMesh.SilhouetteProfile core,
                                        int avgRgb) {
        BufferBuilder.State outerState = buildTexturedState(halfW, HALF_L, BASE_THICKNESS, outer, CAP_OFFSET);
        BufferBuilder.State coreState = core != null
                ? buildColorState(halfW, HALF_L, BASE_THICKNESS, core, CAP_OFFSET * 0.9, avgRgb, 1.0f)
                : null;

        return new CompiledMesh(outerState, coreState);
    }

    private static BufferBuilder.State buildTexturedState(double halfW,
                                                          double halfL,
                                                          double baseThick,
                                                          LoftedBirdMesh.SilhouetteProfile p,
                                                          double capOffset) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);

        for (int i = 0; i < p.count() - 1; i++) {
            Ring a = ringFrom(p, i, halfW, halfL, baseThick);
            Ring b = ringFrom(p, i + 1, halfW, halfL, baseThick);

            quadTex(buf, a.xL, 0, a.z, a.uL, a.v, b.xL, 0, b.z, b.uL, b.v, b.xC, b.yT, b.z, b.uC, b.v, a.xC, a.yT, a.z, a.uC, a.v);
            quadTex(buf, a.xC, a.yT, a.z, a.uC, a.v, b.xC, b.yT, b.z, b.uC, b.v, b.xR, 0, b.z, b.uR, b.v, a.xR, 0, a.z, a.uR, a.v);
            quadTex(buf, a.xR, 0, a.z, a.uR, a.v, b.xR, 0, b.z, b.uR, b.v, b.xC, b.yB, b.z, b.uC, b.v, a.xC, a.yB, a.z, a.uC, a.v);
            quadTex(buf, a.xC, a.yB, a.z, a.uC, a.v, b.xC, b.yB, b.z, b.uC, b.v, b.xL, 0, b.z, b.uL, b.v, a.xL, 0, a.z, a.uL, a.v);
        }

        Ring head = ringFrom(p, p.count() - 1, halfW, halfL, baseThick);
        Ring tail = ringFrom(p, 0, halfW, halfL, baseThick);

        cap4Textured(buf, head, head.z + capOffset, true);
        cap4Textured(buf, tail, tail.z - capOffset, false);

        buf.finishDrawing();
        return buf.getVertexState();
    }

    private static BufferBuilder.State buildColorState(double halfW,
                                                       double halfL,
                                                       double baseThick,
                                                       LoftedBirdMesh.SilhouetteProfile p,
                                                       double capOffset,
                                                       int rgb,
                                                       float alpha) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        int a = Math.max(0, Math.min(255, (int) (alpha * 255.0f)));

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        for (int i = 0; i < p.count() - 1; i++) {
            Ring A = ringFrom(p, i, halfW, halfL, baseThick);
            Ring B = ringFrom(p, i + 1, halfW, halfL, baseThick);

            quadColor(buf, A.xL, 0, A.z, r, g, b, a, B.xL, 0, B.z, r, g, b, a, B.xC, B.yT, B.z, r, g, b, a, A.xC, A.yT, A.z, r, g, b, a);
            quadColor(buf, A.xC, A.yT, A.z, r, g, b, a, B.xC, B.yT, B.z, r, g, b, a, B.xR, 0, B.z, r, g, b, a, A.xR, 0, A.z, r, g, b, a);
            quadColor(buf, A.xR, 0, A.z, r, g, b, a, B.xR, 0, B.z, r, g, b, a, B.xC, B.yB, B.z, r, g, b, a, A.xC, A.yB, A.z, r, g, b, a);
            quadColor(buf, A.xC, A.yB, A.z, r, g, b, a, B.xC, B.yB, B.z, r, g, b, a, B.xL, 0, B.z, r, g, b, a, A.xL, 0, A.z, r, g, b, a);
        }

        Ring head = ringFrom(p, p.count() - 1, halfW, halfL, baseThick);
        Ring tail = ringFrom(p, 0, halfW, halfL, baseThick);

        cap4Color(buf, head, head.z + capOffset, true, r, g, b, a);
        cap4Color(buf, tail, tail.z - capOffset, false, r, g, b, a);

        buf.finishDrawing();
        return buf.getVertexState();
    }

    private static Ring ringFrom(LoftedBirdMesh.SilhouetteProfile p, int i, double halfW, double halfL, double baseThick) {
        double t = p.t[i];
        double z = lerp(-halfL, halfL, t);

        double uL = p.uMin[i];
        double uR = p.uMax[i];
        double uC = p.uC[i];

        double xL = (uL - 0.5) * 2.0 * halfW;
        double xR = (uR - 0.5) * 2.0 * halfW;
        double xC = (uC - 0.5) * 2.0 * halfW;

        double th = baseThick * p.thickMul[i];

        return new Ring(z, xL, xR, xC, th, -th, uL, uR, uC, p.v[i]);
    }

    private static void quadTex(BufferBuilder buf,
                                double x1, double y1, double z1, double u1, double v1,
                                double x2, double y2, double z2, double u2, double v2,
                                double x3, double y3, double z3, double u3, double v3,
                                double x4, double y4, double z4, double u4, double v4) {
        triTex(buf, x1, y1, z1, u1, v1, x2, y2, z2, u2, v2, x3, y3, z3, u3, v3);
        triTex(buf, x1, y1, z1, u1, v1, x3, y3, z3, u3, v3, x4, y4, z4, u4, v4);
    }

    private static void quadColor(BufferBuilder buf,
                                  double x1, double y1, double z1, int r1, int g1, int b1, int a1,
                                  double x2, double y2, double z2, int r2, int g2, int b2, int a2,
                                  double x3, double y3, double z3, int r3, int g3, int b3, int a3,
                                  double x4, double y4, double z4, int r4, int g4, int b4, int a4) {
        triColor(buf, x1, y1, z1, r1, g1, b1, a1, x2, y2, z2, r2, g2, b2, a2, x3, y3, z3, r3, g3, b3, a3);
        triColor(buf, x1, y1, z1, r1, g1, b1, a1, x3, y3, z3, r3, g3, b3, a3, x4, y4, z4, r4, g4, b4, a4);
    }

    private static void cap4Textured(BufferBuilder buf, Ring r, double zTip, boolean head) {
        if (head) {
            triTex(buf, r.xC, r.yT, r.z, r.uC, r.v, r.xR, 0, r.z, r.uR, r.v, r.xC, 0, zTip, r.uC, r.v);
            triTex(buf, r.xR, 0, r.z, r.uR, r.v, r.xC, r.yB, r.z, r.uC, r.v, r.xC, 0, zTip, r.uC, r.v);
            triTex(buf, r.xC, r.yB, r.z, r.uC, r.v, r.xL, 0, r.z, r.uL, r.v, r.xC, 0, zTip, r.uC, r.v);
            triTex(buf, r.xL, 0, r.z, r.uL, r.v, r.xC, r.yT, r.z, r.uC, r.v, r.xC, 0, zTip, r.uC, r.v);
        } else {
            triTex(buf, r.xR, 0, r.z, r.uR, r.v, r.xC, r.yT, r.z, r.uC, r.v, r.xC, 0, zTip, r.uC, r.v);
            triTex(buf, r.xC, r.yT, r.z, r.uC, r.v, r.xL, 0, r.z, r.uL, r.v, r.xC, 0, zTip, r.uC, r.v);
            triTex(buf, r.xL, 0, r.z, r.uL, r.v, r.xC, r.yB, r.z, r.uC, r.v, r.xC, 0, zTip, r.uC, r.v);
            triTex(buf, r.xC, r.yB, r.z, r.uC, r.v, r.xR, 0, r.z, r.uR, r.v, r.xC, 0, zTip, r.uC, r.v);
        }
    }

    private static void cap4Color(BufferBuilder buf, Ring r, double zTip, boolean head, int R, int G, int B, int A) {
        if (head) {
            triColor(buf, r.xC, r.yT, r.z, R, G, B, A, r.xR, 0, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
            triColor(buf, r.xR, 0, r.z, R, G, B, A, r.xC, r.yB, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
            triColor(buf, r.xC, r.yB, r.z, R, G, B, A, r.xL, 0, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
            triColor(buf, r.xL, 0, r.z, R, G, B, A, r.xC, r.yT, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
        } else {
            triColor(buf, r.xR, 0, r.z, R, G, B, A, r.xC, r.yT, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
            triColor(buf, r.xC, r.yT, r.z, R, G, B, A, r.xL, 0, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
            triColor(buf, r.xL, 0, r.z, R, G, B, A, r.xC, r.yB, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
            triColor(buf, r.xC, r.yB, r.z, R, G, B, A, r.xR, 0, r.z, R, G, B, A, r.xC, 0, zTip, R, G, B, A);
        }
    }

    private static void triTex(BufferBuilder buf,
                               double x1, double y1, double z1, double u1, double v1,
                               double x2, double y2, double z2, double u2, double v2,
                               double x3, double y3, double z3, double u3, double v3) {
        buf.pos(x1, y1, z1).tex(u1, v1).endVertex();
        buf.pos(x2, y2, z2).tex(u2, v2).endVertex();
        buf.pos(x3, y3, z3).tex(u3, v3).endVertex();
    }

    private static void triColor(BufferBuilder buf,
                                 double x1, double y1, double z1, int r1, int g1, int b1, int a1,
                                 double x2, double y2, double z2, int r2, int g2, int b2, int a2,
                                 double x3, double y3, double z3, int r3, int g3, int b3, int a3) {
        buf.pos(x1, y1, z1).color(r1, g1, b1, a1).endVertex();
        buf.pos(x2, y2, z2).color(r2, g2, b2, a2).endVertex();
        buf.pos(x3, y3, z3).color(r3, g3, b3, a3).endVertex();
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static final class CompiledMesh {
        private final BufferBuilder.State outerState;
        private final BufferBuilder.State coreState;

        private CompiledMesh(BufferBuilder.State outerState, BufferBuilder.State coreState) {
            this.outerState = outerState;
            this.coreState = coreState;
        }

        public void draw(boolean drawCore) {
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();

            if (drawCore && coreState != null) {
                GlStateManager.disableTexture2D();
                GlStateManager.disableCull();
                buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
                buf.setVertexState(coreState);
                tess.draw();
                GlStateManager.enableTexture2D();
            }

            GlStateManager.enableCull();
            GlStateManager.cullFace(GlStateManager.CullFace.BACK);
            buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);
            buf.setVertexState(outerState);
            tess.draw();
            GlStateManager.disableCull();
        }
    }

    private static final class Ring {
        final double z;
        final double xL;
        final double xR;
        final double xC;
        final double yT;
        final double yB;
        final double uL;
        final double uR;
        final double uC;
        final double v;

        private Ring(double z,
                     double xL,
                     double xR,
                     double xC,
                     double yT,
                     double yB,
                     double uL,
                     double uR,
                     double uC,
                     double v) {
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

    private static final class Key {
        final ResourceLocation tex;
        final int silHash;
        final int lod;
        final int widthBin;
        final boolean includeCore;

        private Key(ResourceLocation tex, int silHash, int lod, int widthBin, boolean includeCore) {
            this.tex = tex;
            this.silHash = silHash;
            this.lod = lod;
            this.widthBin = widthBin;
            this.includeCore = includeCore;
        }

        @Override
        public int hashCode() {
            int h = tex.hashCode();
            h = 31 * h + silHash;
            h = 31 * h + lod;
            h = 31 * h + widthBin;
            h = 31 * h + (includeCore ? 1 : 0);
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Key)) return false;
            Key o = (Key) obj;
            return this.lod == o.lod
                    && this.widthBin == o.widthBin
                    && this.includeCore == o.includeCore
                    && this.silHash == o.silHash
                    && this.tex.equals(o.tex);
        }
    }
}