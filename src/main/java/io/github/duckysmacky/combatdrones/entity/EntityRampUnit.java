package io.github.duckysmacky.combatdrones.entity;

import com.google.common.base.Optional;
import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.network.GunFirePacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.network.RouteCodec;
import io.github.duckysmacky.combatdrones.physics.DroneSystems;
import io.github.duckysmacky.combatdrones.physics.FixedWingFlight;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.github.duckysmacky.combatdrones.physics.RouteAutopilot;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.MoverType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import javax.vecmath.Quat4d;
import java.util.UUID;

/**
 * A ramp-launched fixed-wing unit in flight: loitering munition, gunship plane or recon UAV, depending on its
 * {@link DroneType}. Flies its {@link Route} on autopilot until fuel runs out; before launch the unit lives inside
 * the Launch Ramp's tile entity instead.
 */
public class EntityRampUnit extends Entity implements ChunkAnchor.Holder {
    /** Blocks the autopilot keeps above the terrain it's about to fly over. */
    private static final double TERRAIN_CLEARANCE = 6;
    /** How far ahead (in ticks of flight) the terrain floor looks. */
    private static final int TERRAIN_LOOKAHEAD_TICKS = 30;
    /** Movement lost to a collision beyond this (blocks/tick) counts as a crash. */
    private static final double CRASH_THRESHOLD = 0.1;
    private static final double RENDER_DISTANCE = 160.0;
    /** On its Return landing approach, a unit touching down slower than this share of top speed lands intact. */
    private static final double LANDING_SPEED_FRACTION = 0.75;
    /** Anywhere else only a belly landing does: flat onto the ground, slower than this share, barely sinking. */
    private static final double BELLY_LANDING_SPEED_FRACTION = 0.5;
    private static final double BELLY_LANDING_SINK_RATE = 0.2;
    /** Manual input older than this (ticks) is treated as a centred stick. */
    protected static final int STALE_INPUT_TICKS = 10;
    private static final int DESIGNATE_COOLDOWN_TICKS = 10;
    // Client smoothing: share of the remaining rotation / position error taken out each tick, and the error beyond
    // which it just jumps (teleport, chunk reload)
    private static final float ROTATION_SMOOTHING = 0.5F;
    private static final double POSITION_CORRECTION = 0.3;
    private static final double SNAP_DISTANCE = 8.0;

    private static final DataParameter<String> TYPE_ID = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.STRING);
    private static final DataParameter<Float> BANK = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> FUEL_FRACTION = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.FLOAT);
    // Exact flight state for clients: vanilla sends rotations as bytes (1.4° steps), which made the camera judder
    private static final DataParameter<Float> HEADING = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> PITCH = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> SPEED = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.FLOAT);
    /** Where it's diving (designated point or the autopilot's strike point), for the pilot's marker. */
    private static final DataParameter<Optional<BlockPos>> STRIKE_POINT = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.OPTIONAL_BLOCK_POS);
    private static final DataParameter<Boolean> BOOSTING = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> STRIKING = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> AMMO = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT);
    private static final DataParameter<Float> HEALTH_FRACTION = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.FLOAT);

    protected final FixedWingFlight.State flight = new FixedWingFlight.State();
    @Nullable
    private UUID owner;
    private Route route = Route.empty(Route.TerminalAction.LOITER);
    private int waypointIndex;
    private double launchY;
    /** Where Return flies back to: the launch point. NaN for units summoned without one. */
    private double homeX = Double.NaN;
    private double homeZ = Double.NaN;
    protected double fuel;
    protected float health;
    // Manual control ("Take control"): the controller's latest input, and when it arrived
    private double manualPitch;
    private double manualRoll;
    private double manualThrottle;
    private boolean manualTrigger;
    private boolean manualFree;
    private boolean manualBoost;
    private long manualInputTick = Long.MIN_VALUE;
    /** A point picked from the camera (click-designate scheme): overrides the route with a strike. */
    @Nullable
    private Vec3d designatedTarget;
    private int designateCooldown;
    /** Ticks until the weapon can fire again; fractional so fire rates aren't limited to 20 / whole ticks. */
    private double weaponCooldown;
    /** This tick the autopilot was flying Return's final landing approach. */
    private boolean landingApproach;
    /** Keeps the unit's chunk (and the next one ahead) loaded, so it keeps flying far from any player. */
    protected final ChunkAnchor anchor = new ChunkAnchor(this);

    // Client-side presentation
    public float prevBank;
    /** Latest position from the server, dead-reckoned forward between updates; the client eases toward it. */
    private double serverX;
    private double serverY;
    private double serverZ;
    private boolean hasServerPosition;
    public int hitFlashTicks;
    private boolean wasBoosting;

    public EntityRampUnit(World world) {
        super(world);
        setSize(0.8F, 0.4F);
    }

    /** A unit leaving its ramp: launched along {@code heading} at cruise speed with a slight climb. */
    public EntityRampUnit(World world, DroneType type, @Nullable UUID owner, Route route, double fuel, int ammo, double x, double y, double z, float heading) {
        this(world);
        dataManager.set(AMMO, ammo);
        dataManager.set(TYPE_ID, type.getId());
        this.owner = owner;
        this.route = route;
        this.fuel = fuel;
        this.launchY = y;
        this.homeX = x;
        this.homeZ = z;
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
        dataManager.register(AMMO, 0);
        dataManager.register(STRIKING, false);
        dataManager.register(STRIKE_POINT, Optional.absent());
        dataManager.register(BOOSTING, false);
        dataManager.register(HEADING, 0.0F);
        dataManager.register(PITCH, 0.0F);
        dataManager.register(SPEED, 0.0F);
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

    /** Diving on a designated point (synced for the HUD). */
    public boolean isStrikingDesignated() {
        return dataManager.get(STRIKING);
    }

    /** Rockets or rounds left (synced for the HUD). */
    public int getAmmo() {
        return dataManager.get(AMMO);
    }

    public float getHealthFraction() {
        return dataManager.get(HEALTH_FRACTION);
    }

    /** Server: replace the route in flight (Mission Planner "Apply"). The unit heads for the new first waypoint. */
    public void setRoute(Route newRoute) {
        route = newRoute;
        waypointIndex = 0;
        // A new plan (and target) from the planner replaces any point picked from the camera
        designatedTarget = null;
        dataManager.set(STRIKING, false);
    }

    protected void setHealth(float value) {
        health = value;
        DroneType type = getDroneType();
        dataManager.set(HEALTH_FRACTION, type == null ? 1.0F : Math.max(0, Math.min(1, value / type.getMaxHP())));
    }

    private static FixedWingFlight.Params paramsOf(DroneType type) {
        return new FixedWingFlight.Params(type.getMaxSpeed(), type.getAcceleration(), type.getAgility(), type.getPitchAgility());
    }

    /** Takes the type's hitbox size, also after a config reload; setSize keeps the min corner, so re-centre. */
    private void fitHitbox(@Nullable DroneType type) {
        if (type != null && (width != type.getHitboxWidth() || height != type.getHitboxHeight())) {
            setSize(type.getHitboxWidth(), type.getHitboxHeight());
            setPosition(posX, posY, posZ);
        }
    }

    @Override
    public void onUpdate() {
        prevBank = getBank();
        super.onUpdate();
        fitHitbox(getDroneType());
        if (world.isRemote) {
            clientMotion();
            if (hitFlashTicks > 0) {
                hitFlashTicks--;
            }
            DamageEffects.damageSmoke(this, getHealthFraction());
            DroneType clientType = getDroneType();
            boolean boostingNow = isBoosting();
            if (clientType != null && getFuelFraction() > 0) {
                if (boostingNow && !wasBoosting) {
                    Exhaust.boostStart(this);
                }
                Exhaust.unitTrail(this, clientType, boostingNow);
            }
            wasBoosting = boostingNow;
            return;
        }
        DroneType type = getDroneType();
        if (type == null) {
            CombatDrones.logger.warn("Removing ramp unit with unknown type '{}'", dataManager.get(TYPE_ID));
            setDead();
            return;
        }
        serverUpdate(type);
    }

    /** Server tick of a unit with a known type: route autopilot or manual flight, fuel, movement and collisions. */
    protected void serverUpdate(DroneType type) {
        boolean boosting = isBoosting(type);
        dataManager.set(BOOSTING, boosting);
        FixedWingFlight.Params params = boosting ? paramsOf(type).boosted(ModConfig.boostSpeedMultiplier) : paramsOf(type);
        if (designateCooldown > 0) {
            designateCooldown--;
        }
        if (weaponCooldown > 0) {
            weaponCooldown -= 1; // may dip just below 0; the next shot keeps that remainder
        }
        // The entity position is the truth (picks up /tp and positions set after NBT load)
        flight.position.set(posX, posY, posZ);
        FixedWingFlight.Controls controls = manualControls(type, params, boosting);
        landingApproach = false;
        Vec3d strikePoint = null;
        if (controls == null && designatedTarget != null) {
            controls = RouteAutopilot.pursue(flight, designatedTarget.x, designatedTarget.y, designatedTarget.z, terrainFloor(), params, fuel > 0);
            strikePoint = designatedTarget;
        }
        if (controls == null) {
            RouteAutopilot.Command command = autopilotCommand(type, params);
            controls = command.controls;
            landingApproach = command.phase == RouteAutopilot.Phase.LAND;
            if (command.phase == RouteAutopilot.Phase.STRIKE) {
                strikePoint = routeStrikePoint();
            }
        }
        dataManager.set(STRIKE_POINT, strikePoint == null ? Optional.absent() : Optional.of(new BlockPos(strikePoint)));
        double thrust = FixedWingFlight.step(flight, controls, params);
        if (controls.engineOn) {
            double burn = DroneSystems.drainPerTick(type.getDrainRatePerTick(), thrust) * (boosting ? ModConfig.boostFuelMultiplier : 1);
            fuel = Math.max(0, fuel - burn);
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
        // onGround catches shallow touchdowns, which lose too little movement to count as a crash
        boolean hitEntity = hitsEntity(swept);
        if (blocked > CRASH_THRESHOLD || onGround || hitEntity) {
            onCollision(type, hitEntity);
            return;
        }
        ReconIntel.sweep(this, type);
        anchor.hold(posX + flight.velocity.x * 20, posZ + flight.velocity.z * 20);
    }

    /** The route (and its terminal action) on autopilot. */
    private RouteAutopilot.Command autopilotCommand(DroneType type, FixedWingFlight.Params params) {
        RouteAutopilot.Situation situation = new RouteAutopilot.Situation(launchY, terrainFloor(), fuel > 0,
                fuel / type.getBatteryOrFuelCapacity(), ModConfig.lowFuelFraction, type.isArmed() && type.isImpactDetonates(),
                strikeGroundY(), homeX, launchY - 1, homeZ);
        RouteAutopilot.Command command = RouteAutopilot.guide(flight, route, waypointIndex, situation, params);
        waypointIndex = command.waypointIndex;
        return command;
    }

    /** What an autopilot strike is aiming at: the marked target for Destroy, else the ground under the last waypoint. */
    @Nullable
    private Vec3d routeStrikePoint() {
        Route.Target target = route.getTarget();
        if (route.getTerminal() == Route.TerminalAction.DESTROY && target != null && !Double.isNaN(target.y)) {
            return new Vec3d(target.x, target.y, target.z);
        }
        if (route.size() == 0) {
            return null;
        }
        Route.Waypoint last = route.get(route.size() - 1);
        double groundY = strikeGroundY();
        return new Vec3d(last.x, Double.isNaN(groundY) ? launchY : groundY, last.z);
    }

    /** Boost: a remotely controlled munition with fuel whose pilot holds the boost key (fresh input only). */
    private boolean isBoosting(DroneType type) {
        return manualBoost && type.isImpactDetonates() && fuel > 0 && RemoteSessions.controllerOf(this) != null
                && world.getTotalWorldTime() - manualInputTick <= STALE_INPUT_TICKS;
    }

    public boolean isBoosting() {
        return dataManager.get(BOOSTING);
    }

    protected void setBoosting(boolean boosting) {
        dataManager.set(BOOSTING, boosting);
    }

    /**
     * Airframe orientation for drawing it and for a camera fixed to its nose, interpolated for the frame. Planes go by
     * heading, pitch and bank.
     */
    public Quat4d renderOrientation(float partialTicks) {
        float yaw = prevRotationYaw + MathHelper.wrapDegrees(rotationYaw - prevRotationYaw) * partialTicks;
        float pitch = prevRotationPitch + (rotationPitch - prevRotationPitch) * partialTicks;
        float bank = prevBank + MathHelper.wrapDegrees(getBank() - prevBank) * partialTicks;
        return HandFlownFlight.fromEuler(yaw, pitch, bank);
    }

    /** Client: the point it's diving on, if any. */
    @Nullable
    public BlockPos getStrikePoint() {
        return dataManager.get(STRIKE_POINT).orNull();
    }

    /** Airspeed in blocks/tick, as the server last reported it (for the HUD). */
    public float getSyncedSpeed() {
        return dataManager.get(SPEED);
    }

    /**
     * Client: fly on by the synced heading, pitch and speed every tick and ease toward the server's position, instead
     * of jumping to each position packet. Packets don't arrive exactly once per client tick, so applying them as-is
     * made the unit (and the camera riding it) stutter.
     */
    private void clientMotion() {
        float heading = dataManager.get(HEADING);
        float pitch = dataManager.get(PITCH);
        rotationYaw += MathHelper.wrapDegrees(heading - rotationYaw) * ROTATION_SMOOTHING;
        rotationPitch += (pitch - rotationPitch) * ROTATION_SMOOTHING;
        Vec3d velocity = Vec3d.fromPitchYaw(pitch, heading).scale(dataManager.get(SPEED));
        double x = posX + velocity.x;
        double y = posY + velocity.y;
        double z = posZ + velocity.z;
        if (hasServerPosition) {
            double errorX = serverX - x;
            double errorY = serverY - y;
            double errorZ = serverZ - z;
            if (errorX * errorX + errorY * errorY + errorZ * errorZ > SNAP_DISTANCE * SNAP_DISTANCE) {
                x = serverX;
                y = serverY;
                z = serverZ;
            } else {
                x += errorX * POSITION_CORRECTION;
                y += errorY * POSITION_CORRECTION;
                z += errorZ * POSITION_CORRECTION;
            }
            // Until the next packet, assume the server moved on the same way
            serverX += velocity.x;
            serverY += velocity.y;
            serverZ += velocity.z;
        }
        setPosition(x, y, z);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        // Only remembered: clientMotion() eases toward it. Rotation comes from the exact synced values instead.
        serverX = x;
        serverY = y;
        serverZ = z;
        hasServerPosition = true;
    }

    /** Stick input while a controller flies it by hand (stick scheme), or null when the autopilot should fly. */
    @Nullable
    private FixedWingFlight.Controls manualControls(DroneType type, FixedWingFlight.Params params, boolean boosting) {
        if (type.getControlScheme() != DroneType.ControlScheme.STICK || RemoteSessions.controllerOf(this) == null) {
            return null;
        }
        // Input stopped arriving (lag spike): centre the stick, keep the throttle
        if (world.getTotalWorldTime() - manualInputTick > STALE_INPUT_TICKS) {
            manualPitch = 0;
            manualRoll = 0;
            manualTrigger = false;
        }
        if (manualTrigger) {
            fireWeapon(type);
        }
        double throttle = boosting ? 1 : manualThrottle;
        return manualFree ? FixedWingFlight.fromRates(flight, params, manualPitch, manualRoll, throttle, fuel > 0)
                : FixedWingFlight.fromStick(manualPitch, manualRoll, throttle, fuel > 0);
    }

    /** Fires one rocket or round along the nose, if the weapon is ready and there's ammo. Manual control only. */
    private void fireWeapon(DroneType type) {
        int ammo = getAmmo();
        if (type.getWeaponType() == DroneType.WeaponType.NONE || weaponCooldown > 0 || ammo <= 0) {
            return;
        }
        dataManager.set(AMMO, ammo - 1);
        Vec3d nose = Vec3d.fromPitchYaw(rotationPitch, rotationYaw);
        Vec3d muzzle = new Vec3d(posX, posY + height / 2, posZ).add(nose.scale(1.2));
        if (type.getWeaponType() == DroneType.WeaponType.ROCKET_POD) {
            weaponCooldown += ModConfig.rocketCooldownTicks;
            Vec3d velocity = nose.scale(ModConfig.rocketSpeed).add(flight.velocity.x, flight.velocity.y, flight.velocity.z);
            world.spawnEntity(new EntityRocket(world, this, owner, muzzle, velocity));
            world.playSound(null, posX, posY, posZ, ModSounds.GUNSHIP_ROCKET, SoundCategory.NEUTRAL, 4.5F, 1.0F);
        } else {
            weaponCooldown += ModConfig.gunCooldownTicks;
            fireRound(muzzle);
        }
    }

    /** Gun mount: hitscan along the nose with a little spread; nearby players see a tracer. */
    private void fireRound(Vec3d muzzle) {
        float spread = (float) ModConfig.gunSpread;
        Vec3d direction = Vec3d.fromPitchYaw(rotationPitch + (float) rand.nextGaussian() * spread / 2,
                rotationYaw + (float) rand.nextGaussian() * spread / 2);
        Vec3d end = muzzle.add(direction.scale(ModConfig.gunRange));
        RayTraceResult hit = Raytrace.firstResult(world, muzzle, end, this, ModConfig.gunHitMargin);
        if (hit != null && hit.entityHit != null) {
            if (hit.entityHit instanceof EntityLivingBase) {
                // 10 rounds a second would otherwise mostly land inside vanilla's hurt cooldown and do nothing
                hit.entityHit.hurtResistantTime = 0;
            }
            hit.entityHit.attackEntityFrom(new EntityDamageSourceIndirect("combat_drones.gunfire", this, getBlameTarget()).setProjectile(),
                    (float) ModConfig.gunDamagePerRound);
        }
        Vec3d impact = hit != null ? hit.hitVec : end;
        ModNetwork.CHANNEL.sendToAllAround(new GunFirePacket(muzzle, impact, hit != null, getEntityId()),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(), posX, posY, posZ, 160));
        world.playSound(null, posX, posY, posZ, ModSounds.GUNSHIP_GUN, SoundCategory.NEUTRAL, 4.5F, 0.9F + rand.nextFloat() * 0.2F);
    }

    /** Server: latest manual input from the network. Only the current controller's input counts; values arrive clamped. */
    public void acceptManualInput(EntityPlayerMP sender, double pitch, double roll, double throttle, boolean trigger, boolean free, boolean boost) {
        if (RemoteSessions.controllerOf(this) != sender) {
            return;
        }
        manualPitch = pitch;
        manualRoll = roll;
        manualThrottle = throttle;
        manualTrigger = trigger;
        manualFree = free;
        manualBoost = boost;
        manualInputTick = world.getTotalWorldTime();
    }

    /**
     * Server: click-designate. Raytraces from the unit along the controller's camera direction (up to
     * designateRange) and strikes whatever block or entity it hits first; the point is fixed once picked.
     */
    public void designate(EntityPlayerMP sender, Vec3d direction) {
        DroneType type = getDroneType();
        if (RemoteSessions.controllerOf(this) != sender || type == null || type.getControlScheme() != DroneType.ControlScheme.CLICK_DESIGNATE
                || designateCooldown > 0 || !(direction.lengthSquared() > 1e-6) || !Double.isFinite(direction.lengthSquared())) {
            return;
        }
        designateCooldown = DESIGNATE_COOLDOWN_TICKS;
        Vec3d start = new Vec3d(posX, posY + height / 2, posZ);
        Vec3d end = start.add(direction.normalize().scale(ModConfig.designateRange));
        Vec3d hit = Raytrace.firstHit(world, start, end, this);
        if (hit == null) {
            sender.sendStatusMessage(new TextComponentTranslation("message.combat_drones.remote.nothing_there"), true);
            return;
        }
        designatedTarget = hit;
        dataManager.set(STRIKING, true);
        sender.sendStatusMessage(new TextComponentTranslation("message.combat_drones.remote.designated", Math.round(hit.distanceTo(start))), true);
    }

    /** Server: after a restart Forge hands back the ticket this unit held (see CombatDrones' loading callback). */
    @Override
    public void adoptChunkTicket(ForgeChunkManager.Ticket ticket) {
        anchor.adopt(ticket);
    }

    @Override
    public void setDead() {
        if (!world.isRemote) {
            RemoteSessions.onUnitRemoved(this); // rider home before any explosion
        }
        super.setDead();
        if (!world.isRemote) {
            anchor.release();
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

    /** Ground height under the last waypoint (where Dive aims), or NaN if that area isn't loaded. */
    private double strikeGroundY() {
        if (route.size() == 0 || waypointIndex < route.size()) {
            return Double.NaN;
        }
        Route.Waypoint last = route.get(route.size() - 1);
        BlockPos column = new BlockPos(last.x, 0, last.z);
        return world.isBlockLoaded(column) ? world.getHeight(column).getY() : Double.NaN;
    }

    protected boolean hitsEntity(AxisAlignedBB box) {
        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(this, box)) {
            if (entity.canBeCollidedWith() && entity.isEntityAlive() && !isPassenger(entity)) {
                return true;
            }
        }
        return false;
    }

    protected void syncPresentation() {
        rotationYaw = (float) flight.heading;
        rotationPitch = (float) flight.pitch;
        dataManager.set(BANK, (float) flight.bank);
        dataManager.set(HEADING, (float) flight.heading);
        dataManager.set(PITCH, (float) flight.pitch);
        dataManager.set(SPEED, (float) flight.speed);
        DroneType type = getDroneType();
        dataManager.set(FUEL_FRACTION, type == null ? 0 : (float) Math.min(1, fuel / type.getBatteryOrFuelCapacity()));
    }

    /**
     * Ran into terrain or something solid. Warheads go off. Every other unit lands intact as an item only when it
     * was actually landing (Return's approach, or a flat, slow belly landing); anything else (a wall, a hillside, an
     * entity, a steep or fast touchdown) goes off with the type's blastPower (a gunship's fuel and ammo, the recon
     * UAV's fuel), or just wrecks it.
     */
    private void onCollision(DroneType type, boolean hitEntity) {
        if (type.isArmed() && type.isImpactDetonates()) {
            detonate(type);
            return;
        }
        // Slow speed alone isn't a landing: a plane cruising at Medium into a hillside must still go up. Only Return's
        // approach (where the roll-out may clip a bump or block edge) or a flat, slow belly touchdown counts.
        boolean returnLanding = landingApproach && flight.speed < LANDING_SPEED_FRACTION * type.getMaxSpeed();
        boolean bellyLanding = onGround && !collidedHorizontally && flight.speed < BELLY_LANDING_SPEED_FRACTION * type.getMaxSpeed()
                && Math.abs(flight.velocity.y) < BELLY_LANDING_SINK_RATE;
        if (!hitEntity && (returnLanding || bellyLanding)) {
            land(type);
        } else {
            detonate(type);
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

    /** Comes down intact: drops back as its item, remaining fuel and ammo and all. */
    private void land(DroneType type) {
        setDead();
        ((WorldServer) world).spawnParticle(EnumParticleTypes.CLOUD, posX, posY + 0.1, posZ, 16, width / 2, 0.05, width / 2, 0.02);
        world.playSound(null, posX, posY, posZ, ModSounds.DRONE_HIT, SoundCategory.NEUTRAL, 1.0F, 0.5F);
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(CombatDrones.MOD_ID, type.getId()));
        if (item != null) {
            entityDropItem(ItemDrone.withAmmo(ItemDrone.withFuel(new ItemStack(item), fuel), getAmmo()), 0.2F);
        }
    }

    @Nullable
    protected EntityLivingBase getBlameTarget() {
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
            } else if (type != null && type.getBlastPower() > 0) {
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

    /** A remote pilot rides along invisibly; centre their hitbox on the airframe. */
    @Override
    public double getMountedYOffset() {
        return height / 2 - 0.55;
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
        compound.setDouble("HomeX", homeX);
        compound.setDouble("HomeZ", homeZ);
        compound.setDouble("Fuel", fuel);
        compound.setFloat("Health", health);
        compound.setInteger("Ammo", getAmmo());
        if (designatedTarget != null) {
            compound.setDouble("DesignatedX", designatedTarget.x);
            compound.setDouble("DesignatedY", designatedTarget.y);
            compound.setDouble("DesignatedZ", designatedTarget.z);
        }
        compound.setDouble("Heading", flight.heading);
        compound.setDouble("Pitch", flight.pitch);
        compound.setDouble("Bank", flight.bank);
        compound.setDouble("Speed", flight.speed);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        dataManager.set(TYPE_ID, DroneTypes.migrateId(compound.getString("Type")));
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        route = RouteCodec.fromNbt(compound.getCompoundTag("Route"), Route.TerminalAction.LOITER);
        waypointIndex = compound.getInteger("WaypointIndex");
        launchY = compound.hasKey("LaunchY") ? compound.getDouble("LaunchY") : posY;
        homeX = compound.hasKey("HomeX") ? compound.getDouble("HomeX") : Double.NaN;
        homeZ = compound.hasKey("HomeZ") ? compound.getDouble("HomeZ") : Double.NaN;
        fuel = compound.getDouble("Fuel");
        setHealth(compound.getFloat("Health"));
        dataManager.set(AMMO, compound.getInteger("Ammo"));
        designatedTarget = compound.hasKey("DesignatedX")
                ? new Vec3d(compound.getDouble("DesignatedX"), compound.getDouble("DesignatedY"), compound.getDouble("DesignatedZ")) : null;
        dataManager.set(STRIKING, designatedTarget != null);
        flight.heading = compound.getDouble("Heading");
        flight.pitch = compound.getDouble("Pitch");
        flight.bank = compound.getDouble("Bank");
        flight.speed = compound.getDouble("Speed");
        flight.position.set(posX, posY, posZ);
    }
}
