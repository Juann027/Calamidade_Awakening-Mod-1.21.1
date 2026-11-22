package dev.calamidade_awakening.api;

import dev.calamidade_awakening.api.v1.Backdrop;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure math of the custom sky backgrounds (0.14.0, {@link Backdrop}): which backgrounds a frame draws and how opaque
 * each one is, the cubemap and panorama texture lookups and the gradient. The client builds its static geometry from
 * these functions and {@code calamidade_sky.glsl} repeats them (the passes need the background color and coverage per
 * direction), so the drawn background and the one the passes compose with are the same.
 *
 * <p>Directions are world axes (x east, y up, z south) and need not be unit length unless stated.
 */
public final class BackdropMath {
    /** Most backgrounds drawn in one frame: the main one and the one it fades over. */
    public static final int MAX_DRAWN = 2;
    /** Cubemap faces in atlas order (3 columns × 2 rows): north, east, south, west, up, down. */
    public static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3, UP = 4, DOWN = 5;
    /** Opacity at or above which a background counts as fully opaque. */
    public static final float OPAQUE = 1 - 1e-3f;

    private BackdropMath() {}

    /**
     * A background competing for the frame.
     *
     * @param value  what to draw
     * @param hero   from a HERO effect
     * @param weight instance weight (envelope, range fade, phase, profile transition), 0..1
     * @param base   opacity of the background before the weight (builder opacity, track, tint alpha), 0..1
     * @param order  older instances first on ties
     * @param rank   global events (1000 + priority), then the dimension's profile (500), then effects (0)
     */
    public record Candidate<T>(T value, boolean hero, float weight, float base, long order, int rank) {
        /** Effective opacity: base times weight. */
        public float opacity() { return Math.clamp(base * weight, 0f, 1f); }
    }

    /** One background to draw and its opacity. */
    public record Drawn<T>(T value, float alpha) {}

    /**
     * Backgrounds of this frame in drawing order (bottom first), at most {@link #MAX_DRAWN}. The most important
     * candidate (rank, then HERO, then opacity, then the oldest) is drawn with its opacity. While it is not opaque,
     * the next one is drawn below it with its base opacity times {@code min(1, its weight + the top weight)}: in a
     * crossfade (weights adding up to 1) the lower one stays at full opacity and the upper one fades over it, so the
     * vanilla sky never shows between two backgrounds; a lone background fading in just shows the vanilla sky below.
     */
    public static <T> List<Drawn<T>> select(List<Candidate<T>> candidates) {
        List<Candidate<T>> sorted = new ArrayList<>();
        for (Candidate<T> c : candidates) if (c.opacity() > Backdrop.ACTIVE_OPACITY) sorted.add(c);
        if (sorted.isEmpty()) return List.of();
        sorted.sort(Comparator.<Candidate<T>>comparingInt(c -> -c.rank())
            .thenComparingInt(c -> c.hero() ? 0 : 1)
            .thenComparing(Comparator.<Candidate<T>>comparingDouble(Candidate::opacity).reversed())
            .thenComparingLong(Candidate::order));
        Candidate<T> top = sorted.get(0);
        float alpha = top.opacity();
        if (alpha >= OPAQUE || sorted.size() == 1) return List.of(new Drawn<>(top.value(), alpha));
        Candidate<T> below = sorted.get(1);
        float under = Math.clamp(below.base() * Math.min(1f, below.weight() + top.weight()), 0f, 1f);
        return List.of(new Drawn<>(below.value(), under), new Drawn<>(top.value(), alpha));
    }

    /** Combined coverage of backgrounds drawn one over another with these opacities (bottom first). */
    public static float coverage(float... alphas) {
        float clear = 1;
        for (float a : alphas) clear *= 1 - Math.clamp(a, 0f, 1f);
        return 1 - clear;
    }

    /**
     * Turns a direction around the vertical axis by {@code degrees} in the azimuth convention (positive moves south
     * toward west). The texture lookup of a background turned by {@code r} uses {@code turn(d, -r)}.
     */
    public static double[] turn(double x, double y, double z, double degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
        return new double[] {x * c - z * s, y, x * s + z * c};
    }

    /** Cubemap face of a direction (largest axis; ties go to x, then y): {@link #NORTH} .. {@link #DOWN}. */
    public static int face(double x, double y, double z) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ax >= ay && ax >= az) return x > 0 ? EAST : WEST;
        if (ay >= az) return y > 0 ? UP : DOWN;
        return z > 0 ? SOUTH : NORTH;
    }

    /**
     * Face and local coordinates (u right, v down, 0..1) of a direction on its cubemap face, in the layout of
     * {@link Backdrop}: side faces seen from inside with the top of the image up; up with north at the top and east on
     * the right; down with south at the top and east on the right.
     */
    public static double[] cubeLocal(double x, double y, double z) {
        int f = face(x, y, z);
        double u, v;
        switch (f) {
            case NORTH -> { double m = -z; u = x / m; v = -y / m; }
            case EAST -> { double m = x; u = z / m; v = -y / m; }
            case SOUTH -> { double m = z; u = -x / m; v = -y / m; }
            case WEST -> { double m = -x; u = -z / m; v = -y / m; }
            case UP -> { double m = y; u = x / m; v = z / m; }
            default -> { double m = -y; u = x / m; v = -z / m; }
        }
        return new double[] {f, (u + 1) / 2, (v + 1) / 2};
    }

    /** Atlas coordinates (0..1, v down) of a cubemap direction: the face's cell in the 3 × 2 PNG. */
    public static double[] cubeUv(double x, double y, double z) {
        double[] l = cubeLocal(x, y, z);
        int f = (int) l[0];
        return new double[] {((f % 3) + Math.clamp(l[1], 0, 1)) / 3, ((f / 3) + Math.clamp(l[2], 0, 1)) / 2};
    }

    /**
     * Panorama coordinates (0..1, v down) of a direction: u = 0.5 + azimuth / 360 (south in the middle, west to the
     * right), v = 0.5 − elevation / 180 (zenith at the top).
     */
    public static double[] panoramaUv(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (!(len > 0)) return new double[] {.5, .5};
        double azimuth = Math.toDegrees(Math.atan2(-x, z));
        double elevation = Math.toDegrees(Math.asin(Math.clamp(y / len, -1, 1)));
        double u = .5 + azimuth / 360;
        return new double[] {u - Math.floor(u), .5 - elevation / 180};
    }

    /** Smoothstep from {@code a} to {@code b}, like GLSL. */
    public static double smooth(double a, double b, double x) {
        double t = Math.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * Gradient color at unit-direction height {@code y} (−1..1), RGBA 0..1: from the horizon to the zenith by
     * smoothstep(0, 0.6, y) above, to the nadir by smoothstep(0, 0.3, −y) below.
     */
    public static float[] gradient(int top, int horizon, int bottom, double y) {
        boolean up = y >= 0;
        double k = up ? smooth(0, .6, y) : smooth(0, .3, -y);
        int other = up ? top : bottom;
        float[] out = new float[4];
        for (int c = 0; c < 4; c++) {
            int shift = c == 3 ? 24 : 16 - 8 * c;
            double a = ((horizon >>> shift) & 0xFF) / 255.0, b = ((other >>> shift) & 0xFF) / 255.0;
            out[c] = (float) (a + (b - a) * k);
        }
        return out;
    }
}
