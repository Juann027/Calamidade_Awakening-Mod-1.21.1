package dev.calamidade_awakening.api;

import dev.calamidade_awakening.api.v1.Nebula;
import dev.calamidade_awakening.api.v1.SkyBlend;
import dev.calamidade_awakening.api.v1.SkyDarkening;
import dev.calamidade_awakening.api.v1.StarField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Pure math of the sky objects (0.14.0, phase 3: {@link StarField} and {@link Nebula}): the stars a field produces
 * (deterministic from its seed, brightest first), magnitudes, sizes, constellation segments, the per-profile caps, which
 * objects a frame draws and in what order, the object frame (celestial turn and rotation), and the composition and
 * reaction rules that the shaders {@code sky_stars} and {@code sky_nebula} apply (repeated here so they can be tested
 * without a GPU). Not public API.
 *
 * <p>Directions are world axes (x east, y up, z south), unit length.
 */
public final class SkyObjectsMath {
    /** Sky objects compete on one rank scale with the backgrounds and layers: global events, then the dimension's profile. */
    public static final int RANK_EVENT = 1000, RANK_PROFILE = 500, RANK_EFFECT = 0;

    private SkyObjectsMath() {}

    // ------------------------------------------------------------------------------------------------ brightness

    /**
     * Brightness of a magnitude: {@code 10^(−0.2 (m − 1))}, clamped to 0..32. The real scale is {@code 10^(−0.4 m)};
     * the softer slope keeps magnitude 6 visible on an 8-bit screen (0.1) while magnitude 1 is 1 and −1.5 about 3.2.
     */
    public static float intensity(float magnitude) {
        return (float) Math.clamp(Math.pow(10, -0.2 * (magnitude - 1)), 0, 32);
    }

    /**
     * Angular half size of a star, radians: {@code 0.045° × size × (0.8 + 0.5 √min(I, 8))}. On screen it never goes
     * below about one pixel (the shader dims it instead, by the square root of the size ratio: smaller stars get
     * dimmer, not thinner).
     */
    public static float halfSize(float intensity, float size) {
        return (float) Math.toRadians(0.045 * size * (0.8 + 0.5 * Math.sqrt(Math.min(Math.max(intensity, 0), 8))));
    }

    // ------------------------------------------------------------------------------------------------ stars

    /**
     * One star ready for the vertex buffer.
     *
     * @param x,y,z     unit direction in the field's frame (as seen at midnight)
     * @param intensity brightness, 0..32 (alpha of the color and the field brightness not included)
     * @param argb      color
     * @param halfSize  angular half size, radians
     * @param phase     twinkle phase, 0..1
     */
    public record Star(float x, float y, float z, float intensity, int argb, float halfSize, float phase) {}

    /**
     * One constellation segment: unit directions of its ends, its color (alpha: opacity) and half width (radians).
     */
    public record Segment(double[] from, double[] to, int argb, float halfWidth) {}

    /** The stars of a field, brightest first (ties keep the generation order). Deterministic for a given field. */
    public static List<Star> stars(StarField field) {
        List<Star> out = field.kind() == StarField.Kind.RANDOM ? randomStars(field) : catalogStars(field);
        List<Star> sorted = new ArrayList<>(out);
        sorted.sort(Comparator.comparingDouble((Star s) -> -s.intensity() * ((s.argb() >>> 24) / 255f)));
        return sorted;
    }

    private static List<Star> catalogStars(StarField field) {
        List<Star> out = new ArrayList<>(field.stars().size());
        long h = 0x9E3779B97F4A7C15L;
        for (var s : field.stars()) {
            double[] d = direction(s.azimuth(), s.elevation());
            float i = intensity(s.magnitude());
            h = mix(h + Double.doubleToLongBits(s.azimuth() * 31 + s.elevation()));
            out.add(new Star((float) d[0], (float) d[1], (float) d[2], i, s.argb(), halfSize(i, field.size()), unit(h)));
        }
        return out;
    }

    private static List<Star> randomStars(StarField field) {
        Rng rng = new Rng(field.seed());
        int n = field.count();
        List<Star> out = new ArrayList<>(n);
        double[] pole = direction(field.bandAzimuth(), field.bandElevation());
        double[][] circle = basis(pole);
        double[] center = direction(field.regionAzimuth(), field.regionElevation());
        double cosRegion = Math.cos(Math.toRadians(field.regionRadius()));
        boolean region = field.regionRadius() < 180;
        float[] weights = field.colorWeights();
        int[] colors = field.colors();
        float total = 0;
        for (float w : weights) total += w;
        double span = Math.pow(10, 0.6 * (field.faintest() - field.brightest())) - 1;
        for (int k = 0; k < n; k++) {
            double[] d = null;
            for (int attempt = 0; attempt < 32 && d == null; attempt++) {
                double[] c = field.bandWidth() > 0 && rng.next() < field.bandFraction()
                    ? bandDirection(rng, circle, Math.toRadians(field.bandWidth()))
                    : sphereDirection(rng);
                if (!region || dot(c, center) >= cosRegion) d = c;
            }
            if (d == null) d = capDirection(rng, center, cosRegion);
            // Magnitude: truncated N(<m) ∝ 10^(0.6 m), so about four times more stars per fainter magnitude.
            float m = (float) (field.brightest() + Math.log10(1 + rng.next() * span) / 0.6);
            if (span <= 0) m = field.brightest();
            float pick = (float) rng.next() * total;
            int color = colors[colors.length - 1];
            for (int c = 0; c < colors.length; c++) {
                pick -= weights[c];
                if (pick <= 0) { color = colors[c]; break; }
            }
            float i = intensity(m);
            out.add(new Star((float) d[0], (float) d[1], (float) d[2], i, color, halfSize(i, field.size()), (float) rng.next()));
        }
        return out;
    }

    /** Constellation segments of a catalog (empty for a procedural field). */
    public static List<Segment> segments(StarField field) {
        if (field.lines().isEmpty()) return List.of();
        List<Segment> out = new ArrayList<>(field.lines().size());
        float half = (float) Math.toRadians(field.lineWidth() / 2);
        for (var line : field.lines()) {
            var a = field.stars().get(line.from());
            var b = field.stars().get(line.to());
            out.add(new Segment(direction(a.azimuth(), a.elevation()), direction(b.azimuth(), b.elevation()), line.argb(), half));
        }
        return out;
    }

    /** Pieces a segment is split into so it follows the great circle (one every 2 degrees, 1..90). */
    public static int pieces(Segment s) {
        double angle = Math.acos(Math.clamp(dot(s.from(), s.to()), -1, 1));
        return (int) Math.clamp(Math.ceil(Math.toDegrees(angle) / 2), 1, 90);
    }

    /** Point at {@code t} (0..1) along the great circle of a segment (spherical interpolation). */
    public static double[] along(Segment s, double t) {
        double[] a = s.from(), b = s.to();
        double angle = Math.acos(Math.clamp(dot(a, b), -1, 1));
        if (angle < 1e-6) return a.clone();
        double sa = Math.sin((1 - t) * angle) / Math.sin(angle), sb = Math.sin(t * angle) / Math.sin(angle);
        return normalize(new double[] {a[0] * sa + b[0] * sb, a[1] * sa + b[1] * sb, a[2] * sa + b[2] * sb});
    }

    // ------------------------------------------------------------------------------------------------ caps

    /**
     * Per-frame caps of a quality profile: star fields drawn, stars drawn (the brightest of each field first), nebulae
     * drawn and the nebula noise octaves. Starting points measured by the in-game test (docs/CEU-PERSONALIZADO.md).
     */
    public record Caps(int fields, int stars, int nebulae, int octaves) {}

    /** Caps of a profile by its name (LOW, BALANCED, HIGH, ULTRA; the internal test profile lifts them). */
    public static Caps caps(FrameLimits limits) {
        if (limits.test()) return new Caps(8, 262144, 8, 5);
        return switch (limits.name()) {
            case "LOW" -> new Caps(2, 4000, 2, 3);
            case "BALANCED" -> new Caps(3, 16000, 3, 4);
            case "ULTRA" -> new Caps(6, 131072, 6, 5);
            default -> new Caps(4, 40000, 4, 5);
        };
    }

    /**
     * Stars drawn of each field, in importance order: each takes what it has, up to what is left of {@code budget}
     * (the faintest stars of the less important fields go first).
     */
    public static int[] share(int[] counts, int budget) {
        int[] out = new int[counts.length];
        int left = Math.max(0, budget);
        for (int i = 0; i < counts.length; i++) {
            out[i] = Math.min(Math.max(counts[i], 0), left);
            left -= out[i];
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ selection

    /**
     * A sky object competing for the frame.
     *
     * @param value  what to draw
     * @param hero   from a HERO effect
     * @param weight instance weight (envelope, range fade, profile transition), 0..1
     * @param base   opacity before the weight, 0..1
     * @param order  older instances first on ties
     * @param rank   {@link #RANK_EVENT} + priority, {@link #RANK_PROFILE} or {@link #RANK_EFFECT}
     * @param draw   draw order of the object ({@link StarField#order()}, {@link Nebula#order()})
     */
    public record Candidate<T>(T value, boolean hero, float weight, float base, long order, int rank, int draw) {
        /** Effective opacity: base times weight. */
        public float opacity() { return Math.clamp(base * weight, 0f, 1f); }
    }

    /** Most important first: rank, then HERO, then opacity, then the oldest instance. */
    public static <T> Comparator<Candidate<T>> importance() {
        return Comparator.<Candidate<T>>comparingInt(c -> -c.rank())
            .thenComparingInt(c -> c.hero() ? 0 : 1)
            .thenComparing(Comparator.<Candidate<T>>comparingDouble(Candidate::opacity).reversed())
            .thenComparingLong(Candidate::order);
    }

    /** The visible candidates (opacity above 0.001), most important first, at most {@code max}. */
    public static <T> List<Candidate<T>> select(List<Candidate<T>> candidates, int max) {
        List<Candidate<T>> sorted = new ArrayList<>();
        for (Candidate<T> c : candidates) if (c.opacity() > StarField.ACTIVE_OPACITY) sorted.add(c);
        sorted.sort(importance());
        return sorted.size() > max ? List.copyOf(sorted.subList(0, Math.max(0, max))) : sorted;
    }

    /**
     * Drawing order of selected objects: by draw order (lower first, behind); on equal draw order the less important
     * first, so the most important ends on top.
     */
    public static <T> List<Candidate<T>> drawingOrder(List<Candidate<T>> selected) {
        List<Candidate<T>> out = new ArrayList<>(selected);
        Comparator<Candidate<T>> importance = importance();
        out.sort(Comparator.<Candidate<T>>comparingInt(Candidate::draw).thenComparing(importance.reversed()));
        return out;
    }

    // ------------------------------------------------------------------------------------------------ frame

    /**
     * Object-to-world rotation, column-major 3 × 3: first the turn around the vertical axis ({@code rotation} degrees,
     * positive moves south toward west, as {@link BackdropMath#turn}), then, when {@code celestial} and the dimension has
     * a day cycle ({@code timeOfDay} not NaN), the turn of the vanilla sky around the east-west axis by
     * {@code 2π (timeOfDay − 0.5)}, so the directions given are the ones seen at midnight.
     */
    public static double[] frame(double timeOfDay, boolean celestial, double rotation) {
        double a = Math.toRadians(rotation), ca = Math.cos(a), sa = Math.sin(a);
        // Ry(-a): x' = x ca - z sa, z' = x sa + z ca.
        double[] turn = {ca, 0, sa, 0, 1, 0, -sa, 0, ca};
        if (!celestial || Double.isNaN(timeOfDay)) return turn;
        double b = 2 * Math.PI * (timeOfDay - 0.5), cb = Math.cos(b), sb = Math.sin(b);
        // Rz(b): x' = x cb - y sb, y' = x sb + y cb.
        double[] sky = {cb, sb, 0, -sb, cb, 0, 0, 0, 1};
        return multiply(sky, turn);
    }

    /** Applies a column-major 3 × 3 rotation to a direction. */
    public static double[] apply(double[] m, double x, double y, double z) {
        return new double[] {m[0] * x + m[3] * y + m[6] * z, m[1] * x + m[4] * y + m[7] * z, m[2] * x + m[5] * y + m[8] * z};
    }

    // ------------------------------------------------------------------------------------------------ composition

    /** Coverage factor of a mode for a texel alpha: NORMAL alpha, LUMINOUS 0, COVER 1 where alpha ≥ 0.5 (else 0). */
    public static float coverage(SkyBlend blend, float alpha) {
        return switch (blend) {
            case NORMAL -> alpha;
            case LUMINOUS -> 0;
            case COVER -> alpha >= .5f ? 1 : 0;
        };
    }

    /**
     * Premultiplied pixel an object brings (rgb light, a coverage) for a color, a texel alpha, an opacity and a light
     * gain (brightness × visibility): NORMAL brings {@code color × alpha × opacity × gain} with coverage
     * {@code alpha × opacity}; LUMINOUS the same light with no coverage; COVER {@code color × opacity × gain} with
     * coverage {@code opacity} inside the silhouette.
     */
    public static float[] premultiplied(SkyBlend blend, float[] rgb, float alpha, float opacity, float gain) {
        float shown = blend == SkyBlend.COVER ? coverage(blend, alpha) : alpha;
        float a = coverage(blend, alpha) * opacity;
        float k = shown * opacity * gain;
        return new float[] {rgb[0] * k, rgb[1] * k, rgb[2] * k, a};
    }

    /** {@code behind × (1 − a) + rgb}: what the sky shows after one premultiplied object (each channel). */
    public static float[] over(float[] behind, float[] pixel) {
        return new float[] {behind[0] * (1 - pixel[3]) + pixel[0], behind[1] * (1 - pixel[3]) + pixel[1],
            behind[2] * (1 - pixel[3]) + pixel[2]};
    }

    // ------------------------------------------------------------------------------------------------ reactions

    /**
     * How visible daylight leaves an object (0..1): {@code 1 − daylight × (1 − night)}, with {@code night} the vanilla
     * star visibility (2 × {@code getStarBrightness} × (1 − rain), clamped; 1 in dimensions without a day cycle).
     */
    public static float dayVisibility(float daylight, float night) {
        return 1 - Math.clamp(daylight, 0f, 1f) * (1 - Math.clamp(night, 0f, 1f));
    }

    /**
     * Light gain of an object under the world darkening of sky layers. {@code exposure} is the exposure the sky pass
     * applies to the world (1 none, 0.15 deepest), {@code day} the {@link #dayVisibility}. DIM follows the world
     * ({@code mix(1, exposure, strength)} × day); REVEAL lifts what daylight hid by the darkness
     * ({@code 1 − (1 − day)(1 − strength × darkness)}, darkness = (1 − exposure) / 0.85); IGNORE keeps the day value.
     */
    public static float darkeningGain(SkyDarkening mode, float strength, float exposure, float day) {
        float s = Math.clamp(strength, 0f, 1f), e = Math.clamp(exposure, 0f, 1f);
        return switch (mode) {
            case DIM -> day * (1 + (e - 1) * s);
            case REVEAL -> 1 - (1 - day) * (1 - s * Math.clamp((1 - e) / .85f, 0f, 1f));
            case IGNORE -> day;
        };
    }

    /**
     * Twinkle factor of a star: {@code 1 − amount × (0.5 + 0.25 sin(t·speed·2π·(0.7 + 0.6·phase) + 2π·phase)
     * + 0.25 sin(t·speed·2π·1.7 + 4π·phase))}, between {@code 1 − amount} and 1.
     */
    public static float twinkle(float amount, float speed, float phase, double seconds) {
        double w = seconds * speed * 2 * Math.PI;
        double v = 0.5 + 0.25 * Math.sin(w * (0.7 + 0.6 * phase) + 2 * Math.PI * phase) + 0.25 * Math.sin(w * 1.7 + 4 * Math.PI * phase);
        return (float) (1 - amount * v);
    }

    /** Accessibility scale of the twinkle: screen effect intensity, a quarter with "hide lightning flashes". */
    public static float twinkleScale(float screenEffects, boolean hideFlashes) {
        return Math.clamp(screenEffects, 0f, 1f) * (hideFlashes ? .25f : 1f);
    }

    // ------------------------------------------------------------------------------------------------ geometry

    /** Unit direction of an azimuth and an elevation, degrees, like {@code Sky.Direction.of}. */
    public static double[] direction(double azimuth, double elevation) {
        double az = Math.toRadians(azimuth), el = Math.toRadians(Math.clamp(elevation, -90, 90));
        return new double[] {-Math.sin(az) * Math.cos(el), Math.sin(el), Math.cos(az) * Math.cos(el)};
    }

    /** Two unit vectors perpendicular to {@code axis} and to each other ({@code [u, v]}). */
    public static double[][] basis(double[] axis) {
        double[] helper = Math.abs(axis[1]) < .9 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
        double[] u = normalize(cross(helper, axis));
        return new double[][] {u, cross(axis, u)};
    }

    private static double[] sphereDirection(Rng rng) {
        double z = 2 * rng.next() - 1, phi = 2 * Math.PI * rng.next(), r = Math.sqrt(Math.max(0, 1 - z * z));
        return new double[] {r * Math.cos(phi), z, r * Math.sin(phi)};
    }

    private static double[] bandDirection(Rng rng, double[][] circle, double halfWidth) {
        double theta = 2 * Math.PI * rng.next();
        double off = Math.clamp(rng.gaussian() * halfWidth * 0.6, -Math.PI / 2, Math.PI / 2);
        double c = Math.cos(off), s = Math.sin(off);
        double[] u = circle[0], v = circle[1], pole = cross(u, v);
        double cu = Math.cos(theta) * c, cv = Math.sin(theta) * c;
        return normalize(new double[] {u[0] * cu + v[0] * cv + pole[0] * s, u[1] * cu + v[1] * cv + pole[1] * s,
            u[2] * cu + v[2] * cv + pole[2] * s});
    }

    private static double[] capDirection(Rng rng, double[] center, double cosRadius) {
        double cosT = 1 - rng.next() * (1 - cosRadius), sinT = Math.sqrt(Math.max(0, 1 - cosT * cosT));
        double phi = 2 * Math.PI * rng.next();
        double[][] b = basis(center);
        double a = Math.cos(phi) * sinT, c = Math.sin(phi) * sinT;
        return normalize(new double[] {b[0][0] * a + b[1][0] * c + center[0] * cosT, b[0][1] * a + b[1][1] * c + center[1] * cosT,
            b[0][2] * a + b[1][2] * c + center[2] * cosT});
    }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Unit vector of {@code a} (straight up when {@code a} is zero). */
    public static double[] unit(double[] a) { return normalize(a); }

    static double[] normalize(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return l < 1e-12 ? new double[] {0, 1, 0} : new double[] {a[0] / l, a[1] / l, a[2] / l};
    }

    private static double[] multiply(double[] a, double[] b) {
        double[] out = new double[9];
        for (int c = 0; c < 3; c++) for (int r = 0; r < 3; r++)
            out[c * 3 + r] = a[r] * b[c * 3] + a[3 + r] * b[c * 3 + 1] + a[6 + r] * b[c * 3 + 2];
        return out;
    }

    /** Seed of a nebula's noise as a shader offset (0..1000, stable for a seed). */
    public static float noiseOffset(long seed) { return unit(mix(seed ^ 0x5DEECE66DL)) * 1000f; }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float unit(long h) { return (h >>> 40) / (float) (1L << 24); }

    /** SplitMix64: the same stars on every JVM for a seed (java.util.Random would do too; this is explicit). */
    private static final class Rng {
        private long state;
        private double spare = Double.NaN;

        Rng(long seed) { state = seed; }

        double next() {
            state += 0x9E3779B97F4A7C15L;
            return (mix(state) >>> 11) * 0x1.0p-53;
        }

        double gaussian() {
            if (!Double.isNaN(spare)) { double s = spare; spare = Double.NaN; return s; }
            double u = Math.max(next(), 1e-12), v = next();
            double r = Math.sqrt(-2 * Math.log(u));
            spare = r * Math.sin(2 * Math.PI * v);
            return r * Math.cos(2 * Math.PI * v);
        }
    }

    /** Debug helper: the stars of a field as a flat array (x, y, z, intensity) — used by tests. */
    public static float[] flat(List<Star> stars) {
        float[] out = new float[stars.size() * 4];
        for (int i = 0; i < stars.size(); i++) {
            Star s = stars.get(i);
            out[i * 4] = s.x(); out[i * 4 + 1] = s.y(); out[i * 4 + 2] = s.z(); out[i * 4 + 3] = s.intensity();
        }
        return Arrays.copyOf(out, out.length);
    }
}
