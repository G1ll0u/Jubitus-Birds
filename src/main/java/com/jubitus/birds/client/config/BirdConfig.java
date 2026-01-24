package com.jubitus.birds.client.config;

/**
 * Runtime cache used by your spawn manager.
 * Values are copied from Forge @Config-backed JubitusBirdsConfig so GUI changes apply in-game.
 *
 * NOTE: All bird/species behavior is now configured per-species via /config/jubitusbirds/<species>/properties.json
 */
public class BirdConfig {

    // Borders
    public static double spawnBorderBuffer = 16.0;
    public static double despawnBorderBuffer = 32.0;
    public static int maxBirdsAroundPlayer = 32;

    // Spawning system (global)
    public static int spawnCellSize = 128;
    public static int spawnTimeWindowTicks = 20 * 30;
    public static int spawnRadiusCells = 2;

    // Legacy/optional
    public static double despawnDistance = 256.0;

    public static void reloadFromGuiConfig() {
        // Borders
        spawnBorderBuffer = JubitusBirdsConfig.BORDERS.spawnBorderBuffer;
        despawnBorderBuffer = JubitusBirdsConfig.BORDERS.despawnBorderBuffer;
        maxBirdsAroundPlayer = JubitusBirdsConfig.BORDERS.maxBirdsAroundPlayer;
        despawnDistance = JubitusBirdsConfig.BORDERS.despawnDistance;

        // Spawning system
        spawnCellSize = JubitusBirdsConfig.SPAWNING.spawnCellSize;
        spawnTimeWindowTicks = JubitusBirdsConfig.SPAWNING.spawnTimeWindowTicks;
        spawnRadiusCells = JubitusBirdsConfig.SPAWNING.spawnRadiusCells;

        // Safety clamps
        if (spawnCellSize < 16) spawnCellSize = 16;
        if (spawnTimeWindowTicks < 1) spawnTimeWindowTicks = 1;
        if (spawnRadiusCells < 0) spawnRadiusCells = 0;
    }
}
