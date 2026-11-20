package dev.calamidade_awakening.api.v1;

import dev.calamidade_awakening.api.VisualKind;
import dev.calamidade_awakening.net.StageNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Public common-side API. Register during mod construction. Start and stop on the server thread. */
public final class Effects {
    public enum Shape { NONE, PRISM, RINGS, ORBIT, NEXUS }
    public enum Material { NONE, CRYSTAL, VEIL, LENS, NEBULA }
    public enum ImpactStyle { TINTED_FLASH, MONOCHROME }
    /** Starts at tick zero or later. Frame strength decays linearly over the specified game ticks. */
    public record Impact(int tick, int duration, float strength, ImpactStyle style) {
        public Impact {
            Objects.requireNonNull(style);
            if (tick < 0 || tick >= MAX_TICKS || duration < 1 || duration > 20 ||
                !Float.isFinite(strength) || strength <= 0 || strength > 1)
                throw new IllegalArgumentException("Invalid impact timing or strength");
        }
        public float weight(double age) {
            double elapsed = age - tick;
            return elapsed >= 0 && elapsed < duration ? strength * (float)(1 - elapsed / duration) : 0;
        }
    }

    /** Particle (a SimpleParticleType) and positional opening sound are optional resource IDs. */
    /** Default distance, in blocks, within which players receive an instance. */
    public static final double DEFAULT_RANGE = 128;
    public static final int MAX_TICKS = 12000;

    /**
     * {@code ticks}: 1..12000 (10 minutes). For ambient effects, use a long duration and end it with
     * {@link Handle#stop()}. {@code radius}: (0,512]. {@code range}: 16..1024 blocks; players farther than this
     * from the center do not receive the instance. Beyond each player's render distance only sky pixels exist,
     * so a large range mainly helps effects drawn in the sky.
     */
    public record Definition(int ticks, double radius, int argb, Shape shape, Material material,
                             ResourceLocation particle, ResourceLocation sound, Impact impact, double range) {
        public Definition(int ticks, double radius, int argb, Shape shape, Material material,
                          ResourceLocation particle, ResourceLocation sound) {
            this(ticks, radius, argb, shape, material, particle, sound, null, DEFAULT_RANGE);
        }
        public Definition(int ticks, double radius, int argb, Shape shape, Material material,
                          ResourceLocation particle, ResourceLocation sound, Impact impact) {
            this(ticks, radius, argb, shape, material, particle, sound, impact, DEFAULT_RANGE);
        }
        public Definition {
            if (impact != null && impact.tick() + impact.duration() > ticks)
                throw new IllegalArgumentException("Impact must fit within effect duration");
            Objects.requireNonNull(shape);
            Objects.requireNonNull(material);
            if (ticks < 1 || ticks > MAX_TICKS || !Double.isFinite(radius) || radius <= 0 || radius > 512)
                throw new IllegalArgumentException("Expected 1..12000 ticks and radius in (0,512]");
            if (!Double.isFinite(range) || range < 16 || range > 1024)
                throw new IllegalArgumentException("Expected range in [16,1024]");
        }
        /** Copy with another distribution range. */
        public Definition withRange(double blocks) {
            return new Definition(ticks, radius, argb, shape, material, particle, sound, impact, blocks);
        }
    }
    public interface Handle {
        /** Ends the instance right away. Returns true only on the first explicit stop of a live instance. */
        boolean stop();

        /**
         * Releases a sustained instance (0.7.0): the loop ends and the timeline jumps to its release start, then plays
         * the exit and expires. Synced to every client, including late arrivals. Returns true only on the first
         * release of a live instance whose timeline has sustain; false (and no change) otherwise.
         * Call on the server thread.
         */
        default boolean release() { return false; }
    }

    /**
     * Server-side moment of a live instance, delivered to a {@link Cue} on the server thread.
     * {@code caster} is null for effects started without a player; {@code anchor} is null for fixed positions.
     * {@code center} already includes the entity offset for anchored instances.
     * {@code tick} is the scheduled tick on the timeline clock. Since 0.7.0, {@code timeline} is the registered
     * timeline (null for effects without one), {@code localAge} the local age when the cue runs (equal to
     * {@code tick}) and {@code age} the real age of the instance, which keeps growing across sustain loops.
     */
    public record Context(ServerLevel level, Vec3 center, ServerPlayer caster,
                          Entity anchor, long seed, int tick, Handle handle,
                          Timeline timeline, double localAge, long age) {
        /** 0.6 form, kept for compatibility: no timeline, local and real age equal to the tick. */
        public Context(ServerLevel level, Vec3 center, ServerPlayer caster, Entity anchor, long seed, int tick, Handle handle) {
            this(level, center, caster, anchor, seed, tick, handle, null, tick, tick);
        }
        /** Living entities whose bounding box center lies within the radius, excluding the caster and the anchor. */
        public List<LivingEntity> living(double radius) {
            if (!Double.isFinite(radius) || radius <= 0 || radius > 128)
                throw new IllegalArgumentException("Expected radius in (0,128]");
            double squared = radius * radius;
            return level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                entity -> entity.isAlive() && entity != caster && entity != anchor
                    && entity.getBoundingBox().getCenter().distanceToSqr(center) <= squared);
        }
    }

    /**
     * Gameplay hook. Runs once per live instance when its clock reaches the scheduled tick.
     * The engine stays cosmetic; damage, knockback, block changes and costs belong to the cue.
     */
    @FunctionalInterface public interface Cue {
        void run(Context context);
    }

    private Effects() {}

    public static void register(ResourceLocation id, Definition definition) {
        VisualKind.registerExternal(Objects.requireNonNull(id), Objects.requireNonNull(definition));
    }

    /**
     * Attaches a {@link Timeline} to a registered external effect (0.7.0). Call during mod construction, after
     * {@link #register} and before any cue of the effect. The timeline duration must not exceed the definition.
     * Effects without a timeline behave exactly as in 0.6.
     */
    public static void timeline(ResourceLocation id, Timeline timeline) {
        VisualKind.setTimeline(Objects.requireNonNull(id), Objects.requireNonNull(timeline));
    }

    /**
     * Schedules a server callback at {@code tick} (0 .. duration-1) of every instance of a registered external effect.
     * Call during mod construction, after {@link #register} (and after {@link #timeline}, if any). Several cues per
     * effect and per tick are allowed and run in registration order. Exceptions are logged and do not stop the effect.
     * With a timeline the tick is local: a cue inside a sustain loop runs on every lap, and a cue the release jumps
     * over does not run.
     */
    public static void cue(ResourceLocation id, int tick, Cue cue) {
        VisualKind.addCue(Objects.requireNonNull(id), tick, Objects.requireNonNull(cue));
    }

    /** Schedules a cue on a marker of the effect's timeline (0.7.0). Register the timeline first. */
    public static void cue(ResourceLocation id, String marker, Cue cue) {
        VisualKind.addCue(Objects.requireNonNull(id), Objects.requireNonNull(marker), Objects.requireNonNull(cue));
    }

    /** Schedules a cue at the definition's {@link Impact} tick, keeping visual contact and gameplay in sync. */
    public static void onImpact(ResourceLocation id, Cue cue) {
        VisualKind kind = VisualKind.byId(VisualKind.localId(Objects.requireNonNull(id).toString()));
        if (kind == null || kind.definition() == null || kind.definition().impact() == null)
            throw new IllegalArgumentException("Effect has no Impact in its definition: " + id);
        cue(id, kind.definition().impact().tick(), cue);
    }

    public static Optional<Handle> start(ResourceLocation id, ServerLevel level, Vec3 position) {
        return StageNetwork.start(id, level, position, null, null);
    }

    /** Use the effect's built-in film, if defined, for the specified caster. */
    public static Optional<Handle> start(ResourceLocation id, net.minecraft.server.level.ServerPlayer caster, Vec3 position) {
        return StageNetwork.start(id, caster.serverLevel(), position, null, caster.getUUID());
    }

    /** Offset is in world axes, relative to the entity's feet. */
    public static Optional<Handle> follow(ResourceLocation id, Entity entity, Vec3 offset) {
        if (!(entity.level() instanceof ServerLevel level))
            throw new IllegalArgumentException("Expected a server entity");
        return StageNetwork.start(id, level, offset, entity, null);
    }
}
