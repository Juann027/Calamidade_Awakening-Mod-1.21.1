package dev.calamidade_awakening.api.v1;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable sky layer of one effect (0.12.0): a fantastic object painted on the sky by direction, such as a black
 * hole, a portal opening onto another galaxy, an eclipse, a rift, a giant moon or planet, or an aurora. Pure Java, no
 * Minecraft types, so the registry, the client and tests share the same math.
 *
 * <ul>
 * <li><b>Where</b>: an {@link Anchor}: a fixed direction ({@link Builder#direction azimuth and elevation} in degrees,
 * Minecraft convention: azimuth 0 = south, 90 = west, 180 = north, -90 = east; elevation 90 = zenith), a far world
 * point ({@link Builder#toward}), a point relative to the instance center ({@link Builder#centerOffset}, in the
 * instance frame: x left, y up, z forward) or the vanilla {@link Builder#onSun() sun} or {@link Builder#onMoon() moon}.
 * Tracks {@link Param#AZIMUTH} and {@link Param#ELEVATION} add degrees to the anchor direction (a moon that rises).</li>
 * <li><b>Size</b>: angular diameter in degrees, 0..{@value #MAX_SIZE} (the sun of the game is about 9 degrees wide
 * with its glow). <b>Rotation</b> (roll, degrees) plus <b>spin</b> (degrees per tick, applied over the instance's real
 * age, so it never jumps at sustain loops), <b>tilt</b> (degrees: the accretion disk of a black hole, the ring of a
 * portal or a planet seen at an angle).</li>
 * <li><b>Colors</b> ARGB (alpha ignored): primary and secondary, with a per-type meaning (see {@link Type}).
 * <b>Intensity</b> 0..{@value #MAX_INTENSITY}, <b>progress</b> 0..1 (per type: formation, opening, coverage, phase,
 * activity), <b>lens</b> 0..1 (gravitational lens of a black hole, which also bends the terrain around it) and
 * <b>darken</b> 0..1 (darkens the world while the layer is active: the eclipse).</li>
 * </ul>
 *
 * <p>Every numeric parameter can come from a track of the effect's {@link Timeline} ({@link Builder#track}), clamped
 * to the parameter range. The layer weight is the instance's opacity envelope, times a fade over the last quarter of
 * the effect's range, times an optional phase ({@link Builder#weightPhase}) and an optional {@link Param#WEIGHT} track.
 * The client paints every active layer in one engine pass per frame ("sky"), outside the addon pass budget: on sky
 * pixels only, except the lens, which bends the whole scene near a black hole, and the darkening. At most 2/4/6/8
 * layers are painted per frame (LOW/BALANCED/HIGH/ULTRA), HERO effects first, then by weight.
 *
 * <p>Accessibility: darkening and flashes follow the screen-effects intensity and "hide lightning flashes" (darkening
 * then changes slowly, like the atmosphere exposure); spin, the lens swirl and the portal motion stop when the player
 * turns "motion effects" off, and the lens is halved.
 *
 * <pre>{@code
 * static final Sky BURACO = Sky.blackHole()
 *     .direction(30, 35)                       // azimute, elevação (graus)
 *     .size(26)                                // diâmetro angular do disco de acreção
 *     .track(Sky.Param.SIZE, "tamanho")        // cresce e colapsa pela timeline
 *     .track(Sky.Param.PROGRESS, "disco")
 *     .build();
 * // Construtor do mod: register, timeline, sky.
 * Effects.sky(ID, BURACO);
 * }</pre>
 *
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class Sky {
    /** Most sky layers per effect ({@link Effects#sky}). */
    public static final int MAX_PER_EFFECT = 6;
    /** Largest angular diameter, degrees. */
    public static final float MAX_SIZE = 180f;
    /** Largest intensity multiplier. */
    public static final float MAX_INTENSITY = 8f;
    /** Largest spin, degrees per tick (36 = one turn every half second). */
    public static final float MAX_SPIN = 36f;
    /** Weight below which a layer is not painted. */
    public static final float ACTIVE_WEIGHT = 1e-3f;
    /** Largest distance of a {@link Builder#centerOffset center offset} or {@link Builder#toward world point}
     *  coordinate from the origin of its frame, blocks. */
    public static final double MAX_DISTANCE = 1.0e7;

    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");

    /**
     * What the layer paints. Every type is procedural (a shader, no texture), so any size stays sharp.
     */
    public enum Type {
        /**
         * Black disk (the shadow, a fifth of the size), a thin and very bright photon ring, a wide tilted accretion disk
         * crossing the shadow from side to side with its far half bent over the top of the shadow by the lens, the sky
         * around slightly darkened, and a gravitational lens that bends the sky and the terrain around it. Size: outer
         * diameter of the disk. Primary:
         * hot inner color; secondary: outer color of the disk. Progress: how much of the disk is lit (0 = a bare
         * shadow, 1 = a full disk). Tilt: disk inclination (90 = edge-on). Lens: bending strength.
         */
        BLACK_HOLE,
        /**
         * Energy ring that opens and reveals another sky inside: nebula, dense stars and a spinning spiral galaxy.
         * Size: full diameter when open. Primary: ring color; secondary: nebula color. Progress: opening (0 = closed,
         * 1 = open). Tilt: ring seen at an angle. Spin: galaxy rotation.
         */
        PORTAL,
        /**
         * Disk that covers a sun (the vanilla one with {@link Builder#onSun()}, which is hidden and redrawn) with a
         * corona and streamers at totality, or a reddened moon with {@link Builder#onMoon()}. Size: diameter of the
         * covering disk. Primary: corona color; secondary: the color of the last light and prominences (sun) or of the
         * umbra (moon). Progress: coverage (0 = beside the sun, 1 = total). Darken scales with the covered fraction.
         */
        ECLIPSE,
        /**
         * Jagged crack across the sky with an inner glow and a darker void, small branches and flicker. Size: length.
         * Rotation: direction of the crack. Primary: glow; secondary: void inside. Progress: opening width.
         */
        RIFT,
        /**
         * Lit sphere: a giant moon or planet with phases, a lit limb and, with a non-zero tilt, a ring system. Size:
         * diameter of the sphere. Primary: surface; secondary: atmosphere rim and bands. Progress: lit fraction (0 =
         * new, 0.5 = half, 1 = full). Rotation: side the light comes from.
         */
        CELESTIAL_BODY,
        /**
         * Waving curtains of light along the horizon. The anchor direction is the middle of the band and its
         * elevation the bottom of the curtains; size: azimuth span of the band. Primary: lower color; secondary: upper
         * color. Progress: activity (brightness and height).
         */
        AURORA,
        /**
         * Distant lightning (0.12.0, "raios ao longe"): bolts and beams that fall far away, at random spots around each
         * player (70 to 300 blocks), with a flash on the sky. Deterministic by the instance seed and its real age, so
         * every client sees the same rhythm without any network traffic; purely visual (real lightning with damage
         * stays a cue's job). The anchor direction is the middle of the sector where they fall; size: width of that
         * sector in degrees (180 = all around). Primary: core color; secondary: glow and sky flash. Progress: activity
         * (0 = none, 1 = about two strikes per second). Terrain in front hides them. With "hide lightning flashes" the
         * bolts fade in and out without strobing, dimmer, and the sky flash is off.
         */
        DISTANT_BOLTS
    }

    /** Where a layer is placed on the sky. */
    public enum Anchor {
        /** Fixed azimuth and elevation (world axes: does not turn with the instance). */
        DIRECTION,
        /** Toward an absolute world point (blocks); far points barely move as the player walks. */
        WORLD,
        /** Toward a point relative to the instance center (blocks, instance frame: x left, y up, z forward). */
        CENTER,
        /** On the vanilla sun, following the time of day. */
        SUN,
        /** On the vanilla moon (opposite the sun). */
        MOON
    }

    /** Numeric parameters that can be driven by a timeline track, each with the range used to clamp it. */
    public enum Param {
        /** Degrees added to the anchor azimuth. */
        AZIMUTH(-360, 360),
        /** Degrees added to the anchor elevation (the result is kept within -90..90). */
        ELEVATION(-180, 180),
        /** Angular diameter, degrees. */
        SIZE(0, MAX_SIZE),
        /** Roll, degrees. */
        ROTATION(-3600, 3600),
        /** Spin, degrees per tick over the instance's real age. */
        SPIN(-MAX_SPIN, MAX_SPIN),
        /** Inclination, degrees. */
        TILT(-90, 90),
        /** Brightness multiplier. */
        INTENSITY(0, MAX_INTENSITY),
        /** Per-type progress (formation, opening, coverage, lit fraction, activity), 0..1. */
        PROGRESS(0, 1),
        /** Gravitational lens strength of a black hole, 0..1 (ignored by the other types). */
        LENS(0, 1),
        /** World darkening while the layer is active, 0..1. */
        DARKEN(0, 1),
        /** Extra multiplier of the layer weight, 0..1 (the envelope and the phase still apply). */
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
     * Unit direction on the sky in world axes (x east, y up, z south: Minecraft's axes), with conversions to and from
     * azimuth and elevation in degrees (Minecraft convention: azimuth 0 = south, 90 = west, 180 = north, -90 = east,
     * the same as an entity yaw; elevation 90 = straight up, the opposite sign of a pitch).
     *
     * @param x east component
     * @param y up component
     * @param z south component
     */
    public record Direction(double x, double y, double z) {
        /** Straight up. */
        public static final Direction UP = new Direction(0, 1, 0);

        public Direction {
            double length = Math.sqrt(x * x + y * y + z * z);
            if (!(length > 1e-9) || !Double.isFinite(length))
                throw new IllegalArgumentException("Direction needs a finite, non-zero vector");
            x /= length; y /= length; z /= length;
        }

        /**
         * Direction of an azimuth and an elevation, degrees: {@code x = -sin(az) cos(el)}, {@code y = sin(el)},
         * {@code z = cos(az) cos(el)} (azimuth 90 points west, like an entity yaw of 90).
         */
        public static Direction of(double azimuth, double elevation) {
            double az = Math.toRadians(azimuth), el = Math.toRadians(Math.clamp(elevation, -90, 90));
            return new Direction(-Math.sin(az) * Math.cos(el), Math.sin(el), Math.cos(az) * Math.cos(el));
        }

        /** Direction from a point toward another one (blocks); throws when they coincide. */
        public static Direction toward(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
            return new Direction(toX - fromX, toY - fromY, toZ - fromZ);
        }

        /** Azimuth in degrees, [-180, 180); 0 when the direction is vertical. */
        public double azimuth() {
            if (Math.abs(x) < 1e-12 && Math.abs(z) < 1e-12) return 0;
            double a = Math.toDegrees(Math.atan2(-x, z));
            return a >= 180 ? a - 360 : a;
        }

        /** Elevation in degrees, -90..90. */
        public double elevation() { return Math.toDegrees(Math.asin(Math.clamp(y, -1, 1))); }

        /** Angle to another direction, degrees 0..180 (stable for tiny and for nearly opposite angles). */
        public double angleTo(Direction other) {
            double cx = y * other.z - z * other.y, cy = z * other.x - x * other.z, cz = x * other.y - y * other.x;
            return Math.toDegrees(Math.atan2(Math.sqrt(cx * cx + cy * cy + cz * cz), x * other.x + y * other.y + z * other.z));
        }

        /** This direction moved by {@code azimuth} and {@code elevation} degrees (the elevation is kept within -90..90). */
        public Direction offset(double azimuth, double elevation) {
            if (azimuth == 0 && elevation == 0) return this;
            return of(azimuth() + azimuth, Math.clamp(elevation() + elevation, -90, 90));
        }
    }

    /**
     * Resolved layer at one moment: parameters already taken from tracks and clamped, plus the weight (0..1). The
     * client adds the azimuth and elevation offsets to the anchor direction ({@link #direction(Direction)}).
     *
     * @param type      what the layer paints
     * @param weight    layer weight, 0..1
     * @param azimuth   degrees added to the anchor azimuth
     * @param elevation degrees added to the anchor elevation
     * @param size      angular diameter, degrees
     * @param rotation  roll, degrees (spin not included)
     * @param spin      spin, degrees per tick
     * @param tilt      inclination, degrees
     * @param intensity brightness multiplier
     * @param progress  per-type progress, 0..1
     * @param lens      lens strength, 0..1
     * @param darken    world darkening, 0..1 (before {@link #darkening()})
     * @param primary   primary color, ARGB (alpha ignored)
     * @param secondary secondary color, ARGB (alpha ignored)
     */
    public record State(Type type, float weight, float azimuth, float elevation, float size, float rotation, float spin,
                        float tilt, float intensity, float progress, float lens, float darken, int primary,
                        int secondary) {
        public State {
            Objects.requireNonNull(type, "type");
        }

        /** True when the engine paints this layer: weight above {@link #ACTIVE_WEIGHT}, a size and some brightness. */
        public boolean visible() {
            return weight > ACTIVE_WEIGHT && size > 0 && (intensity > 0 || type == Type.BLACK_HOLE || type == Type.ECLIPSE);
        }

        /** Anchor direction moved by this state's azimuth and elevation offsets. */
        public Direction direction(Direction anchor) { return Objects.requireNonNull(anchor).offset(azimuth, elevation); }

        /** Angular radius, degrees ({@code size / 2}). */
        public float radius() { return size * .5f; }

        /**
         * World darkening this layer asks for, 0..1, before accessibility: {@code darken × weight}, and for an
         * {@link Type#ECLIPSE} also times the fourth power of the covered fraction of the sun
         * ({@link Sky#eclipseCoverage}), so the world only goes dark close to totality.
         */
        public float darkening() {
            float base = darken * weight;
            if (type != Type.ECLIPSE) return base;
            float covered = eclipseCoverage(progress);
            return base * covered * covered * covered * covered;
        }

        /** Rotation in degrees after {@code realAge} ticks of spin, wrapped to [0, 360). */
        public double rotationAt(double realAge) {
            double angle = (rotation + spin * realAge) % 360.0;
            return angle < 0 ? angle + 360 : angle;
        }
    }

    /** Ratio between the radius of the sun an {@link Type#ECLIPSE} draws and the radius of its covering disk. */
    public static final float ECLIPSE_SUN_RATIO = .97f;
    /** Distance between the centers of the eclipse disk and its sun at progress 0, in covering-disk radii. */
    public static final float ECLIPSE_START_DISTANCE = 2.2f;

    /**
     * Fraction of the sun hidden by an {@link Type#ECLIPSE} at {@code progress} (0..1, clamped): the overlap area of the
     * covering disk (radius 1) and the sun (radius {@value #ECLIPSE_SUN_RATIO}) whose centers are
     * {@code (1 - progress) × }{@value #ECLIPSE_START_DISTANCE} radii apart, over the sun's area. 0 at progress 0, 1 at
     * totality (from progress ≈ 0.986 on).
     */
    public static float eclipseCoverage(float progress) {
        double p = Float.isNaN(progress) ? 0 : Math.clamp(progress, 0, 1);
        double d = (1 - p) * ECLIPSE_START_DISTANCE, big = 1, small = ECLIPSE_SUN_RATIO;
        if (d >= big + small) return 0;
        if (d <= big - small) return 1;
        double a = Math.acos(Math.clamp((d * d + small * small - big * big) / (2 * d * small), -1, 1));
        double b = Math.acos(Math.clamp((d * d + big * big - small * small) / (2 * d * big), -1, 1));
        double area = small * small * a + big * big * b
            - .5 * Math.sqrt(Math.max(0, (-d + small + big) * (d + small - big) * (d - small + big) * (d + small + big)));
        return (float) Math.clamp(area / (Math.PI * small * small), 0, 1);
    }

    private final Type type;
    private final Anchor anchor;
    private final double ax, ay, az;
    private final float size, rotation, spin, tilt, intensity, progress, lens, darken;
    private final int primary, secondary;
    private final boolean hidesVanilla;
    private final Map<Param, String> tracks;
    private final String weightPhase;

    private Sky(Builder b) {
        type = b.type; anchor = b.anchor; ax = b.ax; ay = b.ay; az = b.az;
        size = b.size; rotation = b.rotation; spin = b.spin; tilt = b.tilt; intensity = b.intensity;
        progress = b.progress; lens = b.lens; darken = b.darken; primary = b.primary; secondary = b.secondary;
        hidesVanilla = b.hidesVanilla != null ? b.hidesVanilla
            : (anchor == Anchor.SUN || anchor == Anchor.MOON) && type != Type.RIFT && type != Type.AURORA
                && type != Type.DISTANT_BOLTS;
        tracks = Collections.unmodifiableMap(new EnumMap<>(b.tracks));
        weightPhase = b.weightPhase;
    }

    /**
     * New builder of a layer of {@code type}, with the defaults of that type (size, colors, progress, tilt, lens,
     * darken and anchor: an {@link Type#ECLIPSE} starts on the sun, an {@link Type#AURORA} at azimuth 180 and
     * elevation 12, the others at azimuth 0 and elevation 35).
     */
    public static Builder builder(Type type) { return new Builder(type); }

    /** {@code builder(Type.BLACK_HOLE)}: 40 degrees, tilt 80 (a wide disk seen almost edge-on), lens 0.6, spin 1.2 degrees per tick. */
    public static Builder blackHole() { return builder(Type.BLACK_HOLE); }
    /** {@code builder(Type.PORTAL)}: 30 degrees, open, galaxy spin 0.5 degrees per tick. */
    public static Builder portal() { return builder(Type.PORTAL); }
    /** {@code builder(Type.ECLIPSE)}: on the sun, 13 degrees, total, darken 0.75. */
    public static Builder eclipse() { return builder(Type.ECLIPSE); }
    /** {@code builder(Type.RIFT)}: 50 degrees long, rotation 20, open. */
    public static Builder rift() { return builder(Type.RIFT); }
    /** {@code builder(Type.CELESTIAL_BODY)}: 28 degrees, full. */
    public static Builder body() { return builder(Type.CELESTIAL_BODY); }
    /** {@code builder(Type.AURORA)}: 140 degrees of azimuth, toward the north, curtains from 12 degrees up. */
    public static Builder aurora() { return builder(Type.AURORA); }
    /**
     * {@code builder(Type.DISTANT_BOLTS)} (0.12.0): bolts all around (size 180), activity 0.6, white core and violet
     * glow.
     */
    public static Builder bolts() { return builder(Type.DISTANT_BOLTS); }

    /** What the layer paints. */
    public Type type() { return type; }
    /** Where the layer is placed. */
    public Anchor anchor() { return anchor; }
    /** Azimuth (degrees) for {@link Anchor#DIRECTION}, x (blocks) for {@link Anchor#WORLD} and {@link Anchor#CENTER}; 0 otherwise. */
    public double anchorX() { return ax; }
    /** Elevation (degrees) for {@link Anchor#DIRECTION}, y (blocks) for {@link Anchor#WORLD} and {@link Anchor#CENTER}; 0 otherwise. */
    public double anchorY() { return ay; }
    /** z (blocks) for {@link Anchor#WORLD} and {@link Anchor#CENTER}; 0 otherwise. */
    public double anchorZ() { return az; }
    /** Angular diameter, degrees. */
    public float size() { return size; }
    /** Roll, degrees. */
    public float rotation() { return rotation; }
    /** Spin, degrees per tick. */
    public float spin() { return spin; }
    /** Inclination, degrees. */
    public float tilt() { return tilt; }
    /** Brightness multiplier, 0..{@value #MAX_INTENSITY}. */
    public float intensity() { return intensity; }
    /** Per-type progress, 0..1. */
    public float progress() { return progress; }
    /** Lens strength, 0..1. */
    public float lens() { return lens; }
    /** World darkening, 0..1. */
    public float darken() { return darken; }
    /** Primary color, ARGB (alpha ignored). */
    public int primary() { return primary; }
    /** Secondary color, ARGB (alpha ignored). */
    public int secondary() { return secondary; }
    /**
     * True when the layer hides the vanilla sun or moon it is anchored on while it is active (by default for every
     * type anchored on the sun or the moon except {@link Type#RIFT}, {@link Type#AURORA} and
     * {@link Type#DISTANT_BOLTS}).
     */
    public boolean hidesVanilla() { return hidesVanilla; }
    /** Track bound to a parameter, or null when the parameter is constant. */
    public String track(Param param) { return tracks.get(Objects.requireNonNull(param)); }
    /** Every track binding, in {@link Param} order. */
    public Map<Param, String> tracks() { return tracks; }
    /** Phase whose weight multiplies the layer weight, or null. */
    public String weightPhase() { return weightPhase; }
    /** True when some parameter, the weight or the phase depends on the timeline. */
    public boolean animated() { return !tracks.isEmpty() || weightPhase != null; }

    /**
     * Checks that every track and the phase this layer reads exist in {@code timeline}, which may be null only when
     * nothing is {@link #animated() animated}. Called by {@link Effects#sky}.
     */
    public void validate(Timeline timeline) {
        if (!animated()) return;
        if (timeline == null)
            throw new IllegalArgumentException("Sky layer reads tracks or a phase: register the effect's timeline first");
        tracks.forEach((param, track) -> {
            if (!timeline.hasTrack(track)) throw new IllegalArgumentException("Sky " + param + " reads unknown track: " + track);
        });
        if (weightPhase != null && !timeline.hasPhase(weightPhase))
            throw new IllegalArgumentException("Sky weight reads unknown phase: " + weightPhase);
    }

    /**
     * Resolves this layer at one moment. {@code timeline} is the instance's sampler at its local age (any sampler works
     * when nothing is animated); {@code envelope} is the instance's weight before the layer (opacity envelope times the
     * range fade, clamped to 0..1), which becomes the layer weight, times the phase and the {@link Param#WEIGHT} track.
     */
    public State sample(Timeline.Sampler timeline, float envelope) {
        Objects.requireNonNull(timeline, "timeline");
        float weight = Float.isNaN(envelope) ? 0 : Math.clamp(envelope, 0f, 1f);
        if (weightPhase != null) weight *= timeline.weight(weightPhase);
        weight *= value(Param.WEIGHT, 1, timeline);
        return new State(type, Math.clamp(weight, 0f, 1f), value(Param.AZIMUTH, 0, timeline),
            value(Param.ELEVATION, 0, timeline), value(Param.SIZE, size, timeline), value(Param.ROTATION, rotation, timeline),
            value(Param.SPIN, spin, timeline), value(Param.TILT, tilt, timeline), value(Param.INTENSITY, intensity, timeline),
            value(Param.PROGRESS, progress, timeline), value(Param.LENS, lens, timeline), value(Param.DARKEN, darken, timeline),
            primary, secondary);
    }

    private float value(Param param, float constant, Timeline.Sampler timeline) {
        String track = tracks.get(param);
        return track == null ? constant : param.clamp(timeline.value(track));
    }

    /** Builder of a {@link Sky} layer. Every setter validates its arguments right away. */
    public static final class Builder {
        private final Type type;
        private Anchor anchor = Anchor.DIRECTION;
        private double ax, ay = 35, az;
        private float size, rotation, spin, tilt, intensity = 1, progress = 1, lens, darken;
        private int primary, secondary;
        private Boolean hidesVanilla;
        private final Map<Param, String> tracks = new EnumMap<>(Param.class);
        private String weightPhase;

        private Builder(Type type) {
            this.type = Objects.requireNonNull(type, "type");
            switch (type) {
                case BLACK_HOLE -> { size = 40; tilt = 80; lens = .6f; spin = 1.2f; intensity = 1.4f;
                    primary = 0xffffe6c0; secondary = 0xffff6a20; }
                case PORTAL -> { size = 30; spin = .5f; intensity = 1.2f; primary = 0xffb8ecff; secondary = 0xffb040ff; }
                case ECLIPSE -> { anchor = Anchor.SUN; ay = 0; size = 13; darken = .75f; primary = 0xffeaf2ff;
                    secondary = 0xffff5a3a; }
                case RIFT -> { size = 50; rotation = 20; intensity = 1.5f; primary = 0xffff3a8a; secondary = 0xff1a0036; }
                case CELESTIAL_BODY -> { size = 28; primary = 0xffc9bba0; secondary = 0xff7ab8ff; }
                case AURORA -> { ax = 180; ay = 12; size = 140; primary = 0xff40ff9a; secondary = 0xffb050ff; }
                case DISTANT_BOLTS -> { ay = 0; size = 180; progress = .6f; intensity = 1.2f; primary = 0xffeef4ff;
                    secondary = 0xff9a7cff; }
            }
        }

        /**
         * Fixed direction: {@code azimuth} in degrees (0 = south, 90 = west, 180 = north, -90 = east; any value, wrapped)
         * and {@code elevation} in degrees above the horizon, -90..90. World axes: the direction does not turn with the
         * instance orientation.
         */
        public Builder direction(float azimuth, float elevation) {
            if (!Float.isFinite(azimuth) || !(elevation >= -90 && elevation <= 90))
                throw new IllegalArgumentException("Expected a finite azimuth and an elevation in -90..90 degrees");
            anchor = Anchor.DIRECTION; ax = azimuth; ay = elevation; az = 0;
            return this;
        }

        /**
         * Toward an absolute world point (blocks, each coordinate within ±{@value #MAX_DISTANCE}). Pick a far point
         * (thousands of blocks) for an object that barely moves as players walk; a near one moves like a real object.
         */
        public Builder toward(double x, double y, double z) {
            anchor = Anchor.WORLD; ax = coordinate(x); ay = coordinate(y); az = coordinate(z);
            return this;
        }

        /**
         * Toward a point relative to the instance center (blocks, instance frame: x left, y up, z forward; each within
         * ±{@value #MAX_DISTANCE}). {@code centerOffset(0, 400, 0)} keeps the layer 400 blocks above the boss that
         * started the effect, seen from the whole arena.
         */
        public Builder centerOffset(double x, double y, double z) {
            anchor = Anchor.CENTER; ax = coordinate(x); ay = coordinate(y); az = coordinate(z);
            return this;
        }

        /** On the vanilla sun (it follows the time of day; the sun itself is hidden unless {@link #hidesVanilla(boolean)} says no). */
        public Builder onSun() {
            anchor = Anchor.SUN; ax = ay = az = 0;
            return this;
        }

        /** On the vanilla moon (opposite the sun; the moon itself is hidden unless {@link #hidesVanilla(boolean)} says no). */
        public Builder onMoon() {
            anchor = Anchor.MOON; ax = ay = az = 0;
            return this;
        }

        /**
         * Whether the vanilla sun or moon this layer is anchored on is hidden while the layer is active (scaled by its
         * weight). Ignored for the other anchors.
         */
        public Builder hidesVanilla(boolean hide) {
            hidesVanilla = hide;
            return this;
        }

        /** Angular diameter, degrees, 0..{@value #MAX_SIZE}. */
        public Builder size(float degrees) {
            size = range("size", degrees, 0, MAX_SIZE);
            return this;
        }

        /** Roll, degrees, -3600..3600. */
        public Builder rotation(float degrees) {
            rotation = range("rotation", degrees, -3600, 3600);
            return this;
        }

        /** Spin, degrees per tick over the real age, -{@value #MAX_SPIN}..{@value #MAX_SPIN}; stops with motion effects off. */
        public Builder spin(float degreesPerTick) {
            spin = range("spin", degreesPerTick, -MAX_SPIN, MAX_SPIN);
            return this;
        }

        /** Inclination, degrees, -90..90 (disk of a black hole, ring of a portal, rings of a planet). */
        public Builder tilt(float degrees) {
            tilt = range("tilt", degrees, -90, 90);
            return this;
        }

        /** Brightness multiplier, 0..{@value #MAX_INTENSITY}. */
        public Builder intensity(float value) {
            intensity = range("intensity", value, 0, MAX_INTENSITY);
            return this;
        }

        /** Per-type progress, 0..1 (see {@link Type}); usually a track: {@code track(Param.PROGRESS, "abrir")}. */
        public Builder progress(float value) {
            progress = range("progress", value, 0, 1);
            return this;
        }

        /** Gravitational lens strength of a {@link Type#BLACK_HOLE}, 0..1 (0 = no bending). */
        public Builder lens(float strength) {
            lens = range("lens", strength, 0, 1);
            return this;
        }

        /** World darkening while the layer is active, 0..1 (an eclipse also scales it by the covered fraction). */
        public Builder darken(float amount) {
            darken = range("darken", amount, 0, 1);
            return this;
        }

        /** Primary and secondary colors (ARGB, alpha ignored); see {@link Type} for what each one paints. */
        public Builder color(int primary, int secondary) {
            this.primary = primary; this.secondary = secondary;
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

        /** Multiplies the layer weight by the weight of a timeline phase (0 outside it). */
        public Builder weightPhase(String phase) {
            checkName(phase, "phase");
            if (weightPhase != null) throw new IllegalArgumentException("Weight phase already defined");
            weightPhase = phase;
            return this;
        }

        /** Immutable layer. Tracks and phase are checked against the timeline at registration. */
        public Sky build() { return new Sky(this); }

        private static double coordinate(double value) {
            if (!(Math.abs(value) <= MAX_DISTANCE))
                throw new IllegalArgumentException("Coordinates must be finite and within ±" + MAX_DISTANCE + " blocks");
            return value;
        }

        private static float range(String what, float value, float min, float max) {
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
