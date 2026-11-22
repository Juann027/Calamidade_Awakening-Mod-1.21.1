package dev.calamidade_awakening.api;

/**
 * Pass budget of one frame (0.9.0), pure: the common addon budget ({@link Quality#marks}, 1/2/4) and the separate
 * HERO budget ({@link Quality#heroPasses}, 1/2/3). Card shaders, post passes and the engine's beams and lights
 * passes take from the budget of the effect they serve; one pool never borrows from the other. Not public API.
 */
public final class PassBudget {
    private final int commonLimit, heroLimit;
    private int common, hero, refused;

    public PassBudget(Quality quality) { this(quality.marks, quality.heroPasses); }

    public PassBudget(int commonLimit, int heroLimit) {
        this.commonLimit = Math.max(0, commonLimit);
        this.heroLimit = Math.max(0, heroLimit);
    }

    /** Takes one pass from the HERO or the common pool; false (and nothing changes) when that pool is spent. */
    public boolean take(boolean heroPool) {
        if (left(heroPool) <= 0) {
            refused++;
            return false;
        }
        if (heroPool) hero++; else common++;
        return true;
    }

    /** Records passes already queued elsewhere (a post layer spends its own counter), clamped to the pool. */
    public void charge(boolean heroPool, int passes) {
        int n = Math.clamp(passes, 0, left(heroPool));
        if (heroPool) hero += n; else common += n;
    }

    /** Passes still free in a pool. */
    public int left(boolean heroPool) { return heroPool ? heroLimit - hero : commonLimit - common; }

    /** Passes asked with {@link #take} and refused for lack of budget, in both pools (0.11.2). */
    public int refused() { return refused; }

    /** Passes already taken from a pool. */
    public int used(boolean heroPool) { return heroPool ? hero : common; }
}
