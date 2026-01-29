package com.jubitus.birds.species;

import com.jubitus.birds.JubitusBirds;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.*;

public class BiomeNameResolver {

    /**
     * Build maps once per load.
     */
    public static ResolvedBiomeLists resolveLists(List<String> whitelist, List<String> blacklist, String speciesName) {
        Map<String, Biome> byRegistry = new HashMap<>();
        Map<String, List<Biome>> byNameNorm = new HashMap<>();

        for (Biome b : ForgeRegistries.BIOMES.getValuesCollection()) {
            if (b == null) continue;

            ResourceLocation rl = b.getRegistryName();
            if (rl != null) {
                byRegistry.put(rl.toString().toLowerCase(Locale.ROOT), b);
            }

            String n = b.getBiomeName();
            String norm = normalize(n);
            byNameNorm.computeIfAbsent(norm, k -> new ArrayList<>()).add(b);

            // also index by registry path normalized (helps modded ids)
            if (rl != null) {
                String normPath = normalize(rl.getPath());
                byNameNorm.computeIfAbsent(normPath, k -> new ArrayList<>()).add(b);
            }
        }

        Set<String> wlIds = resolveToRegistryIds(whitelist, byRegistry, byNameNorm, speciesName, "whitelist");
        Set<String> blIds = resolveToRegistryIds(blacklist, byRegistry, byNameNorm, speciesName, "blacklist");

        return new ResolvedBiomeLists(wlIds, blIds);
    }

    /**
     * Normalize: lowercase, remove punctuation, collapse whitespace.
     */
    public static String normalize(String s) {
        if (s == null) return "";
        String x = s.toLowerCase(Locale.ROOT);
        x = x.replace('+', ' '); // treat + as space
        x = x.replaceAll("[^a-z0-9\\s]", " "); // remove punctuation
        x = x.replaceAll("\\s+", " ").trim();
        return x;
    }

    private static Set<String> resolveToRegistryIds(List<String> raw,
                                                    Map<String, Biome> byRegistry,
                                                    Map<String, List<Biome>> byNameNorm,
                                                    String speciesName,
                                                    String whichList) {
        Set<String> out = new HashSet<>();
        if (raw == null) return out;

        for (String entry : raw) {
            if (entry == null) continue;
            String s = entry.trim();
            if (s.isEmpty()) continue;

            // 1) Treat as registry id if it looks like one
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.contains(":")) {
                Biome b = byRegistry.get(lower);
                if (b != null && b.getRegistryName() != null) {
                    out.add(b.getRegistryName().toString());
                    continue;
                }
                JubitusBirds.LOGGER.warn("[JubitusBirds] Species '{}' {} entry '{}' did not match any biome registry id.",
                        speciesName, whichList, s);
                continue;
            }

            // 2) Match by normalized biome name or registry path
            String norm = normalize(s);
            List<Biome> matches = byNameNorm.get(norm);

            if (matches == null || matches.isEmpty()) {
                JubitusBirds.LOGGER.warn("[JubitusBirds] Species '{}' {} entry '{}' did not match any biome name.",
                        speciesName, whichList, s);
                continue;
            }

            // Ambiguous? Warn and pick all (better UX: user probably means all variants)
            if (matches.size() > 1) {
                StringBuilder sb = new StringBuilder();
                for (Biome b : matches) {
                    if (b.getRegistryName() != null) {
                        sb.append(b.getRegistryName()).append(" (").append(b.getBiomeName()).append("), ");
                    }
                }
                JubitusBirds.LOGGER.warn("[JubitusBirds] Species '{}' {} entry '{}' matched multiple biomes: {}",
                        speciesName, whichList, s, sb.toString());
            }

            for (Biome b : matches) {
                if (b.getRegistryName() != null) {
                    out.add(b.getRegistryName().toString());
                }
            }
        }

        return out;
    }

    public static class ResolvedBiomeLists {
        public final Set<String> whitelistRegistryIds;
        public final Set<String> blacklistRegistryIds;

        public ResolvedBiomeLists(Set<String> wl, Set<String> bl) {
            this.whitelistRegistryIds = (wl != null) ? wl : new HashSet<>();
            this.blacklistRegistryIds = (bl != null) ? bl : new HashSet<>();
        }
    }
}
