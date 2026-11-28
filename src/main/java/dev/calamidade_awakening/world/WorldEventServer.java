package dev.calamidade_awakening.world;

import com.mojang.logging.LogUtils;
import cyou.rimuru.rimulib.mc1211.api.NetApi;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.CueClock;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.WorldEventRules;
import dev.calamidade_awakening.api.v1.EffectSource;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.WorldEvents;
import dev.calamidade_awakening.config.ServerConfig;
import dev.calamidade_awakening.net.WorldEventEnd;
import dev.calamidade_awakening.net.WorldEventSync;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server side of the global events (0.12.0). Each event runs on the server's game time (the overworld clock that every
 * dimension shares), so its age is the same everywhere and a player who arrives late enters the right phase. Every tick
 * it runs the cues due (in the origin level), ends finished events and makes sure each player who should see an event
 * received its state ({@code world_event_v1}) and each who should not anymore received its end ({@code world_end_v1}):
 * login, dimension change and respawn are covered by this check and also handled right away by their events.
 * Persistent events are kept in {@link WorldEventData} and resumed when the server starts.
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID)
public final class WorldEventServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Global> ACTIVE = new ArrayList<>();
    private static long nextKey = 1;

    private WorldEventServer() {}

    /** One live global event. */
    static final class Global implements WorldEvents.Handle {
        private static final int MAX_CATCH_UP = 1200;
        final long key;
        final VisualKind kind;
        final ResourceLocation id;
        final WorldEvents.Scope scope;
        final ResourceKey<net.minecraft.world.level.Level> dimension;
        final Vec3 origin;
        final long tick, seed;
        final int priority;
        final boolean persistent;
        final MinecraftServer server;
        long released = -1, cuedThrough = -1;
        boolean ended;
        final Set<ServerPlayer> recipients = new HashSet<>();

        Global(MinecraftServer server, long key, VisualKind kind, WorldEvents.Scope scope,
               ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 origin, long tick, long seed, int priority,
               boolean persistent) {
            this.server = server; this.key = key; this.kind = kind; this.id = ResourceLocation.parse(kind.networkKey());
            this.scope = scope; this.dimension = dimension; this.origin = origin; this.tick = tick; this.seed = seed;
            this.priority = priority; this.persistent = persistent;
        }

        long age() { return WorldEventRules.age(server.overworld().getGameTime(), tick); }

        boolean finished() { return WorldEventRules.finished(kind.timeline(), kind.duration, age(), released); }

        ServerLevel level() { return server.getLevel(dimension); }

        WorldEventSync packet() {
            return new WorldEventSync(key, kind.networkKey(), scope, dimension.location(), origin, tick, seed, released, priority);
        }

        boolean sees(ServerPlayer player) {
            return WorldEventRules.applies(scope, dimension.location().toString(), player.level().dimension().location().toString());
        }

        /** Sends the state to players who should see it and have not received it, and the end to those who left it. */
        void dispatch() {
            var players = server.getPlayerList().getPlayers();
            recipients.retainAll(players);
            for (ServerPlayer player : players) dispatch(player);
        }

        void dispatch(ServerPlayer player) {
            boolean sees = sees(player);
            if (!sees && recipients.remove(player)) sendEnd(player);
            if (sees && NetApi.canSend(player, WorldEventSync.PACKET) && recipients.add(player)) NetApi.send(player, packet());
        }

        /** Forgets a player so the next dispatch sends the state again (dimension change, respawn, login). */
        void resend(ServerPlayer player) {
            recipients.remove(player);
            dispatch(player);
        }

        void sendEnd(ServerPlayer player) {
            if (NetApi.canSend(player, WorldEventEnd.PACKET)) NetApi.send(player, new WorldEventEnd(key));
        }

        void runCues() {
            var cues = kind.cues();
            ServerLevel level = level();
            if (cues.isEmpty() || level == null || ended) return;
            long now = age();
            if (now <= cuedThrough) return;
            if (!kind.sustained()) {
                long to = Math.min(now, kind.duration - 1L), from = cuedThrough;
                cuedThrough = now;
                for (var cue : cues) {
                    if (ended) return;
                    if (cue.tick() > from && cue.tick() <= to) run(cue, level, cue.tick(), cue.tick());
                }
                return;
            }
            long from = Math.max(cuedThrough + 1, now - MAX_CATCH_UP);
            cuedThrough = now;
            CueClock.walk(kind.timeline(), released, kind.duration, from, now, (real, local) -> {
                for (var cue : cues) {
                    if (ended) return false;
                    if (cue.tick() == local) run(cue, level, local, real);
                }
                return !ended;
            });
        }

        private void run(VisualKind.ScheduledCue cue, ServerLevel level, double local, long real) {
            try {
                cue.cue().run(new Effects.Context(level, origin, null, null, seed, cue.tick(), this, kind.timeline(), local,
                    real, 0, 0, EffectSource.script()));
            } catch (RuntimeException failure) {
                LOGGER.error("Cue at tick {} of world event {} failed", cue.tick(), kind.networkKey(), failure);
            }
        }

        void end() {
            ended = true;
            for (ServerPlayer player : recipients) sendEnd(player);
            recipients.clear();
            WorldEventData.changed(server);
        }

        @Override public boolean stop() {
            checkThread();
            if (ended || !ACTIVE.remove(this)) return false;
            end();
            return true;
        }

        @Override public boolean release() {
            checkThread();
            if (ended || !kind.sustained() || released >= 0) return false;
            released = Math.max(age(), cuedThrough + 1);
            // The new state (with the release age) goes to everyone who sees it.
            recipients.clear();
            dispatch();
            WorldEventData.changed(server);
            return true;
        }

        @Override public boolean alive() { return !ended && !finished(); }
        @Override public ResourceLocation id() { return id; }
        @Override public WorldEvents.Scope scope() { return scope; }
        @Override public boolean persistent() { return persistent; }
        @Override public int priority() { return priority; }

        private void checkThread() {
            if (!server.isSameThread()) throw new IllegalStateException("Call on the server thread");
        }

        /** One line for {@code /calamidade world list}. */
        String describe() {
            long age = age();
            String phase = released >= 0 ? "outro" : kind.sustained() && kind.timeline().localAge(age, -1) >= kind.timeline().sustainStart()
                ? "loop" : "intro";
            return String.format(java.util.Locale.ROOT, "#%d %s · %s in %s · %d s · %s · priority %d%s", key, kind.networkKey(),
                scope.name(), dimension.location(), age / 20, phase, priority, persistent ? " · persistent" : "");
        }
    }

    /** Implementation of {@link WorldEvents#start}. */
    public static Optional<WorldEvents.Handle> start(ResourceLocation id, WorldEvents.Scope scope, ServerLevel origin,
                                                     WorldEvents.Options options) {
        MinecraftServer server = origin.getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Call on the server thread");
        VisualKind kind = VisualKind.byId(VisualKind.localId(id.toString()));
        if (kind == null) throw new IllegalArgumentException("Unknown effect " + id);
        Vec3 position = options.position() != null ? options.position() : origin.getSharedSpawnPos().getCenter();
        if (scope == WorldEvents.Scope.AREA)
            return Effects.start(id, origin, Placement.at(position)).map(handle -> {
                // 0.15.0: area events show up in /calamidade world list and /calamidade fx list with their scope.
                dev.calamidade_awakening.net.StageNetwork.tag(handle, "AREA", "WorldEvents.start(AREA)");
                return new AreaHandle(handle, id, options.priority());
            });
        var decision = WorldEventRules.admit(ACTIVE.stream()
            .map(g -> new WorldEventRules.Active(g.kind.networkKey(), g.priority, g.key)).toList(),
            kind.networkKey(), options.priority(), ServerConfig.worldEventsMax());
        switch (decision.verdict()) {
            case DUPLICATE, REFUSE -> { return Optional.empty(); }
            case REPLACE -> ACTIVE.get(decision.victim()).stop();
            case ADMIT -> { }
        }
        Global global = new Global(server, nextKey++, kind, scope, origin.dimension(), position,
            server.overworld().getGameTime(), origin.getRandom().nextLong(), options.priority(), options.persistent());
        ACTIVE.add(global);
        global.dispatch();
        WorldEventData.changed(server);
        return Optional.of(global);
    }

    /** Implementation of {@link WorldEvents#get}. */
    public static Optional<WorldEvents.Handle> get(MinecraftServer server, ResourceLocation id) {
        for (Global g : ACTIVE) if (g.server == server && g.id.equals(id) && !g.ended) return Optional.of(g);
        return Optional.empty();
    }

    /** Implementation of {@link WorldEvents#active}. */
    public static List<WorldEvents.Handle> active(MinecraftServer server) {
        List<WorldEvents.Handle> out = new ArrayList<>();
        for (Global g : ACTIVE) if (g.server == server && !g.ended) out.add(g);
        return out;
    }

    /** Real age (ticks) of the active global event of an effect, or -1 (tests and diagnostics). */
    public static long ageOf(ResourceLocation id) {
        for (Global g : ACTIVE) if (g.id.equals(id) && !g.ended) return g.age();
        return -1;
    }

    /** Number of active global events (F7 of an integrated server, /calamidade status). */
    public static int count() { return ACTIVE.size(); }

    /** Effect ids and keys of the active events, for command suggestions. */
    static List<String> activeIds() {
        List<String> out = new ArrayList<>();
        for (Global g : ACTIVE) { out.add(g.id.toString()); out.add("#" + g.key); }
        return out;
    }

    /** 0.15.0: same lines, public, for {@code /calamidade fx list}. */
    public static List<String> describeAll() { return describe(); }

    /** Lines of {@code /calamidade world list}. */
    static List<String> describe() { return ACTIVE.stream().map(Global::describe).toList(); }

    /** Events matching a command argument: an effect id ({@code ns:path}) or a key ({@code #3} or {@code 3}). */
    static List<Global> matching(String target) {
        String t = target.startsWith("#") ? target.substring(1) : target;
        List<Global> out = new ArrayList<>();
        for (Global g : ACTIVE) if (g.id.toString().equals(target) || Long.toString(g.key).equals(t)) out.add(g);
        return out;
    }

    /** Persistent events to save. */
    static List<WorldEventData.Saved> persistent() {
        List<WorldEventData.Saved> out = new ArrayList<>();
        for (Global g : ACTIVE) if (g.persistent && !g.ended)
            out.add(new WorldEventData.Saved(g.kind.networkKey(), g.scope, g.dimension.location().toString(), g.origin.x,
                g.origin.y, g.origin.z, g.tick, g.seed, g.released, g.priority, g.cuedThrough));
        return out;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        for (Global g : List.copyOf(ACTIVE)) if (!g.ended) g.runCues();
        ACTIVE.removeIf(g -> {
            if (!g.ended && !g.finished()) return false;
            if (!g.ended) g.end();
            return true;
        });
        for (Global g : ACTIVE) g.dispatch();
        WorldEventData.touch(event.getServer());
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) for (Global g : ACTIVE) g.resend(player);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) for (Global g : ACTIVE) g.resend(player);
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) for (Global g : ACTIVE) g.resend(player);
    }

    /** Resumes the persistent events saved with the world, in the same phase (the game time kept running with it). */
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ACTIVE.clear();
        for (WorldEventData.Saved saved : WorldEventData.get(server).saved()) {
            VisualKind kind = VisualKind.byId(VisualKind.localId(saved.effect()));
            ResourceLocation dimension = ResourceLocation.tryParse(saved.dimension());
            ServerLevel level = dimension == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (kind == null || level == null || saved.scope() == WorldEvents.Scope.AREA) {
                LOGGER.warn("Dropping saved world event {} in {}: effect or dimension missing", saved.effect(), saved.dimension());
                continue;
            }
            Global g = new Global(server, nextKey++, kind, saved.scope(), level.dimension(), new Vec3(saved.x(), saved.y(), saved.z()),
                saved.tick(), saved.seed(), saved.priority(), true);
            g.released = saved.released();
            g.cuedThrough = saved.cuedThrough();
            if (g.finished()) continue;
            ACTIVE.add(g);
            LOGGER.info("Resumed world event {} at {} s", saved.effect(), g.age() / 20);
        }
        WorldEventData.changed(server);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        nextKey = 1;
    }

    /** AREA scope: an ordinary instance seen through the world event handle. */
    private record AreaHandle(Effects.Handle handle, ResourceLocation id, int priority) implements WorldEvents.Handle {
        @Override public boolean stop() { return handle.stop(); }
        @Override public boolean release() { return handle.release(); }
        @Override public boolean alive() { return handle.alive(); }
        @Override public WorldEvents.Scope scope() { return WorldEvents.Scope.AREA; }
        @Override public boolean persistent() { return false; }
    }
}
