package dev.calamidade_awakening.boss;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cyou.rimuru.rimulib.mc1211.api.NetApi;
import dev.calamidade_awakening.CalamidadeAwakening;
import dev.calamidade_awakening.api.BossBarMath;
import dev.calamidade_awakening.api.BossMachine;
import dev.calamidade_awakening.api.v1.Boss;
import dev.calamidade_awakening.api.v1.Bosses;
import dev.calamidade_awakening.api.v1.EffectSource;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.WorldEvents;
import dev.calamidade_awakening.config.ServerConfig;
import dev.calamidade_awakening.net.BossSync;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server side of the boss controllers (0.13.0): the authoritative phase machine of every attached boss, its bar,
 * its phase effects, persistence on the entity and cleanup. Server thread only.
 */
@EventBusSubscriber(modid = CalamidadeAwakening.ID)
public final class BossServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Callback failures in a row that detach a controller. */
    static final int MAX_FAILURES = 3;
    /** Ticks between two reviews of who sees a bar. */
    static final int VIEWER_PERIOD = 10;

    /** What is saved on the entity: the boss id and the machine state. */
    public record Saved(String boss, String phase, long phaseTicks, int hurt, String pending) {
        static final Saved EMPTY = new Saved("", "", 0, 0, "");
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("boss").forGetter(Saved::boss),
            Codec.STRING.fieldOf("phase").forGetter(Saved::phase),
            Codec.LONG.fieldOf("phase_ticks").forGetter(Saved::phaseTicks),
            Codec.INT.fieldOf("hurt").forGetter(Saved::hurt),
            Codec.STRING.optionalFieldOf("pending", "").forGetter(Saved::pending)).apply(i, Saved::new));
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CalamidadeAwakening.ID);
    /** Boss state saved with the entity (also copied when it changes dimension). */
    public static final Supplier<AttachmentType<Saved>> SAVED = ATTACHMENTS.register("boss",
        () -> AttachmentType.builder(() -> Saved.EMPTY).serialize(Saved.CODEC, s -> !s.boss().isEmpty()).build());

    private static final Map<UUID, Runtime> ACTIVE = new LinkedHashMap<>();

    private BossServer() {}

    /** Registers the attachment type (mod constructor). */
    public static void register(IEventBus modBus) { ATTACHMENTS.register(modBus); }

    // ---------------------------------------------------------------- public entry points (through Bosses)

    public static Optional<Bosses.Handle> attach(LivingEntity entity, Boss boss) {
        if (!(entity.level() instanceof ServerLevel level)) throw new IllegalStateException("Attach bosses on the server");
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Call on the server thread");
        if (entity instanceof Player || entity.isRemoved() || !entity.isAlive()) return Optional.empty();
        Runtime existing = ACTIVE.get(entity.getUUID());
        if (existing != null && existing.entity == entity) {
            return existing.boss == boss ? Optional.of(existing) : Optional.empty();
        }
        if (existing != null) existing.end(false);  // a stale controller of an older copy of this entity
        if (ACTIVE.size() >= ServerConfig.bossMaxActive()) return Optional.empty();
        Runtime runtime = new Runtime(boss, entity, level, new BossMachine.State());
        ACTIVE.put(entity.getUUID(), runtime);
        runtime.save();
        runtime.enter(0, true);
        runtime.viewers(true);
        return runtime.attached ? Optional.of(runtime) : Optional.empty();
    }

    public static Optional<Bosses.Handle> get(Entity entity) {
        Runtime runtime = ACTIVE.get(entity.getUUID());
        return runtime != null && runtime.entity == entity && runtime.attached ? Optional.of(runtime) : Optional.empty();
    }

    public static List<Bosses.Handle> active(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Call on the server thread");
        return List.copyOf(ACTIVE.values());
    }

    /**
     * True when the entity with this UUID has a live controller: the admission of {@code EffectSource.boss} starts
     * (maxPerBoss) checks it, so the mark alone never raises the limit.
     */
    public static boolean isBoss(UUID entity) {
        Runtime runtime = ACTIVE.get(entity);
        return runtime != null && runtime.attached && !runtime.entity.isRemoved();
    }

    /** Diagnostics for /calamidade status and the command. */
    public static int count() { return ACTIVE.size(); }

    // ---------------------------------------------------------------- runtime

    static final class Runtime implements Bosses.Handle, Boss.Context {
        final Boss boss;
        final LivingEntity entity;
        final ServerLevel level;
        final BossMachine.State state;
        final ServerBossEvent bar;
        final Set<String> signals = new HashSet<>();
        Effects.Handle sustain, area, sequence;
        WorldEvents.Handle world;
        long phaseStart;
        int failures;
        boolean failedThisTick, attached = true;
        float sentProgress = -1;
        long sentTick;
        final float[] marks;

        Runtime(Boss boss, LivingEntity entity, ServerLevel level, BossMachine.State state) {
            this.boss = boss; this.entity = entity; this.level = level; this.state = state;
            Boss.Bar spec = boss.bar();
            if (spec != null) {
                bar = new ServerBossEvent(spec.name(), color(spec.color()), overlay(spec.overlay()));
                bar.setDarkenScreen(spec.darken());
                bar.setPlayBossMusic(spec.music());
                bar.setCreateWorldFog(spec.fog());
                bar.setProgress(BossBarMath.progress(entity.getHealth(), entity.getMaxHealth()));
                marks = spec.segments() ? BossBarMath.marks(BossMachine.thresholds(boss.machine())) : new float[0];
            } else {
                bar = null;
                marks = new float[0];
            }
            phaseStart = level.getGameTime();
        }

        // ---- Bosses.Handle
        @Override public ResourceLocation id() { return boss.id(); }
        @Override public LivingEntity entity() { return entity; }
        @Override public String phase() { return boss.machine().phases().get(state.phase); }
        @Override public long phaseTicks() { return state.phaseTicks; }
        @Override public boolean locked() { return state.locked(); }
        @Override public boolean alive() { return attached && !entity.isRemoved(); }

        @Override public boolean signal(String name) {
            if (!BossMachine.Names.valid(name)) throw new IllegalArgumentException("Bad signal name: " + name);
            if (!attached) return false;
            signals.add(name);
            return true;
        }

        @Override public boolean phase(String name) {
            int index = boss.machine().index(name);
            if (!attached || index < 0) return false;
            int left = state.phase;
            if (!BossMachine.force(state, index)) return false;
            stopSequence();
            exit(left);
            enter(index, true);
            save();
            return true;
        }

        @Override public boolean detach() {
            if (!attached) return false;
            end(true);
            return true;
        }

        // ---- Boss.Context
        @Override public ServerLevel level() { return level; }
        @Override public Bosses.Handle handle() { return this; }

        @Override public Optional<Effects.Handle> start(ResourceLocation effect) {
            return start(effect, Placement.on(entity).followingBody());
        }

        @Override public Optional<Effects.Handle> start(ResourceLocation effect, Placement placement) {
            if (!attached || entity.isRemoved()) return Optional.empty();
            return Effects.start(effect, level, placement.withSource(EffectSource.boss(entity)));
        }

        // ---- machine
        void tick() {
            failedThisTick = false;
            BossMachine.Step step = BossMachine.step(boss.machine(), state, new BossMachine.Inputs() {
                @Override public double health() { return BossBarMath.progress(entity.getHealth(), entity.getMaxHealth()); }
                @Override public boolean signal(String name) { return signals.contains(name); }
                @Override public boolean predicate(int index) {
                    try {
                        return boss.predicates().get(index).test(Runtime.this);
                    } catch (RuntimeException failure) {
                        fail("predicate", failure);
                        return false;
                    }
                }
                @Override public boolean sequenceDone() { return sequence == null || !sequence.alive(); }
            });
            // Signals wait through the minimum dwell; once the rules saw them (or the phase changed) they are dropped,
            // so a signal never fires a transition long after it was sent.
            if (step.evaluated() || step.outcome() != BossMachine.Outcome.NONE) signals.clear();
            if (!attached) return;
            apply(step);
            if (!attached) return;
            if (!state.locked() && !state.dead && state.phaseTicks > 0) {
                for (Boss.Periodic periodic : current().periodic()) {
                    if (state.phaseTicks % periodic.every() == 0) call("every " + periodic.every(), () -> periodic.action().run(this));
                    if (!attached) return;
                }
            }
            updateBar();
            if (level.getGameTime() % VIEWER_PERIOD == 0) viewers(false);
            save();
            if (!failedThisTick) failures = 0;
        }

        /**
         * A tick in which the game does not tick the entity: the machine, the periodic actions and the saved state stay
         * as they are (signals keep waiting); the bar still follows health and who is in range.
         */
        void idle() {
            updateBar();
            if (level.getGameTime() % VIEWER_PERIOD == 0) viewers(false);
        }

        void apply(BossMachine.Step step) {
            switch (step.outcome()) {
                case NONE -> { }
                case SEQUENCE -> {
                    ResourceLocation effect = boss.transitions().get(step.rule()).sequence();
                    sequence = start(effect).orElse(null);  // refused: the next step sees it done
                }
                case CHANGED -> {
                    sequence = null;
                    exit(step.left());
                    if (attached) enter(state.phase, true);
                }
            }
        }

        Boss.Phase current() { return boss.phases().get(state.phase); }

        /** Begins a phase: bar, entry effects, sustain, global event, callback ({@code fresh} false on a resume). */
        void enter(int index, boolean fresh) {
            Boss.Phase phase = boss.phases().get(index);
            phaseStart = level.getGameTime() - state.phaseTicks;
            if (bar != null) {
                bar.setColor(color(phase.color() != null ? phase.color() : boss.bar().color()));
                for (ServerPlayer player : bar.getPlayers()) sync(player);
            }
            if (fresh) for (ResourceLocation effect : phase.enter()) start(effect);
            if (phase.sustain() != null) sustain = start(phase.sustain()).orElse(null);
            if (phase.worldEvent() != null) startWorld(phase);
            if (fresh && phase.onEnter() != null) call("onEnter", () -> phase.onEnter().run(this));
        }

        /** Ends a phase: callback, release of sustain and global event, exit effects. */
        void exit(int index) {
            if (index < 0) return;
            Boss.Phase phase = boss.phases().get(index);
            if (phase.onExit() != null) call("onExit", () -> phase.onExit().run(this));
            releaseHeld();
            if (attached) for (ResourceLocation effect : phase.exit()) start(effect);
        }

        void startWorld(Boss.Phase phase) {
            if (phase.worldScope() == WorldEvents.Scope.AREA) {
                // Arena event: an ordinary instance at the boss's position, counted toward maxPerBoss.
                area = start(phase.worldEvent(), Placement.at(entity.position())).orElse(null);
            } else {
                world = WorldEvents.start(phase.worldEvent(), phase.worldScope(), level,
                    WorldEvents.Options.defaults().at(entity.position())).orElse(null);
            }
        }

        void releaseHeld() {
            release(sustain);
            release(area);
            release(world);
            sustain = area = null;
            world = null;
        }

        void stopSequence() {
            if (sequence != null && sequence.alive()) sequence.stop();
            sequence = null;
        }

        static void release(Effects.Handle handle) {
            if (handle == null || !handle.alive()) return;
            if (!handle.release()) handle.stop();
        }

        void hurt(DamageSource source, float amount) {
            if (!attached || state.dead || amount <= 0) return;
            state.hurt++;
            Boss.Phase phase = current();
            if (phase.onHurt() != null) call("onHurt", () -> phase.onHurt().run(this, source, amount));
        }

        void died() {
            if (!attached || state.dead) return;
            stopSequence();
            BossMachine.Step step = BossMachine.die(boss.machine(), state);
            apply(step);
            if (bar != null) bar.setProgress(0);
            save();
        }

        /** Ends the controller: releases everything, removes the bar and (with {@code forget}) the saved state. */
        void end(boolean forget) {
            if (!attached) return;
            attached = false;
            stopSequence();
            releaseHeld();
            if (bar != null) {
                for (ServerPlayer player : List.copyOf(bar.getPlayers())) send(player, BossSync.removal(bar.getId()));
                bar.removeAllPlayers();
            }
            if (forget) entity.removeData(SAVED);
            ACTIVE.remove(entity.getUUID(), this);
        }

        // ---- bar
        void updateBar() {
            if (bar == null) return;
            float progress = BossBarMath.progress(entity.getHealth(), entity.getMaxHealth());
            long now = level.getGameTime();
            if (BossBarMath.shouldSend(sentProgress, progress, sentTick, now)) {
                bar.setProgress(progress);
                sentProgress = progress;
                sentTick = now;
            }
        }

        /** Adds players of the same level within range and removes the others; new viewers get the boss_v1 state. */
        void viewers(boolean force) {
            if (bar == null || !attached) return;
            double range = boss.bar().range();
            for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
                if (player.level() != level || player.isRemoved() || player.distanceToSqr(entity) > range * range) {
                    bar.removePlayer(player);
                    send(player, BossSync.removal(bar.getId()));
                }
            }
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(entity) > range * range || bar.getPlayers().contains(player)) continue;
                sync(player);
                bar.addPlayer(player);
            }
        }

        void sync(ServerPlayer player) {
            Boss.Bar spec = boss.bar();
            Boss.Phase phase = current();
            Boss.BarColor color = phase.color() != null ? phase.color() : spec.color();
            send(player, new BossSync(bar.getId(), false, spec.style() == null ? "" : spec.style().toString(), color.ordinal(),
                spec.overlay().ordinal(), phase.label() == null ? "" : phase.label(), marks, state.phase, phaseStart,
                !phase.invulnerable()));
        }

        static void send(ServerPlayer player, BossSync packet) {
            if (NetApi.canSend(player, BossSync.PACKET)) NetApi.send(player, packet);
        }

        // ---- persistence and failures
        void save() {
            if (!attached) return;
            String pending = state.pending >= 0 ? boss.machine().phases().get(state.pending) : "";
            entity.setData(SAVED, new Saved(boss.id().toString(), phase(), state.phaseTicks, state.hurt, pending));
        }

        void call(String what, Runnable action) {
            try {
                action.run();
            } catch (RuntimeException failure) {
                fail(what, failure);
            }
        }

        void fail(String what, RuntimeException failure) {
            failedThisTick = true;
            failures++;
            LOGGER.error("Boss {} ({}), phase {}: {} failed ({} of {})", boss.id(), entity.getUUID(), phase(), what,
                failures, MAX_FAILURES, failure);
            if (failures >= MAX_FAILURES && attached) {
                LOGGER.error("Boss {} ({}) detached after {} callback failures in a row", boss.id(), entity.getUUID(), MAX_FAILURES);
                end(true);
            }
        }

        @Override public String toString() { return "Boss[" + boss.id() + " on " + entity.getUUID() + ", " + phase() + "]"; }
    }

    // ---------------------------------------------------------------- events

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        for (Runtime runtime : List.copyOf(ACTIVE.values())) {
            if (!runtime.attached) continue;
            if (runtime.entity.isRemoved()) {
                removed(runtime);
                continue;
            }
            if (processed(runtime)) runtime.tick();
            else runtime.idle();
        }
    }

    /**
     * True when the game ticks the boss entity this tick: its chunk is entity-ticking (not just loaded at the edge of
     * the view, where entities stay in the level but frozen) and {@code /tick freeze} is off. Phase time, rules,
     * periodic actions and the saved state only advance then, so a frozen boss never changes phase by itself.
     */
    static boolean processed(Runtime runtime) {
        return runtime.level.isPositionEntityTicking(runtime.entity.blockPosition())
            && !runtime.level.tickRateManager().isEntityFrozen(runtime.entity);
    }

    /** Killed or discarded: the controller ends for good. Unloaded or moved to another dimension: it resumes later. */
    static void removed(Runtime runtime) {
        Entity.RemovalReason reason = runtime.entity.getRemovalReason();
        boolean forever = reason == null || reason.shouldDestroy();
        runtime.end(forever);
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof LivingEntity entity)
            || entity instanceof Player) return;
        Saved saved = entity.getExistingDataOrNull(SAVED);
        if (saved == null || saved.boss().isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(saved.boss());
        Optional<Boss> boss = id == null ? Optional.empty() : Bosses.definition(id);
        if (boss.isEmpty()) {
            LOGGER.warn("Saved boss {} on {} is not defined (addon removed?); dropping its state", saved.boss(), entity.getUUID());
            entity.removeData(SAVED);
            return;
        }
        Runtime old = ACTIVE.get(entity.getUUID());
        if (old != null) {
            if (old.entity == entity) return;
            old.end(false);
        }
        if (ACTIVE.size() >= ServerConfig.bossMaxActive()) {
            LOGGER.warn("Boss {} on {} not resumed: bossMaxActive reached (its state stays saved)", saved.boss(), entity.getUUID());
            return;
        }
        BossMachine.State state = BossMachine.restore(boss.get().machine(), saved.phase(), saved.phaseTicks(), saved.hurt(), saved.pending());
        Runtime runtime = new Runtime(boss.get(), entity, level, state);
        ACTIVE.put(entity.getUUID(), runtime);
        // The entity is joining now: start the held effects (sustain, global event) on the next tick, when it is in
        // the level and can anchor instances.
        RESUME.add(runtime);
    }

    private static final List<Runtime> RESUME = new ArrayList<>();

    @SubscribeEvent
    public static void resume(ServerTickEvent.Pre event) {
        if (RESUME.isEmpty()) return;
        for (Runtime runtime : List.copyOf(RESUME)) {
            if (runtime.attached && !runtime.entity.isRemoved()) {
                runtime.enter(runtime.state.phase, false);
                runtime.viewers(true);
            }
        }
        RESUME.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void incoming(LivingIncomingDamageEvent event) {
        Runtime runtime = ACTIVE.get(event.getEntity().getUUID());
        if (runtime != null && runtime.entity == event.getEntity() && runtime.attached && !runtime.state.dead
            && runtime.current().invulnerable()
            && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void damaged(LivingDamageEvent.Post event) {
        Runtime runtime = ACTIVE.get(event.getEntity().getUUID());
        if (runtime != null && runtime.entity == event.getEntity()) runtime.hurt(event.getSource(), event.getNewDamage());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        Runtime runtime = ACTIVE.get(event.getEntity().getUUID());
        if (runtime != null && runtime.entity == event.getEntity()) runtime.died();
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (Runtime runtime : ACTIVE.values()) if (runtime.bar != null) runtime.bar.removePlayer(player);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        RESUME.clear();
    }

    // ---------------------------------------------------------------- helpers

    static BossEvent.BossBarColor color(Boss.BarColor color) { return BossEvent.BossBarColor.valueOf(color.name()); }

    static BossEvent.BossBarOverlay overlay(Boss.BarOverlay overlay) { return BossEvent.BossBarOverlay.valueOf(overlay.name()); }
}
