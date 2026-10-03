package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.network.DesignatePacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.network.PlaneInputPacket;
import io.github.duckysmacky.combatdrones.network.TogglePilotPacket;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
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
 * the click-designate scheme) the mouse looks around a stabilized camera; under stick control it flies the plane.
 * Like {@link PilotController}, mouse movement still goes through vanilla's player turning and is read back and
 * undone, so the (invisible) body never rotates.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class RemoteController {
    /** Look limits relative to the nose (vanilla pitch convention: positive = down). */
    private static final float LOOK_YAW_LIMIT = 150.0F;
    private static final float LOOK_UP_LIMIT = -30.0F;
    private static final float LOOK_DOWN_LIMIT = 85.0F;
    /** Mouse pushes a virtual stick (degrees -> stick units) that springs back to centre, like FPV angle mode. */
    private static final double MOUSE_STICK_GAIN = 1.0 / 30.0;
    private static final double STICK_SPRING = 0.1;
    private static final double THROTTLE_STEP = 0.02;
    /** Throttle a takeover starts at: roughly level cruise. */
    private static final double START_THROTTLE = 0.7;

    private static int unitId = -1;
    private static boolean control;
    @Nullable
    private static EntityRampUnit unit;
    private static float heldYaw;
    private static float heldPitch;
    private static float lookYaw;
    private static float lookPitch;
    /** Mouse movement gathered per frame, handed to the stick once per tick. */
    private static double pendingMouseX;
    private static double pendingMouseY;
    private static double stickPitch;
    private static double stickRoll;
    private static double throttle;

    private RemoteController() {
    }

    /** The unit whose camera we're looking through, once the client tracks it. */
    @Nullable
    public static EntityRampUnit getUnit() {
        return unit;
    }

    public static boolean isActive() {
        return unitId >= 0;
    }

    /** Flying the unit by hand (stick scheme): the camera is fixed to the nose. */
    public static boolean isStickControl() {
        return control && scheme() == DroneType.ControlScheme.STICK;
    }

    public static boolean isControl() {
        return control;
    }

    public static double getThrottle() {
        return throttle;
    }

    public static float getLookYaw() {
        return lookYaw;
    }

    public static float getLookPitch() {
        return lookPitch;
    }

    @Nullable
    private static DroneType.ControlScheme scheme() {
        DroneType type = unit == null ? null : unit.getDroneType();
        return type == null ? null : type.getControlScheme();
    }

    /** From the server: a session started ({@code id} >= 0) or ended. */
    public static void onSession(int id, boolean underControl) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (id < 0) {
            stop(minecraft);
            return;
        }
        unitId = id;
        control = underControl;
        lookYaw = 0;
        lookPitch = 10;
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
        }
        if (unit != null && isStickControl()) {
            sendStick(minecraft);
        }
    }

    /** Stick flight: mouse (spring-back stick) plus arrow keys for pitch/roll, W/S throttle, LMB held fires. */
    private static void sendStick(Minecraft minecraft) {
        double[] mouse = takeMouseMovement();
        // Mouse forward (view pitch goes down) pushes the nose down, like a flight stick
        double mouseForward = ModConfig.invertMousePitch ? mouse[1] : -mouse[1];
        stickRoll = clamp(stickRoll * (1 - STICK_SPRING) + mouse[0] * MOUSE_STICK_GAIN);
        stickPitch = clamp(stickPitch * (1 - STICK_SPRING) + mouseForward * MOUSE_STICK_GAIN);
        double roll = clamp(stickRoll + axis(DroneKeys.ROLL_RIGHT.isKeyDown(), DroneKeys.ROLL_LEFT.isKeyDown()));
        double pitch = clamp(stickPitch + axis(DroneKeys.PITCH_FORWARD.isKeyDown(), DroneKeys.PITCH_BACK.isKeyDown()));
        if (minecraft.gameSettings.keyBindForward.isKeyDown()) {
            throttle = Math.min(1, throttle + THROTTLE_STEP);
        }
        if (minecraft.gameSettings.keyBindBack.isKeyDown()) {
            throttle = Math.max(0, throttle - THROTTLE_STEP);
        }
        boolean trigger = minecraft.currentScreen == null && Mouse.isButtonDown(0);
        ModNetwork.CHANNEL.sendToServer(new PlaneInputPacket(unitId, pitch, roll, throttle, trigger));
    }

    private static double axis(boolean positive, boolean negative) {
        return positive == negative ? 0 : positive ? 1 : -1;
    }

    private static double clamp(double value) {
        return Math.max(-1, Math.min(1, value));
    }

    /**
     * Per rendered frame (from the camera hook): consume mouse movement. In look modes it turns the camera at once
     * (per tick would look steppy); under stick control it's saved for the next tick's stick input.
     */
    public static void updateLook() {
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
        lookYaw = Math.max(-LOOK_YAW_LIMIT, Math.min(LOOK_YAW_LIMIT, lookYaw + mouseX));
        lookPitch = Math.max(LOOK_UP_LIMIT, Math.min(LOOK_DOWN_LIMIT, lookPitch + mouseY));
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

    /** Clicks would act on whatever the camera looks at; the session uses them itself. */
    @SubscribeEvent
    public static void onMouse(MouseEvent event) {
        if (unitId >= 0 && (event.getButton() != -1 || event.getDwheel() != 0)) {
            event.setCanceled(true);
            // Click-designate: strike whatever the camera is looking at; the server traces it from the unit
            if (event.getButton() == 0 && event.isButtonstate() && control && unit != null
                    && scheme() == DroneType.ControlScheme.CLICK_DESIGNATE) {
                Vec3d direction = Vec3d.fromPitchYaw(lookPitch, unit.rotationYaw + lookYaw);
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
