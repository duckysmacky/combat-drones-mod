package io.github.duckysmacky.combatdrones.physics;

/**
 * Active lock from a recon camera: the crosshair has to stay on the same target for a number of consecutive ticks.
 * Switching targets or losing it starts over. Reports the lock once; holding on afterwards just keeps it at full.
 */
public final class LockTracker {
    public static final int NONE = -1;

    private final int requiredTicks;
    private int candidate = NONE;
    private int heldTicks;

    public LockTracker(int requiredTicks) {
        this.requiredTicks = Math.max(1, requiredTicks);
    }

    /** One tick with {@code candidateId} under the crosshair ({@link #NONE} for nothing). True on the tick it locks. */
    public boolean update(int candidateId) {
        if (candidateId != candidate) {
            candidate = candidateId;
            heldTicks = 0;
        }
        if (candidate == NONE || heldTicks >= requiredTicks) {
            return false;
        }
        heldTicks++;
        return heldTicks == requiredTicks;
    }

    public int getCandidate() {
        return candidate;
    }

    /** 0..1 toward a lock on the current candidate. */
    public double getProgress() {
        return candidate == NONE ? 0 : (double) heldTicks / requiredTicks;
    }

    /** Angle between two directions, in degrees (0 for a zero-length direction). */
    public static double angleBetween(double ax, double ay, double az, double bx, double by, double bz) {
        double lengths = Math.sqrt(ax * ax + ay * ay + az * az) * Math.sqrt(bx * bx + by * by + bz * bz);
        if (lengths == 0) {
            return 0;
        }
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (ax * bx + ay * by + az * bz) / lengths))));
    }
}
