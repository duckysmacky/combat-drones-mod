package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.network.ControlInputPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.network.TogglePilotPacket;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovementInput;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Turns the local player's keyboard and mouse into drone input while they fly, and keeps their body still.
 * Mouse movement still goes through vanilla's {@code player.turn()}; each tick we read how far it turned the player,
 * use that as stick input, and turn the player back, so the body never visibly rotates.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class PilotController {
    private static final double THROTTLE_STEP = 0.03;
    /** ACRO: degrees of mouse turn in one tick that equal full stick (full rotation rate). */
    private static final double ACRO_MOUSE_FULL_STICK = 8.0;
    /** ANGLE/HOVER: mouse pushes a virtual stick (degrees -> stick units) that springs back to centre. */
    private static final double ANGLE_MOUSE_GAIN = 1.0 / 30.0;
    private static final double STICK_SPRING = 0.1;

    @Nullable
    private static EntityHandFlownDrone drone;
    private static float heldYaw;
    private static float heldPitch;
    private static double throttle;
    private static double stickPitch;
    private static double stickRoll;
    private static HandFlownFlight.Mode mode = HandFlownFlight.Mode.ACRO;
    private static boolean motorsOn;
    private static int sequence;

    private PilotController() {
    }

    @Nullable
    public static EntityHandFlownDrone getPilotedDrone() {
        return drone;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        if (player == null || minecraft.world == null) {
            drone = null;
            return;
        }
        // Don't wait for the server to drop the link: a dead pilot gets their own view back immediately
        if (player.getHealth() <= 0 || player.isDead) {
            if (drone != null) {
                stop(minecraft, player);
            }
            return;
        }
        if (DroneKeys.TOGGLE_FPV.isPressed()) {
            ModNetwork.CHANNEL.sendToServer(new TogglePilotPacket());
        }

        EntityHandFlownDrone current = findPilotedDrone(minecraft, player);
        if (current != drone) {
            if (drone != null) {
                stop(minecraft, player);
            }
            if (current != null) {
                start(minecraft, player, current);
            }
        }
        if (drone == null) {
            return;
        }
        HandFlownFlight.Input input = readInput(minecraft, player);
        sequence++;
        // Predicted in the drone's own update later this tick, after vanilla saves prevPos for interpolation
        drone.setPredictionInput(sequence, input);
        ModNetwork.CHANNEL.sendToServer(new ControlInputPacket(drone.getEntityId(), sequence, input));
    }

    @Nullable
    private static EntityHandFlownDrone findPilotedDrone(Minecraft minecraft, EntityPlayerSP player) {
        int playerId = player.getEntityId();
        if (drone != null && !drone.isDead && drone.world == minecraft.world && drone.getPilotId() == playerId) {
            return drone;
        }
        for (Entity entity : minecraft.world.loadedEntityList) {
            if (entity instanceof EntityHandFlownDrone && !entity.isDead && ((EntityHandFlownDrone) entity).getPilotId() == playerId) {
                return (EntityHandFlownDrone) entity;
            }
        }
        return null;
    }

    private static void start(Minecraft minecraft, EntityPlayerSP player, EntityHandFlownDrone newDrone) {
        drone = newDrone;
        drone.startLocalPiloting();
        minecraft.setRenderViewEntity(drone);
        // Level the body's pitch so mouse deltas never hit vanilla's ±90° clamp
        player.rotationPitch = 0;
        player.prevRotationPitch = 0;
        heldYaw = player.rotationYaw;
        heldPitch = 0;
        stickPitch = 0;
        stickRoll = 0;
        motorsOn = drone.areMotorsOn();
        // A drone left running is hovering in place: pick it up in Hover so it doesn't drop on takeover
        mode = motorsOn ? HandFlownFlight.Mode.HOVER : HandFlownFlight.Mode.ACRO;
        throttle = motorsOn ? hoverThrottle() : 0;
    }

    /** Throttle that exactly cancels gravity for the current drone type. */
    private static double hoverThrottle() {
        DroneType type = drone.getDroneType();
        return type == null ? 0 : HandFlownFlight.GRAVITY / (HandFlownFlight.GRAVITY + type.getAcceleration());
    }

    private static void stop(Minecraft minecraft, EntityPlayerSP player) {
        drone.stopLocalPiloting();
        if (minecraft.getRenderViewEntity() == drone) {
            minecraft.setRenderViewEntity(player);
        }
        drone = null;
    }

    private static HandFlownFlight.Input readInput(Minecraft minecraft, EntityPlayerSP player) {
        double mouseX = player.rotationYaw - heldYaw;
        double mouseY = heldPitch - player.rotationPitch; // mouse forward lowers pitch; we want that as nose-down
        if (ModConfig.invertMousePitch) {
            mouseY = -mouseY;
        }
        player.rotationYaw = heldYaw;
        player.prevRotationYaw = heldYaw;
        player.rotationPitch = heldPitch;
        player.prevRotationPitch = heldPitch;

        if (DroneKeys.FLIGHT_MODE.isPressed()) {
            HandFlownFlight.Mode[] modes = HandFlownFlight.Mode.values();
            if (mode == HandFlownFlight.Mode.HOVER) {
                throttle = hoverThrottle(); // leaving altitude hold: start from a throttle that keeps it level
            }
            mode = modes[(mode.ordinal() + 1) % modes.length];
            status(player, "message.combat_drones.mode_" + mode.name().toLowerCase(Locale.ROOT));
        }
        if (DroneKeys.CAMERA.isPressed()) {
            status(player, "message.combat_drones.camera_" + CameraHandler.cycleCameraMode().name().toLowerCase(Locale.ROOT));
        }
        if (DroneKeys.MOTORS.isPressed()) {
            motorsOn = !motorsOn;
            status(player, motorsOn ? "message.combat_drones.motors_on" : "message.combat_drones.motors_off");
        }

        double rollInput;
        double pitchInput;
        if (mode == HandFlownFlight.Mode.ACRO) {
            rollInput = mouseX / ACRO_MOUSE_FULL_STICK;
            pitchInput = mouseY / ACRO_MOUSE_FULL_STICK;
        } else {
            stickRoll = clamp(stickRoll * (1 - STICK_SPRING) + mouseX * ANGLE_MOUSE_GAIN);
            stickPitch = clamp(stickPitch * (1 - STICK_SPRING) + mouseY * ANGLE_MOUSE_GAIN);
            rollInput = stickRoll;
            pitchInput = stickPitch;
        }
        // Arrow keys are a full-deflection stick on top of the mouse
        rollInput = clamp(rollInput + axis(DroneKeys.ROLL_RIGHT.isKeyDown(), DroneKeys.ROLL_LEFT.isKeyDown()));
        pitchInput = clamp(pitchInput + axis(DroneKeys.PITCH_FORWARD.isKeyDown(), DroneKeys.PITCH_BACK.isKeyDown()));

        GameSettings settings = minecraft.gameSettings;
        boolean up = settings.keyBindForward.isKeyDown();
        boolean down = settings.keyBindBack.isKeyDown();
        if (up) {
            throttle = Math.min(1, throttle + THROTTLE_STEP);
        }
        if (down) {
            throttle = Math.max(0, throttle - THROTTLE_STEP);
        }
        double vertical = axis(up, down);
        double yaw = axis(settings.keyBindRight.isKeyDown(), settings.keyBindLeft.isKeyDown());
        return new HandFlownFlight.Input(throttle, vertical, pitchInput, rollInput, yaw, mode, motorsOn);
    }

    private static double axis(boolean positive, boolean negative) {
        return positive == negative ? 0 : positive ? 1 : -1;
    }

    private static double clamp(double value) {
        return Math.max(-1, Math.min(1, value));
    }

    private static void status(EntityPlayerSP player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
    }

    /** W/A/S/D fly the drone, not the body. */
    @SubscribeEvent
    public static void onInputUpdate(InputUpdateEvent event) {
        if (drone == null) {
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
     * Clicks and scrolling would act on whatever the drone camera looks at (vanilla raytraces from the view entity),
     * so they're swallowed while flying. Mouse movement (button -1, no wheel) passes through for the sticks.
     */
    @SubscribeEvent
    public static void onMouse(MouseEvent event) {
        if (drone != null && (event.getButton() != -1 || event.getDwheel() != 0)) {
            event.setCanceled(true);
        }
    }
}
