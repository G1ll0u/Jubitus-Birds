package com.jubitus.birds.client.gui;

import com.jubitus.birds.species.BirdSpecies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class SpeciesSlotList extends GuiSlot {

    // padding inside preview column
    private static final int PREVIEW_FRAME_PAD = 2; // around sprite inside the frame
    private static final int PREVIEW_PAD = 2;       // extra hover hitbox padding
    private static final int TEXT_PAD_L = 6;

    private final GuiSpeciesListScreen owner;

    public SpeciesSlotList(GuiSpeciesListScreen owner, Minecraft mc,
                           int width, int height, int topIn, int bottomIn, int slotHeightIn) {
        super(mc, width, height, topIn, bottomIn, slotHeightIn);
        this.owner = owner;

        // ✅ make the list span almost full screen with a margin
        this.left = 10;
        this.right = width - 10;
    }


    @Override
    protected int getSize() {
        return owner.getSpecies().size();
    }

    @Override
    protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        if (index < 0 || index >= owner.getSpecies().size()) return;

        int rowY = this.top + 4 - (int) this.amountScrolled + index * this.slotHeight;

        // if click inside preview column area => open editor
        if (isMouseOverPreview(rowY, this.slotHeight, mouseX, mouseY)) {
            owner.openEditor(owner.getSpecies().get(index));
            return;
        }

        // otherwise normal click
        owner.openEditor(owner.getSpecies().get(index));
    }

    @Override
    protected boolean isSelected(int index) {
        return false;
    }

    @Override
    protected void drawBackground() {
    }

    @Override
    protected void drawSlot(int index, int x, int y, int heightIn, int mouseXIn, int mouseYIn, float partialTicks) {
        BirdSpecies s = owner.getSpecies().get(index);
        String name = (s.name != null) ? s.name : "(unnamed)";
        String folder = (s.folderName != null) ? s.folderName : "?";

        int textX = x + TEXT_PAD_L;

        // --- layout: [TEXT ....][ PREVIEW ][ SCROLLBAR ] ---
        int scrollbarX = this.getScrollBarX();

        int gapTextToPreview = 6;
        int gapPreviewToScrollbar = 48;
        int minTextW = 90;

        int previewFrameRight = scrollbarX - gapPreviewToScrollbar;

        int desiredSize = heightIn - 4;
        int maxSizeByWidth = (previewFrameRight - gapTextToPreview) - (textX + minTextW) - 2 * PREVIEW_FRAME_PAD;
        int size = Math.min(desiredSize, maxSizeByWidth);

        boolean canPreview = size >= 8;

        int previewFrameLeft;
        if (canPreview) {
            previewFrameLeft = previewFrameRight - (size + 2 * PREVIEW_FRAME_PAD);
        } else {
            // no room => no preview
            previewFrameLeft = previewFrameRight;
        }

        // ✅ text stops BEFORE preview (so never overlaps)
        int textRightLimit = canPreview ? (previewFrameLeft - gapTextToPreview) : (scrollbarX - 4);
        int maxTextW = Math.max(30, textRightLimit - textX);

        String nameTrim = this.mc.fontRenderer.trimStringToWidth(name, maxTextW);
        String folderTrim = this.mc.fontRenderer.trimStringToWidth("folder: " + folder, maxTextW);

        owner.drawString(this.mc.fontRenderer, nameTrim, textX, y + 2, 0xFFFFFF);
        owner.drawString(this.mc.fontRenderer, folderTrim, textX, y + 12, 0xAAAAAA);

        // --- preview (LEFT of scrollbar) ---
        if (canPreview) {
            ResourceLocation tex = owner.pickPreviewTexture(s);
            if (tex != null) {
                int cy = y + (heightIn / 2);
                int cx = (previewFrameLeft + previewFrameRight) / 2;

                int x0 = previewFrameLeft;
                int y0 = cy - (size / 2) - PREVIEW_FRAME_PAD;
                int x1 = previewFrameRight;
                int y1 = cy + (size / 2) + PREVIEW_FRAME_PAD;

                int sky = owner.getDaySkyARGB();
                boolean hover = isMouseOverPreview(y, heightIn, mouseXIn, mouseYIn);
                int border = hover ? 0xFFFFFFFF : 0xFF000000;

                owner.drawPreviewFrame(x0, y0, x1, y1, sky, border);

                long ms = Minecraft.getSystemTime();
                float yaw = (ms % 6000L) * (360.0f / 6000.0f);

                BirdSpecies.SilhouetteSettings sil =
                        (s != null && s.silhouette != null)
                                ? s.silhouette
                                : new BirdSpecies.SilhouetteSettings();

                com.jubitus.birds.client.gui.GuiBirdPreviewRenderer.drawSilhouetteTurntable(
                        tex, sil, cx, cy, size, yaw
                );

                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.colorMask(true, true, true, true);
                GlStateManager.color(1f, 1f, 1f, 1f);
            }
        }
    }

    @Override
    public int getListWidth() {
        return this.right - this.left;
    }

    @Override
    protected int getScrollBarX() {
        return this.right - 6; // ✅ scrollbar always at the right edge of the list
    }

    private boolean isMouseOverPreview(int rowY, int heightIn, int mouseX, int mouseY) {
        int scrollbarX = this.getScrollBarX();

        int gapTextToPreview = 6;
        int gapPreviewToScrollbar = 48;
        int minTextW = 90;

        int textX = this.left + 2 + TEXT_PAD_L; // approximate like drawSlot's x + pad

        int previewFrameRight = scrollbarX - gapPreviewToScrollbar;

        int desiredSize = heightIn - 4;
        int maxSizeByWidth = (previewFrameRight - gapTextToPreview) - (textX + minTextW) - 2 * PREVIEW_FRAME_PAD;
        int size = Math.min(desiredSize, maxSizeByWidth);

        if (size < 8) return false;

        int previewFrameLeft = previewFrameRight - (size + 2 * PREVIEW_FRAME_PAD);

        int cy = rowY + (heightIn / 2);
        int cx = (previewFrameLeft + previewFrameRight) / 2;

        int x0 = cx - (size / 2) - PREVIEW_FRAME_PAD - PREVIEW_PAD;
        int y0 = cy - (size / 2) - PREVIEW_FRAME_PAD - PREVIEW_PAD;
        int x1 = cx + (size / 2) + PREVIEW_FRAME_PAD + PREVIEW_PAD;
        int y1 = cy + (size / 2) + PREVIEW_FRAME_PAD + PREVIEW_PAD;

        return mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1;
    }

}
