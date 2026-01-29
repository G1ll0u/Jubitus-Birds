package com.jubitus.birds.client.config;

/**
 * Runtime cache used by your spawn manager.
 * Values are copied from Forge @Config-backed JubitusBirdsConfig so GUI changes apply in-game.
 * <p>
 * NOTE: All bird/default_species behavior is now configured per-default_species via /config/jubitusbirds/<default_species>/properties.json
 */

public class BirdConfig {

    public static boolean underwaterMuffleEnabled = true;
    public static double underwaterGain = 0.06;
    public static double underwaterSmooth = 0.15;
    public static double underwaterPitchMul = 0.92;


    public static int occlusionCheckIntervalTicks = 5;
    public static double occludedGain = 0.14;
    public static double occlusionSmooth = 0.12;


    public static double masterBirdVolume = 1.0;
    public static double masterBirdVolumeClamp = 2.0;

    // Borders
    public static double spawnBorderBuffer = 16.0;
    public static double despawnBorderBuffer = 32.0;
    public static int maxBirdsAroundPlayer = 32;

    // Spawning system (global)
    public static int spawnCellSize = 128;
    public static int spawnTimeWindowTicks = 20 * 30;
    public static int spawnRadiusCells = 2;
    public static double occlusionSampleStep = 0.75;
    public static double occlusionThicknessK = 0.12;
    public static double occlusionThicknessMax = 64.0;

    public static boolean echoEnabled = true;
    public static int echoDelayTicks = 6;
    public static double echoVolume = 0.18;
    public static double echoPitchMul = 0.92;
    public static double echoMinThickness = 6.0;
    public static int echoCooldownTicks = 60;


    // Legacy/optional
    public static double despawnDistance = 256.0;

    public static void reloadFromGuiConfig() {

        // ✅ NEW (do this first or anywhere)
        masterBirdVolume = JubitusBirdsConfig.SOUND.masterBirdVolume;
        masterBirdVolumeClamp = JubitusBirdsConfig.SOUND.masterBirdVolumeClamp;
        occlusionCheckIntervalTicks = JubitusBirdsConfig.SOUND.occlusionCheckIntervalTicks;
        occludedGain = JubitusBirdsConfig.SOUND.occludedGain;
        occlusionSmooth = JubitusBirdsConfig.SOUND.occlusionSmooth;

        occlusionSampleStep = JubitusBirdsConfig.SOUND.occlusionSampleStep;
        occlusionThicknessK = JubitusBirdsConfig.SOUND.occlusionThicknessK;
        occlusionThicknessMax = JubitusBirdsConfig.SOUND.occlusionThicknessMax;

        echoEnabled = JubitusBirdsConfig.SOUND.echoEnabled;
        echoDelayTicks = JubitusBirdsConfig.SOUND.echoDelayTicks;
        echoVolume = JubitusBirdsConfig.SOUND.echoVolume;
        echoPitchMul = JubitusBirdsConfig.SOUND.echoPitchMul;
        echoMinThickness = JubitusBirdsConfig.SOUND.echoMinThickness;
        echoCooldownTicks = JubitusBirdsConfig.SOUND.echoCooldownTicks;


        // Borders
        spawnBorderBuffer = JubitusBirdsConfig.BORDERS.spawnBorderBuffer;
        despawnBorderBuffer = JubitusBirdsConfig.BORDERS.despawnBorderBuffer;
        maxBirdsAroundPlayer = JubitusBirdsConfig.BORDERS.maxBirdsAroundPlayer;
        despawnDistance = JubitusBirdsConfig.BORDERS.despawnDistance;

        // Spawning
        spawnCellSize = JubitusBirdsConfig.SPAWNING.spawnCellSize;
        spawnTimeWindowTicks = JubitusBirdsConfig.SPAWNING.spawnTimeWindowTicks;
        spawnRadiusCells = JubitusBirdsConfig.SPAWNING.spawnRadiusCells;
        underwaterMuffleEnabled = JubitusBirdsConfig.SOUND.underwaterMuffleEnabled;
        underwaterGain = JubitusBirdsConfig.SOUND.underwaterGain;
        underwaterSmooth = JubitusBirdsConfig.SOUND.underwaterSmooth;
        underwaterPitchMul = JubitusBirdsConfig.SOUND.underwaterPitchMul;

        // Safety clamps
        if (spawnCellSize < 16) spawnCellSize = 16;
        if (spawnTimeWindowTicks < 1) spawnTimeWindowTicks = 1;
        if (spawnRadiusCells < 0) spawnRadiusCells = 0;

        // ✅ Safety clamps for sound too
        if (masterBirdVolume < 0) masterBirdVolume = 0;
        if (masterBirdVolumeClamp < 0) masterBirdVolumeClamp = 0;

        if (occlusionCheckIntervalTicks < 1) occlusionCheckIntervalTicks = 1;
        if (occlusionCheckIntervalTicks > 40) occlusionCheckIntervalTicks = 40;

        if (occludedGain < 0) occludedGain = 0;
        if (occludedGain > 1) occludedGain = 1;

        if (occlusionSmooth < 0.01) occlusionSmooth = 0.01;
        if (occlusionSmooth > 1.0) occlusionSmooth = 1.0;

        if (occlusionSampleStep < 0.25) occlusionSampleStep = 0.25;
        if (occlusionSampleStep > 2.0) occlusionSampleStep = 2.0;

        if (occlusionThicknessK < 0.01) occlusionThicknessK = 0.01;
        if (occlusionThicknessK > 0.5) occlusionThicknessK = 0.5;

        if (occlusionThicknessMax < 1.0) occlusionThicknessMax = 1.0;
        if (occlusionThicknessMax > 256.0) occlusionThicknessMax = 256.0;

        if (echoDelayTicks < 1) echoDelayTicks = 1;
        if (echoDelayTicks > 40) echoDelayTicks = 40;

        if (echoVolume < 0) echoVolume = 0;
        if (echoVolume > 1) echoVolume = 1;

        if (echoPitchMul < 0.5) echoPitchMul = 0.5;
        if (echoPitchMul > 1.2) echoPitchMul = 1.2;

        if (echoMinThickness < 0) echoMinThickness = 0;
        if (echoMinThickness > 64) echoMinThickness = 64;

        if (echoCooldownTicks < 0) echoCooldownTicks = 0;
        if (echoCooldownTicks > 20 * 60) echoCooldownTicks = 20 * 60;
        if (underwaterGain < 0) underwaterGain = 0;
        if (underwaterGain > 1) underwaterGain = 1;

        if (underwaterSmooth < 0.01) underwaterSmooth = 0.01;
        if (underwaterSmooth > 1.0) underwaterSmooth = 1.0;

        if (underwaterPitchMul < 0.5) underwaterPitchMul = 0.5;
        if (underwaterPitchMul > 1.2) underwaterPitchMul = 1.2;

    }

}
