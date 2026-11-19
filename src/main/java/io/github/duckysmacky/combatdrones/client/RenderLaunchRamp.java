package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.Feedback;
import io.github.duckysmacky.combatdrones.entity.EntityLaunchRamp;
import io.github.duckysmacky.combatdrones.entity.EntityRunway;
import io.github.duckysmacky.combatdrones.physics.RunwayOps;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

/**
 * Catapult rail ({@code geo/launch_ramp}) with the loaded unit sitting on its carriage, or a runway
 * ({@code geo/light_runway}, {@code geo/heavy_runway}) with its plane parked level on the deck.
 */
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
    /** How far the carriage runs from the seat to the end stop (pixels on the 4-block design), and its timing in ticks. */
    private static final float CARRIAGE_TRAVEL = 25.0F;
    private static final float SHOT_TICKS = 3.0F;
    private static final float HOLD_TICKS = 30.0F;
    private static final float RETURN_TICKS = 40.0F;
    /** Per ramp: when (entity ticks) it last launched. */
    private static final Map<EntityLaunchRamp, Float> LAUNCHED = new WeakHashMap<>();

    public RenderLaunchRamp(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 1.0F;
    }

    @Override
    public void doRender(EntityLaunchRamp ramp, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-ramp.rotationYaw, 0, 1, 0); // nose (+Z) along the ramp's heading
        float settle = Feedback.settle(ramp, ramp.handledAt, partialTicks); // just loaded, fuelled or armed
        GlStateManager.scale(1 + settle * 0.02F, 1 - settle * 0.05F, 1 + settle * 0.02F);
        EntityLaunchRamp.Kind kind = ramp.getKind();
        DroneType unit = ramp.getUnitType();
        if (ramp instanceof EntityRunway) {
            renderRunway((EntityRunway) ramp, kind, unit, partialTicks);
            GlStateManager.popMatrix();
            super.doRender(ramp, x, y, z, entityYaw, partialTicks);
            return;
        }
        GeoModels.reset(kind.getName());
        GeoModels.show(kind.getName(), "glow_ready", unit != null);
        GeoModels.offset(kind.getName(), "carriage", 0, 0, -CARRIAGE_TRAVEL * kind.railScale() * carriageRun(ramp, partialTicks));
        GeoModels.render(kind.getName(), 1.0F, 1.0F, 1.0F);
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

    /**
     * The strip, lamps lit, and the parked plane on its seat. Just after a landing the plane taxis back to the seat
     * from where its roll-out stopped.
     */
    private static void renderRunway(EntityRunway runway, EntityLaunchRamp.Kind kind, @Nullable DroneType unit, float partialTicks) {
        GeoModels.reset(kind.getName());
        // Knocked out by an EMP: lamps and terminal dark until it comes back
        boolean powered = !runway.isEmpDisabled();
        GeoModels.show(kind.getName(), "glow_edge", powered);
        GeoModels.show(kind.getName(), "glow_screen", powered);
        GeoModels.show(kind.getName(), "glow_ready", powered && unit != null);
        GeoModels.render(kind.getName(), 1.0F, 1.0F, 1.0F);
        if (unit == null) {
            return;
        }
        double length = kind.getLengthBlocks();
        double seat = RunwayOps.seat(runway.geometry());
        double taxi = Math.min(1, (runway.ticksExisted + partialTicks - runway.dockedAt) / TAXI_TICKS);
        double eased = taxi * taxi * (3 - 2 * taxi);
        double along = (length - 1) + (seat - (length - 1)) * eased;
        GlStateManager.translate(0, EntityRunway.DECK_HEIGHT - GeoModels.bounds(GeoModels.nameFor(unit))[1], -length / 2 + along);
        RenderRampUnit.renderStill(unit);
    }

    /** Ticks a landed plane takes to taxi back from the far end to its seat. */
    private static final float TAXI_TICKS = 60.0F;

    /** A catapult shot went off at this point: the nearest ramp's carriage runs down its rail. */
    static void launched(World world, Vec3d at) {
        EntityLaunchRamp nearest = null;
        for (EntityLaunchRamp ramp : world.getEntitiesWithinAABB(EntityLaunchRamp.class, new AxisAlignedBB(at, at).grow(6))) {
            if (nearest == null || ramp.getDistanceSq(at.x, at.y, at.z) < nearest.getDistanceSq(at.x, at.y, at.z)) {
                nearest = ramp;
            }
        }
        if (nearest != null) {
            LAUNCHED.put(nearest, (float) nearest.ticksExisted);
        }
    }

    /** 0 = carriage at the seat, 1 = against the end stop: shot forward, held, then wound back. */
    private static float carriageRun(EntityLaunchRamp ramp, float partialTicks) {
        Float launch = LAUNCHED.get(ramp);
        if (launch == null) {
            return 0;
        }
        float age = ramp.ticksExisted + partialTicks - launch;
        if (age < 0 || age >= SHOT_TICKS + HOLD_TICKS + RETURN_TICKS) {
            return 0;
        }
        return age < SHOT_TICKS ? age / SHOT_TICKS : age < SHOT_TICKS + HOLD_TICKS ? 1 : 1 - (age - SHOT_TICKS - HOLD_TICKS) / RETURN_TICKS;
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityLaunchRamp ramp) {
        return null; // GeoModels binds the texture
    }
}
