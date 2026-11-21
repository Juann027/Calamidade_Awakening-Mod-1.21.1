package dev.calamidade_awakening.api.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import java.util.Objects;

/**
 * Posted once per {@link TerrainEdit} of an instance (0.11.0), on {@code NeoForge.EVENT_BUS} and the server thread,
 * before any block changes: terrain protection mods (claims, spawn protection, regions) cancel it to refuse the edit.
 * A cancelled edit writes nothing; the effect itself still plays, without the crater and without the heat.
 *
 * <p>When: as soon as the plan is ready, normally a tick or two after the instance starts and well before the commit
 * marker; for instances anchored to an entity (their origin is only known at the marker), at the marker. The area is the
 * exact box of every planned position (carved, shell, fill and structure); {@link #blocks()} is the planned volume.
 * The server config and its rules (dimensions, {@code mobGriefing}, volume) are applied before the event: a refused
 * edit is not posted.
 *
 * <pre>{@code
 * @SubscribeEvent
 * public static void protect(TerrainEditEvent event) {
 *     if (Claims.anyIn(event.level(), event.area())) event.setCanceled(true);
 * }
 * }</pre>
 */
public final class TerrainEditEvent extends Event implements ICancellableEvent {
    private final ServerLevel level;
    private final ResourceLocation effect;
    private final int index;
    private final TerrainEdit edit;
    private final Vec3 origin;
    private final BoundingBox area;
    private final int blocks;
    private final EffectSource source;
    private final Entity entity;

    /**
     * Event of one edit. Built by the engine; addons only read it.
     *
     * @param level  server level of the edit
     * @param effect effect ID
     * @param index  index of the edit in {@link Effects#terrain} (0-based)
     * @param edit   the edit
     * @param origin edit origin (the instance center), blocks
     * @param area   box of every planned position, inclusive block coordinates
     * @param blocks planned volume, blocks
     * @param source who started the instance
     * @param entity the player or entity behind the source (caster, or anchor, or the source entity), or null
     */
    public TerrainEditEvent(ServerLevel level, ResourceLocation effect, int index, TerrainEdit edit, Vec3 origin,
                            BoundingBox area, int blocks, EffectSource source, Entity entity) {
        this.level = Objects.requireNonNull(level, "level");
        this.effect = Objects.requireNonNull(effect, "effect");
        this.index = index;
        this.edit = Objects.requireNonNull(edit, "edit");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.area = Objects.requireNonNull(area, "area");
        this.blocks = blocks;
        this.source = Objects.requireNonNull(source, "source");
        this.entity = entity;
    }

    /** Server level of the edit. */
    public ServerLevel level() { return level; }
    /** Effect ID. */
    public ResourceLocation effect() { return effect; }
    /** Index of the edit among the effect's edits (0-based). */
    public int index() { return index; }
    /** The edit (shape, shell, fill, structure). */
    public TerrainEdit edit() { return edit; }
    /** Edit origin, blocks (the instance center when planned). */
    public Vec3 origin() { return origin; }
    /** Box of every planned position, inclusive block coordinates. */
    public BoundingBox area() { return area; }
    /** Planned volume, blocks (carved, shell, fill and structure; air positions included). */
    public int blocks() { return blocks; }
    /** Who started the instance (player, entity, block or script). */
    public EffectSource source() { return source; }
    /** The player or entity behind the source (caster, anchor or source entity), or null for blocks and scripts. */
    public Entity entity() { return entity; }
}
