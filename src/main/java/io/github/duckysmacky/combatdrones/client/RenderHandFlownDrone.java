package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.BombType;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nullable;
import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;
import java.util.Map;
import java.util.WeakHashMap;
import org.lwjgl.opengl.GL11;
import javax.vecmath.Quat4d;

/** Multirotor drawn with its type's geo model, plus whatever payload it carries hanging under the belly. */
public class RenderHandFlownDrone extends Render<EntityHandFlownDrone> {
    /** Rotor speed at full thrust and at idle, radians per tick. */
    private static final float ROTOR_SPEED_MAX = 3.2F;
    private static final float ROTOR_SPEED_IDLE = 1.4F;
    /** Spacing of hanging bombs / grenades, blocks. */
    private static final float PAYLOAD_SPACING = 0.11F;
    /** Bombs hang on a short line that pays out once the motors run, and swing behind the drone's acceleration. */
    private static final float LINE_LENGTH = 0.16F;
    private static final double SWING_PUSH = 120;
    private static final double SWING_SPRING = 0.12;
    private static final double SWING_DAMPING = 0.9;
    private static final double SWING_LIMIT = 55;
    /** Per drone: {tick, velocity x, velocity z, angle x, angle z, rate x, rate z, line paid out 0..1}. */
    private static final Map<EntityHandFlownDrone, double[]> SWING = new WeakHashMap<>();

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
        Quat4d orientation = renderOrientation(drone, partialTicks);
        GlStateManager.pushMatrix();
        AxisAngle4d rotation = new AxisAngle4d();
        rotation.set(orientation);
        GlStateManager.rotate((float) Math.toDegrees(rotation.angle), (float) rotation.x, (float) rotation.y, (float) rotation.z);
        String model = GeoModels.nameFor(type);
        boolean armed = drone.areMotorsOn();
        float rotorSpeed = armed ? ROTOR_SPEED_IDLE + (ROTOR_SPEED_MAX - ROTOR_SPEED_IDLE) * drone.getThrust() : 0;
        GeoModels.spinProps(model, GeoModels.propAngle(drone, rotorSpeed, partialTicks), GeoModels.propSpeed(drone));
        // Status lamps: on with the motors, the rear pair blinking
        GeoModels.show(model, "glow_led", armed);
        if (armed && (drone.ticksExisted + partialTicks) % 20 >= 10) {
            GeoModels.show(model, "glow_led_rear", false);
        }
        float tint = drone.hitFlashTicks > 0 ? 0.35F : 1.0F; // hit flash, like mobs turning red
        GeoModels.render(model, 1.0F, tint, tint);
        float belly = GeoModels.bounds(model)[1];
        boolean dangling = type.getPayload() == DroneType.PayloadKind.BOMB;
        if (!dangling) {
            renderPayload(drone, type, belly, tint);
        }
        GlStateManager.popMatrix();
        if (dangling && drone.getPayloadCount() > 0) {
            renderDangling(drone, type, orientation, belly, tint, partialTicks);
        }
        GlStateManager.popMatrix();
        super.doRender(drone, x, y, z, entityYaw, partialTicks);
    }

    /**
     * Bombs on a line under the belly, hanging straight down whatever the drone's tilt and swinging back against its
     * acceleration. Drawn in world axes from the hang point; the swing is client-only eye candy.
     */
    private static void renderDangling(EntityHandFlownDrone drone, DroneType type, Quat4d orientation, float belly, float tint, float partialTicks) {
        double[] swing = SWING.computeIfAbsent(drone, key -> new double[8]);
        if (swing[0] != drone.ticksExisted) {
            double velocityX = drone.posX - drone.prevPosX;
            double velocityZ = drone.posZ - drone.prevPosZ;
            swing[5] = (swing[5] - (velocityX - swing[1]) * SWING_PUSH - swing[3] * SWING_SPRING) * SWING_DAMPING;
            swing[6] = (swing[6] - (velocityZ - swing[2]) * SWING_PUSH - swing[4] * SWING_SPRING) * SWING_DAMPING;
            swing[3] = MathHelper.clamp(swing[3] + swing[5], -SWING_LIMIT, SWING_LIMIT);
            swing[4] = MathHelper.clamp(swing[4] + swing[6], -SWING_LIMIT, SWING_LIMIT);
            swing[7] += ((drone.areMotorsOn() ? 1 : 0) - swing[7]) * 0.15;
            swing[0] = drone.ticksExisted;
            swing[1] = velocityX;
            swing[2] = velocityZ;
        }
        Matrix3d turn = new Matrix3d();
        turn.set(orientation);
        Vector3d hang = new Vector3d(0, belly, 0);
        turn.transform(hang);
        float line = LINE_LENGTH * (float) swing[7];
        float yaw = drone.prevRotationYaw + MathHelper.wrapDegrees(drone.rotationYaw - drone.prevRotationYaw) * partialTicks;
        GlStateManager.pushMatrix();
        GlStateManager.translate(hang.x, hang.y, hang.z);
        GlStateManager.rotate((float) swing[3], 0, 0, 1);
        GlStateManager.rotate((float) -swing[4], 1, 0, 0);
        if (line > 0.01F) {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.glLineWidth(2.0F);
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(0, 0, 0).color(0.09F, 0.1F, 0.11F, 1.0F).endVertex();
            buffer.pos(0, -line, 0).color(0.09F, 0.1F, 0.11F, 1.0F).endVertex();
            Tessellator.getInstance().draw();
            GlStateManager.glLineWidth(1.0F);
            GlStateManager.enableLighting();
            GlStateManager.enableTexture2D();
        }
        GlStateManager.rotate(-yaw, 0, 1, 0);
        renderPayload(drone, type, -line, tint);
        GlStateManager.popMatrix();
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
