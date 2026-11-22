package dev.calamidade_awakening.api;

import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.WorldEvents;
import java.util.List;

/**
 * Pure rules of global events (0.12.0), shared by the server, the client and checkBudget: admission under the limit
 * with priorities, who receives an event, the event clock for late arrivals and when an event is over. Not public API.
 */
public final class WorldEventRules {
    /** Most global events the config may allow. */
    public static final int MAX_LIMIT = 16;
    /** Default limit ({@code worldEventsMax}). */
    public static final int DEFAULT_LIMIT = 4;

    private WorldEventRules() {}

    /** One active event as admission sees it: effect, priority and start order (older = smaller). */
    public record Active(String effect, int priority, long order) {}

    /** What a start gets. */
    public enum Verdict {
        /** Starts. */
        ADMIT,
        /** Starts after the event at {@link Decision#victim} is stopped. */
        REPLACE,
        /** The limit is full of events with the same or a higher priority. */
        REFUSE,
        /** An event of this effect is already active. */
        DUPLICATE
    }

    /** Verdict and, for {@link Verdict#REPLACE}, the index of the event to stop (-1 otherwise). */
    public record Decision(Verdict verdict, int victim) {}

    /**
     * Admission of a new event of {@code effect} with {@code priority} under a limit of {@code max} (clamped to
     * 1..{@value #MAX_LIMIT}): duplicates are refused; below the limit it starts; at the limit it replaces the active
     * event with the lowest priority (the oldest on ties) when the new priority is strictly higher.
     */
    public static Decision admit(List<Active> active, String effect, int priority, int max) {
        for (Active a : active) if (a.effect().equals(effect)) return new Decision(Verdict.DUPLICATE, -1);
        int limit = Math.clamp(max, 1, MAX_LIMIT);
        if (active.size() < limit) return new Decision(Verdict.ADMIT, -1);
        int victim = -1;
        for (int i = 0; i < active.size(); i++) {
            Active a = active.get(i);
            if (victim < 0 || a.priority() < active.get(victim).priority()
                || (a.priority() == active.get(victim).priority() && a.order() < active.get(victim).order())) victim = i;
        }
        return victim >= 0 && priority > active.get(victim).priority()
            ? new Decision(Verdict.REPLACE, victim) : new Decision(Verdict.REFUSE, -1);
    }

    /** True when a player in {@code playerDimension} sees an event of {@code scope} started in {@code origin}. */
    public static boolean applies(WorldEvents.Scope scope, String origin, String playerDimension) {
        return switch (scope) {
            case SERVER -> true;
            case DIMENSION -> origin.equals(playerDimension);
            case AREA -> false;
        };
    }

    /**
     * Real age of an event at {@code gameTime} (ticks of the server's clock, the overworld time that every dimension
     * shares): the same number on the server and on every client, so a player who arrives late enters the right phase.
     */
    public static long age(long gameTime, long startTick) { return gameTime - startTick; }

    /**
     * Local (timeline) age of an event: {@code timeline.localAge} with the release age, or the real age without a
     * timeline. {@code released} is -1 before the release.
     */
    public static double localAge(Timeline timeline, double age, long released) {
        return timeline == null ? age : timeline.localAge(age, released);
    }

    /** True when an event is over: the real age reached the duration without sustain, or the release played out. */
    public static boolean finished(Timeline timeline, int duration, long age, long released) {
        if (timeline == null || !timeline.sustained()) return age >= duration;
        return released >= 0 && age >= timeline.endAge(released, duration);
    }
}
