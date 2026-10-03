package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
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

/** Placeholder fixed-wing model (fuselage, wings, tail, pusher prop) until the art pass. */
public class RenderRampUnit extends Render<EntityRampUnit> {
    static final ResourceLocation TEXTURE = new ResourceLocation(CombatDrones.MOD_ID, "textures/entity/ramp_unit.png");
    private static final float PIXEL = 1.0F / 16.0F;

    private final PlaneModel model = new PlaneModel();

    public RenderRampUnit(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0.4F;
    }

    @Override
    public void doRender(EntityRampUnit unit, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + unit.height / 2, z);
        float yaw = unit.prevRotationYaw + MathHelper.wrapDegrees(unit.rotationYaw - unit.prevRotationYaw) * partialTicks;
        float pitch = unit.prevRotationPitch + (unit.rotationPitch - unit.prevRotationPitch) * partialTicks;
        float bank = unit.prevBank + MathHelper.wrapDegrees(unit.getBank() - unit.prevBank) * partialTicks;
        AxisAngle4d rotation = new AxisAngle4d();
        rotation.set(HandFlownFlight.fromEuler(yaw, pitch, bank));
        GlStateManager.rotate((float) Math.toDegrees(rotation.angle), (float) rotation.x, (float) rotation.y, (float) rotation.z);
        // Entity models are authored upside down (y grows downward)
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        bindEntityTexture(unit);
        if (unit.hitFlashTicks > 0) {
            GlStateManager.color(1.0F, 0.35F, 0.35F);
        }
        model.render(unit, 0, 0, 0, 0, 0, PIXEL);
        GlStateManager.color(1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        super.doRender(unit, x, y, z, entityYaw, partialTicks);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityRampUnit unit) {
        return TEXTURE;
    }

    /** Nose points to +Z (the body frame's forward), so the entity's yaw/pitch/bank apply directly. */
    static final class PlaneModel extends ModelBase {
        private final ModelRenderer fuselage;
        private final ModelRenderer wings;
        private final ModelRenderer tail;

        PlaneModel() {
            textureWidth = 64;
            textureHeight = 32;
            fuselage = new ModelRenderer(this, 0, 0);
            fuselage.addBox(-1.5F, -1.5F, -8, 3, 3, 16);
            wings = new ModelRenderer(this, 0, 20);
            wings.addBox(-12, -0.5F, -1, 24, 1, 4);
            tail = new ModelRenderer(this, 38, 0);
            tail.addBox(-5, -0.5F, -8, 10, 1, 2);
            tail.addBox(-0.5F, -4.5F, -8, 1, 4, 2);
            tail.addBox(-3, -0.5F, -9.5F, 6, 1, 1); // pusher prop disc, edge-on
        }

        @Override
        public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
            fuselage.render(scale);
            wings.render(scale);
            tail.render(scale);
        }
    }
}
