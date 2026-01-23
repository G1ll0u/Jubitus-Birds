package com.jubitus.birds.render;

import com.jubitus.birds.client.ClientBird;
import com.jubitus.birds.client.util.BirdOrientation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.Collection;

public class RenderBird {

    public static void renderAll(Collection<ClientBird> birds, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3d camPos = new Vec3d(
                mc.getRenderManager().viewerPosX,
                mc.getRenderManager().viewerPosY,
                mc.getRenderManager().viewerPosZ
        );

        // ✅ Save current fog enabled state so we don't break the world renderer
        boolean fogWasEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_FOG);

        GlStateManager.pushMatrix();

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        GlStateManager.disableCull();

        GlStateManager.disableLighting();
        mc.entityRenderer.enableLightmap();

        // ✅ Only enable fog for our draw, then restore
        GlStateManager.enableFog();

        for (ClientBird b : birds) {
            renderOne(b, camPos, partialTicks);
        }

        // ✅ Restore fog exactly as it was
        if (!fogWasEnabled) GlStateManager.disableFog();

        mc.entityRenderer.disableLightmap();

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }



    private static void renderOne(ClientBird b, Vec3d camPos, float partialTicks) {
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

        ResourceLocation tex = BirdTexture.get(b.textureIndex);
        mc.getTextureManager().bindTexture(tex);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);



        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        double scale = 0.45;
        GlStateManager.scale(scale, scale, scale);

        // ✅ Apply lightmap brightness based on the bird's world position
        int bx = (int)Math.floor(ix);
        int by = (int)Math.floor(iy);
        int bz = (int)Math.floor(iz);


        int packedLight = mc.world.getCombinedLight(new BlockPos(bx, by, bz), 0);

// Lightmap coords are split into 2 shorts
        int u = packedLight & 0xFFFF;
        int v = (packedLight >> 16) & 0xFFFF;

// Feed lightmap (works in 1.12.x)
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float)u, (float)v);


        // --- Interpolate angles too (prevents rotation stepping) ---
        float yaw   = lerpAngle(b.prevYaw,   b.orientation.yawDeg,   partialTicks);
        float pitch = lerpAngle(b.prevPitch, b.orientation.pitchDeg, partialTicks);
        float roll  = lerpAngle(b.prevRoll,  b.orientation.rollDeg,  partialTicks);



        // IMPORTANT:
        // Our "paper plane" quad will be in the XZ plane (flat).
        // We define: forward = +Z (this corresponds to the TOP of your PNG = head)
        // So yaw rotates around Y, pitch tilts around X (nose up/down), roll banks around Z.
        GlStateManager.rotate(yaw, 0, 1, 0);
        GlStateManager.rotate(pitch, 1, 0, 0);
        GlStateManager.rotate(roll, 0, 0, 1);

        // Small “flap” / wing wobble: vary width slightly
        // (optional improvement: include partialTicks in time)
        double t = mc.world.getTotalWorldTime() + partialTicks;
        double flap = 0.08 * Math.sin((t + (b.hashCode() & 255)) * 0.35);

        double halfW = 1.2 + flap; // wings (left-right, X)
        double halfL = 0.7;        // length (tail->head, Z). Increase if bird looks too square.

        // Quad in XZ plane at y=0
        // Texture mapping:
        // - v=0 at "head" (forward +Z)
        // - v=1 at "tail" (back -Z)
        // So TOP of PNG points forward (+Z).
        double zHead = +halfL;
        double zTail = -halfL;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        float a = fogFadeAlpha(mc, camPos, ix, iy, iz);
        if (a <= 0.01f) { // optional micro-optimization: skip drawing when invisible
            GlStateManager.popMatrix();
            return;
        }
        GlStateManager.color(1f, 1f, 1f, a);


        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        // Left-Head (top-left of PNG)
        buf.pos(-halfW, 0, zHead).tex(0, 0).endVertex();
        // Right-Head (top-right)
        buf.pos(+halfW, 0, zHead).tex(1, 0).endVertex();
        // Right-Tail (bottom-right)
        buf.pos(+halfW, 0, zTail).tex(1, 1).endVertex();
        // Left-Tail (bottom-left)
        buf.pos(-halfW, 0, zTail).tex(0, 1).endVertex();

        tess.draw();

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
    private static float fogFadeAlpha(Minecraft mc, Vec3d camPos, double wx, double wy, double wz) {
        // distance from camera to bird
        double dx = wx - camPos.x;
        double dy = wy - camPos.y;
        double dz = wz - camPos.z;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);

        // Approximate vanilla-ish fog range from render distance
        double fogEnd = mc.gameSettings.renderDistanceChunks * 16.0;

        boolean underwater = mc.player != null &&
                mc.player.isInsideOfMaterial(net.minecraft.block.material.Material.WATER);

        // Underwater: much shorter visibility
        double fogStart = underwater ? fogEnd * 0.10 : fogEnd * 0.65;
        fogEnd = underwater ? fogEnd * 0.35 : fogEnd * 1.00;

        // Convert to [0..1] alpha
        double a = (fogEnd - dist) / (fogEnd - fogStart);
        if (a < 0) a = 0;
        if (a > 1) a = 1;

        // Underwater: additionally reduce max alpha a bit (looks like water haze)
        if (underwater) a *= 0.7;

        return (float) a;
    }

}
