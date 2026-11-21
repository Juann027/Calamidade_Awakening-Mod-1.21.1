package dev.calamidade_awakening;

import dev.calamidade_awakening.api.TerrainNoise;
import dev.calamidade_awakening.api.TerrainPlan;
import dev.calamidade_awakening.api.TerrainPlanner;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.TerrainPalette;
import dev.calamidade_awakening.api.v1.TerrainShape;
import dev.calamidade_awakening.api.v1.Timeline;
import net.minecraft.resources.ResourceLocation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/** Terrain API core (0.11.0): shapes, noise, palette, plan and registration. Pure; run by FrameBudgetCheck. */
final class TerrainCheck {
    private TerrainCheck() {}

    private static final int MIN_Y = -64, MAX_Y = 320;

    static void run() {
        noise();
        shapes();
        volumes();
        determinism();
        shell();
        trenchOrientation();
        batchOrder();
        palette();
        fillAndStructure();
        edits();
        registration();
        System.out.println("TerrainCheck PASS");
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static TerrainPlan plan(TerrainEdit edit, double ox, double oy, double oz, float yaw, float pitch, long seed) {
        return TerrainPlanner.plan(new TerrainPlanner.Request(edit, ox, oy, oz, yaw, pitch, seed, MIN_Y, MAX_Y, null));
    }

    private static TerrainEdit carve(TerrainShape shape) { return TerrainEdit.carve(shape).commitAt("impacto").build(); }

    private static long key(int x, int y, int z) { return ((long) x << 40) ^ ((long) (y & 0xFFFFF) << 20) ^ (z & 0xFFFFF); }

    /** Every entry of a plan: packed position → action. */
    private static Map<Long, Byte> entries(TerrainPlan plan) {
        Map<Long, Byte> all = new HashMap<>();
        for (var s : plan.sections())
            for (int i = 0; i < s.size(); i++)
                require(all.put(key(s.blockX(i), s.blockY(i), s.blockZ(i)), s.action()[i]) == null, "unique entry");
        return all;
    }

    private static int[] span(TerrainPlan plan, byte action) {
        int[] r = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (var s : plan.sections())
            for (int i = 0; i < s.size(); i++) {
                if (s.action()[i] != action) continue;
                r[0] = Math.min(r[0], s.blockX(i)); r[1] = Math.min(r[1], s.blockY(i)); r[2] = Math.min(r[2], s.blockZ(i));
                r[3] = Math.max(r[3], s.blockX(i)); r[4] = Math.max(r[4], s.blockY(i)); r[5] = Math.max(r[5], s.blockZ(i));
            }
        return r;
    }

    private static boolean near(double value, double expected, double tolerance) {
        return Math.abs(value - expected) <= expected * tolerance;
    }

    // ------------------------------------------------------------------------------------------------ noise

    private static void noise() {
        require(TerrainNoise.seed32(0x1234_5678_9abc_def0L) == (0x1234_5678 ^ 0x9abc_def0), "seed32");
        // Fixed values shared with the GLSL test (scripts/testar_glsl.cjs): a change here breaks the client heat.
        require(TerrainNoise.hash(0, 0, 0, 0) == HASH_000, "hash(0,0,0,0) = " + TerrainNoise.hash(0, 0, 0, 0));
        require(TerrainNoise.hash(-5, 70, 12, 123456) == HASH_SAMPLE, "hash sample = " + TerrainNoise.hash(-5, 70, 12, 123456));
        double min = 1, max = 0;
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 20000; i++) {
            double x = random.nextDouble() * 200 - 100, y = random.nextDouble() * 200 - 100, z = random.nextDouble() * 200 - 100;
            int seed = random.nextInt();
            double v = TerrainNoise.value(x, y, z, seed);
            min = Math.min(min, v); max = Math.max(max, v);
            require(v >= 0 && v <= 1, "value noise range");
            require(Math.abs(TerrainNoise.value(x + 1e-4, y, z, seed) - v) < 1e-3, "value noise continuity");
            double f = TerrainNoise.fbm(x, y, z, seed);
            require(f >= 0 && f <= 1, "fbm range");
            require(TerrainNoise.value(x, y, z, seed) == v, "value noise determinism");
        }
        require(min < .1 && max > .9, "value noise spread " + min + ".." + max);
        require(TerrainNoise.value(3, -4, 9, 99) == TerrainNoise.unit(3, -4, 9, 99), "lattice value");
        require(TerrainNoise.value(3.5, 1.5, 2.5, 1) != TerrainNoise.value(3.5, 1.5, 2.5, 2), "seed changes noise");
        int ones = 0;
        for (int i = 0; i < 4096; i++) ones += Integer.bitCount(TerrainNoise.hash(i, i * 7, -i, 42));
        require(near(ones, 4096 * 16, .02), "hash bit balance " + ones);
    }

    static final int HASH_000 = -1489410342;
    static final int HASH_SAMPLE = -1824810254;

    // ------------------------------------------------------------------------------------------------ shapes

    private static void shapes() {
        var sphere = TerrainShape.sphere(5);
        require(sphere.distance(0, 0, 0, 1) == -5 && Math.abs(sphere.distance(8, 0, 0, 1) - 3) < 1e-9, "sphere distance");
        require(sphere.extent() == 5, "sphere extent");
        var trench = TerrainShape.trench(20, 2);
        require(Math.abs(trench.distance(0, 0, 10, 0) + 2) < 1e-9 && Math.abs(trench.distance(0, 0, 14, 0) - 2) < 1e-9
            && Math.abs(trench.distance(5, 0, 3, 0) - 3) < 1e-9, "trench distance");
        var cyl = TerrainShape.cylinder(4, 6);
        require(cyl.distance(0, 0, 0, 0) == -3 && Math.abs(cyl.distance(0, 5, 0, 0) - 2) < 1e-9
            && Math.abs(cyl.distance(7, 0, 0, 0) - 3) < 1e-9 && Math.abs(cyl.distance(7, 7, 0, 0) - 5) < 1e-9, "cylinder distance");
        var crater = TerrainShape.crater(10, 4);
        require(crater.distance(0, -3.9, 0, 0) < 0 && crater.distance(0, -4.1, 0, 0) > 0, "crater floor at depth");
        require(crater.distance(0, 3.9, 0, 0) < 0 && crater.distance(0, 4.1, 0, 0) > 0, "crater clearance = depth");
        require(crater.distance(9, -1, 0, 0) > 0 && crater.distance(9, -.5, 0, 0) < 0, "crater bowl near the rim: floor at -0.76");
        require(crater.distance(11, 1, 0, 0) > 0, "crater outside the rim");
        require(TerrainShape.crater(10, 4).clearance(0).distance(0, .5, 0, 0) > 0, "no clearance");
        var flat = TerrainShape.crater(10, 4).profile(12);
        require(flat.distance(7, -3.5, 0, 0) < 0 && crater.distance(7, -3.5, 0, 0) > 0, "flat profile keeps the depth wider");
        var moved = TerrainShape.sphere(3).offset(2, 1, 5);
        require(moved.distance(-2, 1, 5, 0) == -3, "offset: right is local -x");
        require(Math.abs(moved.extent() - (Math.sqrt(4 + 1 + 25) + 3)) < 1e-6, "extent with offset");
        var both = TerrainShape.union(TerrainShape.sphere(3), TerrainShape.sphere(3).offset(0, 0, 10));
        require(both.distance(0, 0, 0, 0) < 0 && both.distance(0, 0, 10, 0) < 0 && both.distance(0, 0, 5, 0) > 0, "union");
        var pillar = TerrainShape.cylinder(8, 6).minus(TerrainShape.cylinder(1.5f, 10));
        require(pillar.distance(0, 0, 0, 0) > 0 && pillar.distance(4, 0, 0, 0) < 0, "minus spares the middle");
        var rough = TerrainShape.sphere(10).rough(2, 3);
        double lo = 0, hi = 0;
        for (int i = 0; i < 2000; i++) {
            double a = i * .37, b = i * .11;
            double x = 10 * Math.cos(a) * Math.cos(b), y = 10 * Math.sin(b), z = 10 * Math.sin(a) * Math.cos(b);
            double d = rough.distance(x, y, z, 77);
            require(Math.abs(d) <= 2 + 1e-9, "rough edge within the amplitude");
            lo = Math.min(lo, d); hi = Math.max(hi, d);
        }
        require(lo < -.8 && hi > .8, "rough edge actually moves " + lo + ".." + hi);
        require(rough.extent() == 12, "rough extent");
        require(rough.distance(3, 4, 5, 1) != rough.distance(3, 4, 5, 2) && rough.distance(3, 4, 5, 1) == rough.distance(3, 4, 5, 1),
            "rough edge from the seed");
        require(TerrainShape.sphere(4).equals(TerrainShape.sphere(4)) && !TerrainShape.sphere(4).equals(TerrainShape.sphere(5)), "equality");
        expectRejected(() -> TerrainShape.sphere(0), "zero radius");
        expectRejected(() -> TerrainShape.sphere(65), "radius too big");
        expectRejected(() -> TerrainShape.trench(129, 2), "trench too long");
        expectRejected(() -> TerrainShape.crater(5, 3).profile(.5f), "profile below 1");
        expectRejected(() -> TerrainShape.sphere(5).profile(2), "profile on a sphere");
        expectRejected(() -> TerrainShape.sphere(5).rough(9, 2), "roughness too high");
        expectRejected(() -> TerrainShape.sphere(5).rough(1, .1f), "noise scale too small");
        expectRejected(() -> TerrainShape.union(TerrainShape.sphere(5).rough(1, 2), TerrainShape.sphere(2)), "union of rough");
        expectRejected(() -> TerrainShape.sphere(5).offset(70, 0, 0), "offset too far");
        expectRejected(() -> TerrainShape.sphere(5).minus(TerrainShape.sphere(2).minus(TerrainShape.sphere(1))), "nested subtraction");
        TerrainShape many = TerrainShape.sphere(1);
        for (int i = 1; i < TerrainShape.MAX_PRIMITIVES; i++) many = many.union(TerrainShape.sphere(1).offset(i, 0, 0));
        TerrainShape full = many;
        expectRejected(() -> full.union(TerrainShape.sphere(1)), "too many primitives");
    }

    // ------------------------------------------------------------------------------------------------ volumes

    private static void volumes() {
        var sphere = plan(carve(TerrainShape.sphere(12)), 0, 64, 0, 0, 0, 1);
        require(near(sphere.count(TerrainPlan.CARVE), 4 / 3.0 * Math.PI * 1728, .015), "sphere r12 volume " + sphere.count(TerrainPlan.CARVE));
        require(sphere.total() == sphere.count(TerrainPlan.CARVE) + sphere.count(TerrainPlan.SEAL), "plain carve: carve + seal");
        var cylinder = plan(carve(TerrainShape.cylinder(12, 10)), 0, 64, 0, 0, 0, 1);
        require(near(cylinder.count(TerrainPlan.CARVE), Math.PI * 144 * 10, .02), "cylinder volume " + cylinder.count(TerrainPlan.CARVE));
        var trench = plan(carve(TerrainShape.trench(40, 6)), 0, 64, 0, 0, 0, 1);
        double trenchVolume = Math.PI * 36 * 40 + 4 / 3.0 * Math.PI * 216;
        require(near(trench.count(TerrainPlan.CARVE), trenchVolume, .025), "trench volume " + trench.count(TerrainPlan.CARVE));
        var bowl = plan(carve(TerrainShape.crater(10, 5).clearance(0)), 0, 64, 0, 0, 0, 1);
        require(near(bowl.count(TerrainPlan.CARVE), Math.PI * 100 * 5 * .5, .06), "crater volume " + bowl.count(TerrainPlan.CARVE));
        var cleared = plan(carve(TerrainShape.crater(10, 5).clearance(3)), 0, 64, 0, 0, 0, 1);
        require(near(cleared.count(TerrainPlan.CARVE) - bowl.count(TerrainPlan.CARVE), Math.PI * 100 * 3, .05), "crater clearance volume");
        var cone = plan(carve(TerrainShape.crater(10, 6).profile(1).clearance(0)), 0, 64, 0, 0, 0, 1);
        require(near(cone.count(TerrainPlan.CARVE), Math.PI * 100 * 6 / 3, .08), "cone volume " + cone.count(TerrainPlan.CARVE));
        // Radius 24, the large crater of the measurements.
        var large = plan(carve(TerrainShape.sphere(24)), 0, 64, 0, 0, 0, 1);
        require(near(large.count(TerrainPlan.CARVE), 4 / 3.0 * Math.PI * 13824, .01), "sphere r24 volume");
        // Build height clips the plan.
        var low = plan(carve(TerrainShape.sphere(12)), 0, -60, 0, 0, 0, 1);
        for (var s : low.sections()) for (int i = 0; i < s.size(); i++) require(s.blockY(i) >= MIN_Y, "plan below the world");
        require(low.count(TerrainPlan.CARVE) < sphere.count(TerrainPlan.CARVE), "clipped volume");
    }

    // ------------------------------------------------------------------------------------------------ determinism

    private static void determinism() {
        var edit = TerrainEdit.carve(TerrainShape.crater(9, 5).rough(1.5f, 3)).commitAt("impacto")
            .burn(TerrainPalette.burnt(), 2).build();
        var a = plan(edit, 10.3, 70, -4.7, 30, 10, 1234);
        var b = plan(edit, 10.3, 70, -4.7, 30, 10, 1234);
        require(a.sections().size() == b.sections().size() && a.total() == b.total(), "same seed same size");
        for (int i = 0; i < a.sections().size(); i++) {
            var sa = a.sections().get(i);
            var sb = b.sections().get(i);
            require(sa.key() == sb.key() && Arrays.equals(sa.index(), sb.index()) && Arrays.equals(sa.action(), sb.action())
                && Arrays.equals(sa.value(), sb.value()), "same seed same plan");
        }
        var c = plan(edit, 10.3, 70, -4.7, 30, 10, 1235);
        require(!entries(a).equals(entries(c)), "another seed changes the rough edge");
        var smooth = TerrainEdit.carve(TerrainShape.crater(9, 5)).commitAt("impacto").burn(TerrainPalette.burnt(), 2).build();
        require(entries(plan(smooth, 0, 64, 0, 0, 0, 1)).equals(entries(plan(smooth, 0, 64, 0, 0, 0, 999))),
            "without roughness the area does not depend on the seed");
    }

    // ------------------------------------------------------------------------------------------------ shell

    private static void shell() {
        for (int thickness = 1; thickness <= 3; thickness++) {
            var edit = TerrainEdit.carve(TerrainShape.crater(8, 5).union(TerrainShape.sphere(3).offset(0, -5, 0)).rough(1, 2.5f))
                .commitAt("impacto").burn(TerrainPalette.burnt(), thickness).build();
            var plan = plan(edit, 0.5, 64, 0.5, 20, 0, 42 + thickness);
            var all = entries(plan);
            Set<Long> carved = new HashSet<>();
            for (var e : all.entrySet()) if (e.getValue() == TerrainPlan.CARVE) carved.add(e.getKey());
            int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            int floors = 0, walls = 0, rims = 0;
            for (var s : plan.sections())
                for (int i = 0; i < s.size(); i++) {
                    int x = s.blockX(i), y = s.blockY(i), z = s.blockZ(i);
                    byte action = s.action()[i];
                    if (action == TerrainPlan.CARVE) {
                        // Sealed: every face neighbour of carved space is carved or in the shell.
                        for (int[] f : faces)
                            require(all.containsKey(key(x + f[0], y + f[1], z + f[2])), "carved block exposed to the untouched world");
                        continue;
                    }
                    require(action == TerrainPlan.FLOOR || action == TerrainPlan.WALL || action == TerrainPlan.RIM, "shell actions only");
                    if (action == TerrainPlan.FLOOR) {
                        floors++;
                        boolean above = false;
                        for (int k = 1; k <= thickness; k++) above |= carved.contains(key(x, y + k, z));
                        require(above, "floor block with carved space above it");
                    }
                    if (action == TerrainPlan.WALL) walls++;
                    if (action == TerrainPlan.RIM) {
                        rims++;
                        for (int[] f : faces) require(!carved.contains(key(x + f[0], y + f[1], z + f[2])), "rim blocks do not touch the crater");
                    }
                    int pick = s.value()[i];
                    var band = TerrainPalette.Band.values()[action - 1];
                    require(pick >= 0 && pick < TerrainPalette.burnt().entries(band).size(), "palette pick in range");
                }
            require(floors > 0 && walls > 0 && (thickness == 1 || rims > 0), "bands present t=" + thickness + ": " + floors + "/" + walls + "/" + rims);
        }
        // Without a shell, the blocks touching the crater are sealed (fluids) but nothing else is planned.
        var plain = plan(carve(TerrainShape.sphere(6)), 0, 64, 0, 0, 0, 1);
        require(plain.count(TerrainPlan.SEAL) > 0 && plain.count(TerrainPlan.FLOOR) == 0, "seal without a palette");
    }

    // ------------------------------------------------------------------------------------------------ trench

    private static void trenchOrientation() {
        var edit = carve(TerrainShape.trench(24, 2));
        int[] south = span(plan(edit, 0.5, 64, 0.5, 0, 0, 1), TerrainPlan.CARVE);
        int[] west = span(plan(edit, 0.5, 64, 0.5, 90, 0, 1), TerrainPlan.CARVE);
        require(south[5] - south[2] + 1 >= 26 && south[3] - south[0] + 1 <= 5, "yaw 0: trench along z");
        require(west[3] - west[0] + 1 >= 26 && west[5] - west[2] + 1 <= 5, "yaw 90: trench along x");
        // Diagonal (yaw -45 faces south-east): carved blocks lie near the x = z line.
        var diagonal = plan(edit, 0.5, 64, 0.5, -45, 0, 1);
        for (var s : diagonal.sections())
            for (int i = 0; i < s.size(); i++)
                if (s.action()[i] == TerrainPlan.CARVE)
                    require(Math.abs((s.blockX(i) - s.blockZ(i)) / Math.sqrt(2)) <= 3, "yaw -45 along the diagonal");
        // Pitch 30 looks down: the front end of the trench is lower than the back end.
        var tilted = plan(edit, 0.5, 64, 0.5, 0, 30, 1);
        double front = 0, back = 0;
        int nf = 0, nb = 0;
        for (var s : tilted.sections())
            for (int i = 0; i < s.size(); i++) {
                if (s.action()[i] != TerrainPlan.CARVE) continue;
                if (s.blockZ(i) > 6) { front += s.blockY(i); nf++; }
                if (s.blockZ(i) < -6) { back += s.blockY(i); nb++; }
            }
        require(nf > 0 && nb > 0 && front / nf < back / nb - 5, "pitch 30 tilts the trench down");
    }

    // ------------------------------------------------------------------------------------------------ batches

    private static void batchOrder() {
        var edit = TerrainEdit.carve(TerrainShape.sphere(20).rough(1, 4)).commitAt("impacto").burn(TerrainPalette.burnt(), 1).build();
        double ox = 37.2, oy = 70.6, oz = -11.9;
        var plan = plan(edit, ox, oy, oz, 15, 5, 5);
        double previous = -1;
        int sum = 0;
        int lastY = Integer.MIN_VALUE;
        for (var s : plan.sections()) {
            double dx = (s.x() << 4) + 8 - ox, dy = (s.y() << 4) + 8 - oy, dz = (s.z() << 4) + 8 - oz;
            double d = dx * dx + dy * dy + dz * dz;
            require(d >= previous, "sections nearest first");
            if (d == previous) require(s.y() >= lastY, "ties by section y");
            previous = d;
            lastY = s.y();
            for (int i = 1; i < s.size(); i++) require((s.index()[i] & 0xFFFF) > (s.index()[i - 1] & 0xFFFF), "entries ascending in a section");
            require(s.size() <= 4096 && s.size() > 0, "section size");
            sum += s.size();
        }
        require(sum == plan.total(), "total = sum of batches");
        int counted = 0;
        for (int a = 0; a < TerrainPlan.ACTIONS; a++) counted += plan.count((byte) a);
        require(counted == plan.total(), "counts add up");
        var again = plan(edit, ox, oy, oz, 15, 5, 5);
        for (int i = 0; i < plan.sections().size(); i++) require(plan.sections().get(i).key() == again.sections().get(i).key(), "stable batch order");
        // The first batch is the section holding the origin.
        var first = plan.sections().get(0);
        require(first.x() == ((int) Math.floor(ox) >> 4) && first.y() == ((int) Math.floor(oy) >> 4)
            && first.z() == ((int) Math.floor(oz) >> 4), "first batch at the origin");
        require(plan.minX() <= Math.floor(ox) - 20 && plan.maxX() >= Math.floor(ox) + 19, "bounds");
    }

    // ------------------------------------------------------------------------------------------------ palette

    private static void palette() {
        var p = TerrainPalette.builder().add(TerrainPalette.Band.WALL, "minecraft:stone", 3)
            .add(TerrainPalette.Band.WALL, "minecraft:tuff", 1).add(TerrainPalette.Band.FLOOR, "minecraft:magma_block", 1).build();
        int[] hits = new int[2];
        for (int i = 0; i < 100_000; i++) hits[p.pick(TerrainPalette.Band.WALL, TerrainNoise.hash(i, 0, 0, 9))]++;
        require(near(hits[0], 75_000, .02) && near(hits[1], 25_000, .05), "weights " + Arrays.toString(hits));
        require(p.entries(TerrainPalette.Band.RIM).equals(p.entries(TerrainPalette.Band.WALL)), "empty band uses WALL");
        require(p.entries(TerrainPalette.Band.FLOOR).size() == 1 && p.pick(TerrainPalette.Band.FLOOR, 12345) == 0, "single entry");
        require(p.pickAt(TerrainPalette.Band.WALL, 4, 5, 6, 77) == p.pickAt(TerrainPalette.Band.WALL, 4, 5, 6, 77), "pickAt determinism");
        int differ = 0;
        for (int i = 0; i < 200; i++)
            if (p.pickAt(TerrainPalette.Band.WALL, i, 5, 6, 77) != p.pickAt(TerrainPalette.Band.WALL, i, 5, 6, 78)) differ++;
        require(differ > 30, "pickAt depends on the seed");
        var burnt = TerrainPalette.burnt();
        require(burnt.entries(TerrainPalette.Band.FLOOR).stream().anyMatch(e -> e.block().equals("minecraft:magma_block"))
            && burnt.entries(TerrainPalette.Band.WALL).size() == 6 && burnt.entries(TerrainPalette.Band.RIM).size() == 5, "burnt palette");
        for (var band : TerrainPalette.Band.values())
            for (var e : burnt.entries(band)) require(e.block().startsWith("minecraft:"), "vanilla blocks only");
        expectRejected(() -> TerrainPalette.builder().add(TerrainPalette.Band.FLOOR, "minecraft:stone", 1).build(), "no WALL");
        expectRejected(() -> TerrainPalette.builder().add(TerrainPalette.Band.WALL, "Stone", 1), "bad block id");
        expectRejected(() -> TerrainPalette.builder().add(TerrainPalette.Band.WALL, "minecraft:stone", 0), "zero weight");
    }

    // ------------------------------------------------------------------------------------------------ fill, structure

    private static void fillAndStructure() {
        var solid = TerrainEdit.carve(TerrainShape.sphere(9)).commitAt("impacto")
            .fill(TerrainEdit.Fill.solid("minecraft:raw_gold_block", 6)).build();
        var plan = plan(solid, 0, 64, 0, 0, 0, 1);
        require(near(plan.count(TerrainPlan.FILL), 4 / 3.0 * Math.PI * 216, .02), "solid fill volume " + plan.count(TerrainPlan.FILL));
        require(plan.count(TerrainPlan.CARVE) + plan.count(TerrainPlan.FILL) <= 4 / 3.0 * Math.PI * 729 * 1.02, "fill replaces carve");
        var hollow = TerrainEdit.carve(TerrainShape.sphere(6)).commitAt("impacto")
            .fill(TerrainEdit.Fill.shell("minecraft:end_stone", 4).at(0, -1, 0)).build();
        int shellCount = plan(hollow, 0, 64, 0, 0, 0, 1).count(TerrainPlan.FILL);
        require(shellCount > 60 && shellCount < 4 / 3.0 * Math.PI * 64 * .7, "hollow fill " + shellCount);
        require(!TerrainEdit.Fill.shell("minecraft:stone", 4).contains(0, 0, 0) && TerrainEdit.Fill.shell("minecraft:stone", 4).contains(3.5, 0, 0),
            "hollow fill contains only its shell");
        // Rotation helpers: a bijection inside the rotated box for every rotation.
        for (int r = 0; r < 4; r++) {
            int sx = 3, sz = 5;
            int rx = (r & 1) == 1 ? sz : sx, rz = (r & 1) == 1 ? sx : sz;
            Set<Integer> seen = new HashSet<>();
            for (int x = 0; x < sx; x++)
                for (int z = 0; z < sz; z++) {
                    int nx = TerrainPlanner.rotateX(x, z, sx, sz, r), nz = TerrainPlanner.rotateZ(x, z, sx, sz, r);
                    require(nx >= 0 && nx < rx && nz >= 0 && nz < rz, "rotated inside the box");
                    require(seen.add(nx * 100 + nz), "rotation is a bijection");
                }
        }
        require(TerrainPlanner.rotation(0) == 0 && TerrainPlanner.rotation(90) == 1 && TerrainPlanner.rotation(-90) == 3
            && TerrainPlanner.rotation(180) == 2 && TerrainPlanner.rotation(44) == 0 && TerrainPlanner.rotation(46) == 1, "yaw to rotation");
        // Clockwise 90: the +x end of the template goes to +z (like Rotation.CLOCKWISE_90).
        require(TerrainPlanner.rotateX(2, 0, 3, 1, 1) == 0 && TerrainPlanner.rotateZ(2, 0, 3, 1, 1) == 2, "clockwise 90");
        // A 3 × 2 × 1 template: palette 0..5 by position.
        int[] xs = {0, 1, 2, 0, 1, 2}, ys = {0, 0, 0, 1, 1, 1}, zs = new int[6], pal = {0, 1, 2, 3, 4, 5};
        var blocks = new TerrainPlanner.Blocks(3, 2, 1, xs, ys, zs, pal);
        var tower = TerrainEdit.carve(TerrainShape.sphere(2)).commitAt("impacto").structure("checkaddon:torre")
            .structureOffset(0, 1, 0).build();
        var placed = TerrainPlanner.plan(new TerrainPlanner.Request(tower, 10.5, 64, 20.5, 0, 0, 1, MIN_Y, MAX_Y, blocks));
        require(placed.count(TerrainPlan.STRUCTURE) == 6 && placed.structureY() == 65, "structure placed above the offset");
        require(placed.structureX() == 9 && placed.structureZ() == 20, "structure centered on the origin");
        Map<Long, Integer> values = new HashMap<>();
        for (var s : placed.sections())
            for (int i = 0; i < s.size(); i++)
                if (s.action()[i] == TerrainPlan.STRUCTURE) values.put(key(s.blockX(i), s.blockY(i), s.blockZ(i)), (int) s.value()[i]);
        require(values.get(key(9, 65, 20)) == 0 && values.get(key(11, 66, 20)) == 5, "structure palette by position");
        var turned = TerrainPlanner.plan(new TerrainPlanner.Request(tower, 10.5, 64, 20.5, 90, 0, 1, MIN_Y, MAX_Y, blocks));
        require(turned.rotation() == 1, "rotated structure");
        int[] s1 = span(turned, TerrainPlan.STRUCTURE);
        require(s1[3] - s1[0] == 0 && s1[5] - s1[2] == 2, "rotated structure spans z");
    }

    // ------------------------------------------------------------------------------------------------ edits

    private static void edits() {
        var timeline = Timeline.builder(100).marker("impacto", 40).track("calor", keyframe(40, 1f), keyframe(90, 0f))
            .track("queda", keyframe(0, 0f), keyframe(40, 1f)).build();
        var edit = TerrainEdit.carve(TerrainShape.sphere(4)).commitAt("impacto").cooling(100).build();
        edit.validate(timeline, 100);
        require(edit.heatAt(timeline, 39, -1) == 0 && edit.heatAt(timeline, 40, 0) == 1, "heat starts at the commit");
        require(edit.heatAt(timeline, 90, 50) > 0 && edit.heatAt(timeline, 90, 50) < edit.heatAt(timeline, 60, 20), "heat cools");
        require(edit.heatAt(timeline, 140, 100) == 0, "heat gone after the cooling");
        var tracked = TerrainEdit.carve(TerrainShape.sphere(4)).commitAt("impacto").heatTrack("calor").build();
        tracked.validate(timeline, 100);
        require(Math.abs(tracked.heatAt(timeline, 65, 25) - .5f) < 1e-6, "heat from a track");
        require(TerrainEdit.carve(TerrainShape.sphere(4)).commitAt("impacto").noHeat().build().heatAt(timeline, 41, 1) == 0, "no heat");
        var stand = TerrainEdit.StandIn.sphere(2).from(0, 60, -20).fall(10).reveal(20).build();
        require(stand.progress(timeline, 10, 40) == 0 && stand.progress(timeline, 40, 40) == 1
            && Math.abs(stand.progress(timeline, 25, 40) - .25f) < 1e-6, "ease-in fall");
        require(stand.opacity(5, 40) == 0 && stand.opacity(30, 40) == 1 && stand.opacity(60, 40) == 0
            && stand.opacity(50, 40) > 0 && stand.opacity(50, 40) < 1, "stand-in reveal");
        require(stand.z() == -20 && stand.x() == 0 && stand.y() == 60, "stand-in start offset");
        var trackFall = TerrainEdit.StandIn.sphere(2).track("queda").build();
        require(Math.abs(trackFall.progress(timeline, 20, 40) - .5f) < 1e-6, "fall from a track");
        expectRejected(() -> TerrainEdit.carve(TerrainShape.sphere(4)).build(), "no commit marker");
        expectRejected(() -> TerrainEdit.carve(TerrainShape.sphere(4)).commitAt("Impacto"), "bad marker name");
        expectRejected(() -> TerrainEdit.carve(TerrainShape.sphere(4)).commitAt("x").burn(TerrainPalette.burnt(), 5), "thick shell");
        expectRejected(() -> TerrainEdit.carve(TerrainShape.sphere(4)).commitAt("x").standIn(TerrainEdit.StandIn.structure().build()).build(),
            "structure stand-in without a structure");
        expectRejected(() -> TerrainEdit.carve(TerrainShape.sphere(4)).commitAt("outro").build().validate(timeline, 100), "unknown marker");
        expectRejected(() -> TerrainEdit.carve(TerrainShape.sphere(4)).commitAt("impacto").standIn(TerrainEdit.StandIn.sphere(1).fall(40).build())
            .build().validate(timeline, 100), "fall after the commit");
        expectRejected(() -> TerrainEdit.carve(TerrainShape.sphere(4)).commitAt("impacto").heatTrack("nada").build().validate(timeline, 100),
            "unknown heat track");
        expectRejected(() -> edit.validate(null, 100), "edit without timeline");
        expectRejected(() -> TerrainEdit.Fill.solid("minecraft:stone", 17), "fill too big");
        expectRejected(() -> TerrainEdit.carve(TerrainShape.sphere(4)).blocksPerTick(-1), "negative budget");
        expectRejected(() -> TerrainEdit.carve(TerrainShape.sphere(4)).structure("torre"), "structure id without namespace");
    }

    // ------------------------------------------------------------------------------------------------ registration

    private static void registration() {
        var E = (java.util.function.Function<String, ResourceLocation>) path -> ResourceLocation.fromNamespaceAndPath("checkaddon", path);
        var definition = new Effects.Definition(120, 8, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null);
        var edit = TerrainEdit.carve(TerrainShape.sphere(4)).commitAt("impacto").build();
        expectRejected(() -> Effects.terrain(E.apply("nowhere_terrain"), edit), "unknown effect");
        expectRejected(() -> Effects.terrain(ResourceLocation.parse("calamidade_awakening:nexus"), edit), "internal effect");
        Effects.register(E.apply("crater"), definition);
        expectRejected(() -> Effects.terrain(E.apply("crater"), edit), "terrain before the timeline");
        Effects.timeline(E.apply("crater"), Timeline.builder(120).marker("impacto", 40).build());
        expectRejected(() -> Effects.terrain(E.apply("crater")), "no edits");
        expectRejected(() -> Effects.terrain(E.apply("crater"), edit, edit, edit, edit, edit), "5 edits");
        Effects.terrain(E.apply("crater"), edit);
        expectRejected(() -> Effects.terrain(E.apply("crater"), edit), "duplicate terrain");
        Effects.cue(E.apply("crater"), "impacto", ctx -> {});
        var kind = VisualKind.byId(VisualKind.localId("checkaddon:crater"));
        require(kind.terrain().equals(List.of(edit)), "edit registered");
        require(VisualKind.byId(VisualKind.localId("checkaddon:campfire")).terrain().isEmpty(), "effects without terrain");
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
