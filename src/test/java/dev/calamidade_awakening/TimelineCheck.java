package dev.calamidade_awakening;

import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/** Timeline (0.7.0): pure math, run by FrameBudgetCheck inside checkBudget. */
final class TimelineCheck {
    private static final double EPS = 1e-6;

    private TimelineCheck() {}

    static void run() {
        weights();
        easings();
        tracks();
        markers();
        sustain();
        validation();
    }

    private static void weights() {
        var t = Timeline.builder(120)
            .phase("formacao", 0, 40, 10, 8)
            .phase("impacto", 36, 52, 0, 0)
            .build();
        require(t.weight("formacao", -1) == 0 && t.weight("formacao", 0) == 0, "weight before/at start with rise");
        require(near(t.weight("formacao", 5), .5f), "weight mid rise");
        require(t.weight("formacao", 10) == 1 && t.weight("formacao", 32) == 1, "weight plateau");
        require(near(t.weight("formacao", 36), .5f), "weight mid fall");
        require(t.weight("formacao", 40) == 0 && t.weight("formacao", 200) == 0, "weight at/after end");
        // Monotonic rise and fall, continuous at the plateau edges.
        for (double a = 0; a < 10; a += .25) require(t.weight("formacao", a) <= t.weight("formacao", a + .25), "rise monotonic");
        for (double a = 32; a < 40; a += .25) require(t.weight("formacao", a) >= t.weight("formacao", a + .25), "fall monotonic");
        require(Math.abs(t.weight("formacao", 9.999) - 1) < 1e-3 && Math.abs(t.weight("formacao", 32.001) - 1) < 1e-3, "plateau continuity");
        // Zero rise and fall are steps.
        require(t.weight("impacto", 35.99) == 0 && t.weight("impacto", 36) == 1 && t.weight("impacto", 51.99) == 1
            && t.weight("impacto", 52) == 0, "step phase");
        require(t.progress("formacao", -5) == 0 && near(t.progress("formacao", 10), .25f)
            && t.progress("formacao", 40) == 1 && t.progress("formacao", 90) == 1, "progress");
        require(t.start("impacto") == 36 && t.end("impacto") == 52, "phase bounds");
        require(t.hasPhase("formacao") && !t.hasPhase("eco") && t.phases().iterator().next().equals("formacao"), "phase names");
    }

    private static void easings() {
        for (Easing e : Easing.values()) {
            require(Math.abs(e.apply(0)) < EPS && Math.abs(e.apply(1) - 1) < EPS, "easing ends " + e);
            require(e.apply(-1) == e.apply(0) && e.apply(2) == e.apply(1), "easing clamp " + e);
        }
        require(near(Easing.LINEAR.apply(.25), .25), "linear");
        require(near(Easing.SMOOTH.apply(.5), .5) && near(Easing.SMOOTH.apply(.25), .15625), "smooth");
        require(near(Easing.EASE_IN.apply(.5), .125), "ease in");
        require(near(Easing.EASE_OUT.apply(.5), .875), "ease out");
        require(near(Easing.EASE_IN_OUT.apply(.25), .0625) && near(Easing.EASE_IN_OUT.apply(.5), .5)
            && near(Easing.EASE_IN_OUT.apply(.75), .9375), "ease in out");
        double peak = 0;
        for (double x = 0; x <= 1; x += .01) peak = Math.max(peak, Easing.OVERSHOOT.apply(x));
        require(peak > 1.05 && peak < 1.15, "overshoot peak");
        // Each easing drives the segment that arrives at its keyframe.
        for (Easing e : Easing.values()) {
            var t = Timeline.builder(20).track("v", keyframe(0, 2), keyframe(10, 6, e)).build();
            require(near(t.value("v", 5), 2 + 4 * e.apply(.5)), "track easing " + e);
        }
    }

    private static void tracks() {
        var t = Timeline.builder(100)
            .track("intensidade", keyframe(10, 1), keyframe(30, 3), keyframe(60, 0, Easing.SMOOTH))
            .track("unico", keyframe(50, 7))
            .build();
        require(t.value("intensidade", 0) == 1 && t.value("intensidade", 10) == 1, "hold before first");
        require(near(t.value("intensidade", 20), 2), "linear segment");
        require(t.value("intensidade", 30) == 3, "exact keyframe");
        require(near(t.value("intensidade", 45), 1.5f), "smooth segment midpoint");
        require(t.value("intensidade", 60) == 0 && t.value("intensidade", 99) == 0 && t.value("intensidade", 1e6) == 0, "hold after last");
        require(t.value("unico", 0) == 7 && t.value("unico", 99) == 7, "single keyframe");
        // Continuity at every keyframe.
        for (int tick : new int[]{10, 30, 60})
            require(Math.abs(t.value("intensidade", tick - 1e-4) - t.value("intensidade", tick + 1e-4)) < 1e-3, "keyframe continuity");
        require(t.keyframes("intensidade").size() == 3 && t.tracks().size() == 2, "track listing");
        var sampler = t.at(20);
        require(near(sampler.value("intensidade"), 2) && sampler.localAge() == 20, "sampler");
    }

    private static void markers() {
        var t = Timeline.builder(120).phase("impacto", 36, 52, 2, 8).marker("impacto", 40).marker("inicio", 0).build();
        require(t.tick("impacto") == 40 && t.tick("inicio") == 0, "marker ticks");
        require(t.at(43).since("impacto") == 3 && t.at(30).since("impacto") == -10, "since marker");
        require(t.hasMarker("impacto") && t.hasPhase("impacto"), "phase and marker share a name");
        expect(() -> t.tick("eco"), "unknown marker");
        expect(() -> t.weight("eco", 1), "unknown phase");
        expect(() -> t.value("eco", 1), "unknown track");
    }

    private static void sustain() {
        // Intro 0..40, loop [40,80), exit from 80 to 120.
        var t = Timeline.builder(120).sustain(40, 80)
            .track("feixe", keyframe(0, 0), keyframe(40, 1), keyframe(80, 1), keyframe(120, 0))
            .build();
        require(t.sustained() && t.releaseStart() == 80, "default release start");
        for (int a = 0; a < 80; a++) require(t.localAge(a, -1) == a, "no transform before first wrap");
        require(t.localAge(80, -1) == 40 && t.localAge(119, -1) == 79 && t.localAge(120, -1) == 40
            && t.localAge(1000, -1) == 40, "loop wrap");
        require(near(t.localAge(79.5, -1), 79.5) && near(t.localAge(80.5, -1), 40.5), "partial ticks across wrap");
        // Released at real 130 (loop position 50): jumps to 80 and plays forward without looping.
        require(t.localAge(129, 130) == 49 && t.localAge(130, 130) == 80 && t.localAge(150, 130) == 100
            && t.localAge(170, 130) == 120, "release transition");
        require(t.endAge(130, 120) == 170 && t.endAge(-1, 120) == Long.MAX_VALUE, "end age");
        // Released during the intro: skips the loop.
        require(t.localAge(10, 10) == 80 && t.localAge(9, 10) == 9, "release before loop");
        // Continuity: the local age advances one tick per real tick everywhere except the wrap and the release.
        for (double a = 0; a < 400; a += .5) {
            double now = t.localAge(a, 300), next = t.localAge(a + .5, 300);
            boolean jump = Math.abs(next - now - .5) > EPS;
            if (jump) require((next == 40 && near(now, 79.5)) || (a + .5 == 300 && next == 80), "only wrap and release jump");
        }
        // A seamless loop (equal values at both edges) and a matching exit keep the track continuous.
        for (double a = 0; a < 360; a += .25)
            require(Math.abs(t.value("feixe", t.localAge(a, 300)) - t.value("feixe", t.localAge(a + .25, 300))) <= .25 / 40 + EPS,
                "track continuity with sustain");
        // Explicit release start after a gap.
        var gap = Timeline.builder(100).sustain(20, 40).release(60).build();
        require(gap.localAge(45, -1) == 25 && gap.localAge(45, 45) == 60 && gap.endAge(45, 100) == 85, "explicit release start");
        // No sustain: identity, and the expiry stays at the duration.
        var plain = Timeline.builder(50).build();
        require(!plain.sustained() && plain.localAge(37.5, 10) == 37.5 && plain.endAge(10, 50) == 50
            && plain.sustainStart() == -1 && plain.releaseStart() == -1, "no sustain");
        require(Timeline.empty(30).duration() == 30 && Timeline.empty(30).phases().isEmpty(), "empty");
    }

    private static void validation() {
        expect(() -> Timeline.builder(0), "zero duration");
        expect(() -> Timeline.builder(12001), "duration too long");
        expect(() -> Timeline.builder(50).phase("Fase", 0, 10, 0, 0), "uppercase name");
        expect(() -> Timeline.builder(50).phase("a-b", 0, 10, 0, 0), "dash in name");
        expect(() -> Timeline.builder(50).phase("", 0, 10, 0, 0), "empty name");
        expect(() -> Timeline.builder(50).phase("a", 0, 10, 0, 0).phase("a", 10, 20, 0, 0), "duplicate phase");
        expect(() -> Timeline.builder(50).phase("a", 10, 10, 0, 0), "empty phase");
        expect(() -> Timeline.builder(50).phase("a", 40, 51, 0, 0), "phase past duration");
        expect(() -> Timeline.builder(50).phase("a", -1, 10, 0, 0), "negative phase");
        expect(() -> Timeline.builder(50).phase("a", 0, 10, 6, 5), "rise + fall too long");
        expect(() -> Timeline.builder(50).phase("a", 0, 10, -1, 0), "negative rise");
        expect(() -> Timeline.builder(50).track("v"), "track without keyframes");
        expect(() -> Timeline.builder(50).track("v", keyframe(10, 1), keyframe(10, 2)), "repeated keyframe tick");
        expect(() -> Timeline.builder(50).track("v", keyframe(20, 1), keyframe(10, 2)), "decreasing keyframes");
        expect(() -> Timeline.builder(50).track("v", keyframe(51, 1)), "keyframe past duration");
        expect(() -> keyframe(-1, 1), "negative keyframe");
        expect(() -> keyframe(1, Float.NaN), "NaN keyframe");
        expect(() -> Timeline.builder(50).track("v", keyframe(0, 1)).track("v", keyframe(0, 1)), "duplicate track");
        expect(() -> Timeline.builder(50).marker("m", 50), "marker at duration");
        expect(() -> Timeline.builder(50).marker("m", 1).marker("m", 2), "duplicate marker");
        expect(() -> Timeline.builder(50).sustain(10, 50), "sustain without exit");
        expect(() -> Timeline.builder(50).sustain(10, 10), "empty sustain");
        expect(() -> Timeline.builder(50).sustain(10, 20).sustain(20, 30), "second sustain");
        expect(() -> Timeline.builder(50).release(30).build(), "release without sustain");
        expect(() -> Timeline.builder(50).sustain(10, 30).release(20).build(), "release inside loop");
        expect(() -> Timeline.builder(50).sustain(10, 30).release(50), "release at duration");
        // Names may repeat across kinds.
        Timeline.builder(50).phase("x", 0, 10, 0, 0).track("x", keyframe(0, 1)).marker("x", 3).build();
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-4; }

    private static void expect(Runnable action, String test) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(test + " accepted");
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
