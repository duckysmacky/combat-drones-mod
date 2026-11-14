package io.github.duckysmacky.combatdrones.client.hud;

import io.github.duckysmacky.combatdrones.client.RemoteController;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.Loadout;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

/**
 * Ramp units seen through their camera (remote view or Take control), one look per kind of feed: the planes' green
 * head-up display, the gunship's gunner sight, the recon surveillance picture, the light munition's seeker and the
 * heavy one's bare nose camera.
 */
public final class UnitFeed {
    private UnitFeed() {
    }

    public static void draw(ScaledResolution resolution, EntityRampUnit unit, DroneType type, Feed feed, float partialTicks) {
        int centerX = resolution.getScaledWidth() / 2;
        int centerY = resolution.getScaledHeight() / 2;
        int left = centerX - Widgets.HALF_WIDTH;
        int right = centerX + Widgets.HALF_WIDTH;
        int top = centerY - Widgets.HALF_HEIGHT;
        int bottom = centerY + Widgets.HALF_HEIGHT;

        double speed = unit.getSyncedSpeed() * 72;
        double altitude = Widgets.altitudeAboveGround(unit);
        double heading = MathHelper.positiveModulo(unit.rotationYaw + 180, 360);
        double fuel = unit.getFuelFraction();
        float health = unit.getHealthFraction();
        int fuelColor = fuel < Widgets.LOW ? Ui.DANGER : Ui.TEXT;
        int healthColor = health < 0.5F ? Ui.DANGER : Ui.TEXT;
        String fuelText = I18n.format("hud.combat_drones.percent", Ui.percent(fuel));
        String healthText = I18n.format("hud.combat_drones.percent", Ui.percent(health));
        String mode = I18n.format("hud.combat_drones.mode." + (!RemoteController.isControl() ? "view" : RemoteController.isGunner() ? "gunner"
                : !RemoteController.isStickControl() ? "designate" : RemoteController.isFreeMode() ? "free" : "assisted"));
        // The camera's own attitude: the airframe's under stick control, the stabilised gimbal's otherwise
        double pitch;
        double roll;
        if (RemoteController.isStickControl()) {
            double[] euler = HandFlownFlight.toEuler(unit.renderOrientation(partialTicks));
            pitch = euler[1];
            roll = euler[2];
        } else {
            pitch = RemoteController.getCameraPitch();
            roll = 0;
        }
        int warningY = centerY + 40;

        switch (feed) {
            case GUNNER:
                gunner(unit, type, centerX, centerY, left, right, top, bottom, mode, heading, altitude, speed, fuelText, fuelColor, healthText, healthColor);
                break;
            case RECON:
                recon(unit, centerX, centerY, left, right, top, bottom, heading, altitude, speed, fuelText, fuelColor, healthText, healthColor);
                break;
            case SEEKER:
                seeker(unit, centerX, centerY, left, right, top, bottom, mode, heading, altitude, speed, fuelText, fuelColor, healthText, healthColor);
                break;
            case NOSECAM:
                nosecam(unit, centerX, centerY, left, right, top, bottom, mode, heading, altitude, speed, fuel, fuelText, fuelColor, healthText, healthColor);
                break;
            default:
                flight(resolution, unit, type, centerX, centerY, left, right, top, bottom, mode, heading, altitude, speed, fuelText, fuelColor,
                        healthText, healthColor, pitch, roll);
                break;
        }

        Widgets.lock(centerX, centerY);
        if (fuel <= 0) {
            Ui.textCentered(I18n.format("hud.combat_drones.warning.fuel_empty"), centerX, warningY, Ui.DANGER);
        } else if (fuel < Widgets.LOW) {
            Widgets.warning("hud.combat_drones.warning.low_fuel", centerX, warningY);
        }
    }

    /** Lines down the top-left corner that only show while they apply. */
    private static void states(EntityRampUnit unit, int x, int y, int color) {
        if (unit.isStrikingDesignated()) {
            Ui.text(I18n.format("hud.combat_drones.striking"), x, y, Ui.blink(2) ? Ui.DANGER : Ui.alpha(Ui.DANGER, 0.5F));
            y += Ui.LINE;
        }
        if (unit.isBoosting()) {
            Ui.text(I18n.format("hud.combat_drones.boost"), x, y, Ui.WARN);
            y += Ui.LINE;
        }
        if (RemoteController.getZoom() > 1) {
            Ui.text(I18n.format("hud.combat_drones.zoom", RemoteController.getZoom()), x, y, color);
        }
    }

    private static OsdFont.Icon weaponIcon(DroneType.WeaponType weapon) {
        switch (weapon) {
            case ROCKET_POD:
                return OsdFont.Icon.ROCKET;
            case GUN_MOUNT:
                return OsdFont.Icon.BULLET;
            default:
                return OsdFont.Icon.SHELL;
        }
    }

    /** Weapon stations stacked up from {@code bottomY}, right-aligned: icon, rounds left, a reload bar underneath. */
    private static void weapons(EntityRampUnit unit, DroneType type, int right, int bottomY, int selected, int idleColor) {
        Loadout loadout = unit.getLoadout();
        int armed = 0;
        for (int station = 0; station < Loadout.STATIONS; station++) {
            if (type.getWeapon(station) != DroneType.WeaponType.NONE) {
                armed++;
            }
        }
        int line = 0;
        for (int station = 0; station < Loadout.STATIONS; station++) {
            DroneType.WeaponType weapon = type.getWeapon(station);
            if (weapon == DroneType.WeaponType.NONE) {
                continue;
            }
            String ammo = I18n.format("hud.combat_drones.ammo." + ItemDrone.ammoKey(type, station, loadout), loadout.getAmmo(station),
                    loadout.capacity(type, station));
            int y = bottomY - (armed - 1 - line++) * (Ui.LINE + 1);
            int color = loadout.getAmmo(station) <= 0 ? Ui.DANGER : station == selected ? Ui.WARN : idleColor;
            Ui.labelledRight(weaponIcon(weapon), ammo, right, y, color);
            Widgets.reload(unit, station, right - Ui.width(ammo), y + 9, Ui.width(ammo));
        }
    }

    /** Strike and Attack Planes: head-up display in green over a clear picture. */
    private static void flight(ScaledResolution resolution, EntityRampUnit unit, DroneType type, int centerX, int centerY, int left, int right,
                               int top, int bottom, String mode, double heading, double altitude, double speed, String fuelText, int fuelColor,
                               String healthText, int healthColor, double pitch, double roll) {
        int green = Ui.HUD_GREEN;
        Widgets.tape(centerX, top - 12, heading, green);
        Ui.text(mode, left, top, green);
        states(unit, left, top + Ui.LINE, green);
        Ui.labelledRight(OsdFont.Icon.FUEL, fuelText, right, top, fuelColor == Ui.TEXT ? green : fuelColor);
        Widgets.marks(right, top + Ui.LINE);
        Widgets.ladder(left, centerY, speed, Widgets.SPEED_PIXELS, Widgets.SPEED_TICK, true, green);
        if (altitude >= 0) {
            Widgets.ladder(right, centerY, altitude, Widgets.ALTITUDE_PIXELS, Widgets.ALTITUDE_TICK, false, green);
        }
        weapons(unit, type, right, bottom - 10, -1, Ui.WARN);
        if (RemoteController.isStickControl()) {
            Ui.labelled(OsdFont.Icon.THROTTLE, I18n.format("hud.combat_drones.percent", Ui.percent(RemoteController.getThrottle())), left, bottom + 16, green);
        }
        Ui.labelled(OsdFont.Icon.HEALTH, healthText, left, bottom + 16 + Ui.LINE, healthColor == Ui.TEXT ? green : healthColor);

        Widgets.horizon(resolution, centerX, centerY, pitch, roll, green, true);
        // Flight path marker: a circle with wings and a fin
        BufferBuilder buffer = Ui.beginLines(2.0F);
        Ui.circle(buffer, centerX + 0.5, centerY + 0.5, 4, 12, green);
        Ui.line(buffer, centerX - 11.5, centerY + 0.5, centerX - 3.5, centerY + 0.5, green);
        Ui.line(buffer, centerX + 4.5, centerY + 0.5, centerX + 12.5, centerY + 0.5, green);
        Ui.line(buffer, centerX + 0.5, centerY - 3.5, centerX + 0.5, centerY - 8.5, green);
        Ui.endLines();
    }

    /** Gunship: a gunner sight. Long reticle, a frame, flight data down the sides and the selected weapon in a box. */
    private static void gunner(EntityRampUnit unit, DroneType type, int centerX, int centerY, int left, int right, int top, int bottom,
                               String mode, double heading, double altitude, double speed, String fuelText, int fuelColor, String healthText,
                               int healthColor) {
        Widgets.tape(centerX, top - 12, heading, Ui.TEXT);
        Ui.text(mode, left, top, Ui.TEXT);
        Ui.text(I18n.format("hud.combat_drones.zoom", RemoteController.getZoom()), left, top + Ui.LINE, Ui.TEXT);
        Ui.labelled(OsdFont.Icon.ALTITUDE, I18n.format("hud.combat_drones.metres", Widgets.number(altitude)), left, top + Ui.LINE * 2, Ui.DIM);
        Ui.labelled(OsdFont.Icon.SPEED, I18n.format("hud.combat_drones.kmh", Math.round(speed)), left, top + Ui.LINE * 3, Ui.DIM);
        Ui.labelledRight(OsdFont.Icon.FUEL, fuelText, right, top, fuelColor);
        Ui.labelledRight(OsdFont.Icon.HEALTH, healthText, right, top + Ui.LINE, healthColor);
        Widgets.marks(right, top + Ui.LINE * 2);

        // Reticle: four long arms with range ticks, an open middle with a dot
        Ui.hline(centerX - 46, centerX - 9, centerY, Ui.TEXT);
        Ui.hline(centerX + 10, centerX + 47, centerY, Ui.TEXT);
        Ui.vline(centerX, centerY - 36, centerY - 9, Ui.TEXT);
        Ui.vline(centerX, centerY + 10, centerY + 37, Ui.TEXT);
        for (int tick = 20; tick <= 44; tick += 12) {
            Ui.vline(centerX - tick, centerY - 2, centerY + 3, Ui.TEXT);
            Ui.vline(centerX + tick, centerY - 2, centerY + 3, Ui.TEXT);
            if (tick <= 32) {
                Ui.hline(centerX - 2, centerX + 3, centerY - tick, Ui.TEXT);
                Ui.hline(centerX - 2, centerX + 3, centerY + tick, Ui.TEXT);
            }
        }
        Ui.rect(centerX, centerY, centerX + 1, centerY + 1, Ui.TEXT);
        Ui.brackets(centerX, centerY, 84, 56, 9, Ui.DIM);

        // Selected weapon: name and rounds, then ready or the reload bar
        int station = unit.getSelectedStation();
        Loadout loadout = unit.getLoadout();
        if (RemoteController.isGunner() && type.getWeapon(station) != DroneType.WeaponType.NONE) {
            String ammo = I18n.format("hud.combat_drones.ammo." + ItemDrone.ammoKey(type, station, loadout), loadout.getAmmo(station),
                    loadout.capacity(type, station));
            boolean empty = loadout.getAmmo(station) <= 0;
            int boxWidth = Math.max(96, Ui.width(ammo) + 28);
            Ui.panel(centerX - boxWidth / 2, bottom - 4, boxWidth, 24);
            OsdFont.icon(weaponIcon(type.getWeapon(station)), centerX - boxWidth / 2 + 5, bottom, empty ? Ui.DANGER : Ui.WARN);
            Ui.textCentered(ammo, centerX + 5, bottom, empty ? Ui.DANGER : Ui.WARN);
            if (!Widgets.reload(unit, station, centerX - boxWidth / 2 + 6, bottom + 13, boxWidth - 12)) {
                Ui.textCentered(I18n.format(empty ? "hud.combat_drones.weapon.empty" : "hud.combat_drones.weapon.ready"), centerX, bottom + 10,
                        empty ? Ui.DANGER : Ui.OK);
            }
            Widgets.reload(unit, station, centerX - 14, centerY + 13, 28);
        }
        weapons(unit, type, right, bottom - 10, RemoteController.isGunner() ? station : -1, Ui.DIM);
    }

    /** Recon UAV: a surveillance picture between two data bars. */
    private static void recon(EntityRampUnit unit, int centerX, int centerY, int left, int right, int top, int bottom, double heading,
                              double altitude, double speed, String fuelText, int fuelColor, String healthText, int healthColor) {
        int width = Widgets.HALF_WIDTH * 2;
        Ui.panel(left, top - 34, width, 13);
        Ui.text(I18n.format("hud.combat_drones.feed.recon"), left + 4, top - 31, Ui.OK);
        Ui.textCentered(I18n.format("hud.combat_drones.heading", Math.round(heading)), centerX - 24, top - 31, Ui.TEXT);
        Ui.textCentered(I18n.format("hud.combat_drones.altitude", Widgets.number(altitude)), centerX + 34, top - 31, Ui.TEXT);
        Ui.textRight(I18n.format("hud.combat_drones.kmh", Math.round(speed)), right - 4, top - 31, Ui.TEXT);
        Widgets.tape(centerX, top - 12, heading, Ui.TEXT);

        Ui.panel(left, bottom + 14, width, 13);
        float yaw = MathHelper.wrapDegrees(RemoteController.getCameraYaw() - unit.rotationYaw);
        Ui.text(I18n.format("hud.combat_drones.camera", Math.round(yaw), Math.round(-RemoteController.getCameraPitch())), left + 4, bottom + 17, Ui.TEXT);
        Ui.textCentered(I18n.format("hud.combat_drones.zoom", RemoteController.getZoom()), centerX + 6, bottom + 17,
                RemoteController.getZoom() > 1 ? Ui.SELECT : Ui.DIM);
        Ui.textRight(I18n.format("hud.combat_drones.position", MathHelper.floor(unit.posX), MathHelper.floor(unit.posZ)), right - 4, bottom + 17, Ui.DIM);

        Ui.labelled(OsdFont.Icon.FUEL, fuelText, left, bottom - 4, fuelColor);
        Ui.labelledRight(OsdFont.Icon.HEALTH, healthText, right, bottom - 4, healthColor);
        Widgets.marks(right, top);

        Ui.hline(centerX - 11, centerX - 3, centerY, Ui.TEXT);
        Ui.hline(centerX + 4, centerX + 12, centerY, Ui.TEXT);
        Ui.vline(centerX, centerY - 10, centerY - 2, Ui.TEXT);
        Ui.vline(centerX, centerY + 3, centerY + 11, Ui.TEXT);
        Ui.brackets(centerX, centerY, 64, 42, 7, Ui.DIM);
    }

    /** Light munition: an electro-optical seeker. A target gate with hairlines, range and time to the strike point. */
    private static void seeker(EntityRampUnit unit, int centerX, int centerY, int left, int right, int top, int bottom, String mode,
                               double heading, double altitude, double speed, String fuelText, int fuelColor, String healthText, int healthColor) {
        Widgets.tape(centerX, top - 12, heading, Ui.TEXT);
        Ui.text(mode, left, top, Ui.TEXT);
        states(unit, left, top + Ui.LINE, Ui.TEXT);
        Ui.labelledRight(OsdFont.Icon.FUEL, fuelText, right, top, fuelColor);
        Ui.labelledRight(OsdFont.Icon.HEALTH, healthText, right, top + Ui.LINE, healthColor);
        Widgets.marks(right, top + Ui.LINE * 2);
        Ui.labelled(OsdFont.Icon.SPEED, I18n.format("hud.combat_drones.kmh", Math.round(speed)), left, bottom + 16, Ui.TEXT);
        Ui.labelled(OsdFont.Icon.ALTITUDE, I18n.format("hud.combat_drones.metres", Widgets.number(altitude)), left, bottom + 16 + Ui.LINE, Ui.TEXT);

        Ui.brackets(centerX, centerY, 16, 16, 5, Ui.TEXT);
        Ui.hline(centerX - 62, centerX - 23, centerY, Ui.TEXT);
        Ui.hline(centerX + 24, centerX + 63, centerY, Ui.TEXT);
        Ui.vline(centerX, centerY - 46, centerY - 23, Ui.TEXT);
        Ui.vline(centerX, centerY + 24, centerY + 47, Ui.TEXT);
        Ui.rect(centerX, centerY, centerX + 1, centerY + 1, Ui.TEXT);

        BlockPos target = unit.getStrikePoint();
        if (target != null) {
            double range = Math.sqrt(unit.getDistanceSq(target.getX() + 0.5, target.getY(), target.getZ() + 0.5));
            Ui.textRight(I18n.format("hud.combat_drones.range", Math.round(range)), right, bottom + 16, Ui.WARN);
            if (unit.getSyncedSpeed() > 0.01F) {
                Ui.textRight(I18n.format("hud.combat_drones.time_to_target", String.format(Locale.ROOT, "%.1f", range / unit.getSyncedSpeed() / 20)),
                        right, bottom + 16 + Ui.LINE, Ui.WARN);
            }
        }
    }

    /** Heavy munition: a cheap nose camera. Just the numbers in the corners, a fuel bar and a cross. */
    private static void nosecam(EntityRampUnit unit, int centerX, int centerY, int left, int right, int top, int bottom, String mode,
                                double heading, double altitude, double speed, double fuel, String fuelText, int fuelColor, String healthText,
                                int healthColor) {
        Ui.textCentered(I18n.format("hud.combat_drones.heading", Math.round(heading)), centerX, top - 12, Ui.TEXT);
        Ui.text(mode, left, top, Ui.TEXT);
        states(unit, left, top + Ui.LINE, Ui.TEXT);
        Ui.labelledRight(OsdFont.Icon.HEALTH, healthText, right, top, healthColor);
        Widgets.marks(right, top + Ui.LINE);
        Ui.labelled(OsdFont.Icon.SPEED, I18n.format("hud.combat_drones.kmh", Math.round(speed)), left, bottom + 16, Ui.TEXT);
        Ui.labelledRight(OsdFont.Icon.ALTITUDE, I18n.format("hud.combat_drones.metres", Widgets.number(altitude)), right, bottom + 16, Ui.TEXT);
        if (RemoteController.isStickControl()) {
            Ui.labelled(OsdFont.Icon.THROTTLE, I18n.format("hud.combat_drones.percent", Ui.percent(RemoteController.getThrottle())), left,
                    bottom + 16 + Ui.LINE, Ui.TEXT);
        }
        OsdFont.icon(OsdFont.Icon.FUEL, centerX - 44, bottom + 16, fuelColor);
        Ui.bar(centerX - 32, bottom + 18, 64, 3, fuel, fuelColor);
        Ui.text(fuelText, centerX + 36, bottom + 16, fuelColor);
        Ui.hline(centerX - 6, centerX + 7, centerY, Ui.TEXT);
        Ui.vline(centerX, centerY - 6, centerY + 7, Ui.TEXT);
    }
}
