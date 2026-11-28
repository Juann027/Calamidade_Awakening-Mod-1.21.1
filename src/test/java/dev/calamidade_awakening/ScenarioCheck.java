package dev.calamidade_awakening;

import dev.calamidade_awakening.api.CoverageBudget;
import dev.calamidade_awakening.api.LightBudget;
import dev.calamidade_awakening.api.QualityProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.15.0 phase 5, pure; run by FrameBudgetCheck. Rebuilds the geometry of the vfx-sample coverage scenes
 * ({@code /vfxsample coverage arena|combat|mixed}) in front of a camera and runs the same selection the client runs
 * (spatial spread under the cap, density admission per tile, then the cookie budget with its own admission) until it
 * settles. It explains why combat and mixed kept fewer lights on LOW than the arena (docs/LIGHT-QUALITY-0.15.5.md) and
 * pins the guarantees of the phase 5 budgets: CRITICAL lights and their cookies always kept, ULTRA keeps every light
 * and every cookie of the arena (192 lights, 84 cookies) and of 192 textured lights.
 */
final class ScenarioCheck {
    private ScenarioCheck() {}

    /** One light of a scene: bounding sphere relative to the camera, visible gain, tier, cookie, fallback NONE. */
    record L(double x, double y, double z, double radius, float gain, int tier, boolean cookie, boolean none) {}

    /** Result of a settled frame. */
    record R(int lights, int kept, int byCap, int admitted, int cookies, int sampled, int criticalKept, int critical,
             int criticalSampled, int criticalCookies, int worstTile) {}

    static final double EYE = 1.6, AHEAD = 18;
    static final int ROWS = 192;

    /** Lights and cookie caps of the phase 4 defaults (LOW, BALANCED, HIGH, ULTRA), for the comparison report. */
    static final int[][] PHASE4 = {{24, 16}, {64, 48}, {128, 96}, {192, 128}};

    static void run() {
        var profiles = QualityProfile.values();
        System.out.println("phase 5 coverage scenes (lights kept/total, cookies sampled/requested), phase 4 -> phase 5 defaults:");
        for (String scene : List.of("arena", "combat", "mixed")) {
            List<L> lights = scene(scene);
            for (var p : profiles) {
                R old = settle(lights, PHASE4[p.ordinal()][0], PHASE4[p.ordinal()][1]);
                R r = settle(lights, p.defaults.lights(), p.defaults.cookies());
                System.out.printf("  %-6s %-8s lights %3d -> %3d of %3d (cap %3d +%3d density, worst tile %2d)   cookies %3d -> %3d of %3d%n",
                    scene, p, old.kept, r.kept, r.lights, r.byCap, r.admitted, r.worstTile, old.sampled, r.sampled, r.cookies);
                require(r.criticalKept == r.critical, scene + " " + p + ": every CRITICAL light kept");
                require(r.criticalSampled == r.criticalCookies, scene + " " + p + ": every CRITICAL cookie sampled");
                require(r.kept >= old.kept && r.sampled >= old.sampled, scene + " " + p + ": phase 5 never keeps less");
                if (p == QualityProfile.ULTRA)
                    require(r.kept == r.lights && r.sampled == r.cookies, scene + " ULTRA keeps the whole scene: " + r);
            }
        }
        R combat4 = settle(scene("combat"), 24, 16), combat5 = settle(scene("combat"), 32, 24);
        R mixed4 = settle(scene("mixed"), 24, 16), mixed5 = settle(scene("mixed"), 32, 24);
        require(combat5.kept > combat4.kept && combat5.sampled > combat4.sampled, "LOW phase 5 keeps more in combat: "
            + combat4 + " -> " + combat5);
        require(mixed5.sampled > mixed4.sampled, "LOW phase 5 keeps more projections in mixed: " + mixed4 + " -> " + mixed5);
        // ULTRA: 192 textured lights spread over the screen all keep their cookie (cookie cap = light cap = 192).
        List<L> textured = new ArrayList<>();
        for (int i = 0; i < 192; i++) {
            double a = i * Math.PI * 2 / 192 * 7;
            textured.add(new L(Math.cos(a) * (4 + i % 24), 3 - EYE, -AHEAD - Math.sin(a) * (4 + i % 24), 3, 1, 1, true, false));
        }
        R ultra4 = settle(textured, 192, 128);
        R ultra = settle(textured, QualityProfile.ULTRA.defaults.lights(), QualityProfile.ULTRA.defaults.cookies());
        System.out.printf("  ULTRA 192 textured lights: %d kept, cookies sampled %d -> %d%n", ultra.kept, ultra4.sampled, ultra.sampled);
        require(ultra.kept == 192 && ultra.sampled == 192, "ULTRA: 192 simultaneous cookies: " + ultra);
    }

    /** The scene of LaboratorioCobertura, camera at the origin (eye height), looking down -z, centre 18 blocks ahead. */
    static List<L> scene(String name) {
        List<L> out = new ArrayList<>();
        switch (name) {
            case "arena" -> {
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    arena(out, Math.sin(a) * 14, -AHEAD - Math.cos(a) * 14);
                }
            }
            case "combat" -> {
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2 + Math.PI / 4;
                    decoration(out, Math.sin(a) * 9, -AHEAD - Math.cos(a) * 9);
                }
                reticle(out, -4, -AHEAD);
                reticle(out, 4, -AHEAD + 3);
            }
            case "mixed" -> {
                for (int i = 0; i < 4; i++) mixed(out, (i % 2 - .5) * 16, -AHEAD - (i / 2 - .5) * 16);
            }
            default -> throw new IllegalArgumentException(name);
        }
        return out;
    }

    private static void arena(List<L> out, double x, double z) {
        projector(out, x, 6, z, 7, 14, 2.6f, 1, true, false);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            projector(out, x + Math.cos(a) * 4.5, 5, z + Math.sin(a) * 4.5, 2.4, 9, 2, 0, true, false);
        }
        out.add(new L(x, -EYE, z, 6, .7f, 0, false, false));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + .4;
            out.add(new L(x + Math.cos(a) * 3, 1 - EYE, z + Math.sin(a) * 3, 4, 1.6f, 0, false, false));
        }
    }

    private static void decoration(List<L> out, double x, double z) {
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8, r = 3 + (i % 2) * 2.5;
            if (i % 2 == 0) projector(out, x + Math.cos(a) * r, 5, z + Math.sin(a) * r, 3, 9, 3, 0, true, false);
            else out.add(new L(x + Math.cos(a) * r, 1 - EYE, z + Math.sin(a) * r, 5, 1.8f, 0, false, false));
        }
    }

    private static void reticle(List<L> out, double x, double z) {
        projector(out, x, 7, z, 7, 10, 1, 3, true, true);
        projector(out, x, 7, z, 7, 10, .35f, 3, true, false);
    }

    private static void mixed(List<L> out, double x, double z) {
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2, cx = Math.cos(a), cz = Math.sin(a);
            out.add(new L(x + cx * 2, 1.2 - EYE, z + cz * 2, 6, 2.2f, 1, false, false));
            projector(out, x + cx * 5, 6, z + cz * 5, 4, 9, 3, 1, true, false);
            projector(out, x + (cx - cz) * 3.5, 6, z + (cz + cx) * 3.5, 3, 9, 2.5f, 1, true, false);
            projector(out, x + (cx + cz) * 7, 6, z + (cz - cx) * 7, 3.5, 9, .6f, 1, true, false);
        }
    }

    /** A downward projector: bounding sphere as LightFrame.bounds (centre half the radius below the source). */
    private static void projector(List<L> out, double x, double y, double z, double size, double radius, float gain,
                                  int tier, boolean cookie, boolean none) {
        double bound = Math.sqrt(radius * radius * .25 + size * size * .5);
        out.add(new L(x, y - radius * .5 - EYE, z, bound, gain, tier, cookie, none));
    }

    /** Runs the selection 16 frames with the previous frame fed back (hysteresis and SETTLE streaks), returns the last. */
    static R settle(List<L> scene, int cap, int cookieCap) {
        var proj = new org.joml.Matrix4f().perspective((float) Math.toRadians(70), 16 / 9f, .05f, 1000f);
        float[] m = new float[16];
        proj.get(m);
        int n = scene.size();
        int[] tier = new int[n];
        float[] score = new float[n];
        long[] cell = new long[n];
        int[][] rect = new int[n][];
        for (int i = 0; i < n; i++) {
            L l = scene.get(i);
            tier[i] = l.tier;
            score[i] = LightBudget.score(l.gain, l.radius, Math.sqrt(l.x * l.x + l.y * l.y + l.z * l.z));
            cell[i] = CoverageBudget.cell(l.x, l.y + EYE, l.z);
            int[] r = new int[4];
            rect[i] = LightBudget.tiles(m, l.x, l.y, l.z, l.radius, r) ? r : null;
        }
        boolean[] was = null, wasAdmitted = new boolean[n], cookieWas = null, cookieWasAdmitted = null;
        int[] streak = new int[n];
        int[] cookieStreak = null;
        int[] tiles = new int[LightBudget.TILES];
        R result = null;
        for (int f = 0; f < 16; f++) {
            boolean[] keep = CoverageBudget.select(n, tier, score, was, cell, cap);
            int byCap = Math.min(n, cap), added = 0;
            boolean[] admitted = new boolean[n];
            if (n > cap && cap > 0) {
                boolean[] before = keep.clone();
                added = CoverageBudget.admit(n, keep, tier, score, wasAdmitted, rect, CoverageBudget.tileLights(cap), ROWS,
                    tiles, streak, CoverageBudget.SETTLE);
                if (byCap + added < LightBudget.TILED_FROM) {
                    keep = before;
                    added = 0;
                } else for (int i = 0; i < n; i++) admitted[i] = keep[i] && !before[i];
            }
            int worst = 0;
            for (int t : tiles) worst = Math.max(worst, t);
            was = keep;
            wasAdmitted = admitted;
            // Cookies among the kept lights, as LightFrame.queue.
            List<Integer> idx = new ArrayList<>();
            int count = 0;
            for (int i = 0; i < n; i++) if (keep[i]) {
                count++;
                if (scene.get(i).cookie) idx.add(i);
            }
            int c = idx.size();
            int[] ct = new int[c];
            float[] cs = new float[c];
            long[] cc = new long[c];
            int[][] cr = new int[c][];
            boolean[] cw = new boolean[c], cwa = new boolean[c];
            int[] cst = new int[c];
            for (int j = 0; j < c; j++) {
                int i = idx.get(j);
                ct[j] = tier[i];
                cs[j] = score[i] * (scene.get(i).none ? 2 : 1);
                cc[j] = cell[i];
                cr[j] = rect[i];
                cw[j] = cookieWas != null && cookieWas[i];
                cwa[j] = cookieWasAdmitted != null && cookieWasAdmitted[i];
                cst[j] = cookieStreak != null ? cookieStreak[i] : 0;
            }
            boolean[] ok = CoverageBudget.select(c, ct, cs, cw, cc, cookieCap);
            boolean[] okBefore = ok.clone();
            if (c > cookieCap && count >= LightBudget.TILED_FROM)
                CoverageBudget.admit(c, ok, ct, cs, cwa, cr, CoverageBudget.tileCookies(cookieCap), c, tiles, cst,
                    CoverageBudget.SETTLE);
            cookieWas = new boolean[n];
            cookieWasAdmitted = new boolean[n];
            cookieStreak = new int[n];
            int sampled = 0, critical = 0, criticalKept = 0, criticalCookies = 0, criticalSampled = 0, kept = 0;
            for (int j = 0; j < c; j++) {
                int i = idx.get(j);
                cookieWas[i] = ok[j];
                cookieWasAdmitted[i] = ok[j] && !okBefore[j];
                cookieStreak[i] = cst[j];
                if (ok[j]) sampled++;
            }
            int cookies = 0;
            for (int i = 0; i < n; i++) {
                if (scene.get(i).cookie) cookies++;
                if (keep[i]) kept++;
                if (tier[i] == 3) {
                    critical++;
                    if (keep[i]) criticalKept++;
                    if (scene.get(i).cookie) {
                        criticalCookies++;
                        if (cookieWas[i]) criticalSampled++;
                    }
                }
            }
            result = new R(n, kept, byCap, added, cookies, sampled, criticalKept, critical, criticalSampled, criticalCookies,
                worst);
        }
        return result;
    }

    private static void require(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
