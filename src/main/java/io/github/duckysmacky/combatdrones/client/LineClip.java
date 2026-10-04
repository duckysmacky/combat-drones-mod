package io.github.duckysmacky.combatdrones.client;

/** Liang-Barsky clipping of a 2D line segment to a rectangle. Pure math, no Minecraft. */
public final class LineClip {
    private LineClip() {
    }

    /** The part of the segment inside the rectangle as {x1, y1, x2, y2}, or null if none of it is. */
    public static double[] clip(double x1, double y1, double x2, double y2, double left, double top, double right, double bottom) {
        double deltaX = x2 - x1;
        double deltaY = y2 - y1;
        double[] p = {-deltaX, deltaX, -deltaY, deltaY};
        double[] q = {x1 - left, right - x1, y1 - top, bottom - y1};
        double enter = 0;
        double exit = 1;
        for (int edge = 0; edge < 4; edge++) {
            if (p[edge] == 0) {
                if (q[edge] < 0) {
                    return null; // parallel to this edge and outside it
                }
                continue;
            }
            double t = q[edge] / p[edge];
            if (p[edge] < 0) {
                enter = Math.max(enter, t);
            } else {
                exit = Math.min(exit, t);
            }
        }
        if (enter > exit) {
            return null;
        }
        return new double[] {x1 + deltaX * enter, y1 + deltaY * enter, x1 + deltaX * exit, y1 + deltaY * exit};
    }
}
