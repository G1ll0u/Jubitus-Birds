package com.jubitus.birds.client.gui;

import com.jubitus.birds.species.BirdSpecies;
import com.jubitus.birds.species.BirdSpeciesRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GuiSpeciesListScreen extends GuiScreen {

    private final GuiScreen parent;
    private final List<BirdSpecies> species = new ArrayList<>();
    private SpeciesSlotList list;
    private GuiButton btnMainOptions; // ✅ new

    public GuiSpeciesListScreen(GuiScreen parent) {
        this.parent = parent;
    }

    void openEditor(BirdSpecies s) {
        if (s == null) return;
        this.mc.displayGuiScreen(new GuiSpeciesEditorScreen(this, s));
    }

    List<BirdSpecies> getSpecies() {
        return species;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        if (list != null) list.drawScreen(mouseX, mouseY, partialTicks);

        this.drawCenteredString(this.fontRenderer, "JubitusBirds - Species", this.width / 2, 10, 0xFFFFFF);

        if (species.isEmpty()) {
            this.drawCenteredString(this.fontRenderer,
                    "No default_species loaded. (Check config/jubitusbirds/default_species)",
                    this.width / 2, this.height / 2, 0xFFAAAA);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            this.mc.displayGuiScreen(parent);
            return;
        }

        if (button.id == 1) {
            this.mc.displayGuiScreen(new JubitusMainConfigGui(this));
        }


    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.species.clear();
        this.species.addAll(BirdSpeciesRegistry.all());
        this.species.sort(Comparator.comparing(s -> (s.name != null ? s.name.toLowerCase() : "")));

        int top = 28;
        int bottom = this.height - 28;

        this.list = new SpeciesSlotList(this, this.mc, this.width, this.height, top, bottom, 28);


        // bottom-right done
        this.buttonList.add(new GuiButton(0, this.width - 110, this.height - 24, 100, 20, "Done"));

        // ✅ bottom-left main options
        this.btnMainOptions = new GuiButton(1, 10, this.height - 24, 140, 20, "Main options menu");
        this.buttonList.add(this.btnMainOptions);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (list != null) list.handleMouseInput();
    }

    // Pick a texture for preview (cycles every 3s like your editor)
    ResourceLocation pickPreviewTexture(BirdSpecies s) {
        if (s == null || s.textures == null || s.textures.isEmpty()) return null;
        if (s.textures.size() == 1) return s.textures.get(0);

        long ms = Minecraft.getSystemTime();
        int idx = (int) ((ms / 3000L) % s.textures.size());
        return s.textures.get(idx);
    }

    // Tiny 3D turntable preview (same spirit as the editor one)
    void drawTinyTurntable3D(ResourceLocation tex, int centerX, int centerY, int size, float yawDeg) {
        if (tex == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(tex);

        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();     // show both sides
        GlStateManager.disableLighting(); // GUI friendly
        GlStateManager.color(1f, 1f, 1f, 1f);

        // ✅ No depth: avoids weirdness with GUI scale / other GUI elements
        GlStateManager.disableDepth();

        GlStateManager.translate((float) centerX, (float) centerY, 0f);

        float s = size / 2f;
        GlStateManager.scale(s, s, s);

        // “camera above”
        GlStateManager.rotate(25f, 1f, 0f, 0f);

        // 3D spin
        GlStateManager.rotate(yawDeg, 0f, 1f, 0f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        buf.pos(-1, 0, +1).tex(0, 0).endVertex();
        buf.pos(+1, 0, +1).tex(1, 0).endVertex();
        buf.pos(+1, 0, -1).tex(1, 1).endVertex();
        buf.pos(-1, 0, -1).tex(0, 1).endVertex();

        tess.draw();

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    // Couleur ciel vanilla (biome/heure). Fallback si world/player pas dispo.
    int getDaySkyARGB() {
        return 0xFF7BA4FF; // joli bleu "ciel MC"
    }


    void drawPreviewFrame(int x0, int y0, int x1, int y1, int fillARGB) {
        // Fond
        net.minecraft.client.gui.Gui.drawRect(x0, y0, x1, y1, fillARGB);

        // Cadre noir 1px
        int black = 0xFF000000;
        net.minecraft.client.gui.Gui.drawRect(x0, y0, x1, y0 + 1, black);       // top
        net.minecraft.client.gui.Gui.drawRect(x0, y1 - 1, x1, y1, black);       // bottom
        net.minecraft.client.gui.Gui.drawRect(x0, y0, x0 + 1, y1, black);       // left
        net.minecraft.client.gui.Gui.drawRect(x1 - 1, y0, x1, y1, black);       // right
    }

}

