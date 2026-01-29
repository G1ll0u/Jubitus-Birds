package com.jubitus.birds.species;

import com.jubitus.birds.client.sound.BirdCallType;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BirdSpecies {

    public transient String folderName = "unknown";
    public transient String soundKey = "unknown";

    // NEW: per-type sound tuning blocks (in JSON)
    public SoundSettings soundSingle = null;
    public SoundSettings soundFlock = null;

    // --- Sound (per default_species) ---
    public boolean soundsEnabled = true;


    /**
     * Base pitch. 1.0 = normal.
     */
    public double soundPitch = 1.0;

    /**
     * Base volume multiplier. 1.0 = normal.
     */
    public double soundVolume = 0.8;

    /**
     * Frequency: base interval between calls in ticks (20 ticks = 1 second).
     */
    public int soundBaseIntervalTicks = 200; // 10 seconds

    /**
     * Randomness: 0..1. 0 = regular, 1 = very irregular (±100% jitter).
     */
    public double soundRandomness = 0.35;

    /**
     * Max distance (blocks) at which the call can be heard.
     */
    public double soundMaxDistance = 48.0;

    /**
     * Distance (blocks) at which fading begins (full volume until here).
     */
    public double soundFadeStart = 12.0;

    /**
     * Fade curve power. 1=linear, 2=stronger fade, 0.5=gentler fade.
     */
    public double soundFadePower = 1.0;


    public BiomeRules biomeRules = new BiomeRules();
    public transient java.util.Set<String> resolvedWhitelistIds = new java.util.HashSet<>();
    public transient java.util.Set<String> resolvedBlacklistIds = new java.util.HashSet<>();
    public boolean canSpawnAtDay = true;
    public boolean canSpawnAtNight = false;

    public String name = "unknown";

    // --- Spawn rules ---
    public boolean enabled = true;

    /**
     * Relative weight when multiple default_species are allowed in a biome
     */
    public double spawnWeight = 1.0;

    /**
     * Per-cell max (like your birdsPerCellMax but per default_species)
     */
    public int birdsPerCellMax = 8;

    /**
     * Chance cell becomes a flock for THIS default_species
     */
    public double flockChancePerCell = 0.45;

    /**
     * Flock size range
     */
    public int flockMin = 3;
    public int flockMax = 10;

    /**
     * “Sometimes big flock” chance (optional, simple)
     */
    public double bigFlockChanceDay = 0.35;
    public double bigFlockChanceNight = 0.10;
    public int bigFlockMin = 15;
    public int bigFlockMax = 40;

    // --- Biome rules ---
    /**
     * Registry names like "minecraft:plains". If empty => allowed everywhere (unless blacklisted).
     */
    public List<String> biomeWhitelist = new ArrayList<>();
    public List<String> biomeBlacklist = new ArrayList<>();

    // --- Flight (default_species-specific) ---
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
    /**
     * Filled by loader. Each entry is a runtime-registered texture
     */
    public List<ResourceLocation> textures = new ArrayList<>();
    /**
     * Optional overrides applied during day
     */
    public OverrideBlock day = new OverrideBlock();
    /**
     * Optional overrides applied during night
     */
    public OverrideBlock night = new OverrideBlock();

    public void clampAndFix() {

        // Sounds
        if (soundPitch <= 0) soundPitch = 1.0;
        if (soundVolume < 0) soundVolume = 0.0;
        if (soundVolume > 4.0) soundVolume = 4.0; // allow “power” but avoid insane values

        if (soundBaseIntervalTicks < 20) soundBaseIntervalTicks = 20; // at least 1s
        if (Double.isNaN(soundRandomness)) soundRandomness = 0.35;
        soundRandomness = Math.max(0.0, Math.min(1.0, soundRandomness));

        if (Double.isNaN(soundMaxDistance) || soundMaxDistance < 1.0) soundMaxDistance = 48.0;
        if (Double.isNaN(soundFadeStart) || soundFadeStart < 0.0) soundFadeStart = 12.0;
        if (soundFadeStart > soundMaxDistance) soundFadeStart = soundMaxDistance * 0.5;

        if (Double.isNaN(soundFadePower) || soundFadePower <= 0.01) soundFadePower = 1.0;
        if (soundFadePower > 8.0) soundFadePower = 8.0;

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

    /**
     * Deterministic selection based on birdSeed
     */
    public ResourceLocation pickTexture(long birdSeed) {
        if (textures.isEmpty()) return null;
        Random r = new Random(birdSeed ^ 0xBEEFL);
        return textures.get(r.nextInt(textures.size()));
    }

    public BirdSpeciesView viewForTime(boolean isDay) {
        OverrideBlock o = isDay ? day : night;
        return new BirdSpeciesView(this, o);
    }


    public static class OverrideBlock {
        // If a value is null, it means "no override"

        public SoundOverride soundSingle;
        public SoundOverride soundFlock;


        // sound
        public Boolean soundsEnabled;
        public Double soundPitch;
        public Double soundVolume;
        public Integer soundBaseIntervalTicks;
        public Double soundRandomness;

        public Double soundMaxDistance;
        public Double soundFadeStart;
        public Double soundFadePower;

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
            boolean soundSingleEmpty = (soundSingle == null) || soundSingle.isEmpty();
            boolean soundFlockEmpty = (soundFlock == null) || soundFlock.isEmpty();

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
                    soundsEnabled == null
                    && soundPitch == null
                    && soundVolume == null
                    && soundBaseIntervalTicks == null
                    && soundRandomness == null
                    && soundMaxDistance == null
                    && soundFadeStart == null
                    && soundFadePower == null &&
                    flapSpeed == null &&
                    soundSingleEmpty &&
                    soundFlockEmpty;
        }

    }

    public static class BirdSpeciesView {
        private final BirdSpecies base;
        private final OverrideBlock o;

        public BirdSpeciesView(BirdSpecies base, OverrideBlock o) {
            this.base = base;
            this.o = (o != null) ? o : new OverrideBlock();
        }

        // Sound
// Sound (global enable still uses the old system)
        public boolean soundsEnabled() {
            return (o.soundsEnabled != null) ? o.soundsEnabled : base.soundsEnabled;
        }

        /**
         * Backward-compat: existing code that calls v.soundPitch()/soundVolume() should behave like SINGLE.
         * (So other parts of your mod won’t break.)
         */
        public double soundPitch() {
            return sound(BirdCallType.SINGLE).soundPitch();
        }

        /**
         * NEW: per-type sound view
         */
        public SoundView sound(BirdCallType type) {
            if (type == BirdCallType.FLOCK) {
                SoundOverride ov = (o.soundFlock != null) ? o.soundFlock : null;
                return new SoundView(base.soundFlock, ov);
            }

            // SINGLE: allow legacy day/night override fields as a fallback if soundSingle override block is missing
            SoundOverride ov = (o.soundSingle != null) ? o.soundSingle : legacySingleOverride(o);
            return new SoundView(base.soundSingle, ov);
        }

        private static SoundOverride legacySingleOverride(OverrideBlock o) {
            if (o == null) return null;

            // If legacy override fields are all null, return null
            if (o.soundPitch == null &&
                    o.soundVolume == null &&
                    o.soundBaseIntervalTicks == null &&
                    o.soundRandomness == null &&
                    o.soundMaxDistance == null &&
                    o.soundFadeStart == null &&
                    o.soundFadePower == null) {
                return null;
            }

            SoundOverride so = new SoundOverride();
            so.soundPitch = o.soundPitch;
            so.soundVolume = o.soundVolume;
            so.soundBaseIntervalTicks = o.soundBaseIntervalTicks;
            so.soundRandomness = o.soundRandomness;
            so.soundMaxDistance = o.soundMaxDistance;
            so.soundFadeStart = o.soundFadeStart;
            so.soundFadePower = o.soundFadePower;
            // legacy had no pitchVariation override
            return so;
        }

        public double soundVolume() {
            return sound(BirdCallType.SINGLE).soundVolume();
        }

        public int soundBaseIntervalTicks() {
            return sound(BirdCallType.SINGLE).soundBaseIntervalTicks();
        }

        public double soundRandomness() {
            return sound(BirdCallType.SINGLE).soundRandomness();
        }

        public double soundMaxDistance() {
            return sound(BirdCallType.SINGLE).soundMaxDistance();
        }

        public double soundFadeStart() {
            return sound(BirdCallType.SINGLE).soundFadeStart();
        }

        public double soundFadePower() {
            return sound(BirdCallType.SINGLE).soundFadePower();
        }

        public double soundPitchVariation() {
            return sound(BirdCallType.SINGLE).soundPitchVariation();
        }

        // Spawn
        public double spawnWeight() {
            return (o.spawnWeight != null) ? o.spawnWeight : base.spawnWeight;
        }

        public int birdsPerCellMax() {
            return (o.birdsPerCellMax != null) ? o.birdsPerCellMax : base.birdsPerCellMax;
        }

        public double flockChancePerCell() {
            return (o.flockChancePerCell != null) ? o.flockChancePerCell : base.flockChancePerCell;
        }

        public int flockMin() {
            return (o.flockMin != null) ? o.flockMin : base.flockMin;
        }

        public int flockMax() {
            return (o.flockMax != null) ? o.flockMax : base.flockMax;
        }

        // Flight feel
        public double minSpeed() {
            return (o.minSpeed != null) ? o.minSpeed : base.minSpeed;
        }

        public double maxSpeed() {
            return (o.maxSpeed != null) ? o.maxSpeed : base.maxSpeed;
        }

        public double maxTurnDegPerTick() {
            return (o.maxTurnDegPerTick != null) ? o.maxTurnDegPerTick : base.maxTurnDegPerTick;
        }

        public double noiseStrength() {
            return (o.noiseStrength != null) ? o.noiseStrength : base.noiseStrength;
        }

        // Altitude
        public double minAltitudeAboveGround() {
            return (o.minAltitudeAboveGround != null) ? o.minAltitudeAboveGround : base.minAltitudeAboveGround;
        }

        public double maxAltitudeAboveGround() {
            return (o.maxAltitudeAboveGround != null) ? o.maxAltitudeAboveGround : base.maxAltitudeAboveGround;
        }

        public double preferredAboveGround() {
            return (o.preferredAboveGround != null) ? o.preferredAboveGround : base.preferredAboveGround;
        }

        public double verticalAdjustStrength() {
            return base.verticalAdjustStrength;
        } // keep simple unless you want override too

        // Behavior timing
        public int glideMinTicks() {
            return (o.glideMinTicks != null) ? o.glideMinTicks : base.glideMinTicks;
        }

        public int glideMaxTicks() {
            return (o.glideMaxTicks != null) ? o.glideMaxTicks : base.glideMaxTicks;
        }

        public int circleMinTicks() {
            return (o.circleMinTicks != null) ? o.circleMinTicks : base.circleMinTicks;
        }

        public int circleMaxTicks() {
            return (o.circleMaxTicks != null) ? o.circleMaxTicks : base.circleMaxTicks;
        }

        // Circling
        public double circleRadiusMin() {
            return (o.circleRadiusMin != null) ? o.circleRadiusMin : base.circleRadiusMin;
        }

        public double circleRadiusMax() {
            return (o.circleRadiusMax != null) ? o.circleRadiusMax : base.circleRadiusMax;
        }

        // Render
        public double scale() {
            return (o.scale != null) ? o.scale : base.scale;
        }

        public double flapAmplitude() {
            return (o.flapAmplitude != null) ? o.flapAmplitude : base.flapAmplitude;
        }

        public double flapSpeed() {
            return (o.flapSpeed != null) ? o.flapSpeed : base.flapSpeed;
        }

        public BirdSpecies base() {
            return base;
        }
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

    public static class SoundSettings {
        // Defaults match your requested safe defaults
        public double soundPitch = 1.0;
        public double soundVolume = 1.0;
        public int soundBaseIntervalTicks = 600;
        public double soundRandomness = 0.55;
        public double soundMaxDistance = 128.0;
        public double soundFadeStart = 32.0;
        public double soundFadePower = 1.5;

        // NEW: ± variation as a fraction (0.05 = ±5%)
        public double soundPitchVariation = 0.05;

        public SoundSettings copy() {
            SoundSettings s = new SoundSettings();
            s.soundPitch = this.soundPitch;
            s.soundVolume = this.soundVolume;
            s.soundBaseIntervalTicks = this.soundBaseIntervalTicks;
            s.soundRandomness = this.soundRandomness;
            s.soundMaxDistance = this.soundMaxDistance;
            s.soundFadeStart = this.soundFadeStart;
            s.soundFadePower = this.soundFadePower;
            s.soundPitchVariation = this.soundPitchVariation;
            return s;
        }
    }

    public static class SoundOverride {
        // nullable = “no override”
        public Double soundPitch;
        public Double soundVolume;
        public Integer soundBaseIntervalTicks;
        public Double soundRandomness;
        public Double soundMaxDistance;
        public Double soundFadeStart;
        public Double soundFadePower;
        public Double soundPitchVariation;

        public boolean isEmpty() {
            return soundPitch == null &&
                    soundVolume == null &&
                    soundBaseIntervalTicks == null &&
                    soundRandomness == null &&
                    soundMaxDistance == null &&
                    soundFadeStart == null &&
                    soundFadePower == null &&
                    soundPitchVariation == null;
        }
    }

    public static class SoundView {
        private final SoundSettings base;
        private final SoundOverride o;

        public SoundView(SoundSettings base, SoundOverride o) {
            this.base = (base != null) ? base : new SoundSettings();
            this.o = (o != null) ? o : new SoundOverride();
        }

        public double soundPitch() {
            return (o.soundPitch != null) ? o.soundPitch : base.soundPitch;
        }

        public double soundVolume() {
            return (o.soundVolume != null) ? o.soundVolume : base.soundVolume;
        }

        public int soundBaseIntervalTicks() {
            return (o.soundBaseIntervalTicks != null) ? o.soundBaseIntervalTicks : base.soundBaseIntervalTicks;
        }

        public double soundRandomness() {
            return (o.soundRandomness != null) ? o.soundRandomness : base.soundRandomness;
        }

        public double soundMaxDistance() {
            return (o.soundMaxDistance != null) ? o.soundMaxDistance : base.soundMaxDistance;
        }

        public double soundFadeStart() {
            return (o.soundFadeStart != null) ? o.soundFadeStart : base.soundFadeStart;
        }

        public double soundFadePower() {
            return (o.soundFadePower != null) ? o.soundFadePower : base.soundFadePower;
        }

        public double soundPitchVariation() {
            return (o.soundPitchVariation != null) ? o.soundPitchVariation : base.soundPitchVariation;
        }
    }

}
