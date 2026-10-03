package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityLaunchRamp;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/** Placeholder 3-block launch rail (base frame, inclined rail, support legs) with the loaded unit sitting on it. */
public class RenderLaunchRamp extends Render<EntityLaunchRamp> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(CombatDrones.MOD_ID, "textures/entity/launch_ramp.png");
    private static final float PIXEL = 1.0F / 16.0F;
    /** Rail incline, nose up (degrees). */
    static final float RAIL_ANGLE = 15.0F;

    private final RampModel model = new RampModel();
    private final RenderRampUnit.PlaneModel unitModel = new RenderRampUnit.PlaneModel();

    public RenderLaunchRamp(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0.8F;
    }

    @Override
    public void doRender(EntityLaunchRamp ramp, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-ramp.rotationYaw, 0, 1, 0); // nose (+Z) along the ramp's heading
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        bindEntityTexture(ramp);
        model.render(ramp, 0, 0, 0, 0, 0, PIXEL);
        if (ramp.isLoaded()) {
            // Unit sits on the rail, pitched up with it (model space is upside down: rotate the other way)
            GlStateManager.translate(0, -14 * PIXEL, 2 * PIXEL);
            GlStateManager.rotate(RAIL_ANGLE, 1, 0, 0);
            bindTexture(RenderRampUnit.TEXTURE);
            unitModel.render(ramp, 0, 0, 0, 0, 0, PIXEL);
        }
        GlStateManager.popMatrix();
        super.doRender(ramp, x, y, z, entityYaw, partialTicks);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityLaunchRamp ramp) {
        return TEXTURE;
    }

    /** Model space: y grows downward, nose toward +Z. Ground is y = 0. */
    private static final class RampModel extends ModelBase {
        private final ModelRenderer base;
        private final ModelRenderer rail;

        RampModel() {
            textureWidth = 64;
            textureHeight = 64;
            base = new ModelRenderer(this, 0, 0);
            base.addBox(-6, -2, -24, 12, 2, 48);
            rail = new ModelRenderer(this, 0, 0);
            rail.setRotationPoint(0, -4, -22);
            rail.addBox(-2, -2, 0, 4, 2, 46);
            rail.rotateAngleX = (float) Math.toRadians(RAIL_ANGLE); // rises toward the nose
            // Support legs under the raised end
            ModelRenderer leg = new ModelRenderer(this, 0, 0);
            leg.addBox(-5, -12, 18, 2, 10, 2);
            leg.addBox(3, -12, 18, 2, 10, 2);
            base.addChild(leg);
        }

        @Override
        public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
            base.render(scale);
            rail.render(scale);
        }
    }
}
