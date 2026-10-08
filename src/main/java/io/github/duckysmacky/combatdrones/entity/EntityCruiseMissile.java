package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.EffectPacket;
import io.github.duckysmacky.combatdrones.physics.CruiseMissileFlight;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.Loadout;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import javax.vecmath.Quat4d;
import java.util.UUID;

/**
 * A cruise missile while it's an entity: standing on its pad and climbing after launch, or diving on a strike. In
 * between it only exists in the {@link MissileStore}. Reuses the ramp unit plumbing (client dead reckoning, remote
 * session, nose camera, engine sound, hit effects) with its own flight ({@link CruiseMissileFlight}).
 *
 * <p>Only the dive's impact sets off the warhead. Shot down or crashed at any stage, only its fuel goes up
 * ({@code fuelBlastPower}), so shooting it on its pad doesn't wipe out the shooters.
 */
public class EntityCruiseMissile extends EntityRampUnit {
    /** Longest a dive may last (ticks) before the missile is written off: it can't take this long to fall. */
    private static final int MAX_DIVE_TICKS = 2400;
    /** Movement lost to a collision beyond this (blocks/tick) counts as hitting something. */
    private static final double BLOCKED_THRESHOLD = 0.05;
    private static final double RENDER_DISTANCE = 256.0;
    /** Camera height in the dive: the nose, at the bottom of the upright hitbox. */
    private static final float DIVE_EYE_HEIGHT = 0.3F;

    private static final DataParameter<Integer> STAGE = EntityDataManager.createKey(EntityCruiseMissile.class, DataSerializers.VARINT);
    private static final DataParameter<Float> REF_HEADING = EntityDataManager.createKey(EntityCruiseMissile.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> TILT_FORWARD = EntityDataManager.createKey(EntityCruiseMissile.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> TILT_RIGHT = EntityDataManager.createKey(EntityCruiseMissile.class, DataSerializers.FLOAT);
    /** Ticks left of the hold over the strike point (for the pilot's ARMING countdown). */
    private static final DataParameter<Integer> HOLD_TICKS_LEFT = EntityDataManager.createKey(EntityCruiseMissile.class, DataSerializers.VARINT);
    /** Client: share of the gap to the synced tilts closed per tick (they arrive 0 or 2 per tick, not 1). */
    private static final float TILT_SMOOTHING = 0.5F;

    /** The pad it was launched from: its key in the {@link MissileStore}, and what the steering tablet is linked to. */
    @Nullable
    private UUID padId;
    /** Ticks since ignition (launch), since the hold began, or since the dive began. */
    private int elapsed;
    private final CruiseMissileFlight.Dive dive = new CruiseMissileFlight.Dive();
    private double stickForward;
    private double stickRight;
    private boolean boostHeld;
    private long inputTick = Long.MIN_VALUE;

    // Client: tilts eased toward the synced ones, and last tick's, for smooth interpolation
    private float smoothTiltForward;
    private float smoothTiltRight;
    private float prevTiltForward;
    private float prevTiltRight;

    public EntityCruiseMissile(World world) {
        super(world);
    }

    /** A missile lighting up on its pad: upright, still, on the pad's heading. */
    public EntityCruiseMissile(World world, DroneType type, @Nullable UUID owner, UUID padId, double fuel, double x, double y, double z, float heading) {
        super(world, type, owner, Route.empty(Route.TerminalAction.LOITER), fuel, new Loadout(), x, y, z, heading);
        this.padId = padId;
        flight.pitch = -90;
        flight.speed = 0;
        dataManager.set(STAGE, CruiseMissileFlight.Stage.IGNITION.ordinal());
        syncPresentation();
    }

    /**
     * A missile appearing over its strike point, nose straight down, "up" in its camera along {@code refHeading}. It
     * holds there for {@code strikeHoldSeconds} (armed, aimable, still) before it dives, which gives the server and the
     * pilot's client time to load the terrain round it: diving the moment it appeared made the first seconds stutter.
     */
    public static EntityCruiseMissile strike(World world, DroneType type, @Nullable UUID owner, UUID padId, double x, double y, double z, float refHeading) {
        EntityCruiseMissile missile = new EntityCruiseMissile(world, type, owner, padId, type.getBatteryOrFuelCapacity(), x, y, z, refHeading);
        missile.dataManager.set(STAGE, CruiseMissileFlight.Stage.HOLD.ordinal());
        missile.dive.refHeading = refHeading;
        missile.dive.speed = 0;
        missile.syncDive();
        return missile;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(STAGE, CruiseMissileFlight.Stage.IGNITION.ordinal());
        dataManager.register(REF_HEADING, 0.0F);
        dataManager.register(TILT_FORWARD, 0.0F);
        dataManager.register(TILT_RIGHT, 0.0F);
        dataManager.register(HOLD_TICKS_LEFT, 0);
    }

    public CruiseMissileFlight.Stage getStage() {
        CruiseMissileFlight.Stage[] stages = CruiseMissileFlight.Stage.values();
        int ordinal = dataManager.get(STAGE);
        return ordinal >= 0 && ordinal < stages.length ? stages[ordinal] : CruiseMissileFlight.Stage.IGNITION;
    }

    public boolean isDiving() {
        return getStage() == CruiseMissileFlight.Stage.DIVE;
    }

    /** Over the strike point (holding or diving): nose down, steered by tilt. */
    public boolean isStriking() {
        return getStage().isStrike();
    }

    public int getHoldTicksLeft() {
        return dataManager.get(HOLD_TICKS_LEFT);
    }

    @Nullable
    public UUID getPadId() {
        return padId;
    }

    /** Synced dive tilt away from straight down, in degrees (for the HUD's cone gauge). */
    public float getTiltForward() {
        return dataManager.get(TILT_FORWARD);
    }

    public float getTiltRight() {
        return dataManager.get(TILT_RIGHT);
    }

    @Override
    public void onUpdate() {
        prevTiltForward = smoothTiltForward;
        prevTiltRight = smoothTiltRight;
        smoothTiltForward += (getTiltForward() - smoothTiltForward) * TILT_SMOOTHING;
        smoothTiltRight += (getTiltRight() - smoothTiltRight) * TILT_SMOOTHING;
        super.onUpdate();
    }

    @Override
    protected void serverUpdate(DroneType type) {
        if (padId == null) {
            setDead();
            return;
        }
        if (isDiving()) {
            diveTick(type);
        } else if (isStriking()) {
            holdTick(type);
        } else {
            climbTick(type);
        }
    }

    /** Ignition on the pad, then a straight vertical climb, faster and faster; out of sight at the end. */
    private void climbTick(DroneType type) {
        CruiseMissileFlight.Launch launch = MissileServer.launchSchedule();
        CruiseMissileFlight.Stage stage = launch.stageAt(elapsed);
        if (stage == CruiseMissileFlight.Stage.DONE) {
            MissileStore.get(world).climbed(padId, world.getTotalWorldTime());
            setDead(); // quietly: from here on it only exists in the store
            return;
        }
        if (stage != getStage()) {
            dataManager.set(STAGE, stage.ordinal());
            if (stage == CruiseMissileFlight.Stage.LIFTOFF) {
                EffectPacket.missileLiftoff(world, new Vec3d(posX, posY, posZ));
            }
        }
        if (elapsed == 0) {
            world.playSound(null, posX, posY, posZ, ModSounds.MISSILE_IGNITION, SoundCategory.NEUTRAL, 8.0F, 1.0F);
        }
        double speed = launch.climbSpeed(elapsed);
        elapsed++;
        flight.pitch = -90;
        flight.bank = 0;
        flight.speed = speed;
        if (speed > 0) {
            double startY = posY;
            move(MoverType.SELF, 0, speed, 0);
            if (speed - (posY - startY) > BLOCKED_THRESHOLD) {
                fuelBlast(); // flew into something above the pad
                return;
            }
        }
        flight.position.set(posX, posY, posZ);
        syncPresentation();
        anchor.hold(posX, posZ);
    }

    private static CruiseMissileFlight.DiveParams diveParams(DroneType type) {
        return new CruiseMissileFlight.DiveParams(type.getMaxSpeed(), type.getAcceleration(), ModConfig.missileBoostMultiplier,
                ModConfig.steerConeDegrees, ModConfig.missileSteerRateDegrees);
    }

    private boolean isPiloted() {
        return RemoteSessions.controllerOf(this) != null && world.getTotalWorldTime() - inputTick <= STALE_INPUT_TICKS;
    }

    /** Armed over the strike point: aimable but still. Then the dive starts, and everyone below is warned. */
    private void holdTick(DroneType type) {
        boolean piloted = isPiloted();
        CruiseMissileFlight.steer(dive, piloted ? stickForward : 0, piloted ? stickRight : 0, diveParams(type));
        int holdTicks = (int) Math.round(ModConfig.strikeHoldSeconds * 20);
        elapsed++;
        dataManager.set(HOLD_TICKS_LEFT, Math.max(0, holdTicks - elapsed));
        if (elapsed >= holdTicks) {
            elapsed = 0;
            dive.speed = type.getMaxSpeed();
            dataManager.set(STAGE, CruiseMissileFlight.Stage.DIVE.ordinal());
            BlockPos ground = world.getHeight(new BlockPos(posX, 0, posZ));
            EffectPacket.strikeAlert(world, new Vec3d(posX, ground.getY(), posZ), ModConfig.strikeAlertRadius);
        }
        syncDive();
        anchor.hold(posX, posZ);
    }

    /** The strike: steered (or, without a pilot, held) inside the cone; anything it touches sets off the warhead. */
    private void diveTick(DroneType type) {
        boolean piloted = isPiloted();
        boolean boost = piloted && boostHeld;
        CruiseMissileFlight.step(dive, piloted ? stickForward : 0, piloted ? stickRight : 0, boost, diveParams(type));
        setBoosting(boost);
        double wantedX = dive.velocity.x;
        double wantedY = dive.velocity.y;
        double wantedZ = dive.velocity.z;
        AxisAlignedBB swept = getEntityBoundingBox().expand(wantedX, wantedY, wantedZ);
        double startX = posX;
        double startY = posY;
        double startZ = posZ;
        move(MoverType.SELF, wantedX, wantedY, wantedZ);
        double blocked = Math.sqrt(sq(wantedX - (posX - startX)) + sq(wantedY - (posY - startY)) + sq(wantedZ - (posZ - startZ)));
        syncDive();
        // Water doesn't stop an entity's move; the fuze still goes off on it
        if (blocked > BLOCKED_THRESHOLD || onGround || isInWater() || isInLava() || hitsEntity(swept)) {
            impact(type);
            return;
        }
        if (posY < -64 || ++elapsed > MAX_DIVE_TICKS) {
            setDead();
            MissileStore.get(world).finish(padId, MissileStore.State.LOST);
            return;
        }
        anchor.hold(posX + wantedX * 20, posZ + wantedZ * 20);
    }

    /** Heading, pitch and roll of the dive for clients (dead reckoning, engine sound), plus the exact tilts. */
    private void syncDive() {
        double[] euler = HandFlownFlight.toEuler(CruiseMissileFlight.orientation(dive.refHeading, dive.tiltForward, dive.tiltRight));
        flight.heading = euler[0];
        flight.pitch = euler[1];
        flight.bank = euler[2];
        flight.speed = dive.speed;
        flight.position.set(posX, posY, posZ);
        dataManager.set(REF_HEADING, (float) dive.refHeading);
        dataManager.set(TILT_FORWARD, (float) dive.tiltForward);
        dataManager.set(TILT_RIGHT, (float) dive.tiltRight);
        syncPresentation();
    }

    private void impact(DroneType type) {
        setDead();
        MissileStore.get(world).finish(padId, MissileStore.State.IMPACT);
        // The nose leads: blow up at the bottom of the upright box, where it touched
        DroneExplosion.detonate((WorldServer) world, this, getBlameTarget(), posX, posY + 0.5, posZ, type.getBlastPower(),
                (float) ModConfig.missileShrapnelDamage, (float) ModConfig.missileShrapnelRadius);
    }

    /** Shot down or crashed: just the fuel goes up, never the warhead. */
    private void fuelBlast() {
        setDead();
        if (padId != null) {
            MissileStore.get(world).finish(padId, MissileStore.State.DESTROYED);
        }
        if (ModConfig.fuelBlastPower > 0) {
            DroneExplosion.detonate((WorldServer) world, this, getBlameTarget(), posX, posY + height / 2, posZ, (float) ModConfig.fuelBlastPower);
        } else {
            DamageEffects.wreck(this);
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (isEntityInvulnerable(source) || isDead) {
            return false;
        }
        if (!world.isRemote) {
            setHealth(health - amount * (float) ModConfig.droneDamageTakenMultiplier);
            if (padId != null) {
                MissileStore.get(world).setHealth(padId, getHealthFraction());
            }
            if (health > 0) {
                DamageEffects.hit(this);
            } else {
                fuelBlast();
            }
        }
        return true;
    }

    /** Server: the pilot's steering for the dive (stick forward/right as pitch/roll, boost). Controller only. */
    @Override
    public void acceptManualInput(EntityPlayerMP sender, double pitch, double roll, double throttle, boolean trigger, boolean free, boolean boost) {
        if (RemoteSessions.controllerOf(this) != sender) {
            return;
        }
        stickForward = pitch;
        stickRight = roll;
        boostHeld = boost;
        inputTick = world.getTotalWorldTime();
    }

    /** Click-designate doesn't apply to a missile. */
    @Override
    public void designate(EntityPlayerMP sender, Vec3d direction) {
    }

    @Override
    public void setRoute(Route newRoute) {
    }

    /** Striking: from the (eased) tilts, as yaw is meaningless at straight down. Climbing: upright, like a plane's pitch. */
    @Override
    public Quat4d renderOrientation(float partialTicks) {
        if (!isStriking()) {
            return super.renderOrientation(partialTicks);
        }
        float forward = prevTiltForward + (smoothTiltForward - prevTiltForward) * partialTicks;
        float right = prevTiltRight + (smoothTiltRight - prevTiltRight) * partialTicks;
        return CruiseMissileFlight.orientation(dataManager.get(REF_HEADING), forward, right);
    }

    @Override
    public float getEyeHeight() {
        return isStriking() ? DIVE_EYE_HEIGHT : height * 0.85F;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double distanceSq) {
        return distanceSq < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    private static double sq(double value) {
        return value * value;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        if (padId != null) {
            compound.setUniqueId("Pad", padId);
        }
        compound.setInteger("Stage", dataManager.get(STAGE));
        compound.setInteger("Elapsed", elapsed);
        compound.setDouble("RefHeading", dive.refHeading);
        compound.setDouble("TiltForward", dive.tiltForward);
        compound.setDouble("TiltRight", dive.tiltRight);
        compound.setDouble("DiveSpeed", dive.speed);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        padId = compound.hasUniqueId("Pad") ? compound.getUniqueId("Pad") : null;
        dataManager.set(STAGE, compound.getInteger("Stage"));
        elapsed = compound.getInteger("Elapsed");
        dive.refHeading = compound.getDouble("RefHeading");
        dive.tiltForward = compound.getDouble("TiltForward");
        dive.tiltRight = compound.getDouble("TiltRight");
        dive.speed = compound.getDouble("DiveSpeed");
        if (isStriking()) {
            syncDive();
        }
    }
}
