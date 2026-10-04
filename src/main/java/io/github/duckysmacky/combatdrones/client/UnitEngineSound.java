package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;

/** Engine drone that follows a flying ramp unit while it has fuel; range comes from the type's {@code motorSoundRange}. */
public class UnitEngineSound extends MovingSound {
    private final EntityRampUnit unit;

    public UnitEngineSound(EntityRampUnit unit) {
        super(engineOf(unit), SoundCategory.NEUTRAL);
        this.unit = unit;
        repeat = true;
        repeatDelay = 0;
        DroneType type = unit.getDroneType();
        volume = (float) Math.max(1.0, (type == null ? 64 : type.getMotorSoundRange()) / 16.0);
        update();
    }

    /** The type's own engine loop (electric whine, two-stroke, piston...), or the generic one. */
    private static SoundEvent engineOf(EntityRampUnit unit) {
        DroneType type = unit.getDroneType();
        return ModSounds.engine(type == null ? null : type.getEngineSound(), ModSounds.UNIT_ENGINE);
    }

    static boolean isRunning(EntityRampUnit unit) {
        return !unit.isDead && unit.getFuelFraction() > 0;
    }

    @Override
    public void update() {
        if (!isRunning(unit)) {
            donePlaying = true;
            return;
        }
        xPosF = (float) unit.posX;
        yPosF = (float) unit.posY;
        zPosF = (float) unit.posZ;
        DroneType type = unit.getDroneType();
        double speed = Math.sqrt(Math.pow(unit.posX - unit.prevPosX, 2) + Math.pow(unit.posY - unit.prevPosY, 2) + Math.pow(unit.posZ - unit.prevPosZ, 2));
        double speedFraction = type == null || type.getMaxSpeed() <= 0 ? 0.8 : speed / type.getMaxSpeed();
        pitch = 0.8F + 0.4F * (float) Math.max(0, Math.min(1, speedFraction)) + (unit.isBoosting() ? 0.25F : 0);
    }
}
