package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.api.TerrainNoise;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable "burnt" palette of a {@link TerrainEdit} (0.11.0): weighted block lists per band of the shell around the
 * excavation. Pure Java; blocks are registry IDs ({@code "minecraft:blackstone"}) resolved by the server at commit
 * time (an unknown ID is logged once and skipped).
 *
 * <p>The shell is {@link TerrainEdit.Builder#burn thickness} blocks thick. Bands:
 * <ul>
 * <li>{@link Band#FLOOR}: shell blocks with carved space right above them (the crater floor), and the deeper layers
 * under it;</li>
 * <li>{@link Band#WALL}: the other shell blocks that touch the carved space (walls and ceilings), and the deeper layers
 * behind them;</li>
 * <li>{@link Band#RIM}: deeper shell layers at or above the origin level: the scorched ring on the ground around the
 * crater.</li>
 * </ul>
 * Only solid blocks take the palette; fluids in the shell are sealed with it (so water and lava do not pour into the
 * crater without updates), plants, snow and other non-solid blocks touching the crater burn away (air), and air stays.
 * Empty bands use the {@link Band#WALL} list. Each block's pick comes from a hash of its position and the instance
 * seed: deterministic, different per instance.
 *
 * <pre>{@code
 * static final TerrainPalette GELO = TerrainPalette.builder()
 *     .add(TerrainPalette.Band.WALL, "minecraft:packed_ice", 3).add(TerrainPalette.Band.WALL, "minecraft:blue_ice", 1)
 *     .add(TerrainPalette.Band.RIM, "minecraft:snow_block", 1).build();
 * }</pre>
 */
public final class TerrainPalette {
    /** Largest weight of one entry. */
    public static final int MAX_WEIGHT = 1000;
    /** Most entries per band. */
    public static final int MAX_ENTRIES = 16;

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** Band of the shell around the excavation. */
    public enum Band {
        /** Floor: carved space right above. */
        FLOOR,
        /** Walls and ceilings touching the carved space. */
        WALL,
        /** Outer ring at or above the origin level. */
        RIM
    }

    /**
     * One weighted block of a band.
     *
     * @param block  registry ID of the block, {@code namespace:path}
     * @param weight relative weight, 1..{@value #MAX_WEIGHT}
     */
    public record Entry(String block, int weight) {
        public Entry {
            Objects.requireNonNull(block, "block");
            if (!ID.matcher(block).matches()) throw new IllegalArgumentException("Expected a block ID namespace:path, got " + block);
            if (weight < 1 || weight > MAX_WEIGHT)
                throw new IllegalArgumentException("Expected a weight in [1, " + MAX_WEIGHT + "], got " + weight);
        }
    }

    private final Map<Band, List<Entry>> bands;
    private final Map<Band, int[]> cumulative;

    private TerrainPalette(Map<Band, List<Entry>> source) {
        EnumMap<Band, List<Entry>> lists = new EnumMap<>(Band.class);
        EnumMap<Band, int[]> sums = new EnumMap<>(Band.class);
        for (Band band : Band.values()) {
            List<Entry> list = source.getOrDefault(band, List.of());
            if (list.isEmpty()) list = source.getOrDefault(Band.WALL, List.of());
            lists.put(band, List.copyOf(list));
            int[] sum = new int[list.size()];
            int total = 0;
            for (int i = 0; i < list.size(); i++) sum[i] = total += list.get(i).weight();
            sums.put(band, sum);
        }
        bands = Collections.unmodifiableMap(lists);
        cumulative = sums;
    }

    /** New empty builder; {@link Band#WALL} needs at least one entry. */
    public static Builder builder() { return new Builder(); }

    /**
     * The default burnt palette, vanilla blocks only: floor of blackstone, coarse dirt, basalt, magma block and cobbled
     * deepslate; walls of blackstone, cobblestone, tuff, andesite, cobbled deepslate and a little magma block; a rim of
     * coarse dirt, cobblestone, andesite, tuff and blackstone.
     */
    public static TerrainPalette burnt() { return BURNT; }

    private static final TerrainPalette BURNT = builder()
        .add(Band.FLOOR, "minecraft:blackstone", 4).add(Band.FLOOR, "minecraft:coarse_dirt", 3)
        .add(Band.FLOOR, "minecraft:basalt", 2).add(Band.FLOOR, "minecraft:magma_block", 2)
        .add(Band.FLOOR, "minecraft:cobbled_deepslate", 1)
        .add(Band.WALL, "minecraft:blackstone", 4).add(Band.WALL, "minecraft:cobblestone", 3)
        .add(Band.WALL, "minecraft:tuff", 3).add(Band.WALL, "minecraft:andesite", 2)
        .add(Band.WALL, "minecraft:cobbled_deepslate", 2).add(Band.WALL, "minecraft:magma_block", 1)
        .add(Band.RIM, "minecraft:coarse_dirt", 5).add(Band.RIM, "minecraft:cobblestone", 2)
        .add(Band.RIM, "minecraft:andesite", 2).add(Band.RIM, "minecraft:tuff", 1)
        .add(Band.RIM, "minecraft:blackstone", 1)
        .build();

    /** Entries of a band (the {@link Band#WALL} entries when the band was left empty), immutable. */
    public List<Entry> entries(Band band) { return bands.get(Objects.requireNonNull(band, "band")); }

    /** Index into {@link #entries(Band)} picked by weight from a hash (any int, for example of a block position). */
    public int pick(Band band, int hash) {
        int[] sum = cumulative.get(band);
        int total = sum[sum.length - 1];
        int roll = (int) (((hash >>> 8) * (long) total) >>> 24);
        int low = 0, high = sum.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (roll < sum[mid]) high = mid;
            else low = mid + 1;
        }
        return low;
    }

    /**
     * Index picked for the block at world position ({@code x}, {@code y}, {@code z}) of an instance with this seed:
     * {@link #pick} of a {@link TerrainNoise} hash of the position and the band. Deterministic.
     */
    public int pickAt(Band band, int x, int y, int z, long seed) {
        return pick(band, TerrainNoise.hash(x, y, z, TerrainNoise.seed32(seed) ^ (0x9e3779b9 * (band.ordinal() + 1))));
    }

    /** Same entries in every band. */
    @Override public boolean equals(Object o) { return o instanceof TerrainPalette other && bands.equals(other.bands); }
    /** Hash of the entries. */
    @Override public int hashCode() { return bands.hashCode(); }
    /** Readable form with the entries of each band. */
    @Override public String toString() { return "TerrainPalette" + bands; }

    /** Builder of a {@link TerrainPalette}. */
    public static final class Builder {
        private final EnumMap<Band, List<Entry>> bands = new EnumMap<>(Band.class);

        private Builder() {}

        /** Adds {@code block} (registry ID) to a band with a relative weight, 1..{@value #MAX_WEIGHT}. */
        public Builder add(Band band, String block, int weight) {
            List<Entry> list = bands.computeIfAbsent(Objects.requireNonNull(band, "band"), b -> new ArrayList<>());
            if (list.size() >= MAX_ENTRIES)
                throw new IllegalArgumentException("Expected at most " + MAX_ENTRIES + " entries in band " + band);
            list.add(new Entry(block, weight));
            return this;
        }

        /** The palette; throws when {@link Band#WALL} has no entry. */
        public TerrainPalette build() {
            if (bands.getOrDefault(Band.WALL, List.of()).isEmpty())
                throw new IllegalArgumentException("A terrain palette needs at least one WALL entry");
            return new TerrainPalette(bands);
        }
    }
}
