package com.jubitus.birds.client.gui;

import com.google.gson.*;
import com.jubitus.birds.client.BirdManager;
import com.jubitus.birds.client.sound.BirdSoundSystem;
import com.jubitus.birds.species.BirdSpecies;
import com.jubitus.birds.species.BirdSpeciesLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class GuiSpeciesEditorScreen extends GuiScreen {

    // ---------------------------------------------------
// Tooltips (hover descriptions for entries)
// ---------------------------------------------------
    private static final Map<String, String[]> ENTRY_TOOLTIPS = new HashMap<>();
    // ----- tweakables -----
    private static final int PAD = 10;
    private static final int TITLE_H = 24;
    private static final int BTN_H = 24;
    private static final int LIST_ROW_H = 22;
    private static final int PANEL_W_MIN = 170;
    private static final int PANEL_W_MAX = 260;
    private static final int PREVIEW_H = 200; // right panel height
    private static final int PREVIEW_TEX_SIZE = 120;
    private final GuiScreen parent;
    private final BirdSpecies species;
    private final List<JsonEntry> entries = new ArrayList<>();
    // re-layout guard
    private int lastW = -1, lastH = -1;
    private JsonObject rootJson;
    private EntrySlotList entryList;
    private GuiButton btnSave;
    private GuiButton btnCancel;
    private GuiButton btnReloadFromDisk;
    private int previewX, previewY, previewW, previewH;

    public GuiSpeciesEditorScreen(GuiScreen parent, BirdSpecies species) {
        this.parent = parent;
        this.species = species;
    }

    private static void putTip(String key, String... lines) {
        ENTRY_TOOLTIPS.put(key, lines);
    }

    private static void initTooltipsOnce() {
        if (!ENTRY_TOOLTIPS.isEmpty()) return;

        putTip("name",
                "Display name of the default_species.",
                "Shown in the default_species list and editor title.");

        putTip("enabled",
                "If false, this default_species will NOT spawn.");

        putTip("spawnWeight",
                "Relative chance of being picked when multiple default_species are allowed.",
                "Higher = more common, lower = rarer.");

        putTip("birdsPerCellMax",
                "Max number of single birds this default_species can spawn per cell.",
                "Higher = more birds overall.");

        putTip("flockChancePerCell",
                "Chance that a spawn cell produces a flock instead of singles.",
                "0.0 = never, 1.0 = always.");

        putTip("flockMin",
                "Minimum size of a normal flock.");
        putTip("flockMax",
                "Maximum size of a normal flock.");

        putTip("bigFlockChanceDay",
                "Chance (day) that a spawned flock becomes a BIG flock.");
        putTip("bigFlockChanceNight",
                "Chance (night) that a spawned flock becomes a BIG flock.");

        putTip("bigFlockMin",
                "Minimum size of a big flock.");
        putTip("bigFlockMax",
                "Maximum size of a big flock.");

        putTip("biomeWhitelist",
                "Only allow spawning in these biomes (if not empty).",
                "You can use registry ids (minecraft:plains) or biome names.",
                "Tip: leave empty to allow everywhere (except blacklist).");

        putTip("biomeBlacklist",
                "Never allow spawning in these biomes.",
                "Blacklist wins over whitelist.");

        putTip("canSpawnAtDay",
                "If false, this default_species will not spawn during the day.");
        putTip("canSpawnAtNight",
                "If true, this default_species can spawn during the night.");

        putTip("minSpeed",
                "Minimum flight speed (blocks/tick).");
        putTip("maxSpeed",
                "Maximum flight speed (blocks/tick).");

        putTip("maxTurnDegPerTick",
                "How fast the bird can turn per tick (degrees).",
                "Higher = more agile, lower = smoother/wider turns.");

        putTip("noiseStrength",
                "How much random wandering is added to flight direction.",
                "Higher = more chaotic flight.");

        putTip("minAltitudeAboveGround",
                "Minimum height above ground (blocks).");
        putTip("maxAltitudeAboveGround",
                "Maximum height above ground (blocks).");
        putTip("preferredAboveGround",
                "Preferred cruising height above ground (blocks).");

        putTip("verticalAdjustStrength",
                "How strongly the bird corrects toward its target altitude.",
                "Small values = gentle altitude changes.");

        putTip("glideMinTicks",
                "Minimum duration of GLIDE mode (ticks).",
                "20 ticks = 1 second.");
        putTip("glideMaxTicks",
                "Maximum duration of GLIDE mode (ticks).");

        putTip("circleMinTicks",
                "Minimum duration of CIRCLE mode (ticks).");
        putTip("circleMaxTicks",
                "Maximum duration of CIRCLE mode (ticks).");

        putTip("circleRadiusMin",
                "Minimum circle radius (blocks) while circling.");
        putTip("circleRadiusMax",
                "Maximum circle radius (blocks) while circling.");

        putTip("patternWeightGlide",
                "How often the bird chooses GLIDE mode.",
                "Bigger = more gliding overall.");
        putTip("patternWeightCircle",
                "How often the bird chooses CIRCLE mode.",
                "Bigger = more circling overall.");

        putTip("scale",
                "Visual size of the bird model/quad.");
        putTip("flapAmplitude",
                "How strong the wing wobble is.",
                "0 = no flap.");
        putTip("flapSpeed",
                "How fast the wing wobble animates.");
    }

    private static JsonObject deepMerge(JsonObject base, JsonObject override) {
        if (base == null) base = new JsonObject();
        if (override == null) return base;

        for (Map.Entry<String, JsonElement> e : override.entrySet()) {
            String k = e.getKey();
            JsonElement v = e.getValue();

            if (v != null && v.isJsonObject()) {
                JsonObject baseChild = (base.has(k) && base.get(k).isJsonObject()) ? base.getAsJsonObject(k) : new JsonObject();
                JsonObject mergedChild = deepMerge(baseChild, v.getAsJsonObject());
                base.add(k, mergedChild);
            } else {
                base.add(k, v);
            }
        }
        return base;
    }

    private static void setJsonValue(JsonObject root, String dottedPath, JsonElement value) {
        String[] parts = dottedPath.split("\\.");
        JsonObject cur = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String k = parts[i];
            JsonElement next = cur.get(k);
            if (next == null || !next.isJsonObject()) {
                JsonObject created = new JsonObject();
                cur.add(k, created);
                cur = created;
            } else {
                cur = next.getAsJsonObject();
            }
        }

        cur.add(parts[parts.length - 1], value);
    }

    /**
     * Supports:
     * - direct keys like "spawnWeight"
     * - day/night override paths like "day.spawnWeight" / "night.spawnWeight"
     */
    private java.util.List<String> tooltipForPath(String path) {
        if (path == null) return null;

        initTooltipsOnce();

        String prefix = null;
        String key = path;

        if (path.startsWith("day.")) {
            prefix = "Day override:";
            key = path.substring("day.".length());
        } else if (path.startsWith("night.")) {
            prefix = "Night override:";
            key = path.substring("night.".length());
        }

        String[] base = ENTRY_TOOLTIPS.get(key);
        if (base == null) return null;

        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        if (prefix != null) {
            lines.add(prefix + " only applies during that time.");
            lines.addAll(Arrays.asList(base));
        } else {
            lines.addAll(Arrays.asList(base));
        }
        return lines;
    }

    private void drawEntryTooltips(int mouseX, int mouseY) {
        if (entries == null || entries.isEmpty()) return;

        // Find first hovered entry and draw tooltip
        for (JsonEntry e : entries) {
            if (e == null) continue;
            if (e.kind == JsonEntry.Kind.HEADER) continue;

            if (e.isMouseOver(mouseX, mouseY)) {
                java.util.List<String> tip = tooltipForPath(e.path);
                if (tip != null && !tip.isEmpty()) {
                    this.drawHoveringText(tip, mouseX, mouseY);
                }
                return; // only show one tooltip at a time
            }
        }
    }

    private Path propertiesPath() {
        return BirdSpeciesLoader.getRootConfigDir()
                .resolve(species.folderName)
                .resolve("properties.json");
    }

    private void loadJsonFromDisk() {
        Path p = propertiesPath();
        try {
            String raw = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            JsonElement el = new JsonParser().parse(raw);
            if (el != null && el.isJsonObject()) {
                rootJson = el.getAsJsonObject();
            } else {
                rootJson = new JsonObject();
            }
        } catch (Exception e) {
            rootJson = new JsonObject();
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString("§c[JubitusBirds] Failed reading: " + p));
        }
    }

    private void buildEntriesFromJson() {
        entries.clear();

        // 1) schema defines ALL possible fields
        JsonObject schema = buildDefaultSchemaJson();

        // 2) merge actual json into schema (actual values win)
        JsonObject merged = deepMerge(schema, rootJson);

        // 3) keep merged as the editable base
        rootJson = merged;

        // 4) flatten merged so even missing sections appear (day/night will exist)
        flatten("", rootJson);
    }

    private void flatten(String path, JsonObject obj) {
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            String key = e.getKey();
            JsonElement val = e.getValue();
            String p = path.isEmpty() ? key : (path + "." + key);

            if (val == null || val.isJsonNull()) {
                JsonEntry je = JsonEntry.forString(p, "");
                je.wasNull = true;
                entries.add(je);
                continue;
            }


            if (val.isJsonObject()) {
                // add a header row (non-editable) for readability
                entries.add(JsonEntry.header(p));
                flatten(p, val.getAsJsonObject());
                continue;
            }

            if (val.isJsonArray()) {
                entries.add(JsonEntry.forArray(p, val.getAsJsonArray()));
                continue;
            }

            if (val.isJsonPrimitive()) {
                JsonPrimitive prim = val.getAsJsonPrimitive();
                if (prim.isBoolean()) {
                    entries.add(JsonEntry.forBoolean(p, prim.getAsBoolean()));
                } else if (prim.isNumber()) {
                    // store as string; we’ll parse on save
                    entries.add(JsonEntry.forNumber(p, prim.getAsNumber().toString()));
                } else {
                    entries.add(JsonEntry.forString(p, prim.getAsString()));
                }
            }
        }
    }

    private void applyEntriesToJson() {
        for (JsonEntry en : entries) {
            if (en.kind == JsonEntry.Kind.HEADER) continue;

            String p = en.path;
            if (p == null || p.trim().isEmpty()) continue;

            setJsonValue(rootJson, p, en.toJsonElement());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        if (this.width != lastW || this.height != lastH) {
            lastW = this.width;
            lastH = this.height;
            this.initGui();
        }


        // title
        String title = "Edit Species: " + (species.name != null ? species.name : species.folderName);
        this.drawString(this.fontRenderer, title, 10, 10, 0xFFFFFF);

        // left: entry list
        if (entryList != null) entryList.drawScreen(mouseX, mouseY, partialTicks);

        // right: preview box
        drawPreviewBox(partialTicks);

        super.drawScreen(mouseX, mouseY, partialTicks);

// textfields must draw after buttons/background
        for (JsonEntry e : entries) {
            if (e != null) e.drawTextField();
        }

// ✅ tooltips (draw LAST so they appear on top)
        drawEntryTooltips(mouseX, mouseY);

    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // let textfields eat input first
        for (JsonEntry e : entries) {
            if (e != null && e.keyTyped(typedChar, keyCode)) return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);


        for (JsonEntry e : entries) {
            if (e != null) e.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 2) { // cancel
            this.mc.displayGuiScreen(parent);
            return;
        }

        if (button.id == 3) { // reload from disk
            BirdSpeciesLoader.loadAllSpecies();
            this.initGui();
            return;
        }


        if (button.id == 1) { // save
            // push GUI -> JSON
            applyEntriesToJson();

            // write file
            try {
                Gson g = new GsonBuilder().setPrettyPrinting().create();
                String out = g.toJson(rootJson);
                Files.write(propertiesPath(), out.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                // reload systems (same spirit as your /jubitusbirdsreload)
                BirdSpeciesLoader.loadAllSpecies();
                BirdSoundSystem.reloadSoundsOnly();
                if (BirdManager.INSTANCE != null) BirdManager.INSTANCE.clearAllBirds();

                if (mc.player != null)
                    mc.player.sendMessage(new TextComponentString("§a[JubitusBirds] Saved + reloaded " + species.folderName));
                this.mc.displayGuiScreen(parent);

            } catch (Exception ex) {
                if (mc.player != null)
                    mc.player.sendMessage(new TextComponentString("§c[JubitusBirds] Save failed: " + ex.getMessage()));
            }
        }
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.entries.clear();

        int panelW = MathHelper.clamp(this.width / 4, PANEL_W_MIN, PANEL_W_MAX);

        int top = TITLE_H;
        int bottom = this.height - BTN_H - 4;

        int listLeft = PAD;
        int listRight = this.width - panelW - PAD;
        int listW = Math.max(60, listRight - listLeft);

        // right preview panel
        this.previewX = this.width - panelW + PAD;
        this.previewY = TITLE_H;
        this.previewW = panelW - PAD * 2;
        this.previewH = PREVIEW_H;

        // Buttons
        int panelX = this.width - panelW;
        int btnY = this.height - BTN_H;

// spacing between buttons
        int gap = 4;

// button widths
        int wSave = 55;
        int wCancel = 60;
        int wReload = 55;

// total width of the 3-button group
        int totalW = wSave + gap + wCancel + gap + wReload;

// right-aligned, but never starts left of PAD
        int x0 = Math.max(PAD, this.width - PAD - totalW);

        btnSave = new GuiButton(1, x0, btnY, wSave, 20, "Save");
        btnCancel = new GuiButton(2, x0 + wSave + gap, btnY, wCancel, 20, "Cancel");
        btnReloadFromDisk = new GuiButton(3, x0 + wSave + gap + wCancel + gap, btnY, wReload, 20, "Reload");

        this.buttonList.add(btnSave);
        this.buttonList.add(btnCancel);
        this.buttonList.add(btnReloadFromDisk);

        // Load + build
        loadJsonFromDisk();
        buildEntriesFromJson();

        // IMPORTANT: pass screen size + listLeft/listW so GuiSlot aligns correctly
        this.entryList = new EntrySlotList(
                this.mc,
                this.width, this.height,
                top, bottom,
                LIST_ROW_H, // or 22
                listLeft, listW,
                entries
        );
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (entryList != null) entryList.handleMouseInput();
    }

    private void drawPreviewBox(float partialTicks) {
        // panel background
        drawRect(previewX, previewY, previewX + previewW, previewY + previewH, 0x66000000);
        drawRect(previewX, previewY, previewX + previewW, previewY + 1, 0x99FFFFFF);

        String nm = (species.name != null && !species.name.trim().isEmpty()) ? species.name : species.folderName;
        this.drawCenteredString(this.fontRenderer, nm, previewX + previewW / 2, previewY + 6, 0xFFFFFF);


        ResourceLocation tex = pickPreviewTexture();
        if (tex == null) {
            this.drawCenteredString(this.fontRenderer, "(no textures)", previewX + previewW / 2, previewY + previewH / 2, 0xFFAAAA);
            return;
        }

        // spin angle
        long ms = Minecraft.getSystemTime();
        float yaw = (ms % 6000L) * (360.0f / 6000.0f); // 1 full turn / 6s
        int size = getPreviewSizePx();
        int cx = previewX + previewW / 2;
        int cy = previewY + (previewH / 2) + 10; // centered; +10 to sit a bit lower under the title


        int pad = 4;
        int x0 = cx - (size / 2) - pad;
        int y0 = cy - (size / 2) - pad;
        int x1 = cx + (size / 2) + pad;
        int y1 = cy + (size / 2) + pad;

        drawPreviewFrame(x0, y0, x1, y1, getDaySkyARGB(), 0xFF000000);


        drawTurntable3D(tex, cx, cy, size, yaw);

    }

    private ResourceLocation pickPreviewTexture() {
        if (species.textures == null || species.textures.isEmpty()) return null;
        if (species.textures.size() == 1) return species.textures.get(0);

        long ms = Minecraft.getSystemTime();
        int idx = (int) ((ms / 3000L) % species.textures.size()); // every 3 seconds
        return species.textures.get(idx);
    }

    // --------------------------
    // Inner classes: Slot list + entry widgets
    // --------------------------

    private void drawRotatingTexture(ResourceLocation tex, int centerX, int centerY, int size, float angleDeg) {
        this.mc.getTextureManager().bindTexture(tex);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);

        GlStateManager.translate((float) centerX, (float) centerY, 0f);
        GlStateManager.rotate(angleDeg, 0f, 0f, 1f);
        GlStateManager.translate(-(size / 2f), -(size / 2f), 0f);

        // Draw a quad with UV 0..1 so it works regardless of PNG size
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(0, size, 0).tex(0, 1).endVertex();
        buf.pos(size, size, 0).tex(1, 1).endVertex();
        buf.pos(size, 0, 0).tex(1, 0).endVertex();
        buf.pos(0, 0, 0).tex(0, 0).endVertex();
        tess.draw();

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawMarioKartSpin(ResourceLocation tex, int centerX, int centerY, int size, float yawDeg) {
        this.mc.getTextureManager().bindTexture(tex);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);

        GlStateManager.translate((float) centerX, (float) centerY, 0f);

        // ✅ Rotation 2D “sur elle-même”
        GlStateManager.rotate(yawDeg, 0f, 0f, 1f);

        // (Optionnel) si tu veux la même orientation “à plat” que tu avais :
        GlStateManager.rotate(90f, 0f, 0f, 1f); // enlève cette ligne si tu ne veux pas le +90°

        GlStateManager.translate(-(size / 2f), -(size / 2f), 0f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(0, size, 0).tex(0, 1).endVertex();
        buf.pos(size, size, 0).tex(1, 1).endVertex();
        buf.pos(size, 0, 0).tex(1, 0).endVertex();
        buf.pos(0, 0, 0).tex(0, 0).endVertex();
        tess.draw();

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private JsonObject buildDefaultSchemaJson() {
        JsonObject root = new JsonObject();

        // top-level
        root.addProperty("name", species.folderName);
        root.addProperty("enabled", true);

        root.addProperty("spawnWeight", 1.0);
        root.addProperty("birdsPerCellMax", 8);
        root.addProperty("flockChancePerCell", 0.45);
        root.addProperty("flockMin", 3);
        root.addProperty("flockMax", 10);

        root.addProperty("bigFlockChanceDay", 0.35);
        root.addProperty("bigFlockChanceNight", 0.10);
        root.addProperty("bigFlockMin", 15);
        root.addProperty("bigFlockMax", 40);

        root.add("biomeWhitelist", new JsonArray());
        root.add("biomeBlacklist", new JsonArray());

        root.addProperty("canSpawnAtDay", true);
        root.addProperty("canSpawnAtNight", false);

        root.addProperty("minSpeed", 0.35);
        root.addProperty("maxSpeed", 0.60);

        root.addProperty("maxTurnDegPerTick", 4.0);
        root.addProperty("noiseStrength", 0.04);

        root.addProperty("minAltitudeAboveGround", 24.0);
        root.addProperty("maxAltitudeAboveGround", 96.0);
        root.addProperty("preferredAboveGround", 48.0);
        root.addProperty("verticalAdjustStrength", 0.004);

        root.addProperty("glideMinTicks", 60);
        root.addProperty("glideMaxTicks", 140);
        root.addProperty("circleMinTicks", 80);
        root.addProperty("circleMaxTicks", 220);

        root.addProperty("circleRadiusMin", 16.0);
        root.addProperty("circleRadiusMax", 64.0);

        root.addProperty("patternWeightGlide", 0.55);
        root.addProperty("patternWeightCircle", 0.45);

        root.addProperty("scale", 0.45);
        root.addProperty("flapAmplitude", 0.08);
        root.addProperty("flapSpeed", 0.35);

        // day/night blocks (EMPTY but present!)
        JsonObject day = new JsonObject();
        day.addProperty("spawnWeight", 1.0);
        day.addProperty("preferredAboveGround", 48.0);
// add whichever you want overridable…

        JsonObject night = new JsonObject();
        night.addProperty("spawnWeight", 1.0);
        night.addProperty("minSpeed", 0.35);
        night.addProperty("maxSpeed", 0.60);
        night.addProperty("preferredAboveGround", 48.0);

        root.add("day", day);
        root.add("night", night);


        // Optional: include nested sound blocks if you want them exposed too
        // (You have soundSingle/soundFlock in your BirdSpecies class)
        // root.add("soundSingle", buildDefaultSoundSettings());
        // root.add("soundFlock", buildDefaultSoundSettings());

        return root;
    }

    private int getPreviewSizePx() {
        ScaledResolution sr = new ScaledResolution(this.mc);
        int sf = sr.getScaleFactor(); // 1..4 typically

        // Base size in *scaled GUI pixels*
        int base = PREVIEW_TEX_SIZE; // e.g. 120

        // Make it bigger when GUI scale is bigger.
        // Tweak the multiplier to taste:
        float mult = 0.85f + 0.25f * sf;   // sf=1 => 1.10x, sf=4 => 1.85x

        int size = (int) (base * mult);

        // Never exceed the preview panel (leave some margin for title etc.)
        int maxAllowed = Math.min(previewW - 16, previewH - 40);
        size = MathHelper.clamp(size, 60, Math.max(60, maxAllowed));

        return size;
    }

    private void drawTurntable3D(ResourceLocation tex, int centerX, int centerY, int size, float yawDeg) {
        this.mc.getTextureManager().bindTexture(tex);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);

        // Pour voir les 2 faces (plane)
        GlStateManager.disableCull();

        // Depth aide un peu (sinon ça peut faire “plat” selon l’ordre)
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);

        // Place l’objet au centre du panneau, avec un peu de profondeur
        GlStateManager.translate((float) centerX, (float) centerY, 200f);

        // Scale en "pixels" (on va dessiner un quad -1..1)
        float s = size / 2f;
        GlStateManager.scale(s, s, s);

        // ✅ “Caméra au-dessus” => on tilt l’objet (pitch fixe)
        float pitch = 25f; // monte à 35f si tu veux plus "vue du dessus"
        GlStateManager.rotate(pitch, 1f, 0f, 0f);

        // ✅ Spin 3D sur lui-même : rotation autour de l’axe vertical (Y)
        GlStateManager.rotate(yawDeg, 0f, 1f, 0f);

        // Quad dans le plan XZ (y=0)
        // UV standard 0..1
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        // (x, y, z)
        buf.pos(-1, 0, +1).tex(0, 0).endVertex();
        buf.pos(+1, 0, +1).tex(1, 0).endVertex();
        buf.pos(+1, 0, -1).tex(1, 1).endVertex();
        buf.pos(-1, 0, -1).tex(0, 1).endVertex();

        tess.draw();

        GlStateManager.disableDepth();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private int getDaySkyARGB() {
        return 0xFF7BA4FF; // ciel "jour" constant
    }

    private void drawPreviewFrame(int x0, int y0, int x1, int y1, int fillARGB, int borderARGB) {
        // background
        net.minecraft.client.gui.Gui.drawRect(x0, y0, x1, y1, fillARGB);

        // 1px border
        net.minecraft.client.gui.Gui.drawRect(x0, y0, x1, y0 + 1, borderARGB);     // top
        net.minecraft.client.gui.Gui.drawRect(x0, y1 - 1, x1, y1, borderARGB);     // bottom
        net.minecraft.client.gui.Gui.drawRect(x0, y0, x0 + 1, y1, borderARGB);     // left
        net.minecraft.client.gui.Gui.drawRect(x1 - 1, y0, x1, y1, borderARGB);     // right
    }

    static class EntrySlotList extends GuiSlot {

        private final List<JsonEntry> entries;
        private final int listLeft;
        private final int listW;

        public EntrySlotList(Minecraft mc, int screenW, int screenH,
                             int topIn, int bottomIn, int slotHeightIn,
                             int listLeft, int listW,
                             List<JsonEntry> entries) {
            super(mc, screenW, screenH, topIn, bottomIn, slotHeightIn);
            this.entries = entries;
            this.listLeft = listLeft;
            this.listW = listW;

            // Tell GuiSlot where the list actually is on screen:
            this.left = listLeft;
            this.right = listLeft + listW;
        }

        @Override
        protected int getSize() {
            return entries.size();
        }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        }

        @Override
        protected boolean isSelected(int index) {
            return false;
        }

        @Override
        protected void drawBackground() {
        }

        @Override
        protected void drawSlot(int idx, int right, int y, int heightIn, int mouseXIn, int mouseYIn, float partialTicks) {
            JsonEntry e = entries.get(idx);
            if (e == null) return;

            int x = this.left + 6;

            // layout in the real list width so widgets cannot spill into preview
            e.layout(x, y, this.listW - 12, heightIn);
            e.draw(mouseXIn, mouseYIn, partialTicks);
        }

        @Override
        public int getListWidth() {
            return listW;
        }

        @Override
        protected int getScrollBarX() {
            return this.right - 6;
        }
    }

    static class JsonEntry {

        private static final int LABEL_W = 190;
        final Kind kind;
        final String path;
        boolean wasNull;
        // widgets
        GuiTextField tf;
        GuiButton toggleBtn;

        // cached values
        boolean boolVal;

        private int x, y, w, h;
        private JsonEntry(Kind kind, String path) {
            this.kind = kind;
            this.path = path;
        }

        static JsonEntry header(String path) {
            return new JsonEntry(Kind.HEADER, path);
        }

        static JsonEntry forBoolean(String path, boolean v) {
            JsonEntry e = new JsonEntry(Kind.BOOL, path);
            e.boolVal = v;
            return e;
        }

        static JsonEntry forNumber(String path, String numText) {
            JsonEntry e = new JsonEntry(Kind.NUMBER, path);
            e.tf = new GuiTextField(0, Minecraft.getMinecraft().fontRenderer, 0, 0, 120, 18);
            e.tf.setText(numText != null ? numText : "0");
            return e;
        }

        static JsonEntry forString(String path, String s) {
            JsonEntry e = new JsonEntry(Kind.STRING, path);
            e.tf = new GuiTextField(0, Minecraft.getMinecraft().fontRenderer, 0, 0, 220, 18);
            e.tf.setText(s != null ? s : "");
            return e;
        }

        static JsonEntry forArray(String path, JsonArray arr) {
            JsonEntry e = new JsonEntry(Kind.ARRAY, path);
            e.tf = new GuiTextField(0, Minecraft.getMinecraft().fontRenderer, 0, 0, 260, 18);

            // comma-separated editing
            List<String> parts = new ArrayList<>();
            if (arr != null) {
                for (JsonElement el : arr) {
                    if (el != null && el.isJsonPrimitive()) parts.add(el.getAsString());
                }
            }
            e.tf.setText(String.join(", ", parts));
            return e;
        }

        boolean isMouseOver(int mx, int my) {
            // hover anywhere on the row (label + input area)
            return mx >= x && mx <= (x + w) && my >= y && my <= (y + h);
        }

        void layout(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;

            // label uses ~45% of the row width (clamped)
            int labelW = MathHelper.clamp((int) (w * 0.45f), 90, 220);

            if (tf != null) {
                tf.x = x + labelW;
                tf.y = y + 2;
                tf.width = Math.max(60, w - labelW - 10);
            }

            if (kind == Kind.BOOL) {
                if (toggleBtn == null) toggleBtn = new GuiButton(0, 0, 0, 70, 18, "");
                toggleBtn.x = x + labelW;
                toggleBtn.y = y + 1;
                toggleBtn.displayString = boolVal ? "true" : "false";
            }
        }

        void draw(int mouseX, int mouseY, float partialTicks) {
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

            if (kind == Kind.HEADER) {
                fr.drawString("[" + path + "]", x, y + 5, 0x66CCFF);
                return;
            }

            // label (shorten)
            String label = path;
            int maxLabelPx = (tf != null ? (tf.x - x - 6) : 200);
            label = fr.trimStringToWidth(label, Math.max(40, maxLabelPx));

            fr.drawString(label, x, y + 6, 0xFFFFFF);


            if (tf != null) tf.drawTextBox();
            if (toggleBtn != null) toggleBtn.drawButton(Minecraft.getMinecraft(), mouseX, mouseY, partialTicks);

        }

        void drawTextField() {
            // handled in draw() already, but keeping this hook (some setups need it)
        }

        void mouseClicked(int mx, int my, int btn) throws IOException {
            if (tf != null) tf.mouseClicked(mx, my, btn);

            if (toggleBtn != null && toggleBtn.mousePressed(Minecraft.getMinecraft(), mx, my)) {
                boolVal = !boolVal;
                toggleBtn.playPressSound(Minecraft.getMinecraft().getSoundHandler());
            }
        }

        boolean keyTyped(char c, int key) throws IOException {
            if (tf != null && tf.isFocused()) {
                tf.textboxKeyTyped(c, key);
                return true;
            }
            return false;
        }

        JsonElement toJsonElement() {
            switch (kind) {
                case BOOL:
                    return new JsonPrimitive(boolVal);

                case NUMBER: {
                    String t = (tf != null) ? tf.getText().trim() : "0";
                    if (t.isEmpty()) t = "0";
                    try {
                        if (t.contains(".") || t.contains("e") || t.contains("E")) {
                            return new JsonPrimitive(Double.parseDouble(t));
                        } else {
                            long v = Long.parseLong(t);
                            if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) return new JsonPrimitive((int) v);
                            return new JsonPrimitive(v);
                        }
                    } catch (Exception ex) {
                        return new JsonPrimitive(0);
                    }
                }

                case ARRAY: {
                    JsonArray arr = new JsonArray();
                    String t = (tf != null) ? tf.getText() : "";
                    if (t != null && !t.trim().isEmpty()) {
                        String[] parts = t.split(",");
                        for (String p : parts) {
                            String s = p.trim();
                            if (!s.isEmpty()) arr.add(new JsonPrimitive(s));
                        }
                    }
                    return arr;
                }

                case STRING: {
                    String t = (tf != null) ? tf.getText() : "";
                    if (wasNull && (t == null || t.trim().isEmpty())) {
                        return JsonNull.INSTANCE;
                    }
                    return new JsonPrimitive(t != null ? t : "");
                }

                default:
                    // should never happen, but keeps compiler happy
                    return JsonNull.INSTANCE;
            }
        }

        enum Kind {HEADER, BOOL, NUMBER, STRING, ARRAY}
    }

}