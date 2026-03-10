package com.jubitus.birds.render;

import com.jubitus.birds.client.ClientBird;
import com.jubitus.birds.species.BirdSpecies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.Collection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.Collection;

public class RenderBird {

    private static final BirdSpecies.SilhouetteSettings DEFAULT_SIL = new BirdSpecies.SilhouetteSettings();

    private static final double LOD_HYST = 8.0;
    private static final double LOD_HIGH_DIST = 32.0;
    private static final double LOD_MED_DIST = 64.0;
    private static final double LOD_LOW_DIST = 128.0;
    private static final double CULL_EXTRA = 2.0;

    private static final int LOD_HIGH_RINGS = 7;
    private static final int LOD_MED_RINGS = 6;
    private static final int LOD_LOW_RINGS = 5;
    private static final int LOD_ULTRA_RINGS = 4;

    private static final byte LOD_HIGH = 0;
    private static final byte LOD_MED = 1;
    private static final byte LOD_LOW = 2;
    private static final byte LOD_ULTRA = 3;

    private static final int LOD_UPDATE_EVERY_TICKS = 5;

    public static void renderAll(Collection<ClientBird> birds, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || birds == null || birds.isEmpty()) return;
        if (mc.currentScreen != null) return;

        Vec3d camPos = new Vec3d(
                mc.getRenderManager().viewerPosX,
                mc.getRenderManager().viewerPosY,
                mc.getRenderManager().viewerPosZ
        );

        Frustum frustum = new Frustum();
        frustum.setPosition(camPos.x, camPos.y, camPos.z);

        BirdRenderSafety.beginWorldBirdRender(mc);

        try {
            for (ClientBird b : birds) {
                if (b == null || b.pos == null) continue;

                try {
                    Vec3d p0 = (b.prevPos != null) ? b.prevPos : b.pos;
                    Vec3d p1 = b.pos;

                    double ix = p0.x + (p1.x - p0.x) * partialTicks;
                    double iy = p0.y + (p1.y - p0.y) * partialTicks;
                    double iz = p0.z + (p1.z - p0.z) * partialTicks;

                    float alpha = fogFadeAlpha(mc, camPos, ix, iy, iz);
                    if (alpha <= 0.01f) continue;

                    double dx = ix - camPos.x;
                    double dy = iy - camPos.y;
                    double dz = iz - camPos.z;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    byte lodByte = pickLodHysteresis(b, dist);

                    double scale = b.getScale();
                    double r = Math.max(1.5, scale * 1.8 + CULL_EXTRA);

                    AxisAlignedBB bb = new AxisAlignedBB(
                            ix - r, iy - r, iz - r,
                            ix + r, iy + r, iz + r
                    );

                    if (!frustum.isBoundingBoxInFrustum(bb)) continue;

                    renderOneSafe(b, camPos, partialTicks, lodByte, alpha);
                    BirdRenderSafety.resetAfterEachBird(mc);

                } catch (Throwable ignored) {
                    BirdRenderSafety.resetAfterEachBird(mc);
                }
            }
        } finally {
            BirdRenderSafety.endWorldBirdRender(mc);
        }
    }

    private static void renderOneSafe(ClientBird b, Vec3d camPos, float partialTicks, byte lodByte, float fogAlpha) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) return;

        Vec3d p0 = (b.prevPos != null) ? b.prevPos : b.pos;
        Vec3d p1 = b.pos;

        double ix = p0.x + (p1.x - p0.x) * partialTicks;
        double iy = p0.y + (p1.y - p0.y) * partialTicks;
        double iz = p0.z + (p1.z - p0.z) * partialTicks;

        double x = ix - camPos.x;
        double y = iy - camPos.y;
        double z = iz - camPos.z;

        ResourceLocation tex = BirdTexture.get(b);
        if (tex == null) return;

        BirdSpecies.SilhouetteSettings sil =
                (b.species != null && b.species.silhouette != null)
                        ? b.species.silhouette
                        : DEFAULT_SIL;

        BirdSilhouetteCache.Shape shape = BirdSilhouetteCache.getOrBuild(tex, sil);
        if (shape == null || shape.outer == null) return;

        LoftedBirdMesh.SilhouetteProfile outerProfile = pickOuterProfile(tex, sil, shape, lodByte);
        if (outerProfile == null) return;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);

            double scale = b.getScale();
            GlStateManager.scale(scale, scale, scale);

            float yaw = lerpAngle(b.prevYaw, b.orientation.yawDeg, partialTicks);
            float pitch = lerpAngle(b.prevPitch, b.orientation.pitchDeg, partialTicks);
            float roll = lerpAngle(b.prevRoll, b.orientation.rollDeg, partialTicks);

            GlStateManager.rotate(yaw, 0f, 1f, 0f);
            GlStateManager.rotate(pitch, 1f, 0f, 0f);
            GlStateManager.rotate(roll, 0f, 0f, 1f);

            int bx = (int) Math.floor(ix);
            int by = (int) Math.floor(iy);
            int bz = (int) Math.floor(iz);
            int packedLight = mc.world.getCombinedLight(new BlockPos(bx, by, bz), 0);
            int lu = packedLight & 0xFFFF;
            int lv = (packedLight >> 16) & 0xFFFF;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) lu, (float) lv);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

            mc.getTextureManager().bindTexture(tex);

            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);

            // Important: do not poison depth for hand/item rendering
            GlStateManager.depthMask(false);

            GlStateManager.disableCull();
            GlStateManager.disableLighting();

            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.color(1f, 1f, 1f, fogAlpha);

            double time = mc.world.getTotalWorldTime() + partialTicks;
            double flap = b.getFlapAmplitude() * Math.sin((time + (b.getId() & 255L)) * b.getFlapSpeed());

            double halfW = 1.2 + flap;
            double halfL = 0.7;
            double baseThickness = 0.22;
            double capOffset = 0.06;

            // Safe optional solid core only on high LOD
            if (lodByte == LOD_HIGH && shape.core != null) {
                GlStateManager.disableTexture2D();
                LoftedBirdMesh.drawSilhouetteColor(
                        halfW, halfL, baseThickness,
                        shape.core, capOffset * 0.9,
                        shape.avgRGB, fogAlpha
                );
                GlStateManager.enableTexture2D();
                mc.getTextureManager().bindTexture(tex);
                GlStateManager.color(1f, 1f, 1f, fogAlpha);
            }

            LoftedBirdMesh.drawSilhouetteTextured(
                    halfW, halfL, baseThickness,
                    outerProfile, capOffset
            );

        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static LoftedBirdMesh.SilhouetteProfile pickOuterProfile(ResourceLocation tex,
                                                                     BirdSpecies.SilhouetteSettings sil,
                                                                     BirdSilhouetteCache.Shape shape,
                                                                     byte lodByte) {
        if (lodByte == LOD_HIGH) {
            return shape.outer;
        }
        if (lodByte == LOD_MED) {
            return BirdSilhouetteCache.getOuterLod(tex, sil, LOD_MED_RINGS);
        }
        if (lodByte == LOD_LOW) {
            return BirdSilhouetteCache.getOuterLod(tex, sil, LOD_LOW_RINGS);
        }
        return BirdSilhouetteCache.getOuterLod(tex, sil, LOD_ULTRA_RINGS);
    }

    private static float fogFadeAlpha(Minecraft mc, Vec3d camPos, double wx, double wy, double wz) {
        double dx = wx - camPos.x;
        double dy = wy - camPos.y;
        double dz = wz - camPos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double fogEnd = mc.gameSettings.renderDistanceChunks * 16.0;

        boolean underwater = mc.player != null &&
                mc.player.isInsideOfMaterial(net.minecraft.block.material.Material.WATER);

        double fogStart = underwater ? fogEnd * 0.10 : fogEnd * 0.65;
        fogEnd = underwater ? fogEnd * 0.35 : fogEnd;

        double denom = (fogEnd - fogStart);
        if (denom <= 0.0001) return 1.0f;

        double a = (fogEnd - dist) / denom;
        if (a < 0.0) a = 0.0;
        if (a > 1.0) a = 1.0;

        if (underwater) a *= 0.7;

        return (float) a;
    }

    private static byte pickLodHysteresis(ClientBird b, double dist) {
        if ((b.ageTicks % LOD_UPDATE_EVERY_TICKS) != 0 && b.renderLod >= 0) {
            return b.renderLod;
        }

        byte lod = (b.renderLod < 0) ? LOD_HIGH : b.renderLod;

        if (lod == LOD_HIGH && dist > (LOD_HIGH_DIST + LOD_HYST)) lod = LOD_MED;
        else if (lod == LOD_MED && dist > (LOD_MED_DIST + LOD_HYST)) lod = LOD_LOW;
        else if (lod == LOD_LOW && dist > (LOD_LOW_DIST + LOD_HYST)) lod = LOD_ULTRA;
        else if (lod == LOD_ULTRA && dist < (LOD_LOW_DIST - LOD_HYST)) lod = LOD_LOW;
        else if (lod == LOD_LOW && dist < (LOD_MED_DIST - LOD_HYST)) lod = LOD_MED;
        else if (lod == LOD_MED && dist < (LOD_HIGH_DIST - LOD_HYST)) lod = LOD_HIGH;

        b.renderLod = lod;
        return lod;
    }

    private static float lerpAngle(float a, float b, float t) {
        float delta = wrapDegrees(b - a);
        return a + delta * t;
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360.0f;
        if (deg >= 180.0f) deg -= 360.0f;
        if (deg < -180.0f) deg += 360.0f;
        return deg;
    }
}