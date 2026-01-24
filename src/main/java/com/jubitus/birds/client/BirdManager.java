package com.jubitus.birds.client;

import com.jubitus.birds.client.config.BirdConfig;
import com.jubitus.birds.client.config.JubitusBirdsConfig;
import com.jubitus.birds.client.util.Flock;
import com.jubitus.birds.client.util.FlockSpawner;
import com.jubitus.birds.client.util.SpatialHash;
import com.jubitus.birds.render.RenderBird;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

public class BirdManager {
    public static BirdManager INSTANCE;
    private final Map<Long, ClientBird> birdsById = new HashMap<>();

    // Flocking support
    private final Map<Long, Flock> flocksById = new HashMap<>();
    private final SpatialHash spatial = new SpatialHash(24);

    public BirdManager() {
        INSTANCE = this;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        EntityPlayer player = mc.player;
        if (world == null || player == null) return;

        // --- VIEW BORDER + DESPAWN ---
        int viewChunks = mc.gameSettings.renderDistanceChunks;
        double viewBorder = viewChunks * 16.0;
        double despawnDist = viewBorder + BirdConfig.despawnBorderBuffer;
        double despawnDist2 = despawnDist * despawnDist;

        // --- UPDATE SPATIAL HASH (neighbors) ---
        spatial.clear();
        for (ClientBird b : birdsById.values()) {
            spatial.insert(b);
        }

        // --- TICK FLOCKS (group decisions) ---
        for (Flock f : flocksById.values()) {
            f.tick(world);
        }

        // --- TICK + DESPAWN BIRDS ---
        Vec3d cam = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);

        Iterator<Map.Entry<Long, ClientBird>> it = birdsById.entrySet().iterator();
        while (it.hasNext()) {
            ClientBird b = it.next().getValue();

            Vec3d flockForward = null;
            if (b.flockId != 0L) {
                Flock f = flocksById.get(b.flockId);
                if (f != null) flockForward = f.getGroupForward();
            }

            List<ClientBird> neighbors = spatial.queryNearby(b.pos);

            // ✅ CALL THE NEW TICK SIGNATURE
            b.tick(world, flockForward, neighbors);

            double d2 = b.pos.squareDistanceTo(cam);
            if (b.ageTicks > 60 && d2 > despawnDist2) {
                it.remove();
            }

        }

        // --- OPTIONAL: CLEAN UP EMPTY FLOCKS (keeps map small) ---
        cleanupFlocks();

        // --- SPAWN (ONLY IF UNDER CAP) ---
        if (birdsById.size() >= BirdConfig.maxBirdsAroundPlayer) return;
        long t = world.getWorldTime() % 24000L;
// allow spawn from 0..13000 (daytime-ish)
        if (t > 13000L) return;

        long worldSeed = world.getSeed();
        int dim = world.provider.getDimension();
        long window = world.getTotalWorldTime() / BirdConfig.spawnTimeWindowTicks;

        int cell = BirdConfig.spawnCellSize;
        int px = (int) Math.floor(player.posX);
        int pz = (int) Math.floor(player.posZ);
        int cellX = floorDiv(px, cell);
        int cellZ = floorDiv(pz, cell);

        int radiusCells = JubitusBirdsConfig.SPAWNING.spawnRadiusCells;

        FlockSpawner.SpawnResult sr = FlockSpawner.spawnForCells(
                world, player, worldSeed, dim, window, cellX, cellZ, radiusCells
        );


        // Keep / add flock objects
        flocksById.putAll(sr.flocks);

        for (ClientBird b : sr.birds) {
            long id = b.getId();
            if (!birdsById.containsKey(id)) {
                birdsById.put(id, b);
                if (birdsById.size() >= BirdConfig.maxBirdsAroundPlayer) break;
            }
        }
    }

    private void cleanupFlocks() {
        // Remove flock entries if no birds reference them anymore
        if (flocksById.isEmpty()) return;

        Set<Long> used = new HashSet<>();
        for (ClientBird b : birdsById.values()) {
            if (b.flockId != 0L) used.add(b.flockId);
        }

        flocksById.keySet().removeIf(id -> !used.contains(id));
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        RenderBird.renderAll(birdsById.values(), e.getPartialTicks());
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }
    public void clearAllBirds() {
        birdsById.clear();
        flocksById.clear();
    }

}
