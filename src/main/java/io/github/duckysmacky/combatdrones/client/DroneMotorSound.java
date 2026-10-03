package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;

/**
 * Looping motor noise that follows one drone. Vanilla hears a sound out to 16 × volume blocks (playback loudness is
 * still capped at 1), so for everyone else volume encodes the type's {@code motorSoundRange}. The pilot's camera sits
 * on the drone, so for them it plays quietly instead: the noise is a warning for other players.
 */
public class DroneMotorSound extends MovingSound {
    private static final float PILOT_VOLUME = 0.2F;

    private final EntityHandFlownDrone drone;
    /** Range is fixed when a sound starts, so a change of pilot state ends this sound and a fresh one replaces it. */
    private final boolean forPilot;

    public DroneMotorSound(EntityHandFlownDrone drone) {
        super(drone.isLocallyPiloted() ? ModSounds.DRONE_MOTOR : ModSounds.DRONE_MOTOR_FAR, SoundCategory.NEUTRAL);
        this.drone = drone;
        repeat = true;
        repeatDelay = 0;
        forPilot = drone.isLocallyPiloted();
        DroneType type = drone.getDroneType();
        volume = forPilot ? PILOT_VOLUME : (float) Math.max(1.0, (type == null ? 32 : type.getMotorSoundRange()) / 16.0);
        update();
    }

    @Override
    public void update() {
        if (drone.isDead || !drone.areMotorsOn() || drone.isLocallyPiloted() != forPilot) {
            donePlaying = true;
            return;
        }
        xPosF = (float) drone.posX;
        yPosF = (float) drone.posY;
        zPosF = (float) drone.posZ;
        pitch = 0.8F + 0.7F * Math.max(0, Math.min(1, drone.getThrust()));
    }
}
