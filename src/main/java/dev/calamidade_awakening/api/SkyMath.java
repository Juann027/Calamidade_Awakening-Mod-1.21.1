package dev.calamidade_awakening.api;

import dev.calamidade_awakening.api.v1.Sky;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure rules of the engine's sky pass (0.12.0), shared by the client and checkBudget: which layers are painted, the
 * frame of the vanilla sun and moon quads (read from {@code LevelRenderer.renderSky} with javap: rotation
 * {@code Ry(-90°)·Rx(timeOfDay·360°)}, sun quad at y = 100 with half size 30, moon quad at y = -100 with half size 20,
 * both added to the sky with {@code SRC_ALPHA, ONE}), and the world darkening with its accessibility rules. Not public
 * API.
 */
public final class SkyMath {
    /** Rows of the sky data texture: no profile paints more layers than this. */
    public static final int MAX_LAYERS = 8;
    /** Distance of the vanilla sun and moon quads from the camera, blocks. */
    public static final float VANILLA_DISTANCE = 100f;
    /** Half size of the vanilla sun quad, blocks at {@link #VANILLA_DISTANCE}. */
    public static final float SUN_HALF = 30f;
    /** Half size of the vanilla moon quad, blocks at {@link #VANILLA_DISTANCE}. */
    public static final float MOON_HALF = 20f;
    /** Darkest the world gets under a layer that asks for full darkening: exposure 1 - 0.85. */
    public static final float MAX_DARKENING = .85f;

    private SkyMath() {}

    /** Most layers painted per frame for a quality tier (the ULTRA profile paints {@link #MAX_LAYERS}). */
    public static int layers(Quality tier) {
        return switch (tier) {
            case LOW -> 2;
            case BALANCED -> 4;
            case HIGH -> 6;
        };
    }

    /** Most layers whose gravitational lens is applied per frame: 1 within {@code layers} up to 2, otherwise 2. */
    public static int lenses(int layers) { return layers <= 2 ? 1 : 2; }

    /** Shadow radius of a black hole over its angular radius (sky.fsh). */
    public static final double SHADOW = .2;

    /** Einstein radius (radians) of a black hole of angular radius {@code radius} (radians) and lens strength 0..1. */
    public static double einstein(double radius, double strength) { return radius * SHADOW * (1.05 + 2.2 * strength); }

    /** Extent of the lens (radians) beyond which nothing bends; the bending fades out smoothly toward it. */
    public static double lensExtent(double radius, double strength) { return radius * (2.2 + 2.0 * strength); }

    /**
     * One layer competing for a frame: {@code hero} for layers of HERO effects, {@code weight} its resolved weight,
     * {@code order} the instance order (older first on ties) and {@code rank} (0.12.0): 1000 + priority for global events,
     * 500 for the dimension's sky profile, 0 for ordinary effects, so a global event (an eclipse for the whole server)
     * is painted even when the profile cap is small.
     */
    public record Candidate<T>(T value, boolean hero, float weight, long order, int rank) {
        /** Candidate of an ordinary effect (rank 0). */
        public Candidate(T value, boolean hero, float weight, long order) { this(value, hero, weight, order, 0); }
    }

    /**
     * Layers painted this frame: those with weight above {@link Sky#ACTIVE_WEIGHT}, global events first, then the dimension's
     * profile (by rank), then HERO, then by weight (largest first), then the oldest instance; at most {@code max} (clamped to 0..{@link #MAX_LAYERS}).
     */
    public static <T> List<T> select(List<Candidate<T>> candidates, int max) {
        int cap = Math.clamp(max, 0, MAX_LAYERS);
        List<Candidate<T>> sorted = new ArrayList<>();
        for (Candidate<T> c : candidates) if (c.weight() > Sky.ACTIVE_WEIGHT) sorted.add(c);
        sorted.sort(Comparator.<Candidate<T>>comparingInt(c -> -c.rank())
            .thenComparingInt(c -> c.hero() ? 0 : 1)
            .thenComparing(Comparator.<Candidate<T>>comparingDouble(Candidate::weight).reversed())
            .thenComparingLong(Candidate::order));
        List<T> out = new ArrayList<>(Math.min(cap, sorted.size()));
        for (Candidate<T> c : sorted) {
            if (out.size() >= cap) break;
            out.add(c.value());
        }
        return out;
    }

    /**
     * Columns of the vanilla sky frame for a time of day ({@code ClientLevel.getTimeOfDay}, 0..1, 0 = noon): the local
     * x, y and z axes in world axes, 9 numbers. The sun is along +y (local), the moon along -y. Equal to
     * {@code new Matrix3f().rotateY(-π/2).rotateX(timeOfDay·2π)}.
     */
    public static double[] vanillaAxes(double timeOfDay) {
        double a = timeOfDay * Math.PI * 2, s = Math.sin(a), c = Math.cos(a);
        return new double[] {0, 0, 1, -s, c, 0, -c, -s, 0};
    }

    /** Where sun layers go in a dimension without a vanilla sun (NaN time of day): azimuth 0, elevation 50. */
    public static final Sky.Direction NO_SUN = Sky.Direction.of(0, 50);
    /** Where moon layers go in a dimension without a vanilla moon: azimuth 180, elevation 50. */
    public static final Sky.Direction NO_MOON = Sky.Direction.of(180, 50);

    /**
     * Direction of the vanilla sun for a time of day (unit, world axes): {@code (-sin a, cos a, 0)}. NaN means the
     * dimension draws no vanilla sun (sky type other than NORMAL: the Nether, the End): {@link #NO_SUN}, so layers on
     * the sun (a global eclipse) are still seen there.
     */
    public static Sky.Direction sunDirection(double timeOfDay) {
        if (Double.isNaN(timeOfDay)) return NO_SUN;
        double a = timeOfDay * Math.PI * 2;
        return new Sky.Direction(-Math.sin(a), Math.cos(a), 0);
    }

    /** Direction of the vanilla moon: opposite the sun. */
    public static Sky.Direction moonDirection(double timeOfDay) {
        if (Double.isNaN(timeOfDay)) return NO_MOON;
        var sun = sunDirection(timeOfDay);
        return new Sky.Direction(-sun.x(), -sun.y(), -sun.z());
    }

    /**
     * Texture coordinate (u, v in 0..1) of the vanilla sun quad seen along a world direction, or null when the
     * direction misses it. Same mapping as the game: the corner (-30, 100, -30) has uv (0, 0) and (30, 100, 30) has
     * (1, 1).
     */
    public static double[] sunUv(double[] axes, double x, double y, double z) {
        double lx = axes[0] * x + axes[1] * y + axes[2] * z;
        double ly = axes[3] * x + axes[4] * y + axes[5] * z;
        double lz = axes[6] * x + axes[7] * y + axes[8] * z;
        if (ly <= 1e-6) return null;
        double t = VANILLA_DISTANCE / ly, px = lx * t, pz = lz * t;
        if (Math.abs(px) > SUN_HALF || Math.abs(pz) > SUN_HALF) return null;
        return new double[] {(px + SUN_HALF) / (2 * SUN_HALF), (pz + SUN_HALF) / (2 * SUN_HALF)};
    }

    /**
     * Texture coordinate of the vanilla moon quad (atlas of 4 × 2 phases) seen along a world direction, or null when
     * it misses: the corner (20, -100, 20) has the phase's (u0, v1), (-20, -100, -20) its (u1, v0).
     */
    public static double[] moonUv(double[] axes, int phase, double x, double y, double z) {
        double lx = axes[0] * x + axes[1] * y + axes[2] * z;
        double ly = axes[3] * x + axes[4] * y + axes[5] * z;
        double lz = axes[6] * x + axes[7] * y + axes[8] * z;
        if (ly >= -1e-6) return null;
        double t = -VANILLA_DISTANCE / ly, px = lx * t, pz = lz * t;
        if (Math.abs(px) > MOON_HALF || Math.abs(pz) > MOON_HALF) return null;
        int column = Math.floorMod(phase, 4), row = Math.floorMod(phase / 4, 2);
        return new double[] {(column + (MOON_HALF - px) / (2 * MOON_HALF)) / 4, (row + (pz + MOON_HALF) / (2 * MOON_HALF)) / 2};
    }

    /**
     * Local frame of the sky around {@code center}, as {@code caSkyBasis} in calamidade_fx.glsl: the columns right, up
     * and center (9 numbers), "up" toward the zenith (toward the south when the center is vertical), turned by
     * {@code roll} radians counterclockwise as seen by whoever looks at the center.
     */
    public static double[] basis(Sky.Direction center, double roll) {
        double cx = center.x(), cy = center.y(), cz = center.z();
        boolean vertical = Math.abs(cy) > .999;
        // right = cross(center, ref): ref (0, 0, 1) for a vertical center, (0, 1, 0) otherwise.
        double rx = vertical ? cy : -cz, ry = vertical ? -cx : 0, rz = vertical ? 0 : cx;
        double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rl; ry /= rl; rz /= rl;
        double ux = ry * cz - rz * cy, uy = rz * cx - rx * cz, uz = rx * cy - ry * cx;   // cross(right, center)
        double c = Math.cos(roll), s = Math.sin(roll);
        return new double[] {rx * c + ux * s, ry * c + uy * s, rz * c + uz * s,
            ux * c - rx * s, uy * c - ry * s, uz * c - rz * s, cx, cy, cz};
    }

    /** Direction whose local coordinates (radians, azimuthal equidistant) are (x, y) in {@code basis}, as {@code caSkyFromLocal}. */
    public static Sky.Direction fromLocal(double[] basis, double x, double y) {
        double angle = Math.sqrt(x * x + y * y);
        if (angle < 1e-9) return new Sky.Direction(basis[6], basis[7], basis[8]);
        double nx = x / angle, ny = y / angle, c = Math.cos(angle), s = Math.sin(angle);
        return new Sky.Direction(basis[6] * c + (basis[0] * nx + basis[3] * ny) * s,
            basis[7] * c + (basis[1] * nx + basis[4] * ny) * s, basis[8] * c + (basis[2] * nx + basis[5] * ny) * s);
    }

    /** Local coordinates (radians) of {@code dir} in {@code basis}, as {@code caSkyLocal}: length = angle to the center. */
    public static double[] local(double[] basis, Sky.Direction dir) {
        double px = dir.x() * basis[0] + dir.y() * basis[1] + dir.z() * basis[2];
        double py = dir.x() * basis[3] + dir.y() * basis[4] + dir.z() * basis[5];
        double len = Math.sqrt(px * px + py * py);
        double angle = Math.atan2(len, dir.x() * basis[6] + dir.y() * basis[7] + dir.z() * basis[8]);
        return len > 1e-12 ? new double[] {px / len * angle, py / len * angle} : new double[] {angle, 0};
    }

    /** Most distant bolts of one layer drawn at once. */
    public static final int MAX_BOLTS = 4;
    /** Nearest and farthest distance of a distant bolt from the player, blocks. */
    public static final double BOLT_NEAR = 70, BOLT_FAR = 300;
    /** Grid (blocks) the bolt positions are anchored to, so a bolt does not slide while the player walks. */
    public static final double BOLT_CELL = 32;

    /**
     * One distant bolt: world position of its foot ({@code x}, {@code z}, blocks), when it struck and how long it lives
     * (seconds of the instance's real age), its own seed (0..1, the shape) and its envelope now (0..1).
     */
    public record Bolt(double x, double z, double start, double life, float seed, float envelope) {}

    /**
     * Distant bolts of a {@code DISTANT_BOLTS} layer at {@code seconds} of real age, pure and deterministic: the same
     * seed and time give the same strikes on every client. Strikes come in slots of {@code 1 / (2 × activity)} seconds
     * (about 15% of the slots stay empty, for an irregular rhythm); each lives 0.35 to 0.65 s. A bolt falls in the
     * sector centered on {@code azimuth} (degrees) with {@code width} degrees (180 or more: all around), between
     * {@value #BOLT_NEAR} and {@value #BOLT_FAR} blocks from the player position snapped to {@value #BOLT_CELL} blocks.
     * {@code calm} (hide lightning flashes) gives a smooth envelope without restrikes. At most {@link #MAX_BOLTS}.
     */
    public static List<Bolt> bolts(long seed, double seconds, float activity, double azimuth, double width,
                                   double playerX, double playerZ, boolean calm) {
        List<Bolt> out = new ArrayList<>(MAX_BOLTS);
        if (!(activity > 0) || !Double.isFinite(seconds)) return out;
        double rate = 2.0 * Math.min(activity, 1);
        long now = (long) Math.floor(seconds * rate);
        double cx = Math.floor(playerX / BOLT_CELL) * BOLT_CELL, cz = Math.floor(playerZ / BOLT_CELL) * BOLT_CELL;
        for (long slot = now; slot >= now - 3 && out.size() < MAX_BOLTS; slot--) {
            if (hash(seed, slot, 0) < .15) continue;
            double start = (slot + .8 * hash(seed, slot, 1)) / rate;
            double life = .35 + .3 * hash(seed, slot, 2);
            double age = seconds - start;
            if (age < 0 || age >= life) continue;
            double sector = Math.min(width, 360);
            double az = width >= 180 ? hash(seed, slot, 3) * 360 : azimuth + (hash(seed, slot, 3) - .5) * sector;
            double distance = BOLT_NEAR + (BOLT_FAR - BOLT_NEAR) * hash(seed, slot, 4);
            var dir = Sky.Direction.of(az, 0);
            out.add(new Bolt(cx + dir.x() * distance, cz + dir.z() * distance, start, life, (float) hash(seed, slot, 5),
                boltEnvelope(age / life, calm)));
        }
        return out;
    }

    /**
     * Brightness of a bolt over its life ({@code x} 0..1): a flash at the start, two restrikes and a fall; with
     * {@code calm}, a smooth rise and fall without restrikes, at 60%.
     */
    public static float boltEnvelope(double x, boolean calm) {
        double t = Math.clamp(x, 0, 1);
        if (calm) return (float) (.6 * Math.sin(Math.PI * t));
        double fall = (1 - t) * (1 - t);
        double restrike = .55 + .45 * Math.max(Math.cos(t * Math.PI * 7), 0);
        return (float) Math.clamp(fall * restrike * Math.min(1, t * 40), 0, 1);
    }

    private static double hash(long seed, long slot, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + slot * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33; h *= 0xc4ceb9fe1a85ec53L; h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /**
     * World exposure of a darkening request (0..1, the largest {@code State.darkening()} of the frame) after the
     * screen-effects intensity: {@code 1 - }{@value #MAX_DARKENING}{@code  × darkening × intensity}. At intensity 0 the
     * world is never darkened.
     */
    public static float exposure(float darkening, float intensity) {
        float d = Float.isNaN(darkening) ? 0 : Math.clamp(darkening, 0f, 1f);
        return 1 - MAX_DARKENING * d * Math.clamp(intensity, 0f, 1f);
    }

    /**
     * Displayed world exposure after one frame: the target right away, or, while the player hides lightning flashes,
     * moved toward it by at most {@link AtmosphereMath#CALM_EXPOSURE_PER_TICK} per tick (the atmosphere's rule).
     */
    public static float displayed(float current, float target, float elapsedTicks, boolean calm) {
        return calm ? AtmosphereMath.approach(current, target, elapsedTicks, AtmosphereMath.CALM_EXPOSURE_PER_TICK) : target;
    }
}
