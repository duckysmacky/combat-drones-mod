package io.github.duckysmacky.combatdrones.physics;

/** Whether a box (a hitbox) touches a sphere (an EMP field): the box's closest point to the centre is inside it. */
public final class FieldContact {
    private FieldContact() {
    }

    public static boolean touches(double centreX, double centreY, double centreZ, double radius,
                                  double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double dx = centreX - Math.max(minX, Math.min(centreX, maxX));
        double dy = centreY - Math.max(minY, Math.min(centreY, maxY));
        double dz = centreZ - Math.max(minZ, Math.min(centreZ, maxZ));
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
