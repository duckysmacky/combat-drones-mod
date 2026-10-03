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
 * Analog-OSD style flight HUD while piloting. Vanilla HUD parts that make no sense through goggles are hidden;
 * health and chat stay because the pilot's body can be attacked while they fly.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class HudOverlay {
    private static final Set<ElementType> HIDDEN_WHILE_FLYING = EnumSet.of(
            ElementType.CROSSHAIRS, ElementType.HOTBAR, ElementType.FOOD, ElementType.EXPERIENCE,
            ElementType.ARMOR, ElementType.AIR, ElementType.JUMPBAR);
    private static final int MARGIN = 6;
    private static final int LINE = 10;
    private static final int WHITE = 0xFFFFFF;
    private static final int WARNING = 0xFF5555;
    private static final double LOW_BATTERY = 0.2;
    private static final double LOW_SIGNAL = 0.3;
    /** How far down the altimeter looks for ground. */
    private static final double ALTIMETER_RANGE = 256;

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
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();

        // Top-left: flight state
        drawLeft(font, I18n.format("hud.combat_drones.mode." + PilotController.getMode().name().toLowerCase(Locale.ROOT)), MARGIN, WHITE);
        boolean armed = drone.areMotorsOn();
        drawLeft(font, I18n.format(armed ? "hud.combat_drones.armed" : "hud.combat_drones.disarmed"), MARGIN + LINE, armed ? WHITE : WARNING);
        drawLeft(font, I18n.format("hud.combat_drones.camera." + CameraHandler.getCameraMode().name().toLowerCase(Locale.ROOT)), MARGIN + LINE * 2, WHITE);

        // Top-right: power and link
        BatteryPack pack = drone.getBatteryPack();
        double battery = drone.getKnownBatteryFraction();
        String batteryText = pack == null ? I18n.format("hud.combat_drones.no_battery")
                : I18n.format("hud.combat_drones.battery", I18n.format("hud.combat_drones.pack." + pack.key()), percent(battery));
        drawRight(font, batteryText, width, MARGIN, pack == null || battery < LOW_BATTERY ? WARNING : WHITE);
        if (pack != null) {
            drawBar(width - MARGIN - 50, MARGIN + LINE, 50, battery, battery < LOW_BATTERY ? 0xFFFF5555 : 0xFF55FF55);
        }
        double signal = PilotController.getSignalQuality();
        drawRight(font, I18n.format("hud.combat_drones.signal", percent(signal), Math.round(PilotController.getSignalDistance())),
                width, MARGIN + LINE + 6, signal < LOW_SIGNAL ? WARNING : WHITE);

        // Bottom-left: motion
        HandFlownFlight.State state = drone.getFlightState();
        drawLeft(font, I18n.format("hud.combat_drones.speed", Math.round(state.velocity.length() * 72)), height - MARGIN - LINE * 2, WHITE);
        double altitude = altitudeAboveGround(minecraft, drone);
        drawLeft(font, altitude < 0 ? I18n.format("hud.combat_drones.altitude_unknown")
                : I18n.format("hud.combat_drones.altitude", String.format(Locale.ROOT, "%.1f", altitude)), height - MARGIN - LINE, WHITE);

        // Bottom-right: controls and payload
        String throttle = PilotController.getMode() == HandFlownFlight.Mode.HOVER ? I18n.format("hud.combat_drones.throttle_hold")
                : I18n.format("hud.combat_drones.throttle", percent(PilotController.getThrottle()));
        drawRight(font, throttle, width, height - MARGIN - LINE, WHITE);
        if (type.isImpactDetonates() && type.getPayloadCapacity() > 0) {
            drawRight(font, I18n.format("hud.combat_drones.grenades", drone.getKnownPayload(), type.getPayloadCapacity()),
                    width, height - MARGIN - LINE * 2, WHITE);
        }

        drawCrosshair(width / 2, height / 2);
        drawWarnings(font, minecraft, width, height, pack != null, battery, signal);
    }

    private static void drawWarnings(FontRenderer font, Minecraft minecraft, int width, int height, boolean hasBattery, double battery, double signal) {
        // Blink at 2 Hz like a real OSD
        if (minecraft.world.getTotalWorldTime() / 5 % 2 == 0) {
            return;
        }
        int y = height / 2 + 20;
        if (!hasBattery || battery <= 0) {
            drawCentered(font, I18n.format("hud.combat_drones.warning.battery_empty"), width, y, WARNING);
            y += LINE;
        } else if (battery < LOW_BATTERY) {
            drawCentered(font, I18n.format("hud.combat_drones.warning.low_battery"), width, y, WARNING);
            y += LINE;
        }
        if (signal < LOW_SIGNAL) {
            drawCentered(font, I18n.format("hud.combat_drones.warning.low_signal"), width, y, WARNING);
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

    private static void drawLeft(FontRenderer font, String text, int y, int color) {
        font.drawStringWithShadow(text, MARGIN, y, color);
    }

    private static void drawRight(FontRenderer font, String text, int width, int y, int color) {
        font.drawStringWithShadow(text, width - MARGIN - font.getStringWidth(text), y, color);
    }

    private static void drawCentered(FontRenderer font, String text, int width, int y, int color) {
        font.drawStringWithShadow(text, (width - font.getStringWidth(text)) / 2.0F, y, color);
    }

    private static void drawBar(int x, int y, int barWidth, double fraction, int color) {
        Gui.drawRect(x, y, x + barWidth, y + 3, 0x80000000);
        Gui.drawRect(x, y, x + (int) Math.round(barWidth * Math.max(0, Math.min(1, fraction))), y + 3, color);
    }

    private static void drawCrosshair(int centerX, int centerY) {
        Gui.drawRect(centerX - 4, centerY, centerX - 1, centerY + 1, 0xC0FFFFFF);
        Gui.drawRect(centerX + 2, centerY, centerX + 5, centerY + 1, 0xC0FFFFFF);
        Gui.drawRect(centerX, centerY - 3, centerX + 1, centerY - 1, 0xC0FFFFFF);
    }
}
