package com.jubitus.birds.client.gui;

import com.jubitus.birds.species.BirdSpecies;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.util.ResourceLocation;

public class SpeciesSlotList extends GuiSlot {
    private static final int PREVIEW_GUTTER_W = 78;  // espace réservé à droite pour le preview
    private static final int PREVIEW_SHIFT_L = 156;   // ✅ combien tu le ramènes vers le texte (ajuste)
    private static final int PREVIEW_PAD_R = 6;      // padding depuis le bord droit
    private static final int PREVIEW_PAD = 2; // bord autour

    private final GuiSpeciesListScreen owner;

    public SpeciesSlotList(GuiSpeciesListScreen owner, Minecraft mc, int width, int height, int topIn, int bottomIn, int slotHeightIn) {
        super(mc, width, height, topIn, bottomIn, slotHeightIn);
        this.owner = owner;
    }

    @Override
    protected int getSize() {
        return owner.getSpecies().size();
    }

    @Override
    protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        if (index < 0 || index >= owner.getSpecies().size()) return;

        // y du slot : GuiSlot a une fonction pour ça
        int rowY = this.top + 4 - (int) this.amountScrolled + index * this.slotHeight;

        // Si clic sur la preview => ouvre l’éditeur
        if (isMouseOverPreview(rowY, this.slotHeight, mouseX, mouseY)) {
            owner.openEditor(owner.getSpecies().get(index));
            return;
        }

        // Sinon, comportement normal (clic sur la ligne)
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

        int textX = x + 6;

        // ✅ limite de texte = bord droit de la liste - gutter réservé au preview
        int textRightLimit = this.right - PREVIEW_GUTTER_W;
        int maxTextW = Math.max(30, textRightLimit - textX);

        String nameTrim = this.mc.fontRenderer.trimStringToWidth(name, maxTextW);
        String folderTrim = this.mc.fontRenderer.trimStringToWidth("folder: " + folder, maxTextW);


        owner.drawString(this.mc.fontRenderer, nameTrim, textX, y + 2, 0xFFFFFF);
        owner.drawString(this.mc.fontRenderer, folderTrim, textX, y + 12, 0xAAAAAA);

        // --- preview ---
        ResourceLocation tex = owner.pickPreviewTexture(s);
        if (tex != null) {
            int size = heightIn - 4;
            int cy = y + (heightIn / 2);

// centre X calculé comme avant (avec ton shift)
            int cx = (this.right - PREVIEW_PAD_R) - (size / 2) - PREVIEW_SHIFT_L;

// rectangle du cadre (un peu plus grand que le sprite)
            int pad = 2;
            int x0 = cx - (size / 2) - pad;
            int y0 = cy - (size / 2) - pad;
            int x1 = cx + (size / 2) + pad;
            int y1 = cy + (size / 2) + pad;

// ✅ fond bleu ciel vanilla + cadre noir
            int sky = owner.getDaySkyARGB();
            boolean hover = isMouseOverPreview(y, heightIn, mouseXIn, mouseYIn);
            int border = hover ? 0xFFFFFFFF : 0xFF000000; // blanc si hover
            owner.drawPreviewFrame(x0, y0, x1, y1, sky);

// puis draw l’oiseau par-dessus
            long ms = Minecraft.getSystemTime();
            float yaw = (ms % 6000L) * (360.0f / 6000.0f);
            owner.drawTinyTurntable3D(tex, cx, cy, size, yaw);


            owner.drawTinyTurntable3D(tex, cx, cy, size, yaw);
        }
    }

    private boolean isMouseOverPreview(int rowY, int heightIn, int mouseX, int mouseY) {
        int size = heightIn - 4;
        int cy = rowY + (heightIn / 2);

        int cx = (this.right - PREVIEW_PAD_R) - (size / 2) - PREVIEW_SHIFT_L;

        int x0 = cx - (size / 2) - PREVIEW_PAD;
        int y0 = cy - (size / 2) - PREVIEW_PAD;
        int x1 = cx + (size / 2) + PREVIEW_PAD;
        int y1 = cy + (size / 2) + PREVIEW_PAD;

        return mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1;
    }

}
