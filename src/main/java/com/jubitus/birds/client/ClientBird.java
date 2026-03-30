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
import net.minecraft.entity.EntityLiving;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ClientBird extends EntityLiving {

    private static final double MAX_CLIMB_PER_TICK = 0.20;
    private static final double COLLISION_BUFFER = 1.0;

    public final BirdSpecies species;
    public final BirdOrientation orientation = new BirdOrientation();
    public final ResourceLocation texture;

    private final long birdSeed;
    private final Random rng;

    private final double scaleMul;
    private final double speedMul;
    private final double flapAmpMul;
    private final double flapSpeedMul;
    private final double callPitchMul;

    private final FlockingRules.Params flockParams = new FlockingRules.Params();

    public byte renderLod = -1;
    public int ageTicks = 0;

    public Vec3d pos;
    public Vec3d vel;
    public Vec3d prevPos;

    public long flockId = 0L;

    public float prevYaw;
    public float prevPitch;
    public float prevRoll;

    public float rollDeg;
    public float prevRollDeg;

    private long nextCallWorldTick = -1L;
    private long lastCallWorldTick = -999999L;

    private double smoothVy = 0.0;
    private Vec3d forward;
    private Vec3d waypoint;
    private Vec3d circleCenter;
    private double circleRadius;
    private int modeTicksLeft;
    private int modeTicksTotal;
    private Mode mode;
    private Vec3d lastForwardXZ = new Vec3d(0, 0, 1);

    public ClientBird(World world, BirdSpecies species, long birdSeed, Vec3d startPos, Vec3d initialDir, double speed) {
        super(world);

        this.setNoAI(true);
        this.setSilent(true);
        this.enablePersistence();

        this.species = species;
        this.birdSeed = birdSeed;
        this.rng = new Random(birdSeed);

        BirdSpecies.VariationSettings vs = (species != null && species.variation != null)
                ? species.variation
                : new BirdSpecies.VariationSettings();

        this.scaleMul = clampMul(1.0 + jitter(vs.scaleJitter), 0.2, 3.0);
        this.speedMul = clampMul(1.0 + jitter(vs.speedJitter), 0.2, 3.0);
        this.flapAmpMul = clampMul(1.0 + jitter(vs.flapAmpJitter), 0.2, 3.0);
        this.flapSpeedMul = clampMul(1.0 + jitter(vs.flapSpeedJitter), 0.2, 3.0);
        this.callPitchMul = clampMul(1.0 + jitter(vs.callPitchJitter), 0.5, 2.0);

        this.texture = (species != null) ? species.pickTexture(birdSeed) : null;

        this.pos = startPos;
        this.prevPos = startPos;
        this.forward = initialDir.normalize();
        this.vel = this.forward.scale(speed);

        this.noClip = true;
        this.isImmuneToFire = true;
        this.setSize(0.9F, 0.5F);
        this.setPosition(startPos.x, startPos.y, startPos.z);

        this.prevPosX = this.lastTickPosX = this.posX;
        this.prevPosY = this.lastTickPosY = this.posY;
        this.prevPosZ = this.lastTickPosZ = this.posZ;

        pickNewMode(world, true);

        boolean isDay = world.isDaytime();
        BirdSpecies.BirdSpeciesView v = species.viewForTime(isDay);
        BirdSpecies.SoundView sv = v.sound(BirdCallType.SINGLE);
        double soundMul = BirdEnvironment.soundMultiplier(world, species, isDay);
        scheduleNextCall(world, world.getTotalWorldTime(), sv, soundMul, true);
    }

    private static double clampMul(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private double jitter(double frac) {
        frac = clamp(frac, 0.0, 0.90);
        return (rng.nextDouble() * 2.0 - 1.0) * frac;
    }

    private void pickNewMode(World world, boolean first) {
        if (species == null) {
            mode = Mode.GLIDE;
            modeTicksLeft = 80;
            modeTicksTotal = modeTicksLeft;
            pickGlideWaypoint(world);
            return;
        }

        double wG = species.patternWeightGlide;
        double wC = species.patternWeightCircle;
        double wSoar = 0.0;
        double wSwoop = 0.0;

        if (species.movement != null) {
            if (species.movement.glideWeight != null) wG = species.movement.glideWeight;
            if (species.movement.circleWeight != null) wC = species.movement.circleWeight;
            wSoar = (species.movement.soarWeight != null) ? species.movement.soarWeight : 0.0;
            wSwoop = (species.movement.swoopWeight != null) ? species.movement.swoopWeight : 0.0;
        }

        if (BirdEnvironment.isStorm(world)) {
            wSoar = 0.0;
            wSwoop = 0.0;
            wG += 0.35;
        } else if (BirdEnvironment.isRain(world)) {
            wSoar *= 0.25;
            wSwoop *= 0.35;
        }

        double sum = Math.max(0.0, wG) + Math.max(0.0, wC) + Math.max(0.0, wSoar) + Math.max(0.0, wSwoop);
        if (sum <= 1.0E-9) {
            wG = 1.0;
            wC = 0.0;
            wSoar = 0.0;
            wSwoop = 0.0;
            sum = 1.0;
        }

        double r = rng.nextDouble() * sum;

        if ((r -= Math.max(0.0, wG)) < 0.0) {
            mode = Mode.GLIDE;
            BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());
            modeTicksLeft = randInt(v.glideMinTicks(), v.glideMaxTicks());
            modeTicksTotal = modeTicksLeft;
            pickGlideWaypoint(world);
        } else if ((r -= Math.max(0.0, wC)) < 0.0) {
            mode = Mode.CIRCLE;
            BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());
            modeTicksLeft = randInt(v.circleMinTicks(), v.circleMaxTicks());
            modeTicksTotal = modeTicksLeft;

            circleRadius = lerp(v.circleRadiusMin(), v.circleRadiusMax(), rng.nextDouble());
            double ang = rng.nextDouble() * Math.PI * 2.0;
            circleCenter = new Vec3d(pos.x + Math.cos(ang) * circleRadius, pos.y, pos.z + Math.sin(ang) * circleRadius);
        } else if ((r -= Math.max(0.0, wSoar)) < 0.0) {
            mode = Mode.SOAR;
            BirdSpecies.MovementSettings.SoarSettings ss = (species.movement != null && species.movement.soar != null)
                    ? species.movement.soar
                    : new BirdSpecies.MovementSettings.SoarSettings();

            modeTicksLeft = randInt(ss.minTicks, ss.maxTicks);
            modeTicksTotal = modeTicksLeft;

            circleRadius = lerp(ss.radiusMin, ss.radiusMax, rng.nextDouble());
            double ang = rng.nextDouble() * Math.PI * 2.0;
            circleCenter = new Vec3d(pos.x + Math.cos(ang) * circleRadius, pos.y, pos.z + Math.sin(ang) * circleRadius);
        } else {
            mode = Mode.SWOOP;
            BirdSpecies.MovementSettings.SwoopSettings sw = (species.movement != null && species.movement.swoop != null)
                    ? species.movement.swoop
                    : new BirdSpecies.MovementSettings.SwoopSettings();

            modeTicksLeft = randInt(sw.minTicks, sw.maxTicks);
            modeTicksTotal = modeTicksLeft;
            pickSwoopWaypoint(world);
        }

        if (first) {
            double gy = getGroundY(world, pos.x, pos.z);
            BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());
            double minY = gy + v.minAltitudeAboveGround();
            if (pos.y < minY) {
                pos = new Vec3d(pos.x, minY, pos.z);
                setPosition(pos.x, pos.y, pos.z);
            }
        }
    }

    private void scheduleNextCall(World world, long now, BirdSpecies.SoundView sv, double soundMul, boolean firstSchedule) {
        int base = sv.soundBaseIntervalTicks();

        double m = clamp(soundMul, 0.05, 8.0);
        base = (int) Math.round(base / m);
        if (base < 20) base = 20;

        double r = sv.soundRandomness();
        double factor = 1.0;
        if (r > 0.0) {
            factor = (1.0 - r) + (rng.nextDouble() * (2.0 * r));
        }

        long delay = Math.max(20L, Math.round(base * factor));

        long extraPhase = 0L;
        if (firstSchedule) {
            long cap = Math.min(600L, base);
            extraPhase = (cap > 0L) ? (long) (rng.nextDouble() * cap) : 0L;
        }

        long seedOffset = (birdSeed & 255L);
        nextCallWorldTick = now + delay + seedOffset + extraPhase;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void pickGlideWaypoint(World world) {
        double dist = 80.0 + rng.nextDouble() * 140.0;
        double ang = Math.atan2(forward.z, forward.x) + (rng.nextDouble() - 0.5) * Math.toRadians(50.0);

        double wx = pos.x + Math.cos(ang) * dist;
        double wz = pos.z + Math.sin(ang) * dist;

        double gy = getGroundY(world, wx, wz);
        BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());

        double targetAbove = lerp(v.preferredAboveGround() - 15.0, v.preferredAboveGround() + 15.0, rng.nextDouble());
        double desiredAbove = clamp(targetAbove, v.minAltitudeAboveGround(), v.maxAltitudeAboveGround());

        waypoint = new Vec3d(wx, gy + desiredAbove, wz);
    }

    private int randInt(int a, int b) {
        return a + rng.nextInt(b - a + 1);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private void pickSwoopWaypoint(World world) {
        double dist = 120.0 + rng.nextDouble() * 180.0;
        double ang = Math.atan2(forward.z, forward.x) + (rng.nextDouble() - 0.5) * Math.toRadians(35.0);

        double wx = pos.x + Math.cos(ang) * dist;
        double wz = pos.z + Math.sin(ang) * dist;

        double gy = getGroundY(world, wx, wz);
        BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());

        double lowBand = Math.max(v.minAltitudeAboveGround(), v.preferredAboveGround() * 0.6);
        double desiredAbove = clamp(lowBand, v.minAltitudeAboveGround(), v.maxAltitudeAboveGround());

        waypoint = new Vec3d(wx, gy + desiredAbove, wz);
    }

    private static double getGroundY(World world, double x, double z) {
        BlockPos pos = new BlockPos(x, 0.0, z);
        BlockPos top = world.getHeight(pos);
        return top.getY();
    }

    private static float distanceToPlayer(Vec3d soundPos) {
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

    @Override
    protected void initEntityAI() {
    }

    @Override
    protected void entityInit() {
        super.entityInit();
    }

    @Override
    public void onUpdate() {
        if (this.world == null || !this.world.isRemote) {
            this.setDead();
            return;
        }

        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.lastTickPosX = this.posX;
        this.lastTickPosY = this.posY;
        this.lastTickPosZ = this.posZ;

        this.prevRotationYaw = this.rotationYaw;
        this.prevRotationPitch = this.rotationPitch;
        this.prevYaw = this.rotationYaw;
        this.prevPitch = this.rotationPitch;
        this.prevRoll = this.rollDeg;
        this.prevRollDeg = this.rollDeg;

        this.prevPos = this.pos;
        this.ageTicks++;

        BirdManager manager = BirdManager.INSTANCE;
        Vec3d flockForward = null;
        if (manager != null && this.flockId != 0L) {
            flockForward = manager.getFlockForward(this.flockId);
        }

        double neighborRadius = 48.0;
        if (this.species != null && this.species.flocking != null) {
            neighborRadius = this.species.flocking.neighborRadius;
        }

        List<ClientBird> neighbors = (manager != null)
                ? manager.queryNeighbors(this, neighborRadius)
                : Collections.emptyList();

        tickBird(world, flockForward, neighbors);

        this.motionX = this.vel.x;
        this.motionY = this.vel.y;
        this.motionZ = this.vel.z;

        this.setPosition(this.pos.x, this.pos.y, this.pos.z);
    }

    @Override
    public boolean canDespawn() {
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorInventoryList() {
        return java.util.Collections.emptyList();
    }

    @Override
    public ItemStack getItemStackFromSlot(EntityEquipmentSlot slotIn) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemStackToSlot(EntityEquipmentSlot slotIn, ItemStack stack) {
    }

    @Override
    public EnumHandSide getPrimaryHand() {
        return EnumHandSide.RIGHT;
    }

    @Override
    protected boolean canDropLoot() {
        return false;
    }

    @Override
    public boolean attackEntityFrom(net.minecraft.util.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean isInRangeToRenderDist(double distance) {
        double edge = this.getRenderBoundingBox().getAverageEdgeLength();
        if (Double.isNaN(edge) || edge <= 0.0D) {
            edge = 4.0D;
        }
        double max = edge * 64.0D * 4.0D;
        return distance < max * max;
    }

    @Override
    public boolean isEntityInsideOpaqueBlock() {
        return false;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        double r = Math.max(1.5D, getScale() * 2.2D);
        return new AxisAlignedBB(this.posX - r, this.posY - r, this.posZ - r, this.posX + r, this.posY + r, this.posZ + r);
    }

    public double getScale() {
        double base = (species != null) ? species.scale : 0.45;
        return base * scaleMul;
    }

    public long getId() {
        return this.birdSeed;
    }

    public float getRoll(float partialTicks) {
        return this.prevRollDeg + (this.rollDeg - this.prevRollDeg) * partialTicks;
    }

    public double getFlapAmplitude() {
        double base = (species != null) ? species.flapAmplitude : 0.08;
        return base * flapAmpMul;
    }

    public double getFlapSpeed() {
        double base = (species != null) ? species.flapSpeed : 0.35;
        return base * flapSpeedMul;
    }

    private void tickBird(World world, Vec3d flockForward, List<ClientBird> neighbors) {
        if (world == null || species == null) return;

        boolean isDay = world.isDaytime();
        BirdSpecies.BirdSpeciesView v = species.viewForTime(isDay);

        if (modeTicksLeft-- <= 0) {
            pickNewMode(world, false);
        }

        if (species.flocking != null) {
            BirdSpecies.FlockingSettings fs = species.flocking;
            flockParams.neighborRadius = fs.neighborRadius;
            flockParams.separationRadius = fs.separationRadius;
            flockParams.weightCohesion = fs.cohesionWeight;
            flockParams.weightAlignment = fs.alignmentWeight;
            flockParams.weightSeparation = fs.separationWeight;
            flockParams.maxForce = fs.maxForce;
        }

        Vec3d desiredDir;

        if (flockId != 0L && flockForward != null && flockForward.lengthSquared() > 1.0E-8) {
            desiredDir = new Vec3d(flockForward.x, 0, flockForward.z).normalize();
        } else {
            desiredDir = computeDesiredDirection(world);
        }

        if (flockId != 0L && flockForward != null) {
            desiredDir = desiredDir.add(flockForward.scale(0.35)).normalize();
        }

        if (flockId != 0L && neighbors != null) {
            Vec3d boidsForce = FlockingRules.boidsSteer(this, neighbors, flockParams);
            if (boidsForce.lengthSquared() > 1.0E-8) {
                desiredDir = desiredDir.add(boidsForce).normalize();
            }
        }

        double noise = v.noiseStrength();
        if (flockId != 0L) noise *= 0.20;

        if (BirdEnvironment.isStorm(world)) noise *= 0.60;
        else if (BirdEnvironment.isRain(world)) noise *= 0.80;

        desiredDir = desiredDir.add(
                (rng.nextDouble() - 0.5) * noise,
                (rng.nextDouble() - 0.5) * (noise * 0.3),
                (rng.nextDouble() - 0.5) * noise
        ).normalize();

        double maxTurnDeg = v.maxTurnDegPerTick();
        if (BirdEnvironment.isStorm(world)) maxTurnDeg *= 0.85;
        double maxTurnRad = Math.toRadians(maxTurnDeg);

        forward = BirdSteering.limitTurnXZ(forward, desiredDir, maxTurnRad);

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

        if (mode == Mode.SOAR && species.movement != null && species.movement.soar != null) {
            desiredVy += clamp(species.movement.soar.climbRate, 0.0, 0.20);
        } else if (mode == Mode.SWOOP && species.movement != null && species.movement.swoop != null) {
            BirdSpecies.MovementSettings.SwoopSettings sw = species.movement.swoop;
            double progress = 1.0 - (modeTicksLeft / (double) Math.max(1, modeTicksTotal));

            if (progress < 0.65) {
                desiredVy = Math.min(desiredVy, -Math.abs(sw.diveRate));
            } else {
                desiredVy = Math.max(desiredVy, Math.abs(sw.pullUpRate));
            }

            double gy = getGroundY(world, pos.x, pos.z);
            double minY = gy + Math.max(v.minAltitudeAboveGround(), sw.minAboveGround);
            if (pos.y < minY) desiredVy = Math.max(desiredVy, 0.02);
        }

        Vec3d avoid = obstacleAvoidance(world);
        if (avoid != null) {
            double maxTurnRadAvoid = Math.toRadians(v.maxTurnDegPerTick() * 1.25);
            forward = BirdSteering.limitTurnXZ(forward, avoid, maxTurnRadAvoid);

            if (pos.y < requiredMinY + 6.0) {
                desiredVy = Math.max(desiredVy, 0.015);
            }
        }

        smoothVy = lerp(smoothVy, desiredVy, 0.06);
        double vy = clamp(smoothVy, -maxDown, maxUp);

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

        if (mode == Mode.CIRCLE) {
            targetSpeed = lerp(minSp, maxSp, 0.35);
        } else if (mode == Mode.SOAR && species.movement != null && species.movement.soar != null) {
            targetSpeed = lerp(minSp, maxSp, 0.45) * species.movement.soar.speedMul;
        } else if (mode == Mode.SWOOP && species.movement != null && species.movement.swoop != null) {
            targetSpeed = lerp(minSp, maxSp, 0.75) * species.movement.swoop.diveSpeedMul;
        } else {
            targetSpeed = lerp(minSp, maxSp, 0.65);
        }

        speed = lerp(speed, targetSpeed, 0.03);

        Vec3d horiz = new Vec3d(forward.x, 0, forward.z);
        if (horiz.lengthSquared() < 1.0E-6) horiz = new Vec3d(1, 0, 0);
        horiz = horiz.normalize().scale(speed);

        vel = new Vec3d(horiz.x, vy, horiz.z);

        Vec3d fNow = new Vec3d(forward.x, 0, forward.z);
        if (fNow.lengthSquared() > 1.0E-6) fNow = fNow.normalize();

        Vec3d fPrev = lastForwardXZ;
        double cross = (fPrev.x * fNow.z) - (fPrev.z * fNow.x);
        float targetRoll = (float) BirdSteering.clamp(-cross * 55.0, -35.0, 35.0);
        orientation.setTargetRoll(targetRoll, 3.0f);

        orientation.updateFromVelocity(vel, 6.0f, 4.0f, 3.0f);
        lastForwardXZ = fNow;

        this.rotationYaw = orientation.yawDeg;
        this.rotationPitch = orientation.pitchDeg;
        this.rollDeg = orientation.rollDeg;

        Vec3d nextPos = pos.add(vel);

        double requiredMinNext = computeRequiredMinYAt(world, nextPos) + COLLISION_BUFFER;
        if (nextPos.y < requiredMinNext) {
            double needed = requiredMinNext - nextPos.y;
            double lift = Math.min(needed, MAX_CLIMB_PER_TICK);
            nextPos = new Vec3d(nextPos.x, nextPos.y + lift, nextPos.z);
            smoothVy = Math.max(smoothVy, lift);
        }

        tryPlayCall(world, world.getTotalWorldTime(), v);

        pos = nextPos;
    }

    private Vec3d computeDesiredDirection(World world) {
        if (mode == Mode.GLIDE || mode == Mode.SWOOP) {
            Vec3d to = waypoint.subtract(pos);
            if (to.lengthSquared() < 16.0) {
                if (mode == Mode.SWOOP) pickSwoopWaypoint(world);
                else pickGlideWaypoint(world);
                to = waypoint.subtract(pos);
            }
            return to.normalize();
        } else {
            Vec3d toCenter = circleCenter.subtract(pos);
            Vec3d radial = new Vec3d(toCenter.x, 0, toCenter.z);
            if (radial.lengthSquared() < 1.0E-6) radial = new Vec3d(1, 0, 0);
            radial = radial.normalize();

            boolean cw = (birdSeed & 1L) == 0L;
            Vec3d tangent = cw ? new Vec3d(-radial.z, 0, radial.x) : new Vec3d(radial.z, 0, -radial.x);

            double dist = new Vec3d(pos.x - circleCenter.x, 0, pos.z - circleCenter.z).length();
            double err = (circleRadius - dist);
            Vec3d correction = radial.scale(-err * 0.02);

            return tangent.add(correction).normalize();
        }
    }

    private double computeTargetY(World world) {
        double ground = getGroundY(world, pos.x, pos.z);
        BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());

        double band = lerp(v.preferredAboveGround() - 20, v.preferredAboveGround() + 20, pseudoNoise01(world));
        double desiredAbove = clamp(band, v.minAltitudeAboveGround(), v.maxAltitudeAboveGround());

        if (BirdEnvironment.isStorm(world)) desiredAbove *= 0.70;
        else if (BirdEnvironment.isRain(world)) desiredAbove *= 0.85;

        desiredAbove = clamp(desiredAbove, v.minAltitudeAboveGround(), v.maxAltitudeAboveGround());
        return ground + desiredAbove;
    }

    private Vec3d obstacleAvoidance(World world) {
        Vec3d start = pos;
        Vec3d end = pos.add(forward.scale(16.0));
        RayTraceResult hit = world.rayTraceBlocks(start, end, false, true, false);

        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            boolean left = (birdSeed & 2L) == 0L;
            Vec3d side = left ? new Vec3d(-forward.z, 0, forward.x) : new Vec3d(forward.z, 0, -forward.x);
            return forward.add(side.scale(0.8)).normalize();
        }

        return null;
    }

    private double pseudoNoise01(World world) {
        long t = world.getTotalWorldTime() / 40L;
        long x = birdSeed ^ (t * 0x9E3779B97F4A7C15L);
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        return ((x & 0xFFFFL) / (double) 0xFFFFL);
    }

    private void tryPlayCall(World world, long now, BirdSpecies.BirdSpeciesView v) {
        if (world == null || v == null || species == null) return;
        if (!v.soundsEnabled()) return;
        if (species.soundKey == null || species.soundKey.isEmpty()) return;

        BirdCallType type = (flockId != 0L) ? BirdCallType.FLOCK : BirdCallType.SINGLE;
        if (!BirdSoundSystem.hasSounds(species.soundKey, type)) return;

        BirdSpecies.SoundView sv = v.sound(type);
        boolean isDay = world.isDaytime();
        double soundMul = BirdEnvironment.soundMultiplier(world, species, isDay);

        if (soundMul <= 0.0001) {
            scheduleNextCall(world, now, sv, 0.05, false);
            return;
        }

        if (species.soundSpam != null && species.soundSpam.callCooldownTicks > 0) {
            if (now - lastCallWorldTick < species.soundSpam.callCooldownTicks) {
                scheduleNextCall(world, now, sv, soundMul, false);
                return;
            }
        }

        if (nextCallWorldTick < 0L) {
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

        if (species.soundSpam != null) {
            boolean ok = BirdCallLimiter.allowCall(species.soundKey, now, this.pos, species.soundSpam);
            if (!ok) {
                scheduleNextCall(world, now + Math.max(20, species.soundSpam.callCooldownTicks), sv, soundMul, false);
                return;
            }
        }

        ResourceLocation rl = BirdSoundSystem.getCallEvent(species.soundKey, type);
        SoundEvent evt = BirdSoundSystem.getOrCreateEvent(rl);

        BirdCallSound sound = new BirdCallSound(this, world, evt, vol, pit, maxDist, fadeStart, fadePower);
        BirdSoundSystem.playCallIfAllowed(getId(), flockId, type, sound);

        lastCallWorldTick = now;
        scheduleNextCall(world, now, sv, soundMul, false);
    }

    private double getGroundAheadY(World world, double lookAheadDist) {
        Vec3d f = new Vec3d(forward.x, 0, forward.z);
        if (f.lengthSquared() < 1.0E-6) f = new Vec3d(0, 0, 1);
        f = f.normalize();

        double ax = pos.x + f.x * lookAheadDist;
        double az = pos.z + f.z * lookAheadDist;
        return getGroundY(world, ax, az);
    }

    private double computeRequiredMinY(World world) {
        double[] ds = {0, 6, 12, 18, 26, 34};
        double gMax = -1.0E9;

        for (double d : ds) {
            double g = getGroundAheadY(world, d);
            if (g > gMax) gMax = g;
        }

        BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());
        return gMax + v.minAltitudeAboveGround();
    }

    private double computeRequiredMinYAt(World world, Vec3d atPos) {
        Vec3d f = new Vec3d(forward.x, 0, forward.z);
        if (f.lengthSquared() < 1.0E-6) f = new Vec3d(0, 0, 1);
        f = f.normalize();

        double[] ds = {0, 6, 12, 18, 26};
        double gMax = -1.0E9;

        for (double d : ds) {
            double ax = atPos.x + f.x * d;
            double az = atPos.z + f.z * d;
            double g = getGroundY(world, ax, az);
            if (g > gMax) gMax = g;
        }

        BirdSpecies.BirdSpeciesView v = species.viewForTime(world.isDaytime());
        return gMax + v.minAltitudeAboveGround();
    }

    public enum Mode {
        GLIDE, CIRCLE, SOAR, SWOOP
    }
}