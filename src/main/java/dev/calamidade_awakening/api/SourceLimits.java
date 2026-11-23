package dev.calamidade_awakening.api;

import dev.calamidade_awakening.api.v1.EffectSource;

/**
 * Admission of new instances on the server by source (0.10.0). Pure Java, covered by the checkBudget.
 *
 * <ul>
 * <li>At most {@link #TOTAL} live instances on the server.</li>
 * <li>{@code playerReserve} of them are reserved for players: ENTITY, BLOCK and SCRIPT sources together may hold at
 * most {@code TOTAL - playerReserve}; players may use every slot.</li>
 * <li>Each individual player, entity and block may hold at most its per-source limit; SCRIPT has no identity and so no
 * per-source limit.</li>
 * <li>(0.13.0) An entity with a valid boss controller may hold up to {@code perBoss} instances when the start comes
 * from the controller; the count is still the entity's (marked and unmarked starts together), so ordinary starts of
 * the same entity stay under {@code perEntity}. The total and the player reserve are unchanged.</li>
 * </ul>
 */
public final class SourceLimits {
    /** Live instances on a server. */
    public static final int TOTAL = 64;
    /** Largest {@code perBoss} the server config accepts (0.13.0). */
    public static final int MAX_PER_BOSS = 32;

    /**
     * Limits read from the server config.
     *
     * @param perPlayer     live instances per player, 1..64
     * @param perEntity     live instances per non-player entity, 1..64
     * @param perBlock      live instances per block, 1..64
     * @param playerReserve slots of the 64 only players may use, 0..64
     * @param perBoss       (0.13.0) live instances per entity for starts of its boss controller, 1..{@value #MAX_PER_BOSS}
     */
    public record Rules(int perPlayer, int perEntity, int perBlock, int playerReserve, int perBoss) {
        /** 16 per player, 4 per mob, 2 per block, 16 reserved for players, 8 per boss controller. */
        public static final Rules DEFAULT = new Rules(16, 4, 2, 16, 8);

        public Rules {
            if (perPlayer < 1 || perPlayer > TOTAL || perEntity < 1 || perEntity > TOTAL || perBlock < 1 || perBlock > TOTAL
                || playerReserve < 0 || playerReserve > TOTAL || perBoss < 1 || perBoss > MAX_PER_BOSS)
                throw new IllegalArgumentException("Expected per-source limits in 1..64, a reserve in 0..64 and perBoss in 1..32");
        }

        /** The 0.10 rules, with the default {@code perBoss} of 8. */
        public Rules(int perPlayer, int perEntity, int perBlock, int playerReserve) {
            this(perPlayer, perEntity, perBlock, playerReserve, 8);
        }

        /** Per-source limit of a kind ({@link #TOTAL} for SCRIPT). */
        public int perSource(EffectSource.Kind kind) {
            return switch (kind) {
                case PLAYER -> perPlayer;
                case ENTITY -> perEntity;
                case BLOCK -> perBlock;
                case SCRIPT -> TOTAL;
            };
        }
    }

    /** Result of an admission test. */
    public enum Verdict {
        /** Accepted. */
        OK,
        /** The server already runs {@link #TOTAL} instances. */
        FULL,
        /** Only the slots reserved for players are left. */
        RESERVED,
        /** This individual source already holds its limit. */
        SOURCE_LIMIT;

        /** True for {@link #OK}. */
        public boolean ok() { return this == OK; }
    }

    private SourceLimits() {}

    /**
     * Whether a new instance of {@code kind} may start.
     *
     * @param live           live instances on the server
     * @param liveNonPlayer  live instances whose source is not a player
     * @param liveFromSource live instances of this same individual source
     */
    public static Verdict admit(Rules rules, EffectSource.Kind kind, int live, int liveNonPlayer, int liveFromSource) {
        if (live >= TOTAL) return Verdict.FULL;
        if (kind != EffectSource.Kind.PLAYER && liveNonPlayer >= TOTAL - rules.playerReserve()) return Verdict.RESERVED;
        if (kind != EffectSource.Kind.SCRIPT && liveFromSource >= rules.perSource(kind)) return Verdict.SOURCE_LIMIT;
        return Verdict.OK;
    }

    /**
     * (0.13.0) Like {@link #admit(Rules, EffectSource.Kind, int, int, int)}, but a start marked as coming from a boss
     * controller whose controller the server confirmed ({@code boss}) uses {@code perBoss} instead of {@code perEntity}.
     * {@code liveFromSource} must count every live instance of that entity, marked or not, so ordinary starts and
     * controller starts share one count and switching between them never yields extra slots.
     */
    public static Verdict admit(Rules rules, EffectSource.Kind kind, boolean boss, int live, int liveNonPlayer, int liveFromSource) {
        if (!boss || kind != EffectSource.Kind.ENTITY) return admit(rules, kind, live, liveNonPlayer, liveFromSource);
        if (live >= TOTAL) return Verdict.FULL;
        if (liveNonPlayer >= TOTAL - rules.playerReserve()) return Verdict.RESERVED;
        if (liveFromSource >= rules.perBoss()) return Verdict.SOURCE_LIMIT;
        return Verdict.OK;
    }
}
