package dev.calamidade_awakening.api;

/**
 * Orthonormal frame of an oriented instance (0.10.0). Pure Java (no Minecraft types), shared by the server (follow
 * offsets, cue context), the client (points, beams, lights, layers) and the tests.
 *
 * <p>Local axes are Minecraft's local coordinates ({@code ^x ^y ^z} in commands): {@code x} to the left, {@code y} up,
 * {@code z} forward. Yaw and pitch are in degrees with Minecraft's convention: yaw 0 faces +Z (south), 90 faces -X
 * (west), -90 faces +X (east); pitch is positive when looking down, within [-90, 90]. Yaw 0 and pitch 0 give the world
 * axes, so an instance without orientation keeps the behavior of 0.9.
 */
public final class Basis {
    /** World axes: yaw 0, pitch 0. */
    public static final Basis IDENTITY = new Basis(0, 0);

    /** Yaw (wrapped to [-180, 180)) and pitch ([-90, 90]) in degrees. */
    public final float yaw, pitch;
    /** Left (local +x), up (local +y) and forward (local +z) unit vectors in world axes. */
    public final double lx, ly, lz, ux, uy, uz, fx, fy, fz;

    private Basis(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double cy = Math.cos(y), sy = Math.sin(y), cp = Math.cos(p), sp = Math.sin(p);
        // Forward matches Vec3.directionFromRotation(pitch, yaw); left is horizontal; up = forward x left.
        fx = -sy * cp; fy = -sp; fz = cy * cp;
        lx = cy; ly = 0; lz = sy;
        ux = fy * lz - fz * ly;
        uy = fz * lx - fx * lz;
        uz = fx * ly - fy * lx;
    }

    /**
     * Frame for a yaw and a pitch in degrees. The yaw is wrapped to [-180, 180) and the pitch clamped to [-90, 90];
     * non-finite values throw {@link IllegalArgumentException}.
     */
    public static Basis of(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch))
            throw new IllegalArgumentException("Yaw and pitch must be finite: " + yaw + ", " + pitch);
        float wrapped = wrapYaw(yaw), clamped = Math.clamp(pitch, -90f, 90f);
        return wrapped == 0 && clamped == 0 ? IDENTITY : new Basis(wrapped, clamped);
    }

    /** Yaw wrapped to [-180, 180) degrees ({@code -0} becomes {@code 0}). */
    public static float wrapYaw(float yaw) {
        float wrapped = (float) (yaw - 360.0 * Math.floor((yaw + 180.0) / 360.0));
        if (wrapped >= 180f) wrapped -= 360f;
        return wrapped == 0 ? 0f : wrapped;
    }

    /** Yaw in degrees that looks along the horizontal direction (dx, dz); 0 when both are 0. */
    public static float yawTowards(double dx, double dz) {
        return dx == 0 && dz == 0 ? 0f : wrapYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
    }

    /** Pitch in degrees that looks along (dx, dy, dz) (positive looks down); 0 for a zero vector. */
    public static float pitchTowards(double dx, double dy, double dz) {
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return dy == 0 && horizontal == 0 ? 0f : (float) Math.toDegrees(Math.atan2(-dy, horizontal));
    }

    /** True for the world axes (yaw 0, pitch 0). */
    public boolean identity() { return yaw == 0 && pitch == 0; }

    /** World x of the local vector (x left, y up, z forward). */
    public double worldX(double x, double y, double z) { return lx * x + ux * y + fx * z; }
    /** World y of the local vector (x left, y up, z forward). */
    public double worldY(double x, double y, double z) { return ly * x + uy * y + fy * z; }
    /** World z of the local vector (x left, y up, z forward). */
    public double worldZ(double x, double y, double z) { return lz * x + uz * y + fz * z; }

    /** Local x (left) of a world vector: the inverse of {@link #worldX} (the frame is orthonormal). */
    public double localX(double x, double y, double z) { return lx * x + ly * y + lz * z; }
    /** Local y (up) of a world vector. */
    public double localY(double x, double y, double z) { return ux * x + uy * y + uz * z; }
    /** Local z (forward) of a world vector. */
    public double localZ(double x, double y, double z) { return fx * x + fy * y + fz * z; }

    @Override public String toString() { return "Basis[yaw=" + yaw + ", pitch=" + pitch + "]"; }
}
