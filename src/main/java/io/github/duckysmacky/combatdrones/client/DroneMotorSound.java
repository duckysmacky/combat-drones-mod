package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;

/**
 * Looping motor noise that follows one drone. Vanilla hears a sound out to 16 × volume blocks (playback loudness is
 * still capped at 1), so volume encodes the type's {@code motorSoundRange}: drones are meant to be heard from afar.
 */
public class DroneMotorSound extends MovingSound {
    private final EntityHandFlownDrone drone;

    public DroneMotorSound(EntityHandFlownDrone drone) {
        super(ModSounds.DRONE_MOTOR, SoundCategory.NEUTRAL);
        this.drone = drone;
        repeat = true;
        repeatDelay = 0;
        DroneType type = drone.getDroneType();
        volume = (float) Math.max(1.0, (type == null ? 64 : type.getMotorSoundRange()) / 16.0);
        update();
    }

    @Override
    public void update() {
        if (drone.isDead || !drone.areMotorsOn()) {
            donePlaying = true;
            return;
        }
        xPosF = (float) drone.posX;
        yPosF = (float) drone.posY;
        zPosF = (float) drone.posZ;
        pitch = 0.7F + 0.8F * Math.max(0, Math.min(1, drone.getThrust()));
    }
}
