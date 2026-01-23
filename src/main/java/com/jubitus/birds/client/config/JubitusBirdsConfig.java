package com.jubitus.birds.client.config;


import com.jubitus.jubitusbirds.Tags;
import net.minecraftforge.common.config.Config;

@Config(modid = Tags.MOD_ID, name = "jubitusbirds")
public class JubitusBirdsConfig {

    @Config.Name("borders")
    public static final Borders BORDERS = new Borders();

    @Config.Name("spawning")
    public static final Spawning SPAWNING = new Spawning();

    @Config.Name("altitude")
    public static final Altitude ALTITUDE = new Altitude();

    @Config.Name("flight")
    public static final Flight FLIGHT = new Flight();

    @Config.Name("behavior")
    public static final Behavior BEHAVIOR = new Behavior();

    @Config.Name("circling")
    public static final Circling CIRCLING = new Circling();

    public static class Borders {

        @Config.Comment({
                "Spawn buffer (in BLOCKS) outside the render-distance border.",
                "Birds spawn at: (renderDistanceChunks * 16) + spawnBorderBuffer (+ some random extra)."
        })
        @Config.RangeDouble(min = 0.0, max = 256.0)
        public double spawnBorderBuffer = 16.0;

        @Config.Comment({
                "Despawn buffer (in BLOCKS) beyond the render-distance border.",
                "Birds despawn after leaving: (renderDistanceChunks * 16) + despawnBorderBuffer."
        })
        @Config.RangeDouble(min = 0.0, max = 512.0)
        public double despawnBorderBuffer = 32.0;

        @Config.Comment({
                "Hard cap of birds around the player (client-side).",
                "Prevents unbounded growth."
        })
        @Config.RangeInt(min = 0, max = 2048)
        public int maxBirdsAroundPlayer = 32;

        @Config.Comment({
                "Legacy/optional distance cap (in BLOCKS) from the camera/player.",
                "If your manager uses view-border-based despawn, this may be unused."
        })
        @Config.RangeDouble(min = 0.0, max = 4096.0)
        public double despawnDistance = 256.0;
    }

    public static class Spawning {

        @Config.Comment({
                "Deterministic spawn cell size (in BLOCKS).",
                "The world is partitioned into cells; each cell may spawn birds/flocks per time window.",
                "Higher values = fewer spawn checks and broader distribution."
        })
        @Config.RangeInt(min = 16, max = 1024)
        public int spawnCellSize = 128;

        @Config.Comment({
                "Maximum number of SINGLE birds per cell per time window.",
                "Flocks have their own sizing rules."
        })
        @Config.RangeInt(min = 0, max = 64)
        public int birdsPerCellMax = 8;

        @Config.Comment({
                "Time window size (in TICKS) for deterministic spawning.",
                "Example: 20*12 = 12 seconds."
        })
        @Config.RangeInt(min = 1, max = 20 * 600)
        public int spawnTimeWindowTicks = 20 * 30;

        @Config.Comment({
                "Chance a given cell spawns a FLOCK instead of singles.",
                "If false, the cell will spawn up to birdsPerCellMax singles.",
                "NOTE: Your code currently hardcodes this in FlockSpawner; wire it in if desired."
        })
        @Config.RangeDouble(min = 0.0, max = 1.0)
        public double flockChancePerCell = 0.45;

        @Config.Comment({
                "How many cells around the player to consider for spawning (Chebyshev radius).",
                "2 = a 5x5 cell square."
        })
        @Config.RangeInt(min = 0, max = 8)
        public int spawnRadiusCells = 2;
    }

    public static class Altitude {

        @Config.Comment({
                "Minimum altitude above local ground (in BLOCKS).",
                "Birds will attempt to never go below this clearance."
        })
        @Config.RangeDouble(min = 0.0, max = 512.0)
        public double minAltitudeAboveGround = 24.0;

        @Config.Comment({
                "Maximum altitude above local ground (in BLOCKS).",
                "Birds won't intentionally cruise above this (terrain may still force higher)."
        })
        @Config.RangeDouble(min = 0.0, max = 1024.0)
        public double maxAltitudeAboveGround = 96.0;

        @Config.Comment({
                "Preferred cruising altitude above ground (in BLOCKS).",
                "The target band oscillates around this."
        })
        @Config.RangeDouble(min = 0.0, max = 1024.0)
        public double preferredAboveGround = 48.0;

        @Config.Comment({
                "How strongly birds correct vertical error toward target altitude.",
                "Higher = more aggressive climbs/descents."
        })
        @Config.RangeDouble(min = 0.0, max = 1.0)
        public double verticalAdjustStrength = 0.004;
    }

    public static class Flight {

        @Config.Comment({
                "Minimum horizontal speed (blocks/tick).",
                "0.2 = 4 blocks/sec."
        })
        @Config.RangeDouble(min = 0.01, max = 3.0)
        public double minSpeed = 0.35;

        @Config.Comment({
                "Maximum horizontal speed (blocks/tick)."
        })
        @Config.RangeDouble(min = 0.01, max = 5.0)
        public double maxSpeed = 0.6;

        @Config.Comment({
                "Maximum turn rate (degrees per tick) used by your steering limit.",
                "Lower = more smooth/soaring, higher = more twitchy."
        })
        @Config.RangeDouble(min = 0.1, max = 45.0)
        public double maxTurnDegPerTick = 4.0;

        @Config.Comment({
                "Subtle wandering/noise strength.",
                "Higher = more variation, lower = more straight/consistent."
        })
        @Config.RangeDouble(min = 0.0, max = 0.5)
        public double noiseStrength = 0.04;
    }

    public static class Behavior {

        @Config.Comment({
                "GLIDE mode duration range (ticks).",
                "Birds pick a waypoint and fly toward it for a random time in this range."
        })
        @Config.RangeInt(min = 1, max = 20 * 120)
        public int glideMinTicks = 60;

        @Config.RangeInt(min = 1, max = 20 * 120)
        public int glideMaxTicks = 140;

        @Config.Comment({
                "CIRCLE mode duration range (ticks).",
                "Birds circle around a center for a random time in this range."
        })
        @Config.RangeInt(min = 1, max = 20 * 300)
        public int circleMinTicks = 80;

        @Config.RangeInt(min = 1, max = 20 * 300)
        public int circleMaxTicks = 220;
    }

    public static class Circling {

        @Config.Comment({
                "Circle radius range (blocks) for CIRCLE mode."
        })
        @Config.RangeDouble(min = 1.0, max = 512.0)
        public double circleRadiusMin = 16.0;

        @Config.RangeDouble(min = 1.0, max = 512.0)
        public double circleRadiusMax = 64.0;
    }
}
