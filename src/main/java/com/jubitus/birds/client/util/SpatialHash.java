package com.jubitus.birds.client.util;

import com.jubitus.birds.client.ClientBird;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpatialHash {
    private final int cellSize;
    private final Map<Long, List<ClientBird>> buckets = new HashMap<>();

    public SpatialHash(int cellSize) {
        this.cellSize = Math.max(8, cellSize);
    }

    public void clear() {
        buckets.clear();
    }

    public void insert(ClientBird b) {
        long key = key(b.pos);
        buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
    }

    public List<ClientBird> queryNearby(Vec3d pos) {
        int cx = floorDiv((int)Math.floor(pos.x), cellSize);
        int cz = floorDiv((int)Math.floor(pos.z), cellSize);

        List<ClientBird> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long k = pack(cx + dx, cz + dz);
                List<ClientBird> list = buckets.get(k);
                if (list != null) out.addAll(list);
            }
        }
        return out;
    }

    private long key(Vec3d p) {
        int cx = floorDiv((int)Math.floor(p.x), cellSize);
        int cz = floorDiv((int)Math.floor(p.z), cellSize);
        return pack(cx, cz);
    }

    private static long pack(int x, int z) {
        return (((long)x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }
}
