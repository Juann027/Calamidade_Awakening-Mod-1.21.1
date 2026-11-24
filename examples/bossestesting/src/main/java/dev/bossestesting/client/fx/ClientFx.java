package dev.bossestesting.client.fx;

import cyou.rimuru.rimulib.mc1211.client.api.FilmApi;
import cyou.rimuru.rimulib.mc1211.client.cinematic.Shot;
import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.fx.Fx;
import dev.bossestesting.fx.Variants;
import dev.calamidade_awakening.api.v1.client.AddonLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

import static dev.bossestesting.fx.Fx.*;

/**
 * Client layers of every effect: post passes (telegraph decals, volumetric shockwaves, impact frames, heat,
 * malfunction overlays, energy cores), world meshes (lock-on reticles, shock rings, speed trails, shield bow wave)
 * and the cinematic cameras. Each effect has its own visual identity; phase 1 orange, phase 2 cyan/red.
 */
public final class ClientFx {
    private ClientFx() {}

    static void post(Variants v, Consumer<AddonLayers.Post> layer) { for (ResourceLocation id : v.all()) AddonLayers.post(id, layer); }
    static void world(Variants v, Consumer<AddonLayers.World> layer) { for (ResourceLocation id : v.all()) AddonLayers.world(id, layer); }

    static Vec3 at(AddonLayers.Post p, double right, double up, double fwd) { return p.center().add(p.facing(right, up, fwd)); }

    public static void register() {
        phase1();
        phase2();
        cameras();
    }

    // ================================================================== phase 1

    static void phase1() {
        post(LANDING, p -> {
            var t = p.timeline();
            double since = t.since("impact");
            Passes.impactFrame(p, p.center(), since, 4, 1, false);
            if (since < 0) Passes.circle(p, p.center(), 9, (float) (1 + since / t.tick("impact")), t.value("warn"), 1, P1_HOT, 0xffff2a10);
            else Passes.shock(p, p.center(), t.value("shock_r"), 2.4f, t.value("shock_i") / 3.4f, .55f, t.value("flash"), 1, P1_HOT);
            if (since >= 0) Passes.heat(p, at(p, 0, .2, 0), at(p, 0, 2.5, 0), 7, t.value("heat") * .8f, .3f, P1_EMBER);
            p.bloom(t.value("glow") * .15f + t.value("flash") * .5f);
            if (since >= 0 && since < 12) p.shake((float) (1 - since / 12) * .9f);
            p.zoomBlur(t.value("flash") * .35f);
        });
        world(LANDING, w -> shockRing(w, w.timeline().value("shock_r"), w.timeline().value("shock_i") / 3.4f, P1_HOT, 1.2));

        post(SLAM, p -> {
            var t = p.timeline();
            double since = t.since("impact");
            Passes.impactFrame(p, p.center(), since, 3, .75f, false);
            if (since < 0) Passes.circle(p, p.center(), 8, (float) (1 + since / t.tick("impact")), t.value("warn"), t.value("warn"), P1_HOT, 0xffffd060);
            else Passes.shock(p, p.center(), t.value("shock_r"), 1.8f, t.value("shock_i") / 3.2f, .4f, t.value("flash"), .8f, P1_HOT);
            p.bloom(t.value("flash") * .4f);
            if (since >= 0 && since < 8) p.shake((float) (1 - since / 8) * .6f);
        });
        world(SLAM, w -> shockRing(w, w.timeline().value("shock_r"), w.timeline().value("shock_i") / 3.2f, P1_HOT, .9));

        AddonLayers.post(REACTOR_CHARGE, p -> {
            var t = p.timeline();
            float charge = t.value("charge");
            Passes.core(p, at(p, 0, 0, .5), .55f, charge, t.value("surge") * .4f, P1_CORE, P1_HOT);
            Passes.heat(p, at(p, 0, -.5, .5), at(p, 0, 2.5, .5), 2.2f, charge * .6f, .2f, P1_HOT);
            p.bloom(charge * .2f + t.value("surge") * .15f);
        });
        AddonLayers.post(REACTOR_BEAM, p -> {
            var t = p.timeline();
            float beam = t.value("beam");
            double since = t.since("fire");
            Passes.impactFrame(p, p.center(), since, 2, .55f, false);
            Passes.heat(p, at(p, 0, 0, .5), at(p, 0, 0, 40 * t.value("reveal")), 2.6f, beam * .9f, .25f, P1_HOT);
            p.bloom(beam * .35f + t.value("muzzle") * .1f);
            p.aberration(beam * .25f);
            if (since >= 0 && since < 8) p.shake((float) (1 - since / 8) * .35f);
        });
        post(BEAM_SCAR, p -> Passes.heat(p, p.center(), at(p, 0, 0, 16), 2.2f, p.timeline().value("heat") * .7f, .35f, P1_EMBER));

        for (ResourceLocation id : List.of(PUNCH_R, PUNCH_L)) {
            AddonLayers.world(id, w -> shockRing(w, w.timeline().value("ring_r"), w.timeline().value("hit_i") / 3, P1_HOT, .6));
            AddonLayers.post(id, p -> { p.bloom(p.timeline().value("hit_i") * .08f); p.shake(p.timeline().value("hit_i") * .06f); });
        }

        AddonLayers.world(DASH, w -> {
            float trail = w.timeline().value("trail");
            if (trail <= .02f || w.remainingVertices() < 60) return;
            for (int i = 0; i < 8; i++) {
                double side = (i - 3.5) * .45, up = .6 + (i % 4) * .9, len = 4 + (i * 37 % 5);
                double phase = (w.timeline().localAge() * 1.7 + i * 1.3) % 3;
                Vec3 a = w.facing(side, up, -1 - phase), b = w.facing(side, up, -1 - phase - len);
                w.ribbon(a, b, new Vec3(0, 1, 0), .06, argb(.5f * trail, 0xffd0a0));
            }
        });
        AddonLayers.post(DASH, p -> { if (p.distance() < 18) p.zoomBlur(p.timeline().value("trail") * .12f); });

        AddonLayers.post(STAGGER, p -> Passes.glitch(p, at(p, 0, 0, .5), .25f, p.timeline().value("spark") * .5f, 0, 3, P1_CORE, 0xffff2010));

        AddonLayers.post(FURY, p -> {
            var t = p.timeline();
            Passes.glitch(p, at(p, 0, 2.8, .5), t.value("glitch"), t.value("glitch") * .8f, t.value("rage") * .4f, 4, P1_CORE, 0xffff2a10);
            double since = t.since("roar");
            if (since >= 0) Passes.shock(p, p.center(), t.value("roar_r"), 1.4f, t.value("roar_i") / 3, .9f, 0, .3f, 0xffff5020);
            if (since >= 0 && since < 10) p.shake((float) (1 - since / 10) * .5f);
        });

        AddonLayers.post(TRANSFORM, p -> {
            var t = p.timeline();
            float pulse = t.value("pulse");
            Vec3 reactor = at(p, 0, 2.6, .6);
            double threat = t.since("threat");
            Passes.impactFrame(p, reactor, threat, 6, 1, false);
            Passes.impactFrame(p, reactor, t.since("swap") - 1, 3, .7f, true);
            Passes.glitch(p, reactor, Math.min(1, pulse * .8f + t.weight("dark") * .25f), pulse, t.value("alarm") * .6f, 6, P1_CORE, 0xffff1a10);
            Passes.shock(p, p.center(), t.value("pulse_r"), 1.2f, pulse * .6f, .7f, 0, .4f, P1_HOT);
            p.bloom(pulse * .3f + t.value("release") * .7f + t.value("cyan") * .3f);
            p.desaturate(t.value("dark") * .35f);
            p.vignette(0xff200000, t.value("dark") * .45f + t.value("alarm") * .15f);
            p.shake(pulse * .25f + t.value("knee_i") * .12f);
            p.grain(t.weight("dark") * .25f);
        });
        AddonLayers.world(TRANSFORM, w -> shockRing(w, w.timeline().value("pulse_r"), w.timeline().value("pulse") * .7f, P1_HOT, .7));

        AddonLayers.post(DEATH1, p -> Passes.glitch(p, at(p, 0, 2.4, .6), .2f, p.timeline().value("spark") * .4f, 0, 3, P1_CORE, 0xff200000));
    }

    // ================================================================== phase 2

    static void phase2() {
        AddonLayers.post(REVEAL, p -> {
            var t = p.timeline();
            Vec3 reactor = at(p, 0, 2.8, .6);
            Passes.impactFrame(p, reactor, t.since("ignite"), 4, .9f, true);
            Passes.core(p, reactor, .7f, t.value("ignite") * .8f, .6f, P2_CORE, P2_HOT);
            Passes.shock(p, p.center(), t.value("ring_r"), 2.2f, t.value("ring_i") / 3, .8f, 0, .5f, P2_HOT);
            p.bloom(t.value("ignite") * .3f);
        });
        AddonLayers.world(REVEAL, w -> shockRing(w, w.timeline().value("ring_r"), w.timeline().value("ring_i") / 3, P2_HOT, 1.2));

        post(SLAM_V2, p -> {
            var t = p.timeline();
            double since = t.since("impact");
            Passes.impactFrame(p, p.center(), since, 4, .9f, true);
            if (since < 0) Passes.circle(p, p.center(), 11, (float) (1 + since / t.tick("impact")), t.value("warn"), t.value("warn"), P2_HOT, P2_RED);
            else {
                Passes.shock(p, p.center(), t.value("shock_r"), 2.4f, t.value("shock_i") / 3.6f, .5f, t.value("flash"), .9f, P2_HOT);
                Passes.shock(p, p.center(), t.value("shock2_r"), 1.6f, t.value("shock2_i") / 2.4f, .25f, 0, .4f, P2_RED);
            }
            p.bloom(t.value("flash") * .45f);
            if (since >= 0 && since < 12) p.shake((float) (1 - since / 12) * .8f);
        });
        world(SLAM_V2, w -> {
            shockRing(w, w.timeline().value("shock_r"), w.timeline().value("shock_i") / 3.6f, P2_HOT, 1.2);
            shockRing(w, w.timeline().value("shock2_r"), w.timeline().value("shock2_i") / 2.4f, P2_RED, .8);
        });

        AddonLayers.world(MISSILE_LOCK, w -> {
            float warn = w.timeline().value("warn");
            if (warn <= .02f || w.remainingVertices() < 200) return;
            double age = w.timeline().localAge(), r = 4.2 - 1.2 * Math.min(1, age / 30);
            int col = argb(.75f * warn, 0xff2a3a);
            ring(w, r, .12, .08, col, 40);
            ring(w, r * .55, .06, .08, argb(.5f * warn, 0xff8090), 28);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + age * .08;
                Vec3 o = new Vec3(Math.cos(a) * (r + .6), .1, Math.sin(a) * (r + .6)), in = new Vec3(Math.cos(a) * (r - .4), .1, Math.sin(a) * (r - .4));
                Vec3 side = new Vec3(-Math.sin(a), 0, Math.cos(a));
                w.ribbon(o, in, new Vec3(0, 1, 0).cross(side).normalize(), .18, col);
            }
        });
        post(MISSILE_IMPACT, p -> {
            var t = p.timeline();
            if (p.distance() < 40) Passes.shock(p, p.center(), t.value("ring_r"), .9f, t.value("ring_i") / 2.4f, .6f, t.value("flash"), .6f, 0xffff9a40);
            p.bloom(t.value("flash") * .2f);
            if (p.distance() < 14) p.shake(t.value("flash") * .25f);
        });
        world(MISSILE_IMPACT, w -> shockRing(w, w.timeline().value("ring_r"), w.timeline().value("ring_i") / 2.4f, 0xffff9a40, .5));

        AddonLayers.world(PUNCH_STREAM, w -> shockRing(w, w.timeline().value("ring_r"), w.timeline().value("ring_i") / 2.6f, P2_HOT, .7));
        AddonLayers.post(PUNCH_STREAM, p -> { p.bloom(p.timeline().value("hit") * .06f); p.shake(p.timeline().value("hit") * .05f); });

        AddonLayers.post(EXPOSED_CHARGE, p -> {
            var t = p.timeline();
            float charge = t.value("charge");
            Vec3 core = at(p, 0, 0, .6);
            Passes.core(p, core, .65f, charge * 1.1f, .4f + t.value("surge") * .4f, P2_CORE, P2_HOT);
            Passes.glitch(p, core, t.value("surge") * .25f, charge * .7f, t.value("exposed") * .12f, 5, P2_HOT, P2_RED);
            p.bloom(charge * .25f);
        });
        AddonLayers.post(EXPOSED_BEAM, p -> {
            var t = p.timeline();
            float beam = t.value("beam");
            Passes.impactFrame(p, p.center(), t.since("fire"), 2, .6f, true);
            Passes.heat(p, at(p, 0, 0, .5), at(p, 0, 0, 44 * t.value("reveal")), 3, beam * .9f, .3f, P2_HOT);
            p.bloom(beam * .4f);
            p.aberration(beam * .4f);
        });
        post(EXPOSED_SCAR, p -> Passes.heat(p, p.center(), at(p, 0, 0, 16), 2.2f, p.timeline().value("heat") * .7f, .35f, P2_EMBER));

        AddonLayers.post(OVERLOAD_V2, p -> {
            var t = p.timeline();
            Vec3 core = p.center();
            Passes.impactFrame(p, core, t.since("surge"), 4, .85f, true);
            Passes.glitch(p, core, Math.min(1, t.value("arcs") * .6f), t.value("arcs"), t.value("arcs") * .35f, 6, P2_HOT, P2_RED);
            Passes.shock(p, p.center().add(0, -2.8, 0), t.value("tremor_r"), 1.4f, t.value("tremor_i") / 2.8f, .6f, 0, .6f, P2_HOT);
            p.bloom(t.value("surge") * .2f);
        });
        AddonLayers.post(AURA_OVERLOAD, p -> {
            if (p.distance() > 36 || p.passesLeft() < 2) return;
            float storm = p.timeline().value("storm");
            Passes.heat(p, p.center().add(0, -2.6, 0), p.center().add(0, 2.4, 0), 3.2f, storm * .4f, .15f, P2_HOT);
        });

        post(CHARGED_PUNCH, p -> {
            var t = p.timeline();
            double since = t.since("impact");
            Passes.impactFrame(p, at(p, 0, 1.2, 3), since, 4, 1, true);
            if (since < 0) Passes.lane(p, p.center(), 2.4f, 18, (float) (1 + since / t.tick("impact")), t.value("warn"), P2_HOT, P2_RED);
            else Passes.heat(p, at(p, 0, .5, 2), at(p, 0, .5, 18), 2.4f, t.value("heat"), .4f, P2_EMBER);
            p.bloom(t.value("blast_i") * .15f + t.value("charge") * .08f);
            if (since >= 0 && since < 10) p.shake((float) (1 - since / 10) * .7f);
            p.zoomBlur(t.value("blast_i") * .08f);
        });

        AddonLayers.world(SHIELD_CHARGE, w -> {
            float drive = w.timeline().value("drive");
            if (drive <= .05f || w.remainingVertices() < 120) return;
            for (int i = 0; i < 9; i++) {
                double a0 = -1 + i * .22, a1 = a0 + .22;
                Vec3 p0 = w.facing(Math.sin(a0) * 2.6, 1.6, 2.2 + Math.cos(a0) * .8), p1 = w.facing(Math.sin(a1) * 2.6, 1.6, 2.2 + Math.cos(a1) * .8);
                w.ribbon(p0, p1, new Vec3(0, 1, 0), 2.6 * drive, argb(.28f * Math.min(1, drive), 0x40e8ff));
            }
            for (int i = 0; i < 6; i++) {
                double side = (i - 2.5) * .6;
                double ph = (w.timeline().localAge() * 2.4 + i) % 4;
                w.ribbon(w.facing(side, .4 + (i % 3), -1 - ph), w.facing(side, .4 + (i % 3), -4 - ph), new Vec3(0, 1, 0), .05, argb(.5f * Math.min(1, drive), 0x9af6ff));
            }
        });
        AddonLayers.post(SHIELD_CHARGE, p -> {
            float drive = p.timeline().value("drive");
            if (p.distance() < 20) p.zoomBlur(drive * .06f);
        });
        post(SHIELD_BREACH, p -> { p.shake(p.timeline().value("flash") * .2f); p.bloom(p.timeline().value("flash") * .1f); });

        post(LEAP_SMASH, p -> {
            var t = p.timeline();
            double since = t.since("land");
            Passes.impactFrame(p, p.center(), since, 5, 1, true);
            if (since < 0) Passes.circle(p, p.center(), 12, (float) (1 + since / t.tick("land")), t.value("warn"), 1, P2_HOT, P2_RED);
            else {
                Passes.shock(p, p.center(), t.value("shock_r"), 2.8f, t.value("shock_i") / 3.8f, .6f, t.value("flash"), 1, P2_HOT);
                Passes.heat(p, p.center(), p.center().add(0, 3, 0), 9, t.value("heat") * .6f, .4f, P2_EMBER);
            }
            p.bloom(t.value("flash") * .5f + t.value("column") * .3f);
            if (since >= 0 && since < 14) p.shake((float) (1 - since / 14));
            p.zoomBlur(t.value("flash") * .3f);
        });
        world(LEAP_SMASH, w -> {
            shockRing(w, w.timeline().value("shock_r"), w.timeline().value("shock_i") / 3.8f, P2_HOT, 1.4);
            shockRing(w, w.timeline().value("shock2_r"), w.timeline().value("shock2_i") / 2.4f, P2_RED, .9);
        });

        post(DEATH2, p -> {
            var t = p.timeline();
            Vec3 heart = at(p, 0, 2.4, .8);
            Passes.impactFrame(p, heart, t.since("final"), 6, 1, true);
            Passes.glitch(p, heart, .2f + t.value("heart") * .1f, Math.min(1, t.value("heart") * .4f), 0, 5, P2_HOT, P2_RED);
            Passes.shock(p, p.center(), t.value("final_r"), 2.6f, t.value("final"), .7f, t.value("final"), 1, P2_HOT);
            p.bloom(t.value("final") * .6f + t.value("pod_r") * .08f + t.value("pod_l") * .08f);
            p.shake(t.value("impact") * .2f + t.value("final") * .8f + (t.value("pod_r") + t.value("pod_l")) * .1f);
        });
        world(DEATH2, w -> shockRing(w, w.timeline().value("final_r"), w.timeline().value("final"), P2_HOT, 1.4));
    }

    // ================================================================== meshes

    /** Flat glowing ring on the ground (works even when the post budget is gone, e.g. LOW quality). */
    static void shockRing(AddonLayers.World w, double radius, float strength, int color, double width) {
        if (strength <= .02f || radius <= .2 || w.remainingVertices() < 300) return;
        ring(w, radius, width, .12, argb(Math.min(1, strength) * .8f, color & 0xffffff), 48);
        ring(w, radius * .97, width * 2.5, .1, argb(Math.min(1, strength) * .25f, color & 0xffffff), 48);
    }

    static void ring(AddonLayers.World w, double radius, double width, double y, int color, int segments) {
        double inner = Math.max(0, radius - width / 2), outer = radius + width / 2;
        for (int i = 0; i < segments; i++) {
            double a = i * Math.TAU / segments, b = (i + 1) * Math.TAU / segments;
            Vec3 o0 = new Vec3(Math.cos(a) * outer, y, Math.sin(a) * outer), o1 = new Vec3(Math.cos(b) * outer, y, Math.sin(b) * outer);
            Vec3 i0 = new Vec3(Math.cos(a) * inner, y, Math.sin(a) * inner), i1 = new Vec3(Math.cos(b) * inner, y, Math.sin(b) * inner);
            w.triangle(o0, o1, i1, color);
            w.triangle(o0, i1, i0, color);
        }
    }

    static int argb(float alpha, int rgb) { return (Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xffffff); }

    // ================================================================== cameras

    /** The boss nearest to an instance center on this client, for framing. */
    static GuilherminoBoss boss(Vec3 center) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        return level.getEntitiesOfClass(GuilherminoBoss.class, new AABB(center, center).inflate(48, 64, 48)).stream()
            .min(java.util.Comparator.comparingDouble(b -> b.position().distanceToSqr(center))).orElse(null);
    }

    static Vec3 bossAt(Vec3 center, float partial, double up) {
        GuilherminoBoss b = boss(center);
        return b == null ? center.add(0, up, 0) : b.getPosition(partial).add(0, up, 0);
    }

    static Vec3 fwd(Vec3 center) {
        GuilherminoBoss b = boss(center);
        double yaw = Math.toRadians(b == null ? 0 : b.getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    static void cameras() {
        AddonLayers.film(CAM_LANDING, film -> {
            var t = film.timeline();
            Vec3 c = film.center();
            int prep = t.tick("prep"), impact = t.tick("impact"), rise = t.tick("rise"), end = film.duration();
            Vec3 f = fwd(c), side = new Vec3(-f.z, 0, f.x);
            return List.<Shot>of(
                // Worm's-eye view: the machine falls out of the orange sky, thrusters burning.
                FilmApi.cut(0, prep, (s, partial, k) -> FilmApi.pose(c.add(f.scale(13)).add(side.scale(4)).add(0, 1.2, 0), bossAt(c, partial, 2.5), 72f - 10f * (float) k)),
                // The brake: closer, the jets hammer the ground.
                FilmApi.cut(prep, impact, (s, partial, k) -> FilmApi.pose(c.add(f.scale(9 - 2 * k)).add(side.scale(-5)).add(0, 2, 0), bossAt(c, partial, 1.5), 58f)),
                // Superhero landing: low and wide, the shockwave runs at the lens.
                FilmApi.cut(impact, rise, (s, partial, k) -> FilmApi.pose(c.add(f.scale(7.5 + 2.5 * k)).add(side.scale(2.5)).add(0, .8 + .6 * k, 0), c.add(0, 2.2, 0), 78f - 8f * (float) k)),
                // It rises: slow orbit to a hero shot.
                FilmApi.sweep(rise, end, 12, (s, partial, k) -> FilmApi.pose(c.add(f.scale(10).add(side.scale(-6 + 10 * k))).add(0, 1.5 + k, 0), bossAt(c, partial, 3.2), 62f)));
        });
        AddonLayers.film(CAM_TRANSFORM, film -> {
            var t = film.timeline();
            Vec3 c = film.center();
            int knee = t.tick("knee"), pause = t.tick("pause"), stand = t.tick("stand"), threat = t.tick("threat"), end = film.duration();
            Vec3 f = fwd(c), side = new Vec3(-f.z, 0, f.x);
            return List.<Shot>of(
                FilmApi.cut(0, knee, (s, partial, k) -> FilmApi.pose(c.add(f.scale(10)).add(side.scale(3)).add(0, 2.5, 0), c.add(0, 2.6, 0), 60f)),
                // The pulses: tight on the cabin and the failing reactor (the pilot is visible behind the glass).
                FilmApi.cut(knee, pause, (s, partial, k) -> FilmApi.pose(c.add(f.scale(5.5 - 1.5 * k)).add(side.scale(1.2)).add(0, 2.4, 0), c.add(0, 2.8, 0), 48f - 6f * (float) k)),
                // Darkness: wide low angle while the machine gets up without its orange heart.
                FilmApi.cut(pause, stand, (s, partial, k) -> FilmApi.pose(c.add(f.scale(12)).add(side.scale(-5)).add(0, .6, 0), bossAt(c, partial, 3), 64f)),
                FilmApi.cut(stand, threat, (s, partial, k) -> FilmApi.pose(c.add(f.scale(6.5)).add(side.scale(-1)).add(0, .7, 0), c.add(0, 3.6, 0), 72f)),
                // Release and reveal: pull back as the cyan reactor ignites.
                FilmApi.cut(threat, end, (s, partial, k) -> FilmApi.pose(c.add(f.scale(7 + 5 * k)).add(side.scale(2)).add(0, 1.4 + k, 0), c.add(0, 2.8, 0), 70f - 6f * (float) k)));
        });
        AddonLayers.film(CAM_DEATH, film -> {
            var t = film.timeline();
            Vec3 c = film.center();
            int torso = t.tick("torso"), head = t.tick("head"), ground = t.tick("head_ground"), fin = t.tick("final"), end = film.duration();
            Vec3 f = fwd(c), side = new Vec3(-f.z, 0, f.x);
            return List.<Shot>of(
                FilmApi.cut(0, torso, (s, partial, k) -> FilmApi.pose(c.add(side.scale(9)).add(f.scale(3)).add(0, 2.2, 0), c.add(0, 2.6, 0), 60f)),
                FilmApi.cut(torso, ground, (s, partial, k) -> FilmApi.pose(c.add(f.scale(8)).add(side.scale(-3)).add(0, 1.6, 0), c.add(f.scale(2.5 + 2 * k)).add(0, 1.2, 0), 56f)),
                FilmApi.sweep(ground, fin, 10, (s, partial, k) -> FilmApi.pose(c.add(f.scale(9).add(side.scale(-4 + 8 * k))).add(0, 3 + k, 0), c.add(f.scale(2)).add(0, 1, 0), 60f)),
                FilmApi.cut(fin, end, (s, partial, k) -> FilmApi.pose(c.add(f.scale(14 + 4 * k)).add(side.scale(4)).add(0, 6 + 2 * k, 0), c.add(0, 1, 0), 68f)));
        });
    }
}
