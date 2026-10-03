package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.type.BombType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Bomb dropped from a DJI drone's bay: free-falls with the drone's momentum and explodes on impact. A fuse makes sure
 * it can never sit live on the ground forever.
 */
public class EntityBomb extends Entity {
    /** {@link BombType} ordinal, synced so clients render the right bomb. */
    private static final DataParameter<Integer> BOMB_TYPE = EntityDataManager.createKey(EntityBomb.class, DataSerializers.VARINT);
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

    public EntityBomb(World world, BombType type, double x, double y, double z, double motionX, double motionY, double motionZ, @Nullable UUID owner) {
        this(world);
        dataManager.set(BOMB_TYPE, type.ordinal());
        setPosition(x, y, z);
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.owner = owner;
    }

    @Override
    protected void entityInit() {
        dataManager.register(BOMB_TYPE, BombType.MEDIUM.ordinal());
    }

    public BombType getBombType() {
        BombType type = BombType.byIndex(dataManager.get(BOMB_TYPE));
        return type != null ? type : BombType.MEDIUM;
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
        DroneExplosion.detonate((WorldServer) world, this, blame, posX, posY, posZ, (float) getBombType().getBlastPower());
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setInteger("Age", age);
        compound.setInteger("BombType", getBombType().ordinal());
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        age = compound.getInteger("Age");
        BombType type = compound.hasKey("BombType") ? BombType.byIndex(compound.getInteger("BombType")) : null;
        dataManager.set(BOMB_TYPE, (type != null ? type : BombType.MEDIUM).ordinal());
    }
}
