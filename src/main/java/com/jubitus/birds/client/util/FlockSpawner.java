package com.jubitus.birds.client.util;

import com.jubitus.birds.client.ClientBird;
import com.jubitus.birds.client.config.BirdConfig;
import com.jubitus.birds.species.BirdSpecies;
import com.jubitus.birds.species.BirdSpeciesRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.*;

public class FlockSpawner {

    public static SpawnResult spawnForCells(World world, EntityPlayer player,
                                            long worldSeed, int dim, long window,
                                            int cellX, int cellZ, int radiusCells) {

        boolean isDay = world.isDaytime();
        SpawnResult result = new SpawnResult();

        for (int dx = -radiusCells; dx <= radiusCells; dx++) {
            for (int dz = -radiusCells; dz <= radiusCells; dz++) {

                int cx = cellX + dx;
                int cz = cellZ + dz;

                long seed = mixSeed(worldSeed, dim, cx, cz, window);
                Random rng = new Random(seed);

                // Pick default_species for THIS cell
                Biome biome = world.getBiome(new BlockPos(player.posX, 0, player.posZ));
                BirdSpecies species = BirdSpeciesRegistry.pickForBiome(biome, rng, isDay);
                if (species == null) continue;

                BirdSpecies.BirdSpeciesView view = species.viewForTime(isDay);

                // Decide flock vs singles (day/night override-aware)
                double chance = clamp01(view.flockChancePerCell());
                boolean spawnFlock = rng.nextDouble() < chance;

                if (spawnFlock) {
                    spawnOneFlock(world, player, rng, seed, result, species, view);
                } else {
                    spawnSingles(world, player, rng, seed, result, species, view);
                }
            }
        }

        return result;
    }

    // Same mixer you already use (copy-paste OK)
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

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static void spawnOneFlock(World world, EntityPlayer player, Random rng, long seed,
                                      SpawnResult out, BirdSpecies species, BirdSpecies.BirdSpeciesView view) {

        long flockId = mixSeed(seed, 999, 7, 0, 0);

        // One shared spawn point for the whole flock (uses default_species day/night altitude+speed)
        FlockSpawn base = createFlockSpawn(world, player, rng, view);

        // Flock uses the base travel direction
        Flock flock = new Flock(flockId, base.baseDir);
        out.flocks.put(flockId, flock);

        int size = chooseFlockSize(world, rng, species); // you can make this view-aware later

        for (int i = 0; i < size; i++) {
            long birdId = mixSeed(seed, i, 2, 0, 0);

            double spread = (size <= 10)
                    ? (3.0 + rng.nextDouble() * 8.0)
                    : (6.0 + rng.nextDouble() * 18.0);

            double a = rng.nextDouble() * Math.PI * 2.0;
            double ox = Math.cos(a) * spread;
            double oz = Math.sin(a) * spread;
            double oy = (rng.nextDouble() - 0.5) * 3.0;

            Vec3d pos = base.centerPos.add(ox, oy, oz);

            Vec3d jitter = new Vec3d((rng.nextDouble() - 0.5) * 0.15, 0, (rng.nextDouble() - 0.5) * 0.15);
            Vec3d dir = base.baseDir.add(jitter).normalize();

            double speed = base.baseSpeed * (0.9 + rng.nextDouble() * 0.2);

            ClientBird b = new ClientBird(world, species, birdId, pos, dir, speed);
            b.flockId = flockId;

            out.birds.add(b);
        }
    }

    private static void spawnSingles(World world, EntityPlayer player, Random rng, long seed,
                                     SpawnResult out, BirdSpecies species, BirdSpecies.BirdSpeciesView view) {

        int max = Math.max(0, view.birdsPerCellMax());
        int count = rng.nextInt(max + 1);

        for (int i = 0; i < count; i++) {
            long birdId = mixSeed(seed, i, 1, 0, 0);
            ClientBird b = spawnBird(world, player, rng, birdId, 0L, null, species, view);
            out.birds.add(b);
        }
    }

    private static FlockSpawn createFlockSpawn(World world, EntityPlayer player, Random rng, BirdSpecies.BirdSpeciesView view) {
        FlockSpawn s = new FlockSpawn();

        int viewChunks = net.minecraft.client.Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
        double viewBorder = viewChunks * 16.0;

        double spawnDist = viewBorder + BirdConfig.spawnBorderBuffer + rng.nextDouble() * 64.0;
        double angle = rng.nextDouble() * Math.PI * 2.0;

        double sx = player.posX + Math.cos(angle) * spawnDist;
        double sz = player.posZ + Math.sin(angle) * spawnDist;

        double ground = world.getHeight(new BlockPos(sx, 0, sz)).getY();
        double above = BirdSteering.lerp(view.minAltitudeAboveGround(), view.maxAltitudeAboveGround(), rng.nextDouble());
        double sy = ground + above;

        s.centerPos = new Vec3d(sx, sy, sz);

        Vec3d outward = new Vec3d(sx - player.posX, 0, sz - player.posZ);
        if (outward.lengthSquared() < 1e-6) outward = new Vec3d(0, 0, 1);
        outward = outward.normalize();

        Vec3d inward = outward.scale(-1.0);
        Vec3d side = new Vec3d(-outward.z, 0, outward.x);

        double sideAmt = (rng.nextDouble() - 0.5) * 1.2;
        s.baseDir = inward.add(side.scale(sideAmt)).normalize();

        s.baseSpeed = BirdSteering.lerp(view.minSpeed(), view.maxSpeed(), rng.nextDouble());
        return s;
    }

    private static int chooseFlockSize(World world, Random rng, BirdSpecies species) {
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

    private static ClientBird spawnBird(World world, EntityPlayer player, Random rng,
                                        long birdId, long flockId, Flock flock,
                                        BirdSpecies species, BirdSpecies.BirdSpeciesView view) {

        int viewChunks = net.minecraft.client.Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
        double viewBorder = viewChunks * 16.0;
        double spawnDist = viewBorder + BirdConfig.spawnBorderBuffer + rng.nextDouble() * 64.0;

        double angle = rng.nextDouble() * Math.PI * 2.0;
        double sx = player.posX + Math.cos(angle) * spawnDist;
        double sz = player.posZ + Math.sin(angle) * spawnDist;

        if (flockId != 0L) {
            double spread = 14.0 + rng.nextDouble() * 22.0;
            double a2 = rng.nextDouble() * Math.PI * 2.0;
            sx += Math.cos(a2) * spread;
            sz += Math.sin(a2) * spread;
        }

        BlockPos bp = new BlockPos(sx, 0, sz);
        double ground = world.getHeight(bp).getY();

        double above = BirdSteering.lerp(view.minAltitudeAboveGround(), view.maxAltitudeAboveGround(), rng.nextDouble());
        double sy = ground + above;

        Vec3d pos = new Vec3d(sx, sy, sz);

        Vec3d dir;
        if (flock != null) {
            Vec3d gf = flock.getGroupForward();
            Vec3d jitter = new Vec3d((rng.nextDouble() - 0.5) * 0.25, 0, (rng.nextDouble() - 0.5) * 0.25);
            dir = gf.add(jitter).normalize();
        } else {
            Vec3d outward = new Vec3d(sx - player.posX, 0, sz - player.posZ);
            if (outward.lengthSquared() < 1e-6) outward = new Vec3d(0, 0, 1);
            outward = outward.normalize();

            Vec3d inward = outward.scale(-1.0);
            Vec3d side = new Vec3d(-outward.z, 0, outward.x);

            double sideAmt = (rng.nextDouble() - 0.5) * 1.2;
            dir = inward.add(side.scale(sideAmt)).normalize();
        }

        double speed = BirdSteering.lerp(view.minSpeed(), view.maxSpeed(), rng.nextDouble());
        ClientBird b = new ClientBird(world, species, birdId, pos, dir, speed);

        b.flockId = flockId;
        return b;
    }

    public static class SpawnResult {
        public final List<ClientBird> birds = new ArrayList<>();
        public final Map<Long, Flock> flocks = new HashMap<>();
    }

    private static class FlockSpawn {
        Vec3d centerPos;
        Vec3d baseDir;
        double baseSpeed;
    }


}
