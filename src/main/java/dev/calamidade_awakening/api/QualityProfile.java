package dev.calamidade_awakening.api;

/**
 * Quality profiles of the client (0.11.2), pure. Each profile has default caps, editable per profile in the client
 * config and always clamped to the safety maxima of {@link Caps}. A profile picks the shader detail tier addons see
 * ({@link #tier}); ULTRA shows HIGH to addons, so no new value reaches {@code post.quality()}. LOW stays aimed at
 * integrated graphics. Not public API.
 */
public enum QualityProfile {
    /** Integrated graphics: the caps of 0.11.1. */
    LOW(Quality.LOW, new Caps(3, 1, 1, 2, 16, 8)),
    BALANCED(Quality.BALANCED, new Caps(8, 3, 2, 2, 64, 32)),
    HIGH(Quality.HIGH, new Caps(16, 6, 3, 2, 128, 64)),
    /**
     * Strong machines. Measured on an RTX 4060 at 1920 × 1009 (0.11.2): no cost visible within a 144 Hz frame up to 24
     * drawn effects and 12 addon passes; at 48 drawn the p95 rose from 10.4 to 15.7 ms. 32 keeps a margin.
     */
    ULTRA(Quality.HIGH, new Caps(32, 12, 4, 2, 128, 64));

    /** Shader detail tier shown to addons and used for steps, segments and terrain stand-ins. */
    public final Quality tier;
    /** Default caps of the profile. */
    public final Caps defaults;

    QualityProfile(Quality tier, Caps defaults) {
        this.tier = tier;
        this.defaults = defaults;
    }

    /** One step down, or this profile when already the lowest. */
    public QualityProfile lower() { return this == LOW ? LOW : values()[ordinal() - 1]; }

    /** One step up, or this profile when already the highest. */
    public QualityProfile higher() { return this == ULTRA ? ULTRA : values()[ordinal() + 1]; }

    /** World mesh vertices per frame: those of the tier, 1.5 times for ULTRA. */
    public int vertices() { return this == ULTRA ? tier.vertices * 3 / 2 : tier.vertices; }

    /** The applied caps of this profile with the given (already clamped) values. */
    public FrameLimits limits(Caps caps) {
        return new FrameLimits(tier, name(), caps.effects(), caps.passes(), caps.heroPasses(), caps.heroes(),
            caps.beams(), caps.lights(), vertices(), false);
    }

    /** The applied caps with the defaults. */
    public FrameLimits limits() { return limits(defaults); }

    /**
     * Caps of a profile, clamped to the safety maxima: drawn effects 1..{@link #MAX_EFFECTS}, common passes
     * 0..{@link #MAX_PASSES}, HERO passes 0..{@link #MAX_HERO_PASSES}, HERO drawn 0..{@link #MAX_HEROES} and never
     * above the drawn effects (a HERO is never cut by the effect limit), beams 0..128, lights 0..64.
     */
    public record Caps(int effects, int passes, int heroPasses, int heroes, int beams, int lights) {
        public static final int MAX_EFFECTS = 64, MAX_PASSES = 16, MAX_HERO_PASSES = 8, MAX_HEROES = 4;

        public Caps {
            effects = Math.clamp(effects, 1, MAX_EFFECTS);
            passes = Math.clamp(passes, 0, MAX_PASSES);
            heroPasses = Math.clamp(heroPasses, 0, MAX_HERO_PASSES);
            heroes = Math.clamp(heroes, 0, Math.min(MAX_HEROES, effects));
            beams = Math.clamp(beams, 0, FrameLimits.MAX_BEAMS);
            lights = Math.clamp(lights, 0, FrameLimits.MAX_LIGHTS);
        }
    }
}
