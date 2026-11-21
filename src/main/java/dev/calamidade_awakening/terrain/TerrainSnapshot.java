package dev.calamidade_awakening.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact undo data of one committed terrain edit (0.11.0): for every changed position, grouped by section, the packed
 * local index and the global block state IDs before and after ({@code Block.getId}), about 10 bytes per block, plus the
 * saved data of the block entities that were replaced. Undo writes the old state back only where the current state is
 * still the one the edit wrote, so later changes by players are kept.
 */
final class TerrainSnapshot {
    /** Changed positions of one section. */
    record Section(int x, int y, int z, short[] index, int[] before, int[] after) {}

    final int id;
    final ResourceKey<Level> dimension;
    final String effect;
    final BlockPos origin;
    final long createdTick;
    final String source;
    final List<Section> sections;
    final Map<Long, CompoundTag> blockEntities;
    final int blocks;

    private TerrainSnapshot(Builder b, List<Section> sections) {
        id = b.id; dimension = b.dimension; effect = b.effect; origin = b.origin; createdTick = b.createdTick; source = b.source;
        this.sections = List.copyOf(sections);
        blockEntities = Map.copyOf(b.blockEntities);
        int sum = 0;
        for (Section s : sections) sum += s.index().length;
        blocks = sum;
    }

    /** Records changes while an edit commits. */
    static final class Builder {
        final int id;
        final ResourceKey<Level> dimension;
        final String effect;
        final BlockPos origin;
        final long createdTick;
        final String source;
        private final Map<Long, Growing> sections = new LinkedHashMap<>();
        private final Map<Long, CompoundTag> blockEntities = new HashMap<>();

        Builder(int id, ResourceKey<Level> dimension, String effect, BlockPos origin, long createdTick, String source) {
            this.id = id; this.dimension = dimension; this.effect = effect; this.origin = origin;
            this.createdTick = createdTick; this.source = source;
        }

        void add(int sx, int sy, int sz, int index, int before, int after) {
            section(sx, sy, sz).add(index, before, after);
        }

        /** Changes of one section (kept by the caller while it writes that section: no map lookup per block). */
        Growing section(int sx, int sy, int sz) {
            return sections.computeIfAbsent(net.minecraft.core.SectionPos.asLong(sx, sy, sz), k -> new Growing(sx, sy, sz));
        }

        void blockEntity(BlockPos pos, CompoundTag data) { if (data != null) blockEntities.put(pos.asLong(), data); }

        TerrainSnapshot build() {
            List<Section> list = new ArrayList<>(sections.size());
            for (Growing g : sections.values()) list.add(g.build());
            return new TerrainSnapshot(this, list);
        }
    }

    static final class Growing {
        final int x, y, z;
        short[] index = new short[64];
        int[] before = new int[64], after = new int[64];
        int size;

        Growing(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }

        void add(int i, int b, int a) {
            if (size == index.length) {
                index = Arrays.copyOf(index, size * 2);
                before = Arrays.copyOf(before, size * 2);
                after = Arrays.copyOf(after, size * 2);
            }
            index[size] = (short) i; before[size] = b; after[size] = a;
            size++;
        }

        Section build() {
            return new Section(x, y, z, Arrays.copyOf(index, size), Arrays.copyOf(before, size), Arrays.copyOf(after, size));
        }
    }
}
