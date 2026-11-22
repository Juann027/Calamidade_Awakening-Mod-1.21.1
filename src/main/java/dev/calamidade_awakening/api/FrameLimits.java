package dev.calamidade_awakening.api;

/**
 * Per-frame caps actually applied by the client (0.11.2), pure. A {@link Quality} profile gives the default caps; the
 * internal test profile {@link #unlimited} lifts them for measurements. {@code tier} is what addons see through
 * {@code post.quality()} (shader detail: raymarch steps, segments), so a test profile never leaks a new value into the
 * public API. Not public API.
 *
 * @param tier       the quality addons see and that picks shader detail
 * @param effects    most instances drawn per frame (HERO included)
 * @param marks      common addon pass budget (card shaders, post passes, NORMAL beams/lights/terrain passes)
 * @param heroPasses separate HERO pass budget
 * @param beams      most beams in the engine beams pass (at most {@link #MAX_BEAMS}, the rows of its data texture)
 * @param lights     most lights in the engine lights pass (at most {@link #MAX_LIGHTS})
 * @param vertices   world mesh vertices per frame, shared among the drawn effects
 * @param test       true for the internal test profile (shown in F7 and in recordings)
 */
public record FrameLimits(Quality tier, int effects, int marks, int heroPasses, int beams, int lights, int vertices,
                          boolean test) {
    /** Resident instances per client: no profile can draw more. */
    public static final int MAX_EFFECTS = 96;
    /** Rows of the beams data texture. */
    public static final int MAX_BEAMS = 128;
    /** Rows of the lights data texture. */
    public static final int MAX_LIGHTS = 64;
    /** Most passes of one budget: one full-screen shader per drawn instance at most. */
    public static final int MAX_PASSES = MAX_EFFECTS;

    public FrameLimits {
        effects = Math.clamp(effects, 0, MAX_EFFECTS);
        marks = Math.clamp(marks, 0, MAX_PASSES);
        heroPasses = Math.clamp(heroPasses, 0, MAX_PASSES);
        beams = Math.clamp(beams, 0, MAX_BEAMS);
        lights = Math.clamp(lights, 0, MAX_LIGHTS);
        vertices = Math.max(0, vertices);
    }

    /** The caps of a quality profile, exactly as before 0.11.2. */
    public static FrameLimits of(Quality quality) {
        return new FrameLimits(quality, quality.effects, quality.marks, quality.heroPasses, quality.beams,
            quality.lights, quality.vertices, false);
    }

    /**
     * Internal test profile UNLIMITED (debug command only): every resident instance may be drawn, {@code passes}
     * common and HERO passes, the full beams and lights textures, twice the HIGH vertices; shader detail of HIGH.
     * The HERO cap of {@link FrameBudget#MAX_HEROES} still applies.
     */
    public static FrameLimits unlimited(int effects, int passes) {
        return new FrameLimits(Quality.HIGH, effects, passes, passes, MAX_BEAMS, MAX_LIGHTS,
            Quality.HIGH.vertices * 2, true);
    }

    /** {@link #unlimited(int, int)} with no cap on drawn instances nor on passes. */
    public static FrameLimits unlimited() { return unlimited(MAX_EFFECTS, MAX_PASSES); }

    /** Name for F7 and recordings: the profile, or {@code UNLIMITED(effects/passes)} for the test profile. */
    public String label() { return test ? "UNLIMITED(" + effects + "/" + marks + ")" : tier.name(); }
}
