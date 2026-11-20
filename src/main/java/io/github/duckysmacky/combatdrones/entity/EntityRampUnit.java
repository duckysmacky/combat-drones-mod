package io.github.duckysmacky.combatdrones.entity;

import com.google.common.base.Optional;
import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.LockOnSettings;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.network.GunFirePacket;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.network.RouteCodec;
import io.github.duckysmacky.combatdrones.physics.DownedFall;
import io.github.duckysmacky.combatdrones.physics.DroneSystems;
import io.github.duckysmacky.combatdrones.physics.FixedWingFlight;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.github.duckysmacky.combatdrones.physics.RouteAutopilot;
import io.github.duckysmacky.combatdrones.physics.RunwayOps;
import io.github.duckysmacky.combatdrones.type.AerialBombType;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import io.github.duckysmacky.combatdrones.type.Loadout;
import io.github.duckysmacky.combatdrones.type.RocketType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.MoverType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import javax.vecmath.Quat4d;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A ramp-launched fixed-wing unit in flight: loitering munition, gunship plane or recon UAV, depending on its
 * {@link DroneType}. Flies its {@link Route} on autopilot until fuel runs out; before launch the unit lives inside
 * the Launch Ramp's tile entity instead.
 */
public class EntityRampUnit extends Entity implements ChunkAnchor.Holder, LockOnCarrier {
    /** Blocks the autopilot keeps above the terrain it's about to fly over. */
    private static final double TERRAIN_CLEARANCE = 6;
    /** How far ahead (in ticks of flight) the terrain floor looks. */
    private static final int TERRAIN_LOOKAHEAD_TICKS = 30;
    /** Movement lost to a collision beyond this (blocks/tick) counts as a crash. */
    private static final double CRASH_THRESHOLD = 0.1;
    private static final double RENDER_DISTANCE = 160.0;
    /** Within this distance of its runway a returning plane checks the runway is still there and free. */
    private static final double RUNWAY_CHECK_RANGE = 128;
    /** Manual input older than this (ticks) is treated as a centred stick. */
    protected static final int STALE_INPUT_TICKS = 10;
    private static final int DESIGNATE_COOLDOWN_TICKS = 10;
    /** Farthest crosshair point (blocks from the unit) the gunner's shots are aimed at; the client looks up to 512. */
    public static final double GUNNER_AIM_RANGE = 544;
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
    /** {@link VehicleState}: flying, knocked out by an EMP, or shot down; the last two fall with nobody in control. */
    private static final DataParameter<Byte> STATE = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.BYTE);
    private static final DataParameter<Boolean> STRIKING = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.BOOLEAN);
    // Loadout, synced for the HUD: rounds or rockets left per weapon station, and the rocket size in the pod
    private static final DataParameter<Integer> AMMO = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> AMMO_SECONDARY = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> AMMO_TERTIARY = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> ROCKET_KIND = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> BOMB_KIND = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT);
    /** Weapon station the gunner has selected (gunner scheme). */
    private static final DataParameter<Integer> STATION = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT);
    /**
     * Per weapon station, for the HUD's reload bars: the world tick the station is ready again and how long its last
     * cooldown was. Only changes when a shot is fired, unlike a countdown that would go out every tick.
     */
    private static final List<DataParameter<Integer>> COOLDOWN_READY_TICK = new ArrayList<>();
    private static final List<DataParameter<Integer>> COOLDOWN_LENGTH = new ArrayList<>();

    static {
        for (int station = 0; station < Loadout.STATIONS; station++) {
            COOLDOWN_READY_TICK.add(EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT));
            COOLDOWN_LENGTH.add(EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT));
        }
    }
    private static final DataParameter<Float> HEALTH_FRACTION = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.FLOAT);
    /** Sensor units: lock-on charges, synced for the HUD (see SyncedLockCharges). */
    private static final DataParameter<Integer> LOCK_RELOAD_LENGTH = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> LOCK_MAX = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> LOCK_CHARGES = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> LOCK_READY_TICK = EntityDataManager.createKey(EntityRampUnit.class, DataSerializers.VARINT);
    private static final SyncedLockCharges.Keys LOCK_KEYS = new SyncedLockCharges.Keys(LOCK_MAX, LOCK_CHARGES, LOCK_READY_TICK, LOCK_RELOAD_LENGTH);

    protected final FixedWingFlight.State flight = new FixedWingFlight.State();
    @Nullable
    private UUID owner;
    private Route route = Route.empty(Route.TerminalAction.LOITER);
    private int waypointIndex;
    private double launchY;
    protected double fuel;
    protected float health;
    /** The runway it took off from: where it goes back to land. Null for units summoned without one. */
    @Nullable
    private Route.Runway homeRunway;
    /** On the runway's deck: rolling for takeoff (no flight model, no collisions), or flying. */
    private boolean takingOff;
    private int takeoffTicks;
    /** Just lifted off: climbing straight ahead ({@code runways.climbOutHeight}) before turning onto the route. */
    private boolean climbingOut;
    /** "Return to runway": the route set aside (kept, Apply brings it back) and only landing. */
    private boolean recalling;
    /** Landed: rolling out on the runway it's landing on, braking to a stop, then parked on it. */
    @Nullable
    private Route.Runway rollingOutOn;
    /** Its runway is there and free to land on, as last checked (unknown counts as free until it's close). */
    private boolean runwayFree = true;
    // Manual control ("Take control"): the controller's latest input, and when it arrived
    private double manualPitch;
    private double manualRoll;
    private double manualThrottle;
    private boolean manualTrigger;
    private boolean manualFree;
    private boolean manualBoost;
    /** Bomber: the pilot is at the bombsight, so the plane holds the heading and height it had when they sat down. */
    private boolean manualSight;
    private boolean sightHolding;
    /** Heading change per tick at full A/D while at the bombsight (the plane's own turn rate still limits it). */
    private static final double SIGHT_STEER_DEG_PER_TICK = 1.0;
    private double sightHeading;
    private double sightAltitude;
    private long manualInputTick = Long.MIN_VALUE;
    // Gunner seat (gunner scheme): where the camera aims and whether the trigger is held, and when that arrived
    private Vec3d gunnerAim = Vec3d.ZERO;
    /** What the gunner's crosshair was on, if anything (the camera ray's first hit), so shots converge on it. */
    @Nullable
    private Vec3d gunnerTarget;
    private boolean gunnerTrigger;
    private long gunnerInputTick = Long.MIN_VALUE;
    /** A point picked from the camera (click-designate scheme): overrides the route with a strike. */
    @Nullable
    private Vec3d designatedTarget;
    /** Just shot down: world time until which further damage is ignored (not saved: a restart only shortens it). */
    private long graceUntil;
    /** Ticks left EMP-disabled. */
    private int empTicks;
    private int designateCooldown;
    /** Sensor units: lock-on charges. */
    private final SyncedLockCharges lockCharges = new SyncedLockCharges(this, LOCK_KEYS);
    /** Per station, ticks until it can fire again; fractional so fire rates aren't limited to 20 / whole ticks. */
    private final double[] weaponCooldown = new double[Loadout.STATIONS];
    /** This tick the autopilot was flying Return's final landing approach. */
    private boolean landingApproach;
    // Carpet run (bomber on autopilot): blocks still to fly straight on, the course and waypoint it is flown by, and
    // ticks until the next bomb. shortcut: not saved, a run under way ends at a server restart; save it if that matters.
    private double bombRun;
    private double bombRunHeading;
    private Route.Waypoint bombRunWaypoint;
    private double bombRunTimer;
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
    public EntityRampUnit(World world, DroneType type, @Nullable UUID owner, Route route, double fuel, Loadout loadout, double x, double y, double z, float heading) {
        this(world);
        setLoadout(loadout);
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

    /** Server, before spawning: start on {@code runway}'s deck, standing, and roll out from there. */
    void startTakeoff(Route.Runway runway) {
        homeRunway = runway;
        takingOff = true;
        takeoffTicks = 0;
        flight.speed = 0;
        flight.pitch = 0;
        syncPresentation();
    }

    @Override
    protected void entityInit() {
        dataManager.register(TYPE_ID, "");
        dataManager.register(BANK, 0.0F);
        dataManager.register(FUEL_FRACTION, 1.0F);
        dataManager.register(HEALTH_FRACTION, 1.0F);
        LOCK_KEYS.register(dataManager);
        dataManager.register(AMMO, 0);
        dataManager.register(AMMO_SECONDARY, 0);
        dataManager.register(AMMO_TERTIARY, 0);
        dataManager.register(ROCKET_KIND, 0);
        dataManager.register(BOMB_KIND, 0);
        dataManager.register(STATION, 0);
        for (int station = 0; station < Loadout.STATIONS; station++) {
            dataManager.register(COOLDOWN_READY_TICK.get(station), 0);
            dataManager.register(COOLDOWN_LENGTH.get(station), 0);
        }
        dataManager.register(STRIKING, false);
        dataManager.register(STRIKE_POINT, Optional.absent());
        dataManager.register(BOOSTING, false);
        dataManager.register(STATE, (byte) 0);
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

    /** Rockets and rounds left (synced for the HUD). A copy: change it through {@link #setLoadout}. */
    public Loadout getLoadout() {
        Loadout loadout = new Loadout();
        loadout.setAmmo(0, dataManager.get(AMMO));
        loadout.setAmmo(1, dataManager.get(AMMO_SECONDARY));
        loadout.setAmmo(2, dataManager.get(AMMO_TERTIARY));
        loadout.setRocketKind(RocketType.byIndex(dataManager.get(ROCKET_KIND)));
        loadout.setBombKind(AerialBombType.byIndex(dataManager.get(BOMB_KIND)));
        return loadout;
    }

    /** Weapon station the gunner has selected (synced for the HUD). */
    public int getSelectedStation() {
        return dataManager.get(STATION);
    }

    private void setLoadout(Loadout loadout) {
        dataManager.set(AMMO, loadout.getAmmo(0));
        dataManager.set(AMMO_SECONDARY, loadout.getAmmo(1));
        dataManager.set(AMMO_TERTIARY, loadout.getAmmo(2));
        dataManager.set(ROCKET_KIND, loadout.getRocketKind().ordinal());
        dataManager.set(BOMB_KIND, loadout.getBombKind().ordinal());
    }

    public float getHealthFraction() {
        return dataManager.get(HEALTH_FRACTION);
    }

    /** Server: replace the route in flight (Mission Planner "Apply"). The unit heads for the new first waypoint. */
    public void setRoute(Route newRoute) {
        route = newRoute;
        recalling = false;
        waypointIndex = 0;
        bombRun = 0;
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
            if (isEmpDisabled() && rand.nextInt(3) == 0) {
                Emp.crackle(this);
            }
            if (isShotDown()) {
                DamageEffects.burningTrail(this);
            }
            if (clientType != null && isEngineRunning()) {
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
        if (takingOff && !isDisabled()) {
            takeoffRoll(type);
            return;
        }
        if (rollingOutOn != null && !isDisabled()) {
            rollOut(type);
            return;
        }
        if (ticksExisted % 20 == 0) {
            runwayFree = checkRunway(type);
        }
        if (downedFall(type)) {
            return;
        }
        boolean boosting = isBoosting(type);
        dataManager.set(BOOSTING, boosting);
        if (type.isSensor()) {
            lockCharges.tick(ModConfig.uavLockOn);
        }
        FixedWingFlight.Params params = boosting ? paramsOf(type).boosted(ModConfig.boostSpeedMultiplier) : paramsOf(type);
        if (designateCooldown > 0) {
            designateCooldown--;
        }
        for (int station = 0; station < Loadout.STATIONS; station++) {
            if (weaponCooldown[station] > 0) {
                weaponCooldown[station] -= 1; // may dip just below 0; the next shot keeps that remainder
            }
        }
        // The entity position is the truth (picks up /tp and positions set after NBT load)
        flight.position.set(posX, posY, posZ);
        FixedWingFlight.Controls controls = manualControls(type, params, boosting);
        boolean handFlown = controls != null;
        gunnerFire(type);
        landingApproach = false;
        Vec3d strikePoint = null;
        if (controls == null && designatedTarget != null) {
            controls = RouteAutopilot.pursue(flight, designatedTarget.x, designatedTarget.y, designatedTarget.z, terrainFloor(), params, fuel > 0);
            strikePoint = designatedTarget;
        }
        if (controls != null) {
            bombRun = 0; // hands on the controls end a carpet run
        } else if (bombRun > 0) {
            controls = flyBombRun(type, params);
        }
        if (climbingOut && homeRunway != null) {
            double climbOutY = homeRunway.y + ModConfig.runwayClimbOutHeight;
            climbingOut = posY < climbOutY;
            if (controls == null && climbingOut) {
                controls = RouteAutopilot.holdCourse(flight, homeRunway.heading, climbOutY + 2, Route.Speed.FAST.fraction, params, fuel > 0);
            }
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
        // Down onto its runway: on the autopilot's landing approach, or flown in by hand
        Route.Runway runway = landingRunway();
        boolean landing = landingApproach || (handFlown && !climbingOut);
        if (landing && runway != null && runwayFree && !type.isImpactDetonates() && RunwayOps.captured(flight, runway, type.getMaxSpeed())) {
            rollingOutOn = runway; // whoever is flying it watches it roll out; the controls no longer do anything
            rollOut(type);
            return;
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
        // Only warheads care about entities: a plane that merely clips a player or drone must not blow up
        boolean hitEntity = type.isImpactDetonates() && hitsEntity(swept);
        if (blocked > CRASH_THRESHOLD || onGround || hitEntity) {
            onCollision(type);
            return;
        }
        ReconIntel.sweep(this, type);
        anchor.hold(posX + flight.velocity.x * 20, posZ + flight.velocity.z * 20);
    }

    /**
     * Server tick of the takeoff run: spool up, roll down the centreline (moved directly: the deck was checked clear
     * when the runway went down), and lift off into the climb-out. Nobody's input counts until it's airborne.
     */
    private void takeoffRoll(DroneType type) {
        flight.position.set(posX, posY, posZ);
        boolean airborne = RunwayOps.takeoffRoll(flight, homeRunway, type.getMaxSpeed(), takeoffTicks++,
                (int) Math.round(ModConfig.runwaySpoolUpSeconds * 20), ModConfig.runwayLiftoffSpeed);
        fuel = Math.max(0, fuel - DroneSystems.drainPerTick(type.getDrainRatePerTick(), 1));
        setPosition(flight.position.x, flight.position.y, flight.position.z);
        syncPresentation();
        anchor.hold(posX + flight.velocity.x * 20, posZ + flight.velocity.z * 20);
        if (airborne) {
            takingOff = false;
            climbingOut = true;
        }
    }

    /** On the ground at the start of its takeoff run. */
    public boolean isTakingOff() {
        return takingOff;
    }

    /** Where Return lands: the runway the route names, else the one it took off from. Null if it has neither. */
    @Nullable
    public Route.Runway landingRunway() {
        return route.getRunway() != null ? route.getRunway() : homeRunway;
    }

    /** The runway it took off from, or null. */
    @Nullable
    public Route.Runway getHomeRunway() {
        return homeRunway;
    }

    /** "Return to runway" was pressed: flying back to land, the route set aside until the next Apply. */
    public boolean isRecalling() {
        return recalling;
    }

    /** Server: sets the route aside and heads back to land. False (nothing changes) if it has no runway. */
    public boolean recall() {
        if (landingRunway() == null || getDroneType() == null || getDroneType().isImpactDetonates()) {
            return false;
        }
        recalling = true;
        bombRun = 0;
        designatedTarget = null;
        dataManager.set(STRIKING, false);
        return true;
    }

    /**
     * Is its runway there and free? Only judged close by, where a missing runway means it's gone (the runway's chunk
     * is loaded); far off an unknown runway counts as free.
     */
    private boolean checkRunway(DroneType type) {
        Route.Runway runway = landingRunway();
        if (runway == null || Math.hypot(runway.x - posX, runway.z - posZ) > RUNWAY_CHECK_RANGE) {
            return true;
        }
        BlockPos threshold = new BlockPos(runway.x, runway.y, runway.z);
        if (!world.isBlockLoaded(threshold)) {
            return true;
        }
        Entity entity = ((WorldServer) world).getEntityFromUuid(runway.id);
        // A runway knocked out by an EMP can't take it in yet: circle until it comes back
        return entity instanceof EntityRunway && ((EntityRunway) entity).canTake(type, owner) && !((EntityRunway) entity).isEmpDisabled();
    }

    /** Server tick of the roll-out after a landing; once stopped it's parked on the runway (or wrecked if it's gone). */
    private void rollOut(DroneType type) {
        flight.position.set(posX, posY, posZ);
        boolean stopped = RunwayOps.rollout(flight, rollingOutOn);
        setPosition(flight.position.x, flight.position.y, flight.position.z);
        syncPresentation();
        if (!stopped) {
            return;
        }
        Entity entity = ((WorldServer) world).getEntityFromUuid(rollingOutOn.id);
        if (entity instanceof EntityRunway && ((EntityRunway) entity).canTake(type, owner)) {
            ((EntityRunway) entity).dock(type, this);
            setDead();
        } else {
            detonate(type);
        }
    }

    /** The route (and its terminal action) on autopilot, or straight back to land when recalled. */
    private RouteAutopilot.Command autopilotCommand(DroneType type, FixedWingFlight.Params params) {
        RouteAutopilot.Situation situation = new RouteAutopilot.Situation(launchY, terrainFloor(), fuel > 0,
                fuel / type.getBatteryOrFuelCapacity(), ModConfig.lowFuelFraction, type.isArmed() && type.isImpactDetonates(),
                strikeGroundY(), landingRunway(), runwayFree);
        if (recalling) {
            return RouteAutopilot.returnToRunway(flight, situation, params, waypointIndex);
        }
        RouteAutopilot.Command command = RouteAutopilot.guide(flight, route, waypointIndex, situation, params);
        if (command.waypointIndex > waypointIndex && waypointIndex < route.size()) {
            passDropPoint(type, waypointIndex);
        }
        waypointIndex = command.waypointIndex;
        return command;
    }

    /**
     * The autopilot just passed waypoint {@code index}: a bomber lets one bomb go there, or starts a carpet run along
     * the leg it arrived on (its own heading for the first waypoint).
     */
    private void passDropPoint(DroneType type, int index) {
        Route.Waypoint waypoint = route.get(index);
        if (!waypoint.isDrop() || type.stationOf(DroneType.WeaponType.BOMB_BAY) < 0) {
            return;
        }
        if (waypoint.dropLength == 0) {
            dropBomb(type);
            return;
        }
        bombRun = waypoint.dropLength;
        bombRunWaypoint = waypoint;
        bombRunTimer = 0;
        bombRunHeading = flight.heading;
        if (index > 0) {
            Route.Waypoint from = route.get(index - 1);
            bombRunHeading = Math.toDegrees(Math.atan2(-(waypoint.x - from.x), waypoint.z - from.z));
        }
    }

    /** One tick of a carpet run: straight on at the drop point's height and speed, a bomb every carpet interval. */
    private FixedWingFlight.Controls flyBombRun(DroneType type, FixedWingFlight.Params params) {
        bombRun -= flight.speed;
        if (--bombRunTimer <= 0) {
            if (!dropBomb(type)) {
                bombRun = 0; // bay empty
            }
            bombRunTimer += getLoadout().getBombKind().getCarpetIntervalTicks();
        }
        return RouteAutopilot.holdCourse(flight, bombRunHeading, Math.max(launchY + bombRunWaypoint.altitude, terrainFloor()),
                bombRunWaypoint.speed.fraction, params, fuel > 0);
    }

    /** Releases one bomb from the bay, whatever its hand-drop cooldown says; false when there's no bay or it's empty. */
    private boolean dropBomb(DroneType type) {
        int station = type.stationOf(DroneType.WeaponType.BOMB_BAY);
        Loadout loadout = getLoadout();
        if (station < 0 || loadout.getAmmo(station) <= 0) {
            return false;
        }
        loadout.setAmmo(station, loadout.getAmmo(station) - 1);
        setLoadout(loadout);
        releaseBomb(loadout.getBombKind());
        return true;
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
            sightHolding = false;
            return null;
        }
        // Input stopped arriving (lag spike): centre the stick, keep the throttle
        if (world.getTotalWorldTime() - manualInputTick > STALE_INPUT_TICKS) {
            manualPitch = 0;
            manualRoll = 0;
            manualTrigger = false;
        }
        if (manualTrigger) {
            fireWeapon(type, 0, Vec3d.fromPitchYaw(rotationPitch, rotationYaw), true);
        }
        if (manualSight && type.stationOf(DroneType.WeaponType.BOMB_BAY) >= 0) {
            if (!sightHolding) {
                sightHolding = true;
                sightHeading = flight.heading;
                sightAltitude = posY;
            }
            // A/D at the bombsight turn the held heading (roll input: positive = right, heading grows clockwise)
            sightHeading = MathHelper.wrapDegrees(sightHeading + manualRoll * SIGHT_STEER_DEG_PER_TICK);
            return RouteAutopilot.holdHeading(flight, sightHeading, Math.max(sightAltitude, terrainFloor()), manualThrottle, fuel > 0);
        }
        sightHolding = false;
        double throttle = boosting ? 1 : manualThrottle;
        return manualFree ? FixedWingFlight.fromRates(flight, params, manualPitch, manualRoll, throttle, fuel > 0)
                : FixedWingFlight.fromStick(manualPitch, manualRoll, throttle, fuel > 0);
    }

    /**
     * Gunner seat: fires the selected station at the crosshair while the autopilot flies on. Fresh input only. Aimed
     * at the point the crosshair was on, from where the unit is now: the client's camera saw the unit a tick or two
     * earlier, so firing along its direction alone would land that far off, toward the direction of flight.
     */
    private void gunnerFire(DroneType type) {
        if (type.getControlScheme() != DroneType.ControlScheme.GUNNER || !gunnerTrigger || RemoteSessions.controllerOf(this) == null
                || world.getTotalWorldTime() - gunnerInputTick > STALE_INPUT_TICKS) {
            return;
        }
        Vec3d aim = gunnerAim;
        if (gunnerTarget != null) {
            Vec3d toTarget = gunnerTarget.subtract(posX, posY + height / 2, posZ);
            if (toTarget.lengthSquared() > width * width) {
                aim = toTarget.normalize();
            }
        }
        fireWeapon(type, getSelectedStation(), aim, false);
    }

    /**
     * Fires one rocket or round from {@code station} along {@code aim} (a unit vector), if the weapon is ready and
     * there's ammo. Manual control only. {@code inheritVelocity}: projectiles also carry the plane's own velocity,
     * right when firing along the nose; the gunner fires across the flight path, where it would carry every shot
     * off to the side (12 blocks over 100 for a cannon shell from a cruising gunship).
     */
    private void fireWeapon(DroneType type, int station, Vec3d aim, boolean inheritVelocity) {
        Loadout loadout = getLoadout();
        DroneType.WeaponType weapon = type.getWeapon(station);
        if (weapon == DroneType.WeaponType.NONE || weaponCooldown[station] > 0 || loadout.getAmmo(station) <= 0) {
            return;
        }
        loadout.setAmmo(station, loadout.getAmmo(station) - 1);
        setLoadout(loadout);
        if (weapon == DroneType.WeaponType.BOMB_BAY) {
            startCooldown(station, loadout.getBombKind().getCooldownTicks());
            releaseBomb(loadout.getBombKind());
            return;
        }
        Vec3d muzzle = new Vec3d(posX, posY + height / 2, posZ).add(aim.scale(Math.max(1.2, width)));
        Vec3d carried = inheritVelocity ? new Vec3d(flight.velocity.x, flight.velocity.y, flight.velocity.z) : Vec3d.ZERO;
        if (weapon == DroneType.WeaponType.ROCKET_POD) {
            startCooldown(station, type.getRocketCooldownTicks() > 0 ? type.getRocketCooldownTicks() : ModConfig.rocketCooldownTicks);
            RocketType kind = loadout.getRocketKind();
            Vec3d velocity = aim.scale(ModConfig.rocketSpeed).add(carried);
            world.spawnEntity(new EntityRocket(world, this, owner, muzzle, velocity,
                    kind == RocketType.HEAVY ? EntityRocket.Kind.HEAVY_ROCKET : EntityRocket.Kind.ROCKET, (float) kind.getBlastPower(), 0));
            world.playSound(null, posX, posY, posZ, ModSounds.GUNSHIP_ROCKET, SoundCategory.NEUTRAL, 4.5F, kind == RocketType.HEAVY ? 0.8F : 1.0F);
        } else if (weapon == DroneType.WeaponType.CANNON) {
            startCooldown(station, ModConfig.cannonCooldownTicks);
            Vec3d velocity = spread(aim, ModConfig.cannonSpread).scale(ModConfig.cannonSpeed).add(carried);
            world.spawnEntity(new EntityRocket(world, this, owner, muzzle, velocity, EntityRocket.Kind.CANNON_SHELL,
                    (float) ModConfig.cannonBlastPower, (float) ModConfig.cannonDamage));
            EffectPacket.gunShot(world, getPositionVector(), false);
        } else if (weapon == DroneType.WeaponType.HOWITZER) {
            startCooldown(station, ModConfig.howitzerCooldownTicks);
            Vec3d velocity = spread(aim, ModConfig.howitzerSpread).scale(ModConfig.howitzerSpeed).add(carried);
            world.spawnEntity(new EntityRocket(world, this, owner, muzzle, velocity, EntityRocket.Kind.HOWITZER_SHELL,
                    (float) ModConfig.howitzerBlastPower, (float) ModConfig.howitzerDamage));
            EffectPacket.gunShot(world, getPositionVector(), true);
        } else {
            startCooldown(station, ModConfig.gunCooldownTicks);
            fireRound(muzzle, aim);
        }
    }

    /** A bomb leaves the bay: out under the belly, falling with the plane's own velocity. */
    private void releaseBomb(AerialBombType kind) {
        world.spawnEntity(new EntityBomb(world, kind, posX, posY - 0.6, posZ, flight.velocity.x, flight.velocity.y, flight.velocity.z, owner, this));
        world.playSound(null, posX, posY, posZ, ModSounds.BOMBER_RELEASE, SoundCategory.NEUTRAL, 3.0F, 0.95F + rand.nextFloat() * 0.1F);
    }

    private void startCooldown(int station, double ticks) {
        weaponCooldown[station] += ticks;
        int length = (int) Math.ceil(weaponCooldown[station]);
        dataManager.set(COOLDOWN_LENGTH.get(station), length);
        dataManager.set(COOLDOWN_READY_TICK.get(station), (int) world.getTotalWorldTime() + length);
    }

    /** Client: share of {@code station}'s cooldown still to go (0 = ready), for the HUD. */
    public float getCooldownLeft(int station) {
        int length = dataManager.get(COOLDOWN_LENGTH.get(station));
        int remaining = dataManager.get(COOLDOWN_READY_TICK.get(station)) - (int) world.getTotalWorldTime();
        return length <= 0 || remaining <= 0 ? 0 : Math.min(1, remaining / (float) length);
    }

    /** Client: the length of {@code station}'s last cooldown, in ticks. */
    public int getCooldownLength(int station) {
        return dataManager.get(COOLDOWN_LENGTH.get(station));
    }

    /** Gun mount: hitscan along {@code aim} with a little spread; nearby players see a tracer. */
    private void fireRound(Vec3d muzzle, Vec3d aim) {
        Vec3d end = muzzle.add(spread(aim, ModConfig.gunSpread).scale(ModConfig.gunRange));
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

    /** {@code aim} turned by a random aim error with a standard deviation of half {@code degrees}. */
    private Vec3d spread(Vec3d aim, double degrees) {
        float pitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(aim.y, -1, 1)));
        float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
        return Vec3d.fromPitchYaw(pitch + (float) (rand.nextGaussian() * degrees / 2), yaw + (float) (rand.nextGaussian() * degrees / 2));
    }

    /** Server: latest manual input from the network. Only the current controller's input counts; values arrive clamped. */
    public void acceptManualInput(EntityPlayerMP sender, double pitch, double roll, double throttle, boolean trigger, boolean free, boolean boost,
                                  boolean sight) {
        if (RemoteSessions.controllerOf(this) != sender) {
            return;
        }
        manualPitch = pitch;
        manualRoll = roll;
        manualThrottle = throttle;
        manualTrigger = trigger;
        manualFree = free;
        manualBoost = boost;
        manualSight = sight;
        manualInputTick = world.getTotalWorldTime();
    }

    /**
     * Server: latest gunner-seat input. Only the current controller of a gunner-scheme unit counts; the aim must be a
     * real direction, the crosshair point (null: on nothing, e.g. the sky) a real point within aiming range, and a
     * station without a weapon falls back to the primary one.
     */
    public void acceptGunnerInput(EntityPlayerMP sender, Vec3d aim, @Nullable Vec3d target, boolean trigger, int station) {
        DroneType type = getDroneType();
        double length = aim.length();
        if (RemoteSessions.controllerOf(this) != sender || type == null || type.getControlScheme() != DroneType.ControlScheme.GUNNER
                || !(length > 1e-3) || !Double.isFinite(length)) {
            return;
        }
        gunnerAim = aim.scale(1 / length);
        boolean targetValid = target != null && Double.isFinite(target.x) && Double.isFinite(target.y) && Double.isFinite(target.z)
                && target.squareDistanceTo(posX, posY, posZ) <= GUNNER_AIM_RANGE * GUNNER_AIM_RANGE;
        gunnerTarget = targetValid ? target : null;
        gunnerTrigger = trigger;
        gunnerInputTick = world.getTotalWorldTime();
        boolean validStation = station >= 0 && station < Loadout.STATIONS && type.getWeapon(station) != DroneType.WeaponType.NONE;
        dataManager.set(STATION, validStation ? station : 0);
    }

    /**
     * Server: click-designate. Raytraces from the unit along the controller's camera direction (up to
     * designateRange) and strikes whatever block or entity it hits first; the point is fixed once picked.
     */
    public void designate(EntityPlayerMP sender, Vec3d direction) {
        DroneType type = getDroneType();
        if (RemoteSessions.controllerOf(this) != sender || type == null || type.getControlScheme() != DroneType.ControlScheme.CLICK_DESIGNATE
                || isDisabled() || designateCooldown > 0 || !(direction.lengthSquared() > 1e-6) || !Double.isFinite(direction.lengthSquared())) {
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
     * Ran into terrain or something solid: it goes off with the type's blastPower (a warhead, or a plane's fuel and
     * ammo), or just wrecks. Planes only come down intact on a runway, which catches them before they touch anything.
     */
    private void onCollision(DroneType type) {
        detonate(type);
    }

    private void detonate(DroneType type) {
        setDead();
        if (type.getBlastPower() > 0) {
            DroneExplosion.detonate((WorldServer) world, this, getBlameTarget(), posX, posY + height / 2, posZ, type.getBlastPower());
        } else {
            DamageEffects.wreck(this);
        }
    }

    public VehicleState getState() {
        return VehicleState.byIndex(dataManager.get(STATE));
    }

    private void setState(VehicleState state) {
        dataManager.set(STATE, (byte) state.ordinal());
    }

    public boolean isEmpDisabled() {
        return getState() == VehicleState.EMPED;
    }

    public boolean isShotDown() {
        return getState() == VehicleState.SHOT_DOWN;
    }

    /** EMP-disabled or shot down: falling, with nobody in control. */
    @Override
    public boolean isDisabled() {
        return getState() != VehicleState.FLYING;
    }

    /** Why it can't be flown, watched or re-routed right now (translation key), while {@link #isDisabled}. */
    public String disabledMessage() {
        return isShotDown() ? "message.combat_drones.shot_down" : "message.combat_drones.emp.disabled";
    }

    /** Engine running: fuel in the tank, not knocked out by an EMP and not shot down. */
    public boolean isEngineRunning() {
        return getFuelFraction() > 0 && !isDisabled();
    }

    /** Server: an EMP knocks it out for good: only a small blast, no warhead or fuel going off. */
    void empDestroy(float fizzlePower) {
        setDead();
        Emp.fizzle(this, getBlameTarget(), fizzlePower);
    }

    /**
     * Server: an EMP disables it for {@code ticks} (topped up while it stays in the field): its motors cut out and it
     * glides down with nobody in control; whoever flies or watches it stays on. Hitting anything meanwhile destroys it
     * with only {@code [state.emped] crashBlastPower}.
     */
    void empDisable(int ticks) {
        if (isShotDown()) {
            return; // going down for good anyway
        }
        if (!isEmpDisabled()) {
            flight.velocity.set(posX - prevPosX, posY - prevPosY, posZ - prevPosZ);
            setState(VehicleState.EMPED);
            dataManager.set(BOOSTING, false);
        }
        empTicks = Math.max(empTicks, ticks);
    }

    /** Server: hit something while EMP-disabled: only the small crash blast. */
    protected void empCrash(DroneType type) {
        empDestroy((float) ModConfig.empedCrashBlastPower);
    }

    /**
     * Server tick while EMP-disabled or shot down: no control, forward speed bleeds away and it drops in an arc, nose
     * following the fall; shot down it also rolls and spirals. Returns false when it's flying (the normal tick runs).
     * When an EMP wears off it flies on from wherever it is. Hitting anything meanwhile: a shot-down unit explodes as
     * if flown into the ground, an EMP-disabled one as {@link #empCrash} says.
     */
    protected boolean downedFall(DroneType type) {
        VehicleState state = getState();
        if (state == VehicleState.FLYING) {
            return false;
        }
        if (state == VehicleState.EMPED && --empTicks <= 0) {
            empTicks = 0;
            setState(VehicleState.FLYING);
        }
        DownedFall.Attitude attitude = DownedFall.step(flight.velocity, flight.heading, flight.bank,
                state == VehicleState.SHOT_DOWN ? ModConfig.shotDownFall() : ModConfig.empedFall());
        double wantedX = flight.velocity.x;
        double wantedY = flight.velocity.y;
        double wantedZ = flight.velocity.z;
        AxisAlignedBB swept = getEntityBoundingBox().expand(wantedX, wantedY, wantedZ);
        double startX = posX;
        double startY = posY;
        double startZ = posZ;
        move(MoverType.SELF, wantedX, wantedY, wantedZ);
        double blocked = Math.sqrt(sq(wantedX - (posX - startX)) + sq(wantedY - (posY - startY)) + sq(wantedZ - (posZ - startZ)));
        if (blocked > CRASH_THRESHOLD || onGround || isInWater() || (type.isImpactDetonates() && hitsEntity(swept))) {
            if (state == VehicleState.SHOT_DOWN) {
                detonate(type);
            } else {
                empCrash(type);
            }
            return true;
        }
        flight.position.set(posX, posY, posZ);
        flight.heading = attitude.heading;
        flight.pitch = attitude.pitch;
        flight.bank = attitude.bank;
        flight.speed = attitude.speed;
        syncPresentation();
        anchor.hold(posX, posZ);
        return true;
    }

    @Nullable
    protected EntityLivingBase getBlameTarget() {
        return owner != null ? world.getPlayerEntityByUUID(owner) : null;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (isEntityInvulnerable(source) || isDead || (isShotDown() && world.getTotalWorldTime() < graceUntil)) {
            return false;
        }
        if (!world.isRemote) {
            setHealth(health - amount * (float) ModConfig.droneDamageTakenMultiplier);
            if (health > 0) {
                DamageEffects.hit(this);
            } else {
                shotDown();
            }
        }
        return true;
    }

    /**
     * Server: knocked out of the sky. Where {@code [state.shot_down]} allows it for its kind, it first goes down
     * burning (also from an EMP's fall); otherwise, or shot again on the way down, it goes off in the air with its
     * blastPower (only the EMP blast if an EMP has killed it), or wrecks without one.
     */
    void shotDown() {
        DroneType type = getDroneType();
        if (type != null && !isShotDown() && ModConfig.isShotDownEnabled(type.getKind())) {
            goDown(type);
            return;
        }
        if (isEmpDisabled()) {
            empDestroy((float) ModConfig.empedCrashBlastPower);
            return;
        }
        if (type != null && type.getBlastPower() > 0) {
            detonate(type);
        } else {
            setDead();
            DamageEffects.wreck(this);
        }
    }

    /** Server: shot down: out of control, burning, falling for good with {@code fallingHP} left; whoever rides it stays on. */
    private void goDown(DroneType type) {
        if (!isEmpDisabled()) {
            flight.velocity.set(posX - prevPosX, posY - prevPosY, posZ - prevPosZ);
        }
        setState(VehicleState.SHOT_DOWN);
        empTicks = 0;
        setHealth((float) ModConfig.shotDownFallingHP);
        graceUntil = world.getTotalWorldTime() + Math.round(ModConfig.shotDownGraceSeconds * 20);
        bombRun = 0;
        designatedTarget = null;
        dataManager.set(STRIKING, false);
        dataManager.set(STRIKE_POINT, Optional.absent());
        dataManager.set(BOOSTING, false);
        DamageEffects.goingDown(this);
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
    public boolean spendLockCharge() {
        return lockCharges.spend();
    }

    @Override
    public LockOnSettings getLockOnSettings() {
        return ModConfig.uavLockOn;
    }

    @Override
    public int getLockMaxCharges() {
        return lockCharges.max();
    }

    @Override
    public int getLockCharges() {
        return lockCharges.left();
    }

    @Override
    public int getLockReloadLeft() {
        return lockCharges.reloadLeft();
    }

    @Override
    public float getLockReloadFraction() {
        return lockCharges.reloadFraction();
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setString("Type", dataManager.get(TYPE_ID));
        compound.setInteger("EmpTicks", empTicks);
        compound.setBoolean("ShotDown", isShotDown());
        compound.setDouble("VelocityX", flight.velocity.x);
        compound.setDouble("VelocityY", flight.velocity.y);
        compound.setDouble("VelocityZ", flight.velocity.z);
        if (owner != null) {
            compound.setUniqueId("Owner", owner);
        }
        compound.setTag("Route", RouteCodec.toNbt(route));
        compound.setInteger("WaypointIndex", waypointIndex);
        compound.setDouble("LaunchY", launchY);
        if (homeRunway != null) {
            compound.setTag("HomeRunway", RouteCodec.runwayToNbt(homeRunway));
        }
        compound.setBoolean("TakingOff", takingOff);
        compound.setInteger("TakeoffTicks", takeoffTicks);
        compound.setBoolean("ClimbingOut", climbingOut);
        compound.setBoolean("Recalling", recalling);
        if (rollingOutOn != null) {
            compound.setTag("RollingOutOn", RouteCodec.runwayToNbt(rollingOutOn));
        }
        compound.setDouble("Fuel", fuel);
        compound.setFloat("Health", health);
        lockCharges.write(compound);
        ItemDrone.writeLoadout(compound, getLoadout());
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
        empTicks = compound.getInteger("EmpTicks");
        setState(compound.getBoolean("ShotDown") ? VehicleState.SHOT_DOWN : empTicks > 0 ? VehicleState.EMPED : VehicleState.FLYING);
        flight.velocity.set(compound.getDouble("VelocityX"), compound.getDouble("VelocityY"), compound.getDouble("VelocityZ"));
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        route = RouteCodec.fromNbt(compound.getCompoundTag("Route"), Route.TerminalAction.LOITER);
        waypointIndex = compound.getInteger("WaypointIndex");
        launchY = compound.hasKey("LaunchY") ? compound.getDouble("LaunchY") : posY;
        homeRunway = RouteCodec.runwayFromNbt(compound.getCompoundTag("HomeRunway"));
        takingOff = homeRunway != null && compound.getBoolean("TakingOff");
        takeoffTicks = compound.getInteger("TakeoffTicks");
        climbingOut = compound.getBoolean("ClimbingOut");
        recalling = compound.getBoolean("Recalling");
        rollingOutOn = RouteCodec.runwayFromNbt(compound.getCompoundTag("RollingOutOn"));
        fuel = compound.getDouble("Fuel");
        setHealth(compound.getFloat("Health"));
        lockCharges.read(compound);
        setLoadout(ItemDrone.readLoadout(compound));
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
