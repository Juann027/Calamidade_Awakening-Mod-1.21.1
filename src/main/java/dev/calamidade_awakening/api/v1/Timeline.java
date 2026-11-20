package dev.calamidade_awakening.api.v1;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable script of one effect (0.7.0): named phases with smooth weights, value tracks with keyframes, markers
 * and, for ambient effects, a sustain loop that only ends with a release. Pure Java, no Minecraft types, so the
 * server, the client and tests share the same math.
 *
 * <p>Ages are in ticks (doubles, so partial ticks interpolate). Every query takes the <em>local</em> age, that is,
 * the time on the timeline. Without sustain the local age equals the real age of the instance; with sustain use
 * {@link #localAge(double, double)} (the engine already does it for cues and client layers).
 *
 * <p>Names match {@code [a-z0-9_]+} and are unique within their kind: a phase and a marker may share a name.
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class Timeline {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");

    /** Curve of one keyframe segment. The easing of a keyframe shapes the segment that <em>arrives</em> at it. */
    public enum Easing {
        LINEAR, SMOOTH, EASE_IN, EASE_OUT, EASE_IN_OUT,
        /** Back-out: passes about 10% beyond the target and settles on it. */
        OVERSHOOT;

        private static final double BACK = 1.70158;

        /** Maps t in [0,1] (clamped) to the eased fraction; 0 maps to 0 and 1 to 1. */
        public double apply(double t) {
            double x = Math.clamp(t, 0, 1);
            return switch (this) {
                case LINEAR -> x;
                case SMOOTH -> x * x * (3 - 2 * x);
                case EASE_IN -> x * x * x;
                case EASE_OUT -> { double u = 1 - x; yield 1 - u * u * u; }
                case EASE_IN_OUT -> x < .5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
                case OVERSHOOT -> { double u = x - 1; yield 1 + (BACK + 1) * u * u * u + BACK * u * u; }
            };
        }
    }

    /** One value on a track. {@code tick} in [0, duration]; {@code easing} shapes the segment ending here. */
    public record Keyframe(int tick, float value, Easing easing) {
        public Keyframe {
            Objects.requireNonNull(easing, "easing");
            if (tick < 0 || !Float.isFinite(value))
                throw new IllegalArgumentException("Keyframe needs tick >= 0 and a finite value");
        }
    }

    public static Keyframe keyframe(int tick, float value, Easing easing) { return new Keyframe(tick, value, easing); }
    /** Linear keyframe. */
    public static Keyframe keyframe(int tick, float value) { return new Keyframe(tick, value, Easing.LINEAR); }

    private record Phase(int start, int end, int rise, int fall) {}

    private final int duration;
    private final Map<String, Phase> phases;
    private final Map<String, Keyframe[]> tracks;
    private final Map<String, Integer> markers;
    private final int sustainStart, sustainEnd, releaseStart;

    private Timeline(Builder builder) {
        this.duration = builder.duration;
        this.phases = Collections.unmodifiableMap(new LinkedHashMap<>(builder.phases));
        var copied = new LinkedHashMap<String, Keyframe[]>();
        builder.tracks.forEach((name, keys) -> copied.put(name, keys.clone()));
        this.tracks = Collections.unmodifiableMap(copied);
        this.markers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.markers));
        this.sustainStart = builder.sustainStart;
        this.sustainEnd = builder.sustainEnd;
        this.releaseStart = builder.sustainStart < 0 ? -1
            : builder.releaseStart < 0 ? builder.sustainEnd : builder.releaseStart;
    }

    /** Builder for a timeline of {@code duration} ticks (1..{@link Effects#MAX_TICKS}). */
    public static Builder builder(int duration) { return new Builder(duration); }

    /** Timeline with no phases, tracks or markers; what client layers see for effects registered without one. */
    public static Timeline empty(int duration) { return builder(duration).build(); }

    public int duration() { return duration; }

    // ---------------------------------------------------------------- phases

    /**
     * Weight of a phase, 0..1: zero before {@code start} and from {@code end} on, a smooth rise over {@code rise}
     * ticks, 1 on the plateau and a smooth fall over the last {@code fall} ticks. A zero rise or fall is a step.
     */
    public float weight(String phase, double age) {
        Phase p = phase(phase);
        if (!(age >= p.start) || age >= p.end) return 0;
        double in = p.rise == 0 ? 1 : smooth((age - p.start) / p.rise);
        double out = p.fall == 0 ? 1 : smooth((p.end - age) / p.fall);
        return (float) (in * out);
    }

    /** Linear progress inside the phase: 0 at {@code start} (and before), 1 at {@code end} (and after). */
    public float progress(String phase, double age) {
        Phase p = phase(phase);
        return (float) Math.clamp((age - p.start) / (double) (p.end - p.start), 0, 1);
    }

    public int start(String phase) { return phase(phase).start; }
    public int end(String phase) { return phase(phase).end; }
    public boolean hasPhase(String name) { return phases.containsKey(name); }
    /** Phase names in declaration order. */
    public Set<String> phases() { return phases.keySet(); }

    // ---------------------------------------------------------------- tracks

    /**
     * Interpolated value of a track. Before the first keyframe and after the last one it holds the value of that
     * keyframe. Between two keyframes it follows the easing of the later one.
     */
    public float value(String track, double age) {
        Keyframe[] keys = tracks.get(track);
        if (keys == null) throw new IllegalArgumentException("Unknown track: " + track);
        if (!(age > keys[0].tick())) return keys[0].value();
        Keyframe last = keys[keys.length - 1];
        if (age >= last.tick()) return last.value();
        int index = 1;
        while (keys[index].tick() <= age) index++;
        Keyframe a = keys[index - 1], b = keys[index];
        double t = (age - a.tick()) / (b.tick() - a.tick());
        return (float) (a.value() + (b.value() - a.value()) * b.easing().apply(t));
    }

    public boolean hasTrack(String name) { return tracks.containsKey(name); }
    /** Track names in declaration order. */
    public Set<String> tracks() { return tracks.keySet(); }
    /** Copy of the keyframes of a track. */
    public java.util.List<Keyframe> keyframes(String track) {
        Keyframe[] keys = tracks.get(track);
        if (keys == null) throw new IllegalArgumentException("Unknown track: " + track);
        return java.util.List.of(keys);
    }

    // ---------------------------------------------------------------- markers

    public int tick(String marker) {
        Integer tick = markers.get(marker);
        if (tick == null) throw new IllegalArgumentException("Unknown marker: " + marker);
        return tick;
    }

    public boolean hasMarker(String name) { return markers.containsKey(name); }
    /** Marker names in declaration order. */
    public Set<String> markers() { return markers.keySet(); }

    // ---------------------------------------------------------------- sustain and release

    public boolean sustained() { return sustainStart >= 0; }
    /** First tick of the loop, or -1 without sustain. */
    public int sustainStart() { return sustainStart; }
    /** Tick where the loop jumps back to {@link #sustainStart()}, or -1 without sustain. */
    public int sustainEnd() { return sustainEnd; }
    /** Local tick where the exit begins after a release ({@link #sustainEnd()} by default), or -1 without sustain. */
    public int releaseStart() { return releaseStart; }

    /**
     * Converts the real age of an instance into the local age of this timeline. Pure function.
     * <ul>
     * <li>Without sustain: returns {@code realAge}.</li>
     * <li>Before release: plays normally until {@code sustainEnd}, then loops over [sustainStart, sustainEnd).</li>
     * <li>From {@code releaseAge} on (real age of the release, or any negative value when not released): jumps
     * to {@code releaseStart} and plays forward from there, without looping again.</li>
     * </ul>
     * The instance ends when the local age reaches the effect duration.
     */
    public double localAge(double realAge, double releaseAge) {
        if (!sustained()) return realAge;
        if (releaseAge >= 0 && realAge >= releaseAge) return releaseStart + (realAge - releaseAge);
        if (realAge < sustainEnd) return realAge;
        double length = sustainEnd - sustainStart;
        return sustainStart + (realAge - sustainStart) % length;
    }

    /**
     * Real age at which a released instance reaches local age {@code duration}, that is, when it expires;
     * {@code Long.MAX_VALUE} while not released. Without sustain: {@code duration}.
     */
    public long endAge(long releaseAge, int duration) {
        if (!sustained()) return duration;
        if (releaseAge < 0) return Long.MAX_VALUE;
        return releaseAge + Math.max(0, duration - releaseStart);
    }

    /** Sampler bound to a local age, for layers that read several phases and tracks at once. */
    public Sampler at(double localAge) { return new Sampler(this, localAge); }

    /** Read-only view of a timeline at one local age (client layers receive one per instance and frame). */
    public static final class Sampler {
        private final Timeline timeline;
        private final double localAge;

        public Sampler(Timeline timeline, double localAge) {
            this.timeline = Objects.requireNonNull(timeline);
            this.localAge = localAge;
        }

        public Timeline timeline() { return timeline; }
        /** Local age in ticks, partial tick included. */
        public double localAge() { return localAge; }
        public float weight(String phase) { return timeline.weight(phase, localAge); }
        public float progress(String phase) { return timeline.progress(phase, localAge); }
        public float value(String track) { return timeline.value(track, localAge); }
        public int tick(String marker) { return timeline.tick(marker); }
        /** Ticks since a marker (negative before it). */
        public double since(String marker) { return localAge - timeline.tick(marker); }
    }

    // ---------------------------------------------------------------- builder

    public static final class Builder {
        private final int duration;
        private final Map<String, Phase> phases = new LinkedHashMap<>();
        private final Map<String, Keyframe[]> tracks = new LinkedHashMap<>();
        private final Map<String, Integer> markers = new LinkedHashMap<>();
        private int sustainStart = -1, sustainEnd = -1, releaseStart = -1;

        private Builder(int duration) {
            if (duration < 1 || duration > Effects.MAX_TICKS)
                throw new IllegalArgumentException("Timeline duration must be within 1.." + Effects.MAX_TICKS);
            this.duration = duration;
        }

        /**
         * Named phase over [start, end), with a smooth rise of {@code rise} ticks and fall of {@code fall} ticks.
         * Requires 0 &lt;= start &lt; end &lt;= duration, rise and fall &gt;= 0 and rise + fall &lt;= end - start.
         */
        public Builder phase(String name, int start, int end, int rise, int fall) {
            checkName(name, phases, "phase");
            if (start < 0 || end <= start || end > duration || rise < 0 || fall < 0 || rise + fall > end - start)
                throw new IllegalArgumentException("Invalid phase " + name + ": expected 0 <= start < end <= "
                    + duration + " and rise + fall <= end - start");
            phases.put(name, new Phase(start, end, rise, fall));
            return this;
        }

        /** Value track. Keyframe ticks must be strictly increasing and within [0, duration]. */
        public Builder track(String name, Keyframe... keyframes) {
            checkName(name, tracks, "track");
            Objects.requireNonNull(keyframes, "keyframes");
            if (keyframes.length == 0) throw new IllegalArgumentException("Track " + name + " needs keyframes");
            Keyframe[] keys = Arrays.copyOf(keyframes, keyframes.length);
            for (int i = 0; i < keys.length; i++) {
                Objects.requireNonNull(keys[i], "keyframe");
                if (keys[i].tick() > duration || (i > 0 && keys[i].tick() <= keys[i - 1].tick()))
                    throw new IllegalArgumentException("Track " + name
                        + ": keyframe ticks must increase strictly and stay within 0.." + duration);
            }
            tracks.put(name, keys);
            return this;
        }

        /** Named tick in [0, duration); cues can be scheduled on it with {@link Effects#cue(net.minecraft.resources.ResourceLocation, String, Effects.Cue)}. */
        public Builder marker(String name, int tick) {
            checkName(name, markers, "marker");
            if (tick < 0 || tick >= duration)
                throw new IllegalArgumentException("Marker " + name + " must be within 0.." + (duration - 1));
            markers.put(name, tick);
            return this;
        }

        /**
         * Loop for ambient effects: on reaching {@code end} the local age returns to {@code start} until the instance
         * is released. Requires 0 &lt;= start &lt; end &lt; duration (the exit needs at least one tick).
         */
        public Builder sustain(int start, int end) {
            if (sustainStart >= 0) throw new IllegalArgumentException("Sustain already defined");
            if (start < 0 || end <= start || end >= duration)
                throw new IllegalArgumentException("Sustain must satisfy 0 <= start < end < " + duration);
            sustainStart = start;
            sustainEnd = end;
            return this;
        }

        /** Local tick where the exit starts after a release; defaults to the sustain end. Requires sustain. */
        public Builder release(int start) {
            if (releaseStart >= 0) throw new IllegalArgumentException("Release already defined");
            if (start < 0 || start >= duration)
                throw new IllegalArgumentException("Release must be within 0.." + (duration - 1));
            releaseStart = start;
            return this;
        }

        public Timeline build() {
            if (releaseStart >= 0 && sustainStart < 0)
                throw new IllegalArgumentException("Release requires sustain");
            if (releaseStart >= 0 && releaseStart < sustainEnd)
                throw new IllegalArgumentException("Release must start at or after the sustain end (" + sustainEnd + ")");
            return new Timeline(this);
        }

        private static void checkName(String name, Map<String, ?> existing, String kind) {
            if (name == null || !NAME.matcher(name).matches())
                throw new IllegalArgumentException("Invalid " + kind + " name (expected [a-z0-9_], up to 64): " + name);
            if (existing.containsKey(name)) throw new IllegalArgumentException("Duplicate " + kind + ": " + name);
        }
    }

    private Phase phase(String name) {
        Phase phase = phases.get(name);
        if (phase == null) throw new IllegalArgumentException("Unknown phase: " + name);
        return phase;
    }

    private static double smooth(double x) {
        double t = Math.clamp(x, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
