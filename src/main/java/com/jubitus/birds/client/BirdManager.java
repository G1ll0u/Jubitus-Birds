package com.jubitus.birds.client;

import com.jubitus.birds.client.config.BirdConfig;
import com.jubitus.birds.client.sound.BirdSoundSystem;
import com.jubitus.birds.client.util.Flock;
import com.jubitus.birds.client.util.FlockSpawner;
import com.jubitus.birds.client.util.SpatialHash;
import com.jubitus.birds.client.util.spawn.BirdSpawnController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

public class BirdManager {

    public static BirdManager INSTANCE;

    private final Map<Long, ClientBird> birdsById = new HashMap<>();
    private final BirdSpawnController spawnController = new BirdSpawnController();
    private final Map<Long, Flock> flocksById = new HashMap<>();
    private final SpatialHash spatial = new SpatialHash(24);

    private WorldClient currentWorld;
    private int nextRuntimeEntityId = -100000;

    public BirdManager() {
        INSTANCE = this;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        EntityPlayer player = mc.player;



        if (!(world instanceof WorldClient) || player == null) {
            if (!birdsById.isEmpty() || currentWorld != null) {
                clearAllBirds();
            }
            currentWorld = null;
            return;
        }

        WorldClient worldClient = (WorldClient) world;

        int dim = worldClient.provider.getDimension();
        if (BirdConfig.isDimensionBlacklisted(dim)) {
            if (!birdsById.isEmpty()) {
                clearAllBirds();
            }
            return;
        }

        if (currentWorld != worldClient) {
            clearAllBirds();
            currentWorld = worldClient;
        }

        BirdSoundSystem.tickCleanup();

        purgeDeadAndFar(worldClient, player);
        rebuildSpatial();
        tickFlocks(worldClient);
        cleanupFlocks();
        spawnMore(worldClient, player);

        BirdSoundSystem.tickCleanup();
    }

    public void clearAllBirds() {
        BirdSoundSystem.stopAll();

        if (currentWorld != null) {
            for (ClientBird bird : birdsById.values()) {
                if (bird != null) {
                    currentWorld.removeEntityFromWorld(bird.getEntityId());
                    bird.setDead();
                }
            }
        }

        birdsById.clear();
        flocksById.clear();
        spatial.clear();
        spawnController.resetAllCooldowns();
    }

    private void purgeDeadAndFar(WorldClient world, EntityPlayer player) {
        if (birdsById.isEmpty()) return;

        int viewChunks = Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
        double viewBorder = viewChunks * 16.0;
        double despawnDist = viewBorder + BirdConfig.despawnBorderBuffer;
        double despawnDist2 = despawnDist * despawnDist;

        Vec3d cam = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);

        Iterator<Map.Entry<Long, ClientBird>> it = birdsById.entrySet().iterator();
        while (it.hasNext()) {
            ClientBird bird = it.next().getValue();
            if (bird == null) {
                it.remove();
                continue;
            }

            if (bird.isDead || bird.world != world) {
                BirdSoundSystem.stopForBird(bird.getId());
                world.removeEntityFromWorld(bird.getEntityId());
                it.remove();
                continue;
            }

            double d2 = bird.pos.squareDistanceTo(cam);
            if (bird.ageTicks > 60 && d2 > despawnDist2) {
                BirdSoundSystem.stopForBird(bird.getId());
                world.removeEntityFromWorld(bird.getEntityId());
                bird.setDead();
                it.remove();
            }
        }
    }

    private void rebuildSpatial() {
        spatial.clear();
        for (ClientBird bird : birdsById.values()) {
            if (bird != null && !bird.isDead && bird.pos != null) {
                spatial.insert(bird);
            }
        }
    }

    private void tickFlocks(World world) {
        for (Flock flock : flocksById.values()) {
            flock.tick(world);
        }
    }

    private void cleanupFlocks() {
        if (flocksById.isEmpty()) return;

        Set<Long> used = new HashSet<>();
        for (ClientBird bird : birdsById.values()) {
            if (bird != null && !bird.isDead && bird.flockId != 0L) {
                used.add(bird.flockId);
            }
        }

        flocksById.keySet().removeIf(id -> !used.contains(id));
    }

    private void spawnMore(WorldClient world, EntityPlayer player) {
        int hardCap = BirdConfig.maxBirdsAroundPlayer;
        if (hardCap <= 0) return;
        if (birdsById.size() >= hardCap) return;

        long now = world.getTotalWorldTime();

        spawnController.beginTick(
                world,
                player,
                birdsById,
                now,
                BirdConfig.softTargetBirdsAroundPlayer,
                BirdConfig.maxBirdsAroundPlayer
        );

        long worldSeed = world.getSeed();
        int dim = world.provider.getDimension();
        long window = now / BirdConfig.spawnTimeWindowTicks;

        int cell = BirdConfig.spawnCellSize;
        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);
        int cellX = floorDiv(px, cell);
        int cellZ = floorDiv(pz, cell);

        int radiusCells = BirdConfig.spawnRadiusCells;

        FlockSpawner.SpawnResult sr = FlockSpawner.spawnForCells(
                world,
                player,
                worldSeed,
                dim,
                window,
                cellX,
                cellZ,
                radiusCells,
                spawnController
        );

        flocksById.putAll(sr.flocks);

        for (ClientBird bird : sr.birds) {
            if (bird == null) continue;
            if (birdsById.containsKey(bird.getId())) continue;
            if (birdsById.size() >= hardCap) break;
            addBirdToWorld(world, bird);
        }
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    private void addBirdToWorld(WorldClient world, ClientBird bird) {
        int runtimeId = nextRuntimeEntityId--;
        if (nextRuntimeEntityId > -1) {
            nextRuntimeEntityId = -100000;
        }

        world.addEntityToWorld(runtimeId, bird);
        birdsById.put(bird.getId(), bird);
    }

    public Vec3d getFlockForward(long flockId) {
        Flock flock = flocksById.get(flockId);
        return flock != null ? flock.getGroupForward() : null;
    }

    public List<ClientBird> queryNeighbors(ClientBird bird, double radius) {
        if (bird == null || bird.pos == null) {
            return java.util.Collections.emptyList();
        }
        return spatial.queryRadius(bird.pos, radius);
    }

    public Collection<ClientBird> getAllBirdsForDebug() {
        return birdsById.values();
    }
}