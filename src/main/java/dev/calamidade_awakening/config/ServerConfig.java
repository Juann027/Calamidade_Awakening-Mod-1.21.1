package dev.calamidade_awakening.config;

import dev.calamidade_awakening.api.SourceLimits;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server limits by source (0.10.0), in {@code serverconfig/calamidade_awakening-server.toml} of each world (also
 * editable from the mod list in single player). Starts above a limit are refused without error: the start returns an
 * empty Optional.
 */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue PER_PLAYER;
    private static final ModConfigSpec.IntValue PER_ENTITY;
    private static final ModConfigSpec.IntValue PER_BLOCK;
    private static final ModConfigSpec.IntValue PLAYER_RESERVE;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("Live effect instances by source (0.10.0). The server runs at most 64 instances in total.")
            .translation("calamidade_awakening.configuration.limits").push("limits");
        PER_PLAYER = builder
            .comment("Most live instances started by one player (cards, items, commands with a player caster).")
            .translation("calamidade_awakening.configuration.maxPerPlayer")
            .defineInRange("maxPerPlayer", SourceLimits.Rules.DEFAULT.perPlayer(), 1, SourceLimits.TOTAL);
        PER_ENTITY = builder
            .comment("Most live instances started by one non-player entity (mob attacks).")
            .translation("calamidade_awakening.configuration.maxPerEntity")
            .defineInRange("maxPerEntity", SourceLimits.Rules.DEFAULT.perEntity(), 1, SourceLimits.TOTAL);
        PER_BLOCK = builder
            .comment("Most live instances started by one block (block entity triggers).")
            .translation("calamidade_awakening.configuration.maxPerBlock")
            .defineInRange("maxPerBlock", SourceLimits.Rules.DEFAULT.perBlock(), 1, SourceLimits.TOTAL);
        PLAYER_RESERVE = builder
            .comment("Slots of the 64 that only players may use: mobs, blocks and scripts share the rest (0 = no reserve).")
            .translation("calamidade_awakening.configuration.playerReserve")
            .defineInRange("playerReserve", SourceLimits.Rules.DEFAULT.playerReserve(), 0, SourceLimits.TOTAL);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {}

    /** Current limits; the defaults (16 per player, 4 per mob, 2 per block, 16 reserved) until the world config loads. */
    public static SourceLimits.Rules rules() {
        try {
            return new SourceLimits.Rules(PER_PLAYER.get(), PER_ENTITY.get(), PER_BLOCK.get(), PLAYER_RESERVE.get());
        } catch (IllegalStateException notLoaded) {
            return SourceLimits.Rules.DEFAULT;
        }
    }
}
