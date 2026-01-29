package com.jubitus.birds.client.config;


import com.jubitus.jubitusbirds.Tags;
import net.minecraftforge.common.config.Config;

@Config(modid = Tags.MOD_ID, name = "jubitusbirds/jubitusbirds")
public class JubitusBirdsConfig {

    @Config.Name("borders")
    public static final Borders BORDERS = new Borders();

    @Config.Name("spawning")
    public static final Spawning SPAWNING = new Spawning();

    @Config.Name("sound")
    public static final Sound SOUND = new Sound();

    public static class Sound {

        @Config.Comment({
                "Master volume multiplier for ALL bird calls.",
                "1.0 = normal, 0.0 = mute, 2.0 = twice as loud."
        })
        @Config.RangeDouble(min = 0.0, max = 4.0)
        public double masterBirdVolume = 1.0;

        @Config.Comment({
                "Hard clamp to prevent extreme loudness after multipliers.",
                "Final volume is clamped to this value."
        })
        @Config.RangeDouble(min = 0.0, max = 10.0)
        public double masterBirdVolumeClamp = 2.0;
        @Config.Comment({
                "How often (in ticks) we raytrace for occlusion.",
                "Lower = more responsive but more CPU.",
                "5 ticks = every 0.25s."
        })
        @Config.RangeInt(min = 1, max = 40)
        public int occlusionCheckIntervalTicks = 5;

        @Config.Comment({
                "Volume multiplier when fully occluded (behind walls / underground).",
                "1.0 = no muffling, 0.0 = silent.",
                "Typical: 0.10 - 0.35"
        })
        @Config.RangeDouble(min = 0.0, max = 1.0)
        public double occludedGain = 0.14;

        @Config.Comment({
                "How quickly occlusion transitions toward the target (0..1).",
                "Smaller = smoother/slower transitions."
        })
        @Config.RangeDouble(min = 0.01, max = 1.0)
        public double occlusionSmooth = 0.12;
        @Config.Comment({
                "Step size (blocks) for occlusion thickness sampling along the ray.",
                "Smaller = more accurate but more CPU. 0.75 is a good default."
        })
        @Config.RangeDouble(min = 0.25, max = 2.0)
        public double occlusionSampleStep = 0.75;

        @Config.Comment({
                "How strongly thickness reduces volume. Higher = mountain muffles more.",
                "Used in exp(-k * thickness). Typical 0.08 - 0.20"
        })
        @Config.RangeDouble(min = 0.01, max = 0.5)
        public double occlusionThicknessK = 0.12;

        @Config.Comment({
                "Max thickness (blocks) considered (clamp). Prevents extreme values."
        })
        @Config.RangeDouble(min = 1.0, max = 256.0)
        public double occlusionThicknessMax = 64.0;

        @Config.Comment({
                "Enable fake reflections/reverb when heavily occluded/underground."
        })
        public boolean echoEnabled = true;

        @Config.Comment({
                "Delay (ticks) for fake reflection. 6 ticks = 0.3s."
        })
        @Config.RangeInt(min = 1, max = 40)
        public int echoDelayTicks = 6;

        @Config.Comment({
                "Echo volume multiplier relative to the main sound (0..1)."
        })
        @Config.RangeDouble(min = 0.0, max = 1.0)
        public double echoVolume = 0.18;

        @Config.Comment({
                "Echo pitch multiplier (lower pitch feels more muffled)."
        })
        @Config.RangeDouble(min = 0.5, max = 1.2)
        public double echoPitchMul = 0.92;

        @Config.Comment({
                "Min thickness (blocks) required before echo triggers."
        })
        @Config.RangeDouble(min = 0.0, max = 64.0)
        public double echoMinThickness = 6.0;

        @Config.Comment({
                "Cooldown (ticks) so echo doesn't spam. 60 = 3 seconds."
        })
        @Config.RangeInt(min = 0, max = 20 * 60)
        public int echoCooldownTicks = 60;
        @Config.Comment({
                "If true: when the player's HEAD is underwater, bird sounds get heavily muffled."
        })
        public boolean underwaterMuffleEnabled = true;

        @Config.Comment({
                "Volume multiplier applied when fully underwater (0..1).",
                "0.05 = very muffled (recommended), 0.2 = mild muffling."
        })
        @Config.RangeDouble(min = 0.0, max = 1.0)
        public double underwaterGain = 0.06;

        @Config.Comment({
                "How fast underwater muffling fades in/out (0..1). Smaller = smoother."
        })
        @Config.RangeDouble(min = 0.01, max = 1.0)
        public double underwaterSmooth = 0.15;

        @Config.Comment({
                "Optional pitch multiplier underwater (lower feels more muffled). 1.0 = no change."
        })
        @Config.RangeDouble(min = 0.5, max = 1.2)
        public double underwaterPitchMul = 0.92;

    }

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
        public int maxBirdsAroundPlayer = 128;

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
