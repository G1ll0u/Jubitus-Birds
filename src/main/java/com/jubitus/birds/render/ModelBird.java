package com.jubitus.birds.render;

import com.jubitus.birds.client.ClientBird;
import com.jubitus.birds.species.BirdSpecies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;


public class ModelBird extends ModelBase {

    private static final BirdSpecies.SilhouetteSettings DEFAULT_SIL = new BirdSpecies.SilhouetteSettings();

    private static final double LOD_HIGH_DIST2 = 32.0 * 32.0;
    private static final double LOD_MED_DIST2 = 64.0 * 64.0;
    private static final double LOD_LOW_DIST2 = 128.0 * 128.0;
    private static final double LOD_HYST = 8.0;

    private static final byte LOD_HIGH = 0;
    private static final byte LOD_MED = 1;
    private static final byte LOD_LOW = 2;
    private static final byte LOD_ULTRA = 3;

    private static final double CORE_MAX_DIST2 = 64.0 * 64.0;
    private static final int HALF_W_BIN_MIN = 16;
    private static final int HALF_W_BIN_MAX = 32;

    @Override
    public void render(Entity entity,
                       float limbSwing,
                       float limbSwingAmount,
                       float ageInTicks,
                       float netHeadYaw,
                       float headPitch,
                       float scale) {
        if (!(entity instanceof ClientBird)) return;

        ClientBird bird = (ClientBird) entity;
        ResourceLocation tex = bird.texture;
        if (tex == null) return;

        BirdSpecies.SilhouetteSettings sil = (bird.species != null && bird.species.silhouette != null)
                ? bird.species.silhouette
                : DEFAULT_SIL;

        BirdSilhouetteCache.Shape shape = BirdSilhouetteCache.getOrBuild(tex, sil);
        if (shape == null || shape.outer == null) return;

        double dx = bird.posX - Minecraft.getMinecraft().getRenderManager().viewerPosX;
        double dy = bird.posY - Minecraft.getMinecraft().getRenderManager().viewerPosY;
        double dz = bird.posZ - Minecraft.getMinecraft().getRenderManager().viewerPosZ;
        double dist2 = dx * dx + dy * dy + dz * dz;

        byte lod = pickLodHysteresis(bird, dist2);

        LoftedBirdMesh.SilhouetteProfile outer = pickOuterProfile(tex, sil, shape, lod);
        if (outer == null) return;

        boolean includeCore = shape.core != null && lod <= LOD_MED && dist2 <= CORE_MAX_DIST2;
        LoftedBirdMesh.SilhouetteProfile core = includeCore ? pickCoreProfile(tex, sil, shape, lod) : null;

        double time = Minecraft.getMinecraft().world.getTotalWorldTime() + Minecraft.getMinecraft().getRenderPartialTicks();
        double flap = bird.getFlapAmplitude() * Math.sin((time + (bird.getId() & 255L)) * bird.getFlapSpeed());
        double halfW = 1.2 + flap;

        int widthBin = clamp((int) Math.round(halfW * 20.0), HALF_W_BIN_MIN, HALF_W_BIN_MAX);

        BirdMeshStateCache.CompiledMesh mesh = BirdMeshStateCache.getOrBuild(
                tex,
                sil,
                lod,
                widthBin,
                outer,
                core,
                shape.avgRGB,
                includeCore
        );

        mesh.draw(includeCore);
    }

    private static byte pickLodHysteresis(ClientBird bird, double dist2) {
        byte lod = (bird.renderLod < 0) ? LOD_HIGH : bird.renderLod;

        double highOut = sqr(32.0 + LOD_HYST);
        double medOut = sqr(64.0 + LOD_HYST);
        double lowOut = sqr(128.0 + LOD_HYST);

        double highIn = sqr(32.0 - LOD_HYST);
        double medIn = sqr(64.0 - LOD_HYST);
        double lowIn = sqr(128.0 - LOD_HYST);

        if (lod == LOD_HIGH && dist2 > highOut) lod = LOD_MED;
        else if (lod == LOD_MED && dist2 > medOut) lod = LOD_LOW;
        else if (lod == LOD_LOW && dist2 > lowOut) lod = LOD_ULTRA;
        else if (lod == LOD_ULTRA && dist2 < lowIn) lod = LOD_LOW;
        else if (lod == LOD_LOW && dist2 < medIn) lod = LOD_MED;
        else if (lod == LOD_MED && dist2 < highIn) lod = LOD_HIGH;

        bird.renderLod = lod;
        return lod;
    }

    private static LoftedBirdMesh.SilhouetteProfile pickOuterProfile(ResourceLocation tex,
                                                                     BirdSpecies.SilhouetteSettings sil,
                                                                     BirdSilhouetteCache.Shape shape,
                                                                     byte lodByte) {
        if (lodByte == LOD_HIGH) {
            return shape.outer;
        }
        if (lodByte == LOD_MED) {
            LoftedBirdMesh.SilhouetteProfile p = BirdSilhouetteCache.getOuterLod(tex, sil, 6);
            return p != null ? p : shape.outer;
        }
        if (lodByte == LOD_LOW) {
            LoftedBirdMesh.SilhouetteProfile p = BirdSilhouetteCache.getOuterLod(tex, sil, 5);
            return p != null ? p : shape.outer;
        }

        LoftedBirdMesh.SilhouetteProfile p = BirdSilhouetteCache.getOuterLod(tex, sil, 4);
        return p != null ? p : shape.outer;
    }

    private static LoftedBirdMesh.SilhouetteProfile pickCoreProfile(ResourceLocation tex,
                                                                    BirdSpecies.SilhouetteSettings sil,
                                                                    BirdSilhouetteCache.Shape shape,
                                                                    byte lodByte) {
        if (shape.core == null) return null;

        if (lodByte == LOD_HIGH) {
            return shape.core;
        }
        if (lodByte == LOD_MED) {
            LoftedBirdMesh.SilhouetteProfile p = BirdSilhouetteCache.getCoreLod(tex, sil, 6);
            return p != null ? p : shape.core;
        }
        if (lodByte == LOD_LOW) {
            LoftedBirdMesh.SilhouetteProfile p = BirdSilhouetteCache.getCoreLod(tex, sil, 5);
            return p != null ? p : shape.core;
        }

        LoftedBirdMesh.SilhouetteProfile p = BirdSilhouetteCache.getCoreLod(tex, sil, 4);
        return p != null ? p : shape.core;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double sqr(double v) {
        return v * v;
    }
}