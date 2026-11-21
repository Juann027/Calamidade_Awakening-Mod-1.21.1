package dev.calamidade_awakening.api;

import java.util.List;

/**
 * Result of {@link TerrainPlanner#plan} (0.11.0): the ordered positions of a terrain edit and what each becomes,
 * split in batches per chunk section. Pure data, no Minecraft types.
 *
 * <p>Sections are sorted by the distance of their center to the edit origin (nearest first, so the middle goes first
 * under the flash), ties by section y, x and z. Inside a section, entries are sorted by their packed index
 * {@code y << 8 | z << 4 | x} (local coordinates 0..15). The order depends only on the request, never on hash maps.
 */
public final class TerrainPlan {
    /** Becomes air (unless protected). */
    public static final byte CARVE = 0;
    /** Shell floor band: palette entry {@code value} if solid, sealed if fluid, burned if non-solid. */
    public static final byte FLOOR = 1;
    /** Shell wall band, like {@link #FLOOR}. */
    public static final byte WALL = 2;
    /** Shell rim band, like {@link #FLOOR}. */
    public static final byte RIM = 3;
    /** No shell palette: only fluids are sealed and non-solid blocks touching the carved space burned. */
    public static final byte SEAL = 4;
    /** The edit's fill block. */
    public static final byte FILL = 5;
    /** Structure block {@code value} (index into the structure palette). */
    public static final byte STRUCTURE = 6;
    /** Number of actions. */
    public static final int ACTIONS = 7;

    /**
     * Batch of one chunk section.
     *
     * @param x      section x (block x >> 4)
     * @param y      section y (block y >> 4)
     * @param z      section z (block z >> 4)
     * @param index  packed local positions {@code y << 8 | z << 4 | x}, ascending
     * @param action action per entry
     * @param value  palette entry (bands) or structure palette index (structure); 0 otherwise
     */
    public record Section(int x, int y, int z, short[] index, byte[] action, short[] value) {
        /** Entries in this section. */
        public int size() { return index.length; }
        /** World block x of entry {@code i}. */
        public int blockX(int i) { return (x << 4) | (index[i] & 15); }
        /** World block y of entry {@code i}. */
        public int blockY(int i) { return (y << 4) | ((index[i] >> 8) & 15); }
        /** World block z of entry {@code i}. */
        public int blockZ(int i) { return (z << 4) | ((index[i] >> 4) & 15); }
        /** Packed section key (same packing as {@link TerrainPlan#sectionKey}). */
        public long key() { return sectionKey(x, y, z); }
    }

    private final List<Section> sections;
    private final int total;
    private final int[] counts;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final int rotation, structureX, structureY, structureZ;

    TerrainPlan(List<Section> sections, int[] counts, int[] bounds, int rotation, int[] structure) {
        this.sections = List.copyOf(sections);
        int sum = 0;
        for (int c : counts) sum += c;
        this.total = sum;
        this.counts = counts.clone();
        minX = bounds[0]; minY = bounds[1]; minZ = bounds[2]; maxX = bounds[3]; maxY = bounds[4]; maxZ = bounds[5];
        this.rotation = rotation;
        structureX = structure[0]; structureY = structure[1]; structureZ = structure[2];
    }

    /** Packed key of a section: 22 bits x, 20 bits y, 22 bits z (like Minecraft's section keys, but not the same packing). */
    public static long sectionKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (z & 0x3FFFFF);
    }

    /** Batches, nearest section first. */
    public List<Section> sections() { return sections; }
    /** Total entries (the planned volume). */
    public int total() { return total; }
    /** Entries with an action ({@link #CARVE} .. {@link #STRUCTURE}). */
    public int count(byte action) { return counts[action]; }
    /** Smallest block x of an entry (inclusive); with no entries, 0. */
    public int minX() { return minX; }
    /** Smallest block y of an entry (inclusive). */
    public int minY() { return minY; }
    /** Smallest block z of an entry (inclusive). */
    public int minZ() { return minZ; }
    /** Largest block x of an entry (inclusive). */
    public int maxX() { return maxX; }
    /** Largest block y of an entry (inclusive). */
    public int maxY() { return maxY; }
    /** Largest block z of an entry (inclusive). */
    public int maxZ() { return maxZ; }
    /** Structure rotation in 90° clockwise steps, 0..3 (0 without a structure). */
    public int rotation() { return rotation; }
    /** World x of the structure's lowest corner (0 without a structure). */
    public int structureX() { return structureX; }
    /** World y of the structure's lowest corner. */
    public int structureY() { return structureY; }
    /** World z of the structure's lowest corner. */
    public int structureZ() { return structureZ; }
}
