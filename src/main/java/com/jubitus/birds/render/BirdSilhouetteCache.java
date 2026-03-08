package com.jubitus.birds.render;

import com.jubitus.birds.species.BirdSpecies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SideOnly(Side.CLIENT)
public final class BirdSilhouetteCache {
    private static final java.util.Map<ResourceLocation, BufferedImage> SOURCE_IMG =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class Shape {
        public final LoftedBirdMesh.SilhouetteProfile outer;
        public final LoftedBirdMesh.SilhouetteProfile core;
        public final java.util.Map<Integer, LoftedBirdMesh.SilhouetteProfile> outerLod = new java.util.concurrent.ConcurrentHashMap<>();
        public final java.util.Map<Integer, LoftedBirdMesh.SilhouetteProfile> coreLod  = new java.util.concurrent.ConcurrentHashMap<>();
        public final int avgRGB; // 0xRRGGBB

        private Shape(LoftedBirdMesh.SilhouetteProfile outer,
                      LoftedBirdMesh.SilhouetteProfile core,
                      int avgRGB) {
            this.outer = outer;
            this.core = core;
            this.avgRGB = avgRGB;
        }
    }

    private static final class Key {
        final ResourceLocation tex;
        final int settingsHash;

        Key(ResourceLocation tex, int settingsHash) {
            this.tex = tex;
            this.settingsHash = settingsHash;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return settingsHash == k.settingsHash && tex.equals(k.tex);
        }

        @Override public int hashCode() {
            return 31 * tex.hashCode() + settingsHash;
        }
    }

    private static final java.util.Map<Key, Shape> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private BirdSilhouetteCache() {}

    public static void clear() {
        CACHE.clear();
        SOURCE_IMG.clear();
    }


    /** Fast lookup. Returns null if not built. */
    public static Shape get(ResourceLocation tex, BirdSpecies.SilhouetteSettings settings) {
        if (tex == null || settings == null) return null;
        return CACHE.get(new Key(tex, settings.stableHash()));
    }

    /** Convenience: builds from resource manager if needed. */
    public static Shape getOrBuild(ResourceLocation tex, BirdSpecies.SilhouetteSettings settings) {
        if (tex == null) return null;
        if (settings == null) settings = new BirdSpecies.SilhouetteSettings();

        settings = settings.copy();
        settings.clamp();

        Key key = new Key(tex, settings.stableHash());
        Shape cached = CACHE.get(key);
        if (cached != null) return cached;

        // ✅ If this is a runtime-loaded texture, we stored its image already.
// Use it to rebuild silhouettes for new settings.
        BufferedImage src = SOURCE_IMG.get(tex);
        if (src != null) {
            Shape built = buildShape(src, settings);
            if (built != null) CACHE.put(key, built);
            return built;
        }

        // Load image and build
        try {
            Minecraft mc = Minecraft.getMinecraft();
            IResource res = mc.getResourceManager().getResource(tex);
            try (InputStream in = res.getInputStream()) {
                BufferedImage img = TextureUtil.readBufferedImage(in);
                if (img == null) return null;
                Shape built = buildShape(img, settings);
                if (built != null) CACHE.put(key, built);
                return built;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    public static LoftedBirdMesh.SilhouetteProfile getOuterLod(ResourceLocation tex, BirdSpecies.SilhouetteSettings settings, int rings) {
        Shape s = getOrBuild(tex, settings);
        if (s == null || s.outer == null) return null;
        return s.outerLod.computeIfAbsent(rings, k -> downsampleProfile(s.outer, k));
    }

    public static LoftedBirdMesh.SilhouetteProfile getCoreLod(ResourceLocation tex, BirdSpecies.SilhouetteSettings settings, int rings) {
        Shape s = getOrBuild(tex, settings);
        if (s == null || s.core == null) return null;
        return s.coreLod.computeIfAbsent(rings, k -> downsampleProfile(s.core, k));
    }

    private static LoftedBirdMesh.SilhouetteProfile downsampleProfile(LoftedBirdMesh.SilhouetteProfile src, int rings) {
        int n = src.count();
        if (rings >= n) return src;
        rings = Math.max(3, rings);

        float[] t = new float[rings];
        float[] v = new float[rings];
        float[] uMin = new float[rings];
        float[] uMax = new float[rings];
        float[] uC = new float[rings];
        float[] thick = new float[rings];

        for (int i = 0; i < rings; i++) {
            float s = (rings == 1) ? 0f : (i / (float)(rings - 1));
            int idx = Math.round(s * (n - 1));

            t[i] = src.t[idx];
            v[i] = src.v[idx];
            uMin[i] = src.uMin[idx];
            uMax[i] = src.uMax[idx];
            uC[i] = src.uC[idx];
            thick[i] = src.thickMul[idx];
        }
        return new LoftedBirdMesh.SilhouetteProfile(t, v, uMin, uMax, uC, thick);
    }

    // ---------------------
    // Core builder, now driven by settings
    // ---------------------
    private static Shape buildShape(BufferedImage img, BirdSpecies.SilhouetteSettings S) {
        final int ALPHA_THRESHOLD = S.alphaThreshold;
        final int SMOOTH_RADIUS   = S.smoothRadius;
        final int RING_COUNT      = S.ringCount;
        final float DENSITY_POW   = S.densityPow;
        final float BELL_POW      = S.bellPow;
        final float THICK_GAIN    = S.thickGain;
        final float CORE_WIDTH_FACT = S.coreWidthFact;
        final float CORE_THICK_FACT = S.coreThickFact;

        final int w = img.getWidth();
        final int h = img.getHeight();
        if (w <= 0 || h <= 0) return null;

        float[] uMinRow = new float[h];
        float[] uMaxRow = new float[h];
        float[] densRow = new float[h];
        boolean[] hasRow = new boolean[h];

        long rSum=0,gSum=0,bSum=0, aSum=0;
        int yHead = -1, yTail = -1;

        for (int y = 0; y < h; y++) {
            int left = w, right = -1;
            int countOpaque = 0;

            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 255;

                if (a > 0) {
                    int r = (argb >>> 16) & 255;
                    int g = (argb >>> 8) & 255;
                    int b = (argb) & 255;
                    rSum += (long) r * a;
                    gSum += (long) g * a;
                    bSum += (long) b * a;
                    aSum += a;
                }

                if (a >= ALPHA_THRESHOLD) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    countOpaque++;
                }
            }

            if (right >= left) {
                hasRow[y] = true;
                if (yHead < 0) yHead = y;
                yTail = y;

                int span = (right - left + 1);
                float uMin = (float) ((left + 0.5) / (double) w);
                float uMax = (float) ((right + 0.5) / (double) w);

                float dens = (span > 0) ? (countOpaque / (float) span) : 0f;

                uMinRow[y] = uMin;
                uMaxRow[y] = uMax;
                densRow[y] = clamp01(dens);
            } else {
                hasRow[y] = false;
                uMinRow[y] = 0.5f;
                uMaxRow[y] = 0.5f;
                densRow[y] = 0f;
            }
        }

        if (yHead < 0 || yTail < 0 || yTail <= yHead) return null;

        float[] uMinS = new float[h];
        float[] uMaxS = new float[h];
        float[] densS = new float[h];

        for (int y = 0; y < h; y++) {
            if (!hasRow[y]) {
                uMinS[y] = uMinRow[y];
                uMaxS[y] = uMaxRow[y];
                densS[y] = densRow[y];
                continue;
            }
            uMinS[y] = smoothAt(uMinRow, hasRow, y, SMOOTH_RADIUS, uMinRow[y], h);
            uMaxS[y] = smoothAt(uMaxRow, hasRow, y, SMOOTH_RADIUS, uMaxRow[y], h);
            densS[y] = smoothAt(densRow, hasRow, y, SMOOTH_RADIUS, densRow[y], h);

            if (uMinS[y] > uMaxS[y]) {
                float tmp = uMinS[y];
                uMinS[y] = uMaxS[y];
                uMaxS[y] = tmp;
            }
        }

        int ringN = Math.max(3, RING_COUNT);
        float[] tArr = new float[ringN];
        float[] vArr = new float[ringN];
        float[] uMinArr = new float[ringN];
        float[] uMaxArr = new float[ringN];
        float[] uCArr = new float[ringN];
        float[] thickArr = new float[ringN];

        float[] uMinCore = new float[ringN];
        float[] uMaxCore = new float[ringN];
        float[] uCCore   = new float[ringN];
        float[] thickCore= new float[ringN];

        for (int i = 0; i < ringN; i++) {
            float t = (ringN == 1) ? 0f : (i / (float) (ringN - 1));
            tArr[i] = t;

            float yF = lerp(yTail, yHead, t);
            int y = clampInt(Math.round(yF), 0, h - 1);

            float v = y / (float) (h - 1);
            vArr[i] = v;

            float uMin = uMinS[y];
            float uMax = uMaxS[y];
            float uC = 0.5f * (uMin + uMax);

            uMinArr[i] = uMin;
            uMaxArr[i] = uMax;
            uCArr[i] = uC;

            float dens = densS[y];

            float bell = (float) Math.sin(Math.PI * t);
            bell = (float) Math.pow(clamp01(bell), BELL_POW);

            float densTerm = (float) Math.pow(clamp01(dens), DENSITY_POW);

            float thickMul = clamp01(THICK_GAIN * bell * densTerm);
            thickArr[i] = thickMul;

            float uMinC = uC + (uMin - uC) * CORE_WIDTH_FACT;
            float uMaxC = uC + (uMax - uC) * CORE_WIDTH_FACT;

            uMinCore[i] = uMinC;
            uMaxCore[i] = uMaxC;
            uCCore[i] = 0.5f * (uMinC + uMaxC);
            thickCore[i] = clamp01(thickMul * CORE_THICK_FACT);
        }

        int avgRGB = (aSum > 0)
                ? (((int)(rSum / aSum) & 255) << 16) | (((int)(gSum / aSum) & 255) << 8) | ((int)(bSum / aSum) & 255)
                : 0x6B6B6B;

        LoftedBirdMesh.SilhouetteProfile outer = new LoftedBirdMesh.SilhouetteProfile(tArr, vArr, uMinArr, uMaxArr, uCArr, thickArr);
        LoftedBirdMesh.SilhouetteProfile core  = new LoftedBirdMesh.SilhouetteProfile(tArr, vArr, uMinCore, uMaxCore, uCCore, thickCore);

        return new Shape(outer, core, avgRGB);
    }

    private static float smoothAt(float[] arr, boolean[] has, int idx, int r, float fallback, int h) {
        float sum = 0f;
        int cnt = 0;
        for (int k = -r; k <= r; k++) {
            int y = clampInt(idx + k, 0, h - 1);
            if (!has[y]) continue;
            sum += arr[y];
            cnt++;
        }
        return (cnt == 0) ? fallback : (sum / cnt);
    }

    private static float lerp(int a, int b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return (v < 0f) ? 0f : (v > 1f) ? 1f : v;
    }

    private static int clampInt(int v, int lo, int hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }
    public static void putBuilt(ResourceLocation tex, BirdSpecies.SilhouetteSettings settings, BufferedImage img) {
        if (tex == null || img == null) return;
        if (settings == null) settings = new BirdSpecies.SilhouetteSettings();

        BirdSpecies.SilhouetteSettings s2 = settings.copy();
        s2.clamp();

// Store source image so we can rebuild later with new settings (GUI live editing)
        SOURCE_IMG.put(tex, img);

        Shape built = buildShape(img, s2);
        if (built != null) {
            CACHE.put(new Key(tex, s2.stableHash()), built);
        }

    }

}
