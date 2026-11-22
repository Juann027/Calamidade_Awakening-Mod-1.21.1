package dev.calamidade_awakening.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Accessibility settings applied centrally to every screen effect, from the engine and from addons
 * (calamidade_awakening-client.toml, also editable in the mod list). Safe to load on a dedicated server.
 */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue SCREEN_EFFECTS;
    private static final ModConfigSpec.BooleanValue CAMERA_SHAKE;
    private static final ModConfigSpec.BooleanValue MOTION_EFFECTS;
    private static final ModConfigSpec.BooleanValue VANILLA_FOG;
    /** Quality profile chosen with F8 or in the Atlas (0.11.2). */
    private static final ModConfigSpec.EnumValue<dev.calamidade_awakening.api.QualityProfile> QUALITY;
    /** Caps of each profile (0.11.2): drawn, passes, heroPasses, heroes, beams, lights, in the order of the enum. */
    private static final java.util.Map<dev.calamidade_awakening.api.QualityProfile, ModConfigSpec.IntValue[]> CAPS =
        new java.util.EnumMap<>(dev.calamidade_awakening.api.QualityProfile.class);
    /** Names of the cap entries, also their translation keys. */
    public static final String[] CAP_NAMES = {"drawn", "passes", "heroPasses", "heroes", "beams", "lights"};

    static {
        var builder = new ModConfigSpec.Builder();
        SCREEN_EFFECTS = builder
            .comment("Intensity of bloom, flashes, contrast, desaturation, aberration, vignette, grain, zoom blur, afterimage, atmosphere exposure and ambient tint from Calamidade_Awakening and its addons (0 = off, 1 = as designed).")
            .translation("calamidade_awakening.configuration.screenEffects")
            .defineInRange("screenEffects", 1.0, 0.0, 1.0);
        CAMERA_SHAKE = builder
            .comment("Screen shake from Calamidade_Awakening and its addons.")
            .translation("calamidade_awakening.configuration.cameraShake")
            .define("cameraShake", true);
        MOTION_EFFECTS = builder
            .comment("Zoom blur and afterimage trails from Calamidade_Awakening and its addons. Turn off if they cause motion sickness.")
            .translation("calamidade_awakening.configuration.motionEffects")
            .define("motionEffects", true);
        VANILLA_FOG = builder
            .comment("Atmosphere fog (0.8.0) through the game's own fog, so terrain, entities, particles, water and clouds are fogged at their real distance. Turn off only if a shader pack ignores the game fog: the engine then draws the fog on the screen from the depth buffer.")
            .translation("calamidade_awakening.configuration.vanillaFog")
            .define("vanillaFog", true);
        // 0.11.2: quality profiles. The caps are clamped again by QualityProfile.Caps (safety maxima).
        builder.comment("Quality profiles (0.11.2): the profile in use and the per-frame caps of each one.")
            .translation("calamidade_awakening.configuration.quality").push("quality");
        QUALITY = builder
            .comment("Profile in use: LOW (integrated graphics), BALANCED, HIGH or ULTRA (strong machines). F8 cycles it.")
            .translation("calamidade_awakening.configuration.profile")
            .defineEnum("profile", dev.calamidade_awakening.api.QualityProfile.BALANCED);
        for (var profile : dev.calamidade_awakening.api.QualityProfile.values()) {
            var d = profile.defaults;
            String key = profile.name().toLowerCase(java.util.Locale.ROOT);
            builder.comment("Caps of the " + profile.name() + " profile.")
                .translation("calamidade_awakening.configuration." + key).push(key);
            CAPS.put(profile, new ModConfigSpec.IntValue[] {
                cap(builder, "drawn", "Effects drawn per frame (HERO included).", d.effects(), 1,
                    dev.calamidade_awakening.api.QualityProfile.Caps.MAX_EFFECTS),
                cap(builder, "passes", "Common addon passes per frame (card shaders, post passes, beams and lights of NORMAL effects).",
                    d.passes(), 0, dev.calamidade_awakening.api.QualityProfile.Caps.MAX_PASSES),
                cap(builder, "heroPasses", "Passes reserved for HERO effects per frame.", d.heroPasses(), 0,
                    dev.calamidade_awakening.api.QualityProfile.Caps.MAX_HERO_PASSES),
                cap(builder, "heroes", "HERO effects drawn per frame, the nearest ones.", d.heroes(), 0,
                    dev.calamidade_awakening.api.QualityProfile.Caps.MAX_HEROES),
                cap(builder, "beams", "Beams drawn per frame.", d.beams(), 0, dev.calamidade_awakening.api.FrameLimits.MAX_BEAMS),
                cap(builder, "lights", "World lights drawn per frame (visual only).", d.lights(), 0,
                    dev.calamidade_awakening.api.FrameLimits.MAX_LIGHTS)});
            builder.pop();
        }
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {}

    private static ModConfigSpec.IntValue cap(ModConfigSpec.Builder builder, String name, String comment, int value,
                                              int min, int max) {
        return builder.comment(comment).translation("calamidade_awakening.configuration." + name)
            .defineInRange(name, value, min, max);
    }

    /** Profile in use (0.11.2); BALANCED until the config file is loaded. */
    public static dev.calamidade_awakening.api.QualityProfile qualityProfile() {
        try {
            return QUALITY.get();
        } catch (IllegalStateException notLoaded) {
            return dev.calamidade_awakening.api.QualityProfile.BALANCED;
        }
    }

    /** Stores the profile in use (F8, Atlas); ignored before the config file is loaded. */
    public static void qualityProfile(dev.calamidade_awakening.api.QualityProfile profile) {
        try {
            QUALITY.set(profile);
            QUALITY.save();
        } catch (IllegalStateException notLoaded) {
            // Nothing to store yet.
        }
    }

    /** Caps of a profile from the config, clamped to the safety maxima; the defaults until the file is loaded. */
    public static dev.calamidade_awakening.api.QualityProfile.Caps caps(dev.calamidade_awakening.api.QualityProfile profile) {
        try {
            var v = CAPS.get(profile);
            return new dev.calamidade_awakening.api.QualityProfile.Caps(v[0].get(), v[1].get(), v[2].get(), v[3].get(),
                v[4].get(), v[5].get());
        } catch (IllegalStateException notLoaded) {
            return profile.defaults;
        }
    }

    /** Changes one cap of a profile ({@code index} in {@link #CAP_NAMES}) and saves; ignored before loading. */
    public static void cap(dev.calamidade_awakening.api.QualityProfile profile, int index, int value) {
        try {
            var entry = CAPS.get(profile)[index];
            entry.set(value);
            entry.save();
        } catch (IllegalStateException notLoaded) {
            // Nothing to store yet.
        }
    }

    /** Multiplier for screen effects; 1 until the config file is loaded. */
    public static float screenEffects() {
        try {
            return SCREEN_EFFECTS.get().floatValue();
        } catch (IllegalStateException notLoaded) {
            return 1f;
        }
    }

    public static boolean cameraShake() {
        try {
            return CAMERA_SHAKE.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** Atmosphere fog through the game's fog events (0.8.0); true until the config file is loaded. */
    public static boolean vanillaFog() {
        try {
            return VANILLA_FOG.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** Zoom blur and afterimage (0.7.0); true until the config file is loaded. */
    public static boolean motionEffects() {
        try {
            return MOTION_EFFECTS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}
