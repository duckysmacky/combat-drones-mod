package io.github.duckysmacky.combatdrones.physics;

/** Whether a point is on a camera's screen: inside the rectangle its horizontal and vertical half-angles span. */
public final class CameraView {
    private CameraView() {
    }

    /**
     * {@code yaw}/{@code pitch}: the camera direction in Minecraft degrees (pitch positive = down); {@code dx, dy, dz}:
     * the point relative to the camera; {@code halfWidth}/{@code halfHeight}: half the field of view, in degrees.
     */
    public static boolean contains(double yaw, double pitch, double dx, double dy, double dz, double halfWidth, double halfHeight) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        // Forward as Vec3d.fromPitchYaw, right is horizontal and square to it, up completes the basis
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy = -Math.sin(pitchRad);
        double fz = Math.cos(yawRad) * Math.cos(pitchRad);
        double rx = Math.cos(yawRad);
        double rz = Math.sin(yawRad);
        double ux = fy * rz;
        double uy = fz * rx - fx * rz;
        double uz = -fy * rx;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth <= 0) {
            return false;
        }
        double across = Math.abs(dx * rx + dz * rz);
        double up = Math.abs(dx * ux + dy * uy + dz * uz);
        return across <= depth * Math.tan(Math.toRadians(halfWidth)) && up <= depth * Math.tan(Math.toRadians(halfHeight));
    }
}
