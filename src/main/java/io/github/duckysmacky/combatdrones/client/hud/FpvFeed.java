package io.github.duckysmacky.combatdrones.client.hud;

import io.github.duckysmacky.combatdrones.client.PilotController;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.BatteryPack;
import io.github.duckysmacky.combatdrones.type.BombType;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.MathHelper;

import javax.vecmath.Quat4d;
import java.util.Locale;

/**
 * Hand-flown drones. Racers get the analog FPV OSD: timer, craft name, link and battery icons, speed and height
 * ladders, a compass tape and an artificial horizon. The DJI Drone gets a clean digital one: a status strip, a
 * telemetry block and a gimbal scale.
 */
public final class FpvFeed {
    private static final double LOW_SIGNAL = 0.3;
    /** A LiPo cell reads 4.2 V full and about 3.3 V empty: shown for flavour, derived from the charge. */
    private static final double CELL_FULL = 4.2;
    private static final double CELL_EMPTY = 3.3;
    /** Racing quads tilt the camera up (as CameraHandler does), so the horizon is drawn for the tilted view. */
    private static final Quat4d CAMERA_UPTILT = HandFlownFlight.fromEuler(0, -20, 0);

    private FpvFeed() {
    }

    public static void draw(ScaledResolution resolution, EntityHandFlownDrone drone, DroneType type, Feed feed, long flownMillis, float partialTicks) {
        int centerX = resolution.getScaledWidth() / 2;
        int centerY = resolution.getScaledHeight() / 2;
        int left = centerX - Widgets.HALF_WIDTH;
        int right = centerX + Widgets.HALF_WIDTH;
        int top = centerY - Widgets.HALF_HEIGHT;
        int bottom = centerY + Widgets.HALF_HEIGHT;

        double speed = drone.getFlightState().velocity.length() * 20;
        double altitude = Widgets.altitudeAboveGround(drone);
        double heading = MathHelper.positiveModulo(drone.rotationYaw + 180, 360); // 0 = north, 90 = east
        double signal = PilotController.getSignalQuality();
        BatteryPack pack = drone.getBatteryPack();
        double battery = drone.getKnownBatteryFraction();
        boolean lowBattery = pack == null || battery < Widgets.LOW;
        boolean armed = drone.areMotorsOn();
        String name = I18n.format("item.combat_drones." + type.getId() + ".name");
        String mode = I18n.format("hud.combat_drones.mode." + PilotController.getMode().name().toLowerCase(Locale.ROOT));
        String signalText = I18n.format("hud.combat_drones.percent", Ui.percent(signal));
        String distance = I18n.format("hud.combat_drones.metres", Math.round(PilotController.getSignalDistance()));
        OsdFont.Icon signalIcon = OsdFont.Icon.values()[OsdFont.Icon.SIGNAL_4.ordinal()
                + (signal >= 0.75 ? 0 : signal >= 0.5 ? 1 : signal >= LOW_SIGNAL ? 2 : signal > 0.1 ? 3 : 4)];
        OsdFont.Icon batteryIcon = OsdFont.Icon.values()[OsdFont.Icon.BATTERY_4.ordinal()
                + (pack == null ? 4 : 4 - (int) Math.ceil(battery * 4 - 1e-9))];
        String charge = pack == null ? I18n.format("hud.combat_drones.no_battery")
                : I18n.format("hud.combat_drones.battery", I18n.format("hud.combat_drones.pack." + pack.key()), Ui.percent(battery));
        String payload = payloadText(drone, type);
        int payloadColor = drone.getPayloadCount() > 0 ? Ui.WARN : Ui.DIM;

        if (feed == Feed.DIGITAL) {
            // Status strip: flight mode, craft, link and battery
            Ui.panel(left, top - 34, Widgets.HALF_WIDTH * 2, 13);
            Ui.text(armed ? mode : I18n.format("hud.combat_drones.disarmed"), left + 4, top - 31, armed ? Ui.OK : Ui.WARN);
            Ui.textCentered(name, centerX, top - 31, Ui.DIM);
            Ui.labelledRight(batteryIcon, charge, right - 4, top - 31, lowBattery ? Ui.DANGER : Ui.TEXT);
            Ui.labelledRight(signalIcon, signalText, right - 12 - Ui.width(charge) - 10, top - 31, signal < LOW_SIGNAL ? Ui.DANGER : Ui.TEXT);
            Widgets.tape(centerX, top - 12, heading, Ui.TEXT);

            // Telemetry block: height and distance, horizontal and vertical speed (m/s)
            Ui.panel(left, bottom - 14, 104, 25);
            Ui.text(I18n.format("hud.combat_drones.dji.height", altitude < 0 ? "---" : String.format(Locale.ROOT, "%.1f", altitude)), left + 4, bottom - 10, Ui.TEXT);
            Ui.text(I18n.format("hud.combat_drones.dji.distance", Math.round(PilotController.getSignalDistance())), left + 56, bottom - 10, Ui.TEXT);
            Ui.text(I18n.format("hud.combat_drones.dji.speed", String.format(Locale.ROOT, "%.1f",
                    Math.hypot(drone.getFlightState().velocity.x, drone.getFlightState().velocity.z) * 20)), left + 4, bottom, Ui.DIM);
            Ui.text(I18n.format("hud.combat_drones.vertical_speed", String.format(Locale.ROOT, "%+.1f", drone.getFlightState().velocity.y * 20)),
                    left + 56, bottom, Ui.DIM);

            // Gimbal scale down the right side: level at the top, straight down at the bottom
            float gimbal = PilotController.getGimbalPitch();
            int scaleX = right - 4;
            Ui.vline(scaleX, centerY - 30, centerY + 31, Ui.DIM);
            for (int degrees = 0; degrees <= 90; degrees += 30) {
                Ui.hline(scaleX - 3, scaleX, centerY - 30 + degrees * 60 / 90, Ui.DIM);
            }
            int marker = centerY - 30 + Math.round(MathHelper.clamp(gimbal, 0, 90) * 60 / 90);
            Ui.block(scaleX - 8, marker - 1, scaleX - 4, marker + 2, Ui.TEXT);
            Ui.textRight(I18n.format("hud.combat_drones.gimbal", Math.round(-gimbal)), scaleX - 11, marker - 3, Ui.TEXT);

            Ui.brackets(centerX, centerY, 12, 12, 3, Ui.TEXT);
            Ui.rect(centerX, centerY, centerX + 1, centerY + 1, Ui.TEXT);
            if (payload != null) {
                Ui.labelledRight(OsdFont.Icon.BOMB, payload, right, bottom - 10, payloadColor);
            }
            Ui.labelledRight(OsdFont.Icon.HEALTH, I18n.format("hud.combat_drones.percent", Ui.percent(drone.getHealthFraction())), right, bottom,
                    drone.getHealthFraction() < 0.5F ? Ui.DANGER : Ui.TEXT);
            Widgets.marks(right, top);
        } else {
            // Above the box: link distance, craft name, signal; then the compass tape
            Ui.labelled(OsdFont.Icon.HOME, distance, left, top - 34, Ui.TEXT);
            OsdFont.draw(name, centerX - OsdFont.width(name, OsdFont.WIDE) / 2.0F, top - 34, Ui.TEXT, OsdFont.WIDE);
            Ui.labelledRight(signalIcon, signalText, right, top - 34, signal < LOW_SIGNAL ? Ui.DANGER : Ui.TEXT);
            Widgets.tape(centerX, top - 12, heading, Ui.TEXT);

            // Top-left: flight timer and state. Top-right: battery, then what's loaded
            Ui.labelled(OsdFont.Icon.TIMER, Widgets.clock(flownMillis), left, top, Ui.TEXT);
            Ui.text(mode, left, top + Ui.LINE, Ui.TEXT);
            Ui.text(I18n.format(armed ? "hud.combat_drones.armed" : "hud.combat_drones.disarmed"), left, top + Ui.LINE * 2, armed ? Ui.TEXT : Ui.WARN);
            // The gauge pulses once the pack runs low
            int batteryColor = lowBattery ? (Ui.blink(2) ? Ui.DANGER : Ui.alpha(Ui.DANGER, 0.45F)) : Ui.TEXT;
            Ui.labelledRight(batteryIcon, charge, right, top, batteryColor);
            if (pack != null) {
                Ui.textRight(I18n.format("hud.combat_drones.cell", String.format(Locale.ROOT, "%.2f", CELL_EMPTY + (CELL_FULL - CELL_EMPTY) * battery)),
                        right, top + Ui.LINE, lowBattery ? Ui.DANGER : Ui.DIM);
            }
            if (payload != null) {
                Ui.labelledRight(type.getPayload() == DroneType.PayloadKind.GRENADE ? OsdFont.Icon.GRENADE : OsdFont.Icon.BOMB, payload,
                        right, top + Ui.LINE * 2, payloadColor);
            }

            Widgets.ladder(left, centerY, speed * 3.6, Widgets.SPEED_PIXELS, Widgets.SPEED_TICK, true, Ui.TEXT);
            if (altitude >= 0) {
                Widgets.ladder(right, centerY, altitude, Widgets.ALTITUDE_PIXELS, Widgets.ALTITUDE_TICK, false, Ui.TEXT);
            }

            String throttle = PilotController.getMode() == HandFlownFlight.Mode.HOVER ? I18n.format("hud.combat_drones.throttle_hold")
                    : I18n.format("hud.combat_drones.percent", Ui.percent(PilotController.getThrottle()));
            Ui.labelled(OsdFont.Icon.THROTTLE, throttle, left, bottom + 16, Ui.TEXT);
            float health = drone.getHealthFraction();
            Ui.labelled(OsdFont.Icon.HEALTH, I18n.format("hud.combat_drones.percent", Ui.percent(health)), left, bottom + 16 + Ui.LINE,
                    health < 0.5F ? Ui.DANGER : Ui.TEXT);
            Widgets.marks(right, bottom + 16);

            Quat4d view = HandFlownFlight.slerp(drone.prevOrientation, drone.getFlightState().orientation, partialTicks);
            view.mul(CAMERA_UPTILT);
            double[] euler = HandFlownFlight.toEuler(view);
            Widgets.horizon(resolution, centerX, centerY, euler[1], euler[2], Ui.TEXT, false);
            Widgets.crosshair(centerX, centerY, Ui.TEXT);
        }

        int warningY = centerY + 26;
        if (pack == null || battery <= 0) {
            warningY = Widgets.warning("hud.combat_drones.warning.battery_empty", centerX, warningY);
        } else if (battery < Widgets.LOW) {
            warningY = Widgets.warning("hud.combat_drones.warning.low_battery", centerX, warningY);
        }
        if (signal < LOW_SIGNAL) {
            Widgets.warning("hud.combat_drones.warning.low_signal", centerX, warningY);
        }
    }

    /** Grenade count, the loaded bomb size and count, or null for drones without payload slots. */
    private static String payloadText(EntityHandFlownDrone drone, DroneType type) {
        if (type.getPayload() == null || type.getPayloadCapacity() == 0) {
            return null;
        }
        if (type.getPayload() == DroneType.PayloadKind.GRENADE) {
            return I18n.format("hud.combat_drones.payload.grenade", drone.getPayloadCount(), type.getPayloadCapacity());
        }
        BombType bomb = drone.getPayloadBomb();
        if (bomb == null) {
            return I18n.format("hud.combat_drones.payload.bay_empty");
        }
        return I18n.format("hud.combat_drones.payload.bomb", I18n.format("hud.combat_drones.bomb." + bomb.key()),
                drone.getPayloadCount(), EntityHandFlownDrone.payloadCapacity(type, bomb));
    }
}
