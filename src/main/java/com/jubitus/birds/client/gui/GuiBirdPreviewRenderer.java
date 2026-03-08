package com.jubitus.birds.client.gui;

import com.jubitus.birds.render.BirdSilhouetteCache;
import com.jubitus.birds.render.LoftedBirdMesh;
import com.jubitus.birds.species.BirdSpecies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;

public final class GuiBirdPreviewRenderer {

    private GuiBirdPreviewRenderer() {}

    public static boolean drawSilhouetteTurntable(ResourceLocation tex,
                                                  BirdSpecies.SilhouetteSettings sil,
                                                  int centerX, int centerY,
                                                  int sizePx,
                                                  float yawDeg) {
        if (tex == null) return false;
        if (sil == null) sil = new BirdSpecies.SilhouetteSettings();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return false;

        BirdSilhouetteCache.Shape shape = BirdSilhouetteCache.getOrBuild(tex, sil);
        if (shape == null || shape.outer == null) return false;

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT |
                        GL11.GL_COLOR_BUFFER_BIT |
                        GL11.GL_DEPTH_BUFFER_BIT |
                        GL11.GL_TEXTURE_BIT |
                        GL11.GL_LIGHTING_BIT |
                        GL11.GL_POLYGON_BIT |
                        GL11.GL_CURRENT_BIT
        );

        GlStateManager.pushMatrix();

        try {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
            );

            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.disableCull();

            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(true);

            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.color(1f, 1f, 1f, 1f);

            mc.getTextureManager().bindTexture(tex);
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

            GlStateManager.translate((float) centerX, (float) centerY, 250.0F);

            float s = sizePx / 2.0F;
            GlStateManager.scale(s, s, s);

            GlStateManager.rotate(25.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(yawDeg, 0.0F, 1.0F, 0.0F);

            double halfW = 1.2;
            double halfL = 0.7;
            double baseThickness = 0.22;
            double capOffset = 0.06;

            if (shape.core != null) {
                GlStateManager.disableTexture2D();
                LoftedBirdMesh.drawSilhouetteColor(
                        halfW, halfL, baseThickness,
                        shape.core,
                        capOffset * 0.9,
                        shape.avgRGB,
                        1.0f
                );
                GlStateManager.enableTexture2D();
                GlStateManager.color(1f, 1f, 1f, 1f);
            }

            GlStateManager.enableBlend();
            GlStateManager.enableCull();

            GlStateManager.cullFace(GlStateManager.CullFace.BACK);
            LoftedBirdMesh.drawSilhouetteTextured(halfW, halfL, baseThickness, shape.outer, capOffset);

            GlStateManager.cullFace(GlStateManager.CullFace.FRONT);
            LoftedBirdMesh.drawSilhouetteTextured(halfW, halfL, baseThickness, shape.outer, capOffset);

            GlStateManager.cullFace(GlStateManager.CullFace.BACK);
            GlStateManager.disableCull();

            return true;

        } finally {
            GlStateManager.popMatrix();
            GL11.glPopAttrib();

            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GuiGlStateReset.reset();
        }
    }


}
