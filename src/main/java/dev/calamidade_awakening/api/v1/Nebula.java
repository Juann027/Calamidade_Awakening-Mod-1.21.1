package dev.calamidade_awakening.api.v1;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable procedural nebula of one effect or one {@link SkyProfile.State} (0.14.0): a {@link #cloud cloud} around a
 * direction or a {@link #band band} across the whole sky (like the dust and glow of a galaxy seen edge-on), shaped by
 * seeded noise (every client sees the same nebula). Pure Java, no Minecraft types. Nothing is drawn unless an addon
 * attaches a nebula ({@link Effects#nebulae}, {@link SkyProfile.StateBuilder#nebulae}); players can turn custom skies
 * off with the client option {@code customSkies}.
 *
 * <p><b>How it is drawn.</b> One static mesh per shape (a patch of the sky for a cloud, the sky sphere for a band) with
 * the engine's nebula shader, right after the vanilla sky and the {@link Backdrop}
 * ({@code RenderLevelStageEvent.Stage.AFTER_SKY}), behind terrain, clouds and the engine's {@link Sky} layers. A cloud
 * only costs the pixels it covers. Quality profiles cap the nebulae per frame and the noise detail (octaves), AUTO
 * included.
 *
 * <p><b>Composition.</b> Real transparency by default ({@link SkyBlend#NORMAL}): where the nebula is thin, the
 * background behind shows through; where it is dense, it hides the background — only there, never the whole sky. A dark
 * nebula (dark colors with NORMAL) hides the stars and the background behind it like dust; {@link SkyBlend#LUMINOUS}
 * makes a glowing gas that only adds light. The Atmosphere, daylight and darkening reactions work like those of a
 * {@link StarField} (by default: veiled by the atmosphere, hidden by daylight, revealed by an eclipse).
 *
 * <p>Color: {@link Builder#colors(int, int) inner} where the gas is densest, {@code outer} at its thin edges; their
 * alpha scales the density there. {@link Builder#brightness} multiplies the light (above 1 a NORMAL nebula glows more
 * than it covers).
 *
 * <pre>{@code
 * static final Nebula NEBULOSA = Nebula.cloud(120, 35, 22).colors(0xffff70c0, 0x803060ff).seed(7).build();
 * static final Nebula VIA = Nebula.band(90, 30, 9).colors(0xb0fff0d8, 0x40605080).density(.5f).build();
 * Effects.nebulae(ID, NEBULOSA, VIA);   // after register and timeline
 * }</pre>
 *
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class Nebula {
    /** Most nebulae per effect or profile state. */
    public static final int MAX_PER_EFFECT = 8;
    /** Opacity below which a nebula is not drawn. */
    public static final float ACTIVE_OPACITY = 1e-3f;
    /** Fastest spin, degrees per second. */
    public static final float MAX_SPIN = 90f;
    /** Default draw order of nebulae (lower is drawn first, behind): behind the star fields. */
    public static final int DEFAULT_ORDER = 100;
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");

    /** Shape of the nebula. */
    public enum Shape {
        /** A cloud around a direction, within an angular radius. */
        CLOUD,
        /** A band around a great circle of the sky, defined by its pole. */
        BAND
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
     * A nebula resolved at one moment.
     *
     * @param opacity    effective opacity, 0..1 (builder opacity times weight, phase and track)
     * @param brightness brightness multiplier, 0..16
     * @param rotation   turn around the vertical axis without the spin, degrees
     * @param spin       spin, degrees per second (applied by the client over the instance's real age)
     */
    public record State(float opacity, float brightness, float rotation, float spin) {
        /** True when the nebula should be drawn. */
        public boolean visible() { return opacity > ACTIVE_OPACITY; }

        /** Turn after {@code seconds} of real age, degrees (spin stops when {@code motion} is false). */
        public double rotationAt(double seconds, boolean motion) {
            return rotation + (motion ? spin * seconds : 0);
        }
    }

    private final Shape shape;
    private final double azimuth, elevation;
    private final float extent, roll;
    private final int inner, outer;
    private final float density, detail, warp, brightness, atmosphere, darkeningStrength, daylight, horizon, opacity,
        rotation, spin;
    private final long seed;
    private final SkyBlend blend;
    private final SkyDarkening darkening;
    private final boolean celestial;
    private final int order;
    private final Map<Param, String> tracks;
    private final String weightPhase;

    private Nebula(Builder b) {
        shape = b.shape; azimuth = b.azimuth; elevation = b.elevation; extent = b.extent; roll = b.roll;
        inner = b.inner; outer = b.outer; density = b.density; detail = b.detail; warp = b.warp; brightness = b.brightness;
        atmosphere = b.atmosphere; darkeningStrength = b.darkeningStrength; daylight = b.daylight; horizon = b.horizon;
        opacity = b.opacity; rotation = b.rotation; spin = b.spin; seed = b.seed; blend = b.blend; darkening = b.darkening;
        celestial = b.celestial; order = b.order;
        tracks = Collections.unmodifiableMap(new EnumMap<>(b.tracks));
        weightPhase = b.weightPhase;
    }

    /**
     * Cloud centered on ({@code azimuth}, {@code elevation}) degrees (azimuth 0 = south, 90 = west; the direction seen
     * at midnight when {@link Builder#celestial celestial}) with an angular radius of 1..80 degrees. Noise shapes its
     * edge inside that radius.
     */
    public static Builder cloud(double azimuth, double elevation, float radius) {
        angle(azimuth, elevation);
        if (!Float.isFinite(radius) || radius < 1 || radius > 80) throw new IllegalArgumentException("Expected cloud radius in [1, 80] degrees");
        return new Builder(Shape.CLOUD, azimuth, elevation, radius);
    }

    /**
     * Band around the great circle perpendicular to the pole ({@code poleAzimuth}, {@code poleElevation}) degrees, with
     * a half width of 1..60 degrees (the gas thins out to about twice that).
     */
    public static Builder band(double poleAzimuth, double poleElevation, float halfWidth) {
        angle(poleAzimuth, poleElevation);
        if (!Float.isFinite(halfWidth) || halfWidth < 1 || halfWidth > 60) throw new IllegalArgumentException("Expected band half width in [1, 60] degrees");
        return new Builder(Shape.BAND, poleAzimuth, poleElevation, halfWidth);
    }

    /** Shape of the nebula. */
    public Shape shape() { return shape; }
    /** Azimuth of the cloud center or of the band pole, degrees. */
    public double azimuth() { return azimuth; }
    /** Elevation of the cloud center or of the band pole, degrees. */
    public double elevation() { return elevation; }
    /** Cloud radius or band half width, degrees. */
    public float extent() { return extent; }
    /** Turn of the noise pattern around the cloud center, degrees. */
    public float roll() { return roll; }
    /** Color where the gas is densest, ARGB (alpha scales the density there). */
    public int inner() { return inner; }
    /** Color at the thin edges, ARGB (alpha scales the density there). */
    public int outer() { return outer; }
    /** Density, 0..1. */
    public float density() { return density; }
    /** Noise scale, 0.5..16 (higher: smaller features). */
    public float detail() { return detail; }
    /** Domain warp of the noise, 0..2 (higher: more swirling). */
    public float warp() { return warp; }
    /** Noise seed. */
    public long seed() { return seed; }
    /** Brightness multiplier, 0..16. */
    public float brightness() { return brightness; }
    /** Composition mode. */
    public SkyBlend blend() { return blend; }
    /** How much the Atmosphere sky veils the nebula, 0..1. */
    public float atmosphere() { return atmosphere; }
    /** Reaction to the darkening of sky layers. */
    public SkyDarkening darkening() { return darkening; }
    /** Strength of the darkening reaction, 0..1. */
    public float darkeningStrength() { return darkeningStrength; }
    /** How much daylight hides the nebula, 0..1. */
    public float daylight() { return daylight; }
    /** Height of the fade at the horizon, degrees (0: none). */
    public float horizon() { return horizon; }
    /** Opacity, 0..1. */
    public float opacity() { return opacity; }
    /** Turn around the vertical axis, degrees. */
    public float rotation() { return rotation; }
    /** Spin, degrees per second. */
    public float spin() { return spin; }
    /** True when the nebula turns with the time of day like the vanilla stars. */
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
     * Checks that every track and the phase this nebula reads exist in {@code timeline}, which may be null only when
     * nothing is {@link #animated() animated}. Called by {@link Effects#nebulae}.
     */
    public void validate(Timeline timeline) {
        if (!animated()) return;
        if (timeline == null)
            throw new IllegalArgumentException("Nebula reads tracks or a phase: register the effect's timeline first");
        tracks.forEach((param, track) -> {
            if (!timeline.hasTrack(track)) throw new IllegalArgumentException("Nebula " + param + " reads unknown track: " + track);
        });
        if (weightPhase != null && !timeline.hasPhase(weightPhase))
            throw new IllegalArgumentException("Nebula weight reads unknown phase: " + weightPhase);
    }

    /**
     * Resolves this nebula at one moment: {@code timeline} is the instance's sampler at its local age (any sampler works
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

    /** Short description for logs: shape and extent. */
    @Override public String toString() { return "Nebula[" + shape + " " + extent + "°]"; }

    /** Builder of a {@link Nebula}. Every setter validates its arguments right away. */
    public static final class Builder {
        private final Shape shape;
        private final double azimuth, elevation;
        private final float extent;
        private float roll;
        private int inner = 0xffd080ff, outer = 0x803050c0;
        private float density = .7f, detail = 3, warp = .7f, brightness = 1, atmosphere = 1, darkeningStrength = 1,
            daylight = 1, horizon = 4, opacity = 1, rotation, spin;
        private long seed;
        private SkyBlend blend = SkyBlend.NORMAL;
        private SkyDarkening darkening = SkyDarkening.REVEAL;
        private boolean celestial = true;
        private int order = DEFAULT_ORDER;
        private final Map<Param, String> tracks = new EnumMap<>(Param.class);
        private String weightPhase;

        private Builder(Shape shape, double azimuth, double elevation, float extent) {
            this.shape = shape; this.azimuth = azimuth; this.elevation = elevation; this.extent = extent;
        }

        /** Colors where the gas is densest and at its thin edges, ARGB; alpha scales the density there. */
        public Builder colors(int inner, int outer) { this.inner = inner; this.outer = outer; return this; }

        /** Density, 0..1 (default 0.7). */
        public Builder density(float value) { density = unit("density", value); return this; }

        /** Noise scale, 0.5..16 (default 3; higher: smaller features). */
        public Builder detail(float value) {
            if (!Float.isFinite(value) || value < .5f || value > 16) throw new IllegalArgumentException("Expected detail in [0.5, 16]");
            detail = value;
            return this;
        }

        /** Domain warp, 0..2 (default 0.7; higher: more swirling). */
        public Builder warp(float value) {
            if (!Float.isFinite(value) || value < 0 || value > 2) throw new IllegalArgumentException("Expected warp in [0, 2]");
            warp = value;
            return this;
        }

        /** Noise seed (default 0): the same seed always gives the same nebula. */
        public Builder seed(long value) { seed = value; return this; }

        /** Turn of a cloud's pattern around its center, degrees, ±360. */
        public Builder roll(float degrees) {
            if (!Float.isFinite(degrees) || Math.abs(degrees) > 360) throw new IllegalArgumentException("Expected roll within ±360 degrees");
            roll = degrees;
            return this;
        }

        /** Brightness multiplier, 0..16 (default 1). */
        public Builder brightness(float value) {
            if (!Float.isFinite(value) || value < 0 || value > 16) throw new IllegalArgumentException("Expected brightness in [0, 16]");
            brightness = value;
            return this;
        }

        /** Composition with the sky behind ({@link SkyBlend}; default {@link SkyBlend#NORMAL}). */
        public Builder blend(SkyBlend value) { blend = Objects.requireNonNull(value, "blend"); return this; }

        /** How much the Atmosphere sky veils the nebula, 0..1 (default 1). */
        public Builder atmosphere(float value) { atmosphere = unit("atmosphere", value); return this; }

        /** Reaction to sky layers that darken the world and its strength 0..1 (default {@link SkyDarkening#REVEAL}, 1). */
        public Builder darkening(SkyDarkening mode, float strength) {
            darkening = Objects.requireNonNull(mode, "darkening");
            darkeningStrength = unit("darkening strength", strength);
            return this;
        }

        /** How much daylight and rain hide the nebula, 0..1 (default 1; 0: always visible). */
        public Builder daylight(float value) { daylight = unit("daylight", value); return this; }

        /** Fade near the horizon, 0..30 degrees (default 4; 0: no fade). */
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

        /** Whether the nebula turns with the time of day like the vanilla stars (default true). */
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
            tracks.put(param, Nebula.name("track", track));
            return this;
        }

        /** Multiplies the opacity by the weight of a timeline phase (0 outside it). */
        public Builder weightPhase(String phase) {
            weightPhase = Nebula.name("phase", phase);
            return this;
        }

        /** Immutable nebula. */
        public Nebula build() { return new Nebula(this); }
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
