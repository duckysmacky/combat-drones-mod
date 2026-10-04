package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.network.EngineSoundPacket;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.util.SoundCategory;

/**
 * Engine of a plane this client doesn't track (too far for the server to stream it), placed from the server's
 * {@link EngineSoundPacket} updates and dead-reckoned in between. Same sound, range and pitch as
 * {@link UnitEngineSound}; it hands over as soon as the plane itself is tracked, and fades out if updates stop.
 */
public class FarEngineSound extends MovingSound {
    /** Updates come twice a second; without one for this long the plane is out of earshot or gone. */
    private static final int STALE_TICKS = 30;

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

    FarEngineSound(EngineSoundPacket.Entry entry, DroneType type) {
        super(ModSounds.engine(type.getEngineSound(), ModSounds.UNIT_ENGINE), SoundCategory.NEUTRAL);
        this.entityId = entry.entityId;
        this.type = type;
        repeat = true;
        repeatDelay = 0;
        volume = (float) Math.max(1.0, type.getMotorSoundRange() / 16.0);
        refresh(entry);
        update();
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
    public void update() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || ++ticksSinceUpdate > STALE_TICKS || minecraft.world.getEntityByID(entityId) instanceof EntityRampUnit) {
            donePlaying = true; // gone quiet, or close enough now for the entity's own engine sound
            return;
        }
        x += motionX;
        y += motionY;
        z += motionZ;
        xPosF = (float) x;
        yPosF = (float) y;
        zPosF = (float) z;
        double speed = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        double speedFraction = type.getMaxSpeed() <= 0 ? 0.8 : speed / type.getMaxSpeed();
        pitch = 0.8F + 0.4F * (float) Math.max(0, Math.min(1, speedFraction)) + (boosting ? 0.25F : 0);
    }
}
