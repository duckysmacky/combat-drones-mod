package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.config.LockOnSettings;
import io.github.duckysmacky.combatdrones.type.DroneType;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A vehicle whose camera can lock on to enemies (a sensor type): the Recon UAV and the Recon Drone. Charges are spent
 * on the server and synced for the HUD.
 */
public interface LockOnCarrier {
    @Nullable
    DroneType getDroneType();

    @Nullable
    UUID getOwner();

    /** Server: its lock-on settings ([uav], or a hand-flown type's own table). */
    LockOnSettings getLockOnSettings();

    /** Lock-on charges a full vehicle holds, as the server has it (0 = lock-on is off). */
    int getLockMaxCharges();

    /** Lock-on charges left (synced). */
    int getLockCharges();

    /** Ticks until the next lock-on charge is back: 0 when none is reloading, -1 while the reload is on hold. */
    int getLockReloadLeft();

    /** How far the next lock-on charge has reloaded, 0..1 (0 when none is reloading; held where it was while on hold). */
    float getLockReloadFraction();

    /** Server: uses a lock-on charge; false when there's none left. */
    boolean spendLockCharge();
}
