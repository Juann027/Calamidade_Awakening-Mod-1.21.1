package dev.bossestesting.fx;

import dev.calamidade_awakening.api.v1.Timeline;

import java.util.Map;
import java.util.TreeMap;

/** Keyframe list builder: sorted by tick, the last value wins on a repeated tick, spikes for pulses. */
public final class Kf {
    private final TreeMap<Integer, Timeline.Keyframe> frames = new TreeMap<>();

    public static Kf of(int tick, float value) { return new Kf().k(tick, value); }

    public Kf k(int tick, float value) { frames.put(tick, Timeline.keyframe(tick, value)); return this; }

    public Kf k(int tick, float value, Timeline.Easing easing) { frames.put(tick, Timeline.keyframe(tick, value, easing)); return this; }

    /** A pulse: 0 one tick before, `peak` at `tick`, back to 0 after `decay` ticks (eased out). */
    public Kf spike(int tick, float peak, int decay) {
        if (!frames.containsKey(tick - 1)) k(Math.max(0, tick - 1), 0);
        k(tick, peak);
        return k(tick + decay, 0, Timeline.Easing.EASE_OUT);
    }

    public Timeline.Keyframe[] build() {
        if (frames.isEmpty()) return new Timeline.Keyframe[]{Timeline.keyframe(0, 0)};
        return frames.values().toArray(Timeline.Keyframe[]::new);
    }

    /** Debug: tick → value. */
    public Map<Integer, Timeline.Keyframe> frames() { return frames; }
}
