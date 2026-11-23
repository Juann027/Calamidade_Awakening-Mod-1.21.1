package dev.calamidade_awakening.api;

/**
 * Rig anchor math (0.13.0). Pure Java, covered by the checkBudget; used by the client cache (captured poses, the
 * fallback chain) and by the server approximation ({@code RigAnchors.serverPose}).
 *
 * <p>A pose is a position plus two orthonormal directions, forward and up; "left" is {@code up × forward}, the same
 * left as {@link Basis} (x left, y up, z forward). Offsets on a pose are written as right, up and forward blocks.
 */
public final class AnchorMath {
    /** Ticks a captured pose may be carried along with the entity when it is not rendered. */
    public static final double MAX_CARRY_TICKS = 10;

    private AnchorMath() {}

    /** An anchor pose in world space: position (blocks) and unit forward and up vectors. */
    public record Pose(double x, double y, double z, double fx, double fy, double fz, double ux, double uy, double uz) {
        /** Left vector ({@code up × forward}). */
        public double[] left() { return cross(ux, uy, uz, fx, fy, fz); }

        /** World position of a point {@code right}, {@code up} and {@code forward} blocks from the anchor in its frame. */
        public double[] at(double right, double up, double forward) {
            double[] l = left();
            return new double[] {
                x - l[0] * right + ux * up + fx * forward,
                y - l[1] * right + uy * up + fy * forward,
                z - l[2] * right + uz * up + fz * forward};
        }

        /** Same directions at another position. */
        public Pose moved(double dx, double dy, double dz) { return new Pose(x + dx, y + dy, z + dz, fx, fy, fz, ux, uy, uz); }
    }

    /** Where a static anchor hangs: the feet with the body yaw, or the eyes with the head yaw and pitch. */
    public enum Frame { BODY, HEAD }

    /**
     * Pose of a static anchor: {@code right}, {@code up} and {@code forward} blocks from the feet in the body frame
     * (yaw only) or from the eyes in the head frame (head yaw and pitch, the look).
     *
     * @param px        entity x (feet), blocks
     * @param eyeHeight eye height above the feet, blocks
     * @param bodyYaw   body yaw, degrees (Minecraft convention: 0 faces south)
     * @param headYaw   head yaw, degrees
     * @param headPitch head pitch, degrees (positive looks down)
     */
    public static Pose fixed(Frame frame, double right, double up, double forward, double px, double py, double pz,
                             double eyeHeight, float bodyYaw, float headYaw, float headPitch) {
        Basis basis = frame == Frame.HEAD ? Basis.of(headYaw, headPitch) : Basis.of(bodyYaw, 0);
        double oy = frame == Frame.HEAD ? py + eyeHeight : py;
        double lx = -right;
        double x = px + basis.worldX(lx, up, forward), y = oy + basis.worldY(lx, up, forward), z = pz + basis.worldZ(lx, up, forward);
        return new Pose(x, y, z, basis.fx, basis.fy, basis.fz, basis.ux, basis.uy, basis.uz);
    }

    /**
     * Pose from a captured model matrix (column-major 4×4, as JOML stores it: {@code m[col*4+row]}) in camera-relative
     * space, plus the camera position. {@code localForward} and {@code localUp} are the model-space axes of the part's
     * front and top (vanilla living models: forward {@code (0,0,-1)}, up {@code (0,-1,0)}, because the renderer flips
     * x and y). Returns null when the matrix is degenerate (zero scale, NaN).
     */
    public static Pose fromMatrix(float[] m, double camX, double camY, double camZ,
                                  float lfx, float lfy, float lfz, float lux, float luy, float luz) {
        double x = m[12] + camX, y = m[13] + camY, z = m[14] + camZ;
        double fx = m[0] * lfx + m[4] * lfy + m[8] * lfz;
        double fy = m[1] * lfx + m[5] * lfy + m[9] * lfz;
        double fz = m[2] * lfx + m[6] * lfy + m[10] * lfz;
        double ux = m[0] * lux + m[4] * luy + m[8] * luz;
        double uy = m[1] * lux + m[5] * luy + m[9] * luz;
        double uz = m[2] * lux + m[6] * luy + m[10] * luz;
        return orthonormal(x, y, z, fx, fy, fz, ux, uy, uz);
    }

    /**
     * Gram-Schmidt: forward normalized, up made perpendicular to it and normalized. A null result means the input
     * cannot define a frame (non-finite position, zero or parallel vectors).
     */
    public static Pose orthonormal(double x, double y, double z, double fx, double fy, double fz, double ux, double uy, double uz) {
        if (!finite(x, y, z) || !finite(fx, fy, fz) || !finite(ux, uy, uz)) return null;
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 1e-9) return null;
        fx /= fl; fy /= fl; fz /= fl;
        double d = ux * fx + uy * fy + uz * fz;
        ux -= d * fx; uy -= d * fy; uz -= d * fz;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1e-6) return null;
        return new Pose(x, y, z, fx, fy, fz, ux / ul, uy / ul, uz / ul);
    }

    /**
     * A captured pose carried along with its entity: rotated about the vertical axis through the entity by the body
     * yaw change and moved by the entity's displacement. Used for a few ticks while the entity is not rendered.
     */
    public static Pose carry(Pose captured, double fromX, double fromY, double fromZ, float fromYaw,
                             double toX, double toY, double toZ, float toYaw) {
        // Forward of a yaw is (-sin, cos) on (x, z): turning by the yaw change is this rotation on the x-z plane.
        double angle = Math.toRadians(toYaw - fromYaw);
        double c = Math.cos(angle), s = Math.sin(angle);
        double rx = captured.x - fromX, rz = captured.z - fromZ;
        double nx = toX + rx * c - rz * s, nz = toZ + rx * s + rz * c;
        double ny = captured.y + (toY - fromY);
        return new Pose(nx, ny, nz,
            captured.fx * c - captured.fz * s, captured.fy, captured.fx * s + captured.fz * c,
            captured.ux * c - captured.uz * s, captured.uy, captured.ux * s + captured.uz * c);
    }

    /** True while a capture made at {@code capturedAt} (game ticks plus partial) may still be carried at {@code now}. */
    public static boolean carriable(double capturedAt, double now) {
        double age = now - capturedAt;
        return age >= 0 && age <= MAX_CARRY_TICKS;
    }

    /**
     * End of a ray from the pose along its forward vector, {@code reach} blocks long (before block clipping, which the
     * client does with the world).
     */
    public static double[] aim(Pose pose, double reach) {
        return new double[] {pose.x + pose.fx * reach, pose.y + pose.fy * reach, pose.z + pose.fz * reach};
    }

    static double[] cross(double ax, double ay, double az, double bx, double by, double bz) {
        return new double[] {ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
    }

    private static boolean finite(double a, double b, double c) {
        return Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c);
    }
}
