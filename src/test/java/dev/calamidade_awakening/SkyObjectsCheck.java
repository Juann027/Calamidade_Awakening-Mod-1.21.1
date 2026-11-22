package dev.calamidade_awakening;

import dev.calamidade_awakening.api.FrameLimits;
import dev.calamidade_awakening.api.Quality;
import dev.calamidade_awakening.api.QualityProfile;
import dev.calamidade_awakening.api.SkyObjectsMath;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Nebula;
import dev.calamidade_awakening.api.v1.SkyBlend;
import dev.calamidade_awakening.api.v1.SkyDarkening;
import dev.calamidade_awakening.api.v1.SkyProfile;
import dev.calamidade_awakening.api.v1.StarField;
import dev.calamidade_awakening.api.v1.Timeline;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/**
 * Sky objects (0.14.0, phase 3): StarField and Nebula builder rules and sampling, deterministic star generation,
 * magnitudes, bands and regions, catalogs and constellation segments, profile caps and star budget, frame selection and
 * drawing order, the object frame (celestial turn), the premultiplied composition of the three SkyBlend modes and the
 * reactions to daylight and darkening.
 */
final class SkyObjectsCheck {
    private SkyObjectsCheck() {}

    static void run() {
        builders();
        sampling();
        generation();
        catalog();
        caps();
        selection();
        frame();
        composition();
        reactions();
        registration();
        System.out.println("SkyObjectsCheck PASS");
    }

    private static void builders() {
        var f = StarField.random(42, 1000).build();
        require(f.kind() == StarField.Kind.RANDOM && f.count() == 1000 && f.blend() == SkyBlend.LUMINOUS
            && f.darkening() == SkyDarkening.REVEAL && f.atmosphere() == 1 && f.daylight() == 1 && f.celestial()
            && f.order() == StarField.DEFAULT_ORDER && !f.animated(), "star field defaults");
        var n = Nebula.cloud(10, 20, 15).build();
        require(n.shape() == Nebula.Shape.CLOUD && n.blend() == SkyBlend.NORMAL && n.order() == Nebula.DEFAULT_ORDER
            && n.order() < StarField.DEFAULT_ORDER, "nebula defaults: NORMAL, behind the stars");
        expectRejected(() -> StarField.random(1, 0), "count 0");
        expectRejected(() -> StarField.random(1, StarField.MAX_RANDOM + 1), "count too high");
        expectRejected(() -> StarField.random(1, 10).magnitudes(6, -1), "magnitudes reversed");
        expectRejected(() -> StarField.random(1, 10).star(0, 0, 1, -1), "star on a random field");
        expectRejected(() -> StarField.catalog().magnitudes(0, 1), "magnitudes on a catalog");
        expectRejected(() -> StarField.catalog().build(), "empty catalog");
        expectRejected(() -> StarField.catalog().star("a", 0, 95, 1, -1), "elevation range");
        expectRejected(() -> StarField.catalog().star("a", 0, 0, 1, -1).star("a", 1, 0, 1, -1), "duplicate star name");
        expectRejected(() -> StarField.catalog().star("A", 0, 0, 1, -1), "star name case");
        expectRejected(() -> StarField.catalog().star("a", 0, 0, 1, -1).constellation(-1, "a", "b"), "unknown star in line");
        expectRejected(() -> StarField.catalog().star("a", 0, 0, 1, -1).constellation(-1, "a"), "single-star line");
        expectRejected(() -> StarField.random(1, 10).twinkle(2, 1), "twinkle range");
        expectRejected(() -> StarField.random(1, 10).spin(StarField.MAX_SPIN + 1), "spin range");
        expectRejected(() -> StarField.random(1, 10).order(5000), "order range");
        expectRejected(() -> StarField.random(1, 10).band(0, 90, 0.5f, .5f), "band width");
        expectRejected(() -> StarField.random(1, 10).blend(null), "null blend");
        expectRejected(() -> Nebula.cloud(0, 0, 90), "cloud radius");
        expectRejected(() -> Nebula.band(0, 0, 70), "band width");
        expectRejected(() -> Nebula.cloud(0, 0, 10).detail(.1f), "detail");
        expectRejected(() -> Nebula.cloud(0, 0, 10).density(1.5f), "density");
        // Profiles take constant objects only, at most the per-effect counts.
        var anim = StarField.random(1, 10).weightPhase("fase").build();
        expectRejected(() -> SkyProfile.State.builder().stars(anim), "animated field in a profile");
        expectRejected(() -> SkyProfile.State.builder().nebulae(Nebula.cloud(0, 0, 5).track(Nebula.Param.OPACITY, "t").build()), "animated nebula in a profile");
        var b = SkyProfile.State.builder();
        for (int i = 0; i < StarField.MAX_PER_EFFECT; i++) b.stars(f);
        expectRejected(() -> b.stars(f), "too many fields in a state");
        var state = SkyProfile.State.builder().stars(f).nebulae(n).build();
        require(state.stars().equals(List.of(f)) && state.nebulae().equals(List.of(n)), "profile state keeps them");
        require(SkyProfile.State.builder().build().stars().isEmpty(), "states without objects: nothing new (opt-in)");
    }

    private static void sampling() {
        var tl = Timeline.builder(100).phase("fase", 0, 100, 10, 10).track("op", Timeline.keyframe(0, .5f))
            .track("brilho", Timeline.keyframe(0, 3f)).track("giro", Timeline.keyframe(0, 45f)).build();
        var f = StarField.random(1, 10).opacity(.8f).track(StarField.Param.OPACITY, "op").track(StarField.Param.BRIGHTNESS, "brilho")
            .track(StarField.Param.ROTATION, "giro").weightPhase("fase").spin(2).build();
        f.validate(tl);
        var s = f.sample(tl.at(50), .5f);
        require(near(s.opacity(), .8f * .5f * .5f, 1e-5f) && near(s.brightness(), 3, 1e-6f) && near(s.rotation(), 45, 1e-6f)
            && s.spin() == 2, "star field sampling: " + s);
        require(near((float) s.rotationAt(10, true), 65, 1e-4f) && s.rotationAt(10, false) == 45, "spin stops without motion");
        require(f.sample(tl.at(0), 1).opacity() == 0, "phase weight 0 at its start");
        var n = Nebula.band(0, 90, 10).opacity(.6f).build();
        require(near(n.sample(Timeline.empty(1).at(0), .5f).opacity(), .3f, 1e-6f), "nebula envelope");
        expectRejected(() -> StarField.random(1, 10).weightPhase("nada").build().validate(tl), "unknown phase");
        expectRejected(() -> StarField.random(1, 10).weightPhase("fase").build().validate(null), "phase without timeline");
    }

    private static void generation() {
        var f = StarField.random(1234, 4000).magnitudes(-1, 6).build();
        var a = SkyObjectsMath.stars(f);
        var b = SkyObjectsMath.stars(StarField.random(1234, 4000).magnitudes(-1, 6).build());
        require(a.size() == 4000 && java.util.Arrays.equals(SkyObjectsMath.flat(a), SkyObjectsMath.flat(b)), "deterministic by seed");
        require(!java.util.Arrays.equals(SkyObjectsMath.flat(a), SkyObjectsMath.flat(SkyObjectsMath.stars(StarField.random(1235, 4000).build()))),
            "another seed, other stars");
        for (int i = 1; i < a.size(); i++)
            require(a.get(i - 1).intensity() * (a.get(i - 1).argb() >>> 24) >= a.get(i).intensity() * (a.get(i).argb() >>> 24),
                "brightest first (prefix = the brightest stars)");
        int faint = 0, bright = 0, up = 0;
        for (var s : a) {
            require(near((float) Math.sqrt(s.x() * s.x() + s.y() * s.y() + s.z() * s.z()), 1, 1e-5f), "unit directions");
            if (s.intensity() < SkyObjectsMath.intensity(4)) faint++;
            if (s.intensity() > SkyObjectsMath.intensity(2)) bright++;
            if (s.y() > 0) up++;
            require(s.halfSize() > 0 && s.phase() >= 0 && s.phase() < 1, "size and phase");
        }
        require(faint > bright * 4, "more faint stars than bright ones: " + faint + " vs " + bright);
        require(Math.abs(up - 2000) < 200, "uniform over the sphere: " + up + " of 4000 above the horizon");
        // Magnitudes: 1 is 1, brighter is more, 6 still visible on an 8-bit screen.
        require(near(SkyObjectsMath.intensity(1), 1, 1e-6f) && SkyObjectsMath.intensity(-1.5f) > 4
            && SkyObjectsMath.intensity(6) > 1 / 255f * 10 && SkyObjectsMath.intensity(-100) == 32, "magnitude scale");
        require(SkyObjectsMath.halfSize(4, 1) > SkyObjectsMath.halfSize(.1f, 1) && SkyObjectsMath.halfSize(1, 2) > SkyObjectsMath.halfSize(1, 1), "size grows with brightness and size");
        // Band: most stars near the great circle perpendicular to the pole (zenith pole: the horizon circle).
        var band = SkyObjectsMath.stars(StarField.random(7, 3000).band(0, 90, 8, 1f).build());
        int near = 0;
        for (var s : band) if (Math.abs(Math.toDegrees(Math.asin(s.y()))) < 16) near++;
        require(near > 2700, "band concentrates the stars: " + near);
        // Region: every star inside the circle.
        var region = SkyObjectsMath.stars(StarField.random(9, 2000).region(90, 30, 20).build());
        double[] c = SkyObjectsMath.direction(90, 30);
        for (var s : region) require(s.x() * c[0] + s.y() * c[1] + s.z() * c[2] >= Math.cos(Math.toRadians(20)) - 1e-6, "inside the region");
        // Palette: only the given colors.
        for (var s : SkyObjectsMath.stars(StarField.random(3, 200).colors(0xffff0000, 0xff00ff00).build()))
            require(s.argb() == 0xffff0000 || s.argb() == 0xff00ff00, "palette");
    }

    private static void catalog() {
        var f = StarField.catalog().star("a", 0, 30, 1, 0xffffffff).star("b", 30, 30, 2, 0xffff8080)
            .star("c", 60, 40, 0, 0xff8080ff).star(90, 10, 5, 0x80ffffff).constellation(0x80ffffff, "a", "b", "c").build();
        require(f.count() == 4 && f.lines().size() == 2 && f.lines().get(0).from() == 0 && f.lines().get(1).to() == 2, "catalog lines");
        var stars = SkyObjectsMath.stars(f);
        require(stars.get(0).intensity() == SkyObjectsMath.intensity(0) && stars.get(0).argb() == 0xff8080ff, "brightest catalog star first");
        double[] d = SkyObjectsMath.direction(0, 30);
        require(stars.stream().anyMatch(s -> near(s.x(), (float) d[0], 1e-6f) && near(s.y(), (float) d[1], 1e-6f)), "catalog direction");
        var seg = SkyObjectsMath.segments(f);
        require(seg.size() == 2 && near(seg.get(0).halfWidth(), (float) Math.toRadians(.06), 1e-7f), "segments");
        int pieces = SkyObjectsMath.pieces(seg.get(0));
        require(pieces >= 12 && pieces <= 16, "a ~26 degree line in 2-degree pieces: " + pieces);
        double[] mid = SkyObjectsMath.along(seg.get(0), .5);
        require(near((float) Math.sqrt(mid[0] * mid[0] + mid[1] * mid[1] + mid[2] * mid[2]), 1, 1e-9f)
            && near((float) mid[1], (float) Math.sin(Math.toRadians(30)), .02f), "great circle midpoint on the sphere");
        double[] end = SkyObjectsMath.along(seg.get(0), 1);
        require(near((float) end[0], (float) seg.get(0).to()[0], 1e-9f), "segment end");
    }

    private static void caps() {
        var low = SkyObjectsMath.caps(QualityProfile.LOW.limits());
        var bal = SkyObjectsMath.caps(QualityProfile.BALANCED.limits());
        var high = SkyObjectsMath.caps(QualityProfile.HIGH.limits());
        var ultra = SkyObjectsMath.caps(QualityProfile.ULTRA.limits());
        require(low.stars() < bal.stars() && bal.stars() < high.stars() && high.stars() < ultra.stars()
            && low.fields() <= bal.fields() && low.nebulae() < high.nebulae() && low.octaves() < high.octaves(), "caps grow with the profile");
        require(SkyObjectsMath.caps(FrameLimits.unlimited(8, 8)).stars() >= ultra.stars(), "test profile lifts the caps");
        require(SkyObjectsMath.caps(FrameLimits.of(Quality.HIGH)).equals(high), "tier HIGH by name");
        require(java.util.Arrays.equals(SkyObjectsMath.share(new int[] {3000, 5000, 100}, 4000), new int[] {3000, 1000, 0}),
            "budget: the most important field first");
        require(java.util.Arrays.equals(SkyObjectsMath.share(new int[] {10, 20}, 100), new int[] {10, 20}), "budget left over");
    }

    private static void selection() {
        List<SkyObjectsMath.Candidate<String>> all = new ArrayList<>();
        all.add(new SkyObjectsMath.Candidate<>("efeito", false, 1, 1, 5, SkyObjectsMath.RANK_EFFECT, 200));
        all.add(new SkyObjectsMath.Candidate<>("hero", true, 1, .5f, 6, SkyObjectsMath.RANK_EFFECT, 200));
        all.add(new SkyObjectsMath.Candidate<>("perfil", false, 1, 1, 1, SkyObjectsMath.RANK_PROFILE, 100));
        all.add(new SkyObjectsMath.Candidate<>("evento", false, .2f, 1, 2, SkyObjectsMath.RANK_EVENT + 80, 300));
        all.add(new SkyObjectsMath.Candidate<>("invisivel", false, 0, 1, 3, SkyObjectsMath.RANK_EVENT + 99, 0));
        var chosen = SkyObjectsMath.select(all, 3);
        require(chosen.size() == 3 && chosen.get(0).value().equals("evento") && chosen.get(1).value().equals("perfil")
            && chosen.get(2).value().equals("hero"), "importance: events, profile, HERO; invisible skipped: " + chosen);
        var order = SkyObjectsMath.drawingOrder(chosen);
        require(order.get(0).value().equals("perfil") && order.get(2).value().equals("evento"), "drawn by draw order (behind first)");
        List<SkyObjectsMath.Candidate<String>> same = List.of(
            new SkyObjectsMath.Candidate<>("forte", false, 1, 1, 1, 0, 200), new SkyObjectsMath.Candidate<>("fraco", false, .3f, 1, 2, 0, 200));
        require(SkyObjectsMath.drawingOrder(same).get(1).value().equals("forte"), "equal order: the most important on top");
    }

    private static void frame() {
        // Without the day cycle or celestial off: only the turn (+90 moves south to west).
        double[] turn = SkyObjectsMath.frame(Double.NaN, true, 90);
        double[] w = SkyObjectsMath.apply(turn, 0, 0, 1);
        require(near((float) w[0], -1, 1e-9f) && near((float) w[2], 0, 1e-9f), "turn south to west");
        // Celestial: directions are those seen at midnight; at noon the zenith star is at the nadir; the sun axis is east-west.
        double[] midnight = SkyObjectsMath.apply(SkyObjectsMath.frame(.5, true, 0), 0, 1, 0);
        require(near((float) midnight[1], 1, 1e-9f), "midnight: as given");
        double[] noon = SkyObjectsMath.apply(SkyObjectsMath.frame(0, true, 0), 0, 1, 0);
        require(near((float) noon[1], -1, 1e-9f), "noon: half a turn");
        double[] axis = SkyObjectsMath.apply(SkyObjectsMath.frame(.17, true, 0), 0, 0, 1);
        require(near((float) axis[2], 1, 1e-9f), "the north-south axis does not move (rotation around it)");
        // Same turn as the vanilla sun: the sun direction at t is the midnight nadir turned by the frame.
        var sun = dev.calamidade_awakening.api.SkyMath.sunDirection(.3);
        double[] fromNadir = SkyObjectsMath.apply(SkyObjectsMath.frame(.3, true, 0), 0, -1, 0);
        require(near((float) fromNadir[0], (float) sun.x(), 1e-9f) && near((float) fromNadir[1], (float) sun.y(), 1e-9f), "same axis as the sun");
        require(near((float) SkyObjectsMath.apply(SkyObjectsMath.frame(.3, false, 0), 0, 1, 0)[1], 1, 1e-9f), "celestial off: fixed");
    }

    private static void composition() {
        float[] backdrop = {.2f, .5f, .9f};
        float[] red = {1, .1f, .1f};
        // LUMINOUS keeps the background behind (only adds); never covers.
        float[] lum = SkyObjectsMath.premultiplied(SkyBlend.LUMINOUS, red, .8f, 1, 1);
        require(lum[3] == 0, "LUMINOUS covers nothing");
        float[] lumOut = SkyObjectsMath.over(backdrop, lum);
        require(lumOut[2] >= backdrop[2] && lumOut[1] >= backdrop[1], "LUMINOUS: background still there");
        // NORMAL: an opaque star core keeps its exact color over a bright background.
        float[] core = SkyObjectsMath.over(backdrop, SkyObjectsMath.premultiplied(SkyBlend.NORMAL, red, 1, 1, 1));
        require(near(core[0], 1, 1e-6f) && near(core[1], .1f, 1e-6f) && near(core[2], .1f, 1e-6f), "NORMAL: exact color");
        // NORMAL half transparent: half of the background shows; transparent pixels hide nothing.
        float[] half = SkyObjectsMath.over(backdrop, SkyObjectsMath.premultiplied(SkyBlend.NORMAL, red, .5f, 1, 1));
        require(near(half[2], .5f * .9f + .5f * .1f, 1e-6f), "NORMAL: real transparency");
        float[] clear = SkyObjectsMath.over(backdrop, SkyObjectsMath.premultiplied(SkyBlend.NORMAL, red, 0, 1, 1));
        require(java.util.Arrays.equals(clear, backdrop), "NORMAL: alpha 0 hides nothing (no rectangle)");
        // COVER: only the silhouette (alpha >= 0.5) covers, fully; outside nothing.
        require(SkyObjectsMath.premultiplied(SkyBlend.COVER, red, .6f, 1, 1)[3] == 1
            && SkyObjectsMath.premultiplied(SkyBlend.COVER, red, .4f, 1, 1)[3] == 0
            && SkyObjectsMath.premultiplied(SkyBlend.COVER, red, .4f, 1, 1)[0] == 0, "COVER: silhouette");
        require(SkyObjectsMath.premultiplied(SkyBlend.COVER, red, .9f, .5f, 1)[3] == .5f, "COVER: times opacity");
        // Premultiplied over is associative: drawing objects one by one on the screen equals composing the coverage
        // layer (cleared to zero with the same blend) over the screen. That is why the passes can trust the layer.
        float[] o1 = SkyObjectsMath.premultiplied(SkyBlend.NORMAL, red, .4f, 1, 1);
        float[] o2 = SkyObjectsMath.premultiplied(SkyBlend.LUMINOUS, new float[] {.1f, .9f, .2f}, .7f, 1, 1.5f);
        float[] seq = SkyObjectsMath.over(SkyObjectsMath.over(backdrop, o1), o2);
        float[] layerRgb = SkyObjectsMath.over(new float[] {0, 0, 0}, o1);
        float layerA = 1 - (1 - o1[3]) * (1 - o2[3]);
        float[] layer = SkyObjectsMath.over(layerRgb, o2);
        float[] viaLayer = SkyObjectsMath.over(backdrop, new float[] {layer[0], layer[1], layer[2], layerA});
        for (int i = 0; i < 3; i++) require(near(seq[i], viaLayer[i], 1e-6f), "coverage layer equals sequential drawing");
    }

    private static void reactions() {
        // Daylight: at noon (night 0) a daylight-1 object is gone; daylight 0 always visible.
        require(SkyObjectsMath.dayVisibility(1, 0) == 0 && SkyObjectsMath.dayVisibility(0, 0) == 1
            && SkyObjectsMath.dayVisibility(1, 1) == 1, "daylight");
        // Eclipse at its deepest (exposure 0.15): REVEAL brings day-hidden stars out; DIM darkens; IGNORE keeps the day value.
        require(near(SkyObjectsMath.darkeningGain(SkyDarkening.REVEAL, 1, .15f, 0), 1, 1e-6f), "REVEAL: stars out in totality");
        require(SkyObjectsMath.darkeningGain(SkyDarkening.REVEAL, 1, 1, 0) == 0, "REVEAL: nothing without darkening");
        require(near(SkyObjectsMath.darkeningGain(SkyDarkening.REVEAL, .5f, .15f, 0), .5f, 1e-6f), "REVEAL strength");
        require(near(SkyObjectsMath.darkeningGain(SkyDarkening.DIM, 1, .15f, 1), .15f, 1e-6f), "DIM like the world");
        require(SkyObjectsMath.darkeningGain(SkyDarkening.IGNORE, 1, .15f, .3f) == .3f, "IGNORE");
        // Twinkle stays between 1 - amount and 1; accessibility scales it.
        for (double t = 0; t < 10; t += .37) {
            float k = SkyObjectsMath.twinkle(.4f, 1.3f, .2f, t);
            require(k >= .6f - 1e-6f && k <= 1 + 1e-6f, "twinkle bounds");
        }
        require(SkyObjectsMath.twinkleScale(0, false) == 0 && SkyObjectsMath.twinkleScale(1, true) == .25f, "twinkle accessibility");
    }

    private static void registration() {
        var id = ResourceLocation.fromNamespaceAndPath("checkstars", "ceu");
        Effects.register(id, new Effects.Definition(40, 4, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        expectRejected(() -> Effects.stars(id, StarField.random(1, 10).weightPhase("nada").build()), "phase without timeline");
        expectRejected(() -> Effects.stars(id), "no fields");
        var f = StarField.random(1, 10).build();
        expectRejected(() -> Effects.stars(id, f, f, f, f, f), "too many fields");
        Effects.stars(id, f);
        var n = Nebula.cloud(0, 30, 10).build();
        Effects.nebulae(id, n);
        var kind = VisualKind.byId(VisualKind.localId(id.toString()));
        require(kind.stars().equals(List.of(f)) && kind.nebulae().equals(List.of(n)), "registered");
        expectRejected(() -> Effects.stars(id, f), "duplicate fields");
        expectRejected(() -> Effects.nebulae(id, n), "duplicate nebulae");
        expectRejected(() -> Effects.stars(ResourceLocation.fromNamespaceAndPath("checkstars", "nada"), f), "unknown effect");
        var other = ResourceLocation.fromNamespaceAndPath("checkstars", "sem_ceu");
        Effects.register(other, new Effects.Definition(40, 4, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        var none = VisualKind.byId(VisualKind.localId(other.toString()));
        require(none.stars().isEmpty() && none.nebulae().isEmpty(), "effects without objects draw none (opt-in)");
    }

    private static boolean near(float a, float b, float eps) { return Math.abs(a - b) <= eps; }

    private static void require(boolean ok, String what) {
        if (!ok) throw new AssertionError("SkyObjectsCheck: " + what);
    }

    private static void expectRejected(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException | NullPointerException expected) {
            return;
        }
        throw new AssertionError("SkyObjectsCheck: expected rejection: " + what);
    }
}
