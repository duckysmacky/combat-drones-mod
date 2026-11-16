package io.github.duckysmacky.combatdrones.client.hud;

import io.github.duckysmacky.combatdrones.client.DroneKeys;
import io.github.duckysmacky.combatdrones.client.StingerController;
import io.github.duckysmacky.combatdrones.client.Tooltips;
import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.item.ItemStinger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/**
 * The Stinger's sight, drawn over the zoomed view while aiming: a round optic in a black surround, a fine reticle,
 * the seeker circle (the lock cone, to scale) that fills round as the lock builds and turns red when it holds, the
 * target's name and range, and the launcher's state.
 */
public final class StingerScope {
    private static final int SEGMENTS = 72;
    /** Field of view the world was last drawn with, degrees (set by the camera): sizes the seeker circle. */
    private static float fov = 70;

    private StingerScope() {
    }

    public static void setFov(float degrees) {
        fov = degrees;
    }

    /** Not aiming: a reminder under the crosshair when the tube is empty. */
    public static void drawHip(ScaledResolution resolution, float partialTicks) {
        ItemStack stack = StingerController.held();
        if (stack.isEmpty() || ItemStinger.isLoaded(stack)) {
            return;
        }
        int centerX = resolution.getScaledWidth() / 2;
        int y = resolution.getScaledHeight() / 2 + 14;
        float reload = StingerController.reloadProgress(partialTicks);
        if (reload > 0) {
            Ui.textCentered(I18n.format("hud.combat_drones.stinger.reloading"), centerX, y, Ui.WARN);
            Ui.bar(centerX - 20, y + 11, 40, 2, reload, Ui.WARN);
        } else {
            Ui.textCentered(I18n.format("hud.combat_drones.stinger.reload_hint", Tooltips.keyName(DroneKeys.RELOAD)), centerX, y, Ui.DIM);
        }
    }

    public static void draw(ScaledResolution resolution, float partialTicks) {
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        double optic = Math.min(width, height) * 0.46;
        float progress = StingerController.getProgress();
        boolean locked = progress >= 1;
        boolean loaded = ItemStinger.isLoaded(StingerController.held());
        int tone = locked ? Ui.DANGER : progress > 0 ? Ui.WARN : Ui.HUD_GREEN;

        // Glass: a faint cold tint, then everything outside the optic blacked out with a soft inner edge
        Ui.rect(0, 0, width, height, 0x14223050);
        surround(centerX, centerY, optic, Math.hypot(width, height));

        // The seeker circle is the lock cone at the scope's magnification
        double seeker = Math.tan(Math.toRadians(ModConfig.stingerLockConeDegrees)) / Math.tan(Math.toRadians(fov / 2)) * height / 2;
        seeker = Math.min(seeker, optic - 14);
        BufferBuilder lines = Ui.beginLines(1.0F);
        int fine = Ui.alpha(Ui.HUD_GREEN, 0.7F);
        // Reticle: a broken cross with range ticks, clear in the middle
        Ui.line(lines, centerX - optic + 6, centerY + 0.5, centerX - seeker - 5, centerY + 0.5, fine);
        Ui.line(lines, centerX + seeker + 5, centerY + 0.5, centerX + optic - 6, centerY + 0.5, fine);
        Ui.line(lines, centerX + 0.5, centerY - optic + 6, centerX + 0.5, centerY - seeker - 5, fine);
        Ui.line(lines, centerX + 0.5, centerY + seeker + 5, centerX + 0.5, centerY + optic - 6, fine);
        for (int tick = 1; tick * 12 + seeker + 5 < optic - 8; tick++) {
            double offset = seeker + 5 + tick * 12;
            int half = tick % 2 == 0 ? 4 : 2;
            Ui.line(lines, centerX - offset, centerY - half, centerX - offset, centerY + half + 1, fine);
            Ui.line(lines, centerX + offset, centerY - half, centerX + offset, centerY + half + 1, fine);
            Ui.line(lines, centerX - half, centerY + offset, centerX + half + 1, centerY + offset, fine);
        }
        Ui.line(lines, centerX - 3, centerY + 0.5, centerX + 4, centerY + 0.5, tone);
        Ui.line(lines, centerX + 0.5, centerY - 3, centerX + 0.5, centerY + 4, tone);
        Ui.circle(lines, centerX + 0.5, centerY + 0.5, seeker, SEGMENTS, Ui.alpha(tone, locked ? 1.0F : 0.55F));
        Ui.endLines();
        if (progress > 0) {
            // The lock building: the circle drawn over heavier, clockwise from the top
            BufferBuilder arc = Ui.beginLines(3.0F);
            int steps = locked ? SEGMENTS : Math.max(1, Math.round(SEGMENTS * progress));
            for (int step = 0; step < steps; step++) {
                double from = Math.PI * 2 * step / SEGMENTS - Math.PI / 2;
                double to = Math.PI * 2 * (step + 1) / SEGMENTS - Math.PI / 2;
                Ui.line(arc, centerX + 0.5 + Math.cos(from) * seeker, centerY + 0.5 + Math.sin(from) * seeker,
                        centerX + 0.5 + Math.cos(to) * seeker, centerY + 0.5 + Math.sin(to) * seeker, tone);
            }
            Ui.endLines();
            int gap = (int) Math.round(locked ? seeker * 0.45 : seeker * (1.35 - 0.6 * progress));
            if (!locked || Ui.blink(4)) {
                Ui.brackets((int) centerX, (int) centerY, gap, gap, locked ? 6 : 4, tone);
            }
        }

        int textY = (int) (centerY + seeker + 10);
        float reload = StingerController.reloadProgress(partialTicks);
        String status = reload > 0 ? I18n.format("hud.combat_drones.stinger.reloading")
                : !loaded ? I18n.format("hud.combat_drones.stinger.empty", Tooltips.keyName(DroneKeys.RELOAD))
                : locked ? I18n.format("hud.combat_drones.stinger.locked")
                : progress > 0 ? I18n.format("hud.combat_drones.locking", Ui.percent(progress))
                : I18n.format("hud.combat_drones.stinger.seeking");
        if (!locked || Ui.blink(2)) {
            Ui.textCentered(status, (float) centerX, textY, !loaded || reload > 0 ? Ui.WARN : tone);
        }
        if (reload > 0) {
            Ui.bar((int) centerX - 24, textY + 11, 48, 2, reload, Ui.WARN);
        }
        Entity target = StingerController.getTarget();
        Entity viewer = Minecraft.getMinecraft().getRenderViewEntity();
        if (target != null && viewer != null) {
            Ui.textCentered(target.getName(), (float) centerX, (int) (centerY - seeker - 22), tone);
            Ui.textCentered(I18n.format("hud.combat_drones.stinger.range", Math.round(viewer.getDistance(target))), (float) centerX,
                    (int) (centerY - seeker - 12), tone);
        }
        Ui.text(I18n.format(loaded ? "hud.combat_drones.stinger.ready" : "hud.combat_drones.stinger.no_missile"),
                (float) (centerX - optic * 0.62), (float) (centerY + optic * 0.55), loaded ? Ui.HUD_GREEN : Ui.DANGER);
        Ui.textRight(I18n.format("hud.combat_drones.stinger.zoom", StingerController.ZOOM), (float) (centerX + optic * 0.62),
                (float) (centerY + optic * 0.55), Ui.DIM);
    }

    /** Black from the optic's rim out past the screen corners, fading in over the last few pixels inside the rim. */
    private static void surround(double centerX, double centerY, double radius, double far) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double[] rings = {radius - 10, radius, far};
        int[] alphas = {0, 255, 255};
        for (int ring = 0; ring < 2; ring++) {
            for (int step = 0; step < SEGMENTS; step++) {
                double from = Math.PI * 2 * step / SEGMENTS;
                double to = Math.PI * 2 * (step + 1) / SEGMENTS;
                // Counter-clockwise on screen (y down), as vanilla's GUI quads are
                buffer.pos(centerX + Math.cos(from) * rings[ring], centerY + Math.sin(from) * rings[ring], 0).color(0, 0, 0, alphas[ring]).endVertex();
                buffer.pos(centerX + Math.cos(from) * rings[ring + 1], centerY + Math.sin(from) * rings[ring + 1], 0).color(0, 0, 0, alphas[ring + 1]).endVertex();
                buffer.pos(centerX + Math.cos(to) * rings[ring + 1], centerY + Math.sin(to) * rings[ring + 1], 0).color(0, 0, 0, alphas[ring + 1]).endVertex();
                buffer.pos(centerX + Math.cos(to) * rings[ring], centerY + Math.sin(to) * rings[ring], 0).color(0, 0, 0, alphas[ring]).endVertex();
            }
        }
        Tessellator.getInstance().draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
}
