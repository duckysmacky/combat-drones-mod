package io.github.duckysmacky.combatdrones.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissileGuidanceTest {
    private static final double[] STILL = {0, 0, 0};

    @Test
    void keepsItsSpeed() {
        double[] velocity = MissileGuidance.steer(new double[] {0, 0, 3}, new double[] {40, 10, 5}, new double[] {1, 0, 0}, 3, 12);
        assertEquals(3, Math.sqrt(velocity[0] * velocity[0] + velocity[1] * velocity[1] + velocity[2] * velocity[2]), 1.0E-9);
    }

    @Test
    void turnsNoFasterThanItsLimit() {
        // Target square to the right: only 12 degrees of the 90 this tick
        double[] velocity = MissileGuidance.steer(new double[] {0, 0, 3}, new double[] {50, 0, 0}, STILL, 3, 12);
        assertEquals(12, LockOnAttempt.angleBetween(0, 0, 3, velocity[0], velocity[1], velocity[2]), 1.0E-6);
        assertTrue(velocity[0] > 0);
    }

    @Test
    void pointsStraightAtATargetInsideItsLimit() {
        double[] velocity = MissileGuidance.steer(new double[] {0, 0, 3}, new double[] {1, 0, 20}, STILL, 3, 12);
        assertEquals(0, LockOnAttempt.angleBetween(1, 0, 20, velocity[0], velocity[1], velocity[2]), 1.0E-6);
    }

    @Test
    void turnsRoundFromDeadAstern() {
        double[] velocity = MissileGuidance.steer(new double[] {0, 0, 3}, new double[] {0, 0, -30}, STILL, 3, 12);
        assertEquals(12, LockOnAttempt.angleBetween(0, 0, 3, velocity[0], velocity[1], velocity[2]), 1.0E-6);
    }

    @Test
    void launchedWithNoHeadingGoesForTheTarget() {
        double[] velocity = MissileGuidance.steer(STILL, new double[] {0, 10, 0}, STILL, 3, 12);
        assertEquals(3, velocity[1], 1.0E-9);
    }

    @Test
    void runsDownACrossingTarget() {
        // The fastest aircraft (2.35 blocks/tick) crossing 60 blocks out, the missile launched pointing 30 degrees off
        double[] missile = {0, 0, 0};
        double[] velocity = {1.5, 0, 2.6};
        double[] target = {-20, 30, 50};
        double[] targetMotion = {2.35, 0, 0};
        double closest = Double.MAX_VALUE;
        for (int tick = 0; tick < 200 && closest > 1.5; tick++) {
            velocity = MissileGuidance.steer(velocity, new double[] {target[0] - missile[0], target[1] - missile[1], target[2] - missile[2]},
                    targetMotion, 3, 12);
            // Closest pass within the tick, as the missile's swept hit check sees it
            for (int step = 1; step <= 10; step++) {
                double dx = target[0] + targetMotion[0] * step / 10 - missile[0] - velocity[0] * step / 10;
                double dy = target[1] - missile[1] - velocity[1] * step / 10;
                double dz = target[2] - missile[2] - velocity[2] * step / 10;
                closest = Math.min(closest, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            for (int axis = 0; axis < 3; axis++) {
                missile[axis] += velocity[axis];
                target[axis] += targetMotion[axis];
            }
        }
        assertTrue(closest <= 1.5, "closest pass " + closest);
    }
}
