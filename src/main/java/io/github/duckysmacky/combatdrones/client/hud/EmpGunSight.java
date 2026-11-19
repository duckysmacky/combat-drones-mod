package io.github.duckysmacky.combatdrones.client.hud;

import io.github.duckysmacky.combatdrones.client.DroneKeys;
import io.github.duckysmacky.combatdrones.client.LauncherController;
import io.github.duckysmacky.combatdrones.client.Tooltips;
import io.github.duckysmacky.combatdrones.item.ItemLauncher;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.BufferBuilder;

/**
 * The EMP Gun's sight. Raised: a holographic ring reticle in a round optic (black surround, as the Stinger's) over
 * the zoomed view, the charge filling the ring clockwise, and the gun's state under it. From the hip: a small charge ring round the crosshair and
 * the same state line while charging, reloading or empty.
 */
public final class EmpGunSight {
    private static final int SEGMENTS = 48;

    private EmpGunSight() {
    }

    public static void draw(ScaledResolution resolution, float partialTicks, boolean raised) {
        boolean loaded = ItemLauncher.isLoaded(LauncherController.held());
        float reload = LauncherController.reloadProgress(partialTicks);
        float charge = LauncherController.chargeProgress(partialTicks);
        boolean charged = charge >= 1;
        if (!raised && loaded && reload <= 0 && charge <= 0) {
            return;
        }
        double centerX = resolution.getScaledWidth() / 2.0 + 0.5;
        double centerY = resolution.getScaledHeight() / 2.0 + 0.5;
        double ring = raised ? 30 : 10;
        int tone = charged ? Ui.OK : Ui.FRIENDLY;

        if (raised) {
            // Glass: a faint cold tint, the view round the sight blacked out like the Stinger's
            int width = resolution.getScaledWidth();
            int height = resolution.getScaledHeight();
            Ui.rect(0, 0, width, height, 0x14223050);
            StingerScope.surround(centerX, centerY, Math.min(width, height) * 0.46, Math.hypot(width, height));
        }
        BufferBuilder lines = Ui.beginLines(1.0F);
        if (raised) {
            int fine = Ui.alpha(Ui.FRIENDLY, 0.6F);
            Ui.circle(lines, centerX, centerY, ring, SEGMENTS, fine);
            Ui.circle(lines, centerX, centerY, 1.5, 8, tone);
            // Four short ticks outside the ring, and lead marks along the horizontal
            Ui.line(lines, centerX - ring - 10, centerY, centerX - ring - 3, centerY, fine);
            Ui.line(lines, centerX + ring + 3, centerY, centerX + ring + 10, centerY, fine);
            Ui.line(lines, centerX, centerY - ring - 10, centerX, centerY - ring - 3, fine);
            Ui.line(lines, centerX, centerY + ring + 3, centerX, centerY + ring + 10, fine);
            for (int mark = 1; mark <= 3; mark++) {
                double offset = ring + 10 + mark * 14;
                Ui.line(lines, centerX - offset, centerY - 2, centerX - offset, centerY + 3, fine);
                Ui.line(lines, centerX + offset, centerY - 2, centerX + offset, centerY + 3, fine);
            }
        }
        Ui.endLines();
        if (charge > 0) {
            BufferBuilder arc = Ui.beginLines(raised ? 3.0F : 2.0F);
            int steps = charged ? SEGMENTS : Math.max(1, Math.round(SEGMENTS * charge));
            for (int step = 0; step < steps; step++) {
                double from = Math.PI * 2 * step / SEGMENTS - Math.PI / 2;
                double to = Math.PI * 2 * (step + 1) / SEGMENTS - Math.PI / 2;
                Ui.line(arc, centerX + Math.cos(from) * ring, centerY + Math.sin(from) * ring, centerX + Math.cos(to) * ring,
                        centerY + Math.sin(to) * ring, tone);
            }
            Ui.endLines();
        }

        int textY = (int) (centerY + ring + (raised ? 14 : 6));
        String status = reload > 0 ? I18n.format("hud.combat_drones.emp_gun.reloading")
                : !loaded ? I18n.format("hud.combat_drones.emp_gun.empty", Tooltips.keyName(DroneKeys.RELOAD))
                : charged ? I18n.format("hud.combat_drones.emp_gun.release")
                : charge > 0 ? I18n.format("hud.combat_drones.emp_gun.charging", Ui.percent(charge))
                : I18n.format("hud.combat_drones.emp_gun.ready");
        if (!charged || Ui.blink(3)) {
            Ui.textCentered(status, (float) centerX, textY, !loaded || reload > 0 ? Ui.WARN : tone);
        }
        if (reload > 0) {
            Ui.bar((int) centerX - 24, textY + 11, 48, 2, reload, Ui.WARN);
        }
        if (raised) {
            Ui.textRight(I18n.format("hud.combat_drones.stinger.zoom", LauncherController.zoom()), (float) (centerX + ring + 52),
                    (float) (centerY - ring - 10), Ui.DIM);
        }
    }
}
