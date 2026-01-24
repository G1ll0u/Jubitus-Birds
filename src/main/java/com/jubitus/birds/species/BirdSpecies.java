package com.jubitus.birds.species;

import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BirdSpecies {

    public BiomeRules biomeRules = new BiomeRules();
    public transient java.util.Set<String> resolvedWhitelistIds = new java.util.HashSet<>();
    public transient java.util.Set<String> resolvedBlacklistIds = new java.util.HashSet<>();
    public boolean canSpawnAtDay = true;
    public boolean canSpawnAtNight = false;

    public String name = "unknown";

    // --- Spawn rules ---
    public boolean enabled = true;

    /** Relative weight when multiple species are allowed in a biome */
    public double spawnWeight = 1.0;

    /** Per-cell max (like your birdsPerCellMax but per species) */
    public int birdsPerCellMax = 8;

    /** Chance cell becomes a flock for THIS species */
    public double flockChancePerCell = 0.45;

    /** Flock size range */
    public int flockMin = 3;
    public int flockMax = 10;

    /** “Sometimes big flock” chance (optional, simple) */
    public double bigFlockChanceDay = 0.35;
    public double bigFlockChanceNight = 0.10;
    public int bigFlockMin = 15;
    public int bigFlockMax = 40;

    // --- Biome rules ---
    /** Registry names like "minecraft:plains". If empty => allowed everywhere (unless blacklisted). */
    public List<String> biomeWhitelist = new ArrayList<>();
    public List<String> biomeBlacklist = new ArrayList<>();

    // --- Flight (species-specific) ---
    public double minSpeed = 0.35;
    public double maxSpeed = 0.60;

    public double maxTurnDegPerTick = 4.0;
    public double noiseStrength = 0.04;

    // Altitude above ground
    public double minAltitudeAboveGround = 24.0;
    public double maxAltitudeAboveGround = 96.0;
    public double preferredAboveGround = 48.0;
    public double verticalAdjustStrength = 0.004;

    // Behavior timing
    public int glideMinTicks = 60;
    public int glideMaxTicks = 140;
    public int circleMinTicks = 80;
    public int circleMaxTicks = 220;

    // Circling
    public double circleRadiusMin = 16.0;
    public double circleRadiusMax = 64.0;

    // Pattern weights (expand later)
    public double patternWeightGlide = 0.55;
    public double patternWeightCircle = 0.45;

    // --- Render / animation ---
    public double scale = 0.45;
    public double flapAmplitude = 0.08;
    public double flapSpeed = 0.35;

    // --- Loaded runtime textures ---
    /** Filled by loader. Each entry is a runtime-registered texture */
    public final List<ResourceLocation> textures = new ArrayList<>();

    public void clampAndFix() {
        if (glideMaxTicks < glideMinTicks) glideMaxTicks = glideMinTicks;
        if (circleMaxTicks < circleMinTicks) circleMaxTicks = circleMinTicks;

        if (maxSpeed < minSpeed) maxSpeed = minSpeed;
        if (maxAltitudeAboveGround < minAltitudeAboveGround) maxAltitudeAboveGround = minAltitudeAboveGround;
        if (circleRadiusMax < circleRadiusMin) circleRadiusMax = circleRadiusMin;

        if (Double.isNaN(flockChancePerCell)) flockChancePerCell = 0.45;
        flockChancePerCell = Math.max(0.0, Math.min(1.0, flockChancePerCell));

        if (birdsPerCellMax < 0) birdsPerCellMax = 0;

        // If both pattern weights are <= 0, fallback to glide
        if (patternWeightGlide <= 0 && patternWeightCircle <= 0) {
            patternWeightGlide = 1.0;
            patternWeightCircle = 0.0;
        }

        if (scale <= 0) scale = 0.45;
        if (flapSpeed <= 0) flapSpeed = 0.35;
        if (flapAmplitude < 0) flapAmplitude = 0.0;
    }

    /** Deterministic selection based on birdSeed */
    public ResourceLocation pickTexture(long birdSeed) {
        if (textures.isEmpty()) return null;
        Random r = new Random(birdSeed ^ 0xBEEFL);
        return textures.get(r.nextInt(textures.size()));
    }
    /** Optional overrides applied during day */
    public OverrideBlock day = new OverrideBlock();

    /** Optional overrides applied during night */
    public OverrideBlock night = new OverrideBlock();
    public BirdSpeciesView viewForTime(boolean isDay) {
        OverrideBlock o = isDay ? day : night;
        return new BirdSpeciesView(this, o);
    }




    public static class OverrideBlock {
        // If a value is null, it means "no override"

        // spawn
        public Double spawnWeight;
        public Integer birdsPerCellMax;
        public Double flockChancePerCell;
        public Integer flockMin;
        public Integer flockMax;

        // speed
        public Double minSpeed;
        public Double maxSpeed;

        // altitude
        public Double minAltitudeAboveGround;
        public Double maxAltitudeAboveGround;
        public Double preferredAboveGround;

        // behavior
        public Integer glideMinTicks;
        public Integer glideMaxTicks;
        public Integer circleMinTicks;
        public Integer circleMaxTicks;

        public Double circleRadiusMin;
        public Double circleRadiusMax;

        // feel
        public Double noiseStrength;
        public Double maxTurnDegPerTick;

        // render
        public Double scale;
        public Double flapAmplitude;
        public Double flapSpeed;

        public boolean isEmpty() {
            return spawnWeight == null &&
                    birdsPerCellMax == null &&
                    flockChancePerCell == null &&
                    flockMin == null &&
                    flockMax == null &&
                    minSpeed == null &&
                    maxSpeed == null &&
                    minAltitudeAboveGround == null &&
                    maxAltitudeAboveGround == null &&
                    preferredAboveGround == null &&
                    glideMinTicks == null &&
                    glideMaxTicks == null &&
                    circleMinTicks == null &&
                    circleMaxTicks == null &&
                    circleRadiusMin == null &&
                    circleRadiusMax == null &&
                    noiseStrength == null &&
                    maxTurnDegPerTick == null &&
                    scale == null &&
                    flapAmplitude == null &&
                    flapSpeed == null;
        }
    }
    public static class BirdSpeciesView {
        private final BirdSpecies base;
        private final OverrideBlock o;

        public BirdSpeciesView(BirdSpecies base, OverrideBlock o) {
            this.base = base;
            this.o = (o != null) ? o : new OverrideBlock();
        }

        // Spawn
        public double spawnWeight() { return (o.spawnWeight != null) ? o.spawnWeight : base.spawnWeight; }
        public int birdsPerCellMax() { return (o.birdsPerCellMax != null) ? o.birdsPerCellMax : base.birdsPerCellMax; }
        public double flockChancePerCell() { return (o.flockChancePerCell != null) ? o.flockChancePerCell : base.flockChancePerCell; }
        public int flockMin() { return (o.flockMin != null) ? o.flockMin : base.flockMin; }
        public int flockMax() { return (o.flockMax != null) ? o.flockMax : base.flockMax; }

        // Flight feel
        public double minSpeed() { return (o.minSpeed != null) ? o.minSpeed : base.minSpeed; }
        public double maxSpeed() { return (o.maxSpeed != null) ? o.maxSpeed : base.maxSpeed; }
        public double maxTurnDegPerTick() { return (o.maxTurnDegPerTick != null) ? o.maxTurnDegPerTick : base.maxTurnDegPerTick; }
        public double noiseStrength() { return (o.noiseStrength != null) ? o.noiseStrength : base.noiseStrength; }

        // Altitude
        public double minAltitudeAboveGround() { return (o.minAltitudeAboveGround != null) ? o.minAltitudeAboveGround : base.minAltitudeAboveGround; }
        public double maxAltitudeAboveGround() { return (o.maxAltitudeAboveGround != null) ? o.maxAltitudeAboveGround : base.maxAltitudeAboveGround; }
        public double preferredAboveGround() { return (o.preferredAboveGround != null) ? o.preferredAboveGround : base.preferredAboveGround; }
        public double verticalAdjustStrength() { return base.verticalAdjustStrength; } // keep simple unless you want override too

        // Behavior timing
        public int glideMinTicks() { return (o.glideMinTicks != null) ? o.glideMinTicks : base.glideMinTicks; }
        public int glideMaxTicks() { return (o.glideMaxTicks != null) ? o.glideMaxTicks : base.glideMaxTicks; }
        public int circleMinTicks() { return (o.circleMinTicks != null) ? o.circleMinTicks : base.circleMinTicks; }
        public int circleMaxTicks() { return (o.circleMaxTicks != null) ? o.circleMaxTicks : base.circleMaxTicks; }

        // Circling
        public double circleRadiusMin() { return (o.circleRadiusMin != null) ? o.circleRadiusMin : base.circleRadiusMin; }
        public double circleRadiusMax() { return (o.circleRadiusMax != null) ? o.circleRadiusMax : base.circleRadiusMax; }

        // Render
        public double scale() { return (o.scale != null) ? o.scale : base.scale; }
        public double flapAmplitude() { return (o.flapAmplitude != null) ? o.flapAmplitude : base.flapAmplitude; }
        public double flapSpeed() { return (o.flapSpeed != null) ? o.flapSpeed : base.flapSpeed; }

        public BirdSpecies base() { return base; }
    }
    public static class BiomeRules {

        // --- Temperature numeric range (0.0..2.0 is typical in vanilla; mods may vary) ---
        public Double temperatureMin = null;
        public Double temperatureMax = null;

        // --- Temp category (Biome.TempCategory) ---
        // Allowed values: "OCEAN", "WARM", "MEDIUM", "COLD"
        public java.util.List<String> temperatureCategoryWhitelist = new java.util.ArrayList<>();

        // --- Simple flags ---
        // If null -> ignored
        public Boolean requiresRain = null;
        public Boolean requiresSnow = null;

        // Oceanic means "BiomeDictionary.Type.OCEAN" (works for modded ocean biomes too)
        public Boolean requiresOceanic = null;

        // --- Forge BiomeManager type ---
        // Allowed values: "COOL", "WARM", "DESERT", "ICY"
        public java.util.List<String> biomeManagerTypeWhitelist = new java.util.ArrayList<>();

        // --- Forge BiomeDictionary types ---
        // Example: "FOREST", "PLAINS", "HILLS", "MOUNTAIN", "OCEAN", "HOT", "COLD", "WET", "DRY", ...
        public java.util.List<String> biomeDictionaryWhitelist = new java.util.ArrayList<>();
        public java.util.List<String> biomeDictionaryBlacklist = new java.util.ArrayList<>();

        // Resolved/transient sets (filled by loader so checks are fast)
        public transient java.util.Set<net.minecraft.world.biome.Biome.TempCategory> resolvedTempCats = new java.util.HashSet<>();
        public transient java.util.Set<net.minecraftforge.common.BiomeManager.BiomeType> resolvedBiomeManagerTypes = new java.util.HashSet<>();
        public transient java.util.Set<net.minecraftforge.common.BiomeDictionary.Type> resolvedDictWhitelist = new java.util.HashSet<>();
        public transient java.util.Set<net.minecraftforge.common.BiomeDictionary.Type> resolvedDictBlacklist = new java.util.HashSet<>();
    }

}
