package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.physics.FieldContact;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * EMP fields (the station's and the blast gadget's): every flying drone, ramp unit or missile whose hitbox touches the
 * sphere is knocked out as if shot down, unless the field's {@code spares} setting leaves it alone. A hand-flown drone
 * sitting on the ground isn't flying and is left alone.
 */
public final class Emp {
    private Emp() {
    }

    /** Server, every tick a field is up. {@code spares}: "none", "owner" or "team" (see the config). */
    public static void fry(World world, Vec3d centre, double radius, @Nullable UUID fieldOwner, String spares) {
        AxisAlignedBB reach = new AxisAlignedBB(centre.x, centre.y, centre.z, centre.x, centre.y, centre.z).grow(radius + 4);
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, reach, entity -> entity != null && !entity.isDead && isVehicle(entity))) {
            AxisAlignedBB box = entity.getEntityBoundingBox();
            if (!FieldContact.touches(centre.x, centre.y, centre.z, radius, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
                    || spared(fieldOwner, ownerOf(entity), spares)) {
                continue;
            }
            EffectPacket.empZap(world, new Vec3d(entity.posX, entity.posY + entity.height / 2, entity.posZ), Math.max(entity.width, entity.height));
            if (entity instanceof EntityRampUnit) {
                ((EntityRampUnit) entity).shotDown();
            } else {
                ((EntityHandFlownDrone) entity).shotDown();
            }
        }
    }

    private static boolean isVehicle(Entity entity) {
        return entity instanceof EntityRampUnit || (entity instanceof EntityHandFlownDrone && !entity.onGround);
    }

    @Nullable
    private static UUID ownerOf(Entity entity) {
        return entity instanceof EntityRampUnit ? ((EntityRampUnit) entity).getOwner() : ((EntityHandFlownDrone) entity).getOwner();
    }

    static boolean spared(@Nullable UUID fieldOwner, @Nullable UUID vehicleOwner, String spares) {
        if (fieldOwner == null || vehicleOwner == null || "none".equals(spares)) {
            return false;
        }
        return "team".equals(spares) ? Teams.isTeammate(fieldOwner, vehicleOwner) : fieldOwner.equals(vehicleOwner);
    }
}
