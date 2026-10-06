package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.entity.EntityCruiseMissile;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;

/** Engine drone that follows a flying ramp unit while it has fuel; range comes from the type's {@code motorSoundRange}. */
public class UnitEngineSound extends MovingSound {
    private final EntityRampUnit unit;
    /** The unit's age when this sound started (a missile's scream rises from the start of its dive). */
    private final int startTick;

    public UnitEngineSound(EntityRampUnit unit) {
        super(engineOf(unit), SoundCategory.NEUTRAL);
        this.unit = unit;
        this.startTick = unit.ticksExisted;
        repeat = true;
        repeatDelay = 0;
        DroneType type = unit.getDroneType();
        // Range comes from volume; the pilot riding a missile sits on top of it and needs no range
        volume = isPilot(unit) ? 1.0F : (float) Math.max(1.0, (type == null ? 64 : type.getMotorSoundRange()) / 16.0);
        update();
    }

    private static boolean isPilot(EntityRampUnit unit) {
        return unit == RemoteController.getUnit();
    }

    /**
     * The type's own engine loop (electric whine, two-stroke, piston...), or the generic one. A diving missile comes
     * in with the incoming roar for everyone below; its pilot keeps hearing the engine.
     */
    private static SoundEvent engineOf(EntityRampUnit unit) {
        if (unit instanceof EntityCruiseMissile && ((EntityCruiseMissile) unit).isDiving() && !isPilot(unit)) {
            return ModSounds.MISSILE_SCREAM;
        }
        DroneType type = unit.getDroneType();
        return ModSounds.engine(type == null ? null : type.getEngineSound(), ModSounds.UNIT_ENGINE);
    }

    /** A diving missile's roar rises a little as it comes down (over about an unboosted dive), more when boosting. */
    static float screamPitch(int diveTicks, boolean boosting) {
        return 0.9F + 0.25F * Math.min(1, diveTicks / 170.0F) + (boosting ? 0.1F : 0);
    }

    static boolean isRunning(EntityRampUnit unit) {
        return !unit.isDead && unit.getFuelFraction() > 0;
    }

    @Override
    public void update() {
        if (!isRunning(unit) || !engineOf(unit).getSoundName().equals(positionedSoundLocation)) {
            // Gone quiet, or a missile went from its hold into the dive: the sound tick starts the right one afresh
            donePlaying = true;
            return;
        }
        xPosF = (float) unit.posX;
        yPosF = (float) unit.posY;
        zPosF = (float) unit.posZ;
        if (unit instanceof EntityCruiseMissile && ((EntityCruiseMissile) unit).isDiving()) {
            pitch = screamPitch(unit.ticksExisted - startTick, unit.isBoosting());
            return;
        }
        DroneType type = unit.getDroneType();
        double speed = unit.getSyncedSpeed();
        double speedFraction = type == null || type.getMaxSpeed() <= 0 ? 0.8 : speed / type.getMaxSpeed();
        pitch = 0.8F + 0.4F * (float) Math.max(0, Math.min(1, speedFraction)) + (unit.isBoosting() ? 0.25F : 0);
    }
}
