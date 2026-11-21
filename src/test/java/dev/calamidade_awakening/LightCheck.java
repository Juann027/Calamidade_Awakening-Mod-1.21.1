package dev.calamidade_awakening;

import dev.calamidade_awakening.api.EnginePasses;
import dev.calamidade_awakening.api.Quality;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Timeline;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/** Light API (0.9.0). Pure; run by FrameBudgetCheck inside checkBudget. */
final class LightCheck {
    private LightCheck() {}

    static void run() {
        builder();
        sampling();
        flicker();
        registration();
        caps();
    }

    private static void builder() {
        var p = Light.point(Point.center(0, 1, 0)).build();
        require(p.shape() == Light.Shape.POINT && p.color() == 0xffffffff && p.intensity() == 1 && p.radius() == 8
            && p.falloff() == 2 && p.flicker() == 0 && !p.animated() && p.to() == null && p.beam() == null, "point defaults");
        var s = Light.segment(Point.center(), Point.center(0, 10, 0)).build();
        require(s.shape() == Light.Shape.SEGMENT && s.radius() == 4, "segment defaults");
        expectRejected(() -> Light.segment(Point.center(), Point.center()), "equal segment ends");
        var d = Light.disc(Point.center(), 12).build();
        require(d.shape() == Light.Shape.DISC && d.radius() == 12 && d.width() == 3 && d.height() == 3, "disc defaults");
        var r = Light.ring(Point.center(), 6, 2).height(4).build();
        require(r.shape() == Light.Shape.RING && r.radius() == 6 && r.width() == 2 && r.height() == 4, "ring");
        expectRejected(() -> Light.disc(Point.center(), -1), "negative disc");
        expectRejected(() -> Light.ring(Point.center(), 6, 70), "ring too wide");
        expectRejected(() -> Light.point(Point.center()).intensity(9), "intensity too high");
        expectRejected(() -> Light.point(Point.center()).radius(200), "radius too large");
        expectRejected(() -> Light.point(Point.center()).falloff(0), "falloff too small");
        expectRejected(() -> Light.point(Point.center()).flicker(1.5f, 2), "flicker above 1");
        expectRejected(() -> Light.point(Point.center()).flicker(.5f, 30), "flicker too fast");
        expectRejected(() -> Light.point(Point.center()).height(0), "height 0");
        expectRejected(() -> Light.point(Point.center()).track(Light.Param.RADIUS, "Raio"), "bad track name");
        expectRejected(() -> Light.point(Point.center()).track(Light.Param.RADIUS, "a").track(Light.Param.RADIUS, "b"), "double track");
        var beam = Beam.builder(Point.center(), Point.center(0, 50, 0)).color(0xffffb040).build();
        var along = Light.along(beam).build();
        require(along.shape() == Light.Shape.SEGMENT && along.beam() == beam && along.from().equals(beam.from())
            && along.to().equals(beam.to()) && along.color() == 0xffffb040, "along takes the beam's ends and halo color");
        require(Light.along(beam).color(0xff00ff00).build().color() == 0xff00ff00, "explicit color wins");
        var animated = Light.ring(Point.center(), 0, 2).track(Light.Param.RADIUS, "onda").weightPhase("choque").build();
        expectRejected(() -> animated.validate(null), "animated light without timeline");
        expectRejected(() -> animated.validate(Timeline.builder(40).phase("choque", 0, 40, 0, 0).build()), "unknown track");
        expectRejected(() -> animated.validate(Timeline.builder(40).track("onda", keyframe(0, 0f)).build()), "unknown phase");
        animated.validate(Timeline.builder(40).phase("choque", 0, 40, 0, 0).track("onda", keyframe(0, 0f)).build());
    }

    private static void sampling() {
        var timeline = Timeline.builder(60)
            .phase("choque", 10, 50, 5, 5)
            .track("onda", keyframe(0, 0f), keyframe(40, 400f))
            .track("brilho", keyframe(0, 2f), keyframe(60, -1f))
            .track("subir", keyframe(0, 0f), keyframe(60, 3f))
            .build();
        var ring = Light.ring(Point.center(), 1, 2).color(0xff80c0ff).intensity(3)
            .track(Light.Param.RADIUS, "onda").track(Light.Param.INTENSITY, "brilho").track(Light.Param.Y, "subir")
            .weightPhase("choque").build();
        ring.validate(timeline);
        var mid = ring.sample(timeline.at(30), 1, 1.5, .3f, 1);
        require(mid.radius() == Light.MAX_RADIUS, "radius track (300 at tick 30) clamped to 128");
        require(ring.sample(timeline.at(4), 1, 0, 0, 1).radius() == 40 && ring.sample(timeline.at(4), 1, 0, 0, 1).weight() == 0,
            "radius from a track; weight 0 outside the phase");
        require(Math.abs(mid.weight() - 1) < 1e-6 && Math.abs(mid.intensity() - .5f) < 1e-5 && mid.y() == 1.5f
            && mid.color() == 0xff80c0ff && mid.width() == 2, "constants and tracks");
        require(ring.sample(timeline.at(59), 1, 0, 0, 1).intensity() == 0, "negative intensity clamped to 0");
        require(!ring.sample(timeline.at(59), 1, 0, 0, 1).visible(), "dark light hidden");
        require(ring.sample(timeline.at(30), Float.NaN, 0, 0, 1).weight() == 0, "NaN envelope");
        require(ring.sample(timeline.at(30), .5f, 0, 0, 1).weight() == .5f, "envelope scales the weight");
        var zero = Light.point(Point.center()).radius(0).build();
        require(!zero.sample(Timeline.empty(10).at(0), 1, 0, 0, 1).visible(), "zero radius hidden");
        require(Light.Param.FALLOFF.clamp(Float.NaN) == .25f && Light.Param.Y.clamp(Float.NaN) == 0
            && Light.Param.RADIUS.clamp(1e9f) == Light.MAX_RADIUS, "param clamp");
    }

    private static void flicker() {
        require(Light.flickerFactor(0, 6, 3.2, .4f) == 1 && Light.flickerFactor(.5f, 0, 3.2, .4f) == 1
            && Light.flickerFactor(.5f, 6, Double.NaN, .4f) == 1, "no flicker");
        float min = 1, max = 0;
        double jump = 0;
        float previous = Light.flickerFactor(.4f, 6, 0, .25f);
        for (int i = 1; i <= 4000; i++) {
            double t = i / 400.0; // 10 seconds at 400 samples per second
            float f = Light.flickerFactor(.4f, 6, t, .25f);
            min = Math.min(min, f); max = Math.max(max, f);
            jump = Math.max(jump, Math.abs(f - previous));
            previous = f;
        }
        require(min >= .6f - 1e-6 && max <= 1 + 1e-6, "flicker stays within [1 - amount, 1]");
        require(max - min > .15f, "flicker actually varies");
        require(jump < .05, "flicker is smooth (no jumps between close samples)");
        require(Light.flickerFactor(.4f, 6, 2.5, .25f) == Light.flickerFactor(.4f, 6, 2.5, .25f), "flicker deterministic");
        boolean differs = false;
        for (int i = 0; i < 20; i++) differs |= Light.flickerFactor(.4f, 6, i * .37, .1f) != Light.flickerFactor(.4f, 6, i * .37, .7f);
        require(differs, "seed changes the flicker");
        var fire = Light.point(Point.center()).intensity(2).flicker(.5f, 8).build();
        var timeline = Timeline.empty(100).at(10);
        boolean scaled = true;
        for (int i = 0; i < 50; i++) {
            double t = i * .13;
            float full = fire.sample(timeline, 1, t, .3f, 1).intensity();
            float none = fire.sample(timeline, 1, t, .3f, 0).intensity();
            float quarter = fire.sample(timeline, 1, t, .3f, .25f).intensity();
            scaled &= none == 2 && full >= 1 - 1e-5 && full <= 2 && quarter >= 2 * (1 - .125f) - 1e-5 && quarter <= 2;
        }
        require(scaled, "flicker scale (screen-effects intensity, calm mode)");
    }

    private static void registration() {
        var E = (java.util.function.Function<String, ResourceLocation>) path -> ResourceLocation.fromNamespaceAndPath("checkaddon", path);
        var definition = new Effects.Definition(100, 4, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null);
        var fire = Light.point(Point.center(0, .6, 0)).build();
        expectRejected(() -> Effects.lights(E.apply("nowhere_light"), fire), "unknown effect");
        expectRejected(() -> Effects.lights(ResourceLocation.parse("calamidade_awakening:nexus"), fire), "internal effect");
        Effects.register(E.apply("campfire"), definition);
        expectRejected(() -> Effects.lights(E.apply("campfire")), "no lights");
        Light[] nine = new Light[9];
        java.util.Arrays.fill(nine, fire);
        expectRejected(() -> Effects.lights(E.apply("campfire"), nine), "9 lights");
        Light[] eight = new Light[8];
        java.util.Arrays.fill(eight, fire);
        Effects.lights(E.apply("campfire"), eight);
        expectRejected(() -> Effects.lights(E.apply("campfire"), fire), "duplicate lights");
        require(VisualKind.byId(VisualKind.localId("checkaddon:campfire")).lights().size() == 8, "8 lights registered");
        // A light that follows a beam needs that beam registered first, on the same effect.
        var beam = Beam.builder(Point.center(), Point.center(0, 40, 0)).build();
        var other = Beam.builder(Point.center(), Point.center(0, 40, 0)).build();
        Effects.register(E.apply("lit_pillar"), definition);
        expectRejected(() -> Effects.lights(E.apply("lit_pillar"), Light.along(beam).build()), "beam not registered yet");
        Effects.beams(E.apply("lit_pillar"), beam);
        expectRejected(() -> Effects.lights(E.apply("lit_pillar"), Light.along(other).build()), "equal but different beam");
        var follow = Light.along(beam).radius(6).build();
        Effects.lights(E.apply("lit_pillar"), follow, Light.disc(Point.center(), 5).build());
        var pillar = VisualKind.byId(VisualKind.localId("checkaddon:lit_pillar"));
        require(pillar.lights().size() == 2 && pillar.lights().get(0) == follow && pillar.beams().get(0) == beam, "lights after beams");
        // Animated light: timeline first.
        Effects.register(E.apply("shock"), definition);
        var wave = Light.ring(Point.center(), 0, 2).track(Light.Param.RADIUS, "onda").build();
        expectRejected(() -> Effects.lights(E.apply("shock"), wave), "animated light before timeline");
        Effects.timeline(E.apply("shock"), Timeline.builder(100).track("onda", keyframe(0, 0f), keyframe(50, 20f)).build());
        Effects.lights(E.apply("shock"), wave);
        Effects.cue(E.apply("shock"), 5, ctx -> {});
        var shock = VisualKind.byId(VisualKind.localId("checkaddon:shock"));
        require(shock.lights().get(0).sample(shock.clock().at(25), 1, 0, 0, 1).radius() == 10, "registered light samples its timeline");
        require(VisualKind.byId(VisualKind.localId("checkaddon:plain")).lights().isEmpty(), "effects without lights");
    }

    private static void caps() {
        require(Quality.LOW.lights == 8 && Quality.BALANCED.lights == 24 && Quality.HIGH.lights == 64, "light caps 8/24/64");
        var perEffect = new java.util.ArrayList<List<Integer>>();
        for (int e = 0; e < 12; e++) perEffect.add(java.util.Collections.nCopies(8, e));
        for (Quality q : Quality.values()) {
            var kept = EnginePasses.cap(perEffect, q.lights);
            require(kept.size() == q.lights && kept.get(0) == 0 && kept.get(kept.size() - 1) == (q.lights - 1) / 8,
                "lights of the most important effects first " + q);
        }
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
