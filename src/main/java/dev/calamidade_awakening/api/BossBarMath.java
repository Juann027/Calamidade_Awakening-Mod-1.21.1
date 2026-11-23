package dev.calamidade_awakening.api;

import java.util.List;

/**
 * Boss bar math (0.13.0). Pure Java, covered by the checkBudget: when the server sends a progress update, the segment
 * marks, the trailing "ghost" of recent damage and the phase-change flash drawn by the client.
 */
public final class BossBarMath {
    /** Smallest progress change sent at once (0.5%). */
    public static final float STEP = .005f;
    /** Ticks after which any smaller change is sent anyway. */
    public static final int SETTLE_TICKS = 5;
    /** Most segment marks on a bar. */
    public static final int MAX_MARKS = 16;
    /** Ticks the ghost waits before it starts to fall. */
    public static final float GHOST_HOLD = 10;
    /** Fraction of the bar the ghost falls per tick once it moves. */
    public static final float GHOST_SPEED = .02f;
    /** Length of the phase-change flash, ticks. */
    public static final float FLASH_TICKS = 12;

    private BossBarMath() {}

    /** Progress of a living entity, clamped to 0..1 (0 when the maximum is not positive or values are not finite). */
    public static float progress(float health, float max) {
        if (!(max > 0) || !Float.isFinite(health)) return 0;
        return Math.max(0, Math.min(1, health / max));
    }

    /**
     * Whether the server should send {@code now} after having sent {@code sent} at {@code sentTick}: a change of at
     * least {@link #STEP}, reaching exactly 0 or 1, or any change once {@link #SETTLE_TICKS} have passed.
     */
    public static boolean shouldSend(float sent, float now, long sentTick, long tick) {
        if (now == sent) return false;
        if (now == 0 || now == 1) return true;
        if (Math.abs(now - sent) >= STEP) return true;
        return tick - sentTick >= SETTLE_TICKS;
    }

    /** Segment marks: values inside (0, 1), unique, sorted high to low, at most {@link #MAX_MARKS}. */
    public static float[] marks(List<Double> thresholds) {
        double[] values = thresholds.stream().filter(v -> v != null && v > 0 && v < 1).distinct()
            .sorted((a, b) -> Double.compare(b, a)).limit(MAX_MARKS).mapToDouble(Double::doubleValue).toArray();
        float[] out = new float[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (float) values[i];
        return out;
    }

    /**
     * Ghost of recent damage after {@code dt} ticks: it never sits below the bar, jumps up with healing, holds for
     * {@link #GHOST_HOLD} ticks after a hit ({@code sinceHit}) and then falls at {@link #GHOST_SPEED} per tick.
     */
    public static float ghost(float ghost, float progress, float sinceHit, float dt) {
        if (ghost <= progress) return progress;
        if (sinceHit < GHOST_HOLD) return ghost;
        return Math.max(progress, ghost - GHOST_SPEED * dt);
    }

    /**
     * Strength 0..1 of the phase-change flash {@code since} ticks after the phase began: 1 falling to 0 over
     * {@link #FLASH_TICKS}. With flashes hidden (accessibility) it is a quarter as strong; with reduced motion, off.
     */
    public static float flash(float since, boolean flashAllowed, boolean motion) {
        if (!motion || since < 0 || since >= FLASH_TICKS) return 0;
        float f = 1 - since / FLASH_TICKS;
        return flashAllowed ? f : f * .25f;
    }

    /** Clamps the bar scale of the accessibility option to 0.5..2. */
    public static float scale(double scale) {
        return Double.isFinite(scale) ? (float) Math.max(.5, Math.min(2, scale)) : 1;
    }
}
