package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.api.BossMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Definition of a boss controller (0.13.0): phases, transitions, bar and the effects each phase starts. Immutable;
 * built with {@link #builder(ResourceLocation)} and registered with {@link Bosses#define(Boss)}.
 *
 * <p>A boss is <b>not</b> an entity. The addon owns its {@link LivingEntity} (AI, attributes, movement, model, spawn,
 * loot) and calls {@link Bosses#attach} when it wants the controller. The engine never spawns creatures, bars or events
 * by itself. Everything here runs on the server: phases, triggers and the effects that change the world are
 * authoritative there; clients only draw (effects, bar, anchors).
 *
 * <pre>{@code
 * static final Boss SENTINELA = Boss.builder(id("sentinela"))
 *     .bar(Boss.Bar.of(Component.translatable("boss.meumod.sentinela")).color(Boss.BarColor.PURPLE).segments(true))
 *     .phase("despertar", p -> p.enter(DESPERTAR).invulnerable().label("boss.meumod.despertar"))
 *     .phase("combate", p -> p.sustain(AURA).every(120, ctx -> ctx.start(OLHAR)))
 *     .phase("furia", p -> p.enter(FURIA).worldEvent(ECLIPSE, WorldEvents.Scope.AREA))
 *     .phase("derrota", p -> p.enter(QUEDA))
 *     .transition("despertar", "combate", Boss.Trigger.afterTicks(80))
 *     .transition("combate", "furia", Boss.Trigger.healthBelow(.4f))
 *     .transition(Boss.ANY, "derrota", Boss.Trigger.death())
 *     .build();
 * }</pre>
 *
 * <p><b>Rules of the machine.</b> Transitions are checked once per server tick in declaration order; the first match
 * wins, so at most one phase change happens per tick. No transition except {@link Trigger#death()} fires before the
 * phase is {@link Builder#minDwell(int) minDwell} ticks old (20 by default). After death only death transitions run.
 * Phase time is the time the boss was actually ticked (loaded), and it is what is saved with the entity.
 */
public final class Boss {
    /** "From any phase" for {@link Builder#transition}. */
    public static final String ANY = "*";
    /** Most periodic actions of a phase. */
    public static final int MAX_PERIODIC = 8;
    /** Most entry or exit effects of a phase. */
    public static final int MAX_EFFECTS = 8;

    /** Bar colors (the seven of the vanilla boss bar). */
    public enum BarColor {
        /** Vanilla pink. */
        PINK(0xffec00b8),
        /** Vanilla blue. */
        BLUE(0xff00b7ec),
        /** Vanilla red. */
        RED(0xffec3500),
        /** Vanilla green. */
        GREEN(0xff1dec00),
        /** Vanilla yellow. */
        YELLOW(0xffe9ec00),
        /** Vanilla purple. */
        PURPLE(0xff7b00ec),
        /** Vanilla white. */
        WHITE(0xffececec);

        private final int argb;

        BarColor(int argb) { this.argb = argb; }

        /** Accent color of the custom styles, ARGB. */
        public int argb() { return argb; }
    }

    /** Divisions drawn over the bar (vanilla overlays). */
    public enum BarOverlay {
        /** No divisions. */
        PROGRESS,
        /** 6 segments. */
        NOTCHED_6,
        /** 10 segments. */
        NOTCHED_10,
        /** 12 segments. */
        NOTCHED_12,
        /** 20 segments. */
        NOTCHED_20
    }

    /**
     * Bar of a boss: the vanilla boss bar (synced by the game only when a value changes) plus an optional client style.
     * Immutable; every method returns a copy.
     *
     * @param name     bar title
     * @param color    bar color
     * @param overlay  vanilla divisions
     * @param segments draw a mark at every {@link Trigger#healthBelow} threshold (custom styles)
     * @param style    client style id ({@code calamidade_awakening:segmented} or one registered with
     *                 {@code BossBars.style}); null keeps the vanilla look
     * @param range    players within this many blocks in the same dimension see the bar, 16..256
     * @param darken   vanilla "darken screen"
     * @param music    vanilla "play boss music"
     * @param fog      vanilla "create world fog"
     */
    public record Bar(Component name, BarColor color, BarOverlay overlay, boolean segments, ResourceLocation style,
                      int range, boolean darken, boolean music, boolean fog) {
        /** Style drawn by the engine: the vanilla bar with phase marks, ghost of recent damage, phase label. */
        public static final ResourceLocation SEGMENTED = ResourceLocation.fromNamespaceAndPath("calamidade_awakening", "segmented");

        public Bar {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(overlay, "overlay");
            if (range < 16 || range > 256) throw new IllegalArgumentException("Expected a bar range in 16..256: " + range);
        }

        /** White bar, no divisions, vanilla look, 64 blocks. */
        public static Bar of(Component name) { return new Bar(name, BarColor.WHITE, BarOverlay.PROGRESS, false, null, 64, false, false, false); }

        /** Copy with another color. */
        public Bar color(BarColor value) { return new Bar(name, value, overlay, segments, style, range, darken, music, fog); }
        /** Copy with other vanilla divisions. */
        public Bar overlay(BarOverlay value) { return new Bar(name, color, value, segments, style, range, darken, music, fog); }
        /** Copy with (true) or without phase marks; true also picks {@link #SEGMENTED} when no style was set. */
        public Bar segments(boolean value) {
            return new Bar(name, color, overlay, value, value && style == null ? SEGMENTED : style, range, darken, music, fog);
        }
        /** Copy with a client style (null for vanilla). */
        public Bar style(ResourceLocation value) { return new Bar(name, color, overlay, segments, value, range, darken, music, fog); }
        /** Copy with another range, 16..256 blocks. */
        public Bar range(int blocks) { return new Bar(name, color, overlay, segments, style, blocks, darken, music, fog); }
        /** Copy with the vanilla screen darkening, boss music and world fog flags. */
        public Bar vanillaFlags(boolean darkenScreen, boolean bossMusic, boolean worldFog) {
            return new Bar(name, color, overlay, segments, style, range, darkenScreen, bossMusic, worldFog);
        }
    }

    /** What a transition waits for. Immutable. */
    public static final class Trigger {
        private final BossMachine.Kind kind;
        private final double value;
        private final String signal;
        private final int every;
        private final Predicate<Context> predicate;

        private Trigger(BossMachine.Kind kind, double value, String signal, int every, Predicate<Context> predicate) {
            this.kind = kind; this.value = value; this.signal = signal; this.every = every; this.predicate = predicate;
        }

        /** The phase is at least {@code ticks} old (1..1 000 000; phase time, saved with the entity). */
        public static Trigger afterTicks(int ticks) {
            if (ticks < 1 || ticks > 1_000_000) throw new IllegalArgumentException("Expected 1..1000000 ticks");
            return new Trigger(BossMachine.Kind.AFTER_TICKS, ticks, null, 0, null);
        }

        /** Health at or below a fraction of the maximum (0..1). With {@link Bar#segments} it is also a bar mark. */
        public static Trigger healthBelow(float fraction) {
            if (!(fraction >= 0 && fraction <= 1)) throw new IllegalArgumentException("Expected a fraction in 0..1");
            return new Trigger(BossMachine.Kind.HEALTH_BELOW, fraction, null, 0, null);
        }

        /** Health above a fraction of the maximum (0..1): a boss that heals back. */
        public static Trigger healthAbove(float fraction) {
            if (!(fraction >= 0 && fraction <= 1)) throw new IllegalArgumentException("Expected a fraction in 0..1");
            return new Trigger(BossMachine.Kind.HEALTH_ABOVE, fraction, null, 0, null);
        }

        /** The addon sent this signal ({@link Bosses.Handle#signal}) since the previous tick; {@code [a-z0-9_]}, up to 64. */
        public static Trigger signal(String name) {
            if (!BossMachine.Names.valid(name)) throw new IllegalArgumentException("Bad signal name: " + name);
            return new Trigger(BossMachine.Kind.SIGNAL, 0, name, 0, null);
        }

        /** At least {@code hits} damage events (with damage above 0) since the phase began, 1..1 000 000. */
        public static Trigger hurt(int hits) {
            if (hits < 1 || hits > 1_000_000) throw new IllegalArgumentException("Expected 1..1000000 hits");
            return new Trigger(BossMachine.Kind.HURT, hits, null, 0, null);
        }

        /** The entity died (fires in the death tick, even during a sequence). */
        public static Trigger death() { return new Trigger(BossMachine.Kind.DEATH, 0, null, 0, null); }

        /**
         * A predicate of the addon, asked every {@code every} ticks of the phase (1..1200), never per frame. Keep it
         * cheap; an exception counts as a callback failure (see {@link Bosses}).
         */
        public static Trigger when(int every, Predicate<Context> predicate) {
            if (every < 1 || every > 1200) throw new IllegalArgumentException("Expected an interval in 1..1200 ticks");
            return new Trigger(BossMachine.Kind.WHEN, 0, null, every, Objects.requireNonNull(predicate, "predicate"));
        }

        /** Kind (engine use and diagnostics). */
        public BossMachine.Kind kind() { return kind; }
        /** Ticks, health fraction or hits, by kind; 0 for the others. */
        public double value() { return value; }
        /** Signal name, or null. */
        public String signal() { return signal; }
        /** Interval of a {@link #when} predicate, else 0. */
        public int every() { return every; }
        /** Predicate of {@link #when}, else null. */
        public Predicate<Context> predicate() { return predicate; }
    }

    /**
     * One transition: from a phase (or {@link #ANY}) to another when the trigger fires; with a {@code sequence}, the
     * engine first plays that effect on the boss and changes the phase when it ends.
     *
     * @param from     phase name or {@link #ANY}
     * @param to       target phase name
     * @param trigger  what it waits for
     * @param sequence effect played before the change (its Timeline is the cut-scene), or null
     */
    public record Transition(String from, String to, Trigger trigger, ResourceLocation sequence) {
        public Transition {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(trigger, "trigger");
            if (sequence != null && trigger.kind() == BossMachine.Kind.DEATH)
                throw new IllegalArgumentException("A death transition cannot wait for a sequence");
        }
    }

    /** Server callback of a phase. */
    @FunctionalInterface
    public interface Action {
        /** Runs on the server thread. */
        void run(Context context);
    }

    /** Server callback for damage taken during a phase. */
    @FunctionalInterface
    public interface HurtAction {
        /** Runs on the server thread after the damage was applied ({@code amount} is the damage dealt). */
        void run(Context context, DamageSource source, float amount);
    }

    /** What a callback sees. Valid only during the call. */
    public interface Context {
        /** The boss entity. */
        LivingEntity entity();
        /** Its level. */
        ServerLevel level();
        /** The controller. */
        Bosses.Handle handle();
        /** Current phase name. */
        String phase();
        /** Ticks in the current phase. */
        long phaseTicks();

        /**
         * Starts an effect on the boss: anchored to it, following its body, with the boss controller as source
         * ({@link EffectSource#boss}). Empty when the limits refuse it (no error).
         */
        Optional<Effects.Handle> start(ResourceLocation effect);

        /**
         * Starts an effect with a placement of the addon (a target, a direction); the source is replaced by the boss
         * controller, so it counts toward {@code maxPerBoss}.
         */
        Optional<Effects.Handle> start(ResourceLocation effect, Placement placement);
    }

    /**
     * A phase. Immutable; built inside {@link Builder#phase(String, Consumer)}.
     *
     * @param name         {@code [a-z0-9_]}, up to 64 characters
     * @param label        translation key of the phase label on the bar, or null
     * @param color        bar color in this phase, or null for the bar's
     * @param enter        effects started when the phase begins
     * @param exit         effects started when the phase ends
     * @param sustain      effect started when the phase begins and released ({@link Effects.Handle#release}) when it
     *                     ends, or null
     * @param periodic     actions repeated every N ticks of the phase
     * @param worldEvent   effect started as a global event when the phase begins and released when the phase ends or
     *                     the boss dies or is detached, or null
     * @param worldScope   scope of {@code worldEvent}
     * @param invulnerable damage to the entity is cancelled during the phase
     * @param onEnter      callback at the beginning, or null
     * @param onExit       callback at the end, or null
     * @param onHurt       callback for damage taken, or null
     */
    public record Phase(String name, String label, BarColor color, List<ResourceLocation> enter, List<ResourceLocation> exit,
                        ResourceLocation sustain, List<Periodic> periodic, ResourceLocation worldEvent,
                        WorldEvents.Scope worldScope, boolean invulnerable, Action onEnter, Action onExit, HurtAction onHurt) {
        public Phase {
            if (!BossMachine.Names.valid(name)) throw new IllegalArgumentException("Bad phase name: " + name);
            enter = List.copyOf(enter);
            exit = List.copyOf(exit);
            periodic = List.copyOf(periodic);
            if (enter.size() > MAX_EFFECTS || exit.size() > MAX_EFFECTS) throw new IllegalArgumentException("At most " + MAX_EFFECTS + " effects");
            if (periodic.size() > MAX_PERIODIC) throw new IllegalArgumentException("At most " + MAX_PERIODIC + " periodic actions");
        }
    }

    /**
     * A periodic action of a phase.
     *
     * @param every  interval in ticks of the phase, 1..72000 (the first run is at tick {@code every})
     * @param action what to do
     */
    public record Periodic(int every, Action action) {
        public Periodic {
            if (every < 1 || every > 72000) throw new IllegalArgumentException("Expected an interval in 1..72000 ticks");
            Objects.requireNonNull(action, "action");
        }
    }

    /** Builder of a phase. */
    public static final class PhaseBuilder {
        private final String name;
        private String label;
        private BarColor color;
        private final List<ResourceLocation> enter = new ArrayList<>(), exit = new ArrayList<>();
        private ResourceLocation sustain, worldEvent;
        private WorldEvents.Scope worldScope = WorldEvents.Scope.AREA;
        private final List<Periodic> periodic = new ArrayList<>();
        private boolean invulnerable;
        private Action onEnter, onExit;
        private HurtAction onHurt;

        private PhaseBuilder(String name) { this.name = name; }

        /** Effect started on the boss when the phase begins (up to {@value #MAX_EFFECTS}). */
        public PhaseBuilder enter(ResourceLocation effect) { enter.add(Objects.requireNonNull(effect)); return this; }
        /** Effect started on the boss when the phase ends (up to {@value #MAX_EFFECTS}). */
        public PhaseBuilder exit(ResourceLocation effect) { exit.add(Objects.requireNonNull(effect)); return this; }
        /** Sustained effect (Timeline with {@code sustain}) held for the whole phase and released when it ends. */
        public PhaseBuilder sustain(ResourceLocation effect) { sustain = Objects.requireNonNull(effect); return this; }
        /** Action every {@code ticks} ticks of the phase (up to {@value #MAX_PERIODIC}). */
        public PhaseBuilder every(int ticks, Action action) { periodic.add(new Periodic(ticks, action)); return this; }
        /**
         * Global event (an eclipse over the arena, a storm over the dimension) for the whole phase: started at the boss
         * position with {@link WorldEvents#start}, released when the phase ends, the boss dies or the controller is
         * detached. {@code AREA} is the arena around the boss; {@code DIMENSION} and {@code SERVER} reach further.
         */
        public PhaseBuilder worldEvent(ResourceLocation effect, WorldEvents.Scope scope) {
            worldEvent = Objects.requireNonNull(effect); worldScope = Objects.requireNonNull(scope); return this;
        }
        /** Damage to the entity is cancelled during this phase (opt-in; never the default). */
        public PhaseBuilder invulnerable() { invulnerable = true; return this; }
        /** Translation key of the phase label shown by the custom bar styles. */
        public PhaseBuilder label(String translationKey) { label = Objects.requireNonNull(translationKey); return this; }
        /** Bar color while this phase lasts. */
        public PhaseBuilder color(BarColor value) { color = Objects.requireNonNull(value); return this; }
        /** Callback when the phase begins (after its effects start). */
        public PhaseBuilder onEnter(Action action) { onEnter = Objects.requireNonNull(action); return this; }
        /** Callback when the phase ends (before the exit effects start). */
        public PhaseBuilder onExit(Action action) { onExit = Objects.requireNonNull(action); return this; }
        /** Callback for damage taken during the phase. */
        public PhaseBuilder onHurt(HurtAction action) { onHurt = Objects.requireNonNull(action); return this; }

        private Phase build() {
            return new Phase(name, label, color, enter, exit, sustain, periodic, worldEvent, worldScope, invulnerable, onEnter, onExit, onHurt);
        }
    }

    /** Builder of a boss. */
    public static final class Builder {
        private final ResourceLocation id;
        private Bar bar;
        private final List<Phase> phases = new ArrayList<>();
        private final List<Transition> transitions = new ArrayList<>();
        private int minDwell = 20;

        private Builder(ResourceLocation id) { this.id = id; }

        /** The bar; without one the boss has no bar. */
        public Builder bar(Bar value) { bar = Objects.requireNonNull(value); return this; }

        /** A phase; the first declared is the initial phase. */
        public Builder phase(String name, Consumer<PhaseBuilder> setup) {
            PhaseBuilder builder = new PhaseBuilder(name);
            Objects.requireNonNull(setup, "setup").accept(builder);
            phases.add(builder.build());
            return this;
        }

        /** A phase with no effects or callbacks. */
        public Builder phase(String name) { return phase(name, p -> { }); }

        /** A transition; earlier ones win when several match in the same tick. */
        public Builder transition(String from, String to, Trigger trigger) {
            transitions.add(new Transition(from, to, trigger, null));
            return this;
        }

        /**
         * A transition that first plays {@code sequence} on the boss (a roar, a camera, a cut-scene in its Timeline)
         * and changes the phase when that effect ends; only death breaks it. If the sequence cannot start (limits), the
         * phase changes at once.
         */
        public Builder transition(String from, String to, Trigger trigger, ResourceLocation sequence) {
            transitions.add(new Transition(from, to, trigger, Objects.requireNonNull(sequence)));
            return this;
        }

        /** Ticks a phase lasts at least before a non-death transition (0..1200, default 20). */
        public Builder minDwell(int ticks) {
            if (ticks < 0 || ticks > BossMachine.MAX_DWELL) throw new IllegalArgumentException("Expected 0..1200 ticks");
            minDwell = ticks;
            return this;
        }

        /** Validates names and limits and builds the definition. */
        public Boss build() { return new Boss(this); }
    }

    private final ResourceLocation id;
    private final Bar bar;
    private final List<Phase> phases;
    private final List<Transition> transitions;
    private final int minDwell;
    private final BossMachine.Spec spec;
    private final List<Predicate<Context>> predicates;

    private Boss(Builder b) {
        id = Objects.requireNonNull(b.id, "id");
        bar = b.bar;
        phases = List.copyOf(b.phases);
        transitions = List.copyOf(b.transitions);
        minDwell = b.minDwell;
        List<String> names = phases.stream().map(Phase::name).toList();
        List<BossMachine.Rule> rules = new ArrayList<>();
        List<Predicate<Context>> preds = new ArrayList<>();
        for (Transition t : transitions) {
            int from = ANY.equals(t.from()) ? BossMachine.ANY : names.indexOf(t.from());
            int to = names.indexOf(t.to());
            if (from == -1 && !ANY.equals(t.from()) || to < 0)
                throw new IllegalArgumentException("Transition names an unknown phase: " + t.from() + " -> " + t.to());
            Trigger g = t.trigger();
            int predicate = -1;
            if (g.predicate() != null) { predicate = preds.size(); preds.add(g.predicate()); }
            rules.add(new BossMachine.Rule(from, to, g.kind(), g.value(), g.signal(), g.every(), predicate, t.sequence() != null));
        }
        spec = new BossMachine.Spec(names, rules, minDwell);
        predicates = List.copyOf(preds);
    }

    /** Starts a definition. */
    public static Builder builder(ResourceLocation id) { return new Builder(Objects.requireNonNull(id, "id")); }

    /** Id of the boss. */
    public ResourceLocation id() { return id; }
    /** Bar, or null for none. */
    public Bar bar() { return bar; }
    /** Phases in declaration order; the first is the initial phase. */
    public List<Phase> phases() { return phases; }
    /** Transitions in priority order. */
    public List<Transition> transitions() { return transitions; }
    /** Minimum ticks in a phase before a non-death transition. */
    public int minDwell() { return minDwell; }

    /** Phase by name. */
    public Optional<Phase> phase(String name) { return phases.stream().filter(p -> p.name().equals(name)).findFirst(); }

    /** Machine spec built from the phases and transitions (engine use). */
    public BossMachine.Spec machine() { return spec; }

    /** Predicates of the {@link Trigger#when} transitions, in the order the machine numbers them (engine use). */
    public List<Predicate<Context>> predicates() { return predicates; }

    /** Readable form with id and phase names. */
    @Override public String toString() { return "Boss[" + id + ", " + spec.phases() + "]"; }
}
