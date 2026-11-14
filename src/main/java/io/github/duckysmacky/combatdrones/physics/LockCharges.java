package io.github.duckysmacky.combatdrones.physics;

/** A recon unit's lock-on charges: spent one per attempt, won back one at a time after a reload. */
public final class LockCharges {
    private int charges;
    /** Ticks into reloading the next charge. */
    private int reload;

    public LockCharges(int charges) {
        this.charges = Math.max(0, charges);
    }

    /** One tick. {@code paused}: the reload waits (e.g. while a lock-on is active). Clamps to a lowered maximum. */
    public void tick(int max, int reloadTicks, boolean paused) {
        if (charges >= max) {
            charges = Math.max(0, max);
            reload = 0;
        } else if (!paused && ++reload >= reloadTicks) {
            charges++;
            reload = 0;
        }
    }

    /** Uses a charge; false when there's none left. */
    public boolean spend() {
        if (charges <= 0) {
            return false;
        }
        charges--;
        return true;
    }

    public int getCharges() {
        return charges;
    }

    public int getReload() {
        return reload;
    }

    /** From saved data. */
    public void set(int charges, int reload) {
        this.charges = Math.max(0, charges);
        this.reload = Math.max(0, reload);
    }
}
