package dev.calamidade_awakening.api;

import java.util.Arrays;

/** Frame-time statistics shared by F7, recordings and the cost probe (0.11.2), pure. Not public API. */
public final class FrameStats {
    private FrameStats() {}

    /** Nearest-rank percentile of an ascending array ({@code fraction} 0..1); 0 for an empty array. */
    public static double percentile(double[] sorted, double fraction) {
        if (sorted.length == 0) return 0;
        int index = (int) Math.ceil(fraction * sorted.length) - 1;
        return sorted[Math.clamp(index, 0, sorted.length - 1)];
    }

    /** Median, p95 and mean of frame times in ms (any order; the array is not changed). */
    public static double[] summary(double[] ms) {
        double[] sorted = ms.clone();
        Arrays.sort(sorted);
        double sum = 0;
        for (double v : sorted) sum += v;
        return new double[] {percentile(sorted, .5), percentile(sorted, .95), sorted.length == 0 ? 0 : sum / sorted.length};
    }
}
