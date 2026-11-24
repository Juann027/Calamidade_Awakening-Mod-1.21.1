package dev.bossestesting.fx;

import dev.calamidade_awakening.api.v1.Atmosphere;
import dev.calamidade_awakening.api.v1.Beam;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Light;
import dev.calamidade_awakening.api.v1.Point;
import dev.calamidade_awakening.api.v1.Sky;
import dev.calamidade_awakening.api.v1.TerrainEdit;
import dev.calamidade_awakening.api.v1.TerrainShape;
import dev.calamidade_awakening.api.v1.Timeline;
import dev.calamidade_awakening.api.v1.Timeline.Easing;
import dev.bossestesting.ai.Brain;
import dev.bossestesting.anim.Clip;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

import static dev.bossestesting.anim.Anims.*;
import static dev.bossestesting.fx.Fx.*;

/** Phase 1 effects (JarGolem, industrial orange). */
final class FxP1 {
    private FxP1() {}

    static void register() {
        landing();
        thrusters();
        slam();
        reactor();
        punches();
        dash();
        stagger();
        fury();
        transform();
        death();
        cams();
    }

    /** Camera-only effects: one instance per participant (the engine plays AddonLayers.film for the caster only). */
    static void cams() {
        int land = len(JG_SPAWN_LANDING, 10);
        Effects.register(CAM_LANDING, def(land, 4, P1_HOT));
        Effects.timeline(CAM_LANDING, Timeline.builder(land).marker("prep", t(JG_SPAWN_LANDING, "landing_prep")).marker("impact", t(JG_SPAWN_LANDING, "impact"))
            .marker("rise", t(JG_SPAWN_LANDING, "rise_start")).build());
        int tr = swapTick() + 14;
        Effects.register(CAM_TRANSFORM, def(tr, 4, P1_HOT));
        Effects.timeline(CAM_TRANSFORM, Timeline.builder(tr).marker("knee", t(JG_OVERLOAD, "knee_and_fists_impact"))
            .marker("pause", t(JG_OVERLOAD, "pause_start")).marker("stand", t(JG_OVERLOAD, "stand_impact"))
            .marker("threat", t(JG_OVERLOAD, "threat_release")).marker("swap", swapTick()).build());
    }

    // ------------------------------------------------------------------ helpers shared with phase 2

    /** Radial light rays thrown from the impact (debris streaks of light), `n` of them. */
    static Beam[] rays(int n, double reach, double rise, int core, int halo, String reveal, String intensity) {
        Beam[] out = new Beam[n];
        for (int i = 0; i < n; i++) {
            double a = i * Math.TAU / n + .3 * Math.sin(i * 7.1);
            double r = reach * (.75 + .25 * Math.sin(i * 3.7));
            out[i] = Beam.builder(Point.center(0, .3, 0), Point.center(Math.cos(a) * r, rise * (.6 + .4 * Math.cos(i * 2.3)), Math.sin(a) * r))
                .width(.12f, 1.1f).color(core, halo).intensity(1.6f).noise(.12f)
                .track(Beam.Param.REVEAL, reveal).track(Beam.Param.INTENSITY, intensity).build();
        }
        return out;
    }

    /** Ring of `n` small craters at radius `ring` (the shockwave tearing the ground around the main crater). */
    static TerrainShape craterRing(int n, float ring, float r, float depth, float phase) {
        TerrainShape shape = null;
        for (int i = 0; i < n; i++) {
            double a = phase + i * Math.TAU / n;
            TerrainShape c = TerrainShape.crater(r, depth).offset((float) (Math.cos(a) * ring), 0, (float) (Math.sin(a) * ring));
            shape = shape == null ? c : shape.union(c);
        }
        return shape;
    }

    static TerrainEdit.Builder hot(TerrainEdit.Builder b, int glow, int cooling, float smoke, float cracks) {
        return b.glow(glow).cooling(cooling).smoke(smoke).cracks(cracks);
    }

    // ------------------------------------------------------------------ landing

    static void landing() {
        int impact = t(JG_SPAWN_LANDING, "impact"), prep = t(JG_SPAWN_LANDING, "landing_prep"), rise = t(JG_SPAWN_LANDING, "rise_start");
        int dur = len(JG_SPAWN_LANDING, 60);
        Timeline tl = Timeline.builder(dur)
            .phase("descent", 0, impact, 6, 0)
            .phase("aftermath", impact, dur, 0, 40)
            .marker("prep", prep).marker("impact", impact).marker("ring", impact + 6).marker("rise", rise)
            .track("warn", Kf.of(0, 0).k(prep, 1, Easing.EASE_OUT).k(impact, 1).k(impact + 2, 0).build())
            .track("glow", Kf.of(0, 0).k(prep, 1.2f, Easing.EASE_IN).k(impact - 1, 3f, Easing.EASE_IN).k(impact + 3, 0).build())
            .track("shock_r", Kf.of(impact, 1).k(impact + 34, 30, Easing.EASE_OUT).build())
            .track("shock_i", Kf.of(impact - 1, 0).k(impact, 3.4f).k(impact + 34, 0, Easing.EASE_IN).build())
            .track("shock2_r", Kf.of(impact + 5, 1).k(impact + 40, 22, Easing.EASE_OUT).build())
            .track("shock2_i", Kf.of(impact + 4, 0).k(impact + 5, 1.8f).k(impact + 40, 0).build())
            .track("flash", new Kf().spike(impact, 1, 9).build())
            .track("rays", Kf.of(impact, 0).k(impact + 3, 1, Easing.EASE_OUT).build())
            .track("rays_i", Kf.of(impact - 1, 0).k(impact, 2.4f).k(impact + 16, 0, Easing.EASE_IN).build())
            .track("heat", Kf.of(impact - 1, 0).k(impact, 1).k(dur, 0, Easing.EASE_IN).build())
            .track("dust", Kf.of(impact, 0).k(impact + 8, 1, Easing.EASE_OUT).k(dur, 0).build())
            .build();
        LANDING.register(id -> {
            Effects.register(id, hero(dur, 34, P1_HOT, impact, 6, .35f));
            Effects.timeline(id, tl);
            Effects.atmosphere(id, Atmosphere.builder().sky(0xff3a2010, 0xff8a4a20, .25f).fog(0xff6a4a30, .45f, 4, 56)
                .ambient(0xffffa060, .18f).exposure(1.08f).track(Atmosphere.Param.WEIGHT, "dust").build());
            Effects.beams(id, rays(7, 15, 3.5, P1_CORE, P1_HOT, "rays", "rays_i"));
            Effects.lights(id,
                Light.ring(Point.center(), 1, 3.5f).color(P1_HOT).height(5).track(Light.Param.RADIUS, "shock_r").track(Light.Param.INTENSITY, "shock_i").build(),
                Light.ring(Point.center(), 1, 2).color(P1_DEEP).height(4).track(Light.Param.RADIUS, "shock2_r").track(Light.Param.INTENSITY, "shock2_i").build(),
                Light.disc(Point.center(0, .4, 0), 11).color(P1_CORE).intensity(3).track(Light.Param.INTENSITY, "flash").build(),
                Light.point(Point.center(0, 1.5, 0)).color(P1_HOT).radius(16).falloff(1.3f).track(Light.Param.INTENSITY, "glow").build(),
                Light.point(Point.center(0, .8, 0)).color(P1_EMBER).radius(9).intensity(1.6f).flicker(.3f, 6).track(Light.Param.INTENSITY, "heat").build());
        },
            Variants.edits(TerrainEdit.carve(TerrainShape.crater(5, 2).rough(.8f, 3)).commitAt("impact").cooling(240).glow(P1_EMBER).build()),
            Variants.edits(hot(TerrainEdit.carve(TerrainShape.crater(9, 3.6f).profile(2.3f).rough(1.4f, 4)).commitAt("impact")
                .burn(Fx.SCORCHED, 1), P1_EMBER, 500, .8f, 1).build()),
            Variants.edits(
                hot(TerrainEdit.carve(TerrainShape.crater(14, 5.5f).profile(2.4f).rough(2.2f, 5)).commitAt("impact").burn(Fx.SCORCHED, 1), P1_EMBER, 800, .9f, 1).build(),
                hot(TerrainEdit.carve(craterRing(7, 19, 3.6f, 1.8f, .4f).rough(1, 2)).commitAt("ring").burn(Fx.SCORCHED, 1), P1_EMBER, 500, .6f, .7f).build()));
    }

    /** Thruster jets on each foot during the descent (the instance follows the captured foot bone). */
    static void thrusters() {
        int impact = t(JG_SPAWN_LANDING, "impact"), prep = t(JG_SPAWN_LANDING, "landing_prep");
        int dur = impact + 12;
        Timeline tl = Timeline.builder(dur)
            .track("thrust", Kf.of(0, .6f).k(6, 1).k(prep - 2, 1).k(prep + 1, 1.7f, Easing.OVERSHOOT).k(impact - 2, 1.3f).k(impact, .2f).k(impact + 10, 0).build())
            .track("neg_len", Kf.of(0, -5).k(prep, -9).k(impact - 1, -3).k(impact + 1, -.6f).build())
            .build();
        for (ResourceLocation id : List.of(THRUST_L, THRUST_R)) {
            Effects.register(id, def(dur, 8, P1_HOT).withPriority(Effects.Priority.HERO));
            Effects.timeline(id, tl);
            Effects.anchorSocket(id, id == THRUST_L ? "foot_l" : "foot_r");
            Beam core = Beam.builder(Point.center(0, -.15, 0), Point.center(0, -1, 0)).width(.42f, 1.9f).color(P1_CORE, P1_HOT)
                .intensity(2.2f).pulses(7, -.9f, .9f).noise(.06f).track(Beam.Param.INTENSITY, "thrust").track(Beam.Param.TO_Y, "neg_len").build();
            Beam sheath = Beam.builder(Point.center(0, -.2, 0), Point.center(0, -1, 0)).width(.1f, 3.2f).color(P1_HOT, P1_DEEP)
                .intensity(.8f).wave(.18f, 1.4f, -2.2f).noise(.22f).track(Beam.Param.INTENSITY, "thrust").track(Beam.Param.TO_Y, "neg_len").build();
            Effects.beams(id, core, sheath);
            Effects.lights(id, Light.point(Point.center(0, -1.2, 0)).color(P1_HOT).radius(11).intensity(2.6f).flicker(.22f, 14)
                .track(Light.Param.INTENSITY, "thrust").build());
        }
    }

    // ------------------------------------------------------------------ ground slam

    static void slam() {
        int peak = t(JG_GROUND_SLAM, "windup_peak"), impact = t(JG_GROUND_SLAM, "impact");
        int dur = len(JG_GROUND_SLAM, 50);
        Timeline tl = Timeline.builder(dur)
            .phase("windup", 0, impact, 10, 0)
            .marker("impact", impact).marker("ring", impact + 5)
            .track("warn", Kf.of(0, 0).k(peak, 1, Easing.EASE_IN).k(impact, 1).k(impact + 2, 0).build())
            .track("shock_r", Kf.of(impact, 1).k(impact + 26, 20, Easing.EASE_OUT).build())
            .track("shock_i", Kf.of(impact - 1, 0).k(impact, 3.2f).k(impact + 26, 0, Easing.EASE_IN).build())
            .track("flash", new Kf().spike(impact, 1, 7).build())
            .track("rays", Kf.of(impact, 0).k(impact + 3, 1, Easing.EASE_OUT).build())
            .track("rays_i", Kf.of(impact - 1, 0).k(impact, 2f).k(impact + 14, 0, Easing.EASE_IN).build())
            .track("heat", Kf.of(impact - 1, 0).k(impact, 1).k(dur, 0, Easing.EASE_IN).build())
            .build();
        SLAM.register(id -> {
            Effects.register(id, hero(dur, 26, P1_HOT, impact, 5, .3f));
            Effects.timeline(id, tl);
            Effects.beams(id, rays(6, 11, 2.5, P1_CORE, P1_HOT, "rays", "rays_i"));
            Effects.lights(id,
                Light.ring(Point.center(), 1, 3).color(P1_HOT).height(4).track(Light.Param.RADIUS, "shock_r").track(Light.Param.INTENSITY, "shock_i").build(),
                Light.disc(Point.center(0, .4, 0), 7).color(P1_CORE).intensity(3).track(Light.Param.INTENSITY, "flash").build(),
                Light.point(Point.center(0, .8, 0)).color(P1_EMBER).radius(7).intensity(1.6f).flicker(.3f, 6).track(Light.Param.INTENSITY, "heat").build());
        },
            Variants.edits(TerrainEdit.carve(TerrainShape.crater(4, 1.5f).rough(.6f, 2)).commitAt("impact").glow(P1_EMBER).cooling(200).build()),
            Variants.edits(hot(TerrainEdit.carve(TerrainShape.crater(6.5f, 2.6f).rough(1, 3)).commitAt("impact").burn(Fx.SCORCHED, 1), P1_EMBER, 400, .7f, .9f).build()),
            Variants.edits(
                hot(TerrainEdit.carve(TerrainShape.crater(9.5f, 3.8f).rough(1.5f, 4)).commitAt("impact").burn(Fx.SCORCHED, 1), P1_EMBER, 600, .8f, 1).build(),
                hot(TerrainEdit.carve(craterRing(5, 13, 2.8f, 1.4f, 1.1f).rough(.8f, 2)).commitAt("ring"), P1_EMBER, 300, .5f, .6f).build()));
    }

    // ------------------------------------------------------------------ reactor charge → reactor beam (+ scar)

    static void reactor() {
        Clip c = JG_REACTOR_CHARGE;
        int s1 = t(c, "surge_1"), s2 = t(c, "surge_2"), s3 = t(c, "surge_3"), full = t(c, "full_charge"), ready = t(c, "ready_to_fire");
        int dur = ready + 2;
        Timeline charge = Timeline.builder(dur)
            .track("charge", Kf.of(0, 0).k(t(c, "arms_open"), .2f).k(s1, .4f).k(s2, .62f).k(s3, .82f).k(full, 1).k(dur, 1.15f).build())
            .track("surge", new Kf().spike(s1, 1, 5).spike(s2, 1, 5).spike(s3, 1.2f, 5).spike(full, 1.6f, 4).build())
            .build();
        Effects.register(REACTOR_CHARGE, def(dur, 6, P1_HOT).withPriority(Effects.Priority.HERO));
        Effects.timeline(REACTOR_CHARGE, charge);
        Effects.anchorSocket(REACTOR_CHARGE, "reactor");
        Effects.beams(REACTOR_CHARGE, inflow(10, 4.2, P1_CORE, P1_HOT, .25f));
        Effects.lights(REACTOR_CHARGE, Light.point(Point.center(0, 0, .4)).color(P1_HOT).radius(10).intensity(3).flicker(.25f, 12)
            .track(Light.Param.INTENSITY, "charge").build(),
            Light.point(Point.center(0, 0, .6)).color(P1_CORE).radius(5).intensity(3).track(Light.Param.INTENSITY, "surge").build());

        Clip f = JG_REACTOR_FIRE;
        int fire = t(f, "fire"), stable = t(f, "stabilized"), end = t(f, "end");
        int bd = end + 4;
        Timeline beam = Timeline.builder(bd)
            .marker("fire", fire)
            .track("muzzle", Kf.of(0, 0).k(fire - 1, 1.4f, Easing.EASE_IN).k(fire + 1, 3).k(stable + 6, 0).build())
            .track("reveal", Kf.of(fire - 1, 0).k(fire + 2, 1, Easing.EASE_OUT).build())
            .track("beam", Kf.of(fire - 1, 0).k(fire, 1.4f).k(fire + 3, 1).k(stable, .85f).k(stable + 7, 0, Easing.EASE_IN).build())
            .track("core_w", Kf.of(fire, 1.2f).k(fire + 3, .7f).k(stable, .55f).k(stable + 7, .05f).build())
            .build();
        Effects.register(REACTOR_BEAM, hero(bd, 26, P1_HOT, fire, 4, .25f));
        Effects.timeline(REACTOR_BEAM, beam);
        Beam core = Beam.builder(Point.facing(0, 0, .3), Point.facing(0, 0, 48)).width(.6f, 1.5f).color(P1_CORE, P1_HOT).intensity(2.4f)
            .track(Beam.Param.REVEAL, "reveal").track(Beam.Param.INTENSITY, "beam").track(Beam.Param.CORE_WIDTH, "core_w").build();
        Effects.beams(REACTOR_BEAM, core,
            Beam.builder(Point.facing(0, 0, .3), Point.facing(0, 0, 48)).width(.18f, 3.6f).color(P1_HOT, P1_DEEP).intensity(1.2f)
                .noise(.32f).wave(.25f, 3, 1.6f).pulses(9, 3.6f, 2.4f).track(Beam.Param.REVEAL, "reveal").track(Beam.Param.INTENSITY, "beam").build(),
            Beam.builder(Point.facing(0, 0, .3), Point.facing(0, 0, 48)).width(.07f, .45f).color(P1_CORE, P1_HOT).intensity(1.6f)
                .wave(.62f, 4.5f, 2.2f).track(Beam.Param.REVEAL, "reveal").track(Beam.Param.INTENSITY, "beam").build(),
            Beam.builder(Point.facing(0, 0, .3), Point.facing(0, 0, 48)).width(.07f, .45f).color(P1_CORE, P1_HOT).intensity(1.6f)
                .wave(.55f, 5.3f, -2.6f).track(Beam.Param.REVEAL, "reveal").track(Beam.Param.INTENSITY, "beam").build(),
            Beam.builder(Point.facing(0, 0, .3), Point.facing(0, 0, 46)).width(0, 7).color(P1_DEEP, 0xff6a1a00).intensity(.45f)
                .track(Beam.Param.REVEAL, "reveal").track(Beam.Param.INTENSITY, "beam").build());
        Effects.lights(REACTOR_BEAM, Light.along(core).radius(6).intensity(2.4f).build(),
            Light.point(Point.facing(0, 0, .6)).color(P1_HOT).radius(9).track(Light.Param.INTENSITY, "muzzle").build());

        scar(BEAM_SCAR, P1_HOT, P1_EMBER, Fx.SCORCHED);
    }

    /** Beams converging on the center from a shell: energy drawn into a reactor. */
    static Beam[] inflow(int n, double radius, int core, int halo, float noise) {
        Beam[] out = new Beam[n];
        for (int i = 0; i < n; i++) {
            double a = i * 2.39996, y = 1 - 2 * (i + .5) / n, rr = Math.sqrt(1 - y * y);
            double r = radius * (.8 + .2 * Math.sin(i * 5.3));
            out[i] = Beam.builder(Point.center(Math.cos(a) * rr * r, y * r * .7, Math.sin(a) * rr * r + .4), Point.center(0, 0, .45))
                .width(.05f, .38f).color(core, halo).intensity(1.4f).pulses(3, .5f, .6f).wave(.2f, 1.1f, 1.5f).noise(noise)
                .track(Beam.Param.REVEAL, "charge").track(Beam.Param.INTENSITY, "charge").build();
        }
        return out;
    }

    /** Trench torn by a beam where it touches the ground, along the beam's yaw. */
    static void scar(Variants v, int color, int ember, dev.calamidade_awakening.api.v1.TerrainPalette palette) {
        int dur = 80;
        Timeline tl = Timeline.builder(dur).marker("scar", 2)
            .track("hit", Kf.of(0, 2.6f).k(12, 1.6f).k(30, 0, Easing.EASE_IN).build())
            .track("heat", Kf.of(0, 1).k(dur, 0).build())
            .build();
        v.register(id -> {
            Effects.register(id, def(dur, 18, color));
            Effects.timeline(id, tl);
            Effects.lights(id, Light.point(Point.center(0, 1, 0)).color(color).radius(10).intensity(1).flicker(.35f, 10).track(Light.Param.INTENSITY, "hit").build(),
                Light.segment(Point.facing(0, .6, 0), Point.facing(0, .6, 14)).color(ember).radius(4).intensity(1.2f).track(Light.Param.INTENSITY, "heat").build());
        },
            Variants.edits(TerrainEdit.carve(TerrainShape.trench(9, 1.2f).offset(0, 0, 4)).commitAt("scar").blocksPerTick(600).glow(ember).cooling(200).build()),
            Variants.edits(hot(TerrainEdit.carve(TerrainShape.trench(17, 1.9f).offset(0, 0, 8).rough(.8f, 2)).commitAt("scar").blocksPerTick(900)
                .burn(palette, 1), ember, 360, .7f, 1).build()),
            Variants.edits(hot(TerrainEdit.carve(TerrainShape.union(TerrainShape.trench(28, 2.7f).offset(0, 0, 13), TerrainShape.crater(4, 2.2f))
                .rough(1.2f, 3)).commitAt("scar").blocksPerTick(1400).burn(palette, 1), ember, 500, .8f, 1).build()));
    }

    // ------------------------------------------------------------------ hydraulic punches

    static void punches() {
        for (ResourceLocation id : List.of(PUNCH_R, PUNCH_L)) {
            Clip c = id == PUNCH_R ? JG_PUNCH_RIGHT : JG_PUNCH_LEFT;
            int launch = t(c, "launch"), hit = t(c, "hit"), dur = len(c, 4);
            Timeline tl = Timeline.builder(dur).marker("hit", hit)
                .track("piston", new Kf().spike(launch, 1.3f, 4).build())
                .track("hit_i", new Kf().spike(hit, 3, 8).build())
                .track("ring_r", Kf.of(hit, .4f).k(hit + 8, 4.5f, Easing.EASE_OUT).build())
                .build();
            Effects.register(id, def(dur, 6, P1_HOT));
            Effects.timeline(id, tl);
            Effects.anchorSocket(id, id == PUNCH_R ? "hand_r" : "hand_l");
            Effects.lights(id, Light.point(Point.center(0, 0, .5)).color(P1_HOT).radius(8).track(Light.Param.INTENSITY, "hit_i").build(),
                Light.point(Point.center(0, .6, -.6)).color(0xffffe8c8).radius(4).track(Light.Param.INTENSITY, "piston").build());
        }
    }

    // ------------------------------------------------------------------ dash (sustained until the brake)

    static void dash() {
        int launch = t(JG_DASH_START, "launch");
        int loopStart = Brain.LEAD + JG_DASH_START.ticks(), loopEnd = loopStart + JG_RUN.ticks();
        int release = loopEnd, dur = release + 24;
        Timeline tl = Timeline.builder(dur)
            .sustain(loopStart, loopEnd).release(release)
            .track("trail", Kf.of(0, 0).k(launch, 1, Easing.EASE_OUT).k(loopStart, 1).k(loopEnd, 1).k(release + 20, 0).build())
            .track("coil", Kf.of(0, 0).k(t(JG_DASH_START, "coiled"), 1, Easing.EASE_IN).k(launch + 2, 0).build())
            .build();
        Effects.register(DASH, def(dur, 10, P1_HOT));
        Effects.timeline(DASH, tl);
        Effects.lights(DASH, Light.disc(Point.center(0, .2, -1.5), 3.5f).color(P1_HOT).intensity(2).track(Light.Param.INTENSITY, "trail").build(),
            Light.point(Point.facing(0, 2.8, .6)).color(P1_HOT).radius(7).intensity(2.5f).track(Light.Param.INTENSITY, "coil").build());
    }

    // ------------------------------------------------------------------ stagger (exposed weak point)

    static void stagger() {
        int dur = len(JG_STAGGER, 0);
        Timeline tl = Timeline.builder(dur)
            .track("spark", Kf.of(0, 2.5f).k(t(JG_STAGGER, "regain_control"), 1).k(dur, 0).build())
            .build();
        Effects.register(STAGGER, def(dur, 5, P1_HOT));
        Effects.timeline(STAGGER, tl);
        Effects.anchorSocket(STAGGER, "reactor");
        Effects.lights(STAGGER, Light.point(Point.center(0, 0, .5)).color(0xffffd0a0).radius(7).intensity(2).flicker(1, 18)
            .track(Light.Param.INTENSITY, "spark").build());
        Effects.beams(STAGGER,
            Beam.builder(Point.center(0, 0, .5), Point.center(1.4, -1.2, .8)).width(.04f, .3f).color(0xffffffff, P1_HOT).noise(.35f).wave(.2f, .7f, 3)
                .track(Beam.Param.INTENSITY, "spark").build(),
            Beam.builder(Point.center(0, 0, .5), Point.center(-1.2, .9, .6)).width(.04f, .3f).color(0xffffffff, P1_HOT).noise(.35f).wave(.2f, .6f, -3)
                .track(Beam.Param.INTENSITY, "spark").build());
    }

    // ------------------------------------------------------------------ fury (phase_transition at half health)

    static void fury() {
        Clip c = JG_PHASE_TRANSITION;
        int glitch = t(c, "glitch_onset"), max = t(c, "max_malfunction"), regain = t(c, "pilot_regains_control"), roar = t(c, "roar_peak");
        int dur = len(c, 30);
        Timeline tl = Timeline.builder(dur)
            .marker("roar", roar)
            .track("glitch", Kf.of(glitch, 0).k(max, 1, Easing.EASE_IN).k(regain, .25f).k(roar, 0).build())
            .track("rage", Kf.of(regain, 0).k(roar, 1, Easing.EASE_IN).k(dur - 10, .6f).k(dur, 0).build())
            .track("roar_r", Kf.of(roar, 1).k(roar + 24, 22, Easing.EASE_OUT).build())
            .track("roar_i", new Kf().spike(roar, 3, 24).build())
            .build();
        Effects.register(FURY, new Effects.Definition(dur, 24, P1_HOT, Effects.Shape.NONE, Effects.Material.NONE, null, null,
            new Effects.Impact(roar, 6, .4f, Effects.ImpactStyle.MONOCHROME), 160, Effects.Priority.HERO));
        Effects.timeline(FURY, tl);
        Effects.atmosphere(FURY, Atmosphere.builder().sky(0xff2a0804, 0xffa02a0a, .45f).fog(0xff4a1206, .35f, 10, 90).ambient(0xffff5a2a, .2f)
            .exposure(1.1f).track(Atmosphere.Param.WEIGHT, "rage").build());
        Effects.lights(FURY, Light.ring(Point.center(), 1, 2.5f).color(P1_HOT).height(5).track(Light.Param.RADIUS, "roar_r").track(Light.Param.INTENSITY, "roar_i").build(),
            Light.point(Point.facing(0, 2.8, .6)).color(P1_HOT).radius(9).intensity(1.2f).flicker(.8f, 16).track(Light.Param.INTENSITY, "glitch").build());
    }

    // ------------------------------------------------------------------ transformation (overload of phase 1)

    static final String[] PULSES = {"pulse_1", "pulse_2", "pulse_3", "pulse_4", "pulse_5", "pulse_6", "pulse_7", "pulse_8_peak"};

    /** Server tick (inside TRANSFORM) of the swap: right after threat_release, under the flash and smoke. */
    public static int swapTick() { return t(JG_OVERLOAD, "threat_release") + 2; }

    static void transform() {
        Clip c = JG_OVERLOAD;
        int surge = t(c, "reactor_surge"), legs = t(c, "legs_give"), knee = t(c, "knee_and_fists_impact"), pause = t(c, "pause_start");
        int stand = t(c, "stand_impact"), threat = t(c, "threat_release"), swap = swapTick();
        int dur = len(c, 50);
        Kf pulse = new Kf(), reactor = Kf.of(0, 1).k(surge, 2.4f, Easing.OVERSHOOT), pulseR = new Kf();
        for (String p : PULSES) {
            int k = t(c, p);
            boolean peak = p.endsWith("peak");
            pulse.spike(k, peak ? 1.6f : 1, peak ? 6 : 3);
            reactor.k(k - 1, 1.4f).k(k, peak ? 3.4f : 2.6f);
            pulseR.k(k, 1).k(k + 6, peak ? 18 : 9, Easing.EASE_OUT);
        }
        reactor.k(pause - 3, 1.2f).k(pause, 0, Easing.EASE_IN).k(dur, 0);
        Kf alarm = Kf.of(legs, 0);
        for (int k = legs; k < threat; k += 6) alarm.k(k, .25f).k(k + 3, 1);
        alarm.k(threat, 0);
        Timeline tl = Timeline.builder(dur)
            .phase("crisis", 0, pause, 10, 6).phase("dark", pause, threat, 4, 0).phase("release", threat, dur, 0, 30)
            .marker("knee", knee).marker("pause", pause).marker("stand", stand).marker("threat", threat).marker("swap", swap)
            .track("reactor", reactor.build())
            .track("pulse", pulse.build())
            .track("pulse_r", pulseR.build())
            .track("alarm", alarm.build())
            .track("dark", Kf.of(0, 0).k(pause, .8f, Easing.EASE_IN).k(threat, 1).k(swap + 2, 0).build())
            .track("release", Kf.of(threat - 1, 0).k(threat, 1).k(threat + 16, 0, Easing.EASE_IN).build())
            .track("smoke", Kf.of(threat - 4, 0).k(threat + 2, 1).k(dur, 0).build())
            .track("cyan", Kf.of(swap, 0).k(swap + 5, 1.6f, Easing.OVERSHOOT).k(dur, 0).build())
            .track("rift", Kf.of(t(c, "pulse_1"), 0).k(pause, .7f).k(threat, 1).k(threat + 14, 0).build())
            .track("bolts", Kf.of(t(c, "pulse_1"), .15f).k(t(c, "pulse_8_peak"), .9f).k(pause, .4f).k(threat, 1).k(threat + 20, 0).build())
            .track("knee_i", new Kf().spike(knee, 2.4f, 14).spike(stand, 2, 12).build())
            .build();
        Effects.register(TRANSFORM, new Effects.Definition(dur, 40, P1_HOT, Effects.Shape.NONE, Effects.Material.NONE, null, null,
            new Effects.Impact(threat, 8, .55f, Effects.ImpactStyle.MONOCHROME), 192, Effects.Priority.HERO));
        Effects.timeline(TRANSFORM, tl);
        Effects.atmosphere(TRANSFORM, Atmosphere.builder().sky(0xff0a0303, 0xff3a0e06, .7f).fog(0xff1a0604, .5f, 8, 70).ambient(0xff8a2010, .25f)
            .exposure(.72f).track(Atmosphere.Param.WEIGHT, "dark").build());
        List<Beam> arcs = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            double a = i * Math.TAU / 7 + .4;
            arcs.add(Beam.builder(Point.facing(0, 2.6, .5), Point.facing(Math.cos(a) * 5.5, 0, Math.sin(a) * 5.5)).width(.05f, .5f)
                .color(0xffffffff, i % 2 == 0 ? P1_HOT : 0xffff3a20).intensity(2).noise(.55f).wave(.35f, .9f, 4)
                .track(Beam.Param.INTENSITY, "pulse").build());
        }
        arcs.add(Beam.builder(Point.facing(0, 2.8, .5), Point.facing(0, 60, 0)).width(.6f, 5).color(P2_CORE, P2_HOT).intensity(2.2f)
            .wave(.4f, 6, 3).track(Beam.Param.INTENSITY, "cyan").build());
        Effects.beams(TRANSFORM, arcs.toArray(Beam[]::new));
        Effects.lights(TRANSFORM,
            Light.point(Point.facing(0, 2.6, .6)).color(P1_HOT).radius(14).falloff(1.4f).flicker(.35f, 14).track(Light.Param.INTENSITY, "reactor").build(),
            Light.point(Point.facing(3.5, 6, 0)).color(0xffff1a10).radius(16).flicker(.2f, 4).track(Light.Param.INTENSITY, "alarm").build(),
            Light.point(Point.facing(-3.5, 6, 0)).color(0xffff1a10).radius(16).flicker(.2f, 4).track(Light.Param.INTENSITY, "alarm").build(),
            Light.ring(Point.center(), 1, 2).color(P1_HOT).height(4).intensity(2.2f).track(Light.Param.RADIUS, "pulse_r").track(Light.Param.INTENSITY, "pulse").build(),
            Light.disc(Point.center(0, .4, 0), 6).color(0xffffd8b0).track(Light.Param.INTENSITY, "knee_i").build(),
            Light.disc(Point.center(0, .5, 0), 24).color(0xffffffff).intensity(3).track(Light.Param.INTENSITY, "release").build(),
            Light.point(Point.facing(0, 2.8, .6)).color(P2_HOT).radius(18).falloff(1.2f).track(Light.Param.INTENSITY, "cyan").build());
        Effects.sky(TRANSFORM,
            Sky.rift().direction(0, 62).size(110).color(0xffff4a1a, 0xff1a0000).track(Sky.Param.PROGRESS, "rift").track(Sky.Param.WEIGHT, "rift").build(),
            Sky.bolts().color(0xffffe0c0, 0xffff5a20).track(Sky.Param.PROGRESS, "bolts").build());
    }

    // ------------------------------------------------------------------ abnormal death of phase 1 (void, /kill bypass)

    static void death() {
        Clip c = JG_DEATH;
        int ground = t(c, "ground_impact"), knee = t(c, "knee_impact"), dur = len(c, 30);
        Timeline tl = Timeline.builder(dur)
            .track("spark", Kf.of(0, 2).k(t(c, "collapse"), 1).k(t(c, "motionless"), 0).build())
            .track("impact", new Kf().spike(knee, 2, 10).spike(ground, 3, 18).build())
            .build();
        Effects.register(DEATH1, def(dur, 10, P1_HOT));
        Effects.timeline(DEATH1, tl);
        Effects.lights(DEATH1, Light.point(Point.facing(0, 2.4, .6)).color(P1_HOT).radius(8).flicker(1, 12).track(Light.Param.INTENSITY, "spark").build(),
            Light.disc(Point.center(0, .3, 2.5), 7).color(0xffffd8b0).track(Light.Param.INTENSITY, "impact").build());
    }

}
