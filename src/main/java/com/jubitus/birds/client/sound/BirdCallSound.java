package com.jubitus.birds.client.sound;

import com.jubitus.birds.client.ClientBird;
import com.jubitus.birds.client.config.BirdConfig;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class BirdCallSound extends MovingSound {

    private static final int OUT_OF_RANGE_GRACE_TICKS = 20; // 1 second
    private static final float STOP_HYSTERESIS = 4.0f;      // extra blocks past maxDist before counting grace
    private final ClientBird bird;
    private final World world;
    private final long birdId;
    private final float baseVolume;
    private final float basePitch;
    private final float maxDist;
    private final float fadeStart;
    private final float fadePower;
    // thickness occlusion state
    private float thicknessTarget = 0.0f;   // in blocks
    private float thicknessSmooth = 0.0f;   // smoothed thickness
    private float underwaterTarget = 0.0f;  // 0..1
    private float underwaterSmoothVal = 0.0f; // smoothed 0..1
    // echo spam guard
    private int lastEchoTick = Integer.MIN_VALUE;
    // runtime state
    private int occlusionTick = 0;
    private float occlusionTarget = 0.0f;  // 0..1
    private float occlusionSmooth = 0.0f;  // 0..1 (smoothed)
    private int outOfRangeTicks = 0;


    public BirdCallSound(ClientBird bird,
                         World world,
                         SoundEvent event,
                         float volume,
                         float pitch,
                         float maxDist,
                         float fadeStart,
                         float fadePower) {

        super(event, SoundCategory.AMBIENT);

        this.bird = bird;
        this.world = world;
        this.birdId = bird.getId();

        this.baseVolume = volume;
        this.basePitch = pitch;

        this.maxDist = maxDist;
        this.fadeStart = fadeStart;
        this.fadePower = fadePower;

        this.repeat = false;     // one-shot, can still be long
        this.repeatDelay = 0;

        this.attenuationType = AttenuationType.NONE;

        // initial position
        this.xPosF = (float) bird.pos.x;
        this.yPosF = (float) bird.pos.y;
        this.zPosF = (float) bird.pos.z;

        // initialize current values
        this.pitch = basePitch;

// Set correct initial volume BEFORE playSound() happens
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null) {
            double px = mc.player.posX;
            double py = mc.player.posY + mc.player.getEyeHeight();
            double pz = mc.player.posZ;

            double dx = this.xPosF - px;
            double dy = this.yPosF - py;
            double dz = this.zPosF - pz;

            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float gain = computeGainFromDistance(d);
            this.volume = baseVolume * gain;
        } else {
            // fallback
            this.volume = baseVolume;
        }

    }

    private float computeGainFromDistance(float d) {
        float md = Math.max(0.001f, maxDist);
        float fs = Math.max(0f, Math.min(fadeStart, md));

        float gain;
        if (d <= fs) {
            gain = 1.0f;
        } else {
            float denom = Math.max(0.001f, (md - fs));
            float t = (d - fs) / denom;
            if (t < 0f) t = 0f;
            if (t > 1f) t = 1f;

            float oneMinus = 1.0f - t;
            gain = (float) Math.pow(oneMinus, Math.max(0.01f, fadePower));
        }

        // If you added perceptual drop:
        gain *= gain;

        // If you still use a floor, apply it ONLY while in range:
        // if (d < md) {
        //     float floor = 0.0f; // I'd keep this 0 if you're fighting "too loud far"
        //     gain = floor + (1.0f - floor) * gain;
        // }

        return gain;
    }

    private static float distanceToPlayer(EntityPlayer player, Vec3d soundPos) {
        double px = player.posX;
        double py = player.posY + player.getEyeHeight();
        double pz = player.posZ;
        double dx = px - soundPos.x;
        double dy = py - soundPos.y;
        double dz = pz - soundPos.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public void update() {
        if (bird == null || world == null || bird.pos == null) {
            this.donePlaying = true;
            return;
        }

        // Follow bird
        float bx = (float) bird.pos.x;
        float by = (float) bird.pos.y;
        float bz = (float) bird.pos.z;

        this.xPosF = bx;
        this.yPosF = by;
        this.zPosF = bz;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null) {
            this.volume = baseVolume;
            this.pitch = basePitch;
            return;
        }

        // Distance to listener (player eye)
        double px = mc.player.posX;
        double py = mc.player.posY + mc.player.getEyeHeight();
        double pz = mc.player.posZ;

// ---- Underwater muffling (smooth) ----
        float underwaterGainMul = 1.0f;
        float underwaterPitchMul = 1.0f;

        if (BirdConfig.underwaterMuffleEnabled) {
            // Check WATER at eye position (and slightly below to avoid surface rounding issues)
            BlockPos eyePosA = new BlockPos(px, py, pz);
            BlockPos eyePosB = new BlockPos(px, py - 0.25, pz);

            boolean eyesInWater =
                    world.getBlockState(eyePosA).getMaterial() == net.minecraft.block.material.Material.WATER ||
                            world.getBlockState(eyePosB).getMaterial() == net.minecraft.block.material.Material.WATER;

            underwaterTarget = eyesInWater ? 1.0f : 0.0f;
            underwaterSmoothVal = lerp(underwaterSmoothVal, underwaterTarget, (float) BirdConfig.underwaterSmooth);

            underwaterGainMul = lerp(1.0f, (float) BirdConfig.underwaterGain, underwaterSmoothVal);
            underwaterPitchMul = lerp(1.0f, (float) BirdConfig.underwaterPitchMul, underwaterSmoothVal);
        }

        double dx = bx - px;
        double dy = by - py;
        double dz = bz - pz;

        double d2 = dx * dx + dy * dy + dz * dz;
        float d = (float) Math.sqrt(d2);

        // Optional: if extremely close, push emitter outward for nicer stereo cues
        double min = 2.0; // blocks
        if (d2 < min * min && d2 > 1e-6) {
            double dist = Math.sqrt(d2);
            double k = (min / dist);
            this.xPosF = (float) (px + dx * k);
            this.yPosF = (float) (py + dy * k);
            this.zPosF = (float) (pz + dz * k);
        }

// --- Custom fade curve (no hard stop at maxDist) ---
        float md = Math.max(0.001f, maxDist);
        float fs = Math.max(0f, Math.min(fadeStart, md));

        float gain;
        if (d <= fs) {
            gain = 1.0f;
        } else {
            float denom = Math.max(0.001f, (md - fs));
            float t = (d - fs) / denom;
            if (t < 0f) t = 0f;
            if (t > 1f) t = 1f;

            float oneMinus = 1.0f - t;
            gain = (float) Math.pow(oneMinus, Math.max(0.01f, fadePower));
        }

// ---- Occlusion + thickness (smoothed, no harsh cut) ----
        if (gain > 0.02f) {
            int interval = Math.max(1, BirdConfig.occlusionCheckIntervalTicks);

            if ((occlusionTick++ % interval) == 0) {
                Vec3d listener = new Vec3d(px, py, pz);
                Vec3d source = new Vec3d(bx, by, bz);

                OcclusionInfo info = computeOcclusionThickness(world, listener, source);
                occlusionTarget = info.blockedFraction;
                thicknessTarget = info.thicknessBlocks;
            }

            float smooth = (float) BirdConfig.occlusionSmooth;
            occlusionSmooth = lerp(occlusionSmooth, occlusionTarget, smooth);
            thicknessSmooth = lerp(thicknessSmooth, thicknessTarget, smooth);

        } else {
            occlusionSmooth = lerp(occlusionSmooth, 0.0f, 0.05f);
            thicknessSmooth = lerp(thicknessSmooth, 0.0f, 0.05f);
        }


// ---- Thickness-based volume loss ----
// thicknessSmooth is in "blocks of occluder" (already smoothed)
        float k = (float) BirdConfig.occlusionThicknessK;          // config: strength
        float maxT = (float) BirdConfig.occlusionThicknessMax;     // config: clamp
        float t = thicknessSmooth;
        if (t < 0f) t = 0f;
        if (t > maxT) t = maxT;

// Exponential muffling: 0 blocks -> 1.0, more blocks -> smaller
// Example: k=0.12 => 10 blocks => exp(-1.2)=0.30
        float thickGain = (float) Math.exp(-k * t);

// Optional: never go below this floor even with huge mountains
        float floor = (float) BirdConfig.occludedGain; // 0..1
        if (thickGain < floor) thickGain = floor;

// If nothing is blocked at all, don't apply thickness muffling (prevents “random muffling”)
        float occGain = (occlusionSmooth <= 0.05f) ? 1.0f : thickGain;

// Final volume
        this.volume = baseVolume * gain * occGain * underwaterGainMul;


// Optional: tiny pitch drop when thick occluded (feels muffled)
        this.pitch = basePitch * lerp(1.0f, 0.96f, Math.min(1.0f, thicknessSmooth / 12.0f)) * underwaterPitchMul;

// ---- Fake reflection / reverb: schedule a quiet delayed copy when heavily occluded ----
        if (BirdConfig.echoEnabled && world != null) {
            int nowTick = (int) world.getTotalWorldTime();

            boolean heavilyOccluded = (occlusionSmooth > 0.65f) && (thicknessSmooth >= (float) BirdConfig.echoMinThickness);

            // extra boost if player is underground/indoors
            BlockPos playerPos = new BlockPos(px, py, pz);
            boolean indoors = !world.canSeeSky(playerPos);

            if (heavilyOccluded || (indoors && occlusionSmooth > 0.45f)) {
                int cd = Math.max(0, BirdConfig.echoCooldownTicks);
                if (nowTick - lastEchoTick >= cd) {
                    lastEchoTick = nowTick;

                    float eVol = (float) (this.volume * BirdConfig.echoVolume);
                    float ePit = (float) (this.pitch * BirdConfig.echoPitchMul);

                    // place echo at listener (feels like room reflection / reverb tail)
                    ISound echo = new PositionedSoundRecord(
                            this.getSoundLocation(),
                            SoundCategory.AMBIENT,
                            eVol,
                            ePit,
                            false,                // repeat
                            0,                    // repeatDelay
                            ISound.AttenuationType.LINEAR,
                            (float) px, (float) py, (float) pz
                    );


                    BirdSoundSystem.schedulePlay(echo, BirdConfig.echoDelayTicks);
                }
            }
        }


        // --- Grace stop logic (prevents "0.1s cut" while still freeing channels) ---
        // Only start counting when we are clearly beyond maxDist (hysteresis avoids flapping).
        if (d > (md + STOP_HYSTERESIS)) {
            outOfRangeTicks++;
            if (outOfRangeTicks >= OUT_OF_RANGE_GRACE_TICKS) {
                this.donePlaying = true;
            }
        } else {
            outOfRangeTicks = 0;
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private OcclusionInfo computeOcclusionThickness(World world, Vec3d listener, Vec3d source) {
        Vec3d dir = source.subtract(listener);
        double dist = dir.length();
        if (dist < 0.001) return new OcclusionInfo(0.0f, 0.0f);

        dir = dir.scale(1.0 / dist);

        float step = (float) Math.max(0.25, Math.min(2.0, BirdConfig.occlusionSampleStep));
        float maxT = (float) Math.max(1.0, Math.min(256.0, BirdConfig.occlusionThicknessMax));

        int total = 0;
        int blocked = 0;
        float thickness = 0.0f;

        // sample along the segment
        // start slightly away from listener so you don't count the player's own block
        for (float t = 1.0f; t < dist; t += step) {
            if (thickness >= maxT) break;

            Vec3d p = listener.add(dir.scale(t));
            BlockPos bp = new BlockPos(p);

            IBlockState st = world.getBlockState(bp);
            total++;

            if (countsAsOccluder(st)) {
                blocked++;
                thickness += step;
            }
        }

        float frac = (total <= 0) ? 0.0f : (blocked / (float) total);
        if (thickness > maxT) thickness = maxT;

        return new OcclusionInfo(frac, thickness);
    }

    private boolean countsAsOccluder(IBlockState st) {
        if (st == null) return false;

        Material m = st.getMaterial();
        if (m == null) return false;

        // ignore "soft" stuff so bushes don't act like mountains
        if (m == Material.PLANTS || m == Material.VINE || m == Material.LEAVES || m == Material.WEB) return false;
        if (m.isLiquid()) return false;

        // blocks movement = solid-ish; good enough for occlusion
        return m.blocksMovement();
    }

    public long getBirdId() {
        return birdId;
    }

    /**
     * Returns 0..1 where:
     * 0 = clear line of sight
     * 1 = fully blocked (behind terrain/building / underground)
     * <p>
     * Uses multiple ray traces so it doesn't "flip" as much at edges.
     */
    private float computeOcclusion(World world, Vec3d listener, Vec3d source) {
        // Small multi-sample around the source point (cheap "partial occlusion")
        Vec3d[] targets = new Vec3d[]{
                source,
                source.add(0, 1.0, 0),
                source.add(0, -1.0, 0),
                source.add(0.8, 0.0, 0.0),
                source.add(-0.8, 0.0, 0.0)
        };

        int blocked = 0;
        for (Vec3d t : targets) {
            RayTraceResult hit = world.rayTraceBlocks(listener, t, false, true, false);
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
                blocked++;
            }
        }

        // 0..1 (ratio of rays blocked)
        return blocked / (float) targets.length;
    }

    private static class OcclusionInfo {
        final float blockedFraction;   // 0..1
        final float thicknessBlocks;   // >=0

        OcclusionInfo(float blockedFraction, float thicknessBlocks) {
            this.blockedFraction = blockedFraction;
            this.thicknessBlocks = thicknessBlocks;
        }
    }

}
