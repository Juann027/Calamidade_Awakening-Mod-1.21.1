package dev.calamidade_awakening.terrain;

import com.mojang.logging.LogUtils;
import cyou.rimuru.rimulib.mc1211.api.NetApi;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.TerrainPlan;
import dev.calamidade_awakening.api.TerrainPlanner;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.v1.EffectSource;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.TerrainEditEvent;
import dev.calamidade_awakening.config.ServerConfig;
import dev.calamidade_awakening.net.StageEvent;
import dev.calamidade_awakening.net.StageNetwork;
import dev.calamidade_awakening.net.TerrainSync;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server side of the Terrain API (0.11.0). For every instance of an effect with {@link Effects#terrain terrain edits}:
 * at the start, the safety rules (server config, dimensions, {@code mobGriefing}) and the plan on a worker thread (pure,
 * no world reads); when the plan is ready, the volume check and one {@link TerrainEditEvent}; at the commit marker (an
 * engine cue), a {@link TerrainJob.Commit} that writes {@code terrainBlocksPerTick} blocks per tick, shared by every
 * running job, starting in the marker's tick. Committed edits keep a {@link TerrainSnapshot} for
 * {@code terrainUndoMinutes}. Clients learn the state through {@link TerrainSync} (once per change and on arrival).
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID)
public final class TerrainWorks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService PLANNER = Executors.newFixedThreadPool(
        Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 2)), runnable -> {
            Thread thread = new Thread(runnable, "Calamidade terrain planner");
            thread.setDaemon(true);
            return thread;
        });
    private static final Map<Long, Instance> INSTANCES = new HashMap<>();
    private static final List<TerrainJob> JOBS = new ArrayList<>();
    private static final Deque<TerrainSnapshot> HISTORY = new ArrayDeque<>();
    private static final Set<String> UNKNOWN = new HashSet<>();
    private static final int[] REFUSED = new int[Reason.values().length];
    private static int nextSnapshot = 1;
    private static int commits, undos;
    private static long blocksWritten;
    /** Last committed edit, for /calamidade terrain list and the in-game test: blocks, ticks, server-thread ms. */
    private static String lastReport = "";

    /** Why an edit did not run. */
    public enum Reason {
        /** {@code terrainEnabled = false}. */
        DISABLED,
        /** Dimension not in {@code terrainDimensions}. */
        DIMENSION,
        /** Mob source with the {@code mobGriefing} game rule off. */
        MOB_GRIEFING,
        /** Cancelled by a {@link TerrainEditEvent} listener. */
        EVENT,
        /** Planned volume above {@code terrainMaxVolume}. */
        VOLUME,
        /** Structure template missing or larger than {@code terrainMaxStructure}. */
        STRUCTURE,
        /** Plan failed (shape too large to scan). */
        ERROR
    }

    enum State { WAITING, PLANNING, READY, COMMITTING, DONE, REFUSED }

    private TerrainWorks() {}

    static final class Instance {
        final ServerLevel level;
        final VisualKind kind;
        final StageEvent event;
        final Entity anchor;
        final EffectSource source;
        final Run[] runs;

        Instance(ServerLevel level, VisualKind kind, StageEvent event, Entity anchor, EffectSource source) {
            this.level = level; this.kind = kind; this.event = event; this.anchor = anchor; this.source = source;
            this.runs = new Run[kind.terrain().size()];
        }
    }

    /** One edit of one instance. */
    static final class Run {
        final Instance instance;
        final int index;
        final TerrainEdit edit;
        State state = State.WAITING;
        Reason reason;
        CompletableFuture<TerrainPlan> future;
        TerrainPlan plan;
        Vec3 origin;
        float yaw, pitch;
        long commitTick;
        Structure structure;
        TerrainSync structureSync;

        Run(Instance instance, int index, TerrainEdit edit) { this.instance = instance; this.index = index; this.edit = edit; }

        String label() { return instance.kind.networkKey() + "#" + index; }
    }

    /** A loaded structure template turned for the instance: rotated palette, template blocks and block entity data. */
    record Structure(int sizeX, int sizeY, int sizeZ, int rotation, BlockState[] palette, TerrainPlanner.Blocks blocks,
                     Map<Integer, CompoundTag> data) {}

    // ------------------------------------------------------------------------------------------------ lifecycle

    /** An instance started (server thread, before its first dispatch). */
    public static void started(ServerLevel level, VisualKind kind, StageEvent event, Entity anchor, EffectSource source) {
        if (kind.terrain().isEmpty()) return;
        Instance instance = new Instance(level, kind, event, anchor, source);
        INSTANCES.put(event.order(), instance);
        var rules = ServerConfig.terrain();
        for (int i = 0; i < instance.runs.length; i++) {
            Run run = new Run(instance, i, kind.terrain().get(i));
            instance.runs[i] = run;
            Reason gate = gate(rules, level, source, anchor);
            if (gate != null) {
                refuse(run, gate, false);
                continue;
            }
            run.origin = event.center();
            run.yaw = event.yaw();
            run.pitch = event.pitch();
            if (run.edit.structure() != null) {
                run.structure = loadStructure(level, run.edit, run.yaw, rules.maxStructure());
                if (run.structure == null) {
                    refuse(run, Reason.STRUCTURE, false);
                    continue;
                }
                if (run.edit.standIn() != null && run.edit.standIn().kind() == TerrainEdit.StandIn.Kind.STRUCTURE)
                    run.structureSync = structureSync(run);
            }
            // Instances anchored to an entity only know their origin at the marker: they plan there.
            if (anchor == null) submit(run);
        }
    }

    /** The instance ended: plans not committed yet are dropped; running commits finish. */
    public static void ended(long order) {
        Instance instance = INSTANCES.remove(order);
        if (instance == null) return;
        for (Run run : instance.runs)
            if (run != null && run.future != null && run.state.ordinal() < State.COMMITTING.ordinal()) run.future.cancel(false);
    }

    /** Terrain state for a player that just received the instance (late arrivals included). */
    public static void sendState(ServerPlayer player, long order) {
        Instance instance = INSTANCES.get(order);
        if (instance == null || !NetApi.canSend(player, TerrainSync.PACKET)) return;
        for (Run run : instance.runs) {
            if (run == null) continue;
            if (run.state == State.REFUSED) NetApi.send(player, TerrainSync.refused(order, run.index));
            else {
                if (run.structureSync != null) NetApi.send(player, run.structureSync);
                if (run.state == State.COMMITTING || run.state == State.DONE)
                    NetApi.send(player, TerrainSync.committed(order, run.index, run.commitTick, run.origin, run.yaw, run.pitch));
            }
        }
    }

    private static void broadcast(Run run, TerrainSync packet) {
        for (ServerPlayer player : StageNetwork.recipients(run.instance.event.order()))
            if (NetApi.canSend(player, TerrainSync.PACKET)) NetApi.send(player, packet);
    }

    private static Reason gate(ServerConfig.Terrain rules, ServerLevel level, EffectSource source, Entity anchor) {
        if (!rules.enabled()) return Reason.DISABLED;
        if (!rules.allows(level.dimension().location().toString())) return Reason.DIMENSION;
        if (rules.mobGriefing() && source.kind() == EffectSource.Kind.ENTITY) {
            Entity entity = anchor != null && anchor.getUUID().equals(source.uuid()) ? anchor : level.getEntity(source.uuid());
            if (!(entity instanceof Player) && !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return Reason.MOB_GRIEFING;
        }
        return null;
    }

    private static void refuse(Run run, Reason reason, boolean broadcast) {
        run.state = State.REFUSED;
        run.reason = reason;
        REFUSED[reason.ordinal()]++;
        LOGGER.debug("Terrain edit {} refused: {}", run.label(), reason);
        if (broadcast) broadcast(run, TerrainSync.refused(run.instance.event.order(), run.index));
    }

    private static TerrainPlanner.Request request(Run run) {
        ServerLevel level = run.instance.level;
        return new TerrainPlanner.Request(run.edit, run.origin.x, run.origin.y, run.origin.z, run.yaw, run.pitch,
            run.instance.event.seed(), level.getMinBuildHeight(), level.getMaxBuildHeight(),
            run.structure == null ? null : run.structure.blocks());
    }

    private static void submit(Run run) {
        run.state = State.PLANNING;
        TerrainPlanner.Request request = request(run);
        run.future = CompletableFuture.supplyAsync(() -> TerrainPlanner.plan(request), PLANNER);
    }

    /** Plan ready: volume check and the protection event, once per edit. */
    private static void planned(Run run, boolean broadcast) {
        try {
            run.plan = run.future.join();
        } catch (RuntimeException failure) {
            LOGGER.warn("Terrain plan of {} failed: {}", run.label(), failure.toString());
            refuse(run, Reason.ERROR, broadcast);
            return;
        }
        TerrainPlan plan = run.plan;
        if (plan.total() > ServerConfig.terrain().maxVolume()) {
            refuse(run, Reason.VOLUME, broadcast);
            return;
        }
        Instance instance = run.instance;
        var event = new TerrainEditEvent(instance.level, ResourceLocation.parse(instance.kind.networkKey()), run.index,
            run.edit, run.origin, new BoundingBox(plan.minX(), plan.minY(), plan.minZ(), plan.maxX(), plan.maxY(), plan.maxZ()),
            plan.total(), instance.source, entityOf(instance));
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            refuse(run, Reason.EVENT, broadcast);
            return;
        }
        run.state = State.READY;
    }

    private static Entity entityOf(Instance instance) {
        ServerLevel level = instance.level;
        var caster = instance.event.caster();
        if (caster != null && (caster.getMostSignificantBits() | caster.getLeastSignificantBits()) != 0) {
            Player player = level.getPlayerByUUID(caster);
            if (player != null) return player;
        }
        if (instance.anchor != null) return instance.anchor;
        return instance.source.uuid() != null ? level.getEntity(instance.source.uuid()) : null;
    }

    /** Engine cue at the commit marker of edit {@code index} (registered by {@code VisualKind.setTerrain}). */
    public static void commitCue(Effects.Context ctx, int index) {
        Instance instance = INSTANCES.get(StageNetwork.orderOf(ctx.handle()));
        if (instance == null || index >= instance.runs.length) return;
        Run run = instance.runs[index];
        if (run == null || run.state == State.REFUSED || run.state.ordinal() >= State.COMMITTING.ordinal()) return;
        if (instance.anchor != null) {
            run.origin = ctx.center();
            run.yaw = ctx.yaw();
            run.pitch = ctx.pitch();
            try {
                run.future = CompletableFuture.completedFuture(TerrainPlanner.plan(request(run)));
            } catch (RuntimeException failure) {
                run.future = CompletableFuture.failedFuture(failure);
            }
            planned(run, true);
        } else if (run.state == State.PLANNING) planned(run, true);
        if (run.state != State.READY) return;
        start(run);
    }

    private static void start(Run run) {
        Instance instance = run.instance;
        ServerLevel level = instance.level;
        var rules = ServerConfig.terrain();
        BlockState[][] bands = TerrainJob.bands(run.edit.palette(), TerrainWorks::block);
        BlockState fill = run.edit.fill() == null ? null : block(run.edit.fill().block());
        BlockState[] structure = run.structure == null ? new BlockState[0] : run.structure.palette();
        Map<Long, CompoundTag> data = new HashMap<>();
        if (run.structure != null && !run.structure.data().isEmpty()) {
            TerrainPlan plan = run.plan;
            var blocks = run.structure.blocks();
            for (var entry : run.structure.data().entrySet()) {
                int i = entry.getKey();
                int x = plan.structureX() + TerrainPlanner.rotateX(blocks.x()[i], blocks.z()[i], blocks.sizeX(), blocks.sizeZ(), plan.rotation());
                int z = plan.structureZ() + TerrainPlanner.rotateZ(blocks.x()[i], blocks.z()[i], blocks.sizeX(), blocks.sizeZ(), plan.rotation());
                data.put(BlockPos.asLong(x, plan.structureY() + blocks.y()[i], z), entry.getValue());
            }
        }
        long tick = level.getServer().getTickCount();
        String source = describe(instance);
        TerrainSnapshot.Builder snapshot = new TerrainSnapshot.Builder(nextSnapshot++, level.dimension(),
            instance.kind.networkKey(), BlockPos.containing(run.origin), tick, source);
        var job = new TerrainJob.Commit(level, run, run.plan, bands, fill, structure, data,
            run.edit.replacesBlockEntities() && rules.blockEntities(), snapshot);
        run.commitTick = level.getGameTime();
        run.state = State.COMMITTING;
        JOBS.add(job);
        broadcast(run, TerrainSync.committed(instance.event.order(), run.index, run.commitTick, run.origin, run.yaw, run.pitch));
    }

    private static String describe(Instance instance) {
        Entity entity = entityOf(instance);
        String who = entity == null ? instance.source.kind().name().toLowerCase(java.util.Locale.ROOT)
            : entity.getName().getString();
        return who;
    }

    /** Block state of a registry ID; unknown IDs are logged once and give null. */
    static BlockState block(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location != null && BuiltInRegistries.BLOCK.containsKey(location)) return BuiltInRegistries.BLOCK.get(location).defaultBlockState();
        if (UNKNOWN.add(id)) LOGGER.warn("Terrain palette block {} does not exist; skipped", id);
        return null;
    }

    /** Called by a finished commit (server thread). */
    static void committed(TerrainJob.Commit job) {
        job.run.state = State.DONE;
        commits++;
        blocksWritten += job.writer.written();
        TerrainSnapshot snapshot = job.snapshot.build();
        var rules = ServerConfig.terrain();
        if (rules.undoMinutes() > 0 && rules.undoEdits() > 0 && snapshot.blocks > 0) {
            HISTORY.addLast(snapshot);
            while (HISTORY.size() > rules.undoEdits()) HISTORY.removeFirst();
        }
        lastReport = String.format(java.util.Locale.ROOT,
            "%s: %d planned, %d written, %d protected kept, %d sections unloaded, %d ticks, %.2f ms on the server thread, %d section packets",
            job.label, job.plan.total(), job.writer.written(), job.protectedSkipped, job.unloadedSkipped, job.ticks,
            job.nanos / 1e6, job.writer.sectionsSent());
        LOGGER.debug("Terrain commit {}", lastReport);
    }

    /** Called by a finished undo (server thread). */
    static void undone(TerrainJob.Undo job) {
        undos++;
        blocksWritten += job.writer.written();
        lastReport = String.format(java.util.Locale.ROOT, "undo #%d: %d restored, %d kept (changed since), %d ticks, %.2f ms",
            job.snapshot.id, job.restored, job.kept, job.ticks, job.nanos / 1e6);
    }

    /** Every server tick, after the cues (so a commit writes its first batch in the marker's tick). */
    public static void tick(MinecraftServer server) {
        for (Instance instance : INSTANCES.values())
            for (Run run : instance.runs)
                if (run != null && run.state == State.PLANNING && run.future.isDone()) planned(run, true);
        if (!JOBS.isEmpty()) {
            int budget = ServerConfig.terrain().blocksPerTick();
            for (Iterator<TerrainJob> it = JOBS.iterator(); it.hasNext() && budget > 0; ) {
                TerrainJob job = it.next();
                int cap = budget;
                if (job instanceof TerrainJob.Commit commit && commit.run.edit.blocksPerTick() > 0)
                    cap = Math.min(cap, commit.run.edit.blocksPerTick());
                boolean done;
                try {
                    done = job.step(cap);
                } catch (RuntimeException failure) {
                    LOGGER.error("Terrain job {} failed and was stopped", job.label, failure);
                    done = true;
                }
                budget -= cap;
                if (done) it.remove();
            }
        }
        var rules = ServerConfig.terrain();
        long keep = rules.undoMinutes() * 60L * 20L;
        while (!HISTORY.isEmpty() && server.getTickCount() - HISTORY.peekFirst().createdTick > keep) HISTORY.removeFirst();
    }

    // ------------------------------------------------------------------------------------------------ structures

    private static Structure loadStructure(ServerLevel level, TerrainEdit edit, float yaw, int maxSize) {
        ResourceLocation id = ResourceLocation.tryParse(edit.structure());
        var template = id == null ? java.util.Optional.<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate>empty()
            : level.getStructureManager().get(id);
        if (template.isEmpty()) {
            if (UNKNOWN.add("structure " + edit.structure())) LOGGER.warn("Terrain structure {} not found", edit.structure());
            return null;
        }
        CompoundTag tag = template.get().save(new CompoundTag());
        ListTag size = tag.getList("size", Tag.TAG_INT);
        int sx = size.getInt(0), sy = size.getInt(1), sz = size.getInt(2);
        if (sx < 1 || sy < 1 || sz < 1 || sx > maxSize || sy > maxSize || sz > maxSize) {
            LOGGER.warn("Terrain structure {} is {}x{}x{}, beyond terrainMaxStructure {}", edit.structure(), sx, sy, sz, maxSize);
            return null;
        }
        ListTag paletteTag = tag.contains("palettes", Tag.TAG_LIST) ? tag.getList("palettes", Tag.TAG_LIST).getList(0)
            : tag.getList("palette", Tag.TAG_COMPOUND);
        int rotation = TerrainPlanner.rotation(yaw);
        Rotation turn = Rotation.values()[rotation];
        var lookup = level.holderLookup(Registries.BLOCK);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) palette[i] = NbtUtils.readBlockState(lookup, paletteTag.getCompound(i)).rotate(turn);
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        int n = list.size();
        int[] xs = new int[n], ys = new int[n], zs = new int[n], values = new int[n];
        Map<Integer, CompoundTag> data = new HashMap<>();
        int count = 0;
        for (int i = 0; i < n; i++) {
            CompoundTag entry = list.getCompound(i);
            ListTag pos = entry.getList("pos", Tag.TAG_INT);
            int state = entry.getInt("state");
            if (state < 0 || state >= palette.length || palette[state].is(Blocks.STRUCTURE_VOID)) continue;
            xs[count] = pos.getInt(0); ys[count] = pos.getInt(1); zs[count] = pos.getInt(2); values[count] = state;
            if (entry.contains("nbt", Tag.TAG_COMPOUND)) data.put(count, entry.getCompound("nbt"));
            count++;
        }
        var blocks = new TerrainPlanner.Blocks(sx, sy, sz, java.util.Arrays.copyOf(xs, count), java.util.Arrays.copyOf(ys, count),
            java.util.Arrays.copyOf(zs, count), java.util.Arrays.copyOf(values, count));
        return new Structure(sx, sy, sz, rotation, palette, blocks, data);
    }

    /** Stand-in payload: rotated states and positions relative to the structure corner (air left out). */
    private static TerrainSync structureSync(Run run) {
        Structure s = run.structure;
        int[] corner = TerrainPlanner.structureCorner(run.edit, run.origin.x, run.origin.y, run.origin.z, run.yaw, s.sizeX(), s.sizeZ());
        int[] palette = new int[s.palette().length];
        for (int i = 0; i < palette.length; i++) palette[i] = Block.getId(s.palette()[i]);
        var blocks = s.blocks();
        int rx = (s.rotation() & 1) == 1 ? s.sizeZ() : s.sizeX(), rz = (s.rotation() & 1) == 1 ? s.sizeX() : s.sizeZ();
        int[] positions = new int[blocks.x().length], values = new int[blocks.x().length];
        int count = 0;
        for (int i = 0; i < blocks.x().length; i++) {
            if (s.palette()[blocks.palette()[i]].isAir()) continue;
            int x = TerrainPlanner.rotateX(blocks.x()[i], blocks.z()[i], s.sizeX(), s.sizeZ(), s.rotation());
            int z = TerrainPlanner.rotateZ(blocks.x()[i], blocks.z()[i], s.sizeX(), s.sizeZ(), s.rotation());
            positions[count] = x | (blocks.y()[i] << 6) | (z << 12);
            values[count] = blocks.palette()[i];
            count++;
        }
        return TerrainSync.structure(run.instance.event.order(), run.index, rx, s.sizeY(), rz, corner[0], corner[1], corner[2],
            palette, java.util.Arrays.copyOf(positions, count), java.util.Arrays.copyOf(values, count));
    }

    // ------------------------------------------------------------------------------------------------ commands

    /** Undo of the newest snapshot ({@code id < 0}) or of snapshot {@code id}; null when there is none. */
    public static String undo(MinecraftServer server, int id) {
        TerrainSnapshot target = null;
        if (id < 0) target = HISTORY.peekLast();
        else for (TerrainSnapshot s : HISTORY) if (s.id == id) target = s;
        if (target == null) return null;
        ServerLevel level = server.getLevel(target.dimension);
        if (level == null) return null;
        HISTORY.remove(target);
        JOBS.add(new TerrainJob.Undo(level, target));
        return "#" + target.id + " " + target.effect + " (" + target.blocks + ")";
    }

    /** Lines for /calamidade terrain list: snapshots newest first, running jobs and the last report. */
    public static List<String> list(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        long now = server.getTickCount();
        Iterator<TerrainSnapshot> it = HISTORY.descendingIterator();
        while (it.hasNext()) {
            TerrainSnapshot s = it.next();
            lines.add(String.format(java.util.Locale.ROOT, "#%d %s · %d %d %d (%s) · %d blocks · %ds ago · %s", s.id, s.effect,
                s.origin.getX(), s.origin.getY(), s.origin.getZ(), s.dimension.location(), s.blocks, (now - s.createdTick) / 20, s.source));
        }
        for (TerrainJob job : JOBS) lines.add("running: " + job.label + " · " + job.remaining() + " left");
        return lines;
    }

    /** Last commit or undo report (empty before the first). */
    public static String lastReport() { return lastReport; }

    /** Snapshots kept, running jobs, commits, undos, blocks written and refusals by {@link Reason}. */
    public static int[] counters() {
        int[] out = new int[5 + REFUSED.length];
        out[0] = HISTORY.size(); out[1] = JOBS.size(); out[2] = commits; out[3] = undos;
        out[4] = (int) Math.min(Integer.MAX_VALUE, blocksWritten);
        System.arraycopy(REFUSED, 0, out, 5, REFUSED.length);
        return out;
    }

    /** True while commits or undos are writing (for tests and diagnostics). */
    public static boolean busy() { return !JOBS.isEmpty(); }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        for (Instance instance : INSTANCES.values())
            for (Run run : instance.runs) if (run != null && run.future != null) run.future.cancel(false);
        INSTANCES.clear();
        JOBS.clear();
        HISTORY.clear();
        UNKNOWN.clear();
        java.util.Arrays.fill(REFUSED, 0);
        commits = undos = 0;
        blocksWritten = 0;
        lastReport = "";
    }
}
