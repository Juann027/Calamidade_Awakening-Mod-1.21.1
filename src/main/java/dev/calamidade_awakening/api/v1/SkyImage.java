package dev.calamidade_awakening.api.v1;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable picture in the sky of one effect or one {@link SkyProfile.State} (0.14.0): any PNG an addon ships — an
 * illustrated planet, a dragon, a ship, a creature, a giant structure, a symbol, an illustrated portal, an irregular
 * cosmic object — shown as a billboard tangent to the sky sphere, either a {@link #of still image} or a
 * {@link #sheet spritesheet} played as a flipbook. Pure Java, no Minecraft types. Nothing is drawn unless an addon
 * attaches an image ({@link Effects#images}, {@link SkyProfile.StateBuilder#images}); players can turn custom skies off
 * with the client option {@code customSkies}.
 *
 * <p><b>How it is drawn.</b> At infinity, like the {@link Backdrop} and the {@link StarField stars}: it has a direction,
 * not a position (no parallax, terrain and clouds in front), right after the vanilla sky and the backgrounds
 * ({@code RenderLevelStageEvent.Stage.AFTER_SKY}), interleaved with stars and nebulae by {@link Builder#order order},
 * behind the engine's {@link Sky} layers unless {@link Layer#FRONT}. Images sharing a texture share one draw call; the
 * texture is loaded on first use off the render thread (the image fades in when it is ready), one GL texture per PNG
 * and filter, released when unused, on resource reload and when leaving the world. Quality profiles cap the images
 * drawn per frame and the texture memory, AUTO included.
 *
 * <p><b>Composition.</b> Real transparency by default ({@link SkyBlend#NORMAL}: the PNG's alpha, exact colors);
 * {@link SkyBlend#LUMINOUS} only adds light; {@link SkyBlend#COVER} hides what is behind inside the silhouette
 * (alpha ≥ 0.5). The Atmosphere veils it ({@link Builder#atmosphere}), daylight can hide it ({@link Builder#daylight}),
 * an eclipse can dim or reveal it ({@link Builder#darkening}), exactly like stars and nebulae; the vanilla sun and moon
 * removal and the gravitational lens account for it.
 *
 * <p><b>Flipbooks.</b> {@link #sheet} cuts the PNG into a grid of equal frames (row by row, left to right); the frame
 * comes from a clock ({@link Clock#INSTANCE}: the instance's age, the same on every client; {@link Clock#WORLD}: the game
 * time) at {@link Builder#fps fps} with {@link Play#LOOP}, {@link Play#ONCE} or {@link Play#PING_PONG}, or from a
 * timeline track ({@link Play#TRACK} with {@link Param#FRAME}). No margin is needed between frames: with
 * {@link Builder#filter SMOOTH} the mipmaps are made per frame and sampling stays inside the frame at any size; frames
 * whose sides are powers of two (32, 64, 128) get the most mipmap levels. Accessibility: a sheet that flashes
 * must be marked {@link Builder#flashing flashing(true)} by the addon — it then freezes on a safe frame while the player
 * hides lightning flashes or turns screen effects to 0. The engine also checks every sheet's frame brightness once when
 * loading it and treats a sheet that alternates strongly more than 3 times per second as flashing; this is a simple
 * safety net, not a reliable detector, so marking stays the addon's job.
 *
 * <pre>{@code
 * static final SkyImage DRAGAO = SkyImage.of("meumod:textures/sky/dragao.png")
 *     .direction(120, 35).size(18).blend(SkyBlend.COVER).build();
 * static final SkyImage ASAS = SkyImage.sheet("meumod:textures/sky/asas.png", 4, 2).frames(8).fps(12)
 *     .direction(200, 40).size(25).build();
 * Effects.images(ID, DRAGAO, ASAS);   // after register and timeline
 * }</pre>
 *
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class SkyImage {
    /** Most images per effect or profile state. */
    public static final int MAX_PER_EFFECT = 8;
    /** Opacity below which an image is not drawn. */
    public static final float ACTIVE_OPACITY = 1e-3f;
    /** Default draw order of images (lower is drawn first, behind): in front of stars (200) and nebulae (100). */
    public static final int DEFAULT_ORDER = 300;
    /** Largest angular width or height, degrees. */
    public static final float MAX_SIZE = 160f;
    /** Most columns or rows of a sheet's grid. */
    public static final int MAX_GRID = 64;
    /** Most frames of a sheet. */
    public static final int MAX_FRAMES = 4096;
    /** Fastest flipbook, frames per second. */
    public static final float MAX_FPS = 120f;
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern TEXTURE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+\\.png");

    /** Which way is up for the picture on the sky. */
    public enum Up {
        /** Upright: the picture's top points toward the zenith (toward north for a picture right at the zenith). */
        HORIZON,
        /** The picture's +X (its right side) points where its direction is moving (from the azimuth and elevation tracks). */
        PATH
    }

    /** Where the image is composed. */
    public enum Layer {
        /** Behind the engine's procedural {@link Sky} layers, with the stars and nebulae (default). */
        BEHIND,
        /**
         * In front of the procedural layers and of every {@code BEHIND} object (a ship passing before a portal): composed
         * at the end of the engine's sky pass, only on sky pixels, without the lens. A visual composition layer of the
         * sky, not physical depth. Without a procedural layer in the frame it is drawn like {@code BEHIND}.
         */
        FRONT
    }

    /** Texture filtering. */
    public enum Filter {
        /** Linear filtering with mipmaps: no shimmering when the image is small on screen (default). */
        SMOOTH,
        /** No filtering, no mipmaps: crisp pixel art. */
        PIXEL
    }

    /** How a flipbook plays its frames. */
    public enum Play {
        /** From the start frame to the last, then again (default). */
        LOOP,
        /** From the start frame to the last, then stays on the last. */
        ONCE,
        /** Forth and back without repeating the end frames (0 1 2 3 2 1 0 1 …). */
        PING_PONG,
        /** The frame comes from the {@link Param#FRAME} track of the timeline (no clock). */
        TRACK
    }

    /** Clock of a flipbook. */
    public enum Clock {
        /**
         * The instance's age (effects and global events): every client shows the same frame at the same moment, no
         * packet. In a {@link SkyProfile} there is no instance: the world clock is used.
         */
        INSTANCE,
        /** The game time of the level (the same on every client of a server). */
        WORLD
    }

    /** Numeric parameters that can be driven by a timeline track, each with the range used to clamp it. */
    public enum Param {
        /** Degrees added to the azimuth. */
        AZIMUTH(-3600, 3600),
        /** Degrees added to the elevation (the sum is clamped to ±90). */
        ELEVATION(-180, 180),
        /** Angular width, degrees (replaces it; the height keeps the proportion). */
        SIZE(0, MAX_SIZE),
        /** Turn in the plane of the sky around the pivot, degrees (replaces it). */
        ROLL(-3600, 3600),
        /** Opacity multiplier, 0..1. */
        OPACITY(0, 1),
        /** Brightness multiplier, 0..16 (replaces it). */
        BRIGHTNESS(0, 16),
        /** Frame of a {@link Play#TRACK} flipbook, 0..4095 (fractions blend with {@link Builder#blendFrames}). */
        FRAME(0, MAX_FRAMES - 1);

        /** Smallest and largest value of the parameter; track values are clamped to this range. */
        public final float min, max;

        Param(float min, float max) { this.min = min; this.max = max; }

        /** Clamps a value into this parameter's range; NaN becomes the value closest to 0 in the range. */
        public float clamp(float value) {
            return Float.isNaN(value) ? Math.clamp(0, min, max) : Math.clamp(value, min, max);
        }
    }

    /**
     * An image resolved at one moment.
     *
     * @param opacity    effective opacity, 0..1 (builder opacity times weight, phase and track)
     * @param brightness brightness multiplier, 0..16
     * @param azimuth    azimuth of the pivot, degrees (tracks added)
     * @param elevation  elevation of the pivot, degrees, −90..90 (tracks added)
     * @param width      angular width, degrees
     * @param roll       turn in the plane of the sky, degrees
     * @param frame      frame from the {@link Param#FRAME} track (0..4095), or NaN when the image plays on a clock
     */
    public record State(float opacity, float brightness, double azimuth, double elevation, float width, float roll,
                        float frame) {
        /** True when the image should be drawn. */
        public boolean visible() { return opacity > ACTIVE_OPACITY; }
    }

    private final String texture;
    private final int columns, rows, frames, startFrame, safeFrame;
    private final float fps;
    private final Play play;
    private final Clock clock;
    private final boolean blendFrames, flashing, celestial;
    private final double azimuth, elevation;
    private final float width, height, roll, pivotX, pivotY;
    private final Up up;
    private final SkyBlend blend;
    private final int tint;
    private final float brightness, opacity, atmosphere, darkeningStrength, daylight, horizon;
    private final SkyDarkening darkening;
    private final int order;
    private final Layer layer;
    private final Filter filter;
    private final Map<Param, String> tracks;
    private final String weightPhase;

    private SkyImage(Builder b) {
        texture = b.texture; columns = b.columns; rows = b.rows; frames = b.frames; startFrame = b.startFrame;
        safeFrame = b.safeFrame < 0 ? b.startFrame : b.safeFrame; fps = b.fps; play = b.play; clock = b.clock;
        blendFrames = b.blendFrames; flashing = b.flashing; celestial = b.celestial;
        azimuth = b.azimuth; elevation = b.elevation; width = b.width; height = b.height; roll = b.roll;
        pivotX = b.pivotX; pivotY = b.pivotY; up = b.up; blend = b.blend; tint = b.tint; brightness = b.brightness;
        opacity = b.opacity; atmosphere = b.atmosphere; darkeningStrength = b.darkeningStrength; daylight = b.daylight;
        horizon = b.horizon; darkening = b.darkening; order = b.order; layer = b.layer; filter = b.filter;
        tracks = Collections.unmodifiableMap(new EnumMap<>(b.tracks));
        weightPhase = b.weightPhase;
    }

    /**
     * Still image from a PNG in a resource pack ({@code "namespace:path.png"}, like
     * {@code "meumod:textures/sky/dragao.png"}), straight (not premultiplied) alpha, up to 4096 pixels per side.
     */
    public static Builder of(String texture) {
        return new Builder(texture(texture), 1, 1);
    }

    /**
     * Spritesheet from a PNG cut into {@code columns} × {@code rows} equal frames (1..{@value #MAX_GRID} each), played
     * row by row, left to right. All cells are frames unless {@link Builder#frames} says fewer.
     */
    public static Builder sheet(String texture, int columns, int rows) {
        if (columns < 1 || rows < 1 || columns > MAX_GRID || rows > MAX_GRID)
            throw new IllegalArgumentException("Expected a grid of 1.." + MAX_GRID + " columns and rows");
        if (columns * rows > MAX_FRAMES) throw new IllegalArgumentException("At most " + MAX_FRAMES + " frames per sheet");
        return new Builder(texture(texture), columns, rows);
    }

    /** PNG resource location, {@code "namespace:path.png"}. */
    public String texture() { return texture; }
    /** Columns of the sheet's grid (1 for a still image). */
    public int columns() { return columns; }
    /** Rows of the sheet's grid (1 for a still image). */
    public int rows() { return rows; }
    /** Frames played (1 for a still image; at most columns × rows). */
    public int frames() { return frames; }
    /** True when the image is a flipbook (more than one frame). */
    public boolean animatedFrames() { return frames > 1; }
    /** Frames per second of the flipbook. */
    public float fps() { return fps; }
    /** How the flipbook plays. */
    public Play play() { return play; }
    /** Clock of the flipbook. */
    public Clock clock() { return clock; }
    /** First frame at time 0 (0-based). */
    public int startFrame() { return startFrame; }
    /** Whether neighbouring frames are blended (two texture reads; off in the LOW profile). */
    public boolean blendFrames() { return blendFrames; }
    /** True when the addon marked the sheet as flashing: it freezes on {@link #safeFrame} when flashes are hidden. */
    public boolean flashing() { return flashing; }
    /** Frame shown while a flashing sheet is frozen (0-based; defaults to the start frame). */
    public int safeFrame() { return safeFrame; }
    /** Azimuth of the pivot, degrees (0 = south, 90 = west). */
    public double azimuth() { return azimuth; }
    /** Elevation of the pivot, degrees, −90..90. */
    public double elevation() { return elevation; }
    /** Angular width, degrees. */
    public float width() { return width; }
    /** Angular height, degrees, or NaN when it follows the proportion of the frame. */
    public float height() { return height; }
    /** Turn in the plane of the sky around the pivot, degrees (positive: counterclockwise as seen from the ground). */
    public float roll() { return roll; }
    /** Pivot along the width, 0..1 (0 left edge). */
    public float pivotX() { return pivotX; }
    /** Pivot along the height, 0..1 (0 top edge). */
    public float pivotY() { return pivotY; }
    /** Which way is up. */
    public Up up() { return up; }
    /** True when the image turns with the time of day like the vanilla stars (direction and upright). */
    public boolean celestial() { return celestial; }
    /** Composition mode. */
    public SkyBlend blend() { return blend; }
    /** Tint multiplied with the texture, ARGB (alpha multiplies the opacity). */
    public int tint() { return tint; }
    /** Brightness multiplier, 0..16. */
    public float brightness() { return brightness; }
    /** Opacity, 0..1. */
    public float opacity() { return opacity; }
    /** How much the Atmosphere sky veils the image, 0..1. */
    public float atmosphere() { return atmosphere; }
    /** Reaction to the darkening of sky layers. */
    public SkyDarkening darkening() { return darkening; }
    /** Strength of the darkening reaction, 0..1. */
    public float darkeningStrength() { return darkeningStrength; }
    /** How much daylight hides the image, 0..1. */
    public float daylight() { return daylight; }
    /** Height of the fade at the horizon, degrees (0: none). */
    public float horizon() { return horizon; }
    /** Draw order among sky objects: lower is drawn first, behind (default {@value #DEFAULT_ORDER}). */
    public int order() { return order; }
    /** Composition layer. */
    public Layer layer() { return layer; }
    /** Texture filtering. */
    public Filter filter() { return filter; }
    /** Track bound to a parameter, or null when the parameter is constant. */
    public String track(Param param) { return tracks.get(Objects.requireNonNull(param)); }
    /** Every track binding, in {@code Param} order. */
    public Map<Param, String> tracks() { return tracks; }
    /** Phase whose weight multiplies the opacity, or null. */
    public String weightPhase() { return weightPhase; }
    /** True when a parameter depends on the timeline (tracks or a weight phase); clock flipbooks are not "animated". */
    public boolean animated() { return !tracks.isEmpty() || weightPhase != null; }

    /**
     * Checks that every track and the phase this image reads exist in {@code timeline}, which may be null only when
     * nothing is {@link #animated() animated}. Called by {@link Effects#images}.
     */
    public void validate(Timeline timeline) {
        if (!animated()) return;
        if (timeline == null)
            throw new IllegalArgumentException("SkyImage reads tracks or a phase: register the effect's timeline first");
        tracks.forEach((param, track) -> {
            if (!timeline.hasTrack(track)) throw new IllegalArgumentException("SkyImage " + param + " reads unknown track: " + track);
        });
        if (weightPhase != null && !timeline.hasPhase(weightPhase))
            throw new IllegalArgumentException("SkyImage weight reads unknown phase: " + weightPhase);
    }

    /**
     * Resolves this image at one moment: {@code timeline} is the instance's sampler at its local age (any sampler works
     * when nothing is animated); {@code envelope} is the instance's weight (0..1), which multiplies the opacity together
     * with the phase and the {@link Param#OPACITY} track.
     */
    public State sample(Timeline.Sampler timeline, float envelope) {
        Objects.requireNonNull(timeline, "timeline");
        float weight = Float.isNaN(envelope) ? 0 : Math.clamp(envelope, 0f, 1f);
        if (weightPhase != null) weight *= timeline.weight(weightPhase);
        float op = opacity * value(timeline, Param.OPACITY, 1);
        double az = azimuth + value(timeline, Param.AZIMUTH, 0);
        double el = Math.clamp(elevation + value(timeline, Param.ELEVATION, 0), -90, 90);
        String s = tracks.get(Param.SIZE), r = tracks.get(Param.ROLL), b = tracks.get(Param.BRIGHTNESS), f = tracks.get(Param.FRAME);
        float w = s == null ? width : Param.SIZE.clamp(timeline.value(s));
        float ro = r == null ? roll : Param.ROLL.clamp(timeline.value(r));
        float br = b == null ? brightness : Param.BRIGHTNESS.clamp(timeline.value(b));
        float fr = f == null ? Float.NaN : Param.FRAME.clamp(timeline.value(f));
        return new State(Math.clamp(op * weight, 0f, 1f), br, az, el, w, ro, fr);
    }

    private float value(Timeline.Sampler timeline, Param param, float otherwise) {
        String track = tracks.get(param);
        return track == null ? otherwise : param.clamp(timeline.value(track));
    }

    /** Short description for logs: texture, grid and size. */
    @Override public String toString() {
        return "SkyImage[" + texture + (frames > 1 ? " " + columns + "x" + rows + "/" + frames : "") + " " + width + "°]";
    }

    /** Builder of a {@link SkyImage}. Every setter validates its arguments right away. */
    public static final class Builder {
        private final String texture;
        private final int columns, rows;
        private int frames, startFrame, safeFrame = -1;
        private float fps = 12;
        private Play play = Play.LOOP;
        private Clock clock = Clock.INSTANCE;
        private boolean blendFrames, flashing, celestial;
        private double azimuth, elevation = 30;
        private float width = 10, height = Float.NaN, roll, pivotX = .5f, pivotY = .5f;
        private Up up = Up.HORIZON;
        private SkyBlend blend = SkyBlend.NORMAL;
        private int tint = 0xFFFFFFFF;
        private float brightness = 1, opacity = 1, atmosphere = 1, darkeningStrength = 1, daylight = 0, horizon = 0;
        private SkyDarkening darkening = SkyDarkening.IGNORE;
        private int order = DEFAULT_ORDER;
        private Layer layer = Layer.BEHIND;
        private Filter filter = Filter.SMOOTH;
        private final Map<Param, String> tracks = new EnumMap<>(Param.class);
        private String weightPhase;

        private Builder(String texture, int columns, int rows) {
            this.texture = texture; this.columns = columns; this.rows = rows; this.frames = columns * rows;
        }

        /** Direction of the pivot: azimuth (0 = south, 90 = west) and elevation (−90..90), degrees (default 0, 30). */
        public Builder direction(double azimuth, double elevation) {
            if (!Double.isFinite(azimuth) || !Double.isFinite(elevation) || elevation < -90 || elevation > 90)
                throw new IllegalArgumentException("Expected a finite azimuth and an elevation in [-90, 90] degrees");
            this.azimuth = azimuth; this.elevation = elevation;
            return this;
        }

        /** Angular width, (0, {@value #MAX_SIZE}] degrees (default 10); the height follows the frame's proportion. */
        public Builder size(float width) {
            this.width = angularSize("width", width);
            height = Float.NaN;
            return this;
        }

        /** Angular width and height, (0, {@value #MAX_SIZE}] degrees each (stretches the picture if they differ from it). */
        public Builder size(float width, float height) {
            this.width = angularSize("width", width);
            this.height = angularSize("height", height);
            return this;
        }

        /** Turn in the plane of the sky around the pivot, degrees, ±3600 (positive: counterclockwise from the ground). */
        public Builder roll(float degrees) {
            if (!Float.isFinite(degrees) || Math.abs(degrees) > 3600) throw new IllegalArgumentException("Expected roll within ±3600 degrees");
            roll = degrees;
            return this;
        }

        /** Point of the picture placed at the direction, 0..1 each (default 0.5, 0.5; 0, 0 is the top-left corner). */
        public Builder pivot(float x, float y) {
            pivotX = unit("pivot x", x);
            pivotY = unit("pivot y", y);
            return this;
        }

        /** Which way is up (default {@link Up#HORIZON}). */
        public Builder up(Up value) { up = Objects.requireNonNull(value, "up"); return this; }

        /** Whether the image turns with the time of day like the vanilla stars (default false: fixed in the sky). */
        public Builder celestial(boolean value) { celestial = value; return this; }

        /** Composition with the sky behind ({@link SkyBlend}; default {@link SkyBlend#NORMAL}). */
        public Builder blend(SkyBlend value) { blend = Objects.requireNonNull(value, "blend"); return this; }

        /** Tint multiplied with the texture, ARGB (default white; alpha multiplies the opacity). */
        public Builder tint(int argb) { tint = argb; return this; }

        /** Brightness multiplier, 0..16 (default 1; above 1 a NORMAL image glows more than it covers). */
        public Builder brightness(float value) {
            if (!Float.isFinite(value) || value < 0 || value > 16) throw new IllegalArgumentException("Expected brightness in [0, 16]");
            brightness = value;
            return this;
        }

        /** Opacity, 0..1 (default 1). */
        public Builder opacity(float value) { opacity = unit("opacity", value); return this; }

        /** How much the Atmosphere sky veils the image, 0..1 (default 1). */
        public Builder atmosphere(float value) { atmosphere = unit("atmosphere", value); return this; }

        /** Reaction to sky layers that darken the world and its strength 0..1 (default {@link SkyDarkening#IGNORE}, 1). */
        public Builder darkening(SkyDarkening mode, float strength) {
            darkening = Objects.requireNonNull(mode, "darkening");
            darkeningStrength = unit("darkening strength", strength);
            return this;
        }

        /** How much daylight and rain hide the image, 0..1 (default 0: visible by day too). */
        public Builder daylight(float value) { daylight = unit("daylight", value); return this; }

        /** Fade near the horizon, 0..30 degrees (default 0: no fade). */
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

        /** Composition layer (default {@link Layer#BEHIND}). */
        public Builder layer(Layer value) { layer = Objects.requireNonNull(value, "layer"); return this; }

        /** Texture filtering (default {@link Filter#SMOOTH}). */
        public Builder filter(Filter value) { filter = Objects.requireNonNull(value, "filter"); return this; }

        /** Frames played, 1..columns × rows (default: every cell of the grid). */
        public Builder frames(int count) {
            if (count < 1 || count > columns * rows) throw new IllegalArgumentException("Expected 1.." + columns * rows + " frames");
            frames = count;
            if (startFrame >= count) throw new IllegalArgumentException("The start frame must be below the frame count");
            return this;
        }

        /** Flipbook speed, (0, {@value #MAX_FPS}] frames per second (default 12). */
        public Builder fps(float value) {
            if (!Float.isFinite(value) || value <= 0 || value > MAX_FPS) throw new IllegalArgumentException("Expected fps in (0, " + MAX_FPS + "]");
            fps = value;
            return this;
        }

        /** How the flipbook plays (default {@link Play#LOOP}; {@link Play#TRACK} needs a {@link Param#FRAME} track). */
        public Builder play(Play value) { play = Objects.requireNonNull(value, "play"); return this; }

        /** Clock of the flipbook (default {@link Clock#INSTANCE}). */
        public Builder clock(Clock value) { clock = Objects.requireNonNull(value, "clock"); return this; }

        /** First frame at time 0, 0-based, below the frame count (default 0). */
        public Builder startFrame(int frame) {
            if (frame < 0 || frame >= frames) throw new IllegalArgumentException("Expected a start frame in [0, " + frames + ")");
            startFrame = frame;
            return this;
        }

        /** Blends each frame into the next (two texture reads; off in the LOW profile; default false). */
        public Builder blendFrames(boolean value) { blendFrames = value; return this; }

        /**
         * Marks the sheet as flashing (default false): it freezes on {@link #safeFrame} while the player hides lightning
         * flashes or turns screen effects to 0. Identifying flashing animations is the addon's responsibility.
         */
        public Builder flashing(boolean value) { flashing = value; return this; }

        /** Frame shown while a flashing sheet is frozen, 0-based, below the frame count (default: the start frame). */
        public Builder safeFrame(int frame) {
            if (frame < 0 || frame >= frames) throw new IllegalArgumentException("Expected a safe frame in [0, " + frames + ")");
            safeFrame = frame;
            return this;
        }

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

        /** Immutable image. Throws when {@link Play#TRACK} has no {@link Param#FRAME} track or the frames are invalid. */
        public SkyImage build() {
            if (play == Play.TRACK && !tracks.containsKey(Param.FRAME))
                throw new IllegalArgumentException("Play.TRACK needs a FRAME track");
            if (startFrame >= frames || (safeFrame >= frames))
                throw new IllegalArgumentException("The start and safe frames must be below the frame count");
            return new SkyImage(this);
        }
    }

    static String texture(String value) {
        Objects.requireNonNull(value, "texture");
        if (!TEXTURE.matcher(value).matches())
            throw new IllegalArgumentException("Expected a PNG resource location like \"namespace:textures/sky/x.png\": " + value);
        return value;
    }

    private static float angularSize(String what, float value) {
        if (!Float.isFinite(value) || value <= 0 || value > MAX_SIZE)
            throw new IllegalArgumentException("Expected " + what + " in (0, " + MAX_SIZE + "] degrees");
        return value;
    }

    static float unit(String what, float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Expected " + what + " in [0, 1]");
        return value;
    }

    static String name(String what, String value) {
        Objects.requireNonNull(value, what);
        if (!NAME.matcher(value).matches()) throw new IllegalArgumentException("Invalid " + what + " name: " + value);
        return value;
    }
}
