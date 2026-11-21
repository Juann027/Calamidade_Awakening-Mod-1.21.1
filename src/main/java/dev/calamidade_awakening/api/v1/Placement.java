package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.api.Basis;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;

/**
 * Where and how an instance starts (0.10.0): a fixed position or an anchor entity, an orientation (yaw and pitch),
 * the player who cast it, its {@link EffectSource source}, an optional cooldown per source and an optional block entity
 * that bounds its life. Immutable: every {@code with...}, {@link #facing}, {@link #arrivingFrom}, {@link #shifted},
 * {@link #followingBody} and {@link #boundTo} call returns a copy. Start it with
 * {@link Effects#start(net.minecraft.resources.ResourceLocation, net.minecraft.server.level.ServerLevel, Placement)}
 * on the server thread.
 *
 * <p><b>Orientation.</b> Yaw and pitch are in degrees with Minecraft's convention: yaw 0 faces south (+Z), 90 west
 * (-X), -90 east (+X), 180 north; pitch is positive when looking down, within [-90, 90]. The orientation defines the
 * instance frame, whose axes are Minecraft's local coordinates ({@code ^x ^y ^z} in commands): <b>x to the left, y up,
 * z forward</b>. {@link Point#center(double, double, double) center points}, {@link Point#facing facing points}, the
 * track offsets of {@link Beam beams} and {@link Light lights} and the anchor offset are in that frame. Yaw 0 and pitch
 * 0 (the default) give the world axes, exactly as in 0.9, so effects written for 0.9 look the same when started
 * without orientation and turn with the placement when started with one.
 *
 * <pre>{@code
 * // A fan of beams in front of the player, turned to where they look (horizontally):
 * Effects.start(ID, level, Placement.at(player.position(), player.getYRot(), 0).withCaster(player));
 * // A mob attack that keeps facing where the mob's body faces:
 * Effects.start(ID, level, Placement.on(mob).shifted(0, 1.2, .6).followingBody());
 * // A strike from attacker to victim, forward along the hit:
 * Effects.start(ID, level, Placement.at(victim.getBoundingBox().getCenter()).arrivingFrom(attacker.getEyePosition()));
 * }</pre>
 *
 * Invalid input (non-finite numbers, offsets beyond {@value Point#MAX_OFFSET} blocks, {@link #followingBody} without
 * an anchor) throws {@link IllegalArgumentException}.
 */
public final class Placement {
    private final Vec3 position;
    private final Entity anchor;
    private final float yaw, pitch;
    private final boolean followBody;
    private final ServerPlayer caster;
    // 0.10.0, stage 2: who started it, cooldown per source and effect, block entity that bounds its life.
    private final EffectSource source;
    private final int cooldown;
    private final BlockEntity block;

    private Placement(Vec3 position, Entity anchor, float yaw, float pitch, boolean followBody, ServerPlayer caster,
                      EffectSource source, int cooldown, BlockEntity block) {
        this.position = position; this.anchor = anchor; this.yaw = yaw; this.pitch = pitch;
        this.followBody = followBody; this.caster = caster;
        this.source = source; this.cooldown = cooldown; this.block = block;
    }

    private Placement copy(Vec3 position, Entity anchor, float yaw, float pitch, boolean followBody) {
        return new Placement(position, anchor, yaw, pitch, followBody, caster, source, cooldown, block);
    }

    /** Fixed world position in blocks, no orientation (world axes). */
    public static Placement at(Vec3 position) {
        return new Placement(finite(position, "position"), null, 0, 0, false, null, null, 0, null);
    }

    /** Fixed world position in blocks, turned by {@code yaw} and {@code pitch} (degrees). */
    public static Placement at(Vec3 position, float yaw, float pitch) {
        return at(position).withOrientation(yaw, pitch);
    }

    /** Anchored to an entity: the center follows the entity's feet. The instance ends when the entity goes away. */
    public static Placement on(Entity anchor) {
        return new Placement(Vec3.ZERO, Objects.requireNonNull(anchor, "anchor"), 0, 0, false, null, null, 0, null);
    }

    /**
     * Anchored to an entity with an offset from its feet, in blocks, in the instance frame (x left, y up, z forward;
     * the world axes while the placement has no orientation, as in {@link Effects#follow}).
     */
    public static Placement on(Entity anchor, Vec3 offset) {
        return new Placement(offset(offset), Objects.requireNonNull(anchor, "anchor"), 0, 0, false, null, null, 0, null);
    }

    /**
     * Copy with this orientation: yaw in degrees (any finite value, wrapped to [-180, 180)) and pitch in degrees
     * ([-90, 90], positive looks down). With {@link #followingBody}, the yaw is added to the body yaw.
     */
    public Placement withOrientation(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90)
            throw new IllegalArgumentException("Expected a finite yaw and a pitch in [-90, 90]: " + yaw + ", " + pitch);
        return copy(position, anchor, Basis.wrapYaw(yaw), pitch == 0 ? 0f : pitch, followBody);
    }

    /** Copy with this yaw (degrees), keeping the pitch. */
    public Placement withYaw(float yaw) { return withOrientation(yaw, pitch); }

    /** Copy with the yaw and pitch an entity is looking with ({@code getYRot()}, {@code getXRot()}). */
    public Placement withLookOf(Entity entity) {
        return withOrientation(entity.getYRot(), Math.clamp(entity.getXRot(), -90f, 90f));
    }

    /**
     * Copy oriented to look at {@code target} (blocks): from the position, or from the anchor's eyes for anchored
     * placements. Keeps the current orientation when the target is the starting point itself.
     */
    public Placement facing(Vec3 target) {
        finite(target, "target");
        Vec3 from = anchor == null ? position : anchor.getEyePosition();
        Vec3 d = target.subtract(from);
        if (d.lengthSqr() < 1e-12) return this;
        return withOrientation(Basis.yawTowards(d.x, d.z), Basis.pitchTowards(d.x, d.y, d.z));
    }

    /**
     * Copy oriented along the path of something that arrives here from {@code origin} (blocks): forward points from
     * {@code origin} to the position (or to the anchor's eyes for anchored placements). The direction of a hit, from
     * the attacker to the victim. Keeps the current orientation when {@code origin} is the position itself.
     *
     * <pre>{@code
     * Placement.at(victim.getBoundingBox().getCenter()).arrivingFrom(attacker.getEyePosition())
     * }</pre>
     */
    public Placement arrivingFrom(Vec3 origin) {
        finite(origin, "origin");
        Vec3 to = anchor == null ? position : anchor.getEyePosition();
        Vec3 d = to.subtract(origin);
        if (d.lengthSqr() < 1e-12) return this;
        return withOrientation(Basis.yawTowards(d.x, d.z), Basis.pitchTowards(d.x, d.y, d.z));
    }

    /**
     * Copy moved by {@code right}, {@code up} and {@code forward} blocks in the placement's frame. For a fixed position
     * the position moves now, with the orientation set so far (set the orientation first). For an anchor the amount
     * is added to the offset, which stays in the instance frame and so turns with {@link #followingBody}.
     */
    public Placement shifted(double right, double up, double forward) {
        if (!Double.isFinite(right) || !Double.isFinite(up) || !Double.isFinite(forward))
            throw new IllegalArgumentException("Shift must be finite");
        if (anchor != null) return copy(offset(position.add(-right, up, forward)), anchor, yaw, pitch, followBody);
        Basis basis = Basis.of(yaw, pitch);
        Vec3 moved = position.add(basis.worldX(-right, up, forward), basis.worldY(-right, up, forward),
            basis.worldZ(-right, up, forward));
        return copy(finite(moved, "position"), null, yaw, pitch, followBody);
    }

    /**
     * Copy whose yaw follows the anchor's body every tick and every frame (0.10.0): the body yaw of a living entity
     * ({@code yBodyRot}, interpolated on clients), or the entity's yaw for other entities, plus this placement's yaw
     * as an offset. The pitch stays fixed. Only for anchored placements.
     */
    public Placement followingBody() {
        if (anchor == null) throw new IllegalArgumentException("followingBody needs an anchored placement (Placement.on)");
        return copy(position, anchor, yaw, pitch, true);
    }

    /**
     * Copy cast by this player: their client plays the effect's camera (card shots or {@code AddonLayers.film}),
     * cues receive them as {@code caster} and {@link Point#caster caster points} resolve to them. Null for none.
     */
    public Placement withCaster(ServerPlayer caster) {
        return new Placement(position, anchor, yaw, pitch, followBody, caster, source, cooldown, block);
    }

    /**
     * Copy started by this source (0.10.0), for the per-source limits, the slots reserved for players and
     * {@link #withCooldown}. Null lets the engine pick: the {@link #boundTo bound block}, else the caster, else the
     * anchor, else {@link EffectSource#script()}.
     */
    public Placement withSource(EffectSource source) {
        return new Placement(position, anchor, yaw, pitch, followBody, caster, source, cooldown, block);
    }

    /**
     * Copy with a cooldown per source and effect (0.10.0), in ticks, 0 (none) to 72000: the start is refused (empty
     * Optional) while the cooldown of this {@link #source()} for this effect runs, and an accepted start begins it. The
     * same cooldown {@link Effects#cooldownReady} and {@link Effects#startCooldown} read and write.
     */
    public Placement withCooldown(int ticks) {
        if (ticks < 0 || ticks > 72000) throw new IllegalArgumentException("Expected a cooldown in 0..72000 ticks: " + ticks);
        return new Placement(position, anchor, yaw, pitch, followBody, caster, source, ticks, block);
    }

    /**
     * Copy whose life is bound to a block entity (0.10.0): the instance ends when the block entity is removed (the
     * block is broken or replaced, or its chunk unloads). Unless {@link #withSource} says otherwise, the source is that
     * block ({@link EffectSource#block(BlockEntity)}). The block entity must be in a level.
     */
    public Placement boundTo(BlockEntity blockEntity) {
        Objects.requireNonNull(blockEntity, "blockEntity");
        if (blockEntity.getLevel() == null) throw new IllegalArgumentException("Block entity is not in a level yet");
        return new Placement(position, anchor, yaw, pitch, followBody, caster, source, cooldown, blockEntity);
    }

    /** World position in blocks (fixed placements), or the offset from the anchor's feet in the instance frame. */
    public Vec3 position() { return position; }
    /** Anchor entity, or null for a fixed position. */
    public Entity anchor() { return anchor; }
    /** True when anchored to an entity. */
    public boolean anchored() { return anchor != null; }
    /** Yaw in degrees, [-180, 180); an offset added to the body yaw when {@link #followBody()} is true. */
    public float yaw() { return yaw; }
    /** Pitch in degrees, [-90, 90] (positive looks down). */
    public float pitch() { return pitch; }
    /** True when the yaw follows the anchor's body ({@link #followingBody}). */
    public boolean followBody() { return followBody; }
    /** Player who cast the instance, or null. */
    public ServerPlayer caster() { return caster; }
    /**
     * Source of the instance (0.10.0): the one given to {@link #withSource}, else the {@link #boundTo bound block},
     * else the caster, else the anchor ({@link EffectSource#entity}), else {@link EffectSource#script()}.
     */
    public EffectSource source() {
        if (source != null) return source;
        if (block != null) return EffectSource.block(block);
        if (caster != null) return EffectSource.player(caster);
        if (anchor != null) return EffectSource.entity(anchor);
        return EffectSource.script();
    }
    /** Cooldown per source and effect in ticks, 0 for none (0.10.0). */
    public int cooldown() { return cooldown; }
    /** Block entity that bounds the instance's life, or null (0.10.0). */
    public BlockEntity block() { return block; }

    /** Readable form with the position or anchor, orientation and options. */
    @Override public String toString() {
        return "Placement[" + (anchor == null ? "at " + position : "on " + anchor.getUUID() + " + " + position)
            + ", yaw=" + yaw + ", pitch=" + pitch + (followBody ? ", followBody" : "")
            + (caster != null ? ", caster=" + caster.getUUID() : "") + ", source=" + source()
            + (cooldown > 0 ? ", cooldown=" + cooldown : "") + (block != null ? ", boundTo=" + block.getBlockPos() : "") + "]";
    }

    private static Vec3 finite(Vec3 value, String what) {
        Objects.requireNonNull(value, what);
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z))
            throw new IllegalArgumentException("Non-finite " + what + ": " + value);
        return value;
    }

    private static Vec3 offset(Vec3 value) {
        finite(value, "offset");
        if (Math.abs(value.x) > Point.MAX_OFFSET || Math.abs(value.y) > Point.MAX_OFFSET || Math.abs(value.z) > Point.MAX_OFFSET)
            throw new IllegalArgumentException("Offset must be within " + Point.MAX_OFFSET + " blocks: " + value);
        return value;
    }
}
