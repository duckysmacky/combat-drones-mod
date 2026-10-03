package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nullable;
import javax.vecmath.AxisAngle4d;
import javax.vecmath.Quat4d;

/** Placeholder quad model (body, X-frame, 4 motors) until the art pass replaces it. */
public class RenderHandFlownDrone extends Render<EntityHandFlownDrone> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(CombatDrones.MOD_ID, "textures/entity/drone.png");
    private static final float PIXEL = 1.0F / 16.0F;

    private final DroneModel model = new DroneModel();

    public RenderHandFlownDrone(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0.3F;
    }

    @Override
    public void doRender(EntityHandFlownDrone drone, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + drone.height / 2, z);
        AxisAngle4d rotation = new AxisAngle4d();
        rotation.set(renderOrientation(drone, partialTicks));
        GlStateManager.rotate((float) Math.toDegrees(rotation.angle), (float) rotation.x, (float) rotation.y, (float) rotation.z);
        // Entity models are authored upside down (y grows downward)
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        bindEntityTexture(drone);
        model.render(drone, 0, 0, 0, 0, 0, PIXEL);
        GlStateManager.popMatrix();
        super.doRender(drone, x, y, z, entityYaw, partialTicks);
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
        return TEXTURE;
    }

    private static final class DroneModel extends ModelBase {
        private final ModelRenderer body;
        private final ModelRenderer frame;

        DroneModel() {
            textureWidth = 64;
            textureHeight = 32;
            body = new ModelRenderer(this, 0, 0);
            body.addBox(-3, -1.5F, -4, 6, 3, 8);
            frame = new ModelRenderer(this, 0, 16);
            frame.addBox(-8, -0.5F, -0.5F, 16, 1, 1);
            frame.addBox(-0.5F, -0.5F, -8, 1, 1, 16);
            frame.rotateAngleY = (float) Math.toRadians(45);
            for (int corner = 0; corner < 4; corner++) {
                ModelRenderer motor = new ModelRenderer(this, 28, 0);
                motor.addBox(-1.5F, -2, -1.5F, 3, 2, 3);
                motor.setRotationPoint(corner < 2 ? -7 : 7, -0.5F, corner % 2 == 0 ? -7 : 7);
                frame.addChild(motor);
            }
        }

        @Override
        public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
            body.render(scale);
            frame.render(scale);
        }
    }
}
