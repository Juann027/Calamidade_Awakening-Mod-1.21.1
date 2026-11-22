package dev.calamidade_awakening.api;

/**
 * Per-frame caps actually applied by the client (0.11.2), pure. A {@link QualityProfile} gives them (defaults or the
 * client config); the internal test profile {@link #unlimited} lifts them for measurements. {@code tier} is what addons
 * see through {@code post.quality()} (shader detail: raymarch steps, segments), so neither ULTRA nor the test profile
 * leaks a new value into the public API. Not public API.
 *
 * @param tier       the quality addons see and that picks shader detail
 * @param name       profile name for F7 and recordings
 * @param effects    most instances drawn per frame (HERO included)
 * @param marks      common addon pass budget (card shaders, post passes, NORMAL beams/lights/terrain passes)
 * @param heroPasses separate HERO pass budget
 * @param heroes     most HERO instances drawn per frame, the nearest ones (never above {@code effects})
 * @param beams      most beams in the engine beams pass (at most {@link #MAX_BEAMS}, the rows of its data texture)
 * @param lights     most lights in the engine lights pass (at most {@link #MAX_LIGHTS})
 * @param vertices   world mesh vertices per frame, shared among the drawn effects
 * @param test       true for the internal test profile (shown in F7 and in recordings)
 * @param skyLayers  most sky layers painted per frame by the engine's sky pass (0.12.0, at most {@link SkyMath#MAX_LAYERS})
 */
public record FrameLimits(Quality tier, String name, int effects, int marks, int heroPasses, int heroes, int beams,
                          int lights, int vertices, boolean test, int skyLayers) {
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
        heroes = Math.clamp(heroes, 0, effects);
        beams = Math.clamp(beams, 0, MAX_BEAMS);
        lights = Math.clamp(lights, 0, MAX_LIGHTS);
        vertices = Math.max(0, vertices);
        skyLayers = Math.clamp(skyLayers, 0, SkyMath.MAX_LAYERS);
    }

    /** 0.11.2 form: the sky layers of the tier ({@link SkyMath#layers}). */
    public FrameLimits(Quality tier, String name, int effects, int marks, int heroPasses, int heroes, int beams,
                       int lights, int vertices, boolean test) {
        this(tier, name, effects, marks, heroPasses, heroes, beams, lights, vertices, test, SkyMath.layers(tier));
    }

    /** The caps of a quality tier exactly as before 0.11.2 (LOW, BALANCED, HIGH with 2 HERO). */
    public static FrameLimits of(Quality quality) {
        return new FrameLimits(quality, quality.name(), quality.effects, quality.marks, quality.heroPasses,
            FrameBudget.MAX_HEROES, quality.beams, quality.lights, quality.vertices, false);
    }

    /**
     * Internal test profile UNLIMITED (debug command only): every resident instance may be drawn, {@code passes}
     * common and HERO passes, the full beams and lights textures, twice the HIGH vertices; shader detail of HIGH.
     * HERO drawn stay at {@link FrameBudget#MAX_HEROES}.
     */
    public static FrameLimits unlimited(int effects, int passes) {
        int e = Math.clamp(effects, 0, MAX_EFFECTS), p = Math.clamp(passes, 0, MAX_PASSES);
        return new FrameLimits(Quality.HIGH, "UNLIMITED(" + e + "/" + p + ")", e, p, p, FrameBudget.MAX_HEROES,
            MAX_BEAMS, MAX_LIGHTS, Quality.HIGH.vertices * 2, true, SkyMath.MAX_LAYERS);
    }

    /** {@link #unlimited(int, int)} with no cap on drawn instances nor on passes. */
    public static FrameLimits unlimited() { return unlimited(MAX_EFFECTS, MAX_PASSES); }

    /** Name for F7 and recordings: the profile, or {@code UNLIMITED(effects/passes)} for the test profile. */
    public String label() { return name; }
}
