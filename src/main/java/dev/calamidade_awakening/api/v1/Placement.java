package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.api.Basis;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;

/**
 * Where and how an instance starts (0.10.0): a fixed position or an anchor entity, an orientation (yaw and pitch) and
 * the player who cast it. Immutable: every {@code with...}, {@link #facing}, {@link #shifted} and
 * {@link #followingBody} call returns a copy. Start it with
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
 * // A strike from attacker to victim:
 * Effects.start(ID, level, Placement.at(victim.position()).facing(attacker.getEyePosition()));
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

    private Placement(Vec3 position, Entity anchor, float yaw, float pitch, boolean followBody, ServerPlayer caster) {
        this.position = position; this.anchor = anchor; this.yaw = yaw; this.pitch = pitch;
        this.followBody = followBody; this.caster = caster;
    }

    /** Fixed world position in blocks, no orientation (world axes). */
    public static Placement at(Vec3 position) {
        return new Placement(finite(position, "position"), null, 0, 0, false, null);
    }

    /** Fixed world position in blocks, turned by {@code yaw} and {@code pitch} (degrees). */
    public static Placement at(Vec3 position, float yaw, float pitch) {
        return at(position).withOrientation(yaw, pitch);
    }

    /** Anchored to an entity: the center follows the entity's feet. The instance ends when the entity goes away. */
    public static Placement on(Entity anchor) {
        return new Placement(Vec3.ZERO, Objects.requireNonNull(anchor, "anchor"), 0, 0, false, null);
    }

    /**
     * Anchored to an entity with an offset from its feet, in blocks, in the instance frame (x left, y up, z forward;
     * the world axes while the placement has no orientation, as in {@link Effects#follow}).
     */
    public static Placement on(Entity anchor, Vec3 offset) {
        return new Placement(offset(offset), Objects.requireNonNull(anchor, "anchor"), 0, 0, false, null);
    }

    /**
     * Copy with this orientation: yaw in degrees (any finite value, wrapped to [-180, 180)) and pitch in degrees
     * ([-90, 90], positive looks down). With {@link #followingBody}, the yaw is added to the body yaw.
     */
    public Placement withOrientation(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90)
            throw new IllegalArgumentException("Expected a finite yaw and a pitch in [-90, 90]: " + yaw + ", " + pitch);
        return new Placement(position, anchor, Basis.wrapYaw(yaw), pitch == 0 ? 0f : pitch, followBody, caster);
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
     * Copy moved by {@code right}, {@code up} and {@code forward} blocks in the placement's frame. For a fixed position
     * the position moves now, with the orientation set so far (set the orientation first). For an anchor the amount
     * is added to the offset, which stays in the instance frame and so turns with {@link #followingBody}.
     */
    public Placement shifted(double right, double up, double forward) {
        if (!Double.isFinite(right) || !Double.isFinite(up) || !Double.isFinite(forward))
            throw new IllegalArgumentException("Shift must be finite");
        if (anchor != null) return new Placement(offset(position.add(-right, up, forward)), anchor, yaw, pitch, followBody, caster);
        Basis basis = Basis.of(yaw, pitch);
        Vec3 moved = position.add(basis.worldX(-right, up, forward), basis.worldY(-right, up, forward),
            basis.worldZ(-right, up, forward));
        return new Placement(finite(moved, "position"), null, yaw, pitch, followBody, caster);
    }

    /**
     * Copy whose yaw follows the anchor's body every tick and every frame (0.10.0): the body yaw of a living entity
     * ({@code yBodyRot}, interpolated on clients), or the entity's yaw for other entities, plus this placement's yaw
     * as an offset. The pitch stays fixed. Only for anchored placements.
     */
    public Placement followingBody() {
        if (anchor == null) throw new IllegalArgumentException("followingBody needs an anchored placement (Placement.on)");
        return new Placement(position, anchor, yaw, pitch, true, caster);
    }

    /**
     * Copy cast by this player: their client plays the effect's camera (card shots or {@code AddonLayers.film}),
     * cues receive them as {@code caster} and {@link Point#caster caster points} resolve to them. Null for none.
     */
    public Placement withCaster(ServerPlayer caster) {
        return new Placement(position, anchor, yaw, pitch, followBody, caster);
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

    /** Readable form with the position or anchor, orientation and options. */
    @Override public String toString() {
        return "Placement[" + (anchor == null ? "at " + position : "on " + anchor.getUUID() + " + " + position)
            + ", yaw=" + yaw + ", pitch=" + pitch + (followBody ? ", followBody" : "")
            + (caster != null ? ", caster=" + caster.getUUID() : "") + "]";
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
