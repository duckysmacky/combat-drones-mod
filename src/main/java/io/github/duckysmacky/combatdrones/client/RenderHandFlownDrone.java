package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.BombType;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nullable;
import javax.vecmath.AxisAngle4d;
import javax.vecmath.Quat4d;

/** Multirotor drawn with its type's geo model, plus whatever payload it carries hanging under the belly. */
public class RenderHandFlownDrone extends Render<EntityHandFlownDrone> {
    /** Rotor speed at full thrust and at idle, radians per tick. */
    private static final float ROTOR_SPEED_MAX = 3.2F;
    private static final float ROTOR_SPEED_IDLE = 1.4F;
    /** Spacing of hanging bombs / grenades, blocks. */
    private static final float PAYLOAD_SPACING = 0.11F;

    public RenderHandFlownDrone(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0.35F;
    }

    @Override
    public void doRender(EntityHandFlownDrone drone, double x, double y, double z, float entityYaw, float partialTicks) {
        DroneType type = drone.getDroneType();
        if (type == null) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + drone.height / 2, z);
        AxisAngle4d rotation = new AxisAngle4d();
        rotation.set(renderOrientation(drone, partialTicks));
        GlStateManager.rotate((float) Math.toDegrees(rotation.angle), (float) rotation.x, (float) rotation.y, (float) rotation.z);
        String model = GeoModels.nameFor(type);
        float rotorSpeed = drone.areMotorsOn() ? ROTOR_SPEED_IDLE + (ROTOR_SPEED_MAX - ROTOR_SPEED_IDLE) * drone.getThrust() : 0;
        GeoModels.spinProps(model, GeoModels.propAngle(drone, rotorSpeed, partialTicks), rotorSpeed);
        float tint = drone.hitFlashTicks > 0 ? 0.35F : 1.0F; // hit flash, like mobs turning red
        GeoModels.render(model, 1.0F, tint, tint);
        renderPayload(drone, type, GeoModels.bounds(model)[1], tint);
        GlStateManager.popMatrix();
        super.doRender(drone, x, y, z, entityYaw, partialTicks);
    }

    /**
     * Loaded payload hangs under the belly: bombs nose down, up to four in a 2x2 grid, two side by side, or one in
     * the middle; grenades nose forward in a row.
     */
    private static void renderPayload(EntityHandFlownDrone drone, DroneType type, float belly, float tint) {
        int count = drone.getPayloadCount();
        if (count <= 0) {
            return;
        }
        BombType bomb = drone.getPayloadBomb();
        boolean grenade = type.getPayload() == DroneType.PayloadKind.GRENADE;
        if (bomb == null && !grenade) {
            return;
        }
        String model = grenade ? "frag_grenade" : bomb.itemName();
        float[] bounds = GeoModels.bounds(model);
        for (int index = 0; index < count; index++) {
            GlStateManager.pushMatrix();
            if (grenade) {
                GlStateManager.translate((index - (count - 1) / 2.0F) * PAYLOAD_SPACING, belly - bounds[4], 0);
            } else {
                float offsetX = count == 1 ? 0 : (index % 2 == 0 ? -PAYLOAD_SPACING : PAYLOAD_SPACING);
                float offsetZ = count <= 2 ? 0 : (index < 2 ? -PAYLOAD_SPACING : PAYLOAD_SPACING);
                // Nose down: the tail end (min Z) meets the belly
                GlStateManager.translate(offsetX, belly + bounds[2], offsetZ);
                GlStateManager.rotate(90, 1, 0, 0);
            }
            GeoModels.render(model, 1.0F, tint, tint);
            GlStateManager.popMatrix();
        }
    }

    /** The pilot sees its own prediction; everyone else interpolates the tracked yaw/pitch/roll. */
    private static Quat4d renderOrientation(EntityHandFlownDrone drone, float partialTicks) {
        if (drone.isLocallyPiloted()) {
            return HandFlownFlight.slerp(drone.prevOrientation, drone.getFlightState().orientation, partialTicks);
        }
        float yaw = drone.prevRotationYaw + MathHelper.wrapDegrees(drone.rotationYaw - drone.prevRotationYaw) * partialTicks;
        float pitch = drone.prevRotationPitch + (drone.rotationPitch - drone.prevRotationPitch) * partialTicks;
        float roll = drone.prevRoll + MathHelper.wrapDegrees(drone.getRoll() - drone.prevRoll) * partialTicks;
        return HandFlownFlight.fromEuler(yaw, pitch, roll);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityHandFlownDrone drone) {
        return null; // GeoModels binds the per-type texture
    }
}
