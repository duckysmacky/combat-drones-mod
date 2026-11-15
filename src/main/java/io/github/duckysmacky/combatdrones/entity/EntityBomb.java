package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.physics.BombBallistics;
import io.github.duckysmacky.combatdrones.type.AerialBombType;
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
import net.minecraftforge.common.ForgeChunkManager;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Bomb dropped from a DJI drone's or a bomber's bay: free-falls with the aircraft's momentum and explodes on impact.
 * A fuse makes sure it can never sit live on the ground forever.
 */
public class EntityBomb extends Entity implements ChunkAnchor.Holder {
    /** {@link BombType} ordinal, synced so clients render the right bomb. */
    private static final DataParameter<Integer> BOMB_TYPE = EntityDataManager.createKey(EntityBomb.class, DataSerializers.VARINT);
    /** {@link AerialBombType} ordinal for a bomber's bomb, -1 for a drone's. */
    private static final DataParameter<Integer> AERIAL_TYPE = EntityDataManager.createKey(EntityBomb.class, DataSerializers.VARINT);
    /** Ignore entity contact right after release, so the bomb doesn't hit its own drone on the way out. */
    private static final int ARMING_TICKS = 5;

    @Nullable
    private UUID owner;
    /** The drone that released it (only known until a reload). */
    @Nullable
    private Entity dropper;
    private int age;
    /**
     * A bomber's bombs keep their own chunk loaded on the way down: the plane that dropped them flies on and takes
     * its chunk ticket with it, and a route is often bombed with no player anywhere near.
     */
    private final ChunkAnchor anchor = new ChunkAnchor(this);

    public EntityBomb(World world) {
        super(world);
        setSize(0.3F, 0.3F);
    }

    public EntityBomb(World world, BombType type, double x, double y, double z, double motionX, double motionY, double motionZ, @Nullable UUID owner,
                      @Nullable Entity dropper) {
        this(world);
        this.dropper = dropper;
        dataManager.set(BOMB_TYPE, type.ordinal());
        setPosition(x, y, z);
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.owner = owner;
    }

    /** A bomber's bomb. */
    public EntityBomb(World world, AerialBombType type, double x, double y, double z, double motionX, double motionY, double motionZ,
                      @Nullable UUID owner, @Nullable Entity dropper) {
        this(world, BombType.HEAVY, x, y, z, motionX, motionY, motionZ, owner, dropper);
        dataManager.set(AERIAL_TYPE, type.ordinal());
    }

    @Override
    protected void entityInit() {
        dataManager.register(BOMB_TYPE, BombType.MEDIUM.ordinal());
        dataManager.register(AERIAL_TYPE, -1);
    }

    /** The bomber bomb size, or null for a drone's bomb. */
    @Nullable
    public AerialBombType getAerialType() {
        int index = dataManager.get(AERIAL_TYPE);
        return index < 0 ? null : AerialBombType.byIndex(index);
    }

    /** Geo model (and item) name. */
    public String modelName() {
        AerialBombType aerial = getAerialType();
        return aerial != null ? aerial.itemName() : getBombType().itemName();
    }

    public BombType getBombType() {
        BombType type = BombType.byIndex(dataManager.get(BOMB_TYPE));
        return type != null ? type : BombType.MEDIUM;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        // Integrated on both sides so the fall looks smooth between tracker updates
        motionY -= BombBallistics.GRAVITY;
        move(MoverType.SELF, motionX, motionY, motionZ);
        motionX *= BombBallistics.DRAG;
        motionY *= BombBallistics.DRAG;
        motionZ *= BombBallistics.DRAG;
        if (world.isRemote) {
            return;
        }
        age++;
        if (getAerialType() != null) {
            anchor.hold(posX + motionX * 20, posZ + motionZ * 20);
        }
        if (collidedHorizontally || collidedVertically || (age > ARMING_TICKS && hitsEntity()) || age >= BombBallistics.FUSE_TICKS) {
            explode();
        }
    }

    @Override
    public void adoptChunkTicket(ForgeChunkManager.Ticket ticket) {
        anchor.adopt(ticket);
    }

    @Override
    public void setDead() {
        super.setDead();
        if (!world.isRemote) {
            anchor.release();
        }
    }

    private boolean hitsEntity() {
        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(this, getEntityBoundingBox())) {
            // Never the drone that dropped it, nor its pilot riding along under it
            if (entity.canBeCollidedWith() && entity.isEntityAlive() && (dropper == null || !entity.isRidingSameEntity(dropper))) {
                return true;
            }
        }
        return false;
    }

    private void explode() {
        setDead();
        EntityLivingBase blame = owner != null ? world.getPlayerEntityByUUID(owner) : null;
        AerialBombType aerial = getAerialType();
        DroneExplosion.detonate((WorldServer) world, this, blame, posX, posY, posZ,
                (float) (aerial != null ? aerial.getBlastPower() : getBombType().getBlastPower()));
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setInteger("Age", age);
        compound.setInteger("BombType", getBombType().ordinal());
        compound.setInteger("AerialType", dataManager.get(AERIAL_TYPE));
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        age = compound.getInteger("Age");
        BombType type = compound.hasKey("BombType") ? BombType.byIndex(compound.getInteger("BombType")) : null;
        dataManager.set(BOMB_TYPE, (type != null ? type : BombType.MEDIUM).ordinal());
        dataManager.set(AERIAL_TYPE, compound.hasKey("AerialType") ? compound.getInteger("AerialType") : -1);
    }
}
