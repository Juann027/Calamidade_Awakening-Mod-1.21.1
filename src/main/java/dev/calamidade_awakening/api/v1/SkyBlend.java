package dev.calamidade_awakening.api.v1;

/**
 * How a sky object is composed over what is already in the sky (0.14.0): the vanilla sky, a {@link Backdrop} and the
 * objects drawn before it. Shared by {@link StarField} and {@link Nebula} (and by the images and impostors planned for
 * later versions), so every object can pick the behavior that fits what it shows instead of one global rule.
 *
 * <p>Every object is drawn with premultiplied alpha: its pixel brings a light {@code rgb} and a coverage {@code a},
 * and the sky behind becomes {@code behind × (1 − a) + rgb}. The three modes only change what the object brings:
 * <ul>
 * <li>{@link #NORMAL}: real transparency. Color × alpha as light, alpha as coverage: a half-transparent nebula shows
 * half of the background behind it; an opaque pixel hides it; transparent pixels hide nothing. Colors stay exact
 * (a red star stays red over a bright background).</li>
 * <li>{@link #LUMINOUS}: emissive light. Color × alpha is added and the coverage is zero: the background behind never
 * disappears, it only gets brighter (stars, glows, halos). Over a very bright background the color drifts toward white,
 * like any light added to a lit sky.</li>
 * <li>{@link #COVER}: hard silhouette. Where the object's alpha is at least one half it covers fully (times the
 * object's opacity); elsewhere it shows nothing: the background is hidden only inside the shape, never as a
 * rectangle.</li>
 * </ul>
 * The engine keeps, per pixel, the light and coverage every object brought this frame, so the sky passes (Atmosphere,
 * eclipse darkening, removal of the vanilla sun and moon, gravitational lens) treat the objects consistently whatever
 * their mode.
 */
public enum SkyBlend {
    /** Alpha blending: coverage = alpha (times opacity), exact colors. */
    NORMAL,
    /** Additive light: no coverage, the sky behind always stays. */
    LUMINOUS,
    /** Silhouette: full coverage where alpha is at least 0.5 (times opacity), none elsewhere. */
    COVER
}
