package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import io.github.duckysmacky.combatdrones.network.PlannerServer;
import io.github.duckysmacky.combatdrones.network.RouteCodec;
import io.github.duckysmacky.combatdrones.physics.Route;
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
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Launch Ramp: a 3-block rail placed facing exactly where its placer looks, so units can be aimed at any heading.
 * Holds at most one ramp-launched unit (type, owner, fuel) plus the route planned for it; the unit only becomes an
 * {@link EntityRampUnit} when it launches. Left-click (attack) knocks the ramp down, dropping it and its unit.
 */
public class EntityLaunchRamp extends Entity {
    /** How far along the rail (blocks, from the ramp's centre) a launching unit appears. */
    private static final double RAIL_END = 1.5;

    private static final DataParameter<String> UNIT_TYPE = EntityDataManager.createKey(EntityLaunchRamp.class, DataSerializers.STRING);
    private static final DataParameter<Float> FUEL_FRACTION = EntityDataManager.createKey(EntityLaunchRamp.class, DataSerializers.FLOAT);

    @Nullable
    private UUID owner;
    private double fuel;
    private int ammo;
    private Route route = Route.empty(Route.TerminalAction.LOITER);

    public EntityLaunchRamp(World world) {
        super(world);
        setSize(1.4F, 0.6F);
    }

    public EntityLaunchRamp(World world, double x, double y, double z, float yaw) {
        this(world);
        setPositionAndRotation(x, y, z, yaw, 0);
    }

    @Override
    protected void entityInit() {
        dataManager.register(UNIT_TYPE, "");
        dataManager.register(FUEL_FRACTION, 0.0F);
    }

    @Override
    public void onUpdate() {
        // Static: no gravity or motion; only vanilla bookkeeping (prev positions etc.)
        super.onUpdate();
    }

    @Nullable
    public DroneType getUnitType() {
        String id = dataManager.get(UNIT_TYPE);
        return id.isEmpty() ? null : DroneTypes.get(id);
    }

    public boolean isLoaded() {
        return getUnitType() != null;
    }

    public boolean isOwnedBy(EntityPlayer player) {
        return player.getUniqueID().equals(owner);
    }

    public float getFuelFraction() {
        return dataManager.get(FUEL_FRACTION);
    }

    public int getAmmo() {
        return ammo;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    private void setFuel(double value) {
        fuel = value;
        DroneType type = getUnitType();
        dataManager.set(FUEL_FRACTION, type == null ? 0 : (float) Math.min(1, value / type.getBatteryOrFuelCapacity()));
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return stack.getItem() instanceof ItemDrone || stack.getItem() == ModContent.fuelCan || ammoPerItem(stack) > 0
                    || stack.getItem() == ModContent.missionPlanner || (stack.isEmpty() && player.isSneaking());
        }
        if (stack.getItem() instanceof ItemDrone) {
            install(player, stack);
        } else if (stack.getItem() == ModContent.fuelCan) {
            refuel(player, stack);
        } else if (ammoPerItem(stack) > 0) {
            loadAmmo(player, stack);
        } else if (stack.getItem() == ModContent.missionPlanner) {
            PlannerServer.openRamp(player, this);
        } else if (stack.isEmpty() && player.isSneaking() && isLoaded()) {
            if (!isOwnedBy(player)) {
                status(player, "message.combat_drones.ramp.not_owner");
            } else {
                ItemHandlerHelper.giveItemToPlayer(player, takeUnit());
            }
        } else {
            return false;
        }
        return true;
    }

    private void install(EntityPlayer player, ItemStack stack) {
        DroneType type = ((ItemDrone) stack.getItem()).getDroneType();
        if (type == null || type.getCategory() != DroneType.Category.RAMP_AUTOPILOT) {
            status(player, "message.combat_drones.ramp.not_ramp_unit");
            return;
        }
        if (isLoaded()) {
            status(player, "message.combat_drones.ramp.occupied");
            return;
        }
        dataManager.set(UNIT_TYPE, type.getId());
        owner = player.getUniqueID();
        setFuel(Math.min(ItemDrone.getFuel(stack), type.getBatteryOrFuelCapacity()));
        ammo = Math.min(ItemDrone.getAmmo(stack), type.getPayloadCapacity());
        route = Route.empty(Route.TerminalAction.LOITER);
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        world.playSound(null, posX, posY, posZ, ModSounds.DRONE_BATTERY, SoundCategory.BLOCKS, 1.0F, 0.8F);
        status(player, "message.combat_drones.ramp.installed");
    }

    /** One Fuel Can fills the tank completely. */
    private void refuel(EntityPlayer player, ItemStack stack) {
        DroneType type = getUnitType();
        if (type == null) {
            status(player, "message.combat_drones.ramp.empty");
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

    /** Rockets or rounds one item of {@code stack} loads, or 0 if it isn't gunship ammo. */
    private static int ammoPerItem(ItemStack stack) {
        if (stack.getItem() == ModContent.rocket) {
            return 1;
        }
        return stack.getItem() == ModContent.rifleRounds ? ModConfig.roundsPerRifleItem : 0;
    }

    /** Loads as much of the held ammo as fits; rockets go in a rocket pod, rifle rounds on a gun mount's belt. */
    private void loadAmmo(EntityPlayer player, ItemStack stack) {
        DroneType type = getUnitType();
        if (type == null) {
            status(player, "message.combat_drones.ramp.empty");
            return;
        }
        DroneType.WeaponType wanted = stack.getItem() == ModContent.rocket ? DroneType.WeaponType.ROCKET_POD : DroneType.WeaponType.GUN_MOUNT;
        if (type.getWeaponType() != wanted) {
            status(player, "message.combat_drones.ramp.wrong_ammo");
            return;
        }
        int perItem = ammoPerItem(stack);
        int space = type.getPayloadCapacity() - ammo;
        if (space <= 0) {
            status(player, "message.combat_drones.ramp.ammo_full");
            return;
        }
        // Round up so a part-filled belt can still be topped off; the extra rounds are lost
        int items = Math.min(stack.getCount(), (space + perItem - 1) / perItem);
        ammo = Math.min(type.getPayloadCapacity(), ammo + items * perItem);
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(items);
        }
        world.playSound(null, posX, posY, posZ, ModSounds.DRONE_ATTACH, SoundCategory.BLOCKS, 1.0F, 1.0F);
        player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.ramp.ammo_loaded", ammo, type.getPayloadCapacity()), true);
    }

    /** Takes the unit off as its item (fuel kept on the item), emptying the ramp. */
    public ItemStack takeUnit() {
        DroneType type = getUnitType();
        if (type == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(CombatDrones.MOD_ID, type.getId()));
        ItemStack stack = item == null ? ItemStack.EMPTY : ItemDrone.withAmmo(ItemDrone.withFuel(new ItemStack(item), fuel), ammo);
        clearUnit();
        return stack;
    }

    /** Server: launches the unit from the end of the rail along the ramp's heading. */
    public void launch() {
        DroneType type = getUnitType();
        if (type == null) {
            return;
        }
        double radians = Math.toRadians(rotationYaw);
        double x = posX - Math.sin(radians) * RAIL_END;
        double z = posZ + Math.cos(radians) * RAIL_END;
        world.spawnEntity(new EntityRampUnit(world, type, owner, route, fuel, ammo, x, posY + 1.0, z, rotationYaw));
        world.playSound(null, posX, posY, posZ, ModSounds.UNIT_LAUNCH, SoundCategory.NEUTRAL, 2.0F, 1.0F);
        clearUnit();
    }

    private void clearUnit() {
        dataManager.set(UNIT_TYPE, "");
        owner = null;
        setFuel(0);
        ammo = 0;
        route = Route.empty(Route.TerminalAction.LOITER);
    }

    /** Knocked down: drops itself and whatever unit it held. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (isEntityInvulnerable(source) || isDead) {
            return false;
        }
        if (!world.isRemote) {
            ItemStack unit = takeUnit();
            if (!unit.isEmpty()) {
                entityDropItem(unit, 0.5F);
            }
            entityDropItem(new ItemStack(ModContent.launchRamp), 0.5F);
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
        return distanceSq < 128 * 128;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setString("Unit", dataManager.get(UNIT_TYPE));
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setDouble("Fuel", fuel);
        compound.setInteger("Ammo", ammo);
        compound.setTag("Route", RouteCodec.toNbt(route));
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        dataManager.set(UNIT_TYPE, compound.getString("Unit"));
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        setFuel(compound.getDouble("Fuel"));
        ammo = compound.getInteger("Ammo");
        route = RouteCodec.fromNbt(compound.getCompoundTag("Route"), Route.TerminalAction.LOITER);
    }
}
