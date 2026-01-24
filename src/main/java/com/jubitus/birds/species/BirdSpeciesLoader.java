package com.jubitus.birds.species;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.jubitus.birds.JubitusBirds;
import com.jubitus.jubitusbirds.Tags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

public class BirdSpeciesLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** config/jubitusbirds/ */
    public static Path getRootConfigDir() {
        // 1.12.2: config folder is relative to game dir
        File gameDir = Minecraft.getMinecraft().gameDir;
        return gameDir.toPath().resolve("config").resolve("jubitusbirds/species");
    }

    public static void loadAllSpecies() {
        BirdSpeciesRegistry.clear();

        ensureDefaultSwallowExists();

        Path root = getRootConfigDir();
        if (!Files.exists(root)) {
            JubitusBirds.LOGGER.warn("[JubitusBirds] Species folder does not exist yet: {}", root.toAbsolutePath());
            return;
        }

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
            for (Path speciesDir : ds) {
                if (!Files.isDirectory(speciesDir)) continue;

                Path props = speciesDir.resolve("properties.json");
                Path texDir = speciesDir.resolve("textures");

                if (!Files.exists(props)) {
                    JubitusBirds.LOGGER.warn("[JubitusBirds] Skipping {} (missing properties.json)", speciesDir.getFileName());
                    continue;
                }
                if (!Files.isDirectory(texDir)) {
                    JubitusBirds.LOGGER.warn("[JubitusBirds] Skipping {} (missing textures/ folder)", speciesDir.getFileName());
                    continue;
                }

                BirdSpecies s = readSpeciesJson(speciesDir.getFileName().toString(), props);
                if (s == null) continue;

                // Load textures
                int texCount = loadTexturesForSpecies(s, texDir);
                if (texCount <= 0) {
                    JubitusBirds.LOGGER.warn("[JubitusBirds] Skipping {} (no valid PNGs in textures/)", s.name);
                    continue;
                }

                s.clampAndFix();
                BiomeNameResolver.ResolvedBiomeLists resolved =
                        BiomeNameResolver.resolveLists(s.biomeWhitelist, s.biomeBlacklist, s.name);

                s.resolvedWhitelistIds = resolved.whitelistRegistryIds;
                s.resolvedBlacklistIds = resolved.blacklistRegistryIds;

                BirdSpeciesRegistry.register(s);
                BiomeRuleResolver.resolve(s);
                JubitusBirds.LOGGER.info("[JubitusBirds] Loaded species '{}' with {} texture(s).", s.name, s.textures.size());
            }
        } catch (IOException e) {
            JubitusBirds.LOGGER.error("[JubitusBirds] Failed scanning species folder.", e);
        }

        if (BirdSpeciesRegistry.all().isEmpty()) {
            JubitusBirds.LOGGER.error("[JubitusBirds] No species loaded! Birds will not spawn.");
        }
    }

    private static BirdSpecies readSpeciesJson(String folderName, Path props) {
        try {
            String json = new String(Files.readAllBytes(props), StandardCharsets.UTF_8);

            BirdSpecies s = GSON.fromJson(json, BirdSpecies.class);
            if (s == null) {
                JubitusBirds.LOGGER.warn("[JubitusBirds] Invalid JSON in {} (parsed null).", props);
                return null;
            }

            // If user forgot to set name: use folder name
            if (s.name == null || s.name.trim().isEmpty()) {
                s.name = folderName;
            }

            s.name = s.name.trim();
            return s;

        } catch (JsonSyntaxException jse) {
            JubitusBirds.LOGGER.warn("[JubitusBirds] Invalid JSON syntax in {}: {}", props, jse.getMessage());
            return null;
        } catch (IOException ioe) {
            JubitusBirds.LOGGER.warn("[JubitusBirds] Failed reading {}: {}", props, ioe.getMessage());
            return null;
        }
    }

    private static int loadTexturesForSpecies(BirdSpecies s, Path texDir) {
        int ok = 0;

        try {
            Files.list(texDir).forEach(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!n.endsWith(".png")) return;

                ResourceLocation rl = registerRuntimeTexture(s, p);
                if (rl != null) {
                    s.textures.add(rl);
                }
            });

            ok = s.textures.size();
        } catch (Exception e) {
            JubitusBirds.LOGGER.warn("[JubitusBirds] Error reading textures for {}: {}", s.name, texDir.toAbsolutePath(), e);
        }

        return ok;
    }


    private static ResourceLocation registerRuntimeTexture(BirdSpecies s, Path pngPath) {
        try (InputStream in = Files.newInputStream(pngPath)) {

            // Use Minecraft's own PNG reader (more reliable than ImageIO in modded envs)
            BufferedImage img = TextureUtil.readBufferedImage(in);
            if (img == null) {
                JubitusBirds.LOGGER.warn("[JubitusBirds] '{}' is not a readable PNG (TextureUtil returned null).", pngPath.getFileName());
                return null;
            }

            DynamicTexture dyn = new DynamicTexture(img);

            String safeSpecies = s.name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
            String safeFile = pngPath.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-\\.]", "_");

            ResourceLocation rl = new ResourceLocation(Tags.MOD_ID, "jubitusbirds/" + safeSpecies + "/" + safeFile);

            // This must run when the texture manager exists (we moved loadAllSpecies() to init)
            Minecraft.getMinecraft().getTextureManager().loadTexture(rl, dyn);

            return rl;

        } catch (Exception e) {
            // Log FULL stacktrace so we can see the real cause (NPE etc.)
            JubitusBirds.LOGGER.warn("[JubitusBirds] Failed loading texture: {}", pngPath.toAbsolutePath(), e);
            return null;
        }
    }


    /**
     * Creates /config/jubitusbirds/swallow/ with defaults by copying from jar resources:
     * assets/<modid>/jubitusbirds_defaults/swallow/...
     */
    private static void ensureDefaultSwallowExists() {
        Path root = getRootConfigDir();
        Path swallowDir = root.resolve("swallow");
        Path props = swallowDir.resolve("properties.json");
        Path texDir = swallowDir.resolve("textures");

        try {
            if (!Files.exists(root)) Files.createDirectories(root);

            // If already exists and has properties, do nothing
            if (Files.exists(props) && Files.isDirectory(texDir)) return;

            Files.createDirectories(texDir);

            // Copy properties.json + a couple textures from jar resources.
            // IMPORTANT: you must add these files into your jar under:
            // resources/assets/<modid>/jubitusbirds_defaults/swallow/properties.json
            // resources/assets/<modid>/jubitusbirds_defaults/swallow/textures/swallow_0.png
            // ...
            copyJarResourceIfMissing("/assets/" + Tags.MOD_ID + "/jubitusbirds_defaults/swallow/properties.json", props);

            copyJarResourceIfMissing("/assets/" + Tags.MOD_ID + "/jubitusbirds_defaults/swallow/textures/swallow_0.png",
                    texDir.resolve("swallow_0.png"));
            copyJarResourceIfMissing("/assets/" + Tags.MOD_ID + "/jubitusbirds_defaults/swallow/textures/swallow_1.png",
                    texDir.resolve("swallow_1.png"));

            JubitusBirds.LOGGER.info("[JubitusBirds] Default species 'swallow' ensured at {}", swallowDir.toAbsolutePath());

        } catch (IOException e) {
            JubitusBirds.LOGGER.warn("[JubitusBirds] Failed ensuring default swallow folder: {}", e.getMessage());
        }
    }

    private static void copyJarResourceIfMissing(String jarPath, Path outPath) {
        try {
            if (Files.exists(outPath)) return;

            InputStream in = BirdSpeciesLoader.class.getResourceAsStream(jarPath);
            if (in == null) {
                JubitusBirds.LOGGER.error("[JubitusBirds] Missing bundled default resource in jar: {}", jarPath);
                return;
            }

            Files.createDirectories(outPath.getParent());
            Files.copy(in, outPath, StandardCopyOption.REPLACE_EXISTING);
            in.close();

        } catch (IOException e) {
            JubitusBirds.LOGGER.warn("[JubitusBirds] Failed copying {} to {}: {}", jarPath, outPath, e.getMessage());
        }
    }
}
