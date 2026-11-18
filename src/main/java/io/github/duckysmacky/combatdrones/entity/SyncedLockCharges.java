package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.config.LockOnSettings;
import io.github.duckysmacky.combatdrones.physics.LockCharges;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;

/**
 * A sensor vehicle's lock-on charges: the count itself lives on the server, the HUD reads a synced copy. Synced: charges
 * left, how many it holds (0 = lock-on off on this server), the reload length in ticks and the world tick the next one
 * is back (0 = not reloading; while the reload is on hold, -1 minus the ticks already reloaded).
 */
final class SyncedLockCharges {
    /** The data keys; each entity class creates its own, since createKey belongs in the class it registers for. */
    static final class Keys {
        final DataParameter<Integer> max;
        final DataParameter<Integer> charges;
        final DataParameter<Integer> readyTick;
        final DataParameter<Integer> reloadLength;

        Keys(DataParameter<Integer> max, DataParameter<Integer> charges, DataParameter<Integer> readyTick, DataParameter<Integer> reloadLength) {
            this.max = max;
            this.charges = charges;
            this.readyTick = readyTick;
            this.reloadLength = reloadLength;
        }

        /** From the entity's entityInit. */
        void register(EntityDataManager dataManager) {
            dataManager.register(max, 0);
            dataManager.register(reloadLength, 1);
            dataManager.register(charges, 0);
            dataManager.register(readyTick, 0);
        }
    }

    private final Entity entity;
    private final Keys keys;
    /** Starts over-full: the first tick clamps it to the vehicle's lockOnCharges. */
    private final LockCharges lockCharges = new LockCharges(Integer.MAX_VALUE);

    SyncedLockCharges(Entity entity, Keys keys) {
        this.entity = entity;
        this.keys = keys;
    }

    /** Server, every tick: reloads one charge at a time, waiting while this vehicle has a running lock-on (unless reloadDuringLockOn). */
    void tick(LockOnSettings settings) {
        int reloadTicks = settings.reloadTicks();
        boolean paused = !settings.reloadDuringLockOn && ReconIntel.activeLockOns(entity.getUniqueID()) > 0;
        lockCharges.tick(settings.lockOnCharges, reloadTicks, paused);
        int charges = lockCharges.getCharges();
        EntityDataManager data = entity.getDataManager();
        data.set(keys.max, settings.lockOnEnabled ? settings.lockOnCharges : 0);
        data.set(keys.charges, charges);
        data.set(keys.reloadLength, reloadTicks);
        data.set(keys.readyTick, charges >= settings.lockOnCharges ? 0
                : paused ? -1 - lockCharges.getReload() : (int) entity.world.getTotalWorldTime() + reloadTicks - lockCharges.getReload());
    }

    /** Server: uses a charge; false when there's none left. */
    boolean spend() {
        boolean spent = lockCharges.spend();
        entity.getDataManager().set(keys.charges, lockCharges.getCharges());
        return spent;
    }

    int max() {
        return entity.getDataManager().get(keys.max);
    }

    int left() {
        return entity.getDataManager().get(keys.charges);
    }

    /** Ticks until the next charge is back: 0 when none is reloading, -1 while the reload is on hold. */
    int reloadLeft() {
        int ready = entity.getDataManager().get(keys.readyTick);
        return ready < 0 ? -1 : ready == 0 ? 0 : Math.max(0, ready - (int) entity.world.getTotalWorldTime());
    }

    /** How far the next charge has reloaded, 0..1 (0 when none is reloading; held where it was while on hold). */
    float reloadFraction() {
        int ready = entity.getDataManager().get(keys.readyTick);
        float length = Math.max(1, entity.getDataManager().get(keys.reloadLength));
        if (ready < 0) {
            return Math.min(1, (-1 - ready) / length);
        }
        return ready == 0 ? 0 : Math.max(0, Math.min(1, 1 - (ready - entity.world.getTotalWorldTime()) / length));
    }

    void write(NBTTagCompound compound) {
        compound.setInteger("LockCharges", lockCharges.getCharges());
        compound.setInteger("LockReload", lockCharges.getReload());
    }

    void read(NBTTagCompound compound) {
        if (compound.hasKey("LockCharges")) {
            lockCharges.set(compound.getInteger("LockCharges"), compound.getInteger("LockReload"));
        }
    }
}
