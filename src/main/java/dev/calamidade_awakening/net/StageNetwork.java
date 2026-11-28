package dev.calamidade_awakening.net;

import com.mojang.logging.LogUtils;
import cyou.rimuru.rimulib.mc1211.api.AimApi;
import cyou.rimuru.rimulib.mc1211.api.NetApi;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.Basis;
import dev.calamidade_awakening.api.Cooldowns;
import dev.calamidade_awakening.api.SourceLimits;
import dev.calamidade_awakening.api.v1.EffectSource;
import dev.calamidade_awakening.config.ServerConfig;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.item.AwakeningSkills;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import java.util.*;

@EventBusSubscriber(modid = CalamidadeAwakening.ID)
public final class StageNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_LIVE = SourceLimits.TOTAL;
    private static final List<Live> ACTIVE = new ArrayList<>();
    /** Per source and per effect (0.10.0; per player and per skill since 0.5): one slow effect never blocks the others. */
    private static final Cooldowns COOLDOWNS = new Cooldowns();
    /** Starts refused by the limits since the server started, by verdict (diagnostics for /calamidade status). */
    private static final int[] REFUSED = new int[SourceLimits.Verdict.values().length];
    private static long nextOrder;

    private static final class Live {
        /** Most real ticks a sustained instance replays in one server tick if its clock ever jumps. */
        private static final int MAX_CATCH_UP = 1200;
        final ServerLevel level;
        final StageEvent event;
        final VisualKind kind;
        final Entity anchor;
        /** Who started it (0.10.0) and the block entity that bounds its life, or null. */
        final EffectSource source;
        final BlockEntity block;
        final Set<ServerPlayer> recipients = new HashSet<>();
        Effects.Handle handle;
        /**
         * Without sustain: highest instance tick whose cues already ran. With sustain: highest real age already
         * processed (the local tick repeats on every lap). -1 before the first server tick.
         */
        long cuedThrough = -1;
        /** Real age at which a sustained instance was released, or -1 (0.7.0). */
        long releasedAt = -1;
        /** Set once the instance is stopped or expired; no cue may run afterwards, even later in the same tick. */
        boolean ended;
        /**
         * 0.15.0: lifecycle owner, distinct from the source (who started it) and the anchor (where it is): the boss
         * controller (entity UUID) of an {@code EffectSource.boss} start, or null. A sustained instance is released
         * when its owner's controller ends, even if the handle that held it was lost.
         */
        UUID owner;
        /** 0.15.0: how it was started, for diagnostics: INSTANCE, or AREA for an area world event. */
        String scope = "INSTANCE";
        /** 0.15.0: who holds it, for diagnostics (boss phase, area event...). */
        String note = "";
        /** 0.15.0: set when the watchdog released it because its owner was gone. */
        boolean orphanReleased;

        Live(ServerLevel level, StageEvent event, VisualKind kind, Entity anchor, EffectSource source, BlockEntity block) {
            this.level = level; this.event = event; this.kind = kind; this.anchor = anchor; this.source = source; this.block = block;
        }

        StageEvent event() { return event; }

        long age() { return level.getGameTime() - event.tick(); }

        boolean lost() {
            return (anchor != null && (anchor.isRemoved() || anchor.level() != level)) ||
                (anchor == null && !level.hasChunkAt(BlockPos.containing(event.center()))) ||
                // 0.10.0: the bound block entity was broken, replaced or unloaded (setRemoved in every case).
                (block != null && block.isRemoved());
        }

        /** Without sustain: the real age reaches the duration. With sustain: only after the release plays out. */
        boolean finished() {
            if (lost()) return true;
            if (!kind.sustained()) return age() >= kind.duration;
            return releasedAt >= 0 && age() >= kind.timeline().endAge(releasedAt, kind.duration);
        }

        /** Current frame (0.10.0): the start orientation, or the anchor's body yaw plus the yaw offset. */
        Basis basis() { return basisOf(event, anchor); }

        Vec3 center() { return anchor == null ? event.center() : anchoredCenter(anchor, event.offset(), basis(), kind.socket()); }

        void dispatch() {
            Vec3 center = center();
            recipients.retainAll(level.players());
            for (ServerPlayer player : level.players()) {
                boolean visible = player.position().distanceToSqr(center) <= kind.range * kind.range;
                if (!visible && recipients.remove(player)) sendEnd(player);
                if (visible && NetApi.canSend(player, StageEvent.PACKET) && recipients.add(player)) {
                    NetApi.send(player, event);
                    // Late arrivals of a released instance get the release right after the start.
                    if (releasedAt >= 0) sendRelease(player);
                    // 0.11.0: and the state of its terrain edits (refused, structure stand-in, committed).
                    dev.calamidade_awakening.terrain.TerrainWorks.sendState(player, event.order());
                }
            }
        }

        /** Runs the cues due since the previous server tick, on the timeline clock. */
        void runCues() {
            List<VisualKind.ScheduledCue> cues = kind.cues();
            if (cues.isEmpty() || lost()) return;
            if (kind.sustained()) {
                runSustainedCues(cues);
                return;
            }
            // Without sustain the local age is the real age: same behavior as 0.6.
            long age = Math.min(age(), kind.duration - 1L);
            if (age <= cuedThrough) return;
            long from = cuedThrough;
            cuedThrough = age;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(event.caster());
            Vec3 center = center();
            for (VisualKind.ScheduledCue scheduled : cues) {
                if (ended) return; // a previous cue (or anything it triggered) stopped this instance
                if (scheduled.tick() <= from || scheduled.tick() > age) continue;
                run(scheduled, caster, center, scheduled.tick(), scheduled.tick());
            }
        }

        /** Walks every real tick since the last call; a local tick inside the loop repeats on every lap. */
        private void runSustainedCues(List<VisualKind.ScheduledCue> cues) {
            long now = age();
            if (now <= cuedThrough) return;
            long from = Math.max(cuedThrough + 1, now - MAX_CATCH_UP);
            cuedThrough = now;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(event.caster());
            Vec3 center = center();
            dev.calamidade_awakening.api.CueClock.walk(kind.timeline(), releasedAt, kind.duration, from, now, (real, local) -> {
                for (VisualKind.ScheduledCue scheduled : cues) {
                    if (ended) return false; // a cue stopped this instance
                    if (scheduled.tick() == local) run(scheduled, caster, center, local, real);
                }
                return !ended;
            });
        }

        private void run(VisualKind.ScheduledCue scheduled, ServerPlayer caster, Vec3 center, double local, long real) {
            try {
                Basis basis = basis();
                scheduled.cue().run(new Effects.Context(level, center, caster, anchor, event.seed(), scheduled.tick(),
                    handle, kind.timeline(), local, real, basis.yaw, basis.pitch, source));
            } catch (RuntimeException failure) {
                LOGGER.error("Cue at tick {} of {} failed", scheduled.tick(), kind.networkKey(), failure);
            }
        }

        /**
         * The jump happens at the first real tick whose cues have not run yet, so server cues and client visuals
         * switch to the exit on the same tick.
         */
        void release() {
            releasedAt = Math.max(age(), cuedThrough + 1);
            for (ServerPlayer player : recipients) if (level.players().contains(player)) sendRelease(player);
        }

        void sendRelease(ServerPlayer player) {
            if (NetApi.canSend(player, ReleaseEffect.PACKET)) NetApi.send(player, new ReleaseEffect(event.order(), releasedAt));
        }

        void sendEnd(ServerPlayer player) {
            if (NetApi.canSend(player, EndEffect.PACKET)) NetApi.send(player, new EndEffect(event.order()));
        }

        Info info() {
            String src = source.kind().name().toLowerCase(java.util.Locale.ROOT) + (source.uuid() != null ? " " + shortName(source.uuid()) : "");
            String own = owner == null ? "-" : "boss " + shortName(owner) + (dev.calamidade_awakening.boss.BossServer.isBoss(owner) ? "" : " (ended)");
            String anc = anchor == null ? "fixed" : anchor.getType().toShortString() + " " + shortName(anchor.getUUID());
            long a = age();
            String st = !kind.sustained() ? "ephemeral " + a + "/" + kind.duration
                : releasedAt >= 0 ? "exiting (released at " + releasedAt + ")" + (orphanReleased ? " · orphan" : "")
                : kind.timeline().localAge(a, -1) >= kind.timeline().sustainStart() ? "loop" : "intro";
            boolean orphan = owner != null && kind.sustained() && !dev.calamidade_awakening.boss.BossServer.isBoss(owner);
            return new Info(event.order(), kind.networkKey(), scope, level.dimension().location().toString(), src, own, anc,
                st + " · " + a / 20 + " s", center(), note, orphan);
        }

        void end() {
            ended = true;
            dev.calamidade_awakening.terrain.TerrainWorks.ended(event.order());
            for (ServerPlayer player : recipients) if (level.players().contains(player)) sendEnd(player);
            recipients.clear();
        }
    }

    private static void serverThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Call on the server thread");
    }

    /**
     * What a start asks for (0.10.0): fixed position or anchor (then {@code position} is the offset in the instance
     * frame), caster (null for none), yaw and pitch in degrees and whether the yaw follows the anchor's body.
     */
    record Spawn(Vec3 position, Entity anchor, UUID caster, float yaw, float pitch, boolean followBody,
                 EffectSource source, int cooldown, BlockEntity block) {
        /** The source the engine picks when none was given: the caster, else the anchor, else a script. */
        EffectSource effectiveSource() {
            if (source != null) return source;
            if (caster != null) return EffectSource.player(caster);
            if (anchor != null) return EffectSource.entity(anchor);
            return EffectSource.script();
        }
    }

    /** Body yaw of an entity on the server: {@code yBodyRot} for living entities, the yaw for the others. */
    static float bodyYaw(Entity entity) { return entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot(); }

    /** Frame of an instance on the server: fixed, or the anchor's body yaw plus the yaw offset. */
    static Basis basisOf(StageEvent event, Entity anchor) {
        return event.followBody() && anchor != null ? Basis.of(bodyYaw(anchor) + event.yaw(), event.pitch()) : event.basis();
    }

    /**
     * 0.13.0: with a rig anchor ({@code Effects.anchorSocket}), the server approximation of the anchor plus the offset
     * in the anchor's frame (x left, y up, z forward); otherwise the feet, as before.
     */
    static Vec3 anchoredCenter(Entity anchor, Vec3 offset, Basis basis, String socket) {
        if (socket == null) return anchoredCenter(anchor, offset, basis);
        return dev.calamidade_awakening.api.v1.RigAnchors.approximate(anchor, socket).at(-offset.x, offset.y, offset.z);
    }

    /** Anchor feet plus the offset turned by the instance frame (world axes when the frame is the identity). */
    static Vec3 anchoredCenter(Entity anchor, Vec3 offset, Basis basis) {
        if (basis.identity()) return anchor.position().add(offset);
        return anchor.position().add(basis.worldX(offset.x, offset.y, offset.z), basis.worldY(offset.x, offset.y, offset.z),
            basis.worldZ(offset.x, offset.y, offset.z));
    }

    public static Optional<Effects.Handle> start(ResourceLocation id, ServerLevel level, Vec3 position, Entity anchor, UUID caster) {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))
            throw new IllegalArgumentException("Non-finite anchor coordinates");
        return start(id, level, new Spawn(position, anchor, caster, 0, 0, false, null, 0, null));
    }

    /** 0.10.0: start from a public {@link dev.calamidade_awakening.api.v1.Placement} (already validated). */
    public static Optional<Effects.Handle> start(ResourceLocation id, ServerLevel level, dev.calamidade_awakening.api.v1.Placement placement) {
        return start(id, level, new Spawn(placement.position(), placement.anchor(),
            placement.caster() == null ? null : placement.caster().getUUID(), placement.yaw(), placement.pitch(),
            placement.followBody(), placement.source(), placement.cooldown(), placement.block()));
    }

    private static Optional<Effects.Handle> start(ResourceLocation id, ServerLevel level, Spawn spawn) {
        serverThread(level);
        VisualKind kind = VisualKind.byId(VisualKind.localId(id.toString()));
        if (kind == null) throw new IllegalArgumentException("Unknown effect " + id);
        return start(kind, level, spawn);
    }

    private static Optional<Effects.Handle> start(VisualKind kind, ServerLevel level, Spawn spawn) {
        Entity anchor = spawn.anchor();
        UUID caster = spawn.caster() == null ? new UUID(0, 0) : spawn.caster();
        if (ACTIVE.size() >= MAX_LIVE || (anchor != null && (anchor.isRemoved() || anchor.level() != level))) return Optional.empty();
        BlockEntity block = spawn.block();
        if (block != null && (block.isRemoved() || block.getLevel() != level)) return Optional.empty();
        // 0.10.0: cooldown per source and effect, then the per-source limits and the slots reserved for players.
        EffectSource source = spawn.effectiveSource();
        long now = level.getGameTime();
        if (spawn.cooldown() > 0 && !COOLDOWNS.ready(source, kind.id, now)) return Optional.empty();
        SourceLimits.Verdict verdict = admit(source);
        if (!verdict.ok()) {
            REFUSED[verdict.ordinal()]++;
            return Optional.empty();
        }
        boolean follow = spawn.followBody() && anchor != null;
        Basis basis = follow ? Basis.of(bodyYaw(anchor) + spawn.yaw(), spawn.pitch()) : Basis.of(spawn.yaw(), spawn.pitch());
        Vec3 position = spawn.position();
        Vec3 center = anchor == null ? position : anchoredCenter(anchor, position, basis, kind.socket());
        if (!level.hasChunkAt(BlockPos.containing(center))) return Optional.empty();
        long order = nextOrder++;
        StageEvent event = new StageEvent(kind.id, order, level.getGameTime(), caster,
            level.dimension().location(), center, level.getRandom().nextLong(),
            anchor == null ? -1 : anchor.getId(), anchor == null ? null : anchor.getUUID(), anchor == null ? Vec3.ZERO : position,
            spawn.yaw(), spawn.pitch(), follow, source.kind());
        Live live = new Live(level, event, kind, anchor, source, block);
        if (source.bossController()) live.owner = source.uuid();
        if (spawn.cooldown() > 0) COOLDOWNS.start(source, kind.id, now, spawn.cooldown());
        var serverRef = new java.lang.ref.WeakReference<>(level.getServer());
        live.handle = new LiveHandle(order) {
            @Override public boolean stop() {
                Live target = find(serverRef.get(), order);
                if (target == null) return false;
                ACTIVE.remove(target);
                target.end();
                return true;
            }

            @Override public boolean release() {
                Live target = find(serverRef.get(), order);
                if (target == null || target.ended || !target.kind.sustained() || target.releasedAt >= 0) return false;
                target.release();
                return true;
            }

            @Override public boolean alive() {
                Live target = find(serverRef.get(), order);
                return target != null && !target.ended && !target.finished();
            }
        };
        ACTIVE.add(live);
        // 0.11.0: safety rules and the plan of its terrain edits, before the first dispatch sends their state.
        dev.calamidade_awakening.terrain.TerrainWorks.started(level, kind, event, anchor, source);
        live.dispatch();
        return Optional.of(live.handle);
    }

    /** Handle of a live instance; the order identifies it for the terrain engine (0.11.0). */
    private abstract static class LiveHandle implements Effects.Handle {
        final long order;
        LiveHandle(long order) { this.order = order; }
    }

    /** Order of the instance behind a handle from {@link #start}, or -1 for other handles (0.11.0). */
    public static long orderOf(Effects.Handle handle) { return handle instanceof LiveHandle live ? live.order : -1; }

    /** 0.15.0: marks how an instance was started (diagnostics): scope label and a note (holder). */
    public static void tag(Effects.Handle handle, String scope, String note) {
        long order = orderOf(handle);
        for (Live live : ACTIVE) if (live.event.order() == order) {
            if (scope != null) live.scope = scope;
            if (note != null) live.note = note;
        }
    }

    /**
     * 0.15.0: releases (cinematic exit) every sustained, not yet released instance owned by this boss controller and
     * stops nothing else: one-shot instances it started play out on their own, as intended. Called when a controller
     * ends (death, removal, unload, detach, failures). Returns how many were released.
     */
    public static int releaseOwned(UUID owner) {
        int n = 0;
        for (Live live : List.copyOf(ACTIVE)) {
            if (live.ended || !owner.equals(live.owner) || !live.kind.sustained() || live.releasedAt >= 0) continue;
            live.release();
            n++;
        }
        return n;
    }

    /** 0.15.0: one live instance as seen by the diagnostics commands. */
    public record Info(long order, String effect, String scope, String dimension, String source, String owner,
                       String anchor, String state, Vec3 center, String note, boolean orphan) {}

    /** 0.15.0: every live instance (server thread), for {@code /calamidade fx list}. */
    public static List<Info> describe() {
        List<Info> out = new ArrayList<>();
        for (Live live : ACTIVE) if (!live.ended) out.add(live.info());
        return out;
    }

    /** 0.15.0: stops ({@code stop}) or releases one instance by order; false when there is none or it cannot release. */
    public static boolean act(long order, boolean stop) {
        for (Live live : List.copyOf(ACTIVE)) {
            if (live.event.order() != order || live.ended) continue;
            if (stop) {
                ACTIVE.remove(live);
                live.end();
                return true;
            }
            if (!live.kind.sustained() || live.releasedAt >= 0) return false;
            live.release();
            return true;
        }
        return false;
    }

    /**
     * 0.15.0, tests only: drops an instance on the server WITHOUT telling its players, as if its end packet were lost.
     * The client must drop it at the next live list.
     */
    public static boolean forgetSilently(long order) {
        return ACTIVE.removeIf(live -> {
            if (live.event.order() != order) return false;
            live.ended = true;
            live.recipients.clear();
            return true;
        });
    }

    /** 0.15.0: instances released by the orphan watchdog since the server started (diagnostics, tests). */
    public static int orphansReleased() { return orphans; }

    private static int orphans;

    /** Players that received a live instance, empty when it is gone (0.11.0, terrain state broadcasts). */
    public static java.util.Collection<ServerPlayer> recipients(long order) {
        for (Live live : ACTIVE) if (live.event().order() == order) return List.copyOf(live.recipients);
        return List.of();
    }

    /** Live instance with this order, or null when the server is gone or the instance already ended. */
    private static Live find(net.minecraft.server.MinecraftServer server, long order) {
        if (server == null || server.isStopped()) return null;
        if (!server.isSameThread()) throw new IllegalStateException("Stop or release on the server thread");
        for (Live live : ACTIVE) if (live.event().order() == order) return live;
        return null;
    }

    /** Admission of a new instance of this source against the live ones and the server config (0.10.0). */
    private static SourceLimits.Verdict admit(EffectSource source) {
        int nonPlayer = 0, same = 0;
        for (Live live : ACTIVE) {
            if (live.source.kind() != EffectSource.Kind.PLAYER) nonPlayer++;
            if (live.source.equals(source)) same++;
        }
        // 0.13.0: maxPerBoss only for controller starts of an entity that really has a controller attached.
        boolean boss = source.bossController() && dev.calamidade_awakening.boss.BossServer.isBoss(source.uuid());
        return SourceLimits.admit(ServerConfig.rules(), source.kind(), boss, ACTIVE.size(), nonPlayer, same);
    }

    /** Number of live server instances, for diagnostics. */
    public static int liveCount() { return ACTIVE.size(); }

    /** Live server instances by source kind, in {@link EffectSource.Kind} order (0.10.0). */
    public static int[] liveBySource() {
        int[] counts = new int[EffectSource.Kind.values().length];
        for (Live live : ACTIVE) counts[live.source.kind().ordinal()]++;
        return counts;
    }

    /** Starts refused by the limits since the server started, in {@link SourceLimits.Verdict} order (0.10.0). */
    public static int[] refused() { return REFUSED.clone(); }

    /** Ticks left of a source's cooldown for an effect (0.10.0). */
    public static long cooldownLeft(EffectSource source, ResourceLocation id, ServerLevel level) {
        serverThread(level);
        return COOLDOWNS.remaining(source, kindOf(id).id, level.getGameTime());
    }

    /** Starts (ticks &gt; 0) or clears (0) a source's cooldown for an effect (0.10.0). */
    public static void startCooldown(EffectSource source, ResourceLocation id, ServerLevel level, int ticks) {
        serverThread(level);
        int visual = kindOf(id).id;
        if (ticks <= 0) COOLDOWNS.cancel(source, visual);
        else COOLDOWNS.start(source, visual, level.getGameTime(), ticks);
    }

    private static VisualKind kindOf(ResourceLocation id) {
        VisualKind kind = VisualKind.byId(VisualKind.localId(id.toString()));
        if (kind == null) throw new IllegalArgumentException("Unknown effect " + id);
        return kind;
    }

    private StageNetwork() {}
    public static void register() {
        NetApi.toServer(CastRequest.PACKET);
        NetApi.toClient(StageEvent.PACKET);
        NetApi.toClient(EndEffect.PACKET);
        NetApi.toClient(ReleaseEffect.PACKET);
        NetApi.toClient(TerrainSync.PACKET);
        // 0.12.0: global events.
        NetApi.toClient(WorldEventSync.PACKET);
        NetApi.toClient(WorldEventEnd.PACKET);
        NetApi.toClient(SkyProfileSync.PACKET);
        // 0.13.0: boss bar state beyond the vanilla bar.
        NetApi.toClient(BossSync.PACKET);
        // 0.15.0: periodic list of the instances a player should show (reconciliation of missed ends).
        NetApi.toClient(LiveList.PACKET);
        NetApi.receive(CastRequest.PACKET, (request, ctx) -> ctx.server().execute(() -> {
            if (ctx.player() != null) AwakeningSkills.cast(ctx.player(), request.visual(), request.power());
        }));
    }

    /** Authoritative origin; the client never supplies geometry, intensity or a target. */
    public static boolean request(ServerPlayer player, int visual, float power) {
        VisualKind kind = VisualKind.byId(visual);
        if (kind == null || !player.isAlive() || player.isSpectator() || ACTIVE.size() >= MAX_LIVE) return false;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        EffectSource source = EffectSource.player(player);
        if (!COOLDOWNS.ready(source, kind.id, now)) return false;
        Vec3 center = AimApi.ground(player, 32, 12).add(0, 2, 0);
        var addon = kind.addon();
        if (kind.id < 0 && addon == null) return false;
        boolean accepted = addon == null ? Effects.start(ResourceLocation.parse(kind.networkKey()), player, center).isPresent()
            : addon.action().cast(player, center, addon.power() == null ? 1 : addon.power().clamp(power));
        if (!accepted) return false;
        COOLDOWNS.start(source, kind.id, now, addon == null ? kind.duration + 10 : addon.card().cooldown());
        return true;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        // Snapshot: a cue may stop its own instance or start new ones.
        for (Live live : List.copyOf(ACTIVE)) if (!live.ended) live.runCues();
        // 0.11.0: terrain plans, events and commits, after the cues so a commit writes in its marker's tick.
        dev.calamidade_awakening.terrain.TerrainWorks.tick(event.getServer());
        ACTIVE.removeIf(live -> {
            if (!live.finished()) return false;
            live.end();
            return true;
        });
        for (Live live : ACTIVE) live.dispatch();
        long now = event.getServer().overworld().getGameTime();
        COOLDOWNS.prune(now);
        // 0.15.0: a sustained instance whose owner controller is gone is released (once, with its exit), whatever held
        // its handle; a short grace covers a controller being resumed on the next tick.
        if (now % 20 == 0) for (Live live : List.copyOf(ACTIVE)) {
            if (live.ended || live.owner == null || !live.kind.sustained() || live.releasedAt >= 0 || live.age() < 40) continue;
            if (dev.calamidade_awakening.boss.BossServer.isBoss(live.owner)) continue;
            LOGGER.warn("Instance {} of {} outlived its boss controller {}: released (orphan)", live.event.order(),
                live.kind.networkKey(), live.owner);
            live.orphanReleased = true;
            live.release();
            orphans++;
        }
        // 0.15.0: every 5 s each player gets the list of instances it should show; the client drops any other one
        // (a missed end). Only players that have, or just had, an instance.
        if (now % RECONCILE_PERIOD == 0) reconcile(event.getServer());
    }

    /** Ticks between two live lists sent to each player (0.15.0). */
    static final int RECONCILE_PERIOD = 100;
    private static final Set<UUID> RECONCILED = new HashSet<>();

    private static void reconcile(net.minecraft.server.MinecraftServer server) {
        Map<ServerPlayer, List<Long>> lists = new HashMap<>();
        for (Live live : ACTIVE) if (!live.ended)
            for (ServerPlayer p : live.recipients) lists.computeIfAbsent(p, k -> new ArrayList<>()).add(live.event.order());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<Long> orders = lists.getOrDefault(player, List.of());
            boolean had = RECONCILED.contains(player.getUUID());
            if (orders.isEmpty() && !had) continue;
            if (!NetApi.canSend(player, LiveList.PACKET)) continue;
            NetApi.send(player, new LiveList(orders.stream().mapToLong(Long::longValue).toArray()));
            if (orders.isEmpty()) RECONCILED.remove(player.getUUID()); else RECONCILED.add(player.getUUID());
        }
    }

    private static String shortName(UUID uuid) { return uuid.toString().substring(0, 8); }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        RECONCILED.clear();
        orphans = 0;
        ACTIVE.clear();
        COOLDOWNS.clear();
        java.util.Arrays.fill(REFUSED, 0);
    }
}
