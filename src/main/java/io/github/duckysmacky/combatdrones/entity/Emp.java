package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.Fx;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.physics.FieldContact;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * EMP fields (the station's, the blast gadget's and the EMP Gun's): a launch ramp or runway the field touches can't
 * launch until it wears off (whatever onContact says). Every flying drone, ramp unit or missile whose hitbox touches
 * the sphere is either destroyed with a small blast (no warhead, bombs or fuel going off) or disabled for a while
 * (motors off, gliding down out of control, see {@code [state.emped]}), per the field's {@link Rules}. A kind that
 * {@code [state.emped]} leaves out is destroyed instead. A hand-flown drone sitting on the ground isn't flying and is
 * left alone. A Stinger missile is hit when it touches the field or its shooter does: destroyed, its warhead goes off
 * where it is; disabled, it stops homing and falls for good.
 */
public final class Emp {
    private Emp() {
    }

    /** One field's settings, from its config section. */
    public static final class Rules {
        final String spares;
        final boolean disable;
        final int disableTicks;
        final float destroyBlastPower;

        private Rules(String spares, String onContact, double disableSeconds, double destroyBlastPower) {
            this.spares = spares;
            this.disable = "disable".equals(onContact);
            this.disableTicks = (int) Math.round(disableSeconds * 20);
            this.destroyBlastPower = (float) destroyBlastPower;
        }

        public static Rules station() {
            return new Rules(ModConfig.stationSpares, ModConfig.stationOnContact, ModConfig.stationDisableSeconds, ModConfig.stationDestroyBlastPower);
        }

        public static Rules blast() {
            return new Rules(ModConfig.blastSpares, ModConfig.blastOnContact, ModConfig.blastDisableSeconds, ModConfig.blastDestroyBlastPower);
        }

        public static Rules gun() {
            return new Rules(ModConfig.gunSpares, ModConfig.gunOnContact, ModConfig.gunDisableSeconds, ModConfig.gunDestroyBlastPower);
        }
    }

    /** Server, every tick a field is up. */
    public static void fry(World world, Vec3d centre, double radius, @Nullable UUID fieldOwner, Rules rules) {
        AxisAlignedBB reach = new AxisAlignedBB(centre.x, centre.y, centre.z, centre.x, centre.y, centre.z).grow(radius + 4);
        List<UUID> shooters = new ArrayList<>();
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, reach, entity -> entity != null && !entity.isDead
                && (isVehicle(entity) || entity instanceof EntityLaunchRamp || isStingerMissile(entity) || entity instanceof EntityPlayer))) {
            AxisAlignedBB box = entity.getEntityBoundingBox();
            if (!FieldContact.touches(centre.x, centre.y, centre.z, radius, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
                    || spared(fieldOwner, ownerOf(entity), rules.spares)) {
                continue;
            }
            if (entity instanceof EntityPlayer) {
                shooters.add(entity.getUniqueID());
                continue;
            }
            if (entity instanceof EntityRocket) {
                zapMissile((EntityRocket) entity, rules);
                continue;
            }
            if (entity instanceof EntityLaunchRamp) {
                EntityLaunchRamp launcher = (EntityLaunchRamp) entity;
                if (!launcher.isEmpDisabled()) {
                    // Sized to the hitbox, not the width: a runway is far longer than it is wide
                    EffectPacket.empZap(world, new Vec3d(entity.posX, entity.posY + entity.height / 2, entity.posZ),
                            (float) Math.max(box.maxX - box.minX, Math.max(box.maxZ - box.minZ, entity.height)));
                }
                launcher.empDisable(rules.disableTicks);
                continue;
            }
            boolean ramp = entity instanceof EntityRampUnit;
            boolean fresh = !(ramp ? ((EntityRampUnit) entity).isEmpDisabled() : ((EntityHandFlownDrone) entity).isEmpDisabled());
            DroneType type = ramp ? ((EntityRampUnit) entity).getDroneType() : ((EntityHandFlownDrone) entity).getDroneType();
            boolean disable = rules.disable && type != null && ModConfig.isEmpedEnabled(type.getKind());
            if (!disable || fresh) {
                EffectPacket.empZap(world, new Vec3d(entity.posX, entity.posY + entity.height / 2, entity.posZ), Math.max(entity.width, entity.height));
            }
            if (entity instanceof EntityRampUnit) {
                EntityRampUnit unit = (EntityRampUnit) entity;
                if (disable) {
                    unit.empDisable(rules.disableTicks);
                } else {
                    unit.empDestroy(rules.destroyBlastPower);
                }
            } else {
                EntityHandFlownDrone drone = (EntityHandFlownDrone) entity;
                if (disable) {
                    drone.empDisable(rules.disableTicks);
                } else {
                    drone.empDestroy(rules.destroyBlastPower);
                }
            }
        }
        // A shooter in the field: their Stinger missiles in flight go with them, wherever they are
        if (!shooters.isEmpty()) {
            for (EntityRocket missile : world.getEntities(EntityRocket.class, entity -> isStingerMissile(entity) && !entity.isDead
                    && shooters.contains(((EntityRocket) entity).getOwner()))) {
                zapMissile(missile, rules);
            }
        }
    }

    /** A Stinger missile the field (or its shooter's) reached: set off where it is, or knocked out for good. */
    private static void zapMissile(EntityRocket missile, Rules rules) {
        if (!rules.disable || !missile.isEmpDisabled()) {
            EffectPacket.empZap(missile.world, missile.getPositionVector(), 1.0F);
        }
        if (rules.disable) {
            missile.empDisable();
        } else {
            missile.empDestroy();
        }
    }

    private static boolean isStingerMissile(@Nullable Entity entity) {
        return entity instanceof EntityRocket && ((EntityRocket) entity).getKind() == EntityRocket.Kind.STINGER;
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
        // Spread over the hitbox, so a long runway sparks along its whole length
        AxisAlignedBB box = vehicle.getEntityBoundingBox();
        double spanX = box.maxX - box.minX;
        double spanZ = box.maxZ - box.minZ;
        CombatDrones.proxy.fx(vehicle.world, Fx.ARC, vehicle.posX + (vehicle.world.rand.nextDouble() - 0.5) * spanX,
                vehicle.posY + vehicle.world.rand.nextDouble() * vehicle.height, vehicle.posZ + (vehicle.world.rand.nextDouble() - 0.5) * spanZ,
                0, 0, 0, 0.3F + (float) Math.min(Math.max(vehicle.width, vehicle.height), 4) * 0.2F);
    }

    /** Something flying: a ramp unit or missile, or a hand-flown drone off the ground. */
    static boolean isVehicle(Entity entity) {
        return entity instanceof EntityRampUnit || (entity instanceof EntityHandFlownDrone && !entity.onGround);
    }

    @Nullable
    static UUID ownerOf(Entity entity) {
        if (entity instanceof EntityLaunchRamp) {
            return ((EntityLaunchRamp) entity).getOwner();
        }
        if (entity instanceof EntityRocket) {
            return ((EntityRocket) entity).getOwner();
        }
        if (entity instanceof EntityPlayer) {
            return entity.getUniqueID();
        }
        return entity instanceof EntityRampUnit ? ((EntityRampUnit) entity).getOwner() : ((EntityHandFlownDrone) entity).getOwner();
    }

    static boolean spared(@Nullable UUID fieldOwner, @Nullable UUID vehicleOwner, String spares) {
        if (fieldOwner == null || vehicleOwner == null || "none".equals(spares)) {
            return false;
        }
        return "team".equals(spares) ? Teams.isTeammate(fieldOwner, vehicleOwner) : fieldOwner.equals(vehicleOwner);
    }
}
