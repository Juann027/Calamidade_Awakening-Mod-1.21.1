package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.api.TerrainNoise;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable excavation shape of a {@link TerrainEdit} (0.11.0): a sphere, a crater (a bowl with a configurable depth
 * profile), a trench (a capsule along a segment, the cut of a beam), a cylinder, or a combination of them, with an
 * optional rough edge from deterministic noise. Pure Java, no Minecraft types: the server plan, the client heat shader
 * ({@code terrain.fsh}) and the tests evaluate the same formulas from the same seed, so the client draws the heat over
 * the right area without receiving the block list.
 *
 * <p>Coordinates are in blocks in the instance frame of {@link Placement} (0.10.0): {@code x} to the left, {@code y}
 * up, {@code z} forward, centered on the edit origin (the instance center). An instance without orientation uses the
 * world axes, so forward is south. {@link #distance} is a signed distance estimate: negative inside, positive outside,
 * close to the Euclidean distance near the surface. A block is carved when the distance at its center is &lt;= 0.
 *
 * <ul>
 * <li>{@link #sphere}: a ball of {@code radius} blocks around the origin.</li>
 * <li>{@link #crater}: a bowl of {@code radius} blocks whose floor sits {@code depth} blocks below the origin at the
 * center and rises to the origin at the rim: depth at horizontal distance {@code r} is
 * {@code depth * (1 - (r / radius)^profile)} (profile 1 a cone, 2 a parabola, the default; 8 or more a flat bottom with
 * steep walls). Everything above the floor up to {@code clearance} blocks above the origin (default: the depth) is
 * carved too, so hills and trees inside the rim go.</li>
 * <li>{@link #trench}: a capsule of {@code radius} blocks around the segment from {@code -length/2} to
 * {@code +length/2} along local forward. Turned by the instance yaw it runs along the look direction; the pitch tilts
 * it into the ground.</li>
 * <li>{@link #cylinder}: a vertical (local up) cylinder of {@code radius} and {@code height} blocks, centered.</li>
 * <li>{@link #union} adds shapes, {@link #minus} subtracts one, {@link #offset} moves a shape in the instance frame and
 * {@link #rough} adds the noisy edge (on the final shape only).</li>
 * </ul>
 *
 * <pre>{@code
 * static final TerrainShape CRATERA = TerrainShape.crater(9, 5).profile(2.5f)
 *     .union(TerrainShape.sphere(3).offset(0, -4, 0))
 *     .rough(1.2f, 3.5f);
 * }</pre>
 *
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class TerrainShape {
    /** Largest radius, depth or clearance of one primitive, blocks. */
    public static final float MAX_SIZE = 64;
    /** Largest trench length or cylinder height, blocks. */
    public static final float MAX_LENGTH = 128;
    /** Largest offset of a primitive from the origin, blocks per axis. */
    public static final float MAX_OFFSET = 64;
    /** Most primitives in one combined shape (added and subtracted). */
    public static final int MAX_PRIMITIVES = 8;
    /** Largest bounding radius of a whole shape around the origin, blocks (offsets and rough edge included). */
    public static final float MAX_EXTENT = 128;
    /** Largest rough edge amplitude, blocks. */
    public static final float MAX_ROUGHNESS = 8;

    /** Kind of a primitive. */
    public enum Kind {
        /** Ball: {@code a} = radius. */
        SPHERE,
        /** Bowl: {@code a} = radius, {@code b} = depth, {@code c} = profile exponent, {@code d} = clearance. */
        CRATER,
        /** Capsule along local forward: {@code a} = radius, {@code b} = length. */
        TRENCH,
        /** Vertical cylinder: {@code a} = radius, {@code b} = height. */
        CYLINDER
    }

    /**
     * One primitive of a shape, in the instance frame.
     *
     * @param kind     primitive kind
     * @param subtract true when the primitive is cut out of the added ones ({@link #minus})
     * @param x        center offset to the left, blocks
     * @param y        center offset up, blocks
     * @param z        center offset forward, blocks
     * @param a        first size (radius), blocks
     * @param b        second size (crater depth, trench length, cylinder height), blocks; 0 for a sphere
     * @param c        crater profile exponent (1..16); 0 for the other kinds
     * @param d        crater clearance above the origin, blocks; 0 for the other kinds
     */
    public record Primitive(Kind kind, boolean subtract, float x, float y, float z, float a, float b, float c, float d) {
        public Primitive {
            Objects.requireNonNull(kind, "kind");
        }

        /** Signed distance of a local point (blocks, instance frame) to this primitive, without the rough edge. */
        public double distance(double px, double py, double pz) {
            double qx = px - x, qy = py - y, qz = pz - z;
            switch (kind) {
                case SPHERE: return Math.sqrt(qx * qx + qy * qy + qz * qz) - a;
                case TRENCH: {
                    double half = b * .5;
                    double along = qz - Math.clamp(qz, -half, half);
                    return Math.sqrt(qx * qx + qy * qy + along * along) - a;
                }
                case CYLINDER: {
                    double dx = Math.sqrt(qx * qx + qz * qz) - a, dy = Math.abs(qy) - b * .5;
                    double ox = Math.max(dx, 0), oy = Math.max(dy, 0);
                    return Math.min(Math.max(dx, dy), 0) + Math.sqrt(ox * ox + oy * oy);
                }
                default: {
                    double rho = Math.sqrt(qx * qx + qz * qz);
                    double t = Math.min(rho / a, 1);
                    double floor = -b * (1 - Math.pow(t, c));
                    double slope = rho < a ? b * c * Math.pow(Math.max(t, 1e-4), c - 1) / a : 0;
                    double bowl = (floor - qy) / Math.sqrt(1 + slope * slope);
                    return Math.max(bowl, Math.max(rho - a, qy - d));
                }
            }
        }

        /** Radius of a ball around the primitive center that contains it, blocks. */
        public double bound() {
            return switch (kind) {
                case SPHERE -> a;
                case TRENCH -> a + b * .5;
                case CYLINDER -> Math.sqrt(a * a + b * b * .25);
                case CRATER -> Math.sqrt(a * a + Math.max(b, d) * Math.max(b, d));
            };
        }

        Primitive moved(float dx, float dy, float dz) {
            return new Primitive(kind, subtract, x + dx, y + dy, z + dz, a, b, c, d);
        }

        Primitive cut() { return new Primitive(kind, true, x, y, z, a, b, c, d); }
    }

    private final List<Primitive> primitives;
    private final float roughness, roughScale;

    private TerrainShape(List<Primitive> primitives, float roughness, float roughScale) {
        this.primitives = List.copyOf(primitives);
        this.roughness = roughness;
        this.roughScale = roughScale;
        if (this.primitives.size() > MAX_PRIMITIVES)
            throw new IllegalArgumentException("Expected at most " + MAX_PRIMITIVES + " primitives, got " + this.primitives.size());
        if (this.primitives.stream().noneMatch(p -> !p.subtract()))
            throw new IllegalArgumentException("A terrain shape needs at least one added primitive");
        for (Primitive p : this.primitives)
            if (Math.abs(p.x()) > MAX_OFFSET || Math.abs(p.y()) > MAX_OFFSET || Math.abs(p.z()) > MAX_OFFSET)
                throw new IllegalArgumentException("Primitive offset beyond " + MAX_OFFSET + " blocks: " + p);
        if (extent() > MAX_EXTENT)
            throw new IllegalArgumentException("Shape extent " + extent() + " beyond " + MAX_EXTENT + " blocks");
    }

    private static float size(String what, float value, float max) {
        if (!Float.isFinite(value) || value <= 0 || value > max)
            throw new IllegalArgumentException("Expected " + what + " in (0, " + max + "], got " + value);
        return value;
    }

    private static TerrainShape single(Primitive primitive) {
        return new TerrainShape(List.of(primitive), 0, 1);
    }

    /** Ball of {@code radius} blocks, (0, {@value #MAX_SIZE}], around the origin. */
    public static TerrainShape sphere(float radius) {
        return single(new Primitive(Kind.SPHERE, false, 0, 0, 0, size("radius", radius, MAX_SIZE), 0, 0, 0));
    }

    /**
     * Bowl of {@code radius} blocks whose floor is {@code depth} blocks below the origin at the center (both
     * (0, {@value #MAX_SIZE}]), parabolic profile (2) and clearance equal to the depth. Change them with
     * {@link #profile} and {@link #clearance}.
     */
    public static TerrainShape crater(float radius, float depth) {
        float d = size("depth", depth, MAX_SIZE);
        return single(new Primitive(Kind.CRATER, false, 0, 0, 0, size("radius", radius, MAX_SIZE), d, 2, d));
    }

    /**
     * Capsule of {@code radius} blocks ((0, {@value #MAX_SIZE}]) around a segment {@code length} blocks long
     * ((0, {@value #MAX_LENGTH}]) along local forward, centered on the origin: the cut a beam leaves in the ground.
     */
    public static TerrainShape trench(float length, float radius) {
        return single(new Primitive(Kind.TRENCH, false, 0, 0, 0, size("radius", radius, MAX_SIZE),
            size("length", length, MAX_LENGTH), 0, 0));
    }

    /** Vertical cylinder of {@code radius} ((0, {@value #MAX_SIZE}]) and {@code height} ((0, {@value #MAX_LENGTH}]) blocks, centered. */
    public static TerrainShape cylinder(float radius, float height) {
        return single(new Primitive(Kind.CYLINDER, false, 0, 0, 0, size("radius", radius, MAX_SIZE),
            size("height", height, MAX_LENGTH), 0, 0));
    }

    /** Union of shapes (at least one); each part must be without a rough edge (call {@link #rough} on the result). */
    public static TerrainShape union(TerrainShape first, TerrainShape... more) {
        List<Primitive> all = new ArrayList<>(Objects.requireNonNull(first, "first").plain("union").primitives);
        for (TerrainShape shape : Objects.requireNonNull(more, "more"))
            all.addAll(Objects.requireNonNull(shape, "shape").plain("union").primitives);
        return new TerrainShape(all, 0, 1);
    }

    /** This shape plus {@code other} (see {@link #union(TerrainShape, TerrainShape...)}). */
    public TerrainShape union(TerrainShape other) { return union(this, other); }

    /**
     * This shape with {@code cut} carved out of it: blocks inside {@code cut} are left alone (for example a pillar
     * spared in the middle of a crater). The cut must have only added primitives and no rough edge.
     */
    public TerrainShape minus(TerrainShape cut) {
        Objects.requireNonNull(cut, "cut").plain("minus");
        if (cut.primitives.stream().anyMatch(Primitive::subtract))
            throw new IllegalArgumentException("The shape given to minus() cannot subtract primitives itself");
        List<Primitive> all = new ArrayList<>(plain("minus").primitives);
        for (Primitive p : cut.primitives) all.add(p.cut());
        return new TerrainShape(all, 0, 1);
    }

    /**
     * Copy moved by {@code right}, {@code up} and {@code forward} blocks in the instance frame (each offset of the
     * result within ±{@value #MAX_OFFSET}). The rough edge stays.
     */
    public TerrainShape offset(float right, float up, float forward) {
        if (!Float.isFinite(right) || !Float.isFinite(up) || !Float.isFinite(forward))
            throw new IllegalArgumentException("Offsets must be finite");
        List<Primitive> moved = new ArrayList<>(primitives.size());
        for (Primitive p : primitives) moved.add(p.moved(-right, up, forward));
        return new TerrainShape(moved, roughness, roughScale);
    }

    /**
     * Copy of a single crater with another depth profile exponent, 1..16: 1 a cone, 2 a parabola (default), 8 or more a
     * flat bottom with steep walls.
     */
    public TerrainShape profile(float exponent) {
        Primitive p = onlyCrater("profile");
        if (!Float.isFinite(exponent) || exponent < 1 || exponent > 16)
            throw new IllegalArgumentException("Expected a profile exponent in [1, 16], got " + exponent);
        return new TerrainShape(List.of(new Primitive(Kind.CRATER, false, p.x(), p.y(), p.z(), p.a(), p.b(), exponent, p.d())),
            roughness, roughScale);
    }

    /** Copy of a single crater that also carves up to {@code blocks} above the origin, 0..{@value #MAX_SIZE}. */
    public TerrainShape clearance(float blocks) {
        Primitive p = onlyCrater("clearance");
        if (!Float.isFinite(blocks) || blocks < 0 || blocks > MAX_SIZE)
            throw new IllegalArgumentException("Expected a clearance in [0, " + MAX_SIZE + "], got " + blocks);
        return new TerrainShape(List.of(new Primitive(Kind.CRATER, false, p.x(), p.y(), p.z(), p.a(), p.b(), p.c(), blocks)),
            roughness, roughScale);
    }

    /**
     * Copy with a rough edge: the surface moves in and out by up to {@code amplitude} blocks (0..{@value #MAX_ROUGHNESS};
     * 0 removes it) following two octaves of value noise whose cells are {@code scale} blocks wide (0.5..64). The noise
     * comes from the instance seed, so each instance has its own edge, identical on the server and every client.
     */
    public TerrainShape rough(float amplitude, float scale) {
        if (!Float.isFinite(amplitude) || amplitude < 0 || amplitude > MAX_ROUGHNESS)
            throw new IllegalArgumentException("Expected a roughness in [0, " + MAX_ROUGHNESS + "], got " + amplitude);
        if (!Float.isFinite(scale) || scale < .5f || scale > 64)
            throw new IllegalArgumentException("Expected a noise scale in [0.5, 64], got " + scale);
        return new TerrainShape(primitives, amplitude, amplitude == 0 ? 1 : scale);
    }

    private Primitive onlyCrater(String what) {
        if (primitives.size() != 1 || primitives.get(0).kind() != Kind.CRATER)
            throw new IllegalArgumentException(what + "() applies to a single crater; call it before union/minus/offset");
        return primitives.get(0);
    }

    private TerrainShape plain(String what) {
        if (roughness > 0) throw new IllegalArgumentException(what + "() needs shapes without rough(); apply rough() to the result");
        return this;
    }

    /** Primitives in order: the added ones and the subtracted ones (immutable). */
    public List<Primitive> primitives() { return primitives; }
    /** Rough edge amplitude, blocks (0 for a smooth edge). */
    public float roughness() { return roughness; }
    /** Rough edge noise cell size, blocks (1 when smooth). */
    public float roughScale() { return roughScale; }

    /** Radius around the origin that contains the whole shape, rough edge included, blocks. */
    public double extent() {
        double extent = 0;
        for (Primitive p : primitives) {
            if (p.subtract()) continue;
            double reach = Math.sqrt((double) p.x() * p.x() + (double) p.y() * p.y() + (double) p.z() * p.z()) + p.bound();
            extent = Math.max(extent, reach);
        }
        return extent + roughness;
    }

    /**
     * Signed distance of a local point (blocks, instance frame) to the shape without the rough edge: the union of the
     * added primitives minus the subtracted ones.
     */
    public double baseDistance(double x, double y, double z) {
        double added = Double.POSITIVE_INFINITY, cut = Double.POSITIVE_INFINITY;
        for (Primitive p : primitives) {
            double d = p.distance(x, y, z);
            if (p.subtract()) cut = Math.min(cut, d);
            else added = Math.min(added, d);
        }
        return cut == Double.POSITIVE_INFINITY ? added : Math.max(added, -cut);
    }

    /**
     * Signed distance of a local point (blocks, instance frame) to the shape, rough edge included: negative inside. The
     * same instance seed always gives the same value (the client heat shader uses the same formula).
     */
    public double distance(double x, double y, double z, long seed) {
        double base = baseDistance(x, y, z);
        return roughness == 0 ? base : base - roughness * edge(x, y, z, seed);
    }

    /** Rough edge term in [-1, 1] at a local point: {@code 2 fbm(p / scale) - 1}. Multiply by {@link #roughness()}. */
    public double edge(double x, double y, double z, long seed) {
        double s = 1.0 / roughScale;
        return 2 * TerrainNoise.fbm(x * s, y * s, z * s, TerrainNoise.seed32(seed)) - 1;
    }

    /** Same primitives and rough edge. */
    @Override public boolean equals(Object o) {
        return o instanceof TerrainShape other && primitives.equals(other.primitives) && roughness == other.roughness
            && roughScale == other.roughScale;
    }

    /** Hash of the primitives and the rough edge. */
    @Override public int hashCode() { return Objects.hash(primitives, roughness, roughScale); }

    /** Readable form with the primitives and the rough edge. */
    @Override public String toString() {
        return "TerrainShape" + Arrays.toString(primitives.toArray()) + (roughness > 0 ? " rough " + roughness + "/" + roughScale : "");
    }
}
