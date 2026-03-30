package com.jubitus.birds.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;


public final class BirdRenderState {

    private BirdRenderState() {
    }

    public static void begin(Minecraft mc) {
        // Always work on the modelview stack only.
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

        GlStateManager.pushMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);

        GlStateManager.enableTexture2D();

        // Cutout-like rendering.
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);

        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);

        // Do NOT force fog on here. Let vanilla/world state decide it.
        // Forcing fog in RenderWorldLast is too risky.

        GlStateManager.disableLighting();
        GlStateManager.disableCull();

        if (mc != null && mc.entityRenderer != null) {
            mc.entityRenderer.enableLightmap();
        }

        // Make sure we leave the active texture unit where our renderer expects it.
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    }

    public static void end(Minecraft mc) {
        if (mc != null && mc.entityRenderer != null) {
            mc.entityRenderer.disableLightmap();
        }

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);

        GlStateManager.color(1f, 1f, 1f, 1f);

        // Restore conservative default-ish state without hard-resetting everything.
        GlStateManager.disableCull();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);

        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        GlStateManager.popMatrix();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}