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

    static {
        var builder = new ModConfigSpec.Builder();
        SCREEN_EFFECTS = builder
            .comment("Intensity of bloom, flashes, contrast and desaturation from Calamidade_Awakening and its addons (0 = off, 1 = as designed).")
            .translation("calamidade_awakening.configuration.screenEffects")
            .defineInRange("screenEffects", 1.0, 0.0, 1.0);
        CAMERA_SHAKE = builder
            .comment("Screen shake from Calamidade_Awakening and its addons.")
            .translation("calamidade_awakening.configuration.cameraShake")
            .define("cameraShake", true);
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
}
