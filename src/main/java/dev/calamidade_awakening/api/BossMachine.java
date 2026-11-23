package dev.calamidade_awakening.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Phase machine of a boss controller (0.13.0). Pure Java, covered by the checkBudget; the server drives it once per
 * server tick for each live boss and turns its answers into effects, bar updates and callbacks.
 *
 * <ul>
 * <li>Phases are indices into {@link Spec#phases()}; rules are evaluated in declaration order and the first that
 * matches wins, so at most <b>one transition happens per tick</b>.</li>
 * <li>A rule from {@link #ANY} applies in every phase except its own target.</li>
 * <li>Except for {@link Kind#DEATH}, no rule fires before the phase is {@link Spec#minDwell()} ticks old: a boss
 * never flips between phases tick after tick, even with conflicting health rules.</li>
 * <li>A rule with a sequence first <i>locks</i> the machine (the server plays the sequence effect); the phase changes
 * when the caller reports the sequence done. Only death breaks a lock.</li>
 * <li>After death only {@link Kind#DEATH} rules are considered.</li>
 * <li>Time is <b>elapsed time in the phase</b>, counted in ticks the boss was actually ticked (loaded). It is what is
 * saved; world time while the chunk was unloaded or the server was off does not count.</li>
 * </ul>
 */
public final class BossMachine {
    /** "From any phase" in {@link Rule#from()}. */
    public static final int ANY = -1;
    /** Most phases of a boss. */
    public static final int MAX_PHASES = 32;
    /** Most transition rules of a boss. */
    public static final int MAX_RULES = 64;
    /** Largest minimum dwell, ticks. */
    public static final int MAX_DWELL = 1200;

    /** What a rule waits for. */
    public enum Kind {
        /** The phase is at least {@code value} ticks old. */
        AFTER_TICKS,
        /** Health fraction at or below {@code value} (0..1). */
        HEALTH_BELOW,
        /** Health fraction above {@code value} (0..1). */
        HEALTH_ABOVE,
        /** The addon sent the named signal this tick. */
        SIGNAL,
        /** At least {@code value} damage events since the phase began. */
        HURT,
        /** The entity died. */
        DEATH,
        /** A predicate of the caller, evaluated every {@code every} ticks of the phase. */
        WHEN
    }

    /**
     * One transition rule.
     *
     * @param from      phase index or {@link #ANY}
     * @param to        target phase index
     * @param kind      trigger kind
     * @param value     ticks, health fraction or damage count, by kind (unused for SIGNAL, DEATH and WHEN)
     * @param signal    signal name for {@link Kind#SIGNAL}, else null
     * @param every     evaluation interval in ticks for {@link Kind#WHEN} (1..1200), else 0
     * @param predicate caller's predicate index for {@link Kind#WHEN}, else -1
     * @param sequence  true when the transition first plays a sequence and waits for it
     */
    public record Rule(int from, int to, Kind kind, double value, String signal, int every, int predicate, boolean sequence) {
        public Rule {
            Objects.requireNonNull(kind, "kind");
            if (to < 0 || from < ANY) throw new IllegalArgumentException("Bad phase index");
            switch (kind) {
                case AFTER_TICKS, HURT -> { if (!(value >= 1 && value <= 1_000_000)) throw new IllegalArgumentException("Expected 1..1000000"); }
                case HEALTH_BELOW, HEALTH_ABOVE -> { if (!(value >= 0 && value <= 1)) throw new IllegalArgumentException("Expected a health fraction in 0..1"); }
                case SIGNAL -> { if (signal == null || !Names.valid(signal)) throw new IllegalArgumentException("Bad signal name: " + signal); }
                case WHEN -> { if (every < 1 || every > 1200 || predicate < 0) throw new IllegalArgumentException("Expected every in 1..1200"); }
                case DEATH -> { }
            }
        }
    }

    /**
     * Phases, rules and minimum dwell.
     *
     * @param phases   phase names ({@code [a-z0-9_]}, up to 64 characters), unique; the first is the initial phase
     * @param rules    transition rules in priority order
     * @param minDwell ticks a phase lasts at least before a non-death rule may fire (0..{@value #MAX_DWELL})
     */
    public record Spec(List<String> phases, List<Rule> rules, int minDwell) {
        public Spec {
            phases = List.copyOf(phases);
            rules = List.copyOf(rules);
            if (phases.isEmpty() || phases.size() > MAX_PHASES) throw new IllegalArgumentException("Expected 1.." + MAX_PHASES + " phases");
            if (rules.size() > MAX_RULES) throw new IllegalArgumentException("At most " + MAX_RULES + " rules");
            if (minDwell < 0 || minDwell > MAX_DWELL) throw new IllegalArgumentException("Expected a dwell in 0.." + MAX_DWELL);
            for (int i = 0; i < phases.size(); i++) {
                if (!Names.valid(phases.get(i))) throw new IllegalArgumentException("Bad phase name: " + phases.get(i));
                if (phases.indexOf(phases.get(i)) != i) throw new IllegalArgumentException("Duplicate phase: " + phases.get(i));
            }
            for (Rule rule : rules) {
                if (rule.to >= phases.size() || rule.from >= phases.size()) throw new IllegalArgumentException("Rule names an unknown phase");
                if (rule.from == rule.to) throw new IllegalArgumentException("A rule must change the phase: " + phases.get(rule.to));
            }
        }

        /** Index of a phase name, or -1. */
        public int index(String phase) { return phases.indexOf(phase); }
    }

    /** What the caller knows this tick. */
    public interface Inputs {
        /** Health fraction 0..1. */
        double health();
        /** True when the addon sent this signal since the previous tick. */
        boolean signal(String name);
        /** Caller predicate, only asked on its interval. */
        boolean predicate(int index);
        /** True once the sequence of a locked transition is over (or its effect could not start). */
        default boolean sequenceDone() { return true; }
    }

    /** Mutable state of one boss; the fields {@code phase}, {@code phaseTicks}, {@code hurt} and {@code pending} are saved. */
    public static final class State {
        /** Current phase index. */
        public int phase;
        /** Ticks spent in the current phase (ticked time, not world time). */
        public long phaseTicks;
        /** Damage events since the phase began. */
        public int hurt;
        /** Target of a locked (sequence) transition, or -1. */
        public int pending = -1;
        /** The entity died; only death rules remain. */
        public boolean dead;

        /** A fresh state in phase 0. */
        public State() {}

        /** A restored state (persistence); a pending sequence is finished at the next step. */
        public State(int phase, long phaseTicks, int hurt, int pending) {
            this.phase = phase; this.phaseTicks = Math.max(0, phaseTicks); this.hurt = Math.max(0, hurt); this.pending = pending;
        }

        /** True while a sequence is playing. */
        public boolean locked() { return pending >= 0; }
    }

    /** Result of one step. */
    public enum Outcome {
        /** Nothing changed. */
        NONE,
        /** A sequence started; the phase changes later ({@link State#pending}). */
        SEQUENCE,
        /** The phase changed. */
        CHANGED
    }

    /**
     * One step with its outcome and the rule that fired (index into {@link Spec#rules()}, -1 when the change finished
     * a sequence or nothing fired), plus the phase left (-1 when unchanged).
     */
    public record Step(Outcome outcome, int rule, int left) {
        static final Step NONE = new Step(Outcome.NONE, -1, -1);
    }

    private BossMachine() {}

    /**
     * Restores a saved state against the current spec (phase names may have been reordered or removed by an addon
     * update): unknown phases restart at phase 0, an unknown pending target is dropped.
     */
    public static State restore(Spec spec, String phase, long phaseTicks, int hurt, String pending) {
        int index = spec.index(phase);
        if (index < 0) return new State();
        int target = pending == null || pending.isEmpty() ? -1 : spec.index(pending);
        return new State(index, phaseTicks, hurt, target == index ? -1 : target);
    }

    /** Advances one server tick: the phase ages, then the rules are evaluated. */
    public static Step step(Spec spec, State state, Inputs in) {
        state.phaseTicks++;
        if (state.dead) {
            Step death = death(spec, state);
            if (death != null) return death;
            return Step.NONE;
        }
        if (state.locked()) {
            if (!in.sequenceDone()) return Step.NONE;
            int left = state.phase;
            enter(state, state.pending);
            return new Step(Outcome.CHANGED, -1, left);
        }
        if (state.phaseTicks < spec.minDwell()) return Step.NONE;
        List<Rule> rules = spec.rules();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (!applies(rule, state.phase) || rule.kind == Kind.DEATH) continue;
            if (!matches(rule, state, in)) continue;
            return fire(state, rule, i);
        }
        return Step.NONE;
    }

    /**
     * Marks the boss dead and fires its first matching death rule right away (same tick as the death event), breaking
     * a sequence lock. Returns {@link Step#NONE} when no death rule leaves the current phase.
     */
    public static Step die(Spec spec, State state) {
        if (state.dead) return Step.NONE;
        state.dead = true;
        Step death = death(spec, state);
        return death == null ? Step.NONE : death;
    }

    private static Step death(Spec spec, State state) {
        List<Rule> rules = spec.rules();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (rule.kind != Kind.DEATH || !applies(rule, state.phase)) continue;
            state.pending = -1;
            int left = state.phase;
            enter(state, rule.to);
            return new Step(Outcome.CHANGED, i, left);
        }
        return null;
    }

    /** Forces a phase (debug command, addon override); clears a lock. Returns false when already there or dead. */
    public static boolean force(State state, int phase) {
        if (state.dead || phase == state.phase) return false;
        state.pending = -1;
        enter(state, phase);
        return true;
    }

    private static boolean applies(Rule rule, int phase) {
        return rule.from == phase || (rule.from == ANY && rule.to != phase);
    }

    private static boolean matches(Rule rule, State state, Inputs in) {
        return switch (rule.kind) {
            case AFTER_TICKS -> state.phaseTicks >= rule.value;
            case HEALTH_BELOW -> in.health() <= rule.value;
            case HEALTH_ABOVE -> in.health() > rule.value;
            case SIGNAL -> in.signal(rule.signal);
            case HURT -> state.hurt >= rule.value;
            case WHEN -> state.phaseTicks % rule.every == 0 && in.predicate(rule.predicate);
            case DEATH -> false;
        };
    }

    private static Step fire(State state, Rule rule, int index) {
        if (rule.sequence) {
            state.pending = rule.to;
            return new Step(Outcome.SEQUENCE, index, -1);
        }
        int left = state.phase;
        enter(state, rule.to);
        return new Step(Outcome.CHANGED, index, left);
    }

    private static void enter(State state, int phase) {
        state.phase = phase;
        state.phaseTicks = 0;
        state.hurt = 0;
        state.pending = -1;
    }

    /** Health thresholds of the {@link Kind#HEALTH_BELOW} rules, sorted high to low, unique, inside (0, 1): bar marks. */
    public static List<Double> thresholds(Spec spec) {
        List<Double> out = new ArrayList<>();
        for (Rule rule : spec.rules()) {
            if (rule.kind == Kind.HEALTH_BELOW && rule.value > 0 && rule.value < 1 && !out.contains(rule.value)) out.add(rule.value);
        }
        out.sort((a, b) -> Double.compare(b, a));
        return out;
    }

    /** Names of phases, signals and anchors: {@code [a-z0-9_]}, 1..64 characters. */
    public static final class Names {
        private Names() {}

        /** True for a valid name. */
        public static boolean valid(String name) {
            if (name == null || name.isEmpty() || name.length() > 64) return false;
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')) return false;
            }
            return true;
        }
    }
}
