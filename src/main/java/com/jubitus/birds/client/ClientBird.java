package com.jubitus.birds.client;

import com.jubitus.birds.client.config.BirdConfig;
import com.jubitus.birds.client.util.BirdOrientation;
import com.jubitus.birds.client.util.BirdSteering;
import com.jubitus.birds.client.util.FlockingRules;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

import java.util.Random;

public class ClientBird {


    private static final double MAX_CLIMB_PER_TICK = 0.20; // blocks/tick, how fast it can "pull up" to avoid collision
    private static final double COLLISION_BUFFER = 1.0;    // extra clearance above min

    public int ageTicks = 0;
    private static final FlockingRules.Params FLOCK_PARAMS = new FlockingRules.Params();

    public enum Mode { GLIDE, CIRCLE }

    private double smoothVy = 0.0;


    public Vec3d pos;
    public Vec3d vel;

    private Vec3d forward;        // normalized heading
    private Vec3d waypoint;       // for glide mode
    private Vec3d circleCenter;   // for circle mode
    private double circleRadius;
    private int modeTicksLeft;
    private Mode mode;

    private final long birdSeed;
    private final Random rng;

    public int textureIndex = 0;
    public long flockId = 0L;
    public final BirdOrientation orientation = new BirdOrientation();

    public Vec3d prevPos;

    public float prevYaw, prevPitch, prevRoll;


    // used for banking (roll)
    private Vec3d lastForwardXZ = new Vec3d(0, 0, 1);

    public ClientBird(World world, long birdSeed, Vec3d startPos, Vec3d initialDir, double speed) {
        this.birdSeed = birdSeed;
        this.rng = new Random(birdSeed);

        this.pos = startPos;
        this.forward = initialDir.normalize();
        this.vel = this.forward.scale(speed);

        pickNewMode(world, true);
    }

    public void tick(World world, Vec3d flockForward, java.util.List<ClientBird> neighbors) {
        if (world == null) return;
        prevPos = pos;

        prevYaw = orientation.yawDeg;
        prevPitch = orientation.pitchDeg;
        prevRoll = orientation.rollDeg;


        ageTicks++;
        // If too far from camera/player -> allow manager to remove
        // (manager handles removal; we don’t do it here)

        // Update behavior
        if (modeTicksLeft-- <= 0) {
            pickNewMode(world, false);
        }

        // Compute target direction
        Vec3d desiredDir;

// If in a flock: use the shared flock heading as the main intent.
// This makes the flock actually move like a flock.
        if (flockId != 0L && flockForward != null && flockForward.lengthSquared() > 1e-8) {
            desiredDir = new Vec3d(flockForward.x, 0, flockForward.z).normalize();
        } else {
            desiredDir = computeDesiredDirection(world);
        }


        // If in a flock, gently bias toward flock forward (group decision)
        if (flockId != 0L && flockForward != null) {
            desiredDir = desiredDir.add(flockForward.scale(0.35)).normalize();
        }

// Boids steering (cohesion/alignment/separation)
        if (flockId != 0L && neighbors != null) {
            Vec3d boidsForce = FlockingRules.boidsSteer(this, neighbors, FLOCK_PARAMS);

            if (boidsForce.lengthSquared() > 1e-8) {
                desiredDir = desiredDir.add(boidsForce).normalize();
            }
        }


        // Add subtle deterministic “wander”
        double noise = BirdConfig.noiseStrength;

// Flocks should wander much less or they won't look cohesive.
        if (flockId != 0L) noise *= 0.20;

        desiredDir = desiredDir.add(
                (rng.nextDouble() - 0.5) * noise,
                (rng.nextDouble() - 0.5) * (noise * 0.3),
                (rng.nextDouble() - 0.5) * noise
        ).normalize();


        // Smooth turning: rotate current forward toward desired with turn limit
        double maxTurnRad = Math.toRadians(BirdConfig.maxTurnDegPerTick);
        forward = BirdSteering.limitTurnXZ(forward, desiredDir, maxTurnRad);


        // Altitude control: keep above ground + prefer a high band
        // --- Altitude control: prefer a band, but NEVER violate required minimum (smoothly) ---
        double targetY = computeTargetY(world); // your "nice" cruising target
        double requiredMinY = computeRequiredMinY(world); // ground ahead constraint

// If we're below the required min, raise targetY to at least that.
// Add a little extra buffer so we don't skim peaks.
        double safetyBuffer = 4.0;

// Only force terrain-following when we’re getting close to the constraint.
// Otherwise keep cruising target (prevents constant “mountain bias”).
        double safeTargetY = targetY;
        if (pos.y < requiredMinY + 10.0) {
            safeTargetY = Math.max(targetY, requiredMinY + safetyBuffer);
        }


// Base vertical intent toward safeTargetY
        double yError = safeTargetY - pos.y;

// When below the safe floor, allow a bit stronger climb than normal.
// (Still smooth, but prevents getting "stuck" inside mountains.)
        double maxUp = (pos.y < requiredMinY) ? 0.09 : 0.06;
        double maxDown = 0.06;

        double desiredVy = clamp(yError * BirdConfig.verticalAdjustStrength, -maxDown, maxUp);

// Obstacle avoidance (trees/cliffs directly ahead): keep it gentle
        Vec3d avoid = obstacleAvoidance(world);
        if (avoid != null) {
            double maxTurnRadAvoid = Math.toRadians(BirdConfig.maxTurnDegPerTick * 1.25);
            forward = BirdSteering.limitTurnXZ(forward, avoid, maxTurnRadAvoid);

            // Only request extra climb if we are near/under the floor.
            if (pos.y < requiredMinY + 6.0) {
                desiredVy = Math.max(desiredVy, 0.015);
            }
        }

// Smooth vertical velocity (ease)
        smoothVy = lerp(smoothVy, desiredVy, 0.06);

// Final clamp
        double vy = clamp(smoothVy, -maxDown, maxUp);



        // Speed varies slightly by mode
        double speed = vel.length();
        double targetSpeed = (mode == Mode.CIRCLE)
                ? lerp(BirdConfig.minSpeed, BirdConfig.maxSpeed, 0.35)
                : lerp(BirdConfig.minSpeed, BirdConfig.maxSpeed, 0.65);

        speed = lerp(speed, targetSpeed, 0.03);

        // Update velocity and position
        Vec3d horiz = new Vec3d(forward.x, 0, forward.z);
        if (horiz.lengthSquared() < 1e-6) horiz = new Vec3d(1, 0, 0);
        horiz = horiz.normalize().scale(speed);

        vel = new Vec3d(horiz.x, vy, horiz.z);

        // Banking roll: based on change in forward direction (turning)
        Vec3d fNow = new Vec3d(forward.x, 0, forward.z);
        if (fNow.lengthSquared() > 1e-6) fNow = fNow.normalize();

        Vec3d fPrev = lastForwardXZ;
        double cross = (fPrev.x * fNow.z) - (fPrev.z * fNow.x); // signed turn amount
        float targetRoll = (float) BirdSteering.clamp(-cross * 55.0, -35.0, 35.0);
        orientation.setTargetRoll(targetRoll, 3.0f);

        orientation.updateFromVelocity(vel, 6.0f, 4.0f, 3.0f);

        lastForwardXZ = fNow;


        Vec3d nextPos = pos.add(vel);

// Compute floor based on where we are about to be (prevents entering cliffs)
        double requiredMinNext = computeRequiredMinYAt(world, nextPos) + COLLISION_BUFFER;

        if (nextPos.y < requiredMinNext) {
            double needed = requiredMinNext - nextPos.y;

            // Lift, but limit how much vertical correction happens in one tick
            double lift = Math.min(needed, MAX_CLIMB_PER_TICK);

            nextPos = new Vec3d(nextPos.x, nextPos.y + lift, nextPos.z);

            // Encourage continued climb on following ticks (so it feels like pulling up)
            smoothVy = Math.max(smoothVy, lift); // lift is in blocks/tick effectively
        }

        pos = nextPos;





    }

    private Vec3d computeDesiredDirection(World world) {
        if (mode == Mode.GLIDE) {
            Vec3d to = waypoint.subtract(pos);
            if (to.lengthSquared() < 16.0) {
                // reached waypoint -> pick another glide waypoint
                pickGlideWaypoint(world);
                to = waypoint.subtract(pos);
            }
            return to.normalize();
        } else {
            // circle: tangent direction around circleCenter
            Vec3d toCenter = circleCenter.subtract(pos);
            Vec3d radial = new Vec3d(toCenter.x, 0, toCenter.z);
            if (radial.lengthSquared() < 1e-6) radial = new Vec3d(1, 0, 0);
            radial = radial.normalize();

            // Tangent vector (clockwise or counter-clockwise)
            boolean cw = (birdSeed & 1L) == 0L;
            Vec3d tangent = cw ? new Vec3d(-radial.z, 0, radial.x) : new Vec3d(radial.z, 0, -radial.x);

            // Gentle correction to stay near radius
            double dist = new Vec3d(pos.x - circleCenter.x, 0, pos.z - circleCenter.z).length();
            double err = (circleRadius - dist);
            Vec3d correction = radial.scale(-err * 0.02);

            return tangent.add(correction).normalize();
        }
    }

    private void pickNewMode(World world, boolean first) {
        // Slight preference to glide
        boolean chooseCircle = rng.nextDouble() < 0.45;

        if (chooseCircle) {
            mode = Mode.CIRCLE;
            modeTicksLeft = randInt(BirdConfig.circleMinTicks, BirdConfig.circleMaxTicks);

            circleRadius = lerp(BirdConfig.circleRadiusMin, BirdConfig.circleRadiusMax, rng.nextDouble());

            // Circle center near current position, offset a bit
            double ang = rng.nextDouble() * Math.PI * 2;
            double dx = Math.cos(ang) * circleRadius;
            double dz = Math.sin(ang) * circleRadius;

            circleCenter = new Vec3d(pos.x + dx, pos.y, pos.z + dz);
        } else {
            mode = Mode.GLIDE;
            modeTicksLeft = randInt(BirdConfig.glideMinTicks, BirdConfig.glideMaxTicks);
            pickGlideWaypoint(world);
        }

        // First time: ensure we don’t immediately dive
        if (first) {
            double gy = getGroundY(world, pos.x, pos.z);
            double minY = gy + BirdConfig.minAltitudeAboveGround;
            if (pos.y < minY) pos = new Vec3d(pos.x, minY, pos.z);
        }
    }

    private void pickGlideWaypoint(World world) {
        // A forward-ish waypoint so it feels like it’s passing through an area
        double dist = 80 + rng.nextDouble() * 140;
        double ang = Math.atan2(forward.z, forward.x) + (rng.nextDouble() - 0.5) * Math.toRadians(50);

        double wx = pos.x + Math.cos(ang) * dist;
        double wz = pos.z + Math.sin(ang) * dist;

        double gy = getGroundY(world, wx, wz);
        double targetAbove = lerp(BirdConfig.preferredAboveGround - 15, BirdConfig.preferredAboveGround + 15, rng.nextDouble());
        double wy = gy + clamp(targetAbove, BirdConfig.minAltitudeAboveGround, BirdConfig.maxAltitudeAboveGround);

        waypoint = new Vec3d(wx, wy, wz);
    }

    private double computeTargetY(World world) {
        double ground = getGroundY(world, pos.x, pos.z);

        // Prefer a high band but allow variation
        double band = lerp(BirdConfig.preferredAboveGround - 20, BirdConfig.preferredAboveGround + 20, pseudoNoise01(world));
        double desiredAbove = clamp(band, BirdConfig.minAltitudeAboveGround, BirdConfig.maxAltitudeAboveGround);

        return ground + desiredAbove;
    }

    private double pseudoNoise01(World world) {
        // deterministic, slow-changing based on time and birdSeed
        long t = world.getTotalWorldTime() / 40; // changes every 2 seconds
        long x = birdSeed ^ (t * 0x9E3779B97F4A7C15L);
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        return ((x & 0xFFFF) / (double) 0xFFFF);
    }

    private Vec3d obstacleAvoidance(World world) {
        // Ray trace forward; if we’d hit something, suggest a slight turn/up
        Vec3d start = pos;
        Vec3d end = pos.add(forward.scale(16));

        RayTraceResult hit = world.rayTraceBlocks(start, end, false, true, false);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            // steer a bit sideways (deterministic)
            boolean left = (birdSeed & 2L) == 0L;
            Vec3d side = left ? new Vec3d(-forward.z, 0, forward.x) : new Vec3d(forward.z, 0, -forward.x);
            return forward.add(side.scale(0.8)).normalize();
        }
        return null;
    }

    private static Vec3d turnToward(Vec3d current, Vec3d desired, double maxDegPerTick) {
        // Turn only in XZ plane for smoothness
        Vec3d c = new Vec3d(current.x, 0, current.z).normalize();
        Vec3d d = new Vec3d(desired.x, 0, desired.z).normalize();

        double dot = clamp(c.dotProduct(d), -1.0, 1.0);
        double angle = Math.acos(dot);
        if (angle < 1e-6) return desired.normalize();

        double max = Math.toRadians(maxDegPerTick);
        double t = Math.min(1.0, max / angle);

        Vec3d blended = c.scale(1 - t).add(d.scale(t)).normalize();

        // keep original desired Y tendency small
        return new Vec3d(blended.x, desired.y * 0.2, blended.z).normalize();
    }

    private static double getGroundY(World world, double x, double z) {
        // Use the top solid or liquid block at this column
        BlockPos pos = new BlockPos(x, 0, z);
        BlockPos top = world.getHeight(pos);
        return top.getY();
    }

    private int randInt(int a, int b) {
        return a + rng.nextInt(b - a + 1);
    }


    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    public long getId() { return birdSeed; } // use your existing birdSeed field

    private double getGroundAheadY(World world, double lookAheadDist) {
        Vec3d f = new Vec3d(forward.x, 0, forward.z);
        if (f.lengthSquared() < 1e-6) f = new Vec3d(0, 0, 1);
        f = f.normalize();

        double ax = pos.x + f.x * lookAheadDist;
        double az = pos.z + f.z * lookAheadDist;
        return getGroundY(world, ax, az);
    }

    private double computeRequiredMinY(World world) {
        // Sample ground along the forward path, including near the next position.
        // More samples = fewer "gotcha" cliffs.
        double[] ds = {0, 6, 12, 18, 26, 34};

        double gMax = -1e9;
        for (double d : ds) {
            double g = getGroundAheadY(world, d);
            if (g > gMax) gMax = g;
        }

        return gMax + BirdConfig.minAltitudeAboveGround;
    }
    private double computeRequiredMinYAt(World world, Vec3d atPos) {
        // Same as computeRequiredMinY, but centered at an arbitrary position (nextPos).
        // We sample forward from atPos to handle steep terrain right in front of the next step.
        Vec3d f = new Vec3d(forward.x, 0, forward.z);
        if (f.lengthSquared() < 1e-6) f = new Vec3d(0, 0, 1);
        f = f.normalize();

        double[] ds = {0, 6, 12, 18, 26};

        double gMax = -1e9;
        for (double d : ds) {
            double ax = atPos.x + f.x * d;
            double az = atPos.z + f.z * d;
            double g = getGroundY(world, ax, az);
            if (g > gMax) gMax = g;
        }

        return gMax + BirdConfig.minAltitudeAboveGround;
    }


}
