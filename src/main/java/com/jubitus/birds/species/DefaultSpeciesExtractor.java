package com.jubitus.birds.species;

import com.jubitus.birds.JubitusBirds;
import com.jubitus.jubitusbirds.Tags;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DefaultSpeciesExtractor {
    // Where defaults live INSIDE the JAR
    private static final String JAR_PREFIX = "assets/" + Tags.MOD_ID + "/species/";

    private DefaultSpeciesExtractor() {
    }

    /**
     * If /config/jubitus/default_species exists:
     *  - zip it to /config/jubitus/backup_old_species.zip
     *  - delete the folder
     */
    public static void backupAndDeleteOldDefaultSpecies(FMLPreInitializationEvent event) {
        Path configDir = event.getModConfigurationDirectory().toPath(); // /config
        Path oldDir = configDir.resolve("jubitusbirds").resolve("default_species");
        if (!Files.isDirectory(oldDir)) return;

        Path zipOut = configDir.resolve("jubitusbirds").resolve("backup_old_species.zip");

        try {
            Files.createDirectories(zipOut.getParent());

            // Overwrite if it exists
            if (Files.exists(zipOut)) {
                Files.delete(zipOut);
            }

            zipDirectory(oldDir, zipOut);
            deleteDirectoryRecursive(oldDir);

            JubitusBirds.LOGGER.info("[JubitusBirds] Backed up old default species to {} and deleted {}",
                    zipOut.toAbsolutePath(), oldDir.toAbsolutePath());

        } catch (Exception e) {
            JubitusBirds.LOGGER.warn("[JubitusBirds] Failed to backup+delete old default species folder: {}",
                    oldDir.toAbsolutePath(), e);
        }
    }

    private static void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (java.io.OutputStream fos = Files.newOutputStream(zipFile);
             java.io.BufferedOutputStream bos = new java.io.BufferedOutputStream(fos);
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos)) {

            // Ensure consistent relative paths inside the zip
            final Path base = sourceDir;

            Files.walk(sourceDir).forEach(p -> {
                try {
                    if (Files.isDirectory(p)) return;

                    Path rel = base.relativize(p);
                    String entryName = rel.toString().replace('\\', '/'); // zip standard

                    java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
                    entry.setTime(Files.getLastModifiedTime(p).toMillis());
                    zos.putNextEntry(entry);

                    Files.copy(p, zos);
                    zos.closeEntry();

                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            });
        } catch (RuntimeException re) {
            if (re.getCause() instanceof IOException) throw (IOException) re.getCause();
            throw re;
        }
    }

    private static void deleteDirectoryRecursive(Path dir) throws IOException {
        // Delete files first, then directories
        Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                });
    }

    public static void extractMissingDefaults(FMLPreInitializationEvent event) {
        try {
            File cfgDir = event.getModConfigurationDirectory(); // .../.minecraft/config
            Path targetRoot = cfgDir.toPath().resolve("jubitusbirds").resolve("species");
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
            JubitusBirds.LOGGER.warn("[JubitusBirds] Failed extracting default species.", e);
        }
    }

    private static void extractFromJar(File jarFile, Path targetRoot) throws Exception {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {

            // Discover species folder names contained in defaults
            java.util.Set<String> bundledSpecies = new java.util.HashSet<>();

            java.util.Enumeration<java.util.jar.JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                java.util.jar.JarEntry je = en.nextElement();
                if (je.isDirectory()) continue;

                String name = je.getName(); // e.g. assets/jubitusbirds/species/robin/properties.json
                if (!name.startsWith(JAR_PREFIX)) continue;

                String rest = name.substring(JAR_PREFIX.length());
                int slash = rest.indexOf('/');
                if (slash <= 0) continue;

                String speciesFolder = rest.substring(0, slash);
                bundledSpecies.add(speciesFolder);
            }

            // Copy each species if the folder doesn't exist yet
            for (String speciesFolder : bundledSpecies) {
                Path outDir = targetRoot.resolve(speciesFolder);
                if (Files.isDirectory(outDir)) {
                    JubitusBirds.LOGGER.info("[JubitusBirds] Default species '{}' already exists, skipping.", speciesFolder);
                    continue;
                }

                Files.createDirectories(outDir);

                // Copy all files under that species folder
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

                JubitusBirds.LOGGER.info("[JubitusBirds] Installed default species '{}'.", speciesFolder);
            }
        }
    }

    private static void extractFromDirectory(Path modRoot, Path targetRoot) throws Exception {
        // In dev, resources often end up in: <modRoot>/build/resources/main/...
        // event.getSourceFile() is usually the "classes" folder, so we search for the assets path.
        Path assets = findAssetsRoot(modRoot).resolve(Tags.MOD_ID).resolve("species");
        if (!Files.isDirectory(assets)) {
            JubitusBirds.LOGGER.warn("[JubitusBirds] No species folder found in dev resources: {}", assets);
            return;
        }

        try (java.util.stream.Stream<Path> speciesDirs = Files.list(assets)) {
            speciesDirs.filter(Files::isDirectory).forEach(speciesDir -> {
                String speciesFolder = speciesDir.getFileName().toString();
                Path outDir = targetRoot.resolve(speciesFolder);

                if (Files.isDirectory(outDir)) {
                    JubitusBirds.LOGGER.info("[JubitusBirds] Default species '{}' already exists, skipping.", speciesFolder);
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

                    JubitusBirds.LOGGER.info("[JubitusBirds] Installed default species '{}'.", speciesFolder);

                } catch (Exception e) {
                    JubitusBirds.LOGGER.warn("[JubitusBirds] Failed installing default species '{}'.", speciesFolder, e);
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
