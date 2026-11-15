package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.Fx;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemEmpStation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * EMP Field Station: a placed coil that, switched on, keeps a spherical EMP field up around itself, knocking out every
 * drone, unit or missile that flies into it ({@link Emp}). It runs off a charge of {@link #MAX_CHARGE}: draining while
 * on (switching itself off when empty), refilling while off. Anyone can switch it (right-click) or pick it up (sneak +
 * right-click, the charge stays on the item); a hit knocks it down, a blast doesn't. The placer owns it, for
 * {@code [emp_station] spares}.
 */
public class EntityEmpStation extends Entity {
    public static final float MAX_CHARGE = 100;
    /** Least charge it can be switched on with, so an empty station doesn't flicker on and off. */
    private static final float MIN_START_CHARGE = 5;

    private static final DataParameter<Boolean> ACTIVE = EntityDataManager.createKey(EntityEmpStation.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Float> CHARGE = EntityDataManager.createKey(EntityEmpStation.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> RADIUS = EntityDataManager.createKey(EntityEmpStation.class, DataSerializers.FLOAT);

    @Nullable
    private UUID owner;
    /** Client: {@code ticksExisted} when it was last switched, for the settle animation. */
    public int handledAt = -100;

    public EntityEmpStation(World world) {
        super(world);
        setSize(0.9F, 1.4F);
    }

    public EntityEmpStation(World world, double x, double y, double z, float yaw, UUID owner, float charge) {
        this(world);
        setPositionAndRotation(x, y, z, yaw, 0);
        this.owner = owner;
        dataManager.set(CHARGE, charge);
    }

    @Override
    protected void entityInit() {
        dataManager.register(ACTIVE, false);
        dataManager.register(CHARGE, MAX_CHARGE);
        dataManager.register(RADIUS, (float) ModConfig.stationRadius);
    }

    public boolean isActive() {
        return dataManager.get(ACTIVE);
    }

    public float getCharge() {
        return dataManager.get(CHARGE);
    }

    public float getRadius() {
        return dataManager.get(RADIUS);
    }

    /** Middle of the field: the coil's middle. */
    public Vec3d getFieldCentre() {
        return new Vec3d(posX, posY + height / 2, posZ);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) {
            if (isActive()) {
                CombatDrones.proxy.empHum(this);
                crackle();
            }
            return;
        }
        dataManager.set(RADIUS, (float) ModConfig.stationRadius);
        float charge = getCharge();
        if (isActive()) {
            charge = Math.max(0, charge - (float) ModConfig.stationDischargePerSecond / 20);
            if (charge <= 0) {
                switchTo(false);
            } else {
                Emp.fry(world, getFieldCentre(), getRadius(), owner, Emp.Rules.station());
            }
        } else {
            charge = Math.min(MAX_CHARGE, charge + (float) ModConfig.stationRechargePerSecond / 20);
        }
        dataManager.set(CHARGE, charge);
    }

    /** Client: arcs flickering over the field's surface and round the coil. */
    private void crackle() {
        Vec3d centre = getFieldCentre();
        double radius = getRadius();
        for (int index = 0; index < 1 + (int) (radius / 4); index++) {
            if (rand.nextFloat() < 0.35F) {
                Vec3d out = new Vec3d(rand.nextGaussian(), rand.nextGaussian(), rand.nextGaussian()).normalize().scale(radius);
                CombatDrones.proxy.fx(world, Fx.ARC, centre.x + out.x, centre.y + out.y, centre.z + out.z, 0, 0, 0, 0.8F + rand.nextFloat() * 0.6F);
            }
        }
        if (rand.nextFloat() < 0.25F) {
            CombatDrones.proxy.fx(world, Fx.ARC, posX + (rand.nextDouble() - 0.5) * 0.5, posY + 1.35, posZ + (rand.nextDouble() - 0.5) * 0.5,
                    0, 0, 0, 0.35F);
        }
    }

    private void switchTo(boolean on) {
        dataManager.set(ACTIVE, on);
        Feedback.done(this, on ? ModSounds.EMP_STATION_ON : ModSounds.EMP_STATION_OFF, 1.0F);
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (world.isRemote) {
            player.swingArm(hand);
            return true;
        }
        if (player.isSneaking()) {
            ItemHandlerHelper.giveItemToPlayer(player, asItem());
            world.playSound(null, posX, posY, posZ, ModSounds.UNIT_REMOVE, SoundCategory.PLAYERS, 1.0F, 1.1F);
            setDead();
        } else if (!isActive() && getCharge() < MIN_START_CHARGE) {
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.emp_station.empty"), true);
            Feedback.deny(player);
        } else {
            switchTo(!isActive());
            player.sendStatusMessage(new TextComponentTranslation(isActive() ? "message.combat_drones.emp_station.on" : "message.combat_drones.emp_station.off",
                    Math.round(getCharge())), true);
        }
        return true;
    }

    private ItemStack asItem() {
        return ItemEmpStation.withCharge(new ItemStack(ModContent.empStation), getCharge());
    }

    @Override
    public void handleStatusUpdate(byte id) {
        if (id == Feedback.HANDLED_STATUS) {
            handledAt = ticksExisted;
            Feedback.handled(this);
        } else {
            super.handleStatusUpdate(id);
        }
    }

    /** Knocked down by a hit (dropping itself with its charge); blasts, its own trade, don't move it. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (isEntityInvulnerable(source) || isDead || source.isExplosion()) {
            return false;
        }
        if (!world.isRemote) {
            entityDropItem(asItem(), 0.5F);
            world.playSound(null, posX, posY, posZ, ModSounds.UNIT_REMOVE, SoundCategory.PLAYERS, 1.0F, 0.7F);
            setDead();
        }
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isDead;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    /** The field is drawn with the station, so it mustn't be culled while the coil itself is off screen. */
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return isActive() ? getEntityBoundingBox().grow(getRadius()) : getEntityBoundingBox();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double distanceSq) {
        return distanceSq < 160 * 160;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setBoolean("Active", isActive());
        compound.setFloat("Charge", getCharge());
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        dataManager.set(ACTIVE, compound.getBoolean("Active"));
        dataManager.set(CHARGE, compound.getFloat("Charge"));
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
    }
}
