package com.jubitus.birds.client.util;

import net.minecraft.util.math.Vec3d;

public class BirdOrientation {
    public float yawDeg;   // Y axis (turn left/right)
    public float pitchDeg; // X axis (nose up/down)
    public float rollDeg;  // Z axis (banking)

    private float lastYaw;
    private float lastPitch;
    private float lastRoll;

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public void updateFromVelocity(Vec3d vel, float maxYawStepDeg, float maxPitchStepDeg, float maxRollStepDeg) {
        // If nearly stationary: keep last angles (prevents jitter)
        if (vel.lengthSquared() < 1e-4) {
            yawDeg = lastYaw;
            pitchDeg = lastPitch;
            rollDeg = lastRoll;
            return;
        }

        Vec3d v = vel.normalize();

// We want forward = +Z
// Yaw: rotation around Y so +Z points toward velocity XZ
        float targetYaw = (float) Math.toDegrees(Math.atan2(v.x, v.z));

// Pitch: nose up/down based on v.y vs horizontal speed
        double horiz = Math.sqrt(v.x * v.x + v.z * v.z);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(v.y, horiz));


        yawDeg = approachAngle(lastYaw, targetYaw, maxYawStepDeg);
        pitchDeg = approach(lastPitch, targetPitch, maxPitchStepDeg);

        // rollDeg is controlled separately by steering/banking
        rollDeg = approach(lastRoll, rollDeg, maxRollStepDeg);

        lastYaw = yawDeg;
        lastPitch = pitchDeg;
        lastRoll = rollDeg;
    }

    private static float approachAngle(float current, float target, float maxStep) {
        float delta = wrapDegrees(target - current);
        if (Math.abs(delta) <= maxStep) return target;
        return current + Math.signum(delta) * maxStep;
    }

    private static float approach(float current, float target, float maxStep) {
        float delta = target - current;
        if (Math.abs(delta) <= maxStep) return target;
        return current + Math.signum(delta) * maxStep;
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360.0f;
        if (deg >= 180.0f) deg -= 360.0f;
        if (deg < -180.0f) deg += 360.0f;
        return deg;
    }

    public void setTargetRoll(float targetRollDeg, float maxStepDeg) {
        rollDeg = approach(lastRoll, targetRollDeg, maxStepDeg);
        lastRoll = rollDeg;
    }
}
