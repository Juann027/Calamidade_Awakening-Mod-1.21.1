package dev.calamidade_awakening.api.v1;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable beam of light between two {@link Point points} (0.9.0): a white-hot core inside a saturated halo, with
 * optional pulses that run along it, a lateral wave and noise, and a progressive reveal (the beam "grows" from its
 * start to its end). Pure Java, no Minecraft types, so the registry, the client and tests share the same math.
 *
 * <p>The engine draws every visible beam of the frame in one screen-space pass ("beams"): ray-to-segment distance,
 * a minimum width of one pixel (far beams stay visible without aliasing, with their intensity scaled down instead
 * of growing fat) and occlusion by the depth buffer (terrain in front hides the beam). The pass is charged once per
 * frame to the HERO pass budget when a drawn HERO effect has beams, otherwise to the common addon budget. At most
 * 16/48/128 beams are drawn per frame (LOW/BALANCED/HIGH), taken from the most important effects first.
 *
 * <ul>
 * <li><b>Widths</b> in blocks (full width, not radius): core 0..{@value #MAX_CORE_WIDTH}, halo
 * 0..{@value #MAX_HALO_WIDTH}.</li>
 * <li><b>Colors</b> ARGB (alpha ignored): core (white by default) and halo.</li>
 * <li><b>Intensity</b>: brightness multiplier, 0..{@value #MAX_INTENSITY}.</li>
 * <li><b>Pulses</b>: count 0..{@value #MAX_PULSES} (rounded), speed in blocks per tick (negative runs from the end
 * to the start), size in blocks (length of each pulse).</li>
 * <li><b>Wave</b>: lateral amplitude in blocks, wavelength in blocks, speed in blocks per tick; <b>noise</b>: lateral
 * jitter amplitude in blocks. Both fade out within 2 blocks of each end, so the ends stay attached.</li>
 * <li><b>Reveal</b>: 0..1, the drawn fraction from the start (1 = whole beam), with a hot tip while growing.</li>
 * </ul>
 *
 * <p>Every numeric parameter can come from a track of the effect's {@link Timeline} ({@link Builder#track}), clamped
 * to the parameter range; {@link Param#FROM_X} and the other offset parameters add a track offset (blocks, in the
 * instance frame since 0.10.0: x left, y up, z forward, which are the world axes for an instance without orientation)
 * to the resolved ends. The beam weight is the instance's opacity envelope, times an optional phase
 * ({@link Builder#weightPhase}) and an optional {@link Param#WEIGHT} track. Pulses, wave and noise move with the
 * instance's real age, so they do not jump at sustain loops.
 *
 * <p>Accessibility: halo and pulses follow the player's screen-effects intensity (0 leaves only the core).
 *
 * <pre>{@code
 * static final Beam PILAR = Beam.builder(Point.center(0, 0, 0), Point.center(0, 120, 0))
 *     .width(.6f, 4f)
 *     .color(0xffffffff, 0xffffb040)
 *     .pulses(3, -1.5f, 6f)                 // three pulses falling from the sky
 *     .track(Beam.Param.REVEAL, "nascer")   // grows from the ground up
 *     .build();
 * // Mod constructor: register, timeline, beams.
 * Effects.beams(ID, PILAR);
 * }</pre>
 *
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class Beam {
    /** Most beams per effect ({@link Effects#beams}). */
    public static final int MAX_PER_EFFECT = 16;
    /** Largest core width, blocks. */
    public static final float MAX_CORE_WIDTH = 16f;
    /** Largest halo width, blocks. */
    public static final float MAX_HALO_WIDTH = 64f;
    /** Largest intensity multiplier. */
    public static final float MAX_INTENSITY = 8f;
    /** Most pulses on one beam. */
    public static final int MAX_PULSES = 16;
    /** Largest track offset of an end, blocks per axis. */
    public static final float MAX_TRACK_OFFSET = 256f;
    /** Weight below which a beam is not drawn. */
    public static final float ACTIVE_WEIGHT = 1e-3f;

    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");

    /** Numeric parameters that can be driven by a timeline track, each with the range used to clamp it. */
    public enum Param {
        /** Core width, blocks. */
        CORE_WIDTH(0, MAX_CORE_WIDTH),
        /** Halo width, blocks. */
        HALO_WIDTH(0, MAX_HALO_WIDTH),
        /** Brightness multiplier. */
        INTENSITY(0, MAX_INTENSITY),
        /** Number of pulses, rounded to the nearest integer. */
        PULSE_COUNT(0, MAX_PULSES),
        /** Pulse speed, blocks per tick (negative: from the end toward the start). */
        PULSE_SPEED(-8, 8),
        /** Length of each pulse, blocks. */
        PULSE_SIZE(0, 32),
        /** Lateral wave amplitude, blocks. */
        WAVE_AMPLITUDE(0, 8),
        /** Wavelength, blocks. */
        WAVE_LENGTH(.25f, 256),
        /** Wave speed along the beam, blocks per tick. */
        WAVE_SPEED(-8, 8),
        /** Lateral noise amplitude, blocks. */
        NOISE(0, 8),
        /** Drawn fraction from the start, 0..1. */
        REVEAL(0, 1),
        /** Extra multiplier of the beam weight, 0..1 (the envelope and the phase still apply). */
        WEIGHT(0, 1),
        /** Offset added to the start, x, blocks. */
        FROM_X(-MAX_TRACK_OFFSET, MAX_TRACK_OFFSET),
        /** Offset added to the start, y, blocks. */
        FROM_Y(-MAX_TRACK_OFFSET, MAX_TRACK_OFFSET),
        /** Offset added to the start, z, blocks. */
        FROM_Z(-MAX_TRACK_OFFSET, MAX_TRACK_OFFSET),
        /** Offset added to the end, x, blocks. */
        TO_X(-MAX_TRACK_OFFSET, MAX_TRACK_OFFSET),
        /** Offset added to the end, y, blocks. */
        TO_Y(-MAX_TRACK_OFFSET, MAX_TRACK_OFFSET),
        /** Offset added to the end, z, blocks. */
        TO_Z(-MAX_TRACK_OFFSET, MAX_TRACK_OFFSET);

        /** Smallest and largest value of the parameter; track values are clamped to this range. */
        public final float min, max;

        Param(float min, float max) { this.min = min; this.max = max; }

        /** Clamps a value into this parameter's range; NaN becomes the value closest to 0 in the range. */
        public float clamp(float value) {
            return Float.isNaN(value) ? Math.clamp(0, min, max) : Math.clamp(value, min, max);
        }
    }

    /**
     * Resolved beam at one moment: parameters already taken from tracks and clamped, the weight (0..1) and the track
     * offsets of both ends. The client adds the offsets to the resolved {@link Point points}.
     *
     * @param weight        beam weight, 0..1
     * @param fromX         track offset of the start, x, blocks
     * @param fromY         track offset of the start, y, blocks
     * @param fromZ         track offset of the start, z, blocks
     * @param toX           track offset of the end, x, blocks
     * @param toY           track offset of the end, y, blocks
     * @param toZ           track offset of the end, z, blocks
     * @param coreWidth     core width, blocks
     * @param haloWidth     halo width, blocks
     * @param coreColor     core color, ARGB (alpha ignored)
     * @param haloColor     halo color, ARGB (alpha ignored)
     * @param intensity     brightness multiplier, 0..{@value #MAX_INTENSITY}
     * @param pulses        number of pulses, 0..{@value #MAX_PULSES}
     * @param pulseSpeed    pulse speed, blocks per tick
     * @param pulseSize     pulse length, blocks
     * @param waveAmplitude lateral wave amplitude, blocks
     * @param waveLength    wavelength, blocks
     * @param waveSpeed     wave speed, blocks per tick
     * @param noise         lateral noise amplitude, blocks
     * @param reveal        drawn fraction from the start, 0..1
     */
    public record State(float weight, float fromX, float fromY, float fromZ, float toX, float toY, float toZ,
                        float coreWidth, float haloWidth, int coreColor, int haloColor, float intensity, int pulses,
                        float pulseSpeed, float pulseSize, float waveAmplitude, float waveLength, float waveSpeed,
                        float noise, float reveal) {
        /** True when the engine draws this beam: weight above {@link #ACTIVE_WEIGHT}, something revealed, lit and wide. */
        public boolean visible() {
            return weight > ACTIVE_WEIGHT && reveal > 0 && intensity > 0 && (coreWidth > 0 || haloWidth > 0);
        }
    }

    private final Point from, to;
    private final float coreWidth, haloWidth, intensity, pulseSpeed, pulseSize, waveAmplitude, waveLength, waveSpeed,
        noise, reveal;
    private final int coreColor, haloColor, pulses;
    private final Map<Param, String> tracks;
    private final String weightPhase;

    private Beam(Builder b) {
        from = b.from; to = b.to; coreWidth = b.coreWidth; haloWidth = b.haloWidth; intensity = b.intensity;
        coreColor = b.coreColor; haloColor = b.haloColor; pulses = b.pulses; pulseSpeed = b.pulseSpeed;
        pulseSize = b.pulseSize; waveAmplitude = b.waveAmplitude; waveLength = b.waveLength; waveSpeed = b.waveSpeed;
        noise = b.noise; reveal = b.reveal;
        tracks = Collections.unmodifiableMap(new EnumMap<>(b.tracks));
        weightPhase = b.weightPhase;
    }

    /**
     * New builder for a beam from {@code from} to {@code to} (different points). Defaults: core 0.2 and halo 1.2
     * blocks wide, white core, cyan halo (0xff66ccff), intensity 1, no pulses, no wave, no noise, fully revealed.
     */
    public static Builder builder(Point from, Point to) { return new Builder(from, to); }

    /** Start of the beam. */
    public Point from() { return from; }
    /** End of the beam. */
    public Point to() { return to; }
    /** Core width, blocks. */
    public float coreWidth() { return coreWidth; }
    /** Halo width, blocks. */
    public float haloWidth() { return haloWidth; }
    /** Core color, ARGB (alpha ignored). */
    public int coreColor() { return coreColor; }
    /** Halo color, ARGB (alpha ignored). */
    public int haloColor() { return haloColor; }
    /** Brightness multiplier, 0..{@value #MAX_INTENSITY}. */
    public float intensity() { return intensity; }
    /** Number of pulses, 0..{@value #MAX_PULSES}. */
    public int pulses() { return pulses; }
    /** Pulse speed, blocks per tick. */
    public float pulseSpeed() { return pulseSpeed; }
    /** Pulse length, blocks. */
    public float pulseSize() { return pulseSize; }
    /** Lateral wave amplitude, blocks. */
    public float waveAmplitude() { return waveAmplitude; }
    /** Wavelength, blocks. */
    public float waveLength() { return waveLength; }
    /** Wave speed, blocks per tick. */
    public float waveSpeed() { return waveSpeed; }
    /** Lateral noise amplitude, blocks. */
    public float noise() { return noise; }
    /** Drawn fraction from the start, 0..1. */
    public float reveal() { return reveal; }
    /** Track bound to a parameter, or null when the parameter is constant. */
    public String track(Param param) { return tracks.get(Objects.requireNonNull(param)); }
    /** Every track binding, in {@link Param} order. */
    public Map<Param, String> tracks() { return tracks; }
    /** Phase whose weight multiplies the beam weight, or null. */
    public String weightPhase() { return weightPhase; }
    /** True when some parameter, the weight or the phase depends on the timeline. */
    public boolean animated() { return !tracks.isEmpty() || weightPhase != null; }

    /**
     * Checks that every track and the phase this beam reads exist in {@code timeline}, which may be null only when
     * nothing is {@link #animated() animated}. Called by {@link Effects#beams}.
     */
    public void validate(Timeline timeline) {
        if (!animated()) return;
        if (timeline == null)
            throw new IllegalArgumentException("Beam reads tracks or a phase: register the effect's timeline first");
        tracks.forEach((param, track) -> {
            if (!timeline.hasTrack(track)) throw new IllegalArgumentException("Beam " + param + " reads unknown track: " + track);
        });
        if (weightPhase != null && !timeline.hasPhase(weightPhase))
            throw new IllegalArgumentException("Beam weight reads unknown phase: " + weightPhase);
    }

    /**
     * Resolves this beam at one moment. {@code timeline} is the instance's sampler at its local age (any sampler
     * works when nothing is animated); {@code envelope} is the instance's opacity (clamped to 0..1), which becomes
     * the weight, times the phase and the {@link Param#WEIGHT} track when bound.
     */
    public State sample(Timeline.Sampler timeline, float envelope) {
        Objects.requireNonNull(timeline, "timeline");
        float weight = Float.isNaN(envelope) ? 0 : Math.clamp(envelope, 0f, 1f);
        if (weightPhase != null) weight *= timeline.weight(weightPhase);
        weight *= value(Param.WEIGHT, 1, timeline);
        return new State(Math.clamp(weight, 0f, 1f),
            value(Param.FROM_X, 0, timeline), value(Param.FROM_Y, 0, timeline), value(Param.FROM_Z, 0, timeline),
            value(Param.TO_X, 0, timeline), value(Param.TO_Y, 0, timeline), value(Param.TO_Z, 0, timeline),
            value(Param.CORE_WIDTH, coreWidth, timeline), value(Param.HALO_WIDTH, haloWidth, timeline),
            coreColor, haloColor, value(Param.INTENSITY, intensity, timeline),
            Math.round(value(Param.PULSE_COUNT, pulses, timeline)), value(Param.PULSE_SPEED, pulseSpeed, timeline),
            value(Param.PULSE_SIZE, pulseSize, timeline), value(Param.WAVE_AMPLITUDE, waveAmplitude, timeline),
            value(Param.WAVE_LENGTH, waveLength, timeline), value(Param.WAVE_SPEED, waveSpeed, timeline),
            value(Param.NOISE, noise, timeline), value(Param.REVEAL, reveal, timeline));
    }

    private float value(Param param, float constant, Timeline.Sampler timeline) {
        String track = tracks.get(param);
        return track == null ? constant : param.clamp(timeline.value(track));
    }

    /** Builder of a {@link Beam}. Every setter validates its arguments right away. */
    public static final class Builder {
        private final Point from, to;
        private float coreWidth = .2f, haloWidth = 1.2f, intensity = 1, pulseSpeed, pulseSize = 2, waveAmplitude,
            waveLength = 4, waveSpeed, noise, reveal = 1;
        private int coreColor = 0xffffffff, haloColor = 0xff66ccff, pulses;
        private final Map<Param, String> tracks = new EnumMap<>(Param.class);
        private String weightPhase;

        private Builder(Point from, Point to) {
            this.from = Objects.requireNonNull(from, "from");
            this.to = Objects.requireNonNull(to, "to");
            if (from.equals(to)) throw new IllegalArgumentException("Beam ends must be different points: " + from);
        }

        /** Core and halo widths in blocks (full widths): core 0..{@value #MAX_CORE_WIDTH}, halo 0..{@value #MAX_HALO_WIDTH}. */
        public Builder width(float core, float halo) {
            coreWidth = range("core width", core, 0, MAX_CORE_WIDTH);
            haloWidth = range("halo width", halo, 0, MAX_HALO_WIDTH);
            return this;
        }

        /** Core and halo colors (ARGB, alpha ignored). A white core over a saturated halo reads as hot light. */
        public Builder color(int core, int halo) {
            coreColor = core; haloColor = halo;
            return this;
        }

        /** Halo color (ARGB, alpha ignored), keeping the white core. */
        public Builder color(int halo) { return color(0xffffffff, halo); }

        /** Brightness multiplier, 0..{@value #MAX_INTENSITY} (1 by default). */
        public Builder intensity(float value) {
            intensity = range("intensity", value, 0, MAX_INTENSITY);
            return this;
        }

        /**
         * Pulses running along the beam: {@code count} 0..{@value #MAX_PULSES}, evenly spaced over the full length;
         * {@code speed} in blocks per tick, -8..8 (negative runs from the end to the start); {@code size}, the length
         * of each pulse in blocks, 0..32.
         */
        public Builder pulses(int count, float speed, float size) {
            if (count < 0 || count > MAX_PULSES) throw new IllegalArgumentException("Pulse count must be within 0.." + MAX_PULSES);
            pulses = count;
            pulseSpeed = range("pulse speed", speed, -8, 8);
            pulseSize = range("pulse size", size, 0, 32);
            return this;
        }

        /**
         * Lateral wave: {@code amplitude} 0..8 blocks, {@code wavelength} 0.25..256 blocks, {@code speed} -8..8 blocks
         * per tick along the beam. Fades out within 2 blocks of each end.
         */
        public Builder wave(float amplitude, float wavelength, float speed) {
            waveAmplitude = range("wave amplitude", amplitude, 0, 8);
            waveLength = range("wavelength", wavelength, .25f, 256);
            waveSpeed = range("wave speed", speed, -8, 8);
            return this;
        }

        /** Lateral noise (a crackling, unstable beam), amplitude 0..8 blocks. Fades out within 2 blocks of each end. */
        public Builder noise(float amplitude) {
            noise = range("noise", amplitude, 0, 8);
            return this;
        }

        /** Drawn fraction from the start, 0..1 (1 by default). Usually a track: {@code track(Param.REVEAL, "nascer")}. */
        public Builder reveal(float fraction) {
            reveal = range("reveal", fraction, 0, 1);
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

        /** Multiplies the beam weight by the weight of a timeline phase (0 outside it). */
        public Builder weightPhase(String phase) {
            checkName(phase, "phase");
            if (weightPhase != null) throw new IllegalArgumentException("Weight phase already defined");
            weightPhase = phase;
            return this;
        }

        /** Immutable beam. Tracks and phase are checked against the timeline at registration. */
        public Beam build() { return new Beam(this); }

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
