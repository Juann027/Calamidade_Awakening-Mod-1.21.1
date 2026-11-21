package dev.calamidade_awakening;

import dev.calamidade_awakening.api.FrameBudget;
import dev.calamidade_awakening.api.Quality;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.client.geometry.Mesh;
import dev.calamidade_awakening.client.geometry.Color;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;

/** Executável sem framework de testes, incluído em gradle check. */
public final class FrameBudgetCheck {
    public static void main(String[] args) {
        var marks = List.of(
            new FrameBudget.Candidate<>("far", 100, 1, 1),
            new FrameBudget.Candidate<>("nearSecond", 12, 1, 3),
            new FrameBudget.Candidate<>("nearFirst", 12, 1, 2),
            new FrameBudget.Candidate<>("expired", 5, 0, 4),
            new FrameBudget.Candidate<>("outOfRange", 130, 1, 5)
        );
        require(FrameBudget.select(marks, 2).equals(List.of("nearFirst", "nearSecond")), "priority/tie");
        require(FrameBudget.select(marks, 0).isEmpty(), "zero budget");
        require(FrameBudget.select(marks, 10).equals(List.of("nearFirst", "nearSecond", "far")), "filter");
        require(FrameBudget.envelope(0) == 0 && FrameBudget.envelope(.4) == 1 && FrameBudget.envelope(1) == 0, "envelope");
        require(VisualKind.byId(0) == VisualKind.LATTICE && VisualKind.byId(1) == VisualKind.VEIL
            && VisualKind.byId(99) == null, "protocol");
        require(VisualKind.values().length == 5 && VisualKind.NEXUS.volume == VisualKind.Volume.NEBULA, "catalog");
        var extension = VisualKind.register(100, "test", "Test", 0xFFFFFFFF, 50, 3,
            VisualKind.Geometry.RINGS, VisualKind.Volume.NONE, false, false);
        require(VisualKind.byId(100) == extension, "registration");
        try {
            VisualKind.register(100, "duplicate", "Test", 0, 50, 3,
                VisualKind.Geometry.RINGS, VisualKind.Volume.NONE, false, false);
            throw new AssertionError("duplicate accepted");
        } catch (IllegalArgumentException expected) {}
        VisualKind.seal();
        try {
            VisualKind.register(101, "late", "Late", 0, 50, 3,
                VisualKind.Geometry.RINGS, VisualKind.Volume.NONE, false, false);
            throw new AssertionError("sealed registry accepted");
        } catch (IllegalStateException expected) {}
        for (Quality q : Quality.values()) {
            require(q.marks <= 4 && q.marks <= q.effects && q.heroPasses <= 3 && q.steps <= 22 && q.vertices > 0, "quality bounds");
            for (int count = 1; count <= q.effects; count++) require((q.vertices / count) * count <= q.vertices, "shared vertex cap");
        }
        require(Quality.LOW.next() == Quality.BALANCED && Quality.HIGH.next() == Quality.LOW, "quality cycle");
        require(VisualKind.localId("calamidade_awakening:nexus") == VisualKind.NEXUS.id, "wire ID");
        require(VisualKind.localId("other:nexus") == -1, "unrecognized namespace");
        var cue = new dev.calamidade_awakening.api.v1.Effects.Impact(10, 2, .5f,
            dev.calamidade_awakening.api.v1.Effects.ImpactStyle.TINTED_FLASH);
        require(cue.weight(9.99) == 0 && cue.weight(10) == .5f && cue.weight(11) == .25f
            && cue.weight(12) == 0, "impact timing");
        cooldowns();
        cues();
        rangeAndDuration();
        TimelineCheck.run();
        AtmosphereCheck.run();
        PriorityCheck.run();
        BeamCheck.run();
        LightCheck.run();
        OrientationCheck.run();
        dev.calamidade_awakening.net.StageEventCheck.run();
        SourceCheck.run();
        TriggerCheck.run();
        timelineRegistration();
        atmosphereRegistration();
        meshBudget();
        closedPrimitives();
        System.out.println("FrameBudgetCheck PASS");
    }

    private static void cooldowns() {
        var cooldowns = new dev.calamidade_awakening.api.Cooldowns();
        var player = new java.util.UUID(1, 2);
        var other = new java.util.UUID(3, 4);
        require(cooldowns.ready(player, 4, 0), "fresh cooldown");
        cooldowns.start(player, 4, 100, 210);
        require(!cooldowns.ready(player, 4, 100) && !cooldowns.ready(player, 4, 309), "running cooldown");
        require(cooldowns.ready(player, 4, 310), "expired cooldown");
        require(cooldowns.ready(player, 2, 100), "other skill unaffected");
        require(cooldowns.ready(other, 4, 100), "other player unaffected");
        require(cooldowns.remaining(player, 4, 300) == 10 && cooldowns.remaining(player, 2, 300) == 0, "remaining");
        cooldowns.start(player, 2, 100, 48);
        cooldowns.prune(148);
        require(cooldowns.size() == 1 && cooldowns.ready(player, 2, 148), "prune");
        cooldowns.start(player, 1, 0, 0);
        require(cooldowns.size() == 1, "zero cooldown ignored");
    }

    private static void cues() {
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("checkaddon", "wave");
        dev.calamidade_awakening.api.v1.Effects.register(id, new dev.calamidade_awakening.api.v1.Effects.Definition(60, 3, 0xffffffff,
            dev.calamidade_awakening.api.v1.Effects.Shape.RINGS, dev.calamidade_awakening.api.v1.Effects.Material.NONE, null, null,
            new dev.calamidade_awakening.api.v1.Effects.Impact(20, 2, .5f, dev.calamidade_awakening.api.v1.Effects.ImpactStyle.MONOCHROME)));
        dev.calamidade_awakening.api.v1.Effects.cue(id, 0, context -> {});
        dev.calamidade_awakening.api.v1.Effects.onImpact(id, context -> {});
        var kind = VisualKind.byId(VisualKind.localId(id.toString()));
        require(kind.cues().size() == 2 && kind.cues().get(1).tick() == 20, "cue schedule");
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.cue(id, 60, context -> {}), "cue past duration");
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.cue(id, -1, context -> {}), "negative cue");
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.cue(
            net.minecraft.resources.ResourceLocation.parse("calamidade_awakening:nexus"), 5, context -> {}), "internal cue");
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.cue(
            net.minecraft.resources.ResourceLocation.parse("checkaddon:missing"), 5, context -> {}), "unknown cue");
        var plain = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("checkaddon", "plain");
        dev.calamidade_awakening.api.v1.Effects.register(plain, new dev.calamidade_awakening.api.v1.Effects.Definition(40, 3, 0xffffffff,
            dev.calamidade_awakening.api.v1.Effects.Shape.RINGS, dev.calamidade_awakening.api.v1.Effects.Material.NONE, null, null));
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.onImpact(plain, context -> {}), "impact without Impact");
        require(VisualKind.networkKeys().contains("checkaddon:wave") && VisualKind.networkKeys().contains("calamidade_awakening:nexus"),
            "network keys");
    }

    private static void rangeAndDuration() {
        // Per-candidate distribution range (0.6.2).
        var far = FrameBudget.select(List.of(new FrameBudget.Candidate<>("sky", 600, 1, 1, 800),
            new FrameBudget.Candidate<>("default", 600, 1, 2)), 4);
        require(far.equals(List.of("sky")), "candidate range");
        // Envelope: identical up to 1200 ticks, capped ramps beyond.
        require(Math.abs(FrameBudget.envelope(80, 200) - FrameBudget.envelope(.4)) < 1e-9, "short envelope unchanged");
        require(FrameBudget.envelope(0, 12000) == 0 && FrameBudget.envelope(168, 12000) == 1
            && FrameBudget.envelope(6000, 12000) == 1 && FrameBudget.envelope(12000, 12000) == 0, "long envelope");
        require(Math.abs(FrameBudget.envelope(1200 * .5, 1200) - FrameBudget.envelope(600, 1201)) < 1e-9, "envelope continuity");
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("checkaddon", "aurora_sky");
        var definition = new dev.calamidade_awakening.api.v1.Effects.Definition(12000, 400, 0xffffffff,
            dev.calamidade_awakening.api.v1.Effects.Shape.RINGS, dev.calamidade_awakening.api.v1.Effects.Material.NONE, null, null)
            .withRange(900);
        dev.calamidade_awakening.api.v1.Effects.register(id, definition);
        require(VisualKind.byId(VisualKind.localId(id.toString())).range == 900, "definition range");
        require(VisualKind.NEXUS.range == 128, "built-in range");
        expectRejected(() -> definition.withRange(2000), "range too large");
        expectRejected(() -> new dev.calamidade_awakening.api.v1.Effects.Definition(12001, 4, 0xffffffff,
            dev.calamidade_awakening.api.v1.Effects.Shape.RINGS, dev.calamidade_awakening.api.v1.Effects.Material.NONE, null, null),
            "duration too long");
    }

    private static void atmosphereRegistration() {
        var E = (java.util.function.Function<String, net.minecraft.resources.ResourceLocation>)
            path -> net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("checkaddon", path);
        var definition = new dev.calamidade_awakening.api.v1.Effects.Definition(100, 2, 0xffffffff,
            dev.calamidade_awakening.api.v1.Effects.Shape.NONE, dev.calamidade_awakening.api.v1.Effects.Material.NONE, null, null);
        var constant = dev.calamidade_awakening.api.v1.Atmosphere.builder().sky(0xff000000, 0xffff8000, .5f).build();
        var animated = dev.calamidade_awakening.api.v1.Atmosphere.builder()
            .track(dev.calamidade_awakening.api.v1.Atmosphere.Param.FOG_DENSITY, "nevoa").weightPhase("ceu").build();
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.atmosphere(E.apply("nowhere"), constant), "unknown atmosphere");
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.atmosphere(
            net.minecraft.resources.ResourceLocation.parse("calamidade_awakening:nexus"), constant), "internal atmosphere");
        // Constant atmosphere: no timeline needed.
        dev.calamidade_awakening.api.v1.Effects.register(E.apply("dusk"), definition);
        dev.calamidade_awakening.api.v1.Effects.atmosphere(E.apply("dusk"), constant);
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.atmosphere(E.apply("dusk"), constant), "duplicate atmosphere");
        var dusk = VisualKind.byId(VisualKind.localId("checkaddon:dusk"));
        require(dusk.atmosphere() == constant && dusk.timeline() == null, "constant atmosphere registered");
        // Animated atmosphere: tracks and phase must exist in a timeline registered first.
        dev.calamidade_awakening.api.v1.Effects.register(E.apply("storm"), definition);
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.atmosphere(E.apply("storm"), animated), "atmosphere before timeline");
        dev.calamidade_awakening.api.v1.Effects.timeline(E.apply("storm"), dev.calamidade_awakening.api.v1.Timeline.builder(100)
            .phase("ceu", 10, 90, 10, 10).track("outra", dev.calamidade_awakening.api.v1.Timeline.keyframe(0, 1f)).build());
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.atmosphere(E.apply("storm"), animated), "atmosphere with unknown track");
        require(VisualKind.byId(VisualKind.localId("checkaddon:storm")).atmosphere() == null, "rejected atmosphere not kept");
        dev.calamidade_awakening.api.v1.Effects.register(E.apply("rain"), definition);
        dev.calamidade_awakening.api.v1.Effects.timeline(E.apply("rain"), dev.calamidade_awakening.api.v1.Timeline.builder(100)
            .phase("ceu", 10, 90, 10, 10).track("nevoa", dev.calamidade_awakening.api.v1.Timeline.keyframe(0, 1f)).build());
        dev.calamidade_awakening.api.v1.Effects.atmosphere(E.apply("rain"), animated);
        // Cues still work after an atmosphere; the atmosphere does not touch the cue order rule.
        dev.calamidade_awakening.api.v1.Effects.cue(E.apply("rain"), 5, context -> {});
        var rain = VisualKind.byId(VisualKind.localId("checkaddon:rain"));
        require(rain.atmosphere() == animated && rain.cues().size() == 1, "animated atmosphere registered");
        var state = rain.atmosphere().sample(rain.clock().at(50), 1);
        require(state.weight() == 1 && state.fogDensity() == 1, "registered atmosphere samples its timeline");
        // Effects without an atmosphere keep none.
        require(VisualKind.byId(VisualKind.localId("checkaddon:plain")).atmosphere() == null, "no atmosphere");
    }

    private static void timelineRegistration() {
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("checkaddon", "tide");
        var definition = new dev.calamidade_awakening.api.v1.Effects.Definition(120, 2, 0xffffffff,
            dev.calamidade_awakening.api.v1.Effects.Shape.RINGS, dev.calamidade_awakening.api.v1.Effects.Material.NONE, null, null);
        dev.calamidade_awakening.api.v1.Effects.register(id, definition);
        var timeline = dev.calamidade_awakening.api.v1.Timeline.builder(120).phase("impacto", 36, 52, 2, 8)
            .marker("impacto", 40).build();
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.cue(id, "impacto", context -> {}), "marker cue before timeline");
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.timeline(id,
            dev.calamidade_awakening.api.v1.Timeline.builder(121).build()), "timeline longer than definition");
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.timeline(
            net.minecraft.resources.ResourceLocation.parse("calamidade_awakening:nexus"), timeline), "internal timeline");
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.timeline(
            net.minecraft.resources.ResourceLocation.parse("checkaddon:missing"), timeline), "unknown timeline");
        dev.calamidade_awakening.api.v1.Effects.timeline(id, timeline);
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.timeline(id, timeline), "duplicate timeline");
        dev.calamidade_awakening.api.v1.Effects.cue(id, "impacto", context -> {});
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.cue(id, "eco", context -> {}), "unknown marker cue");
        var kind = VisualKind.byId(VisualKind.localId(id.toString()));
        require(kind.timeline() == timeline && kind.clock() == timeline && kind.cues().getFirst().tick() == 40
            && !kind.sustained() && kind.localAge(70, 10) == 70, "timeline registration");
        // Timelines go before cues.
        var late = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("checkaddon", "late");
        dev.calamidade_awakening.api.v1.Effects.register(late, definition);
        dev.calamidade_awakening.api.v1.Effects.cue(late, 3, context -> {});
        expectRejected(() -> dev.calamidade_awakening.api.v1.Effects.timeline(late, timeline), "timeline after cues");
        // Effects without a timeline: empty clock with their duration, real age untouched.
        var plain = VisualKind.byId(VisualKind.localId("checkaddon:plain"));
        require(plain.timeline() == null && plain.clock().duration() == 40 && plain.clock() == plain.clock()
            && plain.localAge(55.5, 3) == 55.5 && !plain.sustained(), "no timeline");
        // Sustained effect: the kind converts ages.
        var lamp = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("checkaddon", "lamp");
        dev.calamidade_awakening.api.v1.Effects.register(lamp, definition);
        dev.calamidade_awakening.api.v1.Effects.timeline(lamp,
            dev.calamidade_awakening.api.v1.Timeline.builder(100).sustain(20, 60).build());
        var lampKind = VisualKind.byId(VisualKind.localId(lamp.toString()));
        require(lampKind.sustained() && lampKind.localAge(70, -1) == 30 && lampKind.localAge(70, 65) == 65, "sustained kind");
        // 0.6 Context constructor still works and reports no timeline.
        var context = new dev.calamidade_awakening.api.v1.Effects.Context(null, Vec3.ZERO, null, null, 7, 12, () -> true);
        require(context.timeline() == null && context.localAge() == 12 && context.age() == 12 && !context.handle().release(),
            "legacy context");
    }

    private static void expectRejected(Runnable action, String test) {
        try {
            action.run();
            throw new AssertionError(test + " accepted");
        } catch (IllegalArgumentException expected) {}
    }

    private static void meshBudget() {
        CountingConsumer consumer = new CountingConsumer();
        Vec3 origin = new Vec3(0, 0, 0), x = new Vec3(1, 0, 0), z = new Vec3(0, 0, 1);
        Color color = new Color(1, 1, 1, 1);
        Mesh mesh = new Mesh(consumer, new Matrix4f(), origin, 127);
        mesh.ring(origin, x, z, 4, .3, 80, color);
        mesh.beam(origin, new Vec3(0, 3, 0), .4, 32, color);
        mesh.sphere(origin, 1, 16, 32, color, color);
        require(consumer.count + mesh.remaining() == 127 && consumer.count > 0 && consumer.count % 3 == 0,
            "mesh vertex budget");
    }

    private static void closedPrimitives() {
        Vec3 origin = new Vec3(0, 0, 0), x = new Vec3(1, 0, 0), z = new Vec3(0, 0, 1);
        Color white = new Color(1, 1, 1, 1);
        for (int budget = 0; budget <= 240; budget++) {
            CountingConsumer ring = new CountingConsumer();
            new Mesh(ring, new Matrix4f(), origin, budget).ring(origin, x, z, 4, .2, 80, white);
            require(ring.count <= budget, "ring cap");
            if (budget < 36) require(ring.count == 0, "ring too small");
            else require(ring.count >= 36 && ring.count % 12 == 0
                && ring.same(0, ring.count - 11), "closed ring seam");

            CountingConsumer sphere = new CountingConsumer();
            new Mesh(sphere, new Matrix4f(), origin, budget).sphere(origin, 1, 16, 32, white, white);
            require(sphere.count <= budget, "sphere cap");
            if (budget < 36) require(sphere.count == 0, "sphere too small");
            else {
                require(sphere.top && sphere.bottom, "both poles");
                // All longitudes meet at the north pole. Use the lower rim to find its seam.
                int row = 6;
                while (row < sphere.count && !sphere.same(5, row - 4)) row += 6;
                require(row >= 18 && sphere.count % row == 0, "sphere row size");
                for (int start = 0; start < sphere.count; start += row)
                    require(sphere.same(start + 5, start + row - 4), "closed sphere seam");
            }
        }
    }

    private static final class CountingConsumer implements VertexConsumer {
        int count;
        boolean top, bottom;
        final java.util.ArrayList<float[]> points = new java.util.ArrayList<>();
        boolean same(int a, int b) {
            for (int axis = 0; axis < 3; axis++)
                if (Math.abs(points.get(a)[axis] - points.get(b)[axis]) > .00001) return false;
            return true;
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            require(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z), "finite vertex");
            points.add(new float[]{x, y, z});
            top |= y > .9999;
            bottom |= y < -.9999;
            count++;
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }

    private static void require(boolean condition, String test) {
        if (!condition) throw new AssertionError(test);
    }
}
