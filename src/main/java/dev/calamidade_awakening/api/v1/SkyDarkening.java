package dev.calamidade_awakening.api.v1;

/**
 * How a sky object reacts when sky layers darken the world (0.14.0), for example during the totality of an
 * {@link Sky#eclipse() eclipse}. Shared by {@link StarField} and {@link Nebula}. The strength of the reaction is a
 * separate 0..1 factor on each object.
 *
 * <p>The engine never darkens sky objects by itself: each object applies its own reaction, so stars can come out
 * during an eclipse while the background and the terrain go dark.
 */
public enum SkyDarkening {
    /** Darkened like the world and the background (strength 1: exactly as much). */
    DIM,
    /**
     * Revealed by the darkness: the darkening removes daylight, so an object that daylight hides
     * ({@code daylight} above 0) shows up as the sky darkens, like stars during a total eclipse. Never dimmed.
     */
    REVEAL,
    /** Not affected. */
    IGNORE
}
