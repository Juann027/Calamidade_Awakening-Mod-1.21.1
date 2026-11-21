package dev.calamidade_awakening.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules of the engine's shared passes (0.9.0): the beams and lights passes draw the items of every drawn
 * effect at once, so each costs one pass per frame. Not public API.
 */
public final class EnginePasses {
    /** Which budget paid for an engine pass this frame. */
    public enum Pool {
        /** Not drawn: nobody needs it, or the pool of whoever needs it is spent. */
        NONE,
        /** The common addon budget (a NORMAL effect needs the pass). */
        COMMON,
        /** The HERO budget (a HERO effect needs the pass). */
        HERO;

        /** True when the pass is drawn. */
        public boolean drawn() { return this != NONE; }
    }

    private EnginePasses() {}

    /**
     * Pays for one shared engine pass: from the HERO pool when a drawn HERO effect needs it and a HERO pass is left,
     * otherwise from the common pool when a NORMAL effect needs it and a common pass is left. A pool never pays for
     * the other class; once drawn, the pass carries the items of both classes (HERO first).
     */
    public static Pool pay(PassBudget budget, boolean heroNeeds, boolean normalNeeds) {
        if (heroNeeds && budget.take(true)) return Pool.HERO;
        if (normalNeeds && budget.take(false)) return Pool.COMMON;
        return Pool.NONE;
    }

    /**
     * Global cap of a shared pass: takes items effect by effect, in the plan's priority order (HERO first, then by
     * weight), until {@code max} items; an effect that does not fit entirely keeps its first items.
     */
    public static <T> List<T> cap(List<List<T>> perEffect, int max) {
        List<T> out = new ArrayList<>(Math.max(0, max));
        for (List<T> items : perEffect) {
            for (T item : items) {
                if (out.size() >= max) return out;
                out.add(item);
            }
        }
        return out;
    }
}
