package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import io.github.duckysmacky.combatdrones.item.ItemMissileTablet;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.entity.Entity;
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
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Missile Launch Pad: a flat steel stand holding one Cruise Missile upright. Owned by whoever placed it; only they
 * can load, fuel, link a Missile Control Tablet to it or take the missile back. The missile only becomes an
 * {@link EntityCruiseMissile} when the tablet launches it. Its entity UUID is what tablets link to and what the
 * {@link MissileStore} keys the missile in the air by. Left-click knocks it down, dropping it and its missile.
 */
public class EntityMissilePad extends Entity {
    /** Height of the pad's deck, where the missile stands (blocks above the entity origin). */
    public static final double DECK_HEIGHT = 0.375;

    private static final DataParameter<String> MISSILE_TYPE = EntityDataManager.createKey(EntityMissilePad.class, DataSerializers.STRING);
    private static final DataParameter<Float> FUEL_FRACTION = EntityDataManager.createKey(EntityMissilePad.class, DataSerializers.FLOAT);

    @Nullable
    private UUID owner;
    private double fuel;

    public EntityMissilePad(World world) {
        super(world);
        setSize(1.8F, 0.4F);
    }

    public EntityMissilePad(World world, double x, double y, double z, float yaw, UUID owner) {
        this(world);
        setPositionAndRotation(x, y, z, yaw, 0);
        this.owner = owner;
    }

    @Override
    protected void entityInit() {
        dataManager.register(MISSILE_TYPE, "");
        dataManager.register(FUEL_FRACTION, 0.0F);
    }

    @Nullable
    public DroneType getMissileType() {
        String id = dataManager.get(MISSILE_TYPE);
        return id.isEmpty() ? null : DroneTypes.get(id);
    }

    public boolean isLoaded() {
        return getMissileType() != null;
    }

    public boolean isOwnedBy(EntityPlayer player) {
        return player.getUniqueID().equals(owner);
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public double getFuel() {
        return fuel;
    }

    public float getFuelFraction() {
        return dataManager.get(FUEL_FRACTION);
    }

    /** Nothing solid above the deck, all the way up: the missile needs a clear column to climb. */
    public boolean hasOpenSky() {
        return world.canSeeSky(new BlockPos(posX, posY + DECK_HEIGHT, posZ).up());
    }

    private void setFuel(double value) {
        fuel = value;
        DroneType type = getMissileType();
        dataManager.set(FUEL_FRACTION, type == null ? 0 : (float) Math.min(1, value / type.getBatteryOrFuelCapacity()));
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        Item item = stack.getItem();
        if (world.isRemote) {
            return item instanceof ItemDrone || item == ModContent.fuelCan || item == ModContent.missileTablet || (stack.isEmpty() && player.isSneaking());
        }
        boolean handled = item instanceof ItemDrone || item == ModContent.fuelCan || item == ModContent.missileTablet
                || (stack.isEmpty() && player.isSneaking() && isLoaded());
        if (!handled) {
            return false;
        }
        if (!isOwnedBy(player)) {
            status(player, "message.combat_drones.pad.not_owner");
        } else if (item instanceof ItemDrone) {
            load(player, stack);
        } else if (item == ModContent.fuelCan) {
            refuel(player, stack);
        } else if (item == ModContent.missileTablet) {
            ItemMissileTablet.link(stack, this);
            world.playSound(null, posX, posY, posZ, ModSounds.DRONE_LINK, SoundCategory.PLAYERS, 1.0F, 0.8F);
            status(player, "message.combat_drones.pad.linked");
        } else {
            ItemHandlerHelper.giveItemToPlayer(player, takeMissile());
        }
        return true;
    }

    private void load(EntityPlayer player, ItemStack stack) {
        DroneType type = ((ItemDrone) stack.getItem()).getDroneType();
        if (type == null || type.getCategory() != DroneType.Category.VERTICAL_STRIKE) {
            status(player, "message.combat_drones.pad.not_missile");
            return;
        }
        if (isLoaded()) {
            status(player, "message.combat_drones.pad.occupied");
            return;
        }
        dataManager.set(MISSILE_TYPE, type.getId());
        setFuel(Math.min(ItemDrone.getFuel(stack), type.getBatteryOrFuelCapacity()));
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        world.playSound(null, posX, posY, posZ, ModSounds.DRONE_BATTERY, SoundCategory.BLOCKS, 1.0F, 0.6F);
        status(player, "message.combat_drones.pad.loaded");
    }

    /** One Fuel Can fills the tank completely. */
    private void refuel(EntityPlayer player, ItemStack stack) {
        DroneType type = getMissileType();
        if (type == null) {
            status(player, "message.combat_drones.pad.empty");
            return;
        }
        if (getFuelFraction() >= 1) {
            status(player, "message.combat_drones.ramp.full");
            return;
        }
        setFuel(type.getBatteryOrFuelCapacity());
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        world.playSound(null, posX, posY, posZ, ModSounds.DRONE_ATTACH, SoundCategory.BLOCKS, 1.0F, 0.6F);
        status(player, "message.combat_drones.ramp.refuelled");
    }

    /** Takes the missile off as its item (fuel kept on the item), emptying the pad. */
    public ItemStack takeMissile() {
        DroneType type = getMissileType();
        if (type == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(CombatDrones.MOD_ID, type.getId()));
        ItemStack stack = item == null ? ItemStack.EMPTY : ItemDrone.withFuel(new ItemStack(item), fuel);
        clearMissile();
        return stack;
    }

    /** Server: lights the missile up on the deck. The caller has checked owner, fuel, sky and the missile store. */
    public EntityCruiseMissile launch() {
        DroneType type = getMissileType();
        EntityCruiseMissile missile = new EntityCruiseMissile(world, type, owner, getUniqueID(), fuel, posX, posY + DECK_HEIGHT, posZ, rotationYaw);
        world.spawnEntity(missile);
        clearMissile();
        return missile;
    }

    private void clearMissile() {
        dataManager.set(MISSILE_TYPE, "");
        setFuel(0);
    }

    /** Knocked down: drops itself and whatever missile it held. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (isEntityInvulnerable(source) || isDead) {
            return false;
        }
        if (!world.isRemote) {
            ItemStack missile = takeMissile();
            if (!missile.isEmpty()) {
                entityDropItem(missile, 0.5F);
            }
            entityDropItem(new ItemStack(ModContent.missilePad), 0.5F);
            setDead();
        }
        return true;
    }

    private static void status(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
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
    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double distanceSq) {
        return distanceSq < 160 * 160;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setString("Missile", dataManager.get(MISSILE_TYPE));
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setDouble("Fuel", fuel);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        dataManager.set(MISSILE_TYPE, compound.getString("Missile"));
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        setFuel(compound.getDouble("Fuel"));
    }
}
