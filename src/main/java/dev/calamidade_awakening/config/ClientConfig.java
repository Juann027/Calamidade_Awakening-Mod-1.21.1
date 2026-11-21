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
        SPEC = builder.build();
    }

    private ClientConfig() {}

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
