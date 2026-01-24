package com.jubitus.birds.species;

import com.jubitus.birds.JubitusBirds;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeManager;

import java.util.Locale;

public class BiomeRuleResolver {

    public static void resolve(BirdSpecies s) {
        if (s == null || s.biomeRules == null) return;

        BirdSpecies.BiomeRules r = s.biomeRules;

        r.resolvedTempCats.clear();
        r.resolvedBiomeManagerTypes.clear();
        r.resolvedDictWhitelist.clear();
        r.resolvedDictBlacklist.clear();

        // Temp categories
        if (r.temperatureCategoryWhitelist != null) {
            for (String x : r.temperatureCategoryWhitelist) {
                Biome.TempCategory cat = parseTempCategory(x);
                if (cat != null) r.resolvedTempCats.add(cat);
                else warn(s, "temperatureCategoryWhitelist", x);
            }
        }

        // BiomeManager types
        if (r.biomeManagerTypeWhitelist != null) {
            for (String x : r.biomeManagerTypeWhitelist) {
                BiomeManager.BiomeType t = parseBiomeManagerType(x);
                if (t != null) r.resolvedBiomeManagerTypes.add(t);
                else warn(s, "biomeManagerTypeWhitelist", x);
            }
        }

        // BiomeDictionary whitelist
        if (r.biomeDictionaryWhitelist != null) {
            for (String x : r.biomeDictionaryWhitelist) {
                BiomeDictionary.Type t = parseBiomeDictionaryType(x);
                if (t != null) r.resolvedDictWhitelist.add(t);
                else warn(s, "biomeDictionaryWhitelist", x);
            }
        }

        // BiomeDictionary blacklist
        if (r.biomeDictionaryBlacklist != null) {
            for (String x : r.biomeDictionaryBlacklist) {
                BiomeDictionary.Type t = parseBiomeDictionaryType(x);
                if (t != null) r.resolvedDictBlacklist.add(t);
                else warn(s, "biomeDictionaryBlacklist", x);
            }
        }
    }

    private static void warn(BirdSpecies s, String field, String value) {
        JubitusBirds.LOGGER.warn("[JubitusBirds] Species '{}' has unknown {} entry '{}'.", s.name, field, value);
    }

    private static Biome.TempCategory parseTempCategory(String s) {
        if (s == null) return null;
        String u = s.trim().toUpperCase(Locale.ROOT);
        try {
            return Biome.TempCategory.valueOf(u);
        } catch (Exception e) {
            return null;
        }
    }

    private static BiomeManager.BiomeType parseBiomeManagerType(String s) {
        if (s == null) return null;
        String u = s.trim().toUpperCase(Locale.ROOT);
        try {
            return BiomeManager.BiomeType.valueOf(u);
        } catch (Exception e) {
            return null;
        }
    }

    private static BiomeDictionary.Type parseBiomeDictionaryType(String s) {
        if (s == null) return null;

        String u = s.trim().toUpperCase(Locale.ROOT);

        try {
            // Forge 1.12.2 way: creates or retrieves the Type
            return BiomeDictionary.Type.getType(u);
        } catch (Exception e) {
            return null;
        }
    }

}
