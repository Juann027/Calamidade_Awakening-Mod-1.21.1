package dev.bossestesting.ai;

import dev.bossestesting.anim.Clip;
import dev.bossestesting.combat.Hits;
import dev.bossestesting.encounter.Encounter;
import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.entity.JarGolem;
import dev.bossestesting.fx.Fx;
import dev.bossestesting.fx.Sfx;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.RigAnchors;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.bossestesting.anim.Anims.*;

/** AI of El Guilhermino Bananino (JarGolem): slams, reactor beam, hydraulic punches, dash, stagger, fury, overload. */
public final class Phase1Brain extends Brain {
    private boolean furyDone;
    private boolean transformRequested;

    public Phase1Brain(JarGolem boss) {
        super(boss);
        move(new Move("slam", 180, 0, 7.5, c -> 2 + c.nearbyPlayers() * 1.5, Slam::new));
        move(new Move("reactor", 300, 7, 42, c -> 2.2 + (c.distance() > 14 ? 1.5 : 0), Reactor::new));
        move(new Move("punch_right", 50, 0, 5.5, c -> 3, (b, t) -> new Punch(b, t, true)));
        move(new Move("punch_left", 50, 0, 5.5, c -> 2.5, (b, t) -> new Punch(b, t, false)));
        move(new Move("dash", 220, 9, 30, c -> 2 + (c.distance() > 16 ? 1.5 : 0), Dash::new));
        // Test-only entries (commands): never picked by the selection (weight 0).
        move(new Move("stagger", 0, 0, 999, c -> 0, Stagger::new));
        move(new Move("fury", 0, 0, 999, c -> 0, Fury::new));
        move(new Move("transform", 0, 0, 999, c -> 0, Transform::new));
    }

    /** Lethal damage reached the first form: the overload transformation starts at the next tick. */
    public void requestTransform() { transformRequested = true; }

    public boolean transforming() { return action() instanceof Transform; }

    @Override
    protected Action forced(LivingEntity target) {
        if (transformRequested && !transforming()) { transformRequested = false; return new Transform(boss, target); }
        if (!furyDone && boss.getHealth() <= boss.getMaxHealth() * .5f) { furyDone = true; return new Fury(boss, target); }
        return null;
    }

    @Override protected Action death() { return new Death(boss); }
    @Override protected Action stagger(LivingEntity target) { return new Stagger(boss, target); }
    @Override protected double chaseSpeed() { return enraged ? 1.15 : 1.0; }

    /** The cinematic descent: started by the encounter right after the spawn, before any other action. */
    public void land(double groundY) { begin(new Landing(boss, groundY)); }

    // ================================================================== actions

    /** spawn_landing: a real descent driven by the server (collision included), impact at the clip's 1.5 s. */
    static final class Landing extends Action {
        final double groundY, startY;
        final int impact = Fx.t(JG_SPAWN_LANDING, "impact");

        Landing(GuilherminoBoss boss, double groundY) {
            super(boss, null);
            this.groundY = groundY;
            this.startY = boss.getY();
            invulnerable = true;
        }

        @Override public String name() { return "landing"; }

        @Override protected void begin() {
            boss.setNoGravity(true);
            play(JG_SPAWN_LANDING);
            Vec3 ground = new Vec3(boss.getX(), groundY, boss.getZ());
            Fx.LANDING.start(level, boss, Placement.at(ground, boss.getYRot(), 0));
            own(Effects.start(Fx.THRUST_L, level, Placement.on(boss)));
            own(Effects.start(Fx.THRUST_R, level, Placement.on(boss)));
            Encounter e = boss.encounter();
            if (e != null) e.startCams(Fx.CAM_LANDING, ground, boss.getYRot());
            on(JG_SPAWN_LANDING, "descent_start", () -> {
                Sfx.play(level, boss.position(), SoundEvents.FIRECHARGE_USE, 4, .5f);
                Sfx.play(level, boss.position(), SoundEvents.BLAZE_BURN, 4, .4f);
            });
            on(JG_SPAWN_LANDING, "landing_prep", () -> {
                Sfx.play(level, boss.position(), SoundEvents.BLAZE_SHOOT, 4, .5f);
                Sfx.play(level, boss.position(), SoundEvents.GHAST_SHOOT, 3, .45f);
            });
            on(JG_SPAWN_LANDING, "impact", this::impact);
            on(JG_SPAWN_LANDING, "rise_start", () -> { invulnerable = false; Sfx.servo(level, boss.position(), .55f); });
            on(JG_SPAWN_LANDING, "back_foot_lift", () -> Sfx.servo(level, boss.position(), .7f));
            on(JG_SPAWN_LANDING, "back_foot_plant", () -> Sfx.heavyStep(level, boss.position()));
            endWith(JG_SPAWN_LANDING);
        }

        /** Height profile: free fall, thruster brake around landing_prep, final drop onto the impact tick. */
        double heightAt(int a) {
            double s = Math.min(1, a / (double) impact);
            double f = s < .55 ? .7 * Math.pow(s / .55, 2) : s < .85 ? .7 + .18 * ((s - .55) / .3) : .88 + .12 * Math.pow((s - .85) / .15, 2);
            return groundY + (startY - groundY) * (1 - f);
        }

        @Override protected void update() {
            if (age < impact) {
                double want = heightAt(age + 1);
                boss.move(MoverType.SELF, new Vec3(0, want - boss.getY(), 0));
                boss.setDeltaMovement(Vec3.ZERO);
                if (age % 2 == 0) {
                    for (int side = -1; side <= 1; side += 2) {
                        Vec3 foot = RigAnchors.approximate(boss, side < 0 ? "foot_l" : "foot_r").position();
                        Sfx.burst(level, ParticleTypes.FLAME, foot.add(0, -.4, 0), 6, .15, .3, .05);
                        Sfx.burst(level, Sfx.ember(Fx.P1_HOT), foot.add(0, -.8, 0), 4, .2, .4, .2);
                        Sfx.burst(level, Sfx.smoke(0xff3a2a20), foot.add(0, -1.4, 0), 2, .3, .3, .02);
                    }
                }
            }
        }

        void impact() {
            boss.move(MoverType.SELF, new Vec3(0, groundY - boss.getY(), 0));
            boss.setNoGravity(false);
            Vec3 at = boss.position();
            Hits.area(boss, at, 9, 20, .35f, 2.2, .9, Hits.blast(boss, boss));
            Sfx.slam(level, at, .55f);
            Sfx.debris(level, at, 9, 220, Fx.P1_HOT, 0xff4a3a2a);
            Sfx.burst(level, ParticleTypes.EXPLOSION_EMITTER, at.add(0, .5, 0), 1, 0, 0, 0);
        }

        @Override protected void cleanup(boolean interrupted) { boss.setNoGravity(false); }
    }

    /** ground_slam: fist into the ground in front, crater, burnt ground, shockwave; recovery is a weak window. */
    static final class Slam extends Action {
        Vec3 point;
        Slam(GuilherminoBoss boss, LivingEntity target) { super(boss, target); }
        @Override public String name() { return "slam"; }

        @Override protected void begin() {
            turnToward(targetPos(), 180);
            point = groundAt(local(0, 0, 3.4));
            play(JG_GROUND_SLAM);
            own(Fx.SLAM.start(level, boss, Placement.at(point, boss.getYRot(), 0)));
            on(JG_GROUND_SLAM, "windup_start", () -> Sfx.servo(level, boss.position(), .6f));
            on(JG_GROUND_SLAM, "windup_peak", () -> Sfx.charge(level, boss.position(), .7f));
            on(JG_GROUND_SLAM, "impact", () -> {
                Hits.area(boss, point, 8, 22, .35f, 2, .9, Hits.melee(boss));
                Sfx.slam(level, point, .65f);
                Sfx.debris(level, point, 7, 170, Fx.P1_HOT, 0xff4a3a2a);
                damageMultiplier = 1.3f;
                boss.setFlag(GuilherminoBoss.F_VULNERABLE, true);
            });
            on(JG_GROUND_SLAM, "fist_lift", () -> Sfx.servo(level, boss.position(), .8f));
            endWith(JG_GROUND_SLAM);
        }

        @Override protected void update() { if (age < Fx.t(JG_GROUND_SLAM, "windup_peak") - 6) turnToward(targetPos(), 2); }
        @Override protected void cleanup(boolean interrupted) { boss.setFlag(GuilherminoBoss.F_VULNERABLE, false); }
    }

    /** reactor_charge → reactor_fire: exposed reactor while charging (stagger counterplay), a multi-layer beam, a trench. */
    static final class Reactor extends Action {
        Vec3 origin, end;
        Optional<Effects.Handle> charge = Optional.empty();
        Reactor(GuilherminoBoss boss, LivingEntity target) { super(boss, target); }
        @Override public String name() { return "reactor"; }
        @Override protected boolean interruptible() { return age < Fx.t(JG_REACTOR_CHARGE, "ready_to_fire"); }

        @Override protected void begin() {
            play(JG_REACTOR_CHARGE);
            charge = own(Effects.start(Fx.REACTOR_CHARGE, level, Placement.on(boss)));
            damageMultiplier = 1.5f;
            boss.setFlag(GuilherminoBoss.F_VULNERABLE, true);
            on(JG_REACTOR_CHARGE, "charge_start", () -> Sfx.charge(level, boss.position(), .5f));
            on(JG_REACTOR_CHARGE, "arms_open", () -> Sfx.servo(level, boss.position(), .7f));
            for (String s : new String[]{"surge_1", "surge_2", "surge_3"}) on(JG_REACTOR_CHARGE, s, () -> {
                Sfx.charge(level, boss.position(), .7f + boss.getRandom().nextFloat() * .3f);
                Sfx.burst(level, Sfx.arc(Fx.P1_CORE), reactor(), 10, .6, .6, .1);
            });
            on(JG_REACTOR_CHARGE, "full_charge", () -> Sfx.electric(level, boss.position(), 2));
            on(JG_REACTOR_CHARGE, "clench", () -> Sfx.servo(level, boss.position(), .9f));
            on(JG_REACTOR_CHARGE, "ready_to_fire", this::fire);
        }

        Vec3 reactor() { return RigAnchors.approximate(boss, "reactor").position(); }

        void fire() {
            charge.ifPresent(Effects.Handle::stop);
            damageMultiplier = 1.15f;
            boss.setFlag(GuilherminoBoss.F_VULNERABLE, false);
            play(JG_REACTOR_FIRE);
            origin = reactor();
            Vec3 aim = target != null && target.isAlive() ? target.getEyePosition().subtract(0, .4, 0).add(target.getDeltaMovement().scale(6)) : origin.add(forward().scale(30));
            Vec3 dir = aim.subtract(origin).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), pitch = (float) -Math.toDegrees(Math.asin(dir.y));
            boss.setYRot(yaw);
            boss.yBodyRot = yaw;
            boss.yHeadRot = yaw;
            own(Effects.start(Fx.REACTOR_BEAM, level, Placement.at(origin, yaw, pitch)));
            BlockHitResult hit = Hits.ground(boss, origin, origin.add(dir.scale(48)));
            end = hit != null ? hit.getLocation() : origin.add(dir.scale(48));
            on(JG_REACTOR_FIRE, "fire", () -> {
                Sfx.beam(level, origin, false);
                if (hit != null) {
                    Fx.BEAM_SCAR.start(level, boss, Placement.at(end, yaw, 0));
                    Sfx.debris(level, end, 3, 80, Fx.P1_HOT, 0xff3a2a1a);
                }
            });
            int from = tickOf(JG_REACTOR_FIRE, "fire"), to = tickOf(JG_REACTOR_FIRE, "stabilized");
            for (int k = from; k <= to; k += 3) at(k, () -> {
                for (LivingEntity e : Hits.capsule(boss, origin, end, 1.3, 9, .7, .2, Hits.energy(boss), null)) e.igniteForSeconds(4);
            });
            on(JG_REACTOR_FIRE, "recoil_peak", () -> Sfx.play(level, boss.position(), SoundEvents.ANVIL_LAND, 1.4f, .5f));
            on(JG_REACTOR_FIRE, "stabilized", () -> Sfx.servo(level, boss.position(), .6f));
            endWith(JG_REACTOR_FIRE);
        }

        @Override protected void update() { if (origin == null) turnToward(targetPos(), 4); }
        @Override protected void cleanup(boolean interrupted) { boss.setFlag(GuilherminoBoss.F_VULNERABLE, false); }
    }

    /** punch_right / punch_left: hydraulic punch; a right punch may chain into a left one. */
    static final class Punch extends Action {
        final boolean right;
        Punch(GuilherminoBoss boss, LivingEntity target, boolean right) { super(boss, target); this.right = right; }
        @Override public String name() { return right ? "punch_right" : "punch_left"; }

        @Override protected void begin() {
            Clip c = right ? JG_PUNCH_RIGHT : JG_PUNCH_LEFT;
            play(c);
            own(Effects.start(right ? Fx.PUNCH_R : Fx.PUNCH_L, level, Placement.on(boss)));
            on(c, "launch", () -> {
                Sfx.servo(level, boss.position(), 1.3f);
                Vec3 hand = RigAnchors.approximate(boss, right ? "hand_r" : "hand_l").position();
                Sfx.burst(level, ParticleTypes.CLOUD, hand.add(0, .8, -.3), 8, .2, .2, .05);
            });
            on(c, "hit", () -> {
                Vec3 o = boss.position().add(0, 1.5, 0);
                int n = Hits.cone(boss, o, forward(), 5.8, 55, 16, 1.7, .45, new HashSet<>());
                Sfx.play(level, o, SoundEvents.IRON_GOLEM_ATTACK, 3, .6f);
                Sfx.play(level, o, SoundEvents.ANVIL_LAND, n > 0 ? 1.2f : .5f, .7f);
                Vec3 fist = local(right ? 1 : -1, 0, 3.6);
                Sfx.burst(level, Sfx.ember(Fx.P1_HOT), fist.add(0, 1.2, 0), 14, .3, .3, .25);
            });
            endWith(c);
            if (right) brainQueue();
        }

        void brainQueue() {
            if (boss.getRandom().nextFloat() < .55f && target != null && boss.distanceTo(target) < 6.5)
                boss.brain().queue(() -> new Punch(boss, target, false));
        }

        @Override protected void update() { if (age < 8) turnToward(targetPos(), 8); }
    }

    /** dash_start → run → dash_end: a straight heavy charge; contact damage once per entity; brakes with sparks. */
    static final class Dash extends Action {
        Vec3 dir;
        Optional<Effects.Handle> trail = Optional.empty();
        final Set<UUID> hit = new HashSet<>();
        int runStart = -1, braking = -1;
        double traveled;
        Dash(GuilherminoBoss boss, LivingEntity target) { super(boss, target); }
        @Override public String name() { return "dash"; }

        @Override protected void begin() {
            play(JG_DASH_START);
            trail = own(Effects.start(Fx.DASH, level, Placement.on(boss).followingBody()));
            on(JG_DASH_START, "coiled", () -> Sfx.servo(level, boss.position(), .5f));
            on(JG_DASH_START, "launch", () -> {
                dir = targetPos().subtract(boss.position()).multiply(1, 0, 1).normalize();
                Sfx.play(level, boss.position(), SoundEvents.FIRECHARGE_USE, 3, .6f);
                Sfx.play(level, boss.position(), SoundEvents.BLAZE_SHOOT, 2, .5f);
            });
            on(JG_DASH_START, "chain_to_run", () -> {
                play(JG_RUN);
                boss.setFlag(GuilherminoBoss.F_RUNNING, true);
                runStart = age;
            });
        }

        @Override protected void update() {
            if (dir == null) { turnToward(targetPos(), 6); return; }
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            boss.setYRot(yaw);
            boss.yBodyRot = yaw;
            double speed = braking >= 0 ? Math.max(0, .95 * Math.pow(.72, age - braking)) : (runStart < 0 ? .55 : .95);
            Vec3 before = boss.position();
            boss.move(MoverType.SELF, dir.scale(speed));
            traveled += boss.position().distanceTo(before);
            if (braking < 0) {
                Vec3 p = boss.position().add(0, 1.6, 0);
                Hits.capsule(boss, p, p.add(dir.scale(2.6)), 2.2, 18, 2.6, .7, Hits.melee(boss), hit);
                boolean passed = target != null && target.position().subtract(boss.position()).dot(dir) < -2;
                if (runStart >= 0 && (boss.horizontalCollision || traveled > 30 || age - runStart > 30 || passed)) brake();
            } else if (age - braking < 10 && age % 2 == 0) {
                Sfx.burst(level, Sfx.ember(Fx.P1_HOT), boss.position().add(dir.scale(1.5)), 10, .8, .1, .35);
                Sfx.burst(level, ParticleTypes.LARGE_SMOKE, boss.position(), 3, .8, .1, .02);
            }
        }

        void brake() {
            braking = age;
            boss.setFlag(GuilherminoBoss.F_RUNNING, false);
            trail.ifPresent(Effects.Handle::release);
            play(JG_DASH_END);
            Sfx.play(level, boss.position(), SoundEvents.MINECART_RIDING, 2, .5f);
            Sfx.play(level, boss.position(), SoundEvents.ANVIL_LAND, 1, .5f);
            on(JG_DASH_END, "step_left", () -> Sfx.heavyStep(level, boss.position()));
            on(JG_DASH_END, "step_right", () -> Sfx.heavyStep(level, boss.position()));
            on(JG_DASH_END, "feet_set", () -> Sfx.servo(level, boss.position(), .8f));
            endWith(JG_DASH_END);
            damageMultiplier = 1.25f;
        }

        @Override protected void cleanup(boolean interrupted) { boss.setFlag(GuilherminoBoss.F_RUNNING, false); }
    }

    /** stagger: the pilot loses the machine for two seconds; damage taken x1.5. */
    static final class Stagger extends Action {
        Stagger(GuilherminoBoss boss, LivingEntity target) { super(boss, target); }
        @Override public String name() { return "stagger"; }

        @Override protected void begin() {
            play(JG_STAGGER);
            own(Effects.start(Fx.STAGGER, level, Placement.on(boss)));
            damageMultiplier = 1.5f;
            boss.setFlag(GuilherminoBoss.F_VULNERABLE, true);
            on(JG_STAGGER, "hit_received", () -> {
                Sfx.play(level, boss.position(), SoundEvents.IRON_GOLEM_DAMAGE, 3, .5f);
                Sfx.electric(level, boss.position(), 1.5f);
            });
            on(JG_STAGGER, "catch_step_plant", () -> Sfx.heavyStep(level, boss.position()));
            on(JG_STAGGER, "regain_control", () -> Sfx.servo(level, boss.position(), .7f));
            on(JG_STAGGER, "recover_step_plant", () -> Sfx.heavyStep(level, boss.position()));
            endWith(JG_STAGGER);
        }

        @Override protected void cleanup(boolean interrupted) { boss.setFlag(GuilherminoBoss.F_VULNERABLE, false); }
    }

    /** phase_transition at half health: malfunction, the pilot takes back control and roars; the fight speeds up. */
    static final class Fury extends Action {
        Fury(GuilherminoBoss boss, LivingEntity target) { super(boss, target); }
        @Override public String name() { return "fury"; }

        @Override protected void begin() {
            play(JG_PHASE_TRANSITION);
            own(Effects.start(Fx.FURY, level, Placement.on(boss)));
            damageMultiplier = .5f;
            on(JG_PHASE_TRANSITION, "glitch_onset", () -> Sfx.electric(level, boss.position(), 1.5f));
            on(JG_PHASE_TRANSITION, "max_malfunction", () -> {
                Sfx.alarm(level, boss.position());
                Sfx.burst(level, Sfx.arc(Fx.P1_CORE), boss.position().add(0, 3, 0), 30, 1.2, 1.2, .2);
            });
            on(JG_PHASE_TRANSITION, "pilot_regains_control", () -> Sfx.servo(level, boss.position(), .5f));
            on(JG_PHASE_TRANSITION, "roar_peak", () -> {
                Sfx.play(level, boss.position(), SoundEvents.RAVAGER_ROAR, 4, .5f);
                Sfx.play(level, boss.position(), SoundEvents.WARDEN_ROAR, 2, .7f);
                Hits.area(boss, boss.position(), 10, 6, .2f, 2.4, .8, Hits.melee(boss));
                ((Phase1Brain) boss.brain()).enraged = true;
                Encounter e = boss.encounter();
                if (e != null) e.signal(boss, "furia");
            });
            on(JG_PHASE_TRANSITION, "aggressive_stance", () -> damageMultiplier = 1);
            endWith(JG_PHASE_TRANSITION);
        }
    }

    /**
     * overload: the cinematic transformation. Mechanical failures, pulses, alarms, the orange reactor dies at
     * pause_start, the machine rises in the dark and, under the release flash and smoke, the encounter swaps the
     * entity for the second form.
     */
    static final class Transform extends Action {
        Transform(GuilherminoBoss boss, LivingEntity target) { super(boss, target); invulnerable = true; }
        @Override public String name() { return "transform"; }

        @Override protected void begin() {
            boss.setFlag(GuilherminoBoss.F_INVULNERABLE, true);
            boss.getNavigation().stop();
            play(JG_OVERLOAD);
            Encounter e = boss.encounter();
            Effects.start(Fx.TRANSFORM, level, Placement.at(boss.position(), boss.getYRot(), 0));
            if (e != null) {
                e.signal(boss, "sobrecarga");
                e.startCams(Fx.CAM_TRANSFORM, boss.position(), boss.getYRot());
            }
            on(JG_OVERLOAD, "overload_start", () -> Sfx.play(level, boss.position(), SoundEvents.BEACON_DEACTIVATE, 3, .5f));
            on(JG_OVERLOAD, "reactor_surge", () -> { Sfx.electric(level, boss.position(), 2.5f); Sfx.charge(level, boss.position(), .4f); });
            on(JG_OVERLOAD, "legs_give", () -> Sfx.servo(level, boss.position(), .4f));
            on(JG_OVERLOAD, "knee_and_fists_impact", () -> {
                Sfx.slam(level, boss.position(), .8f);
                Sfx.debris(level, boss.position().add(forward().scale(2)), 4, 80, Fx.P1_HOT, 0xff3a2a1a);
                Hits.area(boss, boss.position(), 5, 0, 0, 1.2, .4, Hits.melee(boss));
            });
            String[] pulses = {"pulse_1", "pulse_2", "pulse_3", "pulse_4", "pulse_5", "pulse_6", "pulse_7", "pulse_8_peak"};
            for (int i = 0; i < pulses.length; i++) {
                final int k = i;
                on(JG_OVERLOAD, pulses[i], () -> {
                    Sfx.electric(level, boss.position(), 1 + k * .2f);
                    if (k % 2 == 0) Sfx.alarm(level, boss.position());
                    Sfx.burst(level, Sfx.arc(k == 7 ? 0xffffffff : Fx.P1_CORE), boss.position().add(0, 2.4, 0), 12 + k * 4, 1.4, 1.4, .25);
                });
            }
            on(JG_OVERLOAD, "pause_start", () -> {
                boss.setFlag(GuilherminoBoss.F_REACTOR_DARK, true);
                Sfx.play(level, boss.position(), SoundEvents.BEACON_DEACTIVATE, 4, .4f);
                Sfx.burst(level, Sfx.smoke(0xff2a2420), boss.position().add(0, 3, 0), 40, 1, 1, .02);
            });
            on(JG_OVERLOAD, "pilot_regains", () -> Sfx.play(level, boss.position(), SoundEvents.WARDEN_HEARTBEAT, 4, .7f));
            on(JG_OVERLOAD, "rise_anticipation", () -> Sfx.servo(level, boss.position(), .5f));
            on(JG_OVERLOAD, "launch", () -> Sfx.servo(level, boss.position(), .8f));
            on(JG_OVERLOAD, "stand_impact", () -> Sfx.heavyStep(level, boss.position()));
            on(JG_OVERLOAD, "arms_open_threat", () -> Sfx.play(level, boss.position(), SoundEvents.RAVAGER_ROAR, 4, .45f));
            on(JG_OVERLOAD, "threat_release", () -> {
                Sfx.explosion(level, boss.position().add(0, 2, 0), 5);
                Sfx.burst(level, Sfx.smoke(0xff1c1c20), boss.position().add(0, 2.5, 0), 260, 3, 2.2, .08);
                Sfx.burst(level, Sfx.arc(Fx.P2_HOT), boss.position().add(0, 2.5, 0), 60, 2, 2, .4);
                Hits.area(boss, boss.position(), 9, 4, .3f, 2.6, .8, Hits.blast(boss, boss));
            });
            at(Fx.t(JG_OVERLOAD, "threat_release") + 2, () -> {
                Encounter enc = boss.encounter();
                if (enc != null) enc.swapToPhase2((JarGolem) boss);
                finish();
            });
            endAt(Fx.t(JG_OVERLOAD, "threat_release") + 4);
        }

        @Override protected void cleanup(boolean interrupted) {
            if (interrupted && boss.isAlive()) boss.setFlag(GuilherminoBoss.F_INVULNERABLE, false);
        }
    }

    /** death of the first form (only when the transformation was bypassed: void, removal by a command). */
    static final class Death extends Action {
        Death(GuilherminoBoss boss) { super(boss, null); invulnerable = true; }
        @Override public String name() { return "death1"; }

        @Override protected void begin() {
            play(JG_DEATH);
            Effects.start(Fx.DEATH1, level, Placement.at(boss.position(), boss.getYRot(), 0));
            on(JG_DEATH, "right_arm_power_loss", () -> Sfx.electric(level, boss.position(), 1));
            on(JG_DEATH, "legs_buckle", () -> Sfx.servo(level, boss.position(), .4f));
            on(JG_DEATH, "collapse", () -> Sfx.play(level, boss.position(), SoundEvents.IRON_GOLEM_DEATH, 3, .5f));
            on(JG_DEATH, "knee_impact", () -> Sfx.heavyStep(level, boss.position()));
            on(JG_DEATH, "ground_impact", () -> {
                Sfx.slam(level, boss.position(), .9f);
                Sfx.debris(level, boss.position().add(forward().scale(2.5)), 4, 90, Fx.P1_HOT, 0xff3a2a1a);
            });
            endWith(JG_DEATH);
        }
    }
}
