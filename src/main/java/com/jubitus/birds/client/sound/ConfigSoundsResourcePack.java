package com.jubitus.birds.client.sound;

import com.jubitus.birds.JubitusBirds;
import com.jubitus.jubitusbirds.Tags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

public class ConfigSoundsResourcePack implements IResourcePack {

    private final Set<String> domains = Collections.singleton(Tags.MOD_ID);

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

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        // sounds.json
        if ("pack.mcmeta".equals(location.getPath())) {
            String mcmeta = "{ \"pack\": { \"pack_format\": 3, \"description\": \"JubitusBirds Config Sounds\" } }";
            return new ByteArrayInputStream(mcmeta.getBytes(StandardCharsets.UTF_8));
        }

        if ("sounds.json".equals(location.getPath())) {
            String json = BirdSoundSystem.buildSoundsJson();
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        String p = location.getPath();

        // .ogg
        if (p.startsWith("sounds/default_species/") && p.endsWith(".ogg")) {
            Path file = mapToConfigOgg(p);

            if (file == null) {
                JubitusBirds.LOGGER.warn("[JubitusBirds] mapToConfigOgg returned null for {}", p);
                throw new FileNotFoundException(location.toString());
            }

            if (!Files.exists(file)) {
                JubitusBirds.LOGGER.warn("[JubitusBirds] Sound file NOT FOUND for {} -> {}", p, file.toAbsolutePath());
                throw new FileNotFoundException(location.toString());
            }

            long size;
            try {
                size = Files.size(file);
            } catch (IOException e) {
                JubitusBirds.LOGGER.warn("[JubitusBirds] Could not stat size for sound {} -> {}", p, file.toAbsolutePath(), e);
                size = -1;
            }

            JubitusBirds.LOGGER.info("[JubitusBirds] Serving sound {} -> {} ({} bytes)",
                    p, file.toAbsolutePath(), size);

            // IMPORTANT:
            // Use FileInputStream instead of Files.newInputStream to avoid interruptible NIO channel reads
            // that can throw ClosedByInterruptException on the sound thread.
            return new BufferedInputStream(new FileInputStream(file.toFile()));
        }

        throw new FileNotFoundException(location.toString());
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        if ("pack.mcmeta".equals(location.getPath())) return true;
        if ("sounds.json".equals(location.getPath())) return true;

        String p = location.getPath();
        if (p.startsWith("sounds/default_species/") && p.endsWith(".ogg")) {
            Path file = mapToConfigOgg(p);
            return file != null && Files.exists(file);
        }
        return false;
    }

    @Override
    public Set<String> getResourceDomains() {
        return domains;
    }

    @Override
    public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer serializer, String metadataSectionName) {
        return null; // no pack.mcmeta needed for this use
    }

    @Override
    public BufferedImage getPackImage() {
        return null;
    }

    // -----------------------
    // Mapping: assets path -> config file
    // -----------------------

    @Override
    public String getPackName() {
        return "JubitusBirds Config Sounds";
    }

    private Path mapToConfigOgg(String resourcePath) {
        // Resource paths we serve:
        // - NEW single: "sounds/default_species/<default_species>/single/<name>.ogg"
        // - NEW flock : "sounds/default_species/<default_species>/flock/<name>.ogg"
        // - Legacy    : "sounds/default_species/<default_species>/<name>.ogg" (optional fallback)

        String rest = resourcePath.substring("sounds/default_species/".length()); // "<default_species>/..."
        String[] parts = rest.split("/");

        if (parts.length < 2) return null;

        String speciesKey = parts[0];
        String typePart = null;
        String fileName;

        if (parts.length >= 3) {
            // new layout: <default_species>/<type>/<file.ogg>
            typePart = parts[1];
            fileName = parts[2];
        } else {
            // legacy: <default_species>/<file.ogg>
            fileName = parts[1];
        }

        File gameDir = Minecraft.getMinecraft().gameDir;

        Path speciesBase = gameDir.toPath()
                .resolve("config")
                .resolve("jubitusbirds")
                .resolve("default_species")
                .resolve(speciesKey);

        if ("single".equals(typePart)) {
            return speciesBase.resolve("sounds_single").resolve(fileName);
        }
        if ("flock".equals(typePart)) {
            return speciesBase.resolve("sounds_flock").resolve(fileName);
        }

        // Legacy fallback: /config/jubitusbirds/default_species/<default_species>/sounds/<file>.ogg
        return speciesBase.resolve("sounds").resolve(fileName);
    }


}
