package com.jubitus.birds.client.util;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Random;

public class Flock {
    public final long flockId;
    private final Random rng;

    // shared group heading (XZ)
    private Vec3d groupForward = new Vec3d(0, 0, 1);
    private int ticksToChange = 120;

    public Flock(long flockId, Vec3d initialForward) {
        this.flockId = flockId;
        this.rng = new Random(flockId);

        this.groupForward = new Vec3d(initialForward.x, 0, initialForward.z).normalize();
        if (this.groupForward.lengthSquared() < 1e-6) this.groupForward = new Vec3d(0, 0, 1);

        ticksToChange = 80 + rng.nextInt(180);
    }


    public void tick(World world) {
        if (--ticksToChange <= 0) {
            // group “decision”: shift heading smoothly
            nudgeHeading();
            ticksToChange = 80 + rng.nextInt(220);
        }
    }

    public Vec3d getGroupForward() {
        return groupForward;
    }

    private void nudgeHeading() {
        // rotate a bit left/right around current heading (gentle group decision)
        double ang = (rng.nextDouble() - 0.5) * Math.toRadians(35); // +/- 35 degrees

        double x = groupForward.x;
        double z = groupForward.z;

        double cos = Math.cos(ang);
        double sin = Math.sin(ang);

        double nx = x * cos - z * sin;
        double nz = x * sin + z * cos;

        groupForward = new Vec3d(nx, 0, nz).normalize();
    }

}
