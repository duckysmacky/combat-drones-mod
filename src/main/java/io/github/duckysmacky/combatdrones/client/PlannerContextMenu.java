package io.github.duckysmacky.combatdrones.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Right-click popup for the Mission Planner map. Each row holds segments laid out left to right: plain labels, or
 * clickable options that can be highlighted (e.g. the current speed) and may close the menu when used.
 */
final class PlannerContextMenu {
    private static final int ROW = 12;
    private static final int PADDING = 4;
    private static final int GAP = 6;

    static final class Segment {
        final Supplier<String> text;
        final Runnable action;
        final BooleanSupplier highlighted;
        final boolean closes;

        private Segment(Supplier<String> text, Runnable action, BooleanSupplier highlighted, boolean closes) {
            this.text = text;
            this.action = action;
            this.highlighted = highlighted;
            this.closes = closes;
        }

        static Segment label(Supplier<String> text) {
            return new Segment(text, null, () -> false, false);
        }

        static Segment option(Supplier<String> text, Runnable action, BooleanSupplier highlighted) {
            return new Segment(text, action, highlighted, false);
        }

        static Segment command(Supplier<String> text, Runnable action) {
            return new Segment(text, action, () -> false, true);
        }
    }

    private final List<List<Segment>> rows = new ArrayList<>();
    private final int x;
    private final int y;

    PlannerContextMenu(int x, int y) {
        this.x = x;
        this.y = y;
    }

    PlannerContextMenu row(Segment... segments) {
        rows.add(Arrays.asList(segments));
        return this;
    }

    private int width(FontRenderer font) {
        int widest = 0;
        for (List<Segment> row : rows) {
            int rowWidth = 0;
            for (Segment segment : row) {
                rowWidth += font.getStringWidth(segment.text.get()) + GAP;
            }
            widest = Math.max(widest, rowWidth - GAP);
        }
        return widest + PADDING * 2;
    }

    /** Keeps the menu on screen. */
    private int left(FontRenderer font, int screenWidth) {
        return Math.min(x, screenWidth - width(font) - 2);
    }

    private int top(int screenHeight) {
        return Math.min(y, screenHeight - rows.size() * ROW - PADDING * 2 - 2);
    }

    boolean contains(FontRenderer font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        int left = left(font, screenWidth);
        int top = top(screenHeight);
        return mouseX >= left && mouseX <= left + width(font) && mouseY >= top && mouseY <= top + rows.size() * ROW + PADDING * 2;
    }

    void draw(FontRenderer font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        int left = left(font, screenWidth);
        int top = top(screenHeight);
        Gui.drawRect(left, top, left + width(font), top + rows.size() * ROW + PADDING * 2, 0xE0101010);
        Gui.drawRect(left, top, left + width(font), top + 1, 0xFF888888);
        for (int index = 0; index < rows.size(); index++) {
            int segmentX = left + PADDING;
            int rowY = top + PADDING + index * ROW;
            for (Segment segment : rows.get(index)) {
                String text = segment.text.get();
                int textWidth = font.getStringWidth(text);
                boolean hovered = segment.action != null && mouseX >= segmentX && mouseX < segmentX + textWidth
                        && mouseY >= rowY - 1 && mouseY < rowY + ROW - 1;
                int color = segment.action == null ? 0xAAAAAA : hovered ? 0xFFFF55 : segment.highlighted.getAsBoolean() ? 0x55FF55 : 0xFFFFFF;
                font.drawStringWithShadow(text, segmentX, rowY + 1, color);
                segmentX += textWidth + GAP;
            }
        }
    }

    /** Runs the clicked option. Returns true if the menu should close afterwards. */
    boolean click(FontRenderer font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        int left = left(font, screenWidth);
        int top = top(screenHeight);
        int index = (mouseY - top - PADDING + 1) / ROW;
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        int segmentX = left + PADDING;
        for (Segment segment : rows.get(index)) {
            int textWidth = font.getStringWidth(segment.text.get());
            if (segment.action != null && mouseX >= segmentX && mouseX < segmentX + textWidth) {
                segment.action.run();
                return segment.closes;
            }
            segmentX += textWidth + GAP;
        }
        return false;
    }
}
