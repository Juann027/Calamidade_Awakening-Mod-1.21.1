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
    public static final double SHADOW = .34;

    /** Einstein radius (radians) of a black hole of angular radius {@code radius} (radians) and lens strength 0..1. */
    public static double einstein(double radius, double strength) { return radius * SHADOW * (1.05 + 1.6 * strength); }

    /** Extent of the lens (radians) beyond which nothing bends; the bending fades out from 60% of it. */
    public static double lensExtent(double radius, double strength) { return radius * (1.6 + 2.2 * strength); }

    /**
     * One layer competing for a frame: {@code hero} for layers of HERO effects, {@code weight} its resolved weight and
     * {@code order} the instance order (older first on ties).
     */
    public record Candidate<T>(T value, boolean hero, float weight, long order) {}

    /**
     * Layers painted this frame: those with weight above {@link Sky#ACTIVE_WEIGHT}, HERO first, then by weight
     * (largest first), then the oldest instance; at most {@code max} (clamped to 0..{@link #MAX_LAYERS}).
     */
    public static <T> List<T> select(List<Candidate<T>> candidates, int max) {
        int cap = Math.clamp(max, 0, MAX_LAYERS);
        List<Candidate<T>> sorted = new ArrayList<>();
        for (Candidate<T> c : candidates) if (c.weight() > Sky.ACTIVE_WEIGHT) sorted.add(c);
        sorted.sort(Comparator.<Candidate<T>>comparingInt(c -> c.hero() ? 0 : 1)
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

    /** Direction of the vanilla sun for a time of day (unit, world axes): {@code (-sin a, cos a, 0)}. */
    public static Sky.Direction sunDirection(double timeOfDay) {
        double a = timeOfDay * Math.PI * 2;
        return new Sky.Direction(-Math.sin(a), Math.cos(a), 0);
    }

    /** Direction of the vanilla moon: opposite the sun. */
    public static Sky.Direction moonDirection(double timeOfDay) {
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
