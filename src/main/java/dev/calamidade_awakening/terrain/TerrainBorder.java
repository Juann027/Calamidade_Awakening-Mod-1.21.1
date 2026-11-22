package dev.calamidade_awakening.terrain;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Outer shell of a finished batch write (0.11.1): the blocks just outside what a commit or an undo wrote get what
 * {@code Level.setBlock} would have given them, once, with no cascade through the written volume.
 * <ul>
 *   <li><b>Shape:</b> {@code updateShape} toward each written neighbour (fences, walls, panes, stairs, snowy grass,
 *   portals, waterlogged blocks); a change is applied with {@code Block.updateOrDestroy} and the flags
 *   {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS}: sent to clients, no further shape updates,
 *   no drops.</li>
 *   <li><b>Neighbour:</b> one {@code neighborChanged} from a written neighbour (redstone, fluids, rails).</li>
 *   <li><b>Fluids:</b> a fluid tick for every fluid outside the shell that touches it, and for every written fluid on
 *   the edge (an undo that gives water back).</li>
 * </ul>
 * Inside the written volume nothing is updated: every block there was written by the edit and the shell already
 * seals fluids and burns what is not solid. Runs in budgeted steps on the server thread; never loads chunks.
 */
final class TerrainBorder {
    private static final Direction[] DIRECTIONS = Direction.values();
    /** Budget units of one border block (several state reads and possibly a write) against one scanned position. */
    private static final int APPLY_COST = 4;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private final ServerLevel level;
    private final LongOpenHashSet written;
    private final long[] scan;
    private final LongOpenHashSet seen = new LongOpenHashSet();
    private final LongArrayList border = new LongArrayList();
    private final LongArrayList edgeFluids = new LongArrayList();
    private int scanned, applied, fluidsDone;
    int updated, shapes, fluidTicks;

    TerrainBorder(ServerLevel level, LongOpenHashSet written) {
        this.level = level;
        this.written = written;
        this.scan = written.toLongArray();
    }

    /** Positions still to scan or update (diagnostics). */
    int remaining() { return scan.length - scanned + border.size() - applied + edgeFluids.size() - fluidsDone; }

    /** Scans and updates up to {@code budget} units; true when the whole shell is done. */
    boolean step(int budget) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (scanned < scan.length && budget > 0) {
            long at = scan[scanned++];
            budget--;
            boolean edge = false;
            for (Direction direction : DIRECTIONS) {
                long next = BlockPos.offset(at, direction);
                if (written.contains(next)) continue;
                edge = true;
                if (seen.add(next)) border.add(next);
            }
            if (edge && loaded(pos.set(at)) && !level.getFluidState(pos).isEmpty()) edgeFluids.add(at);
        }
        if (scanned < scan.length) return false;
        while (applied < border.size() && budget > 0) {
            update(pos.set(border.getLong(applied++)));
            budget -= APPLY_COST;
        }
        while (fluidsDone < edgeFluids.size() && budget > 0) {
            if (loaded(pos.set(edgeFluids.getLong(fluidsDone++)))) tickFluid(pos.immutable());
            budget--;
        }
        return applied >= border.size() && fluidsDone >= edgeFluids.size();
    }

    private boolean loaded(BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && TerrainWriter.fullChunk(level, pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private void update(BlockPos.MutableBlockPos mutable) {
        if (!loaded(mutable)) return;
        BlockPos pos = mutable.immutable();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        updated++;
        BlockState shaped = state;
        Block source = null;
        BlockPos from = null;
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbour = pos.relative(direction);
            if (!written.contains(neighbour.asLong()) || !loaded(neighbour)) continue;
            BlockState other = level.getBlockState(neighbour);
            if (!shaped.isAir()) shaped = shaped.updateShape(direction, other, level, pos, neighbour);
            if (source == null) {
                source = other.getBlock();
                from = neighbour;
            }
        }
        if (shaped != state) {
            Block.updateOrDestroy(state, shaped, level, pos, FLAGS, 512);
            shapes++;
        }
        BlockState now = level.getBlockState(pos);
        if (!now.isAir() && source != null) level.neighborChanged(pos, source, from);
        tickFluid(pos);
    }

    private void tickFluid(BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        if (fluid.isEmpty()) return;
        level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
        fluidTicks++;
    }
}
