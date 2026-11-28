package dev.calamidade_awakening.client.geometry;

/** Curvas normalizadas que podem ser combinadas por qualquer habilidade. */
public final class Timeline {
    private Timeline() {}
    public static double progress(double t, double start, double end) {
        if (end <= start) throw new IllegalArgumentException("Invalid interval");
        return Math.clamp((t - start) / (end - start), 0, 1);
    }
    public static double smooth(double t) {
        double p = Math.clamp(t, 0, 1);
        return p * p * (3 - 2 * p);
    }
    public static double envelope(double t, double start, double rise, double fall, double end) {
        return smooth(progress(t, start, rise)) * (1 - smooth(progress(t, fall, end)));
    }
}
