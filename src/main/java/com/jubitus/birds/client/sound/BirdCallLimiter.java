package com.jubitus.birds.client.sound;

import com.jubitus.birds.species.BirdSpecies;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class BirdCallLimiter {

    private static final int GRID = 16; // blocks
    private static final Map<String, SpeciesGrid> BY_SPECIES = new HashMap<>();

    private BirdCallLimiter() {
    }

    public static boolean allowCall(String speciesKey, long nowTick, Vec3d pos, BirdSpecies.SoundSpamSettings s) {
        if (speciesKey == null) speciesKey = "unknown";
        if (pos == null) return true;
        if (s == null) return true;

        int max = Math.max(0, s.maxCallsPerRadius);
        double radius = Math.max(0.0, s.callRadiusBlocks);
        int window = Math.max(1, s.callCooldownTicks);

        if (max <= 0) return false;          // user can set to 0 to mute calls by limiter
        if (radius <= 0.01) return true;     // radius disabled -> no area limiting

        SpeciesGrid g = BY_SPECIES.computeIfAbsent(speciesKey, k -> new SpeciesGrid());
        return g.allow(nowTick, pos, radius, max, window);
    }

    public static void clearAll() {
        BY_SPECIES.clear();
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    private static long pack(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static final class SpeciesGrid {
        private final Map<Long, ArrayList<CallEvent>> buckets = new HashMap<>();
        private long lastCleanupTick = 0;

        boolean allow(long now, Vec3d pos, double radius, int maxCalls, int windowTicks) {
            // cleanup about once per second
            if (now - lastCleanupTick > 20) {
                cleanup(now, windowTicks);
                lastCleanupTick = now;
            }

            int cx = floorDiv((int) Math.floor(pos.x), GRID);
            int cz = floorDiv((int) Math.floor(pos.z), GRID);
            int rCells = (int) Math.ceil(radius / GRID);

            double r2 = radius * radius;
            long minTick = now - windowTicks;

            int count = 0;

            for (int dx = -rCells; dx <= rCells; dx++) {
                for (int dz = -rCells; dz <= rCells; dz++) {

                    long key = pack(cx + dx, cz + dz);
                    ArrayList<CallEvent> list = buckets.get(key);
                    if (list == null || list.isEmpty()) continue;

                    for (int i = list.size() - 1; i >= 0; i--) {
                        CallEvent ev = list.get(i);

                        if (ev.tick < minTick) {
                            list.remove(i);
                            continue;
                        }

                        double ddx = ev.x - pos.x;
                        double ddz = ev.z - pos.z;
                        double d2 = ddx * ddx + ddz * ddz;

                        if (d2 <= r2) {
                            count++;
                            if (count >= maxCalls) return false;
                        }
                    }

                    if (list.isEmpty()) buckets.remove(key);
                }
            }

            // record this event
            long k0 = pack(cx, cz);
            buckets.computeIfAbsent(k0, k -> new ArrayList<>()).add(new CallEvent(now, pos.x, pos.z));
            return true;
        }

        private void cleanup(long now, int windowTicks) {
            long minTick = now - Math.max(1, windowTicks) - 5;

            Iterator<Map.Entry<Long, ArrayList<CallEvent>>> it = buckets.entrySet().iterator();
            while (it.hasNext()) {
                ArrayList<CallEvent> list = it.next().getValue();
                if (list == null || list.isEmpty()) {
                    it.remove();
                    continue;
                }

                for (int i = list.size() - 1; i >= 0; i--) {
                    if (list.get(i).tick < minTick) list.remove(i);
                }

                if (list.isEmpty()) it.remove();
            }
        }
    }

    private static final class CallEvent {
        final long tick;
        final double x, z;

        CallEvent(long tick, double x, double z) {
            this.tick = tick;
            this.x = x;
            this.z = z;
        }
    }
}
