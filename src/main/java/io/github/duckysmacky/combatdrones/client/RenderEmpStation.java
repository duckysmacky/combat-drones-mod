package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.client.fx.EmpField;
import io.github.duckysmacky.combatdrones.entity.EntityEmpStation;
import io.github.duckysmacky.combatdrones.entity.Feedback;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * EMP Field Station ({@code geo/emp_station}): the coil windings glow while it's on, five lamps on the control box show
 * the charge (the next one blinking while it recharges), a red lamp while it's off. Its field swells out when switched
 * on and collapses when switched off.
 */
public class RenderEmpStation extends Render<EntityEmpStation> {
    private static final String MODEL = "emp_station";
    /** Share of the full radius the shown field moves toward its target per tick. */
    private static final float GROW_RATE = 0.18F;
    /** Per station: {shown field size 0..1, time of the last frame}. */
    private static final Map<EntityEmpStation, float[]> FIELDS = new WeakHashMap<>();

    public RenderEmpStation(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0.6F;
    }

    @Override
    public void doRender(EntityEmpStation station, double x, double y, double z, float entityYaw, float partialTicks) {
        float time = station.ticksExisted + partialTicks;
        boolean on = station.isActive();
        float charge = station.getCharge() / EntityEmpStation.MAX_CHARGE;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        float settle = Feedback.settle(station, station.handledAt, partialTicks);
        GlStateManager.scale(1 + settle * 0.02F, 1 - settle * 0.05F, 1 + settle * 0.02F);
        GlStateManager.rotate(-station.rotationYaw, 0, 1, 0);
        GeoModels.reset(MODEL);
        GeoModels.show(MODEL, "glow_coil", on && (time % 9 > 0.6F));
        int lit = (int) Math.ceil(charge * 5 - 0.01);
        for (int lamp = 0; lamp < 5; lamp++) {
            boolean blinking = !on && lamp == lit && charge < 1 && time % 20 < 10;
            GeoModels.show(MODEL, "glow_charge_" + lamp, lamp < lit || blinking);
        }
        GeoModels.show(MODEL, "glow_off", !on);
        GeoModels.render(MODEL, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();

        float[] field = FIELDS.computeIfAbsent(station, key -> new float[] {on ? 1 : 0, time});
        field[0] += ((on ? 1 : 0) - field[0]) * Math.min(1, Math.max(0, time - field[1]) * GROW_RATE);
        field[1] = time;
        if (field[0] > 0.01F) {
            EmpField.draw(x, y + station.height / 2, z, station.getRadius() * field[0], time, field[0]);
        }
        super.doRender(station, x, y, z, entityYaw, partialTicks);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityEmpStation station) {
        return null; // GeoModels binds the texture
    }
}
