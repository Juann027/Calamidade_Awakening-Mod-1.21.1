package dev.calamidade_awakening.terrain;

import dev.calamidade_awakening.api.TerrainPlan;
import dev.calamidade_awakening.api.v1.TerrainPalette;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

/**
 * A batched write on the server thread (0.11.0): a terrain edit being committed ({@link Commit}) or undone
 * ({@link Undo}). {@link #step} writes up to {@code budget} entries and ends the batch with
 * {@link TerrainWriter#flush()}, so light and the changed sections are resent at the end of every tick's batch.
 */
abstract class TerrainJob {
    /** Blocks never touched without an explicit config: data/calamidade_awakening/tags/block/terrain_protected.json. */
    static final TagKey<Block> PROTECTED = TagKey.create(Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath(dev.calamidade_awakening.CalamidadeAwakening.ID, "terrain_protected"));
    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    final ServerLevel level;
    final TerrainWriter writer;
    final String label;
    final long startedTick;
    int ticks;
    long nanos;

    TerrainJob(ServerLevel level, String label) {
        this.level = level;
        this.writer = new TerrainWriter(level);
        this.label = label;
        this.startedTick = level.getServer().getTickCount();
    }

    /** Writes up to {@code budget} entries; true when the job is finished (its last batch already flushed). */
    final boolean step(int budget) {
        long t0 = System.nanoTime();
        boolean done;
        try {
            done = work(budget);
        } finally {
            writer.flush();
        }
        if (done) finish();
        ticks++;
        nanos += System.nanoTime() - t0;
        return done;
    }

    abstract boolean work(int budget);

    void finish() {}

    /** Entries still to visit (for diagnostics). */
    abstract int remaining();

    /** A terrain edit being committed: plan entries resolved against the world, recorded for undo. */
    static final class Commit extends TerrainJob {
        final TerrainWorks.Run run;
        final TerrainPlan plan;
        final BlockState[][] bands;
        final BlockState fill;
        final BlockState[] structure;
        final java.util.Map<Long, CompoundTag> structureData;
        final boolean blockEntities;
        final TerrainSnapshot.Builder snapshot;
        final List<BlockPos> falling = new ArrayList<>();
        int section, entry, protectedSkipped, unloadedSkipped;

        Commit(ServerLevel level, TerrainWorks.Run run, TerrainPlan plan, BlockState[][] bands, BlockState fill,
               BlockState[] structure, java.util.Map<Long, CompoundTag> structureData, boolean blockEntities,
               TerrainSnapshot.Builder snapshot) {
            super(level, run.label());
            this.run = run; this.plan = plan; this.bands = bands; this.fill = fill; this.structure = structure;
            this.structureData = structureData; this.blockEntities = blockEntities; this.snapshot = snapshot;
        }

        @Override boolean work(int budget) {
            var sections = plan.sections();
            int done = 0;
            while (section < sections.size() && done < budget) {
                TerrainPlan.Section s = sections.get(section);
                if (entry == 0 && !writer.enter(s.x(), s.y(), s.z())) {
                    unloadedSkipped++;
                    section++;
                    continue;
                }
                if (entry > 0 && !writer.enter(s.x(), s.y(), s.z())) { section++; entry = 0; continue; }
                for (; entry < s.size() && done < budget; entry++, done++) {
                    int index = s.index()[entry] & 0xFFFF;
                    BlockState old = writer.get(index);
                    BlockPos pos = writer.pos(index);
                    if (isProtected(old, pos)) {
                        if (!old.isAir()) protectedSkipped++;
                        continue;
                    }
                    BlockState target = resolve(s.action()[entry], s.value()[entry], old);
                    if (target == null || target == old) continue;
                    CompoundTag data = null;
                    if (s.action()[entry] == TerrainPlan.STRUCTURE && target.hasBlockEntity()) data = structureData.get(pos.asLong());
                    if (old.hasBlockEntity()) snapshot.blockEntity(pos.immutable(), writer.blockEntity(index));
                    if (target.getBlock() instanceof FallingBlock) falling.add(pos.immutable());
                    snapshot.add(s.x(), s.y(), s.z(), index, Block.getId(old), Block.getId(target));
                    writer.put(index, old, target, data);
                }
                if (entry >= s.size()) { section++; entry = 0; }
            }
            return section >= sections.size();
        }

        boolean isProtected(BlockState old, BlockPos pos) {
            if (old.isAir()) return false;
            if (old.is(Blocks.BEDROCK) || old.is(PROTECTED) || old.getDestroySpeed(level, pos) < 0) return true;
            return old.hasBlockEntity() && !blockEntities;
        }

        BlockState resolve(byte action, short value, BlockState old) {
            switch (action) {
                case TerrainPlan.CARVE:
                    return old.isAir() ? null : AIR;
                case TerrainPlan.FLOOR, TerrainPlan.WALL, TerrainPlan.RIM: {
                    if (old.isAir() || old.is(BlockTags.LOGS)) return null;
                    if (old.is(BlockTags.LEAVES)) return AIR;
                    BlockState band = band(action, value);
                    if (!old.getFluidState().isEmpty()) return band != null ? band : seal(old);
                    return old.isSolid() ? band : AIR;
                }
                case TerrainPlan.SEAL:
                    if (old.isAir() || old.is(BlockTags.LOGS)) return null;
                    if (!old.getFluidState().isEmpty()) return seal(old);
                    return old.is(BlockTags.LEAVES) || !old.isSolid() ? AIR : null;
                case TerrainPlan.FILL:
                    return fill;
                case TerrainPlan.STRUCTURE:
                    return value >= 0 && value < structure.length ? structure[value] : null;
                default:
                    return null;
            }
        }

        private BlockState band(byte action, short value) {
            BlockState[] states = bands[action - 1];
            if (states == null || value < 0 || value >= states.length) return null;
            if (states[value] != null) return states[value];
            for (BlockState state : states) if (state != null) return state;
            return null;
        }

        private static BlockState seal(BlockState old) {
            return old.getFluidState().is(FluidTags.LAVA) ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.STONE.defaultBlockState();
        }

        /** Settles gravity blocks the edit itself wrote over air (a palette, fill or structure with sand or gravel). */
        @Override void finish() {
            for (BlockPos pos : falling) {
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof FallingBlock) || !FallingBlock.isFree(level.getBlockState(pos.below()))) continue;
                int sx = pos.getX() >> 4, sy = pos.getY() >> 4, sz = pos.getZ() >> 4;
                if (!writer.enter(sx, sy, sz)) continue;
                int index = ((pos.getY() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
                BlockState settled = Blocks.COBBLESTONE.defaultBlockState();
                snapshot.add(sx, sy, sz, index, Block.getId(state), Block.getId(settled));
                writer.put(index, state, settled, null);
            }
            writer.flush();
            TerrainWorks.committed(this);
        }

        @Override int remaining() {
            int left = 0;
            for (int i = section; i < plan.sections().size(); i++) left += plan.sections().get(i).size();
            return left - entry;
        }
    }

    /** Undo of a snapshot: old states back where the edit's states are still there. */
    static final class Undo extends TerrainJob {
        final TerrainSnapshot snapshot;
        int section, entry, restored, kept;

        Undo(ServerLevel level, TerrainSnapshot snapshot) {
            super(level, "undo #" + snapshot.id);
            this.snapshot = snapshot;
        }

        @Override boolean work(int budget) {
            int done = 0;
            while (section < snapshot.sections.size() && done < budget) {
                TerrainSnapshot.Section s = snapshot.sections.get(section);
                if (!writer.enter(s.x(), s.y(), s.z())) {
                    kept += s.index().length - entry;
                    section++;
                    entry = 0;
                    continue;
                }
                for (; entry < s.index().length && done < budget; entry++, done++) {
                    int index = s.index()[entry] & 0xFFFF;
                    BlockState current = writer.get(index);
                    if (current != Block.stateById(s.after()[entry])) {
                        kept++;
                        continue;
                    }
                    BlockState before = Block.stateById(s.before()[entry]);
                    CompoundTag data = before.hasBlockEntity() ? snapshot.blockEntities.get(writer.pos(index).asLong()) : null;
                    writer.put(index, current, before, data == null ? null : data.copy());
                    restored++;
                }
                if (entry >= s.index().length) { section++; entry = 0; }
            }
            return section >= snapshot.sections.size();
        }

        @Override void finish() { TerrainWorks.undone(this); }

        @Override int remaining() {
            int left = 0;
            for (int i = section; i < snapshot.sections.size(); i++) left += snapshot.sections.get(i).index().length;
            return left - entry;
        }
    }

    /** Resolves the band block states of a palette (unknown IDs logged once by the caller, null entries). */
    static BlockState[][] bands(TerrainPalette palette, java.util.function.Function<String, BlockState> lookup) {
        BlockState[][] bands = new BlockState[3][];
        if (palette == null) return bands;
        for (TerrainPalette.Band band : TerrainPalette.Band.values()) {
            var entries = palette.entries(band);
            BlockState[] states = new BlockState[entries.size()];
            for (int i = 0; i < states.length; i++) states[i] = lookup.apply(entries.get(i).block());
            bands[band.ordinal()] = states;
        }
        return bands;
    }
}
