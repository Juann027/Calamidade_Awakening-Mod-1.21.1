package dev.calamidade_awakening.client.geometry;

import dev.calamidade_awakening.api.Quality;
import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.client.StageScene;
import net.minecraft.world.phys.Vec3;

/** Composable procedural primitives sampled from the shared deterministic timeline. */
public final class GeometryEffects {
    private static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);
    private static final Color WHITE = new Color(1, 1, 1, .9f);
    private GeometryEffects() {}

    public static void render(Frame frame, StageScene.Sample sample, Quality quality) {
        VisualKind kind = sample.kind();
        if (kind.geometry == VisualKind.Geometry.NONE) return;
        Vec3 center = frame.camera().add(sample.relative());
        double age = sample.progress();
        double opacity = sample.opacity();
        double radius = kind.radius;
        Color tint = argb(kind.color).fade(opacity);
        Color accent = tint.mix(new Color(.7f, .25f, 1f, tint.a()), .45);
        int detail = sample.distance() > 48 ? Math.max(12, quality.segments / 2) : quality.segments;
        int particles = sample.distance() > 48 ? quality.particles / 2 : quality.particles;
        if (kind.geometry == VisualKind.Geometry.NEXUS) {
            horizonCore(frame, center, radius, age, sample.seed(), detail, tint, accent);
            return;
        }
        int rings = switch (kind.geometry) {
            case PRISM, ORBIT -> 3;
            case RINGS -> 2;
            default -> 0;
        };
        Mesh mesh = frame.mesh();
        double charge = Timeline.smooth(Timeline.progress(age, 0, .4));
        for (int i = 0; i < rings && mesh.remaining() >= 12; i++) {
            double turn = age * 5 + i * Math.PI / Math.max(1, rings) + sample.seed() * Math.TAU;
            Vec3 u = X.scale(Math.cos(turn)).add(Z.scale(Math.sin(turn)));
            Vec3 v = Y.scale(.75).add(u.cross(Y).scale(.66)).normalize();
            mesh.ring(center, u, v, radius * (.23 + charge * .28 + i * .035),
                .15 + i * .024, detail, tint.mix(accent, i / (double)Math.max(1, rings - 1)));
        }
        if (kind.geometry == VisualKind.Geometry.PRISM) {
            mesh.billboard(center, frame.right(), frame.up(), .3 + charge * 1.1, tint);
            mesh.billboard(center, frame.right(), frame.up(), .15 + charge * .42, WHITE.fade(opacity));
        }
        for (int i = 0; i < particles && mesh.remaining() >= 18; i++) {
            double phase = hash(sample.seed(), i) * Math.TAU + age * (4 + i % 4);
            double r = radius * (.7 - .3 * charge + Math.max(0, age - .7));
            double height = (hash(sample.seed(), i + 71) - .5) * radius * .65;
            Vec3 p = center.add(Math.cos(phase) * r, height, Math.sin(phase) * r);
            mesh.billboard(p, frame.right(), frame.up(), .09 + .15 * charge, tint.mix(accent, hash(sample.seed(), i + 131)));
            if (kind.geometry == VisualKind.Geometry.ORBIT) {
                Vec3 last = center.add(Math.cos(phase - .09) * r, height, Math.sin(phase - .09) * r);
                mesh.ribbon(p, last, frame.up(), .07, accent);
            }
        }
        double onset = kind == VisualKind.PRISM ? .10 : .58;
        double release = Timeline.envelope(age, onset, onset + .06, onset + .15, onset + .34);
        if (release > .001 && kind.geometry != VisualKind.Geometry.RINGS) {
            mesh.beam(center, center.add(0, radius * 1.8, 0), .25 + .32 * charge,
                Math.max(12, detail / 2), tint.fade(release));
            Vec3 ground = center.subtract(0, 2, 0);
            for (int i = 0; i < 3; i++) {
                double p = Timeline.progress(age, onset + .01 + i * .06, onset + .24 + i * .06);
                if (p > 0 && p < 1) mesh.ring(ground, X, Z, radius * (.1 + p * 1.8), .4,
                    detail, tint.fade((1 - p) * release));
            }
        }

    }

    private static void horizonCore(Frame frame, Vec3 center, double radius, double age,
                                    float seed, int detail, Color tint, Color accent) {
        Mesh mesh = frame.mesh();
        double collapse = Timeline.smooth(Timeline.progress(age, .36, .59));
        double formation = Timeline.smooth(Timeline.progress(age, 0, .19));
        double wake = 1 - Timeline.smooth(Timeline.progress(age, .60, .82));
        int segments = Math.max(10, detail / 2);
        for (int blade = 0; blade < 4 && age < .82; blade++) {
            Vec3 previous = center;
            for (int i = 0; i <= segments; i++) {
                double u = i / (double) segments;
                double angle = seed * Math.TAU + blade * Math.PI / 2 + age * 4 + u * 2.8;
                double r = radius * u * .83 * formation * (1 - .96 * collapse);
                Vec3 point = center.add(r * Math.cos(angle), Math.sin(u * Math.PI) * r * .55, r * Math.sin(angle));
                if (i > 0) mesh.ribbon(previous, point, Y, (.09 + .38 * Math.sin(u*Math.PI)) * formation,
                    tint.fade(wake));
                previous = point;
            }
        }
        double slit = Timeline.envelope(age, .38, .58, .61, .70);
        if (slit > .001) {
            Vec3 axis = new Vec3(Math.cos(seed*Math.TAU), 0, Math.sin(seed*Math.TAU)).scale(1.8 + slit);
            mesh.ribbon(center.subtract(axis), center.add(axis), Y, .14, WHITE.fade(slit));
        }
        if (age >= .60 && age < .89) {
            double t = Timeline.progress(age, .60, .89);
            mesh.ring(center, X, Z, radius*(.04+t), .18+.25*t, detail, accent.fade(1-t));
        }
    }

    private static Color argb(int color) {
        return new Color(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f,
            (color & 255) / 255f, ((color >>> 24) & 255) / 255f);
    }
    private static double hash(double seed, int index) {
        double n = Math.sin(seed * 972.3 + index * 127.1) * 43758.5453;
        return n - Math.floor(n);
    }
}
