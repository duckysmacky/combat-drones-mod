package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.vecmath.Quat4d;

/** FPV and remote views: full drone attitude (including roll) on the camera, no hand. The pilot's stand-in body shows where they stand. */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class CameraHandler {
    /** Racing quads tilt the camera up so it looks ahead while the drone pitches forward to fly. */
    private static final Quat4d CAMERA_UPTILT = HandFlownFlight.fromEuler(0, -20, 0);

    private CameraHandler() {
    }

    @SubscribeEvent
    public static void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        EntityRampUnit remoteUnit = RemoteController.getUnit();
        if (remoteUnit != null && event.getEntity() == remoteUnit) {
            remoteCamera(event, remoteUnit);
            return;
        }
        EntityHandFlownDrone drone = PilotController.getPilotedDrone();
        if (drone == null || event.getEntity() != drone) {
            return;
        }
        // Vanilla would interpolate the entity's yaw/pitch fields; use the predicted quaternion instead (no gimbal
        // issues, and it carries roll)
        Quat4d view = HandFlownFlight.slerp(drone.prevOrientation, drone.getFlightState().orientation, event.getRenderPartialTicks());
        double[] euler;
        if (PilotController.isDji()) {
            PilotController.updateGimbal();
            // Gimbal-stabilized: follows the heading, pilot-controlled tilt (straight down for bombing/scouting),
            // never rolls with the airframe. FPV drones only get their fixed forward camera.
            euler = new double[] {HandFlownFlight.headingYaw(view), PilotController.getGimbalPitch(), 0};
        } else {
            view.mul(CAMERA_UPTILT);
            euler = HandFlownFlight.toEuler(view);
        }
        event.setYaw((float) euler[0] + 180.0F); // vanilla's camera yaw convention is offset by 180
        event.setPitch((float) euler[1]);
        event.setRoll((float) euler[2]);
    }

    /** Zoomed look camera: narrower field of view. */
    @SubscribeEvent
    public static void onFov(EntityViewRenderEvent.FOVModifier event) {
        EntityRampUnit unit = RemoteController.getUnit();
        if (unit != null && event.getEntity() == unit && RemoteController.getZoom() > 1) {
            event.setFOV(event.getFOV() / RemoteController.getZoom());
        }
    }

    /**
     * Ramp unit camera: under stick control it's fixed to the airframe and banks with it; otherwise it's a stabilized
     * gimbal pointing where the mouse aimed it in world terms (it holds on to its target, never rolls).
     */
    private static void remoteCamera(EntityViewRenderEvent.CameraSetup event, EntityRampUnit unit) {
        float partialTicks = (float) event.getRenderPartialTicks();
        RemoteController.updateLook(partialTicks);
        if (RemoteController.isStickControl()) {
            // Through the airframe's orientation, so a missile's near-vertical dive (where yaw is meaningless) is smooth
            double[] euler = HandFlownFlight.toEuler(unit.renderOrientation(partialTicks));
            event.setYaw((float) euler[0] + 180.0F); // vanilla's camera yaw convention is offset by 180
            event.setPitch((float) euler[1]);
            event.setRoll((float) euler[2]);
        } else {
            event.setYaw(RemoteController.getCameraYaw() + 180.0F);
            event.setPitch(RemoteController.getCameraPitch());
            event.setRoll(0);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (PilotController.getPilotedDrone() != null || RemoteController.isActive()) {
            event.setCanceled(true);
        }
    }
}
