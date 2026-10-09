package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.entity.Raytrace;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.DesignatePacket;
import io.github.duckysmacky.combatdrones.network.GunnerInputPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.network.PlaneInputPacket;
import io.github.duckysmacky.combatdrones.network.ReconLookPacket;
import io.github.duckysmacky.combatdrones.network.TogglePilotPacket;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.Loadout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;

/**
 * Client side of a remote session with a ramp unit: the camera rides the unit, the body stays put. In view mode (and
 * the click-designate and gunner schemes) the mouse looks around a stabilized camera; under stick control it flies the
 * plane.
 * Like {@link PilotController}, mouse movement still goes through vanilla's player turning and is read back and
 * undone, so the (invisible) body never rotates.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class RemoteController {
    /** Camera tilt limits (vanilla pitch convention: positive = down); it pans all the way round. */
    private static final float LOOK_UP_LIMIT = -30.0F;
    private static final float LOOK_DOWN_LIMIT = 90.0F;
    /** How far the camera looks for a block or entity to hold on to when you aim it. */
    private static final double ANCHOR_RANGE = 512.0;
    /** How far the camera holds on to an estimated ground point when the line finds nothing (unloaded terrain). */
    private static final double GROUND_FALLBACK_RANGE = 2048.0;
    private static final int MAX_ZOOM = 8;
    /** Gunner seat: the camera starts looking this far down (degrees) along the nose. */
    private static final float GUNNER_START_PITCH = 30.0F;
    /** Mouse pushes a virtual stick (degrees -> stick units) that springs back to centre, like FPV angle mode. */
    private static final double MOUSE_STICK_GAIN = 1.0 / 15.0;
    private static final double STICK_SPRING = 0.1;
    /** Free mode: degrees of mouse movement in one tick that equal full stick (full rotation rate), like FPV acro. */
    private static final double FREE_MOUSE_FULL_STICK = 4.0;
    private static final double THROTTLE_STEP = 0.02;
    /** Throttle a takeover starts at: roughly level cruise. */
    private static final double START_THROTTLE = 0.7;

    private static int unitId = -1;
    /** Where the body stands while flying a hand-flown drone (signal is measured from here), or null. */
    @Nullable
    private static Vec3d droneHome;
    private static boolean control;
    @Nullable
    private static EntityRampUnit unit;
    private static float heldYaw;
    private static float heldPitch;
    /** Look camera direction in world terms (not relative to the unit), so it doesn't swing when the unit turns. */
    private static float cameraYaw;
    private static float cameraPitch;
    /** What the camera holds on to between mouse movements: an entity (followed live) or a fixed point. */
    @Nullable
    private static Entity anchorEntity;
    @Nullable
    private static Vec3d anchorPoint;
    /** Camera zoom in look modes: 1, 2, 4 or 8 times. */
    private static int zoom = 1;
    /** Mouse movement gathered per frame, handed to the stick once per tick. */
    private static double pendingMouseX;
    private static double pendingMouseY;
    private static double stickPitch;
    private static double stickRoll;
    private static double throttle;
    /** Free (rate) stick instead of the self-levelling assisted one; kept between sessions. */
    private static boolean freeMode;
    /** Gunner seat: the selected weapon station. */
    private static int gunnerStation;

    private RemoteController() {
    }

    @Nullable
    public static Vec3d getDroneHome() {
        return droneHome;
    }

    /** The unit whose camera we're looking through, once the client tracks it. */
    @Nullable
    public static EntityRampUnit getUnit() {
        return unit;
    }

    public static boolean isActive() {
        return unitId >= 0;
    }

    /** Flying the unit by hand (stick scheme, or steering a missile's dive): the camera is fixed to the nose. */
    public static boolean isStickControl() {
        return control && (scheme() == DroneType.ControlScheme.STICK || isMissile());
    }

    /** Steering a cruise missile's dive. */
    public static boolean isMissile() {
        return control && scheme() == DroneType.ControlScheme.MISSILE;
    }

    public static boolean isControl() {
        return control;
    }

    /** In a gunship's gunner seat: the autopilot flies, the camera aims the weapons. */
    public static boolean isGunner() {
        return control && scheme() == DroneType.ControlScheme.GUNNER;
    }

    public static boolean isFreeMode() {
        return freeMode;
    }

    public static double getThrottle() {
        return throttle;
    }

    public static int getZoom() {
        return zoom;
    }

    public static float getCameraYaw() {
        return cameraYaw;
    }

    public static float getCameraPitch() {
        return cameraPitch;
    }

    @Nullable
    private static DroneType.ControlScheme scheme() {
        DroneType type = unit == null ? null : unit.getDroneType();
        return type == null ? null : type.getControlScheme();
    }

    /**
     * From the server: a session started ({@code id} >= 0) or ended. Hand-flown drone sessions are driven by
     * {@link PilotController}; here we only keep where the body stands, for the signal readout.
     */
    public static void onSession(int id, boolean underControl, boolean drone, Vec3d home) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (id < 0) {
            droneHome = null;
            stop(minecraft);
            return;
        }
        if (drone) {
            droneHome = home;
            return;
        }
        unitId = id;
        control = underControl;
        anchorEntity = null;
        anchorPoint = null;
        zoom = 1;
        gunnerStation = 0;
        stickPitch = 0;
        stickRoll = 0;
        throttle = START_THROTTLE;
        if (minecraft.player != null) {
            minecraft.player.sendStatusMessage(new TextComponentTranslation(underControl ? "message.combat_drones.remote.control_started"
                    : "message.combat_drones.remote.view_started", Tooltips.keyName(DroneKeys.TOGGLE_FPV)), true);
        }
    }

    private static void stop(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.getRenderViewEntity() == unit) {
            minecraft.setRenderViewEntity(minecraft.player);
        }
        unitId = -1;
        unit = null;
        control = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || unitId < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        if (player == null || minecraft.world == null) {
            stop(minecraft);
            return;
        }
        // The unit only appears once the server has moved us next to it and started tracking it
        Entity entity = minecraft.world.getEntityByID(unitId);
        if (entity instanceof EntityRampUnit && !entity.isDead && unit != entity) {
            unit = (EntityRampUnit) entity;
            minecraft.setRenderViewEntity(unit);
            player.rotationPitch = 0;
            player.prevRotationPitch = 0;
            heldYaw = player.rotationYaw;
            heldPitch = 0;
            cameraYaw = unit.rotationYaw;
            cameraPitch = isGunner() ? GUNNER_START_PITCH : 10;
            if (isMissile()) {
                player.sendStatusMessage(new TextComponentTranslation("message.combat_drones.remote.missile_started",
                        Tooltips.keyName(DroneKeys.TOGGLE_FPV)), true);
            }
        }
        if (unit != null && !control && unit.ticksExisted % 2 == 0) {
            DroneType type = unit.getDroneType();
            if (type != null && type.isSensor()) {
                // Where the camera points; the server works out what's under the crosshair for the lock
                ModNetwork.CHANNEL.sendToServer(new ReconLookPacket(unitId, Vec3d.fromPitchYaw(cameraPitch, cameraYaw)));
            }
        }
        if (unit != null && isMissile()) {
            sendMissileSteer(minecraft);
        } else if (unit != null && isStickControl()) {
            sendStick(minecraft);
        } else if (unit != null && isGunner()) {
            sendGunner(minecraft);
        } else if (unit != null && control) {
            // Click-designate flies itself; only boost goes up each tick
            ModNetwork.CHANNEL.sendToServer(new PlaneInputPacket(unitId, 0, 0, 0, false, false, isBoostKeyDown(minecraft)));
        }
    }

    /**
     * Stick flight: mouse plus arrow keys for pitch/roll (A/D roll too), W/S throttle, LMB held fires. H switches
     * between assisted (the mouse pushes a spring-back stick that sets the attitude, which levels out on release)
     * and free (mouse movement is a rotation rate, the attitude stays where you leave it).
     */
    private static void sendStick(Minecraft minecraft) {
        if (DroneKeys.FLIGHT_MODE.isPressed()) {
            freeMode = !freeMode;
            minecraft.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(ModSounds.DRONE_BEEP, freeMode ? 1.5F : 1.2F));
            minecraft.player.sendStatusMessage(new TextComponentTranslation(freeMode ? "message.combat_drones.remote.mode_free"
                    : "message.combat_drones.remote.mode_assisted"), true);
        }
        double[] mouse = takeMouseMovement();
        double mouseRight = mouse[0] * ModConfig.planeMouseSensitivity;
        // Mouse forward (view pitch goes down) pushes the nose down, like a flight stick
        double mouseForward = (ModConfig.invertMousePitch ? mouse[1] : -mouse[1]) * ModConfig.planeMouseSensitivity;
        if (freeMode) {
            stickRoll = clamp(mouseRight / FREE_MOUSE_FULL_STICK);
            stickPitch = clamp(mouseForward / FREE_MOUSE_FULL_STICK);
        } else {
            stickRoll = clamp(stickRoll * (1 - STICK_SPRING) + mouseRight * MOUSE_STICK_GAIN);
            stickPitch = clamp(stickPitch * (1 - STICK_SPRING) + mouseForward * MOUSE_STICK_GAIN);
        }
        boolean rollRight = DroneKeys.ROLL_RIGHT.isKeyDown() || minecraft.gameSettings.keyBindRight.isKeyDown();
        boolean rollLeft = DroneKeys.ROLL_LEFT.isKeyDown() || minecraft.gameSettings.keyBindLeft.isKeyDown();
        double roll = clamp(stickRoll + axis(rollRight, rollLeft));
        double pitch = clamp(stickPitch + axis(DroneKeys.PITCH_FORWARD.isKeyDown(), DroneKeys.PITCH_BACK.isKeyDown()));
        if (minecraft.gameSettings.keyBindForward.isKeyDown()) {
            throttle = Math.min(1, throttle + THROTTLE_STEP);
        }
        if (minecraft.gameSettings.keyBindBack.isKeyDown()) {
            throttle = Math.max(0, throttle - THROTTLE_STEP);
        }
        boolean trigger = minecraft.currentScreen == null && Mouse.isButtonDown(0);
        ModNetwork.CHANNEL.sendToServer(new PlaneInputPacket(unitId, pitch, roll, throttle, trigger, freeMode, isBoostKeyDown(minecraft)));
    }

    /**
     * Missile dive: the mouse points the nose (up on screen is toward the missile's reference heading), like aiming
     * a camera, but only as fast as the missile can turn. Arrows or W/A/S/D steer at full rate, LMB held boosts (the
     * click itself is swallowed by {@link #onMouse}). Sent as pitch (forward) and roll (right) of the plane input.
     */
    private static void sendMissileSteer(Minecraft minecraft) {
        double[] mouse = takeMouseMovement();
        double right = mouse[0] * ModConfig.planeMouseSensitivity / FREE_MOUSE_FULL_STICK;
        // Mouse forward (view pitch goes up, i.e. negative) points the nose up the screen
        double forward = (ModConfig.invertMousePitch ? mouse[1] : -mouse[1]) * ModConfig.planeMouseSensitivity / FREE_MOUSE_FULL_STICK;
        boolean keysRight = DroneKeys.ROLL_RIGHT.isKeyDown() || minecraft.gameSettings.keyBindRight.isKeyDown();
        boolean keysLeft = DroneKeys.ROLL_LEFT.isKeyDown() || minecraft.gameSettings.keyBindLeft.isKeyDown();
        boolean keysForward = DroneKeys.PITCH_FORWARD.isKeyDown() || minecraft.gameSettings.keyBindForward.isKeyDown();
        boolean keysBack = DroneKeys.PITCH_BACK.isKeyDown() || minecraft.gameSettings.keyBindBack.isKeyDown();
        right = clamp(right + axis(keysRight, keysLeft));
        forward = clamp(forward + axis(keysForward, keysBack));
        boolean boost = minecraft.currentScreen == null && Mouse.isButtonDown(0);
        ModNetwork.CHANNEL.sendToServer(new PlaneInputPacket(unitId, forward, right, 0, false, false, boost));
    }

    /**
     * Gunner seat: the camera direction is the aim, LMB held fires (the click itself is swallowed by {@link #onMouse}),
     * the switch-weapon key cycles through the stations that carry a weapon.
     */
    private static void sendGunner(Minecraft minecraft) {
        DroneType type = unit.getDroneType();
        if (type == null) {
            return;
        }
        if (DroneKeys.SWITCH_WEAPON.isPressed()) {
            for (int step = 1; step <= Loadout.STATIONS; step++) {
                int next = (gunnerStation + step) % Loadout.STATIONS;
                if (type.getWeapon(next) != DroneType.WeaponType.NONE) {
                    if (next != gunnerStation) {
                        gunnerStation = next;
                        minecraft.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(ModSounds.DRONE_BEEP, 1.3F));
                    }
                    break;
                }
            }
        }
        boolean trigger = minecraft.currentScreen == null && Mouse.isButtonDown(0);
        Vec3d aim = Vec3d.fromPitchYaw(cameraPitch, cameraYaw);
        // While firing, also what the crosshair is on, so the server aims at that point from where the unit really is
        Vec3d target = null;
        if (trigger) {
            Vec3d eye = new Vec3d(unit.posX, unit.posY + unit.getEyeHeight(), unit.posZ);
            RayTraceResult hit = Raytrace.firstResult(unit.world, eye, eye.add(aim.scale(ANCHOR_RANGE)), unit);
            target = hit != null ? hit.hitVec : null;
        }
        ModNetwork.CHANNEL.sendToServer(new GunnerInputPacket(unitId, aim, target, trigger, gunnerStation));
    }

    /** Space (jump) boosts a munition: faster, at several times the fuel burn. */
    private static boolean isBoostKeyDown(Minecraft minecraft) {
        return minecraft.currentScreen == null && minecraft.gameSettings.keyBindJump.isKeyDown();
    }

    private static double axis(boolean positive, boolean negative) {
        return positive == negative ? 0 : positive ? 1 : -1;
    }

    private static double clamp(double value) {
        return Math.max(-1, Math.min(1, value));
    }

    /**
     * Per rendered frame (from the camera hook): consume mouse movement. Under stick control it's saved for the next
     * tick's stick input. In look modes the camera turns at once (per tick would look steppy) and then holds on to
     * whatever is under the crosshair: an entity it hits is followed, a block stays put, so the view doesn't drift
     * away from the target while the unit flies on. Pointing at the sky just keeps the direction.
     */
    public static void updateLook(float partialTicks) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || unit == null) {
            return;
        }
        float mouseX = player.rotationYaw - heldYaw;
        float mouseY = player.rotationPitch - heldPitch;
        player.rotationYaw = heldYaw;
        player.prevRotationYaw = heldYaw;
        player.rotationPitch = heldPitch;
        player.prevRotationPitch = heldPitch;
        if (isStickControl()) {
            pendingMouseX += mouseX;
            pendingMouseY += mouseY;
            return;
        }
        Vec3d eye = new Vec3d(unit.prevPosX + (unit.posX - unit.prevPosX) * partialTicks,
                unit.prevPosY + (unit.posY - unit.prevPosY) * partialTicks + unit.getEyeHeight(),
                unit.prevPosZ + (unit.posZ - unit.prevPosZ) * partialTicks);
        if (mouseX != 0 || mouseY != 0) {
            // Finer aim when zoomed in
            cameraYaw = MathHelper.wrapDegrees(cameraYaw + mouseX / zoom);
            cameraPitch = Math.max(LOOK_UP_LIMIT, Math.min(LOOK_DOWN_LIMIT, cameraPitch + mouseY / zoom));
            // The gunner's camera holds a direction, not a target: keeping the aim on a point while orbiting is the skill
            if (!isGunner()) {
                reanchor(eye);
            }
            return;
        }
        if (isGunner()) {
            return;
        }
        if (anchorEntity != null && (anchorEntity.isDead || anchorEntity.world != unit.world)) {
            anchorEntity = null;
        }
        Vec3d target = anchorEntity != null
                ? new Vec3d(anchorEntity.lastTickPosX + (anchorEntity.posX - anchorEntity.lastTickPosX) * partialTicks,
                        anchorEntity.lastTickPosY + (anchorEntity.posY - anchorEntity.lastTickPosY) * partialTicks + anchorEntity.height / 2,
                        anchorEntity.lastTickPosZ + (anchorEntity.posZ - anchorEntity.lastTickPosZ) * partialTicks)
                : anchorPoint;
        if (target == null) {
            return;
        }
        Vec3d toTarget = target.subtract(eye);
        double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontal));
        if (pitch < LOOK_UP_LIMIT) {
            // The target has gone above what the belly gimbal can tilt to: let go of it
            anchorEntity = null;
            anchorPoint = null;
            return;
        }
        cameraYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        cameraPitch = Math.min(LOOK_DOWN_LIMIT, pitch);
    }

    /**
     * Picks what's under the crosshair to hold on to: an entity, else the block it hits, else (aiming below the horizon
     * at terrain this client hasn't loaded) the point where the line meets the ground level under the unit, else
     * nothing (the sky).
     */
    private static void reanchor(Vec3d eye) {
        Vec3d direction = Vec3d.fromPitchYaw(cameraPitch, cameraYaw);
        RayTraceResult hit = Raytrace.firstResult(unit.world, eye, eye.add(direction.scale(ANCHOR_RANGE)), unit);
        anchorEntity = hit != null ? hit.entityHit : null;
        anchorPoint = hit != null ? (hit.entityHit == null ? hit.hitVec : null) : groundPoint(eye, direction);
    }

    /**
     * Where the line from {@code eye} meets the ground height under the unit (its own chunk is always loaded), or null
     * when it never comes down that far.
     */
    @Nullable
    private static Vec3d groundPoint(Vec3d eye, Vec3d direction) {
        double groundY = unit.world.getHeight(new BlockPos(unit)).getY();
        if (direction.y > -1e-3 || eye.y <= groundY) {
            return null;
        }
        double distance = (eye.y - groundY) / -direction.y;
        return distance > GROUND_FALLBACK_RANGE ? null : eye.add(direction.scale(distance));
    }

    /** Mouse movement since the last call, in degrees (x right, y down), for the stick. */
    static double[] takeMouseMovement() {
        double[] movement = {pendingMouseX, pendingMouseY};
        pendingMouseX = 0;
        pendingMouseY = 0;
        return movement;
    }

    /** W/A/S/D and jump/sneak belong to the unit, not the body (sneaking would also dismount it). */
    @SubscribeEvent
    public static void onInputUpdate(InputUpdateEvent event) {
        if (unitId < 0) {
            return;
        }
        MovementInput movement = event.getMovementInput();
        movement.moveForward = 0;
        movement.moveStrafe = 0;
        movement.forwardKeyDown = false;
        movement.backKeyDown = false;
        movement.leftKeyDown = false;
        movement.rightKeyDown = false;
        movement.jump = false;
        movement.sneak = false;
    }

    /**
     * Anyone riding a ramp unit is a remote pilot: invisible, but vanilla would still draw their armour and held
     * item floating on the plane, for every viewer.
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Entity vehicle = event.getEntityPlayer().getRidingEntity();
        if (vehicle instanceof EntityRampUnit || vehicle instanceof EntityHandFlownDrone) {
            event.setCanceled(true);
        }
    }

    /** Clicks would act on whatever the camera looks at; the session uses them itself. */
    @SubscribeEvent
    public static void onMouse(MouseEvent event) {
        if (unitId >= 0 && (event.getButton() != -1 || event.getDwheel() != 0)) {
            event.setCanceled(true);
            // Wheel zooms the look camera (stick flight keeps the plain forward view)
            if (event.getDwheel() != 0 && !isStickControl()) {
                zoom = event.getDwheel() > 0 ? Math.min(MAX_ZOOM, zoom * 2) : Math.max(1, zoom / 2);
            }
            // Click-designate: strike whatever the camera is looking at; the server traces it from the unit
            if (event.getButton() == 0 && event.isButtonstate() && control && unit != null
                    && scheme() == DroneType.ControlScheme.CLICK_DESIGNATE) {
                Vec3d direction = Vec3d.fromPitchYaw(cameraPitch, cameraYaw);
                ModNetwork.CHANNEL.sendToServer(new DesignatePacket(unitId, direction));
            }
        }
    }

    /**
     * Esc leaves the session instead of pausing. Checked on the key itself rather than on the pause screen opening,
     * which also happens when the window loses focus.
     */
    @SubscribeEvent
    public static void onKey(InputEvent.KeyInputEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (unitId >= 0 && Keyboard.getEventKey() == Keyboard.KEY_ESCAPE && Keyboard.getEventKeyState()
                && minecraft.currentScreen instanceof GuiIngameMenu) {
            minecraft.displayGuiScreen(null);
            ModNetwork.CHANNEL.sendToServer(new TogglePilotPacket());
        }
    }
}
