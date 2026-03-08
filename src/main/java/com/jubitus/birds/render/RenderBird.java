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

public class RenderBird {

    private static final BirdSpecies.SilhouetteSettings DEFAULT_SIL = new BirdSpecies.SilhouetteSettings();
    private static final double LOD_HYST = 8.0; // blocks of “stickiness”
    // Tune these
    private static final double LOD_HIGH_DIST = 32.0;      // blocks
    private static final double LOD_MED_DIST = 64.0;
    private static final double LOD_LOW_DIST = 128.0;      // beyond this -> ULTRA
    private static final double CULL_EXTRA = 2.0;       // bbox padding
    private static final int ULTRA_RINGS = 4; // 3..6 is typical
    private static final byte LOD_HIGH = 0;
    private static final byte LOD_MED = 1;
    private static final byte LOD_LOW = 2;
    private static final byte LOD_ULTRA = 3;
    // update LOD only every N ticks per bird (cheap + avoids thrash)
    private static final int LOD_UPDATE_EVERY_TICKS = 5;

    public static void renderAll(Collection<ClientBird> birds, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || birds == null || birds.isEmpty()) return;
        if (mc.currentScreen != null) return;

        boolean fogWasEnabled = GL11.glIsEnabled(GL11.GL_FOG);

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT |
                        GL11.GL_COLOR_BUFFER_BIT |
                        GL11.GL_DEPTH_BUFFER_BIT |
                        GL11.GL_TEXTURE_BIT |
                        GL11.GL_LIGHTING_BIT |
                        GL11.GL_POLYGON_BIT |
                        GL11.GL_CURRENT_BIT
        );

        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();

        try {
            Vec3d camPos = new Vec3d(
                    mc.getRenderManager().viewerPosX,
                    mc.getRenderManager().viewerPosY,
                    mc.getRenderManager().viewerPosZ
            );

            Frustum frustum = new Frustum();
            frustum.setPosition(camPos.x, camPos.y, camPos.z);

            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(true);
            GlStateManager.colorMask(true, true, true, true);

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
            );

            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);

            GlStateManager.disableLighting();
            GlStateManager.disableCull();

            if (fogWasEnabled) {
                GlStateManager.enableFog();
            } else {
                GlStateManager.disableFog();
            }

            mc.entityRenderer.enableLightmap();

            for (ClientBird b : birds) {
                Vec3d p0 = (b.prevPos != null) ? b.prevPos : b.pos;
                Vec3d p1 = b.pos;
                double ix = p0.x + (p1.x - p0.x) * partialTicks;
                double iy = p0.y + (p1.y - p0.y) * partialTicks;
                double iz = p0.z + (p1.z - p0.z) * partialTicks;

                float a = fogFadeAlpha(mc, camPos, ix, iy, iz);
                if (a <= 0.01f) continue;

                double dx = ix - camPos.x;
                double dy = iy - camPos.y;
                double dz = iz - camPos.z;
                double d2 = dx * dx + dy * dy + dz * dz;
                double dist = Math.sqrt(d2);

                byte lodByte = pickLodHysteresis(b, dist);
                LOD lod = (lodByte == LOD_HIGH) ? LOD.HIGH
                        : (lodByte == LOD_MED) ? LOD.MED
                        : (lodByte == LOD_LOW) ? LOD.LOW
                        : LOD.ULTRA;

                double scale = (b.species != null) ? b.species.scale : 0.45;
                double r = scale * 1.6 + CULL_EXTRA;

                AxisAlignedBB bb = new AxisAlignedBB(
                        ix - r, iy - r, iz - r,
                        ix + r, iy + r, iz + r
                );

                if (!frustum.isBoundingBoxInFrustum(bb)) continue;

                renderOneLOD(b, camPos, partialTicks, lod, a);

                OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
                GlStateManager.colorMask(true, true, true, true);
                GlStateManager.depthMask(true);
                GlStateManager.color(1f, 1f, 1f, 1f);
            }

        } finally {
            mc.entityRenderer.disableLightmap();

            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);

            GL11.glPopAttrib();

            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.resetColor();
        }
    }

    private static float fogFadeAlpha(Minecraft mc, Vec3d camPos, double wx, double wy, double wz) {
        // distance from camera to bird
        double dx = wx - camPos.x;
        double dy = wy - camPos.y;
        double dz = wz - camPos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Approximate vanilla-ish fog range from render distance
        double fogEnd = mc.gameSettings.renderDistanceChunks * 16.0;

        boolean underwater = mc.player != null &&
                mc.player.isInsideOfMaterial(net.minecraft.block.material.Material.WATER);

        // Underwater: much shorter visibility
        double fogStart = underwater ? fogEnd * 0.10 : fogEnd * 0.65;
        fogEnd = underwater ? fogEnd * 0.35 : fogEnd;

        // Convert to [0..1] alpha
        double a = (fogEnd - dist) / (fogEnd - fogStart);
        if (a < 0) a = 0;
        if (a > 1) a = 1;

        // Underwater: additionally reduce max alpha a bit (looks like water haze)
        if (underwater) a *= 0.7;

        return (float) a;
    }

    private static byte pickLodHysteresis(ClientBird b, double dist) {
        // only update sometimes (prevents constant flipping)
        if ((b.ageTicks % LOD_UPDATE_EVERY_TICKS) != 0 && b.renderLod >= 0) {
            return b.renderLod;
        }

        byte lod = (b.renderLod < 0) ? LOD_HIGH : b.renderLod;

        // Hysteresis boundaries:
        // - To go UP in detail (closer): use threshold - HYST
        // - To go DOWN in detail (farther): use threshold + HYST

        // Going farther (reduce detail)
        if (lod == LOD_HIGH && dist > (LOD_HIGH_DIST + LOD_HYST)) lod = LOD_MED;
        else if (lod == LOD_MED && dist > (LOD_MED_DIST + LOD_HYST)) lod = LOD_LOW;
        else if (lod == LOD_LOW && dist > (LOD_LOW_DIST + LOD_HYST)) lod = LOD_ULTRA;

            // Going closer (increase detail)
        else if (lod == LOD_ULTRA && dist < (LOD_LOW_DIST - LOD_HYST)) lod = LOD_LOW;
        else if (lod == LOD_LOW && dist < (LOD_MED_DIST - LOD_HYST)) lod = LOD_MED;
        else if (lod == LOD_MED && dist < (LOD_HIGH_DIST - LOD_HYST)) lod = LOD_HIGH;

        b.renderLod = lod;
        return lod;
    }

    private static void renderOneLOD(ClientBird b, Vec3d camPos, float partialTicks, LOD lod, float fogAlpha) {
        Minecraft mc = Minecraft.getMinecraft();

        // --- Interpolate position between ticks ---
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

        // silhouette cache
        BirdSpecies.SilhouetteSettings sil =
                (b.species != null && b.species.silhouette != null)
                        ? b.species.silhouette
                        : DEFAULT_SIL;


        BirdSilhouetteCache.Shape shape =
                BirdSilhouetteCache.getOrBuild(tex, sil);

        if (shape == null || shape.outer == null) return;

        mc.getTextureManager().bindTexture(tex);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        double scale = b.getScale();
        GlStateManager.scale(scale, scale, scale);


        // ✅ Lightmap every time (this fixes black blinking)
        int bx = (int) Math.floor(ix);
        int by = (int) Math.floor(iy);
        int bz = (int) Math.floor(iz);
        int packedLight = mc.world.getCombinedLight(new BlockPos(bx, by, bz), 0);
        int lu = packedLight & 0xFFFF;
        int lv = (packedLight >> 16) & 0xFFFF;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) lu, (float) lv);

        // ✅ Apply yaw/pitch/roll (this fixes facing direction)
        float yaw = lerpAngle(b.prevYaw, b.orientation.yawDeg, partialTicks);
        float pitch = lerpAngle(b.prevPitch, b.orientation.pitchDeg, partialTicks);
        float roll = lerpAngle(b.prevRoll, b.orientation.rollDeg, partialTicks);

        GlStateManager.rotate(yaw, 0, 1, 0);
        GlStateManager.rotate(pitch, 1, 0, 0);
        GlStateManager.rotate(roll, 0, 0, 1);

        // wing flap (cheap)
        double time = mc.world.getTotalWorldTime() + partialTicks;
        double amp = b.getFlapAmplitude();
        double spd = b.getFlapSpeed();
        double flap = amp * Math.sin((time + (b.hashCode() & 255)) * spd);

        double halfW = 1.2 + flap;
        double halfL = 0.7;
        double baseThickness = 0.22;
        double capOffset = 0.06;

        // apply fog fade alpha for this bird
        GlStateManager.color(1f, 1f, 1f, fogAlpha);

        // -----------------------------
        // LOD draw choices (only draw passes change)
        // -----------------------------

        if (lod == LOD.ULTRA) {
            // Far but still 3D: minimum silhouette, single pass, no core, no prepass
            GlStateManager.disableCull();
            GlStateManager.enableBlend();

            GlStateManager.enableAlpha();
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.10f);

            com.jubitus.birds.render.LoftedBirdMesh.SilhouetteProfile pUltra =
                    BirdSilhouetteCache.getOuterLod(tex, sil, ULTRA_RINGS);


            if (pUltra != null) {
                com.jubitus.birds.render.LoftedBirdMesh.drawSilhouetteTextured(
                        halfW, halfL, baseThickness,
                        pUltra, capOffset
                );
            }

            GlStateManager.disableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.resetColor();
            GlStateManager.popMatrix();
            return;
        }


        if (lod == LOD.LOW) {
            // 1 pass textured only
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.10f);

            com.jubitus.birds.render.LoftedBirdMesh.drawSilhouetteTextured(
                    halfW, halfL, baseThickness,
                    shape.outer, capOffset
            );

            GlStateManager.disableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.resetColor();
            GlStateManager.popMatrix();
            return;
        }

        if (lod == LOD.MED) {
            // depth prepass + 1 blended draw (no core, no double-sided)
            GlStateManager.enableAlpha();
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.10f);

            // prepass (depth only)
            GlStateManager.disableBlend();
            GL11.glColorMask(false, false, false, false);
            GlStateManager.disableCull();

            com.jubitus.birds.render.LoftedBirdMesh.drawSilhouetteTextured(
                    halfW, halfL, baseThickness,
                    shape.outer, capOffset
            );

            // real draw
            GL11.glColorMask(true, true, true, true);
            GlStateManager.enableBlend();
            GlStateManager.disableCull();
            GlStateManager.color(1f, 1f, 1f, fogAlpha);

            com.jubitus.birds.render.LoftedBirdMesh.drawSilhouetteTextured(
                    halfW, halfL, baseThickness,
                    shape.outer, capOffset
            );

            GlStateManager.disableAlpha();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.resetColor();
            GlStateManager.popMatrix();
            return;
        }

        // HIGH quality:
        // core + depth prepass + double sided outer
        if (shape.core != null) {
            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
            GlStateManager.disableCull();

            GlStateManager.disableTexture2D();
            com.jubitus.birds.render.LoftedBirdMesh.drawSilhouetteColor(
                    halfW, halfL, baseThickness,
                    shape.core, capOffset * 0.9,
                    shape.avgRGB, 1.0f
            );
            GlStateManager.enableTexture2D();
        }

        GlStateManager.enableAlpha();
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.10f);

        // depth prepass
        GlStateManager.disableBlend();
        GL11.glColorMask(false, false, false, false);
        GlStateManager.disableCull();

        com.jubitus.birds.render.LoftedBirdMesh.drawSilhouetteTextured(
                halfW, halfL, baseThickness,
                shape.outer, capOffset
        );

        // outer draw front + back
        GL11.glColorMask(true, true, true, true);
        GlStateManager.enableBlend();
        GlStateManager.enableCull();

        GlStateManager.cullFace(GlStateManager.CullFace.BACK);
        com.jubitus.birds.render.LoftedBirdMesh.drawSilhouetteTextured(
                halfW, halfL, baseThickness,
                shape.outer, capOffset
        );

        GlStateManager.cullFace(GlStateManager.CullFace.FRONT);
        com.jubitus.birds.render.LoftedBirdMesh.drawSilhouetteTextured(
                halfW, halfL, baseThickness,
                shape.outer, capOffset
        );

        // restore
        GlStateManager.cullFace(GlStateManager.CullFace.BACK);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();

        GlStateManager.popMatrix();
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

    /**
     * Copy of your old tilt logic, simplified. Remove if you don't want it.
     */
    private static void applyCameraTilt(Vec3d camPos, double ix, double iy, double iz) {
        double cx = camPos.x - ix;
        double cy = camPos.y - iy;
        double cz = camPos.z - iz;

        double dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (dist < 1e-6) return;

        double invLen = 1.0 / dist;
        double dxN = cx * invLen;
        double dyN = cy * invLen;
        double dzN = cz * invLen;

        float distFactor = (float) clamp01((dist - 12.0) / 64.0);
        float horizonFactor = 1.0f - (float) Math.min(1.0, Math.abs(dyN));
        float tiltStrength = distFactor * horizonFactor;

        float maxTiltDeg = 25.0f;

        float tiltX = (float) (dzN * maxTiltDeg * tiltStrength);
        float tiltZ = (float) (-dxN * maxTiltDeg * tiltStrength);

        GlStateManager.rotate(tiltZ, 0f, 0f, 1f);
        GlStateManager.rotate(tiltX, 1f, 0f, 0f);
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static void drawBillboardQuad(double halfW, double halfL) {
        double zHead = +halfL;
        double zTail = -halfL;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        buf.pos(-halfW, 0, zHead).tex(0, 0).endVertex();
        buf.pos(+halfW, 0, zHead).tex(1, 0).endVertex();
        buf.pos(+halfW, 0, zTail).tex(1, 1).endVertex();
        buf.pos(-halfW, 0, zTail).tex(0, 1).endVertex();

        tess.draw();
    }

    private enum LOD {HIGH, MED, LOW, ULTRA}

}
