package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.entity.Feedback;
import io.github.duckysmacky.combatdrones.entity.EntityMissilePad;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;
import javax.vecmath.AxisAngle4d;

/**
 * Launch pad ({@code geo/missile_pad}) with the loaded missile standing on its deck, nose up. Drawn exactly where (and
 * how) the {@code EntityCruiseMissile} appears at ignition, so the hand-over on launch doesn't jump.
 */
public class RenderMissilePad extends Render<EntityMissilePad> {
    /** How far the clamp jaws draw back along the pad's diagonals (model pixels), and how fast (per tick). */
    private static final float CLAMP_TRAVEL = 2.4F;
    private static final float CLAMP_RATE = 0.12F;
    /** Per pad: {jaws open 0..1, time of the last frame}. */
    private static final Map<EntityMissilePad, float[]> CLAMPS = new WeakHashMap<>();

    public RenderMissilePad(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 1.2F;
    }

    @Override
    public void doRender(EntityMissilePad pad, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        float settle = Feedback.settle(pad, pad.handledAt, partialTicks); // just loaded, fuelled or linked
        GlStateManager.scale(1 + settle * 0.015F, 1 - settle * 0.04F, 1 + settle * 0.015F);
        GlStateManager.pushMatrix();
        GlStateManager.rotate(-pad.rotationYaw, 0, 1, 0);
        DroneType missile = pad.getMissileType();
        // Clamp jaws draw back when the pad is empty and close on a loaded missile; lamps: green fuelled, red not
        float[] clamps = CLAMPS.computeIfAbsent(pad, key -> new float[] {missile == null ? 1 : 0, pad.ticksExisted});
        float time = pad.ticksExisted + partialTicks;
        clamps[0] += ((missile == null ? 1 : 0) - clamps[0]) * Math.min(1, Math.max(0, time - clamps[1]) * CLAMP_RATE);
        clamps[1] = time;
        GeoModels.reset("missile_pad");
        for (int jaw = 0; jaw < 4; jaw++) {
            GeoModels.offset("missile_pad", "clamp_" + jaw, (jaw >= 2 ? 1 : -1) * CLAMP_TRAVEL * clamps[0], 0,
                    (jaw % 2 == 1 ? 1 : -1) * CLAMP_TRAVEL * clamps[0]);
        }
        GeoModels.show("missile_pad", "glow_ready", missile != null && pad.getFuelFraction() > 0);
        GeoModels.show("missile_pad", "glow_standby", missile != null && pad.getFuelFraction() <= 0);
        GeoModels.render("missile_pad", 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        if (missile != null) {
            GlStateManager.translate(0, EntityMissilePad.DECK_HEIGHT + missile.getHitboxHeight() / 2, 0);
            AxisAngle4d rotation = new AxisAngle4d();
            rotation.set(HandFlownFlight.fromEuler(pad.rotationYaw, -90, 0));
            GlStateManager.rotate((float) Math.toDegrees(rotation.angle), (float) rotation.x, (float) rotation.y, (float) rotation.z);
            RenderRampUnit.renderStill(missile);
        }
        GlStateManager.popMatrix();
        super.doRender(pad, x, y, z, entityYaw, partialTicks);
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityMissilePad pad) {
        return null; // GeoModels binds the texture
    }
}
