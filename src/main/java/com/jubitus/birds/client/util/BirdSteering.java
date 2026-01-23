package com.jubitus.birds.client.util;

import net.minecraft.util.math.Vec3d;

public class BirdSteering {

    public static Vec3d limitTurnXZ(Vec3d currentForward, Vec3d desiredForward, double maxTurnRad) {
        Vec3d c = new Vec3d(currentForward.x, 0, currentForward.z);
        Vec3d d = new Vec3d(desiredForward.x, 0, desiredForward.z);

        if (c.lengthSquared() < 1e-8) c = new Vec3d(0, 0, 1);
        if (d.lengthSquared() < 1e-8) d = new Vec3d(0, 0, 1);

        c = c.normalize();
        d = d.normalize();

        double dot = clamp(c.dotProduct(d), -1.0, 1.0);
        double angle = Math.acos(dot);
        if (angle <= maxTurnRad) return new Vec3d(d.x, desiredForward.y, d.z).normalize();

        double t = maxTurnRad / angle;
        Vec3d blended = c.scale(1 - t).add(d.scale(t)).normalize();
        return new Vec3d(blended.x, desiredForward.y, blended.z).normalize();
    }

    public static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
