package com.jubitus.birds.client.util;

import com.jubitus.birds.client.ClientBird;
import com.jubitus.birds.client.config.BirdConfig;
import com.jubitus.birds.client.util.spawn.BirdSpawnController;
import com.jubitus.birds.species.BirdSpecies;
import com.jubitus.birds.species.BirdSpeciesRegistry;

public class FlockSpawner {

    public static SpawnResult spawnForCells(net.minecraft.world.World world, net.minecraft.entity.player.EntityPlayer player,
                                            long worldSeed, int dim, long window,
                                            int cellX, int cellZ, int radiusCells,
                                            BirdSpawnController controller) {

        boolean isDay = world.isDaytime();
        long now = world.getTotalWorldTime();
        SpawnResult result = new SpawnResult();

        int cellSize = BirdConfig.spawnCellSize;

        for (int dx = -radiusCells; dx <= radiusCells; dx++) {
            for (int dz = -radiusCells; dz <= radiusCells; dz++) {

                if (controller != null && controller.getGlobalRemaining() <= 0) {
                    return result;
                }

                int cx = cellX + dx;
                int cz = cellZ + dz;

                long seed = mixSeed(worldSeed, dim, cx, cz, window);
                java.util.Random rng = new java.util.Random(seed);

                // NEW: global soft-target gating (deterministic per-cell)
                if (controller != null && !controller.allowCell(rng)) continue;

                // More realistic: sample biome at cell center (not just player's feet)
                int sampleX = cx * cellSize + (cellSize / 2);
                int sampleZ = cz * cellSize + (cellSize / 2);
                net.minecraft.world.biome.Biome biome = world.getBiome(new net.minecraft.util.math.BlockPos(sampleX, 0, sampleZ));

                // NEW: pick species with weather/time multipliers + cap/cooldown gating
                BirdSpecies species = BirdSpeciesRegistry.pickForBiomeWithContext(world, biome, rng, isDay, controller, now);
                if (species == null) continue;

                BirdSpecies.BirdSpeciesView view = species.viewForTime(isDay);

                // NEW: intensity multiplier affects "whether this cell spawns" + "how many"
                double intensity = BirdEnvironment.spawnMultiplier(world, species, isDay);
                intensity = clamp(intensity, 0.0, 8.0);
                if (intensity <= 0.0001) continue;

                // Chance gate for low intensity
                double gateChance = clamp01(intensity);
                if (rng.nextDouble() > gateChance) continue;

                // Count multiplier: if > 1 -> more birds; if < 1 -> fewer
                double countMul = Math.max(0.05, intensity);

                // flock vs singles (scaled a bit by intensity)
                double chance = clamp01(view.flockChancePerCell() * Math.min(1.0, countMul));
                boolean spawnFlock = rng.nextDouble() < chance;

                boolean spawnedAny;
                if (spawnFlock) {
                    spawnedAny = spawnOneFlock(world, player, rng, seed, result, species, view, countMul, controller, now);
                } else {
                    spawnedAny = spawnSingles(world, player, rng, seed, result, species, view, countMul, controller, now);
                }

                // NEW: apply per-species cooldown only if at least one bird actually spawned
                if (spawnedAny && controller != null) {
                    controller.markSpeciesSpawned(species, now);
                }
            }
        }

        return result;
    }

    // --------------------------
    // Helpers
    // --------------------------

    // Same mixer you already use (unchanged)
    private static long mixSeed(long a, long b, long c, long d, long e) {
        long x = a;
        x ^= (b * 0x9E3779B97F4A7C15L);
        x ^= (c * 0xC2B2AE3D27D4EB4FL);
        x ^= (d * 0x165667B19E3779F9L);
        x ^= (e * 0x85EBCA6B);
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return x;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static boolean spawnOneFlock(net.minecraft.world.World world, net.minecraft.entity.player.EntityPlayer player, java.util.Random rng, long seed,
                                         SpawnResult out, BirdSpecies species, BirdSpecies.BirdSpeciesView view,
                                         double countMul,
                                         BirdSpawnController controller,
                                         long now) {

        if (controller != null && !controller.canStartSpeciesSpawn(species, now)) return false;

        long flockId = mixSeed(seed, 999, 7, 0, 0);

        // Base spawn point (may fail constraints)
        FlockSpawn base = createFlockSpawn(world, player, rng, species, view);
        if (base == null) return false;

        int desired = chooseFlockSize(world, rng, species);
        desired = Math.max(1, (int) Math.round(desired * countMul));

        boolean any = false;
        Flock flock = null;

        for (int i = 0; i < desired; i++) {

            if (controller != null && controller.getGlobalRemaining() <= 0) break;

            long birdId = mixSeed(seed, i, 2, 0, 0);
            if (controller != null && controller.isBirdKnown(birdId)) continue;

            // If capped, stop early (don’t waste time)
            if (controller != null && controller.remainingForSpecies(species) <= 0) break;

            // Compute bird position around base
            double spread = (desired <= 10)
                    ? (3.0 + rng.nextDouble() * 8.0)
                    : (6.0 + rng.nextDouble() * 18.0);

            double a = rng.nextDouble() * Math.PI * 2.0;
            double ox = Math.cos(a) * spread;
            double oz = Math.sin(a) * spread;
            double oy = (rng.nextDouble() - 0.5) * 3.0;

            net.minecraft.util.math.Vec3d pos = base.centerPos.add(ox, oy, oz);

            // NEW: per-species spawn constraints (sky visibility + absolute Y)
            if (!passesSpawnConstraints(world, species, pos)) continue;

            // Consume budget only if the spawn is actually valid
            if (controller != null && !controller.consumeOne(species)) break;

            // Build flock object only if at least 1 bird is created
            if (!any) {
                flock = new Flock(flockId, base.baseDir);
                out.flocks.put(flockId, flock);
            }

            net.minecraft.util.math.Vec3d jitter = new net.minecraft.util.math.Vec3d((rng.nextDouble() - 0.5) * 0.15, 0, (rng.nextDouble() - 0.5) * 0.15);
            net.minecraft.util.math.Vec3d dir = base.baseDir.add(jitter).normalize();

            double speed = base.baseSpeed * (0.9 + rng.nextDouble() * 0.2);

            ClientBird b = new ClientBird(world, species, birdId, pos, dir, speed);
            b.flockId = flockId;

            out.birds.add(b);
            any = true;
        }

        return any;
    }

    private static boolean spawnSingles(net.minecraft.world.World world, net.minecraft.entity.player.EntityPlayer player, java.util.Random rng, long seed,
                                        SpawnResult out, BirdSpecies species, BirdSpecies.BirdSpeciesView view,
                                        double countMul,
                                        BirdSpawnController controller,
                                        long now) {

        if (controller != null && !controller.canStartSpeciesSpawn(species, now)) return false;

        int max = Math.max(0, view.birdsPerCellMax());
        int effMax = Math.max(0, (int) Math.round(max * countMul));

        int count = (effMax <= 0) ? 0 : rng.nextInt(effMax + 1);

        boolean any = false;

        for (int i = 0; i < count; i++) {

            if (controller != null && controller.getGlobalRemaining() <= 0) break;
            if (controller != null && controller.remainingForSpecies(species) <= 0) break;

            long birdId = mixSeed(seed, i, 1, 0, 0);
            if (controller != null && controller.isBirdKnown(birdId)) continue;

            // Create candidate first (uses RNG deterministically). If constraints fail, skip without consuming budget.
            ClientBird b = spawnBird(world, player, rng, birdId, 0L, null, species, view);
            if (b == null) continue;

            if (controller != null && !controller.consumeOne(species)) break;

            out.birds.add(b);
            any = true;
        }

        return any;
    }

    private static FlockSpawn createFlockSpawn(net.minecraft.world.World world, net.minecraft.entity.player.EntityPlayer player, java.util.Random rng,
                                               BirdSpecies species, BirdSpecies.BirdSpeciesView view) {

        int viewChunks = net.minecraft.client.Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
        double viewBorder = viewChunks * 16.0;

        double spawnDist = computeSpawnDistance(rng, species, viewBorder);

        double angle = rng.nextDouble() * Math.PI * 2.0;

        double sx = player.posX + Math.cos(angle) * spawnDist;
        double sz = player.posZ + Math.sin(angle) * spawnDist;

        double ground = world.getHeight(new net.minecraft.util.math.BlockPos(sx, 0, sz)).getY();
        double above = BirdSteering.lerp(view.minAltitudeAboveGround(), view.maxAltitudeAboveGround(), rng.nextDouble());
        double sy = ground + above;

        net.minecraft.util.math.Vec3d center = new net.minecraft.util.math.Vec3d(sx, sy, sz);

        // NEW constraints
        if (!passesSpawnConstraints(world, species, center)) return null;

        FlockSpawn s = new FlockSpawn();
        s.centerPos = center;

        net.minecraft.util.math.Vec3d outward = new net.minecraft.util.math.Vec3d(sx - player.posX, 0, sz - player.posZ);
        if (outward.lengthSquared() < 1e-6) outward = new net.minecraft.util.math.Vec3d(0, 0, 1);
        outward = outward.normalize();

        net.minecraft.util.math.Vec3d inward = outward.scale(-1.0);
        net.minecraft.util.math.Vec3d side = new net.minecraft.util.math.Vec3d(-outward.z, 0, outward.x);

        double sideAmt = (rng.nextDouble() - 0.5) * 1.2;
        s.baseDir = inward.add(side.scale(sideAmt)).normalize();

        s.baseSpeed = BirdSteering.lerp(view.minSpeed(), view.maxSpeed(), rng.nextDouble());
        return s;
    }

    private static int chooseFlockSize(net.minecraft.world.World world, java.util.Random rng, BirdSpecies species) {
        boolean day = world.isDaytime();
        double bigChance = day ? species.bigFlockChanceDay : species.bigFlockChanceNight;

        if (rng.nextDouble() < bigChance) {
            int min = Math.min(species.bigFlockMin, species.bigFlockMax);
            int max = Math.max(species.bigFlockMin, species.bigFlockMax);
            return min + rng.nextInt(max - min + 1);
        } else {
            int min = Math.min(species.flockMin, species.flockMax);
            int max = Math.max(species.flockMin, species.flockMax);
            return min + rng.nextInt(max - min + 1);
        }
    }

    private static boolean passesSpawnConstraints(net.minecraft.world.World world, BirdSpecies species, net.minecraft.util.math.Vec3d pos) {
        if (world == null || species == null || pos == null) return true;
        if (species.spawning == null) return true;

        BirdSpecies.SpawningSettings sp = species.spawning;

        // absolute Y constraint
        int y = (int) Math.floor(pos.y);
        if (y < sp.minY) return false;
        if (y > sp.maxY) return false;

        // sky visibility constraint
        if (sp.requiresSkyVisible) {
            net.minecraft.util.math.BlockPos bp = new net.minecraft.util.math.BlockPos(pos.x, pos.y, pos.z);
            return world.canSeeSky(bp);
        }

        return true;
    }

    private static ClientBird spawnBird(net.minecraft.world.World world, net.minecraft.entity.player.EntityPlayer player, java.util.Random rng,
                                        long birdId, long flockId, Flock flock,
                                        BirdSpecies species, BirdSpecies.BirdSpeciesView view) {

        int viewChunks = net.minecraft.client.Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
        double viewBorder = viewChunks * 16.0;

        double spawnDist = computeSpawnDistance(rng, species, viewBorder);
        double angle = rng.nextDouble() * Math.PI * 2.0;

        double sx = player.posX + Math.cos(angle) * spawnDist;
        double sz = player.posZ + Math.sin(angle) * spawnDist;

        if (flockId != 0L) {
            double spread = 14.0 + rng.nextDouble() * 22.0;
            double a2 = rng.nextDouble() * Math.PI * 2.0;
            sx += Math.cos(a2) * spread;
            sz += Math.sin(a2) * spread;
        }

        net.minecraft.util.math.BlockPos bp = new net.minecraft.util.math.BlockPos(sx, 0, sz);
        double ground = world.getHeight(bp).getY();

        double above = BirdSteering.lerp(view.minAltitudeAboveGround(), view.maxAltitudeAboveGround(), rng.nextDouble());
        double sy = ground + above;

        net.minecraft.util.math.Vec3d pos = new net.minecraft.util.math.Vec3d(sx, sy, sz);

        // NEW constraints
        if (!passesSpawnConstraints(world, species, pos)) return null;

        net.minecraft.util.math.Vec3d dir;
        if (flock != null) {
            net.minecraft.util.math.Vec3d gf = flock.getGroupForward();
            net.minecraft.util.math.Vec3d jitter = new net.minecraft.util.math.Vec3d((rng.nextDouble() - 0.5) * 0.25, 0, (rng.nextDouble() - 0.5) * 0.25);
            dir = gf.add(jitter).normalize();
        } else {
            net.minecraft.util.math.Vec3d outward = new net.minecraft.util.math.Vec3d(sx - player.posX, 0, sz - player.posZ);
            if (outward.lengthSquared() < 1e-6) outward = new net.minecraft.util.math.Vec3d(0, 0, 1);
            outward = outward.normalize();

            net.minecraft.util.math.Vec3d inward = outward.scale(-1.0);
            net.minecraft.util.math.Vec3d side = new net.minecraft.util.math.Vec3d(-outward.z, 0, outward.x);

            double sideAmt = (rng.nextDouble() - 0.5) * 1.2;
            dir = inward.add(side.scale(sideAmt)).normalize();
        }

        double speed = BirdSteering.lerp(view.minSpeed(), view.maxSpeed(), rng.nextDouble());
        ClientBird b = new ClientBird(world, species, birdId, pos, dir, speed);
        b.flockId = flockId;
        return b;
    }

    private static double computeSpawnDistance(java.util.Random rng, BirdSpecies species, double viewBorder) {
        double legacyMin = viewBorder + BirdConfig.spawnBorderBuffer;
        double legacyMax = legacyMin + 64.0;

        if (species == null || species.spawning == null) {
            return legacyMin + rng.nextDouble() * (legacyMax - legacyMin);
        }

        double minD = species.spawning.minSpawnDistanceFromPlayer;
        double maxD = species.spawning.maxSpawnDistanceFromPlayer;

        // If user leaves both at 0 -> legacy behavior
        if (minD <= 0.0 && maxD <= 0.0) {
            return legacyMin + rng.nextDouble() * (legacyMax - legacyMin);
        }

        if (minD < 0.0) minD = 0.0;
        if (maxD <= 0.0) maxD = Math.max(minD + 1.0, legacyMax);

        if (maxD < minD) maxD = minD;

        // safety clamp
        if (minD > 8192.0) minD = 8192.0;
        if (maxD > 8192.0) maxD = 8192.0;

        return minD + rng.nextDouble() * (maxD - minD);
    }

    public static class SpawnResult {
        public final java.util.List<ClientBird> birds = new java.util.ArrayList<>();
        public final java.util.Map<Long, Flock> flocks = new java.util.HashMap<>();
    }

    private static class FlockSpawn {
        net.minecraft.util.math.Vec3d centerPos;
        net.minecraft.util.math.Vec3d baseDir;
        double baseSpeed;
    }
}
