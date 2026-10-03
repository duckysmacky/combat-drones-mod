package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Bomb dropped from a DJI drone's bay: free-falls with the drone's momentum and explodes on impact. A fuse makes sure
 * it can never sit live on the ground forever.
 */
public class EntityBomb extends Entity {
    private static final double GRAVITY = 0.04;
    private static final double DRAG = 0.98;
    private static final int FUSE_TICKS = 200;
    /** Ignore entity contact right after release, so the bomb doesn't hit its own drone on the way out. */
    private static final int ARMING_TICKS = 5;

    @Nullable
    private UUID owner;
    private int age;

    public EntityBomb(World world) {
        super(world);
        setSize(0.3F, 0.3F);
    }

    public EntityBomb(World world, double x, double y, double z, double motionX, double motionY, double motionZ, @Nullable UUID owner) {
        this(world);
        setPosition(x, y, z);
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.owner = owner;
    }

    @Override
    protected void entityInit() {
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        // Integrated on both sides so the fall looks smooth between tracker updates
        motionY -= GRAVITY;
        move(MoverType.SELF, motionX, motionY, motionZ);
        motionX *= DRAG;
        motionY *= DRAG;
        motionZ *= DRAG;
        if (world.isRemote) {
            return;
        }
        age++;
        if (collidedHorizontally || collidedVertically || (age > ARMING_TICKS && hitsEntity()) || age >= FUSE_TICKS) {
            explode();
        }
    }

    private boolean hitsEntity() {
        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(this, getEntityBoundingBox())) {
            if (entity.canBeCollidedWith() && entity.isEntityAlive()) {
                return true;
            }
        }
        return false;
    }

    private void explode() {
        setDead();
        EntityLivingBase blame = owner != null ? world.getPlayerEntityByUUID(owner) : null;
        DroneExplosion.detonate((WorldServer) world, this, blame, posX, posY, posZ, (float) ModConfig.bombBlastPower);
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
