package dev.calamidade_awakening.api;

import java.util.Objects;

/**
 * Pure transitions between the states of a dimension's sky (0.12.0), shared by the server, the client and checkBudget.
 * A transition goes from {@code from} to {@code to} in {@code ticks} ticks of the server's game time, starting at
 * {@code start}; the weight of {@code to} follows a smoothstep, the weight of {@code from} is the rest. Not public API.
 */
public final class SkyProfileMath {
    private SkyProfileMath() {}

    /**
     * Transition between two states.
     *
     * @param from  state fading out
     * @param to    state fading in (the current target)
     * @param start game time when it started
     * @param ticks duration, ticks (0: instant)
     */
    public record Transition(String from, String to, long start, int ticks) {
        public Transition {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            ticks = Math.max(0, ticks);
        }

        /** Settled in a state since forever. */
        public static Transition settled(String state) { return new Transition(state, state, 0, 0); }

        /** Weight of the target state at {@code now}, 0..1 (smoothstep). */
        public float weightTo(double now) {
            if (ticks <= 0 || from.equals(to)) return 1;
            double x = Math.clamp((now - start) / ticks, 0, 1);
            return (float) (x * x * (3 - 2 * x));
        }

        /** Weight of the state fading out, {@code 1 - weightTo}. */
        public float weightFrom(double now) { return from.equals(to) ? 0 : 1 - weightTo(now); }

        /** True once the target fully replaced the previous state. */
        public boolean done(double now) { return weightTo(now) >= 1; }

        /** The state that dominates now (the target from half of the transition on). */
        public String dominant(double now) { return weightTo(now) >= .5f ? to : from; }

        /**
         * Next transition to {@code state} in {@code ticks}, starting at {@code now} from the dominant state (a change
         * in the middle of another fades from whichever state shows more). Same target: unchanged.
         */
        public Transition toward(String state, long now, int ticks) {
            if (state.equals(to)) return this;
            return new Transition(dominant(now), state, now, ticks);
        }
    }
}
