package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.CombatDrones;
import io.github.duckysmacky.combatdrones.entity.EntityRampUnit;
import io.github.duckysmacky.combatdrones.network.IntelPacket;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.network.PlannerMarkPacket;
import io.github.duckysmacky.combatdrones.network.PlannerOpenPacket;
import io.github.duckysmacky.combatdrones.network.PlannerRequestPacket;
import io.github.duckysmacky.combatdrones.network.PlannerSubmitPacket;
import io.github.duckysmacky.combatdrones.network.RemoteRequestPacket;
import io.github.duckysmacky.combatdrones.physics.Route;
import io.github.duckysmacky.combatdrones.physics.RouteValidator;
import io.github.duckysmacky.combatdrones.type.DroneType;
import io.github.duckysmacky.combatdrones.type.DroneTypes;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mission Planner: an abstract grid map (north up, one grid square per chunk) for planning a ramp unit's route and
 * launching it, or (airborne mode) watching owned units fly and re-routing them. The client only proposes; the server
 * validates every route.
 *
 * <p>Map controls (like the reference mod): left-drag pans, or moves a waypoint when grabbed on one; right-click opens
 * a context menu (add a waypoint, or set a waypoint's height, speed, final action, loiter radius, or remove it); the
 * wheel zooms. The side list selects waypoints for reordering.
 */
public class GuiMissionPlanner extends GuiScreen {
    private static final int PANEL_WIDTH = 140;
    private static final int MARGIN = 8;
    private static final int ROW = 11;
    private static final int GRID_COLOR = 0x30FFFFFF;
    private static final int ROUTE_COLOR = 0xFFFFCC33;
    private static final int UNIT_COLOR = 0xFF55FF55;
    private static final int WARNING = 0xFF5555;
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
    private static final int TARGET_COLOR = 0xFFFF4040;
    private static final int LOCKED_COLOR = 0xFFFF2020;

    private PlannerOpenPacket view;
    /** Index into view.units of the unit being edited (always 0 in ramp mode). */
    private int selectedUnit;
    private final List<Route.Waypoint> waypoints = new ArrayList<>();
    private Route.TerminalAction terminal = Route.TerminalAction.LOITER;
    private double loiterRadius = Route.DEFAULT_LOITER_RADIUS;
    /** Destroy target; its Y is NaN when picked on the map (the server finds the ground). */
    private Route.Target target;
    /** Set once the player edits, so live refreshes don't overwrite their unsent changes. */
    private boolean dirty;
    private int selectedWaypoint = -1;

    // Map view
    private double centerX;
    private double centerZ;
    private double scale = 0.5;
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

    private GuiButton terminalButton;
    private GuiButton launchButton;
    private GuiButton viewButton;
    private GuiButton controlButton;
    private GuiButton markButton;

    public GuiMissionPlanner(PlannerOpenPacket view) {
        this.view = view;
        selectUnit(0);
    }

    /** Whether an incoming planner packet belongs to this screen (same ramp, or both airborne). */
    public boolean accepts(PlannerOpenPacket packet) {
        return view.isRampMode() ? view.rampId == packet.rampId : !packet.isRampMode();
    }

    /** New data from the server: keep the player's unsent edits, refresh everything else. */
    public void update(PlannerOpenPacket packet) {
        int previousId = currentUnit() == null ? -1 : currentUnit().entityId;
        view = packet;
        int index = 0;
        for (int candidate = 0; candidate < view.units.size(); candidate++) {
            if (view.units.get(candidate).entityId == previousId) {
                index = candidate;
            }
        }
        boolean sameUnit = index == selectedUnit && currentUnit() != null && currentUnit().entityId == previousId;
        if (!sameUnit) {
            selectUnit(index);
        } else if (!dirty) {
            // Periodic refresh of the unit being viewed: keep the map position and selection
            loadRoute();
        }
    }

    private void selectUnit(int index) {
        selectedUnit = index;
        selectedWaypoint = -1;
        loadRoute();
        PlannerOpenPacket.UnitView unit = currentUnit();
        if (unit != null) {
            centerX = unit.x;
            centerZ = unit.z;
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
        if (selectedWaypoint >= waypoints.size()) {
            selectedWaypoint = -1;
        }
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
        mapBottom = height - 52;
        int y = height - 26;
        int x = mapLeft;
        terminalButton = addButton(new GuiButton(BUTTON_TERMINAL, x, y, 120, 20, ""));
        x += 124;
        addButton(new GuiButton(BUTTON_CLEAR, x, y, 50, 20, I18n.format("gui.combat_drones.planner.clear")));
        x += 54;
        addButton(new GuiButton(BUTTON_SEND, x, y, 60, 20, I18n.format(view.isRampMode() ? "gui.combat_drones.planner.send" : "gui.combat_drones.planner.apply")));
        x += 64;
        if (view.isRampMode()) {
            launchButton = addButton(new GuiButton(BUTTON_LAUNCH, x, y, 60, 20, I18n.format("gui.combat_drones.planner.launch")));
            x += 64;
        } else {
            viewButton = addButton(new GuiButton(BUTTON_VIEW, x, y, 50, 20, I18n.format("gui.combat_drones.planner.view")));
            x += 54;
            controlButton = addButton(new GuiButton(BUTTON_CONTROL, x, y, 80, 20, I18n.format("gui.combat_drones.planner.take_control")));
            x += 84;
        }
        addButton(new GuiButton(BUTTON_CLOSE, Math.max(x, width - MARGIN - 50), y, 50, 20, I18n.format("gui.done")));

        int panelY = height - 50;
        addButton(new GuiButton(BUTTON_UP, MARGIN, panelY, 20, 20, "▲"));
        addButton(new GuiButton(BUTTON_DOWN, MARGIN + 22, panelY, 20, 20, "▼"));
        addButton(new GuiButton(BUTTON_ALT_DOWN, MARGIN + 46, panelY, 26, 20, "-5"));
        addButton(new GuiButton(BUTTON_ALT_UP, MARGIN + 74, panelY, 26, 20, "+5"));
        addButton(new GuiButton(BUTTON_DELETE, MARGIN + 104, panelY, 36, 20, I18n.format("gui.combat_drones.planner.delete")));
        markButton = addButton(new GuiButton(BUTTON_MARK, MARGIN, panelY - 22, PANEL_WIDTH, 20, I18n.format("gui.combat_drones.planner.mark")));
        refreshButtons();
    }

    private void refreshButtons() {
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
        if (viewButton != null) {
            DroneType type = unit == null ? null : DroneTypes.get(unit.typeId);
            viewButton.enabled = unit != null && unit.entityId >= 0;
            controlButton.enabled = viewButton.enabled && type != null && type.getControlScheme() != null && type.getControlScheme().isFlyable();
        }
        if (launchButton != null) {
            launchButton.enabled = unit != null && !waypoints.isEmpty() && unit.fuelFraction >= view.minLaunchFuel;
        }
    }

    @Override
    public void updateScreen() {
        // Airborne units move: keep their positions, fuel and progress fresh while the map is open
        if (!view.isRampMode() && ++ticks % REFRESH_TICKS == 0) {
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
        drawCenteredString(fontRenderer, title(), width / 2, 10, 0xFFFFFF);
        drawPanel(mouseX, mouseY);
        drawMap(mouseX, mouseY);
        drawStats();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (editingRadius && !waypoints.isEmpty()) {
            drawCenteredString(fontRenderer, I18n.format("gui.combat_drones.planner.radius_hint", Math.round(previewRadius(mouseX, mouseY))),
                    (mapLeft + mapRight) / 2, mapTop + 4, 0xFFFF55);
        }
        if (menu != null) {
            menu.draw(fontRenderer, width, height, mouseX, mouseY);
        }
    }

    private String title() {
        PlannerOpenPacket.UnitView unit = currentUnit();
        if (view.isRampMode() && unit != null) {
            return I18n.format("gui.combat_drones.planner.title_ramp", unitName(unit));
        }
        return I18n.format("gui.combat_drones.planner.title_airborne");
    }

    private static String unitName(PlannerOpenPacket.UnitView unit) {
        return I18n.format("item.combat_drones." + unit.typeId + ".name");
    }

    private void drawPanel(int mouseX, int mouseY) {
        Gui.drawRect(MARGIN - 2, mapTop - 2, MARGIN + PANEL_WIDTH + 2, height - 54, 0x80000000);
        int y = mapTop;
        if (!view.isRampMode()) {
            fontRenderer.drawString(I18n.format("gui.combat_drones.planner.units"), MARGIN, y, 0xAAAAAA);
            y += ROW;
            if (view.units.isEmpty()) {
                fontRenderer.drawString(I18n.format("gui.combat_drones.planner.no_units"), MARGIN, y, 0x777777);
                y += ROW;
            }
            for (int index = 0; index < view.units.size(); index++) {
                PlannerOpenPacket.UnitView unit = view.units.get(index);
                String line = fontRenderer.trimStringToWidth(unitName(unit), PANEL_WIDTH - 34) + " " + percent(unit.fuelFraction) + "%";
                int color = index == selectedUnit ? 0xFFFF55 : 0xFFFFFF;
                fontRenderer.drawString(line, MARGIN, y, color);
                y += ROW;
            }
            y += 4;
        } else if (currentUnit() != null) {
            PlannerOpenPacket.UnitView unit = currentUnit();
            fontRenderer.drawString(I18n.format("gui.combat_drones.planner.fuel", percent(unit.fuelFraction)), MARGIN, y,
                    unit.fuelFraction < view.minLaunchFuel ? WARNING : 0xFFFFFF);
            DroneType unitType = DroneTypes.get(unit.typeId);
            if (unitType != null && unitType.getWeaponType() != DroneType.WeaponType.NONE) {
                y += ROW;
                fontRenderer.drawString(I18n.format("gui.combat_drones.planner.ammo", unit.ammo, unitType.getPayloadCapacity()), MARGIN, y,
                        unit.ammo == 0 ? WARNING : 0xFFFFFF);
            }
            y += ROW + 4;
        }
        fontRenderer.drawString(I18n.format("gui.combat_drones.planner.waypoints", waypoints.size(), view.maxWaypoints), MARGIN, y, 0xAAAAAA);
        y += ROW;
        for (int index = 0; index < waypoints.size() && y < height - 64; index++) {
            Route.Waypoint waypoint = waypoints.get(index);
            String line = String.format(Locale.ROOT, "#%d  %d, %d  ↑%d %s", index + 1, Math.round(waypoint.x), Math.round(waypoint.z),
                    Math.round(waypoint.altitude), waypoint.speed.name().charAt(0));
            boolean badAltitude = waypoint.altitude < RouteValidator.MIN_ALTITUDE || waypoint.altitude > view.maxAltitude;
            int color = index == selectedWaypoint ? 0xFFFF55 : badAltitude ? WARNING : 0xFFFFFF;
            fontRenderer.drawString(line, MARGIN, y, color);
            y += ROW;
        }
    }

    private void drawMap(int mouseX, int mouseY) {
        Gui.drawRect(mapLeft, mapTop, mapRight, mapBottom, 0xC0101810);
        if (terrain == null) {
            terrain = new PlannerTerrainMap();
        }
        terrain.update(mc.world, centerX, centerZ);
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
        if (unit != null) {
            drawRoute(buffer, unit);
            drawTarget(buffer);
        }
        for (PlannerOpenPacket.UnitView other : view.units) {
            if (other.entityId >= 0) {
                drawUnitMarker(buffer, other);
            }
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
        drawUnitIcons();

        // Markers and labels
        if (view.isRampMode() && unit != null) {
            drawMarker(unit.x, unit.z, 0xFF55AAFF, 3);
        }
        for (int index = 0; index < waypoints.size(); index++) {
            Route.Waypoint waypoint = waypoints.get(index);
            int screenX = toScreenX(waypoint.x);
            int screenY = toScreenY(waypoint.z);
            if (insideMap(screenX, screenY)) {
                drawMarker(waypoint.x, waypoint.z, index == selectedWaypoint ? 0xFFFFFF55 : ROUTE_COLOR, 3);
                fontRenderer.drawStringWithShadow(String.valueOf(index + 1), screenX + 5, screenY - 4, 0xFFFFFF);
            }
        }
        if (mc.player != null) {
            drawMarker(mc.player.posX, mc.player.posZ, 0xFFFFFFFF, 2);
        }
        drawIntel();
        fontRenderer.drawStringWithShadow("N", (mapLeft + mapRight) / 2.0F - 2, mapTop + 3, 0xFFFFFF);
        if (insideMap(mouseX, mouseY)) {
            fontRenderer.drawStringWithShadow(String.format(Locale.ROOT, "X %d  Z %d", Math.round(toWorldX(mouseX)), Math.round(toWorldZ(mouseY))),
                    mapLeft + 4, mapBottom - 11, 0xCCCCCC);
        }
    }

    /**
     * Players the recon has spotted: fading red dots at their last seen spot, or a bright red diamond with a name
     * for a locked target (which follows them live).
     */
    private void drawIntel() {
        for (IntelPacket.Entry mark : ClientIntel.getMarks()) {
            int screenX = toScreenX(mark.x);
            int screenY = toScreenY(mark.z);
            if (!insideMap(screenX, screenY)) {
                continue;
            }
            if (mark.locked) {
                for (int row = -4; row <= 4; row++) {
                    int half = 4 - Math.abs(row);
                    Gui.drawRect(screenX - half, screenY + row, screenX + half + 1, screenY + row + 1, LOCKED_COLOR);
                }
                fontRenderer.drawStringWithShadow(mark.name, screenX + 6, screenY - 4, 0xFF6060);
            } else {
                int alpha = 0x40 + (int) (Math.max(0, Math.min(1, mark.freshness)) * 0xBF);
                Gui.drawRect(screenX - 2, screenY - 2, screenX + 3, screenY + 3, alpha << 24 | 0xE02020);
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
            clippedLine(buffer, last.x, last.z, target.x, target.z, 0x80FF4040);
        }
    }

    private void drawRoute(BufferBuilder buffer, PlannerOpenPacket.UnitView unit) {
        double fromX = unit.x;
        double fromZ = unit.z;
        // Airborne: the path starts at the waypoint the unit is heading for
        int start = dirty || view.isRampMode() ? 0 : Math.min(unit.waypointIndex, waypoints.size());
        if (terminal == Route.TerminalAction.LOOP && waypoints.size() > 1) {
            // A loop is a closed circuit: the leg to where it's heading, then every leg including last -> first
            Route.Waypoint next = waypoints.get(Math.min(start, waypoints.size() - 1));
            clippedLine(buffer, fromX, fromZ, next.x, next.z, ROUTE_COLOR);
            for (int index = 0; index < waypoints.size() && waypoints.size() > 1; index++) {
                Route.Waypoint from = waypoints.get(index);
                Route.Waypoint to = waypoints.get((index + 1) % waypoints.size());
                clippedLine(buffer, from.x, from.z, to.x, to.z, index == waypoints.size() - 1 ? 0x80FFCC33 : ROUTE_COLOR);
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
                clippedLine(buffer, previousX, previousZ, pointX, pointZ, 0x80FFCC33);
                previousX = pointX;
                previousZ = pointZ;
            }
        }
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

    /** Each airborne unit's item icon, so different unit types can be told apart. */
    private void drawUnitIcons() {
        RenderHelper.enableGUIStandardItemLighting();
        for (PlannerOpenPacket.UnitView unit : view.units) {
            Item item = unit.entityId < 0 ? null : ForgeRegistries.ITEMS.getValue(new ResourceLocation(CombatDrones.MOD_ID, unit.typeId));
            int screenX = toScreenX(unit.x);
            int screenY = toScreenY(unit.z);
            if (item != null && insideMap(screenX, screenY)) {
                itemRender.renderItemAndEffectIntoGUI(new ItemStack(item), screenX - 8, screenY - 8);
            }
        }
        RenderHelper.disableStandardItemLighting();
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
        int color = view.units.indexOf(unit) == selectedUnit ? 0xFFFFFF55 : UNIT_COLOR;
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
        int x1 = toScreenX(fromX);
        int y1 = toScreenY(fromZ);
        int x2 = toScreenX(toX);
        int y2 = toScreenY(toZ);
        // ponytail: lines with an end off the map are skipped rather than clipped; fine for a route editor
        if (insideMap(x1, y1) && insideMap(x2, y2)) {
            line(buffer, x1, y1, x2, y2, color);
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
        fontRenderer.drawString(text, mapLeft, mapBottom + 4, tooLong || waypoints.size() > view.maxWaypoints ? WARNING : 0xCCCCCC);
        if (!ClientIntel.getMarks().isEmpty()) {
            String spotted = I18n.format("gui.combat_drones.planner.spotted", ClientIntel.getMarks().size());
            fontRenderer.drawString(spotted, mapRight - fontRenderer.getStringWidth(spotted), mapBottom + 4, 0xFF6060);
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
            refreshButtons();
            if (inside) {
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
        if (mouseX >= MARGIN && mouseX <= MARGIN + PANEL_WIDTH) {
            int unitRow = (mouseY - mapTop - ROW) / ROW;
            if (!view.isRampMode() && mouseY >= mapTop + ROW && unitRow < view.units.size()) {
                clickUnitList(unitRow);
            } else {
                clickWaypointList(mouseY);
            }
            return;
        }
        if (!insideMap(mouseX, mouseY) || currentUnit() == null) {
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
            if (hit >= 0) {
                selectedWaypoint = hit;
                menu = waypointMenu(hit, mouseX, mouseY);
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
        return addMenu.row(PlannerContextMenu.Segment.command(() -> I18n.format("gui.combat_drones.planner.menu.add"), () -> addWaypointAt(worldX, worldZ)));
    }

    /** Appends a waypoint, copying the previous one's height and speed. */
    private void addWaypointAt(double worldX, double worldZ) {
        Route.Waypoint previous = waypoints.isEmpty() ? null : waypoints.get(waypoints.size() - 1);
        double altitude = previous != null ? previous.altitude : defaultAltitude();
        Route.Speed speed = previous != null ? previous.speed : Route.Speed.FAST;
        waypoints.add(new Route.Waypoint(Math.round(worldX), Math.round(worldZ), altitude, speed));
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

    /** Loiter radius implied by the cursor's distance from the last waypoint. */
    private double previewRadius(int mouseX, int mouseY) {
        Route.Waypoint last = waypoints.get(waypoints.size() - 1);
        double distance = Math.hypot(toWorldX(mouseX) - last.x, toWorldZ(mouseY) - last.z);
        return Math.max(Route.MIN_LOITER_RADIUS, Math.min(Route.MAX_LOITER_RADIUS, Math.round(distance)));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Esc closes the menu or cancels radius editing before it closes the screen
        if (keyCode == Keyboard.KEY_ESCAPE && (menu != null || editingRadius)) {
            menu = null;
            editingRadius = false;
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private double defaultAltitude() {
        PlannerOpenPacket.UnitView unit = currentUnit();
        DroneType type = unit == null ? null : DroneTypes.get(unit.typeId);
        return type != null && type.getDefaultCruiseAltitude() > 0 ? type.getDefaultCruiseAltitude() : 30;
    }

    private void clickUnitList(int row) {
        if (row >= 0 && row < view.units.size() && row != selectedUnit) {
            selectUnit(row);
            refreshButtons();
        }
    }

    private void clickWaypointList(int mouseY) {
        int listTop = mapTop + ROW + (view.isRampMode() ? ROW + 4 : ROW * (Math.max(1, view.units.size()) + 1) + 4);
        int row = (mouseY - listTop) / ROW;
        if (mouseY >= listTop && row < waypoints.size()) {
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
                send(false);
                break;
            case BUTTON_LAUNCH:
                send(true);
                mc.displayGuiScreen(null);
                return;
            case BUTTON_CLOSE:
                mc.displayGuiScreen(null);
                return;
            case BUTTON_MARK:
                if (currentUnit() != null) {
                    // The draft goes along, so marking in the world doesn't lose unsent edits
                    ModNetwork.CHANNEL.sendToServer(new PlannerMarkPacket(view.rampId, view.isRampMode() ? -1 : currentUnit().entityId, currentRoute()));
                }
                mc.displayGuiScreen(null);
                return;
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
        double altitude = Math.max(RouteValidator.MIN_ALTITUDE, Math.min(view.maxAltitude, old.altitude + delta));
        waypoints.set(selectedWaypoint, old.withAltitude(altitude));
        dirty = true;
    }

    private Route currentRoute() {
        return new Route(waypoints, terminal, loiterRadius, target);
    }

    private void send(boolean launch) {
        PlannerOpenPacket.UnitView unit = currentUnit();
        if (unit == null) {
            return;
        }
        ModNetwork.CHANNEL.sendToServer(view.isRampMode()
                ? PlannerSubmitPacket.forRamp(view.rampId, currentRoute(), launch)
                : PlannerSubmitPacket.forUnit(unit.entityId, currentRoute()));
        dirty = false;
    }
}
