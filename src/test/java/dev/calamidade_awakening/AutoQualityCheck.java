package dev.calamidade_awakening;

import dev.calamidade_awakening.api.AutoQuality;
import dev.calamidade_awakening.api.QualityProfile;
import java.util.function.Function;

/** Automatic quality (0.11.2): hysteresis, cooldown, block after a step down, HERO hold. Pure; run by FrameBudgetCheck. */
final class AutoQualityCheck {
    private static final double TARGET = 1000 / 60.0;

    private AutoQualityCheck() {}

    static void run() {
        down();
        noDownWithoutEffects();
        up();
        noUpWithoutCut();
        heroHold();
        noFlapping();
        spikesIgnored();
    }

    /** Simulated frames at a fixed rate: frame time from the profile, flags constant. */
    private static final class Sim {
        final AutoQuality auto;
        long now;
        int changes;

        Sim(QualityProfile start) { auto = new AutoQuality(start, TARGET, 0); }

        void run(long ms, Function<QualityProfile, Double> frameMs, boolean drawn, boolean cut, boolean hero) {
            long end = now + ms;
            while (now < end) {
                double f = frameMs.apply(auto.current());
                now += Math.max(1, Math.round(f));
                if (auto.frame(now, f, drawn, cut, hero)) changes++;
            }
        }

        /** Runs until the profile changes (or {@code max} ms); returns the time it took. */
        long untilChange(long max, Function<QualityProfile, Double> frameMs, boolean drawn, boolean cut, boolean hero) {
            long start = now;
            int before = changes;
            while (changes == before && now - start < max) run(1, frameMs, drawn, cut, hero);
            return now - start;
        }
    }

    private static void down() {
        Sim s = new Sim(QualityProfile.HIGH);
        s.run(AutoQuality.COOLDOWN_MS, p -> 25.0, true, false, false);
        require(s.changes == 0, "no change in the first seconds of AUTO");
        long t = s.untilChange(60_000, p -> 25.0, true, false, false);
        require(t > AutoQuality.WINDOW_MS && t <= 3 * AutoQuality.WINDOW_MS + 100, "two slow windows: " + t + " ms");
        require(s.auto.current() == QualityProfile.BALANCED, "one step down");
        var r = s.auto.reason();
        require(r.change() == AutoQuality.Change.DOWN && r.from() == QualityProfile.HIGH && r.p95() > r.thresholdMs()
            && Math.abs(r.thresholdMs() - TARGET * 1.2) < 1e-9, "down reason");
        t = s.untilChange(60_000, p -> 25.0, true, false, false);
        require(t >= AutoQuality.COOLDOWN_MS, "cooldown after a change: " + t + " ms");
        s.run(60_000, p -> 25.0, true, false, false);
        require(s.auto.current() == QualityProfile.LOW, "keeps going down to LOW");
        s.run(60_000, p -> 25.0, true, false, false);
        require(s.auto.current() == QualityProfile.LOW, "never below LOW");
    }

    private static void noDownWithoutEffects() {
        Sim s = new Sim(QualityProfile.HIGH);
        s.run(60_000, p -> 30.0, false, false, false);
        require(s.changes == 0, "slow frames without effects do not lower the profile");
    }

    private static void up() {
        Sim s = new Sim(QualityProfile.BALANCED);
        s.run(AutoQuality.COOLDOWN_MS, p -> 8.0, true, true, false);
        long t = s.untilChange(60_000, p -> 8.0, true, true, false);
        require(t > 3 * AutoQuality.WINDOW_MS && t <= 5 * AutoQuality.WINDOW_MS + 100, "four fast windows: " + t + " ms");
        require(s.auto.current() == QualityProfile.HIGH, "one step up");
        require(s.auto.reason().change() == AutoQuality.Change.UP, "up reason");
        s.run(120_000, p -> 8.0, true, true, false);
        require(s.auto.current() == QualityProfile.ULTRA, "up to ULTRA, never above");
    }

    private static void noUpWithoutCut() {
        Sim s = new Sim(QualityProfile.BALANCED);
        s.run(60_000, p -> 5.0, true, false, false);
        require(s.changes == 0, "fast frames with nothing cut do not raise the profile");
    }

    private static void heroHold() {
        Sim s = new Sim(QualityProfile.HIGH);
        s.run(30_000, p -> 40.0, true, true, true);
        require(s.changes == 0, "no change while a HERO effect is in progress");
        long t = s.untilChange(60_000, p -> 40.0, true, true, false);
        require(t > 2 * AutoQuality.WINDOW_MS && s.auto.current() == QualityProfile.BALANCED,
            "after the HERO effect, two full windows before going down: " + t + " ms");
        // A HERO frame in the middle of fast windows also restarts the up streak.
        Sim u = new Sim(QualityProfile.BALANCED);
        u.run(AutoQuality.COOLDOWN_MS + 3 * AutoQuality.WINDOW_MS, p -> 8.0, true, true, false);
        u.run(50, p -> 8.0, true, true, true);
        long tu = u.untilChange(60_000, p -> 8.0, true, true, false);
        require(tu > 3 * AutoQuality.WINDOW_MS, "HERO window discards the up streak: " + tu + " ms");
    }

    /** HIGH is too slow, BALANCED has room and cuts effects: without hysteresis it would flip every few seconds. */
    private static void noFlapping() {
        Sim s = new Sim(QualityProfile.HIGH);
        Function<QualityProfile, Double> cost = p -> p.ordinal() >= QualityProfile.HIGH.ordinal() ? 26.0 : 9.0;
        s.run(600_000, cost, true, true, false);
        require(s.changes <= 7, "at most a few changes in 10 minutes: " + s.changes);
        s.run(30_000, cost, true, true, false);
        require(s.auto.current() == QualityProfile.BALANCED || s.auto.reason().change() == AutoQuality.Change.UP,
            "settles on BALANCED most of the time");
        int before = s.changes;
        s.run(300_000, cost, true, true, false);
        require(s.changes - before <= 1, "the block grows: at most one more try in 5 minutes");
    }

    /** A few slow frames per window (chunk loading) stay under the p95. */
    private static void spikesIgnored() {
        Sim s = new Sim(QualityProfile.HIGH);
        int[] n = {0};
        s.run(60_000, p -> ++n[0] % 50 == 0 ? 80.0 : 10.0, true, false, false);
        require(s.changes == 0, "2% spikes do not lower the profile");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
