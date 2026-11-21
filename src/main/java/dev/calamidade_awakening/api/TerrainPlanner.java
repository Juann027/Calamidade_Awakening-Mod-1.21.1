package dev.calamidade_awakening.api;

import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.TerrainPalette;
import dev.calamidade_awakening.api.v1.TerrainShape;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Edit plan of the Terrain API (0.11.0): from a {@link TerrainEdit}, the origin, the orientation and the seed, the
 * ordered list of positions and target actions, split in batches per chunk section. Pure Java and free of world reads,
 * so the server runs it on a worker thread during the scene; the world-dependent decisions (solid, fluid, protected)
 * happen at the commit, on the server thread.
 *
 * <p>Rules: a block is carved when the shape distance at its center is &lt;= 0. With a shell of thickness {@code t},
 * every non-carved block that touches carved space through a face is in the shell (so no fluid or gravity block is left
 * touching the crater), and so is every block with distance in (0, t]. Shell bands: carved space right above →
 * {@link TerrainPlan#FLOOR}; other face contact → {@link TerrainPlan#WALL}; deeper layers → FLOOR when carved space is
 * within {@code t} blocks above in the same column, {@link TerrainPlan#RIM} at or above the origin level, WALL below it.
 * Without a shell the touching blocks get {@link TerrainPlan#SEAL}. The fill overrides all of that, and structure
 * blocks override everything.
 */
public final class TerrainPlanner {
    /** Largest number of cells the planner scans for one shape (about 256³); larger requests are refused. */
    public static final long MAX_CELLS = 16_777_216L;

    private TerrainPlanner() {}

    /**
     * Blocks of a structure template, in template coordinates (0 .. size − 1), with an index into a palette that the
     * caller keeps (the server's block states, already turned by the rotation).
     *
     * @param sizeX   template size along x, blocks
     * @param sizeY   template size along y, blocks
     * @param sizeZ   template size along z, blocks
     * @param x       x of each block
     * @param y       y of each block
     * @param z       z of each block
     * @param palette palette index of each block, 0..32767
     */
    public record Blocks(int sizeX, int sizeY, int sizeZ, int[] x, int[] y, int[] z, int[] palette) {
        public Blocks {
            if (sizeX < 1 || sizeY < 1 || sizeZ < 1) throw new IllegalArgumentException("Empty structure size");
            if (x.length != y.length || x.length != z.length || x.length != palette.length)
                throw new IllegalArgumentException("Structure arrays of different lengths");
        }
    }

    /**
     * What to plan.
     *
     * @param edit      the terrain edit (shape, shell, fill)
     * @param ox        origin x (the instance center), blocks
     * @param oy        origin y, blocks
     * @param oz        origin z, blocks
     * @param yaw       instance yaw, degrees
     * @param pitch     instance pitch, degrees
     * @param seed      instance seed
     * @param minY      lowest buildable block y (inclusive)
     * @param maxY      highest buildable block y (exclusive)
     * @param structure structure blocks, or null
     */
    public record Request(TerrainEdit edit, double ox, double oy, double oz, float yaw, float pitch, long seed,
                          int minY, int maxY, Blocks structure) {
        public Request {
            Objects.requireNonNull(edit, "edit");
            if (!Double.isFinite(ox) || !Double.isFinite(oy) || !Double.isFinite(oz)) throw new IllegalArgumentException("Non-finite origin");
            if (maxY <= minY) throw new IllegalArgumentException("Empty build height");
        }
    }

    /** Structure rotation in 90° clockwise steps (0..3) for an instance yaw: yaw 90 (facing west) is one step. */
    public static int rotation(float yaw) {
        return Math.floorMod(Math.round(Basis.wrapYaw(yaw) / 90f), 4);
    }

    /** Template x after a rotation, inside the rotated box (sizes unrotated). */
    public static int rotateX(int x, int z, int sizeX, int sizeZ, int rotation) {
        return switch (rotation) {
            case 1 -> sizeZ - 1 - z;
            case 2 -> sizeX - 1 - x;
            case 3 -> z;
            default -> x;
        };
    }

    /** Template z after a rotation, inside the rotated box (sizes unrotated). */
    public static int rotateZ(int x, int z, int sizeX, int sizeZ, int rotation) {
        return switch (rotation) {
            case 1 -> x;
            case 2 -> sizeZ - 1 - z;
            case 3 -> sizeX - 1 - x;
            default -> z;
        };
    }

    /**
     * Lowest corner (world block x, y, z) of a structure placed by an edit at this origin and yaw: the template's
     * rotated horizontal center on the origin plus the edit's structure offset, its lowest layer on that level.
     */
    public static int[] structureCorner(TerrainEdit edit, double ox, double oy, double oz, float yaw, int sizeX, int sizeZ) {
        int rotation = rotation(yaw);
        Basis snapped = Basis.of(rotation * 90f, 0);
        double ax = ox + snapped.worldX(edit.structureX(), edit.structureY(), edit.structureZ());
        double ay = oy + snapped.worldY(edit.structureX(), edit.structureY(), edit.structureZ());
        double az = oz + snapped.worldZ(edit.structureX(), edit.structureY(), edit.structureZ());
        int rx = (rotation & 1) == 1 ? sizeZ : sizeX, rz = (rotation & 1) == 1 ? sizeX : sizeZ;
        return new int[] {(int) Math.floor(ax) - rx / 2, (int) Math.floor(ay), (int) Math.floor(az) - rz / 2};
    }

    /** Plans the edit; throws {@link IllegalArgumentException} when the shape would scan more than {@link #MAX_CELLS}. */
    public static TerrainPlan plan(Request request) {
        TerrainEdit edit = request.edit();
        TerrainShape shape = edit.shape();
        TerrainPalette palette = edit.palette();
        Basis basis = Basis.of(request.yaw(), request.pitch());
        int t = palette == null ? 0 : edit.thickness();
        double amp = shape.roughness();
        double extent = shape.extent();
        double ox = request.ox(), oy = request.oy(), oz = request.oz();
        long seed = request.seed();
        int margin = t + 1;
        int x0 = (int) Math.floor(ox - extent) - margin, x1 = (int) Math.floor(ox + extent) + margin;
        int z0 = (int) Math.floor(oz - extent) - margin, z1 = (int) Math.floor(oz + extent) + margin;
        int y0 = Math.max(request.minY(), (int) Math.floor(oy - extent) - margin);
        int y1 = Math.min(request.maxY() - 1, (int) Math.floor(oy + extent) + margin);
        int nx = x1 - x0 + 1, nz = z1 - z0 + 1, ny = Math.max(0, y1 - y0 + 1);
        long cells = (long) nx * ny * nz;
        if (cells > MAX_CELLS) throw new IllegalArgumentException("Terrain shape too large to plan: " + cells + " cells");

        // Pass 1: carved (1), within the shell distance (2) or outside (0). The noise is skipped when it cannot matter.
        byte[] cls = new byte[(int) cells];
        for (int iy = 0; iy < ny; iy++) {
            double wy = y0 + iy + .5 - oy;
            for (int iz = 0; iz < nz; iz++) {
                double wz = z0 + iz + .5 - oz;
                for (int ix = 0; ix < nx; ix++) {
                    double wx = x0 + ix + .5 - ox;
                    double lx = basis.localX(wx, wy, wz), ly = basis.localY(wx, wy, wz), lz = basis.localZ(wx, wy, wz);
                    int i = (iy * nz + iz) * nx + ix;
                    double base = shape.baseDistance(lx, ly, lz);
                    if (base - amp > t) continue;
                    double d = base + amp <= 0 ? -1 : (amp == 0 ? base : base - amp * shape.edge(lx, ly, lz, seed));
                    cls[i] = d <= 0 ? (byte) 1 : d <= t ? (byte) 2 : (byte) 0;
                }
            }
        }

        // Fill and structure override the grid; kept by packed position (they may lie outside it).
        Map<Long, Integer> overrides = new HashMap<>();
        TerrainEdit.Fill fill = edit.fill();
        if (fill != null) {
            double cx = ox + basis.worldX(fill.x(), fill.y(), fill.z());
            double cy = oy + basis.worldY(fill.x(), fill.y(), fill.z());
            double cz = oz + basis.worldZ(fill.x(), fill.y(), fill.z());
            double r = fill.radius();
            for (int y = Math.max(request.minY(), (int) Math.floor(cy - r)); y <= Math.min(request.maxY() - 1, (int) Math.floor(cy + r)); y++)
                for (int z = (int) Math.floor(cz - r); z <= (int) Math.floor(cz + r); z++)
                    for (int x = (int) Math.floor(cx - r); x <= (int) Math.floor(cx + r); x++) {
                        double wx = x + .5 - ox, wy = y + .5 - oy, wz = z + .5 - oz;
                        if (fill.contains(basis.localX(wx, wy, wz), basis.localY(wx, wy, wz), basis.localZ(wx, wy, wz)))
                            overrides.put(pack(x, y, z), TerrainPlan.FILL << 16);
                    }
        }
        int rotation = 0;
        int[] corner = {0, 0, 0};
        Blocks blocks = request.structure();
        if (blocks != null) {
            rotation = rotation(request.yaw());
            corner = structureCorner(edit, ox, oy, oz, request.yaw(), blocks.sizeX(), blocks.sizeZ());
            for (int i = 0; i < blocks.x().length; i++) {
                int x = corner[0] + rotateX(blocks.x()[i], blocks.z()[i], blocks.sizeX(), blocks.sizeZ(), rotation);
                int y = corner[1] + blocks.y()[i];
                int z = corner[2] + rotateZ(blocks.x()[i], blocks.z()[i], blocks.sizeX(), blocks.sizeZ(), rotation);
                if (y < request.minY() || y >= request.maxY()) continue;
                overrides.put(pack(x, y, z), (TerrainPlan.STRUCTURE << 16) | (blocks.palette()[i] & 0xFFFF));
            }
        }

        // Pass 2: classify and collect per section.
        Map<Long, Collector> bySection = new HashMap<>();
        int[] counts = new int[TerrainPlan.ACTIONS];
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int iy = 0; iy < ny; iy++)
            for (int iz = 0; iz < nz; iz++)
                for (int ix = 0; ix < nx; ix++) {
                    int i = (iy * nz + iz) * nx + ix;
                    int x = x0 + ix, y = y0 + iy, z = z0 + iz;
                    if (!overrides.isEmpty() && overrides.containsKey(pack(x, y, z))) continue;
                    byte c = cls[i];
                    int action;
                    if (c == 1) action = TerrainPlan.CARVE;
                    else {
                        boolean above = carved(cls, ix, iy + 1, iz, nx, ny, nz);
                        boolean side = above || carved(cls, ix, iy - 1, iz, nx, ny, nz) || carved(cls, ix + 1, iy, iz, nx, ny, nz)
                            || carved(cls, ix - 1, iy, iz, nx, ny, nz) || carved(cls, ix, iy, iz + 1, nx, ny, nz)
                            || carved(cls, ix, iy, iz - 1, nx, ny, nz);
                        if (!side && c != 2) continue;
                        if (t == 0) action = TerrainPlan.SEAL;
                        else if (above) action = TerrainPlan.FLOOR;
                        else if (side) action = TerrainPlan.WALL;
                        else if (columnAbove(cls, ix, iy, iz, nx, ny, nz, t)) action = TerrainPlan.FLOOR;
                        else action = localUp(basis, x, y, z, ox, oy, oz) >= -.5 ? TerrainPlan.RIM : TerrainPlan.WALL;
                    }
                    int value = 0;
                    if (action >= TerrainPlan.FLOOR && action <= TerrainPlan.RIM)
                        value = palette.pickAt(TerrainPalette.Band.values()[action - 1], x, y, z, seed);
                    add(bySection, counts, bounds, x, y, z, action, value);
                }
        for (var entry : overrides.entrySet()) {
            long key = entry.getKey();
            add(bySection, counts, bounds, unpackX(key), unpackY(key), unpackZ(key), entry.getValue() >>> 16, entry.getValue() & 0xFFFF);
        }

        List<TerrainPlan.Section> sections = new ArrayList<>(bySection.size());
        for (Collector collector : bySection.values()) sections.add(collector.build());
        sections.sort(Comparator.<TerrainPlan.Section>comparingDouble(s -> {
                double dx = (s.x() << 4) + 8 - ox, dy = (s.y() << 4) + 8 - oy, dz = (s.z() << 4) + 8 - oz;
                return dx * dx + dy * dy + dz * dz;
            }).thenComparingInt(TerrainPlan.Section::y).thenComparingInt(TerrainPlan.Section::x)
            .thenComparingInt(TerrainPlan.Section::z));
        if (bounds[0] == Integer.MAX_VALUE) Arrays.fill(bounds, 0);
        return new TerrainPlan(sections, counts, bounds, rotation, corner);
    }

    private static double localUp(Basis basis, int x, int y, int z, double ox, double oy, double oz) {
        return basis.localY(x + .5 - ox, y + .5 - oy, z + .5 - oz);
    }

    private static boolean carved(byte[] cls, int ix, int iy, int iz, int nx, int ny, int nz) {
        return ix >= 0 && iy >= 0 && iz >= 0 && ix < nx && iy < ny && iz < nz && cls[(iy * nz + iz) * nx + ix] == 1;
    }

    private static boolean columnAbove(byte[] cls, int ix, int iy, int iz, int nx, int ny, int nz, int t) {
        for (int k = 2; k <= t; k++) if (carved(cls, ix, iy + k, iz, nx, ny, nz)) return true;
        return false;
    }

    private static void add(Map<Long, Collector> bySection, int[] counts, int[] bounds, int x, int y, int z, int action, int value) {
        long key = TerrainPlan.sectionKey(x >> 4, y >> 4, z >> 4);
        bySection.computeIfAbsent(key, k -> new Collector(x >> 4, y >> 4, z >> 4))
            .add((short) (((y & 15) << 8) | ((z & 15) << 4) | (x & 15)), (byte) action, (short) value);
        counts[action]++;
        bounds[0] = Math.min(bounds[0], x); bounds[1] = Math.min(bounds[1], y); bounds[2] = Math.min(bounds[2], z);
        bounds[3] = Math.max(bounds[3], x); bounds[4] = Math.max(bounds[4], y); bounds[5] = Math.max(bounds[5], z);
    }

    /** Packs a block position into a long (26 bits x, 12 bits y, 26 bits z). */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    static int unpackX(long key) { return (int) (key >> 38) << 6 >> 6; }
    static int unpackY(long key) { return (int) ((key >> 26) & 0xFFF) << 20 >> 20; }
    static int unpackZ(long key) { return (int) (key & 0x3FFFFFF) << 6 >> 6; }

    private static final class Collector {
        final int x, y, z;
        short[] index = new short[64];
        byte[] action = new byte[64];
        short[] value = new short[64];
        int size;

        Collector(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }

        void add(short i, byte a, short v) {
            if (size == index.length) {
                index = Arrays.copyOf(index, size * 2);
                action = Arrays.copyOf(action, size * 2);
                value = Arrays.copyOf(value, size * 2);
            }
            index[size] = i; action[size] = a; value[size] = v;
            size++;
        }

        TerrainPlan.Section build() {
            Integer[] order = new Integer[size];
            for (int i = 0; i < size; i++) order[i] = i;
            Arrays.sort(order, Comparator.comparingInt(i -> index[i] & 0xFFFF));
            short[] si = new short[size];
            byte[] sa = new byte[size];
            short[] sv = new short[size];
            for (int i = 0; i < size; i++) { si[i] = index[order[i]]; sa[i] = action[order[i]]; sv[i] = value[order[i]]; }
            return new TerrainPlan.Section(x, y, z, si, sa, sv);
        }
    }
}
