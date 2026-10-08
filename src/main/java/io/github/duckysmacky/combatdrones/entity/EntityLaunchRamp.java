package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.network.PlannerServer;
import io.github.duckysmacky.combatdrones.network.RouteCodec;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import io.github.duckysmacky.combatdrones.type.Loadout;
import io.github.duckysmacky.combatdrones.type.RocketType;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Launch Ramp: a rail placed facing exactly where its placer looks, so units can be aimed at any heading. Two sizes: a
 * short Munition Ramp for loitering munitions and a long Plane Ramp for planes and the recon UAV.
 * Holds at most one ramp-launched unit (type, owner, fuel) plus the route planned for it; the unit only becomes an
 * {@link EntityRampUnit} when it launches. Left-click (attack) knocks the ramp down, dropping it and its unit.
 */
public class EntityLaunchRamp extends Entity {
    /** Which ramp it is. The rail geometry scales with the length (the 4-block original is scale 1). */
    public enum Kind {
        // Ramps placed before the split default to MUNITION (ordinal 0)
        MUNITION("launch_ramp", 3, 1.6F),
        PLANE("plane_ramp", 5, 2.2F);

        private final String name;
        private final int lengthBlocks;
        private final float hitboxWidth;

        Kind(String name, int lengthBlocks, float hitboxWidth) {
            this.name = name;
            this.lengthBlocks = lengthBlocks;
            this.hitboxWidth = hitboxWidth;
        }

        /** Item registry name, also its geo model. */
        public String getName() {
            return name;
        }

        /** Rail length relative to the 4-block design all the rail measurements were made on. */
        public float railScale() {
            return lengthBlocks / 4.0F;
        }

        /** Munitions go on the Munition Ramp; everything else that lands (planes, recon UAV) on the Plane Ramp. */
        public static Kind forUnit(DroneType type) {
            return type.isImpactDetonates() ? MUNITION : PLANE;
        }

        static Kind byIndex(int index) {
            Kind[] kinds = values();
            return index >= 0 && index < kinds.length ? kinds[index] : MUNITION;
        }
    }

    /** How far along the rail (blocks, from the ramp's centre) a launching unit appears, on the 4-block design. */
    private static final double RAIL_END = 2.0;
    /** Launch height above the ramp's base: the rail's front end plus clearance, on the 4-block design (scaled part). */
    private static final double LAUNCH_HEIGHT = 1.0;
    private static final double LAUNCH_CLEARANCE = 0.3;

    private static final DataParameter<Integer> KIND = EntityDataManager.createKey(EntityLaunchRamp.class, DataSerializers.VARINT);
    private static final DataParameter<String> UNIT_TYPE = EntityDataManager.createKey(EntityLaunchRamp.class, DataSerializers.STRING);
    private static final DataParameter<Float> FUEL_FRACTION = EntityDataManager.createKey(EntityLaunchRamp.class, DataSerializers.FLOAT);

    @Nullable
    private UUID owner;
    private double fuel;
    private Loadout loadout = new Loadout();
    private Route route = Route.empty(Route.TerminalAction.LOITER);

    public EntityLaunchRamp(World world) {
        super(world);
        setSize(1.6F, 0.8F);
    }

    public EntityLaunchRamp(World world, Kind kind, double x, double y, double z, float yaw) {
        this(world);
        dataManager.set(KIND, kind.ordinal());
        setPositionAndRotation(x, y, z, yaw, 0);
    }

    @Override
    protected void entityInit() {
        dataManager.register(KIND, 0);
        dataManager.register(UNIT_TYPE, "");
        dataManager.register(FUEL_FRACTION, 0.0F);
    }

    public Kind getKind() {
        return Kind.byIndex(dataManager.get(KIND));
    }

    /** Takes the kind's hitbox once the kind is known (spawn data on clients, NBT on load); re-centred like setSize needs. */
    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        if (KIND.equals(key)) {
            setSize(getKind().hitboxWidth, 0.8F);
            setPosition(posX, posY, posZ);
        }
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

    public Loadout getLoadout() {
        return loadout;
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
            return stack.getItem() instanceof ItemDrone || stack.getItem() == ModContent.fuelCan || ammoWeapon(stack) != null
                    || stack.getItem() == ModContent.missionPlanner || (stack.isEmpty() && player.isSneaking());
        }
        if (stack.getItem() instanceof ItemDrone) {
            install(player, stack);
        } else if (stack.getItem() == ModContent.fuelCan) {
            refuel(player, stack);
        } else if (ammoWeapon(stack) != null) {
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
        if (Kind.forUnit(type) != getKind()) {
            status(player, Kind.forUnit(type) == Kind.PLANE ? "message.combat_drones.ramp.needs_plane_ramp" : "message.combat_drones.ramp.needs_munition_ramp");
            return;
        }
        if (isLoaded()) {
            status(player, "message.combat_drones.ramp.occupied");
            return;
        }
        dataManager.set(UNIT_TYPE, type.getId());
        owner = player.getUniqueID();
        setFuel(Math.min(ItemDrone.getFuel(stack), type.getBatteryOrFuelCapacity()));
        loadout = ItemDrone.getLoadout(stack).clampTo(type);
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

    /** The weapon {@code stack} is ammo for, or null if it isn't plane ammo. */
    @Nullable
    private static DroneType.WeaponType ammoWeapon(ItemStack stack) {
        if (rocketKindOf(stack) != null) {
            return DroneType.WeaponType.ROCKET_POD;
        }
        return stack.getItem() == ModContent.rifleRounds ? DroneType.WeaponType.GUN_MOUNT : null;
    }

    @Nullable
    private static RocketType rocketKindOf(ItemStack stack) {
        for (RocketType kind : RocketType.values()) {
            if (stack.getItem() == ModContent.rocketItem(kind)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * Loads as much of the held ammo as fits into the station that takes it: rockets into a rocket pod, rifle rounds
     * onto a gun mount's belt. A pod holds one rocket size at a time, so loading the other size hands the rockets
     * already in it back.
     */
    private void loadAmmo(EntityPlayer player, ItemStack stack) {
        DroneType type = getUnitType();
        if (type == null) {
            status(player, "message.combat_drones.ramp.empty");
            return;
        }
        int station = type.stationOf(ammoWeapon(stack));
        if (station < 0) {
            status(player, "message.combat_drones.ramp.wrong_ammo");
            return;
        }
        RocketType kind = rocketKindOf(stack);
        if (kind != null && kind != loadout.getRocketKind()) {
            returnRockets(player, loadout.getRocketKind(), loadout.getAmmo(station));
            loadout.setAmmo(station, 0);
            loadout.setRocketKind(kind);
        }
        int perItem = kind != null ? 1 : ModConfig.roundsPerRifleItem;
        int capacity = loadout.capacity(type, station);
        int space = capacity - loadout.getAmmo(station);
        if (space <= 0) {
            status(player, "message.combat_drones.ramp.ammo_full");
            return;
        }
        // Round up so a part-filled belt can still be topped off; the extra rounds are lost
        int items = Math.min(stack.getCount(), (space + perItem - 1) / perItem);
        loadout.setAmmo(station, Math.min(capacity, loadout.getAmmo(station) + items * perItem));
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(items);
        }
        world.playSound(null, posX, posY, posZ, ModSounds.DRONE_ATTACH, SoundCategory.BLOCKS, 1.0F, 1.0F);
        player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.ramp.ammo_loaded", loadout.getAmmo(station), capacity), true);
    }

    /** Gives back rockets unloaded to make room for the other size, in whole stacks. */
    private static void returnRockets(EntityPlayer player, RocketType kind, int count) {
        Item item = ModContent.rocketItem(kind);
        while (count > 0) {
            int stackSize = Math.min(count, item.getItemStackLimit());
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(item, stackSize));
            count -= stackSize;
        }
    }

    /** Takes the unit off as its item (fuel kept on the item), emptying the ramp. */
    public ItemStack takeUnit() {
        DroneType type = getUnitType();
        if (type == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(CombatDrones.MOD_ID, type.getId()));
        ItemStack stack = item == null ? ItemStack.EMPTY : ItemDrone.withLoadout(ItemDrone.withFuel(new ItemStack(item), fuel), loadout);
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
        double scale = getKind().railScale();
        double x = posX - Math.sin(radians) * RAIL_END * scale;
        double z = posZ + Math.cos(radians) * RAIL_END * scale;
        double y = posY + LAUNCH_HEIGHT * scale + LAUNCH_CLEARANCE;
        world.spawnEntity(new EntityRampUnit(world, type, owner, route, fuel, loadout, x, y, z, rotationYaw));
        world.playSound(null, posX, posY, posZ, ModSounds.UNIT_LAUNCH, SoundCategory.NEUTRAL, 3.0F, 1.0F);
        EffectPacket.launch(world, new Vec3d(posX, posY, posZ), Vec3d.fromPitchYaw(0, rotationYaw));
        clearUnit();
    }

    private void clearUnit() {
        dataManager.set(UNIT_TYPE, "");
        owner = null;
        setFuel(0);
        loadout = new Loadout();
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
            entityDropItem(new ItemStack(ModContent.rampItem(getKind())), 0.5F);
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
        compound.setInteger("Kind", getKind().ordinal());
        compound.setString("Unit", dataManager.get(UNIT_TYPE));
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setDouble("Fuel", fuel);
        ItemDrone.writeLoadout(compound, loadout);
        compound.setTag("Route", RouteCodec.toNbt(route));
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        dataManager.set(KIND, compound.getInteger("Kind"));
        dataManager.set(UNIT_TYPE, DroneTypes.migrateId(compound.getString("Unit")));
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        setFuel(compound.getDouble("Fuel"));
        loadout = ItemDrone.readLoadout(compound);
        route = RouteCodec.fromNbt(compound.getCompoundTag("Route"), Route.TerminalAction.LOITER);
    }
}
