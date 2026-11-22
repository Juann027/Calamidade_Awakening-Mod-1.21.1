package dev.calamidade_awakening.api.v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable star field of one effect or one {@link SkyProfile.State} (0.14.0): a {@link #random procedural field}
 * (seeded, deterministic: every client sees the same stars) or a {@link #catalog catalog} of named stars with
 * {@linkplain Builder#constellation constellation lines}. Pure Java, no Minecraft types. Nothing is drawn unless an
 * addon attaches a field ({@link Effects#stars}, {@link SkyProfile.StateBuilder#stars}); players can turn custom skies
 * off with the client option {@code customSkies}.
 *
 * <p><b>How it is drawn.</b> The client builds each field once into a static vertex buffer (all its stars and lines in
 * one draw call, brightest stars first) and draws it right after the vanilla sky and the {@link Backdrop}
 * ({@code RenderLevelStageEvent.Stage.AFTER_SKY}), behind terrain, clouds and the engine's {@link Sky} layers (a black
 * hole or an eclipse disc hides the stars behind it, the lens bends them). Quality profiles cap how many fields and
 * stars are drawn per frame (the faintest stars go first), AUTO included. Stars keep at least about one pixel and lose
 * brightness instead of size below that, so a dense field does not sparkle.
 *
 * <p><b>Composition.</b> {@link Builder#blend} picks how the stars meet the sky behind them ({@link SkyBlend}; default
 * {@link SkyBlend#LUMINOUS LUMINOUS}: light added, the background never disappears). Use {@link SkyBlend#NORMAL
 * NORMAL} when exact star colors matter over a bright background. The Atmosphere sky veils the stars as much as
 * {@link Builder#atmosphere} says (1 by default: like the vanilla sky, a dense atmosphere hides them); daylight hides
 * them as much as {@link Builder#daylight} says (1 by default: like the vanilla stars); sky layers that darken the world
 * reveal them by default ({@link SkyDarkening#REVEAL}, an eclipse brings the stars out).
 *
 * <p><b>Directions</b> use azimuth and elevation in degrees like {@link Sky.Direction#of} (azimuth 0 = south,
 * 90 = west). With {@link Builder#celestial(boolean) celestial} on (default) the field turns with the time of day around
 * the same axis as the vanilla sun, and the directions given are the ones seen at midnight; in a dimension without a
 * day cycle the field stays as given.
 *
 * <p><b>Brightness.</b> Magnitudes follow the astronomical scale (smaller is brighter; −1.5 is the brightest star of
 * the night sky, 6 the faintest seen by the eye); the engine compresses them so that magnitude 1 has brightness 1 and
 * magnitude 6 is still visible.
 *
 * <pre>{@code
 * static final StarField CEU = StarField.random(42, 6000).magnitudes(-1, 6).band(90, 30, 14, .5f).build();
 * static final StarField CARRO = StarField.catalog()
 *     .star("a", 160, 40, 1.8f, 0xffcfe0ff).star("b", 150, 45, 2.2f, 0xffffffff).star("c", 140, 42, 2f, 0xffffe0b0)
 *     .constellation(0x806080ff, "a", "b", "c").build();
 * Effects.stars(ID, CEU, CARRO);   // after register and timeline
 * }</pre>
 *
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class StarField {
    /** Most star fields per effect or profile state. */
    public static final int MAX_PER_EFFECT = 4;
    /** Most stars of a procedural field. */
    public static final int MAX_RANDOM = 65536;
    /** Most stars of a catalog. */
    public static final int MAX_CATALOG = 4096;
    /** Most constellation line segments of a catalog. */
    public static final int MAX_LINES = 2048;
    /** Opacity below which a field is not drawn. */
    public static final float ACTIVE_OPACITY = 1e-3f;
    /** Fastest spin, degrees per second. */
    public static final float MAX_SPIN = 90f;
    /** Default draw order of star fields (lower is drawn first, behind). */
    public static final int DEFAULT_ORDER = 200;
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");
    private static final int[] DEFAULT_COLORS = {0xffaabfff, 0xffcad7ff, 0xfff8f7ff, 0xfffff4ea, 0xffffd2a1, 0xffffcc6f};
    private static final float[] DEFAULT_WEIGHTS = {.08f, .17f, .30f, .25f, .14f, .06f};

    /** How the stars are produced. */
    public enum Kind {
        /** Procedural, from a seed and a count. */
        RANDOM,
        /** Listed one by one, with optional constellation lines. */
        CATALOG
    }

    /** Numeric parameters that can be driven by a timeline track, each with the range used to clamp it. */
    public enum Param {
        /** Opacity multiplier, 0..1. */
        OPACITY(0, 1),
        /** Brightness multiplier, 0..16. */
        BRIGHTNESS(0, 16),
        /** Turn around the vertical axis, degrees (positive moves what was south toward west). */
        ROTATION(-3600, 3600);

        /** Smallest and largest value of the parameter; track values are clamped to this range. */
        public final float min, max;

        Param(float min, float max) { this.min = min; this.max = max; }

        /** Clamps a value into this parameter's range; NaN becomes the value closest to 0 in the range. */
        public float clamp(float value) {
            return Float.isNaN(value) ? Math.clamp(0, min, max) : Math.clamp(value, min, max);
        }
    }

    /**
     * One catalog star.
     *
     * @param name      name used by constellation lines, or null
     * @param azimuth   degrees (0 = south, 90 = west), as seen at midnight when the field is celestial
     * @param elevation degrees, −90..90
     * @param magnitude astronomical magnitude, −30..30 (smaller is brighter)
     * @param argb      color (alpha multiplies the star's brightness)
     */
    public record Star(String name, double azimuth, double elevation, float magnitude, int argb) {}

    /**
     * One constellation segment between two catalog stars.
     *
     * @param from index of the first star in {@link #stars()}
     * @param to   index of the second star
     * @param argb line color; alpha is the line opacity
     */
    public record Line(int from, int to, int argb) {}

    /**
     * A field resolved at one moment.
     *
     * @param opacity    effective opacity, 0..1 (builder opacity times weight, phase and track)
     * @param brightness brightness multiplier, 0..16
     * @param rotation   turn around the vertical axis without the spin, degrees
     * @param spin       spin, degrees per second (applied by the client over the instance's real age)
     */
    public record State(float opacity, float brightness, float rotation, float spin) {
        /** True when the field should be drawn. */
        public boolean visible() { return opacity > ACTIVE_OPACITY && brightness > 0; }

        /** Turn after {@code seconds} of real age, degrees (spin stops when {@code motion} is false). */
        public double rotationAt(double seconds, boolean motion) {
            return rotation + (motion ? spin * seconds : 0);
        }
    }

    private final Kind kind;
    private final long seed;
    private final int count;
    private final float brightest, faintest;
    private final int[] colors;
    private final float[] weights;
    private final double bandAzimuth, bandElevation;
    private final float bandWidth, bandFraction;
    private final double regionAzimuth, regionElevation;
    private final float regionRadius;
    private final List<Star> stars;
    private final List<Line> lines;
    private final float lineWidth, size, brightness, twinkle, twinkleSpeed, atmosphere, darkeningStrength, daylight,
        horizon, opacity, rotation, spin;
    private final SkyBlend blend;
    private final SkyDarkening darkening;
    private final boolean celestial;
    private final int order;
    private final Map<Param, String> tracks;
    private final String weightPhase;

    private StarField(Builder b) {
        kind = b.kind; seed = b.seed; count = kind == Kind.RANDOM ? b.count : b.stars.size();
        brightest = b.brightest; faintest = b.faintest;
        colors = b.colors.clone(); weights = b.weights.clone();
        bandAzimuth = b.bandAzimuth; bandElevation = b.bandElevation; bandWidth = b.bandWidth; bandFraction = b.bandFraction;
        regionAzimuth = b.regionAzimuth; regionElevation = b.regionElevation; regionRadius = b.regionRadius;
        stars = List.copyOf(b.stars); lines = List.copyOf(b.lines);
        lineWidth = b.lineWidth; size = b.size; brightness = b.brightness; twinkle = b.twinkle; twinkleSpeed = b.twinkleSpeed;
        atmosphere = b.atmosphere; darkeningStrength = b.darkeningStrength; daylight = b.daylight; horizon = b.horizon;
        opacity = b.opacity; rotation = b.rotation; spin = b.spin; blend = b.blend; darkening = b.darkening;
        celestial = b.celestial; order = b.order;
        tracks = Collections.unmodifiableMap(new EnumMap<>(b.tracks));
        weightPhase = b.weightPhase;
    }

    /**
     * Procedural field: {@code count} stars (1..{@value #MAX_RANDOM}) spread over the whole sky (or a
     * {@linkplain Builder#region region}), with magnitudes distributed like the real sky (about four times more stars
     * per fainter magnitude) and colors from a palette of star temperatures. The same seed always gives the same stars.
     */
    public static Builder random(long seed, int count) {
        if (count < 1 || count > MAX_RANDOM) throw new IllegalArgumentException("Expected 1.." + MAX_RANDOM + " stars");
        Builder b = new Builder(Kind.RANDOM);
        b.seed = seed;
        b.count = count;
        return b;
    }

    /** Catalog: stars added one by one with {@link Builder#star}, joined by {@link Builder#constellation} lines. */
    public static Builder catalog() { return new Builder(Kind.CATALOG); }

    /** How the stars are produced. */
    public Kind kind() { return kind; }
    /** Seed of a procedural field (0 for a catalog). */
    public long seed() { return seed; }
    /** Number of stars: the procedural count or the catalog size. */
    public int count() { return count; }
    /** Brightest magnitude of a procedural field. */
    public float brightest() { return brightest; }
    /** Faintest magnitude of a procedural field. */
    public float faintest() { return faintest; }
    /** Palette of a procedural field, ARGB (a copy). */
    public int[] colors() { return colors.clone(); }
    /** Relative weights of the palette colors (a copy, same length as {@link #colors()}). */
    public float[] colorWeights() { return weights.clone(); }
    /** Azimuth of the band's pole, degrees (procedural fields with a band). */
    public double bandAzimuth() { return bandAzimuth; }
    /** Elevation of the band's pole, degrees. */
    public double bandElevation() { return bandElevation; }
    /** Half width of the band, degrees (0: no band). */
    public float bandWidth() { return bandWidth; }
    /** Fraction of the stars concentrated in the band, 0..1. */
    public float bandFraction() { return bandFraction; }
    /** Azimuth of the region center, degrees. */
    public double regionAzimuth() { return regionAzimuth; }
    /** Elevation of the region center, degrees. */
    public double regionElevation() { return regionElevation; }
    /** Angular radius of the region, degrees (180: the whole sky). */
    public float regionRadius() { return regionRadius; }
    /** Catalog stars, in insertion order (empty for a procedural field). */
    public List<Star> stars() { return stars; }
    /** Constellation segments (empty for a procedural field). */
    public List<Line> lines() { return lines; }
    /** Width of the constellation lines, degrees. */
    public float lineWidth() { return lineWidth; }
    /** Star size multiplier, 0.1..16 (1: about 0.09 degrees across for a magnitude-1 star). */
    public float size() { return size; }
    /** Brightness multiplier, 0..16. */
    public float brightness() { return brightness; }
    /** Twinkle amount, 0..1. */
    public float twinkle() { return twinkle; }
    /** Twinkle speed, cycles per second (about), 0..8. */
    public float twinkleSpeed() { return twinkleSpeed; }
    /** Composition mode. */
    public SkyBlend blend() { return blend; }
    /** How much the Atmosphere sky veils the stars, 0..1. */
    public float atmosphere() { return atmosphere; }
    /** Reaction to the darkening of sky layers. */
    public SkyDarkening darkening() { return darkening; }
    /** Strength of the darkening reaction, 0..1. */
    public float darkeningStrength() { return darkeningStrength; }
    /** How much daylight hides the stars, 0..1 (1: like the vanilla stars). */
    public float daylight() { return daylight; }
    /** Height of the fade at the horizon, degrees (0: none). */
    public float horizon() { return horizon; }
    /** Opacity, 0..1. */
    public float opacity() { return opacity; }
    /** Turn around the vertical axis, degrees. */
    public float rotation() { return rotation; }
    /** Spin, degrees per second. */
    public float spin() { return spin; }
    /** True when the field turns with the time of day like the vanilla stars. */
    public boolean celestial() { return celestial; }
    /** Draw order among sky objects: lower is drawn first, behind (default {@value #DEFAULT_ORDER}). */
    public int order() { return order; }
    /** Track bound to a parameter, or null when the parameter is constant. */
    public String track(Param param) { return tracks.get(Objects.requireNonNull(param)); }
    /** Every track binding, in {@code Param} order. */
    public Map<Param, String> tracks() { return tracks; }
    /** Phase whose weight multiplies the opacity, or null. */
    public String weightPhase() { return weightPhase; }
    /** True when the opacity, the brightness or the rotation depends on the timeline. */
    public boolean animated() { return !tracks.isEmpty() || weightPhase != null; }

    /**
     * Checks that every track and the phase this field reads exist in {@code timeline}, which may be null only when
     * nothing is {@link #animated() animated}. Called by {@link Effects#stars}.
     */
    public void validate(Timeline timeline) {
        if (!animated()) return;
        if (timeline == null)
            throw new IllegalArgumentException("StarField reads tracks or a phase: register the effect's timeline first");
        tracks.forEach((param, track) -> {
            if (!timeline.hasTrack(track)) throw new IllegalArgumentException("StarField " + param + " reads unknown track: " + track);
        });
        if (weightPhase != null && !timeline.hasPhase(weightPhase))
            throw new IllegalArgumentException("StarField weight reads unknown phase: " + weightPhase);
    }

    /**
     * Resolves this field at one moment: {@code timeline} is the instance's sampler at its local age (any sampler works
     * when nothing is animated); {@code envelope} is the instance's weight (0..1), which multiplies the opacity together
     * with the phase and the {@link Param#OPACITY} track.
     */
    public State sample(Timeline.Sampler timeline, float envelope) {
        Objects.requireNonNull(timeline, "timeline");
        float weight = Float.isNaN(envelope) ? 0 : Math.clamp(envelope, 0f, 1f);
        if (weightPhase != null) weight *= timeline.weight(weightPhase);
        String o = tracks.get(Param.OPACITY), b = tracks.get(Param.BRIGHTNESS), r = tracks.get(Param.ROTATION);
        float op = opacity * (o == null ? 1 : Param.OPACITY.clamp(timeline.value(o)));
        float br = b == null ? brightness : Param.BRIGHTNESS.clamp(timeline.value(b));
        float ro = r == null ? rotation : Param.ROTATION.clamp(timeline.value(r));
        return new State(Math.clamp(op * weight, 0f, 1f), br, ro, spin);
    }

    /** Short description for logs: kind and count. */
    @Override public String toString() { return "StarField[" + kind + " " + count + (lines.isEmpty() ? "" : " lines " + lines.size()) + "]"; }

    /** Builder of a {@link StarField}. Every setter validates its arguments right away. */
    public static final class Builder {
        private final Kind kind;
        private long seed;
        private int count;
        private float brightest = -1, faintest = 6;
        private int[] colors = DEFAULT_COLORS.clone();
        private float[] weights = DEFAULT_WEIGHTS.clone();
        private double bandAzimuth, bandElevation = 90, regionAzimuth, regionElevation = 90;
        private float bandWidth, bandFraction, regionRadius = 180;
        private final List<Star> stars = new ArrayList<>();
        private final Map<String, Integer> names = new HashMap<>();
        private final List<Line> lines = new ArrayList<>();
        private float lineWidth = .12f, size = 1, brightness = 1, twinkle = .25f, twinkleSpeed = 1, atmosphere = 1,
            darkeningStrength = 1, daylight = 1, horizon = 4, opacity = 1, rotation, spin;
        private SkyBlend blend = SkyBlend.LUMINOUS;
        private SkyDarkening darkening = SkyDarkening.REVEAL;
        private boolean celestial = true;
        private int order = DEFAULT_ORDER;
        private final Map<Param, String> tracks = new EnumMap<>(Param.class);
        private String weightPhase;

        private Builder(Kind kind) { this.kind = kind; }

        /** Magnitude range of a procedural field, −30..30, brightest first (default −1 to 6). */
        public Builder magnitudes(float brightest, float faintest) {
            random("magnitudes");
            if (!Float.isFinite(brightest) || !Float.isFinite(faintest) || brightest > faintest
                || Math.abs(brightest) > 30 || Math.abs(faintest) > 30)
                throw new IllegalArgumentException("Expected magnitudes within ±30 with brightest <= faintest");
            this.brightest = brightest;
            this.faintest = faintest;
            return this;
        }

        /** Palette of a procedural field, 1..16 ARGB colors with equal weights (default: star temperatures). */
        public Builder colors(int... argb) {
            random("colors");
            if (argb == null || argb.length < 1 || argb.length > 16) throw new IllegalArgumentException("Expected 1..16 colors");
            colors = argb.clone();
            weights = new float[argb.length];
            java.util.Arrays.fill(weights, 1);
            return this;
        }

        /**
         * Concentrates a fraction (0..1) of a procedural field's stars in a band around a great circle, like a galaxy
         * seen edge-on: the circle is the one perpendicular to the pole at ({@code azimuth}, {@code elevation}), and
         * {@code halfWidth} (1..60 degrees) is the band's half width.
         */
        public Builder band(double poleAzimuth, double poleElevation, float halfWidth, float fraction) {
            random("band");
            angle(poleAzimuth, poleElevation);
            if (!Float.isFinite(halfWidth) || halfWidth < 1 || halfWidth > 60) throw new IllegalArgumentException("Expected band half width in [1, 60] degrees");
            bandAzimuth = poleAzimuth; bandElevation = poleElevation; bandWidth = halfWidth; bandFraction = unit("band fraction", fraction);
            return this;
        }

        /** Keeps a procedural field inside a circle of the sky: center ({@code azimuth}, {@code elevation}), radius 1..180 degrees. */
        public Builder region(double azimuth, double elevation, float radius) {
            random("region");
            angle(azimuth, elevation);
            if (!Float.isFinite(radius) || radius < 1 || radius > 180) throw new IllegalArgumentException("Expected region radius in [1, 180] degrees");
            regionAzimuth = azimuth; regionElevation = elevation; regionRadius = radius;
            return this;
        }

        /** Adds a catalog star without a name (see {@link #star(String, double, double, float, int)}). */
        public Builder star(double azimuth, double elevation, float magnitude, int argb) {
            return add(null, azimuth, elevation, magnitude, argb);
        }

        /**
         * Adds a named catalog star: name {@code [a-z0-9_]}, unique in the field; direction in degrees (azimuth 0 =
         * south, 90 = west); magnitude −30..30; ARGB color (alpha multiplies the brightness). Up to {@value #MAX_CATALOG}.
         */
        public Builder star(String name, double azimuth, double elevation, float magnitude, int argb) {
            String n = StarField.name("star", name);
            if (names.containsKey(n)) throw new IllegalArgumentException("Duplicate star name: " + n);
            return add(n, azimuth, elevation, magnitude, argb);
        }

        private Builder add(String name, double azimuth, double elevation, float magnitude, int argb) {
            if (kind != Kind.CATALOG) throw new IllegalArgumentException("star(...) is for catalogs: use StarField.catalog()");
            if (stars.size() >= MAX_CATALOG) throw new IllegalArgumentException("At most " + MAX_CATALOG + " catalog stars");
            angle(azimuth, elevation);
            if (!Float.isFinite(magnitude) || Math.abs(magnitude) > 30) throw new IllegalArgumentException("Expected magnitude within ±30");
            if (name != null) names.put(name, stars.size());
            stars.add(new Star(name, azimuth, elevation, magnitude, argb));
            return this;
        }

        /**
         * Joins named catalog stars with lines, in order ({@code "a", "b", "c"} draws a–b and b–c); call again for
         * branches. {@code argb} is the line color, its alpha the line opacity. At most {@value #MAX_LINES} segments.
         */
        public Builder constellation(int argb, String... path) {
            if (kind != Kind.CATALOG) throw new IllegalArgumentException("constellation(...) is for catalogs");
            if (path == null || path.length < 2) throw new IllegalArgumentException("A constellation needs at least two stars");
            for (int i = 1; i < path.length; i++) {
                Integer a = names.get(path[i - 1]), c = names.get(path[i]);
                if (a == null || c == null) throw new IllegalArgumentException("Unknown star in constellation: " + (a == null ? path[i - 1] : path[i]));
                if (a.equals(c)) throw new IllegalArgumentException("A constellation line joins two different stars");
                if (lines.size() >= MAX_LINES) throw new IllegalArgumentException("At most " + MAX_LINES + " constellation lines");
                lines.add(new Line(a, c, argb));
            }
            return this;
        }

        /** Width of the constellation lines, 0.02..2 degrees (default 0.12; at least about one pixel on screen). */
        public Builder lineWidth(float degrees) {
            if (!Float.isFinite(degrees) || degrees < .02f || degrees > 2) throw new IllegalArgumentException("Expected line width in [0.02, 2] degrees");
            lineWidth = degrees;
            return this;
        }

        /** Star size multiplier, 0.1..16 (default 1). Small stars keep about one pixel and lose brightness instead. */
        public Builder size(float value) {
            if (!Float.isFinite(value) || value < .1f || value > 16) throw new IllegalArgumentException("Expected size in [0.1, 16]");
            size = value;
            return this;
        }

        /** Brightness multiplier, 0..16 (default 1). */
        public Builder brightness(float value) {
            if (!Float.isFinite(value) || value < 0 || value > 16) throw new IllegalArgumentException("Expected brightness in [0, 16]");
            brightness = value;
            return this;
        }

        /**
         * Twinkle: amount 0..1 (default 0.25) and speed 0..8 cycles per second (default 1). Frozen with "motion effects"
         * off, gone with screen effect intensity 0, a quarter with "hide lightning flashes".
         */
        public Builder twinkle(float amount, float speed) {
            twinkle = unit("twinkle", amount);
            if (!Float.isFinite(speed) || speed < 0 || speed > 8) throw new IllegalArgumentException("Expected twinkle speed in [0, 8]");
            twinkleSpeed = speed;
            return this;
        }

        /** Composition with the sky behind ({@link SkyBlend}; default {@link SkyBlend#LUMINOUS}). */
        public Builder blend(SkyBlend value) { blend = Objects.requireNonNull(value, "blend"); return this; }

        /** How much the Atmosphere sky veils the stars, 0..1 (default 1; 0: they shine through any atmosphere). */
        public Builder atmosphere(float value) { atmosphere = unit("atmosphere", value); return this; }

        /** Reaction to sky layers that darken the world and its strength 0..1 (default {@link SkyDarkening#REVEAL}, 1). */
        public Builder darkening(SkyDarkening mode, float strength) {
            darkening = Objects.requireNonNull(mode, "darkening");
            darkeningStrength = unit("darkening strength", strength);
            return this;
        }

        /** How much daylight and rain hide the stars, 0..1 (default 1, like the vanilla stars; 0: always visible). */
        public Builder daylight(float value) { daylight = unit("daylight", value); return this; }

        /** Fade near the horizon: stars reach full brightness this many degrees up, 0..30 (default 4; 0: no fade). */
        public Builder horizon(float degrees) {
            if (!Float.isFinite(degrees) || degrees < 0 || degrees > 30) throw new IllegalArgumentException("Expected horizon fade in [0, 30] degrees");
            horizon = degrees;
            return this;
        }

        /** Opacity, 0..1 (default 1). */
        public Builder opacity(float value) { opacity = unit("opacity", value); return this; }

        /** Turn around the vertical axis, degrees, −3600..3600 (positive moves what was south toward west). */
        public Builder rotation(float degrees) {
            if (!Float.isFinite(degrees) || Math.abs(degrees) > 3600) throw new IllegalArgumentException("Expected rotation within ±3600 degrees");
            rotation = degrees;
            return this;
        }

        /** Spin around the vertical axis, degrees per second (±{@value #MAX_SPIN}); stops with "motion effects" off. */
        public Builder spin(float degreesPerSecond) {
            if (!Float.isFinite(degreesPerSecond) || Math.abs(degreesPerSecond) > MAX_SPIN)
                throw new IllegalArgumentException("Expected spin within ±" + MAX_SPIN + " degrees per second");
            spin = degreesPerSecond;
            return this;
        }

        /** Whether the field turns with the time of day like the vanilla stars (default true). */
        public Builder celestial(boolean value) { celestial = value; return this; }

        /** Draw order among sky objects, −1000..1000: lower is drawn first, behind (default {@value #DEFAULT_ORDER}). */
        public Builder order(int value) {
            if (value < -1000 || value > 1000) throw new IllegalArgumentException("Expected order in [-1000, 1000]");
            order = value;
            return this;
        }

        /** Takes {@code param} from a track of the effect's timeline instead of the constant (clamped to its range). */
        public Builder track(Param param, String track) {
            Objects.requireNonNull(param, "param");
            tracks.put(param, StarField.name("track", track));
            return this;
        }

        /** Multiplies the opacity by the weight of a timeline phase (0 outside it). */
        public Builder weightPhase(String phase) {
            weightPhase = StarField.name("phase", phase);
            return this;
        }

        /** Immutable star field; a catalog needs at least one star. */
        public StarField build() {
            if (kind == Kind.CATALOG && stars.isEmpty()) throw new IllegalArgumentException("A catalog needs at least one star");
            return new StarField(this);
        }

        private void random(String what) {
            if (kind != Kind.RANDOM) throw new IllegalArgumentException(what + "(...) is for procedural fields: use StarField.random(seed, count)");
        }
    }

    private static void angle(double azimuth, double elevation) {
        if (!Double.isFinite(azimuth) || !Double.isFinite(elevation) || elevation < -90 || elevation > 90)
            throw new IllegalArgumentException("Expected a finite azimuth and an elevation in [-90, 90] degrees");
    }

    private static float unit(String what, float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Expected " + what + " in [0, 1]");
        return value;
    }

    private static String name(String what, String value) {
        Objects.requireNonNull(value, what);
        if (!NAME.matcher(value).matches()) throw new IllegalArgumentException("Invalid " + what + " name: " + value);
        return value;
    }
}
