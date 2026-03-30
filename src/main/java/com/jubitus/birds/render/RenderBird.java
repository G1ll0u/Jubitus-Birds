package com.jubitus.birds.render;

import com.jubitus.birds.client.ClientBird;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;


public class RenderBird extends RenderLiving<ClientBird> {

    public RenderBird(RenderManager renderManager) {
        super(renderManager, createModel(), 0.0F);
    }

    private static ModelBase createModel() {
        return new ModelBird();
    }

    @Override
    protected ResourceLocation getEntityTexture(ClientBird entity) {
        return entity != null ? entity.texture : null;
    }

    @Override
    protected void renderModel(ClientBird entity,
                               float limbSwing,
                               float limbSwingAmount,
                               float ageInTicks,
                               float netHeadYaw,
                               float headPitch,
                               float scaleFactor) {
        GlStateManager.disableLighting();
        try {
            super.renderModel(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor);
        } finally {
            GlStateManager.enableLighting();
        }
    }

    @Override
    protected void applyRotations(ClientBird entity, float ageInTicks, float rotationYaw, float partialTicks) {
        float yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks;
        float pitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;
        float roll = entity.getRoll(partialTicks);

        GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(roll, 0.0F, 0.0F, 1.0F);
    }

    @Override
    protected float handleRotationFloat(ClientBird livingBase, float partialTicks) {
        return 0.0F;
    }

    @Override
    protected void preRenderCallback(ClientBird entity, float partialTickTime) {
        double scale = entity.getScale();
        GlStateManager.scale(scale, scale, scale);
    }

    @Override
    protected boolean canRenderName(ClientBird entity) {
        return false;
    }
}