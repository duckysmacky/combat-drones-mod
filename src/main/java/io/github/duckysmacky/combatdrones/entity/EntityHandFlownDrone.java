package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemRemote;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import javax.vecmath.Quat4d;
import java.util.List;
import java.util.UUID;

/**
 * Shared entity for every HAND_FLOWN drone type (racers, cargo, gunships); behaviour differences come from
 * its {@link DroneType}. The server owns the flight state; the pilot's client predicts it (see client code).
 */
public class EntityHandFlownDrone extends Entity {
    /** Impact speed (blocks/tick) below which a collision is a landing, not a crash. */
    static final double ARM_SPEED = 0.25;
    /** Crash damage per block/tick of impact speed for drones that don't detonate. */
    private static final float CRASH_DAMAGE_PER_SPEED = 8.0F;

    private static final DataParameter<String> TYPE_ID = EntityDataManager.createKey(EntityHandFlownDrone.class, DataSerializers.STRING);
    private static final DataParameter<Float> ROLL = EntityDataManager.createKey(EntityHandFlownDrone.class, DataSerializers.FLOAT);

    protected final HandFlownFlight.State flightState = new HandFlownFlight.State();
    @Nullable
    private UUID owner;
    private float health;
    private int grenades;

    public float prevRoll;

    public EntityHandFlownDrone(World world) {
        super(world);
        setSize(0.6F, 0.3F);
    }

    public EntityHandFlownDrone(World world, DroneType type, double x, double y, double z, float yaw) {
        this(world);
        dataManager.set(TYPE_ID, type.getId());
        health = type.getMaxHP();
        setPosition(x, y, z);
        flightState.position.set(x, y, z);
        flightState.orientation.set(HandFlownFlight.fromEuler(yaw, 0, 0));
        rotationYaw = yaw;
    }

    @Override
    protected void entityInit() {
        dataManager.register(TYPE_ID, "");
        dataManager.register(ROLL, 0.0F);
    }

    @Nullable
    public DroneType getDroneType() {
        return DroneTypes.get(dataManager.get(TYPE_ID));
    }

    public float getRoll() {
        return dataManager.get(ROLL);
    }

    public int getGrenades() {
        return grenades;
    }

    @Override
    public void onUpdate() {
        prevRoll = getRoll();
        super.onUpdate();
        if (world.isRemote) {
            return;
        }
        DroneType type = getDroneType();
        if (type == null) {
            CombatDrones.logger.warn("Removing drone with unknown type '{}'", dataManager.get(TYPE_ID));
            setDead();
            return;
        }
        HandFlownFlight.step(flightState, currentInput(), paramsOf(type));
        applyStepToEntity(type);
    }

    /** What the drone flies with this tick. Unpiloted drones just fall. */
    protected HandFlownFlight.Input currentInput() {
        return HandFlownFlight.Input.IDLE;
    }

    static HandFlownFlight.Params paramsOf(DroneType type) {
        return new HandFlownFlight.Params(type.getMaxSpeed(), type.getAcceleration());
    }

    /**
     * Moves the entity to where physics wants it, letting vanilla block collision clip the move, then feeds the
     * clipped result back into the flight state. Returns the speed lost to collisions this tick.
     */
    protected double moveWithCollision() {
        double wantedX = flightState.position.x - posX;
        double wantedY = flightState.position.y - posY;
        double wantedZ = flightState.position.z - posZ;
        double startX = posX;
        double startY = posY;
        double startZ = posZ;
        move(MoverType.SELF, wantedX, wantedY, wantedZ);
        double movedX = posX - startX;
        double movedY = posY - startY;
        double movedZ = posZ - startZ;
        flightState.position.set(posX, posY, posZ);
        flightState.velocity.set(movedX, movedY, movedZ);
        double blockedX = wantedX - movedX;
        double blockedY = wantedY - movedY;
        double blockedZ = wantedZ - movedZ;
        return Math.sqrt(blockedX * blockedX + blockedY * blockedY + blockedZ * blockedZ);
    }

    /** Server-side: collide, then copy orientation into the vanilla-synced rotation fields. */
    private void applyStepToEntity(DroneType type) {
        double speedBefore = flightState.velocity.length();
        double impactSpeed = moveWithCollision();
        if (impactSpeed < ARM_SPEED && speedBefore >= ARM_SPEED && hitsEntity()) {
            impactSpeed = speedBefore;
        }
        syncRotation();
        if (impactSpeed >= ARM_SPEED) {
            onImpact(type, impactSpeed);
        }
    }

    protected void syncRotation() {
        double[] euler = HandFlownFlight.toEuler(flightState.orientation);
        rotationYaw = (float) euler[0];
        rotationPitch = (float) euler[1];
        dataManager.set(ROLL, (float) euler[2]);
    }

    private boolean hitsEntity() {
        AxisAlignedBB box = getEntityBoundingBox();
        List<Entity> touching = world.getEntitiesWithinAABBExcludingEntity(this, box);
        for (Entity entity : touching) {
            if (entity.canBeCollidedWith() && entity.isEntityAlive()) {
                return true;
            }
        }
        return false;
    }

    private void onImpact(DroneType type, double impactSpeed) {
        if (type.isImpactDetonates()) {
            detonate(type);
        } else {
            damage((float) (impactSpeed * CRASH_DAMAGE_PER_SPEED));
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (isEntityInvulnerable(source) || isDead) {
            return false;
        }
        if (!world.isRemote) {
            markVelocityChanged();
            damage(amount);
        }
        return true;
    }

    private void damage(float amount) {
        health -= amount;
        if (health > 0) {
            return;
        }
        DroneType type = getDroneType();
        if (type != null && type.isImpactDetonates() && !onGround) {
            detonate(type);
        } else {
            breakApart();
        }
    }

    protected void detonate(DroneType type) {
        float power = type.getBlastPower() + grenades * (float) ModConfig.grenadeBlastBonus;
        setDead();
        if (power > 0) {
            DroneExplosion.detonate((WorldServer) world, this, getBlameTarget(), posX, posY + height / 2, posZ, power);
        }
    }

    /** Who gets kill credit for this drone's explosion. */
    @Nullable
    protected EntityLivingBase getBlameTarget() {
        return null;
    }

    /** Destroyed without exploding: drop the drone and anything loaded into it. */
    protected void breakApart() {
        setDead();
        Item droneItem = getDroneItem();
        if (droneItem != null) {
            entityDropItem(new ItemStack(droneItem), 0);
        }
        if (grenades > 0) {
            entityDropItem(new ItemStack(ModContent.fragGrenade, grenades), 0);
        }
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public boolean isOwnedBy(EntityPlayer player) {
        return player.getUniqueID().equals(owner);
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return stack.getItem() == ModContent.fpvRemote || (stack.isEmpty() && player.isSneaking());
        }
        if (owner != null && !isOwnedBy(player)) {
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.owned_by_other"), true);
            return true;
        }
        if (stack.getItem() == ModContent.fpvRemote) {
            owner = player.getUniqueID();
            String nameKey = getDroneItemTranslationKey();
            ItemRemote.link(stack, getUniqueID(), nameKey);
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.linked", new TextComponentTranslation(nameKey)), true);
            return true;
        }
        if (stack.isEmpty() && player.isSneaking()) {
            pickUp(player);
            return true;
        }
        return false;
    }

    private String getDroneItemTranslationKey() {
        Item droneItem = getDroneItem();
        return droneItem != null ? droneItem.getTranslationKey() + ".name" : "entity.combat_drones.hand_flown_drone.name";
    }

    @Nullable
    private Item getDroneItem() {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(CombatDrones.MOD_ID, dataManager.get(TYPE_ID)));
    }

    private void pickUp(EntityPlayer player) {
        setDead();
        Item droneItem = getDroneItem();
        if (droneItem != null) {
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(droneItem));
        }
        if (grenades > 0) {
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ModContent.fragGrenade, grenades));
        }
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isDead;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setString("Type", dataManager.get(TYPE_ID));
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setFloat("Health", health);
        compound.setInteger("Grenades", grenades);
        Quat4d orientation = flightState.orientation;
        compound.setDouble("QX", orientation.x);
        compound.setDouble("QY", orientation.y);
        compound.setDouble("QZ", orientation.z);
        compound.setDouble("QW", orientation.w);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        dataManager.set(TYPE_ID, compound.getString("Type"));
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        health = compound.getFloat("Health");
        grenades = compound.getInteger("Grenades");
        flightState.orientation.set(compound.getDouble("QX"), compound.getDouble("QY"), compound.getDouble("QZ"), compound.getDouble("QW"));
        if (flightState.orientation.w == 0 && flightState.orientation.x == 0 && flightState.orientation.y == 0 && flightState.orientation.z == 0) {
            flightState.orientation.set(0, 0, 0, 1);
        }
        flightState.position.set(posX, posY, posZ);
    }
}
