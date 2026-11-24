package dev.bossestesting.ai;

import dev.bossestesting.anim.Clip;
import dev.bossestesting.combat.Hits;
import dev.bossestesting.encounter.Encounter;
import dev.bossestesting.entity.GuilherminoBoss;
import dev.bossestesting.entity.GuilherminoFinale;
import dev.bossestesting.entity.GuilherminoMissile;
import dev.bossestesting.fx.Fx;
import dev.bossestesting.fx.Sfx;
import dev.calamidade_awakening.api.v1.Effects;
import dev.calamidade_awakening.api.v1.Placement;
import dev.calamidade_awakening.api.v1.RigAnchors;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.bossestesting.anim.Anims.*;

/**
 * AI of El Guilhermino Finale: Ground Slam V2, Missile Barrage, Punch Stream, Exposed Reactor, Overload V2 (enters
 * the Overloaded state for 30 s) and, only while Overloaded, Charged Punch, Shield Charge and Leap Smash.
 */
public final class Phase2Brain extends Brain {
    /** Length of the Overloaded state, ticks. */
    public static final int OVERLOADED_TICKS = 600;
    private long overloadedUntil = -1;
    private boolean forcedOverload;
    private boolean revealPending;

    public Phase2Brain(GuilherminoFinale boss) {
        super(boss);
        move(new Move("slam_v2", 220, 0, 8.5, c -> 2.2 + c.nearbyPlayers() * 1.2, SlamV2::new));
        move(new Move("missile_barrage", 380, 9, 60, c -> 2 + (c.distance() > 16 ? 2 : 0), Barrage::new));
        move(new Move("punch_stream", 120, 0, 5.5, c -> 3, PunchStream::new));
        move(new Move("exposed_reactor", 340, 8, 44, c -> 2.2 + (c.distance() > 14 ? 1 : 0), Exposed::new));
        move(new Move("overload_v2", 900, 0, 60, c -> !c.overloaded() && c.health() < .6f ? 1.5 : 0, OverloadV2::new));
        move(new Move("charged_punch", 150, 0, 9, c -> c.overloaded() ? 3.2 : 0, ChargedPunch::new));
        move(new Move("shield_charge", 200, 7, 32, c -> c.overloaded() ? 2.6 + (c.distance() > 14 ? 1 : 0) : 0, ShieldCharge::new));
        move(new Move("leap_smash", 240, 8, 30, c -> c.overloaded() ? 2.8 : 0, Leap::new));
    }

    public boolean overloaded() { return boss.flag(GuilherminoBoss.F_OVERLOADED); }

    /** Starts the Overloaded state (from overload_v2 or a command). */
    public void enterOverloaded() {
        overloadedUntil = boss.level().getGameTime() + OVERLOADED_TICKS;
        boss.setFlag(GuilherminoBoss.F_OVERLOADED, true);
        Encounter e = boss.encounter();
        if (e != null) e.signal(boss, "overload_on");
    }

    /** Ends the Overloaded state now (timer or command). */
    public void exitOverloaded() {
        overloadedUntil = -1;
        if (!overloaded()) return;
        boss.setFlag(GuilherminoBoss.F_OVERLOADED, false);
        Encounter e = boss.encounter();
        if (e != null) e.signal(boss, "overload_off");
        Effects.start(Fx.OVERLOAD_VENT, level(), Placement.on(boss));
        Sfx.play(level(), boss.position(), SoundEvents.FIRE_EXTINGUISH, 3, .5f);
        Sfx.burst(level(), ParticleTypes.CLOUD, boss.position().add(0, 3, 0), 40, 1, 1, .1);
    }

    /** The reveal right after the swap (the entity is new and the music starts with it). */
    public void reveal() { revealPending = true; }

    @Override
    protected Action forced(LivingEntity target) {
        if (revealPending) { revealPending = false; return new Reveal(boss); }
        if (overloaded() && overloadedUntil > 0 && boss.level().getGameTime() >= overloadedUntil) exitOverloaded();
        if (!forcedOverload && !overloaded() && boss.getHealth() <= boss.getMaxHealth() * .5f) {
            forcedOverload = true;
            startCooldown("overload_v2", 900);
            return new OverloadV2(boss, target);
        }
        return null;
    }

    @Override protected Action death() { return new Death(boss); }
    @Override protected double chaseSpeed() { return overloaded() ? 1.25 : 1.0; }
    @Override protected double preferredDistance() { return overloaded() ? 3.5 : 4.5; }

    // ================================================================== actions

    /** The second form appears: reactor ignition (cyan), the music starts on this tick. */
    static final class Reveal extends Action {
        Reveal(GuilherminoBoss boss) { super(boss, null); invulnerable = true; }
        @Override public String name() { return "reveal"; }

        @Override protected void begin() {
            boss.clearClip();
            boss.setFlag(GuilherminoBoss.F_IGNITING, true);
            Effects.start(Fx.REVEAL, level, Placement.at(boss.position(), boss.getYRot(), 0));
            Encounter e = boss.encounter();
            if (e != null) e.startMusic();
            Sfx.play(level, boss.position(), SoundEvents.BEACON_ACTIVATE, 4, .6f);
            Sfx.electric(level, boss.position(), 3);
            after(6, () -> Sfx.play(level, boss.position(), SoundEvents.WARDEN_ROAR, 3, .8f));
            endAt(34);
        }

        @Override protected void cleanup(boolean interrupted) { boss.setFlag(GuilherminoBoss.F_IGNITING, false); }
    }

    /** groundslam_v2: the broken arm reboots, then a double cyan shockwave tears a ring of craters. */
    static final class SlamV2 extends Action {
        Vec3 point;
        SlamV2(GuilherminoBoss boss, LivingEntity target) { super(boss, target); }
        @Override public String name() { return "slam_v2"; }

        @Override protected void begin() {
            turnToward(targetPos(), 180);
            point = groundAt(local(0, 0, 3.6));
            Clip c = P2_GROUNDSLAM_V2;
            play(c);
            own(Fx.SLAM_V2.start(level, boss, Placement.at(point, boss.getYRot(), 0)));
            on(c, "adjust_windup", () -> Sfx.servo(level, boss.position(), .6f));
            on(c, "adjust_hit_left_arm", () -> {
                Vec3 w = RigAnchors.approximate(boss, "wires_l").position();
                Sfx.burst(level, Sfx.arc(Fx.P2_CORE), w, 16, .3, .3, .2);
                Sfx.electric(level, w, 1);
            });
            on(c, "left_arm_reboot", () -> Sfx.play(level, boss.position(), SoundEvents.BEACON_DEACTIVATE, 2, 1.4f));
            on(c, "left_arm_online", () -> Sfx.play(level, boss.position(), SoundEvents.BEACON_ACTIVATE, 2, 1.6f));
            on(c, "windup_peak", () -> Sfx.charge(level, boss.position(), .8f));
            on(c, "impact", () -> {
                Hits.area(boss, point, 11, 26, .35f, 2.4, 1, Hits.melee(boss));
                Sfx.slam(level, point, .75f);
                Sfx.electric(level, point, 2.5f);
                Sfx.debris(level, point, 9, 220, Fx.P2_HOT, 0xff1e2a2e);
                boss.setFlag(GuilherminoBoss.F_VULNERABLE, true);
                damageMultiplier = 1.3f;
            });
            at(tickOf(c, "impact") + 7, () -> {
                Hits.area(boss, point, 17, 10, .4f, 1.4, .5, Hits.energy(boss));
                Sfx.burst(level, Sfx.arc(Fx.P2_RED), point.add(0, .5, 0), 40, 8, .3, .3);
            });
            on(c, "fists_lift", () -> Sfx.servo(level, boss.position(), .8f));
            endWith(c);
        }

        @Override protected void cleanup(boolean interrupted) { boss.setFlag(GuilherminoBoss.F_VULNERABLE, false); }
    }

    /** missile_barrage: kneel, open the pods, six alternating salvos of three missiles, lock-on over the targets. */
    static final class Barrage extends Action {
        final List<LivingEntity> targets = new ArrayList<>();
        Barrage(GuilherminoBoss boss, LivingEntity target) { super(boss, target); }
        @Override public String name() { return "missile_barrage"; }

        @Override protected void begin() {
            Clip c = P2_MISSILE_BARRAGE;
            turnToward(targetPos(), 180);
            play(c);
            own(Effects.start(Fx.BARRAGE, level, Placement.on(boss)));
            Encounter e = boss.encounter();
            for (Player p : level.players())
                if (targets.size() < 4 && p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceTo(boss) < 60 && (e == null || e.isParticipant(p)))
                    targets.add(p);
            if (targets.isEmpty() && target != null) targets.add(target);
            on(c, "kneel_impact", () -> {
                Sfx.heavyStep(level, boss.position());
                Sfx.debris(level, boss.position(), 3, 40, Fx.P2_HOT, 0xff1e2a2e);
                damageMultiplier = 1.25f;
                boss.setFlag(GuilherminoBoss.F_VULNERABLE, true);
            });
            on(c, "pods_opening", () -> Sfx.servo(level, boss.position(), .9f));
            on(c, "pods_open", () -> {
                Sfx.play(level, boss.position(), SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, 3, 1.2f);
                for (LivingEntity t : targets) own(Effects.start(Fx.MISSILE_LOCK, level, Placement.on(t)));
            });
            String[] salvos = {"salvo_1_right", "salvo_2_left", "salvo_3_right", "salvo_4_left", "salvo_5_right", "salvo_6_left"};
            for (int i = 0; i < salvos.length; i++) {
                final int salvo = i;
                on(c, salvos[i], () -> salvo(salvo));
            }
            on(c, "racks_lowering", () -> Sfx.servo(level, boss.position(), .7f));
            on(c, "pods_closed", () -> { boss.setFlag(GuilherminoBoss.F_VULNERABLE, false); damageMultiplier = 1; });
            on(c, "rise_start", () -> Sfx.servo(level, boss.position(), .5f));
            endWith(c);
        }

        /** Three tubes of one row of one pod: right pod on even salvos, front row on salvos 0, 1, 4, 5. */
        void salvo(int i) {
            boolean right = i % 2 == 0;
            boolean front = i < 2 || i > 3;
            RigAnchors.Pose rack = RigAnchors.approximate(boss, right ? "rack_r" : "rack_l");
            Sfx.play(level, rack.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 3, .6f);
            Sfx.play(level, rack.position(), SoundEvents.BLAZE_SHOOT, 2, 1.3f);
            for (int tube = 0; tube < 3; tube++) {
                double side = (tube - 1) * .19 * (right ? 1 : -1);
                Vec3 start = rack.at(side, .3, front ? .14 : -.12);
                LivingEntity t = targets.get((i * 3 + tube) % targets.size());
                Vec3 out = rack.position().subtract(boss.position()).multiply(1, 0, 1).normalize();
                Vec3 launch = new Vec3(0, 1, 0).add(out.scale(.35)).add(forward().scale(.25)).normalize();
                double ang = boss.getRandom().nextDouble() * Math.TAU, r = 1 + boss.getRandom().nextDouble() * 3.2;
                Vec3 aim = t.position().add(t.getDeltaMovement().multiply(1, 0, 1).scale(22)).add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
                GuilherminoMissile m = new GuilherminoMissile(level, boss, start, launch, aim, t, tube * 3 + i);
                level.addFreshEntity(m);
                Encounter e = boss.encounter();
                if (e != null) e.trackMissile(m);
            }
            Sfx.burst(level, ParticleTypes.LARGE_SMOKE, rack.position().add(0, .6, 0), 12, .3, .2, .05);
        }

        @Override protected void cleanup(boolean interrupted) { boss.setFlag(GuilherminoBoss.F_VULNERABLE, false); }
    }

    /** punch_stream: four quick blows (each lands, i-frames reset) and a heavy fifth. */
    static final class PunchStream extends Action {
        PunchStream(GuilherminoBoss boss, LivingEntity target) { super(boss, target); }
        @Override public String name() { return "punch_stream"; }

        @Override protected void begin() {
            Clip c = P2_PUNCH_STREAM;
            play(c);
            own(Effects.start(Fx.PUNCH_STREAM, level, Placement.on(boss).followingBody()));
            on(c, "guard_up", () -> Sfx.servo(level, boss.position(), 1.1f));
            String[] hits = {"hit_1", "hit_2", "hit_3", "hit_4"};
            for (int i = 0; i < hits.length; i++) {
                final int k = i;
                on(c, hits[i], () -> {
                    Vec3 o = boss.position().add(0, 1.6, 0);
                    Hits.cone(boss, o, forward(), 5.2, 50, 7, .5, .15, null);
                    Sfx.play(level, o, SoundEvents.IRON_GOLEM_ATTACK, 2, .8f + k * .08f);
                    Sfx.burst(level, Sfx.arc(k % 2 == 0 ? Fx.P2_HOT : Fx.P2_RED), local(k % 2 == 0 ? 1 : -1, 1.4, 3.4), 8, .3, .3, .2);
                });
            }
            on(c, "hit_5_heavy", () -> {
                Vec3 o = boss.position().add(0, 1.6, 0);
                Hits.cone(boss, o, forward(), 6.2, 60, 18, 2.4, .6, null);
                Sfx.slam(level, local(0, 0, 3), 1.1f);
                Sfx.debris(level, local(0, 0, 3.2), 3, 60, Fx.P2_HOT, 0xff1e2a2e);
            });
            on(c, "recovery", () -> { damageMultiplier = 1.2f; Sfx.servo(level, boss.position(), .7f); });
            endWith(c);
        }

        @Override protected void update() { if (age < tickOf(P2_PUNCH_STREAM, "hit_1")) turnToward(targetPos(), 10); else turnToward(targetPos(), 2); }
    }

    /** exposed_reactor_charge → exposed_reactor_fire: chest open (damage x2), unstable cyan/red beam, fused trench. */
    static final class Exposed extends Action {
        Vec3 origin, end;
        Optional<Effects.Handle> charge = Optional.empty();
        Exposed(GuilherminoBoss boss, LivingEntity target) { super(boss, target); }
        @Override public String name() { return "exposed_reactor"; }

        @Override protected void begin() {
            Clip c = P2_EXPOSED_REACTOR_CHARGE;
            play(c);
            charge = own(Effects.start(Fx.EXPOSED_CHARGE, level, Placement.on(boss)));
            on(c, "charge_start", () -> Sfx.charge(level, boss.position(), .6f));
            on(c, "chest_exposed", () -> {
                damageMultiplier = 2;
                boss.setFlag(GuilherminoBoss.F_VULNERABLE, true);
                Sfx.servo(level, boss.position(), .9f);
                Sfx.alarm(level, boss.position());
            });
            for (String s : new String[]{"surge_1", "surge_2", "surge_3"})
                on(c, s, () -> { Sfx.charge(level, boss.position(), .9f + boss.getRandom().nextFloat() * .3f); Sfx.electric(level, boss.position(), 1); });
            on(c, "full_charge", () -> Sfx.electric(level, boss.position(), 2.5f));
            on(c, "ready_to_fire", this::fire);
        }

        void fire() {
            charge.ifPresent(Effects.Handle::stop);
            Clip f = P2_EXPOSED_REACTOR_FIRE;
            play(f);
            origin = RigAnchors.approximate(boss, "reactor").position();
            Vec3 aim = target != null && target.isAlive() ? target.getEyePosition().subtract(0, .5, 0).add(target.getDeltaMovement().scale(7)) : origin.add(forward().scale(30));
            Vec3 dir = aim.subtract(origin).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), pitch = (float) -Math.toDegrees(Math.asin(dir.y));
            boss.setYRot(yaw);
            boss.yBodyRot = yaw;
            boss.yHeadRot = yaw;
            own(Effects.start(Fx.EXPOSED_BEAM, level, Placement.at(origin, yaw, pitch)));
            BlockHitResult hit = Hits.ground(boss, origin, origin.add(dir.scale(52)));
            end = hit != null ? hit.getLocation() : origin.add(dir.scale(52));
            on(f, "fire", () -> {
                Sfx.beam(level, origin, true);
                if (hit != null) {
                    Fx.EXPOSED_SCAR.start(level, boss, Placement.at(end, yaw, 0));
                    Sfx.debris(level, end, 3, 90, Fx.P2_HOT, 0xff1e2a2e);
                }
            });
            int from = tickOf(f, "fire"), to = tickOf(f, "heart_spent") + 2;
            for (int k = from; k <= to; k += 2) at(k, () -> Hits.capsule(boss, origin, end, 1.5, 6, .5, .15, Hits.energy(boss), null));
            on(f, "heart_spent", () -> { damageMultiplier = 1.6f; Sfx.play(level, boss.position(), SoundEvents.BEACON_DEACTIVATE, 3, .8f); });
            on(f, "left_arm_drops", () -> {
                Vec3 w = RigAnchors.approximate(boss, "wires_l").position();
                Sfx.burst(level, Sfx.arc(Fx.P2_RED), w, 18, .3, .3, .25);
                Sfx.electric(level, w, 1.2f);
            });
            endWith(f);
        }

        @Override protected void update() { if (origin == null) turnToward(targetPos(), 3); }
        @Override protected void cleanup(boolean interrupted) { boss.setFlag(GuilherminoBoss.F_VULNERABLE, false); }
    }

    /** overload_v2: the pilot punches his own reactor; on "stabilized" the Overloaded state starts. */
    static final class OverloadV2 extends Action {
        OverloadV2(GuilherminoBoss boss, LivingEntity target) { super(boss, target); damageMultiplier = .5f; }
        @Override public String name() { return "overload_v2"; }

        @Override protected void begin() {
            Clip c = P2_OVERLOAD_V2;
            play(c);
            own(Effects.start(Fx.OVERLOAD_V2, level, Placement.on(boss)));
            on(c, "windup_peak", () -> Sfx.servo(level, boss.position(), .6f));
            // punch_reactor (events file only) and reactor_surge (animation) share this tick: handled once, here.
            on(c, "reactor_surge", () -> {
                Sfx.play(level, boss.position(), SoundEvents.ANVIL_LAND, 3, .6f);
                Sfx.electric(level, boss.position(), 3);
                Sfx.charge(level, boss.position(), 1.2f);
                Sfx.burst(level, Sfx.arc(0xffffffff), RigAnchors.approximate(boss, "reactor").position(), 40, .8, .8, .5);
            });
            on(c, "tremor_peak", () -> {
                Hits.area(boss, boss.position(), 8, 10, .3f, 2.2, .7, Hits.energy(boss));
                Sfx.debris(level, boss.position(), 6, 120, Fx.P2_HOT, 0xff1e2a2e);
            });
            on(c, "fist_withdraw", () -> Sfx.servo(level, boss.position(), .8f));
            on(c, "stabilized", () -> {
                ((Phase2Brain) boss.brain()).enterOverloaded();
                Sfx.play(level, boss.position(), SoundEvents.WARDEN_ROAR, 3, .9f);
                damageMultiplier = 1;
            });
            endWith(c);
        }
    }

    /** Overloaded: charged punch. Direction fixed when it starts (the telegraph shows the line), directional rip. */
    static final class ChargedPunch extends Action {
        ChargedPunch(GuilherminoBoss boss, LivingEntity target) { super(boss, target); }
        @Override public String name() { return "charged_punch"; }

        @Override protected void begin() {
            turnToward(targetPos(), 180);
            Clip c = P2_OVERLOADED_CHARGED_PUNCH;
            play(c);
            own(Fx.CHARGED_PUNCH.start(level, boss, Placement.at(groundAt(boss.position()), boss.getYRot(), 0)));
            on(c, "charge_start", () -> Sfx.charge(level, boss.position(), .5f));
            on(c, "stance_set", () -> Sfx.heavyStep(level, boss.position()));
            on(c, "charge_peak", () -> Sfx.electric(level, boss.position(), 2));
            on(c, "release", () -> Sfx.play(level, boss.position(), SoundEvents.BREEZE_SHOOT, 3, .5f));
            on(c, "impact", () -> {
                Vec3 o = boss.position().add(0, 1.4, 0);
                Hits.cone(boss, o, forward(), 5, 70, 30, 3.2, .8, new HashSet<>());
                Hits.capsule(boss, local(0, 1.2, 2), local(0, 1.2, 18), 2.2, 16, 2.6, .6, Hits.energy(boss), new HashSet<>());
                Sfx.slam(level, local(0, 0, 4), .9f);
                for (int k = 3; k <= 15; k += 4) Sfx.debris(level, groundAt(local(0, 0, k)), 2.5, 50, Fx.P2_HOT, 0xff1e2a2e);
            });
            on(c, "front_foot_plant", () -> Sfx.heavyStep(level, boss.position()));
            on(c, "arm_overload", () -> {
                Vec3 h = RigAnchors.approximate(boss, "hand_r").position();
                Sfx.burst(level, Sfx.arc(Fx.P2_RED), h, 24, .4, .4, .3);
                Sfx.electric(level, h, 1.5f);
            });
            on(c, "arm_limp", () -> { damageMultiplier = 1.4f; boss.setFlag(GuilherminoBoss.F_VULNERABLE, true); });
            endWith(c);
        }

        @Override protected void cleanup(boolean interrupted) { boss.setFlag(GuilherminoBoss.F_VULNERABLE, false); }
    }

    /** Overloaded: shield charge. Breaks eligible obstacles through the Terrain API (up to three breaches). */
    static final class ShieldCharge extends Action {
        Vec3 dir;
        Optional<Effects.Handle> drive = Optional.empty();
        final Set<UUID> hit = new HashSet<>();
        int loopStart = -1, impact = -1, breaches, lastBreach = -100;
        ShieldCharge(GuilherminoBoss boss, LivingEntity target) { super(boss, target); }
        @Override public String name() { return "shield_charge"; }

        @Override protected void begin() {
            Clip s = P2_OVERLOADED_SHIELD_CHARGE_START;
            turnToward(targetPos(), 180);
            play(s);
            drive = own(Effects.start(Fx.SHIELD_CHARGE, level, Placement.on(boss).followingBody()));
            on(s, "brace", () -> Sfx.servo(level, boss.position(), .6f));
            on(s, "launch", () -> {
                dir = targetPos().subtract(boss.position()).multiply(1, 0, 1).normalize();
                Sfx.play(level, boss.position(), SoundEvents.BREEZE_SHOOT, 3, .6f);
                Sfx.electric(level, boss.position(), 1.5f);
            });
            on(s, "chain_to_loop", () -> { play(P2_OVERLOADED_SHIELD_CHARGE_LOOP); loopStart = age; });
        }

        @Override protected void update() {
            if (dir == null) { turnToward(targetPos(), 6); return; }
            if (impact < 0 && loopStart >= 0) {
                Vec3 want = targetPos().subtract(boss.position()).multiply(1, 0, 1);
                if (want.lengthSqr() > 1) {
                    double cross = dir.x * want.z - dir.z * want.x;
                    double turn = Math.toRadians(Math.copySign(Math.min(2, Math.abs(Math.toDegrees(Math.atan2(cross, dir.dot(want))))), cross));
                    dir = new Vec3(dir.x * Math.cos(turn) - dir.z * Math.sin(turn), 0, dir.x * Math.sin(turn) + dir.z * Math.cos(turn)).normalize();
                }
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            boss.setYRot(yaw);
            boss.yBodyRot = yaw;
            double speed = impact >= 0 ? Math.max(0, .9 * Math.pow(.6, age - impact)) : (loopStart < 0 ? .5 : .9);
            boss.move(MoverType.SELF, dir.scale(speed));
            if (impact >= 0) return;
            Vec3 front = boss.position().add(0, 1.6, 0).add(dir.scale(1.4));
            List<LivingEntity> victims = Hits.capsule(boss, front, front.add(dir.scale(1.8)), 2.4, 14, 2.8, .6, Hits.melee(boss), hit);
            if (loopStart >= 0 && age % 3 == 0) Sfx.burst(level, Sfx.ember(Fx.P2_HOT), boss.position().add(dir.scale(2)), 6, 1, .1, .3);
            boolean blocked = boss.horizontalCollision;
            if (blocked && breaches < 3 && age - lastBreach >= 6) {
                breaches++;
                lastBreach = age;
                Fx.SHIELD_BREACH.start(level, boss, Placement.at(boss.position(), yaw, 0));
                Sfx.slam(level, front, 1.2f);
                Sfx.debris(level, front, 2.5, 60, Fx.P2_HOT, 0xff1e2a2e);
            }
            boolean hitPlayer = victims.stream().anyMatch(v -> v instanceof Player);
            if (loopStart >= 0 && (hitPlayer || (blocked && breaches >= 3) || age - loopStart > 44)) bash(blocked);
        }

        void bash(boolean wall) {
            impact = age;
            drive.ifPresent(Effects.Handle::release);
            Clip c = P2_OVERLOADED_SHIELD_CHARGE_IMPACT;
            play(c);
            on(c, "shield_bash_impact", () -> {
                Vec3 front = boss.position().add(dir.scale(2.5));
                Hits.area(boss, front, 5, 22, .4f, 3, .8, Hits.melee(boss));
                Sfx.slam(level, front, .9f);
                Sfx.debris(level, front, 4, 90, Fx.P2_HOT, 0xff1e2a2e);
                if (wall) Fx.SHIELD_BREACH.start(level, boss, Placement.at(boss.position(), boss.getYRot(), 0));
            });
            on(c, "recoil_peak", () -> { damageMultiplier = 1.3f; boss.setFlag(GuilherminoBoss.F_VULNERABLE, true); });
            on(c, "feet_set", () -> Sfx.heavyStep(level, boss.position()));
            endWith(c);
        }

        @Override protected void cleanup(boolean interrupted) { boss.setFlag(GuilherminoBoss.F_VULNERABLE, false); }
    }

    /** Overloaded: leap smash. Ballistic flight driven by the server, landing exactly on land_impact. */
    static final class Leap extends Action {
        Vec3 start, landing;
        double height;
        int launch, land;
        Leap(GuilherminoBoss boss, LivingEntity target) { super(boss, target); }
        @Override public String name() { return "leap_smash"; }

        @Override protected void begin() {
            Clip c = P2_OVERLOADED_LEAP_SMASH;
            Vec3 t = targetPos().add(target != null ? target.getDeltaMovement().multiply(1, 0, 1).scale(14) : Vec3.ZERO);
            Vec3 d = t.subtract(boss.position()).multiply(1, 0, 1);
            if (d.length() > 26) t = boss.position().add(d.normalize().scale(26));
            landing = groundAt(t);
            turnToward(landing, 180);
            play(c);
            launch = tickOf(c, "launch");
            land = tickOf(c, "land_impact");
            own(Fx.LEAP_SMASH.start(level, boss, Placement.at(landing, boss.getYRot(), 0)));
            height = 9 + d.length() * .25;
            on(c, "crouch", () -> Sfx.servo(level, boss.position(), .5f));
            on(c, "launch", () -> {
                start = boss.position();
                boss.setNoGravity(true);
                Sfx.play(level, start, SoundEvents.BREEZE_JUMP, 4, .5f);
                Sfx.play(level, start, SoundEvents.FIRECHARGE_USE, 3, .6f);
                Sfx.debris(level, start, 3, 60, Fx.P2_HOT, 0xff1e2a2e);
            });
            on(c, "apex", () -> Sfx.electric(level, boss.position(), 2));
            on(c, "fist_swing", () -> Sfx.play(level, boss.position(), SoundEvents.BREEZE_SHOOT, 3, .4f));
            on(c, "land_impact", () -> {
                boss.move(MoverType.SELF, landing.subtract(boss.position()));
                boss.setNoGravity(false);
                Hits.area(boss, landing, 12, 32, .3f, 2.8, 1, Hits.blast(boss, boss));
                Sfx.slam(level, landing, .6f);
                Sfx.explosion(level, landing, 5);
                Sfx.debris(level, landing, 12, 300, Fx.P2_HOT, 0xff1e2a2e);
                Encounter e = boss.encounter();
                if (e != null) e.heatZone(landing, 6, 160);
            });
            on(c, "fist_lift", () -> { damageMultiplier = 1.25f; Sfx.servo(level, boss.position(), .7f); });
            on(c, "rise", () -> Sfx.servo(level, boss.position(), .5f));
            endWith(c);
        }

        @Override protected void update() {
            if (start == null || age < launch || age >= land) return;
            double s = (age + 1 - launch) / (double) (land - launch);
            Vec3 want = start.lerp(landing, s).add(0, 4 * height * s * (1 - s), 0);
            boss.move(MoverType.SELF, want.subtract(boss.position()));
            boss.setDeltaMovement(Vec3.ZERO);
            boss.fallDistance = 0;
            if (age % 2 == 0) Sfx.burst(level, Sfx.ember(Fx.P2_HOT), boss.position().add(0, 2, 0), 6, .6, .6, .1);
        }

        @Override protected void cleanup(boolean interrupted) { boss.setNoGravity(false); }
    }

    /** death: pods blow, arms fall, torso breaks off, the head is ejected; the reactor implodes; loot at the end. */
    static final class Death extends Action {
        Death(GuilherminoBoss boss) { super(boss, null); invulnerable = true; }
        @Override public String name() { return "death2"; }

        @Override protected void begin() {
            Clip c = P2_DEATH;
            play(c);
            boss.setFlag(GuilherminoBoss.F_OVERLOADED, false);
            Vec3 at = boss.position();
            float yaw = boss.getYRot();
            Fx.DEATH2.start(level, boss, Placement.at(at, yaw, 0));
            Encounter e = boss.encounter();
            if (e != null) { e.startCams(Fx.CAM_DEATH, at, yaw); e.defeated(); }
            on(c, "malfunction_start", () -> { Sfx.alarm(level, at); Sfx.electric(level, at, 2); });
            on(c, "heart_flare", () -> Sfx.charge(level, at, 1.4f));
            on(c, "pod_right_blows", () -> pod("rack_r"));
            on(c, "pod_left_blows", () -> pod("rack_l"));
            on(c, "left_arm_falls", () -> Sfx.play(level, at, SoundEvents.ANVIL_LAND, 3, .5f));
            on(c, "right_arm_falls", () -> Sfx.play(level, at, SoundEvents.ANVIL_LAND, 3, .45f));
            on(c, "legs_buckle", () -> Sfx.servo(level, at, .35f));
            on(c, "torso_breaks_off", () -> {
                Sfx.play(level, at, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 3, .5f);
                Sfx.burst(level, Sfx.arc(Fx.P2_HOT), at.add(0, 2.4, 0), 40, .8, .8, .3);
            });
            on(c, "torso_ground_impact", () -> {
                Vec3 p = groundAt(local(0, 0, 2.5));
                Sfx.slam(level, p, 1);
                Sfx.debris(level, p, 3, 90, Fx.P2_HOT, 0xff1e2a2e);
            });
            on(c, "knees_impact", () -> Sfx.heavyStep(level, at));
            on(c, "head_ejected", () -> {
                Sfx.play(level, at, SoundEvents.FIREWORK_ROCKET_LAUNCH, 3, .7f);
                Sfx.burst(level, Sfx.smoke(0xff1c1c20), at.add(0, 3.5, 0), 40, .5, .5, .05);
            });
            on(c, "head_ground_impact", () -> Sfx.heavyStep(level, local(.5, 0, 5.5)));
            at(Fx.deathFinalTick(), () -> {
                Sfx.explosion(level, at.add(0, 1.5, 0), 6);
                Sfx.play(level, at, SoundEvents.BEACON_DEACTIVATE, 4, .5f);
                Hits.area(boss, at, 9, 12, .3f, 2.8, .9, Hits.blast(boss, boss));
                Sfx.debris(level, at, 10, 260, Fx.P2_HOT, 0xff1e2a2e);
                Sfx.burst(level, Sfx.smoke(0xff1c1c20), at.add(0, 2, 0), 300, 3, 2, .06);
                Sfx.burst(level, ParticleTypes.EXPLOSION_EMITTER, at.add(0, 1, 0), 2, 1, .5, 0);
            });
        }

        void pod(String rack) {
            Vec3 p = RigAnchors.approximate(boss, rack).position();
            Sfx.explosion(level, p, 3);
            Sfx.burst(level, ParticleTypes.EXPLOSION, p, 3, .4, .4, 0);
            Sfx.burst(level, Sfx.ember(0xffff8a2a), p, 40, .5, .5, .4);
            Hits.area(boss, p.subtract(0, 3, 0), 3.5, 6, .5f, 1.2, .5, Hits.blast(boss, boss));
        }
    }
}
