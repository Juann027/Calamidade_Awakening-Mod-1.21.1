package dev.calamidade_awakening.api;

/**
 * Adaptive light budget of the lights pass (0.15.0, fase 3), pure (no GL, no Minecraft), shared by the client and the
 * tests. Not public API.
 *
 * <p><b>Selection.</b> When a frame has more lights than {@link FrameLimits#lights} or more cookie lights than
 * {@link FrameLimits#cookies}, the kept ones are chosen by tier first ({@link #tier}: the light priority, with a NORMAL
 * light of a HERO effect counted as IMPORTANT), then by score ({@link #score}: brightness × screen coverage). A light
 * kept in the previous frame has its score multiplied by {@link #STICKY}, so two lights of similar score do not swap
 * every frame (no blinking). Under the cap nothing changes: every light is kept, in any order (the composition of the
 * pass does not depend on the order).
 *
 * <p><b>Effective resolution.</b> {@link #demand} estimates the side of the stored image a light needs from its size on
 * the screen; {@link Resolution} turns the demand of every light that shares an image into the stored side, with
 * hysteresis and a cooldown so the image is never re-uploaded back and forth.
 *
 * <p><b>Screen tiles.</b> {@link #tiles} projects a light's bounding sphere onto a {@link #TILES_X} × {@link #TILES_Y}
 * grid, conservatively; the shader only evaluates, in each pixel, the lights whose rectangle covers its tile.
 */
public final class LightBudget {
    private LightBudget() {}

    /** Score multiplier of a light kept in the previous frame (hysteresis of the selection). */
    public static final float STICKY = 1.5f;
    /** Contribution below which a light lights nothing visible (under half an 8-bit step). */
    public static final float INVISIBLE = 1f / 512f;
    /** Screen tile grid of the lights pass. */
    public static final int TILES_X = 16, TILES_Y = 9, TILES = TILES_X * TILES_Y;
    /** Light rows one tile mask can address: 8 floats of 24 exact bits. */
    public static final int MASK_BITS = 24, MASK_WORDS = 8;
    /** Below this many lights the pass keeps the plain loop of 0.14 (the tiles would cost more than they save). */
    public static final int TILED_FROM = 17;
    /** Cookies in one tile above which the frame interpolation of non-important cookies is dropped (per-pixel cost). */
    public static final int PIXEL_PRESSURE = 12;

    /**
     * Tier of a light: 0 DECORATIVE, 1 NORMAL, 2 IMPORTANT, 3 CRITICAL. A NORMAL light of a HERO effect is IMPORTANT
     * (the effect priority of 0.9 keeps meaning what it meant: HERO lights before the others).
     */
    public static int tier(dev.calamidade_awakening.api.v1.Light.Priority priority, boolean hero) {
        int t = priority.ordinal();
        return hero && t == 1 ? 2 : t;
    }

    /**
     * Visible contribution of a light, comparable between lights: {@code gain} (intensity × weight × brightest color
     * channel, or the strength of an override) times its screen coverage, the square of the angular radius of its
     * bounding sphere, at most 1 (the camera inside the sphere).
     */
    public static float score(float gain, double boundRadius, double distance) {
        double d = Math.max(distance, 1e-3);
        double angular = Math.min(1, boundRadius / d);
        return (float) (Math.max(0, gain) * angular * angular);
    }

    /**
     * Chooses at most {@code max} of {@code n} candidates: higher tier first, then higher score (×{@link #STICKY} when
     * {@code previous[i]}). Ties keep the input order. Returns a mask of the kept candidates. Allocation free except
     * the result; O(n log n).
     */
    public static boolean[] select(int n, int[] tier, float[] score, boolean[] previous, int max) {
        boolean[] keep = new boolean[n];
        if (max >= n) {
            java.util.Arrays.fill(keep, true);
            return keep;
        }
        if (max <= 0) return keep;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> {
            if (tier[a] != tier[b]) return Integer.compare(tier[b], tier[a]);
            float sa = score[a] * (previous != null && previous[a] ? STICKY : 1), sb = score[b] * (previous != null && previous[b] ? STICKY : 1);
            if (sa != sb) return Float.compare(sb, sa);
            return Integer.compare(a, b);
        });
        for (int k = 0; k < max; k++) keep[order[k]] = true;
        return keep;
    }

    /**
     * Height on the screen, in pixels, of a sphere of {@code radius} blocks seen at {@code distance} blocks with a
     * vertical field of view of {@code fovDegrees} on a framebuffer {@code screenHeight} pixels tall; the whole height
     * when the camera is inside.
     */
    public static float footprint(double radius, double distance, int screenHeight, double fovDegrees) {
        if (distance <= radius) return screenHeight;
        double t = Math.tan(Math.toRadians(Math.clamp(fovDegrees, 10, 170)) * .5);
        return (float) Math.min(screenHeight, 2 * radius / distance / (2 * t) * screenHeight);
    }

    /**
     * Stored side an image needs for a light that covers {@code footprint} pixels: one texel per pixel of one frame
     * of the sheet (the largest of columns and rows frames share the side), times 2 for IMPORTANT and CRITICAL, half
     * for DECORATIVE; a power of two between {@link CookieAtlas#MIN} and 2048. A side above the source image is never
     * stored (the decoder only reduces): this is an upper bound of what is useful.
     */
    public static int demand(float footprint, int columns, int rows, int tier) {
        double need = footprint * Math.max(1, Math.max(columns, rows));
        if (tier >= 2) need *= 2;
        else if (tier == 0) need *= .5;
        return Math.clamp(CookieAtlas.pow2((int) Math.ceil(Math.min(need, 4096))), CookieAtlas.MIN, 2048);
    }

    /** Smallest stored side of the standard atlas a tier may go down to under pressure: CRITICAL 256, the rest 128. */
    public static int floor(int tier) { return tier >= 3 ? 256 : CookieAtlas.MIN; }

    /**
     * Hysteresis of the stored side of one image (the standard version, 128..512). {@link #update} takes the side
     * wanted this frame and returns true when the stored side should change now: down after {@link #DOWN_FRAMES}
     * frames in a row of a smaller wish, up after {@link #UP_FRAMES}, never twice within {@link #COOLDOWN} frames.
     * Mutable, render thread only.
     */
    public static final class Resolution {
        public static final int DOWN_FRAMES = 30, UP_FRAMES = 240, COOLDOWN = 600;
        private int side, wish, frames, cooldown;

        public Resolution(int side) { this.side = side; wish = side; }

        /** Stored side now. */
        public int side() { return side; }

        /** Side the next change goes to (equal to {@link #side} when no change is due). */
        public int wish() { return wish; }

        /** One frame with the side wanted now; true when the stored side should change to {@link #wish}. */
        public boolean update(int wanted) {
            if (cooldown > 0) cooldown--;
            if (wanted == side) {
                frames = 0;
                wish = side;
                return false;
            }
            if (wanted != wish) { wish = wanted; frames = 0; }
            frames++;
            return cooldown == 0 && frames >= (wanted < side ? DOWN_FRAMES : UP_FRAMES);
        }

        /** The change to {@link #wish} happened (the new version is uploaded). */
        public void changed(int newSide) {
            side = newSide;
            wish = newSide;
            frames = 0;
            cooldown = COOLDOWN;
        }

        /** The change could not happen (no room): try again after the cooldown. */
        public void postpone() {
            frames = 0;
            cooldown = COOLDOWN / 4;
        }
    }

    /**
     * Conservative tile rectangle of a sphere ({@code x, y, z} relative to the camera, {@code radius} blocks) under the
     * column-major view-projection matrix {@code m} (projection × model-view of the camera-relative world): the corners
     * of its bounding box are projected; a box that crosses the camera plane covers every tile, one wholly behind none. Writes {@code x0, y0, x1,
     * y1} (inclusive, y from the bottom) into {@code out} and returns false when the sphere is off screen.
     */
    public static boolean tiles(float[] m, double x, double y, double z, double radius, int[] out) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        int behind = 0;
        boolean crosses = false;
        for (int c = 0; c < 8; c++) {
            double px = x + ((c & 1) == 0 ? -radius : radius), py = y + ((c & 2) == 0 ? -radius : radius),
                pz = z + ((c & 4) == 0 ? -radius : radius);
            double cw = m[3] * px + m[7] * py + m[11] * pz + m[15];
            if (cw <= 1e-3) {
                behind++;
                crosses = true;
                continue;
            }
            double cx = (m[0] * px + m[4] * py + m[8] * pz + m[12]) / cw, cy = (m[1] * px + m[5] * py + m[9] * pz + m[13]) / cw;
            minX = Math.min(minX, cx); maxX = Math.max(maxX, cx);
            minY = Math.min(minY, cy); maxY = Math.max(maxY, cy);
        }
        if (behind == 8) return false;
        if (crosses) {
            // The box crosses the camera plane: no safe projection, every tile.
            out[0] = 0; out[1] = 0; out[2] = TILES_X - 1; out[3] = TILES_Y - 1;
            return true;
        }
        if (maxX < -1 || minX > 1 || maxY < -1 || minY > 1) return false;
        out[0] = tile(minX, TILES_X);
        out[1] = tile(minY, TILES_Y);
        out[2] = tile(maxX, TILES_X);
        out[3] = tile(maxY, TILES_Y);
        return true;
    }

    private static int tile(double ndc, int count) {
        return Math.clamp((int) Math.floor((ndc * .5 + .5) * count), 0, count - 1);
    }

    /** Sets bit {@code light} of the masks of every tile in {@code rect}: {@code masks} holds {@link #MASK_WORDS} floats per tile. */
    public static void mark(float[] masks, int[] rect, int light) {
        int word = light / MASK_BITS, bit = 1 << (light % MASK_BITS);
        for (int ty = rect[1]; ty <= rect[3]; ty++) for (int tx = rect[0]; tx <= rect[2]; tx++) {
            int k = (ty * TILES_X + tx) * MASK_WORDS + word;
            masks[k] = (float) ((int) masks[k] | bit);
        }
    }
}
