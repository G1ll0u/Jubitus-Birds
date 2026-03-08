package com.jubitus.birds.client.gui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class GuiGlStateReset {
    private GuiGlStateReset() {}

    /** Hard reset to a safe vanilla-ish GUI state (OptiFine friendly). */
    public static void reset() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;

        if (mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(true);
            GL11.glViewport(0, 0, mc.getFramebuffer().framebufferWidth, mc.getFramebuffer().framebufferHeight);
        } else {
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        }

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glDisable(GL11.GL_NORMALIZE);

        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.depthMask(true);

        GlStateManager.disableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);

        GlStateManager.enableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );

        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);

        GlStateManager.color(1f, 1f, 1f, 1f);

        restoreGuiMatrices(mc);
    }

    /** Rebuild the same projection/modelview setup vanilla GUIs expect. */
    private static void restoreGuiMatrices(Minecraft mc) {
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();

        // Projection
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        // Same ranges vanilla uses for GUI
        GlStateManager.ortho(0.0D, (double) sw, (double) sh, 0.0D, 1000.0D, 3000.0D);

        // ModelView
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);

        // Make sure we leave the current mode as MODELVIEW (vanilla expectation)
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    }
}

