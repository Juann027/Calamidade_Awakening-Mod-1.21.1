package dev.calamidade_awakening.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure scheduling policy: distance, phase and deterministic sequence ID. */
public final class FrameBudget {
    /** Most HERO instances drawn per frame (0.9.0), the nearest ones; the rest of the HERO instances are skipped. */
    public static final int MAX_HEROES = 2;

    /**
     * One visible instance competing for the frame.
     *
     * @param hero true for {@code Effects.Priority.HERO} (0.9.0): chosen before NORMAL, nearest first, at most
     *             {@link #MAX_HEROES}, never cut by the effect limit
     */
    public record Candidate<T>(T value, double distance, double intensity, long order, double range, boolean hero) {
        public Candidate(T value, double distance, double intensity, long order) { this(value, distance, intensity, order, 128); }
        public Candidate(T value, double distance, double intensity, long order, double range) {
            this(value, distance, intensity, order, range, false);
        }
        public double weight() { return intensity / (1 + distance / 24); }
        boolean eligible() {
            return Double.isFinite(distance) && distance >= 0 && distance <= range
                && Double.isFinite(intensity) && intensity > .001;
        }
    }
    private FrameBudget() {}

    /**
     * Instances drawn this frame, in priority order. HERO candidates come first: the eligible ones sorted by
     * distance (ties by order), at most {@link #MAX_HEROES}; they take slots of {@code maxMarks} but are never cut by
     * it (it is at least 3). NORMAL candidates fill the remaining slots by weight, exactly as before 0.9.0.
     */
    public static <T> List<T> select(List<Candidate<T>> candidates, int maxMarks) {
        if (maxMarks <= 0) return List.of();
        List<Candidate<T>> heroes = new ArrayList<>(), normal = new ArrayList<>(candidates.size());
        for (Candidate<T> c : candidates) (c.hero() ? heroes : normal).add(c);
        if (heroes.isEmpty()) return selectNormal(normal, maxMarks);
        List<T> chosen = new ArrayList<>(heroes.stream().filter(Candidate::eligible)
            .sorted(Comparator.<Candidate<T>>comparingDouble(Candidate::distance).thenComparingLong(Candidate::order))
            .limit(Math.min(MAX_HEROES, maxMarks)).map(Candidate::value).toList());
        chosen.addAll(selectNormal(normal, maxMarks - chosen.size()));
        return chosen;
    }

    /** The pre-0.9 policy: eligible candidates by weight (intensity over distance), ties by order. */
    public static <T> List<T> selectNormal(List<Candidate<T>> candidates, int maxMarks) {
        if (maxMarks <= 0) return List.of();
        return candidates.stream().filter(Candidate::eligible)
            .sorted(Comparator.<Candidate<T>>comparingDouble(Candidate::weight).reversed()
                .thenComparingLong(Candidate::order))
            .limit(maxMarks).map(Candidate::value).toList();
    }

    /**
     * Envelope by age for any duration. Up to 1200 ticks it equals {@link #envelope(double)}; longer effects keep
     * the ramps of a 1200-tick effect (168 ticks in, 336 out) instead of stretching them for minutes.
     */
    public static double envelope(double ageTicks, int duration) {
        if (duration <= 1200) return envelope(ageTicks / duration);
        double fadeIn = smooth(ageTicks / 168.0);
        double fadeOut = 1 - smooth((ageTicks - (duration - 336.0)) / 336.0);
        return fadeIn * fadeOut;
    }

    public static double envelope(double progress) {
        double fadeIn = smooth(progress / .14);
        double fadeOut = 1 - smooth((progress - .72) / .28);
        return fadeIn * fadeOut;
    }

    private static double smooth(double x) {
        double t = Math.clamp(x, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
