package com.jubitus.birds.client.gui;

import com.jubitus.birds.species.BirdSpecies;
import com.jubitus.birds.species.BirdSpeciesRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GuiSpeciesListScreen extends GuiScreen {

    private final GuiScreen parent;
    private final List<BirdSpecies> species = new ArrayList<>();
    private SpeciesSlotList list;
    private GuiButton btnMainOptions; // ✅ new
    private int previewColX0;
    private int previewColX1;

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
        com.jubitus.birds.client.gui.GuiGlStateReset.reset();

        this.drawBackground(0); // ✅ REPLACE drawDefaultBackground() with this

        if (list != null) list.drawScreen(mouseX, mouseY, partialTicks);

        this.drawCenteredString(this.fontRenderer, "JubitusBirds - Species", this.width / 2, 10, 0xFFFFFF);

        if (species.isEmpty()) {
            this.drawCenteredString(this.fontRenderer,
                    "No species loaded. (Check config/jubitusbirds/species)",
                    this.width / 2, this.height / 2, 0xFFAAAA);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        com.jubitus.birds.client.gui.GuiGlStateReset.reset();
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

        int slotH = 28;

        // ✅ full width list
        this.list = new SpeciesSlotList(this, this.mc, this.width, this.height, top, bottom, slotH);

        this.buttonList.add(new GuiButton(0, this.width - 110, this.height - 24, 100, 20, "Done"));

        this.btnMainOptions = new GuiButton(1, 10, this.height - 24, 140, 20, "Birds options");
        this.buttonList.add(this.btnMainOptions);
    }


    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (list != null) list.handleMouseInput();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
    }

    // Tiny 3D turntable preview (same spirit as the editor one)

    // Pick a texture for preview (cycles every 3s like your editor)
    ResourceLocation pickPreviewTexture(BirdSpecies s) {
        if (s == null || s.textures == null || s.textures.isEmpty()) return null;
        if (s.textures.size() == 1) return s.textures.get(0);

        long ms = Minecraft.getSystemTime();
        int idx = (int) ((ms / 3000L) % s.textures.size());
        return s.textures.get(idx);
    }

    // Couleur ciel vanilla (biome/heure). Fallback si world/player pas dispo.
    int getDaySkyARGB() {
        return 0xFF7BA4FF; // joli bleu "ciel MC"
    }

    void drawPreviewFrame(int x0, int y0, int x1, int y1, int fillARGB, int borderARGB) {
        // fill
        net.minecraft.client.gui.Gui.drawRect(x0, y0, x1, y1, fillARGB);

        // 1px border
        net.minecraft.client.gui.Gui.drawRect(x0, y0, x1, y0 + 1, borderARGB);       // top
        net.minecraft.client.gui.Gui.drawRect(x0, y1 - 1, x1, y1, borderARGB);       // bottom
        net.minecraft.client.gui.Gui.drawRect(x0, y0, x0 + 1, y1, borderARGB);       // left
        net.minecraft.client.gui.Gui.drawRect(x1 - 1, y0, x1, y1, borderARGB);       // right
    }

    int getPreviewColX0() {
        return previewColX0;
    }

    int getPreviewColX1() {
        return previewColX1;
    }
}

