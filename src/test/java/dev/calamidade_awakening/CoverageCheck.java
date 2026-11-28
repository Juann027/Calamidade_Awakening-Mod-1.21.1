package dev.calamidade_awakening;

import dev.calamidade_awakening.api.AutoQuality;
import dev.calamidade_awakening.api.CoverageBudget;
import dev.calamidade_awakening.api.EnginePasses;
import dev.calamidade_awakening.api.LightBudget;
import dev.calamidade_awakening.api.QualityProfile;

/**
 * 0.15.0 fase 4 (coverage first), pure; run by FrameBudgetCheck: spatial spread of the light selection, density
 * admission per screen tile, config migration of every earlier default, AUTO relief before a profile change.
 */
final class CoverageCheck {
    private CoverageCheck() {}

    static void run() {
        spread();
        criticalFirst();
        stability();
        density();
        migration();
        relief();
        require(EnginePasses.Pool.ENGINE.drawn() && EnginePasses.Pool.values().length == 4
            && EnginePasses.Pool.values()[3] == EnginePasses.Pool.ENGINE, "ENGINE pool appended, drawn");
    }

    /** An arena: 12 groups of 16 lights in different world cells; the nearest group is the brightest. */
    private static void spread() {
        int groups = 12, per = 16, n = groups * per;
        int[] tier = new int[n];
        float[] score = new float[n];
        long[] cell = new long[n];
        for (int g = 0; g < groups; g++) for (int i = 0; i < per; i++) {
            int k = g * per + i;
            tier[k] = 1;
            score[k] = 1f / (1 + g) * (1 - i * .01f);   // group 0 near and bright, group 11 far and dim
            cell[k] = CoverageBudget.cell(g * 20, 64, 0);
        }
        boolean[] old = LightBudget.select(n, tier, score, null, 24);
        boolean[] now = CoverageBudget.select(n, tier, score, null, cell, 24);
        int oldCells = CoverageBudget.cells(cell, old), newCells = CoverageBudget.cells(cell, now);
        require(oldCells <= 2, "fase 3 piles the cap onto the nearest groups: " + oldCells);
        require(newCells == groups, "fase 4 keeps every group of the arena with 24 lights: " + newCells);
        require(count(now) == 24 && count(old) == 24, "the cap is the same");
        require(now[0], "the most important light is still the first kept");
        // Spread is not uniform: the nearer (brighter) groups keep more lights than the far ones.
        int near = 0, far = 0;
        for (int i = 0; i < per; i++) { if (now[i]) near++; if (now[11 * per + i]) far++; }
        require(near >= far && near >= 2, "contribution still matters: near " + near + " far " + far);
        require(java.util.Arrays.equals(CoverageBudget.select(n, tier, score, null, null, 24), old), "null cells = fase 3");
        boolean[] all = CoverageBudget.select(n, tier, score, null, cell, n);
        require(count(all) == n, "under the cap everything stays");
    }

    /** CRITICAL lights are never traded for a better spread. */
    private static void criticalFirst() {
        int n = 40;
        int[] tier = new int[n];
        float[] score = new float[n];
        long[] cell = new long[n];
        for (int i = 0; i < n; i++) {
            tier[i] = i < 6 ? 3 : 0;
            score[i] = i < 6 ? .001f : 1;
            cell[i] = i < 6 ? CoverageBudget.cell(0, 0, 0) : CoverageBudget.cell(i * 16, 0, 0);
        }
        boolean[] keep = CoverageBudget.select(n, tier, score, null, cell, 8);
        for (int i = 0; i < 6; i++) require(keep[i], "CRITICAL " + i + " kept even when piled in one cell");
        require(count(keep) == 8, "cap");
    }

    /** No blinking: same scene, kept set constant; similar lights trading 10% each frame do not swap. */
    private static void stability() {
        int n = 48;
        int[] tier = new int[n];
        long[] cell = new long[n];
        boolean[] prev = null;
        int swaps = 0;
        for (int f = 0; f < 200; f++) {
            float[] score = new float[n];
            for (int i = 0; i < n; i++) {
                tier[i] = 1;
                cell[i] = CoverageBudget.cell((i % 8) * 9, 0, (i / 8) * 9);
                score[i] = 1 + .1f * (float) Math.sin(f * .7 + i);   // camera moving: scores wobble
            }
            boolean[] k = CoverageBudget.select(n, tier, score, prev, cell, 20);
            if (prev != null) for (int i = 0; i < n; i++) if (k[i] != prev[i]) swaps++;
            prev = k;
        }
        require(swaps == 0, "hysteresis with spread: no swaps while scores wobble 10%: " + swaps);
    }

    /** Density admission: spread lights come back over the cap, piled ones do not; per-tile cost bounded. */
    private static void density() {
        int n = 192;
        int[] tier = new int[n];
        float[] score = new float[n];
        int[][] rect = new int[n][];
        for (int i = 0; i < n; i++) {
            tier[i] = 1;
            score[i] = 1 - i * .001f;
            int tx = i % LightBudget.TILES_X, ty = (i / LightBudget.TILES_X) % LightBudget.TILES_Y;
            rect[i] = new int[] {tx, ty, tx, ty};   // spread: one light per tile, twice over the screen (144 tiles)
        }
        boolean[] keep = LightBudget.select(n, tier, score, null, 24);
        int[] tiles = new int[LightBudget.TILES];
        int per = CoverageBudget.tileLights(24);
        require(per == 8, "LOW density 8 lights per tile");
        int added = CoverageBudget.admit(n, keep, tier, score, null, rect, per, 192, tiles);
        require(added == 192 - 24 && count(keep) == 192, "spread: every light admitted: " + added);
        int max = 0;
        for (int t : tiles) max = Math.max(max, t);
        require(max <= per, "per-tile count bounded: " + max);
        // Piled: all on one tile: nothing admitted beyond the density.
        for (int i = 0; i < n; i++) rect[i] = new int[] {3, 3, 3, 3};
        keep = LightBudget.select(n, tier, score, null, 24);
        added = CoverageBudget.admit(n, keep, tier, score, null, rect, per, 192, tiles);
        require(added == 0, "piled: the cap already exceeds the density, nothing admitted: " + added);
        // Full-screen lights (null rect) only while every tile has room.
        keep = new boolean[n];
        for (int i = 0; i < 4; i++) keep[i] = true;
        int[][] full = new int[n][];
        added = CoverageBudget.admit(n, keep, tier, score, null, full, per, 192, tiles);
        require(added == per - 4, "full-screen lights fill up to the density: " + added);
        // The cap already piled 12 lights on one tile: the other tiles fill up to 12, that tile gets nothing.
        int[][] mixed = new int[n][];
        keep = new boolean[n];
        for (int i = 0; i < n; i++) {
            int tx = i % 16, ty = (i / 16) % 9;
            mixed[i] = i < 12 ? new int[] {0, 0, 0, 0} : new int[] {tx, ty, tx, ty};
            keep[i] = i < 12;
        }
        added = CoverageBudget.admit(n, keep, tier, score, null, mixed, per, 192, tiles);
        int worst = 0;
        for (int t : tiles) worst = Math.max(worst, t);
        require(worst == 12 && tiles[0] == 12 && added == 179, "the worst tile of the cap is the limit, never raised (light 144 falls on the full tile): worst "
            + worst + " added " + added);
        // Ceiling respected.
        for (int i = 0; i < n; i++) { int tx = i % 16, ty = (i / 16) % 9; rect[i] = new int[] {tx, ty, tx, ty}; }
        keep = LightBudget.select(n, tier, score, null, 24);
        added = CoverageBudget.admit(n, keep, tier, score, null, rect, per, 64, tiles);
        require(count(keep) == 64, "ceiling: " + count(keep));
        // CRITICAL over cap is admitted before NORMAL.
        tier[150] = 3;
        keep = LightBudget.select(n, tier, score, null, 24);
        require(keep[150], "CRITICAL inside the cap");
        // Temporal hysteresis: a new light waits SETTLE frames that it fits; an admitted one tolerates a quarter more.
        int[][] one = new int[3][];
        for (int i = 0; i < 3; i++) one[i] = new int[] {0, 0, 0, 0};
        int[] t3 = {1, 1, 1};
        float[] s3 = {3, 2, 1};
        int[] streak = new int[3];
        int frames = 0;
        boolean[] k3;
        do {
            k3 = new boolean[] {true, false, false};
            CoverageBudget.admit(3, k3, t3, s3, null, one, 2, 3, tiles, streak, CoverageBudget.SETTLE);
            frames++;
        } while (!k3[1] && frames < 20);
        require(frames == CoverageBudget.SETTLE && !k3[2], "a new light enters after " + frames + " frames, density respected");
        boolean[] was3 = {false, true, false};
        k3 = new boolean[] {true, false, false};
        int[][] two = {{0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}};
        CoverageBudget.admit(3, k3, t3, s3, was3, two, 1, 3, tiles, new int[3], CoverageBudget.SETTLE);
        require(k3[1], "an admitted light stays one over the limit (no blinking at a tile edge)");
        require(CoverageBudget.tileCookies(16) == 4 && CoverageBudget.tileCookies(128) == 32 && CoverageBudget.tileLights(192) == 64,
            "densities by profile");
    }

    private static void migration() {
        // Values seen in a real config file (RTX 4060 test): BALANCED drawn 6 and lights 24 are provisional 0.11.2 defaults.
        require(QualityProfile.BALANCED.legacy(0, 6) && QualityProfile.BALANCED.legacy(5, 24) && QualityProfile.BALANCED.legacy(5, 32),
            "BALANCED earlier defaults recognized");
        require(QualityProfile.HIGH.legacy(0, 12) && QualityProfile.ULTRA.legacy(0, 24) && QualityProfile.LOW.legacy(5, 8),
            "HIGH 12 drawn, ULTRA 24 drawn, LOW 8 lights");
        require(!QualityProfile.LOW.legacy(0, 1) && !QualityProfile.ULTRA.legacy(0, 64), "values the player chose stay (LOW 1, ULTRA 64)");
        // Phase 5: the phase 4 defaults of the lights and cookies are earlier defaults; customized values are not.
        int[][] phase4 = {{24, 16}, {64, 48}, {128, 96}, {192, 128}};
        for (QualityProfile p : QualityProfile.values()) {
            if (p != QualityProfile.ULTRA) require(p.legacy(5, phase4[p.ordinal()][0]), "phase 4 lights default is legacy: " + p);
            require(p.legacy(6, phase4[p.ordinal()][1]), "phase 4 cookies default is legacy: " + p);
        }
        require(!QualityProfile.LOW.legacy(5, 20) && !QualityProfile.LOW.legacy(6, 12) && !QualityProfile.HIGH.legacy(6, 100)
            && !QualityProfile.ULTRA.legacy(6, 64) && !QualityProfile.ULTRA.legacy(5, 160), "customized lights and cookies stay");
        require(dev.calamidade_awakening.config.ClientConfig.CAPS_VERSION == 4, "phase 5 migration version");
        for (QualityProfile p : QualityProfile.values()) {
            int[] d = p.defaultValues();
            for (int i = 0; i < d.length; i++) require(!p.legacy(i, d[i]), "a current default is never an earlier one: " + p + " " + i);
            require(d[5] == p.defaults.lights() && d[6] == p.defaults.cookies(), "defaultValues order");
        }
    }

    /** AUTO: under pressure with the extras in use, relief first, then the profile; relief lifted later. */
    private static void relief() {
        double target = 1000 / 60.0;
        var auto = new AutoQuality(QualityProfile.HIGH, target, 0);
        long now = 0;
        int changes = 0;
        java.util.List<AutoQuality.Change> seen = new java.util.ArrayList<>();
        // 30 s slow with the extras in use.
        while (now < 30_000) {
            now += 25;
            if (auto.frame(now, 25, true, false, false, true)) { changes++; seen.add(auto.reason().change()); }
        }
        require(!seen.isEmpty() && seen.get(0) == AutoQuality.Change.RELIEF, "relief comes first: " + seen);
        require(seen.size() >= 2 && seen.get(1) == AutoQuality.Change.DOWN, "then the profile goes down: " + seen);
        require(!auto.relief() || auto.current() != QualityProfile.HIGH, "a new profile starts without relief");
        // Fast frames: relief lifted only after its block, never flapping.
        var b = new AutoQuality(QualityProfile.HIGH, target, 0);
        now = 0;
        seen.clear();
        while (now < 30_000 && seen.isEmpty()) { now += 25; if (b.frame(now, 25, true, false, false, true)) seen.add(b.reason().change()); }
        require(b.relief() && b.current() == QualityProfile.HIGH, "relieved, same profile: " + seen);
        long reliefAt = b.reason().atMs();
        while (now < reliefAt + AutoQuality.BLOCK_MS - 1000) {
            now += 8;
            if (b.frame(now, 8, true, false, false, false)) seen.add(b.reason().change());
        }
        require(b.relief(), "relief held during its block");
        while (now < reliefAt + AutoQuality.BLOCK_MS + 30_000) {
            now += 8;
            if (b.frame(now, 8, true, false, false, false)) seen.add(b.reason().change());
        }
        require(!b.relief() && seen.contains(AutoQuality.Change.RESTORE) && b.current() == QualityProfile.HIGH,
            "relief lifted after the block, profile kept: " + seen);
        // Without the flag, 0.11.2 behavior (direct DOWN).
        var c = new AutoQuality(QualityProfile.HIGH, target, 0);
        now = 0;
        while (now < 20_000 && c.current() == QualityProfile.HIGH) { now += 25; c.frame(now, 25, true, false, false); }
        require(c.current() == QualityProfile.BALANCED && c.reason().change() == AutoQuality.Change.DOWN && !c.relief(),
            "without the extras AUTO steps down as before");
    }

    private static int count(boolean[] k) {
        int c = 0;
        for (boolean b : k) if (b) c++;
        return c;
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError("CoverageCheck: " + test);
        System.out.println("ok - " + test);
    }
}
