package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.client.hud.Feed;
import io.github.duckysmacky.combatdrones.client.hud.FpvFeed;
import io.github.duckysmacky.combatdrones.client.hud.MissileFeed;
import io.github.duckysmacky.combatdrones.client.hud.Ui;
import io.github.duckysmacky.combatdrones.client.hud.UnitFeed;
import io.github.duckysmacky.combatdrones.client.hud.Widgets;
import io.github.duckysmacky.combatdrones.entity.EntityCruiseMissile;
import io.github.duckysmacky.combatdrones.entity.EntityHandFlownDrone;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;

/**
 * The on-screen display of whatever the player is flying or watching. Each vehicle has its own camera feed
 * ({@link Feed}): this class picks it and hands over to the feed's drawing class in {@code client.hud}; all of them
 * share one style ({@link Ui}) and are laid out in a box around the screen centre, so they read the same at any
 * GUI scale.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public final class HudOverlay {
    /** Vanilla HUD parts that make no sense on a camera feed. Health and chat stay. */
    private static final Set<ElementType> HIDDEN_WHILE_FLYING = EnumSet.of(ElementType.HOTBAR, ElementType.CROSSHAIRS, ElementType.FOOD,
            ElementType.EXPERIENCE, ElementType.ARMOR, ElementType.AIR, ElementType.JUMPBAR);
    /** How long the INBOUND banner stays up (ms). */
    private static final long STRIKE_ALERT_MILLIS = 6000;
    private static long strikeAlertUntil;
    /** What the current feed is looking through, and when it came up (for the flight timer and the link-up static). */
    private static Entity session;
    private static long sessionStart;

    private HudOverlay() {
    }

    /** A cruise missile started diving nearby: show the INBOUND banner for a few seconds. */
    static void showStrikeAlert() {
        strikeAlertUntil = Minecraft.getSystemTime() + STRIKE_ALERT_MILLIS;
    }

    /** The feed the player is looking through right now, or null on foot. */
    @Nullable
    public static Feed currentFeed() {
        EntityRampUnit unit = RemoteController.getUnit();
        if (unit != null) {
            DroneType type = unit.getDroneType();
            return unit instanceof EntityCruiseMissile ? Feed.MISSILE : type == null ? null
                    : RemoteController.isBombSight() ? Feed.BOMBSIGHT : Feed.of(type);
        }
        EntityHandFlownDrone drone = PilotController.getPilotedDrone();
        return drone == null || drone.getDroneType() == null ? null : Feed.of(drone.getDroneType());
    }

    /** Milliseconds since the current feed came up (0 on foot). */
    public static long sessionMillis() {
        Entity now = RemoteController.getUnit() != null ? RemoteController.getUnit() : PilotController.getPilotedDrone();
        if (now != session) {
            session = now;
            sessionStart = Minecraft.getSystemTime();
        }
        return now == null ? 0 : Minecraft.getSystemTime() - sessionStart;
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
        ScaledResolution resolution = event.getResolution();
        long flown = sessionMillis();
        Feed feed = currentFeed();
        EntityRampUnit unit = RemoteController.getUnit();
        EntityHandFlownDrone drone = PilotController.getPilotedDrone();
        if (feed != null && unit != null) {
            Widgets.hitFlash(resolution, unit.hitFlashTicks);
            if (unit instanceof EntityCruiseMissile) {
                MissileFeed.draw(resolution, (EntityCruiseMissile) unit);
            } else {
                UnitFeed.draw(resolution, unit, unit.getDroneType(), feed, event.getPartialTicks());
            }
        } else if (feed != null && drone != null) {
            Widgets.hitFlash(resolution, drone.hitFlashTicks);
            FpvFeed.draw(resolution, drone, drone.getDroneType(), feed, flown, event.getPartialTicks());
        }
        int top = 4;
        if (Minecraft.getSystemTime() < strikeAlertUntil) {
            drawStrikeAlert(resolution);
            top += 38;
        }
        drawLockAlert(resolution, top);
    }

    /**
     * A recon UAV is after this player: a blinking LOCK-ON WARNING with a filling bar while a lock-on builds, then a
     * steady TRACKED banner with the time left while it holds.
     */
    private static void drawLockAlert(ScaledResolution resolution, int top) {
        float building = ClientIntel.getWarningProgress();
        long lockedMillis = ClientIntel.getLockedMillisLeft();
        if (building <= 0 && lockedMillis <= 0) {
            return;
        }
        int centerX = resolution.getScaledWidth() / 2;
        boolean bright = Ui.blink(building > 0 ? 4 : 1);
        String title = I18n.format(building > 0 ? "hud.combat_drones.lock_warning" : "hud.combat_drones.lock_tracked");
        String sub = building > 0 ? I18n.format("hud.combat_drones.lock_warning.sub")
                : I18n.format("hud.combat_drones.lock_tracked.sub", Widgets.clock(lockedMillis));
        int halfWidth = Math.max(Ui.width(title), Ui.width(sub) / 2) + 14;
        Ui.rect(centerX - halfWidth, top, centerX + halfWidth, top + 34, bright ? 0xF08A1F20 : 0xF05E1418);
        Ui.frame(centerX - halfWidth, top, halfWidth * 2, 34);
        Ui.textScaled(title, centerX, top + 5, 2, bright ? Ui.TEXT : 0xFFEE7A5E);
        Ui.textCentered(sub, centerX, top + 23, Ui.TEXT);
        if (building > 0) {
            Ui.bar(centerX - halfWidth + 3, top + 31, halfWidth * 2 - 6, 2, building, Ui.WARN);
        }
    }

    /** Blinking red banner at the top of the screen, double size: a cruise missile is diving on this area. */
    private static void drawStrikeAlert(ScaledResolution resolution) {
        int centerX = resolution.getScaledWidth() / 2;
        boolean bright = Ui.blink(2);
        String title = I18n.format("hud.combat_drones.strike_alert");
        int halfWidth = Ui.width(title) + 14;
        Ui.rect(centerX - halfWidth, 4, centerX + halfWidth, 38, bright ? 0xF08A1F20 : 0xF05E1418);
        Ui.frame(centerX - halfWidth, 4, halfWidth * 2, 34);
        Ui.textScaled(title, centerX, 9, 2, bright ? Ui.TEXT : 0xFFEE7A5E);
        Ui.textCentered(I18n.format("hud.combat_drones.strike_alert.sub"), centerX, 27, Ui.TEXT);
    }
}
