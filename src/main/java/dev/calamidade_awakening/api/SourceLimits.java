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
 * </ul>
 */
public final class SourceLimits {
    /** Live instances on a server. */
    public static final int TOTAL = 64;

    /**
     * Limits read from the server config.
     *
     * @param perPlayer     live instances per player, 1..64
     * @param perEntity     live instances per non-player entity, 1..64
     * @param perBlock      live instances per block, 1..64
     * @param playerReserve slots of the 64 only players may use, 0..64
     */
    public record Rules(int perPlayer, int perEntity, int perBlock, int playerReserve) {
        /** 16 per player, 4 per mob, 2 per block, 16 reserved for players. */
        public static final Rules DEFAULT = new Rules(16, 4, 2, 16);

        public Rules {
            if (perPlayer < 1 || perPlayer > TOTAL || perEntity < 1 || perEntity > TOTAL || perBlock < 1 || perBlock > TOTAL
                || playerReserve < 0 || playerReserve > TOTAL)
                throw new IllegalArgumentException("Expected per-source limits in 1..64 and a reserve in 0..64");
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
}
