package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.vecmath.Quat4d;

/** FPV view: full drone attitude (including roll) on the camera, no hand, pilot body still drawn. */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class CameraHandler {
    /** Racing quads tilt the camera up so it looks ahead while the drone pitches forward to fly. */
    private static final Quat4d CAMERA_UPTILT = HandFlownFlight.fromEuler(0, -20, 0);

    private CameraHandler() {
    }

    @SubscribeEvent
    public static void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
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

    /**
     * Vanilla's RenderPlayer skips the local player whenever it isn't the camera entity, so a pilot would never see
     * their own body from the drone. Draw it ourselves, briefly pretending the player is the view entity.
     */
    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        if (PilotController.getPilotedDrone() == null || player == null || player.isInvisible()) {
            return;
        }
        RenderManager renderManager = minecraft.getRenderManager();
        Entity viewEntity = renderManager.renderViewEntity;
        renderManager.renderViewEntity = player;
        RenderHelper.enableStandardItemLighting();
        minecraft.entityRenderer.enableLightmap();
        renderManager.renderEntityStatic(player, event.getPartialTicks(), false);
        minecraft.entityRenderer.disableLightmap();
        RenderHelper.disableStandardItemLighting();
        renderManager.renderViewEntity = viewEntity;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (PilotController.getPilotedDrone() != null) {
            event.setCanceled(true);
        }
    }
}
