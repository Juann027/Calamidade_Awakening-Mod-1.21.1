package dev.calamidade_awakening.api;

import java.util.Comparator;
import java.util.List;

/** Pure scheduling policy: distance, phase and deterministic sequence ID. */
public final class FrameBudget {
    public record Candidate<T>(T value, double distance, double intensity, long order, double range) {
        public Candidate(T value, double distance, double intensity, long order) { this(value, distance, intensity, order, 128); }
        public double weight() { return intensity / (1 + distance / 24); }
    }
    private FrameBudget() {}

    public static <T> List<T> select(List<Candidate<T>> candidates, int maxMarks) {
        if (maxMarks <= 0) return List.of();
        return candidates.stream()
            .filter(c -> Double.isFinite(c.distance()) && c.distance() >= 0 && c.distance() <= c.range()
                && Double.isFinite(c.intensity()) && c.intensity() > .001)
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
