package com.jubitus.birds.client.util;

import com.jubitus.birds.client.ClientBird;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class FlockingRules {

    public static Vec3d boidsSteer(ClientBird self, List<ClientBird> candidates, Params p) {
        Vec3d cohesion = Vec3d.ZERO;
        Vec3d alignment = Vec3d.ZERO;
        Vec3d separation = Vec3d.ZERO;

        int count = 0;

        for (ClientBird other : candidates) {
            if (other == self) continue;
            if (other.flockId != self.flockId) continue; // only flock with same flockId

            double d2 = self.pos.squareDistanceTo(other.pos);
            if (d2 > p.neighborRadius * p.neighborRadius) continue;

            count++;

            // cohesion: toward average position
            cohesion = cohesion.add(other.pos);

            // alignment: toward average velocity
            alignment = alignment.add(other.vel);

            // separation: avoid close neighbors
            if (d2 < p.separationRadius * p.separationRadius && d2 > 1e-6) {
                Vec3d away = self.pos.subtract(other.pos).normalize().scale(1.0 / Math.sqrt(d2));
                separation = separation.add(away);
            }
        }

        if (count == 0) return Vec3d.ZERO;

        cohesion = cohesion.scale(1.0 / count).subtract(self.pos);
        if (cohesion.lengthSquared() > 1e-8) cohesion = cohesion.normalize();

        if (alignment.lengthSquared() > 1e-8) alignment = alignment.normalize();

        if (separation.lengthSquared() > 1e-8) separation = separation.normalize();

        Vec3d force =
                cohesion.scale(p.weightCohesion)
                        .add(alignment.scale(p.weightAlignment))
                        .add(separation.scale(p.weightSeparation));

        // cap steering magnitude
        if (force.lengthSquared() > p.maxForce * p.maxForce) {
            force = force.normalize().scale(p.maxForce);
        }
        return force;
    }

    public static class Params {
        public double neighborRadius = 48.0;
        public double separationRadius = 6.0;

        public double weightCohesion = 1.35;
        public double weightAlignment = 1.05;
        public double weightSeparation = 0.65;

        public double maxForce = 0.10;

    }
}
