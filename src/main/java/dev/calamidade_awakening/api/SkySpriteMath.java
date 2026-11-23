package dev.calamidade_awakening.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.calamidade_awakening.api.v1.SkyImage;
import dev.calamidade_awakening.api.v1.SkyImpostor;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure math of the sky sprites (0.14.0, phase 4: {@link SkyImage} and {@link SkyImpostor}): per-profile caps and
 * memory budgets, flipbook frames, quad geometry on the sky sphere, impostor views and their weights, levels of detail
 * with hysteresis, the flash safety net, upload scheduling, draw runs and the impostor descriptor. Deterministic and
 * testable without a GPU. Not public API.
 *
 * <p>Directions are world axes (x east, y up, z south), unit length; azimuth and elevation as
 * {@link SkyObjectsMath#direction}.
 */
public final class SkySpriteMath {
    private SkySpriteMath() {}

    // ------------------------------------------------------------------------------------------------ caps

    /**
     * Per-frame caps and budgets of a quality profile.
     *
     * @param images        images drawn per frame
     * @param impostors     impostors drawn per frame
     * @param vramBytes     estimated texture memory of the sky cache (RGBA8 with mipmaps)
     * @param uploads       texture uploads per frame
     * @param uploadBytes   bytes uploaded per frame (one upload larger than this still goes alone)
     * @param blendFrames   whether flipbook frames may blend
     * @param viewBlend     what {@link SkyImpostor.ViewBlend#AUTO} means in this profile
     */
    public record Caps(int images, int impostors, long vramBytes, int uploads, long uploadBytes, boolean blendFrames,
                       SkyImpostor.ViewBlend viewBlend) {}

    private static final long MIB = 1L << 20;

    /** Caps of a profile by its name (LOW, BALANCED, HIGH, ULTRA; the internal test profile lifts the counts). */
    public static Caps caps(FrameLimits limits) {
        if (limits.test()) return new Caps(96, 16, 512 * MIB, 2, 32 * MIB, true, SkyImpostor.ViewBlend.QUAD);
        return switch (limits.name()) {
            case "LOW" -> new Caps(8, 2, 64 * MIB, 1, 4 * MIB, false, SkyImpostor.ViewBlend.NEAREST);
            case "BALANCED" -> new Caps(24, 4, 128 * MIB, 1, 8 * MIB, true, SkyImpostor.ViewBlend.PAIR);
            case "ULTRA" -> new Caps(96, 16, 512 * MIB, 2, 32 * MIB, true, SkyImpostor.ViewBlend.QUAD);
            default -> new Caps(48, 8, 256 * MIB, 2, 16 * MIB, true, SkyImpostor.ViewBlend.QUAD);
        };
    }

    /** Bytes of an RGBA8 texture, with its full mipmap chain when {@code mipmaps}. */
    public static long textureBytes(int width, int height, boolean mipmaps) {
        long total = 0;
        int w = Math.max(1, width), h = Math.max(1, height);
        while (true) {
            total += (long) w * h * 4;
            if (!mipmaps || (w == 1 && h == 1)) return total;
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }
    }

    /** Mipmap levels below level 0 of a texture (0 without mipmaps). */
    public static int mipLevels(int width, int height) {
        int n = 0, s = Math.max(width, height);
        while (s > 1) { s /= 2; n++; }
        return n;
    }

    /**
     * Mipmap levels below level 0 that keep every cell of a {@code columns} × {@code rows} grid whole: each level halves
     * the cells, so a sheet gets as many levels as its cell size can be halved exactly (a 64 × 64 frame: 6; 36 × 36: 2).
     * The levels are then made per cell (a 2 × 2 box never crosses a cell border) and the shader keeps its samples half
     * a texel of the sampled level inside the frame, so frames never bleed into each other at any size — no margin
     * needed. A single image ({@code 1 × 1}) gets the full chain; a grid that does not divide the image, none.
     */
    public static int gridLevels(int width, int height, int columns, int rows) {
        if (columns <= 1 && rows <= 1) return mipLevels(width, height);
        if (width % columns != 0 || height % rows != 0) return 0;
        int cw = width / columns, ch = height / rows, n = 0;
        while (cw % 2 == 0 && ch % 2 == 0 && cw > 1 && ch > 1) { cw /= 2; ch /= 2; n++; }
        return n;
    }

    /** Bytes of an RGBA8 texture with exactly {@code levels} mipmap levels below level 0 (sizes halved, at least 1). */
    public static long textureBytes(int width, int height, int levels) {
        long total = 0;
        int w = Math.max(1, width), h = Math.max(1, height);
        for (int i = 0; i <= levels; i++) {
            total += (long) w * h * 4;
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }
        return total;
    }

    /**
     * Whether one more upload of {@code bytes} fits this frame, after {@code done} uploads totalling {@code sent} bytes:
     * never more than {@link Caps#uploads}; the first upload of a frame always goes (otherwise a texture larger than the
     * byte limit would never load); the others only while the bytes stay within {@link Caps#uploadBytes}.
     */
    public static boolean uploadFits(Caps caps, int done, long sent, long bytes) {
        if (done >= caps.uploads()) return false;
        if (done == 0) return true;
        return sent + bytes <= caps.uploadBytes();
    }

    /** Halvings so that the larger side of a {@code width} × {@code height} image fits {@code max} (0 when it fits). */
    public static int halvings(int width, int height, int max) {
        int n = 0, s = Math.max(width, height);
        while (s > Math.max(1, max)) { s = (s + 1) / 2; n++; }
        return n;
    }

    // ------------------------------------------------------------------------------------------------ flipbooks

    /**
     * Frames to show: {@code a} blended toward {@code b} by {@code mix} (0..1; 0 shows only {@code a}).
     */
    public record Pick(int a, int b, float mix) {
        /** One frame, no blend. */
        public static Pick of(int frame) { return new Pick(frame, frame, 0); }
    }

    /**
     * Frame of a flipbook at {@code seconds} of its clock (negative counts as 0). Deterministic: the same inputs give the
     * same frames on every client. {@code track} is the FRAME track value (used by {@link SkyImage.Play#TRACK} only).
     * Each frame lasts {@code 1 / fps} seconds.
     * {@code frozen} shows {@code safe}. Without {@code blend} the mix is 0. LOOP: start, …, last, 0, …; ONCE: stays on
     * the last; PING_PONG: 0 … last … 1 0 … (ends not repeated); TRACK: the track value clamped to the frames.
     */
    public static Pick frame(SkyImage.Play play, int frames, float fps, int start, double seconds, float track, boolean blend,
                             boolean frozen, int safe) {
        if (frames <= 1) return Pick.of(0);
        if (frozen) return Pick.of(Math.clamp(safe, 0, frames - 1));
        double t;
        int a, b;
        switch (play) {
            case TRACK -> {
                t = Float.isNaN(track) ? 0 : Math.clamp(track, 0, frames - 1);
                a = (int) Math.floor(t);
                b = Math.min(a + 1, frames - 1);
            }
            case ONCE -> {
                t = start + Math.max(0, seconds) * fps;
                if (t >= frames - 1) return Pick.of(frames - 1);
                a = (int) Math.floor(t);
                b = a + 1;
            }
            case PING_PONG -> {
                // Step i of the period 2n − 2 shows frame i going up and 2n − 2 − i coming back; blends toward the next step.
                int period = 2 * frames - 2;
                t = start + Math.max(0, seconds) * fps;
                int i = (int) Math.floorMod((long) Math.floor(t), period), j = (i + 1) % period;
                a = i <= frames - 1 ? i : period - i;
                b = j <= frames - 1 ? j : period - j;
            }
            default -> {
                double u = start + Math.max(0, seconds) * fps;
                t = u;
                a = (int) Math.floorMod((long) Math.floor(u), frames);
                b = (a + 1) % frames;
            }
        }
        float mix = blend ? (float) (t - Math.floor(t)) : 0;
        if (a == b) mix = 0;
        return new Pick(a, b, mix);
    }

    /**
     * Rectangle of a cell of a {@code columns} × {@code rows} grid (row by row) in texture coordinates
     * {@code {u0, v0, u1, v1}} (0..1, v down from the top row), shrunk by {@code inset} pixels of a cell of
     * {@code cellW} × {@code cellH} on every side (the gutter of an impostor page; 0 for a sheet).
     */
    public static float[] cell(int index, int columns, int rows, int insetX, int insetY, int cellW, int cellH) {
        int c = Math.floorMod(index, columns), r = Math.min(index / columns, rows - 1);
        float u0 = (c + insetX / (float) cellW) / columns, u1 = (c + 1 - insetX / (float) cellW) / columns;
        float v0 = (r + insetY / (float) cellH) / rows, v1 = (r + 1 - insetY / (float) cellH) / rows;
        return new float[] {u0, v0, u1, v1};
    }

    /** Whether a flipbook freezes: marked or detected as flashing, while the player hides flashes or turns screen effects to 0. */
    public static boolean frozen(boolean flashing, boolean detected, boolean hideFlashes, float screenEffects) {
        return (flashing || detected) && (hideFlashes || screenEffects <= 0);
    }

    // ------------------------------------------------------------------------------------------------ flash safety net

    /** Opposing luminance changes of at least this much (0..1) count as half a flash (WCAG general flash: 10 %). */
    public static final float FLASH_STEP = .1f;
    /** Above this many flashes per second a sheet counts as flashing. */
    public static final float FLASH_RATE = 3f;

    /**
     * Flashes per second of a flipbook from the mean relative luminance of each frame (0..1, alpha included): walks one
     * period of the play mode (LOOP wraps around, PING_PONG goes forth and back, ONCE once), counts luminance changes of
     * at least {@link #FLASH_STEP} in alternating directions; a flash is a pair of opposing changes. TRACK has no
     * period: 0. A simple safety net, not a detector (small areas, saturated colors and tracks escape it).
     */
    public static float flashRate(float[] luminance, float fps, SkyImage.Play play) {
        int n = luminance.length;
        if (n < 2 || play == SkyImage.Play.TRACK || !(fps > 0)) return 0;
        List<Float> seq = new ArrayList<>();
        for (float l : luminance) seq.add(l);
        if (play == SkyImage.Play.PING_PONG) for (int i = n - 2; i >= 1; i--) seq.add(luminance[i]);
        if (play != SkyImage.Play.ONCE) seq.add(seq.get(0));
        int changes = 0, direction = 0;
        float anchor = seq.get(0);
        for (int i = 1; i < seq.size(); i++) {
            float d = seq.get(i) - anchor;
            if (Math.abs(d) >= FLASH_STEP) {
                int dir = d > 0 ? 1 : -1;
                if (dir != direction) changes++;
                direction = dir;
                anchor = seq.get(i);
            } else if ((d > 0 && direction > 0) || (d < 0 && direction < 0)) anchor = seq.get(i);
        }
        int steps = seq.size() - 1;
        double period = steps / (double) fps;
        return (float) ((changes / 2) / period);
    }

    /** Relative luminance (sRGB, linearized) of a color 0..1 per channel, as WCAG defines it. */
    public static float luminance(float r, float g, float b) {
        return (float) (0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b));
    }

    private static double linear(float c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    // ------------------------------------------------------------------------------------------------ geometry

    /**
     * Plane of a picture on the sky: {@code right} and {@code up} perpendicular to {@code center} and to each other.
     * Upright ({@link SkyImage.Up#HORIZON}): up is the zenith projected on the plane (north at the zenith, south at the
     * nadir); along a path: right is {@code motion} projected on the plane (falls back to upright without movement).
     * Then turned by {@code roll} degrees counterclockwise as seen from the ground.
     *
     * @return {@code {right, up}}
     */
    public static double[][] plane(double[] center, SkyImage.Up mode, double[] motion, double roll) {
        double[] right = null, up = null;
        if (mode == SkyImage.Up.PATH && motion != null) {
            double[] t = reject(motion, center);
            if (length(t) > 1e-7) {
                right = SkyObjectsMath.normalize(t);
                up = SkyObjectsMath.cross(right, center);
            }
        }
        if (right == null) {
            up = upright(center);
            right = SkyObjectsMath.cross(center, up);
        }
        double r = Math.toRadians(roll), cr = Math.cos(r), sr = Math.sin(r);
        double[] rr = {right[0] * cr + up[0] * sr, right[1] * cr + up[1] * sr, right[2] * cr + up[2] * sr};
        double[] uu = {up[0] * cr - right[0] * sr, up[1] * cr - right[1] * sr, up[2] * cr - right[2] * sr};
        return new double[][] {rr, uu};
    }

    /** The zenith projected on the plane perpendicular to {@code center} (north at the zenith, south at the nadir), unit. */
    public static double[] upright(double[] center) {
        double[] t = reject(new double[] {0, 1, 0}, center);
        if (length(t) < 1e-6) t = reject(new double[] {0, 0, center[1] > 0 ? -1 : 1}, center);
        return SkyObjectsMath.normalize(t);
    }

    /**
     * Four corners (unit directions) of a picture of {@code width} × {@code height} degrees whose point ({@code pivotX},
     * {@code pivotY}) of the picture (0..1, from the top-left) is at {@code center}, on the plane {@code {right, up}},
     * by gnomonic projection (the quad is flat; the texture keeps its perspective). Order: texture (0,0) top-left, (1,0),
     * (1,1), (0,1). Flat array of 12 values.
     */
    public static double[] corners(double[] center, double[] right, double[] up, double width, double height, double pivotX,
                                   double pivotY) {
        double tw = 2 * Math.tan(Math.toRadians(Math.min(width, 179) / 2)), th = 2 * Math.tan(Math.toRadians(Math.min(height, 179) / 2));
        double[] out = new double[12];
        double[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int k = 0; k < 4; k++) {
            double x = (uv[k][0] - pivotX) * tw, y = (pivotY - uv[k][1]) * th;
            double[] p = SkyObjectsMath.normalize(new double[] {center[0] + right[0] * x + up[0] * y,
                center[1] + right[1] * x + up[1] * y, center[2] + right[2] * x + up[2] * y});
            out[k * 3] = p[0]; out[k * 3 + 1] = p[1]; out[k * 3 + 2] = p[2];
        }
        return out;
    }

    /** Direction of motion between two directions (the second slightly later), or null when they coincide. */
    public static double[] motion(double[] now, double[] later) {
        double[] d = {later[0] - now[0], later[1] - now[1], later[2] - now[2]};
        return length(d) < 1e-9 ? null : d;
    }

    // ------------------------------------------------------------------------------------------------ impostors

    /**
     * Object-to-world rotation of an impostor, column-major 3 × 3 (columns: local X, Y, Z in world). {@code FIXED}: the
     * front (local +Z) is {@code direction(yaw, pitch)} (yaw 0 faces south, 90 west; pitch raises it), the top is the
     * perpendicular toward the zenith, then turned by {@code roll} around the front. {@code PATH} with {@code motion}:
     * the base front is the motion (projected on the plane of the sky at {@code center}) and the base top the zenith
     * made perpendicular to it; yaw, pitch and roll then apply on top in that frame.
     */
    public static double[] orientation(float yaw, float pitch, float roll, SkyImpostor.Heading heading, double[] center,
                                       double[] motion) {
        double[] local = basisFromAngles(yaw, pitch, roll);
        if (heading != SkyImpostor.Heading.PATH || motion == null) return local;
        double[] f = reject(motion, center);
        if (length(f) < 1e-7) return local;
        f = SkyObjectsMath.normalize(f);
        double[] u = reject(new double[] {0, 1, 0}, f);
        if (length(u) < 1e-6) u = reject(new double[] {0, 0, -1}, f);
        u = SkyObjectsMath.normalize(u);
        double[] x = SkyObjectsMath.cross(u, f);
        double[] base = {x[0], x[1], x[2], u[0], u[1], u[2], f[0], f[1], f[2]};
        return multiply(base, local);
    }

    private static double[] basisFromAngles(float yaw, float pitch, float roll) {
        double[] f = SkyObjectsMath.direction(yaw, pitch);
        double[] u = SkyObjectsMath.direction(yaw, pitch + 90.0);
        if (pitch + 90.0 > 90) {
            // direction() clamps the elevation: build the top past the zenith by hand (it leans backward).
            double p = Math.toRadians(pitch + 90.0), y = Math.toRadians(yaw);
            u = new double[] {-Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p)};
        }
        double r = Math.toRadians(roll), cr = Math.cos(r), sr = Math.sin(r);
        double[] fu = SkyObjectsMath.cross(f, u);
        double[] uu = SkyObjectsMath.normalize(new double[] {u[0] * cr + fu[0] * sr, u[1] * cr + fu[1] * sr, u[2] * cr + fu[2] * sr});
        double[] x = SkyObjectsMath.cross(uu, f);
        return new double[] {x[0], x[1], x[2], uu[0], uu[1], uu[2], f[0], f[1], f[2]};
    }

    /**
     * View angles of the camera seen from the object: the camera sits at the origin and the object in direction
     * {@code center}, so the camera is in local direction {@code R^T × (−center)}. Returns {@code {yaw 0..360, pitch}}
     * in degrees, with the convention of {@link SkyObjectsMath#direction}.
     */
    public static double[] viewAngles(double[] rotation, double[] center) {
        double x = -center[0], y = -center[1], z = -center[2];
        double lx = rotation[0] * x + rotation[1] * y + rotation[2] * z;
        double ly = rotation[3] * x + rotation[4] * y + rotation[5] * z;
        double lz = rotation[6] * x + rotation[7] * y + rotation[8] * z;
        double pitch = Math.toDegrees(Math.asin(Math.clamp(ly, -1, 1)));
        double yaw = Math.toDegrees(Math.atan2(-lx, lz));
        if (yaw < 0) yaw += 360;
        if (yaw >= 360) yaw -= 360;
        return new double[] {yaw, pitch};
    }

    /**
     * One weighted view: the cell of the view in a frame's block of views ({@code pitchIndex × storedYaws + yawIndex}),
     * whether the picture is mirrored horizontally, and its weight.
     */
    public record View(int cell, boolean mirrored, float weight) {}

    /** Stored yaw views of a descriptor: {@code yaw} without mirror, {@code yaw / 2 + 1} with it (θ in [0, 180]). */
    public static int storedYaws(int yaw, boolean mirror) { return mirror ? yaw / 2 + 1 : yaw; }

    /**
     * Views to blend for camera angles ({@code yaw} 0..360, {@code pitch}) with {@code yaws} views around the circle and
     * the pitch rows {@code pitches} (ascending); weights sum to 1 (views with weight 0 are left out). NEAREST: the
     * closest; PAIR: the two closest yaws on the closest pitch row; QUAD: bilinear in yaw and pitch. With {@code mirror},
     * a view past 180° is the mirrored picture of 360° − θ.
     */
    public static List<View> views(double yaw, double pitch, int yaws, float[] pitches, boolean mirror, SkyImpostor.ViewBlend mode) {
        double step = 360.0 / yaws, y = ((yaw % 360) + 360) % 360 / step;
        int y0 = (int) Math.floor(y) % yaws, y1 = (y0 + 1) % yaws;
        float ty = (float) (y - Math.floor(y));
        int p0 = 0, p1 = 0;
        float tp = 0;
        if (pitches.length > 1) {
            double p = Math.clamp(pitch, pitches[0], pitches[pitches.length - 1]);
            int j = 0;
            while (j < pitches.length - 2 && p > pitches[j + 1]) j++;
            p0 = j; p1 = j + 1;
            tp = (float) ((p - pitches[j]) / Math.max(1e-6, pitches[j + 1] - pitches[j]));
        }
        List<View> out = new ArrayList<>(4);
        switch (mode) {
            case NEAREST, AUTO -> add(out, ty < .5f ? y0 : y1, tp < .5f ? p0 : p1, yaws, mirror, 1);
            case PAIR -> {
                int row = tp < .5f ? p0 : p1;
                add(out, y0, row, yaws, mirror, 1 - ty);
                add(out, y1, row, yaws, mirror, ty);
            }
            case QUAD -> {
                add(out, y0, p0, yaws, mirror, (1 - ty) * (1 - tp));
                add(out, y1, p0, yaws, mirror, ty * (1 - tp));
                add(out, y0, p1, yaws, mirror, (1 - ty) * tp);
                add(out, y1, p1, yaws, mirror, ty * tp);
            }
        }
        return out;
    }

    private static void add(List<View> out, int yawIndex, int pitchIndex, int yaws, boolean mirror, float weight) {
        if (weight <= 1e-4f) return;
        boolean mirrored = false;
        int k = yawIndex;
        if (mirror && yawIndex > yaws / 2) {
            k = yaws - yawIndex;
            mirrored = true;
        }
        int stored = storedYaws(yaws, mirror);
        int cell = pitchIndex * stored + k;
        for (int i = 0; i < out.size(); i++) {
            View v = out.get(i);
            if (v.cell() == cell && v.mirrored() == mirrored) {
                out.set(i, new View(cell, mirrored, v.weight() + weight));
                return;
            }
        }
        out.add(new View(cell, mirrored, weight));
    }

    // ------------------------------------------------------------------------------------------------ levels of detail

    /** Fraction of a level's threshold below which the next smaller level is chosen. */
    public static final double LOD_DOWN = 0.85;
    /** Fraction of a level's threshold above which the larger level comes back. */
    public static final double LOD_UP = 1.15;
    /** Frames a level change must keep being asked for before it happens. */
    public static final int LOD_FRAMES = 10;

    /**
     * Level of detail with hysteresis: {@code level} (0 = full), the level asked for and for how many frames in a row.
     * Immutable; {@link #step} returns the next state.
     */
    public record Lod(int level, int asked, int frames) {
        /** Starts at the full level. */
        public static final Lod START = new Lod(0, 0, 0);

        /**
         * Next state for an on-screen width of {@code pixels}, with the thresholds {@code below[i]} of levels 1..n
         * ({@code below[0]} unused): one level at a time, down below {@link #LOD_DOWN} × the threshold of the next level,
         * up above {@link #LOD_UP} × the threshold of the current one, after {@link #LOD_FRAMES} frames of asking;
         * {@code ready[i]} false keeps the current level (its texture is not loaded yet).
         */
        public Lod step(double pixels, int[] below, boolean[] ready) {
            int levels = below.length;
            int want = level;
            if (level + 1 < levels && pixels < LOD_DOWN * below[level + 1]) want = level + 1;
            else if (level > 0 && pixels > LOD_UP * below[level]) want = level - 1;
            if (want == level) return new Lod(level, level, 0);
            int count = want == asked ? frames + 1 : 1;
            if (count >= LOD_FRAMES && (ready == null || ready[want])) return new Lod(want, want, 0);
            return new Lod(level, want, count);
        }
    }

    // ------------------------------------------------------------------------------------------------ batching

    /**
     * Runs of consecutive equal keys (texture ids in drawing order): {@code {start, count}} pairs, in order. One draw call
     * per run.
     */
    public static List<int[]> runs(int[] keys) {
        List<int[]> out = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= keys.length; i++) {
            if (i == keys.length || keys[i] != keys[start]) {
                if (i > start) out.add(new int[] {start, i - start});
                start = i;
            }
        }
        return out;
    }

    /** True when the four clip-space corners ({@code x, y, z, w} each) are all outside one side of the view. */
    public static boolean culled(float[] clip) {
        int[] planes = new int[6];
        for (int k = 0; k < 4; k++) {
            float x = clip[k * 4], y = clip[k * 4 + 1], w = clip[k * 4 + 3];
            if (w <= 0) planes[5]++;
            if (x < -w) planes[0]++;
            if (x > w) planes[1]++;
            if (y < -w) planes[2]++;
            if (y > w) planes[3]++;
        }
        for (int p : planes) if (p == 4) return true;
        return false;
    }

    // ------------------------------------------------------------------------------------------------ descriptor

    private static final Pattern TEXTURE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+\\.png");

    /** One level of detail of an impostor: its pages, frame size and the on-screen width below which it is used. */
    public record Level(List<String> textures, int frameW, int frameH, int gutter, int below) {}

    /**
     * Impostor descriptor (format 1), validated. {@code levels.get(0)} is the full level (below 0).
     *
     * @param columns,rows    grid of every page
     * @param yaws            views around the circle
     * @param pitches         pitch rows, ascending, degrees
     * @param mirror          bilateral symmetry: only θ in [0, 180] stored
     * @param frames          animation frames
     * @param fps             animation speed
     * @param play            LOOP, ONCE or PING_PONG
     * @param pivotX,pivotY   point of the picture at the direction (0..1 from the top-left)
     */
    public record Descriptor(List<Level> levels, int columns, int rows, int yaws, float[] pitches, boolean mirror,
                             int frames, float fps, SkyImage.Play play, float pivotX, float pivotY) {
        /** Views of one animation frame. */
        public int viewsPerFrame() { return storedYaws(yaws, mirror) * pitches.length; }
        /** Whole animation frames per page. */
        public int framesPerPage() { return (columns * rows) / viewsPerFrame(); }
        /** Page and cell of a view of an animation frame: {@code {page, cell}}. */
        public int[] locate(int frame, int viewCell) {
            int perPage = framesPerPage();
            return new int[] {frame / perPage, (frame % perPage) * viewsPerFrame() + viewCell};
        }
        /** Width over height of the frames. */
        public float aspect() { return levels.get(0).frameW() / (float) levels.get(0).frameH(); }
    }

    /** Thrown with a clear message when a descriptor is invalid. */
    public static final class DescriptorException extends IllegalArgumentException {
        /** New exception with a message for the log. */
        public DescriptorException(String message) { super(message); }
    }

    /**
     * Parses and validates a descriptor. Every problem is a {@link DescriptorException} whose message names the field:
     * format, pages, frame size, grid, views, frames, play, pivot and levels of detail (same pages and grid, same
     * proportion, threshold at most the level's frame width and decreasing).
     */
    public static Descriptor parse(String json) {
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) throw new DescriptorException("the descriptor must be a JSON object");
            o = e.getAsJsonObject();
        } catch (DescriptorException d) {
            throw d;
        } catch (RuntimeException bad) {
            throw new DescriptorException("invalid JSON: " + bad.getMessage());
        }
        int format = integer(o, "format");
        if (format != 1) throw new DescriptorException("unknown format " + format + " (expected 1)");
        List<String> textures = textures(o, "textures");
        int[] frame = pair(o, "frame", 1, 4096);
        int[] grid = pair(o, "grid", 1, 64);
        int gutter = o.has("gutter") ? integer(o, "gutter") : 0;
        if (gutter < 0 || gutter > 64) throw new DescriptorException("gutter must be 0..64 pixels");
        if (!o.has("views") || !o.get("views").isJsonObject()) throw new DescriptorException("missing object \"views\"");
        JsonObject v = o.getAsJsonObject("views");
        int yaws = integer(v, "yaw");
        if (yaws < 1 || yaws > 256) throw new DescriptorException("views.yaw must be 1..256");
        boolean mirror = v.has("mirror") && bool(v, "mirror");
        if (mirror && yaws % 2 != 0) throw new DescriptorException("views.mirror needs an even views.yaw");
        float[] pitches;
        if (v.has("pitch")) {
            JsonArray a = array(v, "pitch");
            if (a.isEmpty() || a.size() > 32) throw new DescriptorException("views.pitch must list 1..32 angles");
            pitches = new float[a.size()];
            for (int i = 0; i < a.size(); i++) {
                pitches[i] = number(a.get(i), "views.pitch[" + i + "]");
                if (pitches[i] < -90 || pitches[i] > 90) throw new DescriptorException("views.pitch angles must be within ±90");
                if (i > 0 && pitches[i] <= pitches[i - 1]) throw new DescriptorException("views.pitch must be strictly increasing");
            }
        } else pitches = new float[] {0};
        int frames = o.has("frames") ? integer(o, "frames") : 1;
        if (frames < 1 || frames > SkyImage.MAX_FRAMES) throw new DescriptorException("frames must be 1.." + SkyImage.MAX_FRAMES);
        float fps = o.has("fps") ? number(o.get("fps"), "fps") : 12;
        if (!(fps > 0) || fps > SkyImage.MAX_FPS) throw new DescriptorException("fps must be in (0, " + SkyImage.MAX_FPS + "]");
        SkyImage.Play play = SkyImage.Play.LOOP;
        if (o.has("play")) {
            String p = string(o, "play");
            play = switch (p) {
                case "loop" -> SkyImage.Play.LOOP;
                case "once" -> SkyImage.Play.ONCE;
                case "ping_pong" -> SkyImage.Play.PING_PONG;
                default -> throw new DescriptorException("play must be \"loop\", \"once\" or \"ping_pong\", got \"" + p + "\"");
            };
        }
        float px = .5f, py = .5f;
        if (o.has("pivot")) {
            JsonArray a = array(o, "pivot");
            if (a.size() != 2) throw new DescriptorException("pivot must be [x, y]");
            px = number(a.get(0), "pivot[0]");
            py = number(a.get(1), "pivot[1]");
            if (px < 0 || px > 1 || py < 0 || py > 1) throw new DescriptorException("pivot values must be 0..1");
        }
        int views = storedYaws(yaws, mirror) * pitches.length;
        int cells = grid[0] * grid[1];
        if (views > cells)
            throw new DescriptorException("one frame needs " + views + " views but a page has only " + cells + " cells (" + grid[0] + " × " + grid[1] + ")");
        int perPage = cells / views;
        int pages = (frames + perPage - 1) / perPage;
        if (textures.size() != pages)
            throw new DescriptorException(frames + " frames of " + views + " views need " + pages + " page(s) of " + grid[0] + " × " + grid[1]
                + " (whole frames per page: " + perPage + "), but \"textures\" lists " + textures.size());
        List<Level> levels = new ArrayList<>();
        levels.add(new Level(textures, frame[0], frame[1], gutter, 0));
        if (o.has("lod")) {
            JsonArray a = array(o, "lod");
            if (a.size() > 4) throw new DescriptorException("at most 4 levels of detail");
            int previous = Integer.MAX_VALUE;
            for (int i = 0; i < a.size(); i++) {
                if (!a.get(i).isJsonObject()) throw new DescriptorException("lod[" + i + "] must be an object");
                JsonObject l = a.get(i).getAsJsonObject();
                String at = "lod[" + i + "]";
                List<String> lt = textures(l, "textures");
                if (lt.size() != pages) throw new DescriptorException(at + ".textures must list " + pages + " page(s), like the full level");
                int[] lf = pair(l, "frame", 1, 4096);
                if ((long) lf[0] * frame[1] != (long) lf[1] * frame[0])
                    throw new DescriptorException(at + ".frame " + lf[0] + " × " + lf[1] + " must keep the proportion of " + frame[0] + " × " + frame[1]);
                if (lf[0] >= (i == 0 ? frame[0] : levels.get(i).frameW()))
                    throw new DescriptorException(at + ".frame must be smaller than the level before it");
                int lg = l.has("gutter") ? integer(l, "gutter") : gutter;
                if (lg < 0 || lg > 64) throw new DescriptorException(at + ".gutter must be 0..64 pixels");
                int below = integer(l, "below");
                if (below < 1 || below > lf[0])
                    throw new DescriptorException(at + ".below (" + below + ") must be 1.." + lf[0] + " (at most the level's frame width, so the switch does not show)");
                if (below >= previous) throw new DescriptorException(at + ".below must be smaller than the level before it");
                previous = below;
                levels.add(new Level(lt, lf[0], lf[1], lg, below));
            }
        }
        return new Descriptor(List.copyOf(levels), grid[0], grid[1], yaws, pitches, mirror, frames, fps, play, px, py);
    }

    /**
     * Checks a page's real size against the descriptor: {@code columns × (frameW + 2 × gutter)} by
     * {@code rows × (frameH + 2 × gutter)}. Returns null when it matches, else the message.
     */
    public static String checkPage(Descriptor d, Level level, String texture, int width, int height) {
        int w = d.columns() * (level.frameW() + 2 * level.gutter()), h = d.rows() * (level.frameH() + 2 * level.gutter());
        if (width == w && height == h) return null;
        return "page " + texture + " is " + width + " × " + height + " but the descriptor needs " + w + " × " + h
            + " (" + d.columns() + " × " + d.rows() + " cells of " + level.frameW() + " × " + level.frameH() + " plus a gutter of " + level.gutter() + ")";
    }

    private static int integer(JsonObject o, String key) {
        if (!o.has(key)) throw new DescriptorException("missing \"" + key + "\"");
        JsonElement e = o.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new DescriptorException("\"" + key + "\" must be a number");
        double d = e.getAsDouble();
        if (d != Math.rint(d)) throw new DescriptorException("\"" + key + "\" must be a whole number");
        return (int) d;
    }

    private static float number(JsonElement e, String what) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new DescriptorException(what + " must be a number");
        float f = e.getAsFloat();
        if (!Float.isFinite(f)) throw new DescriptorException(what + " must be finite");
        return f;
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw new DescriptorException("\"" + key + "\" must be true or false");
        return e.getAsBoolean();
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new DescriptorException("\"" + key + "\" must be a string");
        return e.getAsString();
    }

    private static JsonArray array(JsonObject o, String key) {
        if (!o.has(key)) throw new DescriptorException("missing \"" + key + "\"");
        if (!o.get(key).isJsonArray()) throw new DescriptorException("\"" + key + "\" must be an array");
        return o.getAsJsonArray(key);
    }

    private static int[] pair(JsonObject o, String key, int min, int max) {
        JsonArray a = array(o, key);
        if (a.size() != 2) throw new DescriptorException("\"" + key + "\" must be [width, height]");
        int[] out = new int[2];
        for (int i = 0; i < 2; i++) {
            float f = number(a.get(i), key + "[" + i + "]");
            if (f != Math.rint(f) || f < min || f > max) throw new DescriptorException("\"" + key + "\" values must be whole numbers in " + min + ".." + max);
            out[i] = (int) f;
        }
        return out;
    }

    private static List<String> textures(JsonObject o, String key) {
        JsonArray a = array(o, key);
        if (a.isEmpty() || a.size() > 16) throw new DescriptorException("\"" + key + "\" must list 1..16 PNG pages");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            JsonElement e = a.get(i);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new DescriptorException(key + "[" + i + "] must be a string");
            String t = e.getAsString();
            if (!TEXTURE.matcher(t).matches()) throw new DescriptorException(key + "[" + i + "] \"" + t + "\" is not a PNG location like \"namespace:textures/x.png\"");
            out.add(t);
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static double[] reject(double[] v, double[] axis) {
        double d = SkyObjectsMath.dot(v, axis);
        return new double[] {v[0] - axis[0] * d, v[1] - axis[1] * d, v[2] - axis[2] * d};
    }

    private static double length(double[] v) { return Math.sqrt(SkyObjectsMath.dot(v, v)); }

    /** Column-major 3 × 3 product {@code a × b}. */
    public static double[] multiply(double[] a, double[] b) {
        double[] out = new double[9];
        for (int c = 0; c < 3; c++) for (int r = 0; r < 3; r++)
            out[c * 3 + r] = a[r] * b[c * 3] + a[3 + r] * b[c * 3 + 1] + a[6 + r] * b[c * 3 + 2];
        return out;
    }
}
