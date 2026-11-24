package dev.bossestesting.fx;

import dev.bossestesting.ai.Brain;
import dev.bossestesting.anim.Clip;
import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.TerrainShape;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;

import java.util.ArrayList;
import java.util.List;

import static dev.bossestesting.anim.Anims.*;
import static dev.bossestesting.fx.Fx.*;
import static dev.bossestesting.fx.FxP1.craterRing;
import static dev.bossestesting.fx.FxP1.hot;
import static dev.bossestesting.fx.FxP1.rays;

/** Phase 2 effects (Finale: unstable cyan energy with red contrast). */
final class FxP2 {
    private FxP2() {}

    static void register() {
        reveal();
        slamV2();
        barrage();
        missileImpact();
        punchStream();
        exposedReactor();
        overload();
        auras();
        chargedPunch();
        shieldCharge();
        leapSmash();
        death();
        int dc = finalBlast() + 24;
        Effects.register(CAM_DEATH, def(dc, 4, P2_HOT));
        Effects.timeline(CAM_DEATH, Timeline.builder(dc).marker("torso", t(P2_DEATH, "torso_breaks_off")).marker("head", t(P2_DEATH, "head_ejected"))
            .marker("head_ground", t(P2_DEATH, "head_ground_impact")).marker("final", finalBlast()).build());
    }

    /** Radial energy fissures racing over the ground (cyan, a red one between). */
    static Beam[] fissures(int n, double reach, String reveal, String intensity) {
        Beam[] out = new Beam[n];
        for (int i = 0; i < n; i++) {
            double a = i * Math.TAU / n + .2 * Math.sin(i * 4.1);
            boolean red = i % 3 == 2;
            out[i] = Beam.builder(Point.center(0, .15, 0), Point.center(Math.cos(a) * reach, .2, Math.sin(a) * reach))
                .width(.1f, .9f).color(red ? 0xffffd0d0 : P2_CORE, red ? P2_RED : P2_HOT).intensity(1.8f).noise(.55f).wave(.3f, 1.6f, 3)
                .track(Beam.Param.REVEAL, reveal).track(Beam.Param.INTENSITY, intensity).build();
        }
        return out;
    }

    // ------------------------------------------------------------------ reveal of the second form

    static void reveal() {
        int dur = 90;
        Timeline tl = Timeline.builder(dur)
            .marker("ignite", 6)
            .track("ignite", Kf.of(0, 0).k(6, 2.4f, Easing.OVERSHOOT).k(40, .8f).k(dur, 0).build())
            .track("ring_r", Kf.of(6, 1).k(40, 26, Easing.EASE_OUT).build())
            .track("ring_i", new Kf().spike(6, 3, 34).build())
            .track("spikes", Kf.of(5, 0).k(9, 1, Easing.EASE_OUT).build())
            .track("spikes_i", Kf.of(5, 0).k(6, 2.2f).k(30, 0, Easing.EASE_IN).build())
            .track("weight", Kf.of(0, 0).k(6, 1).k(dur, 0).build())
            .build();
        Effects.register(REVEAL, new Effects.Definition(dur, 30, P2_HOT, Effects.Shape.NONE, Effects.Material.NONE, null, null,
            new Effects.Impact(6, 6, .45f, Effects.ImpactStyle.MONOCHROME), 192, Effects.Priority.HERO));
        Effects.timeline(REVEAL, tl);
        Effects.atmosphere(REVEAL, Atmosphere.builder().ambient(0xff40e8ff, .28f).exposure(1.2f).track(Atmosphere.Param.WEIGHT, "weight").build());
        List<Beam> spikes = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            double a = i * Math.TAU / 9, el = .35 + .5 * ((i * 7) % 5) / 5.0;
            spikes.add(Beam.builder(Point.facing(0, 2.8, .5), Point.facing(Math.cos(a) * 12 * (1 - el), 2.8 + 12 * el, Math.sin(a) * 12 * (1 - el)))
                .width(.12f, 1.2f).color(P2_CORE, i % 3 == 0 ? P2_RED : P2_HOT).intensity(2).noise(.4f)
                .track(Beam.Param.REVEAL, "spikes").track(Beam.Param.INTENSITY, "spikes_i").build());
        }
        Effects.beams(REVEAL, spikes.toArray(Beam[]::new));
        Effects.lights(REVEAL, Light.point(Point.facing(0, 2.8, .6)).color(P2_HOT).radius(16).falloff(1.3f).track(Light.Param.INTENSITY, "ignite").build(),
            Light.ring(Point.center(), 1, 3).color(P2_HOT).height(5).track(Light.Param.RADIUS, "ring_r").track(Light.Param.INTENSITY, "ring_i").build(),
            Light.point(Point.facing(0, 3.4, .2)).color(P2_RED).radius(8).flicker(.6f, 6).track(Light.Param.INTENSITY, "weight").build());
    }

    // ------------------------------------------------------------------ ground slam v2

    static void slamV2() {
        Clip c = P2_GROUNDSLAM_V2;
        int peak = t(c, "windup_peak"), impact = t(c, "impact"), ring = impact + 7, dur = len(c, 60);
        Timeline tl = Timeline.builder(dur)
            .marker("impact", impact).marker("ring", ring)
            .track("warn", Kf.of(0, 0).k(t(c, "left_arm_online"), .4f).k(peak, 1, Easing.EASE_IN).k(impact, 1).k(impact + 2, 0).build())
            .track("shock_r", Kf.of(impact, 1).k(impact + 32, 26, Easing.EASE_OUT).build())
            .track("shock_i", Kf.of(impact - 1, 0).k(impact, 3.6f).k(impact + 32, 0, Easing.EASE_IN).build())
            .track("shock2_r", Kf.of(ring, 2).k(ring + 34, 32, Easing.EASE_OUT).build())
            .track("shock2_i", Kf.of(ring - 1, 0).k(ring, 2.4f).k(ring + 34, 0, Easing.EASE_IN).build())
            .track("flash", new Kf().spike(impact, 1, 8).build())
            .track("fissure", Kf.of(impact, 0).k(ring, 1, Easing.EASE_OUT).build())
            .track("fissure_i", Kf.of(impact - 1, 0).k(impact, 2.2f).k(ring + 30, 0, Easing.EASE_IN).build())
            .track("heat", Kf.of(impact - 1, 0).k(impact, 1).k(dur, 0, Easing.EASE_IN).build())
            .build();
        SLAM_V2.register(id -> {
            Effects.register(id, hero(dur, 36, P2_HOT, impact, 6, .35f));
            Effects.timeline(id, tl);
            List<Beam> beams = new ArrayList<>(List.of(fissures(9, 17, "fissure", "fissure_i")));
            beams.addAll(List.of(rays(5, 10, 3, P2_CORE, P2_HOT, "fissure", "fissure_i")));
            Effects.beams(id, beams.toArray(Beam[]::new));
            Effects.lights(id,
                Light.ring(Point.center(), 1, 3.5f).color(P2_HOT).height(5).track(Light.Param.RADIUS, "shock_r").track(Light.Param.INTENSITY, "shock_i").build(),
                Light.ring(Point.center(), 2, 2.5f).color(P2_RED).height(4).track(Light.Param.RADIUS, "shock2_r").track(Light.Param.INTENSITY, "shock2_i").build(),
                Light.disc(Point.center(0, .4, 0), 9).color(P2_CORE).intensity(3).track(Light.Param.INTENSITY, "flash").build(),
                Light.point(Point.center(0, .9, 0)).color(P2_EMBER).radius(10).intensity(1.6f).flicker(.35f, 8).track(Light.Param.INTENSITY, "heat").build(),
                Light.point(Point.center(0, .5, 0)).color(P2_RED).radius(6).intensity(.9f).flicker(.6f, 4).track(Light.Param.INTENSITY, "heat").build());
        },
            Variants.edits(TerrainEdit.carve(TerrainShape.crater(5, 2).rough(.8f, 3)).commitAt("impact").glow(P2_EMBER).cooling(240).build()),
            Variants.edits(
                hot(TerrainEdit.carve(TerrainShape.crater(9.5f, 3.9f).rough(1.5f, 4)).commitAt("impact").burn(Fx.FUSED, 1), P2_EMBER, 600, .8f, 1).build(),
                hot(TerrainEdit.carve(craterRing(7, 15.5f, 3, 1.5f, .25f).rough(1, 2)).commitAt("ring"), P2_EMBER, 360, .6f, .8f).build()),
            Variants.edits(
                hot(TerrainEdit.carve(TerrainShape.crater(14, 5.4f).profile(2.4f).rough(2.2f, 5)).commitAt("impact").burn(Fx.FUSED, 1), P2_EMBER, 900, .9f, 1).build(),
                hot(TerrainEdit.carve(craterRing(8, 21, 4.2f, 2, .25f).rough(1.4f, 3)).commitAt("ring").burn(Fx.FUSED, 1), P2_EMBER, 500, .7f, .9f).build()));
    }

    // ------------------------------------------------------------------ missile barrage (pods) + lock-on + impacts

    /** Salvo events in order, alternating right and left pods. */
    static final String[] SALVOS = {"salvo_1_right", "salvo_2_left", "salvo_3_right", "salvo_4_left", "salvo_5_right", "salvo_6_left"};

    static void barrage() {
        Clip c = P2_MISSILE_BARRAGE;
        int open = t(c, "pods_opening"), opened = t(c, "pods_open"), lowering = t(c, "racks_lowering"), closed = t(c, "pods_closed");
        int dur = len(c, 6);
        Kf right = new Kf(), left = new Kf();
        for (String s : SALVOS) (s.endsWith("right") ? right : left).spike(t(c, s), 3, 5);
        Timeline tl = Timeline.builder(dur)
            .track("pods", Kf.of(open, 0).k(opened, 1, Easing.EASE_OUT).k(lowering, 1).k(closed, 0).build())
            .track("muzzle_r", right.build())
            .track("muzzle_l", left.build())
            .track("kneel", new Kf().spike(t(c, "kneel_impact"), 2, 12).build())
            .build();
        Effects.register(BARRAGE, def(dur, 12, P2_HOT).withPriority(Effects.Priority.HERO));
        Effects.timeline(BARRAGE, tl);
        Effects.beams(BARRAGE,
            Beam.builder(Point.socket("rack_r", 0, 0, 0), Point.socket("rack_r", 0, 2.4, 0)).width(.3f, 1.6f).color(0xfffff0c0, 0xffff8a2a).intensity(2)
                .noise(.15f).track(Beam.Param.INTENSITY, "muzzle_r").build(),
            Beam.builder(Point.socket("rack_l", 0, 0, 0), Point.socket("rack_l", 0, 2.4, 0)).width(.3f, 1.6f).color(0xfffff0c0, 0xffff8a2a).intensity(2)
                .noise(.15f).track(Beam.Param.INTENSITY, "muzzle_l").build());
        Effects.lights(BARRAGE,
            Light.point(Point.socket("rack_r", 0, .6, 0)).color(0xffffa040).radius(9).track(Light.Param.INTENSITY, "muzzle_r").build(),
            Light.point(Point.socket("rack_l", 0, .6, 0)).color(0xffffa040).radius(9).track(Light.Param.INTENSITY, "muzzle_l").build(),
            Light.point(Point.socket("rack_r", 0, .2, 0)).color(P2_RED).radius(4).intensity(1.4f).flicker(.5f, 8).track(Light.Param.INTENSITY, "pods").build(),
            Light.point(Point.socket("rack_l", 0, .2, 0)).color(P2_RED).radius(4).intensity(1.4f).flicker(.5f, 8).track(Light.Param.INTENSITY, "pods").build(),
            Light.disc(Point.center(0, .3, 0), 5).color(0xffd0f0ff).track(Light.Param.INTENSITY, "kneel").build());

        // Lock-on over each targeted player: a red reticle on the ground while the salvos are in the air.
        int lock = 80;
        Effects.register(MISSILE_LOCK, def(lock, 7, P2_RED));
        Effects.timeline(MISSILE_LOCK, Timeline.builder(lock)
            .track("warn", Kf.of(0, 0).k(8, 1, Easing.EASE_OUT).k(lock - 16, 1).k(lock, 0).build()).build());
    }

    static void missileImpact() {
        int dur = 56;
        Timeline tl = Timeline.builder(dur)
            .marker("boom", 1)
            .track("flash", new Kf().spike(1, 1, 7).build())
            .track("fire", Kf.of(0, 0).k(1, 3).k(10, 1.4f).k(36, 0, Easing.EASE_IN).build())
            .track("ring_r", Kf.of(1, .5f).k(15, 8, Easing.EASE_OUT).build())
            .track("ring_i", new Kf().spike(1, 2.4f, 14).build())
            .build();
        MISSILE_IMPACT.register(id -> {
            Effects.register(id, def(dur, 6, 0xffff8a2a));
            Effects.timeline(id, tl);
            Effects.lights(id, Light.point(Point.center(0, 1.2, 0)).color(0xffff9a40).radius(10).falloff(1.4f).flicker(.3f, 9).track(Light.Param.INTENSITY, "fire").build(),
                Light.ring(Point.center(), 1, 1.6f).color(P2_HOT).height(3).track(Light.Param.RADIUS, "ring_r").track(Light.Param.INTENSITY, "ring_i").build());
        },
            Variants.edits(TerrainEdit.carve(TerrainShape.crater(2.2f, 1)).commitAt("boom").glow(0xffff7a20).cooling(160).smoke(.6f).build()),
            Variants.edits(TerrainEdit.carve(TerrainShape.crater(3.2f, 1.6f).rough(.6f, 2)).commitAt("boom").glow(0xffff7a20).cooling(260).smoke(.8f).build()),
            Variants.edits(hot(TerrainEdit.carve(TerrainShape.crater(4.6f, 2.3f).rough(.9f, 2)).commitAt("boom").burn(Fx.SCORCHED, 1), 0xffff7a20, 360, .9f, .8f).build()));
    }

    // ------------------------------------------------------------------ punch stream

    static final String[] HITS = {"hit_1", "hit_2", "hit_3", "hit_4", "hit_5_heavy"};

    static void punchStream() {
        Clip c = P2_PUNCH_STREAM;
        int dur = len(c, 6);
        Kf hits = new Kf(), ring = new Kf();
        for (String h : HITS) {
            boolean heavy = h.endsWith("heavy");
            hits.spike(t(c, h), heavy ? 3.4f : 2, heavy ? 10 : 4);
        }
        int heavy = t(c, "hit_5_heavy");
        ring.k(heavy, 1).k(heavy + 14, 12, Easing.EASE_OUT);
        Timeline tl = Timeline.builder(dur)
            .track("hit", hits.build())
            .track("ring_r", ring.build())
            .track("ring_i", new Kf().spike(heavy, 2.6f, 14).build())
            .build();
        Effects.register(PUNCH_STREAM, def(dur, 9, P2_HOT));
        Effects.timeline(PUNCH_STREAM, tl);
        Effects.lights(PUNCH_STREAM,
            Light.point(Point.socket("hand_r", 0, 0, .4)).color(P2_HOT).radius(6).track(Light.Param.INTENSITY, "hit").build(),
            Light.point(Point.socket("hand_l", 0, 0, .4)).color(P2_RED).radius(5).track(Light.Param.INTENSITY, "hit").build(),
            Light.ring(Point.facing(0, 0, 3), 1, 2).color(P2_HOT).height(3).track(Light.Param.RADIUS, "ring_r").track(Light.Param.INTENSITY, "ring_i").build());
    }

    // ------------------------------------------------------------------ exposed reactor charge → fire (+ scar)

    static void exposedReactor() {
        Clip c = P2_EXPOSED_REACTOR_CHARGE;
        int exposed = t(c, "chest_exposed"), ready = t(c, "ready_to_fire"), dur = ready + 2;
        Timeline charge = Timeline.builder(dur)
            .track("charge", Kf.of(0, 0).k(exposed, .25f).k(t(c, "surge_1"), .45f).k(t(c, "surge_2"), .65f).k(t(c, "surge_3"), .85f)
                .k(t(c, "full_charge"), 1).k(dur, 1.2f).build())
            .track("surge", new Kf().spike(t(c, "surge_1"), 1.2f, 5).spike(t(c, "surge_2"), 1.3f, 5).spike(t(c, "surge_3"), 1.5f, 5)
                .spike(t(c, "full_charge"), 2, 4).build())
            .track("exposed", Kf.of(exposed - 2, 0).k(exposed, 1.6f).k(dur, 1.6f).build())
            .build();
        Effects.register(EXPOSED_CHARGE, def(dur, 7, P2_HOT).withPriority(Effects.Priority.HERO));
        Effects.timeline(EXPOSED_CHARGE, charge);
        Effects.anchorSocket(EXPOSED_CHARGE, "reactor");
        Effects.beams(EXPOSED_CHARGE, FxP1.inflow(12, 5, P2_CORE, P2_HOT, .45f));
        Effects.lights(EXPOSED_CHARGE,
            Light.point(Point.center(0, 0, .5)).color(P2_HOT).radius(11).intensity(3).flicker(.35f, 14).track(Light.Param.INTENSITY, "charge").build(),
            Light.point(Point.center(0, 0, .7)).color(P2_CORE).radius(6).intensity(2.6f).track(Light.Param.INTENSITY, "surge").build(),
            Light.point(Point.center(0, .4, .9)).color(P2_RED).radius(5).flicker(.7f, 7).track(Light.Param.INTENSITY, "exposed").build());

        Clip f = P2_EXPOSED_REACTOR_FIRE;
        int fire = t(f, "fire"), spent = t(f, "heart_spent"), bd = len(f, 4);
        Kf jitter = Kf.of(fire - 1, 0).k(fire, 1.5f);
        for (int k = fire + 1; k < spent + 4; k++) jitter.k(k, (k * 37 % 7) / 7f * .7f + .7f);
        jitter.k(spent + 10, 0, Easing.EASE_IN);
        Timeline beam = Timeline.builder(bd)
            .marker("fire", fire)
            .track("muzzle", Kf.of(0, 0).k(fire - 1, 1.6f, Easing.EASE_IN).k(fire + 1, 3.4f).k(spent + 8, 0).build())
            .track("reveal", Kf.of(fire - 1, 0).k(fire + 2, 1, Easing.EASE_OUT).build())
            .track("beam", jitter.build())
            .track("core_w", Kf.of(fire, 1.4f).k(fire + 3, .85f).k(spent, .7f).k(spent + 10, .05f).build())
            .build();
        Effects.register(EXPOSED_BEAM, hero(bd, 28, P2_HOT, fire, 4, .3f));
        Effects.timeline(EXPOSED_BEAM, beam);
        Beam core = Beam.builder(Point.facing(0, 0, .3), Point.facing(0, 0, 52)).width(.75f, 1.8f).color(P2_CORE, P2_HOT).intensity(2.6f)
            .track(Beam.Param.REVEAL, "reveal").track(Beam.Param.INTENSITY, "beam").track(Beam.Param.CORE_WIDTH, "core_w").build();
        Effects.beams(EXPOSED_BEAM, core,
            Beam.builder(Point.facing(0, 0, .3), Point.facing(0, 0, 52)).width(.2f, 3.8f).color(0xffffd8d8, P2_RED).intensity(1.3f)
                .noise(.55f).wave(.45f, 4, 2.5f).pulses(10, 4, 2.4f).track(Beam.Param.REVEAL, "reveal").track(Beam.Param.INTENSITY, "beam").build(),
            Beam.builder(Point.facing(0, 0, .3), Point.facing(0, 0, 52)).width(.08f, .55f).color(P2_CORE, P2_HOT).intensity(1.8f)
                .wave(.8f, 4.2f, 2.8f).noise(.12f).track(Beam.Param.REVEAL, "reveal").track(Beam.Param.INTENSITY, "beam").build(),
            Beam.builder(Point.facing(0, 0, .3), Point.facing(0, 0, 52)).width(.08f, .55f).color(P2_CORE, P2_HOT).intensity(1.8f)
                .wave(.7f, 5.6f, -3.1f).noise(.12f).track(Beam.Param.REVEAL, "reveal").track(Beam.Param.INTENSITY, "beam").build(),
            Beam.builder(Point.facing(0, 0, .3), Point.facing(0, 0, 50)).width(0, 8).color(P2_DEEP, 0xff600010).intensity(.5f)
                .track(Beam.Param.REVEAL, "reveal").track(Beam.Param.INTENSITY, "beam").build());
        Effects.lights(EXPOSED_BEAM, Light.along(core).radius(7).intensity(2.6f).build(),
            Light.point(Point.facing(0, 0, .6)).color(P2_HOT).radius(10).track(Light.Param.INTENSITY, "muzzle").build(),
            Light.point(Point.facing(0, .5, .8)).color(P2_RED).radius(6).flicker(.8f, 10).track(Light.Param.INTENSITY, "muzzle").build());

        FxP1.scar(EXPOSED_SCAR, P2_HOT, P2_EMBER, Fx.FUSED);
    }

    // ------------------------------------------------------------------ overload_v2 (enters the Overloaded state) and vent

    static void overload() {
        Clip c = P2_OVERLOAD_V2;
        int surge = t(c, "reactor_surge"), tremor = t(c, "tremor_peak"), stable = t(c, "stabilized"), dur = len(c, 10);
        Timeline tl = Timeline.builder(dur)
            .marker("surge", surge)
            .track("windup", Kf.of(0, 0).k(t(c, "windup_peak"), 1, Easing.EASE_IN).k(surge, 0).build())
            .track("surge", Kf.of(surge - 1, 0).k(surge, 3.2f, Easing.OVERSHOOT).k(stable, 1).k(dur, 0).build())
            .track("arcs", Kf.of(surge - 1, 0).k(surge, 1.8f).k(tremor, 1.4f).k(stable, .6f).k(dur, 0).build())
            .track("tremor_r", Kf.of(tremor, 1).k(tremor + 22, 18, Easing.EASE_OUT).build())
            .track("tremor_i", new Kf().spike(tremor, 2.8f, 22).build())
            .track("flash", Kf.of(surge - 1, 0).k(surge, 1).k(surge + 30, 0, Easing.EASE_IN).build())
            .build();
        Effects.register(OVERLOAD_V2, new Effects.Definition(dur, 24, P2_HOT, Effects.Shape.NONE, Effects.Material.NONE, null, null,
            new Effects.Impact(surge, 6, .5f, Effects.ImpactStyle.MONOCHROME), 192, Effects.Priority.HERO));
        Effects.timeline(OVERLOAD_V2, tl);
        Effects.anchorSocket(OVERLOAD_V2, "reactor");
        Effects.atmosphere(OVERLOAD_V2, Atmosphere.builder().ambient(0xff50f0ff, .25f).exposure(1.25f).sky(0xff020c12, 0xff10606a, .3f)
            .track(Atmosphere.Param.WEIGHT, "flash").build());
        List<Beam> arcs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.TAU / 8 + .3, y = (i % 3 - 1) * 1.6;
            arcs.add(Beam.builder(Point.center(0, 0, .5), Point.center(Math.cos(a) * 7, y - 1.5, Math.sin(a) * 7)).width(.05f, .5f)
                .color(0xffffffff, i % 3 == 0 ? P2_RED : P2_HOT).intensity(2.2f).noise(.7f).wave(.45f, .8f, 5)
                .track(Beam.Param.INTENSITY, "arcs").build());
        }
        Effects.beams(OVERLOAD_V2, arcs.toArray(Beam[]::new));
        Effects.lights(OVERLOAD_V2,
            Light.point(Point.center(0, 0, .5)).color(P2_HOT).radius(18).falloff(1.2f).flicker(.3f, 16).track(Light.Param.INTENSITY, "surge").build(),
            Light.point(Point.center(0, .5, .6)).color(P2_CORE).radius(5).track(Light.Param.INTENSITY, "windup").build(),
            Light.point(Point.center(2.5, 2, 0)).color(P2_RED).radius(10).flicker(1, 9).track(Light.Param.INTENSITY, "arcs").build(),
            Light.point(Point.center(-2.5, 2, 0)).color(P2_RED).radius(10).flicker(1, 7).track(Light.Param.INTENSITY, "arcs").build(),
            Light.ring(Point.center(0, -2.8, 0), 1, 2.5f).color(P2_HOT).height(5).track(Light.Param.RADIUS, "tremor_r").track(Light.Param.INTENSITY, "tremor_i").build());

        int vd = 60;
        Effects.register(OVERLOAD_VENT, def(vd, 6, P2_HOT));
        Effects.timeline(OVERLOAD_VENT, Timeline.builder(vd).track("vent", Kf.of(0, 2).k(vd, 0, Easing.EASE_IN).build()).build());
        Effects.anchorSocket(OVERLOAD_VENT, "reactor");
        Effects.lights(OVERLOAD_VENT, Light.point(Point.center(0, 0, .5)).color(0xffd8f4ff).radius(7).flicker(.4f, 6).track(Light.Param.INTENSITY, "vent").build());
    }

    /** Sustained phase effects: the cyan reactor heartbeat of the Finale, and the Overloaded storm around the body. */
    static void auras() {
        Timeline tl = Timeline.builder(100)
            .sustain(20, 80)
            .track("beat", Kf.of(0, 0).k(20, 1).k(30, 1.8f).k(36, .9f).k(42, 1.5f).k(50, .8f).k(80, 1).k(100, 0).build())
            .build();
        Effects.register(AURA_P2, def(100, 3, P2_HOT).withRange(128));
        Effects.timeline(AURA_P2, tl);
        Effects.anchorSocket(AURA_P2, "reactor");
        Effects.lights(AURA_P2, Light.point(Point.center(0, 0, .5)).color(P2_HOT).radius(6).intensity(1.2f).track(Light.Param.INTENSITY, "beat").build(),
            Light.point(Point.center(.8, 1.2, .3)).color(P2_RED).radius(3).intensity(.8f).flicker(.9f, 3).build());

        Timeline ol = Timeline.builder(100)
            .sustain(20, 80)
            .track("storm", Kf.of(0, 0).k(20, 1).k(32, 1.6f).k(44, 1).k(58, 1.7f).k(70, 1.1f).k(80, 1).k(100, 0).build())
            .build();
        Effects.register(AURA_OVERLOAD, def(100, 6, P2_HOT).withRange(128));
        Effects.timeline(AURA_OVERLOAD, ol);
        Effects.anchorSocket(AURA_OVERLOAD, "reactor");
        List<Beam> arcs = new ArrayList<>();
        double[][] ends = {{1.6, 1.4, .2}, {-1.7, 1.2, .1}, {1.4, -1.6, .4}, {-1.3, -1.8, .3}, {0, 2.2, -.4}};
        for (int i = 0; i < ends.length; i++)
            arcs.add(Beam.builder(Point.center(0, 0, .5), Point.center(ends[i][0], ends[i][1], ends[i][2])).width(.04f, .32f)
                .color(0xffffffff, i % 2 == 0 ? P2_HOT : P2_RED).intensity(1.6f).noise(.45f).wave(.25f, .5f, 6)
                .track(Beam.Param.INTENSITY, "storm").build());
        Effects.beams(AURA_OVERLOAD, arcs.toArray(Beam[]::new));
        Effects.lights(AURA_OVERLOAD, Light.point(Point.center(0, 0, .5)).color(P2_HOT).radius(10).intensity(1.6f).flicker(.45f, 12)
                .track(Light.Param.INTENSITY, "storm").build(),
            Light.point(Point.center(0, 2, 0)).color(P2_RED).radius(9).intensity(1.2f).flicker(1, 5).build());
    }

    // ------------------------------------------------------------------ Overloaded: charged punch

    static void chargedPunch() {
        Clip c = P2_OVERLOADED_CHARGED_PUNCH;
        int peak = t(c, "charge_peak"), impact = t(c, "impact"), rip = impact + 3, dur = len(c, 40);
        Timeline tl = Timeline.builder(dur)
            .marker("impact", impact).marker("rip", rip)
            .track("warn", Kf.of(0, 0).k(peak, 1, Easing.EASE_IN).k(impact, 1).k(impact + 2, 0).build())
            .track("charge", Kf.of(0, 0).k(t(c, "stance_set"), .3f).k(peak, 2.6f, Easing.EASE_IN).k(impact + 1, 0).build())
            .track("blast", Kf.of(impact - 1, 0).k(impact + 3, 1, Easing.EASE_OUT).build())
            .track("blast_i", Kf.of(impact - 1, 0).k(impact, 2.8f).k(impact + 22, 0, Easing.EASE_IN).build())
            .track("heat", Kf.of(impact - 1, 0).k(impact, 1).k(dur, 0).build())
            .build();
        CHARGED_PUNCH.register(id -> {
            Effects.register(id, hero(dur, 26, P2_HOT, impact, 5, .4f));
            Effects.timeline(id, tl);
            List<Beam> cone = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                double x = (i - 3) * 1.1, len = 20 - Math.abs(i - 3) * 2.5;
                cone.add(Beam.builder(Point.facing(0, 1.4, 1.5), Point.facing(x, .4 + (i % 2) * .8, len)).width(i == 3 ? .5f : .14f, i == 3 ? 2.6f : 1.2f)
                    .color(P2_CORE, i % 3 == 1 ? P2_RED : P2_HOT).intensity(2).noise(.3f).wave(.2f, 2, 4)
                    .track(Beam.Param.REVEAL, "blast").track(Beam.Param.INTENSITY, "blast_i").build());
            }
            Effects.beams(id, cone.toArray(Beam[]::new));
            Effects.lights(id,
                Light.point(Point.facing(1.3, 1.6, 1.2)).color(P2_HOT).radius(8).flicker(.4f, 14).track(Light.Param.INTENSITY, "charge").build(),
                Light.segment(Point.facing(0, 1, 2), Point.facing(0, 1, 18)).color(P2_HOT).radius(5).track(Light.Param.INTENSITY, "blast_i").build(),
                Light.disc(Point.facing(0, .3, 4), 6).color(P2_CORE).track(Light.Param.INTENSITY, "blast_i").build(),
                Light.segment(Point.facing(0, .4, 3), Point.facing(0, .4, 16)).color(P2_EMBER).radius(3).flicker(.4f, 6).track(Light.Param.INTENSITY, "heat").build());
        },
            Variants.edits(TerrainEdit.carve(TerrainShape.trench(10, 1.4f).offset(0, 0, 8)).commitAt("rip").glow(P2_EMBER).cooling(200).build()),
            Variants.edits(
                hot(TerrainEdit.carve(TerrainShape.crater(4.5f, 2.4f).offset(0, 0, 4).rough(.8f, 2)).commitAt("impact").burn(Fx.FUSED, 1), P2_EMBER, 400, .7f, 1).build(),
                hot(TerrainEdit.carve(TerrainShape.trench(18, 2.1f).offset(0, 0, 13).rough(1, 3)).commitAt("rip").blocksPerTick(1200).burn(Fx.FUSED, 1), P2_EMBER, 400, .7f, 1).build()),
            Variants.edits(
                hot(TerrainEdit.carve(TerrainShape.crater(6.5f, 3.2f).offset(0, 0, 4).rough(1.2f, 3)).commitAt("impact").burn(Fx.FUSED, 1), P2_EMBER, 600, .8f, 1).build(),
                hot(TerrainEdit.carve(TerrainShape.union(TerrainShape.trench(28, 2.9f).offset(0, 0, 18), TerrainShape.trench(16, 1.4f).offset(3.2f, 0, 14),
                    TerrainShape.trench(16, 1.4f).offset(-3.2f, 0, 14)).rough(1.2f, 3)).commitAt("rip").blocksPerTick(1800).burn(Fx.FUSED, 1), P2_EMBER, 600, .8f, 1).build()));
    }

    // ------------------------------------------------------------------ Overloaded: shield charge (sustained run) + breaches

    static void shieldCharge() {
        Clip s = P2_OVERLOADED_SHIELD_CHARGE_START;
        int launch = t(s, "launch"), loopStart = Brain.LEAD + s.ticks(), loopEnd = loopStart + P2_OVERLOADED_SHIELD_CHARGE_LOOP.ticks();
        int dur = loopEnd + P2_OVERLOADED_SHIELD_CHARGE_IMPACT.ticks() + 10;
        Timeline tl = Timeline.builder(dur)
            .sustain(loopStart, loopEnd).release(loopEnd)
            .track("drive", Kf.of(0, 0).k(launch, 1.6f, Easing.EASE_OUT).k(loopStart, 1.2f).k(loopEnd, 1.2f).k(loopEnd + 4, 2.4f).k(dur, 0).build())
            .build();
        Effects.register(SHIELD_CHARGE, def(dur, 8, P2_HOT).withPriority(Effects.Priority.HERO));
        Effects.timeline(SHIELD_CHARGE, tl);
        Effects.beams(SHIELD_CHARGE,
            Beam.builder(Point.facing(-2, .6, 2.4), Point.facing(2, .6, 2.4)).width(.12f, 1.4f).color(P2_CORE, P2_HOT).intensity(1.6f).wave(.5f, 4, 3).noise(.2f)
                .track(Beam.Param.INTENSITY, "drive").build(),
            Beam.builder(Point.facing(-1.8, 2.4, 2.2), Point.facing(1.8, 2.4, 2.2)).width(.1f, 1.1f).color(P2_CORE, P2_RED).intensity(1.3f).wave(.45f, 3.6f, -3).noise(.25f)
                .track(Beam.Param.INTENSITY, "drive").build(),
            Beam.builder(Point.facing(-2.2, 1.5, 2.3), Point.facing(2.2, 1.5, 2.3)).width(.08f, 1.8f).color(P2_CORE, P2_HOT).intensity(1.1f).noise(.4f)
                .track(Beam.Param.INTENSITY, "drive").build());
        Effects.lights(SHIELD_CHARGE, Light.point(Point.facing(0, 1.6, 2.6)).color(P2_HOT).radius(9).flicker(.3f, 12).track(Light.Param.INTENSITY, "drive").build(),
            Light.disc(Point.center(0, .2, 0), 3.5f).color(P2_HOT).track(Light.Param.INTENSITY, "drive").build());

        int bd = 34;
        Timeline breach = Timeline.builder(bd).marker("breach", 1)
            .track("flash", new Kf().spike(1, 2.4f, 12).build())
            .build();
        SHIELD_BREACH.register(id -> {
            Effects.register(id, def(bd, 6, P2_HOT));
            Effects.timeline(id, breach);
            Effects.lights(id, Light.point(Point.facing(0, 1.6, 1)).color(P2_HOT).radius(8).track(Light.Param.INTENSITY, "flash").build());
        },
            Variants.edits(TerrainEdit.carve(TerrainShape.trench(3.5f, 1.8f).offset(0, 2.2f, 1.6f)).commitAt("breach").noHeat().build()),
            Variants.edits(TerrainEdit.carve(TerrainShape.trench(4.6f, 2.4f).offset(0, 2.4f, 2.2f).rough(.5f, 2)).commitAt("breach").glow(P2_EMBER).cooling(80).build()),
            Variants.edits(TerrainEdit.carve(TerrainShape.trench(6.5f, 3.1f).offset(0, 2.8f, 3).rough(.8f, 2)).commitAt("breach").glow(P2_EMBER).cooling(120).build()));
    }

    // ------------------------------------------------------------------ Overloaded: leap smash

    static void leapSmash() {
        Clip c = P2_OVERLOADED_LEAP_SMASH;
        int launch = t(c, "launch"), land = t(c, "land_impact"), ring = land + 6, dur = len(c, 100);
        Timeline tl = Timeline.builder(dur)
            .marker("land", land).marker("ring", ring)
            .track("warn", Kf.of(0, 0).k(launch, .6f).k(land - 2, 1, Easing.EASE_IN).k(land, 1).k(land + 2, 0).build())
            .track("fall", Kf.of(t(c, "apex"), 0).k(land - 1, 2.4f, Easing.EASE_IN).k(land + 2, 0).build())
            .track("shock_r", Kf.of(land, 1).k(land + 34, 30, Easing.EASE_OUT).build())
            .track("shock_i", Kf.of(land - 1, 0).k(land, 3.8f).k(land + 34, 0, Easing.EASE_IN).build())
            .track("shock2_r", Kf.of(ring, 2).k(ring + 36, 34, Easing.EASE_OUT).build())
            .track("shock2_i", Kf.of(ring - 1, 0).k(ring, 2.4f).k(ring + 36, 0, Easing.EASE_IN).build())
            .track("flash", new Kf().spike(land, 1.2f, 9).build())
            .track("column", Kf.of(land - 1, 0).k(land, 1).k(land + 12, 0, Easing.EASE_IN).build())
            .track("fissure", Kf.of(land, 0).k(ring, 1, Easing.EASE_OUT).build())
            .track("fissure_i", Kf.of(land - 1, 0).k(land, 2.4f).k(ring + 40, 0, Easing.EASE_IN).build())
            .track("heat", Kf.of(land - 1, 0).k(land, 1.2f).k(dur, .25f).build())
            .track("dust", Kf.of(land, 0).k(land + 8, 1).k(dur, 0).build())
            .build();
        LEAP_SMASH.register(id -> {
            Effects.register(id, hero(dur, 40, P2_HOT, land, 7, .45f));
            Effects.timeline(id, tl);
            Effects.atmosphere(id, Atmosphere.builder().fog(0xff20363a, .4f, 6, 60).ambient(0xff60e8ff, .15f).track(Atmosphere.Param.WEIGHT, "dust").build());
            List<Beam> beams = new ArrayList<>(List.of(fissures(10, 22, "fissure", "fissure_i")));
            beams.add(Beam.builder(Point.center(0, 0, 0), Point.center(0, 34, 0)).width(1.6f, 7).color(P2_CORE, P2_HOT).intensity(2.4f).wave(.6f, 8, 3)
                .track(Beam.Param.INTENSITY, "column").build());
            Effects.beams(id, beams.toArray(Beam[]::new));
            Effects.lights(id,
                Light.ring(Point.center(), 1, 4).color(P2_HOT).height(6).track(Light.Param.RADIUS, "shock_r").track(Light.Param.INTENSITY, "shock_i").build(),
                Light.ring(Point.center(), 2, 3).color(P2_RED).height(5).track(Light.Param.RADIUS, "shock2_r").track(Light.Param.INTENSITY, "shock2_i").build(),
                Light.disc(Point.center(0, .4, 0), 13).color(P2_CORE).intensity(3).track(Light.Param.INTENSITY, "flash").build(),
                Light.point(Point.center(0, 6, 0)).color(P2_HOT).radius(16).falloff(1.2f).track(Light.Param.INTENSITY, "fall").build(),
                Light.point(Point.center(0, 1, 0)).color(P2_EMBER).radius(13).intensity(1.4f).flicker(.35f, 7).track(Light.Param.INTENSITY, "heat").build(),
                Light.point(Point.center(0, .6, 0)).color(P2_RED).radius(8).intensity(.8f).flicker(.7f, 4).track(Light.Param.INTENSITY, "heat").build());
        },
            Variants.edits(TerrainEdit.carve(TerrainShape.crater(6, 2.5f).rough(1, 3)).commitAt("land").glow(P2_EMBER).cooling(400).build()),
            Variants.edits(
                hot(TerrainEdit.carve(TerrainShape.crater(11.5f, 4.6f).profile(2.2f).rough(1.8f, 5)).commitAt("land").burn(Fx.FUSED, 1), P2_EMBER, 1000, 1, 1).build(),
                hot(TerrainEdit.carve(craterRing(6, 16, 3.2f, 1.6f, .5f).rough(1, 2)).commitAt("ring"), P2_EMBER, 500, .7f, .8f).build()),
            Variants.edits(
                hot(TerrainEdit.carve(TerrainShape.crater(17, 6.8f).profile(2.4f).rough(2.6f, 6)).commitAt("land").burn(Fx.FUSED, 2), P2_EMBER, 1400, 1, 1).build(),
                hot(TerrainEdit.carve(craterRing(8, 23, 4.4f, 2.2f, .5f).rough(1.4f, 3)).commitAt("ring").burn(Fx.FUSED, 1), P2_EMBER, 700, .8f, .9f).build()));
    }

    // ------------------------------------------------------------------ cinematic death of the Finale

    /** Server tick (inside DEATH2) of the final reactor implosion. */
    public static int finalBlast() { return t(P2_DEATH, "pilot_still") + 22; }

    static void death() {
        Clip c = P2_DEATH;
        int flare = t(c, "heart_flare"), podR = t(c, "pod_right_blows"), podL = t(c, "pod_left_blows"), torso = t(c, "torso_ground_impact");
        int knees = t(c, "knees_impact"), head = t(c, "head_ground_impact"), fin = finalBlast(), dur = fin + 40;
        Timeline tl = Timeline.builder(dur)
            .marker("torso", torso).marker("head", head).marker("final", fin)
            .track("heart", Kf.of(0, 1).k(flare, 3, Easing.OVERSHOOT).k(t(c, "torso_breaks_off"), .7f).k(fin - 12, .4f).k(fin - 1, 3.4f, Easing.EASE_IN).k(fin + 6, 0).build())
            .track("pod_r", new Kf().spike(podR, 3.2f, 16).build())
            .track("pod_l", new Kf().spike(podL, 3.2f, 16).build())
            .track("impact", new Kf().spike(torso, 2.6f, 14).spike(knees, 2, 10).spike(head, 1.6f, 10).build())
            .track("final", Kf.of(fin - 1, 0).k(fin, 1).k(fin + 24, 0, Easing.EASE_IN).build())
            .track("final_r", Kf.of(fin, 1).k(fin + 30, 30, Easing.EASE_OUT).build())
            .track("smoke", Kf.of(podR, 0).k(podR + 30, 1).k(dur, .5f).build())
            .track("rise", Kf.of(fin, 0).k(fin + 4, 1, Easing.EASE_OUT).build())
            .build();
        DEATH2.register(id -> {
            Effects.register(id, new Effects.Definition(dur, 36, P2_HOT, Effects.Shape.NONE, Effects.Material.NONE, null, null,
                new Effects.Impact(fin, 8, .6f, Effects.ImpactStyle.MONOCHROME), 192, Effects.Priority.HERO));
            Effects.timeline(id, tl);
            Effects.atmosphere(id, Atmosphere.builder().fog(0xff141c1e, .45f, 6, 60).ambient(0xff60d0e0, .12f).track(Atmosphere.Param.WEIGHT, "smoke").build());
            List<Beam> up = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                double a = i * Math.TAU / 8;
                up.add(Beam.builder(Point.facing(0, 1.2, 1.5), Point.facing(Math.cos(a) * 7, 26 + (i % 3) * 6, Math.sin(a) * 7 + 1.5)).width(.2f, 1.6f)
                    .color(P2_CORE, i % 2 == 0 ? P2_HOT : P2_RED).intensity(2).noise(.3f).track(Beam.Param.REVEAL, "rise").track(Beam.Param.INTENSITY, "final").build());
            }
            Effects.beams(id, up.toArray(Beam[]::new));
            Effects.lights(id,
                Light.point(Point.facing(1.45, 4.1, 0)).color(0xffff8a30).radius(10).track(Light.Param.INTENSITY, "pod_r").build(),
                Light.point(Point.facing(-1.45, 4.1, 0)).color(0xffff8a30).radius(10).track(Light.Param.INTENSITY, "pod_l").build(),
                Light.point(Point.facing(0, 2.4, .8)).color(P2_HOT).radius(12).flicker(.5f, 12).track(Light.Param.INTENSITY, "heart").build(),
                Light.disc(Point.facing(0, .3, 2.5), 7).color(0xffd8f0ff).track(Light.Param.INTENSITY, "impact").build(),
                Light.ring(Point.center(), 1, 4).color(P2_HOT).height(6).intensity(3).track(Light.Param.RADIUS, "final_r").track(Light.Param.INTENSITY, "final").build(),
                Light.disc(Point.center(0, .5, 1), 20).color(0xffffffff).intensity(3).track(Light.Param.INTENSITY, "final").build());
        },
            Variants.edits(hot(TerrainEdit.carve(TerrainShape.crater(4, 1.5f)).commitAt("final"), P2_EMBER, 600, .9f, .8f).build()),
            Variants.edits(
                TerrainEdit.carve(TerrainShape.crater(3, 1.2f).offset(0, 0, 2.5f)).commitAt("torso").glow(P1_EMBER).cooling(300).build(),
                TerrainEdit.carve(TerrainShape.crater(1.6f, .8f).offset(.5f, 0, 5.5f)).commitAt("head").glow(P1_EMBER).cooling(200).build(),
                hot(TerrainEdit.carve(TerrainShape.crater(7.5f, 2.8f).offset(0, 0, 1).rough(1.2f, 3)).commitAt("final").burn(Fx.FUSED, 1), P2_EMBER, 1200, 1, 1).build()),
            Variants.edits(
                TerrainEdit.carve(TerrainShape.crater(3.6f, 1.5f).offset(0, 0, 2.5f)).commitAt("torso").glow(P1_EMBER).cooling(300).build(),
                TerrainEdit.carve(TerrainShape.crater(2, 1).offset(.5f, 0, 5.5f)).commitAt("head").glow(P1_EMBER).cooling(200).build(),
                hot(TerrainEdit.carve(TerrainShape.crater(11, 4).offset(0, 0, 1).rough(1.6f, 4)).commitAt("final").burn(Fx.FUSED, 1), P2_EMBER, 1600, 1, 1).build(),
                hot(TerrainEdit.carve(craterRing(6, 15, 3, 1.4f, .2f).offset(0, 0, 1)).commitAt("final"), P2_EMBER, 700, .8f, .8f).build()));
    }
}
