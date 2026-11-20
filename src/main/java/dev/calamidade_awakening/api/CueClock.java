package dev.calamidade_awakening.api;

import dev.calamidade_awakening.api.v1.Timeline;

/** Pure walk of the local ticks of a sustained instance (0.7.0), shared by the server and checkBudget. */
public final class CueClock {
    @FunctionalInterface public interface Visit {
        /** Return false to stop the walk (the instance ended). */
        boolean visit(long realAge, int localTick);
    }

    private CueClock() {}

    /**
     * Visits every real age in [from, to] in order with its local tick, stopping once the local age reaches
     * {@code duration} or the visitor returns false. A local tick inside the loop is visited on every lap; local
     * ticks skipped by the release jump are not visited.
     */
    public static void walk(Timeline timeline, long releasedAt, int duration, long from, long to, Visit visit) {
        for (long real = Math.max(0, from); real <= to; real++) {
            double local = timeline.localAge(real, releasedAt);
            if (local >= duration || !visit.visit(real, (int) local)) return;
        }
    }
}
