package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityCruiseMissile;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.physics.SoundBands;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Engine of a flying ramp unit this client tracks, while it has fuel. */
class UnitEngineSound extends EngineVoice {
    /** Look this far ahead (ticks) for what it's about to hit. */
    private static final int IMPACT_LOOKAHEAD_TICKS = 80;

    private final EntityRampUnit unit;
    /** Age when its dive started (a missile's scream rises from the start of its dive); -1 = not diving. */
    private int diveStartTick = -1;

    UnitEngineSound(EntityRampUnit unit, DroneType type) {
        super(type);
        this.unit = unit;
    }

    /** A diving missile's roar rises a little as it comes down (over about an unboosted dive), more when boosting. */
    static float screamPitch(int diveTicks, boolean boosting) {
        return 0.9F + 0.25F * Math.min(1, diveTicks / 170.0F) + (boosting ? 0.1F : 0);
    }

    static boolean isRunning(EntityRampUnit unit) {
        return !unit.isDead && unit.getFuelFraction() > 0;
    }

    /** Engine pitch from speed against top speed, up when boosting. */
    static double speedPitch(DroneType type, double speed, boolean boosting) {
        double speedFraction = type.getMaxSpeed() <= 0 ? 0.8 : speed / type.getMaxSpeed();
        return 0.8 + 0.4 * Math.max(0, Math.min(1, speedFraction)) + (boosting ? 0.25 : 0);
    }

    @Override
    boolean isRunning() {
        return isRunning(unit);
    }

    @Override
    Vec3d position() {
        return unit.getPositionVector();
    }

    @Override
    double enginePitch() {
        DroneType type = unit.getDroneType();
        return type == null ? 1 : speedPitch(type, unit.getSyncedSpeed(), unit.isBoosting());
    }

    @Override
    boolean heardFromInside() {
        return unit == RemoteController.getUnit();
    }

    /** A missile's dive scream and closing roar are the warning for those below; its pilot keeps the engine. */
    @Override
    boolean diveHeardInside() {
        return !(unit instanceof EntityCruiseMissile);
    }

    private double speedFraction() {
        DroneType type = unit.getDroneType();
        return type == null || type.getMaxSpeed() <= 0 ? 0 : unit.getSyncedSpeed() / type.getMaxSpeed();
    }

    private Vec3d velocity() {
        return Vec3d.fromPitchYaw(unit.rotationPitch, unit.rotationYaw).scale(unit.getSyncedSpeed());
    }

    @Override
    double diveAmount() {
        if (!(unit instanceof EntityCruiseMissile)) {
            return SoundBands.diveAmount(unit.rotationPitch, speedFraction());
        }
        if (!((EntityCruiseMissile) unit).isDiving()) {
            diveStartTick = -1;
            return 0;
        }
        if (diveStartTick < 0) {
            diveStartTick = unit.ticksExisted;
        }
        return 1;
    }

    @Override
    double divePitch() {
        if (!(unit instanceof EntityCruiseMissile)) {
            // The air roar climbs with speed
            return 0.85 + 0.3 * Math.min(1, speedFraction()) + (unit.isBoosting() ? 0.1 : 0);
        }
        return screamPitch(unit.ticksExisted - diveStartTick, unit.isBoosting());
    }

    @Override
    double ticksToImpact() {
        Vec3d velocity = velocity();
        double speed = velocity.length();
        if (speed < 0.05) {
            return Double.POSITIVE_INFINITY;
        }
        Vec3d from = unit.getPositionVector();
        RayTraceResult hit = unit.world.rayTraceBlocks(from, from.add(velocity.scale(IMPACT_LOOKAHEAD_TICKS)), true, true, false);
        return hit == null ? Double.POSITIVE_INFINITY : hit.hitVec.distanceTo(from) / speed;
    }
}
