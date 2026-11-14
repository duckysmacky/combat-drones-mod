package io.github.duckysmacky.combatdrones.client.hud;

import io.github.duckysmacky.combatdrones.config.ModConfig;
import io.github.duckysmacky.combatdrones.entity.EntityCruiseMissile;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.resources.I18n;

import java.util.Locale;

/**
 * Cruise missile dive: the terminal seeker. A cone reticle in the middle (the ring is the steering limit, the mark
 * where the nose points, up = forward on screen), speed and height ladders, the arming countdown and time to impact.
 */
public final class MissileFeed {
    private static final int CONE_RADIUS = 34;

    private MissileFeed() {
    }

    public static void draw(ScaledResolution resolution, EntityCruiseMissile missile) {
        int centerX = resolution.getScaledWidth() / 2;
        int centerY = resolution.getScaledHeight() / 2;
        int left = centerX - Widgets.HALF_WIDTH;
        int right = centerX + Widgets.HALF_WIDTH;
        int top = centerY - Widgets.HALF_HEIGHT;
        int bottom = centerY + Widgets.HALF_HEIGHT;
        double speed = missile.getSyncedSpeed();
        double altitude = Widgets.altitudeAboveGround(missile);

        Ui.text(I18n.format("hud.combat_drones.mode.missile"), left, top, Ui.TEXT);
        Ui.text(I18n.format("hud.combat_drones.warhead_armed"), left, top + Ui.LINE, Ui.blink(1) ? Ui.DANGER : Ui.alpha(Ui.DANGER, 0.5F));
        if (missile.isBoosting()) {
            Ui.text(I18n.format("hud.combat_drones.boost"), left, top + Ui.LINE * 2, Ui.WARN);
        }
        if (!missile.isDiving()) {
            // Holding over the target: big countdown to the dive
            Ui.textScaled(I18n.format("hud.combat_drones.arming", (missile.getHoldTicksLeft() + 19) / 20), centerX, top - 22, 2,
                    Ui.blink(1) ? Ui.DANGER : Ui.WARN);
        } else if (altitude >= 0 && speed > 0.01) {
            // Vertical closing speed: the nose is never more than the cone off straight down
            double sink = speed * Math.cos(Math.toRadians(Math.hypot(missile.getTiltForward(), missile.getTiltRight())));
            Ui.textScaled(I18n.format("hud.combat_drones.impact_in", String.format(Locale.ROOT, "%.1f", altitude / sink / 20)), centerX, top - 22, 2,
                    Ui.DANGER);
        }
        Widgets.ladder(left, centerY, speed * 72, Widgets.SPEED_PIXELS, Widgets.SPEED_TICK, true, Ui.TEXT);
        if (altitude >= 0) {
            Widgets.ladder(right, centerY, altitude, Widgets.ALTITUDE_PIXELS, Widgets.ALTITUDE_TICK, false, Ui.TEXT);
        }
        float health = missile.getHealthFraction();
        Ui.labelled(OsdFont.Icon.HEALTH, I18n.format("hud.combat_drones.percent", Ui.percent(health)), left, bottom + 16 + Ui.LINE,
                health < 0.5F ? Ui.DANGER : Ui.TEXT);

        // Cone reticle: dark under-stroke first so the ring reads on any ground
        double cone = Math.max(1, ModConfig.steerConeDegrees); // synced from the server
        double markX = centerX + 0.5 + missile.getTiltRight() / cone * CONE_RADIUS;
        double markY = centerY + 0.5 - missile.getTiltForward() / cone * CONE_RADIUS;
        for (int stroke = 0; stroke < 2; stroke++) {
            int ring = stroke == 0 ? Ui.SHADE : Ui.TEXT;
            int mark = stroke == 0 ? Ui.SHADE : Ui.DANGER;
            BufferBuilder buffer = Ui.beginLines(stroke == 0 ? 4.0F : 1.5F);
            Ui.circle(buffer, centerX + 0.5, centerY + 0.5, CONE_RADIUS, 48, ring);
            for (int quarter = 0; quarter < 4; quarter++) {
                double angle = Math.PI / 2 * quarter;
                Ui.line(buffer, centerX + 0.5 + Math.cos(angle) * (CONE_RADIUS - 5), centerY + 0.5 + Math.sin(angle) * (CONE_RADIUS - 5),
                        centerX + 0.5 + Math.cos(angle) * (CONE_RADIUS + 5), centerY + 0.5 + Math.sin(angle) * (CONE_RADIUS + 5), ring);
            }
            Ui.circle(buffer, markX, markY, 4, 12, mark);
            Ui.line(buffer, markX - 7, markY, markX - 4, markY, mark);
            Ui.line(buffer, markX + 4, markY, markX + 7, markY, mark);
            Ui.endLines();
        }
        Widgets.crosshair(centerX, centerY, Ui.TEXT);
    }
}
