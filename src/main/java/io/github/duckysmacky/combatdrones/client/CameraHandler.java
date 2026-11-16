package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.client.hud.StingerScope;
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
    /**
     * The direction this frame's camera looks when it's one of ours (remote unit or FPV): vanilla's render manager still
     * has the body's held rotation then, so billboards drawn in the world have to face this instead.
     */
    private static boolean customView;
    private static float viewYaw;
    private static float viewPitch;

    private CameraHandler() {
    }

    @SubscribeEvent
    public static void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
        customView = false;
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
        aim(event, (float) euler[0], (float) euler[1], (float) euler[2]);
    }

    /** Points the camera (yaw/pitch in entity terms) and remembers where, for {@link #isCustomView}. */
    private static void aim(EntityViewRenderEvent.CameraSetup event, float yaw, float pitch, float roll) {
        event.setYaw(yaw + 180.0F); // vanilla's camera yaw convention is offset by 180
        event.setPitch(pitch);
        event.setRoll(roll);
        customView = true;
        viewYaw = yaw;
        viewPitch = pitch;
    }

    /** True while this frame's camera is a remote unit's or an FPV drone's (see {@link #getViewYaw}). */
    public static boolean isCustomView() {
        return customView;
    }

    public static float getViewYaw() {
        return viewYaw;
    }

    public static float getViewPitch() {
        return viewPitch;
    }

    /** Zoomed look camera: narrower field of view. */
    @SubscribeEvent
    public static void onFov(EntityViewRenderEvent.FOVModifier event) {
        EntityRampUnit unit = RemoteController.getUnit();
        if (unit != null && event.getEntity() == unit && RemoteController.getZoom() > 1) {
            event.setFOV(event.getFOV() / RemoteController.getZoom());
        }
        // Through the Stinger's scope, easing in as it comes up
        float scope = StingerController.scopeAmount((float) event.getRenderPartialTicks());
        if (scope > 0) {
            event.setFOV(event.getFOV() / (1 + (StingerController.ZOOM - 1) * scope));
        }
        StingerScope.setFov(event.getFOV());
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
            aim(event, (float) euler[0], (float) euler[1], (float) euler[2]);
        } else {
            aim(event, RemoteController.getCameraYaw(), RemoteController.getCameraPitch(), 0);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (PilotController.getPilotedDrone() != null || RemoteController.isActive() || StingerController.scopeAmount(event.getPartialTicks()) > 0.5F) {
            event.setCanceled(true);
        }
    }
}
