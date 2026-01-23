package com.jubitus.birds.client.util;

import com.jubitus.birds.client.config.BirdConfig;
import com.jubitus.birds.client.ClientBird;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class FlockSpawner {

    public static class SpawnResult {
        public final List<ClientBird> birds = new ArrayList<>();
        public final Map<Long, Flock> flocks = new HashMap<>();
    }

    public static SpawnResult spawnForCells(World world, EntityPlayer player,
                                            long worldSeed, int dim, long window, int cellX, int cellZ, int radiusCells,
                                            double flockChancePerCell)
 {
        SpawnResult result = new SpawnResult();

        for (int dx = -radiusCells; dx <= radiusCells; dx++) {
            for (int dz = -radiusCells; dz <= radiusCells; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;

                long seed = mixSeed(worldSeed, dim, cx, cz, window);
                Random rng = new Random(seed);

                // Decide if this cell spawns a flock or singles
                double chance = Math.max(0.0, Math.min(1.0, flockChancePerCell));
                boolean spawnFlock = rng.nextDouble() < chance;

                if (spawnFlock) {
                    spawnOneFlock(world, player, rng, seed, result);
                } else {
                    spawnSingles(world, player, rng, seed, result);
                }
            }
        }

        return result;
    }

    private static void spawnSingles(World world, EntityPlayer player, Random rng, long seed, SpawnResult out) {
        int count = rng.nextInt(BirdConfig.birdsPerCellMax + 1);
        for (int i = 0; i < count; i++) {
            long birdId = mixSeed(seed, i, 1, 0, 0);
            ClientBird b = spawnBird(world, player, rng, birdId, 0L, null);
            out.birds.add(b);
        }
    }

    private static void spawnOneFlock(World world, EntityPlayer player, Random rng, long seed, SpawnResult out) {
        long flockId = mixSeed(seed, 999, 7, 0, 0);

// ✅ One shared spawn point for the whole flock
        FlockSpawn base = createFlockSpawn(world, player, rng);

// ✅ Flock uses the base travel direction
        Flock flock = new Flock(flockId, base.baseDir);
        out.flocks.put(flockId, flock);


        int size = chooseFlockSize(world, player, rng);

        // ✅ One shared spawn point for the whole flock
        // Spawn members as offsets around the center
        for (int i = 0; i < size; i++) {
            long birdId = mixSeed(seed, i, 2, 0, 0);

            // Spread: small groups tighter; big flocks a bit looser
            double spread = (size <= 10)
                    ? (3.0 + rng.nextDouble() * 8.0)   // ~3–11 blocks
                    : (6.0 + rng.nextDouble() * 18.0); // ~6–24 blocks

            double a = rng.nextDouble() * Math.PI * 2.0;
            double ox = Math.cos(a) * spread;
            double oz = Math.sin(a) * spread;

            // Small vertical variation so they aren’t on a perfect plane
            double oy = (rng.nextDouble() - 0.5) * 3.0;

            Vec3d pos = base.centerPos.add(ox, oy, oz);

            // Slight dir variation per bird
            Vec3d jitter = new Vec3d((rng.nextDouble() - 0.5) * 0.15, 0, (rng.nextDouble() - 0.5) * 0.15);
            Vec3d dir = base.baseDir.add(jitter).normalize();

            // Slight speed variation
            double speed = base.baseSpeed * (0.9 + rng.nextDouble() * 0.2);

            ClientBird b = new ClientBird(world, birdId, pos, dir, speed);
            b.flockId = flockId;
            b.textureIndex = 0;

            out.birds.add(b);
        }
    }


    private static int chooseFlockSize(World world, EntityPlayer player, Random rng) {
        // Simple beginner rule: day = more birds, night = fewer
        boolean day = world.isDaytime();

        // “Sometimes big flock”
        double bigChance = day ? 0.35 : 0.10;
        if (rng.nextDouble() < bigChance) {
            return 15 + rng.nextInt(26); // 15–40
        } else {
            return 3 + rng.nextInt(8);   // 3–10
        }
    }

    private static ClientBird spawnBird(World world, EntityPlayer player, Random rng, long birdId, long flockId, Flock flock) {
        // Spawn at view border
        int viewChunks = net.minecraft.client.Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
        double viewBorder = viewChunks * 16.0;
        double spawnDist = viewBorder + BirdConfig.spawnBorderBuffer + rng.nextDouble() * 64.0;

        double angle = rng.nextDouble() * Math.PI * 2.0;
        double sx = player.posX + Math.cos(angle) * spawnDist;
        double sz = player.posZ + Math.sin(angle) * spawnDist;

        // If flock: spread members around leader spawn (random offsets)
        if (flockId != 0L) {
            double spread = 14.0 + rng.nextDouble() * 22.0; // loose formation start
            double a2 = rng.nextDouble() * Math.PI * 2.0;
            sx += Math.cos(a2) * spread;
            sz += Math.sin(a2) * spread;
        }

        BlockPos bp = new BlockPos(sx, 0, sz);
        double ground = world.getHeight(bp).getY();

        double above = BirdSteering.lerp(BirdConfig.minAltitudeAboveGround, BirdConfig.maxAltitudeAboveGround, rng.nextDouble());
        double sy = ground + above;

        Vec3d pos = new Vec3d(sx, sy, sz);

        // Direction: toward player area but with sideways offset,
        // OR for flock: use flock group heading mostly
        Vec3d dir;
        if (flock != null) {
            Vec3d gf = flock.getGroupForward();
            // small per-bird variation so they don’t look identical
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

        double speed = BirdSteering.lerp(BirdConfig.minSpeed, BirdConfig.maxSpeed, rng.nextDouble());
        ClientBird b = new ClientBird(world, birdId, pos, dir, speed);

        b.flockId = flockId;
        b.textureIndex = 0;
        return b;
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
    private static class FlockSpawn {
        Vec3d centerPos;
        Vec3d baseDir;
        double baseSpeed;
    }
    private static FlockSpawn createFlockSpawn(World world, EntityPlayer player, Random rng) {
        FlockSpawn s = new FlockSpawn();

        int viewChunks = net.minecraft.client.Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
        double viewBorder = viewChunks * 16.0;

        // Spawn at/just outside view border
        double spawnDist = viewBorder + BirdConfig.spawnBorderBuffer + rng.nextDouble() * 64.0;
        double angle = rng.nextDouble() * Math.PI * 2.0;

        double sx = player.posX + Math.cos(angle) * spawnDist;
        double sz = player.posZ + Math.sin(angle) * spawnDist;

        double ground = world.getHeight(new BlockPos(sx, 0, sz)).getY();
        double above = BirdSteering.lerp(BirdConfig.minAltitudeAboveGround, BirdConfig.maxAltitudeAboveGround, rng.nextDouble());
        double sy = ground + above;

        s.centerPos = new Vec3d(sx, sy, sz);

        // Flock direction: primarily group heading, but slightly inward so they cross the scene
        Vec3d toPlayer = new Vec3d(player.posX - sx, 0, player.posZ - sz);
        if (toPlayer.lengthSquared() < 1e-6) toPlayer = new Vec3d(0, 0, 1);
        toPlayer = toPlayer.normalize();


        Vec3d outward = new Vec3d(sx - player.posX, 0, sz - player.posZ);
        if (outward.lengthSquared() < 1e-6) outward = new Vec3d(0, 0, 1);
        outward = outward.normalize();

        Vec3d inward = outward.scale(-1.0);
        Vec3d side = new Vec3d(-outward.z, 0, outward.x); // perpendicular in XZ

        double sideAmt = (rng.nextDouble() - 0.5) * 1.2;   // how much it “misses” the player
        s.baseDir = inward.add(side.scale(sideAmt)).normalize();


        s.baseSpeed = BirdSteering.lerp(BirdConfig.minSpeed, BirdConfig.maxSpeed, rng.nextDouble());
        return s;
    }

}
