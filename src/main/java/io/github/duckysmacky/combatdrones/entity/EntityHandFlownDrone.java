package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemBattery;
import io.github.duckysmacky.combatdrones.item.ItemBomb;
import io.github.duckysmacky.combatdrones.item.ItemRemote;
import io.github.duckysmacky.combatdrones.network.DroneStatePacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.physics.DroneSystems;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.BatteryPack;
import io.github.duckysmacky.combatdrones.type.BombType;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import javax.vecmath.Quat4d;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

/**
 * Shared entity for every HAND_FLOWN drone type (FPV racers, DJI drone); behaviour differences come from
 * its {@link DroneType}. The server owns the flight state; the pilot's client predicts it (see client code).
 */
public class EntityHandFlownDrone extends Entity {
    /** Impact speed (blocks/tick) below which a collision is a landing, not a crash. */
    static final double ARM_SPEED = 0.25;
    /** Crash damage per block/tick of impact speed for drones that don't detonate. */
    private static final float CRASH_DAMAGE_PER_SPEED = 8.0F;
    /** Blocks; matches the tracker range so a drone is visible wherever it's tracked. */
    private static final double RENDER_DISTANCE = 160.0;

    private static final DataParameter<String> TYPE_ID = EntityDataManager.createKey(EntityHandFlownDrone.class, DataSerializers.STRING);
    private static final DataParameter<Float> ROLL = EntityDataManager.createKey(EntityHandFlownDrone.class, DataSerializers.FLOAT);
    /** Entity ID of the player flying this drone, or -1. Lets the pilot's client know to switch into FPV. */
    private static final DataParameter<Integer> PILOT_ID = EntityDataManager.createKey(EntityHandFlownDrone.class, DataSerializers.VARINT);
    private static final DataParameter<Boolean> MOTORS_ON = EntityDataManager.createKey(EntityHandFlownDrone.class, DataSerializers.BOOLEAN);
    /** Installed {@link BatteryPack} ordinal, or -1. Synced because the pack's weight changes flight prediction. */
    private static final DataParameter<Integer> BATTERY_PACK = EntityDataManager.createKey(EntityHandFlownDrone.class, DataSerializers.VARINT);
    /** Health as a fraction of max, for damage smoke on every client and the pilot's HUD. */
    private static final DataParameter<Float> HEALTH_FRACTION = EntityDataManager.createKey(EntityHandFlownDrone.class, DataSerializers.FLOAT);
    /** Entity status byte telling clients the drone was hit (red flash). */
    private static final byte HIT_STATUS = 2;
    /**
     * Loaded payload, synced to every client (HUD and the bombs drawn under the drone): low byte = count, next byte =
     * {@link BombType} ordinal + 1 for bomb bays (0 for grenades or empty).
     */
    private static final DataParameter<Integer> PAYLOAD = EntityDataManager.createKey(EntityHandFlownDrone.class, DataSerializers.VARINT);
    /** Thrust as a fraction of maximum, for motor sound pitch on every client. */
    private static final DataParameter<Float> THRUST = EntityDataManager.createKey(EntityHandFlownDrone.class, DataSerializers.FLOAT);
    private static final String OUT_OF_RANGE = "message.combat_drones.out_of_range";
    /** Inputs the server holds ahead of simulation; anything beyond this is dropped (per-drone rate limit). */
    private static final int MAX_QUEUED_INPUTS = 3;

    protected final HandFlownFlight.State flightState = new HandFlownFlight.State();
    @Nullable
    private UUID owner;
    private float health;
    /** Server-side battery charge in charge units; capacity depends on the type and installed pack. */
    private double charge;

    // Server-side piloting state
    @Nullable
    private EntityPlayerMP pilot;
    private final ArrayDeque<InputFrame> inputQueue = new ArrayDeque<>();
    private HandFlownFlight.Input lastInput = HandFlownFlight.Input.MOTORS_OFF;
    private int lastQueuedSequence = Integer.MIN_VALUE;
    /** Ticks until the trigger can fire again (rate limit for TriggerPacket). */
    private int triggerCooldown;
    private static final int TRIGGER_COOLDOWN_TICKS = 10;
    /**
     * Set when the link dropped because the drone flew out of range: it keeps flying on the pilot's last input
     * instead of hovering, so losing signal is a real risk (a racer just carries on until it hits something).
     * ponytail: not saved to NBT; a drifting drone that gets unloaded resumes as a normal unattended drone.
     */
    @Nullable
    private HandFlownFlight.Input driftInput;
    protected int lastProcessedSequence;

    public float prevRoll;

    // Client-side prediction, only active on the pilot's own client
    private boolean locallyPiloted;
    /** Pilot's client: last battery level the server reported (HUD + prediction). */
    private float knownBatteryFraction;
    @Nullable
    private InputFrame pendingPrediction;
    private final ArrayDeque<InputFrame> predictionHistory = new ArrayDeque<>();
    /** Orientation before this tick's predicted step; the FPV camera interpolates from it. */
    public final Quat4d prevOrientation = new Quat4d(0, 0, 0, 1);
    /** Inputs kept for replay; must cover a round trip (40 ticks = 2 s of latency). */
    private static final int PREDICTION_HISTORY = 40;

    public EntityHandFlownDrone(World world) {
        super(world);
        setSize(0.6F, 0.3F);
    }

    public EntityHandFlownDrone(World world, DroneType type, double x, double y, double z, float yaw) {
        this(world);
        dataManager.set(TYPE_ID, type.getId());
        setHealth(type.getMaxHP());
        setPosition(x, y, z);
        flightState.position.set(x, y, z);
        flightState.orientation.set(HandFlownFlight.fromEuler(yaw, 0, 0));
        rotationYaw = yaw;
    }

    @Override
    protected void entityInit() {
        dataManager.register(TYPE_ID, "");
        dataManager.register(ROLL, 0.0F);
        dataManager.register(PILOT_ID, -1);
        dataManager.register(MOTORS_ON, false);
        dataManager.register(BATTERY_PACK, -1);
        dataManager.register(THRUST, 0.0F);
        dataManager.register(HEALTH_FRACTION, 1.0F);
        dataManager.register(PAYLOAD, 0);
    }

    public float getHealthFraction() {
        return dataManager.get(HEALTH_FRACTION);
    }

    private void setHealth(float value) {
        health = value;
        DroneType type = getDroneType();
        dataManager.set(HEALTH_FRACTION, type == null ? 1.0F : Math.max(0, Math.min(1, value / type.getMaxHP())));
    }

    /** Client: ticks left of the red hit flash. */
    public int hitFlashTicks;

    @Override
    public void handleStatusUpdate(byte id) {
        if (id == HIT_STATUS) {
            hitFlashTicks = 6;
        } else {
            super.handleStatusUpdate(id);
        }
    }

    /** Client: smoke trail once the airframe is badly damaged, so everyone can see it's been hit. */
    private void spawnDamageSmoke() {
        float fraction = getHealthFraction();
        if (fraction >= 0.5F || rand.nextFloat() > (fraction < 0.25F ? 0.8F : 0.35F)) {
            return;
        }
        EnumParticleTypes smoke = fraction < 0.25F ? EnumParticleTypes.SMOKE_LARGE : EnumParticleTypes.SMOKE_NORMAL;
        world.spawnParticle(smoke, posX + (rand.nextDouble() - 0.5) * width, posY + height / 2, posZ + (rand.nextDouble() - 0.5) * width, 0, 0.02, 0);
    }

    public float getThrust() {
        return dataManager.get(THRUST);
    }

    /** Motors need an installed pack with charge left. */
    private boolean hasPower() {
        return getBatteryPack() != null && charge > 0;
    }

    @Nullable
    public BatteryPack getBatteryPack() {
        return BatteryPack.byIndex(dataManager.get(BATTERY_PACK));
    }

    /** Full charge for the installed pack, or 0 without one. */
    private double getCapacity() {
        DroneType type = getDroneType();
        BatteryPack pack = getBatteryPack();
        return type == null || pack == null ? 0 : type.getBatteryOrFuelCapacity() * pack.getCapacityMultiplier();
    }

    /** Server: 0..1 charge of the installed pack (0 without one). */
    public double getChargeFraction() {
        double capacity = getCapacity();
        return capacity > 0 ? Math.min(1, charge / capacity) : 0;
    }

    public int getPilotId() {
        return dataManager.get(PILOT_ID);
    }

    public boolean areMotorsOn() {
        return dataManager.get(MOTORS_ON);
    }

    @Nullable
    public DroneType getDroneType() {
        return DroneTypes.get(dataManager.get(TYPE_ID));
    }

    public float getRoll() {
        return dataManager.get(ROLL);
    }

    public int getPayloadCount() {
        return dataManager.get(PAYLOAD) & 0xFF;
    }

    /** Which bomb size is in the bay, or null (empty bay, or a grenade drone). */
    @Nullable
    public BombType getPayloadBomb() {
        return BombType.byIndex((dataManager.get(PAYLOAD) >> 8) - 1);
    }

    private void setPayload(int count, @Nullable BombType bomb) {
        dataManager.set(PAYLOAD, count | (count > 0 && bomb != null ? bomb.ordinal() + 1 : 0) << 8);
    }

    /** How many of the loaded (or given) payload fit: grenade slots, or bombs of that size in the bomb bay. */
    public static int payloadCapacity(DroneType type, @Nullable BombType bomb) {
        if (type.getPayload() == DroneType.PayloadKind.BOMB) {
            return bomb == null ? 0 : bomb.capacityIn(type.getPayloadCapacity());
        }
        return type.getPayloadCapacity();
    }

    /** The item the loaded payload goes back to when the drone is picked up. */
    @Nullable
    private Item loadedPayloadItem(DroneType type) {
        if (type.getPayload() == DroneType.PayloadKind.BOMB) {
            BombType bomb = getPayloadBomb();
            return bomb == null ? null : ModContent.bombItem(bomb);
        }
        return type.getPayload() == DroneType.PayloadKind.GRENADE ? ModContent.fragGrenade : null;
    }

    @Override
    public void onUpdate() {
        prevRoll = getRoll();
        super.onUpdate();
        if (world.isRemote) {
            if (locallyPiloted) {
                predictStep();
            }
            if (hitFlashTicks > 0) {
                hitFlashTicks--;
            }
            spawnDamageSmoke();
            return;
        }
        DroneType type = getDroneType();
        if (type == null) {
            CombatDrones.logger.warn("Removing drone with unknown type '{}'", dataManager.get(TYPE_ID));
            setDead();
            return;
        }
        if (triggerCooldown > 0) {
            triggerCooldown--;
        }
        if (pilot != null) {
            String problem = checkPilot(pilot);
            if (problem != null) {
                clearPilot(problem.equals(OUT_OF_RANGE) ? LinkLoss.SIGNAL : LinkLoss.OTHER);
            }
        }
        HandFlownFlight.Input input = nextInput();
        if (input.motorsOn && !hasPower()) {
            input = HandFlownFlight.Input.MOTORS_OFF;
            if (pilot != null && areMotorsOn()) {
                pilot.sendStatusMessage(new TextComponentTranslation("message.combat_drones.battery_empty"), true);
            }
        }
        dataManager.set(MOTORS_ON, input.motorsOn);
        // The entity position is the truth: picks up /tp, pistons, or a position set after NBT load
        flightState.position.set(posX, posY, posZ);
        double thrust = HandFlownFlight.step(flightState, input, flightParams(type));
        dataManager.set(THRUST, (float) thrust);
        if (input.motorsOn) {
            charge = Math.max(0, charge - DroneSystems.drainPerTick(type.getDrainRatePerTick(), thrust));
        }
        applyStepToEntity(type);
        if (pilot != null && !isDead) {
            ModNetwork.CHANNEL.sendTo(new DroneStatePacket(this, lastProcessedSequence), pilot);
        }
    }

    /**
     * One queued input per tick, in order; if the client fell behind, repeat its last intent. A drone nobody is
     * flying keeps its motors as they were: running means it holds position in hover, off means it falls.
     */
    private HandFlownFlight.Input nextInput() {
        if (pilot == null) {
            return unattendedInput();
        }
        InputFrame frame = inputQueue.poll();
        if (frame != null) {
            lastInput = frame.input;
            lastProcessedSequence = frame.sequence;
        }
        return lastInput;
    }

    private HandFlownFlight.Input unattendedInput() {
        if (!areMotorsOn()) {
            driftInput = null;
            return HandFlownFlight.Input.MOTORS_OFF;
        }
        return driftInput != null ? driftInput : holdInput();
    }

    private boolean isDjiScheme() {
        DroneType type = getDroneType();
        return type != null && type.getControlScheme() == DroneType.ControlScheme.DJI;
    }

    /** Neutral position hold in the drone's own scheme. */
    private HandFlownFlight.Input holdInput() {
        return isDjiScheme() ? HandFlownFlight.Input.VELOCITY_HOLD : HandFlownFlight.Input.HOLD_POSITION;
    }

    /**
     * Clients choose the flight mode, so a modified client could ask for any of them; only the modes of this
     * drone's scheme are allowed (a DJI can't be flown in acro, an FPV quad can't use GPS velocity hold).
     */
    private HandFlownFlight.Input restrictToScheme(HandFlownFlight.Input input) {
        boolean dji = isDjiScheme();
        if (dji == (input.mode == HandFlownFlight.Mode.VELOCITY)) {
            return input;
        }
        HandFlownFlight.Mode fallback = dji ? HandFlownFlight.Mode.VELOCITY : HandFlownFlight.Mode.HOVER;
        return new HandFlownFlight.Input(input.throttle, input.vertical, input.pitch, input.roll, input.yaw, fallback, input.motorsOn);
    }

    /** Server: the pilot pulled the trigger. Bomb-carrying drones release one bomb below the airframe. */
    public void onTrigger(EntityPlayerMP sender) {
        DroneType type = getDroneType();
        if (sender != pilot || triggerCooldown > 0 || type == null) {
            return;
        }
        triggerCooldown = TRIGGER_COOLDOWN_TICKS;
        BombType bomb = getPayloadBomb();
        int count = getPayloadCount();
        if (type.getPayload() != DroneType.PayloadKind.BOMB || bomb == null || count <= 0) {
            return;
        }
        setPayload(count - 1, bomb);
        HandFlownFlight.State state = flightState;
        world.spawnEntity(new EntityBomb(world, bomb, posX, posY - 0.35, posZ, state.velocity.x, state.velocity.y, state.velocity.z, owner));
        world.playSound(null, posX, posY, posZ, ModSounds.DRONE_BATTERY, SoundCategory.NEUTRAL, 1.0F, 0.6F);
    }

    /** Server: accept a control input from the network. Only the current pilot's inputs count. */
    public void queueInput(EntityPlayerMP sender, int sequence, HandFlownFlight.Input input) {
        if (sender != pilot || sequence <= lastQueuedSequence) {
            return;
        }
        lastQueuedSequence = sequence;
        if (inputQueue.size() >= MAX_QUEUED_INPUTS) {
            inputQueue.poll();
        }
        inputQueue.add(new InputFrame(sequence, restrictToScheme(input)));
    }

    /**
     * Why {@code player} can't fly this drone right now, as a translation key, or null if they can. Checked on
     * takeoff and every tick while flying.
     */
    @Nullable
    public String checkPilot(EntityPlayerMP player) {
        DroneType type = getDroneType();
        if (!player.isEntityAlive() || player.hasDisconnected() || player.world != world || type == null) {
            return "message.combat_drones.cannot_pilot";
        }
        if (!isOwnedBy(player)) {
            return "message.combat_drones.owned_by_other";
        }
        if (player.getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem() != ModContent.fpvGoggles) {
            return "message.combat_drones.need_goggles";
        }
        if (!getUniqueID().equals(heldRemoteLink(player))) {
            return "message.combat_drones.need_remote";
        }
        if (getDistanceSq(player) > type.getSignalRange() * type.getSignalRange()) {
            return OUT_OF_RANGE;
        }
        return null;
    }

    private void setPilot(EntityPlayerMP player) {
        pilot = player;
        driftInput = null;
        inputQueue.clear();
        // Until the pilot's first input arrives, keep doing what the drone was already doing
        lastInput = unattendedInput();
        lastQueuedSequence = Integer.MIN_VALUE;
        dataManager.set(PILOT_ID, player.getEntityId());
    }

    private enum LinkLoss {
        /** The pilot left on purpose (G): hover in place. */
        NONE,
        /** Flew out of range: keep flying on the last input. */
        SIGNAL,
        /** Goggles off, remote put away, death, logout: hover in place. */
        OTHER
    }

    private void clearPilot(LinkLoss reason) {
        if (pilot != null && reason != LinkLoss.NONE) {
            pilot.sendStatusMessage(new TextComponentTranslation(reason == LinkLoss.SIGNAL
                    ? "message.combat_drones.signal_lost" : "message.combat_drones.link_lost"), true);
        }
        if (reason == LinkLoss.SIGNAL) {
            driftInput = lastInput;
        }
        pilot = null;
        inputQueue.clear();
        dataManager.set(PILOT_ID, -1);
    }

    /** Server: G key. Exits FPV if already flying the remote's drone, otherwise tries to take off with it. */
    public static void togglePilot(EntityPlayerMP player) {
        UUID linked = heldRemoteLink(player);
        if (linked == null) {
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.need_remote"), true);
            return;
        }
        Entity entity = ((WorldServer) player.world).getEntityFromUuid(linked);
        if (!(entity instanceof EntityHandFlownDrone) || entity.isDead) {
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.drone_not_found"), true);
            return;
        }
        EntityHandFlownDrone drone = (EntityHandFlownDrone) entity;
        if (drone.pilot == player) {
            drone.clearPilot(LinkLoss.NONE);
            return;
        }
        String problem = drone.checkPilot(player);
        if (problem != null) {
            player.sendStatusMessage(new TextComponentTranslation(problem), true);
            return;
        }
        drone.setPilot(player);
    }

    @Nullable
    private static UUID heldRemoteLink(EntityPlayer player) {
        for (EnumHand hand : EnumHand.values()) {
            ItemStack stack = player.getHeldItem(hand);
            if (stack.getItem() == ModContent.fpvRemote) {
                return ItemRemote.getLinkedDrone(stack);
            }
        }
        return null;
    }

    private static final class InputFrame {
        final int sequence;
        final HandFlownFlight.Input input;

        InputFrame(int sequence, HandFlownFlight.Input input) {
            this.sequence = sequence;
            this.input = input;
        }
    }

    /** Flight tuning for this drone: its type's stats, slowed down by the weight of the installed pack. */
    HandFlownFlight.Params flightParams(DroneType type) {
        BatteryPack pack = getBatteryPack();
        double weight = pack == null ? 1.0 : pack.getPerformanceMultiplier();
        BombType bomb = getPayloadBomb();
        if (bomb != null) {
            // Each bomb carried costs a little speed; capped so a full bay never cripples the drone
            weight *= Math.max(0.5, 1 - bomb.getWeight() * getPayloadCount());
        }
        return new HandFlownFlight.Params(type.getMaxSpeed() * weight, type.getAcceleration() * weight);
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
        if (!world.isRemote) {
            dataManager.set(ROLL, (float) euler[2]);
        }
    }

    public HandFlownFlight.State getFlightState() {
        return flightState;
    }

    public boolean isLocallyPiloted() {
        return locallyPiloted;
    }

    /** Client: this player just became the pilot. Seed prediction from what the tracker last told us. */
    public void startLocalPiloting() {
        locallyPiloted = true;
        flightState.position.set(posX, posY, posZ);
        flightState.velocity.set(0, 0, 0);
        flightState.orientation.set(HandFlownFlight.fromEuler(rotationYaw, rotationPitch, getRoll()));
        prevOrientation.set(flightState.orientation);
        predictionHistory.clear();
        pendingPrediction = null;
        // Assume powered until the first state packet says otherwise, so takeoff isn't held back
        knownBatteryFraction = 1;
    }

    public void stopLocalPiloting() {
        locallyPiloted = false;
        predictionHistory.clear();
        pendingPrediction = null;
    }

    /** Client: the input to predict with on this tick's update (same one just sent to the server). */
    public void setPredictionInput(int sequence, HandFlownFlight.Input input) {
        pendingPrediction = new InputFrame(sequence, input);
    }

    private void predictStep() {
        prevOrientation.set(flightState.orientation);
        DroneType type = getDroneType();
        if (pendingPrediction == null || type == null) {
            return;
        }
        flightState.position.set(posX, posY, posZ);
        HandFlownFlight.step(flightState, predictedInput(pendingPrediction.input), flightParams(type));
        moveWithCollision();
        syncRotation();
        predictionHistory.add(pendingPrediction);
        if (predictionHistory.size() > PREDICTION_HISTORY) {
            predictionHistory.poll();
        }
        pendingPrediction = null;
    }

    /**
     * Client: authoritative state after the server processed input {@code sequence}. Rewind to it and replay the
     * inputs the server hasn't seen yet, so the pilot keeps a responsive view that still converges on the truth.
     */
    public void reconcile(int sequence, HandFlownFlight.State serverState, float batteryFraction) {
        knownBatteryFraction = batteryFraction;
        while (!predictionHistory.isEmpty() && predictionHistory.peek().sequence <= sequence) {
            predictionHistory.poll();
        }
        DroneType type = getDroneType();
        if (type == null) {
            return;
        }
        flightState.position.set(serverState.position);
        flightState.velocity.set(serverState.velocity);
        flightState.orientation.set(serverState.orientation);
        setPosition(serverState.position.x, serverState.position.y, serverState.position.z);
        HandFlownFlight.Params params = flightParams(type);
        for (InputFrame frame : predictionHistory) {
            HandFlownFlight.step(flightState, predictedInput(frame.input), params);
            moveWithCollision();
        }
        syncRotation();
    }

    /** Mirror the server's empty-battery motor cut so prediction doesn't fly on with dead motors. */
    private HandFlownFlight.Input predictedInput(HandFlownFlight.Input input) {
        boolean powered = getBatteryPack() != null && knownBatteryFraction > 0;
        return input.motorsOn && !powered ? HandFlownFlight.Input.MOTORS_OFF : input;
    }

    public float getKnownBatteryFraction() {
        return knownBatteryFraction;
    }

    /** Tracker updates would fight our own prediction, so the pilot's client ignores them. */
    @Override
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        if (!locallyPiloted) {
            super.setPositionAndRotationDirect(x, y, z, yaw, pitch, posRotationIncrements, teleport);
        }
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
            damage(amount * (float) ModConfig.droneDamageTakenMultiplier);
        }
        return true;
    }

    private void damage(float amount) {
        setHealth(health - amount);
        if (health > 0) {
            world.setEntityState(this, HIT_STATUS);
            world.playSound(null, posX, posY, posZ, ModSounds.DRONE_HIT, SoundCategory.NEUTRAL, 1.0F, 0.9F + rand.nextFloat() * 0.2F);
            return;
        }
        DroneType type = getDroneType();
        if (type != null && type.isImpactDetonates() && !onGround) {
            detonate(type);
        } else {
            destroy();
        }
    }

    protected void detonate(DroneType type) {
        int grenades = type.getPayload() == DroneType.PayloadKind.GRENADE ? getPayloadCount() : 0;
        float power = type.getBlastPower() + grenades * (float) ModConfig.grenadeBlastBonus;
        setDead();
        if (power > 0) {
            DroneExplosion.detonate((WorldServer) world, this, getBlameTarget(), posX, posY + height / 2, posZ, power);
        }
    }

    /** Kill credit goes to the owner (the only player who can fly it), if they're online. */
    @Nullable
    protected EntityLivingBase getBlameTarget() {
        return owner != null ? world.getPlayerEntityByUUID(owner) : null;
    }

    /** Destroyed without exploding: drop the drone and anything loaded into it. */
    /**
     * Shot down or wrecked. Nothing drops: a destroyed drone is gone, along with its battery. A loaded bomb bay goes
     * off with the bombs' power (one bomb's blast, +25% per extra bomb).
     */
    protected void destroy() {
        setDead();
        BombType bomb = getPayloadBomb();
        int count = getPayloadCount();
        if (bomb != null && count > 0) {
            float power = (float) (bomb.getBlastPower() * (1 + 0.25 * (count - 1)));
            DroneExplosion.detonate((WorldServer) world, this, getBlameTarget(), posX, posY + height / 2, posZ, power);
            return;
        }
        ((WorldServer) world).spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, posX, posY + height / 2, posZ, 10, 0.2, 0.2, 0.2, 0.03);
        world.playSound(null, posX, posY, posZ, ModSounds.DRONE_HIT, SoundCategory.NEUTRAL, 1.2F, 0.6F);
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
            return stack.getItem() == ModContent.fpvRemote || stack.getItem() == ModContent.fragGrenade || stack.getItem() instanceof ItemBomb
                    || stack.getItem() instanceof ItemBattery || (stack.isEmpty() && player.isSneaking());
        }
        if (owner != null && !isOwnedBy(player)) {
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.owned_by_other"), true);
            return true;
        }
        if (stack.getItem() == ModContent.fpvRemote) {
            owner = player.getUniqueID();
            String nameKey = getDroneItemTranslationKey();
            ItemRemote.link(stack, getUniqueID(), nameKey);
            world.playSound(null, posX, posY, posZ, ModSounds.DRONE_LINK, SoundCategory.PLAYERS, 1.0F, 1.0F);
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.linked", new TextComponentTranslation(nameKey)), true);
            return true;
        }
        if (stack.getItem() == ModContent.fragGrenade || stack.getItem() instanceof ItemBomb) {
            attachPayload(player, stack);
            return true;
        }
        if (stack.getItem() instanceof ItemBattery) {
            swapBattery(player, stack);
            return true;
        }
        if (stack.isEmpty() && player.isSneaking()) {
            pickUp(player);
            return true;
        }
        return false;
    }

    /** Puts one pack from the held stack in; the pack that was installed (if any) goes to the player's inventory. */
    private void swapBattery(EntityPlayer player, ItemStack stack) {
        BatteryPack pack = ((ItemBattery) stack.getItem()).getPack();
        double fraction = ItemBattery.getCharge(stack);
        String nameKey = stack.getItem().getTranslationKey() + ".name";
        ItemStack removed = removeBattery();
        dataManager.set(BATTERY_PACK, pack.ordinal());
        charge = fraction * getCapacity();
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        if (!removed.isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, removed);
        }
        world.playSound(null, posX, posY, posZ, ModSounds.DRONE_BATTERY, SoundCategory.PLAYERS, 1.0F, 1.0F);
        player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.battery_installed",
                new TextComponentTranslation(nameKey), Math.round(fraction * 100)), true);
    }

    /** Takes the installed pack out as an item carrying its remaining charge (empty stack if none). */
    private ItemStack removeBattery() {
        BatteryPack pack = getBatteryPack();
        if (pack == null) {
            return ItemStack.EMPTY;
        }
        ItemStack battery = ItemBattery.withCharge(ModContent.batteryItem(pack), getChargeFraction());
        dataManager.set(BATTERY_PACK, -1);
        charge = 0;
        return battery;
    }

    /**
     * Loads one grenade or bomb, if this drone's payload slots take that item. A bomb bay holds one bomb size at a
     * time; how many fit depends on the size (bay units).
     */
    private void attachPayload(EntityPlayer player, ItemStack stack) {
        DroneType type = getDroneType();
        boolean grenade = stack.getItem() == ModContent.fragGrenade;
        DroneType.PayloadKind wanted = grenade ? DroneType.PayloadKind.GRENADE : DroneType.PayloadKind.BOMB;
        if (type == null || type.getPayloadCapacity() == 0 || type.getPayload() != wanted) {
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.wrong_payload"), true);
            return;
        }
        int count = getPayloadCount();
        BombType bomb = grenade ? null : ((ItemBomb) stack.getItem()).getBombType();
        BombType loaded = getPayloadBomb();
        if (!grenade && count > 0 && loaded != bomb) {
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.bay_holds_other",
                    new TextComponentTranslation(ModContent.bombItem(loaded).getTranslationKey() + ".name")), true);
            return;
        }
        int capacity = payloadCapacity(type, bomb);
        if (count >= capacity) {
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.payload_full"), true);
            return;
        }
        setPayload(count + 1, bomb);
        world.playSound(null, posX, posY, posZ, ModSounds.DRONE_ATTACH, SoundCategory.PLAYERS, 1.0F, 1.0F);
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.payload_attached",
                new TextComponentTranslation(stack.getItem().getTranslationKey() + ".name"), count + 1, capacity), true);
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
        DroneType type = getDroneType();
        Item payloadItem = type == null ? null : loadedPayloadItem(type);
        if (payloadItem != null && getPayloadCount() > 0) {
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(payloadItem, getPayloadCount()));
        }
        ItemStack battery = removeBattery();
        if (!battery.isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, battery);
        }
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isDead;
    }

    /** Vanilla scales render distance with hitbox size, which would hide a small drone past ~30 blocks. */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double distanceSq) {
        return distanceSq < RENDER_DISTANCE * RENDER_DISTANCE;
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
        compound.setInteger("Payload", getPayloadCount());
        BombType bomb = getPayloadBomb();
        compound.setInteger("PayloadBomb", bomb == null ? -1 : bomb.ordinal());
        compound.setInteger("Battery", dataManager.get(BATTERY_PACK));
        compound.setDouble("Charge", charge);
        Quat4d orientation = flightState.orientation;
        compound.setDouble("QX", orientation.x);
        compound.setDouble("QY", orientation.y);
        compound.setDouble("QZ", orientation.z);
        compound.setDouble("QW", orientation.w);
        // Motors and momentum persist so an unattended hovering drone is still airborne after a reload
        compound.setBoolean("MotorsOn", areMotorsOn());
        compound.setDouble("VX", flightState.velocity.x);
        compound.setDouble("VY", flightState.velocity.y);
        compound.setDouble("VZ", flightState.velocity.z);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        dataManager.set(TYPE_ID, compound.getString("Type"));
        owner = compound.hasUniqueId("Owner") ? compound.getUniqueId("Owner") : null;
        setHealth(compound.getFloat("Health"));
        // "Grenades" was the M2/M3 key, before bombs shared the slot
        int count = compound.hasKey("Payload") ? compound.getInteger("Payload") : compound.getInteger("Grenades");
        // Bombs loaded before bomb sizes existed were the medium ones
        BombType bomb = compound.hasKey("PayloadBomb") ? BombType.byIndex(compound.getInteger("PayloadBomb")) : BombType.MEDIUM;
        setPayload(count, bomb);
        BatteryPack pack = compound.hasKey("Battery") ? BatteryPack.byIndex(compound.getInteger("Battery")) : null;
        dataManager.set(BATTERY_PACK, pack == null ? -1 : pack.ordinal());
        charge = compound.getDouble("Charge");
        flightState.orientation.set(compound.getDouble("QX"), compound.getDouble("QY"), compound.getDouble("QZ"), compound.getDouble("QW"));
        if (flightState.orientation.w == 0 && flightState.orientation.x == 0 && flightState.orientation.y == 0 && flightState.orientation.z == 0) {
            flightState.orientation.set(0, 0, 0, 1);
        }
        dataManager.set(MOTORS_ON, compound.getBoolean("MotorsOn"));
        flightState.velocity.set(compound.getDouble("VX"), compound.getDouble("VY"), compound.getDouble("VZ"));
    }
}
