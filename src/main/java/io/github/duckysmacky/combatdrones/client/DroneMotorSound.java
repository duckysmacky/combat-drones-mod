package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;

/**
 * The pilot's own motor noise: their camera sits on the drone, so it plays quietly, the high prop whine. Everyone
 * else hears the drone through its {@link DroneEngineSound} bands.
 */
public class DroneMotorSound extends MovingSound {
    private static final float PILOT_VOLUME = 0.2F;

    private final EntityHandFlownDrone drone;

    public DroneMotorSound(EntityHandFlownDrone drone) {
        super(ModSounds.DRONE_MOTOR, SoundCategory.NEUTRAL);
        this.drone = drone;
        repeat = true;
        repeatDelay = 0;
        volume = PILOT_VOLUME;
        update();
    }

    @Override
    public void update() {
        if (drone.isDead || !drone.areMotorsOn() || !drone.isLocallyPiloted()) {
            donePlaying = true;
            return;
        }
        xPosF = (float) drone.posX;
        yPosF = (float) drone.posY;
        zPosF = (float) drone.posZ;
        pitch = thrustPitch(drone);
    }

    static float thrustPitch(EntityHandFlownDrone drone) {
        return 0.8F + 0.7F * Math.max(0, Math.min(1, drone.getThrust()));
    }
}
