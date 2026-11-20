package dev.calamidade_awakening.net;

import com.mojang.logging.LogUtils;
import cyou.rimuru.rimulib.mc1211.api.AimApi;
import cyou.rimuru.rimulib.mc1211.api.NetApi;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.Cooldowns;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.item.AwakeningSkills;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
    private static final int MAX_LIVE = 64;
    private static final List<Live> ACTIVE = new ArrayList<>();
    /** Per player and per skill: one slow skill no longer blocks the others. */
    private static final Cooldowns COOLDOWNS = new Cooldowns();
    private static long nextOrder;

    private static final class Live {
        final ServerLevel level;
        final StageEvent event;
        final VisualKind kind;
        final Entity anchor;
        final Set<ServerPlayer> recipients = new HashSet<>();
        Effects.Handle handle;
        /** Highest instance tick whose cues already ran; -1 before the first server tick. */
        long cuedThrough = -1;
        /** Set once the instance is stopped or expired; no cue may run afterwards, even later in the same tick. */
        boolean ended;

        Live(ServerLevel level, StageEvent event, VisualKind kind, Entity anchor) {
            this.level = level; this.event = event; this.kind = kind; this.anchor = anchor;
        }

        StageEvent event() { return event; }

        long age() { return level.getGameTime() - event.tick(); }

        boolean lost() {
            return (anchor != null && (anchor.isRemoved() || anchor.level() != level)) ||
                (anchor == null && !level.hasChunkAt(BlockPos.containing(event.center())));
        }

        boolean finished() { return age() >= kind.duration || lost(); }

        Vec3 center() { return anchor == null ? event.center() : anchor.position().add(event.offset()); }

        void dispatch() {
            Vec3 center = center();
            recipients.retainAll(level.players());
            for (ServerPlayer player : level.players()) {
                boolean visible = player.position().distanceToSqr(center) <= kind.range * kind.range;
                if (!visible && recipients.remove(player)) sendEnd(player);
                if (visible && NetApi.canSend(player, StageEvent.PACKET) && recipients.add(player))
                    NetApi.send(player, event);
            }
        }

        /** Runs every cue scheduled in (cuedThrough, age], clamped to the effect duration. */
        void runCues() {
            List<VisualKind.ScheduledCue> cues = kind.cues();
            if (cues.isEmpty() || lost()) return;
            long age = Math.min(age(), kind.duration - 1L);
            if (age <= cuedThrough) return;
            long from = cuedThrough;
            cuedThrough = age;
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(event.caster());
            Vec3 center = center();
            for (VisualKind.ScheduledCue scheduled : cues) {
                if (ended) return; // a previous cue (or anything it triggered) stopped this instance
                if (scheduled.tick() <= from || scheduled.tick() > age) continue;
                try {
                    scheduled.cue().run(new Effects.Context(level, center, caster, anchor, event.seed(), scheduled.tick(), handle));
                } catch (RuntimeException failure) {
                    LOGGER.error("Cue at tick {} of {} failed", scheduled.tick(), kind.networkKey(), failure);
                }
            }
        }

        void sendEnd(ServerPlayer player) {
            if (NetApi.canSend(player, EndEffect.PACKET)) NetApi.send(player, new EndEffect(event.order()));
        }

        void end() {
            ended = true;
            for (ServerPlayer player : recipients) if (level.players().contains(player)) sendEnd(player);
            recipients.clear();
        }
    }

    private static void serverThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Call on the server thread");
    }

    public static Optional<Effects.Handle> start(ResourceLocation id, ServerLevel level, Vec3 position, Entity anchor, UUID caster) {
        serverThread(level);
        VisualKind kind = VisualKind.byId(VisualKind.localId(id.toString()));
        if (kind == null) throw new IllegalArgumentException("Unknown effect " + id);
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))
            throw new IllegalArgumentException("Non-finite anchor coordinates");
        return start(kind, level, position, anchor, caster == null ? new UUID(0, 0) : caster);
    }

    private static Optional<Effects.Handle> start(VisualKind kind, ServerLevel level, Vec3 position, Entity anchor, UUID caster) {
        if (ACTIVE.size() >= MAX_LIVE || (anchor != null && (anchor.isRemoved() || anchor.level() != level))) return Optional.empty();
        Vec3 center = anchor == null ? position : anchor.position().add(position);
        if (!level.hasChunkAt(BlockPos.containing(center))) return Optional.empty();
        long order = nextOrder++;
        StageEvent event = new StageEvent(kind.id, order, level.getGameTime(), caster,
            level.dimension().location(), center, level.getRandom().nextLong(),
            anchor == null ? -1 : anchor.getId(), anchor == null ? null : anchor.getUUID(), anchor == null ? Vec3.ZERO : position);
        Live live = new Live(level, event, kind, anchor);
        var serverRef = new java.lang.ref.WeakReference<>(level.getServer());
        live.handle = () -> {
            var server = serverRef.get();
            if (server == null || server.isStopped()) return false;
            if (!server.isSameThread()) throw new IllegalStateException("Stop on the server thread");
            for (var it = ACTIVE.iterator(); it.hasNext();) {
                Live target = it.next();
                if (target.event().order() != order) continue;
                it.remove();
                target.end();
                return true;
            }
            return false;
        };
        ACTIVE.add(live);
        live.dispatch();
        return Optional.of(live.handle);
    }

    /** Number of live server instances, for diagnostics. */
    public static int liveCount() { return ACTIVE.size(); }

    private StageNetwork() {}
    public static void register() {
        NetApi.toServer(CastRequest.PACKET);
        NetApi.toClient(StageEvent.PACKET);
        NetApi.toClient(EndEffect.PACKET);
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
        if (!COOLDOWNS.ready(player.getUUID(), kind.id, now)) return false;
        Vec3 center = AimApi.ground(player, 32, 12).add(0, 2, 0);
        var addon = kind.addon();
        if (kind.id < 0 && addon == null) return false;
        boolean accepted = addon == null ? Effects.start(ResourceLocation.parse(kind.networkKey()), player, center).isPresent()
            : addon.action().cast(player, center, addon.power() == null ? 1 : addon.power().clamp(power));
        if (!accepted) return false;
        COOLDOWNS.start(player.getUUID(), kind.id, now, addon == null ? kind.duration + 10 : addon.card().cooldown());
        return true;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        // Snapshot: a cue may stop its own instance or start new ones.
        for (Live live : List.copyOf(ACTIVE)) if (!live.ended) live.runCues();
        ACTIVE.removeIf(live -> {
            if (!live.finished()) return false;
            live.end();
            return true;
        });
        for (Live live : ACTIVE) live.dispatch();
        COOLDOWNS.prune(event.getServer().overworld().getGameTime());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        ACTIVE.clear();
        COOLDOWNS.clear();
    }
}
