package dev.calamidade_awakening.api.v1;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable pre-rendered 3D object in the sky of one effect or one {@link SkyProfile.State} (0.14.0): an impostor. The
 * addon renders its model (Blockbench, Blender or any other tool) from several angles into PNGs, and the engine shows,
 * as a billboard, the view that matches how the object is oriented relative to the camera, blending neighbouring views
 * so turning never jumps. No geometry is loaded; real 3D models are planned for 0.15 and this does not replace them.
 * Pure Java, no Minecraft types. Nothing is drawn unless an addon attaches an impostor ({@link Effects#impostors},
 * {@link SkyProfile.StateBuilder#impostors}).
 *
 * <p><b>Resources.</b> A descriptor {@code assets/<namespace>/calamidade/impostors/<name>.json} (format 1) names the
 * PNG pages, the frame size, the grid, the views (yaw count, pitch list, mirror), the animation frames and optional
 * smaller levels of detail; {@code scripts/gerar_impostor.cjs} builds the pages and the descriptor from a folder of
 * pre-rendered PNGs. The format is documented in {@code docs/CEU-PERSONALIZADO.md}. A missing or invalid descriptor or
 * page is logged once and the impostor is skipped; the rest of the sky goes on.
 *
 * <p><b>Views.</b> The object sits at infinity in its {@link Builder#direction direction}, so the camera always sees it
 * along that direction: the view only depends on the object's {@link Builder#orientation orientation} and on where it
 * is in the sky (and, with {@link Heading#PATH}, on where it is moving). View (yaw θ, pitch φ) is the camera in the
 * object's local direction of azimuth θ and elevation φ (θ 0 = the object's front, its local +Z; θ 90 = its local −X),
 * the same convention as {@link Sky.Direction}. No parallax, lighting baked into the pictures.
 *
 * <p>Composition, reactions and layers are those of {@link SkyImage}. Levels of detail switch with hysteresis and only
 * where both levels are being minified on screen, so the switch does not show.
 *
 * <pre>{@code
 * static final SkyImpostor NAVE = SkyImpostor.of("meumod:nave")
 *     .direction(90, 20).size(12).orientation(45, 0, 0).build();
 * Effects.impostors(ID, NAVE);   // after register and timeline
 * }</pre>
 *
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class SkyImpostor {
    /** Most impostors per effect or profile state. */
    public static final int MAX_PER_EFFECT = 4;
    /** Opacity below which an impostor is not drawn. */
    public static final float ACTIVE_OPACITY = 1e-3f;
    /** Default draw order (lower is drawn first, behind): with the images. */
    public static final int DEFAULT_ORDER = SkyImage.DEFAULT_ORDER;
    private static final Pattern DESCRIPTOR = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** Where the object's front points. */
    public enum Heading {
        /** Fixed by {@link Builder#orientation} (and the YAW and PITCH tracks). */
        FIXED,
        /**
         * Its front points where its direction is moving (from the azimuth and elevation tracks), its top toward the
         * zenith; the orientation's yaw, pitch and roll are applied on top. Without movement it falls back to FIXED.
         */
        PATH
    }

    /** How neighbouring views are blended. */
    public enum ViewBlend {
        /** Follows the quality profile: LOW NEAREST, BALANCED PAIR, HIGH and ULTRA QUAD (default). */
        AUTO,
        /** The closest view only (one texture read). */
        NEAREST,
        /** The two closest views around the yaw circle. */
        PAIR,
        /** The four closest views in yaw and pitch, bilinear. */
        QUAD
    }

    /** Numeric parameters that can be driven by a timeline track, each with the range used to clamp it. */
    public enum Param {
        /** Degrees added to the azimuth. */
        AZIMUTH(-3600, 3600),
        /** Degrees added to the elevation (the sum is clamped to ±90). */
        ELEVATION(-180, 180),
        /** Angular width, degrees (replaces it). */
        SIZE(0, SkyImage.MAX_SIZE),
        /** Yaw of the object, degrees (replaces it). */
        YAW(-3600, 3600),
        /** Pitch of the object, degrees (replaces it). */
        PITCH(-90, 90),
        /** Opacity multiplier, 0..1. */
        OPACITY(0, 1),
        /** Brightness multiplier, 0..16 (replaces it). */
        BRIGHTNESS(0, 16),
        /** Animation frame when playing {@link SkyImage.Play#TRACK}, 0..4095. */
        FRAME(0, SkyImage.MAX_FRAMES - 1);

        /** Smallest and largest value of the parameter; track values are clamped to this range. */
        public final float min, max;

        Param(float min, float max) { this.min = min; this.max = max; }

        /** Clamps a value into this parameter's range; NaN becomes the value closest to 0 in the range. */
        public float clamp(float value) {
            return Float.isNaN(value) ? Math.clamp(0, min, max) : Math.clamp(value, min, max);
        }
    }

    /**
     * An impostor resolved at one moment.
     *
     * @param opacity    effective opacity, 0..1
     * @param brightness brightness multiplier, 0..16
     * @param azimuth    azimuth, degrees (tracks added)
     * @param elevation  elevation, degrees, −90..90 (tracks added)
     * @param width      angular width, degrees
     * @param yaw        yaw of the object, degrees
     * @param pitch      pitch of the object, degrees
     * @param frame      animation frame from the {@link Param#FRAME} track, or NaN when it plays on a clock
     */
    public record State(float opacity, float brightness, double azimuth, double elevation, float width, float yaw,
                        float pitch, float frame) {
        /** True when the impostor should be drawn. */
        public boolean visible() { return opacity > ACTIVE_OPACITY; }
    }

    private final String descriptor;
    private final double azimuth, elevation;
    private final float width, yaw, pitch, roll;
    private final Heading heading;
    private final ViewBlend viewBlend;
    private final boolean celestial, blendFrames, flashing;
    private final SkyImage.Play play;
    private final SkyImage.Clock clock;
    private final float fps;
    private final SkyBlend blend;
    private final int tint;
    private final float brightness, opacity, atmosphere, darkeningStrength, daylight, horizon;
    private final SkyDarkening darkening;
    private final int order;
    private final SkyImage.Layer layer;
    private final Map<Param, String> tracks;
    private final String weightPhase;

    private SkyImpostor(Builder b) {
        descriptor = b.descriptor; azimuth = b.azimuth; elevation = b.elevation; width = b.width; yaw = b.yaw;
        pitch = b.pitch; roll = b.roll; heading = b.heading; viewBlend = b.viewBlend; celestial = b.celestial;
        blendFrames = b.blendFrames; flashing = b.flashing; play = b.play; clock = b.clock; fps = b.fps; blend = b.blend;
        tint = b.tint; brightness = b.brightness; opacity = b.opacity; atmosphere = b.atmosphere;
        darkeningStrength = b.darkeningStrength; daylight = b.daylight; horizon = b.horizon; darkening = b.darkening;
        order = b.order; layer = b.layer;
        tracks = Collections.unmodifiableMap(new EnumMap<>(b.tracks));
        weightPhase = b.weightPhase;
    }

    /**
     * Impostor described by {@code assets/<namespace>/calamidade/impostors/<path>.json} ({@code "namespace:path"}, like
     * {@code "meumod:nave"}).
     */
    public static Builder of(String descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (!DESCRIPTOR.matcher(descriptor).matches() || descriptor.endsWith(".json"))
            throw new IllegalArgumentException("Expected an impostor id like \"namespace:name\" (no .json): " + descriptor);
        return new Builder(descriptor);
    }

    /** Descriptor id, {@code "namespace:path"}. */
    public String descriptor() { return descriptor; }
    /** Resource path of the descriptor inside the namespace: {@code calamidade/impostors/<path>.json}. */
    public String descriptorPath() { return "calamidade/impostors/" + descriptor.substring(descriptor.indexOf(':') + 1) + ".json"; }
    /** Namespace of the descriptor. */
    public String descriptorNamespace() { return descriptor.substring(0, descriptor.indexOf(':')); }
    /** Azimuth, degrees (0 = south, 90 = west). */
    public double azimuth() { return azimuth; }
    /** Elevation, degrees, −90..90. */
    public double elevation() { return elevation; }
    /** Angular width, degrees (the height follows the frame's proportion). */
    public float width() { return width; }
    /** Yaw of the object, degrees (turn around its up axis; positive turns its front toward west when it faces south). */
    public float yaw() { return yaw; }
    /** Pitch of the object, degrees (positive raises its front). */
    public float pitch() { return pitch; }
    /** Roll of the object, degrees (around its front axis). */
    public float roll() { return roll; }
    /** Where the object's front points. */
    public Heading heading() { return heading; }
    /** How neighbouring views are blended. */
    public ViewBlend viewBlend() { return viewBlend; }
    /** True when the impostor turns with the time of day like the vanilla stars. */
    public boolean celestial() { return celestial; }
    /** How the animation plays (null: as the descriptor says). */
    public SkyImage.Play play() { return play; }
    /** Clock of the animation. */
    public SkyImage.Clock clock() { return clock; }
    /** Animation speed, frames per second (NaN: as the descriptor says). */
    public float fps() { return fps; }
    /** Whether neighbouring animation frames are blended. */
    public boolean blendFrames() { return blendFrames; }
    /** True when the addon marked the animation as flashing (it freezes on frame 0 when flashes are hidden). */
    public boolean flashing() { return flashing; }
    /** Composition mode. */
    public SkyBlend blend() { return blend; }
    /** Tint multiplied with the texture, ARGB. */
    public int tint() { return tint; }
    /** Brightness multiplier, 0..16. */
    public float brightness() { return brightness; }
    /** Opacity, 0..1. */
    public float opacity() { return opacity; }
    /** How much the Atmosphere sky veils the impostor, 0..1. */
    public float atmosphere() { return atmosphere; }
    /** Reaction to the darkening of sky layers. */
    public SkyDarkening darkening() { return darkening; }
    /** Strength of the darkening reaction, 0..1. */
    public float darkeningStrength() { return darkeningStrength; }
    /** How much daylight hides the impostor, 0..1. */
    public float daylight() { return daylight; }
    /** Height of the fade at the horizon, degrees (0: none). */
    public float horizon() { return horizon; }
    /** Draw order among sky objects: lower is drawn first, behind. */
    public int order() { return order; }
    /** Composition layer. */
    public SkyImage.Layer layer() { return layer; }
    /** Track bound to a parameter, or null when the parameter is constant. */
    public String track(Param param) { return tracks.get(Objects.requireNonNull(param)); }
    /** Every track binding, in {@code Param} order. */
    public Map<Param, String> tracks() { return tracks; }
    /** Phase whose weight multiplies the opacity, or null. */
    public String weightPhase() { return weightPhase; }
    /** True when a parameter depends on the timeline (tracks or a weight phase). */
    public boolean animated() { return !tracks.isEmpty() || weightPhase != null; }

    /**
     * Checks that every track and the phase this impostor reads exist in {@code timeline}, which may be null only when
     * nothing is {@link #animated() animated}. Called by {@link Effects#impostors}.
     */
    public void validate(Timeline timeline) {
        if (!animated()) return;
        if (timeline == null)
            throw new IllegalArgumentException("SkyImpostor reads tracks or a phase: register the effect's timeline first");
        tracks.forEach((param, track) -> {
            if (!timeline.hasTrack(track)) throw new IllegalArgumentException("SkyImpostor " + param + " reads unknown track: " + track);
        });
        if (weightPhase != null && !timeline.hasPhase(weightPhase))
            throw new IllegalArgumentException("SkyImpostor weight reads unknown phase: " + weightPhase);
    }

    /**
     * Resolves this impostor at one moment: {@code timeline} is the instance's sampler at its local age (any sampler
     * works when nothing is animated); {@code envelope} is the instance's weight (0..1).
     */
    public State sample(Timeline.Sampler timeline, float envelope) {
        Objects.requireNonNull(timeline, "timeline");
        float weight = Float.isNaN(envelope) ? 0 : Math.clamp(envelope, 0f, 1f);
        if (weightPhase != null) weight *= timeline.weight(weightPhase);
        float op = opacity * value(timeline, Param.OPACITY, 1);
        double az = azimuth + value(timeline, Param.AZIMUTH, 0);
        double el = Math.clamp(elevation + value(timeline, Param.ELEVATION, 0), -90, 90);
        String s = tracks.get(Param.SIZE), y = tracks.get(Param.YAW), p = tracks.get(Param.PITCH), b = tracks.get(Param.BRIGHTNESS),
            f = tracks.get(Param.FRAME);
        return new State(Math.clamp(op * weight, 0f, 1f), b == null ? brightness : Param.BRIGHTNESS.clamp(timeline.value(b)), az, el,
            s == null ? width : Param.SIZE.clamp(timeline.value(s)), y == null ? yaw : Param.YAW.clamp(timeline.value(y)),
            p == null ? pitch : Param.PITCH.clamp(timeline.value(p)), f == null ? Float.NaN : Param.FRAME.clamp(timeline.value(f)));
    }

    private float value(Timeline.Sampler timeline, Param param, float otherwise) {
        String track = tracks.get(param);
        return track == null ? otherwise : param.clamp(timeline.value(track));
    }

    /** Short description for logs: descriptor and size. */
    @Override public String toString() { return "SkyImpostor[" + descriptor + " " + width + "°]"; }

    /** Builder of a {@link SkyImpostor}. Every setter validates its arguments right away. */
    public static final class Builder {
        private final String descriptor;
        private double azimuth, elevation = 30;
        private float width = 10, yaw, pitch, roll;
        private Heading heading = Heading.FIXED;
        private ViewBlend viewBlend = ViewBlend.AUTO;
        private boolean celestial, blendFrames, flashing;
        private SkyImage.Play play;
        private SkyImage.Clock clock = SkyImage.Clock.INSTANCE;
        private float fps = Float.NaN;
        private SkyBlend blend = SkyBlend.NORMAL;
        private int tint = 0xFFFFFFFF;
        private float brightness = 1, opacity = 1, atmosphere = 1, darkeningStrength = 1, daylight = 0, horizon = 0;
        private SkyDarkening darkening = SkyDarkening.IGNORE;
        private int order = DEFAULT_ORDER;
        private SkyImage.Layer layer = SkyImage.Layer.BEHIND;
        private final Map<Param, String> tracks = new EnumMap<>(Param.class);
        private String weightPhase;

        private Builder(String descriptor) { this.descriptor = descriptor; }

        /** Direction: azimuth (0 = south, 90 = west) and elevation (−90..90), degrees (default 0, 30). */
        public Builder direction(double azimuth, double elevation) {
            if (!Double.isFinite(azimuth) || !Double.isFinite(elevation) || elevation < -90 || elevation > 90)
                throw new IllegalArgumentException("Expected a finite azimuth and an elevation in [-90, 90] degrees");
            this.azimuth = azimuth; this.elevation = elevation;
            return this;
        }

        /** Angular width, (0, {@value SkyImage#MAX_SIZE}] degrees (default 10). */
        public Builder size(float width) {
            if (!Float.isFinite(width) || width <= 0 || width > SkyImage.MAX_SIZE)
                throw new IllegalArgumentException("Expected width in (0, " + SkyImage.MAX_SIZE + "] degrees");
            this.width = width;
            return this;
        }

        /** Orientation of the object: yaw (±3600), pitch (−90..90) and roll (±3600), degrees (default 0, 0, 0). */
        public Builder orientation(float yaw, float pitch, float roll) {
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll) || Math.abs(yaw) > 3600
                || Math.abs(pitch) > 90 || Math.abs(roll) > 3600)
                throw new IllegalArgumentException("Expected yaw and roll within ±3600 and pitch within ±90 degrees");
            this.yaw = yaw; this.pitch = pitch; this.roll = roll;
            return this;
        }

        /** Where the front points (default {@link Heading#FIXED}). */
        public Builder heading(Heading value) { heading = Objects.requireNonNull(value, "heading"); return this; }

        /** How neighbouring views are blended (default {@link ViewBlend#AUTO}). */
        public Builder viewBlend(ViewBlend value) { viewBlend = Objects.requireNonNull(value, "view blend"); return this; }

        /** Whether the impostor turns with the time of day like the vanilla stars (default false). */
        public Builder celestial(boolean value) { celestial = value; return this; }

        /** Overrides how the animation plays (default: the descriptor's {@code play}). */
        public Builder play(SkyImage.Play value) { play = Objects.requireNonNull(value, "play"); return this; }

        /** Clock of the animation (default {@link SkyImage.Clock#INSTANCE}). */
        public Builder clock(SkyImage.Clock value) { clock = Objects.requireNonNull(value, "clock"); return this; }

        /** Overrides the animation speed, (0, {@value SkyImage#MAX_FPS}] frames per second (default: the descriptor's). */
        public Builder fps(float value) {
            if (!Float.isFinite(value) || value <= 0 || value > SkyImage.MAX_FPS)
                throw new IllegalArgumentException("Expected fps in (0, " + SkyImage.MAX_FPS + "]");
            fps = value;
            return this;
        }

        /** Blends neighbouring animation frames (default false; view blending then uses PAIR at most). */
        public Builder blendFrames(boolean value) { blendFrames = value; return this; }

        /** Marks the animation as flashing: frozen on frame 0 while flashes are hidden (default false). */
        public Builder flashing(boolean value) { flashing = value; return this; }

        /** Composition with the sky behind ({@link SkyBlend}; default {@link SkyBlend#NORMAL}). */
        public Builder blend(SkyBlend value) { blend = Objects.requireNonNull(value, "blend"); return this; }

        /** Tint multiplied with the texture, ARGB (default white; alpha multiplies the opacity). */
        public Builder tint(int argb) { tint = argb; return this; }

        /** Brightness multiplier, 0..16 (default 1). */
        public Builder brightness(float value) {
            if (!Float.isFinite(value) || value < 0 || value > 16) throw new IllegalArgumentException("Expected brightness in [0, 16]");
            brightness = value;
            return this;
        }

        /** Opacity, 0..1 (default 1). */
        public Builder opacity(float value) { opacity = SkyImage.unit("opacity", value); return this; }

        /** How much the Atmosphere sky veils the impostor, 0..1 (default 1). */
        public Builder atmosphere(float value) { atmosphere = SkyImage.unit("atmosphere", value); return this; }

        /** Reaction to sky layers that darken the world and its strength 0..1 (default {@link SkyDarkening#IGNORE}, 1). */
        public Builder darkening(SkyDarkening mode, float strength) {
            darkening = Objects.requireNonNull(mode, "darkening");
            darkeningStrength = SkyImage.unit("darkening strength", strength);
            return this;
        }

        /** How much daylight and rain hide the impostor, 0..1 (default 0). */
        public Builder daylight(float value) { daylight = SkyImage.unit("daylight", value); return this; }

        /** Fade near the horizon, 0..30 degrees (default 0). */
        public Builder horizon(float degrees) {
            if (!Float.isFinite(degrees) || degrees < 0 || degrees > 30) throw new IllegalArgumentException("Expected horizon fade in [0, 30] degrees");
            horizon = degrees;
            return this;
        }

        /** Draw order among sky objects, −1000..1000: lower is drawn first, behind (default {@value #DEFAULT_ORDER}). */
        public Builder order(int value) {
            if (value < -1000 || value > 1000) throw new IllegalArgumentException("Expected order in [-1000, 1000]");
            order = value;
            return this;
        }

        /** Composition layer (default {@link SkyImage.Layer#BEHIND}). */
        public Builder layer(SkyImage.Layer value) { layer = Objects.requireNonNull(value, "layer"); return this; }

        /** Takes {@code param} from a track of the effect's timeline instead of the constant (clamped to its range). */
        public Builder track(Param param, String track) {
            Objects.requireNonNull(param, "param");
            tracks.put(param, SkyImage.name("track", track));
            return this;
        }

        /** Multiplies the opacity by the weight of a timeline phase (0 outside it). */
        public Builder weightPhase(String phase) {
            weightPhase = SkyImage.name("phase", phase);
            return this;
        }

        /** Immutable impostor. Throws when {@link SkyImage.Play#TRACK} has no {@link Param#FRAME} track. */
        public SkyImpostor build() {
            if (play == SkyImage.Play.TRACK && !tracks.containsKey(Param.FRAME))
                throw new IllegalArgumentException("Play.TRACK needs a FRAME track");
            return new SkyImpostor(this);
        }
    }
}
