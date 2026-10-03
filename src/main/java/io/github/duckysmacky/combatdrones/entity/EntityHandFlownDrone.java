package io.github.duckysmacky.combatdrones.entity;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemRemote;
import io.github.duckysmacky.combatdrones.network.DroneStatePacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
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
 * Shared entity for every HAND_FLOWN drone type (racers, cargo, gunships); behaviour differences come from
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
    /** Inputs the server holds ahead of simulation; anything beyond this is dropped (per-drone rate limit). */
    private static final int MAX_QUEUED_INPUTS = 3;
    private static final HandFlownFlight.Input MOTORS_OFF = new HandFlownFlight.Input(0, 0, 0, 0, 0, HandFlownFlight.Mode.ANGLE, false);

    protected final HandFlownFlight.State flightState = new HandFlownFlight.State();
    @Nullable
    private UUID owner;
    private float health;
    private int grenades;

    // Server-side piloting state
    @Nullable
    private EntityPlayerMP pilot;
    private final ArrayDeque<InputFrame> inputQueue = new ArrayDeque<>();
    private HandFlownFlight.Input lastInput = MOTORS_OFF;
    private int lastQueuedSequence = Integer.MIN_VALUE;
    protected int lastProcessedSequence;

    public float prevRoll;

    // Client-side prediction, only active on the pilot's own client
    private boolean locallyPiloted;
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
        dataManager.register(PILOT_ID, -1);
        dataManager.register(MOTORS_ON, false);
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

    public int getGrenades() {
        return grenades;
    }

    @Override
    public void onUpdate() {
        prevRoll = getRoll();
        super.onUpdate();
        if (world.isRemote) {
            if (locallyPiloted) {
                predictStep();
            }
            return;
        }
        DroneType type = getDroneType();
        if (type == null) {
            CombatDrones.logger.warn("Removing drone with unknown type '{}'", dataManager.get(TYPE_ID));
            setDead();
            return;
        }
        if (pilot != null && checkPilot(pilot) != null) {
            clearPilot(true);
        }
        HandFlownFlight.Input input = nextInput();
        dataManager.set(MOTORS_ON, input.motorsOn);
        // The entity position is the truth: picks up /tp, pistons, or a position set after NBT load
        flightState.position.set(posX, posY, posZ);
        HandFlownFlight.step(flightState, input, paramsOf(type));
        applyStepToEntity(type);
        if (pilot != null && !isDead) {
            ModNetwork.CHANNEL.sendTo(new DroneStatePacket(this, lastProcessedSequence), pilot);
        }
    }

    /**
     * One queued input per tick, in order; if the client fell behind, repeat its last intent. Unpiloted drones
     * have their motors off and fall.
     */
    private HandFlownFlight.Input nextInput() {
        if (pilot == null) {
            return MOTORS_OFF;
        }
        InputFrame frame = inputQueue.poll();
        if (frame != null) {
            lastInput = frame.input;
            lastProcessedSequence = frame.sequence;
        }
        return lastInput;
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
        inputQueue.add(new InputFrame(sequence, input));
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
            return "message.combat_drones.out_of_range";
        }
        return null;
    }

    private void setPilot(EntityPlayerMP player) {
        pilot = player;
        inputQueue.clear();
        lastInput = MOTORS_OFF;
        lastQueuedSequence = Integer.MIN_VALUE;
        dataManager.set(PILOT_ID, player.getEntityId());
    }

    private void clearPilot(boolean linkLost) {
        if (pilot != null && linkLost) {
            pilot.sendStatusMessage(new TextComponentTranslation("message.combat_drones.link_lost"), true);
        }
        pilot = null;
        inputQueue.clear();
        lastInput = MOTORS_OFF;
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
            drone.clearPilot(false);
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
        HandFlownFlight.step(flightState, pendingPrediction.input, paramsOf(type));
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
    public void reconcile(int sequence, HandFlownFlight.State serverState) {
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
        HandFlownFlight.Params params = paramsOf(type);
        for (InputFrame frame : predictionHistory) {
            HandFlownFlight.step(flightState, frame.input, params);
            moveWithCollision();
        }
        syncRotation();
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

    /** Kill credit goes to the owner (the only player who can fly it), if they're online. */
    @Nullable
    protected EntityLivingBase getBlameTarget() {
        return owner != null ? world.getPlayerEntityByUUID(owner) : null;
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
            return stack.getItem() == ModContent.fpvRemote || stack.getItem() == ModContent.fragGrenade || (stack.isEmpty() && player.isSneaking());
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
        if (stack.getItem() == ModContent.fragGrenade) {
            attachGrenade(player, stack);
            return true;
        }
        if (stack.isEmpty() && player.isSneaking()) {
            pickUp(player);
            return true;
        }
        return false;
    }

    /** Grenades only arm kamikaze racers; other drones' payload slots hold bombs or ammo (M4). */
    private void attachGrenade(EntityPlayer player, ItemStack stack) {
        DroneType type = getDroneType();
        if (type == null || !type.isImpactDetonates() || type.getPayloadCapacity() == 0) {
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.no_grenade_slot"), true);
            return;
        }
        if (grenades >= type.getPayloadCapacity()) {
            player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.payload_full"), true);
            return;
        }
        grenades++;
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.grenade_attached", grenades, type.getPayloadCapacity()), true);
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
    }
}
