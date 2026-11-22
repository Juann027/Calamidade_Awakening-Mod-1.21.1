package dev.calamidade_awakening.api.v1;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable custom sky background of one effect or one {@link SkyProfile.State} (0.14.0, "Backdrop"): a skybox drawn
 * behind everything else, as a {@link Kind#CUBEMAP cubemap}, an equirectangular {@link Kind#PANORAMA panorama} or a
 * {@link Kind#GRADIENT gradient}. Pure Java, no Minecraft types. Nothing is drawn unless an addon attaches a backdrop
 * ({@link Effects#backdrop}, {@link SkyProfile.StateBuilder#backdrop}); players can turn every custom background off
 * with the client option {@code customSkies}.
 *
 * <p><b>How it is drawn.</b> The client draws the background right after the vanilla sky
 * ({@code RenderLevelStageEvent.Stage.AFTER_SKY}), as static geometry around the camera with the game's own textured
 * shader, writing no depth: terrain, entities and the vanilla clouds draw in front of it, and the engine's passes
 * (sky layers, gravitational lens, eclipse darkening, Atmosphere) work on top of it. A static background turns on no
 * post-processing pass by itself. It follows the same rules as the vanilla sky: it is not drawn with blindness or
 * darkness, with the camera in lava or powder snow, or under a foggy biome or a boss fog.
 *
 * <p><b>Textures</b> are resource locations of PNGs in a resource pack, like {@code "meumod:textures/sky/ceu.png"},
 * loaded once on first use and again on resource reload, sampled without filtering (pixel art, like the game):
 * <ul>
 * <li>{@link #cubemap}: one PNG with the six faces in 3 columns × 2 rows, all of the same square size. Row 0: north
 * (−Z), east (+X), south (+Z); row 1: west (−X), up (+Y), down (−Y). Each side face is seen from inside, with the top
 * of the image up. The up face has north at the top of the image and east on the right; the down face has south at
 * the top and east on the right.</li>
 * <li>{@link #panorama}: one equirectangular PNG (2:1 recommended): the horizontal center of the image is south,
 * the image turns toward west to the right (azimuth 90 at three quarters of the width), the top row is the zenith and
 * the bottom row the nadir.</li>
 * </ul>
 * Texture alpha is coverage: transparent pixels let the vanilla sky (or a background below) show through.
 *
 * <p><b>Composition</b> (what the engine guarantees): over a background, the Atmosphere sky gradient mixes onto the
 * background as it would onto the vanilla sky; the eclipse darkens it; the lens bends it; the vanilla sun and moon are
 * hidden where it covers them (or drawn again on top of it with {@link Builder#vanillaSunMoon(boolean)}), and a sky
 * layer that hides them hides the redrawn ones too. With {@link Builder#matchFog(boolean)} (default on) the fog of
 * distant terrain takes the background's horizon color, so there is no light line where terrain meets the background.
 *
 * <p><b>Weight and transitions.</b> The background opacity is {@link Builder#opacity} times the instance weight
 * (opacity envelope, range fade), an optional phase and an optional {@link Param#OPACITY} track; a sky profile
 * crossfades the backgrounds of two states. At most two backgrounds are drawn per frame: the most important one
 * (global events first, then the dimension's profile, then HERO effects, then by opacity) and, while it is not fully
 * opaque, the next one below it, so one background fades into another without the vanilla sky showing between them.
 *
 * <pre>{@code
 * static final Backdrop NEBULOSA = Backdrop.panorama("meumod:textures/sky/nebulosa.png").spin(.5f).build();
 * Effects.backdrop(ID, NEBULOSA);   // after register and timeline
 * }</pre>
 *
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class Backdrop {
    /** Opacity below which a background is not drawn. */
    public static final float ACTIVE_OPACITY = 1e-3f;
    /** Fastest spin, degrees per second. */
    public static final float MAX_SPIN = 90f;
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern TEXTURE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+\\.png");

    /** How the background is mapped onto the sky. */
    public enum Kind {
        /** Six square faces in one 3 × 2 PNG (see the class documentation for the layout). */
        CUBEMAP,
        /** One equirectangular PNG: azimuth across, elevation down from the zenith. */
        PANORAMA,
        /** Vertical gradient from three ARGB colors (zenith, horizon, nadir); alpha is coverage. */
        GRADIENT
    }

    /** Numeric parameters that can be driven by a timeline track, each with the range used to clamp it. */
    public enum Param {
        /** Opacity multiplier, 0..1. */
        OPACITY(0, 1),
        /** Turn around the vertical axis, degrees (positive moves what was south toward west). */
        ROTATION(-3600, 3600);

        /** Smallest and largest value of the parameter; track values are clamped to this range. */
        public final float min, max;

        Param(float min, float max) { this.min = min; this.max = max; }

        /** Clamps a value into this parameter's range; NaN becomes the value closest to 0 in the range. */
        public float clamp(float value) {
            return Float.isNaN(value) ? Math.clamp(0, min, max) : Math.clamp(value, min, max);
        }
    }

    /**
     * A background resolved at one moment.
     *
     * @param opacity  effective opacity, 0..1 (the builder opacity times weight, phase and track)
     * @param rotation turn around the vertical axis without the spin, degrees
     * @param spin     spin, degrees per second (applied by the client over the instance's real age)
     */
    public record State(float opacity, float rotation, float spin) {
        /** True when the background should be drawn. */
        public boolean visible() { return opacity > ACTIVE_OPACITY; }

        /** Turn after {@code seconds} of real age, degrees (spin stops when {@code motion} is false). */
        public double rotationAt(double seconds, boolean motion) {
            return rotation + (motion ? spin * seconds : 0);
        }
    }

    private final Kind kind;
    private final String texture;
    private final int top, horizon, bottom, tint;
    private final float opacity, rotation, spin;
    private final boolean vanillaSunMoon, matchFog;
    private final Map<Param, String> tracks;
    private final String weightPhase;

    private Backdrop(Builder b) {
        kind = b.kind; texture = b.texture; top = b.top; horizon = b.horizon; bottom = b.bottom; tint = b.tint;
        opacity = b.opacity; rotation = b.rotation; spin = b.spin; vanillaSunMoon = b.vanillaSunMoon; matchFog = b.matchFog;
        tracks = Collections.unmodifiableMap(new EnumMap<>(b.tracks));
        weightPhase = b.weightPhase;
    }

    /** Cubemap from one 3 × 2 PNG, as {@code namespace:path/file.png}. */
    public static Builder cubemap(String texture) { return new Builder(Kind.CUBEMAP, texture(texture)); }

    /** Equirectangular panorama from one PNG, as {@code namespace:path/file.png}. */
    public static Builder panorama(String texture) { return new Builder(Kind.PANORAMA, texture(texture)); }

    /**
     * Vertical gradient: {@code top} at the zenith, {@code horizon} at the horizon and {@code bottom} at the nadir, all
     * ARGB with alpha as coverage (0xff opaque). Above the horizon it eases to the zenith color by 0.6 of the height
     * (like the Atmosphere gradient); below, to the nadir color by 0.3.
     */
    public static Builder gradient(int top, int horizon, int bottom) {
        Builder b = new Builder(Kind.GRADIENT, null);
        b.top = top; b.horizon = horizon; b.bottom = bottom;
        return b;
    }

    private static String texture(String id) {
        Objects.requireNonNull(id, "texture");
        if (!TEXTURE.matcher(id).matches())
            throw new IllegalArgumentException("Expected a PNG resource location namespace:path/file.png, got " + id);
        return id;
    }

    /** Mapping of the background. */
    public Kind kind() { return kind; }
    /** Texture resource location of a cubemap or panorama, or null for a gradient. */
    public String texture() { return texture; }
    /** Gradient zenith color, ARGB (0 for textures). */
    public int top() { return top; }
    /** Gradient horizon color, ARGB (0 for textures). */
    public int horizon() { return horizon; }
    /** Gradient nadir color, ARGB (0 for textures). */
    public int bottom() { return bottom; }
    /** Tint multiplied into the background, ARGB (alpha multiplies the opacity); white by default. */
    public int tint() { return tint; }
    /** Opacity, 0..1 (1 by default). */
    public float opacity() { return opacity; }
    /** Turn around the vertical axis, degrees. */
    public float rotation() { return rotation; }
    /** Spin, degrees per second. */
    public float spin() { return spin; }
    /** True when the vanilla sun and moon are drawn again on top of the background. */
    public boolean vanillaSunMoon() { return vanillaSunMoon; }
    /** True when the fog of distant terrain takes the background's horizon color (default). */
    public boolean matchFog() { return matchFog; }
    /** Track bound to a parameter, or null when the parameter is constant. */
    public String track(Param param) { return tracks.get(Objects.requireNonNull(param)); }
    /** Every track binding, in {@code Param} order. */
    public Map<Param, String> tracks() { return tracks; }
    /** Phase whose weight multiplies the opacity, or null. */
    public String weightPhase() { return weightPhase; }
    /** True when the opacity or the rotation depends on the timeline. */
    public boolean animated() { return !tracks.isEmpty() || weightPhase != null; }

    /**
     * Checks that every track and the phase this background reads exist in {@code timeline}, which may be null only
     * when nothing is {@link #animated() animated}. Called by {@link Effects#backdrop}.
     */
    public void validate(Timeline timeline) {
        if (!animated()) return;
        if (timeline == null)
            throw new IllegalArgumentException("Backdrop reads tracks or a phase: register the effect's timeline first");
        tracks.forEach((param, track) -> {
            if (!timeline.hasTrack(track)) throw new IllegalArgumentException("Backdrop " + param + " reads unknown track: " + track);
        });
        if (weightPhase != null && !timeline.hasPhase(weightPhase))
            throw new IllegalArgumentException("Backdrop weight reads unknown phase: " + weightPhase);
    }

    /**
     * Resolves this background at one moment: {@code timeline} is the instance's sampler at its local age (any sampler
     * works when nothing is animated); {@code envelope} is the instance's weight (0..1), which multiplies the opacity
     * together with the phase, the {@link Param#OPACITY} track and the tint alpha.
     */
    public State sample(Timeline.Sampler timeline, float envelope) {
        Objects.requireNonNull(timeline, "timeline");
        float weight = Float.isNaN(envelope) ? 0 : Math.clamp(envelope, 0f, 1f);
        if (weightPhase != null) weight *= timeline.weight(weightPhase);
        String opacityTrack = tracks.get(Param.OPACITY);
        float o = opacity * (opacityTrack == null ? 1 : Param.OPACITY.clamp(timeline.value(opacityTrack)));
        o *= ((tint >>> 24) & 0xFF) / 255f;
        String rotationTrack = tracks.get(Param.ROTATION);
        float r = rotationTrack == null ? rotation : Param.ROTATION.clamp(timeline.value(rotationTrack));
        return new State(Math.clamp(o * weight, 0f, 1f), r, spin);
    }

    /** Short description for logs: kind and texture. */
    @Override public String toString() { return "Backdrop[" + kind + (texture == null ? "" : " " + texture) + "]"; }

    /** Builder of a {@link Backdrop}. Every setter validates its arguments right away. */
    public static final class Builder {
        private final Kind kind;
        private final String texture;
        private int top, horizon, bottom, tint = 0xffffffff;
        private float opacity = 1, rotation, spin;
        private boolean vanillaSunMoon, matchFog = true;
        private final Map<Param, String> tracks = new EnumMap<>(Param.class);
        private String weightPhase;

        private Builder(Kind kind, String texture) {
            this.kind = kind;
            this.texture = texture;
        }

        /** Opacity, 0..1 (1 by default): below 1 the vanilla sky shows through everywhere. */
        public Builder opacity(float value) {
            if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Expected opacity in [0, 1]");
            opacity = value;
            return this;
        }

        /** Turn around the vertical axis, degrees, -3600..3600 (positive moves what was south toward west). */
        public Builder rotation(float degrees) {
            if (!Float.isFinite(degrees) || Math.abs(degrees) > 3600) throw new IllegalArgumentException("Expected rotation within ±3600 degrees");
            rotation = degrees;
            return this;
        }

        /**
         * Spin, degrees per second (±{@value #MAX_SPIN}), over the instance's real age (the world clock for a sky
         * profile); stops when the player turns "motion effects" off.
         */
        public Builder spin(float degreesPerSecond) {
            if (!Float.isFinite(degreesPerSecond) || Math.abs(degreesPerSecond) > MAX_SPIN)
                throw new IllegalArgumentException("Expected spin within ±" + MAX_SPIN + " degrees per second");
            spin = degreesPerSecond;
            return this;
        }

        /** Tint multiplied into the background, ARGB; its alpha multiplies the opacity (white, opaque by default). */
        public Builder tint(int argb) { tint = argb; return this; }

        /**
         * Draws the vanilla sun and moon again on top of the background (false by default: where the background
         * covers the sky, they are hidden like the rest of the vanilla sky). A sky layer that hides the sun or moon
         * (an eclipse) still hides them.
         */
        public Builder vanillaSunMoon(boolean draw) { vanillaSunMoon = draw; return this; }

        /** Whether the fog of distant terrain takes the background's horizon color (true by default). */
        public Builder matchFog(boolean match) { matchFog = match; return this; }

        /** Takes {@code param} from a track of the effect's timeline instead of the constant (clamped to its range). */
        public Builder track(Param param, String track) {
            Objects.requireNonNull(param, "param");
            tracks.put(param, name("track", track));
            return this;
        }

        /** Multiplies the opacity by the weight of a timeline phase (0 outside it). */
        public Builder weightPhase(String phase) {
            weightPhase = name("phase", phase);
            return this;
        }

        /** Immutable background. */
        public Backdrop build() { return new Backdrop(this); }
    }

    private static String name(String what, String value) {
        Objects.requireNonNull(value, what);
        if (!NAME.matcher(value).matches()) throw new IllegalArgumentException("Invalid " + what + " name: " + value);
        return value;
    }
}
