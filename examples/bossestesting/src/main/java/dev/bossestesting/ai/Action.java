package dev.bossestesting.ai;

import dev.bossestesting.anim.Clip;
import dev.bossestesting.entity.GuilherminoBoss;
import dev.calamidade_awakening.api.v1.Effects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One server-side move of the boss: plays clips, runs gameplay at clip events and owns the effects it started.
 *
 * <p>Timing: {@link #play(Clip)} shows a clip now; {@link #on(Clip, String, Runnable)} runs code at that clip's event,
 * {@link Brain#LEAD} ticks later than the raw event (GeckoLib transition plus the network tick), the same offset
 * the effect timelines use in {@code Fx}. So damage, terrain commits and visuals all land on one server tick.
 */
public abstract class Action {
    protected final GuilherminoBoss boss;
    protected final ServerLevel level;
    protected final LivingEntity target;
    protected int age;
    private int end = Integer.MAX_VALUE;
    private int stepStart;
    private final TreeMap<Integer, List<Runnable>> events = new TreeMap<>();
    private final List<Effects.Handle> owned = new ArrayList<>();
    private boolean finished;

    /** Damage multiplier while this move runs (vulnerability windows raise it). */
    protected float damageMultiplier = 1;
    /** True while the boss ignores damage. */
    protected boolean invulnerable;

    protected Action(GuilherminoBoss boss, LivingEntity target) {
        this.boss = boss;
        this.level = (ServerLevel) boss.level();
        this.target = target;
    }

    /** Name for commands and logs. */
    public abstract String name();

    /** Schedules the first clip and its events (age 0). */
    protected abstract void begin();

    /** Called every tick after the scheduled events; movement and continuous hits go here. */
    protected void update() {}

    /** Called once when the move ends normally or is interrupted (stop transient state here). */
    protected void cleanup(boolean interrupted) {}

    // ---------------------------------------------------------------- scheduling

    /** Shows `clip` from its first frame now; its events are relative to this tick. */
    protected final void play(Clip clip) {
        boss.playClip(clip);
        stepStart = age;
    }

    /** Runs `r` at `event` of the clip started by the last {@link #play}. */
    protected final void on(Clip clip, String event, Runnable r) {
        at(stepStart + Brain.LEAD + clip.at(event), r);
    }

    /** Runs `r` at an absolute age of this move. */
    protected final void at(int tick, Runnable r) {
        events.computeIfAbsent(Math.max(tick, age), k -> new ArrayList<>()).add(r);
    }

    /** Runs `r` `delay` ticks from now. */
    protected final void after(int delay, Runnable r) { at(age + Math.max(0, delay), r); }

    /** Ends the move when `clip` (started by the last play) is over. */
    protected final void endWith(Clip clip) { end = stepStart + Brain.LEAD + clip.ticks(); }

    /** Ends the move at an absolute age. */
    protected final void endAt(int tick) { end = tick; }

    /** Age of the current clip's event (absolute), for checks inside update(). */
    protected final int tickOf(Clip clip, String event) { return stepStart + Brain.LEAD + clip.at(event); }

    /** Age when the last clip started. */
    protected final int stepStart() { return stepStart; }

    /** Keeps an effect handle so an interruption stops it (a terrain plan not yet committed is dropped with it). */
    protected final Optional<Effects.Handle> own(Optional<Effects.Handle> handle) {
        handle.ifPresent(owned::add);
        return handle;
    }

    // ---------------------------------------------------------------- driven by the brain

    final void start() { begin(); }

    final void tick() {
        var due = events.headMap(age, true);
        while (!due.isEmpty()) {
            var first = due.pollFirstEntry();
            for (Runnable r : first.getValue()) {
                try { r.run(); } catch (RuntimeException e) { Brain.ERRORS.incrementAndGet(); Brain.LOG.error("Event of {} failed", name(), e); }
            }
        }
        if (finished) return;
        update();
        age++;
        if (age >= end) finish(false);
    }

    /** Ends the move now (normal end). */
    protected final void finish() { finish(false); }

    final void finish(boolean interrupted) {
        if (finished) return;
        finished = true;
        if (interrupted) for (Effects.Handle h : owned) if (h.alive()) h.stop();
        events.clear();
        cleanup(interrupted);
    }

    final boolean finished() { return finished; }

    /** True when a damage burst may cut this move into a stagger. */
    protected boolean interruptible() { return false; }

    public float damageMultiplier() { return damageMultiplier; }
    public boolean invulnerable() { return invulnerable; }
    public int age() { return age; }

    // ---------------------------------------------------------------- helpers

    protected Vec3 targetPos() { return target != null && target.isAlive() ? target.position() : boss.position().add(boss.getLookAngle().scale(6)); }

    /** Turns the body toward a point at most `maxDegrees` per call. */
    protected void turnToward(Vec3 point, float maxDegrees) {
        double dx = point.x - boss.getX(), dz = point.z - boss.getZ();
        if (dx * dx + dz * dz < 1e-4) return;
        float want = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float delta = net.minecraft.util.Mth.wrapDegrees(want - boss.getYRot());
        float step = net.minecraft.util.Mth.clamp(delta, -maxDegrees, maxDegrees);
        boss.setYRot(boss.getYRot() + step);
        boss.yBodyRot = boss.getYRot();
        boss.yHeadRot = boss.getYRot();
    }

    /** Ground under a point (searching 4 up and 8 down), or the point itself. */
    protected Vec3 groundAt(Vec3 p) {
        var r = dev.bossestesting.combat.Hits.ground(boss, p.add(0, 4, 0), p.add(0, -8, 0));
        return r != null ? new Vec3(p.x, r.getLocation().y, p.z) : p;
    }

    /** Unit forward vector of the body, horizontal. */
    protected Vec3 forward() {
        double yaw = Math.toRadians(boss.getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** Point `right`, `up`, `forward` blocks from the feet in the body frame. */
    protected Vec3 local(double right, double up, double fwd) {
        Vec3 f = forward();
        Vec3 r = new Vec3(-f.z, 0, f.x);
        return boss.position().add(r.scale(right)).add(0, up, 0).add(f.scale(fwd));
    }
}
