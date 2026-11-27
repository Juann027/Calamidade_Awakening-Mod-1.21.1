package dev.calamidade_awakening;

import dev.calamidade_awakening.api.FrameBudget;
import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.FrameStats;
import dev.calamidade_awakening.api.Quality;
import dev.calamidade_awakening.api.QualityProfile;
import java.util.ArrayList;
import java.util.List;

/** Per-frame caps, the internal test profile UNLIMITED and frame statistics (0.11.2). Pure; run by FrameBudgetCheck. */
final class LimitsCheck {
    private LimitsCheck() {}

    static void run() {
        profiles();
        newProfiles();
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

    /** 0.15.0 fase 3: separate light and cookie budgets per profile; cookies never above the applied lights. */
    private static void fase3() {
        int[][] expected = {{24, 16}, {64, 48}, {128, 96}, {192, 128}};
        for (QualityProfile p : QualityProfile.values()) {
            FrameLimits l = p.limits();
            require(l.lights() == expected[p.ordinal()][0] && l.cookies() == expected[p.ordinal()][1],
                "fase 3 budget " + p + ": " + l.lights() + "/" + l.cookies());
        }
        var c = new QualityProfile.Caps(32, 12, 4, 2, 128, 40, 100);
        require(c.cookies() == 100 && QualityProfile.ULTRA.limits(c).cookies() == 40, "cookies stored apart, applied up to the lights");
        require(new QualityProfile.Caps(32, 12, 4, 2, 128, 64).cookies() == 16, "six-cap form keeps the old cookie policy");
        require(new QualityProfile.Caps(1, 1, 1, 1, 1, 1, 9999).cookies() == FrameLimits.MAX_COOKIES, "cookie safety max");
        require(QualityProfile.LEGACY_LIGHTS.length == QualityProfile.values().length && QualityProfile.LEGACY_LIGHTS[3] == 64,
            "legacy light defaults for the config migration");
        for (QualityProfile p : QualityProfile.values())
            require(p.defaults.lights() != QualityProfile.LEGACY_LIGHTS[p.ordinal()], "migration can tell old defaults apart: " + p);
    }

    private static void unlimited() {
        FrameLimits u = FrameLimits.unlimited();
        require(u.test() && u.tier() == Quality.HIGH, "test profile keeps the HIGH tier for addons");
        require(u.effects() == FrameLimits.MAX_EFFECTS && u.marks() == FrameLimits.MAX_PASSES
            && u.beams() == FrameLimits.MAX_BEAMS && u.lights() == FrameLimits.MAX_LIGHTS, "test profile lifts the caps");
        require(u.label().equals("UNLIMITED(96/96)"), "test profile label " + u.label());
        FrameLimits clamped = new FrameLimits(Quality.LOW, "x", 500, -3, 999, 999, 1000, 1000, -1, true);
        require(clamped.effects() == 96 && clamped.marks() == 0 && clamped.heroPasses() == 96 && clamped.heroes() == 96
            && clamped.beams() == 128 && clamped.lights() == 192 && clamped.vertices() == 0, "caps clamped to the hard maxima");
        require(clamped.cookies() == 16, "pre-fase-3 constructor: cookies of the old policy (a quarter of the lights, 16 at most)");
        require(u.cookies() == FrameLimits.MAX_COOKIES, "test profile lifts the cookie cap");
        // 60 NORMAL candidates: all drawn under UNLIMITED, 12 under HIGH; HERO still capped at 2.
        List<FrameBudget.Candidate<Integer>> c = new ArrayList<>();
        for (int i = 0; i < 60; i++) c.add(new FrameBudget.Candidate<>(i, 14, 1, i, 128, i < 5));
        List<Integer> all = FrameBudget.select(c, u.effects());
        require(all.size() == 57, "unlimited draws every NORMAL and the 2 nearest HERO: " + all.size());
        require(FrameBudget.select(c, FrameLimits.of(Quality.HIGH).effects()).size() == 12, "HIGH still draws 12");
        require(FrameLimits.unlimited(12, 4).marks() == 4 && FrameLimits.unlimited(12, 4).effects() == 12, "test caps");
    }

    /** 0.11.2: LOW keeps the caps of 0.11.1, the others grow; ULTRA shows HIGH to addons; config caps clamped. */
    private static void newProfiles() {
        for (QualityProfile p : List.of(QualityProfile.LOW)) {
            FrameLimits a = p.limits(), b = FrameLimits.of(p.tier);
            require(a.effects() == b.effects() && a.marks() == b.marks() && a.heroPasses() == b.heroPasses()
                && a.heroes() == b.heroes() && a.beams() == b.beams()
                && a.vertices() == b.vertices() && a.tier() == p.tier, "profile " + p + " keeps the 0.11.1 caps (lights: fase 3)");
        }
        QualityProfile[] all = QualityProfile.values();
        for (int i = 1; i < all.length; i++)
            require(all[i].defaults.effects() > all[i - 1].defaults.effects() && all[i].defaults.passes() > all[i - 1].defaults.passes(),
                "each profile draws more than the one below: " + all[i]);
        require(QualityProfile.BALANCED.defaults.effects() >= Quality.BALANCED.effects
            && QualityProfile.HIGH.defaults.effects() >= Quality.HIGH.effects, "0.11.2 defaults never below 0.11.1");
        FrameLimits ultra = QualityProfile.ULTRA.limits();
        require(ultra.tier() == Quality.HIGH && ultra.label().equals("ULTRA"), "ULTRA shows HIGH to addons");
        require(ultra.effects() > QualityProfile.HIGH.defaults.effects() && ultra.marks() > QualityProfile.HIGH.defaults.passes()
            && ultra.vertices() == Quality.HIGH.vertices * 3 / 2, "ULTRA above HIGH");
        require(QualityProfile.LOW.lower() == QualityProfile.LOW && QualityProfile.ULTRA.higher() == QualityProfile.ULTRA
            && QualityProfile.BALANCED.higher() == QualityProfile.HIGH && QualityProfile.HIGH.lower() == QualityProfile.BALANCED,
            "profile steps");
        var caps = new QualityProfile.Caps(1000, 1000, 1000, 1000, 1000, 1000);
        require(caps.effects() == QualityProfile.Caps.MAX_EFFECTS && caps.passes() == QualityProfile.Caps.MAX_PASSES
            && caps.heroPasses() == QualityProfile.Caps.MAX_HERO_PASSES && caps.heroes() == QualityProfile.Caps.MAX_HEROES
            && caps.beams() == FrameLimits.MAX_BEAMS && caps.lights() == FrameLimits.MAX_LIGHTS, "config caps: safety maxima");
        fase3();
        var low = new QualityProfile.Caps(0, -1, -1, 3, -1, -1);
        require(low.effects() == 1 && low.heroes() == 1 && low.passes() == 0, "at least 1 drawn; HERO never above drawn");
        // HERO cap from the config: 3 HERO with a cap of 1 draw the nearest one only.
        List<FrameBudget.Candidate<Integer>> c = new ArrayList<>();
        for (int i = 0; i < 3; i++) c.add(new FrameBudget.Candidate<>(i, 10 + i, 1, i, 128, true));
        require(FrameBudget.select(c, 6, 1).equals(List.of(0)), "configured HERO cap");
        require(FrameBudget.select(c, 2, 4).size() == 2, "HERO cap clamped to the drawn cap");
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
