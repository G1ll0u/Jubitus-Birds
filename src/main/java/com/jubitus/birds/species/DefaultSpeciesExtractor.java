package com.jubitus.birds.species;

import com.jubitus.birds.JubitusBirds;
import com.jubitus.jubitusbirds.Tags;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DefaultSpeciesExtractor {
    // Where defaults live INSIDE the JAR
    private static final String JAR_PREFIX = "assets/" + Tags.MOD_ID + "/default_species/";

    private DefaultSpeciesExtractor() {
    }

    public static void extractMissingDefaults(FMLPreInitializationEvent event) {
        try {
            File cfgDir = event.getModConfigurationDirectory(); // .../.minecraft/config
            Path targetRoot = cfgDir.toPath().resolve("jubitusbirds").resolve("default_species");
            Files.createDirectories(targetRoot);

            File source = event.getSourceFile(); // jar in production, folder in dev

            if (source.isFile() && source.getName().endsWith(".jar")) {
                extractFromJar(source, targetRoot);
            } else if (source.isDirectory()) {
                extractFromDirectory(source.toPath(), targetRoot);
            } else {
                JubitusBirds.LOGGER.warn("[JubitusBirds] Unknown source type: {}", source);
            }
        } catch (Exception e) {
            JubitusBirds.LOGGER.warn("[JubitusBirds] Failed extracting default default_species.", e);
        }
    }

    private static void extractFromJar(File jarFile, Path targetRoot) throws Exception {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {

            // Discover default_species folder names contained in defaults
            java.util.Set<String> bundledSpecies = new java.util.HashSet<>();

            java.util.Enumeration<java.util.jar.JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                java.util.jar.JarEntry je = en.nextElement();
                if (je.isDirectory()) continue;

                String name = je.getName(); // e.g. assets/jubitusbirds/default_species/robin/properties.json
                if (!name.startsWith(JAR_PREFIX)) continue;

                String rest = name.substring(JAR_PREFIX.length());
                int slash = rest.indexOf('/');
                if (slash <= 0) continue;

                String speciesFolder = rest.substring(0, slash);
                bundledSpecies.add(speciesFolder);
            }

            // Copy each default_species if the folder doesn't exist yet
            for (String speciesFolder : bundledSpecies) {
                Path outDir = targetRoot.resolve(speciesFolder);
                if (Files.isDirectory(outDir)) {
                    JubitusBirds.LOGGER.info("[JubitusBirds] Default default_species '{}' already exists, skipping.", speciesFolder);
                    continue;
                }

                Files.createDirectories(outDir);

                // Copy all files under that default_species folder
                java.util.Enumeration<java.util.jar.JarEntry> en2 = jar.entries();
                while (en2.hasMoreElements()) {
                    java.util.jar.JarEntry je = en2.nextElement();
                    if (je.isDirectory()) continue;

                    String name = je.getName();
                    String prefix = JAR_PREFIX + speciesFolder + "/";
                    if (!name.startsWith(prefix)) continue;

                    String rel = name.substring(prefix.length()); // e.g. textures/a.png
                    Path outFile = outDir.resolve(rel);
                    Files.createDirectories(outFile.getParent());

                    // don't overwrite (but folder is new anyway)
                    try (java.io.InputStream in = jar.getInputStream(je)) {
                        Files.copy(in, outFile);
                    }
                }

                JubitusBirds.LOGGER.info("[JubitusBirds] Installed default default_species '{}'.", speciesFolder);
            }
        }
    }

    private static void extractFromDirectory(Path modRoot, Path targetRoot) throws Exception {
        // In dev, resources often end up in: <modRoot>/build/resources/main/...
        // event.getSourceFile() is usually the "classes" folder, so we search for the assets path.
        Path assets = findAssetsRoot(modRoot).resolve(Tags.MOD_ID).resolve("default_species");
        if (!Files.isDirectory(assets)) {
            JubitusBirds.LOGGER.warn("[JubitusBirds] No default_species folder found in dev resources: {}", assets);
            return;
        }

        try (java.util.stream.Stream<Path> speciesDirs = Files.list(assets)) {
            speciesDirs.filter(Files::isDirectory).forEach(speciesDir -> {
                String speciesFolder = speciesDir.getFileName().toString();
                Path outDir = targetRoot.resolve(speciesFolder);

                if (Files.isDirectory(outDir)) {
                    JubitusBirds.LOGGER.info("[JubitusBirds] Default default_species '{}' already exists, skipping.", speciesFolder);
                    return;
                }

                try {
                    Files.createDirectories(outDir);

                    Files.walk(speciesDir).forEach(p -> {
                        try {
                            if (Files.isDirectory(p)) return;
                            Path rel = speciesDir.relativize(p);
                            Path outFile = outDir.resolve(rel);
                            Files.createDirectories(outFile.getParent());
                            Files.copy(p, outFile);
                        } catch (Exception ex) {
                            JubitusBirds.LOGGER.warn("[JubitusBirds] Failed copying default file {}", p, ex);
                        }
                    });

                    JubitusBirds.LOGGER.info("[JubitusBirds] Installed default default_species '{}'.", speciesFolder);

                } catch (Exception e) {
                    JubitusBirds.LOGGER.warn("[JubitusBirds] Failed installing default default_species '{}'.", speciesFolder, e);
                }
            });
        }
    }

    private static Path findAssetsRoot(Path from) {
        // Try a few common dev locations
        Path[] candidates = new Path[]{
                from.resolve("assets"),
                from.resolve("resources").resolve("main").resolve("assets"),
                from.resolve("build").resolve("resources").resolve("main").resolve("assets"),
                from.getParent() != null ? from.getParent().resolve("resources").resolve("main").resolve("assets") : null,
                from.getParent() != null ? from.getParent().resolve("build").resolve("resources").resolve("main").resolve("assets") : null,
        };

        for (Path c : candidates) {
            if (c != null && Files.isDirectory(c)) return c;
        }

        // fallback: assume "assets" exists somewhere above
        Path cur = from;
        for (int i = 0; i < 6 && cur != null; i++) {
            Path a = cur.resolve("assets");
            if (Files.isDirectory(a)) return a;
            cur = cur.getParent();
        }

        // last resort
        return from.resolve("assets");
    }
}
