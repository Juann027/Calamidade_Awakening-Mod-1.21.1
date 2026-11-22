package dev.calamidade_awakening.terrain;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LightEngine;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bulk writer of the Terrain API (0.11.0): writes block states straight into {@link LevelChunkSection}s on the server
 * thread and keeps what {@code LevelChunk.setBlockState} keeps (conferred with {@code javap -c}): the four heightmaps,
 * the light engine (section status when a section stops or starts being empty; sky light sources and {@code checkBlock}
 * only when the light properties change, as vanilla), points of interest and the unsaved flag. It skips on
 * purpose {@code onRemove}/{@code onPlace}, drops, neighbour and shape updates and physics inside the volume (the
 * outer shell gets them from {@link TerrainBorder} at the end). {@link #flush()} ends a batch: every changed section
 * goes once to the players that track its chunk, as a section update packet, followed by the data packet of each new
 * block entity; the client relights locally, and players at the edge of their view distance get light through the
 * game's own path.
 */
final class TerrainWriter {
    private static final Heightmap.Types[] HEIGHTMAPS = {Heightmap.Types.MOTION_BLOCKING,
        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE};

    private final ServerLevel level;
    private final ThreadedLevelLightEngine light;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final Map<Long, Touched> touched = new LinkedHashMap<>();
    /** Every position written by this writer (0.11.1): the inside of the outer shell ({@link TerrainBorder}). */
    private final LongOpenHashSet positions = new LongOpenHashSet();
    private Touched current;
    private int written, sectionsSent;

    /** A section changed in this batch. */
    private static final class Touched {
        final LevelChunk chunk;
        final LevelChunkSection section;
        final SectionPos sectionPos;
        final Heightmap[] heightmaps = new Heightmap[HEIGHTMAPS.length];
        final ShortSet changed = new ShortOpenHashSet();
        /** Positions whose new state has a block entity: their data packet follows the section packet (0.11.1). */
        final LongArrayList entities = new LongArrayList();
        boolean empty;

        Touched(LevelChunk chunk, LevelChunkSection section, SectionPos sectionPos) {
            this.chunk = chunk; this.section = section; this.sectionPos = sectionPos;
            for (int i = 0; i < HEIGHTMAPS.length; i++) heightmaps[i] = chunk.getOrCreateHeightmapUnprimed(HEIGHTMAPS[i]);
            empty = section.hasOnlyAir();
        }
    }

    TerrainWriter(ServerLevel level) {
        this.level = level;
        this.light = level.getChunkSource().getLightEngine();
    }

    /**
     * Enters a section; false when its chunk is not loaded (never loads chunks) or the section is outside the build
     * height. Leave the previous one first.
     */
    boolean enter(int sx, int sy, int sz) {
        long key = SectionPos.asLong(sx, sy, sz);
        Touched known = touched.get(key);
        if (known != null) {
            current = known;
            return true;
        }
        LevelChunk chunk = fullChunk(level, sx, sz);
        if (chunk == null) return false;
        int index = level.getSectionIndexFromSectionY(sy);
        if (index < 0 || index >= level.getSectionsCount()) return false;
        current = new Touched(chunk, chunk.getSection(index), SectionPos.of(sx, sy, sz));
        touched.put(key, current);
        return true;
    }

    /**
     * Chunk at section coordinates if it is loaded and FULL, else null; never loads or generates chunks. Conferred with
     * {@code javap -c}: {@code ServerChunkCache.getChunkNow} returns null off the server thread and only a chunk that
     * reached {@code ChunkStatus.FULL} (a {@link LevelChunk}), or NeoForge's {@code ChunkHolder.currentlyLoading} (a chunk
     * being promoted to FULL inside its load event; edits run in the server tick, never inside that callback). The
     * full-status check also rules out chunks whose ticket level dropped ({@code FullChunkStatus.INACCESSIBLE}: being
     * unloaded).
     */
    static LevelChunk fullChunk(ServerLevel level, int sx, int sz) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)) return null;
        return chunk;
    }

    /** Chunk of the current section. */
    LevelChunk chunk() { return current.chunk; }

    /** State at a packed local index ({@code y << 8 | z << 4 | x}) of the current section. */
    BlockState get(int index) {
        return current.section.getBlockState(index & 15, (index >> 8) & 15, (index >> 4) & 15);
    }

    /** Mutable world position of a packed local index of the current section (valid until the next call). */
    BlockPos pos(int index) {
        SectionPos s = current.sectionPos;
        return pos.set((s.x() << 4) | (index & 15), (s.y() << 4) | ((index >> 8) & 15), (s.z() << 4) | ((index >> 4) & 15));
    }

    /**
     * Writes {@code target} at a packed local index whose current state is {@code old}. A block entity of the old state
     * is removed (the caller decides whether that is allowed and saves its data); a block entity of the new state is
     * created and, when {@code data} is not null, loaded from it.
     */
    void put(int index, BlockState old, BlockState target, CompoundTag data) {
        Touched t = current;
        int lx = index & 15, ly = (index >> 8) & 15, lz = (index >> 4) & 15;
        BlockPos at = pos(index);
        int y = at.getY();
        if (old.hasBlockEntity()) t.chunk.removeBlockEntity(at);
        // A section that stops being empty must be known to the light engine before light checks run in it.
        if (t.empty && !target.isAir()) {
            light.updateSectionStatus(t.sectionPos, false);
            t.empty = false;
        }
        t.section.setBlockState(lx, ly, lz, target, false);
        for (Heightmap heightmap : t.heightmaps) heightmap.update(lx, y, lz, target);
        BlockPos fixed = at.immutable();
        // Same branch as LevelChunk.setBlockState (javap -c): sky light sources and the light check only when the light
        // properties change. Light engine mods (ScalableLux, Starlight-based) replace the sources and leave the field
        // null, so it is checked; their engine still gets checkBlock, which they redirect.
        if (LightEngine.hasDifferentLightProperties(t.chunk, at, old, target)) {
            ChunkSkyLightSources sources = t.chunk.getSkyLightSources();
            if (sources != null) sources.update(t.chunk, lx, y, lz);
            light.checkBlock(fixed);
        }
        level.onBlockStateChange(fixed, old, target);
        if (target.hasBlockEntity()) {
            BlockEntity entity = t.chunk.getBlockEntity(at, LevelChunk.EntityCreationType.IMMEDIATE);
            if (entity != null && data != null) {
                entity.loadWithComponents(data, level.registryAccess());
                entity.setChanged();
            }
        }
        // The packet uses Minecraft's packing (SectionPos.sectionRelativePos: x << 8 | z << 4 | y, conferred with
        // javap -c), not the plan's (y << 8 | z << 4 | x).
        t.changed.add((short) ((lx << 8) | (lz << 4) | ly));
        if (target.hasBlockEntity()) t.entities.add(fixed.asLong());
        positions.add(fixed.asLong());
        written++;
    }

    /** Saved data of the block entity at a packed local index of the current section, or null. */
    CompoundTag blockEntity(int index) {
        BlockEntity entity = current.chunk.getBlockEntity(pos(index));
        return entity == null ? null : entity.saveWithFullMetadata(level.registryAccess());
    }

    /** Ends a batch: section status of emptied sections, unsaved flags and one packet per changed section. */
    void flush() {
        for (Touched t : touched.values()) {
            if (t.changed.isEmpty()) continue;
            if (!t.empty && t.section.hasOnlyAir()) {
                light.updateSectionStatus(t.sectionPos, true);
                t.empty = true;
            }
            t.chunk.setUnsaved(true);
            ChunkPos chunkPos = t.chunk.getPos();
            var players = level.getChunkSource().chunkMap.getPlayers(chunkPos, false);
            if (!players.isEmpty()) {
                var packet = new ClientboundSectionBlocksUpdatePacket(t.sectionPos, t.changed, t.section);
                for (ServerPlayer player : players) player.connection.send(packet);
                // As ChunkHolder.broadcastBlockEntityIfNeeded (javap -c): the section packet carries no block entity
                // data, so signs, banners, skulls and modded blocks would show defaults on the client.
                for (int i = 0; i < t.entities.size(); i++) {
                    BlockEntity entity = level.getBlockEntity(BlockPos.of(t.entities.getLong(i)));
                    var data = entity == null ? null : entity.getUpdatePacket();
                    if (data != null) for (ServerPlayer player : players) player.connection.send(data);
                }
            }
            sectionsSent++;
        }
        touched.clear();
        current = null;
    }

    /** Positions written since this writer was made (packed {@link BlockPos#asLong}). */
    LongOpenHashSet writtenPositions() { return positions; }

    /** Blocks written since this writer was made. */
    int written() { return written; }

    /** Section packets sent (sections changed in a batch, counted once per batch). */
    int sectionsSent() { return sectionsSent; }
}
