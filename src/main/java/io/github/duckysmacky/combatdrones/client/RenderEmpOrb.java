package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.client.fx.EmpField;
import io.github.duckysmacky.combatdrones.client.fx.FieldSphere;
import io.github.duckysmacky.combatdrones.entity.EntityEmpBlast;
import io.github.duckysmacky.combatdrones.entity.EntityEmpOrb;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * An EMP Gun orb: a condensed core (a white-hot centre in a blue glow, pulsing) and, in the travelling field mode, its
 * EMP field around it; once stopped, the field fading out with no core.
 */
public class RenderEmpOrb extends Render<EntityEmpOrb> {
    /** palette white 4, blue 4 */
    private static final float[] CORE = {0xf4 / 255F, 0xf5 / 255F, 0xf0 / 255F};
    private static final float[] GLOW = {0x86 / 255F, 0xc0 / 255F, 0xf4 / 255F};

    public RenderEmpOrb(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0;
    }

    @Override
    public void doRender(EntityEmpOrb orb, double x, double y, double z, float entityYaw, float partialTicks) {
        float age = orb.ticksExisted + partialTicks;
        double centerY = y; // the position is the field's centre
        int stopped = orb.getStopped();
        if (stopped >= 0) {
            float strength = Math.max(0, Math.min(1, 1 - (age - stopped) / EntityEmpBlast.FADE_TICKS));
            if (strength > 0) {
                EmpField.draw(x, centerY, z, orb.radiusAt(stopped), age, strength);
            }
            return;
        }
        float pulse = 0.85F + 0.15F * (float) Math.sin(age * 1.3);
        // About a block across: a dense white core inside a pulsing blue shell
        FieldSphere.draw(x, centerY, z, 0.5 * pulse, GLOW, 0.55F, 0.25F, null, 1, 0, 0);
        FieldSphere.draw(x, centerY, z, 0.3, CORE, 0.95F, 0.7F, null, 1, 0, 0);
        if (!orb.isBurst()) {
            EmpField.draw(x, centerY, z, orb.radiusAt(age), age, 1);
        }
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityEmpOrb orb) {
        return null;
    }
}
