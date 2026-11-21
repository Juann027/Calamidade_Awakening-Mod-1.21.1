package dev.calamidade_awakening;

import dev.calamidade_awakening.api.FrameBudget;
import dev.calamidade_awakening.api.PassBudget;
import dev.calamidade_awakening.api.Quality;
import dev.calamidade_awakening.api.v1.Effects;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** HERO priority (0.9.0): selection, cap, separate pass budget. Pure; run by FrameBudgetCheck inside checkBudget. */
final class PriorityCheck {
    private PriorityCheck() {}

    static void run() {
        quality();
        heroNeverCut();
        heroCap();
        heroFilters();
        normalUnchanged();
        passBudget();
        definition();
    }

    private static void quality() {
        require(Quality.LOW.heroPasses == 1 && Quality.BALANCED.heroPasses == 2 && Quality.HIGH.heroPasses == 3, "hero passes 1/2/3");
        require(Quality.LOW.marks == 1 && Quality.BALANCED.marks == 2 && Quality.HIGH.marks == 4, "common passes 1/2/4");
        for (Quality q : Quality.values()) require(q.effects >= FrameBudget.MAX_HEROES, "the effect limit never cuts heroes");
        require(FrameBudget.MAX_HEROES == 2, "hero cap");
    }

    private static FrameBudget.Candidate<String> normal(String name, double distance, double intensity, long order) {
        return new FrameBudget.Candidate<>(name, distance, intensity, order, 128, false);
    }

    private static FrameBudget.Candidate<String> hero(String name, double distance, double intensity, long order) {
        return new FrameBudget.Candidate<>(name, distance, intensity, order, 128, true);
    }

    private static void heroNeverCut() {
        for (Quality q : Quality.values()) {
            var list = new ArrayList<FrameBudget.Candidate<String>>();
            // Many strong, near NORMAL instances that would win any weight contest.
            for (int i = 0; i < 40; i++) list.add(normal("n" + i, 1 + i * .01, 1, i));
            // Two weak, far HERO instances.
            list.add(hero("h1", 100, .05, 100));
            list.add(hero("h2", 90, .02, 101));
            var chosen = FrameBudget.select(list, q.effects);
            require(chosen.size() == q.effects, "full frame " + q);
            require(chosen.get(0).equals("h2") && chosen.get(1).equals("h1"), "heroes first, nearest first " + q);
            require(chosen.subList(2, chosen.size()).equals(
                java.util.stream.IntStream.range(0, q.effects - 2).mapToObj(i -> "n" + i).toList()), "normals fill the rest " + q);
        }
        // A single HERO costs one slot; the normals keep the remaining ones.
        var one = FrameBudget.select(List.of(normal("a", 5, 1, 1), normal("b", 6, 1, 2), normal("c", 7, 1, 3),
            hero("h", 50, .5, 4)), 3);
        require(one.equals(List.of("h", "a", "b")), "one hero, two normals at LOW");
    }

    private static void heroCap() {
        var list = new ArrayList<FrameBudget.Candidate<String>>();
        for (int i = 0; i < 10; i++) list.add(hero("h" + i, 50 - i * 3, 1, i)); // h9 is the nearest
        list.add(normal("n", 4, 1, 99));
        var chosen = FrameBudget.select(list, Quality.HIGH.effects);
        require(chosen.equals(List.of("h9", "h8", "n")), "at most 2 heroes, the nearest; the rest are not drawn");
        // Same distance: order breaks the tie (the older instance first).
        var tie = FrameBudget.select(List.of(hero("late", 10, 1, 7), hero("early", 10, 1, 3), hero("third", 10, 1, 1)), 6);
        require(tie.equals(List.of("third", "early")), "hero tie by order");
        // A HERO server: 60 heroes still draw only 2, so the frame cost is bounded.
        var full = new ArrayList<FrameBudget.Candidate<String>>();
        for (int i = 0; i < 60; i++) full.add(hero("h" + i, 10 + i, 1, i));
        require(FrameBudget.select(full, Quality.BALANCED.effects).size() == 2, "hero server capped at 2");
    }

    private static void heroFilters() {
        var chosen = FrameBudget.select(List.of(
            new FrameBudget.Candidate<>("far", 300, 1, 1, 128, true),      // beyond its range
            new FrameBudget.Candidate<>("faded", 5, 0, 2, 128, true),      // envelope at 0
            new FrameBudget.Candidate<>("nan", Double.NaN, 1, 3, 128, true),
            new FrameBudget.Candidate<>("ok", 60, .5, 4, 128, true),
            normal("n", 5, 1, 5)), 3);
        require(chosen.equals(List.of("ok", "n")), "heroes obey range and intensity filters");
        require(FrameBudget.select(List.of(hero("h", 1, 1, 1)), 0).isEmpty(), "zero budget draws nothing");
    }

    private static void normalUnchanged() {
        var random = new Random(9);
        for (int round = 0; round < 500; round++) {
            var list = new ArrayList<FrameBudget.Candidate<Integer>>();
            int n = random.nextInt(30);
            for (int i = 0; i < n; i++) {
                double distance = random.nextInt(4) == 0 ? 200 + random.nextDouble() * 100 : random.nextDouble() * 120;
                double intensity = random.nextInt(6) == 0 ? 0 : random.nextDouble();
                list.add(new FrameBudget.Candidate<>(i, distance, intensity, random.nextInt(20), 128 + random.nextInt(2) * 64));
            }
            int max = 1 + random.nextInt(12);
            require(FrameBudget.select(list, max).equals(legacy(list, max)), "NORMAL selection unchanged without heroes");
        }
    }

    /** The 0.8.0 algorithm, copied here as the reference. */
    private static <T> List<T> legacy(List<FrameBudget.Candidate<T>> candidates, int maxMarks) {
        if (maxMarks <= 0) return List.of();
        return candidates.stream()
            .filter(c -> Double.isFinite(c.distance()) && c.distance() >= 0 && c.distance() <= c.range()
                && Double.isFinite(c.intensity()) && c.intensity() > .001)
            .sorted(java.util.Comparator.<FrameBudget.Candidate<T>>comparingDouble(FrameBudget.Candidate::weight).reversed()
                .thenComparingLong(FrameBudget.Candidate::order))
            .limit(maxMarks).map(FrameBudget.Candidate::value).toList();
    }

    private static void passBudget() {
        for (Quality q : Quality.values()) {
            var budget = new PassBudget(q);
            int hero = 0, common = 0;
            while (budget.take(true)) hero++;
            require(hero == q.heroPasses && budget.left(true) == 0, "hero pool size " + q);
            // Spending the HERO pool leaves the common pool untouched.
            require(budget.left(false) == q.marks && budget.used(false) == 0, "separate pools " + q);
            while (budget.take(false)) common++;
            require(common == q.marks && !budget.take(false) && !budget.take(true), "common pool size " + q);
        }
        var budget = new PassBudget(2, 2);
        budget.charge(false, 1);
        require(budget.left(false) == 1 && budget.left(true) == 2, "charge one pool");
        budget.charge(false, 5);
        require(budget.left(false) == 0 && budget.used(false) == 2, "charge clamps to the pool");
        budget.charge(true, -3);
        require(budget.used(true) == 0, "negative charge ignored");
        // NORMAL effects without heroes: the same 1/2/4 as before 0.9.0.
        var normalOnly = new PassBudget(Quality.HIGH);
        int taken = 0;
        while (normalOnly.take(false)) taken++;
        require(taken == 4 && normalOnly.used(true) == 0, "normal budget unchanged without heroes");
    }

    private static void definition() {
        var plain = new Effects.Definition(40, 3, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null);
        require(plain.priority() == Effects.Priority.NORMAL, "default priority");
        var legacy = new Effects.Definition(40, 3, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null, null, 200);
        require(legacy.priority() == Effects.Priority.NORMAL && legacy.range() == 200, "0.8 constructor");
        var hero = plain.withPriority(Effects.Priority.HERO);
        require(hero.priority() == Effects.Priority.HERO && plain.priority() == Effects.Priority.NORMAL, "withPriority copy");
        require(hero.withRange(300).priority() == Effects.Priority.HERO && hero.withRange(300).range() == 300, "withRange keeps priority");
        require(hero.withPriority(Effects.Priority.NORMAL).equals(plain), "back to normal");
        try {
            plain.withPriority(null);
            throw new AssertionError("null priority accepted");
        } catch (NullPointerException expected) {}
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("checkaddon", "protagonist");
        Effects.register(id, hero);
        var kind = dev.calamidade_awakening.api.VisualKind.byId(dev.calamidade_awakening.api.VisualKind.localId(id.toString()));
        require(kind.hero(), "registered hero");
        require(!dev.calamidade_awakening.api.VisualKind.NEXUS.hero(), "built-in effects are normal");
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
