package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.Fx;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.physics.FieldContact;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * EMP fields (the station's and the blast gadget's): every flying drone, ramp unit or missile whose hitbox touches the
 * sphere is either destroyed with a small blast (no warhead, bombs or fuel going off) or disabled for a while (motors
 * off, falling, nobody flying, viewing or re-routing it), per the field's {@link Rules}. A hand-flown drone sitting
 * on the ground isn't flying and is left alone.
 */
public final class Emp {
    private Emp() {
    }

    /** One field's settings, from its config section. */
    public static final class Rules {
        final String spares;
        final boolean disable;
        final int disableTicks;
        final float fizzlePower;

        private Rules(String spares, String onContact, double disableSeconds, double fizzlePower) {
            this.spares = spares;
            this.disable = "disable".equals(onContact);
            this.disableTicks = (int) Math.round(disableSeconds * 20);
            this.fizzlePower = (float) fizzlePower;
        }

        public static Rules station() {
            return new Rules(ModConfig.stationSpares, ModConfig.stationOnContact, ModConfig.stationDisableSeconds, ModConfig.stationDestroyBlastPower);
        }

        public static Rules blast() {
            return new Rules(ModConfig.blastSpares, ModConfig.blastOnContact, ModConfig.blastDisableSeconds, ModConfig.blastDestroyBlastPower);
        }
    }

    /** Server, every tick a field is up. */
    public static void fry(World world, Vec3d centre, double radius, @Nullable UUID fieldOwner, Rules rules) {
        AxisAlignedBB reach = new AxisAlignedBB(centre.x, centre.y, centre.z, centre.x, centre.y, centre.z).grow(radius + 4);
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, reach, entity -> entity != null && !entity.isDead && isVehicle(entity))) {
            AxisAlignedBB box = entity.getEntityBoundingBox();
            if (!FieldContact.touches(centre.x, centre.y, centre.z, radius, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
                    || spared(fieldOwner, ownerOf(entity), rules.spares)) {
                continue;
            }
            boolean fresh = !(entity instanceof EntityRampUnit ? ((EntityRampUnit) entity).isEmpDisabled() : ((EntityHandFlownDrone) entity).isEmpDisabled());
            if (!rules.disable || fresh) {
                EffectPacket.empZap(world, new Vec3d(entity.posX, entity.posY + entity.height / 2, entity.posZ), Math.max(entity.width, entity.height));
            }
            if (entity instanceof EntityRampUnit) {
                EntityRampUnit unit = (EntityRampUnit) entity;
                if (rules.disable) {
                    unit.empDisable(rules.disableTicks, rules.fizzlePower);
                } else {
                    unit.empDestroy(rules.fizzlePower);
                }
            } else {
                EntityHandFlownDrone drone = (EntityHandFlownDrone) entity;
                if (rules.disable) {
                    drone.empDisable(rules.disableTicks, rules.fizzlePower);
                } else {
                    drone.empDestroy(rules.fizzlePower);
                }
            }
        }
    }

    /** Server: a vehicle gone to an EMP, already removed: a small blast, or with none, a wreck. */
    static void fizzle(Entity vehicle, @Nullable EntityLivingBase blame, float power) {
        if (power > 0) {
            DroneExplosion.detonate((WorldServer) vehicle.world, vehicle, blame, vehicle.posX, vehicle.posY + vehicle.height / 2, vehicle.posZ, power);
        } else {
            DamageEffects.wreck(vehicle);
        }
    }

    /** Client: an arc snapping somewhere over a disabled vehicle. */
    static void crackle(Entity vehicle) {
        CombatDrones.proxy.fx(vehicle.world, Fx.ARC, vehicle.posX + (vehicle.world.rand.nextDouble() - 0.5) * vehicle.width,
                vehicle.posY + vehicle.world.rand.nextDouble() * vehicle.height, vehicle.posZ + (vehicle.world.rand.nextDouble() - 0.5) * vehicle.width,
                0, 0, 0, 0.3F + Math.max(vehicle.width, vehicle.height) * 0.2F);
    }

    /** Something flying: a ramp unit or missile, or a hand-flown drone off the ground. */
    static boolean isVehicle(Entity entity) {
        return entity instanceof EntityRampUnit || (entity instanceof EntityHandFlownDrone && !entity.onGround);
    }

    @Nullable
    static UUID ownerOf(Entity entity) {
        return entity instanceof EntityRampUnit ? ((EntityRampUnit) entity).getOwner() : ((EntityHandFlownDrone) entity).getOwner();
    }

    static boolean spared(@Nullable UUID fieldOwner, @Nullable UUID vehicleOwner, String spares) {
        if (fieldOwner == null || vehicleOwner == null || "none".equals(spares)) {
            return false;
        }
        return "team".equals(spares) ? Teams.isTeammate(fieldOwner, vehicleOwner) : fieldOwner.equals(vehicleOwner);
    }
}
