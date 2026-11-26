package dev.calamidade_awakening.api.v1;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable visual light of one effect (0.9.0): it brightens the blocks, entities and terrain on screen around a
 * {@link Point}, along a segment (optionally the segment of one of the effect's {@link Beam beams}) or over a disc or
 * a ring on the ground (shock waves). Pure Java, no Minecraft types, so the registry, the client and tests share the
 * same math.
 *
 * <p><b>Visual only.</b> The light is drawn by a screen pass on what the camera sees; it does not change the game's
 * light level, mob spawning, plant growth or anything the server knows. Blocks behind the camera or hidden from it
 * are not lit, and the light does not cast shadows.
 *
 * <p>The engine draws every visible light of the frame in one screen pass ("lights"): it rebuilds the position of
 * each pixel from the depth buffer, estimates the surface normal from neighbouring pixels (only faces turned toward
 * the source are lit), adds the light with distance attenuation, never lights the sky and fades with the game's own
 * fog. The pass runs before the effect passes and the atmosphere, so the atmosphere covers it in the distance. It is
 * charged once per frame to the HERO pass budget when a drawn HERO effect has lights, otherwise to the common addon
 * budget, and draws at most 8/24/64 lights per frame (LOW/BALANCED/HIGH), taken from the most important effects.
 *
 * <ul>
 * <li>{@link #point}: light at a point; {@code radius} is its reach in blocks (0 light beyond it) and
 * {@code falloff} the exponent of {@code (1 - distance / radius)}.</li>
 * <li>{@link #segment} and {@link #along(Beam)}: the same from the nearest point of a segment; {@code along} uses
 * the ends, reveal, weight and halo color of a beam of the same effect, so the light grows and fades with it.</li>
 * <li>{@link #disc} and {@link #ring}: light on the ground around a center: a disc of {@code radius} blocks with a
 * soft edge {@code width} blocks wide, or a band {@code width} blocks wide at {@code radius}; it reaches
 * {@code height} blocks above and below the center and lights faces turned up more than walls. Discs and rings stay
 * horizontal when the instance is oriented (0.10.0): only their center turns with it.</li>
 * </ul>
 *
 * <p>Every numeric parameter can come from a track of the effect's {@link Timeline} ({@link Builder#track}), clamped
 * to the parameter range: a ring whose {@link Param#RADIUS} follows the shock wave, a light that fades with
 * {@link Param#INTENSITY}. The light weight is the instance's opacity envelope, times an optional phase and an
 * optional {@link Param#WEIGHT} track. Optional flicker (fire, unstable energy) varies the intensity with smooth
 * noise; it follows the player's screen-effects intensity and is reduced to a quarter when the player hides
 * lightning flashes.
 *
 * <pre>{@code
 * static final Light FOGUEIRA = Light.point(Point.center(0, .6, 0))
 *     .color(0xffff9a40).intensity(2.2f).radius(9).flicker(.35f, 6)
 *     .build();
 * Effects.lights(ID, FOGUEIRA);   // mod constructor, after register (and timeline/beams if used)
 * }</pre>
 *
 * Invalid input throws {@link IllegalArgumentException}, like the rest of the API.
 */
public final class Light {
    /** Most lights per effect ({@link Effects#lights}). */
    public static final int MAX_PER_EFFECT = 8;
    /** Largest reach (point and segment) or disc/ring radius, blocks. */
    public static final float MAX_RADIUS = 128f;
    /** Largest intensity multiplier. */
    public static final float MAX_INTENSITY = 8f;
    /** Largest track offset, blocks per axis. */
    public static final float MAX_TRACK_OFFSET = 256f;
    /** Weight below which a light is not drawn. */
    public static final float ACTIVE_WEIGHT = 1e-3f;

    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");

    /** Shape of the light source. */
    public enum Shape {
        /** A point: lights in every direction within {@code radius}. */
        POINT,
        /** A segment (or a beam): lights from its nearest point within {@code radius}. */
        SEGMENT,
        /** A disc on the ground: {@code radius} wide with a soft edge {@code width} blocks wide. */
        DISC,
        /** A ring on the ground: a band {@code width} blocks wide at {@code radius} from the center. */
        RING,
        /**
         * (0.15.0) A spotlight: a cone from {@link Light#from()} along its direction, {@code radius} blocks long, with a
         * full aperture of {@link Param#ANGLE} degrees and a soft edge ({@link Param#SOFTNESS}). With a
         * {@link Cookie} the aperture becomes the square that holds the image (a slide projector).
         */
        SPOT,
        /**
         * (0.15.0) A planar projector: a box {@link Param#SIZE_X} × {@link Param#SIZE_Y} blocks wide that starts at
         * {@link Light#from()} and runs {@code radius} blocks along its direction. Rays are parallel, so a
         * {@link Cookie} keeps its size whatever the distance (runes on the ground, a grid on a wall, caustics).
         */
        PROJECTOR
    }

    /** (0.15.0) Distance attenuation curve; the default {@link #POWER} is the 0.9 curve. */
    public enum Attenuation {
        /** {@code (1 - d / radius) ^ falloff}: the 0.9 curve (default). */
        POWER,
        /** {@code (1 - (d / radius)²)²}: bright and wide, smooth to 0 at the edge (no falloff exponent). */
        SMOOTH,
        /** Windowed inverse square, {@code 1 / (1 + (k·d/radius)²) × (1 - (d/radius)²)²}, {@code k = 2 × falloff}: a hot core. */
        INVERSE_SQUARE,
        /** Flat up to 80% of the reach, then a smooth fade to 0: a uniform pool of light. */
        CONSTANT
    }

    /**
     * (0.15.0) How a light composes with the others in the single lights pass. Every mode is order independent, so
     * lights never depend on which effect was drawn first.
     */
    public enum Blend {
        /** Adds light (the 0.9 behavior, default). */
        ADD,
        /**
         * Adds light that saturates: the sum of every SOFT light of a pixel goes through {@code 1 - e^(-x)} per channel,
         * so many overlapping lights never blow the scene out (crowds of torches, stress scenes).
         */
        SOFT,
        /**
         * Removes light: multiplies the scene by {@code 1 - color × intensity × attenuation} (at most 95% darker).
         * Shadows and gobos: a cookie with SUBTRACT projects darkness (a moving shadow, an eclipse band).
         */
        SUBTRACT
    }

    /** Numeric parameters that can be driven by a timeline track, each with the range used to clamp it. */
    public enum Param {
        /** Brightness multiplier. */
        INTENSITY(0, MAX_INTENSITY),
        /** Reach (point, segment) or disc/ring radius, blocks. */
        RADIUS(0, MAX_RADIUS),
        /** Exponent of the distance attenuation (point, segment); 1 linear, 2 soft (default), 4 tight. */
        FALLOFF(.25f, 4),
        /** Ring band width or disc edge softness, blocks. */
        WIDTH(0, 64),
        /** Vertical reach of a disc or ring above and below its center, blocks. */
        HEIGHT(.25f, 32),
        /** Flicker amount, 0..1 (0 steady, 1 may go fully dark). */
        FLICKER(0, 1),
        /** Flicker speed, changes per second. */
        FLICKER_SPEED(0, 20),
        /**
         * Offset added to the position (all ends and the center), x, blocks. Since 0.10.0 the X, Y and Z offsets are in
         * the instance frame (x left, y up, z forward), the world axes for an instance without orientation.
         */
        X(-MAX_TRACK_OFFSET, MAX_TRACK_OFFSET),
        /** Offset added to the position, y (up), blocks. */
        Y(-MAX_TRACK_OFFSET, MAX_TRACK_OFFSET),
        /** Offset added to the position, z (forward in the instance frame), blocks. */
        Z(-MAX_TRACK_OFFSET, MAX_TRACK_OFFSET),
        /** Extra multiplier of the light weight, 0..1 (the envelope and the phase still apply). */
        WEIGHT(0, 1),
        /** (0.15.0) Degrees added to the yaw of a spot or projector (instance frame; 0 = forward), -3600..3600. */
        YAW(-3600, 3600),
        /** (0.15.0) Degrees added to the pitch of a spot or projector (positive looks down), -180..180. */
        PITCH(-180, 180),
        /** (0.15.0) Degrees the projected image turns around the light axis (spot, projector, disc cookie), -3600..3600. */
        ROLL(-3600, 3600),
        /** (0.15.0) Full aperture of a spot, degrees, 1..170. */
        ANGLE(1, 170),
        /** (0.15.0) Edge softness of a spot or projector, 0 (hard) .. 1 (the whole aperture fades). */
        SOFTNESS(0, 1),
        /** (0.15.0) Width of a projector (image x), blocks. */
        SIZE_X(0, MAX_RADIUS),
        /** (0.15.0) Height of a projector (image y), blocks. */
        SIZE_Y(0, MAX_RADIUS),
        /** (0.15.0) Cookie tiling: how many copies of the image fit across the projection, 0.01..64 (1 = once). */
        COOKIE_SCALE(.01f, 64),
        /** (0.15.0) Cookie rotation inside the projection, degrees, -3600..3600 (same as ROLL but in image space). */
        COOKIE_ROTATION(-3600, 3600),
        /** (0.15.0) Cookie scroll along image x, in images (1 = one whole image), -10000..10000. */
        COOKIE_U(-10000, 10000),
        /** (0.15.0) Cookie scroll along image y, in images, -10000..10000. */
        COOKIE_V(-10000, 10000),
        /** (0.15.0) Frame of a cookie sheet, 0..4095 (fractions blend two frames with {@link Cookie#blendFrames}). */
        FRAME(0, 4095),
        /** (0.15.0) Position along the {@link Builder#colorRamp color ramp}, 0..1. */
        COLOR(0, 1);

        /** Smallest and largest value of the parameter; track values are clamped to this range. */
        public final float min, max;

        Param(float min, float max) { this.min = min; this.max = max; }

        /** Clamps a value into this parameter's range; NaN becomes the value closest to 0 in the range. */
        public float clamp(float value) {
            return Float.isNaN(value) ? Math.clamp(0, min, max) : Math.clamp(value, min, max);
        }
    }

    /**
     * Resolved light at one moment: parameters taken from tracks and clamped, flicker applied to the intensity, and
     * the weight (0..1). The client turns the offset by the instance frame (0.10.0) and adds it to the resolved
     * points.
     *
     * @param weight    light weight, 0..1 (for {@link #along(Beam)}, times the beam weight on the client)
     * @param x         track offset, x, blocks
     * @param y         track offset, y, blocks
     * @param z         track offset, z, blocks
     * @param color     light color, ARGB (alpha ignored)
     * @param intensity brightness multiplier after flicker, 0..{@value #MAX_INTENSITY}
     * @param radius    reach (point, segment) or disc/ring radius, blocks
     * @param falloff   attenuation exponent
     * @param width     ring band width or disc edge, blocks
     * @param height    vertical reach of a disc or ring, blocks
     */
    public record State(float weight, float x, float y, float z, int color, float intensity, float radius,
                        float falloff, float width, float height) {
        /** True when the engine draws this light: weight above {@link #ACTIVE_WEIGHT}, lit and with some size. */
        public boolean visible() { return weight > ACTIVE_WEIGHT && intensity > 0 && radius > 0; }
    }


    /**
     * (0.15.0) Orientation and projection of a {@link Shape#SPOT}, {@link Shape#PROJECTOR} or of the cookie of a
     * {@link Shape#DISC} at one moment: base values plus tracks, clamped. Pure; the client turns it into world axes.
     *
     * @param yaw       yaw in the instance frame, degrees (0 forward, 90 left)
     * @param pitch     pitch, degrees (90 straight down, the default)
     * @param roll      turn of the image around the light axis, degrees
     * @param angle     full aperture of a spot, degrees
     * @param softness  edge softness, 0..1
     * @param sizeX     projector width, blocks
     * @param sizeY     projector height, blocks
     * @param scale     cookie tiling (1 = the image once)
     * @param rotation  cookie rotation in image space, degrees
     * @param u         cookie scroll along x, images
     * @param v         cookie scroll along y, images
     * @param frame     cookie sheet frame (fractional: blend), or -1 to follow the cookie clock
     * @param ramp      position along the color ramp, 0..1
     */
    public record Projection(float yaw, float pitch, float roll, float angle, float softness, float sizeX, float sizeY,
                             float scale, float rotation, float u, float v, float frame, float ramp) {}

    /**
     * (0.15.0) Light cookie: an image (PNG from any resource pack, path {@code ns:textures/...png}) that shapes the
     * light a {@link Shape#SPOT}, {@link Shape#PROJECTOR} or {@link Shape#DISC} throws on the surfaces it reaches. It
     * modulates the light, it is not drawn as a plane: surfaces outside the light get nothing, and lit surfaces keep
     * their own texture under it.
     *
     * <p><b>Channels.</b> The image is read with straight alpha and premultiplied on load ({@code rgb × a}).
     * {@link Mode#MASK} (default): the light is multiplied by the luminance of {@code rgb × a}, so white is full
     * light, black or transparent is none; a white-on-transparent PNG and a grayscale PNG without alpha both work.
     * {@link Mode#COLOR}: the light is multiplied by {@code rgb × a} per channel, so the image's colors tint the light
     * (use a white {@link Builder#color light color} to keep them exact). Alpha never makes the light brighter than the
     * light's own color × intensity.
     *
     * <p><b>Coordinates.</b> Image x runs to the right and y up when you look along the light (for a light pointing
     * straight down, the top of the image points to the instance's forward). The image covers the whole aperture of a
     * spot, the {@code SIZE_X × SIZE_Y} rectangle of a projector or the square around a disc ({@code 2 × radius}).
     * {@link #scale} tiles it, {@link #rotation} turns and {@link #offset} scrolls it in image units. Outside
     * the image: {@link Wrap#CLAMP} gives no light (transparent), {@link Wrap#REPEAT} tiles it. The aperture edge
     * itself always fades with the light's softness.
     *
     * <p><b>Sheets.</b> {@link #sheet} splits the image into a grid of frames (left to right, top to bottom, no margin)
     * played at {@link #fps} on the instance clock, or chosen by the light's {@link Param#FRAME} track;
     * {@link #blendFrames} cross-fades two frames (smooth caustics from few frames).
     *
     * <p><b>Limits.</b> Images larger than {@value #MAX_SIDE} pixels on a side are halved on load until they fit (a
     * whole sheet must fit in that square); larger than 4096 are refused. Missing or invalid images are logged once and
     * the light uses its {@link Fallback}.
     *
     * <pre>{@code
     * static final Light RUNAS = Light.projector(Point.center(0, 6, 0), 10, 10)
     *     .cookie(Light.Cookie.of("meumod:textures/lights/runas.png"))
     *     .color(0xff80c0ff).intensity(3).radius(9)
     *     .track(Light.Param.ROLL, "giro").build();
     * }</pre>
     */
    public static final class Cookie {
        /** Largest stored side of a cookie image (or of a whole sheet), pixels. */
        public static final int MAX_SIDE = 512;
        /** Most frames of a sheet. */
        public static final int MAX_FRAMES = 256;
        private static final Pattern LOCATION = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,200}\\.png");

        /** How the image modulates the light. */
        public enum Mode {
            /** Monochrome: luminance of {@code rgb × a} times the light color (default). */
            MASK,
            /** Multicolor: {@code rgb × a} per channel times the light color. */
            COLOR
        }

        /** What a projection reads outside the image (after scale, rotation and offset). */
        public enum Wrap {
            /** No light outside the image (default). */
            CLAMP,
            /** The image tiles. */
            REPEAT
        }

        /** Sheet playback on the clock (ignored when the light has a {@link Param#FRAME} track). */
        public enum Play {
            /** Loops (default). */
            LOOP,
            /** Plays once and holds the last frame. */
            ONCE,
            /** Forward then backward. */
            PING_PONG
        }

        /** What a cookie light draws when its image is loading, missing, invalid or over the frame's cookie budget. */
        public enum Fallback {
            /**
             * Draws the light without the image, with the intensity scaled by the image's average coverage (0.5 when
             * the image never loaded): the light keeps its size and roughly its energy (default).
             */
            AVERAGE,
            /** Draws nothing. */
            NONE
        }

        private final String texture;
        private final Mode mode;
        private final Wrap wrap;
        private final Play play;
        private final Fallback fallback;
        private final int columns, rows, frames;
        private final float fps, scale, rotation, offsetU, offsetV;
        private final boolean blend, pixelated;

        private Cookie(String texture, Mode mode, Wrap wrap, Play play, Fallback fallback, int columns, int rows,
                       int frames, float fps, float scale, float rotation, float offsetU, float offsetV, boolean blend,
                       boolean pixelated) {
            this.texture = texture; this.mode = mode; this.wrap = wrap; this.play = play; this.fallback = fallback;
            this.columns = columns; this.rows = rows; this.frames = frames; this.fps = fps; this.scale = scale;
            this.rotation = rotation; this.offsetU = offsetU; this.offsetV = offsetV; this.blend = blend;
            this.pixelated = pixelated;
        }

        /**
         * Cookie from a PNG, {@code namespace:textures/path.png} (any resource pack, usually your mod's
         * {@code assets/<ns>/textures/lights/}). MASK, CLAMP, one frame, smooth filtering, AVERAGE fallback.
         */
        public static Cookie of(String texture) {
            if (texture == null || !LOCATION.matcher(texture).matches())
                throw new IllegalArgumentException("Cookie texture must be ns:path.png (lower case): " + texture);
            return new Cookie(texture, Mode.MASK, Wrap.CLAMP, Play.LOOP, Fallback.AVERAGE, 1, 1, 1, 0, 1, 0, 0, 0, false, false);
        }

        private Cookie with(Mode m, Wrap w, Play p, Fallback f, int c, int r, int n, float fp, float sc, float ro,
                            float u, float v, boolean b, boolean px) {
            return new Cookie(texture, m, w, p, f, c, r, n, fp, sc, ro, u, v, b, px);
        }

        /** Channel semantics (MASK by default). */
        public Cookie mode(Mode value) {
            return with(Objects.requireNonNull(value, "mode"), wrap, play, fallback, columns, rows, frames, fps, scale,
                rotation, offsetU, offsetV, blend, pixelated);
        }

        /** Shorthand for {@code mode(Mode.COLOR)}: the image's colors tint the light. */
        public Cookie colored() { return mode(Mode.COLOR); }

        /** Outside-the-image behavior (CLAMP by default). */
        public Cookie wrap(Wrap value) {
            return with(mode, Objects.requireNonNull(value, "wrap"), play, fallback, columns, rows, frames, fps, scale,
                rotation, offsetU, offsetV, blend, pixelated);
        }

        /** Shorthand for {@code wrap(Wrap.REPEAT)}. */
        public Cookie repeat() { return wrap(Wrap.REPEAT); }

        /** Fallback when the image is not available or over budget (AVERAGE by default). */
        public Cookie fallback(Fallback value) {
            return with(mode, wrap, play, Objects.requireNonNull(value, "fallback"), columns, rows, frames, fps, scale,
                rotation, offsetU, offsetV, blend, pixelated);
        }

        /**
         * Splits the image into {@code columns × rows} frames (1..64 each, left to right then top to bottom, no
         * margin); all of them are played unless {@link #frames} says fewer.
         */
        public Cookie sheet(int columns, int rows) {
            if (columns < 1 || columns > 64 || rows < 1 || rows > 64 || columns * rows > MAX_FRAMES)
                throw new IllegalArgumentException("Cookie sheet must be 1..64 × 1..64 with at most " + MAX_FRAMES + " frames");
            return with(mode, wrap, play, fallback, columns, rows, columns * rows, fps == 0 ? 12 : fps, scale, rotation,
                offsetU, offsetV, blend, pixelated);
        }

        /** Number of frames actually used, 1..columns × rows. */
        public Cookie frames(int count) {
            if (count < 1 || count > columns * rows) throw new IllegalArgumentException("Cookie frames must be 1.." + columns * rows);
            return with(mode, wrap, play, fallback, columns, rows, count, fps, scale, rotation, offsetU, offsetV, blend, pixelated);
        }

        /** Frames per second on the clock, 0..60 (12 by default for sheets; 0 holds frame 0). */
        public Cookie fps(float value) {
            return with(mode, wrap, play, fallback, columns, rows, frames, Builder.range("cookie fps", value, 0, 60), scale,
                rotation, offsetU, offsetV, blend, pixelated);
        }

        /** Playback on the clock (LOOP by default). */
        public Cookie play(Play value) {
            return with(mode, wrap, Objects.requireNonNull(value, "play"), fallback, columns, rows, frames, fps, scale,
                rotation, offsetU, offsetV, blend, pixelated);
        }

        /** Cross-fades the current and the next frame instead of cutting (no cost when the sheet has one frame). */
        public Cookie blendFrames(boolean value) {
            return with(mode, wrap, play, fallback, columns, rows, frames, fps, scale, rotation, offsetU, offsetV, value, pixelated);
        }

        /** Base tiling, 0.01..64 (the {@link Param#COOKIE_SCALE} track replaces it). */
        public Cookie scale(float copies) {
            return with(mode, wrap, play, fallback, columns, rows, frames, fps, Builder.range("cookie scale", copies, .01f, 64),
                rotation, offsetU, offsetV, blend, pixelated);
        }

        /** Base rotation in image space, degrees (the {@link Param#COOKIE_ROTATION} track replaces it). */
        public Cookie rotation(float degrees) {
            return with(mode, wrap, play, fallback, columns, rows, frames, fps, scale,
                Builder.range("cookie rotation", degrees, -3600, 3600), offsetU, offsetV, blend, pixelated);
        }

        /** Base scroll in images (the {@link Param#COOKIE_U}/{@link Param#COOKIE_V} tracks replace it). */
        public Cookie offset(float u, float v) {
            return with(mode, wrap, play, fallback, columns, rows, frames, fps, scale, rotation,
                Builder.range("cookie u", u, -10000, 10000), Builder.range("cookie v", v, -10000, 10000), blend, pixelated);
        }

        /** Nearest-texel sampling (pixel art) instead of smooth filtering with mipmaps. */
        public Cookie pixelated() {
            return with(mode, wrap, play, fallback, columns, rows, frames, fps, scale, rotation, offsetU, offsetV, blend, true);
        }

        /** PNG location, {@code ns:path.png}. */
        public String texture() { return texture; }
        /** Channel semantics. */
        public Mode mode() { return mode; }
        /** Outside-the-image behavior. */
        public Wrap wrap() { return wrap; }
        /** Clock playback of a sheet. */
        public Play play() { return play; }
        /** Fallback without the image. */
        public Fallback fallback() { return fallback; }
        /** Sheet columns (1 without a sheet). */
        public int columns() { return columns; }
        /** Sheet rows (1 without a sheet). */
        public int rows() { return rows; }
        /** Frames played (1 without a sheet). */
        public int frames() { return frames; }
        /** Frames per second on the clock. */
        public float fps() { return fps; }
        /** Base tiling. */
        public float scale() { return scale; }
        /** Base rotation, degrees. */
        public float rotation() { return rotation; }
        /** Base scroll along x, images. */
        public float offsetU() { return offsetU; }
        /** Base scroll along y, images. */
        public float offsetV() { return offsetV; }
        /** True when two frames cross-fade. */
        public boolean blendFrames() { return blend; }
        /** True for nearest-texel sampling. */
        public boolean isPixelated() { return pixelated; }

        /**
         * Frame position at a moment, pure: {@code track} (a {@link Param#FRAME} value, or NaN for none) wins; else the
         * clock ({@code seconds} × fps) with the playback mode. The integer part is the frame (0..frames-1), the
         * fraction the cross-fade toward the next one (0 unless {@link #blendFrames}).
         */
        public float frameAt(double seconds, float track) {
            if (frames <= 1) return 0;
            double f;
            if (!Float.isNaN(track)) f = track;
            else {
                double x = Double.isFinite(seconds) ? Math.max(0, seconds) * fps : 0;
                f = switch (play) {
                    case LOOP -> x % frames;
                    case ONCE -> Math.min(x, frames - 1);
                    case PING_PONG -> {
                        double period = 2.0 * (frames - 1);
                        double m = x % period;
                        yield m <= frames - 1 ? m : period - m;
                    }
                };
            }
            f = Math.clamp(f, 0, play == Play.LOOP && Float.isNaN(track) ? frames - 1e-4 : frames - 1);
            if (!blend) f = Math.floor(f);
            return (float) f;
        }

        /** Equal when every setting is equal (same image, mode, sheet, playback and base transform). */
        @Override public boolean equals(Object o) {
            return o instanceof Cookie c && texture.equals(c.texture) && mode == c.mode && wrap == c.wrap && play == c.play
                && fallback == c.fallback && columns == c.columns && rows == c.rows && frames == c.frames && fps == c.fps
                && scale == c.scale && rotation == c.rotation && offsetU == c.offsetU && offsetV == c.offsetV
                && blend == c.blend && pixelated == c.pixelated;
        }

        /** Hash of the settings, consistent with {@link #equals}. */
        @Override public int hashCode() { return Objects.hash(texture, mode, wrap, columns, rows, frames, fps, scale, blend, pixelated); }

        /** Short description for logs. */
        @Override public String toString() { return "Cookie[" + texture + " " + mode + " " + columns + "x" + rows + "]"; }
    }

    private final Shape shape;
    private final Point from, to;
    private final Beam beam;
    private final int color;
    private final boolean colorSet;
    private final float intensity, radius, falloff, width, height, flicker, flickerSpeed;
    private final Map<Param, String> tracks;
    private final String weightPhase;
    // 0.15.0
    private final Point target;
    private final Cookie cookie;
    private final Attenuation attenuation;
    private final Blend blend;
    private final float yaw, pitch, roll, angle, softness, sizeX, sizeY;
    private final int[] ramp, gradient;

    private Light(Builder b) {
        shape = b.shape; from = b.from; to = b.to; beam = b.beam; color = b.color; colorSet = b.colorSet;
        intensity = b.intensity; radius = b.radius; falloff = b.falloff; width = b.width; height = b.height;
        flicker = b.flicker; flickerSpeed = b.flickerSpeed;
        tracks = Collections.unmodifiableMap(new EnumMap<>(b.tracks));
        weightPhase = b.weightPhase;
        target = b.target; cookie = b.cookie; attenuation = b.attenuation; blend = b.blend;
        yaw = b.yaw; pitch = b.pitch; roll = b.roll; angle = b.angle; softness = b.softness; sizeX = b.sizeX; sizeY = b.sizeY;
        ramp = b.ramp; gradient = b.gradient;
    }

    /** Point light at {@code at}. Defaults: white, intensity 1, reach 8 blocks, falloff 2, no flicker. */
    public static Builder point(Point at) {
        return new Builder(Shape.POINT, Objects.requireNonNull(at, "at"), null, null, 8, 0);
    }

    /** Light along the segment from {@code from} to {@code to} (different points). Defaults as {@link #point}, reach 4. */
    public static Builder segment(Point from, Point to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) throw new IllegalArgumentException("Light segment ends must be different points: " + from);
        return new Builder(Shape.SEGMENT, from, to, null, 4, 0);
    }

    /**
     * Light along a beam of the same effect (register the beams before the lights): its ends, reveal and weight, and
     * its halo color unless {@link Builder#color} is called. Defaults as {@link #segment}.
     */
    public static Builder along(Beam beam) {
        Objects.requireNonNull(beam, "beam");
        return new Builder(Shape.SEGMENT, beam.from(), beam.to(), beam, 4, 0);
    }

    /** Disc of light on the ground: {@code radius} blocks (0..{@value #MAX_RADIUS}) around {@code center}; soft edge 25% of it. */
    public static Builder disc(Point center, float radius) {
        float r = Builder.range("disc radius", radius, 0, MAX_RADIUS);
        return new Builder(Shape.DISC, Objects.requireNonNull(center, "center"), null, null, r, r * .25f);
    }

    /** Ring of light on the ground: a band {@code width} blocks wide (0..64) at {@code radius} blocks from {@code center}. */
    public static Builder ring(Point center, float radius, float width) {
        float r = Builder.range("ring radius", radius, 0, MAX_RADIUS);
        float w = Builder.range("ring width", width, 0, 64);
        return new Builder(Shape.RING, Objects.requireNonNull(center, "center"), null, null, r, w);
    }

    /**
     * (0.15.0) Spotlight at {@code at}, pointing straight down by default ({@link Builder#direction},
     * {@link Builder#toward}). Defaults: white, intensity 1, reach 12 blocks, falloff 2, aperture 40°, softness 0.25.
     * A {@link Builder#cookie cookie} turns it into a slide projector.
     */
    public static Builder spot(Point at) {
        Builder b = new Builder(Shape.SPOT, Objects.requireNonNull(at, "at"), null, null, 12, 0);
        b.softness = .25f;
        return b;
    }

    /**
     * (0.15.0) Planar projector at {@code at}: a {@code width × height} block rectangle (0..{@value #MAX_RADIUS}) pushed
     * {@code radius} blocks along its direction (straight down by default). Parallel rays, so a cookie keeps its size on
     * every surface it reaches. Defaults: white, intensity 1, reach 8 blocks, falloff 1, softness 0.1.
     */
    public static Builder projector(Point at, float width, float height) {
        Builder b = new Builder(Shape.PROJECTOR, Objects.requireNonNull(at, "at"), null, null, 8, 0);
        b.sizeX = Builder.range("projector width", width, 0, MAX_RADIUS);
        b.sizeY = Builder.range("projector height", height, 0, MAX_RADIUS);
        b.falloff = 1;
        b.softness = .1f;
        return b;
    }

    /** Shape of the source. */
    public Shape shape() { return shape; }
    /** Position (point), start (segment) or center (disc, ring). */
    public Point from() { return from; }
    /** End of a segment, or null for the other shapes. */
    public Point to() { return to; }
    /** Beam this light follows ({@link #along(Beam)}), or null. */
    public Beam beam() { return beam; }
    /** Light color, ARGB (alpha ignored); for {@link #along(Beam)} without an explicit color, the beam's halo color. */
    public int color() { return colorSet || beam == null ? color : beam.haloColor(); }
    /** Brightness multiplier, 0..{@value #MAX_INTENSITY}. */
    public float intensity() { return intensity; }
    /** Reach (point, segment) or disc/ring radius, blocks. */
    public float radius() { return radius; }
    /** Attenuation exponent (point, segment). */
    public float falloff() { return falloff; }
    /** Ring band width or disc edge softness, blocks. */
    public float width() { return width; }
    /** Vertical reach of a disc or ring, blocks. */
    public float height() { return height; }
    /** Flicker amount, 0..1. */
    public float flicker() { return flicker; }
    /** Flicker speed, changes per second. */
    public float flickerSpeed() { return flickerSpeed; }
    /** Track bound to a parameter, or null when the parameter is constant. */
    public String track(Param param) { return tracks.get(Objects.requireNonNull(param)); }
    /** Every track binding, in {@link Param} order. */
    public Map<Param, String> tracks() { return tracks; }
    /** Phase whose weight multiplies the light weight, or null. */
    public String weightPhase() { return weightPhase; }
    /** (0.15.0) Point a spot or projector aims at every frame, or null to use {@link #yaw()}/{@link #pitch()}. */
    public Point target() { return target; }
    /** (0.15.0) The light's cookie, or null. */
    public Cookie cookie() { return cookie; }
    /** (0.15.0) Attenuation curve ({@link Attenuation#POWER} by default). */
    public Attenuation attenuation() { return attenuation; }
    /** (0.15.0) Composition mode ({@link Blend#ADD} by default). */
    public Blend blend() { return blend; }
    /** (0.15.0) Base yaw of a spot or projector in the instance frame, degrees. */
    public float yaw() { return yaw; }
    /** (0.15.0) Base pitch of a spot or projector, degrees (90 = down). */
    public float pitch() { return pitch; }
    /** (0.15.0) Base roll of the projected image, degrees. */
    public float roll() { return roll; }
    /** (0.15.0) Full aperture of a spot, degrees. */
    public float angle() { return angle; }
    /** (0.15.0) Edge softness, 0..1. */
    public float softness() { return softness; }
    /** (0.15.0) Projector width, blocks. */
    public float sizeX() { return sizeX; }
    /** (0.15.0) Projector height, blocks. */
    public float sizeY() { return sizeY; }
    /** (0.15.0) Color ramp stops (ARGB, 2..4) read by {@link Param#COLOR}, or an empty array. */
    public int[] colorRamp() { return ramp.clone(); }
    /** (0.15.0) Spatial gradient stops (ARGB, 2..4) from the source to the reach, or an empty array. */
    public int[] distanceGradient() { return gradient.clone(); }
    /** (0.15.0) Number of spatial gradient stops (0 without a gradient). */
    public int gradientStops() { return gradient.length; }
    /** (0.15.0) Spatial gradient stop {@code i} (ARGB). */
    public int gradientStop(int i) { return gradient[i]; }
    /** (0.15.0) True when the light needs an orientation (spot, projector, or a disc with a cookie). */
    public boolean oriented() { return shape == Shape.SPOT || shape == Shape.PROJECTOR || (shape == Shape.DISC && cookie != null); }

    /**
     * (0.15.0) Orientation and projection at one moment: base values plus tracks (clamped). {@code seconds} is the
     * instance's real age (drives the cookie clock).
     */
    public Projection projection(Timeline.Sampler timeline, double seconds) {
        Objects.requireNonNull(timeline, "timeline");
        float frame = -1;
        if (cookie != null) {
            String t = tracks.get(Param.FRAME);
            frame = cookie.frameAt(seconds, t == null ? Float.NaN : Param.FRAME.clamp(timeline.value(t)));
        }
        return new Projection(yaw + value(Param.YAW, 0, timeline), pitch + value(Param.PITCH, 0, timeline),
            roll + value(Param.ROLL, 0, timeline), value(Param.ANGLE, angle, timeline), value(Param.SOFTNESS, softness, timeline),
            value(Param.SIZE_X, sizeX, timeline), value(Param.SIZE_Y, sizeY, timeline),
            value(Param.COOKIE_SCALE, cookie == null ? 1 : cookie.scale(), timeline),
            value(Param.COOKIE_ROTATION, cookie == null ? 0 : cookie.rotation(), timeline),
            value(Param.COOKIE_U, cookie == null ? 0 : cookie.offsetU(), timeline),
            value(Param.COOKIE_V, cookie == null ? 0 : cookie.offsetV(), timeline), frame, value(Param.COLOR, 0, timeline));
    }

    /**
     * (0.15.0) Color of a ramp of ARGB stops (equally spaced) at {@code t} (0..1), linear per channel, opaque. Pure;
     * one stop returns it.
     */
    public static int rampColor(int[] stops, float t) {
        if (stops.length == 0) return 0xffffffff;
        if (stops.length == 1) return stops[0] | 0xff000000;
        float x = (Float.isNaN(t) ? 0 : Math.clamp(t, 0f, 1f)) * (stops.length - 1);
        int i = Math.min((int) x, stops.length - 2);
        float f = x - i;
        int a = stops[i], b = stops[i + 1];
        int r = Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return 0xff000000 | r << 16 | g << 8 | bl;
    }

    /** True when some parameter, the weight or the phase depends on the timeline. */
    public boolean animated() { return !tracks.isEmpty() || weightPhase != null; }

    /**
     * Checks that every track and the phase this light reads exist in {@code timeline}, which may be null only when
     * nothing is {@link #animated() animated}. Called by {@link Effects#lights}.
     */
    public void validate(Timeline timeline) {
        if (!animated()) return;
        if (timeline == null)
            throw new IllegalArgumentException("Light reads tracks or a phase: register the effect's timeline first");
        tracks.forEach((param, track) -> {
            if (!timeline.hasTrack(track)) throw new IllegalArgumentException("Light " + param + " reads unknown track: " + track);
        });
        if (weightPhase != null && !timeline.hasPhase(weightPhase))
            throw new IllegalArgumentException("Light weight reads unknown phase: " + weightPhase);
    }

    /**
     * Resolves this light at one moment. {@code timeline} is the instance's sampler at its local age;
     * {@code envelope} the instance opacity (clamped to 0..1); {@code seconds} the instance's real age in seconds
     * (drives the flicker, which does not jump at sustain loops); {@code seed} the instance seed (0..1), so two
     * instances flicker differently; {@code flickerScale} (0..1) scales the flicker amount (the engine passes the
     * screen-effects intensity, a quarter of it when the player hides lightning flashes).
     */
    public State sample(Timeline.Sampler timeline, float envelope, double seconds, float seed, float flickerScale) {
        Objects.requireNonNull(timeline, "timeline");
        float weight = Float.isNaN(envelope) ? 0 : Math.clamp(envelope, 0f, 1f);
        if (weightPhase != null) weight *= timeline.weight(weightPhase);
        weight *= value(Param.WEIGHT, 1, timeline);
        float amount = value(Param.FLICKER, flicker, timeline) * (Float.isNaN(flickerScale) ? 0 : Math.clamp(flickerScale, 0f, 1f));
        float lit = value(Param.INTENSITY, intensity, timeline)
            * flickerFactor(amount, value(Param.FLICKER_SPEED, flickerSpeed, timeline), seconds, seed);
        return new State(Math.clamp(weight, 0f, 1f), value(Param.X, 0, timeline), value(Param.Y, 0, timeline),
            value(Param.Z, 0, timeline), ramp.length > 0 ? rampColor(ramp, value(Param.COLOR, 0, timeline)) : color(),
            Math.clamp(lit, 0f, MAX_INTENSITY), value(Param.RADIUS, radius, timeline),
            value(Param.FALLOFF, falloff, timeline), value(Param.WIDTH, width, timeline), value(Param.HEIGHT, height, timeline));
    }

    private float value(Param param, float constant, Timeline.Sampler timeline) {
        String track = tracks.get(param);
        return track == null ? constant : param.clamp(timeline.value(track));
    }

    /**
     * Flicker multiplier of the intensity, pure: {@code 1 - amount * n}, with {@code n} a smooth value noise in 0..1
     * that changes about {@code speed} times per second, offset by {@code seed}. 1 when amount or speed is 0;
     * always within {@code [1 - amount, 1]}.
     */
    public static float flickerFactor(float amount, float speed, double seconds, float seed) {
        if (!(amount > 0) || !(speed > 0) || !Double.isFinite(seconds)) return 1;
        double x = seconds * speed + seed * 61.7;
        double cell = Math.floor(x), f = x - cell;
        double u = f * f * (3 - 2 * f);
        double a = hash(cell, seed), b = hash(cell + 1, seed);
        double n = a + (b - a) * u;
        // A second, faster octave gives the irregular look of fire.
        double x2 = x * 2.37 + 11.3, cell2 = Math.floor(x2), f2 = x2 - cell2, u2 = f2 * f2 * (3 - 2 * f2);
        double n2 = hash(cell2, seed + .5f) + (hash(cell2 + 1, seed + .5f) - hash(cell2, seed + .5f)) * u2;
        return (float) (1 - Math.min(amount, 1) * Math.clamp(n * .7 + n2 * .3, 0, 1));
    }

    private static double hash(double cell, float seed) {
        double s = Math.sin(cell * 127.1 + seed * 311.7) * 43758.5453;
        return s - Math.floor(s);
    }

    /** Builder of a {@link Light}. Every setter validates its arguments right away. */
    public static final class Builder {
        private final Shape shape;
        private final Point from, to;
        private final Beam beam;
        private int color = 0xffffffff;
        private boolean colorSet;
        private float intensity = 1, radius, falloff = 2, width, height = 3, flicker, flickerSpeed = 6;
        private final Map<Param, String> tracks = new EnumMap<>(Param.class);
        private String weightPhase;
        private Point target;
        private Cookie cookie;
        private Attenuation attenuation = Attenuation.POWER;
        private Blend blend = Blend.ADD;
        private float yaw, pitch = 90, roll, angle = 40, softness, sizeX, sizeY;
        private int[] ramp = new int[0], gradient = new int[0];

        private Builder(Shape shape, Point from, Point to, Beam beam, float radius, float width) {
            this.shape = shape; this.from = from; this.to = to; this.beam = beam; this.radius = radius; this.width = width;
        }

        /** Light color (ARGB, alpha ignored); white by default, the halo color for {@link Light#along(Beam)}. */
        public Builder color(int argb) {
            color = argb; colorSet = true;
            return this;
        }

        /** Brightness multiplier, 0..{@value #MAX_INTENSITY} (1 by default). */
        public Builder intensity(float value) {
            intensity = range("intensity", value, 0, MAX_INTENSITY);
            return this;
        }

        /** Reach of a point or segment light, or the radius of a disc or ring, in blocks (0..{@value #MAX_RADIUS}). */
        public Builder radius(float blocks) {
            radius = range("radius", blocks, 0, MAX_RADIUS);
            return this;
        }

        /** Attenuation exponent of a point or segment light, 0.25..4 (2 by default). */
        public Builder falloff(float exponent) {
            falloff = range("falloff", exponent, .25f, 4);
            return this;
        }

        /** Ring band width or disc edge softness, blocks (0..64). */
        public Builder width(float blocks) {
            width = range("width", blocks, 0, 64);
            return this;
        }

        /** Vertical reach of a disc or ring above and below its center, blocks (0.25..32, 3 by default). */
        public Builder height(float blocks) {
            height = range("height", blocks, .25f, 32);
            return this;
        }

        /** Flicker: {@code amount} 0..1 of the intensity, about {@code speed} changes per second (0..20). */
        public Builder flicker(float amount, float speed) {
            flicker = range("flicker", amount, 0, 1);
            flickerSpeed = range("flicker speed", speed, 0, 20);
            return this;
        }

        /**
         * Takes {@code param} from a track of the effect's timeline instead of the constant (clamped to the parameter
         * range). One track per parameter; a track may feed several parameters.
         */
        public Builder track(Param param, String track) {
            Objects.requireNonNull(param, "param");
            checkName(track, "track");
            if (tracks.putIfAbsent(param, track) != null)
                throw new IllegalArgumentException("Parameter already bound to a track: " + param);
            return this;
        }

        /** Multiplies the light weight by the weight of a timeline phase (0 outside it). */
        public Builder weightPhase(String phase) {
            checkName(phase, "phase");
            if (weightPhase != null) throw new IllegalArgumentException("Weight phase already defined");
            weightPhase = phase;
            return this;
        }

        private void needsOrientation(String what) {
            if (shape != Shape.SPOT && shape != Shape.PROJECTOR)
                throw new IllegalArgumentException(what + " is only for spot and projector lights");
        }

        /**
         * (0.15.0) Direction of a spot or projector in the instance frame: {@code yaw} degrees (0 = the instance's
         * forward, 90 = its left) and {@code pitch} degrees (-90 up .. 90 down, 90 by default). Tracks
         * {@link Param#YAW}/{@link Param#PITCH} add to them.
         */
        public Builder direction(float yaw, float pitch) {
            needsOrientation("direction");
            this.yaw = range("yaw", yaw, -3600, 3600);
            this.pitch = range("pitch", pitch, -90, 90);
            target = null;
            return this;
        }

        /**
         * (0.15.0) Aims a spot or projector at a point every frame (an entity, its eyes, a socket, a world position);
         * the yaw and pitch tracks still add to the aim. Without the point's entity loaded the light is not drawn.
         */
        public Builder toward(Point point) {
            needsOrientation("toward");
            target = Objects.requireNonNull(point, "point");
            return this;
        }

        /** (0.15.0) Turn of the projected image around the light axis, degrees (spot, projector, disc cookie). */
        public Builder roll(float degrees) {
            roll = range("roll", degrees, -3600, 3600);
            return this;
        }

        /** (0.15.0) Full aperture of a spot, degrees (1..170, 40 by default). */
        public Builder angle(float degrees) {
            if (shape != Shape.SPOT) throw new IllegalArgumentException("angle is only for spot lights");
            angle = range("angle", degrees, 1, 170);
            return this;
        }

        /** (0.15.0) Edge softness of a spot or projector, 0..1 (0.25 spot, 0.1 projector). */
        public Builder softness(float value) {
            needsOrientation("softness");
            softness = range("softness", value, 0, 1);
            return this;
        }

        /** (0.15.0) Width and height of a projector, blocks (0..{@value #MAX_RADIUS}). */
        public Builder size(float width, float height) {
            if (shape != Shape.PROJECTOR) throw new IllegalArgumentException("size is only for projector lights");
            sizeX = range("projector width", width, 0, MAX_RADIUS);
            sizeY = range("projector height", height, 0, MAX_RADIUS);
            return this;
        }

        /**
         * (0.15.0) Light cookie: the image that shapes the light (see {@link Cookie}). For spots, projectors and discs
         * (a disc maps the image onto the square of side {@code 2 × radius} around its center, turned by
         * {@link #roll}).
         */
        public Builder cookie(Cookie value) {
            if (shape != Shape.SPOT && shape != Shape.PROJECTOR && shape != Shape.DISC)
                throw new IllegalArgumentException("Cookies are only for spot, projector and disc lights");
            cookie = Objects.requireNonNull(value, "cookie");
            return this;
        }

        /** (0.15.0) Shorthand for {@code cookie(Cookie.of(texture))}. */
        public Builder cookie(String texture) { return cookie(Cookie.of(texture)); }

        /** (0.15.0) Attenuation curve (POWER by default, the 0.9 curve). */
        public Builder attenuation(Attenuation value) {
            attenuation = Objects.requireNonNull(value, "attenuation");
            return this;
        }

        /** (0.15.0) Composition with the other lights (ADD by default). */
        public Builder blend(Blend value) {
            blend = Objects.requireNonNull(value, "blend");
            return this;
        }

        /**
         * (0.15.0) Color animated over time: 2..4 ARGB stops, equally spaced, read at the {@link Param#COLOR} track
         * (0..1; without the track, the first stop). Replaces {@link #color}.
         */
        public Builder colorRamp(int... argb) {
            ramp = stops("color ramp", argb);
            return this;
        }

        /**
         * (0.15.0) Color that changes with distance: 2..4 ARGB stops from the source (0) to the reach (1), multiplied by
         * the light color (keep it white for the exact stops). Distance is from the point or segment, along the axis of a
         * projector, from the center of a disc or ring.
         */
        public Builder distanceGradient(int... argb) {
            gradient = stops("distance gradient", argb);
            return this;
        }

        private static int[] stops(String what, int[] argb) {
            if (argb == null || argb.length < 2 || argb.length > 4)
                throw new IllegalArgumentException(what + " needs 2..4 colors");
            return argb.clone();
        }

        /** Immutable light. Tracks, phase and the linked beam are checked at registration. */
        public Light build() { return new Light(this); }

        static float range(String what, float value, float min, float max) {
            if (!(value >= min && value <= max))
                throw new IllegalArgumentException(what + " must be within " + min + ".." + max);
            return value;
        }

        private static void checkName(String name, String kind) {
            if (name == null || !NAME.matcher(name).matches())
                throw new IllegalArgumentException("Invalid " + kind + " name (expected [a-z0-9_], up to 64): " + name);
        }
    }
}
