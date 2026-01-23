package com.jubitus.birds.client.config;

/**
 * Runtime cache used by your flight / spawn code.
 * Values are copied from the Forge @Config-backed JubitusBirdsConfig so changes apply in-game.
 */
public class BirdConfig {

    // Border logic (in blocks)
    public static double spawnBorderBuffer = 16.0;
    public static double despawnBorderBuffer = 32.0;
    public static int maxBirdsAroundPlayer = 32;

    // Spawning
    public static int spawnCellSize = 128;
    public static int birdsPerCellMax = 8;
    public static int spawnTimeWindowTicks = 20 * 30;

    public static double flockChancePerCell = 0.45;
    public static int spawnRadiusCells = 2;

    // Altitude
    public static double minAltitudeAboveGround = 24.0;
    public static double maxAltitudeAboveGround = 96.0;
    public static double preferredAboveGround = 48.0;
    public static double verticalAdjustStrength = 0.004;

    // Flight feel
    public static double minSpeed = 0.35;
    public static double maxSpeed = 0.6;
    public static double maxTurnDegPerTick = 4.0;
    public static double noiseStrength = 0.04;

    // Behavior timing
    public static int glideMinTicks = 60;
    public static int glideMaxTicks = 140;
    public static int circleMinTicks = 80;
    public static int circleMaxTicks = 220;

    // Circling
    public static double circleRadiusMin = 16.0;
    public static double circleRadiusMax = 64.0;

    // Despawn distance (legacy/optional)
    public static double despawnDistance = 256.0;

    public static void reloadFromGuiConfig() {
        // Borders
        spawnBorderBuffer = JubitusBirdsConfig.BORDERS.spawnBorderBuffer;
        despawnBorderBuffer = JubitusBirdsConfig.BORDERS.despawnBorderBuffer;
        maxBirdsAroundPlayer = JubitusBirdsConfig.BORDERS.maxBirdsAroundPlayer;
        despawnDistance = JubitusBirdsConfig.BORDERS.despawnDistance;

        // Spawning
        spawnCellSize = JubitusBirdsConfig.SPAWNING.spawnCellSize;
        birdsPerCellMax = JubitusBirdsConfig.SPAWNING.birdsPerCellMax;
        spawnTimeWindowTicks = JubitusBirdsConfig.SPAWNING.spawnTimeWindowTicks;

        flockChancePerCell = JubitusBirdsConfig.SPAWNING.flockChancePerCell;
        spawnRadiusCells = JubitusBirdsConfig.SPAWNING.spawnRadiusCells;

        // Altitude
        minAltitudeAboveGround = JubitusBirdsConfig.ALTITUDE.minAltitudeAboveGround;
        maxAltitudeAboveGround = JubitusBirdsConfig.ALTITUDE.maxAltitudeAboveGround;
        preferredAboveGround = JubitusBirdsConfig.ALTITUDE.preferredAboveGround;
        verticalAdjustStrength = JubitusBirdsConfig.ALTITUDE.verticalAdjustStrength;

        // Flight
        minSpeed = JubitusBirdsConfig.FLIGHT.minSpeed;
        maxSpeed = JubitusBirdsConfig.FLIGHT.maxSpeed;
        maxTurnDegPerTick = JubitusBirdsConfig.FLIGHT.maxTurnDegPerTick;
        noiseStrength = JubitusBirdsConfig.FLIGHT.noiseStrength;

        // Behavior
        glideMinTicks = JubitusBirdsConfig.BEHAVIOR.glideMinTicks;
        glideMaxTicks = JubitusBirdsConfig.BEHAVIOR.glideMaxTicks;
        circleMinTicks = JubitusBirdsConfig.BEHAVIOR.circleMinTicks;
        circleMaxTicks = JubitusBirdsConfig.BEHAVIOR.circleMaxTicks;

        // Circling
        circleRadiusMin = JubitusBirdsConfig.CIRCLING.circleRadiusMin;
        circleRadiusMax = JubitusBirdsConfig.CIRCLING.circleRadiusMax;

        // Safety clamps
        if (glideMaxTicks < glideMinTicks) glideMaxTicks = glideMinTicks;
        if (circleMaxTicks < circleMinTicks) circleMaxTicks = circleMinTicks;
        if (maxSpeed < minSpeed) maxSpeed = minSpeed;
        if (maxAltitudeAboveGround < minAltitudeAboveGround) maxAltitudeAboveGround = minAltitudeAboveGround;
        if (circleRadiusMax < circleRadiusMin) circleRadiusMax = circleRadiusMin;

        // Clamp flock chance too (just in case)
        if (Double.isNaN(flockChancePerCell)) flockChancePerCell = 0.45;
        flockChancePerCell = Math.max(0.0, Math.min(1.0, flockChancePerCell));
    }
}
