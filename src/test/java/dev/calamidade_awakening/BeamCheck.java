package dev.calamidade_awakening;

import dev.calamidade_awakening.api.EnginePasses;
import dev.calamidade_awakening.api.PassBudget;
import dev.calamidade_awakening.api.Quality;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/** Beam API, points and the shared engine passes (0.9.0). Pure; run by FrameBudgetCheck inside checkBudget. */
final class BeamCheck {
    private BeamCheck() {}

    static void run() {
        points();
        builder();
        sampling();
        registration();
        enginePasses();
    }

    private static void points() {
        var c = Point.center(0, 40, 0);
        require(c.source() == Point.Source.CENTER && c.y() == 40 && !c.source().entity(), "center point");
        require(Point.center().equals(Point.center(0, 0, 0)) && Point.center().hashCode() == Point.center(0, 0, 0).hashCode(), "point equality");
        require(!Point.center(0, 1, 0).equals(Point.anchor(0, 1, 0)), "source matters");
        require(Point.world(100, 64, -200).source() == Point.Source.WORLD, "world point");
        require(Point.casterAim(48).reach() == 48 && Point.casterAim(48).source().aim() && Point.casterAim(48).source().caster(), "caster aim");
        require(Point.anchorEye(0, -.2, 0).source().eye() && !Point.anchorEye(0, 0, 0).source().caster(), "anchor eye");
        require(Point.caster(0, 0, 0).source().entity() && !Point.world(0, 0, 0).source().entity(), "entity flag");
        expectRejected(() -> Point.center(Double.NaN, 0, 0), "NaN offset");
        expectRejected(() -> Point.center(0, 2000, 0), "offset too large");
        expectRejected(() -> Point.world(0, 5000, 0), "world y outside");
        expectRejected(() -> Point.world(4e7, 0, 0), "world x outside");
        expectRejected(() -> Point.casterAim(0.1), "reach too small");
        expectRejected(() -> Point.anchorAim(300), "reach too large");
        require(Point.casterAim(48).toString().equals("CASTER_AIM(48.0)"), "aim toString");
    }

    private static void builder() {
        var b = Beam.builder(Point.center(), Point.center(0, 10, 0)).build();
        require(b.coreWidth() == .2f && b.haloWidth() == 1.2f && b.coreColor() == 0xffffffff && b.haloColor() == 0xff66ccff
            && b.intensity() == 1 && b.pulses() == 0 && b.reveal() == 1 && b.noise() == 0 && b.waveAmplitude() == 0
            && !b.animated(), "beam defaults");
        expectRejected(() -> Beam.builder(Point.center(0, 1, 0), Point.center(0, 1, 0)), "equal ends");
        expectRejected(() -> Beam.builder(Point.center(), Point.center(0, 1, 0)).width(-1, 1), "negative width");
        expectRejected(() -> Beam.builder(Point.center(), Point.center(0, 1, 0)).width(1, 65), "halo too wide");
        expectRejected(() -> Beam.builder(Point.center(), Point.center(0, 1, 0)).width(Float.NaN, 1), "NaN width");
        expectRejected(() -> Beam.builder(Point.center(), Point.center(0, 1, 0)).pulses(17, 1, 1), "too many pulses");
        expectRejected(() -> Beam.builder(Point.center(), Point.center(0, 1, 0)).pulses(2, 9, 1), "pulse too fast");
        expectRejected(() -> Beam.builder(Point.center(), Point.center(0, 1, 0)).wave(1, .1f, 0), "wavelength too short");
        expectRejected(() -> Beam.builder(Point.center(), Point.center(0, 1, 0)).noise(9), "noise too large");
        expectRejected(() -> Beam.builder(Point.center(), Point.center(0, 1, 0)).reveal(1.5f), "reveal above 1");
        expectRejected(() -> Beam.builder(Point.center(), Point.center(0, 1, 0)).intensity(9), "intensity too high");
        expectRejected(() -> Beam.builder(Point.center(), Point.center(0, 1, 0)).track(Beam.Param.REVEAL, "Nascer"), "bad track name");
        expectRejected(() -> Beam.builder(Point.center(), Point.center(0, 1, 0)).track(Beam.Param.REVEAL, "a").track(Beam.Param.REVEAL, "b"), "double track");
        expectRejected(() -> Beam.builder(Point.center(), Point.center(0, 1, 0)).weightPhase("a").weightPhase("b"), "double phase");
        var full = Beam.builder(Point.casterEye(0, -.2, 0), Point.casterAim(48)).width(.3f, 2f).color(0xffff4060)
            .intensity(2).pulses(3, -1.5f, 4).wave(.5f, 6, 1).noise(.2f).reveal(.5f)
            .track(Beam.Param.REVEAL, "nascer").weightPhase("ativo").build();
        require(full.coreColor() == 0xffffffff && full.haloColor() == 0xffff4060 && full.pulses() == 3 && full.pulseSpeed() == -1.5f
            && full.waveLength() == 6 && full.animated() && full.track(Beam.Param.REVEAL).equals("nascer")
            && full.track(Beam.Param.NOISE) == null && full.weightPhase().equals("ativo"), "beam setters");
        expectRejected(() -> full.validate(null), "animated beam without timeline");
        expectRejected(() -> full.validate(Timeline.builder(40).phase("ativo", 0, 40, 0, 0).build()), "unknown track");
        expectRejected(() -> full.validate(Timeline.builder(40).track("nascer", keyframe(0, 0f)).build()), "unknown phase");
        full.validate(Timeline.builder(40).phase("ativo", 0, 40, 0, 0).track("nascer", keyframe(0, 0f)).build());
        b.validate(null);
    }

    private static void sampling() {
        var timeline = Timeline.builder(100)
            .phase("ativo", 20, 80, 10, 10)
            .track("nascer", keyframe(0, 0f), keyframe(20, 1f, Easing.OVERSHOOT), keyframe(100, 1f))
            .track("pulsos", keyframe(0, 2.4f), keyframe(100, 2.6f))
            .track("subir", keyframe(0, 0f), keyframe(100, 500f))
            .track("peso", keyframe(0, .5f))
            .build();
        var beam = Beam.builder(Point.center(), Point.center(0, 30, 0)).width(.4f, 3f).intensity(2)
            .track(Beam.Param.REVEAL, "nascer").track(Beam.Param.PULSE_COUNT, "pulsos").track(Beam.Param.TO_Y, "subir")
            .track(Beam.Param.WEIGHT, "peso").weightPhase("ativo").build();
        beam.validate(timeline);
        var mid = beam.sample(timeline.at(50), 1);
        require(Math.abs(mid.weight() - .5f) < 1e-6, "weight = envelope x phase x track");
        require(mid.reveal() == 1 && mid.coreWidth() == .4f && mid.haloWidth() == 3f && mid.intensity() == 2, "constants pass through");
        require(mid.toY() == 250 && mid.fromY() == 0, "end offset from a track");
        require(beam.sample(timeline.at(99), 1).toY() == Beam.MAX_TRACK_OFFSET, "offset clamped to 256");
        require(mid.pulses() == 3 && beam.sample(timeline.at(0), 1).pulses() == 2, "pulse count rounded");
        // OVERSHOOT passes the target: reveal stays at 1.
        boolean clamped = true;
        for (int t = 0; t <= 100; t++) clamped &= beam.sample(timeline.at(t), 1).reveal() <= 1;
        require(clamped, "reveal clamped to 1");
        require(beam.sample(timeline.at(5), 1).weight() == 0, "outside the phase");
        require(beam.sample(timeline.at(50), Float.NaN).weight() == 0, "NaN envelope");
        require(beam.sample(timeline.at(50), 3).weight() == .5f, "envelope clamped");
        require(!beam.sample(timeline.at(5), 1).visible(), "weightless beam hidden");
        require(beam.sample(timeline.at(50), 1).visible(), "visible beam");
        var dark = Beam.builder(Point.center(), Point.center(0, 1, 0)).intensity(0).build();
        require(!dark.sample(Timeline.empty(10).at(0), 1).visible(), "intensity 0 hidden");
        var thin = Beam.builder(Point.center(), Point.center(0, 1, 0)).width(0, 0).build();
        require(!thin.sample(Timeline.empty(10).at(0), 1).visible(), "zero width hidden");
        var unrevealed = Beam.builder(Point.center(), Point.center(0, 1, 0)).reveal(0).build();
        require(!unrevealed.sample(Timeline.empty(10).at(0), 1).visible(), "unrevealed hidden");
        require(Beam.Param.REVEAL.clamp(Float.NaN) == 0 && Beam.Param.WAVE_LENGTH.clamp(Float.NaN) == .25f
            && Beam.Param.PULSE_SPEED.clamp(Float.NaN) == 0 && Beam.Param.PULSE_SPEED.clamp(-20) == -8, "param clamp");
    }

    private static void registration() {
        var E = (java.util.function.Function<String, ResourceLocation>) path -> ResourceLocation.fromNamespaceAndPath("checkaddon", path);
        var definition = new Effects.Definition(100, 4, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null);
        var plain = Beam.builder(Point.center(), Point.center(0, 10, 0)).build();
        var animated = Beam.builder(Point.center(), Point.center(0, 10, 0)).track(Beam.Param.REVEAL, "nascer").build();
        expectRejected(() -> Effects.beams(E.apply("nowhere_beam"), plain), "unknown effect");
        expectRejected(() -> Effects.beams(ResourceLocation.parse("calamidade_awakening:nexus"), plain), "internal effect");
        Effects.register(E.apply("pillar"), definition);
        expectRejected(() -> Effects.beams(E.apply("pillar")), "no beams");
        Beam[] seventeen = new Beam[17];
        java.util.Arrays.fill(seventeen, plain);
        expectRejected(() -> Effects.beams(E.apply("pillar"), seventeen), "17 beams");
        expectRejected(() -> Effects.beams(E.apply("pillar"), animated), "animated beam before timeline");
        Beam[] sixteen = new Beam[16];
        java.util.Arrays.fill(sixteen, plain);
        Effects.beams(E.apply("pillar"), sixteen);
        expectRejected(() -> Effects.beams(E.apply("pillar"), plain), "duplicate beams");
        var pillar = VisualKind.byId(VisualKind.localId("checkaddon:pillar"));
        require(pillar.beams().size() == 16, "16 beams registered");
        Effects.register(E.apply("grow"), definition);
        Effects.timeline(E.apply("grow"), Timeline.builder(100).track("nascer", keyframe(0, 0f), keyframe(50, 1f)).build());
        Effects.beams(E.apply("grow"), animated, plain);
        Effects.cue(E.apply("grow"), 10, ctx -> {});
        var grow = VisualKind.byId(VisualKind.localId("checkaddon:grow"));
        require(grow.beams().equals(List.of(animated, plain)) && grow.cues().size() == 1, "beams in order, cues after");
        require(grow.beams().get(0).sample(grow.clock().at(25), 1).reveal() == .5f, "registered beam samples its timeline");
        require(VisualKind.byId(VisualKind.localId("checkaddon:plain")).beams().isEmpty(), "effects without beams");
        try {
            grow.beams().add(plain);
            throw new AssertionError("beam list is mutable");
        } catch (UnsupportedOperationException expected) {}
    }

    private static void enginePasses() {
        // Global cap: effect by effect, in priority order; the last effect keeps its first items.
        var cap = EnginePasses.cap(List.of(List.of("h1", "h2"), List.of("a1", "a2", "a3"), List.of("b1")), 4);
        require(cap.equals(List.of("h1", "h2", "a1", "a2")), "cap by priority");
        require(EnginePasses.cap(List.of(List.of("x")), 0).isEmpty(), "cap zero");
        require(Quality.LOW.beams == 16 && Quality.BALANCED.beams == 48 && Quality.HIGH.beams == 128, "beam caps 16/48/128");
        var many = new java.util.ArrayList<List<Integer>>();
        for (int e = 0; e < 20; e++) many.add(java.util.Collections.nCopies(16, e));
        for (Quality q : Quality.values()) require(EnginePasses.cap(many, q.beams).size() == q.beams, "cap reached " + q);
        // Paying: HERO pool for HERO needs, common pool for NORMAL needs, never borrowing.
        var budget = new PassBudget(Quality.LOW);
        require(EnginePasses.pay(budget, true, true) == EnginePasses.Pool.HERO && budget.left(false) == 1, "hero pays first");
        require(EnginePasses.pay(budget, true, false) == EnginePasses.Pool.NONE && budget.left(false) == 1, "hero never borrows");
        require(EnginePasses.pay(budget, true, true) == EnginePasses.Pool.COMMON && budget.left(false) == 0, "normal pays when hero is spent");
        require(EnginePasses.pay(budget, false, true) == EnginePasses.Pool.NONE, "spent");
        require(EnginePasses.pay(new PassBudget(Quality.HIGH), false, false) == EnginePasses.Pool.NONE, "nobody needs it");
        var normalOnly = new PassBudget(Quality.BALANCED);
        require(EnginePasses.pay(normalOnly, false, true) == EnginePasses.Pool.COMMON && normalOnly.left(true) == 2
            && normalOnly.left(false) == 1, "normal beams use the common pool");
    }

    private static void expectRejected(Runnable action, String test) {
        try {
            action.run();
            throw new AssertionError(test + " accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
