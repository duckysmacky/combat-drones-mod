package io.github.duckysmacky.combatdrones.client;

import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.network.ModNetwork;
import io.github.duckysmacky.combatdrones.network.TabletActionPacket;
import io.github.duckysmacky.combatdrones.network.TabletStatusPacket;
import io.github.duckysmacky.combatdrones.physics.CruiseMissileFlight;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * The Missile Control Tablet screen. Normally it's just the missile's status and one big centred button: LAUNCH, or SELECT
 * TARGET once the missile is up and ready. SELECT TARGET opens the target map: a hitmarker follows the mouse (the
 * cursor is hidden over the map) and a left click strikes that point at once. The server decides everything; the
 * screen only shows it and asks.
 */
public class GuiMissileTablet extends GuiScreen {
    /** Tablet bezel around the screen. */
    private static final int BEZEL = 10;
    private static final int TOP_BAR = 14;
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 36;
    private static final int LINE = 11;
    private static final int POLL_TICKS = 10;
    /** Target map: default zoom (pixels per block) and limits. */
    private static final double TARGET_SCALE = 1.5;
    private static final double MIN_SCALE = 0.05;
    private static final double MAX_SCALE = 8;
    private static final double LOW_FUEL = 0.2;

    private static final int BEZEL_COLOR = 0xFF2A2C2E;
    private static final int BEZEL_EDGE = 0xFF45484C;
    private static final int SCREEN_BACKGROUND = 0xFF0E140E;
    private static final int TEXT = 0xFFD8E8D0;
    private static final int DIM = 0xFF8A9A84;
    private static final int GOOD = 0xFF55FF55;
    private static final int WARNING = 0xFFFF5555;
    private static final int AMBER = 0xFFFFAA00;
    private static final int RANGE_COLOR = 0xC0FFAA00;
    private static final int HITMARKER = 0xFFFF3030;
    private static final int HITMARKER_INVALID = 0xFF909090;
    private static final int PAD_COLOR = 0xFF55AAFF;

    /** A transparent 1x1 cursor, to hide the pointer over the target map; null if the system can't do it. */
    @Nullable
    private static Cursor blankCursor;
    private static boolean blankCursorTried;

    private TabletStatusPacket status;
    /** Client ticks since the last status arrived, so countdowns run between polls. */
    private int ticksSinceStatus;
    private int ticks;
    private boolean targetView;
    private boolean strikeSent;
    private boolean cursorHidden;

    private double centerX;
    private double centerZ;
    private double scale = TARGET_SCALE;
    private int screenLeft;
    private int screenTop;
    private int screenRight;
    private int screenBottom;
    private int mapTop;
    private int buttonLeft;
    private int buttonTop;
    private boolean panning;
    private int lastMouseX;
    private int lastMouseY;
    private PlannerTerrainMap terrain;

    public GuiMissileTablet(TabletStatusPacket status) {
        this.status = status;
    }

    public void update(TabletStatusPacket packet) {
        status = packet;
        ticksSinceStatus = 0;
        if (targetView && packet.state != TabletStatusPacket.State.STANDBY && !strikeSent) {
            closeTargetView(); // ran out of fuel (or similar) while choosing
        }
    }

    @Override
    public void initGui() {
        screenLeft = BEZEL;
        screenTop = BEZEL;
        screenRight = width - BEZEL;
        screenBottom = height - BEZEL;
        mapTop = screenTop + TOP_BAR;
        buttonLeft = (width - BUTTON_WIDTH) / 2;
        buttonTop = height / 2 + 30;
    }

    @Override
    public void updateScreen() {
        ticks++;
        ticksSinceStatus++;
        if (ticks % POLL_TICKS == 0) {
            ModNetwork.CHANNEL.sendToServer(new TabletActionPacket(TabletActionPacket.Action.STATUS, 0, 0));
        }
    }

    @Override
    public void onGuiClosed() {
        setCursorHidden(false);
        if (terrain != null) {
            terrain.delete();
            terrain = null;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBezel();
        if (targetView) {
            drawTargetView(mouseX, mouseY);
        } else {
            drawStatusView(mouseX, mouseY);
        }
        setCursorHidden(targetView && !strikeSent && insideMap(mouseX, mouseY));
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** A rugged tablet: thick dark bezel with a lighter edge and a camera dot, the screen inside. */
    private void drawBezel() {
        drawRect(0, 0, width, height, BEZEL_EDGE);
        drawRect(2, 2, width - 2, height - 2, BEZEL_COLOR);
        drawRect(width / 2 - 2, 4, width / 2 + 2, 7, 0xFF101214);
        drawRect(screenLeft, screenTop, screenRight, screenBottom, SCREEN_BACKGROUND);
    }

    private int ticksLeft() {
        return Math.max(0, status.ticksLeft - ticksSinceStatus);
    }

    private static String seconds(int ticks) {
        return String.valueOf((ticks + 19) / 20);
    }

    // ---- status view --------------------------------------------------------------------------------------------

    private void drawStatusView(int mouseX, int mouseY) {
        fontRenderer.drawStringWithShadow(I18n.format("gui.combat_drones.tablet.title"), screenLeft + 6, screenTop + 5, GOOD);
        String pad = I18n.format("gui.combat_drones.tablet.pad", status.padX, status.padY, status.padZ);
        fontRenderer.drawStringWithShadow(pad, screenRight - 6 - fontRenderer.getStringWidth(pad), screenTop + 5, DIM);

        int centerX = width / 2;
        int y = height / 2 - 70;
        String key = "gui.combat_drones.tablet.state." + status.state.name().toLowerCase(Locale.ROOT);
        drawScaledCentered(I18n.format(key), centerX, y, 2.5F, stateColor());
        y += 28;
        List<String> info = fontRenderer.listFormattedStringToWidth(I18n.format(key + ".info"), Math.min(320, width - 60));
        for (String line : info) {
            drawCenteredString(fontRenderer, line, centerX, y, DIM);
            y += LINE;
        }
        y += 4;
        switch (status.state) {
            case LAUNCHING:
                if (status.stage >= 0 && status.stage < CruiseMissileFlight.Stage.values().length) {
                    String stage = CruiseMissileFlight.Stage.values()[status.stage].name().toLowerCase(Locale.ROOT);
                    drawCenteredString(fontRenderer, I18n.format("gui.combat_drones.tablet.stage." + stage) + "   "
                            + I18n.format("gui.combat_drones.tablet.countdown", seconds(ticksLeft())), centerX, y, AMBER);
                    y += LINE;
                }
                drawHealth(centerX, y);
                break;
            case STANDBY:
                drawFuel(centerX, y);
                break;
            case STRIKING:
                drawHealth(centerX, y);
                break;
            case NO_FUEL:
            case READY:
            case SKY_BLOCKED:
                drawCenteredString(fontRenderer, I18n.format("gui.combat_drones.planner.fuel", Math.round(status.fuelFraction * 100)), centerX, y, TEXT);
                break;
            default:
                break;
        }
        drawBigButton(mouseX, mouseY);
    }

    private void drawHealth(int centerX, int y) {
        drawCenteredString(fontRenderer, I18n.format("gui.combat_drones.tablet.health", Math.round(status.healthFraction * 100)), centerX, y,
                status.healthFraction < 0.5 ? WARNING : TEXT);
    }

    private void drawFuel(int centerX, int y) {
        boolean low = status.fuelFraction < LOW_FUEL;
        drawCenteredString(fontRenderer, I18n.format("gui.combat_drones.tablet.fuel", Math.round(status.fuelFraction * 100), seconds(ticksLeft())),
                centerX, y, low ? WARNING : TEXT);
        if (low && (ticks / 10) % 2 == 0) {
            drawCenteredString(fontRenderer, I18n.format("gui.combat_drones.tablet.fuel_warning"), centerX, y + LINE, WARNING);
        }
    }

    private void drawScaledCentered(String text, int centerX, int y, float textScale, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, y, 0);
        GlStateManager.scale(textScale, textScale, 1);
        fontRenderer.drawStringWithShadow(text, -fontRenderer.getStringWidth(text) / 2.0F, 0, color);
        GlStateManager.popMatrix();
    }

    private int stateColor() {
        switch (status.state) {
            case READY:
            case STANDBY:
                return GOOD;
            case LAUNCHING:
            case STRIKING:
                return AMBER;
            default:
                return WARNING;
        }
    }

    private boolean buttonEnabled() {
        return status.state.canLaunch() || status.state == TabletStatusPacket.State.STANDBY;
    }

    private String buttonLabel() {
        if (status.state == TabletStatusPacket.State.STANDBY) {
            return I18n.format("gui.combat_drones.tablet.select_target");
        }
        if (status.state.ordinal() <= TabletStatusPacket.State.READY.ordinal()) {
            return I18n.format("gui.combat_drones.tablet.launch");
        }
        return I18n.format("gui.combat_drones.tablet.state." + status.state.name().toLowerCase(Locale.ROOT));
    }

    private boolean overButton(int mouseX, int mouseY) {
        return mouseX >= buttonLeft && mouseX < buttonLeft + BUTTON_WIDTH && mouseY >= buttonTop && mouseY < buttonTop + BUTTON_HEIGHT;
    }

    private void drawBigButton(int mouseX, int mouseY) {
        boolean enabled = buttonEnabled();
        boolean hover = enabled && overButton(mouseX, mouseY);
        boolean strike = status.state == TabletStatusPacket.State.STANDBY;
        int fill = !enabled ? 0xFF2A2E2A : strike ? (hover ? 0xFFD03030 : 0xFFA02020) : (hover ? 0xFF3A9A3A : 0xFF2A7A2A);
        drawRect(buttonLeft - 2, buttonTop - 2, buttonLeft + BUTTON_WIDTH + 2, buttonTop + BUTTON_HEIGHT + 2, enabled ? 0xFFE0E0E0 : 0xFF505450);
        drawRect(buttonLeft, buttonTop, buttonLeft + BUTTON_WIDTH, buttonTop + BUTTON_HEIGHT, fill);
        drawScaledCentered(buttonLabel(), buttonLeft + BUTTON_WIDTH / 2, buttonTop + BUTTON_HEIGHT / 2 - 7, 2, enabled ? 0xFFFFFF : 0x808880);
    }

    private void pressBigButton() {
        beep(1.4F);
        if (status.state.canLaunch()) {
            ModNetwork.CHANNEL.sendToServer(new TabletActionPacket(TabletActionPacket.Action.LAUNCH, 0, 0));
        } else if (status.state == TabletStatusPacket.State.STANDBY) {
            openTargetView();
        }
    }

    // ---- target view --------------------------------------------------------------------------------------------

    /** Close in, centred on where the player stands if that's within reach, else on the pad. */
    private void openTargetView() {
        targetView = true;
        strikeSent = false;
        scale = TARGET_SCALE;
        boolean playerInRange = mc.player != null && distanceFromPad(mc.player.posX, mc.player.posZ) <= status.strikeRange;
        centerX = playerInRange ? mc.player.posX : status.padX + 0.5;
        centerZ = playerInRange ? mc.player.posZ : status.padZ + 0.5;
    }

    private void closeTargetView() {
        targetView = false;
        panning = false;
        setCursorHidden(false);
    }

    private void drawTargetView(int mouseX, int mouseY) {
        drawMap();
        drawRect(screenLeft, screenTop, screenRight, mapTop, 0xE0141C14);
        fontRenderer.drawStringWithShadow(I18n.format("gui.combat_drones.tablet.target_title"), screenLeft + 6, screenTop + 3, WARNING);
        String fuel = I18n.format("gui.combat_drones.tablet.fuel", Math.round(status.fuelFraction * 100), seconds(ticksLeft()));
        fontRenderer.drawStringWithShadow(fuel, screenRight - 6 - fontRenderer.getStringWidth(fuel), screenTop + 3,
                status.fuelFraction < LOW_FUEL ? WARNING : TEXT);
        fontRenderer.drawStringWithShadow(I18n.format("gui.combat_drones.tablet.target_help"), screenLeft + 6, screenBottom - 11, DIM);
        if (strikeSent) {
            drawScaledCentered(I18n.format("gui.combat_drones.tablet.striking"), width / 2, height / 2 - 10, 2.5F, WARNING);
        } else if (insideMap(mouseX, mouseY)) {
            drawHitmarker(mouseX, mouseY);
        }
    }

    private void drawMap() {
        if (terrain == null) {
            terrain = new PlannerTerrainMap();
        }
        terrain.update(mc.world, centerX, centerZ, (screenRight - screenLeft) / scale);
        terrain.draw(screenLeft, mapTop, screenRight, screenBottom, centerX, centerZ, scale);
        beginLines();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        circle(buffer, toScreenX(status.padX + 0.5), toScreenY(status.padZ + 0.5), status.strikeRange * scale, 128, RANGE_COLOR);
        tessellator.draw();
        endLines();
        marker(status.padX + 0.5, status.padZ + 0.5, PAD_COLOR, 3);
        if (mc.player != null) {
            marker(mc.player.posX, mc.player.posZ, 0xFFFFFFFF, 2);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        fontRenderer.drawStringWithShadow("N", width / 2.0F - 2, mapTop + 3, 0xFFFFFF);
    }

    /** Red crosshair at the mouse with the circle the dive can still be steered within; grey when out of range. */
    private void drawHitmarker(int mouseX, int mouseY) {
        boolean valid = targetValid(toWorldX(mouseX), toWorldZ(mouseY));
        int color = valid ? HITMARKER : HITMARKER_INVALID;
        beginLines();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        int gap = 3;
        int arm = 10;
        line(buffer, mouseX - gap - arm, mouseY, mouseX - gap, mouseY, color);
        line(buffer, mouseX + gap, mouseY, mouseX + gap + arm, mouseY, color);
        line(buffer, mouseX, mouseY - gap - arm, mouseX, mouseY - gap, color);
        line(buffer, mouseX, mouseY + gap, mouseX, mouseY + gap + arm, color);
        circle(buffer, mouseX, mouseY, status.steerReach * scale, 48, (color & 0xFFFFFF) | 0x70000000);
        tessellator.draw();
        endLines();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        Gui.drawRect(mouseX, mouseY, mouseX + 1, mouseY + 1, color);
        String readout = valid ? String.format(Locale.ROOT, "%d, %d  %d m", Math.round(toWorldX(mouseX)), Math.round(toWorldZ(mouseY)),
                Math.round(distanceFromPad(toWorldX(mouseX), toWorldZ(mouseY)))) : I18n.format("gui.combat_drones.tablet.out_of_range");
        fontRenderer.drawStringWithShadow(readout, mouseX + 8, mouseY + 6, valid ? TEXT : WARNING);
    }

    /** Line drawing over the map, clipped to it. */
    private void beginLines() {
        int factor = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(screenLeft * factor, mc.displayHeight - screenBottom * factor, (screenRight - screenLeft) * factor, (screenBottom - mapTop) * factor);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
    }

    private void endLines() {
        GlStateManager.enableTexture2D();
    }

    private static void circle(BufferBuilder buffer, double centerX, double centerY, double radius, int segments, int color) {
        for (int index = 0; index < segments; index++) {
            double from = Math.PI * 2 * index / segments;
            double to = Math.PI * 2 * (index + 1) / segments;
            line(buffer, centerX + Math.cos(from) * radius, centerY + Math.sin(from) * radius,
                    centerX + Math.cos(to) * radius, centerY + Math.sin(to) * radius, color);
        }
    }

    private static void line(BufferBuilder buffer, double fromX, double fromY, double toX, double toY, int color) {
        int alpha = color >>> 24;
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        buffer.pos(fromX, fromY, 0).color(red, green, blue, alpha).endVertex();
        buffer.pos(toX, toY, 0).color(red, green, blue, alpha).endVertex();
    }

    private void marker(double worldX, double worldZ, int color, int size) {
        int x = (int) Math.round(toScreenX(worldX));
        int y = (int) Math.round(toScreenY(worldZ));
        if (insideMap(x, y)) {
            Gui.drawRect(x - size, y - size, x + size + 1, y + size + 1, color);
        }
    }

    private double distanceFromPad(double x, double z) {
        return Math.hypot(x - (status.padX + 0.5), z - (status.padZ + 0.5));
    }

    private boolean targetValid(double x, double z) {
        return distanceFromPad(x, z) <= status.strikeRange && mc.world.getWorldBorder().contains(new BlockPos(x, 0, z));
    }

    private double toScreenX(double worldX) {
        return width / 2.0 + (worldX - centerX) * scale;
    }

    private double toScreenY(double worldZ) {
        return (mapTop + screenBottom) / 2.0 + (worldZ - centerZ) * scale;
    }

    private double toWorldX(int screenX) {
        return centerX + (screenX - width / 2.0) / scale;
    }

    private double toWorldZ(int screenY) {
        return centerZ + (screenY - (mapTop + screenBottom) / 2.0) / scale;
    }

    private boolean insideMap(int screenX, int screenY) {
        return screenX >= screenLeft && screenX < screenRight && screenY >= mapTop && screenY < screenBottom;
    }

    // ---- input --------------------------------------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (!targetView) {
            if (button == 0 && overButton(mouseX, mouseY) && buttonEnabled()) {
                pressBigButton();
            }
            return;
        }
        if (button == 1 && insideMap(mouseX, mouseY)) {
            panning = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        } else if (button == 0 && insideMap(mouseX, mouseY) && !strikeSent) {
            double targetX = toWorldX(mouseX);
            double targetZ = toWorldZ(mouseY);
            if (!targetValid(targetX, targetZ)) {
                beep(0.6F);
                return;
            }
            strikeSent = true;
            setCursorHidden(false);
            beep(1.8F);
            ModNetwork.CHANNEL.sendToServer(new TabletActionPacket(TabletActionPacket.Action.STRIKE, targetX, targetZ));
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (panning) {
            centerX -= (mouseX - lastMouseX) / scale;
            centerZ -= (mouseY - lastMouseY) / scale;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        panning = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && targetView) {
            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * (wheel > 0 ? 1.25 : 0.8)));
        }
    }

    /** Esc on the target map goes back to the status view instead of closing the tablet. */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE && targetView) {
            closeTargetView();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void beep(float pitch) {
        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(ModSounds.DRONE_BEEP, pitch));
    }

    /** Hides the OS cursor (the hitmarker replaces it); silently does nothing where the system can't. */
    private void setCursorHidden(boolean hidden) {
        if (hidden == cursorHidden) {
            return;
        }
        Cursor cursor = hidden ? blankCursor() : null;
        if (hidden && cursor == null) {
            return;
        }
        try {
            Mouse.setNativeCursor(cursor);
            cursorHidden = hidden;
        } catch (LWJGLException ignored) {
            // keep the visible cursor
        }
    }

    @Nullable
    private static Cursor blankCursor() {
        if (!blankCursorTried) {
            blankCursorTried = true;
            try {
                if ((Cursor.getCapabilities() & Cursor.CURSOR_ONE_BIT_TRANSPARENCY) != 0) {
                    blankCursor = new Cursor(1, 1, 0, 0, 1, BufferUtils.createIntBuffer(1), null);
                }
            } catch (LWJGLException ignored) {
                // no custom cursors here
            }
        }
        return blankCursor;
    }
}
