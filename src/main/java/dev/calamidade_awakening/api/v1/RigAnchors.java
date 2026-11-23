package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.api.AnchorMath;
import dev.calamidade_awakening.api.BossMachine;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named points of a creature's body (0.13.0): mouth, eyes, hands, wings, tail, weapon. Effects use them through
 * {@link Point#socket} and {@link Point#socketAim} (beams and lights) and {@link Effects#anchorSocket} (the whole
 * instance follows the anchor).
 *
 * <p><b>Two kinds of pose.</b> On clients the pose of an anchor comes first from the model as it was drawn this frame
 * ({@code AnchorCapture} in {@code api.v1.client}: the addon's renderer reports the bone or part), then from a client
 * provider, then from the static definition here, then from the eyes. That pose is <b>visual only</b>: it never goes
 * back to the server. The server knows no animation; for damage, hit tests or anything authoritative use
 * {@link #approximate}, the static definition applied to the entity's server position, body yaw and head look.
 *
 * <pre>{@code
 * RigAnchors.define(EntityType.IRON_GOLEM, "olhos", RigAnchors.Spec.head(0, .05, .35));
 * RigAnchors.define(EntityType.IRON_GOLEM, "mao_direita", RigAnchors.Spec.body(.95, 1.3, .2));
 * }</pre>
 *
 * <p>Names are {@code [a-z0-9_]}, up to 64 characters. The engine depends on no animation library (GeckoLib or
 * others): the capture is a matrix the addon passes from its own renderer.
 */
public final class RigAnchors {
    private static final Map<EntityType<?>, Map<String, Spec>> SPECS = new ConcurrentHashMap<>();

    /** Where a static anchor hangs. */
    public enum Frame {
        /** From the feet, turning with the body yaw (hands, wings, tail, back). */
        BODY,
        /** From the eyes, turning with the head yaw and pitch (mouth, eyes, horns). */
        HEAD
    }

    /**
     * A static anchor: {@code right}, {@code up} and {@code forward} blocks from the frame's origin (each within
     * {@value Point#MAX_OFFSET}).
     *
     * @param frame   feet and body yaw, or eyes and head look
     * @param right   blocks to the right of the frame origin
     * @param up      blocks up
     * @param forward blocks forward
     */
    public record Spec(Frame frame, double right, double up, double forward) {
        public Spec {
            Objects.requireNonNull(frame, "frame");
            if (!(Math.abs(right) <= Point.MAX_OFFSET && Math.abs(up) <= Point.MAX_OFFSET && Math.abs(forward) <= Point.MAX_OFFSET))
                throw new IllegalArgumentException("Anchor offset must be finite and within " + Point.MAX_OFFSET + " blocks");
        }

        /** Offset from the feet in the body frame. */
        public static Spec body(double right, double up, double forward) { return new Spec(Frame.BODY, right, up, forward); }

        /** Offset from the eyes in the head frame (follows the look). */
        public static Spec head(double right, double up, double forward) { return new Spec(Frame.HEAD, right, up, forward); }
    }

    /**
     * An anchor pose in world space: position (blocks) and unit forward and up vectors (perpendicular).
     *
     * @param position world position, blocks
     * @param forward  unit vector the anchor faces
     * @param up       unit vector of the anchor's top
     */
    public record Pose(Vec3 position, Vec3 forward, Vec3 up) {
        public Pose {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(forward, "forward");
            Objects.requireNonNull(up, "up");
        }

        /** Unit left vector ({@code up × forward}). */
        public Vec3 left() { return up.cross(forward); }

        /** World position of a point {@code right}, {@code up} and {@code forward} blocks from the anchor in its frame. */
        public Vec3 at(double right, double up, double forward) {
            return position.add(left().scale(-right)).add(this.up.scale(up)).add(this.forward.scale(forward));
        }

        /** Engine form. */
        public AnchorMath.Pose math() {
            return new AnchorMath.Pose(position.x, position.y, position.z, forward.x, forward.y, forward.z, up.x, up.y, up.z);
        }

        /** From the engine form. */
        public static Pose of(AnchorMath.Pose p) {
            return new Pose(new Vec3(p.x(), p.y(), p.z()), new Vec3(p.fx(), p.fy(), p.fz()), new Vec3(p.ux(), p.uy(), p.uz()));
        }
    }

    private RigAnchors() {}

    /** Defines (or replaces) a static anchor of an entity type, on both sides (mod construction). */
    public static void define(EntityType<?> type, String name, Spec spec) {
        Objects.requireNonNull(type, "type");
        if (!BossMachine.Names.valid(name)) throw new IllegalArgumentException("Bad anchor name: " + name);
        SPECS.computeIfAbsent(type, t -> new ConcurrentHashMap<>()).put(name, Objects.requireNonNull(spec, "spec"));
    }

    /** Static anchor of a type, if defined. */
    public static Optional<Spec> spec(EntityType<?> type, String name) {
        Map<String, Spec> specs = SPECS.get(type);
        return specs == null ? Optional.empty() : Optional.ofNullable(specs.get(name));
    }

    /**
     * Server-safe approximation of an anchor (works on both sides): the static definition on the entity's current
     * position, body yaw and head look; without a definition, the eyes facing the look. Authoritative code (damage,
     * hit tests) uses this, never a client capture.
     */
    public static Pose approximate(Entity entity, String name) { return approximate(entity, name, 1); }

    /** Like {@link #approximate(Entity, String)}, interpolated at {@code partial} (0..1) between the last two ticks. */
    public static Pose approximate(Entity entity, String name, float partial) {
        Objects.requireNonNull(entity, "entity");
        Spec spec = spec(entity.getType(), name).orElse(null);
        Vec3 at = entity.getPosition(partial);
        float body, head, pitch = entity.getViewXRot(partial);
        if (entity instanceof LivingEntity living) {
            body = Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot);
            head = Mth.rotLerp(partial, living.yHeadRotO, living.yHeadRot);
        } else {
            body = head = entity.getViewYRot(partial);
        }
        AnchorMath.Frame frame = spec == null || spec.frame() == Frame.HEAD ? AnchorMath.Frame.HEAD : AnchorMath.Frame.BODY;
        return Pose.of(AnchorMath.fixed(frame, spec == null ? 0 : spec.right(), spec == null ? 0 : spec.up(),
            spec == null ? 0 : spec.forward(), at.x, at.y, at.z, entity.getEyeHeight(), body, head, pitch));
    }
}
