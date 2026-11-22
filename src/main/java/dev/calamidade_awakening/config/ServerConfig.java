package dev.calamidade_awakening.config;

import dev.calamidade_awakening.api.SourceLimits;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server limits by source (0.10.0), in {@code serverconfig/calamidade_awakening-server.toml} of each world (also
 * editable from the mod list in single player). Starts above a limit are refused without error: the start returns an
 * empty Optional. Since 0.11.0 also the safety rules of the Terrain API (section {@code terrain}), with conservative
 * defaults.
 */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue PER_PLAYER;
    private static final ModConfigSpec.IntValue PER_ENTITY;
    private static final ModConfigSpec.IntValue PER_BLOCK;
    private static final ModConfigSpec.IntValue PLAYER_RESERVE;
    private static final ModConfigSpec.BooleanValue TERRAIN_ENABLED;
    private static final ModConfigSpec.IntValue TERRAIN_MAX_VOLUME;
    private static final ModConfigSpec.IntValue TERRAIN_BLOCKS_PER_TICK;
    private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> TERRAIN_DIMENSIONS;
    private static final ModConfigSpec.BooleanValue TERRAIN_BLOCK_ENTITIES;
    private static final ModConfigSpec.BooleanValue TERRAIN_MOB_GRIEFING;
    private static final ModConfigSpec.IntValue TERRAIN_UNDO_MINUTES;
    private static final ModConfigSpec.IntValue TERRAIN_UNDO_EDITS;
    private static final ModConfigSpec.IntValue TERRAIN_MAX_STRUCTURE;
    private static final ModConfigSpec.BooleanValue TERRAIN_EXPLOSION_EVENT;
    private static final ModConfigSpec.BooleanValue TERRAIN_SPAWN_PROTECTION;
    /** Most global events at once (0.12.0). */
    private static final ModConfigSpec.IntValue WORLD_EVENTS_MAX;

    /** Defaults of the terrain rules (0.11.0; explosion event and spawn protection since 0.11.1). */
    public static final Terrain TERRAIN_DEFAULT = new Terrain(true, 150_000, 16_384,
        java.util.List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"), false, true, 10, 16, 32,
        true, true);

    /**
     * Terrain API rules (0.11.0).
     *
     * @param enabled           terrain edits run at all
     * @param maxVolume         most planned blocks in one edit (carved, shell, fill and structure); larger edits are refused
     * @param blocksPerTick     blocks written per server tick, shared by all running edits (an edit may ask for less)
     * @param dimensions        dimensions where edits run ({@code "*"} for all)
     * @param blockEntities     edits that ask for it may replace blocks with a block entity
     * @param mobGriefing       edits whose source is a mob follow the {@code mobGriefing} game rule
     * @param undoMinutes       minutes a committed edit can be undone (0 keeps no snapshot)
     * @param undoEdits         most snapshots kept
     * @param maxStructure      largest structure template per axis, blocks
     * @param explosionEvent    (0.11.1) every edit also goes through NeoForge's {@code ExplosionEvent.Start} (cancel
     *                          refuses it) and {@code ExplosionEvent.Detonate} (positions removed by listeners are kept),
     *                          so protection mods that handle explosions protect their areas
     * @param spawnProtection   (0.11.1) edits leave the server's spawn protection area alone (operators exempt only
     *                          when they are the source)
     */
    public record Terrain(boolean enabled, int maxVolume, int blocksPerTick, java.util.List<String> dimensions,
                          boolean blockEntities, boolean mobGriefing, int undoMinutes, int undoEdits, int maxStructure,
                          boolean explosionEvent, boolean spawnProtection) {
        /** True when edits may run in this dimension. */
        public boolean allows(String dimension) { return dimensions.contains("*") || dimensions.contains(dimension); }
    }

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
        builder.comment("Terrain API (0.11.0): craters, burnt shells and blocks placed by effects. Conservative defaults.")
            .translation("calamidade_awakening.configuration.terrain").push("terrain");
        TERRAIN_ENABLED = builder.comment("Terrain edits run at all.")
            .translation("calamidade_awakening.configuration.terrainEnabled")
            .define("terrainEnabled", TERRAIN_DEFAULT.enabled());
        TERRAIN_MAX_VOLUME = builder.comment("Most planned blocks in one edit (carved, shell, fill, structure); larger edits are refused.")
            .translation("calamidade_awakening.configuration.terrainMaxVolume")
            .defineInRange("terrainMaxVolume", TERRAIN_DEFAULT.maxVolume(), 1, 2_000_000);
        TERRAIN_BLOCKS_PER_TICK = builder.comment("Blocks written per server tick, shared by all running edits; an edit may ask for less.")
            .translation("calamidade_awakening.configuration.terrainBlocksPerTick")
            .defineInRange("terrainBlocksPerTick", TERRAIN_DEFAULT.blocksPerTick(), 64, 1_000_000);
        TERRAIN_DIMENSIONS = builder.comment("Dimensions where terrain edits run (\"*\" for all).")
            .translation("calamidade_awakening.configuration.terrainDimensions")
            .defineListAllowEmpty("terrainDimensions", TERRAIN_DEFAULT.dimensions(), () -> "minecraft:overworld",
                value -> value instanceof String text && (text.equals("*") || text.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")));
        TERRAIN_BLOCK_ENTITIES = builder.comment("Edits that explicitly ask for it may replace blocks with a block entity (their contents go to the undo snapshot).")
            .translation("calamidade_awakening.configuration.terrainBlockEntities")
            .define("terrainBlockEntities", TERRAIN_DEFAULT.blockEntities());
        TERRAIN_MOB_GRIEFING = builder.comment("Edits started by a mob follow the mobGriefing game rule.")
            .translation("calamidade_awakening.configuration.terrainMobGriefing")
            .define("terrainMobGriefing", TERRAIN_DEFAULT.mobGriefing());
        TERRAIN_UNDO_MINUTES = builder.comment("Minutes a committed edit can be undone with /calamidade terrain undo (0 keeps no snapshot).")
            .translation("calamidade_awakening.configuration.terrainUndoMinutes")
            .defineInRange("terrainUndoMinutes", TERRAIN_DEFAULT.undoMinutes(), 0, 1440);
        TERRAIN_UNDO_EDITS = builder.comment("Most undo snapshots kept (the oldest go first).")
            .translation("calamidade_awakening.configuration.terrainUndoEdits")
            .defineInRange("terrainUndoEdits", TERRAIN_DEFAULT.undoEdits(), 0, 256);
        TERRAIN_MAX_STRUCTURE = builder.comment("Largest structure template an edit may place, blocks per axis.")
            .translation("calamidade_awakening.configuration.terrainMaxStructure")
            .defineInRange("terrainMaxStructure", TERRAIN_DEFAULT.maxStructure(), 1, 64);
        TERRAIN_EXPLOSION_EVENT = builder.comment("Every edit also goes through NeoForge's explosion events (Start: cancel refuses it; Detonate: positions removed by listeners stay intact), so claim and region mods that protect against explosions protect against edits too. Off: only TerrainEditEvent, world border and spawn protection apply.")
            .translation("calamidade_awakening.configuration.terrainExplosionEvent")
            .define("terrainExplosionEvent", TERRAIN_DEFAULT.explosionEvent());
        TERRAIN_SPAWN_PROTECTION = builder.comment("Edits leave the server's spawn protection area (spawn-protection in server.properties) intact; an operator is exempt only when the edit is theirs.")
            .translation("calamidade_awakening.configuration.terrainSpawnProtection")
            .define("terrainSpawnProtection", TERRAIN_DEFAULT.spawnProtection());
        builder.pop();
        builder.comment("Global events (0.12.0): effects seen by a whole dimension or by the whole server, such as an eclipse.")
            .translation("calamidade_awakening.configuration.worldEvents").push("worldEvents");
        WORLD_EVENTS_MAX = builder.comment("Most global events at once; a start above it replaces the event with the lowest priority when its own priority is higher, otherwise it is refused.")
            .translation("calamidade_awakening.configuration.worldEventsMax")
            .defineInRange("worldEventsMax", dev.calamidade_awakening.api.WorldEventRules.DEFAULT_LIMIT, 1,
                dev.calamidade_awakening.api.WorldEventRules.MAX_LIMIT);
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

    /** Most global events at once (0.12.0); 4 until the world config loads. */
    public static int worldEventsMax() {
        try {
            return WORLD_EVENTS_MAX.get();
        } catch (IllegalStateException notLoaded) {
            return dev.calamidade_awakening.api.WorldEventRules.DEFAULT_LIMIT;
        }
    }

    /** Current terrain rules (0.11.0); the defaults until the world config loads. */
    public static Terrain terrain() {
        try {
            return new Terrain(TERRAIN_ENABLED.get(), TERRAIN_MAX_VOLUME.get(), TERRAIN_BLOCKS_PER_TICK.get(),
                java.util.List.copyOf(TERRAIN_DIMENSIONS.get()), TERRAIN_BLOCK_ENTITIES.get(), TERRAIN_MOB_GRIEFING.get(),
                TERRAIN_UNDO_MINUTES.get(), TERRAIN_UNDO_EDITS.get(), TERRAIN_MAX_STRUCTURE.get(),
                TERRAIN_EXPLOSION_EVENT.get(), TERRAIN_SPAWN_PROTECTION.get());
        } catch (IllegalStateException notLoaded) {
            return TERRAIN_DEFAULT;
        }
    }
}
