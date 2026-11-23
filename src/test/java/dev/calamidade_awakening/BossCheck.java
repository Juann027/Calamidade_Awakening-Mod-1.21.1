package dev.calamidade_awakening;

import dev.calamidade_awakening.api.AnchorMath;
import dev.calamidade_awakening.api.BossBarMath;
import dev.calamidade_awakening.api.BossMachine;
import dev.calamidade_awakening.api.BossMachine.Kind;
import dev.calamidade_awakening.api.BossMachine.Rule;
import dev.calamidade_awakening.api.BossMachine.Spec;
import dev.calamidade_awakening.api.BossMachine.State;
import dev.calamidade_awakening.api.SourceLimits;
import dev.calamidade_awakening.api.SourceLimits.Rules;
import dev.calamidade_awakening.api.SourceLimits.Verdict;
import dev.calamidade_awakening.api.v1.EffectSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 0.13.0: boss phase machine, rig anchor math, boss bar math and the maxPerBoss admission rule. */
final class BossCheck {
    static void run() {
        machine();
        sequenceAndDeath();
        restore();
        anchors();
        bar();
        sources();
        System.out.println("BossCheck PASS");
    }

    /** Minimal inputs: a health value, signals and predicates set by the test. */
    static final class In implements BossMachine.Inputs {
        double health = 1;
        final Set<String> signals = new HashSet<>();
        boolean predicate, sequenceDone;
        int predicateAsks;
        @Override public double health() { return health; }
        @Override public boolean signal(String name) { return signals.contains(name); }
        @Override public boolean predicate(int index) { predicateAsks++; return predicate; }
        @Override public boolean sequenceDone() { return sequenceDone; }
    }

    private static Rule rule(int from, int to, Kind kind, double value) {
        return new Rule(from, to, kind, value, null, 0, -1, false);
    }

    private static void machine() {
        // 0 despertar -> 1 combate after 80 ticks; 1 -> 2 furia at 40% health; signal "golpe" 1 -> 3; ANY -> 4 on death.
        Spec spec = new Spec(List.of("despertar", "combate", "furia", "especial", "derrota"), List.of(
            rule(0, 1, Kind.AFTER_TICKS, 80),
            rule(1, 2, Kind.HEALTH_BELOW, .4),
            new Rule(1, 3, Kind.SIGNAL, 0, "golpe", 0, -1, false),
            rule(BossMachine.ANY, 4, Kind.DEATH, 0)), 20);
        State s = new State();
        In in = new In();
        for (int i = 1; i < 80; i++) require(BossMachine.step(spec, s, in).outcome() == BossMachine.Outcome.NONE, "waits 80 ticks");
        var step = BossMachine.step(spec, s, in);
        require(step.outcome() == BossMachine.Outcome.CHANGED && s.phase == 1 && step.left() == 0 && step.rule() == 0
            && s.phaseTicks == 0, "after 80 ticks");
        // Health already low and a signal on the same tick: dwell blocks both for 20 ticks, then only the first rule fires.
        in.health = .3;
        in.signals.add("golpe");
        for (int i = 1; i < 20; i++) require(BossMachine.step(spec, s, in).outcome() == BossMachine.Outcome.NONE, "min dwell");
        step = BossMachine.step(spec, s, in);
        require(step.outcome() == BossMachine.Outcome.CHANGED && s.phase == 2 && step.rule() == 1, "first matching rule wins");
        require(BossMachine.step(spec, s, in).outcome() == BossMachine.Outcome.NONE, "one transition per tick, no rule from 2");
        // Hurt count and WHEN predicate.
        Spec hurt = new Spec(List.of("a", "b", "c"), List.of(rule(0, 1, Kind.HURT, 3),
            new Rule(1, 2, Kind.WHEN, 0, null, 10, 0, false)), 0);
        State h = new State();
        In hi = new In();
        h.hurt = 2;
        require(BossMachine.step(hurt, h, hi).outcome() == BossMachine.Outcome.NONE, "2 hits are not 3");
        h.hurt = 3;
        require(BossMachine.step(hurt, h, hi).outcome() == BossMachine.Outcome.CHANGED && h.phase == 1 && h.hurt == 0,
            "3 hits; counter resets");
        hi.predicate = true;
        for (int i = 1; i < 10; i++) BossMachine.step(hurt, h, hi);
        require(h.phase == 1 && hi.predicateAsks == 0, "predicate only on its interval");
        BossMachine.step(hurt, h, hi);
        require(h.phase == 2 && hi.predicateAsks == 1, "predicate at tick 10");
        // HEALTH_ABOVE (healing back) and ANY excludes its own target.
        Spec heal = new Spec(List.of("a", "b"), List.of(rule(0, 1, Kind.HEALTH_BELOW, .5), rule(1, 0, Kind.HEALTH_ABOVE, .8)), 5);
        State hs = new State();
        In ho = new In();
        ho.health = .4;
        for (int i = 0; i < 5; i++) BossMachine.step(heal, hs, ho);
        require(hs.phase == 1, "below .5");
        for (int i = 0; i < 20; i++) BossMachine.step(heal, hs, ho);
        require(hs.phase == 1, "no flapping without healing");
        ho.health = .9;
        for (int i = 0; i < 5; i++) BossMachine.step(heal, hs, ho);
        require(hs.phase == 0, "healed above .8");
        // Validation.
        expect(() -> new Spec(List.of("a", "a"), List.of(), 0), "duplicate phase");
        expect(() -> new Spec(List.of("A"), List.of(), 0), "upper case phase");
        expect(() -> new Spec(List.of("a"), List.of(rule(0, 0, Kind.AFTER_TICKS, 1)), 0), "rule to itself");
        expect(() -> new Spec(List.of("a", "b"), List.of(rule(0, 2, Kind.AFTER_TICKS, 1)), 0), "unknown phase");
        expect(() -> rule(0, 1, Kind.HEALTH_BELOW, 1.5), "health above 1");
        expect(() -> new Rule(0, 1, Kind.SIGNAL, 0, "Bad name", 0, -1, false), "bad signal");
        expect(() -> new Spec(List.of("a"), List.of(), 1201), "dwell too long");
        require(BossMachine.thresholds(new Spec(List.of("a", "b", "c"), List.of(rule(0, 1, Kind.HEALTH_BELOW, .4),
            rule(1, 2, Kind.HEALTH_BELOW, .7), rule(2, 0, Kind.HEALTH_BELOW, .4)), 0)).equals(List.of(.7, .4)), "thresholds");
    }

    private static void sequenceAndDeath() {
        Spec spec = new Spec(List.of("a", "b", "morte"), List.of(
            new Rule(0, 1, Kind.AFTER_TICKS, 5, null, 0, -1, true),
            rule(BossMachine.ANY, 2, Kind.DEATH, 0)), 0);
        State s = new State();
        In in = new In();
        for (int i = 0; i < 4; i++) BossMachine.step(spec, s, in);
        var step = BossMachine.step(spec, s, in);
        require(step.outcome() == BossMachine.Outcome.SEQUENCE && s.locked() && s.phase == 0 && s.pending == 1, "sequence locks");
        for (int i = 0; i < 30; i++) require(BossMachine.step(spec, s, in).outcome() == BossMachine.Outcome.NONE, "locked");
        in.sequenceDone = true;
        step = BossMachine.step(spec, s, in);
        require(step.outcome() == BossMachine.Outcome.CHANGED && s.phase == 1 && !s.locked() && step.rule() == -1, "sequence done");
        // Death breaks a lock, fires once, and after it nothing else runs.
        State t = new State();
        t.pending = 1;
        step = BossMachine.die(spec, t);
        require(step.outcome() == BossMachine.Outcome.CHANGED && t.phase == 2 && !t.locked() && t.dead, "death breaks the lock");
        require(BossMachine.die(spec, t).outcome() == BossMachine.Outcome.NONE, "dies once");
        for (int i = 0; i < 10; i++) require(BossMachine.step(spec, t, in).outcome() == BossMachine.Outcome.NONE, "nothing after death");
        // No death rule: dying only marks the state.
        Spec plain = new Spec(List.of("a", "b"), List.of(rule(0, 1, Kind.AFTER_TICKS, 1)), 0);
        State p = new State();
        require(BossMachine.die(plain, p).outcome() == BossMachine.Outcome.NONE && p.dead, "dead without a death rule");
        require(BossMachine.step(plain, p, in).outcome() == BossMachine.Outcome.NONE && p.phase == 0, "dead: no time rule");
        require(!BossMachine.force(p, 1), "no force after death");
        State f = new State();
        require(BossMachine.force(f, 1) && f.phase == 1 && f.phaseTicks == 0 && !BossMachine.force(f, 1), "force");
    }

    private static void restore() {
        Spec spec = new Spec(List.of("a", "b", "c"), List.of(rule(0, 1, Kind.AFTER_TICKS, 100)), 0);
        State s = BossMachine.restore(spec, "a", 95, 2, "");
        require(s.phase == 0 && s.phaseTicks == 95 && s.hurt == 2 && !s.locked(), "restored");
        In in = new In();
        for (int i = 0; i < 4; i++) BossMachine.step(spec, s, in);
        require(s.phase == 0, "elapsed time continues, not world time");
        BossMachine.step(spec, s, in);
        require(s.phase == 1, "after the saved elapsed time");
        require(BossMachine.restore(spec, "removida", 50, 0, null).phase == 0, "unknown phase restarts");
        State pending = BossMachine.restore(spec, "a", 3, 0, "c");
        require(pending.pending == 2, "pending sequence kept");
        in.sequenceDone = true;  // the server has no live sequence effect after a reload
        BossMachine.step(spec, pending, in);
        require(pending.phase == 2, "a pending sequence finishes after a reload");
        require(BossMachine.restore(spec, "b", 3, 0, "nada").pending == -1, "unknown pending dropped");
        require(BossMachine.restore(spec, "b", -7, -1, null).phaseTicks == 0, "negative elapsed clamped");
    }

    private static void anchors() {
        // Static anchor on the body: yaw 0 faces south (+Z), right is west (-X).
        var p = AnchorMath.fixed(AnchorMath.Frame.BODY, 1, 2, 3, 10, 64, -5, 1.6, 0, 0, 0);
        near(p.x(), 9, "right is -X at yaw 0");
        near(p.y(), 66, "up");
        near(p.z(), -2, "forward is +Z");
        near(p.fz(), 1, "forward vector");
        // Facing west (yaw 90): forward -X, right -Z (north).
        var w = AnchorMath.fixed(AnchorMath.Frame.BODY, 1, 0, 2, 0, 0, 0, 1.6, 90, 0, 0);
        near(w.x(), -2, "west forward");
        near(w.z(), -1, "right is north");
        // Head frame: starts at the eyes, follows the head yaw and pitch (looking down 90 degrees: forward is -Y).
        var h = AnchorMath.fixed(AnchorMath.Frame.HEAD, 0, 0, 1, 0, 0, 0, 1.5, 45, 0, 90);
        near(h.y(), .5, "head frame at the eyes, forward down");
        near(h.fy(), -1, "looking down");
        // Pose offsets: at(right, up, forward).
        double[] at = p.at(0, 0, 2);
        near(at[2], 0, "offset forward");
        double[] r = w.at(1, 0, 0);
        near(r[2], w.z() - 1, "offset right of west");
        // Matrix: identity rotation with translation; model axes of vanilla (forward -Z, up -Y) under the renderer's
        // flip (scale -1, -1, 1) give forward -Z... here we use a plain matrix and plain axes.
        float[] m = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 2, 3, 4, 1};
        var c = AnchorMath.fromMatrix(m, 100, 60, -20, 0, 0, 1, 0, 1, 0);
        near(c.x(), 102, "camera + translation x");
        near(c.y(), 63, "y");
        near(c.z(), -16, "z");
        near(c.fz(), 1, "forward axis");
        // A flipped model matrix (x and y scaled by -1/16, as model parts are): vanilla axes give world forward and up.
        float k = 1 / 16f;
        float[] flip = {-k, 0, 0, 0, 0, -k, 0, 0, 0, 0, k, 0, 0, 0, 0, 1};
        var v = AnchorMath.fromMatrix(flip, 0, 0, 0, 0, 0, -1, 0, -1, 0);
        near(v.fz(), -1, "model -Z is -Z here (no yaw rotation)");
        near(v.uy(), 1, "model -Y is world up after the flip");
        require(AnchorMath.fromMatrix(new float[16], 0, 0, 0, 0, 0, 1, 0, 1, 0) == null, "degenerate matrix");
        var skew = AnchorMath.orthonormal(0, 0, 0, 0, 0, 2, 0, 1, 1);
        near(skew.uz(), 0, "up made perpendicular");
        near(skew.uy(), 1, "up normalized");
        require(AnchorMath.orthonormal(0, 0, 0, 0, 0, 1, 0, 0, 3) == null, "parallel vectors");
        require(AnchorMath.orthonormal(Double.NaN, 0, 0, 0, 0, 1, 0, 1, 0) == null, "NaN position");
        // Carry: an entity at the origin facing south turns to face west and moves +5 x; a point 2 ahead follows.
        var ahead = AnchorMath.fixed(AnchorMath.Frame.BODY, 0, 1, 2, 0, 0, 0, 1.6, 0, 0, 0);
        var carried = AnchorMath.carry(ahead, 0, 0, 0, 0, 5, 0, 0, 90);
        var expected = AnchorMath.fixed(AnchorMath.Frame.BODY, 0, 1, 2, 5, 0, 0, 1.6, 90, 0, 0);
        near(carried.x(), expected.x(), "carry x");
        near(carried.z(), expected.z(), "carry z");
        near(carried.fx(), expected.fx(), "carry forward");
        require(AnchorMath.carriable(100, 110) && !AnchorMath.carriable(100, 110.5) && !AnchorMath.carriable(100, 99), "carry window");
        double[] aim = AnchorMath.aim(ahead, 10);
        near(aim[2], 12, "aim along forward");
    }

    private static void bar() {
        require(BossBarMath.progress(50, 200) == .25f && BossBarMath.progress(5, 0) == 0
            && BossBarMath.progress(300, 200) == 1 && BossBarMath.progress(Float.NaN, 10) == 0, "progress");
        require(!BossBarMath.shouldSend(.5f, .5f, 0, 100), "same value never sent");
        require(BossBarMath.shouldSend(.5f, .494f, 10, 10), "0.6% at once");
        require(!BossBarMath.shouldSend(.5f, .498f, 10, 12) && BossBarMath.shouldSend(.5f, .498f, 10, 15), "small change settles");
        require(BossBarMath.shouldSend(.001f, 0, 10, 10) && BossBarMath.shouldSend(.999f, 1, 10, 10), "ends at once");
        float[] marks = BossBarMath.marks(List.of(.4, .7, .4, 0.0, 1.0));
        require(marks.length == 2 && marks[0] == .7f && marks[1] == .4f, "marks");
        require(BossBarMath.ghost(.8f, .5f, 3, 1) == .8f, "ghost holds");
        require(Math.abs(BossBarMath.ghost(.8f, .5f, 20, 1) - .78f) < 1e-6, "ghost falls");
        require(BossBarMath.ghost(.51f, .5f, 20, 5) == .5f, "ghost never below");
        require(BossBarMath.ghost(.3f, .6f, 0, 1) == .6f, "healing lifts the ghost");
        require(BossBarMath.flash(0, true, true) == 1 && BossBarMath.flash(0, false, true) == .25f
            && BossBarMath.flash(0, true, false) == 0 && BossBarMath.flash(12, true, true) == 0, "flash");
        require(BossBarMath.scale(5) == 2 && BossBarMath.scale(.1) == .5f && BossBarMath.scale(Double.NaN) == 1, "scale");
    }

    private static void sources() {
        UUID mob = new UUID(7, 7);
        EffectSource plain = EffectSource.entity(mob), boss = EffectSource.boss(mob);
        require(plain.equals(boss) && plain.hashCode() == boss.hashCode(), "the mark is not a new identity");
        require(boss.bossController() && !plain.bossController() && boss.kind() == EffectSource.Kind.ENTITY, "mark");
        Rules d = Rules.DEFAULT;
        require(d.perBoss() == 8 && new Rules(16, 4, 2, 16).perBoss() == 8, "default 8");
        expect(() -> new Rules(16, 4, 2, 16, 0), "perBoss 0");
        expect(() -> new Rules(16, 4, 2, 16, 33), "perBoss above 32");
        // A confirmed controller: up to 8 for the entity, counting every instance of the entity.
        require(SourceLimits.admit(d, EffectSource.Kind.ENTITY, true, 10, 10, 7) == Verdict.OK
            && SourceLimits.admit(d, EffectSource.Kind.ENTITY, true, 10, 10, 8) == Verdict.SOURCE_LIMIT, "8 for the boss");
        // Without confirmation (ordinary mob, or a mark with no controller): still 4.
        require(SourceLimits.admit(d, EffectSource.Kind.ENTITY, false, 10, 10, 4) == Verdict.SOURCE_LIMIT, "unconfirmed: 4");
        // Global total and player reserve still apply to the boss.
        require(SourceLimits.admit(d, EffectSource.Kind.ENTITY, true, 64, 10, 0) == Verdict.FULL, "total");
        require(SourceLimits.admit(d, EffectSource.Kind.ENTITY, true, 48, 48, 0) == Verdict.RESERVED, "reserve");
        // A player never gets the boss rule.
        require(SourceLimits.admit(d, EffectSource.Kind.PLAYER, true, 10, 0, 16) == Verdict.SOURCE_LIMIT, "player unaffected");
        // A lower perBoss than perEntity is the admin's choice and is honored for controller starts.
        require(SourceLimits.admit(new Rules(16, 4, 2, 16, 2), EffectSource.Kind.ENTITY, true, 0, 0, 2) == Verdict.SOURCE_LIMIT, "perBoss 2");
    }

    private static void near(double a, double b, String test) {
        if (Math.abs(a - b) > 1e-6) throw new AssertionError(test + ": " + a + " != " + b);
    }

    private static void expect(Runnable action, String test) {
        try {
            action.run();
        } catch (IllegalArgumentException | NullPointerException expected) {
            return;
        }
        throw new AssertionError("expected a failure: " + test);
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
