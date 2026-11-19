package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.Fx;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One EMP Blast Gadget use: during the build-up it rides along with its user, crackling; then it stays where they
 * stood and its field grows from a block across to the full radius over the expand time, knocking out vehicles it
 * reaches ({@link Emp}), and fades. An EMP Gun orb's burst ({@link #burst}) is the same without the build-up. The timings and radius ride in entity data, so clients draw the same field from
 * the entity's age. Never saved: a blast doesn't survive a restart.
 */
public class EntityEmpBlast extends Entity {
    /** Ticks the field takes to fade out after reaching its full size. */
    public static final int FADE_TICKS = 10;
    private static final double START_RADIUS = 0.5;

    private static final DataParameter<Integer> BUILD_TICKS = EntityDataManager.createKey(EntityEmpBlast.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> EXPAND_TICKS = EntityDataManager.createKey(EntityEmpBlast.class, DataSerializers.VARINT);
    private static final DataParameter<Float> RADIUS = EntityDataManager.createKey(EntityEmpBlast.class, DataSerializers.FLOAT);
    /** The user's entity id, so their own first-person view can leave out the charge glow round them. */
    private static final DataParameter<Integer> USER = EntityDataManager.createKey(EntityEmpBlast.class, DataSerializers.VARINT);

    @Nullable
    private UUID owner;
    /** An EMP Gun orb's burst: the gun's field rules, not the gadget's. */
    private boolean fromGun;
    private int age;

    public EntityEmpBlast(World world) {
        super(world);
        setSize(0.2F, 0.2F);
        noClip = true;
    }

    public EntityEmpBlast(World world, EntityPlayer user) {
        this(world);
        owner = user.getUniqueID();
        setPosition(user.posX, user.posY + user.height / 2, user.posZ);
        dataManager.set(BUILD_TICKS, ModConfig.blastBuildUp ? (int) Math.round(ModConfig.blastBuildUpSeconds * 20) : 0);
        dataManager.set(EXPAND_TICKS, Math.max(1, (int) Math.round(ModConfig.blastExpandSeconds * 20)));
        dataManager.set(RADIUS, (float) ModConfig.blastRadius);
        dataManager.set(USER, user.getEntityId());
    }

    /** An EMP Gun orb bursting at {@code at}: no build-up, grows to the gun's radius over its expand time. */
    public static EntityEmpBlast burst(World world, Vec3d at, @Nullable UUID owner) {
        EntityEmpBlast blast = new EntityEmpBlast(world);
        blast.owner = owner;
        blast.fromGun = true;
        blast.setPosition(at.x, at.y, at.z);
        blast.dataManager.set(BUILD_TICKS, 0);
        blast.dataManager.set(EXPAND_TICKS, Math.max(1, (int) Math.round(ModConfig.gunBurstExpandSeconds * 20)));
        blast.dataManager.set(RADIUS, (float) ModConfig.gunRadius);
        return blast;
    }

    @Override
    protected void entityInit() {
        dataManager.register(BUILD_TICKS, 0);
        dataManager.register(EXPAND_TICKS, 60);
        dataManager.register(RADIUS, 6.0F);
        dataManager.register(USER, -1);
    }

    public int getBuildTicks() {
        return dataManager.get(BUILD_TICKS);
    }

    public int getExpandTicks() {
        return dataManager.get(EXPAND_TICKS);
    }

    public float getMaxRadius() {
        return dataManager.get(RADIUS);
    }

    public int getUserId() {
        return dataManager.get(USER);
    }

    /** Field radius {@code ticks} after the build-up ended: fast at first, easing into the full size. */
    public static double radiusAt(double ticks, int expandTicks, double maxRadius) {
        double t = Math.max(0, Math.min(1, ticks / expandTicks));
        return START_RADIUS + (maxRadius - START_RADIUS) * (1 - (1 - t) * (1 - t));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        int build = getBuildTicks();
        int expand = getExpandTicks();
        if (world.isRemote) {
            if (ticksExisted < build) {
                crackle(ticksExisted / (float) build);
            }
            return;
        }
        if (age < build) {
            EntityPlayer user = owner == null ? null : world.getPlayerEntityByUUID(owner);
            if (user != null && user.isEntityAlive()) {
                setPosition(user.posX, user.posY + user.height / 2, user.posZ);
            }
        } else if (age == build) {
            EffectPacket.empBlast(world, getPositionVector(), getMaxRadius());
        }
        int since = age - build;
        if (since >= 0 && since <= expand) {
            Emp.fry(world, getPositionVector(), radiusAt(since, expand, getMaxRadius()), owner, fromGun ? Emp.Rules.gun() : Emp.Rules.blast());
        }
        if (since > expand + FADE_TICKS) {
            setDead();
        }
        age++;
    }

    /** Client, during the build-up: arcs snapping round the user, faster as it charges. */
    private void crackle(float progress) {
        if (rand.nextFloat() < 0.3F + progress * 0.6F) {
            Vec3d out = new Vec3d(rand.nextGaussian(), rand.nextGaussian() * 0.8, rand.nextGaussian()).normalize().scale(0.5 + rand.nextDouble() * 0.4);
            CombatDrones.proxy.fx(world, Fx.ARC, posX + out.x, posY + out.y, posZ + out.z, 0, 0, 0, 0.3F + progress * 0.3F);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return getEntityBoundingBox().grow(getMaxRadius() + 1);
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
