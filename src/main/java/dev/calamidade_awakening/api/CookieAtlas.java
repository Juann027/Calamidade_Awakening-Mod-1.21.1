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
        /** Index of the big cell that holds this one. */
        public int big() { return (y / BIG) * PER_ROW + x / BIG; }
    }

    // 0 free, -1 split into small cells (see small[]), 1 used whole
    private final int[] bigState = new int[BIG_CELLS];
    private final boolean[][] small = new boolean[BIG_CELLS][4];

    /** Smallest cell side that holds an image of {@code w × h} texels (both at most {@link #BIG}). */
    public static int cellFor(int w, int h) { return w <= SMALL && h <= SMALL ? SMALL : BIG; }

    /** Allocates a cell for an image, or null when the atlas has no room of that size. */
    public Cell allocate(int w, int h) {
        if (w < 1 || h < 1 || w > BIG || h > BIG) return null;
        if (cellFor(w, h) == SMALL) {
            // First a free quarter of an already split cell, then split a free big cell.
            for (int i = 0; i < BIG_CELLS; i++) if (bigState[i] == -1) {
                for (int q = 0; q < 4; q++) if (!small[i][q]) { small[i][q] = true; return smallCell(i, q); }
            }
            for (int i = 0; i < BIG_CELLS; i++) if (bigState[i] == 0) {
                bigState[i] = -1;
                small[i][0] = true;
                return smallCell(i, 0);
            }
            return null;
        }
        for (int i = 0; i < BIG_CELLS; i++) if (bigState[i] == 0) {
            bigState[i] = 1;
            return new Cell((i % PER_ROW) * BIG, (i / PER_ROW) * BIG, BIG);
        }
        return null;
    }

    private static Cell smallCell(int big, int q) {
        return new Cell((big % PER_ROW) * BIG + (q & 1) * SMALL, (big / PER_ROW) * BIG + (q >> 1) * SMALL, SMALL);
    }

    /** Frees a cell returned by {@link #allocate}; a split big cell becomes free when its four quarters are. */
    public void free(Cell cell) {
        int i = cell.big();
        if (cell.size() == BIG) { bigState[i] = 0; return; }
        int q = ((cell.x() % BIG) / SMALL) + 2 * ((cell.y() % BIG) / SMALL);
        small[i][q] = false;
        if (!small[i][0] && !small[i][1] && !small[i][2] && !small[i][3]) bigState[i] = 0;
    }

    /** Frees everything. */
    public void clear() {
        java.util.Arrays.fill(bigState, 0);
        for (boolean[] q : small) java.util.Arrays.fill(q, false);
    }

    /** Cells in use, in small-cell units (a big cell counts 4). */
    public int usedQuarters() {
        int n = 0;
        for (int i = 0; i < BIG_CELLS; i++) {
            if (bigState[i] == 1) n += 4;
            else if (bigState[i] == -1) for (boolean b : small[i]) if (b) n++;
        }
        return n;
    }

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
     * Cookie lights that may sample their image per frame for a lights cap (8/32/64 by profile): a quarter of it,
     * between 2 and 16. The others use their fallback. Pure, so the tests and F7 agree.
     */
    public static int textured(int lights) { return Math.clamp(lights / 4, Math.min(2, lights), 16); }
}
