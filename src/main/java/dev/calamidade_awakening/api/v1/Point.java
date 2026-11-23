package dev.calamidade_awakening.api.v1;

import java.util.Objects;

/**
 * Immutable place in the world used by {@link Beam} ends and {@link Light} positions (0.9.0). Pure Java: the
 * client resolves it every frame from the instance, so nothing travels over the network.
 *
 * <ul>
 * <li>{@link #center(double, double, double)}: offset in blocks from the instance center (which already follows the
 * anchor entity of {@link Effects#follow}).</li>
 * <li>{@link #world(double, double, double)}: fixed world position in blocks, the same for every instance.</li>
 * <li>{@link #anchor}, {@link #anchorEye}, {@link #anchorAim}: the entity of {@link Effects#follow}: its feet or
 * its eyes plus an offset, or the first block its look hits.</li>
 * <li>{@link #caster}, {@link #casterEye}, {@link #casterAim}: the same for the player that cast the instance
 * ({@link Effects#start(net.minecraft.resources.ResourceLocation, net.minecraft.server.level.ServerPlayer,
 * net.minecraft.world.phys.Vec3)} or a card).</li>
 * </ul>
 *
 * <p>Offsets are in blocks (|offset| up to {@value #MAX_OFFSET}). Since 0.10.0 the offsets of {@link #center center
 * points} are in the <b>instance frame</b> set by the {@link Placement} (or the yaw and pitch given to
 * {@link Effects#start}): x to the left, y up, z forward, like Minecraft's {@code ^x ^y ^z}. An instance started without
 * orientation has yaw 0 and pitch 0, whose frame is the world axes, so effects written for 0.9 look the same.
 * {@link #facing(double, double, double)} writes the same offset as right, up and forward. Offsets of entity points
 * (anchor and caster) stay in world axes. Entity points use the entity's interpolated position, so they follow it
 * smoothly. While the entity is not loaded on a client (out of its view,
 * or the instance has no anchor or caster), the beam or light that uses the point is not drawn there.
 *
 * <pre>{@code
 * Point.center(0, 40, 0)        // 40 blocks above the instance center
 * Point.facing(0, 1.5, 6)       // 6 blocks ahead of the center and 1.5 up, turned with the instance (0.10.0)
 * Point.casterEye(0, -0.2, 0)   // just below the caster's eyes
 * Point.casterAim(48)           // first block the caster looks at, up to 48 blocks away
 * Point.socket("boca", 0, 0, .2)  // 0.2 blocks ahead of the anchor entity's "boca" rig anchor (0.13.0)
 * Point.socketAim("boca", 64)     // first block hit straight ahead of the mouth, up to 64 blocks (0.13.0)
 * }</pre>
 *
 * <p><b>Rig anchors (0.13.0).</b> {@link #socket(String, double, double, double)} and {@link #socketAim} follow a named point of the anchor entity's
 * body ({@link RigAnchors}): on clients, the pose the addon captured from its model this frame, else a client
 * provider, else the static definition, else the eyes. Their offsets are right, up and forward in the anchor's own
 * frame. These poses are visual only; server code uses {@link RigAnchors#approximate}.
 */
public final class Point {
    /** Largest offset on each axis, in blocks. */
    public static final double MAX_OFFSET = 1024;
    /** Largest aim distance, in blocks. */
    public static final double MAX_REACH = 256;
    /** Largest horizontal world coordinate, in blocks (the world border). */
    public static final double MAX_WORLD = 30_000_000;

    /** What a point is attached to. */
    public enum Source {
        /** Instance center plus offset in the instance frame (world axes without orientation). */
        CENTER,
        /** Fixed world position. */
        WORLD,
        /** Feet of the anchor entity ({@link Effects#follow}) plus offset. */
        ANCHOR,
        /** Eyes of the anchor entity plus offset. */
        ANCHOR_EYE,
        /** First block hit along the anchor entity's look, up to the reach. */
        ANCHOR_AIM,
        /** Feet of the caster plus offset. */
        CASTER,
        /** Eyes of the caster plus offset. */
        CASTER_EYE,
        /** First block hit along the caster's look, up to the reach. */
        CASTER_AIM,
        /** (0.13.0) A rig anchor of the anchor entity plus an offset in the anchor's frame (right, up, forward). */
        ANCHOR_SOCKET,
        /** (0.13.0) First block hit along the forward of a rig anchor of the anchor entity, up to the reach. */
        ANCHOR_SOCKET_AIM;

        /** True when the point needs an entity (anchor or caster) loaded on the client. */
        public boolean entity() { return this != CENTER && this != WORLD; }
        /** True for the caster's points. */
        public boolean caster() { return this == CASTER || this == CASTER_EYE || this == CASTER_AIM; }
        /** True for the look-ray points ({@code *_AIM}). */
        public boolean aim() { return this == ANCHOR_AIM || this == CASTER_AIM || this == ANCHOR_SOCKET_AIM; }
        /** (0.13.0) True for the rig anchor points ({@code ANCHOR_SOCKET*}). */
        public boolean socket() { return this == ANCHOR_SOCKET || this == ANCHOR_SOCKET_AIM; }
        /** True for the eye points ({@code *_EYE}). */
        public boolean eye() { return this == ANCHOR_EYE || this == CASTER_EYE; }
    }

    private final Source source;
    private final double x, y, z, reach;
    private final String socket;

    private Point(Source source, double x, double y, double z, double reach) {
        this(source, x, y, z, reach, null);
    }

    private Point(Source source, double x, double y, double z, double reach, String socket) {
        this.source = source; this.x = x; this.y = y; this.z = z; this.reach = reach; this.socket = socket;
    }

    /** The instance center. */
    public static Point center() { return new Point(Source.CENTER, 0, 0, 0, 0); }

    /**
     * Offset in blocks from the instance center, in the instance frame: {@code dx} to the left, {@code dy} up,
     * {@code dz} forward (0.10.0). Without orientation (yaw 0, pitch 0) these are the world axes, as in 0.9.
     */
    public static Point center(double dx, double dy, double dz) { return offset(Source.CENTER, dx, dy, dz); }

    /**
     * Offset from the instance center written as {@code right}, {@code up} and {@code forward} blocks in the instance
     * frame (0.10.0). The same point as {@code center(-right, up, forward)}: it turns with the yaw and pitch of the
     * {@link Placement}. Without orientation "forward" is south (+Z) and "right" is west (-X).
     *
     * <pre>{@code
     * Beam.builder(Point.facing(.3, 1.4, .5), Point.facing(0, 1.4, 24))  // a bolt straight ahead
     * }</pre>
     */
    public static Point facing(double right, double up, double forward) {
        return offset(Source.CENTER, right == 0 ? 0 : -right, up, forward);
    }

    /**
     * Fixed world position in blocks, the same for every instance (|x| and |z| up to {@value #MAX_WORLD}, |y| up to
     * {@value #MAX_OFFSET}). Useful for arena fixtures; for anything tied to a cast, prefer {@link #center}.
     */
    public static Point world(double x, double y, double z) {
        if (!finite(x, y, z) || Math.abs(x) > MAX_WORLD || Math.abs(z) > MAX_WORLD || Math.abs(y) > MAX_OFFSET)
            throw new IllegalArgumentException("World point outside the world: " + x + ", " + y + ", " + z);
        return new Point(Source.WORLD, x, y, z, 0);
    }

    /** Feet of the anchor entity ({@link Effects#follow}) plus an offset in blocks. */
    public static Point anchor(double dx, double dy, double dz) { return offset(Source.ANCHOR, dx, dy, dz); }

    /** Eyes of the anchor entity plus an offset in blocks. */
    public static Point anchorEye(double dx, double dy, double dz) { return offset(Source.ANCHOR_EYE, dx, dy, dz); }

    /** First block hit along the anchor entity's look, up to {@code reach} blocks (0.5..{@value #MAX_REACH}); the full reach when nothing is hit. */
    public static Point anchorAim(double reach) { return aim(Source.ANCHOR_AIM, reach); }

    /** Feet of the caster plus an offset in blocks. */
    public static Point caster(double dx, double dy, double dz) { return offset(Source.CASTER, dx, dy, dz); }

    /** Eyes of the caster plus an offset in blocks. */
    public static Point casterEye(double dx, double dy, double dz) { return offset(Source.CASTER_EYE, dx, dy, dz); }

    /** First block hit along the caster's look, up to {@code reach} blocks (0.5..{@value #MAX_REACH}); the full reach when nothing is hit. */
    public static Point casterAim(double reach) { return aim(Source.CASTER_AIM, reach); }

    /**
     * (0.13.0) A rig anchor ({@link RigAnchors}) of the anchor entity plus {@code right}, {@code up} and
     * {@code forward} blocks in the anchor's frame. Name {@code [a-z0-9_]}, up to 64 characters.
     *
     * <pre>{@code
     * Beam.builder(Point.socket("boca", 0, 0, 0), Point.socketAim("boca", 48))  // breath from the mouth
     * }</pre>
     */
    public static Point socket(String name, double right, double up, double forward) {
        socketName(name);
        Point p = offset(Source.ANCHOR_SOCKET, right, up, forward);
        return new Point(Source.ANCHOR_SOCKET, p.x, p.y, p.z, 0, name);
    }

    /**
     * (0.13.0) First block hit along the forward vector of a rig anchor of the anchor entity, up to {@code reach}
     * blocks (0.5..{@value #MAX_REACH}); the full reach when nothing is hit.
     */
    public static Point socketAim(String name, double reach) {
        socketName(name);
        Point p = aim(Source.ANCHOR_SOCKET_AIM, reach);
        return new Point(Source.ANCHOR_SOCKET_AIM, 0, 0, 0, p.reach, name);
    }

    private static void socketName(String name) {
        if (!dev.calamidade_awakening.api.BossMachine.Names.valid(name))
            throw new IllegalArgumentException("Bad anchor name: " + name);
    }

    private static Point offset(Source source, double dx, double dy, double dz) {
        if (!finite(dx, dy, dz) || Math.abs(dx) > MAX_OFFSET || Math.abs(dy) > MAX_OFFSET || Math.abs(dz) > MAX_OFFSET)
            throw new IllegalArgumentException("Point offset must be finite and within " + MAX_OFFSET + " blocks");
        return new Point(source, dx, dy, dz, 0);
    }

    private static Point aim(Source source, double reach) {
        if (!(reach >= .5 && reach <= MAX_REACH))
            throw new IllegalArgumentException("Aim reach must be within 0.5.." + MAX_REACH + " blocks");
        return new Point(source, 0, 0, 0, reach);
    }

    private static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    /** What the point is attached to. */
    public Source source() { return source; }
    /** Offset (or world coordinate for {@link Source#WORLD}) on x, blocks; 0 for aim points. */
    public double x() { return x; }
    /** Offset (or world coordinate for {@link Source#WORLD}) on y, blocks; 0 for aim points. */
    public double y() { return y; }
    /** Offset (or world coordinate for {@link Source#WORLD}) on z, blocks; 0 for aim points. */
    public double z() { return z; }
    /** Aim distance in blocks for {@code *_AIM} points; 0 for the others. */
    public double reach() { return reach; }
    /** (0.13.0) Rig anchor name of {@code ANCHOR_SOCKET*} points; null for the others. */
    public String socket() { return socket; }

    /** Same source, offsets, reach and anchor name. */
    @Override public boolean equals(Object o) {
        return o instanceof Point p && p.source == source && Double.compare(p.x, x) == 0 && Double.compare(p.y, y) == 0
            && Double.compare(p.z, z) == 0 && Double.compare(p.reach, reach) == 0 && Objects.equals(p.socket, socket);
    }

    /** Hash of source, offsets, reach and anchor name. */
    @Override public int hashCode() { return Objects.hash(source, x, y, z, reach, socket); }

    /** Readable form, such as {@code CENTER(0.0, 40.0, 0.0)}, {@code CASTER_AIM(48.0)} or {@code ANCHOR_SOCKET(boca, 0.0, 0.0, 0.2)}. */
    @Override public String toString() {
        String name = socket == null ? "" : socket + ", ";
        return source.aim() ? source + "(" + name + reach + ")" : source + "(" + name + x + ", " + y + ", " + z + ")";
    }
}
