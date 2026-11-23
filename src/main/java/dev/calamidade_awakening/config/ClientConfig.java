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
    /** 0.14.0: custom sky backgrounds (Backdrop) of addons; off draws none of them. */
    private static final ModConfigSpec.BooleanValue CUSTOM_SKIES;
    /** 0.13.0: boss bar accessibility. */
    private static final ModConfigSpec.DoubleValue BOSS_BAR_SCALE;
    private static final ModConfigSpec.BooleanValue BOSS_BAR_CONTRAST;
    private static final ModConfigSpec.BooleanValue BOSS_BAR_LABELS;
    private static final ModConfigSpec.BooleanValue BOSS_BAR_VANILLA;
    /** Quality mode chosen with F8 or in the Atlas (0.11.2): AUTO or a forced profile. */
    private static final ModConfigSpec.EnumValue<dev.calamidade_awakening.api.QualityMode> QUALITY;
    /** Frame rate AUTO aims at (0.11.2). */
    private static final ModConfigSpec.IntValue AUTO_TARGET_FPS;
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
        CUSTOM_SKIES = builder
            .comment("Custom skies of addons (0.14.0): backgrounds (skyboxes), star fields, constellations and nebulae. Off: the vanilla sky stays; the engine's procedural sky layers are not affected.")
            .translation("calamidade_awakening.configuration.customSkies")
            .define("customSkies", true);
        // 0.11.2: quality profiles. The caps are clamped again by QualityProfile.Caps (safety maxima).
        builder.comment("Quality profiles (0.11.2): the profile in use and the per-frame caps of each one.")
            .translation("calamidade_awakening.configuration.quality").push("quality");
        QUALITY = builder
            .comment("AUTO (default: moves between the profiles by the p95 of the frame time, with hysteresis) or a forced profile: LOW (integrated graphics), BALANCED, HIGH or ULTRA (strong machines). F8 cycles it.")
            .translation("calamidade_awakening.configuration.mode")
            .defineEnum("mode", dev.calamidade_awakening.api.QualityMode.AUTO);
        AUTO_TARGET_FPS = builder
            .comment("Frame rate AUTO aims at: it goes down when the p95 frame time stays 20% above 1000/fps while effects are drawn, and up when it stays 30% below while the caps cut effects. With VSync or a frame cap at this rate, AUTO only goes down.")
            .translation("calamidade_awakening.configuration.autoTargetFps")
            .defineInRange("autoTargetFps", 60, 30, 240);
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
        // 0.13.0: boss bars of addons (Boss API).
        builder.comment("Boss bars (0.13.0) drawn with a custom style by addons that use the Boss API.")
            .translation("calamidade_awakening.configuration.bossBar").push("bossBar");
        BOSS_BAR_SCALE = builder.comment("Size of custom boss bars (0.5 to 2).")
            .translation("calamidade_awakening.configuration.bossBarScale")
            .defineInRange("scale", 1.0, 0.5, 2.0);
        BOSS_BAR_CONTRAST = builder.comment("Solid, high-contrast colors and a dark backing for custom boss bars.")
            .translation("calamidade_awakening.configuration.bossBarContrast")
            .define("highContrast", false);
        BOSS_BAR_LABELS = builder.comment("Show the phase name under custom boss bars.")
            .translation("calamidade_awakening.configuration.bossBarLabels")
            .define("phaseLabels", true);
        BOSS_BAR_VANILLA = builder.comment("Always draw the vanilla boss bar, ignoring the custom styles of addons.")
            .translation("calamidade_awakening.configuration.bossBarVanilla")
            .define("forceVanilla", false);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {}

    /** Atlas accessibility tab (0.11.2): stores a value and saves the file; ignored before the file is loaded. */
    public static void screenEffects(float value) { store(SCREEN_EFFECTS, (double) Math.clamp(value, 0F, 1F)); }

    public static void cameraShake(boolean value) { store(CAMERA_SHAKE, value); }

    public static void motionEffects(boolean value) { store(MOTION_EFFECTS, value); }

    public static void vanillaFog(boolean value) { store(VANILLA_FOG, value); }

    private static <T> void store(ModConfigSpec.ConfigValue<T> entry, T value) {
        try {
            entry.set(value);
            entry.save();
        } catch (IllegalStateException notLoaded) {
            // Nothing to store yet.
        }
    }

    private static ModConfigSpec.IntValue cap(ModConfigSpec.Builder builder, String name, String comment, int value,
                                              int min, int max) {
        return builder.comment(comment).translation("calamidade_awakening.configuration." + name)
            .defineInRange(name, value, min, max);
    }

    /** Quality mode (0.11.2); AUTO until the config file is loaded. */
    public static dev.calamidade_awakening.api.QualityMode qualityMode() {
        try {
            return QUALITY.get();
        } catch (IllegalStateException notLoaded) {
            return dev.calamidade_awakening.api.QualityMode.AUTO;
        }
    }

    /** Frame rate AUTO aims at; 60 until the config file is loaded. */
    public static int autoTargetFps() {
        try {
            return AUTO_TARGET_FPS.get();
        } catch (IllegalStateException notLoaded) {
            return 60;
        }
    }

    /** Changes the AUTO target frame rate and saves; ignored before loading. */
    public static void autoTargetFps(int fps) {
        try {
            AUTO_TARGET_FPS.set(Math.clamp(fps, 30, 240));
            AUTO_TARGET_FPS.save();
        } catch (IllegalStateException notLoaded) {
            // Nothing to store yet.
        }
    }

    /** Stores the quality mode (F8, Atlas); ignored before the config file is loaded. */
    public static void qualityMode(dev.calamidade_awakening.api.QualityMode mode) {
        try {
            QUALITY.set(mode);
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

    /** Custom sky backgrounds (0.14.0); true until the config file is loaded. */
    public static boolean customSkies() {
        try {
            return CUSTOM_SKIES.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static void customSkies(boolean value) { store(CUSTOM_SKIES, value); }

    /** Zoom blur and afterimage (0.7.0); true until the config file is loaded. */
    public static boolean motionEffects() {
        try {
            return MOTION_EFFECTS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** Custom boss bar scale 0.5..2 (0.13.0); 1 until the config file is loaded. */
    public static double bossBarScale() {
        try {
            return BOSS_BAR_SCALE.get();
        } catch (IllegalStateException notLoaded) {
            return 1;
        }
    }

    /** High-contrast custom boss bars (0.13.0). */
    public static boolean bossBarHighContrast() {
        try {
            return BOSS_BAR_CONTRAST.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    /** Phase labels under custom boss bars (0.13.0). */
    public static boolean bossBarLabels() {
        try {
            return BOSS_BAR_LABELS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** Ignore custom boss bar styles (0.13.0). */
    public static boolean bossBarVanilla() {
        try {
            return BOSS_BAR_VANILLA.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    /** Test hook (0.13.0): sets the boss bar options in memory. */
    public static void bossBar(double scale, boolean highContrast, boolean labels, boolean vanilla) {
        store(BOSS_BAR_SCALE, Math.clamp(scale, .5, 2));
        store(BOSS_BAR_CONTRAST, highContrast);
        store(BOSS_BAR_LABELS, labels);
        store(BOSS_BAR_VANILLA, vanilla);
    }
}
