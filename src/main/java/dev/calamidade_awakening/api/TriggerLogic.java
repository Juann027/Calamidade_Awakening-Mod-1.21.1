package dev.calamidade_awakening.api;

/**
 * State machine of a block trigger (0.10.0): decides, once per server tick, whether to start an instance, release the
 * running one or do nothing. Pure Java, covered by the checkBudget; {@code EffectTriggers.BlockTrigger.Component} feeds
 * it the redstone signal or the presence of players and acts on the answer.
 *
 * <ul>
 * <li>{@link Mode#PERIODIC}: starts every {@code period} ticks, from the first tick. A sustained effect (timeline with
 * sustain) is started once and held instead.</li>
 * <li>{@link Mode#REDSTONE} and {@link Mode#PROXIMITY}: start on the rising edge of the input (powered, a player in
 * range). While the input stays on, a non-sustained effect starts again every {@code period} ticks (0: once per edge)
 * and a sustained one is held; on the falling edge the held instance is released.</li>
 * </ul>
 *
 * A start the server refuses (cooldown, limits) is retried no sooner than {@code max(period, 20)} ticks later.
 */
public final class TriggerLogic {
    /** How a block trigger decides to start. */
    public enum Mode { PERIODIC, REDSTONE, PROXIMITY }

    /** What the component should do this tick. */
    public enum Action { NONE, START, RELEASE }

    /** Shortest wait before trying again to hold a sustained instance that is not running. */
    public static final int RETRY = 20;

    private boolean wasActive;
    private long lastAttempt = Long.MIN_VALUE;

    /**
     * One server tick.
     *
     * @param mode      trigger mode
     * @param active    input this tick (redstone power or a player in range); ignored by PERIODIC
     * @param sustained true when the effect loops until released
     * @param running   true while the instance this component started last is alive
     * @param period    ticks between starts (PERIODIC: at least 1; REDSTONE/PROXIMITY: 0 for once per edge)
     * @param now       game time, ticks
     */
    public Action step(Mode mode, boolean active, boolean sustained, boolean running, int period, long now) {
        if (mode == Mode.PERIODIC) active = true;
        boolean rising = active && !wasActive, falling = !active && wasActive;
        wasActive = active;
        if (falling) return sustained && running ? Action.RELEASE : Action.NONE;
        if (!active) return Action.NONE;
        if (sustained) {
            if (running) return Action.NONE;
            if (rising || elapsed(now, Math.max(period, RETRY))) return attempt(now);
            return Action.NONE;
        }
        if (rising && mode != Mode.PERIODIC) return attempt(now);
        if (mode == Mode.PERIODIC ? elapsed(now, Math.max(1, period)) : period > 0 && elapsed(now, period)) return attempt(now);
        return Action.NONE;
    }

    /** Input seen on the previous tick. */
    public boolean active() { return wasActive; }

    /** Forgets the input and the last attempt (the block entity was reloaded). */
    public void reset() {
        wasActive = false;
        lastAttempt = Long.MIN_VALUE;
    }

    private boolean elapsed(long now, int ticks) {
        return lastAttempt == Long.MIN_VALUE || now - lastAttempt >= ticks;
    }

    private Action attempt(long now) {
        lastAttempt = now;
        return Action.START;
    }
}
