package dev.calamidade_awakening.api.v1;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Objects;
import java.util.UUID;

/**
 * Who started an instance (0.10.0): a player, an entity (a mob), a block or a script. Every instance has one. The
 * server uses it for the per-source limits (by default at most 16 live instances per player, 4 per mob and 2 per block),
 * for the slots of the 64 reserved for players (16 by default: mobs, blocks and scripts share the other 48) and for
 * per-source cooldowns ({@link Effects#cooldownReady}). F7 and {@code /calamidade status} count live instances by
 * {@link Kind}. The limits are in {@code serverconfig/calamidade_awakening-server.toml}.
 *
 * <p>Immutable value: two sources are equal when they have the same kind and identity (the UUID of the player or
 * entity, or the dimension and position of the block). A source never keeps the entity, level or block entity alive.
 *
 * <p>When a start does not name a source, the engine picks one: the block of
 * {@link Placement#boundTo(BlockEntity)}, else the caster (PLAYER), else the anchor ({@link #entity(Entity)}: PLAYER for
 * players, ENTITY for other entities), else {@link #script()}. {@code Effects.start(id, level, position)} is SCRIPT,
 * {@code Effects.start(id, player, position)} is PLAYER and {@code Effects.follow(id, entity, offset)} is the entity.
 *
 * <pre>{@code
 * Effects.start(ID, level, Placement.at(pos).withSource(EffectSource.block(level, blockPos)).withCooldown(40));
 * }</pre>
 */
public final class EffectSource {
    /** Kind of source, each with its own per-source limit. */
    public enum Kind {
        /** A player (cards, items, commands run by a player). Players may use every slot, including the reserve. */
        PLAYER,
        /** A non-player entity: a mob attack, a projectile, a vehicle. */
        ENTITY,
        /** A block or block entity, identified by dimension and position. */
        BLOCK,
        /** Code with no individual source (commands, datapacks, mod logic). No per-source limit; never uses the reserve. */
        SCRIPT
    }

    private static final EffectSource SCRIPT = new EffectSource(Kind.SCRIPT, null, null, null);

    private final Kind kind;
    private final UUID uuid;
    private final ResourceLocation dimension;
    private final BlockPos pos;

    private EffectSource(Kind kind, UUID uuid, ResourceLocation dimension, BlockPos pos) {
        this.kind = kind; this.uuid = uuid; this.dimension = dimension; this.pos = pos;
    }

    /** A player. */
    public static EffectSource player(Player player) { return player(Objects.requireNonNull(player, "player").getUUID()); }

    /** A player by UUID (for cooldown lookups of offline or remembered players). */
    public static EffectSource player(UUID uuid) { return new EffectSource(Kind.PLAYER, Objects.requireNonNull(uuid, "uuid"), null, null); }

    /** An entity: {@link Kind#PLAYER} for players, {@link Kind#ENTITY} for any other entity. */
    public static EffectSource entity(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return entity instanceof Player player ? player(player) : entity(entity.getUUID());
    }

    /** A non-player entity by UUID ({@link Kind#ENTITY}). */
    public static EffectSource entity(UUID uuid) { return new EffectSource(Kind.ENTITY, Objects.requireNonNull(uuid, "uuid"), null, null); }

    /** A block at a position of a level. */
    public static EffectSource block(Level level, BlockPos pos) {
        return block(Objects.requireNonNull(level, "level").dimension().location(), pos);
    }

    /** A block at a position of a dimension (for example {@code minecraft:overworld}). */
    public static EffectSource block(ResourceLocation dimension, BlockPos pos) {
        return new EffectSource(Kind.BLOCK, null, Objects.requireNonNull(dimension, "dimension"),
            Objects.requireNonNull(pos, "pos").immutable());
    }

    /** The block of a block entity placed in a level. */
    public static EffectSource block(BlockEntity blockEntity) {
        Level level = Objects.requireNonNull(blockEntity, "blockEntity").getLevel();
        if (level == null) throw new IllegalArgumentException("Block entity is not in a level yet");
        return block(level, blockEntity.getBlockPos());
    }

    /** Code with no individual source. */
    public static EffectSource script() { return SCRIPT; }

    /** Kind of source. */
    public Kind kind() { return kind; }
    /** UUID of the player or entity; null for blocks and scripts. */
    public UUID uuid() { return uuid; }
    /** Dimension of a block source; null for the others. */
    public ResourceLocation dimension() { return dimension; }
    /** Position of a block source (immutable); null for the others. */
    public BlockPos pos() { return pos; }

    /** Same kind and identity. */
    @Override public boolean equals(Object o) {
        return o instanceof EffectSource s && s.kind == kind && Objects.equals(s.uuid, uuid)
            && Objects.equals(s.dimension, dimension) && Objects.equals(s.pos, pos);
    }

    /** Hash of kind and identity. */
    @Override public int hashCode() { return Objects.hash(kind, uuid, dimension, pos); }

    /** Readable form, such as {@code ENTITY(uuid)} or {@code BLOCK(minecraft:overworld 10 64 -3)}. */
    @Override public String toString() {
        return switch (kind) {
            case PLAYER, ENTITY -> kind + "(" + uuid + ")";
            case BLOCK -> "BLOCK(" + dimension + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ")";
            case SCRIPT -> "SCRIPT";
        };
    }
}
