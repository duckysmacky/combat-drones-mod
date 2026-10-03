package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.physics.HandFlownFlight;
import io.github.duckysmacky.combatdrones.type.BatteryPack;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
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
        if (PilotController.getPilotedDrone() != null && HIDDEN_WHILE_FLYING.contains(event.getType())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderGameOverlayEvent.Post event) {
        EntityHandFlownDrone drone = PilotController.getPilotedDrone();
        if (event.getType() != ElementType.ALL || drone == null) {
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
        drawText(font, I18n.format("hud.combat_drones.camera." + CameraHandler.getCameraMode().name().toLowerCase(Locale.ROOT)), left, top + LINE * 2, TEXT);

        // Top-right of the box: segmented battery gauge
        drawBattery(font, right, top, pack, battery);

        // Side ladders
        drawLadder(font, left, centerY, speedKmh, SPEED_PIXELS_PER_KMH, SPEED_TICK, true);
        if (altitude >= 0) {
            drawLadder(font, right, centerY, altitude, ALTITUDE_PIXELS_PER_METER, ALTITUDE_TICK, false);
        }

        // Bottom: heading tape, then throttle / payload
        drawHeadingTape(font, centerX, bottom, heading);
        String throttle = PilotController.getMode() == HandFlownFlight.Mode.HOVER ? I18n.format("hud.combat_drones.throttle_hold")
                : I18n.format("hud.combat_drones.throttle", percent(PilotController.getThrottle()));
        drawText(font, throttle, left, bottom + 16, TEXT);
        float health = drone.getHealthFraction();
        drawText(font, I18n.format("hud.combat_drones.health", percent(health)), left, bottom + 16 + LINE, health < 0.5F ? WARNING : TEXT);
        if (type.isImpactDetonates() && type.getPayloadCapacity() > 0) {
            String grenades = I18n.format("hud.combat_drones.grenades", drone.getKnownPayload(), type.getPayloadCapacity());
            drawText(font, grenades, right - font.getStringWidth(grenades), bottom + 16, TEXT);
        }

        drawCrosshair(centerX, centerY);
        drawWarnings(font, minecraft, centerX, centerY + 24, pack != null, battery, signal);
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
    private static double altitudeAboveGround(Minecraft minecraft, EntityHandFlownDrone drone) {
        Vec3d from = new Vec3d(drone.posX, drone.posY, drone.posZ);
        RayTraceResult hit = minecraft.world.rayTraceBlocks(from, from.add(0, -ALTIMETER_RANGE, 0), false, true, false);
        return hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK ? -1 : drone.posY - hit.hitVec.y;
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
