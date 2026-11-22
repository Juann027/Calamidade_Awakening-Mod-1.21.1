package dev.calamidade_awakening.api;

import java.util.Arrays;

/**
 * Automatic quality (0.11.2), pure: watches the p95 of the frame time in windows of {@link #WINDOW_MS} and moves one
 * profile up or down with hysteresis. Not public API.
 * <ul>
 *   <li><b>Down:</b> {@link #DOWN_WINDOWS} windows in a row with p95 above {@code target × }{@link #DOWN_FACTOR}
 *       while the engine draws effects in at least half of the frames (lowering the profile cannot help a slow frame
 *       without effects).</li>
 *   <li><b>Up:</b> {@link #UP_WINDOWS} windows in a row with p95 below {@code target × }{@link #UP_FACTOR} while the
 *       caps cut something in at least a quarter of the frames (no reason to climb when nothing is left out).</li>
 *   <li><b>No flapping:</b> after any change, {@link #COOLDOWN_MS} without evaluating; after a step down, climbing back
 *       to the profile that was too slow is blocked for {@link #BLOCK_MS}, doubled each time the same profile is left
 *       again for being slow (up to {@link #MAX_BLOCK_MS}).</li>
 *   <li><b>HERO:</b> a window that saw a HERO effect in progress is discarded and the streaks restart, so the profile
 *       never changes during a protagonist effect nor right after it because of it.</li>
 * </ul>
 * With VSync or a frame cap at the target, p95 never drops below the up threshold: AUTO then only goes down.
 */
public final class AutoQuality {
    public static final long WINDOW_MS = 2000, COOLDOWN_MS = 6000, BLOCK_MS = 60_000, MAX_BLOCK_MS = 600_000;
    public static final int DOWN_WINDOWS = 2, UP_WINDOWS = 4, MIN_FRAMES = 10;
    public static final double DOWN_FACTOR = 1.2, UP_FACTOR = .7;

    /** Kind of the last change. */
    public enum Change { START, UP, DOWN }

    /** Why the profile is what it is: the change, the profiles, the p95 and the threshold that decided, the time. */
    public record Reason(Change change, QualityProfile from, QualityProfile to, double p95, double thresholdMs, long atMs) {}

    private QualityProfile current;
    private double targetMs;
    private Reason reason;
    private long lastChange, windowStart = -1;
    private double[] times = new double[256];
    private int frames, drawing, cut;
    private boolean hero;
    private int over, under;
    private final long[] blockedUntil = new long[QualityProfile.values().length];
    private final long[] blockMs = new long[QualityProfile.values().length];

    public AutoQuality(QualityProfile start, double targetMs, long nowMs) {
        Arrays.fill(blockMs, BLOCK_MS);
        restart(start, targetMs, nowMs);
    }

    /** Starts again from {@code start} (entering AUTO), with no streaks and no blocks. */
    public void restart(QualityProfile start, double targetMs, long nowMs) {
        current = start;
        this.targetMs = targetMs;
        lastChange = nowMs;
        reason = new Reason(Change.START, start, start, 0, 0, nowMs);
        Arrays.fill(blockedUntil, Long.MIN_VALUE);
        Arrays.fill(blockMs, BLOCK_MS);
        over = under = 0;
        windowStart = -1;
    }

    /** Target frame time, ms (1000 / target FPS). */
    public void target(double ms) { targetMs = ms; }

    public double target() { return targetMs; }

    public QualityProfile current() { return current; }

    public Reason reason() { return reason; }

    /**
     * One rendered frame.
     *
     * @param nowMs      wall clock, ms
     * @param frameMs    duration of the frame, ms
     * @param drawn      the engine drew at least one effect in this frame
     * @param capped     the caps left a visible effect or a pass out of this frame
     * @param heroActive a HERO effect is in progress (resident on this client)
     * @return true when the profile changed
     */
    public boolean frame(long nowMs, double frameMs, boolean drawn, boolean capped, boolean heroActive) {
        if (windowStart < 0) windowStart = nowMs;
        if (Double.isFinite(frameMs) && frameMs > 0) {
            if (frames == times.length) times = Arrays.copyOf(times, frames * 2);
            times[frames++] = frameMs;
            if (drawn) drawing++;
            if (capped) cut++;
        }
        hero |= heroActive;
        if (nowMs - windowStart < WINDOW_MS) return false;
        boolean changed = evaluate(nowMs);
        frames = drawing = cut = 0;
        hero = false;
        windowStart = nowMs;
        return changed;
    }

    private boolean evaluate(long nowMs) {
        if (hero) {
            over = under = 0;
            return false;
        }
        if (nowMs - lastChange < COOLDOWN_MS || frames < MIN_FRAMES) return false;
        double[] sorted = Arrays.copyOf(times, frames);
        Arrays.sort(sorted);
        double p95 = FrameStats.percentile(sorted, .95);
        double down = targetMs * DOWN_FACTOR, up = targetMs * UP_FACTOR;
        if (p95 > down && drawing * 2 >= frames) {
            over++;
            under = 0;
        } else if (p95 < up && cut * 4 >= frames) {
            under++;
            over = 0;
        } else {
            over = under = 0;
        }
        if (over >= DOWN_WINDOWS && current != QualityProfile.LOW) {
            QualityProfile from = current;
            int i = from.ordinal();
            // Leaving the same profile again for being slow: block the way back for longer.
            if (blockedUntil[i] != Long.MIN_VALUE) blockMs[i] = Math.min(MAX_BLOCK_MS, blockMs[i] * 2);
            blockedUntil[i] = nowMs + blockMs[i];
            return change(Change.DOWN, from.lower(), p95, down, nowMs);
        }
        if (under >= UP_WINDOWS && current != QualityProfile.ULTRA
            && nowMs >= blockedUntil[current.higher().ordinal()]) {
            return change(Change.UP, current.higher(), p95, up, nowMs);
        }
        return false;
    }

    private boolean change(Change kind, QualityProfile to, double p95, double threshold, long nowMs) {
        reason = new Reason(kind, current, to, p95, threshold, nowMs);
        current = to;
        lastChange = nowMs;
        over = under = 0;
        return true;
    }
}
