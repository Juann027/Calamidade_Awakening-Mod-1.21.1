package dev.calamidade_awakening.api;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Coverage-first light budget (0.15.0, fase 4), pure (no GL, no Minecraft), shared by the client and the tests. Not
 * public API.
 *
 * <p>Fase 3 kept, over a cap, the brightest lights of the highest tier. With many similar lights (an arena of runes)
 * that kept the ones nearest the camera and dropped whole regions of the scene. Fase 4 keeps the tier order and the
 * hysteresis, and adds two things:
 * <ul>
 *   <li><b>Spatial spread</b> ({@link #select}): inside one tier, every light already kept in the same world cell of
 *       {@link #CELL} blocks halves the score of the next candidate of that cell ({@link #SPREAD}). The selection still
 *       starts with the most important light; it just stops piling the whole budget onto one corner. Cells are fixed in
 *       the world, not on the screen, so moving the camera does not reshuffle them; a light kept last frame keeps its
 *       {@link LightBudget#STICKY} bonus. A higher tier is never given up for a better spread: CRITICAL lights all go
 *       before any IMPORTANT one.</li>
 *   <li><b>Density admission</b> ({@link #admit}): the real per-pixel cost of the tiled lights pass is the number of
 *       lights per screen tile, not the total. Lights left out by the cap are admitted, in priority order, while every
 *       tile they reach holds fewer than a per-tile density ({@link #tileLights}, {@link #tileCookies}), up to a hard
 *       ceiling. Spread lights come back; piled lights stay out. The per-tile limit is the density or the most loaded
 *       tile the cap itself produced this frame, whichever is larger: the worst per-pixel cost of the frame never
 *       rises; only emptier screen regions are filled.</li>
 * </ul>
 */
public final class CoverageBudget {
    private CoverageBudget() {}

    /** Side of the world cells of the spatial spread, blocks. */
    public static final double CELL = 8;
    /** Score factor per light already kept in the same cell and tier. */
    public static final float SPREAD = .5f;
    /** Most lights resolved per frame before the selection (CPU ceiling; the highest-priority instances go first). */
    public static final int MAX_RESOLVED = 4 * FrameLimits.MAX_LIGHTS;

    /** Lights per screen tile the density admission fills up to, from the lights cap: a third, 4..64. */
    public static int tileLights(int lightsCap) { return Math.clamp(lightsCap / 3, 4, 64); }

    /** Sampled cookies per screen tile the density admission fills up to, from the cookie cap: a quarter, 2..32. */
    public static int tileCookies(int cookieCap) { return Math.clamp(cookieCap / 4, 2, 32); }

    /** World cell of a point (blocks), as one long. */
    public static long cell(double x, double y, double z) {
        long cx = (long) Math.floor(x / CELL), cy = (long) Math.floor(y / CELL), cz = (long) Math.floor(z / CELL);
        return (cx & 0x1FFFFF) << 42 | (cy & 0x1FFFFF) << 21 | (cz & 0x1FFFFF);
    }

    /**
     * {@link LightBudget#select} with spatial spread: tier first (always), then score ×{@link LightBudget#STICKY} for
     * lights kept last frame, ×{@link #SPREAD} for each light of the same tier already kept in the same cell. Null
     * {@code cell} is exactly {@link LightBudget#select}. Deterministic: ties keep the input order.
     */
    public static boolean[] select(int n, int[] tier, float[] score, boolean[] previous, long[] cell, int max) {
        if (cell == null || max >= n || max <= 0) return LightBudget.select(n, tier, score, previous, max);
        boolean[] keep = new boolean[n];
        float[] base = new float[n];
        for (int i = 0; i < n; i++) base[i] = score[i] * (previous != null && previous[i] ? LightBudget.STICKY : 1);
        int taken = 0;
        int top = 0;
        for (int t : tier) top = Math.max(top, t);
        for (int t = top; t >= 0 && taken < max; t--) {
            // Lazy greedy: an entry stamped with an older cell count is re-scored and pushed back.
            Map<Long, Integer> count = new HashMap<>();
            PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> a[0] != b[0] ? Double.compare(b[0], a[0])
                : Double.compare(a[1], b[1]));
            for (int i = 0; i < n; i++) if (tier[i] == t) queue.add(new double[] {base[i], i, 0});
            while (!queue.isEmpty() && taken < max) {
                double[] e = queue.poll();
                int i = (int) e[1];
                int c = count.getOrDefault(cell[i], 0);
                if (c != (int) e[2]) {
                    queue.add(new double[] {base[i] * Math.pow(SPREAD, c), i, c});
                    continue;
                }
                keep[i] = true;
                taken++;
                count.put(cell[i], c + 1);
            }
        }
        return keep;
    }

    /**
     * Density admission: adds to {@code keep}, in priority order (tier, then the lights admitted last frame, then score),
     * candidates whose tile rectangle ({@code rect[i]}: x0, y0, x1, y1 inclusive, null = every tile)
     * has fewer than the per-tile limit in every tile ({@code +1} for a light admitted last frame, so a light at the
     * edge of a tile does not blink), until {@code ceiling} lights are kept in total. The limit is the larger of
     * {@code perTile} and the most loaded tile the cap already accepted this frame: the admission fills the emptier
     * tiles and never raises the worst per-pixel cost of the frame. {@code tiles} gets the final count per tile. Returns
     * how many were admitted.
     */
    public static int admit(int n, boolean[] keep, int[] tier, float[] score, boolean[] previous, int[][] rect,
                            int perTile, int ceiling, int[] tiles) {
        return admit(n, keep, tier, score, previous, rect, perTile, ceiling, tiles, null, 0);
    }

    /** Frames a light must fit in a row before the density admits it ({@link #admit} with streaks). */
    public static final int SETTLE = 8;

    /**
     * {@link #admit} with temporal hysteresis: a light not admitted last frame enters only after it fits for {@code need}
     * frames in a row ({@code streak[i]}, kept by the caller across frames: incremented while it fits, zeroed otherwise);
     * a light admitted last frame stays while its tiles hold at most a quarter (at least one) above the limit. A turning
     * camera then moves lights across tile edges without popping them in and out. Null {@code streak}: no delay.
     */
    public static int admit(int n, boolean[] keep, int[] tier, float[] score, boolean[] previous, int[][] rect,
                            int perTile, int ceiling, int[] tiles, int[] streak, int need) {
        java.util.Arrays.fill(tiles, 0);
        int kept = 0;
        for (int i = 0; i < n; i++) if (keep[i]) {
            kept++;
            mark(rect[i], tiles, 1);
        }
        int worst = 0;
        for (int t : tiles) worst = Math.max(worst, t);
        perTile = Math.max(perTile, worst);
        if (kept >= ceiling || perTile <= 0) return 0;
        Integer[] order = new Integer[n];
        int m = 0;
        for (int i = 0; i < n; i++) if (!keep[i]) order[m++] = i;
        Integer[] rest = java.util.Arrays.copyOf(order, m);
        java.util.Arrays.sort(rest, (a, b) -> {
            if (tier[a] != tier[b]) return Integer.compare(tier[b], tier[a]);
            // Hysteresis: a light admitted last frame keeps its place while it still fits (no churn when the camera turns).
            boolean pa = previous != null && previous[a], pb = previous != null && previous[b];
            if (pa != pb) return pa ? -1 : 1;
            float sa = score[a] * (previous != null && previous[a] ? LightBudget.STICKY : 1);
            float sb = score[b] * (previous != null && previous[b] ? LightBudget.STICKY : 1);
            if (sa != sb) return Float.compare(sb, sa);
            return Integer.compare(a, b);
        });
        int admitted = 0;
        for (int i : rest) {
            if (kept >= ceiling) break;
            boolean was = previous != null && previous[i];
            int limit = perTile + (was ? Math.max(1, perTile / 4) : 0);
            if (!fits(rect[i], tiles, limit)) {
                if (streak != null) streak[i] = 0;
                continue;
            }
            if (streak != null && !was && ++streak[i] < need) continue;
            keep[i] = true;
            mark(rect[i], tiles, 1);
            kept++;
            admitted++;
        }
        return admitted;
    }

    /** Distinct cells among the marked entries ({@code mask} null = all): the spatial spread F7 and the tests report. */
    public static int cells(long[] cell, boolean[] mask) {
        java.util.Set<Long> set = new java.util.HashSet<>();
        for (int i = 0; i < cell.length; i++) if (mask == null || mask[i]) set.add(cell[i]);
        return set.size();
    }

    private static boolean fits(int[] r, int[] tiles, int limit) {
        if (r == null) {
            for (int t : tiles) if (t >= limit) return false;
            return true;
        }
        for (int ty = r[1]; ty <= r[3]; ty++) for (int tx = r[0]; tx <= r[2]; tx++)
            if (tiles[ty * LightBudget.TILES_X + tx] >= limit) return false;
        return true;
    }

    private static void mark(int[] r, int[] tiles, int d) {
        if (r == null) {
            for (int t = 0; t < tiles.length; t++) tiles[t] += d;
            return;
        }
        for (int ty = r[1]; ty <= r[3]; ty++) for (int tx = r[0]; tx <= r[2]; tx++) tiles[ty * LightBudget.TILES_X + tx] += d;
    }
}
