package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Rocket from a gunship's pod: flies straight (no drop) and explodes on the first block or entity in its path,
 * credited to the gunship's owner. Checked with a raytrace along each tick's movement, so it can't tunnel through
 * thin targets at speed.
 */
public class EntityRocket extends Entity {
    private static final int LIFETIME_TICKS = 100;

    /** The gunship that fired it (never hit by its own rocket); only known until a reload. */
    @Nullable
    private Entity shooter;
    @Nullable
    private UUID owner;
    private int age;

    public EntityRocket(World world) {
        super(world);
        setSize(0.25F, 0.25F);
    }

    public EntityRocket(World world, Entity shooter, @Nullable UUID owner, Vec3d position, Vec3d velocity) {
        this(world);
        this.shooter = shooter;
        this.owner = owner;
        setPosition(position.x, position.y, position.z);
        motionX = velocity.x;
        motionY = velocity.y;
        motionZ = velocity.z;
        float horizontal = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        rotationYaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        rotationPitch = (float) -Math.toDegrees(Math.atan2(velocity.y, horizontal));
    }

    @Override
    protected void entityInit() {
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        Vec3d from = new Vec3d(posX, posY, posZ);
        Vec3d to = from.add(motionX, motionY, motionZ);
        if (world.isRemote) {
            world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, posX, posY, posZ, 0, 0, 0);
            setPosition(to.x, to.y, to.z);
            return;
        }
        RayTraceResult hit = Raytrace.firstResult(world, from, to, shooter);
        if (hit != null) {
            explode(hit.hitVec);
            return;
        }
        setPosition(to.x, to.y, to.z);
        if (++age >= LIFETIME_TICKS) {
            explode(to);
        }
    }

    private void explode(Vec3d at) {
        setDead();
        EntityLivingBase blame = owner != null ? world.getPlayerEntityByUUID(owner) : null;
        DroneExplosion.detonate((WorldServer) world, this, blame, at.x, at.y, at.z, (float) ModConfig.rocketBlastPower);
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setInteger("Age", age);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        age = compound.getInteger("Age");
    }
}
