package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.client.hud.Feed;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

/**
 * The pilot's own propellers in the camera picture. Vanilla never draws the entity you're looking out of, so an FPV
 * feed would show nothing of the aircraft; this draws just its spinning props (the body would fill the lens), a
 * little ahead of the camera so they sit at the edges of the frame, as on a real quad's or a twin's nose camera.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class FirstPersonProps {
    /** How far ahead of the camera the airframe is drawn (blocks): a racer's props, a plane's engines. */
    private static final double RACER_AHEAD = 0.15;
    private static final double PLANE_AHEAD = 0.5;
    private static final float ROTOR_SPEED_MAX = 3.2F;
    private static final float ROTOR_SPEED_IDLE = 1.4F;
    private static final float PROP_SPEED = 2.6F;

    private FirstPersonProps() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        Feed feed = HudOverlay.currentFeed();
        if (minecraft.gameSettings.thirdPersonView != 0 || (feed != Feed.ANALOG && feed != Feed.FLIGHT)) {
            return;
        }
        float partialTicks = event.getPartialTicks();
        EntityHandFlownDrone drone = PilotController.getPilotedDrone();
        EntityRampUnit unit = RemoteController.getUnit();
        if (feed == Feed.ANALOG && drone != null && drone.getDroneType() != null) {
            float speed = drone.areMotorsOn() ? ROTOR_SPEED_IDLE + (ROTOR_SPEED_MAX - ROTOR_SPEED_IDLE) * drone.getThrust() : 0;
            draw(drone, drone.getDroneType(), HandFlownFlight.slerp(drone.prevOrientation, drone.getFlightState().orientation, partialTicks),
                    speed, RACER_AHEAD, partialTicks);
        } else if (feed == Feed.FLIGHT && unit != null && unit.getDroneType() != null && RemoteController.isStickControl()) {
            draw(unit, unit.getDroneType(), unit.renderOrientation(partialTicks), UnitEngineSound.isRunning(unit) ? PROP_SPEED : 0, PLANE_AHEAD,
                    partialTicks);
        }
    }

    private static void draw(Entity entity, DroneType type, Quat4d orientation, float propSpeed, double ahead, float partialTicks) {
        String model = GeoModels.nameFor(type);
        GeoModels.spinProps(model, GeoModels.propAngle(entity, propSpeed, partialTicks), GeoModels.propSpeed(entity));
        if (GeoModels.showOnly(model, "hub_") == 0) {
            return;
        }
        Matrix3d turn = new Matrix3d();
        turn.set(orientation);
        Vector3d forward = new Vector3d(0, 0, ahead);
        turn.transform(forward);
        AxisAngle4d rotation = new AxisAngle4d();
        rotation.set(orientation);

        Minecraft minecraft = Minecraft.getMinecraft();
        int light = entity.getBrightnessForRender();
        minecraft.entityRenderer.enableLightmap();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536.0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        // World-last draws relative to the view entity's own position: its feet are the origin
        GlStateManager.translate(forward.x, entity.height / 2 + forward.y, forward.z);
        GlStateManager.rotate((float) Math.toDegrees(rotation.angle), (float) rotation.x, (float) rotation.y, (float) rotation.z);
        GeoModels.render(model, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        minecraft.entityRenderer.disableLightmap();
    }
}
