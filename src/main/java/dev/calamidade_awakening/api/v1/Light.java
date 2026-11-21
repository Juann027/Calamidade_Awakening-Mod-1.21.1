package dev.calamidade_awakening.api.v1;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable visual light of one effect (0.9.0): it brightens the blocks, entities and terrain on screen around a
 * {@link Point}, along a segment (optionally the segment of one of the effect's {@link Beam beams}) or over a disc or
 * a ring on the ground (shock waves). Pure Java, no Minecraft types, so the registry, the client and tests share the
 * same math.
 *
 * <p><b>Visual only.</b> The light is drawn by a screen pass on what the camera sees; it does not change the game's
 * light level, mob spawning, plant growth or anything the server knows. Blocks behind the camera or hidden from it
 * are not lit, and the light does not cast shadows.
 *
 * <p>The engine draws every visible light of the frame in one screen pass ("lights"): it rebuilds the position of
 * each pixel from the depth buffer, estimates the surface normal from neighbouring pixels (only faces turned toward
 * the source are lit), adds the light with distance attenuation, never lights the sky and fades with the game's own
 * fog. The pass runs before the effect passes and the atmosphere, so the atmosphere covers it in the distance. It is
 * charged once per frame to the HERO pass budget when a drawn HERO effect has lights, otherwise to the common addon
 * budget, and draws at most 8/24/64 lights per frame (LOW/BALANCED/HIGH), taken from the most important effects.
 *
 * <ul>
 * <li>{@link #point}: light at a point; {@code radius} is its reach in blocks (0 light beyond it) and
 * {@code falloff} the exponent of {@code (1 - distance / radius)}.</li>
 * <li>{@link #segment} and {@link #along(Beam)}: the same from the nearest point of a segment; {@code along} uses
 * the ends, reveal, weight and halo color of a beam of the same effect, so the light grows and fades with it.</li>
 * <li>{@link #disc} and {@link #ring}: light on the ground around a center: a disc of {@code radius} blocks with a
 * soft edge {@code width} blocks wide, or a band {@code width} blocks wide at {@code radius}; it reaches
 * {@code height} blocks above and below the center and lights faces turned up more than walls.</li>
 * </ul>
 *
 * <p>Every numeric parameter can come from a track of the effect's {@link Timeline} ({@link Builder#track}), clamped
 * to the parameter range: a ring whose {@link Param#RADIUS} follows the shock wave, a light that fades with
 * {@link Param#INTENSITY}. The light weight is the instance's opacity envelope, times an optional phase and an
 * optional {@link Param#WEIGHT} track. Optional flicker (fire, unstable energy) varies the intensity with smooth
 * noise; it follows the player's screen-effects intensity and is reduced to a quarter when the player hides
 * lightning flashes.
 *
 * <pre>{@code
 * static final Light FOGUEIRA = Light.point(Point.center(0, .6, 0))
 *     .color(0xffff9a40).intensity(2.2f).radius(9).flicker(.35f, 6)
 *     .build();
 * Effects.lights(ID, FOGUEIRA);   // mod constructor, after register (and timeline/beams if used)
 * }</pre>
 *
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class Light {
    /** Most lights per effect ({@link Effects#lights}). */
    public static final int MAX_PER_EFFECT = 8;
    /** Largest reach (point and segment) or disc/ring radius, blocks. */
    public static final float MAX_RADIUS = 128f;
    /** Largest intensity multiplier. */
    public static final float MAX_INTENSITY = 8f;
    /** Largest track offset, blocks per axis. */
    public static final float MAX_TRACK_OFFSET = 256f;
    /** Weight below which a light is not drawn. */
    public static final float ACTIVE_WEIGHT = 1e-3f;

    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");

    /** Shape of the light source. */
    public enum Shape {
        /** A point: lights in every direction within {@code radius}. */
        POINT,
        /** A segment (or a beam): lights from its nearest point within {@code radius}. */
        SEGMENT,
        /** A disc on the ground: {@code radius} wide with a soft edge {@code width} blocks wide. */
        DISC,
        /** A ring on the ground: a band {@code width} blocks wide at {@code radius} from the center. */
        RING
    }

    /** Numeric parameters that can be driven by a timeline track, each with the range used to clamp it. */
    public enum Param {
        /** Brightness multiplier. */
        INTENSITY(0, MAX_INTENSITY),
        /** Reach (point, segment) or disc/ring radius, blocks. */
        RADIUS(0, MAX_RADIUS),
        /** Exponent of the distance attenuation (point, segment); 1 linear, 2 soft (default), 4 tight. */
        FALLOFF(.25f, 4),
        /** Ring band width or disc edge softness, blocks. */
        WIDTH(0, 64),
        /** Vertical reach of a disc or ring above and below its center, blocks. */
        HEIGHT(.25f, 32),
        /** Flicker amount, 0..1 (0 steady, 1 may go fully dark). */
        FLICKER(0, 1),
        /** Flicker speed, changes per second. */
        FLICKER_SPEED(0, 20),
        /** Offset added to the position (all ends and the center), x, blocks. */
        X(-MAX_TRACK_OFFSET, MAX_TRACK_OFFSET),
        /** Offset added to the position, y, blocks. */
        Y(-MAX_TRACK_OFFSET, MAX_TRACK_OFFSET),
        /** Offset added to the position, z, blocks. */
        Z(-MAX_TRACK_OFFSET, MAX_TRACK_OFFSET),
        /** Extra multiplier of the light weight, 0..1 (the envelope and the phase still apply). */
        WEIGHT(0, 1);

        /** Smallest and largest value of the parameter; track values are clamped to this range. */
        public final float min, max;

        Param(float min, float max) { this.min = min; this.max = max; }

        /** Clamps a value into this parameter's range; NaN becomes the value closest to 0 in the range. */
        public float clamp(float value) {
            return Float.isNaN(value) ? Math.clamp(0, min, max) : Math.clamp(value, min, max);
        }
    }

    /**
     * Resolved light at one moment: parameters taken from tracks and clamped, flicker applied to the intensity, and
     * the weight (0..1). The client adds the offset to the resolved points.
     *
     * @param weight    light weight, 0..1 (for {@link #along(Beam)}, times the beam weight on the client)
     * @param x         track offset, x, blocks
     * @param y         track offset, y, blocks
     * @param z         track offset, z, blocks
     * @param color     light color, ARGB (alpha ignored)
     * @param intensity brightness multiplier after flicker, 0..{@value #MAX_INTENSITY}
     * @param radius    reach (point, segment) or disc/ring radius, blocks
     * @param falloff   attenuation exponent
     * @param width     ring band width or disc edge, blocks
     * @param height    vertical reach of a disc or ring, blocks
     */
    public record State(float weight, float x, float y, float z, int color, float intensity, float radius,
                        float falloff, float width, float height) {
        /** True when the engine draws this light: weight above {@link #ACTIVE_WEIGHT}, lit and with some size. */
        public boolean visible() { return weight > ACTIVE_WEIGHT && intensity > 0 && radius > 0; }
    }

    private final Shape shape;
    private final Point from, to;
    private final Beam beam;
    private final int color;
    private final boolean colorSet;
    private final float intensity, radius, falloff, width, height, flicker, flickerSpeed;
    private final Map<Param, String> tracks;
    private final String weightPhase;

    private Light(Builder b) {
        shape = b.shape; from = b.from; to = b.to; beam = b.beam; color = b.color; colorSet = b.colorSet;
        intensity = b.intensity; radius = b.radius; falloff = b.falloff; width = b.width; height = b.height;
        flicker = b.flicker; flickerSpeed = b.flickerSpeed;
        tracks = Collections.unmodifiableMap(new EnumMap<>(b.tracks));
        weightPhase = b.weightPhase;
    }

    /** Point light at {@code at}. Defaults: white, intensity 1, reach 8 blocks, falloff 2, no flicker. */
    public static Builder point(Point at) {
        return new Builder(Shape.POINT, Objects.requireNonNull(at, "at"), null, null, 8, 0);
    }

    /** Light along the segment from {@code from} to {@code to} (different points). Defaults as {@link #point}, reach 4. */
    public static Builder segment(Point from, Point to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) throw new IllegalArgumentException("Light segment ends must be different points: " + from);
        return new Builder(Shape.SEGMENT, from, to, null, 4, 0);
    }

    /**
     * Light along a beam of the same effect (register the beams before the lights): its ends, reveal and weight, and
     * its halo color unless {@link Builder#color} is called. Defaults as {@link #segment}.
     */
    public static Builder along(Beam beam) {
        Objects.requireNonNull(beam, "beam");
        return new Builder(Shape.SEGMENT, beam.from(), beam.to(), beam, 4, 0);
    }

    /** Disc of light on the ground: {@code radius} blocks (0..{@value #MAX_RADIUS}) around {@code center}; soft edge 25% of it. */
    public static Builder disc(Point center, float radius) {
        float r = Builder.range("disc radius", radius, 0, MAX_RADIUS);
        return new Builder(Shape.DISC, Objects.requireNonNull(center, "center"), null, null, r, r * .25f);
    }

    /** Ring of light on the ground: a band {@code width} blocks wide (0..64) at {@code radius} blocks from {@code center}. */
    public static Builder ring(Point center, float radius, float width) {
        float r = Builder.range("ring radius", radius, 0, MAX_RADIUS);
        float w = Builder.range("ring width", width, 0, 64);
        return new Builder(Shape.RING, Objects.requireNonNull(center, "center"), null, null, r, w);
    }

    /** Shape of the source. */
    public Shape shape() { return shape; }
    /** Position (point), start (segment) or center (disc, ring). */
    public Point from() { return from; }
    /** End of a segment, or null for the other shapes. */
    public Point to() { return to; }
    /** Beam this light follows ({@link #along(Beam)}), or null. */
    public Beam beam() { return beam; }
    /** Light color, ARGB (alpha ignored); for {@link #along(Beam)} without an explicit color, the beam's halo color. */
    public int color() { return colorSet || beam == null ? color : beam.haloColor(); }
    /** Brightness multiplier, 0..{@value #MAX_INTENSITY}. */
    public float intensity() { return intensity; }
    /** Reach (point, segment) or disc/ring radius, blocks. */
    public float radius() { return radius; }
    /** Attenuation exponent (point, segment). */
    public float falloff() { return falloff; }
    /** Ring band width or disc edge softness, blocks. */
    public float width() { return width; }
    /** Vertical reach of a disc or ring, blocks. */
    public float height() { return height; }
    /** Flicker amount, 0..1. */
    public float flicker() { return flicker; }
    /** Flicker speed, changes per second. */
    public float flickerSpeed() { return flickerSpeed; }
    /** Track bound to a parameter, or null when the parameter is constant. */
    public String track(Param param) { return tracks.get(Objects.requireNonNull(param)); }
    /** Every track binding, in {@link Param} order. */
    public Map<Param, String> tracks() { return tracks; }
    /** Phase whose weight multiplies the light weight, or null. */
    public String weightPhase() { return weightPhase; }
    /** True when some parameter, the weight or the phase depends on the timeline. */
    public boolean animated() { return !tracks.isEmpty() || weightPhase != null; }

    /**
     * Checks that every track and the phase this light reads exist in {@code timeline}, which may be null only when
     * nothing is {@link #animated() animated}. Called by {@link Effects#lights}.
     */
    public void validate(Timeline timeline) {
        if (!animated()) return;
        if (timeline == null)
            throw new IllegalArgumentException("Light reads tracks or a phase: register the effect's timeline first");
        tracks.forEach((param, track) -> {
            if (!timeline.hasTrack(track)) throw new IllegalArgumentException("Light " + param + " reads unknown track: " + track);
        });
        if (weightPhase != null && !timeline.hasPhase(weightPhase))
            throw new IllegalArgumentException("Light weight reads unknown phase: " + weightPhase);
    }

    /**
     * Resolves this light at one moment. {@code timeline} is the instance's sampler at its local age;
     * {@code envelope} the instance opacity (clamped to 0..1); {@code seconds} the instance's real age in seconds
     * (drives the flicker, which does not jump at sustain loops); {@code seed} the instance seed (0..1), so two
     * instances flicker differently; {@code flickerScale} (0..1) scales the flicker amount (the engine passes the
     * screen-effects intensity, a quarter of it when the player hides lightning flashes).
     */
    public State sample(Timeline.Sampler timeline, float envelope, double seconds, float seed, float flickerScale) {
        Objects.requireNonNull(timeline, "timeline");
        float weight = Float.isNaN(envelope) ? 0 : Math.clamp(envelope, 0f, 1f);
        if (weightPhase != null) weight *= timeline.weight(weightPhase);
        weight *= value(Param.WEIGHT, 1, timeline);
        float amount = value(Param.FLICKER, flicker, timeline) * (Float.isNaN(flickerScale) ? 0 : Math.clamp(flickerScale, 0f, 1f));
        float lit = value(Param.INTENSITY, intensity, timeline)
            * flickerFactor(amount, value(Param.FLICKER_SPEED, flickerSpeed, timeline), seconds, seed);
        return new State(Math.clamp(weight, 0f, 1f), value(Param.X, 0, timeline), value(Param.Y, 0, timeline),
            value(Param.Z, 0, timeline), color(), Math.clamp(lit, 0f, MAX_INTENSITY), value(Param.RADIUS, radius, timeline),
            value(Param.FALLOFF, falloff, timeline), value(Param.WIDTH, width, timeline), value(Param.HEIGHT, height, timeline));
    }

    private float value(Param param, float constant, Timeline.Sampler timeline) {
        String track = tracks.get(param);
        return track == null ? constant : param.clamp(timeline.value(track));
    }

    /**
     * Flicker multiplier of the intensity, pure: {@code 1 - amount * n}, with {@code n} a smooth value noise in 0..1
     * that changes about {@code speed} times per second, offset by {@code seed}. 1 when amount or speed is 0;
     * always within {@code [1 - amount, 1]}.
     */
    public static float flickerFactor(float amount, float speed, double seconds, float seed) {
        if (!(amount > 0) || !(speed > 0) || !Double.isFinite(seconds)) return 1;
        double x = seconds * speed + seed * 61.7;
        double cell = Math.floor(x), f = x - cell;
        double u = f * f * (3 - 2 * f);
        double a = hash(cell, seed), b = hash(cell + 1, seed);
        double n = a + (b - a) * u;
        // A second, faster octave gives the irregular look of fire.
        double x2 = x * 2.37 + 11.3, cell2 = Math.floor(x2), f2 = x2 - cell2, u2 = f2 * f2 * (3 - 2 * f2);
        double n2 = hash(cell2, seed + .5f) + (hash(cell2 + 1, seed + .5f) - hash(cell2, seed + .5f)) * u2;
        return (float) (1 - Math.min(amount, 1) * Math.clamp(n * .7 + n2 * .3, 0, 1));
    }

    private static double hash(double cell, float seed) {
        double s = Math.sin(cell * 127.1 + seed * 311.7) * 43758.5453;
        return s - Math.floor(s);
    }

    /** Builder of a {@link Light}. Every setter validates its arguments right away. */
    public static final class Builder {
        private final Shape shape;
        private final Point from, to;
        private final Beam beam;
        private int color = 0xffffffff;
        private boolean colorSet;
        private float intensity = 1, radius, falloff = 2, width, height = 3, flicker, flickerSpeed = 6;
        private final Map<Param, String> tracks = new EnumMap<>(Param.class);
        private String weightPhase;

        private Builder(Shape shape, Point from, Point to, Beam beam, float radius, float width) {
            this.shape = shape; this.from = from; this.to = to; this.beam = beam; this.radius = radius; this.width = width;
        }

        /** Light color (ARGB, alpha ignored); white by default, the halo color for {@link Light#along(Beam)}. */
        public Builder color(int argb) {
            color = argb; colorSet = true;
            return this;
        }

        /** Brightness multiplier, 0..{@value #MAX_INTENSITY} (1 by default). */
        public Builder intensity(float value) {
            intensity = range("intensity", value, 0, MAX_INTENSITY);
            return this;
        }

        /** Reach of a point or segment light, or the radius of a disc or ring, in blocks (0..{@value #MAX_RADIUS}). */
        public Builder radius(float blocks) {
            radius = range("radius", blocks, 0, MAX_RADIUS);
            return this;
        }

        /** Attenuation exponent of a point or segment light, 0.25..4 (2 by default). */
        public Builder falloff(float exponent) {
            falloff = range("falloff", exponent, .25f, 4);
            return this;
        }

        /** Ring band width or disc edge softness, blocks (0..64). */
        public Builder width(float blocks) {
            width = range("width", blocks, 0, 64);
            return this;
        }

        /** Vertical reach of a disc or ring above and below its center, blocks (0.25..32, 3 by default). */
        public Builder height(float blocks) {
            height = range("height", blocks, .25f, 32);
            return this;
        }

        /** Flicker: {@code amount} 0..1 of the intensity, about {@code speed} changes per second (0..20). */
        public Builder flicker(float amount, float speed) {
            flicker = range("flicker", amount, 0, 1);
            flickerSpeed = range("flicker speed", speed, 0, 20);
            return this;
        }

        /**
         * Takes {@code param} from a track of the effect's timeline instead of the constant (clamped to the parameter
         * range). One track per parameter; a track may feed several parameters.
         */
        public Builder track(Param param, String track) {
            Objects.requireNonNull(param, "param");
            checkName(track, "track");
            if (tracks.putIfAbsent(param, track) != null)
                throw new IllegalArgumentException("Parameter already bound to a track: " + param);
            return this;
        }

        /** Multiplies the light weight by the weight of a timeline phase (0 outside it). */
        public Builder weightPhase(String phase) {
            checkName(phase, "phase");
            if (weightPhase != null) throw new IllegalArgumentException("Weight phase already defined");
            weightPhase = phase;
            return this;
        }

        /** Immutable light. Tracks, phase and the linked beam are checked at registration. */
        public Light build() { return new Light(this); }

        static float range(String what, float value, float min, float max) {
            if (!(value >= min && value <= max))
                throw new IllegalArgumentException(what + " must be within " + min + ".." + max);
            return value;
        }

        private static void checkName(String name, String kind) {
            if (name == null || !NAME.matcher(name).matches())
                throw new IllegalArgumentException("Invalid " + kind + " name (expected [a-z0-9_], up to 64): " + name);
        }
    }
}
