package com.jubitus.birds.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;


import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;


public final class BirdRenderSafety {

    private BirdRenderSafety() {
    }

    public static void beginWorldBirdRender(Minecraft mc) {
        hardReset(mc);

        GlStateManager.pushMatrix();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.5F);

        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);

        GlStateManager.disableCull();
        GlStateManager.disableLighting();

        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.resetColor();

        // Do not use world lightmap for birds
        if (mc != null && mc.entityRenderer != null) {
            mc.entityRenderer.disableLightmap();
        }
    }

    public static void endWorldBirdRender(Minecraft mc) {
        try {
            GlStateManager.popMatrix();
        } catch (Exception ignored) {
        }

        hardReset(mc);
    }

    public static void resetAfterEachBird(Minecraft mc) {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();

        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.5F);

        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);

        GlStateManager.disableCull();
        GlStateManager.disableLighting();

        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.resetColor();

        if (mc != null && mc.entityRenderer != null) {
            mc.entityRenderer.disableLightmap();
        }
    }

    public static void hardReset(Minecraft mc) {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();
        GlStateManager.enableTexture2D();

        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();
        GlStateManager.enableTexture2D();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.depthMask(true);

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );

        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);

        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);

        GlStateManager.disableCull();
        GlStateManager.disableLighting();

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.resetColor();

        if (mc != null && mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(false);
        }
    }
}