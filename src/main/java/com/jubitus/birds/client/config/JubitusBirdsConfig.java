package com.jubitus.birds.client.config;


import com.jubitus.jubitusbirds.Tags;
import net.minecraftforge.common.config.Config;

@Config(modid = Tags.MOD_ID, name = "jubitusbirds/jubitusbirds")
public class JubitusBirdsConfig {

    @Config.Name("borders")
    public static final Borders BORDERS = new Borders();

    @Config.Name("spawning")
    public static final Spawning SPAWNING = new Spawning();

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
                "Hard cap of birds around the player (client-side). Prevents unbounded growth."
        })
        @Config.RangeInt(min = 0, max = 2048)
        public int maxBirdsAroundPlayer = 32;

        @Config.Comment({
                "Legacy/optional distance cap (in BLOCKS) from the camera/player."
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
                "Time window size (in TICKS) for deterministic spawning.",
                "Example: 20*12 = 12 seconds."
        })
        @Config.RangeInt(min = 1, max = 20 * 600)
        public int spawnTimeWindowTicks = 20 * 30;

        @Config.Comment({
                "How many cells around the player to consider for spawning (Chebyshev radius).",
                "2 = a 5x5 cell square."
        })
        @Config.RangeInt(min = 0, max = 8)
        public int spawnRadiusCells = 2;
    }
}
