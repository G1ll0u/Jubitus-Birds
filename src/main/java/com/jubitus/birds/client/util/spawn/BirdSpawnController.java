package com.jubitus.birds.client.util.spawn;

import com.jubitus.birds.client.ClientBird;
import com.jubitus.birds.species.BirdSpecies;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class BirdSpawnController {

    private final Map<String, Integer> countBySpecies = new HashMap<>();
    private final Map<String, Integer> remainingBySpecies = new HashMap<>();
    private final Map<String, Long> cooldownUntilTick = new HashMap<>();
    private Map<Long, ClientBird> birdsByIdRef;
    private int globalRemaining = 0;
    private double cellChanceMul = 1.0;

    public void beginTick(World world, EntityPlayer player,
                          Map<Long, ClientBird> birdsById,
                          long nowTick,
                          int softTarget,
                          int hardCap) {

        this.birdsByIdRef = birdsById;

        countBySpecies.clear();
        remainingBySpecies.clear();

        int currentTotal = (birdsById != null) ? birdsById.size() : 0;

        hardCap = Math.max(0, hardCap);
        softTarget = Math.max(0, softTarget);
        if (softTarget > hardCap) softTarget = hardCap;

        globalRemaining = Math.max(0, hardCap - currentTotal);

        // global soft target -> deterministic "cell gating" probability
        if (hardCap <= 0) {
            cellChanceMul = 0.0;
        } else if (softTarget <= 0 || currentTotal < softTarget) {
            cellChanceMul = 1.0;
        } else if (hardCap == softTarget) {
            cellChanceMul = (currentTotal < hardCap) ? 1.0 : 0.0;
        } else {
            cellChanceMul = (hardCap - currentTotal) / (double) (hardCap - softTarget);
            cellChanceMul = clamp01(cellChanceMul);
        }

        // count existing birds per species
        if (birdsById != null) {
            for (ClientBird b : birdsById.values()) {
                if (b == null || b.species == null) continue;
                String key = speciesKey(b.species);
                countBySpecies.put(key, countBySpecies.getOrDefault(key, 0) + 1);
            }
        }
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static String speciesKey(BirdSpecies s) {
        if (s.folderName != null && !s.folderName.trim().isEmpty()) return s.folderName.trim();
        if (s.name != null && !s.name.trim().isEmpty()) return s.name.trim().toLowerCase(Locale.ROOT);
        return "unknown";
    }

    /**
     * Global soft target: each cell gets a deterministic chance to spawn anything
     */
    public boolean allowCell(Random rng) {
        if (rng == null) return true;
        if (cellChanceMul >= 0.999) return true;
        if (cellChanceMul <= 0.001) return false;
        return rng.nextDouble() < cellChanceMul;
    }

    public int getGlobalRemaining() {
        return globalRemaining;
    }

    public boolean isBirdKnown(long birdId) {
        return birdsByIdRef != null && birdsByIdRef.containsKey(birdId);
    }

    /**
     * Used at species selection time (so we can pick another species if this one is capped/cooldown).
     */
    public boolean canStartSpeciesSpawn(BirdSpecies species, long nowTick) {
        if (species == null) return false;
        if (globalRemaining <= 0) return false;

        String key = speciesKey(species);

        // cooldown gate
        Long until = cooldownUntilTick.get(key);
        if (until != null && nowTick < until) return false;

        // cap gate
        return remainingForSpecies(species) > 0;
    }

    public int remainingForSpecies(BirdSpecies species) {
        if (species == null) return 0;
        String key = speciesKey(species);

        Integer cached = remainingBySpecies.get(key);
        if (cached != null) return cached;

        int cap = Integer.MAX_VALUE;
        if (species.spawning != null && species.spawning.maxAroundPlayer > 0) {
            cap = species.spawning.maxAroundPlayer;
        }

        int have = countBySpecies.getOrDefault(key, 0);

        int rem;
        if (cap == Integer.MAX_VALUE) rem = Integer.MAX_VALUE;
        else rem = Math.max(0, cap - have);

        remainingBySpecies.put(key, rem);
        return rem;
    }

    /**
     * Consume exactly 1 bird from budgets. DOES NOT apply cooldown (cooldown is applied only if something spawned).
     */
    public boolean consumeOne(BirdSpecies species) {
        if (species == null) return false;
        if (globalRemaining <= 0) return false;

        String key = speciesKey(species);

        int rem = remainingForSpecies(species);
        if (rem <= 0) return false;

        if (rem != Integer.MAX_VALUE) {
            remainingBySpecies.put(key, rem - 1);
        }

        countBySpecies.put(key, countBySpecies.getOrDefault(key, 0) + 1);
        globalRemaining--;

        return true;
    }

    /**
     * Call once per spawn event (cell) if at least one bird actually spawned
     */
    public void markSpeciesSpawned(BirdSpecies species, long nowTick) {
        if (species == null || species.spawning == null) return;

        int cd = species.spawning.cooldownAfterSpawnTicks;
        if (cd <= 0) return;

        String key = speciesKey(species);
        cooldownUntilTick.put(key, nowTick + (long) cd);
    }

    public void resetAllCooldowns() {
        cooldownUntilTick.clear();
    }
}
