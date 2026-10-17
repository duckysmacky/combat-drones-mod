package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.util.math.Vec3d;

/** A hand-flown drone's motors as everyone but its pilot hears them (the pilot gets {@link DroneMotorSound}). */
class DroneEngineSound extends EngineVoice {
    private final EntityHandFlownDrone drone;

    DroneEngineSound(EntityHandFlownDrone drone, DroneType type) {
        super(type);
        this.drone = drone;
    }

    @Override
    boolean isRunning() {
        return !drone.isDead && drone.areMotorsOn() && !drone.isLocallyPiloted();
    }

    @Override
    Vec3d position() {
        return drone.getPositionVector();
    }

    @Override
    double enginePitch() {
        return DroneMotorSound.thrustPitch(drone);
    }
}
