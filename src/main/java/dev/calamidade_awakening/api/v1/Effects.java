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

/**
 * Public common-side API: register effects, schedule server cues and start or stop instances.
 *
 * <p>Register during mod construction, on both sides (the common mod constructor), in this order:
 * {@link #register}, then optionally {@link #timeline}, {@link #atmosphere}, {@link #beams} and {@link #lights}
 * (0.9.0), {@link #terrain} (0.11.0), {@link #sky} (0.12.0) and the cues ({@link #cue}, {@link #onImpact}). Start, follow and stop instances on the server thread; since
 * 0.10.0 an instance can be turned by a yaw and a pitch or started from a {@link Placement}. Times are in game ticks (20 per
 * second), distances in blocks, colors in ARGB ints. Invalid input throws {@link IllegalArgumentException}.
 *
 * <pre>{@code
 * var id = ResourceLocation.fromNamespaceAndPath("meumod", "golpe");
 * Effects.register(id, new Effects.Definition(80, 4, 0xffffb050, Effects.Shape.RINGS,
 *     Effects.Material.LENS, null, null));
 * Effects.onImpact(id, ctx -> ...);                         // optional gameplay
 * Effects.start(id, serverLevel, position);                 // server thread
 * }</pre>
 */
public final class Effects {
    /** Built-in world geometry drawn by the engine around the instance center. */
    public enum Shape {
        /** No built-in geometry (use AddonLayers.world or post passes). */
        NONE,
        /** Spinning prism shards. */
        PRISM,
        /** Concentric rings. */
        RINGS,
        /** Orbiting ribbons. */
        ORBIT,
        /** Curved blades that converge (Horizon Core style). */
        NEXUS
    }

    /** Built-in volumetric material raymarched by the engine's shared pass (costs one of its marks). */
    public enum Material {
        /** No volume. */
        NONE,
        /** Faceted crystal shell with an inner grid. */
        CRYSTAL,
        /** Translucent petals and strands. */
        VEIL,
        /** Lens band with streams. */
        LENS,
        /** Noisy nebula halo. */
        NEBULA
    }

    /**
     * Drawing priority of an effect (0.9.0), set with {@link Definition#withPriority}.
     *
     * <p>{@code HERO} is for the protagonist of a scene (a boss attack, the finishing move of a cast): its visible
     * instances are chosen before every NORMAL one and are never cut by the per-frame effect limit (3/6/12 for
     * LOW/BALANCED/HIGH). At most 2 HERO instances are drawn per frame, the nearest to the camera, so a server full
     * of HERO effects cannot bring the frame rate down again. HERO instances also get their own pass budget per frame
     * (1/2/3 for LOW/BALANCED/HIGH), separate from the common addon budget (1/2/4): their card shader, post passes and
     * the engine's beams and lights passes are charged there. Use HERO sparingly: one or two effects per addon.
     */
    public enum Priority {
        /** Default: competes with the other instances by intensity over distance. */
        NORMAL,
        /** Protagonist: chosen first, never cut by the effect limit (nearest 2 per frame), own pass budget. */
        HERO
    }

    /** Look of the impact accent. */
    public enum ImpactStyle {
        /** Flash tinted with the definition color (hidden when the player hides lightning flashes). */
        TINTED_FLASH,
        /** Desaturation plus contrast (a monochrome impact frame). */
        MONOCHROME
    }

    /**
     * Impact accent of a definition: a screen accent at {@code tick} whose strength decays linearly to zero over
     * {@code duration} ticks. Also the tick used by {@link #onImpact}.
     *
     * @param tick     start tick, 0..{@value #MAX_TICKS}-1 (and tick + duration within the definition)
     * @param duration ticks, 1..20
     * @param strength peak strength, (0, 1]
     * @param style    accent style
     */
    public record Impact(int tick, int duration, float strength, ImpactStyle style) {
        public Impact {
            Objects.requireNonNull(style);
            if (tick < 0 || tick >= MAX_TICKS || duration < 1 || duration > 20 ||
                !Float.isFinite(strength) || strength <= 0 || strength > 1)
                throw new IllegalArgumentException("Invalid impact timing or strength");
        }
        /** Accent strength at {@code age} ticks: {@code strength} at the impact tick, falling to 0 after {@code duration}. */
        public float weight(double age) {
            double elapsed = age - tick;
            return elapsed >= 0 && elapsed < duration ? strength * (float)(1 - elapsed / duration) : 0;
        }
    }

    /** Default distribution range, in blocks: players farther than this from the center do not receive an instance. */
    public static final double DEFAULT_RANGE = 128;
    /** Longest effect, in ticks (12000 = 10 minutes). */
    public static final int MAX_TICKS = 12000;

    /**
     * What an effect is: duration, size, color, built-in look, optional particle and sound, impact accent and
     * distribution range. For ambient effects use a long duration and end them with {@link Handle#stop()}, or a
     * {@link Timeline} with sustain and {@link Handle#release()}. Beyond each player's render distance only sky
     * pixels exist, so a large range mainly helps effects drawn in the sky.
     *
     * <pre>{@code
     * new Effects.Definition(120, 2, 0xff65e8c9, Effects.Shape.RINGS, Effects.Material.CRYSTAL,
     *     ResourceLocation.withDefaultNamespace("end_rod"), null,
     *     new Effects.Impact(40, 3, .75f, Effects.ImpactStyle.MONOCHROME)).withRange(256);
     * }</pre>
     *
     * <p>Since 0.9.0 a definition also has a {@link Priority} ({@code NORMAL} unless {@link #withPriority} says
     * {@code HERO}).
     *
     * @param ticks    duration, 1..{@value #MAX_TICKS} ticks
     * @param radius   size in blocks, (0, 512]; also the frustum test extent and the radius handed to layers
     * @param argb     main color (ARGB), used by built-in geometry, flashes and cards
     * @param shape    built-in geometry
     * @param material built-in volume
     * @param particle optional particle ID (a SimpleParticleType), emitted every 4 ticks; null for none
     * @param sound    optional positional opening sound ID; null for none
     * @param impact   optional impact accent; null for none
     * @param range    distribution range, 16..1024 blocks ({@link #DEFAULT_RANGE} by default)
     * @param priority drawing priority (0.9.0), {@link Priority#NORMAL} by default; see {@link #withPriority}
     */
    public record Definition(int ticks, double radius, int argb, Shape shape, Material material,
                             ResourceLocation particle, ResourceLocation sound, Impact impact, double range,
                             Priority priority) {
        /** Definition without impact accent and with the {@link #DEFAULT_RANGE default range}. */
        public Definition(int ticks, double radius, int argb, Shape shape, Material material,
                          ResourceLocation particle, ResourceLocation sound) {
            this(ticks, radius, argb, shape, material, particle, sound, null, DEFAULT_RANGE);
        }
        /** Definition with an impact accent and the {@link #DEFAULT_RANGE default range}. */
        public Definition(int ticks, double radius, int argb, Shape shape, Material material,
                          ResourceLocation particle, ResourceLocation sound, Impact impact) {
            this(ticks, radius, argb, shape, material, particle, sound, impact, DEFAULT_RANGE);
        }
        /** 0.8 form, kept for compatibility: {@link Priority#NORMAL} priority. */
        public Definition(int ticks, double radius, int argb, Shape shape, Material material,
                          ResourceLocation particle, ResourceLocation sound, Impact impact, double range) {
            this(ticks, radius, argb, shape, material, particle, sound, impact, range, Priority.NORMAL);
        }
        public Definition {
            if (impact != null && impact.tick() + impact.duration() > ticks)
                throw new IllegalArgumentException("Impact must fit within effect duration");
            Objects.requireNonNull(shape);
            Objects.requireNonNull(material);
            Objects.requireNonNull(priority, "priority");
            if (ticks < 1 || ticks > MAX_TICKS || !Double.isFinite(radius) || radius <= 0 || radius > 512)
                throw new IllegalArgumentException("Expected 1..12000 ticks and radius in (0,512]");
            if (!Double.isFinite(range) || range < 16 || range > 1024)
                throw new IllegalArgumentException("Expected range in [16,1024]");
        }
        /** Copy with another distribution range, 16..1024 blocks (keeps the priority). */
        public Definition withRange(double blocks) {
            return new Definition(ticks, radius, argb, shape, material, particle, sound, impact, blocks, priority);
        }
        /**
         * Copy with another drawing priority (0.9.0). {@code withPriority(Priority.HERO)} makes the effect a
         * protagonist: drawn before NORMAL effects, never cut by the effect limit (the nearest 2 HERO instances per
         * frame) and with its own pass budget (1/2/3 for LOW/BALANCED/HIGH).
         *
         * <pre>{@code
         * new Effects.Definition(200, 24, 0xffffd070, Effects.Shape.NONE, Effects.Material.NONE, null, null)
         *     .withRange(256).withPriority(Effects.Priority.HERO);
         * }</pre>
         */
        public Definition withPriority(Priority value) {
            return new Definition(ticks, radius, argb, shape, material, particle, sound, impact, range, value);
        }
    }
    /** Server-side control of one live instance, returned by {@link #start} and {@link #follow}. */
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

        /**
         * True while the instance lives on the server (0.10.0): not stopped, not expired and its anchor or bound block
         * still there. Call on the server thread. Implementations from before 0.10.0 answer false.
         */
        default boolean alive() { return false; }
    }

    /**
     * Server-side moment of a live instance, delivered to a {@link Cue} on the server thread.
     *
     * @param level    server level of the instance
     * @param center   current center (blocks); includes the entity offset for anchored instances
     * @param caster   player who cast it, or null (started without a player, or the player left)
     * @param anchor   entity of {@link Effects#follow}, or null for fixed positions
     * @param seed     instance seed, the same the client uses for its visuals
     * @param tick     scheduled tick on the timeline clock (local tick)
     * @param handle   handle of this instance ({@code stop()}, {@code release()})
     * @param timeline registered timeline, or null (0.7.0)
     * @param localAge local age when the cue runs, equal to {@code tick} (0.7.0)
     * @param age      real age in ticks, which keeps growing across sustain loops (0.7.0)
     * @param yaw      instance yaw in degrees when the cue runs, [-180, 180) (0.10.0): the {@link Placement} yaw, plus
     *                 the anchor's body yaw when the instance follows it; 0 without orientation
     * @param pitch    instance pitch in degrees, [-90, 90], positive looks down (0.10.0); 0 without orientation
     * @param source   who started the instance (0.10.0): player, entity, block or script
     */
    public record Context(ServerLevel level, Vec3 center, ServerPlayer caster,
                          Entity anchor, long seed, int tick, Handle handle,
                          Timeline timeline, double localAge, long age, float yaw, float pitch, EffectSource source) {
        /** 0.6 form, kept for compatibility: no timeline, local and real age equal to the tick. */
        public Context(ServerLevel level, Vec3 center, ServerPlayer caster, Entity anchor, long seed, int tick, Handle handle) {
            this(level, center, caster, anchor, seed, tick, handle, null, tick, tick);
        }
        /**
         * 0.7 to 0.9 form, kept for compatibility: no orientation (yaw 0, pitch 0, the world axes); the source is the
         * caster, else the anchor, else a script.
         */
        public Context(ServerLevel level, Vec3 center, ServerPlayer caster, Entity anchor, long seed, int tick,
                       Handle handle, Timeline timeline, double localAge, long age) {
            this(level, center, caster, anchor, seed, tick, handle, timeline, localAge, age, 0, 0,
                caster != null ? EffectSource.player(caster) : anchor != null ? EffectSource.entity(anchor) : EffectSource.script());
        }
        public Context {
            Objects.requireNonNull(source, "source");
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
        /**
         * World offset (blocks) of {@code right}, {@code up} and {@code forward} in the instance frame (0.10.0); add it
         * to {@link #center()} for a point in front of the effect. Without orientation, forward is south (+Z).
         *
         * <pre>{@code
         * Vec3 tip = ctx.center().add(ctx.facing(0, 0, 6));   // 6 blocks ahead of the effect
         * }</pre>
         */
        public Vec3 facing(double right, double up, double forward) {
            var basis = dev.calamidade_awakening.api.Basis.of(yaw, pitch);
            return new Vec3(basis.worldX(-right, up, forward), basis.worldY(-right, up, forward),
                basis.worldZ(-right, up, forward));
        }
        /** Unit vector the instance faces (0.10.0); (0, 0, 1) without orientation. */
        public Vec3 forward() { return facing(0, 0, 1); }
    }

    /**
     * Gameplay hook. Runs once per live instance when its clock reaches the scheduled tick.
     * The engine stays cosmetic; damage, knockback, block changes and costs belong to the cue.
     */
    @FunctionalInterface public interface Cue {
        /** Runs on the server thread; exceptions are logged and do not stop the effect or the other cues. */
        void run(Context context);
    }

    private Effects() {}

    /**
     * Registers an external effect in your own namespace (the {@code calamidade_awakening} namespace is reserved).
     * Call during mod construction on both sides; IDs must be unique.
     */
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
     * (0.13.0) Makes the whole instance follow a rig anchor ({@link RigAnchors}) of its anchor entity: the center of
     * an instance started with {@link #follow} or {@link Placement#on} is the anchor's pose (plus the start offset, in
     * the anchor's frame) instead of the entity's feet. Particles in the eyes, an explosion on the chest, a shader
     * around the hands. On clients the pose is the visual one (captured from the model this frame, a provider, the
     * static definition or the eyes); on the server, and so in cues ({@code ctx.center()}), it is
     * {@link RigAnchors#approximate}. Instances without an anchor entity are unaffected. Call during mod construction
     * (both sides), after {@link #register}; once per effect.
     *
     * <pre>{@code
     * Effects.register(OLHOS, new Effects.Definition(60, 1.5, 0xffff4020, Effects.Shape.NONE, Effects.Material.NONE, null, null));
     * Effects.anchorSocket(OLHOS, "olhos");
     * }</pre>
     */
    public static void anchorSocket(ResourceLocation id, String name) {
        VisualKind.setSocket(Objects.requireNonNull(id), Objects.requireNonNull(name));
    }

    /**
     * Attaches an {@link Atmosphere} to a registered external effect (0.8.0): sky tint, fog, exposure, ambient
     * tint and stars while its instances are active. Call during mod construction (common side, so a dedicated
     * server validates it too), after {@link #register} and, when the atmosphere reads tracks or a phase, after
     * {@link #timeline}. One per effect; only effects of your namespace. Effects without an atmosphere behave
     * exactly as in 0.7. The client draws the result in one engine pass per frame, outside the addon pass budget.
     */
    public static void atmosphere(ResourceLocation id, Atmosphere atmosphere) {
        VisualKind.setAtmosphere(Objects.requireNonNull(id), Objects.requireNonNull(atmosphere));
    }

    /**
     * Attaches 1..{@value Beam#MAX_PER_EFFECT} {@link Beam beams} to a registered external effect (0.9.0). Call once
     * per effect during mod construction (common side, so a dedicated server validates it too), after
     * {@link #register} and, when a beam reads tracks or a phase, after {@link #timeline}. Only effects of your
     * namespace. The client draws the beams of every drawn instance in one engine pass per frame, charged to the HERO
     * pass budget when the effect is {@link Priority#HERO}, otherwise to the common addon budget.
     *
     * <pre>{@code
     * Effects.beams(ID, Beam.builder(Point.casterEye(0, -.2, 0), Point.casterAim(48)).width(.15f, .9f)
     *     .color(0xffff4060).build());
     * }</pre>
     */
    public static void beams(ResourceLocation id, Beam... beams) {
        VisualKind.setBeams(Objects.requireNonNull(id), List.of(Objects.requireNonNull(beams)));
    }

    /**
     * Attaches 1..{@value Light#MAX_PER_EFFECT} visual {@link Light lights} to a registered external effect (0.9.0).
     * Call once per effect during mod construction (common side), after {@link #register}, after {@link #timeline}
     * when a light reads tracks or a phase, and after {@link #beams} when a light follows a beam
     * ({@link Light#along(Beam)}). Only effects of your namespace. The client draws the lights of every drawn instance
     * in one engine pass per frame, before the effect passes and the atmosphere, charged to the HERO pass budget when
     * the effect is {@link Priority#HERO}, otherwise to the common addon budget. Visual only: the game's light level,
     * mob spawning and plant growth do not change.
     *
     * <pre>{@code
     * Effects.lights(ID, Light.point(Point.center(0, .6, 0)).color(0xffff9a40).intensity(2).radius(9).build());
     * }</pre>
     */
    public static void lights(ResourceLocation id, Light... lights) {
        VisualKind.setLights(Objects.requireNonNull(id), List.of(Objects.requireNonNull(lights)));
    }

    /**
     * Attaches 1..{@value TerrainEdit#MAX_PER_EFFECT} {@link TerrainEdit terrain edits} to a registered external effect
     * (0.11.0): excavation, burnt shell, real blocks or a structure placed at a marker of the effect's timeline, with
     * the heat and the falling stand-in drawn by the client. Call once per effect during mod construction on both sides
     * (the client draws the heat from the same shape and seed), after {@link #register} and {@link #timeline}. Only
     * effects of your namespace. The server runs each edit once per instance, the first time the instance clock reaches
     * the marker, under the safety rules of the server config; see {@link TerrainEdit}.
     *
     * <pre>{@code
     * Effects.terrain(ID, TerrainEdit.carve(TerrainShape.crater(8, 4).rough(1, 3)).commitAt("impacto")
     *     .burn(TerrainPalette.burnt(), 2).build());
     * }</pre>
     */
    public static void terrain(ResourceLocation id, TerrainEdit... edits) {
        VisualKind.setTerrain(Objects.requireNonNull(id), List.of(Objects.requireNonNull(edits)));
    }

    /**
     * Attaches 1..{@value Sky#MAX_PER_EFFECT} {@link Sky sky layers} to a registered external effect (0.12.0): black
     * holes, portals, eclipses, rifts, giant moons and planets, auroras, placed by direction on the sky. Call once per
     * effect during mod construction (common side, so a dedicated server validates it too), after {@link #register} and,
     * when a layer reads tracks or a phase, after {@link #timeline}. Only effects of your namespace. Works on ordinary
     * effects: every player within the effect's range sees the layers on their own sky, wherever they look. The client
     * paints every active layer in one engine pass per frame ("sky"), outside the addon pass budget, at most 2/4/6/8 per
     * frame (LOW/BALANCED/HIGH/ULTRA), HERO effects first.
     *
     * <pre>{@code
     * Effects.sky(ID, Sky.portal().direction(0, 40).size(36).track(Sky.Param.PROGRESS, "abrir").build());
     * }</pre>
     */
    public static void sky(ResourceLocation id, Sky... layers) {
        VisualKind.setSky(Objects.requireNonNull(id), List.of(Objects.requireNonNull(layers)));
    }

    /**
     * Attaches a custom sky {@link Backdrop background} (cubemap, panorama or gradient) to a registered external effect
     * (0.14.0). Call once per effect during mod construction (common side), after {@link #register} and, when the
     * background reads tracks or a phase, after {@link #timeline}. Only effects of your namespace. While an instance is
     * active, every player within the effect's range sees the background behind everything else, at the instance's
     * weight; started as a global event ({@link WorldEvents}), the whole scope sees it. Nothing changes for effects
     * without a background, and players can turn custom backgrounds off ({@code customSkies}).
     *
     * <pre>{@code
     * Effects.backdrop(ID, Backdrop.cubemap("meumod:textures/sky/arena.png").build());
     * }</pre>
     */
    public static void backdrop(ResourceLocation id, Backdrop backdrop) {
        VisualKind.setBackdrop(Objects.requireNonNull(id), Objects.requireNonNull(backdrop));
    }

    /**
     * Attaches 1..{@value StarField#MAX_PER_EFFECT} {@link StarField star fields} (procedural fields or catalogs with
     * constellations) to a registered external effect (0.14.0). Call once per effect during mod construction (common
     * side), after {@link #register} and, when a field reads tracks or a phase, after {@link #timeline}. Only effects of
     * your namespace. While an instance is active, every player within the effect's range sees the stars at the
     * instance's weight (started as a global event, the whole scope); nothing changes for effects without star fields,
     * and players can turn custom skies off ({@code customSkies}).
     *
     * <pre>{@code
     * Effects.stars(ID, StarField.random(42, 6000).band(90, 30, 14, .5f).build());
     * }</pre>
     */
    public static void stars(ResourceLocation id, StarField... fields) {
        VisualKind.setStars(Objects.requireNonNull(id), List.of(Objects.requireNonNull(fields)));
    }

    /**
     * Attaches 1..{@value Nebula#MAX_PER_EFFECT} procedural {@link Nebula nebulae} to a registered external effect
     * (0.14.0). Same rules as {@link #stars}: once per effect, during mod construction, after {@link #register} and,
     * when a nebula reads tracks or a phase, after {@link #timeline}.
     *
     * <pre>{@code
     * Effects.nebulae(ID, Nebula.cloud(120, 35, 22).colors(0xffff70c0, 0x803060ff).build());
     * }</pre>
     */
    public static void nebulae(ResourceLocation id, Nebula... nebulae) {
        VisualKind.setNebulae(Objects.requireNonNull(id), List.of(Objects.requireNonNull(nebulae)));
    }

    /**
     * Attaches 1..{@value SkyImage#MAX_PER_EFFECT} {@link SkyImage images} (still PNGs or flipbooks) to a registered
     * external effect (0.14.0). Same rules as {@link #stars}: once per effect, during mod construction, after
     * {@link #register} and, when an image reads tracks or a phase, after {@link #timeline}. The textures are loaded on
     * the client the first time an instance shows them.
     *
     * <pre>{@code
     * Effects.images(ID, SkyImage.of("meumod:textures/sky/dragao.png").direction(120, 35).size(18).build());
     * }</pre>
     */
    public static void images(ResourceLocation id, SkyImage... images) {
        VisualKind.setImages(Objects.requireNonNull(id), List.of(Objects.requireNonNull(images)));
    }

    /**
     * Attaches 1..{@value SkyImpostor#MAX_PER_EFFECT} {@link SkyImpostor impostors} (pre-rendered 3D objects) to a
     * registered external effect (0.14.0). Same rules as {@link #stars}. The descriptor and pages are read on the client
     * the first time an instance shows them; an invalid descriptor is logged once and skipped.
     *
     * <pre>{@code
     * Effects.impostors(ID, SkyImpostor.of("meumod:nave").direction(90, 20).size(12).build());
     * }</pre>
     */
    public static void impostors(ResourceLocation id, SkyImpostor... impostors) {
        VisualKind.setImpostors(Objects.requireNonNull(id), List.of(Objects.requireNonNull(impostors)));
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

    /**
     * True when {@code source} may start {@code id} now: no cooldown of that source for that effect is running
     * (0.10.0). Cooldowns are per source and per effect, server-side, and are the same that {@link Placement#withCooldown},
     * the {@link EffectTriggers triggers} and the Focus cards use (a card's cooldown is its player's). Server thread;
     * unknown effects throw {@link IllegalArgumentException}.
     *
     * <pre>{@code
     * var mob = EffectSource.entity(zombie);
     * if (Effects.cooldownReady(mob, ID, level) && Effects.start(ID, level, Placement.on(zombie)).isPresent())
     *     Effects.startCooldown(mob, ID, level, 60);
     * }</pre>
     */
    public static boolean cooldownReady(EffectSource source, ResourceLocation id, ServerLevel level) {
        return StageNetwork.cooldownLeft(Objects.requireNonNull(source), Objects.requireNonNull(id), Objects.requireNonNull(level)) == 0;
    }

    /** Ticks left of the cooldown of {@code source} for {@code id}, 0 when ready (0.10.0). Server thread. */
    public static long cooldownLeft(EffectSource source, ResourceLocation id, ServerLevel level) {
        return StageNetwork.cooldownLeft(Objects.requireNonNull(source), Objects.requireNonNull(id), Objects.requireNonNull(level));
    }

    /**
     * Starts (or restarts) the cooldown of {@code source} for {@code id}: {@code ticks} 1..72000, 0 clears it
     * (0.10.0). Server thread.
     */
    public static void startCooldown(EffectSource source, ResourceLocation id, ServerLevel level, int ticks) {
        if (ticks < 0 || ticks > 72000) throw new IllegalArgumentException("Expected a cooldown in 0..72000 ticks: " + ticks);
        StageNetwork.startCooldown(Objects.requireNonNull(source), Objects.requireNonNull(id), Objects.requireNonNull(level), ticks);
    }

    /**
     * Starts an instance at a fixed world position (blocks). Server thread. Unknown effects throw
     * {@link IllegalArgumentException}; the result is empty when the position's chunk is not loaded or the server
     * already runs 64 instances. Since 0.10.0 its source is {@link EffectSource#script()}, so it also respects the slots
     * reserved for players (see {@link EffectSource}).
     */
    public static Optional<Handle> start(ResourceLocation id, ServerLevel level, Vec3 position) {
        return StageNetwork.start(id, level, position, null, null);
    }

    /**
     * Starts an instance at a fixed world position (blocks) turned by {@code yaw} and {@code pitch} (degrees,
     * Minecraft convention: yaw 0 faces south, pitch positive looks down, within [-90, 90]) (0.10.0). Center points,
     * {@link Point#facing facing points} and the track offsets of beams and lights use that frame. Server thread.
     *
     * <pre>{@code
     * Effects.start(ID, level, position, player.getYRot(), 0);   // turned to where the player looks
     * }</pre>
     */
    public static Optional<Handle> start(ResourceLocation id, ServerLevel level, Vec3 position, float yaw, float pitch) {
        return start(id, level, Placement.at(position, yaw, pitch));
    }

    /**
     * Starts an instance at a position for a caster: the caster's client plays the effect's camera (card shots or
     * {@code AddonLayers.film}) and cues receive the player as {@code caster}. Server thread. Since 0.10.0 its source is
     * the player: empty when that player already holds the per-player limit (16 by default).
     */
    public static Optional<Handle> start(ResourceLocation id, net.minecraft.server.level.ServerPlayer caster, Vec3 position) {
        return StageNetwork.start(id, caster.serverLevel(), position, null, caster.getUUID());
    }

    /**
     * Starts an instance for a caster at a position turned by {@code yaw} and {@code pitch} in degrees (0.10.0); see
     * {@link #start(ResourceLocation, ServerLevel, Vec3, float, float)}. Server thread.
     */
    public static Optional<Handle> start(ResourceLocation id, ServerPlayer caster, Vec3 position, float yaw, float pitch) {
        return start(id, caster.serverLevel(), Placement.at(position, yaw, pitch).withCaster(caster));
    }

    /**
     * Starts an instance from a {@link Placement} (0.10.0): fixed position or anchor entity, yaw and pitch, the yaw
     * optionally following the anchor's body, the caster, the {@link EffectSource source}, a cooldown and a bound block
     * entity. Server thread. Empty (no error) when the chunk is not loaded, the anchor is removed or in another level
     * than {@code level}, the source's cooldown for this effect is running, the source already holds its limit (by
     * default 16 per player, 4 per mob, 2 per block), only the slots reserved for players are left (16 of the 64 by
     * default, for non-player sources) or the server already runs 64 instances.
     *
     * <pre>{@code
     * Effects.start(ID, level, Placement.on(mob).shifted(0, 1.2, .5).followingBody());
     * }</pre>
     */
    public static Optional<Handle> start(ResourceLocation id, ServerLevel level, Placement placement) {
        return StageNetwork.start(id, Objects.requireNonNull(level), Objects.requireNonNull(placement));
    }

    /**
     * Starts an instance anchored to a server entity: the center follows the entity's feet plus {@code offset}
     * (world axes, blocks). The instance ends when the entity is removed or unloaded. Server thread. Empty when
     * the entity is removed or in another level, its chunk is not loaded or the server already runs 64 instances.
     * Since 0.10.0 the entity is the source ({@link EffectSource#entity}): empty also above its per-source limit.
     */
    public static Optional<Handle> follow(ResourceLocation id, Entity entity, Vec3 offset) {
        if (!(entity.level() instanceof ServerLevel level))
            throw new IllegalArgumentException("Expected a server entity");
        return StageNetwork.start(id, level, offset, entity, null);
    }

    /**
     * Starts an instance anchored to a server entity whose yaw follows the entity's body when {@code followBody} is
     * true (0.10.0): the body yaw of a living entity, interpolated on clients, or the yaw of other entities. The
     * {@code offset} (blocks) is then in the body frame (x left, y up, z forward), so {@code new Vec3(0, 1.2, .8)} stays
     * in front of the chest while the mob turns. With {@code followBody} false this is
     * {@link #follow(ResourceLocation, Entity, Vec3)}. Server thread.
     */
    public static Optional<Handle> follow(ResourceLocation id, Entity entity, Vec3 offset, boolean followBody) {
        if (!(entity.level() instanceof ServerLevel level))
            throw new IllegalArgumentException("Expected a server entity");
        Placement placement = Placement.on(entity, offset);
        return start(id, level, followBody ? placement.followingBody() : placement);
    }
}
