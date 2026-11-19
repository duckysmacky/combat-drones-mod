package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.Fx;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * An EMP Gun's orb: a condensed core flying dead straight (no drop) until it hits a block or has flown the max range.
 * Entities don't stop it. In the {@code travelling} field mode it carries its EMP field the whole way (growing from the core over the
 * expand time), knocking out
 * vehicles it passes ({@link Emp}), and where it stops the field gives a discharge and fades. In {@code burst} mode
 * only the core flies; touching a flying vehicle stops it too, and where it stops it bursts into an
 * {@link EntityEmpBlast}. Radius and mode are fixed when fired and ride in entity data; never saved.
 */
public class EntityEmpOrb extends Entity {
    private static final DataParameter<Float> RADIUS = EntityDataManager.createKey(EntityEmpOrb.class, DataSerializers.FLOAT);
    /** Ticks the travelling field takes to grow to {@link #RADIUS} (0 = full size from the start). */
    private static final DataParameter<Integer> EXPAND_TICKS = EntityDataManager.createKey(EntityEmpOrb.class, DataSerializers.VARINT);
    private static final DataParameter<Boolean> BURST = EntityDataManager.createKey(EntityEmpOrb.class, DataSerializers.BOOLEAN);
    /** The {@code ticksExisted} it stopped at (travelling: its field fading from then on), or -1 while flying. */
    private static final DataParameter<Integer> STOPPED = EntityDataManager.createKey(EntityEmpOrb.class, DataSerializers.VARINT);

    @Nullable
    private UUID owner;
    private int lifetime;

    public EntityEmpOrb(World world) {
        super(world);
        setSize(0.5F, 0.5F);
        noClip = true;
    }

    public EntityEmpOrb(World world, @Nullable UUID owner, Vec3d position, Vec3d velocity) {
        this(world);
        this.owner = owner;
        setPosition(position.x, position.y, position.z);
        motionX = velocity.x;
        motionY = velocity.y;
        motionZ = velocity.z;
        lifetime = Math.max(1, (int) Math.ceil(ModConfig.gunMaxRange / Math.max(0.01, velocity.length())));
        dataManager.set(RADIUS, (float) ModConfig.gunRadius);
        dataManager.set(EXPAND_TICKS, (int) Math.round(ModConfig.gunTravellingExpandSeconds * 20));
        dataManager.set(BURST, "burst".equals(ModConfig.gunFieldMode));
    }

    @Override
    protected void entityInit() {
        dataManager.register(RADIUS, 12.0F);
        dataManager.register(EXPAND_TICKS, 0);
        dataManager.register(BURST, false);
        dataManager.register(STOPPED, -1);
    }

    public float getRadius() {
        return dataManager.get(RADIUS);
    }

    /** The travelling field's radius {@code age} ticks after the shot: growing from the core, easing into the full size. */
    public double radiusAt(float age) {
        int expand = dataManager.get(EXPAND_TICKS);
        return expand <= 0 ? getRadius() : EntityEmpBlast.radiusAt(age, expand, getRadius());
    }

    public boolean isBurst() {
        return dataManager.get(BURST);
    }

    public int getStopped() {
        return dataManager.get(STOPPED);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        int stopped = getStopped();
        if (stopped >= 0) {
            if (!world.isRemote && ticksExisted - stopped > EntityEmpBlast.FADE_TICKS) {
                setDead();
            }
            return;
        }
        Vec3d from = getPositionVector();
        Vec3d to = from.add(motionX, motionY, motionZ);
        if (world.isRemote) {
            CombatDrones.proxy.empHum(this);
            crackle();
            setPosition(to.x, to.y, to.z);
            return;
        }
        boolean burst = isBurst();
        if (!burst) {
            Emp.fry(world, from, radiusAt(ticksExisted), owner, Emp.Rules.gun());
        }
        RayTraceResult hit = world.rayTraceBlocks(from, to, false, true, false);
        Vec3d end = hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK ? hit.hitVec : null;
        if (burst) {
            Vec3d touched = touchedVehicle(from, end != null ? end : to);
            end = touched != null ? touched : end;
        }
        if (end != null || ticksExisted >= lifetime) {
            stop(end != null ? end : to);
        } else {
            setPosition(to.x, to.y, to.z);
        }
    }

    /** Burst mode: where the core's path first meets a flying vehicle the gun doesn't spare, or null. */
    @Nullable
    private Vec3d touchedVehicle(Vec3d from, Vec3d to) {
        AxisAlignedBB sweep = new AxisAlignedBB(from.x, from.y, from.z, to.x, to.y, to.z).grow(width / 2 + 2);
        Vec3d nearest = null;
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, sweep, entity -> entity != null && !entity.isDead && Emp.isVehicle(entity)
                && !Emp.spared(owner, Emp.ownerOf(entity), ModConfig.gunSpares))) {
            RayTraceResult near = entity.getEntityBoundingBox().grow(width / 2).calculateIntercept(from, to);
            if (near != null && (nearest == null || from.squareDistanceTo(near.hitVec) < from.squareDistanceTo(nearest))) {
                nearest = near.hitVec;
            }
        }
        return nearest;
    }

    private void stop(Vec3d at) {
        setPosition(at.x, at.y, at.z);
        motionX = motionY = motionZ = 0;
        if (isBurst()) {
            world.spawnEntity(EntityEmpBlast.burst(world, at, owner));
            setDead();
        } else {
            EffectPacket.empBlast(world, at, (float) radiusAt(ticksExisted));
            dataManager.set(STOPPED, ticksExisted);
        }
    }

    /** Client, in flight: arcs snapping round the core. */
    private void crackle() {
        if (rand.nextFloat() < 0.6F) {
            Vec3d out = new Vec3d(rand.nextGaussian(), rand.nextGaussian(), rand.nextGaussian()).normalize().scale(0.3 + rand.nextDouble() * 0.5);
            CombatDrones.proxy.fx(world, Fx.ARC, posX + out.x, posY + out.y, posZ + out.z, 0, 0, 0, 0.3F + rand.nextFloat() * 0.2F);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return getEntityBoundingBox().grow(getRadius() + 1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double distanceSq) {
        return distanceSq < 160 * 160;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound compound) {
        return false;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
    }
}
