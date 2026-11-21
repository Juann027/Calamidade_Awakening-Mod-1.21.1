package dev.calamidade_awakening.api.v1;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable atmosphere of one effect (0.8.0): how the sky, the fog and the whole scene look while the effect is
 * active. Pure Java, no Minecraft types, so the registry, the client and tests share the same math.
 *
 * <ul>
 * <li><b>Sky</b>: {@code skyTop} and {@code skyHorizon} (ARGB, alpha ignored) form a vertical gradient that replaces
 * the vanilla sky color by {@code skyBlend} (0..1). The sun, moon, clouds and effects drawn over the sky are kept.</li>
 * <li><b>Fog</b>: {@code fogColor} (ARGB, alpha ignored), {@code fogStart} and {@code fogEnd} in blocks
 * (0 &lt;= start &lt; end &lt;= {@value #MAX_DISTANCE}) and {@code fogDensity} (0..1): 0 leaves the game fog
 * untouched, 1 makes the fog go from {@code fogStart} (clear) to {@code fogEnd} (opaque) in {@code fogColor};
 * values in between move the game fog that far toward these distances and this color. The fog never gets
 * thinner than the game's own (blindness, water and render distance still win).</li>
 * <li><b>Exposure</b>: multiplier of the final scene color, 0..{@value #MAX_EXPOSURE} (1 = unchanged).</li>
 * <li><b>Ambient tint</b>: {@code ambientTint} (ARGB, alpha ignored) multiplies the scene, normalized to keep its
 * brightness, by {@code ambientStrength} (0..1).</li>
 * <li><b>Stars</b>: procedural star field on the sky, 0..1.</li>
 * </ul>
 *
 * <p>Every numeric parameter can come from a track of the effect's {@link Timeline} ({@link Builder#track}); the
 * value is clamped to the parameter range. The global weight of an instance is its opacity envelope, times the
 * weight of an optional phase ({@link Builder#weightPhase}) and of an optional {@link Param#WEIGHT} track. When
 * several effects with an atmosphere are active, the client mixes the two with the largest weight
 * ({@link #mix(List)}).
 *
 * <pre>{@code
 * static final Atmosphere CEU_DOURADO = Atmosphere.builder()
 *     .sky(0xff3a2410, 0xffffb050, .5f)       // topo, horizonte, mistura
 *     .exposure(1.2f)
 *     .weightPhase("impacto")                // só durante a fase "impacto" da timeline
 *     .build();
 * // Construtor do mod: register, timeline, atmosphere.
 * Effects.atmosphere(ID, CEU_DOURADO);
 * }</pre>
 *
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class Atmosphere {
    /** Largest fog distance, in blocks. */
    public static final float MAX_DISTANCE = 2048f;
    /** Largest exposure multiplier. */
    public static final float MAX_EXPOSURE = 4f;
    /** Smallest gap between fog start and end, in blocks, after track values are clamped. */
    public static final float MIN_FOG_SPAN = .5f;
    /** Weight below which an atmosphere is ignored (the engine draws no pass). */
    public static final float ACTIVE_WEIGHT = 1e-3f;

    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");

    /** Numeric parameters that can be driven by a timeline track. Each has a fixed range used to clamp tracks. */
    public enum Param {
        /** Sky gradient mix, 0..1. */
        SKY_BLEND(0, 1),
        /** Fog density, 0..1. */
        FOG_DENSITY(0, 1),
        /** Fog start, blocks. */
        FOG_START(0, MAX_DISTANCE),
        /** Fog end, blocks (kept at least {@link #MIN_FOG_SPAN} beyond the start). */
        FOG_END(0, MAX_DISTANCE),
        /** Exposure multiplier, 0..{@value #MAX_EXPOSURE}. */
        EXPOSURE(0, MAX_EXPOSURE),
        /** Ambient tint strength, 0..1. */
        AMBIENT_STRENGTH(0, 1),
        /** Star field, 0..1. */
        STARS(0, 1),
        /** Extra multiplier of the global weight, 0..1 (the envelope and the phase still apply). */
        WEIGHT(0, 1);

        public final float min, max;

        Param(float min, float max) { this.min = min; this.max = max; }

        /** Clamps a value into this parameter's range; NaN becomes {@code min}. */
        public float clamp(float value) {
            return Float.isNaN(value) ? min : Math.clamp(value, min, max);
        }
    }

    /** Linear RGB color with components in 0..1, built from ARGB ints (alpha ignored). */
    public record Rgb(float r, float g, float b) {
        public static final Rgb WHITE = new Rgb(1, 1, 1);
        public static final Rgb BLACK = new Rgb(0, 0, 0);

        public Rgb {
            if (!(r >= 0 && r <= 1 && g >= 0 && g <= 1 && b >= 0 && b <= 1))
                throw new IllegalArgumentException("Rgb components must be within 0..1");
        }

        /** Color of an ARGB int; alpha is ignored. */
        public static Rgb of(int argb) {
            return new Rgb(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f);
        }

        /** Opaque ARGB int, rounded to the nearest 8-bit value. */
        public int argb() {
            return 0xff000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
        }

        /** Linear interpolation: {@code this} at t = 0, {@code other} at t = 1 (t clamped to 0..1). */
        public Rgb lerp(Rgb other, float t) {
            float k = Math.clamp(t, 0f, 1f);
            return new Rgb(r + (other.r - r) * k, g + (other.g - g) * k, b + (other.b - b) * k);
        }

        /** Relative luminance (Rec. 709 weights). */
        public float luminance() { return .2126f * r + .7152f * g + .0722f * b; }
    }

    /**
     * Resolved atmosphere at one moment: every parameter is a plain number already taken from tracks and
     * clamped, plus the global {@code weight} (0..1). {@link #NEUTRAL} changes nothing. The client builds one per
     * active instance and frame, then {@link Atmosphere#mix(List) mixes} them.
     */
    public record State(float weight, Rgb skyTop, Rgb skyHorizon, float skyBlend, Rgb fogColor, float fogDensity,
                        float fogStart, float fogEnd, float exposure, Rgb ambientTint, float ambientStrength,
                        float stars) {
        /** Weight 0, no sky blend, no fog, exposure 1, no tint, no stars. */
        public static final State NEUTRAL = new State(0, Rgb.BLACK, Rgb.BLACK, 0, Rgb.WHITE, 0, 16, 96, 1,
            Rgb.WHITE, 0, 0);

        public State {
            Objects.requireNonNull(skyTop, "skyTop");
            Objects.requireNonNull(skyHorizon, "skyHorizon");
            Objects.requireNonNull(fogColor, "fogColor");
            Objects.requireNonNull(ambientTint, "ambientTint");
            if (!unit(weight) || !unit(skyBlend) || !unit(fogDensity) || !unit(ambientStrength) || !unit(stars)
                || !(exposure >= 0 && exposure <= MAX_EXPOSURE)
                || !(fogStart >= 0 && fogStart < fogEnd && fogEnd <= MAX_DISTANCE))
                throw new IllegalArgumentException("State outside its ranges: " + this);
        }

        /** True when the weight is above {@link #ACTIVE_WEIGHT}: the engine draws the atmosphere pass. */
        public boolean active() { return weight > ACTIVE_WEIGHT; }

        /**
         * Game fog start (blocks) after this atmosphere: moves {@code vanillaNear} toward {@code fogStart} by
         * {@code fogDensity}, never farther than the game's own value.
         */
        public float fogNear(float vanillaNear) {
            return Math.min(vanillaNear, vanillaNear + (fogStart - vanillaNear) * fogDensity);
        }

        /**
         * Game fog end (blocks) after this atmosphere: moves {@code vanillaFar} toward {@code fogEnd} by
         * {@code fogDensity}, never farther than the game's own value and at least {@link #MIN_FOG_SPAN} beyond
         * {@link #fogNear(float) fogNear(vanillaNear)}.
         */
        public float fogFar(float vanillaNear, float vanillaFar) {
            float far = Math.min(vanillaFar, vanillaFar + (fogEnd - vanillaFar) * fogDensity);
            return Math.max(far, fogNear(vanillaNear) + MIN_FOG_SPAN);
        }

        /** Game fog color after this atmosphere: {@code vanilla} moved toward {@code fogColor} by {@code fogDensity}. */
        public Rgb fogColorOver(Rgb vanilla) { return vanilla.lerp(fogColor, fogDensity); }

        /**
         * Fraction of fog (0..1) at {@code distance} blocks when the engine draws the fog itself on the screen
         * (client option {@code vanillaFog} off): {@code fogDensity * smoothstep(fogStart, fogEnd, distance)}.
         */
        public float screenFog(double distance) {
            double t = Math.clamp((distance - fogStart) / (fogEnd - fogStart), 0, 1);
            return (float) (fogDensity * t * t * (3 - 2 * t));
        }
    }

    private final int skyTop, skyHorizon, fogColor, ambientTint;
    private final float skyBlend, fogDensity, fogStart, fogEnd, exposure, ambientStrength, stars;
    private final Map<Param, String> tracks;
    private final String weightPhase;

    private Atmosphere(Builder b) {
        skyTop = b.skyTop; skyHorizon = b.skyHorizon; fogColor = b.fogColor; ambientTint = b.ambientTint;
        skyBlend = b.skyBlend; fogDensity = b.fogDensity; fogStart = b.fogStart; fogEnd = b.fogEnd;
        exposure = b.exposure; ambientStrength = b.ambientStrength; stars = b.stars;
        tracks = Collections.unmodifiableMap(new EnumMap<>(b.tracks));
        weightPhase = b.weightPhase;
    }

    /** New builder. Every parameter starts neutral: no sky blend, no fog, exposure 1, no tint, no stars. */
    public static Builder builder() { return new Builder(); }

    /** Sky color at the zenith (ARGB, alpha ignored). */
    public int skyTop() { return skyTop; }
    /** Sky color at the horizon (ARGB, alpha ignored). */
    public int skyHorizon() { return skyHorizon; }
    /** Sky gradient mix, 0..1. */
    public float skyBlend() { return skyBlend; }
    /** Fog color (ARGB, alpha ignored). */
    public int fogColor() { return fogColor; }
    /** Fog density, 0..1. */
    public float fogDensity() { return fogDensity; }
    /** Fog start, blocks. */
    public float fogStart() { return fogStart; }
    /** Fog end, blocks. */
    public float fogEnd() { return fogEnd; }
    /** Exposure multiplier, 0..{@value #MAX_EXPOSURE}. */
    public float exposure() { return exposure; }
    /** Ambient tint color (ARGB, alpha ignored). */
    public int ambientTint() { return ambientTint; }
    /** Ambient tint strength, 0..1. */
    public float ambientStrength() { return ambientStrength; }
    /** Star field, 0..1. */
    public float stars() { return stars; }
    /** Track bound to a parameter, or null when the parameter is constant. */
    public String track(Param param) { return tracks.get(Objects.requireNonNull(param)); }
    /** Every track binding, in {@link Param} order. */
    public Map<Param, String> tracks() { return tracks; }
    /** Phase whose weight multiplies the global weight, or null. */
    public String weightPhase() { return weightPhase; }
    /** True when some parameter, the weight or the phase depends on the timeline. */
    public boolean animated() { return !tracks.isEmpty() || weightPhase != null; }

    /**
     * Checks that every track and the phase this atmosphere reads exist in {@code timeline}. {@code timeline} may
     * be null only when nothing is {@link #animated() animated}. Called by {@link Effects#atmosphere}.
     */
    public void validate(Timeline timeline) {
        if (!animated()) return;
        if (timeline == null)
            throw new IllegalArgumentException("Atmosphere reads tracks or a phase: register the effect's timeline first");
        tracks.forEach((param, track) -> {
            if (!timeline.hasTrack(track))
                throw new IllegalArgumentException("Atmosphere " + param + " reads unknown track: " + track);
        });
        if (weightPhase != null && !timeline.hasPhase(weightPhase))
            throw new IllegalArgumentException("Atmosphere weight reads unknown phase: " + weightPhase);
    }

    /**
     * Resolves this atmosphere at one moment. {@code timeline} is the instance's sampler at its local age (any
     * sampler works when nothing is animated); {@code envelope} is the instance's opacity envelope (clamped to
     * 0..1), which becomes the global weight, times the phase and {@link Param#WEIGHT} track when bound.
     */
    public State sample(Timeline.Sampler timeline, float envelope) {
        Objects.requireNonNull(timeline, "timeline");
        float weight = Float.isNaN(envelope) ? 0 : Math.clamp(envelope, 0f, 1f);
        if (weightPhase != null) weight *= timeline.weight(weightPhase);
        weight *= value(Param.WEIGHT, 1, timeline);
        float start = value(Param.FOG_START, fogStart, timeline);
        float end = value(Param.FOG_END, fogEnd, timeline);
        if (start > MAX_DISTANCE - MIN_FOG_SPAN) start = MAX_DISTANCE - MIN_FOG_SPAN;
        if (end < start + MIN_FOG_SPAN) end = start + MIN_FOG_SPAN;
        return new State(Math.clamp(weight, 0f, 1f), Rgb.of(skyTop), Rgb.of(skyHorizon),
            value(Param.SKY_BLEND, skyBlend, timeline), Rgb.of(fogColor), value(Param.FOG_DENSITY, fogDensity, timeline),
            start, end, value(Param.EXPOSURE, exposure, timeline), Rgb.of(ambientTint),
            value(Param.AMBIENT_STRENGTH, ambientStrength, timeline), value(Param.STARS, stars, timeline));
    }

    private float value(Param param, float constant, Timeline.Sampler timeline) {
        String track = tracks.get(param);
        return track == null ? constant : param.clamp(timeline.value(track));
    }

    /**
     * Mixes the active atmospheres of one frame. Pure function.
     * <ul>
     * <li>Only the two states with the largest weight count (ties keep list order); weights &lt;= 0 are ignored.
     * No state with weight &gt; 0 gives {@link State#NEUTRAL}.</li>
     * <li>The result weight is {@code min(1, wa + wb)}, so a crossfade (one fading out while the other fades in)
     * keeps full strength.</li>
     * <li>Amounts ({@code skyBlend}, {@code fogDensity}, {@code ambientStrength}, {@code stars}, {@code exposure - 1})
     * are the weighted mean of the two, scaled by the result weight. A lone state with weight w gives its amounts
     * times w.</li>
     * <li>Colors and fog distances are weighted by how much each state uses them (weight times the matching
     * amount), so a state without fog does not pull the fog color of the other.</li>
     * </ul>
     */
    public static State mix(List<State> states) {
        Objects.requireNonNull(states, "states");
        State a = null, b = null;
        for (State s : states) {
            if (s == null || !(s.weight > 0)) continue;
            if (a == null || s.weight > a.weight) { b = a; a = s; }
            else if (b == null || s.weight > b.weight) b = s;
        }
        if (a == null) return State.NEUTRAL;
        if (b == null) b = State.NEUTRAL;
        float wa = a.weight, wb = b == State.NEUTRAL ? 0 : b.weight;
        float sum = wa + wb, total = Math.min(1f, sum);
        float sa = wa / sum, sb = wb / sum;

        float ka = wa * a.skyBlend, kb = wb * b.skyBlend;
        Rgb skyTop = blend(a.skyTop, b.skyTop, ka, kb), skyHorizon = blend(a.skyHorizon, b.skyHorizon, ka, kb);
        float skyBlend = (sa * a.skyBlend + sb * b.skyBlend) * total;

        float fa = wa * a.fogDensity, fb = wb * b.fogDensity;
        Rgb fogColor = blend(a.fogColor, b.fogColor, fa, fb);
        float fogStart = mean(a.fogStart, b.fogStart, fa, fb), fogEnd = mean(a.fogEnd, b.fogEnd, fa, fb);
        if (fogEnd < fogStart + MIN_FOG_SPAN) fogEnd = Math.min(MAX_DISTANCE, fogStart + MIN_FOG_SPAN);
        float fogDensity = (sa * a.fogDensity + sb * b.fogDensity) * total;

        float ta = wa * a.ambientStrength, tb = wb * b.ambientStrength;
        Rgb tint = blend(a.ambientTint, b.ambientTint, ta, tb);
        float strength = (sa * a.ambientStrength + sb * b.ambientStrength) * total;

        float exposure = 1 + (sa * (a.exposure - 1) + sb * (b.exposure - 1)) * total;
        float stars = (sa * a.stars + sb * b.stars) * total;
        return new State(total, skyTop, skyHorizon, clampUnit(skyBlend), fogColor, clampUnit(fogDensity),
            fogStart, fogEnd, Math.clamp(exposure, 0f, MAX_EXPOSURE), tint, clampUnit(strength), clampUnit(stars));
    }

    private static Rgb blend(Rgb a, Rgb b, float ka, float kb) {
        return ka + kb > 0 ? a.lerp(b, kb / (ka + kb)) : a;
    }

    private static float mean(float a, float b, float ka, float kb) {
        return ka + kb > 0 ? (a * ka + b * kb) / (ka + kb) : a;
    }

    private static float clampUnit(float x) { return Math.clamp(x, 0f, 1f); }

    private static boolean unit(float x) { return x >= 0 && x <= 1; }

    /** Builder of an {@link Atmosphere}. Every setter validates its arguments right away. */
    public static final class Builder {
        private int skyTop = 0xff000000, skyHorizon = 0xff000000, fogColor = 0xffffffff, ambientTint = 0xffffffff;
        private float skyBlend, fogDensity, fogStart = 16, fogEnd = 96, exposure = 1, ambientStrength, stars;
        private final Map<Param, String> tracks = new EnumMap<>(Param.class);
        private String weightPhase;

        private Builder() {}

        /** Sky gradient from {@code horizon} to {@code top} (ARGB, alpha ignored), mixed by {@code blend} (0..1). */
        public Builder sky(int top, int horizon, float blend) {
            skyTop = top; skyHorizon = horizon; skyBlend = unit("sky blend", blend);
            return this;
        }

        /**
         * Distance fog: {@code color} (ARGB, alpha ignored), {@code density} 0..1, from {@code start} to
         * {@code end} blocks (0 &lt;= start &lt; end &lt;= {@value #MAX_DISTANCE}).
         */
        public Builder fog(int color, float density, float start, float end) {
            if (!(start >= 0 && end > start && end <= MAX_DISTANCE))
                throw new IllegalArgumentException("Fog needs 0 <= start < end <= " + MAX_DISTANCE + " blocks");
            fogColor = color; fogDensity = unit("fog density", density); fogStart = start; fogEnd = end;
            return this;
        }

        /** Exposure multiplier, 0..{@value #MAX_EXPOSURE} (1 = unchanged). */
        public Builder exposure(float multiplier) {
            if (!(multiplier >= 0 && multiplier <= MAX_EXPOSURE))
                throw new IllegalArgumentException("Exposure must be within 0.." + MAX_EXPOSURE);
            exposure = multiplier;
            return this;
        }

        /** Ambient tint (ARGB, alpha ignored) applied to the whole scene by {@code strength} (0..1). */
        public Builder ambient(int tint, float strength) {
            ambientTint = tint; ambientStrength = unit("ambient strength", strength);
            return this;
        }

        /** Procedural star field on the sky, 0..1. */
        public Builder stars(float amount) {
            stars = unit("stars", amount);
            return this;
        }

        /**
         * Takes {@code param} from a track of the effect's timeline instead of the constant (clamped to the
         * parameter range). One track per parameter; a track may feed several parameters.
         */
        public Builder track(Param param, String track) {
            Objects.requireNonNull(param, "param");
            checkName(track, "track");
            if (tracks.putIfAbsent(param, track) != null)
                throw new IllegalArgumentException("Parameter already bound to a track: " + param);
            return this;
        }

        /** Multiplies the global weight by the weight of a timeline phase (0 outside it). */
        public Builder weightPhase(String phase) {
            checkName(phase, "phase");
            if (weightPhase != null) throw new IllegalArgumentException("Weight phase already defined");
            weightPhase = phase;
            return this;
        }

        /** Immutable atmosphere. Tracks and phase are checked against the timeline at registration. */
        public Atmosphere build() { return new Atmosphere(this); }

        private static float unit(String what, float value) {
            if (!(value >= 0 && value <= 1)) throw new IllegalArgumentException(what + " must be within 0..1");
            return value;
        }

        private static void checkName(String name, String kind) {
            if (name == null || !NAME.matcher(name).matches())
                throw new IllegalArgumentException("Invalid " + kind + " name (expected [a-z0-9_], up to 64): " + name);
        }
    }
}
