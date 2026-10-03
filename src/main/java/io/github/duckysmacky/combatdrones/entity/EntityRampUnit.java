package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import io.github.duckysmacky.combatdrones.network.RouteCodec;
import io.github.duckysmacky.combatdrones.physics.DroneSystems;
import io.github.duckysmacky.combatdrones.physics.FixedWingFlight;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.github.duckysmacky.combatdrones.physics.RouteAutopilot;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A ramp-launched fixed-wing unit in flight: loitering munition, gunship plane or recon UAV, depending on its
 * {@link DroneType}. Flies its {@link Route} on autopilot until fuel runs out; before launch the unit lives inside
 * the Launch Ramp's tile entity instead.
 */
public class EntityRampUnit extends Entity {
    /** Blocks the autopilot keeps above the terrain it's about to fly over. */
    private static final double TERRAIN_CLEARANCE = 6;
    /** How far ahead (in ticks of flight) the terrain floor looks. */
    private static final int TERRAIN_LOOKAHEAD_TICKS = 30;
    /** Movement lost to a collision beyond this (blocks/tick) counts as a crash. */
    private static final double CRASH_THRESHOLD = 0.1;
    private static final double RENDER_DISTANCE = 160.0;

    private static final DataParameter<String> TYPE_ID = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.STRING);
    private static final DataParameter<Float> BANK = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> FUEL_FRACTION = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> HEALTH_FRACTION = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.FLOAT);

    private final FixedWingFlight.State flight = new FixedWingFlight.State();
    @Nullable
    private UUID owner;
    private Route route = Route.empty(Route.TerminalAction.LOITER);
    private int waypointIndex;
    private double launchY;
    private double fuel;
    private float health;
    /** Keeps the unit's chunk (and the next one ahead) loaded, so it keeps flying far from any player. */
    @Nullable
    private ForgeChunkManager.Ticket chunkTicket;

    // Client-side presentation
    public float prevBank;
    public int hitFlashTicks;

    public EntityRampUnit(World world) {
        super(world);
        setSize(0.8F, 0.4F);
    }

    /** A unit leaving its ramp: launched along {@code heading} at cruise speed with a slight climb. */
    public EntityRampUnit(World world, DroneType type, @Nullable UUID owner, Route route, double fuel, double x, double y, double z, float heading) {
        this(world);
        dataManager.set(TYPE_ID, type.getId());
        this.owner = owner;
        this.route = route;
        this.fuel = fuel;
        this.launchY = y;
        setHealth(type.getMaxHP());
        setPosition(x, y, z);
        flight.position.set(x, y, z);
        flight.heading = heading;
        flight.pitch = -10;
        flight.speed = type.getMaxSpeed() * 0.8;
        rotationYaw = heading;
        syncPresentation();
    }

    @Override
    protected void entityInit() {
        dataManager.register(TYPE_ID, "");
        dataManager.register(BANK, 0.0F);
        dataManager.register(FUEL_FRACTION, 1.0F);
        dataManager.register(HEALTH_FRACTION, 1.0F);
    }

    @Nullable
    public DroneType getDroneType() {
        return DroneTypes.get(dataManager.get(TYPE_ID));
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public Route getRoute() {
        return route;
    }

    public int getWaypointIndex() {
        return waypointIndex;
    }

    public double getLaunchY() {
        return launchY;
    }

    public float getBank() {
        return dataManager.get(BANK);
    }

    public float getFuelFraction() {
        return dataManager.get(FUEL_FRACTION);
    }

    public float getHealthFraction() {
        return dataManager.get(HEALTH_FRACTION);
    }

    /** Server: replace the route in flight (Mission Planner "Apply"). The unit heads for the new first waypoint. */
    public void setRoute(Route newRoute) {
        route = newRoute;
        waypointIndex = 0;
    }

    private void setHealth(float value) {
        health = value;
        DroneType type = getDroneType();
        dataManager.set(HEALTH_FRACTION, type == null ? 1.0F : Math.max(0, Math.min(1, value / type.getMaxHP())));
    }

    private static FixedWingFlight.Params paramsOf(DroneType type) {
        return new FixedWingFlight.Params(type.getMaxSpeed(), type.getAcceleration());
    }

    @Override
    public void onUpdate() {
        prevBank = getBank();
        super.onUpdate();
        if (world.isRemote) {
            if (hitFlashTicks > 0) {
                hitFlashTicks--;
            }
            DamageEffects.damageSmoke(this, getHealthFraction());
            return;
        }
        DroneType type = getDroneType();
        if (type == null) {
            CombatDrones.logger.warn("Removing ramp unit with unknown type '{}'", dataManager.get(TYPE_ID));
            setDead();
            return;
        }
        FixedWingFlight.Params params = paramsOf(type);
        // The entity position is the truth (picks up /tp and positions set after NBT load)
        flight.position.set(posX, posY, posZ);
        RouteAutopilot.Command command = RouteAutopilot.guide(flight, route, waypointIndex, launchY, terrainFloor(), params, fuel > 0);
        waypointIndex = command.waypointIndex;
        double thrust = FixedWingFlight.step(flight, command.controls, params);
        if (command.controls.engineOn) {
            fuel = Math.max(0, fuel - DroneSystems.drainPerTick(type.getDrainRatePerTick(), thrust));
        }

        double wantedX = flight.position.x - posX;
        double wantedY = flight.position.y - posY;
        double wantedZ = flight.position.z - posZ;
        AxisAlignedBB swept = getEntityBoundingBox().expand(wantedX, wantedY, wantedZ);
        double startX = posX;
        double startY = posY;
        double startZ = posZ;
        move(MoverType.SELF, wantedX, wantedY, wantedZ);
        double blocked = Math.sqrt(sq(wantedX - (posX - startX)) + sq(wantedY - (posY - startY)) + sq(wantedZ - (posZ - startZ)));
        flight.position.set(posX, posY, posZ);
        syncPresentation();
        if (blocked > CRASH_THRESHOLD || hitsEntity(swept)) {
            onCollision(type);
            return;
        }
        updateChunkLoading();
    }

    /** Server: forces the current chunk and the one a second ahead; releases chunks it has left behind. */
    private void updateChunkLoading() {
        if (chunkTicket == null) {
            chunkTicket = ForgeChunkManager.requestTicket(CombatDrones.instance, world, ForgeChunkManager.Type.ENTITY);
            if (chunkTicket == null) {
                return; // out of tickets (forgeChunkLoading.cfg): fly on, but only while players keep the area loaded
            }
            chunkTicket.bindEntity(this);
        }
        Set<ChunkPos> wanted = new HashSet<>();
        wanted.add(new ChunkPos(MathHelper.floor(posX) >> 4, MathHelper.floor(posZ) >> 4));
        wanted.add(new ChunkPos(MathHelper.floor(posX + flight.velocity.x * 20) >> 4, MathHelper.floor(posZ + flight.velocity.z * 20) >> 4));
        for (ChunkPos forced : chunkTicket.getChunkList()) {
            if (!wanted.contains(forced)) {
                ForgeChunkManager.unforceChunk(chunkTicket, forced);
            }
        }
        for (ChunkPos chunk : wanted) {
            if (!chunkTicket.getChunkList().contains(chunk)) {
                ForgeChunkManager.forceChunk(chunkTicket, chunk);
            }
        }
    }

    /** Server: after a restart Forge hands back the ticket this unit held (see CombatDrones' loading callback). */
    public void adoptChunkTicket(ForgeChunkManager.Ticket ticket) {
        chunkTicket = ticket;
    }

    @Override
    public void setDead() {
        super.setDead();
        if (!world.isRemote && chunkTicket != null) {
            ForgeChunkManager.releaseTicket(chunkTicket);
            chunkTicket = null;
        }
    }

    private static double sq(double value) {
        return value * value;
    }

    /**
     * Lowest safe absolute altitude: the highest ground the unit is about to fly over, plus clearance. Sampled along
     * its current heading, so it climbs over hills instead of flying into them.
     */
    private double terrainFloor() {
        double headingRadians = Math.toRadians(flight.heading);
        double stepX = -Math.sin(headingRadians) * Math.max(flight.speed, 0.3);
        double stepZ = Math.cos(headingRadians) * Math.max(flight.speed, 0.3);
        int highest = 0;
        for (int tick = 0; tick <= TERRAIN_LOOKAHEAD_TICKS; tick += 3) {
            BlockPos column = new BlockPos(posX + stepX * tick, 0, posZ + stepZ * tick);
            if (world.isBlockLoaded(column)) {
                highest = Math.max(highest, world.getHeight(column).getY());
            }
        }
        return highest + TERRAIN_CLEARANCE;
    }

    private boolean hitsEntity(AxisAlignedBB box) {
        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(this, box)) {
            if (entity.canBeCollidedWith() && entity.isEntityAlive()) {
                return true;
            }
        }
        return false;
    }

    private void syncPresentation() {
        rotationYaw = (float) flight.heading;
        rotationPitch = (float) flight.pitch;
        dataManager.set(BANK, (float) flight.bank);
        DroneType type = getDroneType();
        dataManager.set(FUEL_FRACTION, type == null ? 0 : (float) Math.min(1, fuel / type.getBatteryOrFuelCapacity()));
    }

    /**
     * Ran into terrain or something solid. Warheads go off; an unarmed recon UAV never detonates (architecture
     * rule 6) and lands intact as an item; anything else is wrecked.
     */
    private void onCollision(DroneType type) {
        if (type.isArmed() && type.isImpactDetonates()) {
            detonate(type);
        } else if (!type.isArmed()) {
            land(type);
        } else {
            setDead();
            DamageEffects.wreck(this);
        }
    }

    private void detonate(DroneType type) {
        setDead();
        if (type.getBlastPower() > 0) {
            DroneExplosion.detonate((WorldServer) world, this, getBlameTarget(), posX, posY + height / 2, posZ, type.getBlastPower());
        } else {
            DamageEffects.wreck(this);
        }
    }

    /** Comes down intact: drops back as its item, remaining fuel and all. */
    private void land(DroneType type) {
        setDead();
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(CombatDrones.MOD_ID, type.getId()));
        if (item != null) {
            entityDropItem(ItemDrone.withFuel(new ItemStack(item), fuel), 0.2F);
        }
    }

    @Nullable
    private EntityLivingBase getBlameTarget() {
        return owner != null ? world.getPlayerEntityByUUID(owner) : null;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (isEntityInvulnerable(source) || isDead) {
            return false;
        }
        if (!world.isRemote) {
            setHealth(health - amount * (float) ModConfig.droneDamageTakenMultiplier);
            DroneType type = getDroneType();
            if (health > 0) {
                DamageEffects.hit(this);
            } else if (type != null && type.isArmed() && type.getBlastPower() > 0) {
                detonate(type); // a warhead shot down goes off in the air
            } else {
                setDead();
                DamageEffects.wreck(this);
            }
        }
        return true;
    }

    @Override
    public void handleStatusUpdate(byte id) {
        if (id == DamageEffects.HIT_STATUS) {
            hitFlashTicks = DamageEffects.HIT_FLASH_TICKS;
        } else {
            super.handleStatusUpdate(id);
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
    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double distanceSq) {
        return distanceSq < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setString("Type", dataManager.get(TYPE_ID));
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setTag("Route", RouteCodec.toNbt(route));
        compound.setInteger("WaypointIndex", waypointIndex);
        compound.setDouble("LaunchY", launchY);
        compound.setDouble("Fuel", fuel);
        compound.setFloat("Health", health);
        compound.setDouble("Heading", flight.heading);
        compound.setDouble("Pitch", flight.pitch);
        compound.setDouble("Bank", flight.bank);
        compound.setDouble("Speed", flight.speed);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        dataManager.set(TYPE_ID, compound.getString("Type"));
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        route = RouteCodec.fromNbt(compound.getCompoundTag("Route"), Route.TerminalAction.LOITER);
        waypointIndex = compound.getInteger("WaypointIndex");
        launchY = compound.hasKey("LaunchY") ? compound.getDouble("LaunchY") : posY;
        fuel = compound.getDouble("Fuel");
        setHealth(compound.getFloat("Health"));
        flight.heading = compound.getDouble("Heading");
        flight.pitch = compound.getDouble("Pitch");
        flight.bank = compound.getDouble("Bank");
        flight.speed = compound.getDouble("Speed");
        flight.position.set(posX, posY, posZ);
    }
}
