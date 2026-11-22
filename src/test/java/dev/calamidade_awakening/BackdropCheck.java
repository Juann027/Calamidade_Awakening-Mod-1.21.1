package dev.calamidade_awakening;

import dev.calamidade_awakening.api.BackdropMath;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.api.v1.Backdrop;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.SkyProfile;
import dev.calamidade_awakening.api.v1.Timeline;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Custom sky backgrounds (0.14.0): builder rules, sampling, frame selection and crossfade, texture lookups. */
final class BackdropCheck {
    private BackdropCheck() {}

    static void run() {
        builder();
        sampling();
        selection();
        lookups();
        registration();
        System.out.println("BackdropCheck PASS");
    }

    private static void builder() {
        var cube = Backdrop.cubemap("meumod:textures/sky/ceu.png").build();
        require(cube.kind() == Backdrop.Kind.CUBEMAP && cube.texture().equals("meumod:textures/sky/ceu.png"), "cubemap");
        require(cube.opacity() == 1 && cube.rotation() == 0 && cube.spin() == 0 && cube.tint() == 0xffffffff
            && cube.matchFog() && !cube.vanillaSunMoon() && !cube.animated(), "defaults");
        var g = Backdrop.gradient(0xff102040, 0xff8090a0, 0x80000000).build();
        require(g.kind() == Backdrop.Kind.GRADIENT && g.texture() == null && g.bottom() == 0x80000000, "gradient");
        expectRejected(() -> Backdrop.panorama("sem_namespace.png"), "texture without namespace");
        expectRejected(() -> Backdrop.panorama("meumod:textures/ceu.jpg"), "texture not png");
        expectRejected(() -> Backdrop.panorama("Meu:textures/ceu.png"), "uppercase namespace");
        expectRejected(() -> Backdrop.panorama("a:b.png").opacity(1.5f), "opacity range");
        expectRejected(() -> Backdrop.panorama("a:b.png").spin(Backdrop.MAX_SPIN + 1), "spin range");
        expectRejected(() -> Backdrop.panorama("a:b.png").rotation(Float.NaN), "rotation NaN");
        expectRejected(() -> Backdrop.panorama("a:b.png").track(Backdrop.Param.OPACITY, "Bad"), "track name");
        // Profiles take constant backgrounds only.
        var fixed = SkyProfile.State.builder().backdrop(cube).build();
        require(fixed.backdrop() == cube && SkyProfile.State.builder().build().backdrop() == null, "state backdrop");
        expectRejected(() -> SkyProfile.State.builder().backdrop(Backdrop.panorama("a:b.png").weightPhase("x").build()),
            "animated backdrop in a profile");
    }

    private static void sampling() {
        var timeline = Timeline.builder(100).phase("fundo", 0, 100, 10, 10)
            .track("op", Timeline.keyframe(0, .2f), Timeline.keyframe(100, 2f)).track("giro", Timeline.keyframe(0, 45f)).build();
        var b = Backdrop.panorama("a:b.png").opacity(.5f).tint(0x80ffffff).weightPhase("fundo")
            .track(Backdrop.Param.OPACITY, "op").track(Backdrop.Param.ROTATION, "giro").spin(10).build();
        b.validate(timeline);
        var s = b.sample(timeline.at(50), .8f);
        // opacity .5 × track ~1.1 → clamped 1 × tint alpha 128/255 × weight .8 × phase 1
        require(near(s.opacity(), .5f * 1f * (128 / 255f) * .8f, 1e-4f), "opacity product " + s.opacity());
        require(s.rotation() == 45 && s.spin() == 10 && near((float) s.rotationAt(2, true), 65, 1e-4f)
            && s.rotationAt(2, false) == 45, "rotation and spin");
        require(b.sample(timeline.at(0), 1).opacity() == 0, "phase weight 0 at its start");
        require(!Backdrop.panorama("a:b.png").build().sample(timeline.at(5), Float.NaN).visible(), "NaN envelope");
        expectRejected(() -> b.validate(null), "animated without timeline");
        expectRejected(() -> Backdrop.panorama("a:b.png").track(Backdrop.Param.OPACITY, "nada").build().validate(timeline), "unknown track");
        require(Backdrop.Param.OPACITY.clamp(Float.NaN) == 0 && Backdrop.Param.ROTATION.clamp(1e9f) == 3600, "param clamp");
    }

    private static void selection() {
        require(BackdropMath.select(List.<BackdropMath.Candidate<String>>of()).isEmpty(), "nothing");
        // A lone background fading in: drawn alone at its opacity (the vanilla sky shows below).
        var lone = BackdropMath.select(List.of(new BackdropMath.Candidate<>("a", false, .4f, 1, 0, 0)));
        require(lone.size() == 1 && near(lone.get(0).alpha(), .4f, 1e-6f), "lone fade");
        // Profile crossfade: weights 0.3 (to) and 0.7 (from). The heavier is on top, the other below at full opacity.
        var cross = BackdropMath.select(List.of(new BackdropMath.Candidate<>("para", false, .3f, 1, 1, 500),
            new BackdropMath.Candidate<>("de", false, .7f, 1, 2, 500)));
        require(cross.size() == 2 && cross.get(0).value().equals("para") && cross.get(0).alpha() == 1
            && cross.get(1).value().equals("de") && near(cross.get(1).alpha(), .7f, 1e-6f), "crossfade order " + cross);
        require(near(BackdropMath.coverage(cross.get(0).alpha(), cross.get(1).alpha()), 1, 1e-6f), "crossfade keeps full coverage");
        // A global event goes over the dimension's profile; an opaque top draws alone.
        var event = BackdropMath.select(List.of(new BackdropMath.Candidate<>("perfil", false, 1, 1, 0, 500),
            new BackdropMath.Candidate<>("evento", false, 1, 1, 9, 1080)));
        require(event.size() == 1 && event.get(0).value().equals("evento"), "event over profile");
        // An effect fading out over the profile: profile stays full below.
        var fading = BackdropMath.select(List.of(new BackdropMath.Candidate<>("perfil", false, 1, .9f, 0, 500),
            new BackdropMath.Candidate<>("efeito", true, .5f, 1, 3, 0)));
        require(fading.size() == 1 && fading.get(0).value().equals("perfil") && near(fading.get(0).alpha(), .9f, 1e-6f)
            || fading.size() == 2, "profile rank wins");
        var effects = BackdropMath.select(List.of(new BackdropMath.Candidate<>("normal", false, 1, 1, 0, 0),
            new BackdropMath.Candidate<>("heroi", true, .5f, 1, 5, 0)));
        require(effects.size() == 2 && effects.get(1).value().equals("heroi") && effects.get(0).alpha() == 1, "HERO on top " + effects);
        require(BackdropMath.select(List.of(new BackdropMath.Candidate<>("x", false, 1, 0, 0, 0))).isEmpty(), "transparent skipped");
        require(near(BackdropMath.coverage(.5f, .5f), .75f, 1e-6f) && BackdropMath.coverage() == 0, "coverage");
    }

    private static void lookups() {
        // Face centers land in the middle of their atlas cells.
        double[][] centers = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        for (int f = 0; f < 6; f++) {
            double[] uv = BackdropMath.cubeUv(centers[f][0], centers[f][1], centers[f][2]);
            require(BackdropMath.face(centers[f][0], centers[f][1], centers[f][2]) == f
                && near(uv[0], ((f % 3) + .5) / 3) && near(uv[1], ((f / 3) + .5) / 2), "face center " + f);
        }
        // Side faces: up is the top of the image; seen from inside, north's right is east, east's right is south.
        require(near(BackdropMath.cubeLocal(.5, .9, -1)[2], .05) && near(BackdropMath.cubeLocal(.9, 0, -1)[1], .95), "north orientation");
        require(near(BackdropMath.cubeLocal(1, 0, .9)[1], .95) && near(BackdropMath.cubeLocal(-.9, 0, 1)[1], .95)
            && near(BackdropMath.cubeLocal(-1, 0, -.9)[1], .95), "side faces turn clockwise from above");
        // Up: north at the top, east on the right. Down: south at the top, east on the right.
        require(near(BackdropMath.cubeLocal(0, 1, -.9)[2], .05) && near(BackdropMath.cubeLocal(.9, 1, 0)[1], .95), "up orientation");
        require(near(BackdropMath.cubeLocal(0, -1, .9)[2], .05) && near(BackdropMath.cubeLocal(.9, -1, 0)[1], .95), "down orientation");
        // Neighbouring faces meet: the north face's top edge and the up face's top edge are the same line.
        double[] a = BackdropMath.cubeLocal(.3, .999, -1), b = BackdropMath.cubeLocal(.3, 1, -.999);
        require(near(a[1], b[1], 2e-3) && near(a[2], 0, 2e-3) && near(b[2], 0, 2e-3), "north/up seam");
        // Inverses: the geometry corners map back to the same face and coordinates.
        for (int f = 0; f < 6; f++) for (double u = .1; u < 1; u += .4) for (double v = .1; v < 1; v += .4) {
            double[] d = BackdropMath.cubeDirection(f, u, v), l = BackdropMath.cubeLocal(d[0], d[1], d[2]);
            require((int) l[0] == f && near(l[1], u, 1e-9) && near(l[2], v, 1e-9), "cube inverse " + f + " " + u + " " + v);
        }
        for (double u = .05; u < 1; u += .3) for (double v = .1; v < .95; v += .2) {
            double[] d = BackdropMath.panoramaDirection(u, v), p = BackdropMath.panoramaUv(d[0], d[1], d[2]);
            require(near(p[0], u, 1e-9) && near(p[1], v, 1e-9), "panorama inverse " + u + " " + v);
        }
        // Panorama: south in the middle, west at three quarters, zenith on top.
        require(near(BackdropMath.panoramaUv(0, 0, 1)[0], .5) && near(BackdropMath.panoramaUv(-1, 0, 0)[0], .75)
            && near(BackdropMath.panoramaUv(1, 0, 0)[0], .25) && near(BackdropMath.panoramaUv(0, 1, 0)[1], 0)
            && near(BackdropMath.panoramaUv(0, -1, 0)[1], 1) && near(BackdropMath.panoramaUv(0, 0, -1)[0], 0, 1e-9)
            || near(BackdropMath.panoramaUv(0, 0, -1)[0], 1, 1e-9), "panorama");
        // Turning: +90 moves south to west; the lookup of a background turned by r uses turn(d, -r).
        double[] w = BackdropMath.turn(0, 0, 1, 90);
        require(near(w[0], -1) && near(w[2], 0), "turn south to west");
        double[] back = BackdropMath.turn(-1, 0, 0, -90);
        require(near(back[2], 1), "lookup inverse");
        // Gradient: horizon at y = 0, zenith from 0.6, nadir from -0.3; alpha too.
        float[] h = BackdropMath.gradient(0xff0000ff, 0xffff0000, 0x0000ff00, 0);
        float[] t = BackdropMath.gradient(0xff0000ff, 0xffff0000, 0x0000ff00, .7);
        float[] n = BackdropMath.gradient(0xff0000ff, 0xffff0000, 0x0000ff00, -.5);
        require(h[0] == 1 && h[2] == 0 && t[2] == 1 && t[0] == 0 && n[1] == 1 && n[3] == 0 && h[3] == 1, "gradient");
    }

    private static void registration() {
        var id = ResourceLocation.fromNamespaceAndPath("checkbackdrop", "fundo");
        Effects.register(id, new Effects.Definition(40, 4, 0xffffffff, Effects.Shape.NONE, Effects.Material.NONE, null, null));
        expectRejected(() -> Effects.backdrop(id, Backdrop.panorama("a:b.png").weightPhase("nada").build()), "phase without timeline");
        var bd = Backdrop.gradient(0xff000000, 0xff202020, 0xff000000).build();
        Effects.backdrop(id, bd);
        require(VisualKind.byId(VisualKind.localId(id.toString())).backdrop() == bd, "registered");
        expectRejected(() -> Effects.backdrop(id, bd), "duplicate");
        expectRejected(() -> Effects.backdrop(ResourceLocation.fromNamespaceAndPath("checkbackdrop", "nao_registrado"), bd), "unknown effect");
    }

    private static boolean near(double a, double b) { return near(a, b, 1e-9); }
    private static boolean near(double a, double b, double eps) { return Math.abs(a - b) <= eps; }
    private static boolean near(float a, float b, float eps) { return Math.abs(a - b) <= eps; }

    private static void require(boolean ok, String what) {
        if (!ok) throw new AssertionError("BackdropCheck: " + what);
    }

    private static void expectRejected(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("BackdropCheck: expected rejection: " + what);
    }
}
