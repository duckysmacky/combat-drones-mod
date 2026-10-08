package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.EntityLaunchRamp;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/** Catapult rail ({@code geo/launch_ramp} or {@code geo/plane_ramp}) with the loaded unit sitting on its carriage. */
public class RenderLaunchRamp extends Render<EntityLaunchRamp> {
    private static final float PIXEL = 1.0F / 16.0F;
    /** Rail incline, nose up (degrees). Must match the rail bone in launch_ramp.geo.json. */
    static final float RAIL_ANGLE = 15.0F;
    /**
     * Rail top surface at its back end, in pixels: height above ground and distance behind the ramp centre. Along-rail
     * measurements are for the 4-block design and scale with the ramp's length.
     */
    private static final float RAIL_BACK_HEIGHT = 7.0F;
    private static final float RAIL_BACK_OFFSET = 30.0F;
    /** Where along the rail (pixels from the back end) the loaded unit's centre sits, and the carriage height. */
    private static final float SEAT_ALONG = 34.0F;
    private static final float CARRIAGE_HEIGHT = 2.0F;

    public RenderLaunchRamp(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 1.0F;
    }

    @Override
    public void doRender(EntityLaunchRamp ramp, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-ramp.rotationYaw, 0, 1, 0); // nose (+Z) along the ramp's heading
        EntityLaunchRamp.Kind kind = ramp.getKind();
        GeoModels.render(kind.getName(), 1.0F, 1.0F, 1.0F);
        DroneType unit = ramp.getUnitType();
        if (unit != null) {
            double incline = Math.toRadians(RAIL_ANGLE);
            float seatAlong = SEAT_ALONG * kind.railScale();
            GlStateManager.translate(0, (RAIL_BACK_HEIGHT + Math.sin(incline) * seatAlong) * PIXEL,
                    (-RAIL_BACK_OFFSET * kind.railScale() + Math.cos(incline) * seatAlong) * PIXEL);
            GlStateManager.rotate(-RAIL_ANGLE, 1, 0, 0); // nose up
            // Sit the model's belly on the carriage, whatever its size
            GlStateManager.translate(0, CARRIAGE_HEIGHT * PIXEL - GeoModels.bounds(GeoModels.nameFor(unit))[1], 0);
            RenderRampUnit.renderStill(unit);
        }
        GlStateManager.popMatrix();
        super.doRender(ramp, x, y, z, entityYaw, partialTicks);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityLaunchRamp ramp) {
        return null; // GeoModels binds the texture
    }
}
