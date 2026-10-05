package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.BatteryPack;
import io.github.duckysmacky.combatdrones.type.BombType;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Analog FPV OSD, condensed around the screen centre like real goggles: status line on top, speed ladder left,
 * altitude ladder right, heading tape at the bottom. Vanilla HUD parts that make no sense through goggles are
 * hidden; health and chat stay because the pilot's body can be attacked while they fly.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class HudOverlay {
    private static final Set<ElementType> HIDDEN_WHILE_FLYING = EnumSet.of(
            ElementType.CROSSHAIRS, ElementType.HOTBAR, ElementType.FOOD, ElementType.EXPERIENCE,
            ElementType.ARMOR, ElementType.AIR, ElementType.JUMPBAR);

    /** Half-size of the OSD box around the screen centre (scaled GUI pixels). */
    private static final int HALF_WIDTH = 120;
    private static final int HALF_HEIGHT = 75;
    /** Ladders are shorter than the box so they clear the corner readouts. */
    private static final int LADDER_HALF_HEIGHT = 42;
    private static final int LINE = 10;
    private static final int TEXT = 0xFFFFFF;
    private static final int WARNING = 0xFF5555;
    private static final int LINE_COLOR = 0xD0FFFFFF;
    private static final int SHADOW = 0x80000000;
    private static final double LOW_BATTERY = 0.2;
    private static final double LOW_SIGNAL = 0.3;
    private static final double ALTIMETER_RANGE = 256;

    // Ladder scales: pixels per unit, and how often ticks / labels appear
    private static final double SPEED_PIXELS_PER_KMH = 1.5;
    private static final int SPEED_TICK = 10;
    private static final double ALTITUDE_PIXELS_PER_METER = 3;
    private static final int ALTITUDE_TICK = 5;
    private static final double HEADING_PIXELS_PER_DEGREE = 1.2;
    private static final int HEADING_TICK = 15;

    private HudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderPre(RenderGameOverlayEvent.Pre event) {
        if ((PilotController.getPilotedDrone() != null || RemoteController.getUnit() != null) && HIDDEN_WHILE_FLYING.contains(event.getType())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderGameOverlayEvent.Post event) {
        if (event.getType() != ElementType.ALL) {
            return;
        }
        EntityRampUnit unit = RemoteController.getUnit();
        if (unit != null) {
            drawPlaneHud(event.getResolution(), unit);
            return;
        }
        EntityHandFlownDrone drone = PilotController.getPilotedDrone();
        if (drone == null) {
            return;
        }
        DroneType type = drone.getDroneType();
        if (type == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        ScaledResolution resolution = event.getResolution();
        int centerX = resolution.getScaledWidth() / 2;
        int centerY = resolution.getScaledHeight() / 2;
        int left = centerX - HALF_WIDTH;
        int right = centerX + HALF_WIDTH;
        int top = centerY - HALF_HEIGHT;
        int bottom = centerY + HALF_HEIGHT;

        double speedKmh = drone.getFlightState().velocity.length() * 72;
        double altitude = altitudeAboveGround(minecraft, drone);
        double heading = MathHelper.positiveModulo(drone.rotationYaw + 180, 360); // 0 = north, 90 = east
        double signal = PilotController.getSignalQuality();
        BatteryPack pack = drone.getBatteryPack();
        double battery = drone.getKnownBatteryFraction();

        // Status line above the box
        String status = I18n.format("hud.combat_drones.status", percent(signal), Math.round(PilotController.getSignalDistance()),
                Math.round(heading), altitude < 0 ? "---" : String.valueOf(Math.round(altitude)));
        drawCentered(font, status, centerX, top - LINE - 4, signal < LOW_SIGNAL ? WARNING : TEXT);

        // Top-left of the box: flight state
        drawText(font, I18n.format("hud.combat_drones.mode." + PilotController.getMode().name().toLowerCase(Locale.ROOT)), left, top, TEXT);
        boolean armed = drone.areMotorsOn();
        drawText(font, I18n.format(armed ? "hud.combat_drones.armed" : "hud.combat_drones.disarmed"), left, top + LINE, armed ? TEXT : WARNING);
        if (PilotController.isDji()) {
            drawText(font, I18n.format("hud.combat_drones.gimbal", Math.round(-PilotController.getGimbalPitch())), left, top + LINE * 2, TEXT);
        }

        // Top-right of the box: segmented battery gauge, then what's loaded (grenades / bombs) right under it
        drawBattery(font, right, top, pack, battery);
        String payload = payloadText(drone, type);
        if (payload != null) {
            boolean loaded = drone.getPayloadCount() > 0;
            drawText(font, payload, right - font.getStringWidth(payload), top + 4 * 4 + 2 + LINE, loaded ? 0xFFAA00 : TEXT);
        }

        // Side ladders
        drawLadder(font, left, centerY, speedKmh, SPEED_PIXELS_PER_KMH, SPEED_TICK, true);
        if (altitude >= 0) {
            drawLadder(font, right, centerY, altitude, ALTITUDE_PIXELS_PER_METER, ALTITUDE_TICK, false);
        }

        // Bottom: heading tape, then throttle / payload
        drawHeadingTape(font, centerX, bottom, heading);
        String throttle;
        if (PilotController.getMode() == HandFlownFlight.Mode.VELOCITY) {
            // DJI has no throttle: show vertical speed in m/s instead
            throttle = I18n.format("hud.combat_drones.vertical_speed", String.format(Locale.ROOT, "%+.1f", drone.getFlightState().velocity.y * 20));
        } else if (PilotController.getMode() == HandFlownFlight.Mode.HOVER) {
            throttle = I18n.format("hud.combat_drones.throttle_hold");
        } else {
            throttle = I18n.format("hud.combat_drones.throttle", percent(PilotController.getThrottle()));
        }
        drawText(font, throttle, left, bottom + 16, TEXT);
        float health = drone.getHealthFraction();
        drawText(font, I18n.format("hud.combat_drones.health", percent(health)), left, bottom + 16 + LINE, health < 0.5F ? WARNING : TEXT);
        // Players your team's recon has spotted (boxed in the world by MarkerRenderer)
        if (!ClientIntel.getMarks().isEmpty()) {
            String targets = I18n.format("hud.combat_drones.targets", ClientIntel.getMarks().size());
            drawText(font, targets, right - font.getStringWidth(targets), bottom + 16, WARNING);
        }

        drawCrosshair(centerX, centerY);
        drawWarnings(font, minecraft, centerX, centerY + 24, pack != null, battery, signal);
    }

    /** OSD for a ramp unit seen through its camera (remote view or Take control), same layout as the FPV one. */
    private static void drawPlaneHud(ScaledResolution resolution, EntityRampUnit unit) {
        DroneType type = unit.getDroneType();
        if (type == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        int centerX = resolution.getScaledWidth() / 2;
        int centerY = resolution.getScaledHeight() / 2;
        int left = centerX - HALF_WIDTH;
        int right = centerX + HALF_WIDTH;
        int top = centerY - HALF_HEIGHT;
        int bottom = centerY + HALF_HEIGHT;

        double speedKmh = unit.getSyncedSpeed() * 72;
        double altitude = altitudeAboveGround(minecraft, unit);
        double heading = MathHelper.positiveModulo(unit.rotationYaw + 180, 360);
        double fuel = unit.getFuelFraction();

        drawCentered(font, I18n.format("hud.combat_drones.plane_status", Math.round(heading),
                altitude < 0 ? "---" : String.valueOf(Math.round(altitude))), centerX, top - LINE - 4, TEXT);

        String mode = !RemoteController.isControl() ? "view" : !RemoteController.isStickControl() ? "designate"
                : RemoteController.isFreeMode() ? "free" : "assisted";
        drawText(font, I18n.format("hud.combat_drones.mode." + mode), left, top, TEXT);
        if (unit.isStrikingDesignated()) {
            drawText(font, I18n.format("hud.combat_drones.striking"), left, top + LINE, WARNING);
        }
        if (unit.isBoosting()) {
            drawText(font, I18n.format("hud.combat_drones.boost"), left, top + LINE * 2, 0xFFAA00);
        }
        if (RemoteController.getZoom() > 1) {
            drawText(font, I18n.format("hud.combat_drones.zoom", RemoteController.getZoom()), left, top + LINE * 3, TEXT);
        }
        if (!ClientIntel.getMarks().isEmpty()) {
            String targets = I18n.format("hud.combat_drones.targets", ClientIntel.getMarks().size());
            drawText(font, targets, right - font.getStringWidth(targets), top + LINE * 2, WARNING);
        }

        String fuelText = I18n.format("hud.combat_drones.fuel", percent(fuel));
        drawText(font, fuelText, right - font.getStringWidth(fuelText), top, fuel < LOW_BATTERY ? WARNING : TEXT);
        if (type.getWeaponType() != DroneType.WeaponType.NONE) {
            String ammo = I18n.format("hud.combat_drones.ammo." + type.getWeaponType().name().toLowerCase(Locale.ROOT), unit.getAmmo(),
                    type.getPayloadCapacity());
            drawText(font, ammo, right - font.getStringWidth(ammo), top + LINE, unit.getAmmo() > 0 ? 0xFFAA00 : WARNING);
        }

        drawLadder(font, left, centerY, speedKmh, SPEED_PIXELS_PER_KMH, SPEED_TICK, true);
        if (altitude >= 0) {
            drawLadder(font, right, centerY, altitude, ALTITUDE_PIXELS_PER_METER, ALTITUDE_TICK, false);
        }
        drawHeadingTape(font, centerX, bottom, heading);
        if (RemoteController.isStickControl()) {
            drawText(font, I18n.format("hud.combat_drones.throttle", percent(RemoteController.getThrottle())), left, bottom + 16, TEXT);
        }
        float health = unit.getHealthFraction();
        drawText(font, I18n.format("hud.combat_drones.health", percent(health)), left, bottom + 16 + LINE, health < 0.5F ? WARNING : TEXT);
        drawCrosshair(centerX, centerY);
        drawLock(font, centerX, centerY, ClientIntel.getLockProgress());
        if (fuel <= 0) {
            drawCentered(font, I18n.format("hud.combat_drones.warning.fuel_empty"), centerX, centerY + 24, WARNING);
        } else if (fuel < LOW_BATTERY && (minecraft.world.getTotalWorldTime() / 10) % 2 == 0) {
            drawCentered(font, I18n.format("hud.combat_drones.warning.low_fuel"), centerX, centerY + 24, WARNING);
        }
    }

    /** Recon lock: brackets closing in on the crosshair as the lock builds, then LOCKED. */
    private static void drawLock(FontRenderer font, int centerX, int centerY, float progress) {
        if (progress <= 0) {
            return;
        }
        int color = progress >= 1 ? 0xFFFF3030 : 0xFFFFAA00;
        int gap = 18 - Math.round(10 * Math.min(1, progress));
        int arm = 5;
        for (int sideX = -1; sideX <= 1; sideX += 2) {
            for (int sideY = -1; sideY <= 1; sideY += 2) {
                int cornerX = centerX + sideX * gap;
                int cornerY = centerY + sideY * gap;
                Gui.drawRect(Math.min(cornerX, cornerX - sideX * arm), cornerY, Math.max(cornerX, cornerX - sideX * arm) + 1, cornerY + 1, color);
                Gui.drawRect(cornerX, Math.min(cornerY, cornerY - sideY * arm), cornerX + 1, Math.max(cornerY, cornerY - sideY * arm) + 1, color);
            }
        }
        String text = progress >= 1 ? I18n.format("hud.combat_drones.locked") : I18n.format("hud.combat_drones.locking", percent(progress));
        drawCentered(font, text, centerX, centerY + 22, color & 0xFFFFFF);
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

    /**
     * Vertical tape centred on the current value. Labels sit on the outside of the tape; the current value is boxed
     * at the centre line, pointing inwards.
     */
    private static void drawLadder(FontRenderer font, int x, int centerY, double value, double pixelsPerUnit, int tick, boolean onLeft) {
        int visible = (int) (LADDER_HALF_HEIGHT / pixelsPerUnit);
        int first = (int) Math.floor((value - visible) / tick) * tick;
        for (int mark = first; mark <= value + visible; mark += tick) {
            int y = centerY - (int) Math.round((mark - value) * pixelsPerUnit);
            if (Math.abs(y - centerY) > LADDER_HALF_HEIGHT || mark < 0) {
                continue;
            }
            boolean major = mark % (tick * 2) == 0;
            int length = major ? 6 : 3;
            int tickStart = onLeft ? x + 18 : x - 18 - length;
            Gui.drawRect(tickStart, y, tickStart + length, y + 1, LINE_COLOR);
            if (major && Math.abs(y - centerY) > 8) {
                String label = String.valueOf(mark);
                int labelX = onLeft ? x : x - font.getStringWidth(label);
                font.drawStringWithShadow(label, labelX, y - 4, TEXT);
            }
        }
        // Boxed current value
        String current = String.valueOf(Math.round(value));
        int boxWidth = font.getStringWidth(current) + 6;
        int boxX = onLeft ? x : x - boxWidth;
        Gui.drawRect(boxX, centerY - 6, boxX + boxWidth, centerY + 6, SHADOW);
        drawOutline(boxX, centerY - 6, boxWidth, 12);
        font.drawStringWithShadow(current, boxX + 3, centerY - 4, TEXT);
        int arrowX = onLeft ? boxX + boxWidth + 1 : boxX - 3;
        Gui.drawRect(arrowX, centerY - 1, arrowX + 2, centerY + 1, LINE_COLOR);
    }

    /** Horizontal compass tape: ticks every 15°, cardinal letters, numbers at the 45° marks, marker at centre. */
    private static void drawHeadingTape(FontRenderer font, int centerX, int y, double heading) {
        int visible = (int) (HALF_WIDTH / HEADING_PIXELS_PER_DEGREE);
        int first = (int) Math.floor((heading - visible) / HEADING_TICK) * HEADING_TICK;
        Gui.drawRect(centerX - HALF_WIDTH + 20, y, centerX + HALF_WIDTH - 20, y + 1, 0x60FFFFFF);
        for (int mark = first; mark <= heading + visible; mark += HEADING_TICK) {
            int x = centerX + (int) Math.round((mark - heading) * HEADING_PIXELS_PER_DEGREE);
            if (Math.abs(x - centerX) > HALF_WIDTH - 20) {
                continue;
            }
            int degrees = Math.floorMod(mark, 360);
            boolean labelled = degrees % 45 == 0;
            Gui.drawRect(x, y - (labelled ? 4 : 2), x + 1, y + 1, LINE_COLOR);
            if (labelled) {
                String label = compassLabel(degrees);
                font.drawStringWithShadow(label, x - font.getStringWidth(label) / 2.0F, y + 3, TEXT);
            }
        }
        // Centre marker above the tape
        Gui.drawRect(centerX - 2, y - 8, centerX + 3, y - 7, TEXT | 0xFF000000);
        Gui.drawRect(centerX - 1, y - 7, centerX + 2, y - 6, TEXT | 0xFF000000);
        Gui.drawRect(centerX, y - 6, centerX + 1, y - 5, TEXT | 0xFF000000);
    }

    private static String compassLabel(int degrees) {
        switch (degrees) {
            case 0:
                return "N";
            case 90:
                return "E";
            case 180:
                return "S";
            case 270:
                return "W";
            default:
                return String.valueOf(degrees);
        }
    }

    /** Stacked bars like a goggle battery icon, plus the pack size and percentage under it. */
    private static void drawBattery(FontRenderer font, int right, int top, BatteryPack pack, double fraction) {
        int segments = 4;
        int width = 18;
        int x = right - width;
        boolean low = pack == null || fraction < LOW_BATTERY;
        int filled = pack == null ? 0 : (int) Math.ceil(fraction * segments - 1e-9);
        for (int segment = 0; segment < segments; segment++) {
            int y = top + segment * 4;
            // Top segment is the last to fill, like a fuel gauge
            boolean on = segments - segment <= filled;
            Gui.drawRect(x, y, x + width, y + 3, on ? (low ? 0xFFFF5555 : 0xFFFFFFFF) : 0x50FFFFFF);
        }
        String label = pack == null ? I18n.format("hud.combat_drones.no_battery")
                : I18n.format("hud.combat_drones.battery", I18n.format("hud.combat_drones.pack." + pack.key()), percent(fraction));
        font.drawStringWithShadow(label, right - font.getStringWidth(label), top + segments * 4 + 2, low ? WARNING : TEXT);
    }

    private static void drawWarnings(FontRenderer font, Minecraft minecraft, int centerX, int y, boolean hasBattery, double battery, double signal) {
        // Blink at 2 Hz like a real OSD
        if (minecraft.world.getTotalWorldTime() / 5 % 2 == 0) {
            return;
        }
        if (!hasBattery || battery <= 0) {
            drawCentered(font, I18n.format("hud.combat_drones.warning.battery_empty"), centerX, y, WARNING);
            y += LINE;
        } else if (battery < LOW_BATTERY) {
            drawCentered(font, I18n.format("hud.combat_drones.warning.low_battery"), centerX, y, WARNING);
            y += LINE;
        }
        if (signal < LOW_SIGNAL) {
            drawCentered(font, I18n.format("hud.combat_drones.warning.low_signal"), centerX, y, WARNING);
        }
    }

    /** Height above the first solid block below the drone, or -1 if there's none within range. */
    private static double altitudeAboveGround(Minecraft minecraft, Entity entity) {
        Vec3d from = new Vec3d(entity.posX, entity.posY, entity.posZ);
        RayTraceResult hit = minecraft.world.rayTraceBlocks(from, from.add(0, -ALTIMETER_RANGE, 0), false, true, false);
        return hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK ? -1 : entity.posY - hit.hitVec.y;
    }

    private static long percent(double fraction) {
        return Math.round(Math.max(0, Math.min(1, fraction)) * 100);
    }

    private static void drawText(FontRenderer font, String text, int x, int y, int color) {
        font.drawStringWithShadow(text, x, y, color);
    }

    private static void drawCentered(FontRenderer font, String text, int centerX, int y, int color) {
        font.drawStringWithShadow(text, centerX - font.getStringWidth(text) / 2.0F, y, color);
    }

    private static void drawOutline(int x, int y, int width, int height) {
        Gui.drawRect(x, y, x + width, y + 1, LINE_COLOR);
        Gui.drawRect(x, y + height - 1, x + width, y + height, LINE_COLOR);
        Gui.drawRect(x, y, x + 1, y + height, LINE_COLOR);
        Gui.drawRect(x + width - 1, y, x + width, y + height, LINE_COLOR);
    }

    private static void drawCrosshair(int centerX, int centerY) {
        Gui.drawRect(centerX - 6, centerY, centerX - 2, centerY + 1, LINE_COLOR);
        Gui.drawRect(centerX + 3, centerY, centerX + 7, centerY + 1, LINE_COLOR);
        Gui.drawRect(centerX, centerY - 4, centerX + 1, centerY - 1, LINE_COLOR);
    }
}
