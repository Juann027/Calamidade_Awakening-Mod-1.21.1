package dev.calamidade_awakening.client;

import dev.calamidade_awakening.api.Basis;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.net.StageEvent;
import dev.calamidade_awakening.net.TerrainSync;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

/**
 * What the client knows about the terrain edits of each instance (0.11.0): the {@link TerrainSync} states sent by the
 * server (committed with origin and orientation, refused, structure stand-in blocks) and the shared math that both the
 * terrain pass and the structure mesh use: when the commit happened (predicted at the marker until the server says
 * so) and where a stand-in is in its fall.
 */
final class TerrainClient {
    /** State of one edit of one instance. */
    static final class Entry {
        int state;
        long commitTick;
        Vec3 origin;
        float yaw, pitch;
        Structure structure;
        /** True once the dust burst of the landing was spawned. */
        boolean dusted;
    }

    /** Blocks of a structure stand-in: rotated states and positions packed {@code x | y << 6 | z << 12} from the corner. */
    record Structure(int sizeX, int sizeY, int sizeZ, int cornerX, int cornerY, int cornerZ, BlockState[] palette,
                     int[] positions, int[] values, long key) {}

    /** Commit of an edit for drawing: origin and frame, and ticks since the commit (negative before it). */
    record Commit(Vec3 origin, Basis basis, double since) {}

    /** Stand-in this frame: position (world), fall direction (unit), opacity and the impact point. */
    record Pose(Vec3 position, Vec3 direction, float opacity, float progress, Vec3 impact) {}

    private final Map<Long, Entry[]> entries = new HashMap<>();

    void receive(TerrainSync packet) {
        if (packet.index() < 0 || packet.index() >= TerrainEdit.MAX_PER_EFFECT) return;
        Entry entry = entries.computeIfAbsent(packet.order(), k -> new Entry[TerrainEdit.MAX_PER_EFFECT])[packet.index()];
        if (entry == null) entries.get(packet.order())[packet.index()] = entry = new Entry();
        switch (packet.state()) {
            case TerrainSync.COMMITTED -> {
                entry.state = TerrainSync.COMMITTED;
                entry.commitTick = packet.commitTick();
                entry.origin = packet.origin();
                entry.yaw = packet.yaw();
                entry.pitch = packet.pitch();
            }
            case TerrainSync.REFUSED -> entry.state = TerrainSync.REFUSED;
            case TerrainSync.STRUCTURE -> {
                BlockState[] palette = new BlockState[packet.palette().length];
                for (int i = 0; i < palette.length; i++) palette[i] = Block.stateById(packet.palette()[i]);
                entry.structure = new Structure(packet.sizeX(), packet.sizeY(), packet.sizeZ(), packet.cornerX(),
                    packet.cornerY(), packet.cornerZ(), palette, packet.positions(), packet.values(),
                    java.util.Arrays.hashCode(packet.palette()) * 31L + java.util.Arrays.hashCode(packet.positions())
                        + 977L * java.util.Arrays.hashCode(packet.values()));
            }
            default -> { }
        }
    }

    /** Entry of an edit, created when missing (to remember client-side flags before any packet). */
    Entry entry(long order, int index) {
        Entry[] list = entries.computeIfAbsent(order, k -> new Entry[TerrainEdit.MAX_PER_EFFECT]);
        if (list[index] == null) list[index] = new Entry();
        return list[index];
    }

    /**
     * Dust when an object lands (0.11.0): at the commit of every edit with a stand-in or a structure, a burst of smoke,
     * puffs and dust of the ground block around the origin, once per instance. Particles follow the game's particle
     * setting.
     */
    void dust(net.minecraft.client.multiplayer.ClientLevel level, java.util.Collection<StageEvent> events) {
        long now = level.getGameTime();
        for (StageEvent event : events) {
            VisualKind kind = VisualKind.byId(event.visual());
            if (kind == null || kind.terrain().isEmpty()) continue;
            for (int i = 0; i < kind.terrain().size(); i++) {
                TerrainEdit edit = kind.terrain().get(i);
                if (edit.standIn() == null && edit.structure() == null) continue;
                Entry entry = entry(event.order(), i);
                if (entry.dusted || entry.state == TerrainSync.REFUSED) continue;
                long commit;
                Vec3 origin;
                if (entry.state == TerrainSync.COMMITTED) {
                    commit = entry.commitTick;
                    origin = entry.origin;
                } else if (event.entityId() < 0) {
                    commit = event.tick() + kind.timeline().tick(edit.marker());
                    origin = event.center();
                } else continue;
                if (now < commit) continue;
                entry.dusted = true;
                if (now - commit > 10) continue;
                burst(level, origin, (float) Math.max(3, edit.shape().extent()), event.seed());
            }
        }
    }

    private static void burst(net.minecraft.client.multiplayer.ClientLevel level, Vec3 origin, float radius, long seed) {
        var random = new java.util.Random(seed);
        var ground = level.getBlockState(net.minecraft.core.BlockPos.containing(origin.x, origin.y - .5, origin.z));
        var dust = ground.isAir() ? null : new net.minecraft.core.particles.BlockParticleOption(
            net.minecraft.core.particles.ParticleTypes.BLOCK, ground);
        int count = (int) Math.clamp(radius * 7, 24, 140);
        for (int n = 0; n < count; n++) {
            double angle = random.nextDouble() * Math.PI * 2, r = radius * (.35 + random.nextDouble() * .8);
            double x = origin.x + Math.cos(angle) * r, z = origin.z + Math.sin(angle) * r, y = origin.y + random.nextDouble() * 2;
            double out = .04 + random.nextDouble() * .12;
            level.addParticle(n % 3 == 0 ? net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE
                    : net.minecraft.core.particles.ParticleTypes.POOF,
                x, y, z, Math.cos(angle) * out, .03 + random.nextDouble() * .08, Math.sin(angle) * out);
            if (dust != null && n % 2 == 0)
                level.addParticle(dust, x, y + .2, z, Math.cos(angle) * out * 2, .15 + random.nextDouble() * .2, Math.sin(angle) * out * 2);
        }
    }

    Entry get(long order, int index) {
        Entry[] list = entries.get(order);
        return list == null ? null : list[index];
    }

    void forget(long order) { entries.remove(order); }

    void clear() { entries.clear(); }

    /** Orders with state, for pruning against the scene. */
    java.util.Set<Long> orders() { return entries.keySet(); }

    /**
     * Commit of edit {@code index}: the server's, or predicted at the marker tick (origin and frame of the instance now)
     * until the server's arrives; null when the server refused the edit.
     */
    Commit commit(StageEvent event, VisualKind kind, TerrainEdit edit, int index, double now, Vec3 center, Basis basis) {
        Entry entry = get(event.order(), index);
        if (entry != null && entry.state == TerrainSync.REFUSED) return null;
        if (entry != null && entry.state == TerrainSync.COMMITTED)
            return new Commit(entry.origin, Basis.of(entry.yaw, entry.pitch), now - entry.commitTick);
        int marker = kind.timeline().tick(edit.marker());
        return new Commit(center, basis, now - (event.tick() + marker));
    }

    /**
     * Stand-in of an edit this frame, or null when it has none or is invisible. The fall runs from the start offset
     * (instance frame) to the impact: the fill center (or the origin) for a sphere, the structure corner for a
     * structure.
     */
    static Pose standIn(TerrainEdit edit, Timeline timeline, double localAge, Vec3 origin, Basis basis, Structure structure) {
        TerrainEdit.StandIn standIn = edit.standIn();
        if (standIn == null) return null;
        int commitTick = timeline.tick(edit.marker());
        float opacity = standIn.opacity(localAge, commitTick);
        if (opacity <= 1e-3f) return null;
        Vec3 impact;
        if (standIn.kind() == TerrainEdit.StandIn.Kind.STRUCTURE) {
            if (structure == null) return null;
            impact = new Vec3(structure.cornerX(), structure.cornerY(), structure.cornerZ());
        } else {
            TerrainEdit.Fill fill = edit.fill();
            impact = fill == null ? origin : origin.add(StageScene.turn(basis, fill.x(), fill.y(), fill.z()));
        }
        Vec3 start = impact.add(StageScene.turn(basis, standIn.x(), standIn.y(), standIn.z()));
        float progress = standIn.progress(timeline, localAge, commitTick);
        Vec3 path = impact.subtract(start);
        Vec3 direction = path.lengthSqr() > 1e-8 ? path.normalize() : new Vec3(0, -1, 0);
        return new Pose(start.add(path.scale(progress)), direction, opacity, progress, impact);
    }
}
