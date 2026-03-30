package com.jubitus.birds.render;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public final class BirdLightHelper {

    private BirdLightHelper() {
    }

    public static void applyLightmap(World world, double x, double y, double z) {
        if (world == null) {
            setPackedLight(0x00F000F0); // reasonable fallback / bright default
            return;
        }

        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y);
        int bz = MathHelper.floor(z);

        if (by < 0) by = 0;
        if (by > 255) by = 255;

        BlockPos pos = new BlockPos(bx, by, bz);
        int packed = world.getCombinedLight(pos, 0);
        setPackedLight(packed);
    }

    private static void setPackedLight(int packedLight) {
        int low = packedLight & 0xFFFF;
        int high = (packedLight >> 16) & 0xFFFF;

        OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit,
                (float) low,
                (float) high
        );

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }
}