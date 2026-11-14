package io.github.duckysmacky.combatdrones.physics;

/**
 * A recon lock-on attempt: started on one target, it locks once the crosshair has been on that target for a number
 * of ticks with the trigger held the whole time. Letting go of the trigger fails it at once; losing the target fails
 * it after a short grace (the camera direction arrives over the network, so a tick or two off target is jitter).
 */
public final class LockOnAttempt {
    public enum Result { IDLE, RUNNING, LOCKED, FAILED }

    public static final int NONE = -1;

    private final int requiredTicks;
    private final int graceTicks;
    private int target = NONE;
    private int heldTicks;
    private int missedTicks;

    public LockOnAttempt(int requiredTicks, int graceTicks) {
        this.requiredTicks = Math.max(1, requiredTicks);
        this.graceTicks = Math.max(0, graceTicks);
    }

    public void start(int targetId) {
        target = targetId;
        heldTicks = 0;
        missedTicks = 0;
    }

    public boolean isRunning() {
        return target != NONE;
    }

    public int getTarget() {
        return target;
    }

    /**
     * One tick of a running attempt: {@code onTarget} = the target is under the crosshair and in sight, {@code held}
     * = the trigger is held. LOCKED or FAILED end the attempt (it's idle again afterwards).
     */
    public Result tick(boolean onTarget, boolean held) {
        if (target == NONE) {
            return Result.IDLE;
        }
        if (!held) {
            return finish(Result.FAILED);
        }
        if (onTarget) {
            missedTicks = 0;
            if (++heldTicks >= requiredTicks) {
                return finish(Result.LOCKED);
            }
        } else if (++missedTicks > graceTicks) {
            return finish(Result.FAILED);
        }
        return Result.RUNNING;
    }

    private Result finish(Result result) {
        target = NONE;
        heldTicks = 0;
        missedTicks = 0;
        return result;
    }

    /** 0..1 toward the lock (0 while idle). */
    public double getProgress() {
        return target == NONE ? 0 : (double) heldTicks / requiredTicks;
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
