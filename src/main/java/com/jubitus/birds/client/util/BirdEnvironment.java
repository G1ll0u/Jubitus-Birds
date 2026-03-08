package com.jubitus.birds.client.util;

import com.jubitus.birds.species.BirdSpecies;
import net.minecraft.world.World;

public final class BirdEnvironment {
    private BirdEnvironment() {
    }

    /**
     * Combined spawn multiplier: day/night + weather + time-of-day curve
     */
    public static double spawnMultiplier(World world, BirdSpecies species, boolean isDay) {
        if (world == null || species == null) return 1.0;

        BirdSpecies.BirdSpeciesView v = species.viewForTime(isDay);

        double mul = 1.0;

        // day/night config multiplier (stored in day/night override blocks)
        mul *= safeMul(v.spawnMul(), 0.0, 8.0, 1.0);

        // weather multipliers (per-species)
        if (isStorm(world))
            mul *= safeMul(species.weather != null ? species.weather.stormSpawnMul : 1.0, 0.0, 8.0, 1.0);
        else if (isRain(world))
            mul *= safeMul(species.weather != null ? species.weather.rainSpawnMul : 1.0, 0.0, 8.0, 1.0);

        // realism: dawn boost + noon dip
        mul *= timeSpawnCurve(world, isDay);

        return clamp(mul, 0.0, 8.0);
    }

    private static double safeMul(double v, double lo, double hi, double def) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return def;
        return clamp(v, lo, hi);
    }

    public static boolean isStorm(World world) {
        return world != null && world.isThundering();
    }

    public static boolean isRain(World world) {
        return world != null && world.isRaining();
    }

    private static double timeSpawnCurve(World world, boolean isDay) {
        long t = world.getWorldTime() % 24000L;

        // Dawn peaks around 0 and 24000
        double dawn = gaussian(t, 0.0, 900.0) + gaussian(t, 24000.0, 900.0);

        // Noon dip around 6000
        double noon = gaussian(t, 6000.0, 2200.0);

        double mul = 1.0 + 0.20 * dawn - 0.12 * noon;

        // slight general reduction at night (species can override via night.spawnMul)
        if (!isDay) mul *= 0.75;

        return clamp(mul, 0.0, 2.0);
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static double gaussian(double t, double center, double width) {
        double x = (t - center) / width;
        return Math.exp(-x * x);
    }

    /**
     * Combined sound multiplier: day/night + weather + time-of-day curve
     */
    public static double soundMultiplier(World world, BirdSpecies species, boolean isDay) {
        if (world == null || species == null) return 1.0;

        BirdSpecies.BirdSpeciesView v = species.viewForTime(isDay);

        double mul = 1.0;

        // day/night config multiplier
        mul *= safeMul(v.soundMul(), 0.0, 8.0, 1.0);

        // weather multipliers
        if (isStorm(world))
            mul *= safeMul(species.weather != null ? species.weather.stormSoundMul : 1.0, 0.0, 8.0, 1.0);
        else if (isRain(world))
            mul *= safeMul(species.weather != null ? species.weather.rainSoundMul : 1.0, 0.0, 8.0, 1.0);

        // realism curve
        mul *= timeSoundCurve(world, isDay);

        // realism: day-only species go quiet at night even if they remain alive
        if (!isDay && !species.canSpawnAtNight) mul *= 0.05;

        return clamp(mul, 0.0, 8.0);
    }

    private static double timeSoundCurve(World world, boolean isDay) {
        long t = world.getWorldTime() % 24000L;

        double dawn = gaussian(t, 0.0, 1000.0) + gaussian(t, 24000.0, 1000.0);
        double noon = gaussian(t, 6000.0, 2400.0);

        double mul = 1.0 + 0.55 * dawn - 0.25 * noon;

        if (!isDay) mul *= 0.70;

        return clamp(mul, 0.0, 2.5);
    }
}
