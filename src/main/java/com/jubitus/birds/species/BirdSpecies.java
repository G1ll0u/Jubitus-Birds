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

    // --- Sound (per species) ---
    public boolean soundsEnabled = true;
    // --- NEW: Per-species spawning controls + constraints ---
    public SpawningSettings spawning = new SpawningSettings();

    // --- NEW: Weather multipliers (spawn + sound) ---
    public WeatherSettings weather = new WeatherSettings();

    // --- NEW: Per-species flocking tuning ---
    public FlockingSettings flocking = new FlockingSettings();

    // --- NEW: Extra movement modes (SOAR + SWOOP) ---
    public MovementSettings movement = new MovementSettings();

    // --- NEW: Individual per-bird variation ranges ---
    public VariationSettings variation = new VariationSettings();

    // --- NEW: Sound anti-spam limiter settings ---
    public SoundSpamSettings soundSpam = new SoundSpamSettings();


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
     * Relative weight when multiple species are allowed in a biome
     */
    public double spawnWeight = 1.0;

    /**
     * Per-cell max (like your birdsPerCellMax but per species)
     */
    public int birdsPerCellMax = 8;

    /**
     * Chance cell becomes a flock for THIS species
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

    public SilhouetteSettings silhouette = new SilhouetteSettings();

    public void clampAndFix() {

        // Sounds (legacy)
        if (soundPitch <= 0) soundPitch = 1.0;
        if (soundVolume < 0) soundVolume = 0.0;
        if (soundVolume > 4.0) soundVolume = 4.0;

        if (soundBaseIntervalTicks < 20) soundBaseIntervalTicks = 20;
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

        // pattern weights (legacy)
        if (patternWeightGlide <= 0 && patternWeightCircle <= 0) {
            patternWeightGlide = 1.0;
            patternWeightCircle = 0.0;
        }

        if (scale <= 0) scale = 0.45;
        if (flapSpeed <= 0) flapSpeed = 0.35;
        if (flapAmplitude < 0) flapAmplitude = 0.0;

        if (silhouette == null) silhouette = new SilhouetteSettings();
        silhouette.clamp();

        // --- NEW blocks: ensure non-null + clamp ---
        if (spawning == null) spawning = new SpawningSettings();
        spawning.clamp();

        if (weather == null) weather = new WeatherSettings();
        weather.clamp();

        if (flocking == null) flocking = new FlockingSettings();
        flocking.clamp();

        if (movement == null) movement = new MovementSettings();
        movement.clamp();

        if (variation == null) variation = new VariationSettings();
        variation.clamp();

        if (soundSpam == null) soundSpam = new SoundSpamSettings();
        soundSpam.clamp();

        // day/night override blocks must exist
        if (day == null) day = new OverrideBlock();
        if (night == null) night = new OverrideBlock();

        // clamp day/night multipliers if present
        if (day.spawnMul != null) day.spawnMul = clampD(day.spawnMul, 0.0, 8.0, 1.0);
        if (night.spawnMul != null) night.spawnMul = clampD(night.spawnMul, 0.0, 8.0, 1.0);

        if (day.soundMul != null) day.soundMul = clampD(day.soundMul, 0.0, 8.0, 1.0);
        if (night.soundMul != null) night.soundMul = clampD(night.soundMul, 0.0, 8.0, 1.0);
    }

    // small shared helper for new classes
    private static double clampD(double v, double lo, double hi, double def) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return def;
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
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

    public static class SilhouetteSettings {
        public int alphaThreshold = 12;   // 0..255
        public int smoothRadius = 1;    // 0..3
        public float densityPow = 1.8f; // higher => wings thinner
        public float bellPow = 1.2f; // higher => more taper head/tail
        public float thickGain = 0.48f;// overall thickness multiplier
        public float coreWidthFact = 0.4f; // 0..1
        public float coreThickFact = 0.80f;// 0..1

        /**
         * Optional: allow per-species ring count too.
         */
        public int ringCount = 7;           // low poly rings (>=3)

        public void clamp() {
            alphaThreshold = clampInt(alphaThreshold, 0, 250);
            smoothRadius = clampInt(smoothRadius, 0, 6);
            ringCount = clampInt(ringCount, 3, 64);

            densityPow = clampF(densityPow, 0.01f, 8.0f);
            bellPow = clampF(bellPow, 0.01f, 8.0f);

            thickGain = clampF(thickGain, 0.0f, 4.0f);
            coreWidthFact = clampF(coreWidthFact, 0.0f, 1.0f);
            coreThickFact = clampF(coreThickFact, 0.0f, 1.0f);
        }

        private static int clampInt(int v, int lo, int hi) {
            return (v < lo) ? lo : (v > hi) ? hi : v;
        }

        private static float clampF(float v, float lo, float hi) {
            return (v < lo) ? lo : (v > hi) ? hi : v;
        }

        /**
         * Used for caching: “same values = same key”
         */
        public int stableHash() {
            int h = 1;
            h = 31 * h + alphaThreshold;
            h = 31 * h + smoothRadius;
            h = 31 * h + ringCount;
            h = 31 * h + Float.floatToIntBits(densityPow);
            h = 31 * h + Float.floatToIntBits(bellPow);
            h = 31 * h + Float.floatToIntBits(thickGain);
            h = 31 * h + Float.floatToIntBits(coreWidthFact);
            h = 31 * h + Float.floatToIntBits(coreThickFact);
            return h;
        }

        public SilhouetteSettings copy() {
            SilhouetteSettings s = new SilhouetteSettings();
            s.alphaThreshold = this.alphaThreshold;
            s.smoothRadius = this.smoothRadius;
            s.ringCount = this.ringCount;
            s.densityPow = this.densityPow;
            s.bellPow = this.bellPow;
            s.thickGain = this.thickGain;
            s.coreWidthFact = this.coreWidthFact;
            s.coreThickFact = this.coreThickFact;
            return s;
        }
    }

    public static class OverrideBlock {
        // If a value is null, it means "no override"

        // NEW multipliers (day/night)
        public Double spawnMul;
        public Double soundMul;


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
                    soundFlockEmpty
                    && spawnMul == null
                    && soundMul == null;
        }

    }

    public static class BirdSpeciesView {
        private final BirdSpecies base;
        private final OverrideBlock o;

        public BirdSpeciesView(BirdSpecies base, OverrideBlock o) {
            this.base = base;
            this.o = (o != null) ? o : new OverrideBlock();
        }

        public double spawnMul() {
            return (o.spawnMul != null) ? o.spawnMul : 1.0;
        }

        public double soundMul() {
            return (o.soundMul != null) ? o.soundMul : 1.0;
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

    public static class SpawningSettings {
        // 0 = unlimited (uses global cap only)
        public int maxAroundPlayer = 0;

        // 0 = no cooldown
        public int cooldownAfterSpawnTicks = 0;

        // If both are 0 -> legacy spawn ring (renderDistance+buffer .. +64)
        public double minSpawnDistanceFromPlayer = 0.0;
        public double maxSpawnDistanceFromPlayer = 0.0;

        // Constraints
        public boolean requiresSkyVisible = false;
        public int minY = 0;
        public int maxY = 256;

        public void clamp() {
            if (maxAroundPlayer < 0) maxAroundPlayer = 0;
            if (maxAroundPlayer > 4096) maxAroundPlayer = 4096;

            if (cooldownAfterSpawnTicks < 0) cooldownAfterSpawnTicks = 0;
            if (cooldownAfterSpawnTicks > 20 * 60 * 60) cooldownAfterSpawnTicks = 20 * 60 * 60;

            if (Double.isNaN(minSpawnDistanceFromPlayer) || minSpawnDistanceFromPlayer < 0)
                minSpawnDistanceFromPlayer = 0.0;
            if (Double.isNaN(maxSpawnDistanceFromPlayer) || maxSpawnDistanceFromPlayer < 0)
                maxSpawnDistanceFromPlayer = 0.0;

            if (minSpawnDistanceFromPlayer > 8192.0) minSpawnDistanceFromPlayer = 8192.0;
            if (maxSpawnDistanceFromPlayer > 8192.0) maxSpawnDistanceFromPlayer = 8192.0;

            if (minY < 0) minY = 0;
            if (minY > 255) minY = 255;
            if (maxY < 0) maxY = 0;
            if (maxY > 255) maxY = 255;
            if (maxY < minY) maxY = minY;
        }
    }

    public static class WeatherSettings {
        public double rainSpawnMul = 0.55;
        public double stormSpawnMul = 0.15;

        public double rainSoundMul = 0.75;
        public double stormSoundMul = 0.25;

        public void clamp() {
            rainSpawnMul = clampD(rainSpawnMul, 0.0, 8.0, 0.55);
            stormSpawnMul = clampD(stormSpawnMul, 0.0, 8.0, 0.15);
            rainSoundMul = clampD(rainSoundMul, 0.0, 8.0, 0.75);
            stormSoundMul = clampD(stormSoundMul, 0.0, 8.0, 0.25);
        }
    }

    public static class FlockingSettings {
        public double neighborRadius = 48.0;
        public double separationRadius = 6.0;

        public double cohesionWeight = 1.35;
        public double alignmentWeight = 1.05;
        public double separationWeight = 0.65;

        public double maxForce = 0.10;

        public void clamp() {
            neighborRadius = clampD(neighborRadius, 8.0, 256.0, 48.0);
            separationRadius = clampD(separationRadius, 0.5, 128.0, 6.0);
            if (separationRadius > neighborRadius) separationRadius = neighborRadius;

            cohesionWeight = clampD(cohesionWeight, 0.0, 10.0, 1.35);
            alignmentWeight = clampD(alignmentWeight, 0.0, 10.0, 1.05);
            separationWeight = clampD(separationWeight, 0.0, 10.0, 0.65);

            maxForce = clampD(maxForce, 0.0, 2.0, 0.10);
        }
    }

    public static class MovementSettings {
        // If glide/circle weights are null -> use legacy patternWeightGlide/patternWeightCircle
        public Double glideWeight = null;
        public Double circleWeight = null;

        // New modes (default 0 = off)
        public Double soarWeight = 0.0;
        public Double swoopWeight = 0.0;

        public SoarSettings soar = new SoarSettings();
        public SwoopSettings swoop = new SwoopSettings();

        public void clamp() {
            if (glideWeight != null) glideWeight = clampD(glideWeight, 0.0, 10.0, 0.55);
            if (circleWeight != null) circleWeight = clampD(circleWeight, 0.0, 10.0, 0.45);

            if (soarWeight == null) soarWeight = 0.0;
            if (swoopWeight == null) swoopWeight = 0.0;

            soarWeight = clampD(soarWeight, 0.0, 10.0, 0.0);
            swoopWeight = clampD(swoopWeight, 0.0, 10.0, 0.0);

            if (soar == null) soar = new SoarSettings();
            if (swoop == null) swoop = new SwoopSettings();

            soar.clamp();
            swoop.clamp();
        }

        public static class SoarSettings {
            public int minTicks = 100;
            public int maxTicks = 260;

            public double radiusMin = 22.0;
            public double radiusMax = 80.0;

            // extra upward intent added to vy
            public double climbRate = 0.025;

            // speed multiplier while soaring
            public double speedMul = 0.85;

            public void clamp() {
                if (minTicks < 10) minTicks = 10;
                if (maxTicks < minTicks) maxTicks = minTicks;
                if (maxTicks > 20 * 600) maxTicks = 20 * 600;

                radiusMin = clampD(radiusMin, 4.0, 256.0, 22.0);
                radiusMax = clampD(radiusMax, radiusMin, 512.0, 80.0);

                climbRate = clampD(climbRate, 0.0, 0.20, 0.025);
                speedMul = clampD(speedMul, 0.2, 2.5, 0.85);
            }
        }

        public static class SwoopSettings {
            public int minTicks = 50;
            public int maxTicks = 120;

            // forced vy intents during phases
            public double diveRate = 0.07;
            public double pullUpRate = 0.09;

            // speed multiplier while swooping
            public double diveSpeedMul = 1.25;

            // don’t dive below this above-ground buffer (soft; terrain clamp still applies)
            public double minAboveGround = 10.0;

            public void clamp() {
                if (minTicks < 10) minTicks = 10;
                if (maxTicks < minTicks) maxTicks = minTicks;
                if (maxTicks > 20 * 600) maxTicks = 20 * 600;

                diveRate = clampD(diveRate, 0.0, 0.30, 0.07);
                pullUpRate = clampD(pullUpRate, 0.0, 0.30, 0.09);

                diveSpeedMul = clampD(diveSpeedMul, 0.2, 3.0, 1.25);
                minAboveGround = clampD(minAboveGround, 0.0, 256.0, 10.0);
            }
        }
    }

    public static class VariationSettings {
        // Each is a FRACTION: 0.05 => ±5%
        public double scaleJitter = 0.05;
        public double speedJitter = 0.07;
        public double flapAmpJitter = 0.20;
        public double flapSpeedJitter = 0.15;
        public double callPitchJitter = 0.04;

        public void clamp() {
            scaleJitter = clampD(scaleJitter, 0.0, 0.90, 0.05);
            speedJitter = clampD(speedJitter, 0.0, 0.90, 0.07);
            flapAmpJitter = clampD(flapAmpJitter, 0.0, 0.90, 0.20);
            flapSpeedJitter = clampD(flapSpeedJitter, 0.0, 0.90, 0.15);
            callPitchJitter = clampD(callPitchJitter, 0.0, 0.90, 0.04);
        }
    }

    public static class SoundSpamSettings {
        public int maxCallsPerRadius = 2;
        public double callRadiusBlocks = 48.0;
        public int callCooldownTicks = 80;

        public void clamp() {
            if (maxCallsPerRadius < 0) maxCallsPerRadius = 0;
            if (maxCallsPerRadius > 64) maxCallsPerRadius = 64;

            callRadiusBlocks = clampD(callRadiusBlocks, 0.0, 256.0, 48.0);

            if (callCooldownTicks < 0) callCooldownTicks = 0;
            if (callCooldownTicks > 20 * 60) callCooldownTicks = 20 * 60;
        }
    }

}
