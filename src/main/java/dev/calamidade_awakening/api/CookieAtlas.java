package dev.calamidade_awakening.api;

/**
 * Cell allocator and frame math of the light cookie atlas (0.15.0), pure (no GL, no Minecraft), shared by the client
 * and the tests. Not public API.
 *
 * <p>The atlas is one square texture of {@link #SIDE} texels with mipmaps down to {@link #LEVELS} - 1. It holds
 * {@code (SIDE / BIG)²} big cells of {@link #BIG} texels; a big cell may be split into four small cells of
 * {@link #SMALL} texels. Every cell is aligned to its own size, so every mipmap level up to {@code log2(SMALL)} keeps
 * the cells apart (box filtering never mixes two images). An image is stored at the top-left of the smallest cell it
 * fits in.
 *
 * <p>Fase 3: the cells form a quadtree down to {@link #MIN} (512 → 256 → 128), so an image whose effective resolution
 * was reduced under atlas pressure takes a quarter or a sixteenth of a big cell; 128 cells of 128 fit in one quarter of
 * the atlas. The per-frame cookie budget is no longer derived from the lights cap: see {@link FrameLimits#cookies}.
 */
public final class CookieAtlas {
    /** Side of the atlas, texels. */
    public static final int SIDE = 2048;
    /** Side of a big cell, texels (the largest stored image or sheet). */
    public static final int BIG = 512;
    /** Side of a small cell, texels. */
    public static final int SMALL = 256;
    /** Mipmap levels stored (0..8): the 1-texel level of a small cell is the last one that never mixes cells. */
    public static final int LEVELS = 9;
    /** Big cells per row. */
    public static final int PER_ROW = SIDE / BIG;
    /** Big cells in the atlas. */
    public static final int BIG_CELLS = PER_ROW * PER_ROW;
    /** GPU memory of the atlas with its mipmaps, bytes (RGBA8). */
    public static final long BYTES;

    static {
        long total = 0;
        for (int k = 0; k < LEVELS; k++) total += (long) (SIDE >> k) * (SIDE >> k) * 4;
        BYTES = total;
    }

    /** One allocated cell: top-left texel and side. */
    public record Cell(int x, int y, int size) {
        /** Index of the big cell that holds this one in the standard atlas. */
        public int big() { return (y / BIG) * PER_ROW + x / BIG; }
    }

    /**
     * (0.15.0, fase 2) Largest stored side allowed for high-resolution cookies by the lights cap of the profile: LOW (8
     * lights) 512, i.e. no large atlas; BALANCED (32) 1024; HIGH and ULTRA (64) 2048. Pure.
     */
    public static int largeSide(int lights) { return lights <= 8 ? 512 : lights <= 32 ? 1024 : 2048; }

    /**
     * (0.15.0, fase 3) Largest stored side by the shader tier of the profile, independent of the lights cap (which
     * now grows to 24/64/128/192): LOW 512 (no large atlas), BALANCED 1024, HIGH (and ULTRA, which shows HIGH) 2048.
     */
    public static int largeSide(Quality tier) { return tier == Quality.LOW ? 512 : tier == Quality.BALANCED ? 1024 : 2048; }

    /** Next power of two at or above {@code n} (1 for n below 1). */
    public static int pow2(int n) { return n <= 1 ? 1 : Integer.highestOneBit(n - 1) << 1; }

    /**
     * Stored size of an image of {@code w × h} for a largest side {@code max} (a power of two): halved while above it,
     * then each side rounded up to a power of two (at most {@code max}), so mipmaps and tiling never see padding.
     */
    public static int[] fit(int w, int h, int max) {
        while (w > max || h > max) { w = Math.max(1, w / 2); h = Math.max(1, h / 2); }
        return new int[] {Math.min(pow2(w), max), Math.min(pow2(h), max)};
    }

    /** (0.15.0, fase 3) Side of the smallest cell of the standard atlas: images reduced under pressure go down to it. */
    public static final int MIN = 128;

    private final int bigSize, smallSize, perRow, depth;
    // Quadtree, one array per level (0 = big cells): 0 free, 1 used whole, -1 split into four children.
    private final byte[][] state;
    private long usedTexels;

    /** The standard atlas: 16 cells of 512, each splittable into four of 256, each of those into four of 128. */
    public CookieAtlas() { this(BIG, SMALL, MIN); }

    /**
     * (0.15.0, fase 2) An atlas of {@link #SIDE} texels with cells of {@code bigSize}, each splittable into four of
     * {@code smallSize} (the large atlas: one 2048 or four 1024).
     */
    public CookieAtlas(int bigSize, int smallSize) { this(bigSize, smallSize, smallSize); }

    /**
     * (0.15.0, fase 3) An atlas whose big cells split down to {@code minSize} (powers of two, quadtree: a cell is
     * always aligned to its own size, so the mipmaps never mix two cells up to the level where it is one texel).
     */
    public CookieAtlas(int bigSize, int smallSize, int minSize) {
        if (Integer.bitCount(bigSize) != 1 || Integer.bitCount(minSize) != 1 || minSize > smallSize || smallSize > bigSize)
            throw new IllegalArgumentException("cell sizes must be powers of two, big >= small >= min");
        this.bigSize = bigSize; this.smallSize = smallSize;
        perRow = SIDE / bigSize;
        depth = Integer.numberOfTrailingZeros(bigSize) - Integer.numberOfTrailingZeros(minSize) + 1;
        state = new byte[depth][];
        for (int l = 0; l < depth; l++) state[l] = new byte[(perRow << l) * (perRow << l)];
    }

    /**
     * Smallest cell side of the standard atlas that holds an image of {@code w × h} texels (both at most {@link #BIG}):
     * 128, 256 or 512 (fase 3; before it, 256 or 512).
     */
    public static int cellFor(int w, int h) { return w <= MIN && h <= MIN ? MIN : w <= SMALL && h <= SMALL ? SMALL : BIG; }

    private int sizeAt(int level) { return bigSize >> level; }

    private int rowAt(int level) { return perRow << level; }

    /**
     * Allocates a cell for an image, or null when the atlas has no room of that size. Best fit: a free cell of the
     * right size inside an already split cell first, then the smallest free cell that can be split down to it.
     */
    public Cell allocate(int w, int h) {
        if (w < 1 || h < 1 || w > bigSize || h > bigSize) return null;
        int target = 0;
        while (target + 1 < depth && w <= sizeAt(target + 1) && h <= sizeAt(target + 1)) target++;
        for (int level = target; level >= 0; level--) {
            int row = rowAt(level);
            byte[] st = state[level];
            for (int i = 0; i < st.length; i++) {
                if (st[i] != 0) continue;
                if (level > 0) {
                    int px = (i % row) >> 1, py = (i / row) >> 1;
                    if (state[level - 1][py * rowAt(level - 1) + px] != -1) continue;
                }
                int x = i % row, y = i / row, l = level;
                while (l < target) {
                    state[l][y * rowAt(l) + x] = -1;
                    l++; x <<= 1; y <<= 1;
                }
                state[l][y * rowAt(l) + x] = 1;
                usedTexels += (long) sizeAt(l) * sizeAt(l);
                return new Cell(x * sizeAt(l), y * sizeAt(l), sizeAt(l));
            }
        }
        return null;
    }

    /** Frees a cell returned by {@link #allocate}; a split cell becomes free again when its four children are. */
    public void free(Cell cell) {
        int level = Integer.numberOfTrailingZeros(bigSize) - Integer.numberOfTrailingZeros(cell.size());
        if (level < 0 || level >= depth) return;
        int x = cell.x() / cell.size(), y = cell.y() / cell.size();
        if (state[level][y * rowAt(level) + x] != 1) return;
        state[level][y * rowAt(level) + x] = 0;
        usedTexels -= (long) cell.size() * cell.size();
        while (level > 0) {
            int bx = x & ~1, by = y & ~1, row = rowAt(level);
            byte[] st = state[level];
            if (st[by * row + bx] != 0 || st[by * row + bx + 1] != 0 || st[(by + 1) * row + bx] != 0 || st[(by + 1) * row + bx + 1] != 0)
                return;
            level--; x >>= 1; y >>= 1;
            state[level][y * rowAt(level) + x] = 0;
        }
    }

    /** Frees everything. */
    public void clear() {
        for (byte[] st : state) java.util.Arrays.fill(st, (byte) 0);
        usedTexels = 0;
    }

    /** Cells in use, in units of {@code smallSize²} rounded up (a big cell counts 4; fase 3: a 128 cell a quarter). */
    public int usedQuarters() {
        long unit = (long) smallSize * smallSize;
        return (int) ((usedTexels + unit - 1) / unit);
    }

    /** (fase 3) Texels in use and the occupation 0..1 of the atlas. */
    public long usedTexels() { return usedTexels; }

    public float occupancy() { return usedTexels / (float) ((long) SIDE * SIDE); }

    /** Halvings that bring an image of {@code w × h} within {@link #BIG} on both sides. */
    public static int halvings(int w, int h) {
        int n = 0;
        while ((w >> n) > BIG || (h >> n) > BIG) n++;
        return n;
    }

    /**
     * Rectangle of a sheet frame in atlas units (u0, v0, du, dv), top-left origin, for an image of {@code w × h}
     * texels stored at {@code cell}. Frames run left to right, then top to bottom.
     */
    public static float[] frameRect(Cell cell, int w, int h, int columns, int rows, int frame, float[] out) {
        int f = Math.floorMod(frame, Math.max(1, columns * rows));
        float fw = (float) w / columns, fh = (float) h / rows;
        out[0] = (cell.x() + (f % columns) * fw) / SIDE;
        out[1] = (cell.y() + (f / columns) * fh) / SIDE;
        out[2] = fw / SIDE;
        out[3] = fh / SIDE;
        return out;
    }

    /**
     * Highest mipmap level a frame of {@code fw × fh} texels may be read at without mixing its neighbours: two levels
     * under the one where the frame is a single texel, within the stored levels.
     */
    public static float maxLod(float fw, float fh) {
        float side = Math.max(1, Math.min(fw, fh));
        return Math.clamp((float) (Math.log(side) / Math.log(2)) - 1, 0, LEVELS - 1);
    }

    /**
     * Legacy policy (0.15.0 fases 1 e 2): a quarter of the lights cap, between 2 and 16. Since fase 3 the cookie
     * budget is its own cap ({@link FrameLimits#cookies}, configurable per profile); this is only the default of the
     * pre-fase-3 constructors of {@link FrameLimits} and {@link FrameLimits#of}. Pure.
     */
    public static int textured(int lights) { return Math.clamp(lights / 4, Math.min(2, lights), 16); }
}
