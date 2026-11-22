package dev.calamidade_awakening;

import dev.calamidade_awakening.api.FrameBudget;
import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.FrameStats;
import dev.calamidade_awakening.api.Quality;
import java.util.ArrayList;
import java.util.List;

/** Per-frame caps, the internal test profile UNLIMITED and frame statistics (0.11.2). Pure; run by FrameBudgetCheck. */
final class LimitsCheck {
    private LimitsCheck() {}

    static void run() {
        profiles();
        unlimited();
        stats();
    }

    private static void profiles() {
        for (Quality q : Quality.values()) {
            FrameLimits l = FrameLimits.of(q);
            require(l.tier() == q && l.effects() == q.effects && l.marks() == q.marks && l.heroPasses() == q.heroPasses
                && l.beams() == q.beams && l.lights() == q.lights && l.vertices() == q.vertices && !l.test(),
                "profile caps equal the quality " + q);
            require(l.label().equals(q.name()), "profile label " + q);
        }
    }

    private static void unlimited() {
        FrameLimits u = FrameLimits.unlimited();
        require(u.test() && u.tier() == Quality.HIGH, "test profile keeps the HIGH tier for addons");
        require(u.effects() == FrameLimits.MAX_EFFECTS && u.marks() == FrameLimits.MAX_PASSES
            && u.beams() == FrameLimits.MAX_BEAMS && u.lights() == FrameLimits.MAX_LIGHTS, "test profile lifts the caps");
        require(u.label().equals("UNLIMITED(96/96)"), "test profile label " + u.label());
        FrameLimits clamped = new FrameLimits(Quality.LOW, 500, -3, 999, 1000, 1000, -1, true);
        require(clamped.effects() == 96 && clamped.marks() == 0 && clamped.heroPasses() == 96 && clamped.beams() == 128
            && clamped.lights() == 64 && clamped.vertices() == 0, "caps clamped to the safety maxima");
        // 60 NORMAL candidates: all drawn under UNLIMITED, 12 under HIGH; HERO still capped at 2.
        List<FrameBudget.Candidate<Integer>> c = new ArrayList<>();
        for (int i = 0; i < 60; i++) c.add(new FrameBudget.Candidate<>(i, 14, 1, i, 128, i < 5));
        List<Integer> all = FrameBudget.select(c, u.effects());
        require(all.size() == 57, "unlimited draws every NORMAL and the 2 nearest HERO: " + all.size());
        require(FrameBudget.select(c, FrameLimits.of(Quality.HIGH).effects()).size() == 12, "HIGH still draws 12");
        require(FrameLimits.unlimited(12, 4).marks() == 4 && FrameLimits.unlimited(12, 4).effects() == 12, "test caps");
    }

    private static void stats() {
        double[] ms = new double[100];
        for (int i = 0; i < 100; i++) ms[99 - i] = i + 1;
        double[] s = FrameStats.summary(ms);
        require(s[0] == 50 && s[1] == 95 && Math.abs(s[2] - 50.5) < 1e-9, "median, p95 and mean of 1..100");
        require(ms[0] == 100, "summary does not sort the caller's array");
        require(FrameStats.summary(new double[0])[1] == 0, "empty summary");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
