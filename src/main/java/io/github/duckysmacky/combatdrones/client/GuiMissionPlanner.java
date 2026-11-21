package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.client.hud.UiSounds;
import io.github.duckysmacky.combatdrones.client.hud.Ui;
import io.github.duckysmacky.combatdrones.client.hud.UiButton;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import io.github.duckysmacky.combatdrones.item.ItemMissionPlanner;
import io.github.duckysmacky.combatdrones.type.Loadout;
import io.github.duckysmacky.combatdrones.entity.EntityLaunchRamp;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.network.PlannerMarkPacket;
import io.github.duckysmacky.combatdrones.network.PlannerOpenPacket;
import io.github.duckysmacky.combatdrones.network.PlannerRequestPacket;
import io.github.duckysmacky.combatdrones.network.PlannerSubmitPacket;
import io.github.duckysmacky.combatdrones.network.RemoteRequestPacket;
import io.github.duckysmacky.combatdrones.physics.BombBallistics;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.github.duckysmacky.combatdrones.physics.RouteAutopilot;
import io.github.duckysmacky.combatdrones.type.AerialBombType;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mission Planner: an abstract grid map (north up, one grid square per chunk) of the player's units: grounded on
 * linked ramps (plan, save with Apply, Launch or Launch &amp; Fly) and in flight (watch, re-route, View or Take control).
 * The client only proposes; the server validates every route.
 *
 * <p>Map controls (like the reference mod): left-drag pans, or moves a waypoint when grabbed on one; right-click opens
 * a context menu (add a waypoint, or set a waypoint's height, speed, final action, loiter radius, or remove it); the
 * wheel zooms. The side list selects waypoints for reordering.
 */
@Mod.EventBusSubscriber(modid = CombatDrones.MOD_ID, value = Side.CLIENT)
public class GuiMissionPlanner extends GuiScreen {
    private static final int PANEL_WIDTH = 140;
    private static final int MARGIN = 8;
    private static final int ROW = 11;
    private static final int GRID_COLOR = 0x30FFFFFF;
    private static final int ROUTE_COLOR = Ui.WARN;
    private static final int ROUTE_FAINT = 0x80F2CC3C;
    private static final int UNIT_COLOR = Ui.OK;
    /** Scale bar: the round number of blocks whose bar comes closest to this many pixels. */
    private static final int SCALE_BAR_PIXELS = 60;
    private static final int[] SCALE_BAR_BLOCKS = {5, 10, 20, 50, 100, 200, 500, 1000, 2000};
    /** Map icon size of an airborne unit, GUI pixels along its longest side. */
    private static final float UNIT_ICON_SIZE = 18;
    private static final int WARNING = Ui.DANGER;
    private static final double MIN_SCALE = 0.1;
    private static final double MAX_SCALE = 8;
    private static final double PICK_RADIUS_PX = 6;
    /** Fallback refresh for units outside client tracking range; tracked ones are read from their entity each frame. */
    private static final int REFRESH_TICKS = 4;

    private static final int BUTTON_TERMINAL = 0;
    private static final int BUTTON_CLEAR = 1;
    private static final int BUTTON_SEND = 2;
    private static final int BUTTON_LAUNCH = 3;
    private static final int BUTTON_CLOSE = 4;
    private static final int BUTTON_UP = 5;
    private static final int BUTTON_DOWN = 6;
    private static final int BUTTON_ALT_DOWN = 7;
    private static final int BUTTON_ALT_UP = 8;
    private static final int BUTTON_DELETE = 9;
    private static final int BUTTON_VIEW = 10;
    private static final int BUTTON_CONTROL = 11;
    private static final int BUTTON_MARK = 12;
    private static final int BUTTON_LAUNCH_FLY = 13;
    private static final int BUTTON_RETURN = 14;
    private static final int RUNWAY_COLOR = 0xC0E8E8E8;
    private static final int RUNWAY_TAKEN = 0x80808890;
    private static final int TARGET_COLOR = Ui.DANGER;
    private static final int BORDER_COLOR = 0xC0D94F3C;
    private static final int DROP_FAINT = 0x90D94F3C;
    /** Carpet run length a drop point starts with, and the step the menu changes it by. */
    private static final double DEFAULT_CARPET = 64;
    private static final double CARPET_STEP = 16;
    /** A carpet's strip on the map is this many blocks wide per point of blast power, each side of the line. */
    private static final double BLAST_REACH = 1.5;

    /**
     * The unit selected when the planner was last open, picked again on the next open. Matched by entity or ramp ID,
     * so a unit remembered on its ramp isn't found again once it has launched (it gets a new entity).
     */
    private static PlannerOpenPacket.UnitView lastSelected;

    private PlannerOpenPacket view;
    /** Index into view.units of the unit being edited. */
    private int selectedUnit;
    private final List<Route.Waypoint> waypoints = new ArrayList<>();
    private Route.TerminalAction terminal = Route.TerminalAction.LOITER;
    private double loiterRadius = Route.DEFAULT_LOITER_RADIUS;
    /** Destroy target; its Y is NaN when picked on the map (the server finds the ground). */
    private Route.Target target;
    /** Runway picked to land on ("Land here"), or null for the unit's home runway. */
    private Route.Runway runway;
    /** Set once the player edits, so live refreshes don't overwrite their unsent changes. */
    private boolean dirty;
    private int selectedWaypoint = -1;

    // Map view
    private double centerX;
    private double centerZ;
    private double scale = 1.0;
    private int mapLeft;
    private int mapTop;
    private int mapRight;
    private int mapBottom;
    private int dragWaypoint = -1;
    private boolean panning;
    private int lastMouseX;
    private int lastMouseY;
    private int ticks;
    private int cursorX;
    private int cursorY;
    private PlannerContextMenu menu;
    private PlannerTerrainMap terrain;
    /** While true the loiter radius follows the cursor's distance from the last waypoint. */
    private boolean editingRadius;
    /** Bombs the route on screen drops in all (counted while its drop points are drawn). */
    private int bombsNeeded;

    private GuiButton terminalButton;
    private GuiButton launchButton;
    private GuiButton launchFlyButton;
    private GuiButton viewButton;
    private GuiButton controlButton;
    private GuiButton markButton;
    private GuiButton returnButton;

    public GuiMissionPlanner(PlannerOpenPacket view) {
        this.view = view;
        int index = indexOf(view.selectId);
        for (int candidate = 0; index < 0 && candidate < view.units.size(); candidate++) {
            if (view.units.get(candidate).sameUnit(lastSelected)) {
                index = candidate;
            }
        }
        selectUnit(Math.max(0, index));
    }

    /** Entity ID of the last selected unit if it was in flight, else -1. */
    static int lastUnitEntityId() {
        return lastSelected == null ? -1 : lastSelected.entityId;
    }

    /** Shift+right-click with the planner: take control of the last selected unit, else the first flyable one. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld().isRemote && event.getEntityPlayer().isSneaking() && event.getItemStack().getItem() instanceof ItemMissionPlanner) {
            ModNetwork.CHANNEL.sendToServer(new RemoteRequestPacket(lastUnitEntityId(), true, true));
        }
    }

    @SubscribeEvent
    public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        lastSelected = null;
    }

    /** Index of the unit in flight with entity ID {@code id}, or on the ramp with that ID; -1 if none. */
    private int indexOf(int id) {
        for (int index = 0; id >= 0 && index < view.units.size(); index++) {
            PlannerOpenPacket.UnitView unit = view.units.get(index);
            if (unit.entityId == id || unit.rampId == id) {
                return index;
            }
        }
        return -1;
    }

    /** New data from the server: keep the player's unsent edits, refresh everything else. */
    public void update(PlannerOpenPacket packet) {
        PlannerOpenPacket.UnitView previous = currentUnit();
        view = packet;
        int index = indexOf(packet.selectId);
        for (int candidate = 0; index < 0 && candidate < view.units.size(); candidate++) {
            if (view.units.get(candidate).sameUnit(previous)) {
                index = candidate;
            }
        }
        index = Math.max(0, index);
        boolean sameUnit = index == selectedUnit && view.units.size() > index && view.units.get(index).sameUnit(previous);
        if (!sameUnit) {
            selectUnit(index);
        } else if (!dirty) {
            // Periodic refresh of the unit being viewed: keep the map position and selection
            loadRoute();
        }
        lastSelected = currentUnit();
    }

    private void selectUnit(int index) {
        selectedUnit = index;
        selectedWaypoint = -1;
        loadRoute();
        PlannerOpenPacket.UnitView unit = currentUnit();
        lastSelected = unit;
        if (unit != null) {
            centerX = unit.x;
            centerZ = unit.z;
        } else if (Minecraft.getMinecraft().player != null) {
            // Nothing linked yet: start over the player (also called from the constructor, before mc is set)
            centerX = Minecraft.getMinecraft().player.posX;
            centerZ = Minecraft.getMinecraft().player.posZ;
        }
    }

    private void loadRoute() {
        waypoints.clear();
        dirty = false;
        PlannerOpenPacket.UnitView unit = currentUnit();
        if (unit == null) {
            selectedWaypoint = -1;
            return;
        }
        waypoints.addAll(unit.route.getWaypoints());
        terminal = unit.allowedTerminals.contains(unit.route.getTerminal()) ? unit.route.getTerminal() : firstAllowed(unit);
        loiterRadius = unit.route.getLoiterRadius();
        target = unit.route.getTarget();
        runway = unit.route.getRunway();
        if (selectedWaypoint >= waypoints.size()) {
            selectedWaypoint = -1;
        }
        clampToLoiterMinimums();
    }

    private PlannerOpenPacket.UnitView currentUnit() {
        return selectedUnit >= 0 && selectedUnit < view.units.size() ? view.units.get(selectedUnit) : null;
    }

    private static Route.TerminalAction firstAllowed(PlannerOpenPacket.UnitView unit) {
        for (Route.TerminalAction action : Route.TerminalAction.values()) {
            if (unit.allowedTerminals.contains(action)) {
                return action;
            }
        }
        return Route.TerminalAction.LOITER;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        mapLeft = PANEL_WIDTH + MARGIN * 2;
        mapTop = 28;
        mapRight = width - MARGIN;
        mapBottom = height - 44;
        int y = height - 26;
        int x = mapLeft;
        // Sized to their labels, so the row still fits the map's width on a small screen
        String clear = I18n.format("gui.combat_drones.planner.clear");
        addButton(new UiButton(BUTTON_CLEAR, x, y, fit(clear), 20, clear));
        x += fit(clear) + 4;
        String apply = I18n.format("gui.combat_drones.planner.apply");
        addButton(new UiButton(BUTTON_SEND, x, y, fit(apply), 20, apply));
        x += fit(apply) + 4;
        // Same slot, swapped by refreshButtons: Launch / Launch & Fly for a grounded unit, View / Take control /
        // Return to runway in flight
        String launch = I18n.format("gui.combat_drones.planner.launch");
        String launchFly = I18n.format("gui.combat_drones.planner.launch_fly");
        launchButton = addButton(new UiButton(BUTTON_LAUNCH, x, y, fit(launch), 20, launch));
        launchFlyButton = addButton(new UiButton(BUTTON_LAUNCH_FLY, x + fit(launch) + 4, y, fit(launchFly), 20, launchFly));
        String viewLabel = I18n.format("gui.combat_drones.planner.view");
        String control = I18n.format("gui.combat_drones.planner.take_control");
        viewButton = addButton(new UiButton(BUTTON_VIEW, x, y, fit(viewLabel), 20, viewLabel));
        x += fit(viewLabel) + 4;
        controlButton = addButton(new UiButton(BUTTON_CONTROL, x, y, fit(control), 20, control));
        x += fit(control) + 4;
        String returnLabel = I18n.format("gui.combat_drones.planner.return_runway");
        if (x + fit(returnLabel) > mapRight) {
            returnLabel = I18n.format("gui.combat_drones.planner.return_short");
        }
        returnButton = addButton(new UiButton(BUTTON_RETURN, x, y, Math.min(fit(returnLabel), mapRight - x), 20, returnLabel));
        addButton(new UiButton(BUTTON_CLOSE, width - MARGIN - 50, 5, 50, 18, I18n.format("gui.done")));

        int panelY = height - 26;
        addButton(new UiButton(BUTTON_UP, MARGIN, panelY, 20, 20, "▲"));
        addButton(new UiButton(BUTTON_DOWN, MARGIN + 22, panelY, 20, 20, "▼"));
        addButton(new UiButton(BUTTON_ALT_DOWN, MARGIN + 46, panelY, 26, 20, "-5"));
        addButton(new UiButton(BUTTON_ALT_UP, MARGIN + 74, panelY, 26, 20, "+5"));
        addButton(new UiButton(BUTTON_DELETE, MARGIN + 104, panelY, 36, 20, I18n.format("gui.combat_drones.planner.delete")));
        markButton = addButton(new UiButton(BUTTON_MARK, MARGIN, panelY - 22, PANEL_WIDTH, 20, I18n.format("gui.combat_drones.planner.mark")));
        terminalButton = addButton(new UiButton(BUTTON_TERMINAL, MARGIN, panelY - 44, PANEL_WIDTH, 20, ""));
        refreshButtons();
    }

    /** Button width for a label: the text plus padding. */
    private int fit(String label) {
        return fontRenderer.getStringWidth(label) + 12;
    }

    private void refreshButtons() {
        // Runs after every edit: keep the orbit at the unit's minimums, as the server will on Apply
        clampToLoiterMinimums();
        PlannerOpenPacket.UnitView unit = currentUnit();
        terminalButton.displayString = I18n.format("gui.combat_drones.planner.terminal",
                I18n.format("gui.combat_drones.terminal." + terminal.name().toLowerCase(Locale.ROOT)));
        terminalButton.enabled = unit != null && unit.allowedTerminals.size() > 1;
        boolean editable = unit != null;
        for (GuiButton button : buttonList) {
            if (button.id >= BUTTON_UP && button.id <= BUTTON_DELETE) {
                button.enabled = editable && selectedWaypoint >= 0;
            } else if (button.id == BUTTON_SEND || button.id == BUTTON_CLEAR) {
                button.enabled = editable;
            }
        }
        markButton.visible = unit != null && unit.allowedTerminals.contains(Route.TerminalAction.DESTROY);
        DroneType type = unit == null ? null : DroneTypes.get(unit.typeId);
        boolean grounded = unit != null && unit.isGrounded();
        viewButton.visible = !grounded;
        controlButton.visible = !grounded;
        viewButton.enabled = unit != null && unit.entityId >= 0;
        controlButton.enabled = viewButton.enabled && type != null && type.getControlScheme() != null && type.getControlScheme().isFlyable();
        boolean fueled = grounded && unit.fuelFraction >= view.minLaunchFuel;
        launchButton.visible = grounded;
        launchButton.enabled = fueled && (!waypoints.isEmpty() || terminal == Route.TerminalAction.RETURN);
        // Hand-flown straight off the ramp: only stick types, which need no route (without one they fly straight on)
        launchFlyButton.visible = grounded && type != null && type.getControlScheme() == DroneType.ControlScheme.STICK;
        launchFlyButton.enabled = fueled;
        returnButton.visible = !grounded && unit != null && unit.allowedTerminals.contains(Route.TerminalAction.RETURN);
        returnButton.enabled = returnButton.visible && !unit.recalling && landingRunway(unit) != null;
    }

    /** Where the selected route's Return lands: the runway picked on the map, else the unit's own. */
    private Route.Runway landingRunway(PlannerOpenPacket.UnitView unit) {
        return runway != null ? runway : unit.homeRunway;
    }

    @Override
    public void updateScreen() {
        // Units move (and ramps get loaded or launched): keep positions, fuel and progress fresh while the map is open
        if (++ticks % REFRESH_TICKS == 0) {
            ModNetwork.CHANNEL.sendToServer(new PlannerRequestPacket());
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (terrain != null) {
            terrain.delete();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---- Drawing ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        followTrackedUnits(partialTicks);
        cursorX = mouseX;
        cursorY = mouseY;
        // Ground-station frame: a title bar, the unit and route panel down the left, the map in its own frame
        Ui.panel(MARGIN - 4, 3, width - MARGIN * 2 + 8, 22);
        fontRenderer.drawStringWithShadow(title(), MARGIN + 2, 10, Ui.TEXT);
        String summary = I18n.format("gui.combat_drones.planner.units") + " " + view.units.size();
        fontRenderer.drawStringWithShadow(summary, MARGIN + 10 + fontRenderer.getStringWidth(title()), 10, Ui.FAINT);
        drawPanel(mouseX, mouseY);
        drawMap(mouseX, mouseY);
        drawStats();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (editingRadius && !waypoints.isEmpty()) {
            drawCenteredString(fontRenderer, I18n.format("gui.combat_drones.planner.radius_hint", Math.round(previewRadius(mouseX, mouseY))),
                    (mapLeft + mapRight) / 2, mapTop + 14, Ui.SELECT);
        }
        if (menu != null) {
            menu.draw(fontRenderer, width, height, mouseX, mouseY);
        }
    }

    private String title() {
        return I18n.format("gui.combat_drones.planner.title");
    }

    private static String unitName(PlannerOpenPacket.UnitView unit) {
        return I18n.format("item.combat_drones." + unit.typeId + ".name");
    }

    private void drawPanel(int mouseX, int mouseY) {
        Ui.panel(MARGIN - 4, mapTop - 3, PANEL_WIDTH + 8, height - 3 - (mapTop - 3));
        int y = mapTop;
        fontRenderer.drawStringWithShadow(I18n.format("gui.combat_drones.planner.units"), MARGIN, y, Ui.DIM);
        y += ROW;
        if (view.units.isEmpty()) {
            fontRenderer.drawStringWithShadow(I18n.format("gui.combat_drones.planner.no_units"), MARGIN, y, Ui.FAINT);
            y += ROW;
        }
        for (int index = 0; index < view.units.size(); index++) {
            PlannerOpenPacket.UnitView unit = view.units.get(index);
            String tag = unit.isGrounded() ? I18n.format("gui.combat_drones.planner.on_ramp") + " "
                    : unit.recalling ? I18n.format("gui.combat_drones.planner.returning") + " " : "";
            // Number keys 1..9, 0 pick the first ten rows
            String prefix = (index < 10 ? (index + 1) % 10 + ". " : "") + tag;
            String line = prefix + fontRenderer.trimStringToWidth(unitName(unit), PANEL_WIDTH - 34 - fontRenderer.getStringWidth(prefix)) + " "
                    + percent(unit.fuelFraction) + "%";
            int color = index == selectedUnit ? Ui.SELECT : unit.isGrounded() ? Ui.FRIENDLY : Ui.TEXT;
            if (index == selectedUnit) {
                Ui.rect(MARGIN - 3, y - 2, MARGIN + PANEL_WIDTH + 3, y + ROW - 2, 0x50FBE678);
            }
            // Fuel as a bar under the row, so the list reads at a glance
            Ui.rect(MARGIN, y + 8, MARGIN + (int) Math.round(PANEL_WIDTH * Math.max(0, Math.min(1, unit.fuelFraction))), y + 9,
                    unit.fuelFraction < view.minLaunchFuel ? Ui.DANGER : Ui.alpha(Ui.OK, 0.6F));
            fontRenderer.drawStringWithShadow(line, MARGIN, y - 1, color);
            y += ROW;
        }
        y += 4;
        // A grounded unit's ammo, checked before launch (in flight the HUD shows it)
        PlannerOpenPacket.UnitView selected = currentUnit();
        DroneType selectedType = selected == null || !selected.isGrounded() ? null : DroneTypes.get(selected.typeId);
        for (int station = 0; selectedType != null && station < Loadout.STATIONS; station++) {
            if (selectedType.getWeapon(station) != DroneType.WeaponType.NONE) {
                int ammo = selected.loadout.getAmmo(station);
                fontRenderer.drawStringWithShadow(I18n.format("hud.combat_drones.ammo." + ItemDrone.ammoKey(selectedType, station, selected.loadout), ammo,
                        selected.loadout.capacity(selectedType, station)), MARGIN, y, ammo == 0 ? WARNING : Ui.TEXT);
                y += ROW;
            }
        }
        fontRenderer.drawStringWithShadow(I18n.format("gui.combat_drones.planner.waypoints", waypoints.size(), view.maxWaypoints), MARGIN, y, Ui.DIM);
        y += ROW;
        for (int index = 0; index < waypoints.size() && y < height - 84; index++) {
            Route.Waypoint waypoint = waypoints.get(index);
            String line = String.format(Locale.ROOT, "#%d  %d, %d  ↑%d %s", index + 1, Math.round(waypoint.x), Math.round(waypoint.z),
                    Math.round(waypoint.altitude), waypoint.speed.name().charAt(0))
                    + (waypoint.dropLength > 0 ? " C" + Math.round(waypoint.dropLength) : waypoint.isDrop() ? " B" : "");
            boolean badAltitude = waypoint.altitude < view.minAltitude || waypoint.altitude > view.maxAltitude;
            int color = index == selectedWaypoint ? Ui.SELECT : badAltitude ? WARNING : Ui.TEXT;
            if (index == selectedWaypoint) {
                Ui.rect(MARGIN - 3, y - 2, MARGIN + PANEL_WIDTH + 3, y + ROW - 2, 0x50FBE678);
            }
            fontRenderer.drawStringWithShadow(line, MARGIN, y - 1, color);
            y += ROW;
        }
    }

    private void drawMap(int mouseX, int mouseY) {
        Ui.rect(mapLeft, mapTop, mapRight, mapBottom, Ui.SCREEN);
        Ui.frame(mapLeft - 1, mapTop - 1, mapRight - mapLeft + 2, mapBottom - mapTop + 2);
        if (terrain == null) {
            terrain = new PlannerTerrainMap();
        }
        terrain.update(mc.world, centerX, centerZ, (mapRight - mapLeft) / scale);
        terrain.draw(mapLeft, mapTop, mapRight, mapBottom, centerX, centerZ, scale);
        PlannerOpenPacket.UnitView unit = currentUnit();

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        drawGrid(buffer);
        drawWorldBorder(buffer);
        drawRunways(buffer, unit);
        if (unit != null) {
            drawRoute(buffer, unit);
            drawDrops(buffer, unit);
            drawTarget(buffer);
        }
        for (PlannerOpenPacket.UnitView other : view.units) {
            if (!other.isGrounded()) {
                drawUnitMarker(buffer, other);
            }
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
        drawUnitIcons();

        // Markers and labels
        for (PlannerOpenPacket.UnitView other : view.units) {
            if (other.isGrounded()) {
                drawMarker(other.x, other.z, other == unit ? Ui.SELECT : Ui.FRIENDLY, 3);
            }
        }
        for (int index = 0; index < waypoints.size(); index++) {
            Route.Waypoint waypoint = waypoints.get(index);
            int screenX = toScreenX(waypoint.x);
            int screenY = toScreenY(waypoint.z);
            if (insideMap(screenX, screenY)) {
                drawMarker(waypoint.x, waypoint.z, index == selectedWaypoint ? Ui.SELECT : waypoint.isDrop() ? TARGET_COLOR : ROUTE_COLOR, 3);
                fontRenderer.drawStringWithShadow(String.valueOf(index + 1), screenX + 5, screenY - 4, Ui.TEXT);
            }
        }
        if (mc.player != null) {
            drawMarker(mc.player.posX, mc.player.posZ, Ui.TEXT, 2);
        }
        drawIntel();
        int bay = unit == null || currentType() == null ? -1 : currentType().stationOf(DroneType.WeaponType.BOMB_BAY);
        if (bay >= 0 && bombsNeeded > 0) {
            int loaded = unit.loadout.getAmmo(bay);
            Ui.text(I18n.format("gui.combat_drones.planner.bombs", bombsNeeded, loaded), mapLeft + 4, mapTop + 4, bombsNeeded > loaded ? WARNING : Ui.TEXT);
        }
        // North mark, the scale bar, and the cursor's coordinates
        int north = (mapLeft + mapRight) / 2;
        Ui.block(north, mapTop + 3, north + 1, mapTop + 4, Ui.TEXT);
        Ui.block(north - 1, mapTop + 4, north + 2, mapTop + 5, Ui.TEXT);
        Ui.textCentered(I18n.format("hud.combat_drones.compass.n"), north + 1, mapTop + 8, Ui.TEXT);
        int blocks = SCALE_BAR_BLOCKS[0];
        for (int candidate : SCALE_BAR_BLOCKS) {
            if (Math.abs(candidate * scale - SCALE_BAR_PIXELS) < Math.abs(blocks * scale - SCALE_BAR_PIXELS)) {
                blocks = candidate;
            }
        }
        int barWidth = (int) Math.round(blocks * scale);
        if (barWidth < mapRight - mapLeft - 12) {
            Ui.hline(mapRight - 6 - barWidth, mapRight - 6, mapBottom - 6, Ui.TEXT);
            Ui.vline(mapRight - 6 - barWidth, mapBottom - 9, mapBottom - 5, Ui.TEXT);
            Ui.vline(mapRight - 7, mapBottom - 9, mapBottom - 5, Ui.TEXT);
            Ui.textRight(I18n.format("hud.combat_drones.metres", blocks), mapRight - 6, mapBottom - 17, Ui.TEXT);
        }
        if (insideMap(mouseX, mouseY)) {
            Ui.text(I18n.format("hud.combat_drones.position", Math.round(toWorldX(mouseX)), Math.round(toWorldZ(mouseY))), mapLeft + 4, mapBottom - 10,
                    Ui.DIM);
        }
    }

    /**
     * What the recon tracks: a dot for each tracked target, a diamond with a name for a locked-on one. Players red,
     * anything else yellow.
     */
    private void drawIntel() {
        for (IntelPacket.Entry mark : ClientIntel.getMarks()) {
            int screenX = toScreenX(mark.x);
            int screenY = toScreenY(mark.z);
            if (!insideMap(screenX, screenY)) {
                continue;
            }
            int color = mark.player ? Ui.DANGER : Ui.WARN;
            if (mark.locked) {
                for (int row = -4; row <= 4; row++) {
                    int half = 4 - Math.abs(row);
                    Gui.drawRect(screenX - half, screenY + row, screenX + half + 1, screenY + row + 1, color);
                }
                fontRenderer.drawStringWithShadow(mark.name, screenX + 6, screenY - 4, color);
            } else {
                Gui.drawRect(screenX - 2, screenY - 2, screenX + 3, screenY + 3, color);
            }
        }
    }

    private void drawGrid(BufferBuilder buffer) {
        // One square per chunk, coarser when zoomed out so lines don't merge
        int spacing = 16;
        while (spacing * scale < 8) {
            spacing *= 2;
        }
        double firstX = Math.floor(toWorldX(mapLeft) / spacing) * spacing;
        for (double worldX = firstX; toScreenX(worldX) <= mapRight; worldX += spacing) {
            line(buffer, toScreenX(worldX), mapTop, toScreenX(worldX), mapBottom, GRID_COLOR);
        }
        double firstZ = Math.floor(toWorldZ(mapTop) / spacing) * spacing;
        for (double worldZ = firstZ; toScreenY(worldZ) <= mapBottom; worldZ += spacing) {
            line(buffer, mapLeft, toScreenY(worldZ), mapRight, toScreenY(worldZ), GRID_COLOR);
        }
    }

    /** The world border (the playable area), as the client knows it from vanilla's border packet. */
    private void drawWorldBorder(BufferBuilder buffer) {
        WorldBorder border = mc.world.getWorldBorder();
        clippedLine(buffer, border.minX(), border.minZ(), border.maxX(), border.minZ(), BORDER_COLOR);
        clippedLine(buffer, border.maxX(), border.minZ(), border.maxX(), border.maxZ(), BORDER_COLOR);
        clippedLine(buffer, border.maxX(), border.maxZ(), border.minX(), border.maxZ(), BORDER_COLOR);
        clippedLine(buffer, border.minX(), border.maxZ(), border.minX(), border.minZ(), BORDER_COLOR);
    }

    /** Destroy target as a red cross, joined to the last waypoint when Destroy is selected. */
    private void drawTarget(BufferBuilder buffer) {
        if (target == null) {
            return;
        }
        double arm = 5 / scale;
        clippedLine(buffer, target.x - arm, target.z - arm, target.x + arm, target.z + arm, TARGET_COLOR);
        clippedLine(buffer, target.x - arm, target.z + arm, target.x + arm, target.z - arm, TARGET_COLOR);
        if (terminal == Route.TerminalAction.DESTROY && !waypoints.isEmpty()) {
            Route.Waypoint last = waypoints.get(waypoints.size() - 1);
            clippedLine(buffer, last.x, last.z, target.x, target.z, 0x80D94F3C);
        }
    }

    /**
     * A bomber's drop points: a line from each to a cross where its bomb should land (released as the waypoint is
     * passed, thrown on by the leg's speed from the waypoint's height over the ground there), and for a carpet run the
     * strip it covers from that point on, as wide as the blasts reach, with a tick per bomb.
     */
    private void drawDrops(BufferBuilder buffer, PlannerOpenPacket.UnitView unit) {
        bombsNeeded = 0;
        DroneType type = currentType();
        if (type == null || type.stationOf(DroneType.WeaponType.BOMB_BAY) < 0) {
            return;
        }
        AerialBombType kind = unit.loadout.getBombKind();
        double fromX = unit.x;
        double fromZ = unit.z;
        for (int index = dirty || unit.isGrounded() ? 0 : Math.min(unit.waypointIndex, waypoints.size()); index < waypoints.size(); index++) {
            Route.Waypoint waypoint = waypoints.get(index);
            double leg = Math.hypot(waypoint.x - fromX, waypoint.z - fromZ);
            double alongX = leg < 1e-3 ? 0 : (waypoint.x - fromX) / leg;
            double alongZ = leg < 1e-3 ? 0 : (waypoint.z - fromZ) / leg;
            fromX = waypoint.x;
            fromZ = waypoint.z;
            if (!waypoint.isDrop() || leg < 1e-3) {
                continue;
            }
            double speed = type.getMaxSpeed() * waypoint.speed.fraction;
            // The autopilot counts a waypoint as passed a little before it
            double early = Math.min(leg, RouteAutopilot.acceptRadius(speed));
            double releaseX = waypoint.x - alongX * early;
            double releaseZ = waypoint.z - alongZ * early;
            double landX = releaseX;
            double landZ = releaseZ;
            for (int pass = 0; pass < 2; pass++) {
                double height = unit.originY + waypoint.altitude - groundAt(landX, landZ, unit.originY - 1);
                double thrown = BombBallistics.fall(speed, 0, height).forward;
                landX = releaseX + alongX * thrown;
                landZ = releaseZ + alongZ * thrown;
            }
            clippedLine(buffer, waypoint.x, waypoint.z, landX, landZ, DROP_FAINT);
            double arm = 4 / scale;
            clippedLine(buffer, landX - arm, landZ - arm, landX + arm, landZ + arm, TARGET_COLOR);
            clippedLine(buffer, landX - arm, landZ + arm, landX + arm, landZ - arm, TARGET_COLOR);
            if (waypoint.dropLength <= 0) {
                bombsNeeded++;
                continue;
            }
            double spacing = Math.max(0.05, speed * kind.getCarpetIntervalTicks());
            int bombs = (int) (waypoint.dropLength / spacing) + 1;
            bombsNeeded += bombs;
            double sideX = -alongZ * kind.getBlastPower() * BLAST_REACH;
            double sideZ = alongX * kind.getBlastPower() * BLAST_REACH;
            double endX = landX + alongX * waypoint.dropLength;
            double endZ = landZ + alongZ * waypoint.dropLength;
            clippedLine(buffer, landX - sideX, landZ - sideZ, endX - sideX, endZ - sideZ, TARGET_COLOR);
            clippedLine(buffer, landX + sideX, landZ + sideZ, endX + sideX, endZ + sideZ, TARGET_COLOR);
            clippedLine(buffer, landX - sideX, landZ - sideZ, landX + sideX, landZ + sideZ, TARGET_COLOR);
            clippedLine(buffer, endX - sideX, endZ - sideZ, endX + sideX, endZ + sideZ, TARGET_COLOR);
            for (int bomb = 1; bomb < Math.min(bombs, 128); bomb++) {
                double tickX = landX + alongX * spacing * bomb;
                double tickZ = landZ + alongZ * spacing * bomb;
                clippedLine(buffer, tickX - sideX, tickZ - sideZ, tickX + sideX, tickZ + sideZ, DROP_FAINT);
            }
        }
    }

    /** Ground height at a spot the client has loaded, else {@code fallback}. */
    private double groundAt(double x, double z, double fallback) {
        BlockPos column = new BlockPos(x, 0, z);
        return mc.world.isBlockLoaded(column, false) ? mc.world.getHeight(column).getY() : fallback;
    }

    private void drawRoute(BufferBuilder buffer, PlannerOpenPacket.UnitView unit) {
        double fromX = unit.x;
        double fromZ = unit.z;
        // Airborne: the path starts at the waypoint the unit is heading for
        int start = dirty || unit.isGrounded() ? 0 : Math.min(unit.waypointIndex, waypoints.size());
        if (terminal == Route.TerminalAction.LOOP && waypoints.size() > 1) {
            // A loop is a closed circuit: the leg to where it's heading, then every leg including last -> first
            Route.Waypoint next = waypoints.get(Math.min(start, waypoints.size() - 1));
            clippedLine(buffer, fromX, fromZ, next.x, next.z, ROUTE_COLOR);
            for (int index = 0; index < waypoints.size() && waypoints.size() > 1; index++) {
                Route.Waypoint from = waypoints.get(index);
                Route.Waypoint to = waypoints.get((index + 1) % waypoints.size());
                clippedLine(buffer, from.x, from.z, to.x, to.z, index == waypoints.size() - 1 ? ROUTE_FAINT : ROUTE_COLOR);
            }
            return;
        }
        for (int index = start; index < waypoints.size(); index++) {
            Route.Waypoint waypoint = waypoints.get(index);
            clippedLine(buffer, fromX, fromZ, waypoint.x, waypoint.z, ROUTE_COLOR);
            fromX = waypoint.x;
            fromZ = waypoint.z;
        }
        // Loiter circles the last waypoint (so does a one-waypoint loop); shown while setting the radius too
        boolean circles = terminal == Route.TerminalAction.LOITER || (terminal == Route.TerminalAction.LOOP && waypoints.size() == 1);
        if (!waypoints.isEmpty() && (circles || editingRadius)) {
            Route.Waypoint last = waypoints.get(waypoints.size() - 1);
            double radius = editingRadius ? previewRadius(cursorX, cursorY) : loiterRadius;
            double previousX = last.x + radius;
            double previousZ = last.z;
            for (int step = 1; step <= 32; step++) {
                double angle = step * Math.PI * 2 / 32;
                double pointX = last.x + Math.cos(angle) * radius;
                double pointZ = last.z + Math.sin(angle) * radius;
                clippedLine(buffer, previousX, previousZ, pointX, pointZ, ROUTE_FAINT);
                previousX = pointX;
                previousZ = pointZ;
            }
        }
    }

    /**
     * The player's runways, outlined to scale with a tick across the threshold (taken ones dimmer), and for a route
     * that ends in Return a faint leg from the last waypoint (or the unit, when recalled) out to the runway's approach
     * and down the final.
     */
    private void drawRunways(BufferBuilder buffer, PlannerOpenPacket.UnitView unit) {
        Route.Runway landing = unit == null ? null : landingRunway(unit);
        for (PlannerOpenPacket.RunwayView view : this.view.runways) {
            Route.Runway strip = view.runway;
            boolean target = landing != null && strip.id.equals(landing.id) && terminal == Route.TerminalAction.RETURN;
            int color = target ? Ui.SELECT : view.free ? RUNWAY_COLOR : RUNWAY_TAKEN;
            // At least a few pixels wide, so a runway still shows zoomed out
            double half = Math.max(strip.width / 2, 2 / scale);
            double length = Math.max(strip.length, 6 / scale);
            double[][] corners = {{0, -half}, {length, -half}, {length, half}, {0, half}};
            for (int corner = 0; corner < 4; corner++) {
                double[] from = corners[corner];
                double[] to = corners[(corner + 1) % 4];
                clippedLine(buffer, strip.pointX(from[0], from[1]), strip.pointZ(from[0], from[1]), strip.pointX(to[0], to[1]),
                        strip.pointZ(to[0], to[1]), color);
            }
        }
        if (landing == null || terminal != Route.TerminalAction.RETURN && !unit.recalling) {
            return;
        }
        double fromX = unit.x;
        double fromZ = unit.z;
        if (!unit.recalling && !waypoints.isEmpty()) {
            fromX = waypoints.get(waypoints.size() - 1).x;
            fromZ = waypoints.get(waypoints.size() - 1).z;
        }
        double fixX = landing.pointX(-40, 0);
        double fixZ = landing.pointZ(-40, 0);
        clippedLine(buffer, fromX, fromZ, fixX, fixZ, ROUTE_FAINT);
        clippedLine(buffer, fixX, fixZ, landing.x, landing.z, ROUTE_FAINT);
    }

    /** The runway under the cursor, or null. */
    private PlannerOpenPacket.RunwayView runwayAt(int screenX, int screenY) {
        double worldX = toWorldX(screenX);
        double worldZ = toWorldZ(screenY);
        double slack = PICK_RADIUS_PX / scale;
        for (PlannerOpenPacket.RunwayView view : this.view.runways) {
            double along = view.runway.along(worldX, worldZ);
            if (along > -slack && along < view.runway.length + slack && Math.abs(view.runway.cross(worldX, worldZ)) < view.runway.width / 2 + slack) {
                return view;
            }
        }
        return null;
    }

    /** Right-click on a runway: land the selected plane there at the end of its route. */
    private PlannerContextMenu runwayMenu(PlannerOpenPacket.RunwayView picked, int mouseX, int mouseY) {
        PlannerContextMenu runwayMenu = new PlannerContextMenu(mouseX, mouseY);
        EntityLaunchRamp.Kind kind = EntityLaunchRamp.Kind.values()[Math.min(picked.kind, EntityLaunchRamp.Kind.values().length - 1)];
        runwayMenu.row(PlannerContextMenu.Segment.label(() -> I18n.format("item.combat_drones." + kind.getName() + ".name")));
        PlannerOpenPacket.UnitView unit = currentUnit();
        DroneType type = currentType();
        if (unit == null || type == null || !unit.allowedTerminals.contains(Route.TerminalAction.RETURN)) {
            return runwayMenu;
        }
        if (!kind.accepts(type)) {
            return runwayMenu.row(PlannerContextMenu.Segment.label(() -> I18n.format("gui.combat_drones.planner.menu.too_small")));
        }
        return runwayMenu.row(PlannerContextMenu.Segment.command(() -> I18n.format("gui.combat_drones.planner.menu.land_here"), () -> {
            runway = picked.runway;
            terminal = Route.TerminalAction.RETURN;
            dirty = true;
        }));
    }

    /** Interpolated live positions for units the client is tracking, smoother than the packet refresh. */
    private void followTrackedUnits(float partialTicks) {
        for (PlannerOpenPacket.UnitView unit : view.units) {
            Entity entity = unit.entityId >= 0 ? mc.world.getEntityByID(unit.entityId) : null;
            if (entity instanceof EntityRampUnit) {
                unit.x = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks;
                unit.z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;
                unit.heading = entity.rotationYaw;
            }
        }
    }

    /** Each unit seen from above, turned to its heading, so different unit types can be told apart. */
    private void drawUnitIcons() {
        for (PlannerOpenPacket.UnitView unit : view.units) {
            DroneType type = DroneTypes.get(unit.typeId);
            int screenX = toScreenX(unit.x);
            int screenY = toScreenY(unit.z);
            if (type != null && insideMap(screenX, screenY)) {
                GeoModels.renderTopDown(GeoModels.nameFor(type), screenX, screenY, unit.heading, UNIT_ICON_SIZE);
            }
        }
    }

    /** An arrowhead around an airborne unit's icon, pointing the way it's heading. */
    private void drawUnitMarker(BufferBuilder buffer, PlannerOpenPacket.UnitView unit) {
        double radians = Math.toRadians(unit.heading);
        double noseX = unit.x - Math.sin(radians) * 14 / scale;
        double noseZ = unit.z + Math.cos(radians) * 14 / scale;
        double leftX = unit.x - Math.sin(radians + 0.45) * 10 / scale;
        double leftZ = unit.z + Math.cos(radians + 0.45) * 10 / scale;
        double rightX = unit.x - Math.sin(radians - 0.45) * 10 / scale;
        double rightZ = unit.z + Math.cos(radians - 0.45) * 10 / scale;
        int color = view.units.indexOf(unit) == selectedUnit ? Ui.SELECT : UNIT_COLOR;
        clippedLine(buffer, leftX, leftZ, noseX, noseZ, color);
        clippedLine(buffer, noseX, noseZ, rightX, rightZ, color);
        clippedLine(buffer, rightX, rightZ, leftX, leftZ, color);
    }

    private void drawMarker(double worldX, double worldZ, int color, int size) {
        int screenX = toScreenX(worldX);
        int screenY = toScreenY(worldZ);
        if (insideMap(screenX, screenY)) {
            Gui.drawRect(screenX - size, screenY - size, screenX + size, screenY + size, color);
        }
    }

    private void clippedLine(BufferBuilder buffer, double fromX, double fromZ, double toX, double toZ, int color) {
        double[] clipped = LineClip.clip(toScreenX(fromX), toScreenY(fromZ), toScreenX(toX), toScreenY(toZ), mapLeft, mapTop, mapRight, mapBottom);
        if (clipped != null) {
            line(buffer, clipped[0], clipped[1], clipped[2], clipped[3], color);
        }
    }

    private static void line(BufferBuilder buffer, double x1, double y1, double x2, double y2, int color) {
        int alpha = color >>> 24;
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        buffer.pos(x1, y1, 0).color(red, green, blue, alpha).endVertex();
        buffer.pos(x2, y2, 0).color(red, green, blue, alpha).endVertex();
    }

    private void drawStats() {
        PlannerOpenPacket.UnitView unit = currentUnit();
        if (unit == null) {
            return;
        }
        DroneType type = DroneTypes.get(unit.typeId);
        double length = currentRoute().length(unit.x, unit.z);
        boolean tooLong = length > view.maxLength;
        String text;
        if (type != null && type.getMaxSpeed() > 0) {
            // Each leg flies at its target waypoint's speed; level cruise needs throttle ~ speed fraction squared
            double ticks = 0;
            double fuelTicks = 0;
            double fromX = unit.x;
            double fromZ = unit.z;
            for (Route.Waypoint waypoint : waypoints) {
                double legTicks = Math.hypot(waypoint.x - fromX, waypoint.z - fromZ) / (type.getMaxSpeed() * waypoint.speed.fraction);
                ticks += legTicks;
                fuelTicks += legTicks * waypoint.speed.fraction * waypoint.speed.fraction;
                fromX = waypoint.x;
                fromZ = waypoint.z;
            }
            double seconds = ticks / 20;
            double fuelNeeded = fuelTicks * type.getDrainRatePerTick() / type.getBatteryOrFuelCapacity();
            text = I18n.format("gui.combat_drones.planner.stats", Math.round(length), Math.round(seconds), percent(fuelNeeded), percent(unit.fuelFraction));
        } else {
            text = I18n.format("gui.combat_drones.planner.length", Math.round(length));
        }
        fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(text, mapRight - mapLeft), mapLeft, mapBottom + 5,
                tooLong || waypoints.size() > view.maxWaypoints ? WARNING : Ui.DIM);
        if (!ClientIntel.getMarks().isEmpty()) {
            String spotted = I18n.format("gui.combat_drones.planner.spotted", ClientIntel.getMarks().size());
            fontRenderer.drawStringWithShadow(spotted, mapRight - fontRenderer.getStringWidth(spotted) - 4, mapTop + 4, Ui.DANGER);
        }
    }

    private static long percent(double fraction) {
        return Math.round(Math.max(0, fraction) * 100);
    }

    // ---- Coordinates ----

    private int toScreenX(double worldX) {
        return (int) Math.round((mapLeft + mapRight) / 2.0 + (worldX - centerX) * scale);
    }

    private int toScreenY(double worldZ) {
        return (int) Math.round((mapTop + mapBottom) / 2.0 + (worldZ - centerZ) * scale);
    }

    private double toWorldX(int screenX) {
        return centerX + (screenX - (mapLeft + mapRight) / 2.0) / scale;
    }

    private double toWorldZ(int screenY) {
        return centerZ + (screenY - (mapTop + mapBottom) / 2.0) / scale;
    }

    private boolean insideMap(int screenX, int screenY) {
        return screenX >= mapLeft && screenX <= mapRight && screenY >= mapTop && screenY <= mapBottom;
    }

    private int waypointAt(int screenX, int screenY) {
        for (int index = waypoints.size() - 1; index >= 0; index--) {
            Route.Waypoint waypoint = waypoints.get(index);
            if (Math.hypot(toScreenX(waypoint.x) - screenX, toScreenY(waypoint.z) - screenY) <= PICK_RADIUS_PX) {
                return index;
            }
        }
        return -1;
    }

    // ---- Input ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (editingRadius) {
            if (button == 0 && !waypoints.isEmpty()) {
                loiterRadius = previewRadius(mouseX, mouseY);
                dirty = true;
            }
            editingRadius = false;
            return;
        }
        if (menu != null) {
            boolean inside = menu.contains(fontRenderer, width, height, mouseX, mouseY);
            if (!inside || menu.click(fontRenderer, width, height, mouseX, mouseY)) {
                menu = null;
            }
            if (inside) {
                UiSounds.play(UiSounds.CLICK);
            }
            refreshButtons();
            if (inside) {
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
        if (mouseX >= MARGIN && mouseX <= MARGIN + PANEL_WIDTH) {
            int unitRow = (mouseY - mapTop - ROW) / ROW;
            if (mouseY >= mapTop + ROW && unitRow < view.units.size()) {
                UiSounds.play(UiSounds.SELECT);
                clickUnitList(unitRow);
            } else {
                clickWaypointList(mouseY);
            }
            return;
        }
        if (!insideMap(mouseX, mouseY)) {
            return;
        }
        // Panning works on an empty planner too; everything else edits the selected unit's route
        if (currentUnit() == null) {
            panning = button == 0;
            return;
        }
        int hit = waypointAt(mouseX, mouseY);
        if (button == 0) {
            if (hit >= 0) {
                dragWaypoint = hit;
                selectedWaypoint = hit;
            } else {
                panning = true;
            }
        } else if (button == 1) {
            PlannerOpenPacket.RunwayView runwayHit = hit >= 0 ? null : runwayAt(mouseX, mouseY);
            if (hit >= 0) {
                selectedWaypoint = hit;
                menu = waypointMenu(hit, mouseX, mouseY);
            } else if (runwayHit != null) {
                menu = runwayMenu(runwayHit, mouseX, mouseY);
            } else {
                menu = addMenu(toWorldX(mouseX), toWorldZ(mouseY), mouseX, mouseY);
            }
        }
        refreshButtons();
    }

    private PlannerContextMenu addMenu(double worldX, double worldZ, int mouseX, int mouseY) {
        PlannerContextMenu addMenu = new PlannerContextMenu(mouseX, mouseY);
        PlannerOpenPacket.UnitView unit = currentUnit();
        if (unit != null && unit.allowedTerminals.contains(Route.TerminalAction.DESTROY)) {
            addMenu.row(PlannerContextMenu.Segment.command(() -> I18n.format("gui.combat_drones.planner.menu.target"), () -> {
                target = new Route.Target(Math.round(worldX), Double.NaN, Math.round(worldZ));
                terminal = Route.TerminalAction.DESTROY;
                dirty = true;
            }));
        }
        if (waypoints.size() >= view.maxWaypoints) {
            return addMenu.row(PlannerContextMenu.Segment.label(() -> I18n.format("gui.combat_drones.planner.menu.full")));
        }
        if (unit != null && unit.allowedTerminals.contains(Route.TerminalAction.LOITER)) {
            // Shortcut: a final waypoint here, circled at the loiter radius
            addMenu.row(PlannerContextMenu.Segment.command(() -> I18n.format("gui.combat_drones.planner.menu.loiter_here"), () -> {
                addWaypointAt(worldX, worldZ);
                terminal = Route.TerminalAction.LOITER;
            }));
        }
        if (isBomber()) {
            addMenu.row(PlannerContextMenu.Segment.command(() -> I18n.format("gui.combat_drones.planner.menu.add_drop"), () -> {
                addWaypointAt(worldX, worldZ);
                setDrop(waypoints.size() - 1, 0);
            }));
        }
        return addMenu.row(PlannerContextMenu.Segment.command(() -> I18n.format("gui.combat_drones.planner.menu.add"), () -> addWaypointAt(worldX, worldZ)));
    }

    private boolean isBomber() {
        DroneType type = currentType();
        return type != null && type.stationOf(DroneType.WeaponType.BOMB_BAY) >= 0;
    }

    private double dropOf(int index) {
        return index < waypoints.size() ? waypoints.get(index).dropLength : Route.NO_DROP;
    }

    private void setDrop(int index, double dropLength) {
        if (index < waypoints.size()) {
            waypoints.set(index, waypoints.get(index).withDrop(dropLength));
            dirty = true;
        }
    }

    /** Appends a waypoint, copying the previous one's height and speed (the first one flies at Medium). */
    private void addWaypointAt(double worldX, double worldZ) {
        Route.Waypoint previous = waypoints.isEmpty() ? null : waypoints.get(waypoints.size() - 1);
        double altitude = previous != null ? previous.altitude : defaultAltitude();
        Route.Speed speed = previous != null ? previous.speed : Route.Speed.MEDIUM;
        UiSounds.play(UiSounds.SELECT, 1.2F);
        waypoints.add(new Route.Waypoint(Math.round(worldX), Math.round(worldZ), altitude, speed)); // never copies a drop
        selectedWaypoint = waypoints.size() - 1;
        dirty = true;
    }

    /** Options act on {@link #selectedWaypoint}, which is the clicked waypoint for as long as the menu is open. */
    private PlannerContextMenu waypointMenu(int index, int mouseX, int mouseY) {
        PlannerContextMenu waypointMenu = new PlannerContextMenu(mouseX, mouseY);
        waypointMenu.row(PlannerContextMenu.Segment.label(() -> I18n.format("gui.combat_drones.planner.waypoint", index + 1)));
        waypointMenu.row(
                PlannerContextMenu.Segment.label(() -> I18n.format("gui.combat_drones.planner.menu.height")),
                PlannerContextMenu.Segment.option(() -> "-5", () -> changeAltitude(-5), () -> false),
                PlannerContextMenu.Segment.option(() -> "-1", () -> changeAltitude(-1), () -> false),
                PlannerContextMenu.Segment.label(() -> index < waypoints.size() ? Math.round(waypoints.get(index).altitude) + " m" : ""),
                PlannerContextMenu.Segment.option(() -> "+1", () -> changeAltitude(1), () -> false),
                PlannerContextMenu.Segment.option(() -> "+5", () -> changeAltitude(5), () -> false));
        List<PlannerContextMenu.Segment> speedRow = new ArrayList<>();
        speedRow.add(PlannerContextMenu.Segment.label(() -> I18n.format("gui.combat_drones.planner.menu.speed")));
        for (Route.Speed speed : Route.Speed.values()) {
            speedRow.add(PlannerContextMenu.Segment.option(
                    () -> I18n.format("gui.combat_drones.speed." + speed.name().toLowerCase(Locale.ROOT)),
                    () -> {
                        waypoints.set(index, waypoints.get(index).withSpeed(speed));
                        dirty = true;
                    },
                    () -> index < waypoints.size() && waypoints.get(index).speed == speed));
        }
        waypointMenu.row(speedRow.toArray(new PlannerContextMenu.Segment[0]));
        if (isBomber()) {
            waypointMenu.row(
                    PlannerContextMenu.Segment.label(() -> I18n.format("gui.combat_drones.planner.menu.drop")),
                    PlannerContextMenu.Segment.option(() -> I18n.format("gui.combat_drones.planner.drop.off"),
                            () -> setDrop(index, Route.NO_DROP), () -> dropOf(index) < 0),
                    PlannerContextMenu.Segment.option(() -> I18n.format("gui.combat_drones.planner.drop.single"),
                            () -> setDrop(index, 0), () -> dropOf(index) == 0),
                    PlannerContextMenu.Segment.option(() -> I18n.format("gui.combat_drones.planner.drop.carpet"),
                            () -> setDrop(index, Math.max(dropOf(index), Math.min(DEFAULT_CARPET, view.maxCarpetLength))), () -> dropOf(index) > 0));
            waypointMenu.row(
                    PlannerContextMenu.Segment.label(() -> I18n.format("gui.combat_drones.planner.menu.carpet")),
                    PlannerContextMenu.Segment.option(() -> "-" + Math.round(CARPET_STEP), () -> changeCarpet(index, -CARPET_STEP), () -> false),
                    PlannerContextMenu.Segment.label(() -> dropOf(index) > 0 ? Math.round(dropOf(index)) + " m" : "-"),
                    PlannerContextMenu.Segment.option(() -> "+" + Math.round(CARPET_STEP), () -> changeCarpet(index, CARPET_STEP), () -> false));
        }
        if (index == waypoints.size() - 1) {
            waypointMenu.row(
                    PlannerContextMenu.Segment.label(() -> I18n.format("gui.combat_drones.planner.menu.final")),
                    PlannerContextMenu.Segment.option(() -> I18n.format("gui.combat_drones.terminal." + terminal.name().toLowerCase(Locale.ROOT)),
                            this::cycleTerminal, () -> true));
            waypointMenu.row(
                    PlannerContextMenu.Segment.label(() -> I18n.format("gui.combat_drones.planner.menu.radius", Math.round(loiterRadius))),
                    PlannerContextMenu.Segment.command(() -> I18n.format("gui.combat_drones.planner.menu.set_radius"), () -> editingRadius = true));
        }
        waypointMenu.row(PlannerContextMenu.Segment.command(() -> I18n.format("gui.combat_drones.planner.menu.remove"), () -> {
            waypoints.remove(index);
            selectedWaypoint = -1;
            dirty = true;
        }));
        return waypointMenu;
    }

    /** Lengthens or shortens a carpet run (only once the waypoint is one). */
    private void changeCarpet(int index, double delta) {
        if (dropOf(index) > 0) {
            setDrop(index, Math.max(CARPET_STEP, Math.min(view.maxCarpetLength, dropOf(index) + delta)));
        }
    }

    /** Loiter radius implied by the cursor's distance from the last waypoint. */
    private double previewRadius(int mouseX, int mouseY) {
        Route.Waypoint last = waypoints.get(waypoints.size() - 1);
        double distance = Math.hypot(toWorldX(mouseX) - last.x, toWorldZ(mouseY) - last.z);
        DroneType type = currentType();
        double min = Math.max(view.minLoiterRadius, type == null ? 0 : type.getMinLoiterRadius());
        return Math.max(min, Math.min(view.maxLoiterRadius, Math.round(distance)));
    }

    private void clampToLoiterMinimums() {
        DroneType type = currentType();
        if (type == null) {
            return;
        }
        Route clamped = currentRoute().withLoiterMinimums(type.getMinLoiterAltitude(), Math.max(view.minLoiterRadius, type.getMinLoiterRadius()));
        waypoints.clear();
        waypoints.addAll(clamped.getWaypoints());
        loiterRadius = clamped.getLoiterRadius();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Esc closes the menu or cancels radius editing before it closes the screen
        if (keyCode == Keyboard.KEY_ESCAPE && (menu != null || editingRadius)) {
            menu = null;
            editingRadius = false;
            return;
        }
        if (keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_0) {
            int row = keyCode - Keyboard.KEY_1;
            if (row < view.units.size()) {
                UiSounds.play(UiSounds.SELECT);
                clickUnitList(row);
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private DroneType currentType() {
        PlannerOpenPacket.UnitView unit = currentUnit();
        return unit == null ? null : DroneTypes.get(unit.typeId);
    }

    private double defaultAltitude() {
        DroneType type = currentType();
        return type != null && type.getDefaultCruiseAltitude() > 0 ? type.getDefaultCruiseAltitude() : 30;
    }

    private void clickUnitList(int row) {
        if (row >= 0 && row < view.units.size() && row != selectedUnit) {
            selectUnit(row);
            refreshButtons();
        }
    }

    /** Ammo lines drawPanel shows for the selected unit (grounded units only). */
    private int groundedAmmoRows() {
        PlannerOpenPacket.UnitView unit = currentUnit();
        DroneType type = unit == null || !unit.isGrounded() ? null : DroneTypes.get(unit.typeId);
        int rows = 0;
        for (int station = 0; type != null && station < Loadout.STATIONS; station++) {
            if (type.getWeapon(station) != DroneType.WeaponType.NONE) {
                rows++;
            }
        }
        return rows;
    }

    private void clickWaypointList(int mouseY) {
        int listTop = mapTop + ROW * (Math.max(1, view.units.size()) + 2) + 4 + ROW * groundedAmmoRows();
        int row = (mouseY - listTop) / ROW;
        if (mouseY >= listTop && row < waypoints.size()) {
            UiSounds.play(UiSounds.SELECT);
            selectedWaypoint = row;
            refreshButtons();
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (dragWaypoint >= 0 && dragWaypoint < waypoints.size()) {
            Route.Waypoint old = waypoints.get(dragWaypoint);
            waypoints.set(dragWaypoint, old.withPosition(Math.round(toWorldX(mouseX)), Math.round(toWorldZ(mouseY))));
            dirty = true;
        } else if (panning) {
            centerX -= (mouseX - lastMouseX) / scale;
            centerZ -= (mouseY - lastMouseY) / scale;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        dragWaypoint = -1;
        panning = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            double factor = wheel > 0 ? 1.25 : 0.8;
            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BUTTON_TERMINAL:
                cycleTerminal();
                break;
            case BUTTON_CLEAR:
                waypoints.clear();
                selectedWaypoint = -1;
                dirty = true;
                break;
            case BUTTON_SEND:
                send(false, false);
                break;
            case BUTTON_LAUNCH:
                // The planner stays open and moves on to the launched unit (the server selects it)
                send(true, false);
                break;
            case BUTTON_LAUNCH_FLY:
                send(true, true);
                mc.displayGuiScreen(null);
                return;
            case BUTTON_CLOSE:
                mc.displayGuiScreen(null);
                return;
            case BUTTON_MARK:
                if (currentUnit() != null) {
                    // The draft goes along, so marking in the world doesn't lose unsent edits
                    ModNetwork.CHANNEL.sendToServer(new PlannerMarkPacket(currentUnit().rampId, currentUnit().entityId, currentRoute()));
                }
                mc.displayGuiScreen(null);
                return;
            case BUTTON_RETURN:
                if (currentUnit() != null) {
                    // The route on screen stays as it is: Apply sends it again and the plane takes it back up
                    ModNetwork.CHANNEL.sendToServer(PlannerSubmitPacket.forRecall(currentUnit().entityId));
                }
                break;
            case BUTTON_VIEW:
            case BUTTON_CONTROL:
                if (currentUnit() != null) {
                    ModNetwork.CHANNEL.sendToServer(new RemoteRequestPacket(currentUnit().entityId, button.id == BUTTON_CONTROL));
                }
                mc.displayGuiScreen(null);
                return;
            case BUTTON_UP:
                swap(selectedWaypoint, selectedWaypoint - 1);
                break;
            case BUTTON_DOWN:
                swap(selectedWaypoint, selectedWaypoint + 1);
                break;
            case BUTTON_ALT_DOWN:
                changeAltitude(isShiftKeyDown() ? -1 : -5);
                break;
            case BUTTON_ALT_UP:
                changeAltitude(isShiftKeyDown() ? 1 : 5);
                break;
            case BUTTON_DELETE:
                if (selectedWaypoint >= 0 && selectedWaypoint < waypoints.size()) {
                    waypoints.remove(selectedWaypoint);
                    selectedWaypoint = -1;
                    dirty = true;
                }
                break;
            default:
                break;
        }
        refreshButtons();
    }

    private void cycleTerminal() {
        PlannerOpenPacket.UnitView unit = currentUnit();
        if (unit == null) {
            return;
        }
        Route.TerminalAction[] actions = Route.TerminalAction.values();
        for (int step = 1; step <= actions.length; step++) {
            Route.TerminalAction next = actions[(terminal.ordinal() + step) % actions.length];
            if (unit.allowedTerminals.contains(next)) {
                terminal = next;
                dirty = true;
                return;
            }
        }
    }

    private void swap(int from, int to) {
        if (from < 0 || to < 0 || from >= waypoints.size() || to >= waypoints.size()) {
            return;
        }
        Route.Waypoint moved = waypoints.get(from);
        waypoints.set(from, waypoints.get(to));
        waypoints.set(to, moved);
        selectedWaypoint = to;
        dirty = true;
    }

    private void changeAltitude(double delta) {
        if (selectedWaypoint < 0 || selectedWaypoint >= waypoints.size()) {
            return;
        }
        Route.Waypoint old = waypoints.get(selectedWaypoint);
        double altitude = Math.max(view.minAltitude, Math.min(view.maxAltitude, old.altitude + delta));
        waypoints.set(selectedWaypoint, old.withAltitude(altitude));
        dirty = true;
    }

    private Route currentRoute() {
        return new Route(waypoints, terminal, loiterRadius, target, runway);
    }

    private void send(boolean launch, boolean fly) {
        PlannerOpenPacket.UnitView unit = currentUnit();
        if (unit == null) {
            return;
        }
        ModNetwork.CHANNEL.sendToServer(unit.isGrounded()
                ? PlannerSubmitPacket.forRamp(unit.rampId, currentRoute(), launch, fly)
                : PlannerSubmitPacket.forUnit(unit.entityId, currentRoute()));
        dirty = false;
    }
}
