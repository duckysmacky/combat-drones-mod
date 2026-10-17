package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.network.EngineSoundPacket;
import io.github.duckysmacky.combatdrones.physics.SoundBands;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Vec3d;

/**
 * Engine of a plane this client doesn't track (too far for the server to stream it), placed from the server's
 * {@link EngineSoundPacket} updates and dead-reckoned in between. Same bands and pitch as {@link UnitEngineSound};
 * it hands over as soon as the plane itself is tracked, and falls silent if updates stop.
 */
class FarEngineSound extends EngineVoice {
    /**
     * Updates come twice a second; without one for this long the plane is out of earshot or gone (a munition that hit
     * must not fly on, droning underground).
     */
    private static final int STALE_TICKS = 14;

    private final int entityId;
    private final DroneType type;
    private double x;
    private double y;
    private double z;
    private double motionX;
    private double motionY;
    private double motionZ;
    private boolean boosting;
    private int ticksSinceUpdate;
    private int diveTicks;

    private FarEngineSound(EngineSoundPacket.Entry entry, DroneType type) {
        super(type);
        this.entityId = entry.entityId;
        this.type = type;
        refresh(entry);
    }

    /** Null if the type is unknown on this client. */
    static FarEngineSound of(EngineSoundPacket.Entry entry) {
        DroneType type = DroneTypes.get(entry.typeId);
        return type == null ? null : new FarEngineSound(entry, type);
    }

    void refresh(EngineSoundPacket.Entry entry) {
        x = entry.x;
        y = entry.y;
        z = entry.z;
        motionX = entry.motionX;
        motionY = entry.motionY;
        motionZ = entry.motionZ;
        boosting = entry.boosting;
        ticksSinceUpdate = 0;
    }

    @Override
    boolean isRunning() {
        Minecraft minecraft = Minecraft.getMinecraft();
        // Gone quiet, or close enough now for the entity's own engine sound
        return minecraft.world != null && ticksSinceUpdate <= STALE_TICKS && !(minecraft.world.getEntityByID(entityId) instanceof EntityRampUnit);
    }

    @Override
    Vec3d position() {
        // Called once a tick: dead-reckon on from the last update
        ticksSinceUpdate++;
        x += motionX;
        y += motionY;
        z += motionZ;
        return new Vec3d(x, y, z);
    }

    @Override
    double enginePitch() {
        return UnitEngineSound.speedPitch(type, speed(), boosting);
    }

    private double speed() {
        return Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
    }

    @Override
    double diveAmount() {
        if (type.getCategory() != DroneType.Category.VERTICAL_STRIKE) {
            double pitchDown = Math.toDegrees(Math.atan2(-motionY, Math.sqrt(motionX * motionX + motionZ * motionZ)));
            return SoundBands.diveAmount(pitchDown, type.getMaxSpeed() <= 0 ? 0 : speed() / type.getMaxSpeed());
        }
        boolean diving = motionY < 0;
        diveTicks = diving ? diveTicks + 1 : 0;
        return diving ? 1 : 0;
    }

    @Override
    double divePitch() {
        if (type.getCategory() != DroneType.Category.VERTICAL_STRIKE) {
            return 0.85 + 0.3 * Math.min(1, type.getMaxSpeed() <= 0 ? 0 : speed() / type.getMaxSpeed()) + (boosting ? 0.1 : 0);
        }
        return UnitEngineSound.screamPitch(diveTicks, boosting);
    }
}
