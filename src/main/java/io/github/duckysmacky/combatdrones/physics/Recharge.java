package io.github.duckysmacky.combatdrones.physics;

/**
 * Charges on a hand-held gadget, worked out from world time instead of ticked: {@code stored} charges as of
 * {@code since}, plus one for every full {@code reloadTicks} after it, up to {@code max}. Nothing needs to update while
 * the item sits in an inventory.
 */
public final class Recharge {
    private Recharge() {
    }

    /** Charges ready at {@code now}. */
    public static int available(int stored, long since, long now, int max, int reloadTicks) {
        if (stored >= max) {
            return max;
        }
        long gained = elapsed(since, now) / Math.max(1, reloadTicks);
        return (int) Math.min(max, Math.max(0, stored) + gained);
    }

    /** Ticks until the next charge is ready, 0 when full. */
    public static long ticksToNext(int stored, long since, long now, int max, int reloadTicks) {
        if (available(stored, since, now, max, reloadTicks) >= max) {
            return 0;
        }
        int reload = Math.max(1, reloadTicks);
        return reload - elapsed(since, now) % reload;
    }

    /**
     * Uses one charge at {@code now}: the new {charges, since}, keeping any reload already under way. The caller has
     * checked there is a charge.
     */
    public static long[] spend(int stored, long since, long now, int max, int reloadTicks) {
        int ready = available(stored, since, now, max, reloadTicks);
        if (ready >= max) {
            return new long[] {ready - 1, now}; // the reload starts now
        }
        int reload = Math.max(1, reloadTicks);
        long start = Math.min(since, now);
        long progressed = start + (elapsed(start, now) / reload) * reload;
        return new long[] {ready - 1, progressed};
    }

    /** A start in the future (an item from another world) gains nothing; the item resets it to now. */
    private static long elapsed(long since, long now) {
        return Math.max(0, now - since);
    }
}
