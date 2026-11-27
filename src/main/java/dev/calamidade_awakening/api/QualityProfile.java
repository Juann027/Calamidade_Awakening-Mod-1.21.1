package dev.calamidade_awakening.api;

/**
 * Quality profiles of the client (0.11.2), pure. Each profile has default caps, editable per profile in the client
 * config and always clamped to the safety maxima of {@link Caps}. A profile picks the shader detail tier addons see
 * ({@link #tier}); ULTRA shows HIGH to addons, so no new value reaches {@code post.quality()}. LOW stays aimed at
 * integrated graphics. Not public API.
 */
public enum QualityProfile {
    /**
     * Integrated graphics: the caps of 0.11.1; fase 3 (0.15.0): 24 lights and 16 cookies (before: 8 and 2). The lights
     * pass culls by screen tiles, so lights far from a pixel cost it nothing (docs/LUZ-0.15.md, "Fase 3").
     */
    LOW(Quality.LOW, new Caps(3, 1, 1, 2, 16, 24, 16)),
    /** Fase 3: 64 lights and 48 cookies (before: 32 and 8). */
    BALANCED(Quality.BALANCED, new Caps(8, 3, 2, 2, 64, 64, 48)),
    /** Fase 3: 128 lights and 96 cookies (before: 64 and 16). */
    HIGH(Quality.HIGH, new Caps(16, 6, 3, 2, 128, 128, 96)),
    /**
     * Strong machines. Measured on an RTX 4060 at 1920 × 1009 (0.11.2): no cost visible within a 144 Hz frame up to 24
     * drawn effects and 12 addon passes; at 48 drawn the p95 rose from 10.4 to 15.7 ms. 32 keeps a margin. Fase 3:
     * 192 lights and 128 cookies (before: 64 and 16), proposed values to be measured on the RTX 4060
     * (docs/VALIDACAO.md, procedure of fase 3).
     */
    ULTRA(Quality.HIGH, new Caps(32, 12, 4, 2, 128, 192, 128));

    /** Lights and cookies caps of each profile before fase 3 (config migration of untouched values). */
    public static final int[] LEGACY_LIGHTS = {8, 32, 64, 64};

    /**
     * (fase 4) Every earlier default of the caps, by profile and cap (drawn, passes, heroPasses, heroes, beams, lights),
     * from the provisional 0.11.2 profiles (BALANCED 6/2/48/24, HIGH 12/4, ULTRA 24/8) to fase 3. A config value equal
     * to one of these was never chosen by the player and moves to the current default once (config migration 3).
     */
    public static final int[][][] LEGACY = {
        {{}, {}, {}, {}, {}, {8}},
        {{6}, {2}, {}, {}, {48}, {24, 32}},
        {{12}, {4}, {}, {}, {}, {64}},
        {{24}, {8}, {}, {}, {}, {64}},
    };

    /** (fase 4) True when {@code value} is an earlier default of cap {@code index} of this profile (not the current one). */
    public boolean legacy(int index, int value) {
        if (index < 0 || index >= LEGACY[ordinal()].length) return false;
        for (int v : LEGACY[ordinal()][index]) if (v == value) return true;
        return false;
    }

    /** (fase 4) The defaults in the order of the client config entries (drawn ... cookies). */
    public int[] defaultValues() {
        var d = defaults;
        return new int[] {d.effects(), d.passes(), d.heroPasses(), d.heroes(), d.beams(), d.lights(), d.cookies()};
    }

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
            caps.beams(), caps.lights(), vertices(), false, skyLayers(), caps.cookies());
    }

    /** Sky layers painted per frame (0.12.0): 2, 4, 6 and 8 for LOW, BALANCED, HIGH and ULTRA. */
    public int skyLayers() { return this == ULTRA ? SkyMath.MAX_LAYERS : SkyMath.layers(tier); }

    /** The applied caps with the defaults. */
    public FrameLimits limits() { return limits(defaults); }

    /**
     * Caps of a profile, clamped to the safety maxima: drawn effects 1..{@link #MAX_EFFECTS}, common passes
     * 0..{@link #MAX_PASSES}, HERO passes 0..{@link #MAX_HERO_PASSES}, HERO drawn 0..{@link #MAX_HEROES} and never
     * above the drawn effects (a HERO is never cut by the effect limit), beams 0..128, lights 0..192 and (fase 3)
     * cookies 0..192, independent of the lights (the applied value never exceeds the lights: {@link FrameLimits}).
     */
    public record Caps(int effects, int passes, int heroPasses, int heroes, int beams, int lights, int cookies) {
        public static final int MAX_EFFECTS = 64, MAX_PASSES = 16, MAX_HERO_PASSES = 8, MAX_HEROES = 4;

        public Caps {
            effects = Math.clamp(effects, 1, MAX_EFFECTS);
            passes = Math.clamp(passes, 0, MAX_PASSES);
            heroPasses = Math.clamp(heroPasses, 0, MAX_HERO_PASSES);
            heroes = Math.clamp(heroes, 0, Math.min(MAX_HEROES, effects));
            beams = Math.clamp(beams, 0, FrameLimits.MAX_BEAMS);
            lights = Math.clamp(lights, 0, FrameLimits.MAX_LIGHTS);
            cookies = Math.clamp(cookies, 0, FrameLimits.MAX_COOKIES);
        }

        /** Pre-fase-3 form (six caps): the cookie budget is the old policy of the lights cap. */
        public Caps(int effects, int passes, int heroPasses, int heroes, int beams, int lights) {
            this(effects, passes, heroPasses, heroes, beams, lights, CookieAtlas.textured(Math.clamp(lights, 0, FrameLimits.MAX_LIGHTS)));
        }
    }
}
