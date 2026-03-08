package com.jubitus.birds.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BirdAvgColorCache {

    private static final Map<ResourceLocation, Integer> CACHE = new ConcurrentHashMap<>();

    private BirdAvgColorCache() {
    }

    /**
     * Returns average RGB of the PNG, ignoring fully transparent pixels.
     * Packed as 0xRRGGBB.
     */
    public static int getAvgRGB(ResourceLocation tex, int fallbackRGB) {
        if (tex == null) return fallbackRGB;
        Integer cached = CACHE.get(tex);
        if (cached != null) return cached;

        int rgb = fallbackRGB;

        try {
            Minecraft mc = Minecraft.getMinecraft();
            IResource res = mc.getResourceManager().getResource(tex);

            try (InputStream in = res.getInputStream()) {
                BufferedImage img = ImageIO.read(in);
                if (img != null) {
                    rgb = computeAvgRGB(img, fallbackRGB);
                }
            }
        } catch (Exception ignored) {
            // keep fallback
        }

        CACHE.put(tex, rgb);
        return rgb;
    }

    private static int computeAvgRGB(BufferedImage img, int fallbackRGB) {
        long rSum = 0, gSum = 0, bSum = 0;
        long count = 0;

        int w = img.getWidth();
        int h = img.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) continue; // ignore fully transparent

                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = (argb) & 0xFF;

                // (Optional) alpha-weighted averaging:
                rSum += (long) r * a;
                gSum += (long) g * a;
                bSum += (long) b * a;
                count += a;
            }
        }

        if (count <= 0) return fallbackRGB;

        int r = (int) (rSum / count);
        int g = (int) (gSum / count);
        int b = (int) (bSum / count);

        return (r << 16) | (g << 8) | b;
    }

    /**
     * If you ever reload textures and want to refresh colors.
     */
    public static void clear() {
        CACHE.clear();
    }
}
