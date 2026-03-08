package com.jubitus.birds.client;

import com.jubitus.birds.client.config.BirdConfig;
import com.jubitus.birds.client.sound.BirdCallLimiter;
import com.jubitus.birds.client.sound.BirdCallSound;
import com.jubitus.birds.client.sound.BirdCallType;
import com.jubitus.birds.client.sound.BirdSoundSystem;
import com.jubitus.birds.client.util.BirdEnvironment;
import com.jubitus.birds.client.util.BirdOrientation;
import com.jubitus.birds.client.util.BirdSteering;
import com.jubitus.birds.client.util.FlockingRules;
import com.jubitus.birds.species.BirdSpecies;

public class ClientBird {

    private static final double MAX_CLIMB_PER_TICK = 0.20;
    private static final double COLLISION_BUFFER = 1.0;
    public final BirdSpecies species;
    public final BirdOrientation orientation = new BirdOrientation();
    public final net.minecraft.util.ResourceLocation texture;
    private final long birdSeed;
    private final java.util.Random rng;
    // Individual variation (multipliers)
    private final double scaleMul;
    private final double speedMul;
    private final double flapAmpMul;
    private final double flapSpeedMul;
    private final double callPitchMul;
    // Per-bird flocking params (filled from species each tick; avoids shared static)
    private final FlockingRules.Params flockParams = new FlockingRules.Params();
    public byte renderLod = -1;
    public int lodUpdateTicker = 0;
    public int ageTicks = 0;
    public net.minecraft.util.math.Vec3d pos;
    public net.minecraft.util.math.Vec3d vel;
    public long flockId = 0L;
    public net.minecraft.util.math.Vec3d prevPos;
    public float prevYaw, prevPitch, prevRoll;
    private long nextCallWorldTick = -1;
    private long lastCallWorldTick = -999999L;
    private double smoothVy = 0.0;
    private net.minecraft.util.math.Vec3d forward;
    private net.minecraft.util.math.Vec3d waypoint;
    private net.minecraft.util.math.Vec3d circleCenter;
    private double circleRadius;
    private int modeTicksLeft;
    private int modeTicksTotal;
    private Mode mode;
    private net.minecraft.util.math.Vec3d lastForwardXZ = new net.minecraft.util.math.Vec3d(0, 0, 1);

    public ClientBird(net.minecraft.world.World world, BirdSpecies species, long birdSeed,
                      net.minecraft.util.math.Vec3d startPos,
                      net.minecraft.util.math.Vec3d initialDir,
                      double speed) {

        this.species = species;
        this.birdSeed = birdSeed;
        this.rng = new java.util.Random(birdSeed);

        BirdSpecies.VariationSettings vs = (species != null && species.variation != null) ? species.variation : new BirdSpecies.VariationSettings();
        this.scaleMul = clampMul(1.0 + jitter(vs.scaleJitter), 0.2, 3.0);
        this.speedMul = clampMul(1.0 + jitter(vs.speedJitter), 0.2, 3.0);
        this.flapAmpMul = clampMul(1.0 + jitter(vs.flapAmpJitter), 0.2, 3.0);
        this.flapSpeedMul = clampMul(1.0 + jitter(vs.flapSpeedJitter), 0.2, 3.0);
        this.callPitchMul = clampMul(1.0 + jitter(vs.callPitchJitter), 0.5, 2.0);

        this.pos = startPos;
        this.forward = initialDir.normalize();
        this.vel = this.forward.scale(speed);

        this.texture = (species != null) ? species.pickTexture(birdSeed) : null;

        pickNewMode(world, true);

        // schedule first call (includes env multipliers)
        boolean isDay = world.isDaytime();
        BirdSpecies.BirdSpeciesView v = species.viewForTime(isDay);
        BirdSpecies.SoundView sv = v.sound(BirdCallType.SINGLE);

        double soundMul = BirdEnvironment.soundMultiplier(world, species, isDay);
        scheduleNextCall(world, world.getTotalWorldTime(), sv, soundMul, true);
    }

    // -------------------
    // Public getters for renderer
    // -------------------

    private static double clampMul(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private double jitter(double frac) {
        frac = clamp(frac, 0.0, 0.90);
        return (rng.nextDouble() * 2.0 - 1.0) * frac;
    }

    private void pickNewMode(net.minecraft.world.World world, boolean first) {
        if (species == null) {
            mode = Mode.GLIDE;
            modeTicksLeft = 80;
            modeTicksTotal = modeTicksLeft;
            pickGlideWaypoint(world);
            return;
        }

        // Base weights (legacy)
        double wG = species.patternWeightGlide;
        double wC = species.patternWeightCircle;

        // Movement block can override glide/circle + add new modes
        double wSoar = 0.0;
        double wSwoop = 0.0;

        if (species.movement != null) {
            if (species.movement.glideWeight != null) wG = species.movement.glideWeight;
            if (species.movement.circleWeight != null) wC = species.movement.circleWeight;

            wSoar = (species.movement.soarWeight != null) ? species.movement.soarWeight : 0.0;
            wSwoop = (species.movement.swoopWeight != null) ? species.movement.swoopWeight : 0.0;
        }

        // Realism: storms suppress fancy soaring/swooping
        if (BirdEnvironment.isStorm(world)) {
            wSoar = 0.0;
            wSwoop = 0.0;
            wG += 0.35;
        } else if (BirdEnvironment.isRain(world)) {
            wSoar *= 0.25;
            wSwoop *= 0.35;
        }

        double sum = Math.max(0.0, wG) + Math.max(0.0, wC) + Math.max(0.0, wSoar) + Math.max(0.0, wSwoop);
        if (sum <= 1e-9) {
            wG = 1.0;
            wC = 0.0;
            wSoar = 0.0;
            wSwoop = 0.0;
            sum = 1.0;
        }

        double r = rng.nextDouble() * sum;

        if ((r -= Math.max(0.0, wG)) < 0) {
            mode = Mode.GLIDE;
            BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());
            modeTicksLeft = randInt(v.glideMinTicks(), v.glideMaxTicks());
            modeTicksTotal = modeTicksLeft;
            pickGlideWaypoint(world);

        } else if ((r -= Math.max(0.0, wC)) < 0) {
            mode = Mode.CIRCLE;
            BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());
            modeTicksLeft = randInt(v.circleMinTicks(), v.circleMaxTicks());
            modeTicksTotal = modeTicksLeft;

            circleRadius = lerp(v.circleRadiusMin(), v.circleRadiusMax(), rng.nextDouble());
            double ang = rng.nextDouble() * Math.PI * 2;
            circleCenter = new net.minecraft.util.math.Vec3d(pos.x + Math.cos(ang) * circleRadius, pos.y, pos.z + Math.sin(ang) * circleRadius);

        } else if ((r -= Math.max(0.0, wSoar)) < 0) {
            mode = Mode.SOAR;
            BirdSpecies.MovementSettings.SoarSettings ss = (species.movement != null && species.movement.soar != null)
                    ? species.movement.soar : new BirdSpecies.MovementSettings.SoarSettings();

            modeTicksLeft = randInt(ss.minTicks, ss.maxTicks);
            modeTicksTotal = modeTicksLeft;

            circleRadius = lerp(ss.radiusMin, ss.radiusMax, rng.nextDouble());
            double ang = rng.nextDouble() * Math.PI * 2;
            circleCenter = new net.minecraft.util.math.Vec3d(pos.x + Math.cos(ang) * circleRadius, pos.y, pos.z + Math.sin(ang) * circleRadius);

        } else {
            mode = Mode.SWOOP;
            BirdSpecies.MovementSettings.SwoopSettings sw = (species.movement != null && species.movement.swoop != null)
                    ? species.movement.swoop : new BirdSpecies.MovementSettings.SwoopSettings();

            modeTicksLeft = randInt(sw.minTicks, sw.maxTicks);
            modeTicksTotal = modeTicksLeft;

            pickSwoopWaypoint(world);
        }

        // First-time safety: keep above min altitude band
        if (first) {
            double gy = getGroundY(world, pos.x, pos.z);
            BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());
            double minY = gy + v.minAltitudeAboveGround();
            if (pos.y < minY) pos = new net.minecraft.util.math.Vec3d(pos.x, minY, pos.z);
        }
    }

    private void scheduleNextCall(net.minecraft.world.World world, long now, BirdSpecies.SoundView sv, double soundMul, boolean firstSchedule) {
        int base = sv.soundBaseIntervalTicks();

        // soundMul > 1 => calls more often; < 1 => calls less often
        double m = clamp(soundMul, 0.05, 8.0);
        base = (int) Math.round(base / m);
        if (base < 20) base = 20;

        double r = sv.soundRandomness();
        double factor = 1.0;
        if (r > 0) {
            factor = (1.0 - r) + (rng.nextDouble() * (2.0 * r));
        }

        long delay = Math.max(20, Math.round(base * factor));

        long extraPhase = 0;
        if (firstSchedule) {
            long cap = Math.min(600L, base);
            extraPhase = (cap > 0) ? (long) (rng.nextDouble() * cap) : 0;
        }

        long seedOffset = (birdSeed & 255L);
        nextCallWorldTick = now + delay + seedOffset + extraPhase;
    }

    // -------------------
    // Modes
    // -------------------

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void pickGlideWaypoint(net.minecraft.world.World world) {
        double dist = 80 + rng.nextDouble() * 140;
        double ang = Math.atan2(forward.z, forward.x) + (rng.nextDouble() - 0.5) * Math.toRadians(50);

        double wx = pos.x + Math.cos(ang) * dist;
        double wz = pos.z + Math.sin(ang) * dist;

        double gy = getGroundY(world, wx, wz);
        BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());

        double targetAbove = lerp(v.preferredAboveGround() - 15, v.preferredAboveGround() + 15, rng.nextDouble());
        double desiredAbove = clamp(targetAbove, v.minAltitudeAboveGround(), v.maxAltitudeAboveGround());

        waypoint = new net.minecraft.util.math.Vec3d(wx, gy + desiredAbove, wz);
    }

    private int randInt(int a, int b) {
        return a + rng.nextInt(b - a + 1);
    }

    // -------------------
    // Tick
    // -------------------

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private void pickSwoopWaypoint(net.minecraft.world.World world) {
        // Longer forward waypoint (feels like a pass)
        double dist = 120 + rng.nextDouble() * 180;
        double ang = Math.atan2(forward.z, forward.x) + (rng.nextDouble() - 0.5) * Math.toRadians(35);

        double wx = pos.x + Math.cos(ang) * dist;
        double wz = pos.z + Math.sin(ang) * dist;

        double gy = getGroundY(world, wx, wz);
        BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());

        // swoop aims lower but still safe above ground (terrain clamp still exists)
        double lowBand = Math.max(v.minAltitudeAboveGround(), v.preferredAboveGround() * 0.6);
        double desiredAbove = clamp(lowBand, v.minAltitudeAboveGround(), v.maxAltitudeAboveGround());

        waypoint = new net.minecraft.util.math.Vec3d(wx, gy + desiredAbove, wz);
    }

    private static double getGroundY(net.minecraft.world.World world, double x, double z) {
        net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(x, 0, z);
        net.minecraft.util.math.BlockPos top = world.getHeight(pos);
        return top.getY();
    }

    private static float distanceToPlayer(net.minecraft.util.math.Vec3d soundPos) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc == null || mc.player == null) return Float.MAX_VALUE;

        double px = mc.player.posX;
        double py = mc.player.posY + mc.player.getEyeHeight();
        double pz = mc.player.posZ;

        double dx = px - soundPos.x;
        double dy = py - soundPos.y;
        double dz = pz - soundPos.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double getScale() {
        double base = (species != null) ? species.scale : 0.45;
        return base * scaleMul;
    }

    public double getFlapAmplitude() {
        double base = (species != null) ? species.flapAmplitude : 0.08;
        return base * flapAmpMul;
    }

    public double getFlapSpeed() {
        double base = (species != null) ? species.flapSpeed : 0.35;
        return base * flapSpeedMul;
    }

    public long getId() {
        return birdSeed;
    }

    public void tick(net.minecraft.world.World world, net.minecraft.util.math.Vec3d flockForward, java.util.List<ClientBird> neighbors) {
        if (world == null) return;

        prevPos = pos;

        prevYaw = orientation.yawDeg;
        prevPitch = orientation.pitchDeg;
        prevRoll = orientation.rollDeg;

        ageTicks++;

        boolean isDay = world.isDaytime();
        BirdSpecies.BirdSpeciesView v = species.viewForTime(isDay);

        // mode switch
        if (modeTicksLeft-- <= 0) {
            pickNewMode(world, false);
        }

        // Update flock params from species each tick (cheap)
        if (species != null && species.flocking != null) {
            BirdSpecies.FlockingSettings fs = species.flocking;
            flockParams.neighborRadius = fs.neighborRadius;
            flockParams.separationRadius = fs.separationRadius;
            flockParams.weightCohesion = fs.cohesionWeight;
            flockParams.weightAlignment = fs.alignmentWeight;
            flockParams.weightSeparation = fs.separationWeight;
            flockParams.maxForce = fs.maxForce;
        }

        // Compute target direction
        net.minecraft.util.math.Vec3d desiredDir;

        if (flockId != 0L && flockForward != null && flockForward.lengthSquared() > 1e-8) {
            desiredDir = new net.minecraft.util.math.Vec3d(flockForward.x, 0, flockForward.z).normalize();
        } else {
            desiredDir = computeDesiredDirection(world);
        }

        if (flockId != 0L && flockForward != null) {
            desiredDir = desiredDir.add(flockForward.scale(0.35)).normalize();
        }

        if (flockId != 0L && neighbors != null) {
            net.minecraft.util.math.Vec3d boidsForce = FlockingRules.boidsSteer(this, neighbors, flockParams);
            if (boidsForce.lengthSquared() > 1e-8) {
                desiredDir = desiredDir.add(boidsForce).normalize();
            }
        }

        // Wander noise (reduced in flock)
        double noise = v.noiseStrength();
        if (flockId != 0L) noise *= 0.20;

        // Weather realism: storms reduce wandering (tighter flight), rain slightly reduces
        if (BirdEnvironment.isStorm(world)) noise *= 0.60;
        else if (BirdEnvironment.isRain(world)) noise *= 0.80;

        desiredDir = desiredDir.add(
                (rng.nextDouble() - 0.5) * noise,
                (rng.nextDouble() - 0.5) * (noise * 0.3),
                (rng.nextDouble() - 0.5) * noise
        ).normalize();

        // Turning
        double maxTurnDeg = v.maxTurnDegPerTick();
        if (BirdEnvironment.isStorm(world)) maxTurnDeg *= 0.85;
        double maxTurnRad = Math.toRadians(maxTurnDeg);

        forward = BirdSteering.limitTurnXZ(forward, desiredDir, maxTurnRad);

        // Altitude control
        double targetY = computeTargetY(world);
        double requiredMinY = computeRequiredMinY(world);

        double safetyBuffer = 4.0;
        double safeTargetY = targetY;
        if (pos.y < requiredMinY + 10.0) {
            safeTargetY = Math.max(targetY, requiredMinY + safetyBuffer);
        }

        double yError = safeTargetY - pos.y;

        double maxUp = (pos.y < requiredMinY) ? 0.09 : 0.06;
        double maxDown = 0.06;

        double desiredVy = clamp(yError * v.verticalAdjustStrength(), -maxDown, maxUp);

        // Mode vertical behavior
        if (mode == Mode.SOAR && species != null && species.movement != null && species.movement.soar != null) {
            desiredVy += clamp(species.movement.soar.climbRate, 0.0, 0.20);
        } else if (mode == Mode.SWOOP && species != null && species.movement != null && species.movement.swoop != null) {
            BirdSpecies.MovementSettings.SwoopSettings sw = species.movement.swoop;
            double progress = 1.0 - (modeTicksLeft / (double) Math.max(1, modeTicksTotal));

            // First ~65%: dive; last ~35%: pull up
            if (progress < 0.65) {
                desiredVy = Math.min(desiredVy, -Math.abs(sw.diveRate));
            } else {
                desiredVy = Math.max(desiredVy, Math.abs(sw.pullUpRate));
            }

            // Soft above-ground floor while swooping
            double gy = getGroundY(world, pos.x, pos.z);
            double minY = gy + Math.max(v.minAltitudeAboveGround(), sw.minAboveGround);
            if (pos.y < minY) desiredVy = Math.max(desiredVy, 0.02);
        }

        // Obstacle avoidance
        net.minecraft.util.math.Vec3d avoid = obstacleAvoidance(world);
        if (avoid != null) {
            double maxTurnRadAvoid = Math.toRadians(v.maxTurnDegPerTick() * 1.25);
            forward = BirdSteering.limitTurnXZ(forward, avoid, maxTurnRadAvoid);

            if (pos.y < requiredMinY + 6.0) {
                desiredVy = Math.max(desiredVy, 0.015);
            }
        }

        smoothVy = lerp(smoothVy, desiredVy, 0.06);
        double vy = clamp(smoothVy, -maxDown, maxUp);

        // Speed (with per-bird + weather + mode modifiers)
        double minSp = v.minSpeed() * speedMul;
        double maxSp = v.maxSpeed() * speedMul;

        if (BirdEnvironment.isStorm(world)) {
            minSp *= 0.85;
            maxSp *= 0.85;
        } else if (BirdEnvironment.isRain(world)) {
            minSp *= 0.93;
            maxSp *= 0.93;
        }

        double speed = vel.length();

        double targetSpeed;
        if (mode == Mode.CIRCLE) targetSpeed = lerp(minSp, maxSp, 0.35);
        else if (mode == Mode.SOAR && species != null && species.movement != null && species.movement.soar != null) {
            targetSpeed = lerp(minSp, maxSp, 0.45) * species.movement.soar.speedMul;
        } else if (mode == Mode.SWOOP && species != null && species.movement != null && species.movement.swoop != null) {
            targetSpeed = lerp(minSp, maxSp, 0.75) * species.movement.swoop.diveSpeedMul;
        } else targetSpeed = lerp(minSp, maxSp, 0.65);

        speed = lerp(speed, targetSpeed, 0.03);

        net.minecraft.util.math.Vec3d horiz = new net.minecraft.util.math.Vec3d(forward.x, 0, forward.z);
        if (horiz.lengthSquared() < 1e-6) horiz = new net.minecraft.util.math.Vec3d(1, 0, 0);
        horiz = horiz.normalize().scale(speed);

        vel = new net.minecraft.util.math.Vec3d(horiz.x, vy, horiz.z);

        // Banking roll
        net.minecraft.util.math.Vec3d fNow = new net.minecraft.util.math.Vec3d(forward.x, 0, forward.z);
        if (fNow.lengthSquared() > 1e-6) fNow = fNow.normalize();

        net.minecraft.util.math.Vec3d fPrev = lastForwardXZ;
        double cross = (fPrev.x * fNow.z) - (fPrev.z * fNow.x);
        float targetRoll = (float) BirdSteering.clamp(-cross * 55.0, -35.0, 35.0);
        orientation.setTargetRoll(targetRoll, 3.0f);

        orientation.updateFromVelocity(vel, 6.0f, 4.0f, 3.0f);
        lastForwardXZ = fNow;

        net.minecraft.util.math.Vec3d nextPos = pos.add(vel);

        double requiredMinNext = computeRequiredMinYAt(world, nextPos) + COLLISION_BUFFER;
        if (nextPos.y < requiredMinNext) {
            double needed = requiredMinNext - nextPos.y;
            double lift = Math.min(needed, MAX_CLIMB_PER_TICK);
            nextPos = new net.minecraft.util.math.Vec3d(nextPos.x, nextPos.y + lift, nextPos.z);
            smoothVy = Math.max(smoothVy, lift);
        }

        tryPlayCall(world, world.getTotalWorldTime(), v);

        pos = nextPos;
    }

    private net.minecraft.util.math.Vec3d computeDesiredDirection(net.minecraft.world.World world) {
        if (mode == Mode.GLIDE || mode == Mode.SWOOP) {
            net.minecraft.util.math.Vec3d to = waypoint.subtract(pos);
            if (to.lengthSquared() < 16.0) {
                if (mode == Mode.SWOOP) pickSwoopWaypoint(world);
                else pickGlideWaypoint(world);
                to = waypoint.subtract(pos);
            }
            return to.normalize();
        } else {
            // CIRCLE or SOAR: tangent around circleCenter
            net.minecraft.util.math.Vec3d toCenter = circleCenter.subtract(pos);
            net.minecraft.util.math.Vec3d radial = new net.minecraft.util.math.Vec3d(toCenter.x, 0, toCenter.z);
            if (radial.lengthSquared() < 1e-6) radial = new net.minecraft.util.math.Vec3d(1, 0, 0);
            radial = radial.normalize();

            boolean cw = (birdSeed & 1L) == 0L;
            net.minecraft.util.math.Vec3d tangent = cw ? new net.minecraft.util.math.Vec3d(-radial.z, 0, radial.x) : new net.minecraft.util.math.Vec3d(radial.z, 0, -radial.x);

            double dist = new net.minecraft.util.math.Vec3d(pos.x - circleCenter.x, 0, pos.z - circleCenter.z).length();
            double err = (circleRadius - dist);
            net.minecraft.util.math.Vec3d correction = radial.scale(-err * 0.02);

            return tangent.add(correction).normalize();
        }
    }

    private double computeTargetY(net.minecraft.world.World world) {
        double ground = getGroundY(world, pos.x, pos.z);

        BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());

        double band = lerp(v.preferredAboveGround() - 20, v.preferredAboveGround() + 20, pseudoNoise01(world));
        double desiredAbove = clamp(band, v.minAltitudeAboveGround(), v.maxAltitudeAboveGround());

        // Weather realism: fly a bit lower in rain/storm
        if (BirdEnvironment.isStorm(world)) desiredAbove *= 0.70;
        else if (BirdEnvironment.isRain(world)) desiredAbove *= 0.85;

        desiredAbove = clamp(desiredAbove, v.minAltitudeAboveGround(), v.maxAltitudeAboveGround());

        return ground + desiredAbove;
    }

    private net.minecraft.util.math.Vec3d obstacleAvoidance(net.minecraft.world.World world) {
        net.minecraft.util.math.Vec3d start = pos;
        net.minecraft.util.math.Vec3d end = pos.add(forward.scale(16));

        net.minecraft.util.math.RayTraceResult hit = world.rayTraceBlocks(start, end, false, true, false);
        if (hit != null && hit.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            boolean left = (birdSeed & 2L) == 0L;
            net.minecraft.util.math.Vec3d side = left ? new net.minecraft.util.math.Vec3d(-forward.z, 0, forward.x) : new net.minecraft.util.math.Vec3d(forward.z, 0, -forward.x);
            return forward.add(side.scale(0.8)).normalize();
        }
        return null;
    }

    private double pseudoNoise01(net.minecraft.world.World world) {
        long t = world.getTotalWorldTime() / 40;
        long x = birdSeed ^ (t * 0x9E3779B97F4A7C15L);
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        return ((x & 0xFFFF) / (double) 0xFFFF);
    }

    private void tryPlayCall(net.minecraft.world.World world, long now, BirdSpecies.BirdSpeciesView v) {
        if (world == null || v == null || species == null) return;

        if (!v.soundsEnabled()) return;
        if (species.soundKey == null || species.soundKey.isEmpty()) return;

        BirdCallType type = (flockId != 0L) ? BirdCallType.FLOCK : BirdCallType.SINGLE;
        if (!BirdSoundSystem.hasSounds(species.soundKey, type)) return;

        BirdSpecies.SoundView sv = v.sound(type);

        boolean isDay = world.isDaytime();
        double soundMul = BirdEnvironment.soundMultiplier(world, species, isDay);

        if (soundMul <= 0.0001) {
            // very quiet -> push next call far away
            scheduleNextCall(world, now, sv, 0.05, false);
            return;
        }

        // Species anti-spam: per-bird minimum cooldown
        if (species.soundSpam != null && species.soundSpam.callCooldownTicks > 0) {
            if (now - lastCallWorldTick < species.soundSpam.callCooldownTicks) {
                scheduleNextCall(world, now, sv, soundMul, false);
                return;
            }
        }

        if (nextCallWorldTick < 0) {
            scheduleNextCall(world, now, sv, soundMul, true);
            return;
        }
        if (now < nextCallWorldTick) return;

        float vol = (float) (sv.soundVolume() * BirdConfig.masterBirdVolume * soundMul);
        if (vol > (float) BirdConfig.masterBirdVolumeClamp) vol = (float) BirdConfig.masterBirdVolumeClamp;
        if (vol < 0f) vol = 0f;

        float pit = (float) (sv.soundPitch() * callPitchMul);

        double var = sv.soundPitchVariation();
        if (var > 0.0) {
            double jitter = (rng.nextDouble() * 2.0 - 1.0) * var;
            pit = (float) (pit * (1.0 + jitter));
        }
        pit = (float) Math.max(0.5, Math.min(2.0, pit));

        float maxDist = (float) sv.soundMaxDistance();
        float distNow = distanceToPlayer(this.pos);

        float fadeStart = (float) sv.soundFadeStart();
        float fadePower = (float) sv.soundFadePower();

        if (distNow >= maxDist) {
            scheduleNextCall(world, now, sv, soundMul, false);
            return;
        }

        // NEW: area anti-spam (only checked when call is about to play)
        if (species.soundSpam != null) {
            boolean ok = BirdCallLimiter.allowCall(species.soundKey, now, this.pos, species.soundSpam);
            if (!ok) {
                // If blocked, push next call later to avoid "retry spam"
                scheduleNextCall(world, now + Math.max(20, species.soundSpam.callCooldownTicks), sv, soundMul, false);
                return;
            }
        }

        net.minecraft.util.ResourceLocation rl = BirdSoundSystem.getCallEvent(species.soundKey, type);
        net.minecraft.util.SoundEvent evt = BirdSoundSystem.getOrCreateEvent(rl);

        BirdCallSound sound =
                new BirdCallSound(this, world, evt, vol, pit, maxDist, fadeStart, fadePower);

        BirdSoundSystem.playCallIfAllowed(getId(), flockId, type, sound);

        lastCallWorldTick = now;
        scheduleNextCall(world, now, sv, soundMul, false);
    }

    private double getGroundAheadY(net.minecraft.world.World world, double lookAheadDist) {
        net.minecraft.util.math.Vec3d f = new net.minecraft.util.math.Vec3d(forward.x, 0, forward.z);
        if (f.lengthSquared() < 1e-6) f = new net.minecraft.util.math.Vec3d(0, 0, 1);
        f = f.normalize();

        double ax = pos.x + f.x * lookAheadDist;
        double az = pos.z + f.z * lookAheadDist;
        return getGroundY(world, ax, az);
    }

    private double computeRequiredMinY(net.minecraft.world.World world) {
        double[] ds = {0, 6, 12, 18, 26, 34};

        double gMax = -1e9;
        for (double d : ds) {
            double g = getGroundAheadY(world, d);
            if (g > gMax) gMax = g;
        }

        BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());
        return gMax + v.minAltitudeAboveGround();
    }

    private double computeRequiredMinYAt(net.minecraft.world.World world, net.minecraft.util.math.Vec3d atPos) {
        net.minecraft.util.math.Vec3d f = new net.minecraft.util.math.Vec3d(forward.x, 0, forward.z);
        if (f.lengthSquared() < 1e-6) f = new net.minecraft.util.math.Vec3d(0, 0, 1);
        f = f.normalize();

        double[] ds = {0, 6, 12, 18, 26};

        double gMax = -1e9;
        for (double d : ds) {
            double ax = atPos.x + f.x * d;
            double az = atPos.z + f.z * d;
            double g = getGroundY(world, ax, az);
            if (g > gMax) gMax = g;
        }

        BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());
        return gMax + v.minAltitudeAboveGround();
    }

    public enum Mode {GLIDE, CIRCLE, SOAR, SWOOP}
}
