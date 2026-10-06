package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import javax.vecmath.AxisAngle4d;

/** Fixed-wing unit drawn with its type's geo model, rotated by heading, pitch and synced bank. */
public class RenderRampUnit extends Render<EntityRampUnit> {
    /** Prop speed with the engine running, radians per tick (blurred). */
    private static final float PROP_SPEED = 2.6F;

    public RenderRampUnit(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0.5F;
    }

    @Override
    public void doRender(EntityRampUnit unit, double x, double y, double z, float entityYaw, float partialTicks) {
        DroneType type = unit.getDroneType();
        if (type == null) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + unit.height / 2, z);
        AxisAngle4d rotation = new AxisAngle4d();
        rotation.set(unit.renderOrientation(partialTicks));
        GlStateManager.rotate((float) Math.toDegrees(rotation.angle), (float) rotation.x, (float) rotation.y, (float) rotation.z);
        String model = GeoModels.nameFor(type);
        float propSpeed = UnitEngineSound.isRunning(unit) ? PROP_SPEED : 0;
        GeoModels.spinProps(model, GeoModels.propAngle(unit, propSpeed, partialTicks), propSpeed);
        float tint = unit.hitFlashTicks > 0 ? 0.35F : 1.0F; // hit flash, like mobs turning red
        GeoModels.render(model, 1.0F, tint, tint);
        GlStateManager.popMatrix();
        super.doRender(unit, x, y, z, entityYaw, partialTicks);
    }

    /** Draws a ramp unit's model unrotated at the GL origin (used on the launch rail). */
    static void renderStill(DroneType type) {
        String model = GeoModels.nameFor(type);
        GeoModels.spinProps(model, 0, 0);
        GeoModels.render(model, 1.0F, 1.0F, 1.0F);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityRampUnit unit) {
        return null; // GeoModels binds the per-type texture
    }
}
