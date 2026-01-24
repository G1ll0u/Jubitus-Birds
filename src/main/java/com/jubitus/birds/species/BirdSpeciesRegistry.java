package com.jubitus.birds.species;

import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.*;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeManager;
import java.util.Set;

public class BirdSpeciesRegistry {

    private static final Map<String, BirdSpecies> BY_NAME = new LinkedHashMap<>();

    public static void clear() {
        BY_NAME.clear();
    }

    public static void register(BirdSpecies s) {
        BY_NAME.put(s.name.toLowerCase(Locale.ROOT), s);
    }

    public static Collection<BirdSpecies> all() {
        return BY_NAME.values();
    }


    public static BirdSpecies pickForBiome(Biome biome, Random rng, boolean isDay) {
        if (biome == null) return null;

        String biomeId = (biome.getRegistryName() != null) ? biome.getRegistryName().toString() : "";

        List<BirdSpecies> allowed = new ArrayList<>();
        double totalW = 0.0;

        for (BirdSpecies s : BY_NAME.values()) {
            if (s == null || !s.enabled) continue;
            if (!isBiomeAllowed(s, biome)) continue;
            if (s.spawnWeight <= 0) continue;
            if (isDay && !s.canSpawnAtDay) continue;
            if (!isDay && !s.canSpawnAtNight) continue;

            allowed.add(s);
            totalW += s.spawnWeight;
        }

        if (allowed.isEmpty()) return null;

        double roll = rng.nextDouble() * totalW;
        for (BirdSpecies s : allowed) {
            roll -= s.spawnWeight;
            if (roll <= 0) return s;
        }
        return allowed.get(allowed.size() - 1);
    }

    private static boolean isBiomeAllowed(BirdSpecies s, Biome biome) {
        if (biome == null) return false;

        String biomeId = (biome.getRegistryName() != null) ? biome.getRegistryName().toString() : "";

        // 1) Existing resolved blacklist/whitelist by registry id (from earlier system)
        if (s.resolvedBlacklistIds != null && s.resolvedBlacklistIds.contains(biomeId)) return false;
        if (s.resolvedWhitelistIds != null && !s.resolvedWhitelistIds.isEmpty() && !s.resolvedWhitelistIds.contains(biomeId)) {
            return false;
        }

        // 2) NEW: biomeRules (optional)
        BirdSpecies.BiomeRules r = s.biomeRules;
        if (r == null) return true;

        // Temperature numeric range
        // Note: getDefaultTemperature() exists in 1.12.2 Biome
        float temp = biome.getDefaultTemperature();
        if (r.temperatureMin != null && temp < r.temperatureMin) return false;
        if (r.temperatureMax != null && temp > r.temperatureMax) return false;

        // Temperature category whitelist
        if (r.resolvedTempCats != null && !r.resolvedTempCats.isEmpty()) {
            Biome.TempCategory cat = biome.getTempCategory();
            if (!r.resolvedTempCats.contains(cat)) return false;
        }

        // Rain flag
        if (r.requiresRain != null) {
            // In 1.12.2: canRain() is a Biome method
            boolean canRain = biome.canRain();
            if (r.requiresRain.booleanValue() != canRain) return false;
        }

        // Snow flag
        if (r.requiresSnow != null) {
            // In 1.12.2: getEnableSnow() exists
            boolean snow = biome.getEnableSnow();
            if (r.requiresSnow.booleanValue() != snow) return false;
        }

        // Oceanic (use BiomeDictionary so modded oceans work)
        if (r.requiresOceanic != null) {
            boolean isOcean = BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN);
            if (r.requiresOceanic.booleanValue() != isOcean) return false;
        }

        // Forge BiomeManager type whitelist
        if (r.resolvedBiomeManagerTypes != null && !r.resolvedBiomeManagerTypes.isEmpty()) {
            boolean ok = false;
            for (BiomeManager.BiomeType t : r.resolvedBiomeManagerTypes) {
                // This checks membership in that biome type list
                if (BiomeManager.isTypeListModded(t)) {
                    // Even if modded, BiomeManager still maintains the list
                }
                if (BiomeManager.getBiomes(t).stream().anyMatch(e -> e.biome == biome)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) return false;
        }

        // BiomeDictionary blacklist wins
        if (r.resolvedDictBlacklist != null && !r.resolvedDictBlacklist.isEmpty()) {
            for (BiomeDictionary.Type t : r.resolvedDictBlacklist) {
                if (BiomeDictionary.hasType(biome, t)) return false;
            }
        }

        // BiomeDictionary whitelist: require at least ONE match
        if (r.resolvedDictWhitelist != null && !r.resolvedDictWhitelist.isEmpty()) {
            boolean ok = false;
            for (BiomeDictionary.Type t : r.resolvedDictWhitelist) {
                if (BiomeDictionary.hasType(biome, t)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) return false;
        }

        return true;
    }


}
