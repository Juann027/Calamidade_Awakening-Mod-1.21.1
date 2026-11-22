package dev.calamidade_awakening;

import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.Quality;
import dev.calamidade_awakening.api.QualityProfile;
import dev.calamidade_awakening.api.SkyMath;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Sky;
import dev.calamidade_awakening.api.v1.Timeline;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import static dev.calamidade_awakening.api.v1.Timeline.keyframe;

/** Sky API (0.12.0): direction and angular size, tracks, registration, selection, vanilla sun and moon, darkening. */
final class SkyCheck {
    private static final double EPS = 1e-9;
    private static final Timeline.Sampler NONE = Timeline.empty(10).at(0);

    private SkyCheck() {}

    static void run() {
        directions();
        sizesAndDefaults();
        tracks();
        validation();
        registration();
        eclipse();
        selection();
        vanillaFrame();
        localFrame();
        darkening();
        limits();
        System.out.println("SkyCheck PASS");
    }

    private static void directions() {
        // Minecraft convention: azimuth like an entity yaw (0 south, 90 west, 180 north, -90 east).
        near(Sky.Direction.of(0, 0), 0, 0, 1, "azimuth 0 is south");
        near(Sky.Direction.of(90, 0), -1, 0, 0, "azimuth 90 is west");
        near(Sky.Direction.of(-90, 0), 1, 0, 0, "azimuth -90 is east");
        near(Sky.Direction.of(180, 0), 0, 0, -1, "azimuth 180 is north");
        near(Sky.Direction.of(33, 90), 0, 1, 0, "elevation 90 is the zenith");
        near(Sky.Direction.of(0, -90), 0, -1, 0, "elevation -90 is the nadir");
        // Same as Vec3.directionFromRotation(pitch = -elevation, yaw = azimuth), within Mth's sine table (~1e-4).
        for (double yaw = -170; yaw < 180; yaw += 37) for (double el = -80; el <= 80; el += 23) {
            var view = net.minecraft.world.phys.Vec3.directionFromRotation((float) -el, (float) yaw);
            var d = Sky.Direction.of(yaw, el);
            require(Math.abs(view.x - d.x()) < 5e-4 && Math.abs(view.y - d.y()) < 5e-4 && Math.abs(view.z - d.z()) < 5e-4,
                "direction = Vec3.directionFromRotation at " + yaw + "/" + el);
            require(Math.abs(d.elevation() - el) < 1e-9, "elevation round trip");
            double back = d.azimuth(), wrapped = ((yaw + 180) % 360 + 360) % 360 - 180;
            require(Math.abs(back - wrapped) < 1e-9, "azimuth round trip " + yaw + " -> " + back);
        }
        require(Sky.Direction.of(400, 10).angleTo(Sky.Direction.of(40, 10)) < 1e-9, "azimuth wraps");
        require(Math.abs(Sky.Direction.of(0, 0).angleTo(Sky.Direction.of(90, 0)) - 90) < 1e-9, "right angle");
        require(Math.abs(Sky.Direction.of(0, 0).angleTo(Sky.Direction.of(180, 0)) - 180) < 1e-9, "opposite");
        require(Math.abs(Sky.Direction.of(0, 0).angleTo(Sky.Direction.of(1e-4, 0)) - 1e-4) < 1e-10, "tiny angles stay precise");
        var moved = Sky.Direction.of(30, 10).offset(15, 20);
        require(Math.abs(moved.azimuth() - 45) < 1e-9 && Math.abs(moved.elevation() - 30) < 1e-9, "offset adds degrees");
        require(Math.abs(Sky.Direction.of(30, 80).offset(0, 40).elevation() - 90) < 1e-9, "offset keeps elevation within 90");
        var toward = Sky.Direction.toward(0, 64, 0, 0, 64, 1000);
        near(toward, 0, 0, 1, "toward a far point");
        require(new Sky.Direction(0, 0, 5).z() == 1, "normalized");
        expectRejected(() -> new Sky.Direction(0, 0, 0), "zero direction");
        expectRejected(() -> new Sky.Direction(Double.NaN, 0, 1), "NaN direction");
    }

    private static void sizesAndDefaults() {
        var hole = Sky.blackHole().build();
        require(hole.type() == Sky.Type.BLACK_HOLE && hole.size() == 40 && hole.tilt() == 80 && hole.lens() == .6f
            && hole.anchor() == Sky.Anchor.DIRECTION && hole.anchorY() == 35 && !hole.hidesVanilla(), "black hole defaults");
        var eclipse = Sky.eclipse().build();
        require(eclipse.anchor() == Sky.Anchor.SUN && eclipse.hidesVanilla() && eclipse.darken() == .75f, "eclipse on the sun");
        require(!Sky.eclipse().hidesVanilla(false).build().hidesVanilla(), "eclipse may keep the sun");
        require(Sky.body().onMoon().build().hidesVanilla() && !Sky.rift().onSun().build().hidesVanilla()
            && !Sky.aurora().onMoon().build().hidesVanilla(), "hidesVanilla default per type");
        require(Sky.aurora().build().anchorX() == 180 && Sky.aurora().build().anchorY() == 12, "aurora to the north");
        var state = Sky.portal().size(40).build().sample(NONE, 1);
        require(state.radius() == 20 && state.visible(), "angular radius is half the size");
        require(!Sky.portal().size(0).build().sample(NONE, 1).visible(), "size 0 is not painted");
        require(!Sky.portal().build().sample(NONE, 0).visible(), "weight 0 is not painted");
        require(Sky.blackHole().intensity(0).build().sample(NONE, 1).visible(), "a black hole without light still has its shadow");
        var w = Sky.rift().toward(100, 200, -3000).build();
        require(w.anchor() == Sky.Anchor.WORLD && w.anchorZ() == -3000, "world anchor");
        var c = Sky.body().centerOffset(0, 400, 10).build();
        require(c.anchor() == Sky.Anchor.CENTER && c.anchorY() == 400, "center anchor");
        require(Sky.portal().direction(-45, 10).build().anchorX() == -45, "direction anchor");
        var spin = Sky.portal().rotation(10).spin(2).build().sample(NONE, 1);
        require(Math.abs(spin.rotationAt(100) - 210) < 1e-9 && Math.abs(spin.rotationAt(200) - 50) < 1e-9, "spin wraps to 0..360");
        require(Math.abs(Sky.portal().rotation(-30).build().sample(NONE, 1).rotationAt(0) - 330) < 1e-9, "negative rotation wraps");
    }

    private static void tracks() {
        var timeline = Timeline.builder(100).phase("visivel", 10, 90, 10, 10)
            .track("tamanho", keyframe(0, 0f), keyframe(50, 300f, Easing.LINEAR))
            .track("abrir", keyframe(0, -1f), keyframe(100, 2f))
            .track("subir", keyframe(0, -10f), keyframe(100, 70f))
            .track("peso", keyframe(0, .5f)).build();
        var layer = Sky.portal().direction(90, 5).track(Sky.Param.SIZE, "tamanho").track(Sky.Param.PROGRESS, "abrir")
            .track(Sky.Param.ELEVATION, "subir").track(Sky.Param.WEIGHT, "peso").weightPhase("visivel").build();
        layer.validate(timeline);
        var at50 = layer.sample(timeline.at(50), 1);
        require(at50.size() == Sky.MAX_SIZE, "track clamped to the size range");
        require(Math.abs(at50.progress() - .5f) < 1e-6, "progress from track");
        require(Math.abs(at50.weight() - .5f) < 1e-6, "weight track times the phase");
        require(Math.abs(at50.direction(Sky.Direction.of(90, 5)).elevation() - 35) < 1e-4, "elevation track rises the layer");
        require(layer.sample(timeline.at(0), 1).weight() == 0, "outside the phase");
        require(layer.sample(timeline.at(0), 1).progress() == 0 && layer.sample(timeline.at(100), 1).progress() == 1,
            "progress clamped to 0..1");
        require(layer.sample(timeline.at(50), Float.NaN).weight() == 0, "NaN envelope");
        require(Sky.Param.SIZE.clamp(Float.NaN) == 0 && Sky.Param.AZIMUTH.clamp(Float.NaN) == 0, "NaN clamps toward 0");
        require(layer.animated() && !Sky.portal().build().animated(), "animated");
    }

    private static void validation() {
        expectRejected(() -> Sky.portal().size(181), "size above 180");
        expectRejected(() -> Sky.portal().size(-1), "negative size");
        expectRejected(() -> Sky.portal().direction(0, 91), "elevation above 90");
        expectRejected(() -> Sky.portal().direction(Float.NaN, 0), "NaN azimuth");
        expectRejected(() -> Sky.portal().spin(40), "spin above max");
        expectRejected(() -> Sky.portal().tilt(91), "tilt above 90");
        expectRejected(() -> Sky.portal().progress(1.1f), "progress above 1");
        expectRejected(() -> Sky.portal().lens(-.1f), "negative lens");
        expectRejected(() -> Sky.portal().darken(2), "darken above 1");
        expectRejected(() -> Sky.portal().intensity(9), "intensity above max");
        expectRejected(() -> Sky.portal().toward(0, Double.POSITIVE_INFINITY, 0), "infinite point");
        expectRejected(() -> Sky.portal().centerOffset(0, 2e7, 0), "point too far");
        expectRejected(() -> Sky.portal().track(Sky.Param.SIZE, "Bad-Name"), "bad track name");
        expectRejected(() -> Sky.portal().track(Sky.Param.SIZE, "a").track(Sky.Param.SIZE, "b"), "parameter bound twice");
        expectRejected(() -> Sky.portal().weightPhase("a").weightPhase("b"), "two weight phases");
        expectRejected(() -> Sky.portal().track(Sky.Param.SIZE, "nada").build().validate(null), "animated without timeline");
        expectRejected(() -> Sky.portal().track(Sky.Param.SIZE, "nada").build().validate(Timeline.empty(10)), "unknown track");
        expectRejected(() -> Sky.portal().weightPhase("nada").build().validate(Timeline.empty(10)), "unknown phase");
        Sky.portal().build().validate(null);
    }

    private static void registration() {
        var id = (java.util.function.Function<String, ResourceLocation>) path -> ResourceLocation.fromNamespaceAndPath("checksky", path);
        var definition = new Effects.Definition(100, 2, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null);
        var animated = Sky.portal().track(Sky.Param.PROGRESS, "abrir").build();
        expectRejected(() -> Effects.sky(id.apply("nowhere"), Sky.portal().build()), "sky of an unknown effect");
        Effects.register(id.apply("a"), definition);
        expectRejected(() -> Effects.sky(id.apply("a"), animated), "tracks before the timeline");
        expectRejected(() -> Effects.sky(id.apply("a")), "no layers");
        Sky[] seven = new Sky[Sky.MAX_PER_EFFECT + 1];
        java.util.Arrays.fill(seven, Sky.portal().build());
        expectRejected(() -> Effects.sky(id.apply("a"), seven), "too many layers");
        Effects.timeline(id.apply("a"), Timeline.builder(100).track("abrir", keyframe(0, 0f), keyframe(50, 1f)).build());
        Effects.sky(id.apply("a"), animated, Sky.eclipse().build());
        var kind = dev.calamidade_awakening.api.VisualKind.byId(dev.calamidade_awakening.api.VisualKind.localId("checksky:a"));
        require(kind.sky().size() == 2 && kind.sky().get(0) == animated, "layers registered in order");
        expectRejected(() -> Effects.sky(id.apply("a"), Sky.portal().build()), "duplicate sky");
        expectRejected(() -> Effects.sky(ResourceLocation.fromNamespaceAndPath("calamidade_awakening", "nexus"),
            Sky.portal().build()), "engine effects are not external");
        // An ordinary effect without timeline accepts constant layers.
        Effects.register(id.apply("b"), definition);
        Effects.sky(id.apply("b"), Sky.body().build());
    }

    private static void eclipse() {
        require(Sky.eclipseCoverage(0) == 0 && Sky.eclipseCoverage(-1) == 0 && Sky.eclipseCoverage(Float.NaN) == 0, "no coverage");
        require(Sky.eclipseCoverage(1) == 1 && Sky.eclipseCoverage(.99f) == 1, "totality");
        float previous = 0;
        for (int i = 1; i <= 100; i++) {
            float c = Sky.eclipseCoverage(i / 100f);
            require(c >= previous - 1e-6f && c >= 0 && c <= 1, "coverage grows with progress");
            previous = c;
        }
        // Equal-ish disks touching at progress ~0.105 (distance 1.97 radii), half the sun at distance ~0.4 radii.
        require(Sky.eclipseCoverage(.10f) == 0 && Sky.eclipseCoverage(.12f) > 0, "first contact");
        float half = Sky.eclipseCoverage(1 - .4f / Sky.ECLIPSE_START_DISTANCE);
        require(half > .5f && half < .8f, "large overlap at a small distance: " + half);
        var total = Sky.eclipse().progress(1).build().sample(NONE, 1);
        require(Math.abs(total.darkening() - .75f) < 1e-6, "total eclipse darkens by darken × weight");
        var partial = Sky.eclipse().progress(.5f).build().sample(NONE, 1);
        float c = Sky.eclipseCoverage(.5f);
        require(Math.abs(partial.darkening() - .75f * c * c * c * c) < 1e-6, "partial eclipse: coverage to the fourth");
        require(Sky.portal().darken(.4f).build().sample(NONE, .5f).darkening() == .2f, "other types: darken × weight");
    }

    private static void selection() {
        var list = new ArrayList<SkyMath.Candidate<String>>();
        list.add(new SkyMath.Candidate<>("normal-strong", false, .9f, 1));
        list.add(new SkyMath.Candidate<>("hero-weak", true, .2f, 5));
        list.add(new SkyMath.Candidate<>("normal-weak", false, .3f, 2));
        list.add(new SkyMath.Candidate<>("tie-old", false, .3f, 0));
        list.add(new SkyMath.Candidate<>("off", false, 0, 3));
        require(SkyMath.select(list, 8).equals(List.of("hero-weak", "normal-strong", "tie-old", "normal-weak")), "order");
        require(SkyMath.select(list, 2).equals(List.of("hero-weak", "normal-strong")), "cap");
        require(SkyMath.select(list, 0).isEmpty() && SkyMath.select(list, -3).isEmpty(), "zero cap");
        var many = new ArrayList<SkyMath.Candidate<Integer>>();
        for (int i = 0; i < 20; i++) many.add(new SkyMath.Candidate<>(i, false, 1, i));
        require(SkyMath.select(many, 99).size() == SkyMath.MAX_LAYERS, "never above the data texture rows");
    }

    private static void vanillaFrame() {
        for (double t = 0; t < 1; t += .0625) {
            double a = t * Math.PI * 2;
            var m = new org.joml.Matrix3f().rotateY((float) (-Math.PI / 2)).rotateX((float) a);
            double[] axes = SkyMath.vanillaAxes(t);
            org.joml.Vector3f x = m.transform(new org.joml.Vector3f(1, 0, 0)), y = m.transform(new org.joml.Vector3f(0, 1, 0)),
                z = m.transform(new org.joml.Vector3f(0, 0, 1));
            float[] joml = {x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z};
            for (int i = 0; i < 9; i++) require(Math.abs(joml[i] - axes[i]) < 1e-6, "vanilla frame = JOML Ry(-90)·Rx at " + t);
            var sun = SkyMath.sunDirection(t);
            require(Math.abs(sun.x() - y.x) < 1e-6 && Math.abs(sun.y() - y.y) < 1e-6, "sun along local +y");
            double[] uv = SkyMath.sunUv(axes, sun.x(), sun.y(), sun.z());
            require(uv != null && Math.abs(uv[0] - .5) < 1e-9 && Math.abs(uv[1] - .5) < 1e-9, "sun center is uv 0.5");
            var moon = SkyMath.moonDirection(t);
            require(SkyMath.sunUv(axes, moon.x(), moon.y(), moon.z()) == null, "the moon direction misses the sun");
        }
        near(SkyMath.sunDirection(0), 0, 1, 0, "noon: sun at the zenith");
        require(SkyMath.sunDirection(Double.NaN).angleTo(Sky.Direction.of(0, 50)) < 1e-9
            && SkyMath.moonDirection(Double.NaN).angleTo(Sky.Direction.of(180, 50)) < 1e-9, "no vanilla sun: fixed spots");
        require(SkyMath.sunDirection(.25).x() < -.99, "sunset (time of day 0.25): sun to the west");
        double[] axes = SkyMath.vanillaAxes(0);
        // Corners of the sun quad (local (±30, 100, ±30)) map to the game's uv corners.
        double[] c00 = corner(axes, -30, 100, -30), c11 = corner(axes, 30, 100, 30);
        require(close(SkyMath.sunUv(axes, c00[0], c00[1], c00[2]), 0, 0), "sun corner (-30,-30) is uv (0,0)");
        require(close(SkyMath.sunUv(axes, c11[0], c11[1], c11[2]), 1, 1), "sun corner (30,30) is uv (1,1)");
        double[] m1 = corner(axes, 20, -100, 20), m2 = corner(axes, -20, -100, -20);
        // Phase 5: column 1, row 1: u0 = 0.25, v1 = 1; u1 = 0.5, v0 = 0.5.
        require(close(SkyMath.moonUv(axes, 5, m1[0], m1[1], m1[2]), .25, 1), "moon corner (20,20) is (u0, v1)");
        require(close(SkyMath.moonUv(axes, 5, m2[0], m2[1], m2[2]), .5, .5), "moon corner (-20,-20) is (u1, v0)");
        require(SkyMath.moonUv(axes, 0, 0, 1, 0) == null && SkyMath.sunUv(axes, 0, -1, 0) == null, "wrong hemisphere");
        double[] outside = corner(axes, 31, 100, 0);
        require(SkyMath.sunUv(axes, outside[0], outside[1], outside[2]) == null, "outside the sun quad");
    }

    /** World direction (not normalized) of a point of the vanilla local frame, pulled a hair inside the quad edge. */
    private static double[] corner(double[] axes, double lx, double ly, double lz) {
        double x = lx * .999999, z = lz * .999999;
        return new double[] {axes[0] * x + axes[3] * ly + axes[6] * z, axes[1] * x + axes[4] * ly + axes[7] * z,
            axes[2] * x + axes[5] * ly + axes[8] * z};
    }

    private static boolean close(double[] uv, double u, double v) {
        return uv != null && Math.abs(uv[0] - u) < 1e-4 && Math.abs(uv[1] - v) < 1e-4;
    }

    private static void localFrame() {
        var center = Sky.Direction.of(37, 21);
        double[] basis = SkyMath.basis(center, .6);
        for (int i = 0; i < 3; i++) {
            double len = Math.sqrt(basis[i * 3] * basis[i * 3] + basis[i * 3 + 1] * basis[i * 3 + 1] + basis[i * 3 + 2] * basis[i * 3 + 2]);
            require(Math.abs(len - 1) < 1e-9, "basis is unit");
        }
        require(Math.abs(basis[0] * basis[3] + basis[1] * basis[4] + basis[2] * basis[5]) < 1e-9, "basis orthogonal");
        // Without roll, "up" points toward the zenith and "right" is horizontal.
        double[] flat = SkyMath.basis(Sky.Direction.of(0, 10), 0);
        require(Math.abs(flat[1]) < 1e-12 && flat[4] > 0 && flat[0] < -.99, "looking south: right is west, up is up");
        var d = Sky.Direction.of(45, 30);
        double[] local = SkyMath.local(basis, d);
        require(Math.abs(Math.hypot(local[0], local[1]) - Math.toRadians(center.angleTo(d))) < 1e-9, "local length is the angle");
        var back = SkyMath.fromLocal(basis, local[0], local[1]);
        require(back.angleTo(d) < 1e-7, "local round trip");
        require(SkyMath.fromLocal(basis, 0, 0).angleTo(center) < 1e-9, "origin is the center");
        double[] up = SkyMath.basis(new Sky.Direction(0, 1, 0), 0);
        require(up[5] > .99 && Math.abs(up[0] - 1) < 1e-9, "vertical center: up points south");
    }

    private static void darkening() {
        require(SkyMath.exposure(0, 1) == 1, "no darkening");
        require(Math.abs(SkyMath.exposure(1, 1) - (1 - SkyMath.MAX_DARKENING)) < 1e-6, "full darkening");
        require(SkyMath.exposure(1, 0) == 1, "intensity 0: never darkened");
        require(Math.abs(SkyMath.exposure(.5f, .5f) - (1 - SkyMath.MAX_DARKENING * .25f)) < 1e-6, "scaled by intensity");
        require(SkyMath.exposure(Float.NaN, 1) == 1 && SkyMath.exposure(3, 1) == SkyMath.exposure(1, 1), "clamped");
        require(SkyMath.displayed(1, .15f, 1, false) == .15f, "without calm: right away");
        float calm = SkyMath.displayed(1, .15f, 1, true);
        require(Math.abs(calm - (1 - dev.calamidade_awakening.api.AtmosphereMath.CALM_EXPOSURE_PER_TICK)) < 1e-6,
            "hidden flashes: 0.025 per tick");
        float e = 1;
        int ticks = 0;
        while (e > .15f + 1e-4f && ticks < 1000) { e = SkyMath.displayed(e, .15f, 1, true); ticks++; }
        require(ticks == 34, "full darkening takes 34 ticks with hidden flashes: " + ticks);
    }

    private static void limits() {
        require(QualityProfile.LOW.skyLayers() == 2 && QualityProfile.BALANCED.skyLayers() == 4
            && QualityProfile.HIGH.skyLayers() == 6 && QualityProfile.ULTRA.skyLayers() == 8, "layers per profile");
        for (var p : QualityProfile.values()) require(p.limits().skyLayers() == p.skyLayers(), "profile limits carry the sky cap");
        require(FrameLimits.of(Quality.BALANCED).skyLayers() == 4, "0.11.2 constructor: layers of the tier");
        require(FrameLimits.unlimited().skyLayers() == SkyMath.MAX_LAYERS, "test profile: every row");
        require(new FrameLimits(Quality.LOW, "x", 1, 1, 1, 1, 1, 1, 1, false, 99).skyLayers() == SkyMath.MAX_LAYERS, "clamped");
        require(SkyMath.lenses(2) == 1 && SkyMath.lenses(4) == 2 && SkyMath.lenses(8) == 2, "lenses");
        double r = Math.toRadians(15);
        require(Math.abs(SkyMath.einstein(r, 0) - r * SkyMath.SHADOW * 1.05) < 1e-12, "lens 0: ring just outside the shadow");
        require(SkyMath.SHADOW == .2, "shadow is a fifth of the size (sky.fsh BH_SHADOW)");
        require(SkyMath.einstein(r, 1) > SkyMath.einstein(r, .5) && SkyMath.lensExtent(r, 1) > SkyMath.lensExtent(r, .5),
            "stronger lens: larger ring and reach");
        require(SkyMath.lensExtent(r, 0) > SkyMath.einstein(r, 1), "the lens reaches beyond its Einstein ring");
    }

    private static void near(Sky.Direction d, double x, double y, double z, String test) {
        require(Math.abs(d.x() - x) < 1e-9 && Math.abs(d.y() - y) < 1e-9 && Math.abs(d.z() - z) < 1e-9,
            test + ": " + d);
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
