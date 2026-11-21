package dev.calamidade_awakening.api;

/** Pure helpers of the engine's atmosphere pass (0.8.0), shared by the client and checkBudget. Not public API. */
public final class AtmosphereMath {
    /**
     * Largest exposure change per tick while the player hides lightning flashes: 0.025 per tick, that is,
     * half a stop of brightness takes about a second. Faster changes count as flashes.
     */
    public static final float CALM_EXPOSURE_PER_TICK = .025f;

    private AtmosphereMath() {}

    /**
     * Weight factor by distance to the effect center: 1 up to 75% of the effect's range, then a smooth fall to 0
     * at the range, so walking out of an arena fades the sky instead of popping it.
     */
    public static float rangeFade(double distance, double range) {
        if (!(range > 0) || !Double.isFinite(distance)) return 0;
        double t = Math.clamp((distance - range * .75) / (range * .25), 0, 1);
        return (float) (1 - t * t * (3 - 2 * t));
    }

    /** Moves {@code current} toward {@code target} by at most {@code maxPerTick * elapsedTicks}. */
    public static float approach(float current, float target, float elapsedTicks, float maxPerTick) {
        if (!Float.isFinite(current)) return target;
        float step = Math.max(0, elapsedTicks) * Math.max(0, maxPerTick);
        return current + Math.clamp(target - current, -step, step);
    }

    /** Exposure after the screen-effects intensity setting: 1 + (exposure - 1) * intensity. */
    public static float scaledExposure(float exposure, float intensity) {
        return 1 + (exposure - 1) * Math.clamp(intensity, 0f, 1f);
    }
}
