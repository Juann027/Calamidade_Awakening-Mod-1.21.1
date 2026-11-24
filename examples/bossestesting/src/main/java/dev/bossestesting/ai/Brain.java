package dev.bossestesting.ai;

import dev.bossestesting.encounter.Encounter;
import dev.bossestesting.entity.GuilherminoBoss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server AI of one form: target choice, heavy locomotion, contextual move selection with cooldowns, recovery gaps,
 * combos, interruptions (stagger) and forced moves (fury, overload, death). Subclasses list the moves.
 */
public abstract class Brain {
    static final Logger LOG = LoggerFactory.getLogger("bossestesting");
    /** Failed action events since start (diagnostics and the self test). */
    public static final java.util.concurrent.atomic.AtomicInteger ERRORS = new java.util.concurrent.atomic.AtomicInteger();

    /** GeckoLib transition length used by the boss controllers, ticks. */
    public static final int TRANSITION = 2;
    /** Server offset of every clip event: transition + one tick for the synced clip to reach clients. */
    public static final int LEAD = TRANSITION + 1;

    /** One selectable move. */
    public record Move(String name, int cooldown, double minRange, double maxRange, Weight weight, Factory factory) {}
    /** Weight of a move in the current context (0 = not now). */
    public interface Weight { double of(Context c); }
    /** Builds the action for a target. */
    public interface Factory { Action create(GuilherminoBoss boss, LivingEntity target); }
    /** What the selection sees. */
    public record Context(double distance, int nearbyPlayers, float health, boolean overloaded, boolean enraged, int sinceLast) {}

    protected final GuilherminoBoss boss;
    private final List<Move> moves = new ArrayList<>();
    private final Map<String, Long> readyAt = new HashMap<>();
    private Action action;
    private Supplier<Action> queued;
    private int gap = 30;
    private int idle;
    private float burst;
    private int burstWindow;
    protected boolean enraged;
    private String lastMove = "";

    protected Brain(GuilherminoBoss boss) { this.boss = boss; }

    protected final void move(Move move) { moves.add(move); }

    public List<Move> moves() { return moves; }

    // ---------------------------------------------------------------- hooks of the forms

    /** Forced action before the normal selection (fury, overload), or null. */
    protected Action forced(LivingEntity target) { return null; }
    /** The death sequence. */
    protected abstract Action death();
    /** Reaction to a damage burst, or null. */
    protected Action stagger(LivingEntity target) { return null; }
    /** Walking speed modifier toward the target. */
    protected double chaseSpeed() { return 1.0; }
    /** Distance the boss tries to keep from its target while walking. */
    protected double preferredDistance() { return 4.5; }

    // ---------------------------------------------------------------- tick

    public final void tick() {
        if (burstWindow > 0 && --burstWindow == 0) burst = 0;
        if (action != null) {
            Action current = action;
            current.tick(); // may remove the boss (the swap) and interrupt this brain
            if (current.finished()) {
                if (action == current) action = null;
                if (boss.isRemoved()) return;
                boss.clearClip();
                idle = 0;
                if (queued != null && !boss.isDeadOrDying()) {
                    Action next = queued.get();
                    queued = null;
                    if (next != null) begin(next);
                }
            }
            return;
        }
        if (boss.isDeadOrDying()) return;
        LivingEntity target = acquireTarget();
        boss.setTarget(target);
        if (target == null) { boss.getNavigation().stop(); return; }
        Action force = forced(target);
        if (force != null) { begin(force); return; }
        locomote(target);
        idle++;
        if (idle >= gap) choose(target);
    }

    /** Starts an action now, interrupting the current one. */
    public final void begin(Action next) {
        if (action != null) action.finish(true);
        boss.getNavigation().stop();
        action = next;
        lastMove = next.name();
        next.start();
    }

    /** Queues an action to start when the current one ends (combos). */
    public final void queue(Supplier<Action> next) { this.queued = next; }

    /** Stops the current action (removal, transformation, commands). */
    public final void interrupt() {
        if (action != null) action.finish(true);
        action = null;
        queued = null;
    }

    /** Starts the death sequence (from {@code die}). */
    public final void startDeath() {
        interrupt();
        Action d = death();
        if (d != null) { action = d; lastMove = d.name(); d.start(); }
    }

    public Action action() { return action; }
    public String lastMove() { return lastMove; }

    /** Runs a move by name now (test commands); false when unknown. */
    public boolean force(String name) {
        LivingEntity t = acquireTarget();
        for (Move m : moves) if (m.name().equals(name)) { begin(m.factory().create(boss, t)); return true; }
        return false;
    }

    public void resetCooldowns() { readyAt.clear(); }

    // ---------------------------------------------------------------- damage

    public boolean invulnerable() { return action != null && action.invulnerable(); }

    public float damageMultiplier(DamageSource source) { return action != null ? action.damageMultiplier() : 1; }

    public void onHurt(DamageSource source, float amount) {
        burst += amount;
        if (burstWindow == 0) burstWindow = 60;
        if (burst >= boss.getMaxHealth() * .18f && (action == null || action.interruptible())) {
            Action s = stagger(boss.getTarget());
            if (s != null && cooldownReady("stagger")) {
                burst = 0;
                startCooldown("stagger", 300);
                begin(s);
            }
        }
    }

    // ---------------------------------------------------------------- selection

    private void choose(LivingEntity target) {
        double distance = boss.distanceTo(target);
        Context c = new Context(distance, nearbyPlayers(10), boss.getHealth() / boss.getMaxHealth(),
            boss.flag(GuilherminoBoss.F_OVERLOADED), enraged, idle);
        double total = 0;
        double[] w = new double[moves.size()];
        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            if (!cooldownReady(m.name()) || distance < m.minRange() || distance > m.maxRange()) continue;
            w[i] = Math.max(0, m.weight().of(c));
            if (m.name().equals(lastMove)) w[i] *= .35; // variety
            total += w[i];
        }
        if (total <= 0) return;
        double pick = boss.getRandom().nextDouble() * total;
        for (int i = 0; i < moves.size(); i++) {
            if (w[i] <= 0) continue;
            pick -= w[i];
            if (pick <= 0) {
                Move m = moves.get(i);
                startCooldown(m.name(), (int) (m.cooldown() * (enraged ? .75 : 1)));
                gap = (enraged ? 10 : 18) + boss.getRandom().nextInt(14);
                begin(m.factory().create(boss, target));
                return;
            }
        }
    }

    public boolean cooldownReady(String name) { return boss.level().getGameTime() >= readyAt.getOrDefault(name, 0L); }

    public void startCooldown(String name, int ticks) { readyAt.put(name, boss.level().getGameTime() + ticks); }

    private int nearbyPlayers(double radius) {
        return boss.level().getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(radius),
            p -> p.isAlive() && !p.isSpectator() && !p.isCreative()).size();
    }

    // ---------------------------------------------------------------- target and walking

    private LivingEntity acquireTarget() {
        LivingEntity current = boss.getTarget();
        if (valid(current) && boss.distanceTo(current) < 64) return current;
        Encounter e = boss.encounter();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : boss.level().players()) {
            if (!valid(p)) continue;
            if (e != null && !e.isParticipant(p) && boss.distanceTo(p) > 40) continue;
            double d = boss.distanceToSqr(p);
            if (d < bestD && d < 64 * 64) { bestD = d; best = p; }
        }
        if (best == null && boss.getLastHurtByMob() != null && valid(boss.getLastHurtByMob())) best = boss.getLastHurtByMob();
        return best;
    }

    private static boolean valid(LivingEntity e) {
        return e != null && e.isAlive() && !(e instanceof Player p && (p.isSpectator() || p.isCreative()));
    }

    private void locomote(LivingEntity target) {
        boss.getLookControl().setLookAt(target, 20, 20);
        double d = boss.distanceTo(target);
        if (d > preferredDistance()) {
            if (boss.tickCount % 10 == 0 || boss.getNavigation().isDone())
                boss.getNavigation().moveTo(target, chaseSpeed());
        } else {
            boss.getNavigation().stop();
        }
    }

    protected ServerLevel level() { return (ServerLevel) boss.level(); }
}
