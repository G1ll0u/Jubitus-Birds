package com.jubitus.birds.species;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.jubitus.birds.JubitusBirds;
import com.jubitus.birds.client.sound.BirdCallType;
import com.jubitus.birds.client.sound.BirdSoundSystem;
import com.jubitus.jubitusbirds.Tags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BirdSpeciesLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void loadAllSpecies() {
        BirdSpeciesRegistry.clear();
        BirdSoundSystem.clearAllSpeciesSounds(); // IMPORTANT: avoid stale keys after reload


        Path root = getRootConfigDir();
        if (!Files.exists(root)) {
            JubitusBirds.LOGGER.warn("[JubitusBirds] Species folder does not exist yet: {}", root.toAbsolutePath());
            return;
        }

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
            for (Path speciesDir : ds) {
                if (!Files.isDirectory(speciesDir)) continue;

                String folderName = speciesDir.getFileName().toString();

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

                BirdSpecies s = readSpeciesJson(folderName, props);
                if (s == null) continue;

                s.folderName = folderName;

                String soundKey = folderName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
                s.soundKey = soundKey;


                // Load textures
                int texCount = loadTexturesForSpecies(s, texDir);
                if (texCount <= 0) {
                    JubitusBirds.LOGGER.warn("[JubitusBirds] Skipping {} (no valid PNGs in textures/)", s.name);
                    continue;
                }

                // NEW per-default_species sound folders:
                Path singleDir = speciesDir.resolve("sounds_single");
                Path flockDir = speciesDir.resolve("sounds_flock");

                List<String> singleSounds = loadSoundsForPool(soundKey, "single", singleDir);
                List<String> flockSounds = loadSoundsForPool(soundKey, "flock", flockDir);

                BirdSoundSystem.setSpeciesSounds(soundKey, BirdCallType.SINGLE, singleSounds);
                BirdSoundSystem.setSpeciesSounds(soundKey, BirdCallType.FLOCK, flockSounds);

                s.clampAndFix();
// On prend le max entre jour/nuit pour éviter les surprises quand il fait nuit
                double singleMax = Math.max(
                        s.viewForTime(true).sound(BirdCallType.SINGLE).soundMaxDistance(),
                        s.viewForTime(false).sound(BirdCallType.SINGLE).soundMaxDistance()
                );

                double flockMax = Math.max(
                        s.viewForTime(true).sound(BirdCallType.FLOCK).soundMaxDistance(),
                        s.viewForTime(false).sound(BirdCallType.FLOCK).soundMaxDistance()
                );

                BirdSoundSystem.setAttenuationDistance(soundKey, BirdCallType.SINGLE, singleMax);
                BirdSoundSystem.setAttenuationDistance(soundKey, BirdCallType.FLOCK, flockMax);

                BiomeNameResolver.ResolvedBiomeLists resolved =
                        BiomeNameResolver.resolveLists(s.biomeWhitelist, s.biomeBlacklist, s.name);

                s.resolvedWhitelistIds = resolved.whitelistRegistryIds;
                s.resolvedBlacklistIds = resolved.blacklistRegistryIds;

                BirdSpeciesRegistry.register(s);
                BiomeRuleResolver.resolve(s);

                JubitusBirds.LOGGER.info("[JubitusBirds] Species '{}' soundKey='{}' singleSounds={} flockSounds={}",
                        s.name, s.soundKey, singleSounds.size(), flockSounds.size());

                JubitusBirds.LOGGER.info("[JubitusBirds] Loaded default_species '{}' with {} texture(s).", s.name, s.textures.size());
            }
        } catch (IOException e) {
            JubitusBirds.LOGGER.error("[JubitusBirds] Failed scanning default_species folder.", e);
        }

        if (BirdSpeciesRegistry.all().isEmpty()) {
            JubitusBirds.LOGGER.error("[JubitusBirds] No default_species loaded! Birds will not spawn.");
        }
    }

    /**
     * config/jubitusbirds/
     */
    public static Path getRootConfigDir() {
        // 1.12.2: config folder is relative to game dir
        File gameDir = Minecraft.getMinecraft().gameDir;
        return gameDir.toPath().resolve("config").resolve("jubitusbirds/default_species");
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

    private static List<String> loadSoundsForPool(String speciesKey, String poolName, Path soundDir) {
        List<String> out = new ArrayList<>();
        try {
            if (!Files.isDirectory(soundDir)) return out;

            Files.list(soundDir).forEach(p -> {
                String n = p.getFileName().toString();
                String lower = n.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".ogg")) return;

                String codec = detectOggCodec(p);

                if (!"vorbis".equals(codec)) {
                    long size = -1;
                    try {
                        size = Files.size(p);
                    } catch (IOException ignored) {
                    }
                    JubitusBirds.LOGGER.error("[JubitusBirds] Skipping {} sound for default_species='{}' file={} (codec={}, {} bytes). Minecraft 1.12 needs VORBIS.",
                            poolName, speciesKey, p.toAbsolutePath(), codec, size);
                    return;
                }

                // sound name in sounds.json must NOT include ".ogg"
                String noExt = n.substring(0, n.length() - 4);
                noExt = noExt.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
                out.add(noExt);
            });

        } catch (Exception e) {
            JubitusBirds.LOGGER.warn("[JubitusBirds] Error reading {} sounds for speciesKey='{}' dir={} err={}",
                    poolName, speciesKey, soundDir.toAbsolutePath(), e.getMessage());
        }

        return out;
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

    private static String detectOggCodec(Path file) {
        try {
            if (!Files.isRegularFile(file)) return "not_a_file";
            long size = Files.size(file);
            if (size < 64) return "too_small";

            byte[] buf = new byte[4096];
            int n;
            try (InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(file.toFile()))) {
                n = in.read(buf);
            }
            if (n <= 0) return "unreadable";

            // Ogg container magic
            if (!(buf[0] == 'O' && buf[1] == 'g' && buf[2] == 'g' && buf[3] == 'S')) {
                return "not_ogg_container";
            }

            String s = new String(buf, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (s.contains("vorbis")) return "vorbis";
            if (s.contains("OpusHead")) return "opus";
            if (s.contains("Speex")) return "speex";
            if (s.contains("FLAC")) return "flac_in_ogg";

            return "unknown_ogg_codec";

        } catch (Exception e) {
            return "error:" + e.getClass().getSimpleName();
        }
    }

}
