package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.BombType;
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
import java.util.EnumMap;

/** Placeholder quad model (body, X-frame, 4 motors) until the art pass replaces it. */
public class RenderHandFlownDrone extends Render<EntityHandFlownDrone> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(CombatDrones.MOD_ID, "textures/entity/drone.png");
    private static final float PIXEL = 1.0F / 16.0F;

    private final DroneModel model = new DroneModel();
    private final EnumMap<BombType, BombModel> bombModels = new EnumMap<>(BombType.class);

    public RenderHandFlownDrone(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0.3F;
        bombModels.put(BombType.LIGHT, new BombModel(2, 4));
        bombModels.put(BombType.MEDIUM, new BombModel(3, 5));
        bombModels.put(BombType.HEAVY, new BombModel(4, 7));
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
        if (drone.hitFlashTicks > 0) {
            GlStateManager.color(1.0F, 0.35F, 0.35F); // hit flash, like mobs turning red
        }
        model.render(drone, 0, 0, 0, 0, 0, PIXEL);
        renderBombs(drone);
        GlStateManager.color(1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        super.doRender(drone, x, y, z, entityYaw, partialTicks);
    }

    /** Loaded bombs hang under the body: up to four in a 2x2 grid, two side by side, or one in the middle. */
    private void renderBombs(EntityHandFlownDrone drone) {
        BombType bomb = drone.getPayloadBomb();
        int count = drone.getPayloadCount();
        if (bomb == null || count <= 0) {
            return;
        }
        BombModel bombModel = bombModels.get(bomb);
        bindTexture(new ResourceLocation(CombatDrones.MOD_ID, "textures/items/" + bomb.itemName() + ".png"));
        for (int index = 0; index < count; index++) {
            float offsetX = count == 1 ? 0 : (index % 2 == 0 ? -2.0F : 2.0F);
            float offsetZ = count <= 2 ? 0 : (index < 2 ? -2.0F : 2.0F);
            bombModel.render(offsetX, offsetZ, PIXEL);
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
        return TEXTURE;
    }

    /** One bomb hanging from the bay; textured with the bomb's item sprite (placeholder art). */
    private static final class BombModel extends ModelBase {
        private final ModelRenderer bomb;

        BombModel(int width, int length) {
            textureWidth = 16;
            textureHeight = 16;
            bomb = new ModelRenderer(this, 0, 0);
            // Model space here is upside down (y grows downward), so the bomb hangs below the body (y = 1.5)
            bomb.addBox(-width / 2.0F, 1.5F, -width / 2.0F, width, length, width);
        }

        void render(float offsetX, float offsetZ, float scale) {
            bomb.setRotationPoint(offsetX, 0, offsetZ);
            bomb.render(scale);
        }
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
