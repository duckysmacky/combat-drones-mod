package io.github.duckysmacky.combatdrones.client.fx;

import io.github.duckysmacky.combatdrones.CombatDrones;
import net.minecraft.util.ResourceLocation;

/**
 * The look of an EMP field (station and blast alike): a faint blue bubble, brighter at its rim, with two webs of
 * arcs crawling over it in opposite directions, flickering. 10-30% opaque at full {@code strength}.
 */
public final class EmpField {
    private static final ResourceLocation ARCS = new ResourceLocation(CombatDrones.MOD_ID, "textures/misc/emp_field.png");
    /** palette blue 3 and 4 */
    private static final float[] HAZE = {0x4f / 255F, 0x96 / 255F, 0xe6 / 255F};
    private static final float[] ARC = {0x86 / 255F, 0xc0 / 255F, 0xf4 / 255F};

    private EmpField() {
    }

    /**
     * Camera-relative centre. {@code time} in ticks drives the crawl and flicker; {@code strength} 0..1 fades the
     * whole field (switching on and off, a blast dying away).
     */
    public static void draw(double x, double y, double z, double radius, float time, float strength) {
        float flicker = 0.85F + 0.15F * (float) (Math.sin(time * 1.7) * Math.sin(time * 0.53 + 1.3));
        float level = strength * flicker;
        float tiles = Math.max(2, Math.round(radius * 0.75F));
        FieldSphere.draw(x, y, z, radius, HAZE, 0.10F * level, 0.30F * level, null, 1, 0, 0);
        FieldSphere.draw(x, y, z, radius, ARC, 0.22F * level, 0.30F * level, ARCS, tiles, time * 0.011F, time * 0.007F);
        FieldSphere.draw(x, y, z, radius * 0.985, ARC, 0.15F * level, 0.22F * level, ARCS, tiles, -time * 0.009F, time * 0.013F + 0.5F);
    }
}
