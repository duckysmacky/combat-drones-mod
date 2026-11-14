package io.github.duckysmacky.combatdrones.client.hud;

import io.github.duckysmacky.combatdrones.client.ClientIntel;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.entity.Raytrace;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/** OSD parts every feed is built from: tapes, ladders, reticles, the lock brackets, warnings, the hit flash. */
public final class Widgets {
    /** Half-size of the OSD box around the screen centre (scaled GUI pixels). */
    public static final int HALF_WIDTH = 120;
    public static final int HALF_HEIGHT = 75;
    /** Ladders are shorter than the box so they clear the corner readouts. */
    private static final int LADDER_HALF_HEIGHT = 42;
    private static final double HEADING_PIXELS_PER_DEGREE = 1.2;
    private static final int HEADING_TICK = 15;
    private static final double ALTIMETER_RANGE = 256;
    /** Weapons that reload faster than this (ticks) get no reload bar: it would only flicker. */
    private static final int MIN_RELOAD_BAR_TICKS = 10;
    /** Most running lock-ons listed on the recon feed. */
    private static final int MAX_LISTED_LOCK_ONS = 4;
    public static final double LOW = 0.2;
    /** Speed and altitude ladder scales: pixels per unit, tick spacing. */
    public static final double SPEED_PIXELS = 1.5;
    public static final int SPEED_TICK = 10;
    public static final double ALTITUDE_PIXELS = 3;
    public static final int ALTITUDE_TICK = 5;

    private Widgets() {
    }

    /** Height above the first solid block below the entity, or -1 if there's none within range. */
    public static double altitudeAboveGround(Entity entity) {
        Vec3d from = new Vec3d(entity.posX, entity.posY, entity.posZ);
        RayTraceResult hit = Raytrace.blocks(Minecraft.getMinecraft().world, from, from.add(0, -ALTIMETER_RANGE, 0));
        return hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK ? -1 : entity.posY - hit.hitVec.y;
    }

    public static String number(double value) {
        return value < 0 ? "---" : String.valueOf(Math.round(value));
    }

    /** mm:ss of a duration in milliseconds. */
    public static String clock(long millis) {
        long seconds = millis / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    /** Compass tape: labels on top, ticks under them every 15 degrees, a caret under the middle. */
    public static void tape(int centerX, int y, double heading, int color) {
        int reach = HALF_WIDTH - 24;
        int visible = (int) (reach / HEADING_PIXELS_PER_DEGREE);
        int first = (int) Math.floor((heading - visible) / HEADING_TICK) * HEADING_TICK;
        for (int mark = first; mark <= heading + visible; mark += HEADING_TICK) {
            int x = centerX + (int) Math.round((mark - heading) * HEADING_PIXELS_PER_DEGREE);
            if (Math.abs(x - centerX) > reach) {
                continue;
            }
            int degrees = Math.floorMod(mark, 360);
            boolean labelled = degrees % 45 == 0;
            Ui.vline(x, y, y + (labelled ? 4 : 2), color);
            if (labelled) {
                Ui.textCentered(compass(degrees), x + 1, y - 9, color);
            }
        }
        Ui.block(centerX, y + 6, centerX + 1, y + 7, color);
        Ui.block(centerX - 1, y + 7, centerX + 2, y + 8, color);
    }

    private static String compass(int degrees) {
        switch (degrees) {
            case 0:
                return I18n.format("hud.combat_drones.compass.n");
            case 90:
                return I18n.format("hud.combat_drones.compass.e");
            case 180:
                return I18n.format("hud.combat_drones.compass.s");
            case 270:
                return I18n.format("hud.combat_drones.compass.w");
            default:
                return String.valueOf(degrees);
        }
    }

    /**
     * Vertical tape centred on the current value. Labels sit on the outside of the tape; the current value is boxed
     * at the centre line, pointing inwards.
     */
    public static void ladder(int x, int centerY, double value, double pixelsPerUnit, int tick, boolean onLeft, int color) {
        int visible = (int) (LADDER_HALF_HEIGHT / pixelsPerUnit);
        int first = (int) Math.floor((value - visible) / tick) * tick;
        for (int mark = first; mark <= value + visible; mark += tick) {
            int y = centerY - (int) Math.round((mark - value) * pixelsPerUnit);
            if (Math.abs(y - centerY) > LADDER_HALF_HEIGHT || mark < 0) {
                continue;
            }
            boolean major = mark % (tick * 2) == 0;
            int length = major ? 6 : 3;
            int tickStart = onLeft ? x + 24 : x - 24 - length;
            Ui.hline(tickStart, tickStart + length, y, color);
            if (major && Math.abs(y - centerY) > 9) {
                String label = String.valueOf(mark);
                if (onLeft) {
                    Ui.text(label, x, y - 3, color);
                } else {
                    Ui.textRight(label, x, y - 3, color);
                }
            }
        }
        String current = String.valueOf(Math.round(value));
        int boxWidth = Ui.width(current) + 7;
        int boxX = onLeft ? x : x - boxWidth;
        Ui.rect(boxX, centerY - 6, boxX + boxWidth, centerY + 6, Ui.SHADE);
        Ui.outline(boxX, centerY - 6, boxWidth, 12, color);
        Ui.text(current, boxX + 4, centerY - 3, color);
        int arrowX = onLeft ? boxX + boxWidth + 2 : boxX - 4;
        Ui.block(arrowX, centerY - 1, arrowX + 2, centerY + 1, color);
    }

    /** The plain aiming mark: a gapped cross with a dot. */
    public static void crosshair(int centerX, int centerY, int color) {
        Ui.hline(centerX - 8, centerX - 3, centerY, color);
        Ui.hline(centerX + 4, centerX + 9, centerY, color);
        Ui.vline(centerX, centerY - 7, centerY - 2, color);
        Ui.vline(centerX, centerY + 3, centerY + 8, color);
        Ui.rect(centerX, centerY, centerX + 1, centerY + 1, color);
    }

    /**
     * Artificial horizon laid over the real one: a broken line that tilts against the camera's roll and slides with
     * its pitch (degrees, positive looking down). With {@code pitchLadder}, shorter bars every 10 degrees as well.
     */
    public static void horizon(ScaledResolution resolution, int centerX, int centerY, double pitch, double roll, int color, boolean pitchLadder) {
        double halfFov = Math.toRadians(Minecraft.getMinecraft().gameSettings.fovSetting / 2.0);
        double pixelsPerDegree = resolution.getScaledHeight() / 2.0 / Math.tan(halfFov) * Math.toRadians(1);
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 0);
        GlStateManager.rotate((float) -roll, 0, 0, 1);
        int level = (int) Math.round(-pitch * pixelsPerDegree);
        if (Math.abs(level) <= HALF_HEIGHT - 8) {
            Ui.hline(-70, -24, level, color);
            Ui.hline(25, 71, level, color);
        }
        if (pitchLadder) {
            for (int degrees = -30; degrees <= 30; degrees += 10) {
                int y = (int) Math.round((-pitch - degrees) * pixelsPerDegree);
                if (degrees == 0 || Math.abs(y) > HALF_HEIGHT - 12) {
                    continue;
                }
                Ui.hline(-34, -18, y, color);
                Ui.hline(19, 35, y, color);
                Ui.text(String.valueOf(Math.abs(degrees)), 38, y - 3, color);
            }
        }
        GlStateManager.popMatrix();
    }

    /** Recon lock: brackets closing in on the crosshair as the lock builds, then snapping tight and red. */
    public static void lock(int centerX, int centerY) {
        float progress = ClientIntel.getLockProgress();
        if (progress <= 0) {
            return;
        }
        boolean locked = progress >= 1;
        int color = locked ? Ui.DANGER : Ui.WARN;
        int gap = locked ? 9 : 24 - Math.round(13 * progress);
        Ui.brackets(centerX, centerY, gap, gap, locked ? 6 : 4, color);
        if (locked) {
            // A diamond inside the brackets, pulsing
            int size = Ui.blink(2) ? 4 : 3;
            BufferBuilder buffer = Ui.beginLines(2.0F);
            Ui.line(buffer, centerX + 0.5, centerY - size + 0.5, centerX + size + 0.5, centerY + 0.5, color);
            Ui.line(buffer, centerX + size + 0.5, centerY + 0.5, centerX + 0.5, centerY + size + 0.5, color);
            Ui.line(buffer, centerX + 0.5, centerY + size + 0.5, centerX - size + 0.5, centerY + 0.5, color);
            Ui.line(buffer, centerX - size + 0.5, centerY + 0.5, centerX + 0.5, centerY - size + 0.5, color);
            Ui.endLines();
        }
        Ui.textCentered(locked ? I18n.format("hud.combat_drones.locked") : I18n.format("hud.combat_drones.locking", Ui.percent(progress)),
                centerX, centerY + 28, color);
    }

    /** A thin bar filling up while {@code station} reloads; nothing when it's ready. Returns true while reloading. */
    public static boolean reload(EntityRampUnit unit, int station, int x, int y, int width) {
        float left = unit.getCooldownLeft(station);
        if (left <= 0 || unit.getCooldownLength(station) < MIN_RELOAD_BAR_TICKS) {
            return false;
        }
        Ui.bar(x, y, width, 1, 1 - left, Ui.WARN);
        return true;
    }

    /**
     * Recon lock-on charges left, with the time to the next one (or HOLD while a lock-on keeps the reload waiting) and a
     * reload bar under it. Returns the y for the next line.
     */
    public static int lockCharges(EntityRampUnit unit, int right, int y) {
        int charges = unit.getLockCharges();
        int max = unit.getLockMaxCharges();
        int reload = unit.getLockReloadLeft();
        String text = reload < 0 ? I18n.format("hud.combat_drones.lock_hold", charges, max)
                : reload > 0 ? I18n.format("hud.combat_drones.lock_reload", charges, max, (reload + 19) / 20)
                : I18n.format("hud.combat_drones.lock_charges", charges, max);
        Ui.labelledRight(OsdFont.Icon.LOCK, text, right, y, charges > 0 ? Ui.TEXT : Ui.DANGER);
        if (charges < max) {
            int width = Ui.width(text);
            Ui.bar(right - width, y + 9, width, 1, unit.getLockReloadFraction(), reload < 0 ? Ui.DIM : Ui.WARN);
            return y + Ui.LINE + 2;
        }
        return y + Ui.LINE;
    }

    /** A thin bar under the reticle while the next lock-on charge reloads, like a weapon's. */
    public static void lockReload(EntityRampUnit unit, int x, int y, int width) {
        if (unit.getLockCharges() < unit.getLockMaxCharges()) {
            Ui.bar(x, y, width, 1, unit.getLockReloadFraction(), unit.getLockReloadLeft() < 0 ? Ui.DIM : Ui.WARN);
        }
    }

    /** Running lock-ons (yours and your team's): each target's name and the time it stays boxed, from {@code y} down. */
    public static void lockOns(int right, int y) {
        int shown = 0;
        for (IntelPacket.Entry mark : ClientIntel.getMarks()) {
            if (mark.locked && shown++ < MAX_LISTED_LOCK_ONS) {
                String text = I18n.format("hud.combat_drones.lock_on_time", mark.name, clock(ClientIntel.millisLeft(mark)));
                Ui.textRight(text, right, y, mark.player ? Ui.DANGER : Ui.WARN);
                y += Ui.LINE;
            }
        }
    }

    /** Players the team's recon has spotted, as a count ending at {@code right}. */
    public static void marks(int right, int y) {
        if (!ClientIntel.getMarks().isEmpty()) {
            Ui.labelledRight(OsdFont.Icon.TARGET, String.valueOf(ClientIntel.getMarks().size()), right, y, Ui.DANGER);
        }
    }

    /** A centred warning, blinking twice a second like a real OSD. Returns the y for the next one. */
    public static int warning(String key, int centerX, int y) {
        if (Ui.blink(2)) {
            String text = I18n.format(key);
            OsdFont.icon(OsdFont.Icon.WARNING, centerX - Ui.width(text) / 2.0F - 11, y, Ui.DANGER);
            Ui.textCentered(text, centerX, y, Ui.DANGER);
        }
        return y + Ui.LINE;
    }

    /** The vehicle was just hit: red closes in from the screen edges and fades over the flash. */
    public static void hitFlash(ScaledResolution resolution, int flashTicks) {
        if (flashTicks <= 0) {
            return;
        }
        int alpha = Math.min(150, flashTicks * 26);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        int depth = Math.max(24, height / 5);
        Ui.fade(0, 0, width, depth, 0xD94F3C, alpha, 0, true);
        Ui.fade(0, height - depth, width, height, 0xD94F3C, 0, alpha, true);
        Ui.fade(0, 0, depth, height, 0xD94F3C, alpha, 0, false);
        Ui.fade(width - depth, 0, width, height, 0xD94F3C, 0, alpha, false);
    }
}
